package com.dcn.securefiletransfer.dto;

import java.util.List;
import java.util.Map;

/**
 * Analytics Summary Response DTO.
 */
public class AnalyticsSummaryResponse {
    private int totalTransfers;
    private int successfulTransfers;
    private int failedTransfers;
    private int totalFilesTransferred;
    private long totalDataTransferredBytes;
    private String totalDataTransferredFormatted;
    private double averageTransferSpeedKbps;
    private int integrityVerificationFailures;
    private int activeDevicesCount;
    private int securityAlertsCount;
    private List<Map<String, Object>> recentTransfers;
    private List<Map<String, Object>> speedHistory;

    public AnalyticsSummaryResponse() {}

    public int getTotalTransfers() { return totalTransfers; }
    public void setTotalTransfers(int totalTransfers) { this.totalTransfers = totalTransfers; }

    public int getSuccessfulTransfers() { return successfulTransfers; }
    public void setSuccessfulTransfers(int successfulTransfers) { this.successfulTransfers = successfulTransfers; }

    public int getFailedTransfers() { return failedTransfers; }
    public void setFailedTransfers(int failedTransfers) { this.failedTransfers = failedTransfers; }

    public int getTotalFilesTransferred() { return totalFilesTransferred; }
    public void setTotalFilesTransferred(int totalFilesTransferred) { this.totalFilesTransferred = totalFilesTransferred; }

    public long getTotalDataTransferredBytes() { return totalDataTransferredBytes; }
    public void setTotalDataTransferredBytes(long totalDataTransferredBytes) { this.totalDataTransferredBytes = totalDataTransferredBytes; }

    public String getTotalDataTransferredFormatted() { return totalDataTransferredFormatted; }
    public void setTotalDataTransferredFormatted(String totalDataTransferredFormatted) { this.totalDataTransferredFormatted = totalDataTransferredFormatted; }

    public double getAverageTransferSpeedKbps() { return averageTransferSpeedKbps; }
    public void setAverageTransferSpeedKbps(double averageTransferSpeedKbps) { this.averageTransferSpeedKbps = averageTransferSpeedKbps; }

    public int getIntegrityVerificationFailures() { return integrityVerificationFailures; }
    public void setIntegrityVerificationFailures(int integrityVerificationFailures) { this.integrityVerificationFailures = integrityVerificationFailures; }

    public int getActiveDevicesCount() { return activeDevicesCount; }
    public void setActiveDevicesCount(int activeDevicesCount) { this.activeDevicesCount = activeDevicesCount; }

    public int getSecurityAlertsCount() { return securityAlertsCount; }
    public void setSecurityAlertsCount(int securityAlertsCount) { this.securityAlertsCount = securityAlertsCount; }

    public List<Map<String, Object>> getRecentTransfers() { return recentTransfers; }
    public void setRecentTransfers(List<Map<String, Object>> recentTransfers) { this.recentTransfers = recentTransfers; }

    public List<Map<String, Object>> getSpeedHistory() { return speedHistory; }
    public void setSpeedHistory(List<Map<String, Object>> speedHistory) { this.speedHistory = speedHistory; }
}
