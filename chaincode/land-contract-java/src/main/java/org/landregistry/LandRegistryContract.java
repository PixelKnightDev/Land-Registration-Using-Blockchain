package org.landregistry;

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import com.owlike.genson.Genson;

@Contract(
        name = "LandRegistryContract",
        info = @Info(
                title = "Land Registry Smart Contract",
                description = "The core chaincode executing business rules for Land Registration and Transfer",
                version = "1.1.0",
                license = @License(name = "Apache 2.0"),
                contact = @Contact(email = "admin@landregistry.gov.in", name = "Land Registry Admin")
        )
)
@Default
public final class LandRegistryContract implements ContractInterface {

    private final Genson genson = new Genson();

    private enum LandRegistryErrors {
        ASSET_NOT_FOUND,
        ASSET_ALREADY_EXISTS,
        ASSET_NOT_ACTIVE,
        UNAUTHORIZED_SELLER, // Added to support Rule 2: Ownership Verification
        INVALID_INPUT        // Added for basic input validation
    }

    /**
     * Initializes the ledger.
     * @param ctx the transaction context
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void initLedger(final Context ctx) {
        System.out.println("Land Registry Ledger Initialized.");
    }

    /**
     * Creates a new Land Asset on the blockchain.
     * @param ctx the transaction context
     * @param ulpin the Unique Land Parcel Identification Number (Primary Key)
     * @param gpsCoordinates the mathematical anchor
     * @param parentUlpin Lineage tracking (pass "NONE" for root assets)
     * @param currentOwnerId The ID of the initial owner
     * @param documentHash The SHA-256 hash of the initial physical deed
     * @return the created LandAsset
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public LandAsset createLandAsset(final Context ctx, final String ulpin, final String gpsCoordinates, 
                                     final String parentUlpin, final String currentOwnerId, final String documentHash) {
        
        // --- INPUT VALIDATION PHASE ---
        if (ulpin == null || ulpin.trim().isEmpty()) {
            throw new ChaincodeException("ULPIN must not be null or empty", 
                                         LandRegistryErrors.INVALID_INPUT.toString());
        }
        if (gpsCoordinates == null || gpsCoordinates.trim().isEmpty()) {
            throw new ChaincodeException("GPS coordinates must not be null or empty", 
                                         LandRegistryErrors.INVALID_INPUT.toString());
        }
        if (parentUlpin == null || parentUlpin.trim().isEmpty()) {
            throw new ChaincodeException("Parent ULPIN must not be null or empty (use 'NONE' for root assets)", 
                                         LandRegistryErrors.INVALID_INPUT.toString());
        }
        if (currentOwnerId == null || currentOwnerId.trim().isEmpty()) {
            throw new ChaincodeException("Current owner ID must not be null or empty", 
                                         LandRegistryErrors.INVALID_INPUT.toString());
        }
        if (documentHash == null || documentHash.trim().isEmpty()) {
            throw new ChaincodeException("Document hash must not be null or empty", 
                                         LandRegistryErrors.INVALID_INPUT.toString());
        }

        // Validation: Ensure the ULPIN doesn't already exist
        if (assetExists(ctx, ulpin)) {
            throw new ChaincodeException("Land Asset with ULPIN " + ulpin + " already exists", 
                                         LandRegistryErrors.ASSET_ALREADY_EXISTS.toString());
        }

        // Creation: Instantiate the immutable Java object
        LandAsset land = new LandAsset(ulpin, gpsCoordinates, parentUlpin, currentOwnerId, documentHash, "ACTIVE");

        // Persistence: Convert to JSON and save to the CouchDB world state
        ctx.getStub().putStringState(ulpin, genson.serialize(land));

        return land;
    }

    /**
     * Executes Step 4: Smart Contract Execution to transfer land ownership.
     * @param ctx the transaction context
     * @param ulpin the Primary Key of the land being transferred
     * @param sellerId the ID of the current owner attempting the transfer
     * @param buyerId the ID of the new owner
     * @param newDocumentHash the SHA-256 hash of the newly signed Sale Deed
     * @return the updated LandAsset
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public LandAsset transferLandOwnership(final Context ctx, final String ulpin, 
                                           final String sellerId, final String buyerId, final String newDocumentHash) {
         // Input Validation: Ensure buyerId and newDocumentHash are not null or blank
         if (buyerId == null || buyerId.trim().isEmpty()) {
             throw new ChaincodeException("Transaction Rejected: buyerId must not be null or blank",
                                          "INVALID_BUYER_ID");
         }
         if (newDocumentHash == null || newDocumentHash.trim().isEmpty()) {
             throw new ChaincodeException("Transaction Rejected: newDocumentHash must not be null or blank",
                                          "INVALID_DOCUMENT_HASH");
         }
        // RULE 1: Land Existence
        String landJson = ctx.getStub().getStringState(ulpin);
        if (landJson == null || landJson.isEmpty()) {
            throw new ChaincodeException("Transaction Rejected: Land Asset " + ulpin + " does not exist", 
                                         LandRegistryErrors.ASSET_NOT_FOUND.toString());
        }

        LandAsset land = genson.deserialize(landJson, LandAsset.class);

        // RULE 2: Ownership Verification
        if (!land.getCurrentOwnerId().equals(sellerId)) {
            throw new ChaincodeException("Transaction Rejected: Seller ID " + sellerId + " is not the recognized owner of ULPIN " + ulpin, 
                                         LandRegistryErrors.UNAUTHORIZED_SELLER.toString());
        }

        // State Check
        if (!land.getStatus().equals("ACTIVE")) {
            throw new ChaincodeException("Transaction Rejected: Land Asset " + ulpin + " is not ACTIVE", 
                                         LandRegistryErrors.ASSET_NOT_ACTIVE.toString());
        }

        // --- MUTATION PHASE ---
        // Enforce immutability in memory by creating a new instance
        LandAsset updatedLand = new LandAsset(
            land.getUlpin(),
            land.getGpsCoordinates(),
            land.getParentUlpin(),
            buyerId, // <-- Updated owner
            newDocumentHash, // <-- Updated document hash
            land.getStatus()
        );

        // Step 5: Ledger Update
        ctx.getStub().putStringState(ulpin, genson.serialize(updatedLand));

        return updatedLand;
    }

    /**
     * Splits an existing land parcel into two new child parcels (Mutation).
     * @param ctx the transaction context
     * @param parentUlpin the ULPIN of the land being split
     * @param currentOwnerId the ID of the owner authorizing the split
     * @param child1Ulpin the primary key for the first new parcel
     * @param child1Gps the GPS boundary for the first new parcel
     * @param child2Ulpin the primary key for the second new parcel
     * @param child2Gps the GPS boundary for the second new parcel
     * @param newDocumentHash the hash of the new mutation deed/document
     * @return a success message confirming the mutation
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String mutateLand(final Context ctx, final String parentUlpin, final String currentOwnerId,
                             final String child1Ulpin, final String child1Gps,
                             final String child2Ulpin, final String child2Gps,
                             final String newDocumentHash) {
        // 1. Fetch and Validate Parent Asset
        String parentJson = ctx.getStub().getStringState(parentUlpin);
        if (parentJson == null || parentJson.isEmpty()) {
            throw new ChaincodeException("Mutation Rejected: Parent Land Asset does not exist", 
                                         LandRegistryErrors.ASSET_NOT_FOUND.toString());
        }
        LandAsset parentLand = genson.deserialize(parentJson, LandAsset.class);

        // 2. Verify Ownership and Status
        if (!parentLand.getCurrentOwnerId().equals(currentOwnerId)) {
            throw new ChaincodeException("Mutation Rejected: Unauthorized owner", 
                                         LandRegistryErrors.UNAUTHORIZED_SELLER.toString());
        }
        if (!parentLand.getStatus().equals("ACTIVE")) {
            throw new ChaincodeException("Mutation Rejected: Parent asset is not ACTIVE", 
                                         LandRegistryErrors.ASSET_NOT_ACTIVE.toString());
        }

        // Safety Check: Ensure new ULPINs don't clash with existing ones
        if (assetExists(ctx, child1Ulpin) || assetExists(ctx, child2Ulpin)) {
            throw new ChaincodeException("Mutation Rejected: One or both child ULPINs already exist", 
                                         LandRegistryErrors.ASSET_ALREADY_EXISTS.toString());
        }

        // 3. RETIRE the Parent Asset (Enforcing Immutability)
        LandAsset retiredParent = new LandAsset(
                parentLand.getUlpin(),
                parentLand.getGpsCoordinates(),
                parentLand.getParentUlpin(),
                parentLand.getCurrentOwnerId(),
                newDocumentHash,
                "RETIRED_MUTATED" // <-- Status change
        );
        ctx.getStub().putStringState(parentUlpin, genson.serialize(retiredParent));

        // 4. MINT Child Asset 1
        LandAsset child1 = new LandAsset(
                child1Ulpin, child1Gps, parentUlpin, // <-- Lineage tied to parent
                currentOwnerId, newDocumentHash, "ACTIVE"
        );
        ctx.getStub().putStringState(child1Ulpin, genson.serialize(child1));

        // 5. MINT Child Asset 2
        LandAsset child2 = new LandAsset(
                child2Ulpin, child2Gps, parentUlpin, // <-- Lineage tied to parent
                currentOwnerId, newDocumentHash, "ACTIVE"
        );
        ctx.getStub().putStringState(child2Ulpin, genson.serialize(child2));

        return String.format("Successfully mutated %s into %s and %s", parentUlpin, child1Ulpin, child2Ulpin);
    }

    /**
     * Retrieves a Land Asset by its ULPIN.
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public LandAsset queryLandByUlpin(final Context ctx, final String ulpin) {
        String landJson = ctx.getStub().getStringState(ulpin);
        if (landJson == null || landJson.isEmpty()) {
            throw new ChaincodeException("Land Asset " + ulpin + " does not exist",
                                         LandRegistryErrors.ASSET_NOT_FOUND.toString());
        }
        return genson.deserialize(landJson, LandAsset.class);
    }

    /**
     * Optional but Highly Recommended for CouchDB: Query by Owner
     * Uses CouchDB rich JSON querying to find all land owned by a specific Aadhaar/ID.
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String queryLandByOwner(final Context ctx, final String ownerId) {
        String queryString = String.format("{\"selector\":{\"currentOwnerId\":\"%s\"}}", ownerId);
        
        // Use Fabric's rich query iterator
        QueryResultsIterator<KeyValue> results = ctx.getStub().getQueryResult(queryString);
        
        // Iterate and build a JSON array response
        StringBuilder response = new StringBuilder("[");
        for (KeyValue result : results) {
            if (response.length() > 1) response.append(",");
            response.append(result.getStringValue());
        }
        response.append("]");
        return response.toString();
    }

    /**
     * Helper method to check if a land asset exists in the world state.
     * @param ctx the transaction context
     * @param ulpin the Unique Land Parcel Identification Number
     * @return true if the asset exists, false otherwise
     */
    private boolean assetExists(final Context ctx, final String ulpin) {
        String landJson = ctx.getStub().getStringState(ulpin);
        return (landJson != null && !landJson.isEmpty());
    }
}