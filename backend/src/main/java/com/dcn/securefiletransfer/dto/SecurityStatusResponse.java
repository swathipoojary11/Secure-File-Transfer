package com.dcn.securefiletransfer.dto;

import java.util.List;

/**
 * Security Status Response DTO.
 */
public class SecurityStatusResponse {
    private String tlsVersion;
    private List<String> supportedCiphers;
    private String activeCipherSuite;
    private int totalSecurityEvents;
    private int criticalViolations;
    private int highSeverityEvents;
    private int integrityMismatches;
    private int tlsHandshakeFailures;
    private String securityPosture; // SECURE, ELEVATED_RISK, COMPROMISED
    private String localListeningPort;

    public SecurityStatusResponse() {}

    public String getTlsVersion() { return tlsVersion; }
    public void setTlsVersion(String tlsVersion) { this.tlsVersion = tlsVersion; }

    public List<String> getSupportedCiphers() { return supportedCiphers; }
    public void setSupportedCiphers(List<String> supportedCiphers) { this.supportedCiphers = supportedCiphers; }

    public String getActiveCipherSuite() { return activeCipherSuite; }
    public void setActiveCipherSuite(String activeCipherSuite) { this.activeCipherSuite = activeCipherSuite; }

    public int getTotalSecurityEvents() { return totalSecurityEvents; }
    public void setTotalSecurityEvents(int totalSecurityEvents) { this.totalSecurityEvents = totalSecurityEvents; }

    public int getCriticalViolations() { return criticalViolations; }
    public void setCriticalViolations(int criticalViolations) { this.criticalViolations = criticalViolations; }

    public int getHighSeverityEvents() { return highSeverityEvents; }
    public void setHighSeverityEvents(int highSeverityEvents) { this.highSeverityEvents = highSeverityEvents; }

    public int getIntegrityMismatches() { return integrityMismatches; }
    public void setIntegrityMismatches(int integrityMismatches) { this.integrityMismatches = integrityMismatches; }

    public int getTlsHandshakeFailures() { return tlsHandshakeFailures; }
    public void setTlsHandshakeFailures(int tlsHandshakeFailures) { this.tlsHandshakeFailures = tlsHandshakeFailures; }

    public String getSecurityPosture() { return securityPosture; }
    public void setSecurityPosture(String securityPosture) { this.securityPosture = securityPosture; }

    public String getLocalListeningPort() { return localListeningPort; }
    public void setLocalListeningPort(String localListeningPort) { this.localListeningPort = localListeningPort; }
}
