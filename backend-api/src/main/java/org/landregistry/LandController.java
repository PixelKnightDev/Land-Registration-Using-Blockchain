package org.landregistry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/land")
@CrossOrigin(origins = "*") // Allows your future frontend UI to call this API locally
public class LandController {

    private final LandRegistryService landRegistryService;

    @Autowired
    public LandController(LandRegistryService landRegistryService) {
        this.landRegistryService = landRegistryService;
    }

    // --- Data Transfer Objects (DTOs) for JSON payloads ---
    public record CreateLandRequest(String ulpin, String gpsCoordinates, String parentUlpin, String currentOwnerId, String documentHash) {}
    public record TransferRequest(String newOwnerId) {}
    public record MutateRequest(String newUlpin1, String newUlpin2, String newDimensions1, String newDimensions2) {}

    // --- ENDPOINTS ---

    // 1. Register a new property
    // POST http://localhost:8080/api/land
    @PostMapping
    public ResponseEntity<String> createLandAsset(@RequestBody CreateLandRequest request) {
        try {
            String result = landRegistryService.createLandAsset(
                    request.ulpin(), 
                    request.gpsCoordinates(), 
                    request.parentUlpin(), 
                    request.currentOwnerId(), 
                    request.documentHash()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    // 2. Read a specific property by ULPIN
    // GET http://localhost:8080/api/land/{ulpin}
    @GetMapping("/{ulpin}")
    public ResponseEntity<String> readLandAsset(@PathVariable String ulpin) {
        try {
            String result = landRegistryService.readLandAsset(ulpin);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: " + e.getMessage());
        }
    }

    // 3. Transfer property ownership
    // PUT http://localhost:8080/api/land/{ulpin}/transfer
    @PutMapping("/{ulpin}/transfer")
    public ResponseEntity<String> transferOwnership(@PathVariable String ulpin, @RequestBody TransferRequest request) {
        try {
            String result = landRegistryService.transferOwnership(ulpin, request.newOwnerId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    // 4. Split/Mutate a property
    // POST http://localhost:8080/api/land/{ulpin}/mutate
    @PostMapping("/{ulpin}/mutate")
    public ResponseEntity<String> mutateLand(@PathVariable String ulpin, @RequestBody MutateRequest request) {
        try {
            String result = landRegistryService.mutateLand(
                    ulpin, request.newUlpin1(), request.newUlpin2(), request.newDimensions1(), request.newDimensions2());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    // 5. Find all properties owned by a specific person
    // GET http://localhost:8080/api/land/owner/{ownerId}
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<String> queryLandByOwner(@PathVariable String ownerId) {
        try {
            String result = landRegistryService.queryLandByOwner(ownerId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }
}