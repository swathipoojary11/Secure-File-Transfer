package com.dcn.securefiletransfer.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Integrity Check Audit Log Model.
 * 
 * Networking Viva Explanation:
 * Persists the result of the SHA-256 integrity verification handshake.
 * Proves mathematically whether any bit-level tampering, payload corruption,
 * or transmission errors occurred between the sender agent and receiver agent.
 */
public class IntegrityCheck {

    private String id;
    private String transferId;
    private String filename;
    private String originalHash;
    private String receivedHash;
    private String verificationResult; // VERIFIED, FAILED
    private String algorithm; // SHA-256
    private Instant verifiedAt;
    private String details;

    public IntegrityCheck() {
        this.id = UUID.randomUUID().toString();
        this.algorithm = "SHA-256";
        this.verifiedAt = Instant.now();
    }

    public IntegrityCheck(String transferId, String filename, String originalHash,
                          String receivedHash, String verificationResult, String details) {
        this();
        this.transferId = transferId;
        this.filename = filename;
        this.originalHash = originalHash;
        this.receivedHash = receivedHash;
        this.verificationResult = verificationResult;
        this.details = details;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTransferId() { return transferId; }
    public void setTransferId(String transferId) { this.transferId = transferId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getOriginalHash() { return originalHash; }
    public void setOriginalHash(String originalHash) { this.originalHash = originalHash; }

    public String getReceivedHash() { return receivedHash; }
    public void setReceivedHash(String receivedHash) { this.receivedHash = receivedHash; }

    public String getVerificationResult() { return verificationResult; }
    public void setVerificationResult(String verificationResult) { this.verificationResult = verificationResult; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
