package com.dcn.securefiletransfer.controller;

import com.dcn.securefiletransfer.model.IntegrityCheck;
import com.dcn.securefiletransfer.security.crypto.SHA256Util;
import com.dcn.securefiletransfer.service.IntegrityService;
import com.dcn.securefiletransfer.service.SecurityEventService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controlled File Tampering Simulation Lab REST Controller.
 * 
 * Networking Viva Explanation:
 * Provides a controlled, isolated sandbox for academic demonstration:
 * 1. Stages a sample payload file.
 * 2. Computes the original SHA-256 cryptographic digest.
 * 3. Creates an isolated test copy.
 * 4. Modifies the test copy (e.g. flipping exactly 1 bit in byte 0, or appending bytes).
 * 5. Computes the new SHA-256 hash.
 * 6. Evaluates the Avalanche Effect (percentage of output bits altered).
 * 7. If tampering occurred, logs a CRITICAL security event in the audit trail.
 * 8. Never interferes with real user files or live network streams.
 */
@RestController
@RequestMapping("/api/tampering")
public class TamperingController {

    private final IntegrityService integrityService;
    private final SecurityEventService securityEventService;

    public TamperingController(IntegrityService integrityService, SecurityEventService securityEventService) {
        this.integrityService = integrityService;
        this.securityEventService = securityEventService;
    }

    @PostMapping(value = "/simulate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> simulateTamper(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "sampleText", required = false) String sampleText,
            @RequestParam(value = "tamperType", defaultValue = "SINGLE_BIT_FLIP") String tamperType) throws IOException {

        File sandboxDir = new File("./storage/temp/tamper_sandbox").getAbsoluteFile();
        if (!sandboxDir.exists()) sandboxDir.mkdirs();

        String simId = UUID.randomUUID().toString();
        File originalFile = new File(sandboxDir, "orig_" + simId + ".dat");
        File modifiedFile = new File(sandboxDir, "mod_" + simId + ".dat");

        try {
            // 1. Stage original payload
            if (file != null && !file.isEmpty()) {
                file.transferTo(originalFile);
            } else {
                String text = (sampleText != null && !sampleText.isBlank()) 
                        ? sampleText 
                        : "DCN MINI-PROJECT: Cryptographic Integrity Verification & Controlled Tampering Simulation Lab.";
                Files.writeString(originalFile.toPath(), text, StandardCharsets.UTF_8);
            }

            // 2. Compute Original SHA-256
            String originalHash = SHA256Util.calculateFileHash(originalFile);

            // 3. Create Clone for Modification
            Files.copy(originalFile.toPath(), modifiedFile.toPath());

            int bytesModified = 0;
            String modificationSummary = "No modification (Control Case A: Unaltered Transmission)";

            // 4. Apply Controlled Modification
            if ("SINGLE_BIT_FLIP".equalsIgnoreCase(tamperType)) {
                byte[] data = Files.readAllBytes(modifiedFile.toPath());
                if (data.length > 0) {
                    // Flip the least significant bit of the first byte (XOR with 0x01)
                    data[0] = (byte) (data[0] ^ 0x01);
                    Files.write(modifiedFile.toPath(), data);
                    bytesModified = 1;
                    modificationSummary = "Bit-level modification: Flipped bit 0 of byte 0 in file header.";
                }
            } else if ("BYTE_INJECTION".equalsIgnoreCase(tamperType)) {
                try (FileOutputStream fos = new FileOutputStream(modifiedFile, true)) {
                    fos.write(" [MALICIOUS_PAYLOAD_INJECTED]".getBytes(StandardCharsets.UTF_8));
                }
                bytesModified = 28;
                modificationSummary = "Payload injection: Appended 28 unauthorized bytes to file stream.";
            } else if ("BYTE_TRUNCATION".equalsIgnoreCase(tamperType)) {
                byte[] data = Files.readAllBytes(modifiedFile.toPath());
                if (data.length > 2) {
                    byte[] truncated = new byte[data.length - 2];
                    System.arraycopy(data, 0, truncated, 0, truncated.length);
                    Files.write(modifiedFile.toPath(), truncated);
                    bytesModified = 2;
                    modificationSummary = "Truncation tamper: Stripped final 2 bytes from payload tail.";
                }
            }

            // 5. Compute Modified SHA-256
            String modifiedHash = SHA256Util.calculateFileHash(modifiedFile);

            // 6. Compare Hashes in Constant Time
            boolean matched = SHA256Util.verifyHash(originalHash, modifiedHash);
            String status = matched ? "VERIFIED" : "FAILED";

            // 7. Calculate Avalanche Effect (Hamming distance of the 256 bits)
            double avalanchePct = calculateAvalancheEffect(originalHash, modifiedHash);

            // 8. Record in Integrity Audit
            String filename = file != null ? file.getOriginalFilename() : "simulated_payload.dat";
            IntegrityCheck check = integrityService.recordCheck(
                    "sim-" + simId, filename, originalHash, modifiedHash, status,
                    matched ? "Simulation Case A: Hashes identical. File accepted."
                            : "Simulation Case B: Tampering detected! " + modificationSummary
            );

            // 9. Emit Security Event if Tampered
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userId = (auth != null && auth.getPrincipal() != null) ? auth.getPrincipal().toString() : "operator";

            if (!matched) {
                securityEventService.logEvent(
                        userId, "CONTROLLED_TAMPER_SIMULATION", "CRITICAL",
                        "Tampering Lab Detection for " + filename + ": " + modificationSummary + " (" + String.format("%.1f", avalanchePct) + "% bits diverged)",
                        "127.0.0.1"
                );
            }

            // 10. Return Structured Response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("simulationId", simId);
            response.put("filename", filename);
            response.put("tamperType", tamperType);
            response.put("originalHash", originalHash);
            response.put("modifiedHash", modifiedHash);
            response.put("match", matched);
            response.put("integrityStatus", status);
            response.put("bytesModified", bytesModified);
            response.put("modificationSummary", modificationSummary);
            response.put("avalancheEffectPercentage", Math.round(avalanchePct * 10.0) / 10.0);
            response.put("timestamp", Instant.now().toString());

            return ResponseEntity.ok(response);

        } finally {
            // Clean up sandbox files immediately
            if (originalFile.exists()) originalFile.delete();
            if (modifiedFile.exists()) modifiedFile.delete();
        }
    }

    /**
     * Calculates the percentage of bits that changed between two 64-character hexadecimal SHA-256 hashes.
     * Demonstrates the FIPS 180-4 cryptographic Avalanche Effect (~50% bit flip).
     */
    private double calculateAvalancheEffect(String hash1, String hash2) {
        if (hash1 == null || hash2 == null || hash1.length() != 64 || hash2.length() != 64) {
            return 0.0;
        }
        int differingBits = 0;
        for (int i = 0; i < 64; i += 2) {
            int b1 = Integer.parseInt(hash1.substring(i, i + 2), 16);
            int b2 = Integer.parseInt(hash2.substring(i, i + 2), 16);
            differingBits += Integer.bitCount(b1 ^ b2);
        }
        // 256 total bits
        return ((double) differingBits / 256.0) * 100.0;
    }
}
