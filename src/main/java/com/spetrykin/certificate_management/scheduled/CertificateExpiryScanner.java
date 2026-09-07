package com.spetrykin.certificate_management.scheduled;

import com.spetrykin.certificate_management.domain.CertState;
import com.spetrykin.certificate_management.domain.Certificate;
import com.spetrykin.certificate_management.repository.CertificateRepository;
import com.spetrykin.certificate_management.service.CertificateService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Finds certificates due for renewal and transitions them to
 * {@link CertState#EXPIRING_SOON}, which — via
 * {@link CertificateService#transitionCertificate} — also writes the
 * corresponding audit log entry and (subject to the Day 4 uniqueness guard)
 * queues a {@code RenewalTask}, all in one transaction per certificate.
 * <p>
 * Scope is deliberately limited to ACTIVE -&gt; EXPIRING_SOON detection.
 * True-expiry handling (a non-terminal certificate whose {@code expiresAt}
 * has already passed -&gt; {@link CertState#EXPIRED}) is out of scope for this
 * scanner.
 */
@Component
public class CertificateExpiryScanner {

    private static final Logger log = LoggerFactory.getLogger(CertificateExpiryScanner.class);

    private static final String ACTOR = "scheduler";
    private static final String REASON = "expiry threshold reached";

    private final CertificateRepository certificateRepository;
    private final CertificateService certificateService;
    private final int thresholdDays;

    public CertificateExpiryScanner(
            CertificateRepository certificateRepository,
            CertificateService certificateService,
            @Value("${certificate.expiry-scan.threshold-days:30}") int thresholdDays
    ) {
        this.certificateRepository = certificateRepository;
        this.certificateService = certificateService;
        this.thresholdDays = thresholdDays;
    }

    /**
     * Runs on a fixed rate (see {@code certificate.expiry-scan.interval-ms}
     * in application.yaml). Each matching certificate is transitioned
     * independently; one certificate's failure is logged and skipped rather
     * than aborting the rest of the batch — this method itself never
     * propagates an exception.
     */
    @Scheduled(fixedRateString = "${certificate.expiry-scan.interval-ms:60000}")
    public void scanForExpiringCertificates() {
        LocalDateTime cutoff = LocalDateTime.now().plusDays(thresholdDays);
        List<Certificate> dueForRenewal =
                certificateRepository.findByStateAndExpiresAtBefore(CertState.ACTIVE, cutoff);

        for (Certificate certificate : dueForRenewal) {
            try {
                certificateService.transitionCertificate(
                        certificate.getId(), CertState.EXPIRING_SOON, ACTOR, REASON);
            } catch (RuntimeException e) {
                log.warn("Expiry scan failed to transition certificate {} to EXPIRING_SOON",
                        certificate.getId(), e);
            }
        }
    }
}
