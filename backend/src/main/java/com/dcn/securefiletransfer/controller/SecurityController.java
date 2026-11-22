package com.dcn.securefiletransfer.controller;

import com.dcn.securefiletransfer.dto.SecurityStatusResponse;
import com.dcn.securefiletransfer.model.SecurityEvent;
import com.dcn.securefiletransfer.service.IntegrityService;
import com.dcn.securefiletransfer.service.SecurityEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Security Center REST Controller.
 * 
 * Networking Viva Explanation:
 * Surfaces cybersecurity telemetry:
 * - Active TLS protocol version and authenticated cipher suites.
 * - Audit logs of security events (tampering attempts, TLS handshake rejections, etc.).
 * - System security posture evaluation.
 */
@RestController
@RequestMapping("/api/security")
public class SecurityController {

    private final SecurityEventService securityEventService;
    private final IntegrityService integrityService;

    public SecurityController(SecurityEventService securityEventService, IntegrityService integrityService) {
        this.securityEventService = securityEventService;
        this.integrityService = integrityService;
    }

    /**
     * Lists security audit events, optionally filtered by severity (LOW, MEDIUM, HIGH, CRITICAL).
     */
    @GetMapping("/events")
    public ResponseEntity<List<SecurityEvent>> listEvents(
            @RequestParam(value = "severity", required = false) String severity) {
        return ResponseEntity.ok(securityEventService.getEvents(severity));
    }

    /**
     * Returns overall system security posture and TLS cryptographic status.
     */
    @GetMapping("/status")
    public ResponseEntity<SecurityStatusResponse> getSecurityStatus() {
        int failures = integrityService.getFailureCount();
        return ResponseEntity.ok(securityEventService.getSecurityStatus(failures));
    }
}
