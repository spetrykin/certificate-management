package com.spetrykin.certificate_management.domain;

/**
 * Thrown when a {@link Device} lookup by id finds nothing — in practice, Day
 * 6's {@code CertificateService.createCertificate} validating the target
 * device exists before creating a certificate for it. Same shape as
 * {@link CertificateNotFoundException}.
 */
public class DeviceNotFoundException extends RuntimeException {

    public DeviceNotFoundException(Long deviceId) {
        super("Device not found: id=" + deviceId);
    }
}
