package com.spetrykin.certificate_management.repository;

import com.spetrykin.certificate_management.domain.Certificate;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Certificate}. No custom queries yet
 * — see PROGRESS.md, Day 2.
 */
public interface CertificateRepository extends JpaRepository<Certificate, Long> {
}
