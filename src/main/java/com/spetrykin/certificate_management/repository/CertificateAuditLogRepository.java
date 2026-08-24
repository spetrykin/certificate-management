package com.spetrykin.certificate_management.repository;

import com.spetrykin.certificate_management.domain.CertificateAuditLog;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link CertificateAuditLog}. No custom
 * queries yet — see PROGRESS.md, Day 2.
 */
public interface CertificateAuditLogRepository extends JpaRepository<CertificateAuditLog, Long> {
}
