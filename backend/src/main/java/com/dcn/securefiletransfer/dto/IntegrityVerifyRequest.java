package com.dcn.securefiletransfer.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Manual Integrity Verification Request DTO.
 */
public class IntegrityVerifyRequest {

    private String transferId;

    @NotBlank(message = "Original expected SHA-256 hash is required")
    private String originalHash;

    @NotBlank(message = "Received or test SHA-256 hash is required")
    private String receivedHash;

    private String filename;

    public IntegrityVerifyRequest() {}

    public String getTransferId() { return transferId; }
    public void setTransferId(String transferId) { this.transferId = transferId; }

    public String getOriginalHash() { return originalHash; }
    public void setOriginalHash(String originalHash) { this.originalHash = originalHash; }

    public String getReceivedHash() { return receivedHash; }
    public void setReceivedHash(String receivedHash) { this.receivedHash = receivedHash; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
}
