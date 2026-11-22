package com.dcn.securefiletransfer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health & Diagnostics REST Controller.
 * 
 * Networking Concept:
 * Acts as the baseline Layer 7 (Application Layer) ping endpoint.
 * The Next.js frontend uses this endpoint to display real-time connection status
 * and discover the local host IP and system readiness.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> response = new LinkedHashMap<>();

        String localIp = "Unknown";
        String hostName = "Unknown";
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            localIp = localHost.getHostAddress();
            hostName = localHost.getHostName();
        } catch (Exception e) {
            localIp = "127.0.0.1";
        }

        response.put("status", "UP");
        response.put("service", "Secure File Transfer Backend");
        response.put("version", "1.0.0");
        response.put("dcnModule", "Data Communication & Networks Academic Mini-Project");
        response.put("timestamp", Instant.now().toString());
        response.put("localIp", localIp);
        response.put("hostName", hostName);
        response.put("javaVersion", System.getProperty("java.version"));
        response.put("osName", System.getProperty("os.name"));
        response.put("tlsSupported", true);
        response.put("sha256VerificationReady", true);
        response.put("tcpAgentStatus", "STANDBY");

        return ResponseEntity.ok(response);
    }
}
