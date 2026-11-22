package com.dcn.securefiletransfer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Transfer Initiation DTO.
 */
public class TransferInitiateRequest {

    @NotBlank(message = "Recipient IP address is required")
    private String recipientIp;

    @Min(value = 1024, message = "Port must be >= 1024")
    @Max(value = 65535, message = "Port must be <= 65535")
    private int recipientPort = 8443;

    private String recipientId;
    private String recipientName;
    private boolean encryptWithAes = false;

    public String getRecipientIp() { return recipientIp; }
    public void setRecipientIp(String recipientIp) { this.recipientIp = recipientIp; }

    public int getRecipientPort() { return recipientPort; }
    public void setRecipientPort(int recipientPort) { this.recipientPort = recipientPort; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public boolean isEncryptWithAes() { return encryptWithAes; }
    public void setEncryptWithAes(boolean encryptWithAes) { this.encryptWithAes = encryptWithAes; }
}
