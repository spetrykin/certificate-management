package com.spetrykin.certificate_management.repository;

import com.spetrykin.certificate_management.domain.CertState;
import com.spetrykin.certificate_management.domain.Certificate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA repository for {@link Certificate}.
 */
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    /**
     * Backs the Day 4 scheduled expiry scanner
     * ({@code CertificateExpiryScanner}): finds certificates in {@code state}
     * whose {@code expiresAt} falls before {@code cutoff} (the scanner
     * computes {@code cutoff} as now plus its configurable threshold).
     */
    List<Certificate> findByStateAndExpiresAtBefore(CertState state, LocalDateTime cutoff);
}
