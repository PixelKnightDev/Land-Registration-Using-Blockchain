package org.landregistry;

import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Network;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class LandRegistryService {

    private final Contract contract;

    @Autowired
    public LandRegistryService(Gateway gateway) {
        // "landchannel" is the exact channel name generated in your deployment script
        Network network = gateway.getNetwork("landchannel");
        
        // "landregistry" is the chaincode name installed on the peers
        this.contract = network.getContract("landregistry");
    }

    // --- WRITE OPERATIONS (Requires Consensus) ---

    public String createLandAsset(String ulpin, String gpsCoordinates, String parentUlpin, String currentOwnerId, String documentHash) throws Exception {
        byte[] result = contract.submitTransaction("createLandAsset", ulpin, gpsCoordinates, parentUlpin, currentOwnerId, documentHash);
        return new String(result, StandardCharsets.UTF_8);
    }

    public String transferOwnership(String ulpin, String newOwnerId) throws Exception {
        byte[] result = contract.submitTransaction("transferOwnership", ulpin, newOwnerId);
        return new String(result, StandardCharsets.UTF_8);
    }

    public String mutateLand(String originalUlpin, String newUlpin1, String newUlpin2, String newDimensions1, String newDimensions2) throws Exception {
        byte[] result = contract.submitTransaction("mutateLand", originalUlpin, newUlpin1, newUlpin2, newDimensions1, newDimensions2);
        return new String(result, StandardCharsets.UTF_8);
    }

    // --- READ OPERATIONS (Fast Local Queries) ---

    public String readLandAsset(String ulpin) throws Exception {
        byte[] result = contract.evaluateTransaction("readLandAsset", ulpin);
        return new String(result, StandardCharsets.UTF_8);
    }

    public String queryLandByOwner(String ownerId) throws Exception {
        byte[] result = contract.evaluateTransaction("queryLandByOwner", ownerId);
        return new String(result, StandardCharsets.UTF_8);
    }
}