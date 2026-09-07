package com.spetrykin.certificate_management.repository;

import com.spetrykin.certificate_management.domain.RenewalStatus;
import com.spetrykin.certificate_management.domain.RenewalTask;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link RenewalTask}.
 */
public interface RenewalTaskRepository extends JpaRepository<RenewalTask, Long> {

    /**
     * Backs the Day 4 application-level "at most one active RenewalTask per
     * certificate" guard in {@code CertificateService.transitionCertificate}
     * (see architecture-plan.md, Concurrency section). Returning the list
     * rather than a plain {@code boolean} lets the caller both check for an
     * existing active task and log its id without a second query.
     */
    List<RenewalTask> findByCertificateIdAndStatusIn(Long certificateId, List<RenewalStatus> statuses);
}
