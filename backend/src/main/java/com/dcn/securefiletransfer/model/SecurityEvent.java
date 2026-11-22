package com.dcn.securefiletransfer.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Security Event Model.
 * 
 * Networking Viva Explanation:
 * Logs all cybersecurity-relevant events:
 * - Hash mismatch / Integrity tampering detection (CRITICAL)
 * - TLS handshake certificate validation failure (HIGH)
 * - Unauthorized access attempts (MEDIUM)
 * - Node connection / authentication events (LOW/INFO)
 */
public class SecurityEvent {

    private String id;
    private String userId;
    private String eventType;
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL
    private String description;
    private String sourceIp;
    private Instant createdAt;

    public SecurityEvent() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }

    public SecurityEvent(String userId, String eventType, String severity, String description, String sourceIp) {
        this();
        this.userId = userId;
        this.eventType = eventType;
        this.severity = severity;
        this.description = description;
        this.sourceIp = sourceIp;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
