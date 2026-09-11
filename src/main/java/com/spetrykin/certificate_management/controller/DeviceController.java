package com.spetrykin.certificate_management.controller;

import com.spetrykin.certificate_management.domain.Device;
import com.spetrykin.certificate_management.dto.DeviceCreateRequest;
import com.spetrykin.certificate_management.dto.DeviceResponse;
import com.spetrykin.certificate_management.service.DeviceService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mounted under {@code /api/**} per SecurityConfig's convention: GET
 * requires ADMIN or VIEWER, POST (create) requires ADMIN.
 * <p>
 * All access here — mutating and read alike — goes through {@link
 * DeviceService} consistently; the service layer is the only layer that
 * talks to repositories.
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    public ResponseEntity<DeviceResponse> createDevice(@Valid @RequestBody DeviceCreateRequest request) {
        Device device = deviceService.createDevice(request.identifier());
        return ResponseEntity.status(HttpStatus.CREATED).body(DeviceResponse.from(device));
    }

    @GetMapping("/{id}")
    public DeviceResponse getDevice(@PathVariable Long id) {
        Device device = deviceService.getDeviceById(id);
        return DeviceResponse.from(device);
    }
}
