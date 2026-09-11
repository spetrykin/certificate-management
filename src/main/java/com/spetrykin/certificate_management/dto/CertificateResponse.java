package com.spetrykin.certificate_management.dto;

import com.spetrykin.certificate_management.domain.CertState;
import com.spetrykin.certificate_management.domain.Certificate;

import java.time.LocalDateTime;

/**
 * Deliberately omits {@code version}: it's a JPA/optimistic-locking
 * implementation detail (see architecture-plan.md, Concurrency section),
 * not something an API consumer needs to see or set.
 */
public record CertificateResponse(
        Long id,
        Long deviceId,
        String serialNumber,
        String commonName,
        CertState state,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt
) {

    public static CertificateResponse from(Certificate certificate) {
        return new CertificateResponse(
                certificate.getId(),
                certificate.getDeviceId(),
                certificate.getSerialNumber(),
                certificate.getCommonName(),
                certificate.getState(),
                certificate.getIssuedAt(),
                certificate.getExpiresAt()
        );
    }
}
