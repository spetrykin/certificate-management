package com.spetrykin.certificate_management.service;

import com.spetrykin.certificate_management.domain.Device;
import com.spetrykin.certificate_management.domain.DeviceNotFoundException;
import com.spetrykin.certificate_management.repository.DeviceRepository;

import org.springframework.stereotype.Service;

/**
 * Deliberately minimal, matching {@link Device}'s own documented
 * one-week-scope cut (see CLAUDE.md): identifier only, no metadata, no
 * update/delete, no device-level endpoints beyond creation and lookup by
 * id. {@code DeviceController} calls only this service, never {@link
 * DeviceRepository} directly — it's the only layer that talks to
 * repositories.
 */
@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public Device createDevice(String identifier) {
        return deviceRepository.save(new Device(identifier));
    }

    /**
     * @throws DeviceNotFoundException if no device exists for {@code id}
     */
    public Device getDeviceById(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new DeviceNotFoundException(id));
    }
}
