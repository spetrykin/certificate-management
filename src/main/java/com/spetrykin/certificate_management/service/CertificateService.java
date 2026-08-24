package com.spetrykin.certificate_management.service;

import com.spetrykin.certificate_management.domain.CertState;
import com.spetrykin.certificate_management.domain.Certificate;
import com.spetrykin.certificate_management.domain.CertificateAuditLog;
import com.spetrykin.certificate_management.domain.CertificateNotFoundException;
import com.spetrykin.certificate_management.domain.CertificateStateMachine;
import com.spetrykin.certificate_management.domain.RenewalStatus;
import com.spetrykin.certificate_management.domain.RenewalTask;
import com.spetrykin.certificate_management.repository.CertificateAuditLogRepository;
import com.spetrykin.certificate_management.repository.CertificateRepository;
import com.spetrykin.certificate_management.repository.RenewalTaskRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Orchestrates certificate state transitions. Per CLAUDE.md, the audit log
 * write and the state transition happen inside the same
 * {@code @Transactional} method — never separated — and this class is the
 * only place that calls {@link CertificateStateMachine#transition}, which
 * remains the sole path by which a {@link Certificate}'s state may change.
 */
@Service
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final CertificateAuditLogRepository certificateAuditLogRepository;
    private final RenewalTaskRepository renewalTaskRepository;

    public CertificateService(
            CertificateRepository certificateRepository,
            CertificateAuditLogRepository certificateAuditLogRepository,
            RenewalTaskRepository renewalTaskRepository
    ) {
        this.certificateRepository = certificateRepository;
        this.certificateAuditLogRepository = certificateAuditLogRepository;
        this.renewalTaskRepository = renewalTaskRepository;
    }

    /**
     * Transitions {@code certificateId} to {@code target} via
     * {@link CertificateStateMachine#transition}, then — only if that
     * succeeds — writes the corresponding audit log entry and, if
     * {@code target} is {@link CertState#EXPIRING_SOON}, creates a queued
     * {@link RenewalTask}, all in this one transaction.
     * <p>
     * No application-level "one active RenewalTask per certificate" check
     * here: that's deferred to Day 4 alongside the scheduled scanner (see
     * architecture-plan.md, Concurrency section). A RenewalTask is created
     * unconditionally on every transition to EXPIRING_SOON at this stage.
     * <p>
     * No explicit {@code certificateRepository.save(certificate)} call:
     * {@code certificate} is loaded via {@code findById} within this same
     * transaction, so it stays JPA-managed for the whole method body, and
     * its state mutation from {@link CertificateStateMachine#transition} is
     * flushed automatically at commit via dirty checking. This is unlike
     * the audit log and RenewalTask below, which are newly-constructed
     * (detached) entities and therefore do need an explicit {@code save}.
     *
     * @throws CertificateNotFoundException     if no certificate exists for
     *                                           {@code certificateId}
     * @throws com.spetrykin.certificate_management.domain.IllegalStateTransitionException
     *                                           if {@code target} is not a
     *                                           legal transition from the
     *                                           certificate's current state
     *                                           — left to propagate, which
     *                                           rolls back this transaction
     */
    @Transactional
    public Certificate transitionCertificate(Long certificateId, CertState target, String actor, String reason) {
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new CertificateNotFoundException(certificateId));

        CertState previousState = certificate.getState();
        CertificateStateMachine.transition(certificate, target, actor, reason);

        LocalDateTime now = LocalDateTime.now();
        CertificateAuditLog auditLog = new CertificateAuditLog(
                certificate.getId(), previousState, target, now, actor);
        certificateAuditLogRepository.save(auditLog);

        if (target == CertState.EXPIRING_SOON) {
            RenewalTask renewalTask = new RenewalTask(certificate.getId(), RenewalStatus.QUEUED, now);
            renewalTaskRepository.save(renewalTask);
        }

        return certificate;
    }
}
