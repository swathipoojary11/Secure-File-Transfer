package com.dcn.securefiletransfer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for Device Registration.
 */
public class DeviceRegisterRequest {

    @NotBlank(message = "Device name cannot be blank")
    private String deviceName;

    @NotBlank(message = "Device IP address cannot be blank")
    @Pattern(regexp = "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$|^localhost$",
             message = "Invalid IPv4 address format")
    private String deviceIp;

    @Min(value = 1024, message = "Listening port must be >= 1024 (non-privileged)")
    @Max(value = 65535, message = "Listening port must be <= 65535")
    private int listeningPort = 8443;

    public DeviceRegisterRequest() {
    }

    public DeviceRegisterRequest(String deviceName, String deviceIp, int listeningPort) {
        this.deviceName = deviceName;
        this.deviceIp = deviceIp;
        this.listeningPort = listeningPort;
    }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getDeviceIp() { return deviceIp; }
    public void setDeviceIp(String deviceIp) { this.deviceIp = deviceIp; }

    public int getListeningPort() { return listeningPort; }
    public void setListeningPort(int listeningPort) { this.listeningPort = listeningPort; }
}
