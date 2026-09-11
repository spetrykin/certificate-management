package com.spetrykin.certificate_management.service;

import com.spetrykin.certificate_management.domain.CertState;
import com.spetrykin.certificate_management.domain.Certificate;
import com.spetrykin.certificate_management.domain.CertificateAuditLog;
import com.spetrykin.certificate_management.domain.CertificateNotFoundException;
import com.spetrykin.certificate_management.domain.CertificateStateMachine;
import com.spetrykin.certificate_management.domain.DeviceNotFoundException;
import com.spetrykin.certificate_management.domain.RenewalStatus;
import com.spetrykin.certificate_management.domain.RenewalTask;
import com.spetrykin.certificate_management.repository.CertificateAuditLogRepository;
import com.spetrykin.certificate_management.repository.CertificateRepository;
import com.spetrykin.certificate_management.repository.DeviceRepository;
import com.spetrykin.certificate_management.repository.RenewalTaskRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Orchestrates certificate state transitions. Per CLAUDE.md, the audit log
 * write and the state transition happen inside the same
 * {@code @Transactional} method — never separated — and this class is the
 * only place that calls {@link CertificateStateMachine#transition}, which
 * remains the sole path by which a {@link Certificate}'s state may change.
 */
@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    /**
     * RenewalTask statuses that count as "active" for the Day 4 uniqueness
     * guard below — a task that hasn't reached a terminal outcome yet.
     */
    private static final List<RenewalStatus> ACTIVE_RENEWAL_STATUSES =
            List.of(RenewalStatus.QUEUED, RenewalStatus.IN_PROGRESS);

    private final CertificateRepository certificateRepository;
    private final CertificateAuditLogRepository certificateAuditLogRepository;
    private final RenewalTaskRepository renewalTaskRepository;
    private final DeviceRepository deviceRepository;

    public CertificateService(
            CertificateRepository certificateRepository,
            CertificateAuditLogRepository certificateAuditLogRepository,
            RenewalTaskRepository renewalTaskRepository,
            DeviceRepository deviceRepository
    ) {
        this.certificateRepository = certificateRepository;
        this.certificateAuditLogRepository = certificateAuditLogRepository;
        this.renewalTaskRepository = renewalTaskRepository;
        this.deviceRepository = deviceRepository;
    }

    /**
     * Creates a new {@link Certificate} for {@code deviceId} in {@link
     * CertState#PENDING_CSR} — the certificate's initial state, so this is
     * deliberately NOT routed through {@link CertificateStateMachine}: there
     * is no prior state to transition from, and the state machine models
     * transitions between existing states, not object creation.
     * <p>
     * {@code @Transactional} here even though this is a single logical
     * step (a read to check the device exists, then a write), not
     * multi-step orchestration like {@link #transitionCertificate}: the
     * device foreign key already backstops referential integrity at the
     * DB level (see V1__init_schema.sql's {@code fk_certificate_device}),
     * so this annotation isn't covering a real correctness gap today.
     * It's here for consistency with this class's other mutating method
     * and so this "check, then write" pair reads as one explicit unit of
     * work rather than two independently-committed repository calls —
     * cheap to add, and the natural boundary if this method ever grows
     * (e.g. a future serial-number allocation step) past a single write.
     *
     * @throws DeviceNotFoundException if no device exists for {@code deviceId}
     */
    @Transactional
    public Certificate createCertificate(Long deviceId, String commonName) {
        deviceRepository.findById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));

        Certificate certificate = new Certificate(deviceId, commonName, CertState.PENDING_CSR);
        return certificateRepository.save(certificate);
    }

    /**
     * No {@code @Transactional}: a single read, no orchestration —
     * same reasoning as {@link #listCertificates()}.
     *
     * @throws CertificateNotFoundException if no certificate exists for {@code id}
     */
    public Certificate getCertificateById(Long id) {
        return certificateRepository.findById(id)
                .orElseThrow(() -> new CertificateNotFoundException(id));
    }

    /**
     * No {@code @Transactional}: a single read, no orchestration — same
     * reasoning as {@link #getCertificateById(Long)}.
     */
    public List<Certificate> listCertificates() {
        return certificateRepository.findAll();
    }

    /**
     * Transitions {@code certificateId} to {@code target} via
     * {@link CertificateStateMachine#transition}, then — only if that
     * succeeds — writes the corresponding audit log entry and, if
     * {@code target} is {@link CertState#EXPIRING_SOON}, creates a queued
     * {@link RenewalTask}, all in this one transaction.
     * <p>
     * A RenewalTask is created on every transition to EXPIRING_SOON only if
     * the certificate doesn't already have one active (see the
     * "one active RenewalTask per certificate" guard below).
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
            // Application-level, defense-in-depth guard (architecture-plan.md,
            // Concurrency section). It is NOT the primary protection against
            // the documented race: two concurrent scans transitioning the same
            // certificate to EXPIRING_SOON already conflict on
            // Certificate.version first, so only one of them ever reaches this
            // point in the same transaction. This check instead covers the
            // case where a certificate legitimately re-enters EXPIRING_SOON
            // (e.g. the RENEWAL_IN_PROGRESS -> EXPIRING_SOON failure path)
            // while an earlier RenewalTask for it is still active.
            List<RenewalTask> activeTasks = renewalTaskRepository.findByCertificateIdAndStatusIn(
                    certificate.getId(), ACTIVE_RENEWAL_STATUSES);
            if (activeTasks.isEmpty()) {
                RenewalTask renewalTask = new RenewalTask(certificate.getId(), RenewalStatus.QUEUED, now);
                renewalTaskRepository.save(renewalTask);
            } else {
                log.info("Skipping RenewalTask creation for certificate {}: active RenewalTask {} already exists",
                        certificate.getId(), activeTasks.get(0).getId());
            }
        }

        return certificate;
    }
}
