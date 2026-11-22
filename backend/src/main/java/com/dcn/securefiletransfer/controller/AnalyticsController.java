package com.dcn.securefiletransfer.controller;

import com.dcn.securefiletransfer.dto.AnalyticsSummaryResponse;
import com.dcn.securefiletransfer.service.DeviceService;
import com.dcn.securefiletransfer.service.IntegrityService;
import com.dcn.securefiletransfer.service.SecurityEventService;
import com.dcn.securefiletransfer.service.TransferService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analytics Dashboard REST Controller.
 * 
 * Networking Viva Explanation:
 * Aggregates network transfer metrics for visualization with Recharts:
 * - Bandwidth / Data throughput metrics.
 * - Success vs failure ratios.
 * - Latency and transfer speeds.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final TransferService transferService;
    private final DeviceService deviceService;
    private final IntegrityService integrityService;
    private final SecurityEventService securityEventService;

    public AnalyticsController(TransferService transferService, DeviceService deviceService,
                               IntegrityService integrityService, SecurityEventService securityEventService) {
        this.transferService = transferService;
        this.deviceService = deviceService;
        this.integrityService = integrityService;
        this.securityEventService = securityEventService;
    }

    /**
     * Returns the aggregate summary metrics for dashboard KPI cards.
     */
    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryResponse> getSummary() {
        int activeDevices = deviceService.getAllDevices().size();
        int integrityFailures = integrityService.getFailureCount();
        int securityAlerts = securityEventService.getEvents("CRITICAL").size() + securityEventService.getEvents("HIGH").size();

        AnalyticsSummaryResponse summary = transferService.getAnalyticsSummary(activeDevices, integrityFailures, securityAlerts);
        return ResponseEntity.ok(summary);
    }

    /**
     * Returns categorized transfer metrics for charts.
     */
    @GetMapping("/transfers")
    public ResponseEntity<Map<String, Object>> getTransferMetrics() {
        Map<String, Object> data = new HashMap<>();
        data.put("statusBreakdown", List.of(
                Map.of("name", "Completed", "value", transferService.listTransfers(null, null, "completed", "DESC").size()),
                Map.of("name", "In Progress", "value", transferService.listTransfers(null, null, "in_progress", "DESC").size()),
                Map.of("name", "Failed", "value", transferService.listTransfers(null, null, "failed", "DESC").size()),
                Map.of("name", "Cancelled", "value", transferService.listTransfers(null, null, "cancelled", "DESC").size())
        ));
        return ResponseEntity.ok(data);
    }

    /**
     * Returns security severity distributions for charts.
     */
    @GetMapping("/security")
    public ResponseEntity<Map<String, Object>> getSecurityMetrics() {
        Map<String, Object> data = new HashMap<>();
        data.put("severityBreakdown", List.of(
                Map.of("severity", "LOW", "count", securityEventService.getEvents("LOW").size()),
                Map.of("severity", "MEDIUM", "count", securityEventService.getEvents("MEDIUM").size()),
                Map.of("severity", "HIGH", "count", securityEventService.getEvents("HIGH").size()),
                Map.of("severity", "CRITICAL", "count", securityEventService.getEvents("CRITICAL").size())
        ));
        return ResponseEntity.ok(data);
    }
}
