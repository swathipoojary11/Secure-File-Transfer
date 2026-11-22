package com.dcn.securefiletransfer.dto;

public class TransferStatusResponse {
    private String id;
    private String filename;
    private String transferStatus;
    private double progressPercent;
    private double transferSpeedKbps;
    private long transferDurationMs;
    private String integrityStatus;
    private String originalHash;
    private String receivedHash;
    private String errorMessage;

    public TransferStatusResponse() {}

    public TransferStatusResponse(String id, String filename, String transferStatus, double progressPercent,
                                  double transferSpeedKbps, long transferDurationMs, String integrityStatus,
                                  String originalHash, String receivedHash, String errorMessage) {
        this.id = id;
        this.filename = filename;
        this.transferStatus = transferStatus;
        this.progressPercent = progressPercent;
        this.transferSpeedKbps = transferSpeedKbps;
        this.transferDurationMs = transferDurationMs;
        this.integrityStatus = integrityStatus;
        this.originalHash = originalHash;
        this.receivedHash = receivedHash;
        this.errorMessage = errorMessage;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getTransferStatus() { return transferStatus; }
    public void setTransferStatus(String transferStatus) { this.transferStatus = transferStatus; }

    public double getProgressPercent() { return progressPercent; }
    public void setProgressPercent(double progressPercent) { this.progressPercent = progressPercent; }

    public double getTransferSpeedKbps() { return transferSpeedKbps; }
    public void setTransferSpeedKbps(double transferSpeedKbps) { this.transferSpeedKbps = transferSpeedKbps; }

    public long getTransferDurationMs() { return transferDurationMs; }
    public void setTransferDurationMs(long transferDurationMs) { this.transferDurationMs = transferDurationMs; }

    public String getIntegrityStatus() { return integrityStatus; }
    public void setIntegrityStatus(String integrityStatus) { this.integrityStatus = integrityStatus; }

    public String getOriginalHash() { return originalHash; }
    public void setOriginalHash(String originalHash) { this.originalHash = originalHash; }

    public String getReceivedHash() { return receivedHash; }
    public void setReceivedHash(String receivedHash) { this.receivedHash = receivedHash; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
