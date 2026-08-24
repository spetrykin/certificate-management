# Progress

## Current State

Day 1 (domain layer) complete: entities, `CertState`, `CertificateStateMachine`, one unit test, Flyway baseline migration — all committed and pushed to origin/main. `RenewalStatus` enum added as its own enum (not a bare `String`) per explicit decision. Testcontainers pinned to `mysql:8.4.11` LTS, not `latest` — see architecture-plan.md Deployment section for rationale.

CLAUDE.md governance rules added since original setup: commits only on explicit user instruction (never agent-initiated); no `.git` deletion or equivalent history-destroying operations under any framing, ever, by the agent; no committed secrets/credentials — datasource config will use env-var placeholders with a gitignored local file, per the plan already confirmed and recorded.

Remote repo created and pushed: github.com/spetrykin/certificate-management, branch main, MIT licensed.

## Next Step

Day 2 — service layer: `CertificateService.transitionCertificate()` (transactional, orchestrates state-machine transition + audit log write + conditional `RenewalTask` creation on EXPIRING_SOON), `CertificateRepository` / `CertificateAuditLogRepository` / `RenewalTaskRepository` (Spring Data JPA, no custom queries yet). Not yet started.

Full architecture decisions — see architecture-plan.md.

## Seven-Day Plan

- Day 1: Scaffold, Flyway migration, entities, state machine, one unit test.
- Day 2: Service layer — transition orchestration, transactional audit write, RenewalTask creation on EXPIRING_SOON.
- Day 3: Optimistic locking wired end-to-end + one integration test (Testcontainers) proving the race is caught.
- Day 4: Scheduled expiry scanner + application-level RenewalTask uniqueness check.
- Day 5: Spring Security — JWT filter chain, ADMIN/VIEWER roles.
- Day 6: REST controllers + DTOs + ProblemDetail error handling + manual curl verification.
- Day 7: README, architecture-plan.md/PROGRESS.md finalization, push to GitHub, LinkedIn update.
