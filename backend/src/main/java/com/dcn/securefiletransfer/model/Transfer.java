package com.dcn.securefiletransfer.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Transfer Entity representing a secure file transmission event.
 * 
 * Networking Viva Explanation:
 * Tracks the complete lifecycle of a file transfer session:
 * - Handshake initiation (pending)
 * - Active byte streaming over TLS 1.3 socket (in_progress)
 * - SHA-256 cryptographic verification
 * - Final state (completed, failed, cancelled)
 */
public class Transfer {

    private String id;
    private String senderId;
    private String senderName;
    private String receiverId;
    private String receiverName;
    private String receiverIp;
    private int receiverPort;
    private String filename;
    private long fileSize;
    private String transferStatus; // pending, in_progress, completed, failed, cancelled
    private String originalHash;
    private String receivedHash;
    private String integrityStatus; // pending, verified, failed
    private double transferSpeed; // in KB/s
    private long transferDurationMs;
    private boolean encryptedWithAes;
    private double progressPercent;
    private String downloadPath;
    private String errorMessage;
    private Instant createdAt;
    private Instant completedAt;

    public Transfer() {
        this.id = UUID.randomUUID().toString();
        this.transferStatus = "pending";
        this.integrityStatus = "pending";
        this.createdAt = Instant.now();
        this.progressPercent = 0.0;
        this.transferSpeed = 0.0;
        this.transferDurationMs = 0;
    }

    public Transfer(String senderId, String senderName, String receiverId, String receiverName,
                    String receiverIp, int receiverPort, String filename, long fileSize,
                    String originalHash, boolean encryptedWithAes) {
        this();
        this.senderId = senderId;
        this.senderName = senderName;
        this.receiverId = receiverId;
        this.receiverName = receiverName;
        this.receiverIp = receiverIp;
        this.receiverPort = receiverPort;
        this.filename = filename;
        this.fileSize = fileSize;
        this.originalHash = originalHash;
        this.encryptedWithAes = encryptedWithAes;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getReceiverIp() { return receiverIp; }
    public void setReceiverIp(String receiverIp) { this.receiverIp = receiverIp; }

    public int getReceiverPort() { return receiverPort; }
    public void setReceiverPort(int receiverPort) { this.receiverPort = receiverPort; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getTransferStatus() { return transferStatus; }
    public void setTransferStatus(String transferStatus) { this.transferStatus = transferStatus; }

    public String getOriginalHash() { return originalHash; }
    public void setOriginalHash(String originalHash) { this.originalHash = originalHash; }

    public String getReceivedHash() { return receivedHash; }
    public void setReceivedHash(String receivedHash) { this.receivedHash = receivedHash; }

    public String getIntegrityStatus() { return integrityStatus; }
    public void setIntegrityStatus(String integrityStatus) { this.integrityStatus = integrityStatus; }

    public double getTransferSpeed() { return transferSpeed; }
    public void setTransferSpeed(double transferSpeed) { this.transferSpeed = transferSpeed; }

    public long getTransferDurationMs() { return transferDurationMs; }
    public void setTransferDurationMs(long transferDurationMs) { this.transferDurationMs = transferDurationMs; }

    public boolean isEncryptedWithAes() { return encryptedWithAes; }
    public void setEncryptedWithAes(boolean encryptedWithAes) { this.encryptedWithAes = encryptedWithAes; }

    public double getProgressPercent() { return progressPercent; }
    public void setProgressPercent(double progressPercent) { this.progressPercent = progressPercent; }

    public String getDownloadPath() { return downloadPath; }
    public void setDownloadPath(String downloadPath) { this.downloadPath = downloadPath; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
