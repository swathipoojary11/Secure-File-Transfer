package com.dcn.securefiletransfer.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Peer Device Entity for Distributed Network Nodes.
 * 
 * Networking Viva Explanation:
 * Represents a physical computer (laptop/workstation) registered on the local network.
 * Stores Layer 3 (IP Address) and Layer 4 (TCP Listening Port) addressing details
 * required for direct peer-to-peer file transfer sockets.
 */
public class Device {

    private String id;
    private String userId;
    private String deviceName;
    private String deviceIp;
    private int listeningPort;
    private String status; // "online", "offline", "busy"
    private Instant lastSeen;
    private Instant createdAt;

    public Device() {
        this.id = UUID.randomUUID().toString();
        this.status = "offline";
        this.lastSeen = Instant.now();
        this.createdAt = Instant.now();
        this.listeningPort = 8443;
    }

    public Device(String userId, String deviceName, String deviceIp, int listeningPort) {
        this();
        this.userId = userId;
        this.deviceName = deviceName;
        this.deviceIp = deviceIp;
        this.listeningPort = listeningPort > 0 ? listeningPort : 8443;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getDeviceIp() { return deviceIp; }
    public void setDeviceIp(String deviceIp) { this.deviceIp = deviceIp; }

    public int getListeningPort() { return listeningPort; }
    public void setListeningPort(int listeningPort) { this.listeningPort = listeningPort; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
