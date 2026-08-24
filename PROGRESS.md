# Progress

## Current State

Day 1 and Day 2 complete, committed, and pushed to origin/main
(HEAD c91fdb6). Day 1: domain layer (entities, CertState, RenewalStatus,
CertificateStateMachine, one unit test, Flyway baseline migration).
Day 2: CertificateService.transitionCertificate() — transactional,
orchestrates state-machine transition, audit log write, and conditional
RenewalTask creation on EXPIRING_SOON in one transaction; three Spring
Data JPA repositories (CertificateRepository, CertificateAuditLogRepository,
RenewalTaskRepository), no custom queries yet; CertificateNotFoundException
added alongside IllegalStateTransitionException. Certificate's state
mutation persists via JPA dirty-checking (no explicit save() call),
documented in CertificateService's Javadoc. Four Mockito-based unit
tests cover the legal-transition, illegal-transition, EXPIRING_SOON
RenewalTask-creation, and not-found paths — deliberately no database/
Spring context here, reserving the week's one Testcontainers test for
Day 3's concurrency test. 7/7 tests passing (2 domain + 4 service +
1 context-load smoke test).

## Next Step

Day 3 — Testcontainers-based integration test proving the optimistic-
lock race is caught (two concurrent transition attempts on the same
certificate; the losing attempt must fail with the JPA/Spring
optimistic-locking exception). This is also the only test in the suite
that verifies transitionCertificate() persists to a real database —
must assert the post-commit state via a fresh repository read, not
just the in-memory object, and must confirm exactly one audit log row
exists after the race (no orphaned/partial log from the failed
attempt). Not yet started.

Full architecture decisions — see architecture-plan.md.

## Seven-Day Plan

- Day 1: Scaffold, Flyway migration, entities, state machine, one unit test.
- Day 2: Service layer — transition orchestration, transactional audit write, RenewalTask creation on EXPIRING_SOON.
- Day 3: Optimistic locking wired end-to-end + one integration test (Testcontainers) proving the race is caught.
- Day 4: Scheduled expiry scanner + application-level RenewalTask uniqueness check.
- Day 5: Spring Security — JWT filter chain, ADMIN/VIEWER roles.
- Day 6: REST controllers + DTOs + ProblemDetail error handling + manual curl verification.
- Day 7: README, architecture-plan.md/PROGRESS.md finalization, push to GitHub, LinkedIn update.
