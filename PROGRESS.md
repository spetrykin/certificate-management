# Progress

## Current State

Repo initialized via Spring Initializr, dependencies set, no implementation yet.

## Next Step

Day 1 — Flyway baseline migration + entity classes + `CertState` enum + `CertificateStateMachine` + one unit test proving legal/illegal transitions.

Full architecture decisions — see architecture-plan.md.

## Seven-Day Plan

- Day 1: Scaffold, Flyway migration, entities, state machine, one unit test.
- Day 2: Service layer — transition orchestration, transactional audit write, RenewalTask creation on EXPIRING_SOON.
- Day 3: Optimistic locking wired end-to-end + one integration test (Testcontainers) proving the race is caught.
- Day 4: Scheduled expiry scanner + application-level RenewalTask uniqueness check.
- Day 5: Spring Security — JWT filter chain, ADMIN/VIEWER roles.
- Day 6: REST controllers + DTOs + ProblemDetail error handling + manual curl verification.
- Day 7: README, architecture-plan.md/PROGRESS.md finalization, push to GitHub, LinkedIn update.
