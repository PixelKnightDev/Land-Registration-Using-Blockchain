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
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyModification;

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
        INVALID_INPUT,        // Added for basic input validation
        QUERY_FAILED // <-- FIXED: Added this missing enum value
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
     * @param parentUlpin Lineage tracking (use null/blank for root assets)
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
         // parentUlpin: allow null/blank to represent a root asset.
         // Also accept legacy literal "NONE" (case-insensitive) and normalize it to null.
        if (currentOwnerId == null || currentOwnerId.trim().isEmpty()) {
            throw new ChaincodeException("Current owner ID must not be null or empty", 
                                         LandRegistryErrors.INVALID_INPUT.toString());
        }
        if (documentHash == null || documentHash.trim().isEmpty()) {
            throw new ChaincodeException("Document hash must not be null or empty", 
                                         LandRegistryErrors.INVALID_INPUT.toString());
        }
         // Normalize ULPIN for consistent keying and identity
         final String normalizedUlpin = ulpin.trim();

        // Validation: Ensure the ULPIN doesn't already exist
          if (assetExists(ctx, normalizedUlpin)) {
             throw new ChaincodeException("Land Asset with ULPIN " + normalizedUlpin + " already exists", 
                                         LandRegistryErrors.ASSET_ALREADY_EXISTS.toString());
        }

        // Normalize parent ULPIN: treat null/blank/"NONE" as a root asset (null in the domain model)
         String normalizedParentUlpin = null;
         if (parentUlpin != null) {
             String trimmedParent = parentUlpin.trim();
             if (!trimmedParent.isEmpty() && !"NONE".equalsIgnoreCase(trimmedParent)) {
                 normalizedParentUlpin = trimmedParent;
             }
         }

        // Creation: Instantiate the immutable Java object
        LandAsset land = new LandAsset(normalizedUlpin, gpsCoordinates, normalizedParentUlpin, currentOwnerId, documentHash, "ACTIVE");
        // Persistence: Convert to JSON and save to the CouchDB world state
        ctx.getStub().putStringState(normalizedUlpin, genson.serialize(land));

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
         // Input Validation: Ensure ulpin, sellerId, buyerId and newDocumentHash are not null or blank
          if (ulpin == null || ulpin.trim().isEmpty()) {
              throw new ChaincodeException("Transaction Rejected: ulpin must not be null or blank",
                                           LandRegistryErrors.INVALID_INPUT.toString());
          }
          if (sellerId == null || sellerId.trim().isEmpty()) {
              throw new ChaincodeException("Transaction Rejected: sellerId must not be null or blank",
                                           LandRegistryErrors.INVALID_INPUT.toString());
          }
         if (buyerId == null || buyerId.trim().isEmpty()) {
             throw new ChaincodeException("Transaction Rejected: buyerId must not be null or blank",
                                          LandRegistryErrors.INVALID_INPUT.toString());
         }
         if (newDocumentHash == null || newDocumentHash.trim().isEmpty()) {
             throw new ChaincodeException("Transaction Rejected: newDocumentHash must not be null or blank",
                                          LandRegistryErrors.INVALID_INPUT.toString());
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

    // Helper method to validate that a string is neither null nor blank.
    private boolean isNullOrBlank(final String value) {
        return value == null || value.trim().isEmpty();
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

         // 0. Basic input validation to prevent null/blank values from reaching ledger APIs
         if (isNullOrBlank(parentUlpin)
                 || isNullOrBlank(currentOwnerId)
                 || isNullOrBlank(child1Ulpin)
                 || isNullOrBlank(child1Gps)
                 || isNullOrBlank(child2Ulpin)
                 || isNullOrBlank(child2Gps)
                 || isNullOrBlank(newDocumentHash)) {
             throw new ChaincodeException(
                     "Mutation Rejected: Input parameters must not be null or empty",
                     LandRegistryErrors.INVALID_INPUT.toString());
         }

         // Normalize ULPINs to ensure consistent world-state keys and comparisons
         final String normalizedParentUlpin = parentUlpin != null ? parentUlpin.trim() : null;
         final String normalizedChild1Ulpin = child1Ulpin != null ? child1Ulpin.trim() : null;
         final String normalizedChild2Ulpin = child2Ulpin != null ? child2Ulpin.trim() : null;

        // 1. Fetch and Validate Parent Asset
        String parentJson = ctx.getStub().getStringState(normalizedParentUlpin);
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

                // Safety Check: Ensure all ULPINs involved in mutation are distinct
         if (normalizedParentUlpin.equals(normalizedChild1Ulpin)
                 || normalizedParentUlpin.equals(normalizedChild2Ulpin)
                 || normalizedChild1Ulpin.equals(normalizedChild2Ulpin)) {
             throw new ChaincodeException("Mutation Rejected: Parent and child ULPINs must all be distinct",
                                          LandRegistryErrors.INVALID_INPUT.toString());
         }
         // Optional Safety Check: Prevent obviously invalid GPS collisions (if identical boundaries are disallowed)
         if (child1Gps.equals(child2Gps)
                 || child1Gps.equals(parentLand.getGpsCoordinates())
                 || child2Gps.equals(parentLand.getGpsCoordinates())) {
             throw new ChaincodeException("Mutation Rejected: Child GPS coordinates must differ from each other and from the parent",
                                          LandRegistryErrors.INVALID_INPUT.toString());
         }


        // Safety Check: Ensure new ULPINs don't clash with existing ones
        if (assetExists(ctx, normalizedChild1Ulpin) || assetExists(ctx, normalizedChild2Ulpin)) {
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
        if (ulpin == null || ulpin.trim().isEmpty()) {
            throw new ChaincodeException(
                    "Invalid ulpin: value must be non-null and non-blank",
                    LandRegistryErrors.INVALID_INPUT.toString());
        }
        // Normalize ULPIN once and use the trimmed value for state lookup and error messages
        String normalizedUlpin = ulpin.trim();
        String landJson = ctx.getStub().getStringState(normalizedUlpin);
        if (landJson == null || landJson.isEmpty()) {
            throw new ChaincodeException("Land Asset " + normalizedUlpin + " does not exist",
                                         LandRegistryErrors.ASSET_NOT_FOUND.toString());
        }
        return genson.deserialize(landJson, LandAsset.class);
    }

    /**
     * Optional but Highly Recommended for CouchDB: Query by Owner
     * Uses CouchDB rich JSON querying to find all land owned by a specific Aadhaar/ID.
      * <p>Performance note (CouchDB only): this method relies on a rich query filter on the
      * {@code currentOwnerId} field. For large ledgers, you <strong>must</strong> provide a
      * CouchDB index on {@code currentOwnerId} (for example, by packaging an index JSON file
      * such as {@code META-INF/statedb/couchdb/indexes/land_indexCurrentOwnerId.json} that
      * defines an index on {@code currentOwnerId}). Without such an index, CouchDB may need
      * to perform a full scan of the state database and this query can become very slow.</p>
      *
      * <p>If you do not package this index with the chaincode, ensure that your network
      * operators create and maintain an appropriate index on {@code currentOwnerId} in the
      * state database.</p>
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String queryLandByOwner(final Context ctx, final String ownerId) {
         if (ownerId == null) {
             throw new ChaincodeException(
                     "Invalid ownerId: value must be non-null and non-blank",
                     LandRegistryErrors.INVALID_INPUT.toString());
         }

         String normalizedOwnerId = ownerId.trim();
         if (normalizedOwnerId.isEmpty()) {
             throw new ChaincodeException(
                     "Invalid ownerId: value must be non-null and non-blank",
                     LandRegistryErrors.INVALID_INPUT.toString());
         }

         Map<String, Object> selector = new HashMap<>();
         selector.put("currentOwnerId", normalizedOwnerId);
         Map<String, Object> query = new HashMap<>();
         query.put("selector", selector);
         String queryString = genson.serialize(query);
        StringBuilder response = new StringBuilder("[");
        
         // Use Fabric's rich query iterator. For CouchDB, ensure an index exists on "currentOwnerId"
         // (see JavaDoc for queryLandByOwner) to avoid full collection scans on large ledgers.
        try (QueryResultsIterator<KeyValue> results = ctx.getStub().getQueryResult(queryString)) {
            // Iterate and build a JSON array response
            for (KeyValue result : results) {
                if (response.length() > 1) {
                    response.append(",");
                }
                response.append(result.getStringValue());
            }
        } catch (Exception e) {
            // Catch and log the exception thrown during rich query execution or implicit close()
             Logger.getLogger(LandRegistryContract.class.getName())
                     .log(Level.SEVERE, "Failed to execute rich query for owner: " + normalizedOwnerId, e);
             throw new ChaincodeException("Failed to execute rich query for owner: " + normalizedOwnerId,

                      LandRegistryErrors.QUERY_FAILED.toString());     
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

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getAssetHistory(final Context ctx, final String ulpin) {
        ChaincodeStub stub = ctx.getStub();
        
        // Use the native Fabric API to get the history for a specific key
        QueryResultsIterator<KeyModification> history = stub.getHistoryForKey(ulpin);

        List<String> historyList = new ArrayList<>();
        
        // Loop through the history and build a JSON response
        for (KeyModification modification : history) {
            String record = String.format("{\"txId\":\"%s\", \"value\":%s, \"timestamp\":\"%s\", \"isDeleted\":%b}",
                    modification.getTxId(),
                    modification.getStringValue(),
                    modification.getTimestamp().toString(),
                    modification.isDeleted());
            historyList.add(record);
        }

        return "[" + String.join(",", historyList) + "]";
    }
}