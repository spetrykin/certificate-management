# Progress

## Current State

Day 1, Day 2, and Day 3 complete and committed (HEAD 47229bb); not yet
pushed to origin/main (origin still at c91fdb6). Day 1: domain layer
(entities, CertState, RenewalStatus, CertificateStateMachine, one unit
test, Flyway baseline migration). Day 2: CertificateService.transitionCertificate()
— transactional, orchestrates state-machine transition, audit log
write, and conditional RenewalTask creation on EXPIRING_SOON in one
transaction; three Spring Data JPA repositories (CertificateRepository,
CertificateAuditLogRepository, RenewalTaskRepository), no custom
queries yet; CertificateNotFoundException added alongside
IllegalStateTransitionException. Certificate's state mutation persists
via JPA dirty-checking (no explicit save() call), documented in
CertificateService's Javadoc. Four Mockito-based unit tests cover the
legal-transition, illegal-transition, EXPIRING_SOON RenewalTask-creation,
and not-found paths — deliberately no database/Spring context here,
reserving the week's one Testcontainers test for Day 3's concurrency
test.

Day 3: CertificateServiceConcurrencyTest, the week's one and only
Testcontainers test (real MySQL 8.4.11 via Docker), proving the
@Version optimistic lock on Certificate catches the documented race —
two concurrent scans transitioning the same certificate from
EXPIRING_SOON to RENEWAL_IN_PROGRESS. Simulates the race sequentially
(two independently-committed reads, both observing version = 0, rather
than real threads — see the test's Javadoc for why genuine
multi-threading was rejected as disproportionate) rather than via real
concurrency. The winning attempt goes through the real
transitionCertificate() service method, proving for the first time in
the suite that it persists to a real database; the losing attempt
replicates transitionCertificate()'s two operations (state-machine
transition, audit log write) against an already-loaded stale entity,
since the ID-based method has no seam to inject a stale instance. A
nested-transaction variant that would have routed the losing attempt
through the real method too was considered and explicitly rejected
(see the test's Javadoc) — the test therefore documents a known scope
boundary: it proves the optimistic-locking mechanism itself, not
transitionCertificate()'s specific exception-propagation path under
contention (expected correct by inspection — no try/catch anywhere in
the method — but not independently proven here). Confirmed empirically
that org.springframework.orm.ObjectOptimisticLockingFailureException,
not the raw jakarta.persistence.OptimisticLockException, is what
surfaces. Also asserts, via a fresh post-race repository read, that
only the winning transition persisted, and that exactly one audit log
row exists (no orphan from the loser's rolled-back transaction, despite
CertificateAuditLog's IDENTITY generation forcing an immediate insert).
TestcontainersConfiguration widened from package-private to public
(test scaffolding only) so the new test, in the service package, could
import it. 8/8 tests passing (2 domain + 4 service + 1 concurrency +
1 context-load smoke test).

## Next Step

Day 4 — Scheduled expiry scanner and application-level RenewalTask
uniqueness check (at most one active task per certificate), enforced
in the service layer since MySQL/InnoDB has no partial unique index
(see architecture-plan.md, Concurrency section). Not yet started.

Full architecture decisions — see architecture-plan.md.

## Seven-Day Plan

- Day 1: Scaffold, Flyway migration, entities, state machine, one unit test.
- Day 2: Service layer — transition orchestration, transactional audit write, RenewalTask creation on EXPIRING_SOON.
- Day 3: Optimistic locking wired end-to-end + one integration test (Testcontainers) proving the race is caught.
- Day 4: Scheduled expiry scanner + application-level RenewalTask uniqueness check.
- Day 5: Spring Security — JWT filter chain, ADMIN/VIEWER roles.
- Day 6: REST controllers + DTOs + ProblemDetail error handling + manual curl verification.
- Day 7: README, architecture-plan.md/PROGRESS.md finalization, push to GitHub, LinkedIn update.
