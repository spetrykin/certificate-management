# Progress

## Current State

Days 1 through 4 complete and committed, not yet pushed to origin/main.

Day 4: application-level RenewalTask uniqueness guard in
CertificateService.transitionCertificate() — before creating a
RenewalTask on transition to EXPIRING_SOON, checks whether an active
(QUEUED or IN_PROGRESS) RenewalTask already exists for the certificate
via RenewalTaskRepository.findByCertificateIdAndStatusIn(); if so,
skips creation and logs at INFO, but still completes the state
transition and audit log write. This is documented as defense-in-depth,
not the primary concurrency guard — the primary guard remains
optimistic locking (@Version) on Certificate, which already prevents
two genuinely concurrent scans from both reaching RenewalTask creation
in the same race; this check instead covers legitimate re-entry into
EXPIRING_SOON via the RENEWAL_IN_PROGRESS failure path while an earlier
RenewalTask is still active.

CertificateExpiryScanner (new, scheduled package): @Scheduled method,
interval and threshold externalized as certificate.expiry-scan.interval-ms
(default 60000) and certificate.expiry-scan.threshold-days (default 30)
in application.yaml. Queries CertificateRepository.findByStateAndExpiresAtBefore
for ACTIVE certificates past the threshold, calls
transitionCertificate() for each with actor "scheduler"; each
certificate's failure is caught and logged at WARN individually so one
bad row doesn't abort the batch. @EnableScheduling confirmed present
on CertificateManagementApplication (required for @Scheduled to have
any effect at all — silently a no-op without it). Verified with a live
Testcontainers-backed run (interval overridden to 2s) that the
scheduler genuinely fires periodically on Spring's scheduling thread,
not just via direct unit-test invocation.

Tests: CertificateServiceTest gained a case proving the uniqueness
guard skips RenewalTask creation but still transitions/audits when an
active task exists. New CertificateExpiryScannerTest proves the
scanner calls transitionCertificate() once per due certificate and
that one certificate's failure doesn't stop the rest of the batch from
processing. 11/11 tests passing (2 domain + 5 service + 2 scheduled +
1 concurrency + 1 context-load smoke test).

## Next Step

Day 5 — Spring Security: JWT filter chain, ADMIN/VIEWER roles.
Refresh token rotation and revocation remain explicitly out of scope
(see architecture-plan.md, Security section). Not yet started.

Full architecture decisions — see architecture-plan.md.

## Seven-Day Plan

- Day 1: Scaffold, Flyway migration, entities, state machine, one unit test.
- Day 2: Service layer — transition orchestration, transactional audit write, RenewalTask creation on EXPIRING_SOON.
- Day 3: Optimistic locking wired end-to-end + one integration test (Testcontainers) proving the race is caught.
- Day 4: Scheduled expiry scanner + application-level RenewalTask uniqueness check.
- Day 5: Spring Security — JWT filter chain, ADMIN/VIEWER roles.
- Day 6: REST controllers + DTOs + ProblemDetail error handling + manual curl verification.
- Day 7: README, architecture-plan.md/PROGRESS.md finalization, push to GitHub, LinkedIn update.
