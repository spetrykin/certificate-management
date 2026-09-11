package com.spetrykin.certificate_management.dto;

import com.spetrykin.certificate_management.domain.Device;

public record DeviceResponse(Long id, String identifier) {

    public static DeviceResponse from(Device device) {
        return new DeviceResponse(device.getId(), device.getIdentifier());
    }
}
