package com.dcn.securefiletransfer.service;

import com.dcn.securefiletransfer.dto.SecurityStatusResponse;
import com.dcn.securefiletransfer.model.SecurityEvent;
import com.dcn.securefiletransfer.security.tls.TlsContextFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Security Monitoring Service for logging, tracking, and auditing cybersecurity events.
 */
@Service
public class SecurityEventService {

    private final List<SecurityEvent> eventLog = new CopyOnWriteArrayList<>();

    @Value("${tcp.transfer.port:8443}")
    private String listeningPort;

    public SecurityEventService() {
        // Seed baseline security startup audit event
        eventLog.add(new SecurityEvent("system", "SYSTEM_STARTUP", "LOW",
                "Secure File Transfer Agent initialized with TLS 1.3 cryptographic socket listener", "127.0.0.1"));
    }

    public SecurityEvent logEvent(String userId, String eventType, String severity, String description, String sourceIp) {
        SecurityEvent event = new SecurityEvent(userId, eventType, severity, description, sourceIp);
        eventLog.add(0, event); // Insert at beginning (most recent first)
        System.out.println("[SECURITY EVENT] [" + severity + "] " + eventType + ": " + description);
        return event;
    }

    public List<SecurityEvent> getEvents(String severity) {
        if (severity == null || severity.isBlank() || "ALL".equalsIgnoreCase(severity)) {
            return new ArrayList<>(eventLog);
        }
        return eventLog.stream()
                .filter(e -> severity.equalsIgnoreCase(e.getSeverity()))
                .collect(Collectors.toList());
    }

    public SecurityStatusResponse getSecurityStatus(int integrityMismatchesCount) {
        SecurityStatusResponse response = new SecurityStatusResponse();
        response.setTlsVersion(TlsContextFactory.TLS_PROTOCOL_VERSION);
        response.setSupportedCiphers(Arrays.asList(TlsContextFactory.SUPPORTED_CIPHER_SUITES));
        response.setActiveCipherSuite(TlsContextFactory.SUPPORTED_CIPHER_SUITES[0]);
        response.setLocalListeningPort(listeningPort);

        int total = eventLog.size();
        int critical = (int) eventLog.stream().filter(e -> "CRITICAL".equalsIgnoreCase(e.getSeverity())).count();
        int high = (int) eventLog.stream().filter(e -> "HIGH".equalsIgnoreCase(e.getSeverity())).count();
        int tlsFailures = (int) eventLog.stream().filter(e -> "TLS_HANDSHAKE_FAILURE".equalsIgnoreCase(e.getEventType())).count();

        response.setTotalSecurityEvents(total);
        response.setCriticalViolations(critical);
        response.setHighSeverityEvents(high);
        response.setIntegrityMismatches(integrityMismatchesCount);
        response.setTlsHandshakeFailures(tlsFailures);

        if (critical > 0 || integrityMismatchesCount > 0) {
            response.setSecurityPosture("ELEVATED_RISK");
        } else {
            response.setSecurityPosture("SECURE");
        }

        return response;
    }
}
