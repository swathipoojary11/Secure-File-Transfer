package com.dcn.securefiletransfer.service;

import com.dcn.securefiletransfer.dto.AnalyticsSummaryResponse;
import com.dcn.securefiletransfer.model.Transfer;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Transfer Management Service.
 * 
 * Networking Viva Explanation:
 * - Manages session state for file transfers across the network.
 * - Records performance metrics: byte counts, transfer speed (KB/s), latency,
 *   integrity verification outcomes, and cryptographic status.
 */
@Service
public class TransferService {

    private final Map<String, Transfer> transferStore = new ConcurrentHashMap<>();

    public TransferService() {
        // Seed initial transfer record for immediate demo visibility
        Transfer demo = new Transfer(
                "system-sender", "Laptop-A (Sender)",
                "system-receiver", "Laptop-B (Receiver)",
                "192.168.29.125", 8443,
                "dcn_lab_report_v1.pdf", 1048576,
                "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae", false
        );
        demo.setTransferStatus("completed");
        demo.setIntegrityStatus("verified");
        demo.setReceivedHash("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
        demo.setTransferSpeed(12450.5);
        demo.setTransferDurationMs(84);
        demo.setProgressPercent(100.0);
        demo.setCompletedAt(Instant.now());
        transferStore.put(demo.getId(), demo);
    }

    public Transfer saveTransfer(Transfer transfer) {
        transferStore.put(transfer.getId(), transfer);
        return transfer;
    }

    public Optional<Transfer> getTransferById(String id) {
        return Optional.ofNullable(transferStore.get(id));
    }

    /**
     * Lists transfers with search filtering, status filtering, and sorting.
     */
    public List<Transfer> listTransfers(String userId, String search, String status, String sort) {
        List<Transfer> list = new ArrayList<>(transferStore.values());

        // Optional search filter (by filename or recipient/sender name)
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase().trim();
            list = list.stream()
                    .filter(t -> (t.getFilename() != null && t.getFilename().toLowerCase().contains(q))
                            || (t.getReceiverName() != null && t.getReceiverName().toLowerCase().contains(q))
                            || (t.getSenderName() != null && t.getSenderName().toLowerCase().contains(q)))
                    .collect(Collectors.toList());
        }

        // Optional status filter
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            list = list.stream()
                    .filter(t -> status.equalsIgnoreCase(t.getTransferStatus()))
                    .collect(Collectors.toList());
        }

        // Sort by creation time (default DESC)
        boolean ascending = "ASC".equalsIgnoreCase(sort);
        list.sort((a, b) -> {
            int cmp = a.getCreatedAt().compareTo(b.getCreatedAt());
            return ascending ? cmp : -cmp;
        });

        return list;
    }

    /**
     * Cancels an ongoing transfer.
     */
    public boolean cancelTransfer(String id) {
        Transfer t = transferStore.get(id);
        if (t != null && "in_progress".equalsIgnoreCase(t.getTransferStatus())) {
            t.setTransferStatus("cancelled");
            t.setErrorMessage("Transfer aborted by user request.");
            return true;
        }
        return false;
    }

    /**
     * Computes real-time analytics aggregation for the Recharts dashboard.
     */
    public AnalyticsSummaryResponse getAnalyticsSummary(int activeDevices, int integrityFailures, int securityAlerts) {
        AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse();

        int total = transferStore.size();
        int completed = 0;
        int failed = 0;
        long totalBytes = 0;
        double speedSum = 0;
        int speedCount = 0;

        List<Map<String, Object>> speedHistory = new ArrayList<>();

        for (Transfer t : transferStore.values()) {
            if ("completed".equalsIgnoreCase(t.getTransferStatus())) {
                completed++;
                totalBytes += t.getFileSize();
                if (t.getTransferSpeed() > 0) {
                    speedSum += t.getTransferSpeed();
                    speedCount++;
                }
            } else if ("failed".equalsIgnoreCase(t.getTransferStatus())) {
                failed++;
            }

            Map<String, Object> point = new HashMap<>();
            point.put("timestamp", t.getCreatedAt().toString());
            point.put("filename", t.getFilename());
            point.put("speedKbps", t.getTransferSpeed());
            point.put("status", t.getTransferStatus());
            speedHistory.add(point);
        }

        summary.setTotalTransfers(total);
        summary.setSuccessfulTransfers(completed);
        summary.setFailedTransfers(failed);
        summary.setTotalFilesTransferred(completed);
        summary.setTotalDataTransferredBytes(totalBytes);
        summary.setTotalDataTransferredFormatted(formatBytes(totalBytes));
        summary.setAverageTransferSpeedKbps(speedCount > 0 ? (speedSum / speedCount) : 0.0);
        summary.setIntegrityVerificationFailures(integrityFailures);
        summary.setActiveDevicesCount(activeDevices);
        summary.setSecurityAlertsCount(securityAlerts);
        summary.setSpeedHistory(speedHistory);

        // Recent 5 transfers
        List<Map<String, Object>> recent = transferStore.values().stream()
                .sorted(Comparator.comparing(Transfer::getCreatedAt).reversed())
                .limit(5)
                .map(t -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", t.getId());
                    map.put("filename", t.getFilename());
                    map.put("fileSize", t.getFileSize());
                    map.put("status", t.getTransferStatus());
                    map.put("integrity", t.getIntegrityStatus());
                    map.put("speed", t.getTransferSpeed());
                    map.put("createdAt", t.getCreatedAt().toString());
                    return map;
                })
                .collect(Collectors.toList());
        summary.setRecentTransfers(recent);

        return summary;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return new DecimalFormat("#.##").format(bytes / Math.pow(1024, exp)) + " " + pre + "B";
    }
}
