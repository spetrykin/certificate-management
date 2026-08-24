package com.spetrykin.certificate_management.service;

import com.spetrykin.certificate_management.domain.CertState;
import com.spetrykin.certificate_management.domain.Certificate;
import com.spetrykin.certificate_management.domain.CertificateAuditLog;
import com.spetrykin.certificate_management.domain.CertificateNotFoundException;
import com.spetrykin.certificate_management.domain.IllegalStateTransitionException;
import com.spetrykin.certificate_management.domain.RenewalStatus;
import com.spetrykin.certificate_management.domain.RenewalTask;
import com.spetrykin.certificate_management.repository.CertificateAuditLogRepository;
import com.spetrykin.certificate_management.repository.CertificateRepository;
import com.spetrykin.certificate_management.repository.RenewalTaskRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain Mockito unit test, no Spring context and no database. This is a
 * deliberate choice over a {@code @DataJpaTest} slice or a Testcontainers
 * integration test:
 * <p>
 * - CLAUDE.md scopes Testcontainers to exactly one test for the whole
 * one-week build, already earmarked (per architecture-plan.md/PROGRESS.md,
 * Day 3) for the optimistic-lock race test. Spending that budget here on
 * transition/audit-log/RenewalTask wiring — none of which depends on real
 * MySQL behavior — would leave nothing for the test it was reserved for.
 * <p>
 * - What this method needs proven is orchestration logic: does it call the
 * state machine, does it persist an audit row only on success, does it
 * create a RenewalTask only for EXPIRING_SOON. None of that depends on a
 * real persistence provider, so mocking the three repositories is sufficient
 * and keeps this test fast and independent of Docker being available.
 * <p>
 * On the "transaction rolled back" case: this test does not exercise
 * Spring's transaction manager (there is no Spring context here at all), so
 * it does not prove rollback in the framework sense. What it proves is the
 * precondition rollback depends on — that once
 * {@code CertificateStateMachine.transition} throws, the method returns
 * without calling either repository's {@code save}. Given {@code @Transactional}
 * on the method (verified by inspection, and exercised for real in the
 * Day 3 Testcontainers test), no save call means nothing to roll back.
 */
@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private CertificateAuditLogRepository certificateAuditLogRepository;

    @Mock
    private RenewalTaskRepository renewalTaskRepository;

    private CertificateService certificateService;

    @BeforeEach
    void setUp() {
        certificateService = new CertificateService(
                certificateRepository, certificateAuditLogRepository, renewalTaskRepository);
    }

    private static Certificate certificateWithId(Long id, CertState state) {
        Certificate certificate = new Certificate(1L, "device-01.example.com", state);
        ReflectionTestUtils.setField(certificate, "id", id);
        return certificate;
    }

    @Test
    void legalTransitionUpdatesStateAndPersistsMatchingAuditLog() {
        Certificate certificate = certificateWithId(100L, CertState.ISSUED);
        when(certificateRepository.findById(100L)).thenReturn(Optional.of(certificate));

        Certificate result = certificateService.transitionCertificate(
                100L, CertState.ACTIVE, "admin", "issued by CA");

        assertThat(result.getState()).isEqualTo(CertState.ACTIVE);

        ArgumentCaptor<CertificateAuditLog> auditLogCaptor = ArgumentCaptor.forClass(CertificateAuditLog.class);
        verify(certificateAuditLogRepository).save(auditLogCaptor.capture());
        CertificateAuditLog auditLog = auditLogCaptor.getValue();
        assertThat(auditLog.getCertificateId()).isEqualTo(100L);
        assertThat(auditLog.getFromState()).isEqualTo(CertState.ISSUED);
        assertThat(auditLog.getToState()).isEqualTo(CertState.ACTIVE);
        assertThat(auditLog.getActor()).isEqualTo("admin");
        assertThat(auditLog.getOccurredAt()).isNotNull();

        verify(renewalTaskRepository, never()).save(any());
    }

    @Test
    void illegalTransitionThrowsAndPersistsNothing() {
        Certificate certificate = certificateWithId(200L, CertState.PENDING_CSR);
        when(certificateRepository.findById(200L)).thenReturn(Optional.of(certificate));

        assertThatThrownBy(() ->
                certificateService.transitionCertificate(200L, CertState.ACTIVE, "admin", "skip issuance"))
                .isInstanceOf(IllegalStateTransitionException.class);

        assertThat(certificate.getState()).isEqualTo(CertState.PENDING_CSR);
        verify(certificateAuditLogRepository, never()).save(any());
        verify(renewalTaskRepository, never()).save(any());
    }

    @Test
    void transitionToExpiringSoonCreatesExactlyOneRenewalTask() {
        Certificate certificate = certificateWithId(300L, CertState.ACTIVE);
        when(certificateRepository.findById(300L)).thenReturn(Optional.of(certificate));

        certificateService.transitionCertificate(300L, CertState.EXPIRING_SOON, "scheduler", "90 days to expiry");

        ArgumentCaptor<RenewalTask> renewalTaskCaptor = ArgumentCaptor.forClass(RenewalTask.class);
        verify(renewalTaskRepository, times(1)).save(renewalTaskCaptor.capture());
        RenewalTask renewalTask = renewalTaskCaptor.getValue();
        assertThat(renewalTask.getCertificateId()).isEqualTo(300L);
        assertThat(renewalTask.getStatus()).isEqualTo(RenewalStatus.QUEUED);
        assertThat(renewalTask.getCreatedAt()).isNotNull();

        verify(certificateAuditLogRepository, times(1)).save(any(CertificateAuditLog.class));
    }

    @Test
    void missingCertificateThrowsCertificateNotFoundException() {
        when(certificateRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                certificateService.transitionCertificate(999L, CertState.ACTIVE, "admin", "n/a"))
                .isInstanceOf(CertificateNotFoundException.class);

        verify(certificateAuditLogRepository, never()).save(any());
        verify(renewalTaskRepository, never()).save(any());
    }
}
