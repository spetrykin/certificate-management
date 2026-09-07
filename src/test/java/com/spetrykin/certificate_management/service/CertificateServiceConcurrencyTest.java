package com.spetrykin.certificate_management.service;

import com.spetrykin.certificate_management.TestcontainersConfiguration;
import com.spetrykin.certificate_management.domain.CertState;
import com.spetrykin.certificate_management.domain.Certificate;
import com.spetrykin.certificate_management.domain.CertificateAuditLog;
import com.spetrykin.certificate_management.domain.CertificateStateMachine;
import com.spetrykin.certificate_management.domain.Device;
import com.spetrykin.certificate_management.repository.CertificateAuditLogRepository;
import com.spetrykin.certificate_management.repository.CertificateRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The week's one and only Testcontainers-backed test (see CLAUDE.md, PROGRESS.md Day 3),
 * proving that the {@code @Version} optimistic lock on {@link Certificate} is what actually
 * catches the race described in architecture-plan.md's Concurrency section: two concurrent
 * scheduled-job scans finding the same certificate in {@code EXPIRING_SOON} and both attempting
 * to transition it to {@code RENEWAL_IN_PROGRESS}.
 * <p>
 * <b>Deliberately no {@code @Transactional} on this class or its test method.</b> Test-managed
 * transactional rollback would swallow every commit this test relies on being real, defeating
 * the entire point — this is the one test in the suite required to hit real MySQL and leave real
 * committed rows behind, verified via fresh reads afterward.
 * <p>
 * <b>Approach: sequential simulation of two independent persistence contexts, not real threads.</b>
 * Genuine multi-threaded concurrency was considered and rejected: {@link CertificateService#transitionCertificate}
 * is a single atomic {@code @Transactional} method with no seam to pause it between its read and
 * its write, so forcing two real threads to interleave deterministically (rather than flakily,
 * dependent on OS scheduling) would require either instrumenting production code with a
 * test-only synchronization hook (not something CLAUDE.md's architecture-review process should
 * have to sign off on for one test) or a MySQL-level lock-holding trick with sleeps and timeouts
 * to guarantee both threads are blocked before releasing it. Both are disproportionate to what's
 * being proven. Per the Day 3 task brief, simulating the race sequentially is explicitly
 * acceptable as long as it exercises real JPA optimistic locking against real MySQL — which the
 * approach below does: two separate, independently-committed reads (two separate {@link
 * TransactionTemplate} executions, each with its own persistence context) both observe {@code
 * version = 0} because nothing has written to the row between them, exactly as two real
 * concurrent scans would.
 * <p>
 * <b>Why the "loser" doesn't call {@code transitionCertificate()} directly.</b> That method takes
 * a certificate ID and does its own {@code findById} internally — calling it a second time after
 * the winner has already committed would just read the fresh, already-transitioned row (version
 * = 1), not a stale one, and would never exercise the lock at all. There is no way to hand a
 * pre-loaded, deliberately-stale {@link Certificate} instance into it. So the losing attempt
 * below replicates the two operations {@code transitionCertificate()} performs — {@link
 * CertificateStateMachine#transition} and the audit log write — against the already-loaded stale
 * entity, inside its own transaction, using the real state machine and real repositories. It does
 * not reimplement any business rule; it composes the same two production calls the service method
 * itself makes, in the same order, just without the redundant internal reload the ID-based method
 * would otherwise force.
 * <p>
 * <b>Known scope boundary: this does not independently prove {@code transitionCertificate()}'s own
 * exception-propagation path under contention.</b> A nested-transaction variant (joining
 * {@code transitionCertificate()}'s default REQUIRED propagation to a shared outer transaction) was
 * considered as a way to route the losing attempt through the real production method. It was
 * rejected: because a participating (non-new) transaction is not flushed/committed at its own
 * method exit, the resulting exception would surface at the outer transaction's commit rather than
 * from the call to {@code transitionCertificate()} itself — proving a narrower, structurally
 * artificial claim, not closing the gap. It would also introduce a shared-transaction topology
 * that doesn't exist in the real system, where two concurrent scans are always two independent
 * top-level invocations. This test therefore accepts, as a documented scope boundary, that it
 * verifies the underlying optimistic-locking mechanism rather than {@code transitionCertificate()}'s
 * specific exception-propagation path under contention — which is expected to work correctly by
 * inspection (no try/catch anywhere in the method), but is not independently proven here.
 */
// jwt.signing-key: Day 5 introduced a required (no-default) property that
// JwtService needs to construct; this full Spring context now includes it,
// so a throwaway, test-only value is supplied here — never the real
// JWT_SIGNING_KEY environment variable / production mechanism.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jwt.signing-key=test-only-signing-key-for-concurrency-test-not-for-real-use-000")
class CertificateServiceConcurrencyTest {

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateAuditLogRepository certificateAuditLogRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void concurrentTransitionAttemptsOnSameCertificateOneWinsOneFailsOptimisticLock() {
        // Setup: persist one certificate in EXPIRING_SOON (a legal-transition state, and the
        // exact state the documented race scenario starts from) via the real repository, real
        // transaction. Commits, leaving version = 0 in the database.
        Long certificateId = transactionTemplate.execute(status -> {
            Device device = new Device("device-race-01.example.com");
            entityManager.persist(device);

            Certificate certificate = new Certificate(device.getId(), "race-cert-01", CertState.EXPIRING_SOON);
            certificateRepository.save(certificate);
            return certificate.getId();
        });

        // Two independent loads, each in its own transaction and its own persistence context —
        // simulating two concurrent scheduled-job scans finding the same certificate. Nothing has
        // written to the row between these two reads, so both correctly observe version = 0,
        // exactly as two genuinely concurrent scans would.
        Certificate scanA = transactionTemplate.execute(status ->
                certificateRepository.findById(certificateId).orElseThrow());
        Certificate scanB = transactionTemplate.execute(status ->
                certificateRepository.findById(certificateId).orElseThrow());

        assertThat(scanA.getVersion()).isZero();
        assertThat(scanB.getVersion()).isZero();

        // "Scan A" wins the race: completes via the real transitionCertificate() service method
        // and commits. This bumps version to 1 in the database and is also the only place in the
        // whole suite that proves transitionCertificate() persists to a real database.
        Certificate winnerResult = certificateService.transitionCertificate(
                certificateId, CertState.RENEWAL_IN_PROGRESS, "scanner-A", "concurrent scan A");

        assertThat(winnerResult.getState()).isEqualTo(CertState.RENEWAL_IN_PROGRESS);
        assertThat(winnerResult.getVersion()).isEqualTo(1L);

        // "Scan B" loses the race: it still holds its stale (version = 0) in-memory certificate
        // from the read above. Attempting to complete its own transition against that stale
        // instance must fail with the optimistic-locking exception, because the version it read
        // no longer matches the row's actual version in the database.
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            CertificateStateMachine.transition(scanB, CertState.RENEWAL_IN_PROGRESS, "scanner-B", "concurrent scan B");
            certificateRepository.save(scanB);

            CertificateAuditLog auditLog = new CertificateAuditLog(
                    scanB.getId(), CertState.EXPIRING_SOON, CertState.RENEWAL_IN_PROGRESS,
                    LocalDateTime.now(), "scanner-B");
            certificateAuditLogRepository.save(auditLog);
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        // Confirmed empirically (see the Day 3 report's console output): Spring's JPA exception
        // translation surfaces org.springframework.orm.ObjectOptimisticLockingFailureException
        // here, not the raw jakarta.persistence.OptimisticLockException.

        // Fresh repository read in a brand-new transaction — not reusing winnerResult, scanA, or
        // scanB — proving the persisted state in the database reflects ONLY scan A's transition.
        Certificate persisted = transactionTemplate.execute(status ->
                certificateRepository.findById(certificateId).orElseThrow());

        assertThat(persisted.getState()).isEqualTo(CertState.RENEWAL_IN_PROGRESS);
        assertThat(persisted.getVersion()).isEqualTo(1L);

        // Exactly one audit log row for this certificate: scan B's transaction rolled back on the
        // optimistic-lock failure, so nothing it did survives — no orphaned or partial audit log
        // from the losing attempt.
        Long certId = certificateId;
        List<CertificateAuditLog> auditLogs = transactionTemplate.execute(status ->
                certificateAuditLogRepository.findAll().stream()
                        .filter(log -> log.getCertificateId().equals(certId))
                        .toList());

        assertThat(auditLogs).hasSize(1);
        assertThat(auditLogs.get(0).getActor()).isEqualTo("scanner-A");
        assertThat(auditLogs.get(0).getFromState()).isEqualTo(CertState.EXPIRING_SOON);
        assertThat(auditLogs.get(0).getToState()).isEqualTo(CertState.RENEWAL_IN_PROGRESS);
    }
}
