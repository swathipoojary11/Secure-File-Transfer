package com.dcn.securefiletransfer.service;

import com.dcn.securefiletransfer.dto.DeviceRegisterRequest;
import com.dcn.securefiletransfer.model.Device;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Peer Device Registry & Layer 4 TCP Reachability Service.
 * 
 * Networking Viva Explanation:
 * - Manages registered peer computers participating in the transfer mesh.
 * - Live Reachability Check: Probes peer IP and listening port via Layer 4 TCP SYN.
 *   If the peer transfer agent is running, the socket connect handshake succeeds,
 *   confirming the node is 'online' and ready for inbound TLS socket transfers.
 */
@Service
public class DeviceService {

    // Thread-safe in-memory cache of registered devices
    private final Map<String, Device> deviceStore = new ConcurrentHashMap<>();

    public DeviceService() {
        // Initialize default local host node representation
        try {
            String localIp = InetAddress.getLocalHost().getHostAddress();
            String hostName = InetAddress.getLocalHost().getHostName();
            Device localDevice = new Device("system-default", hostName + " (Local)", localIp, 8443);
            localDevice.setStatus("online");
            deviceStore.put(localDevice.getId(), localDevice);
        } catch (Exception ignored) {}
    }

    /**
     * Registers a new computer node on the network.
     */
    public Device registerDevice(String userId, DeviceRegisterRequest request) {
        Device device = new Device(userId, request.getDeviceName(), request.getDeviceIp(), request.getListeningPort());
        // Probe immediate status
        boolean reachable = probeTcpPort(device.getDeviceIp(), device.getListeningPort(), 1200);
        device.setStatus(reachable ? "online" : "offline");
        device.setLastSeen(Instant.now());

        deviceStore.put(device.getId(), device);
        System.out.println("[DEVICE REGISTRY] Registered node: " + device.getDeviceName() + 
                " (" + device.getDeviceIp() + ":" + device.getListeningPort() + ") -> Status: " + device.getStatus());
        return device;
    }

    /**
     * Retrieves all discovered devices on the network.
     */
    public List<Device> getAllDevices() {
        return new ArrayList<>(deviceStore.values());
    }

    /**
     * Retrieves devices registered by a specific user.
     */
    public List<Device> getDevicesForUser(String userId) {
        List<Device> list = new ArrayList<>();
        for (Device d : deviceStore.values()) {
            if (userId != null && userId.equals(d.getUserId())) {
                list.add(d);
            }
        }
        return list;
    }

    /**
     * Deletes a registered device.
     */
    public boolean deleteDevice(String userId, String deviceId) {
        Device d = deviceStore.get(deviceId);
        if (d != null && (userId.equals(d.getUserId()) || "system-default".equals(d.getUserId()))) {
            deviceStore.remove(deviceId);
            return true;
        }
        return false;
    }

    /**
     * Performs a live Layer 4 TCP port check to determine if the target device
     * is currently reachable and accepting file transfers.
     */
    public Map<String, Object> checkDeviceStatus(String deviceId) {
        Device device = deviceStore.get(deviceId);
        Map<String, Object> result = new LinkedHashMap<>();

        if (device == null) {
            result.put("found", false);
            result.put("status", "unknown");
            result.put("message", "Device ID not found in registry");
            return result;
        }

        long startTime = System.currentTimeMillis();
        boolean reachable = probeTcpPort(device.getDeviceIp(), device.getListeningPort(), 2000);
        long pingDurationMs = System.currentTimeMillis() - startTime;

        String newStatus = reachable ? "online" : "offline";
        device.setStatus(newStatus);
        device.setLastSeen(Instant.now());

        result.put("found", true);
        result.put("deviceId", device.getId());
        result.put("deviceName", device.getDeviceName());
        result.put("deviceIp", device.getDeviceIp());
        result.put("listeningPort", device.getListeningPort());
        result.put("status", newStatus);
        result.put("reachable", reachable);
        result.put("pingDurationMs", pingDurationMs);
        result.put("checkedAt", Instant.now().toString());

        return result;
    }

    /**
     * Layer 4 TCP SYN Probe: Attempts socket connection to target IP:Port.
     */
    public boolean probeTcpPort(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
