package com.dcn.securefiletransfer.controller;

import com.dcn.securefiletransfer.dto.DeviceRegisterRequest;
import com.dcn.securefiletransfer.model.Device;
import com.dcn.securefiletransfer.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Device Management REST API Controller.
 * 
 * Networking Viva Explanation:
 * Provides endpoints for discovering, registering, and pinging peer devices
 * on the Wi-Fi / LAN network.
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /**
     * Lists all registered network devices.
     */
    @GetMapping
    public ResponseEntity<List<Device>> listDevices() {
        return ResponseEntity.ok(deviceService.getAllDevices());
    }

    /**
     * Registers a new device node belonging to the authenticated user.
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerDevice(@Valid @RequestBody DeviceRegisterRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = (auth != null && auth.getPrincipal() != null) ? auth.getPrincipal().toString() : "anonymous-user";

        Device device = deviceService.registerDevice(userId, request);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Device registered successfully");
        response.put("device", device);

        return ResponseEntity.ok(response);
    }

    /**
     * Removes a device from the network registry.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> removeDevice(@PathVariable("id") String deviceId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = (auth != null && auth.getPrincipal() != null) ? auth.getPrincipal().toString() : "anonymous-user";

        boolean deleted = deviceService.deleteDevice(userId, deviceId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", deleted);
        response.put("message", deleted ? "Device removed from registry" : "Device not found or unauthorized");

        return deleted ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    /**
     * Executes an active Layer 4 TCP SYN probe to test peer reachability.
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> checkStatus(@PathVariable("id") String deviceId) {
        Map<String, Object> statusResult = deviceService.checkDeviceStatus(deviceId);
        return ResponseEntity.ok(statusResult);
    }
}
