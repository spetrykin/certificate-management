# Progress

## Current State

Days 1 through 6 complete and committed, not yet pushed to origin/main.

Day 6: REST API surface added under /api/**, matching Day 5's
authorization convention (GET requires ADMIN or VIEWER, other methods
require ADMIN). DTOs (dto package) mediate every request/response —
JPA entities are never exposed directly, per CLAUDE.md; CertificateResponse
deliberately omits `version` (a JPA/optimistic-locking implementation
detail, not an API concern). Service layer gained creation methods
(DeviceService.createDevice, CertificateService.createCertificate,
validating the target device exists via DeviceNotFoundException) and
read methods (getCertificateById, listCertificates, getDeviceById via
DeviceService) — after an explicit decision, ALL controller access
(mutating and read alike) goes through the service layer consistently;
no repository is ever injected into a controller. ApiExceptionHandler
(@RestControllerAdvice) maps CertificateNotFoundException/
DeviceNotFoundException to 404, IllegalStateTransitionException to 409,
ObjectOptimisticLockingFailureException to 409 (confirmed reachable
from POST /api/certificates/{id}/transitions — two concurrent HTTP
requests can race on Certificate.version exactly as Day 3's scheduler
test proved), Bean Validation failures to 400 with field-level detail,
and a generic RuntimeException fallback to 500 that never leaks
internals into the response body.

One real production bug found and fixed during manual curl
verification against the live app (not caught by any @WebMvcTest
slice): an authenticated-but-wrong-role request correctly received a
403 from Spring Security's AccessDeniedHandlerImpl, but the client
actually received a 401. Root cause: Spring Boot's default error
handling internally forwards the already-decided response to /error,
which re-enters the same security filter chain as a fresh anonymous
request; without an explicit permitAll for /error, that second,
unrelated pass failed authentication and its 401 silently overwrote
the correct 403. Fixed by permitting /error in SecurityConfig.
MockMvc-based tests never execute this real container-level forward,
so this class of bug is structurally invisible to the slice-test
layer — exactly what the curl-against-a-live-server step exists to
catch. Documented in SecurityConfig's Javadoc as a permanent warning
against removing that permitAll entry.

Manual curl verification performed against a live Testcontainers-backed
run: full login → create device → create certificate → legal transition
→ illegal transition (409 with ProblemDetail body) → unauthenticated
request (401) → VIEWER read access (200) → VIEWER mutation attempt
(403, confirmed fixed) sequence, all behaving as designed.

44/44 tests passing (unit tests for all service methods including the
new creation/read methods, @WebMvcTest slices for both controllers
covering success/not-found/validation/authorization paths, plus the
existing domain/concurrency/scheduled/security suites from Days 1-5,
unchanged).

## Next Step

Day 7 — README, architecture-plan.md/PROGRESS.md finalization, final
push to GitHub, LinkedIn profile update. Not yet started.

Full architecture decisions — see architecture-plan.md.

## Seven-Day Plan

- Day 1: Scaffold, Flyway migration, entities, state machine, one unit test.
- Day 2: Service layer — transition orchestration, transactional audit write, RenewalTask creation on EXPIRING_SOON.
- Day 3: Optimistic locking wired end-to-end + one integration test (Testcontainers) proving the race is caught.
- Day 4: Scheduled expiry scanner + application-level RenewalTask uniqueness check.
- Day 5: Spring Security — JWT filter chain, ADMIN/VIEWER roles.
- Day 6: REST controllers + DTOs + ProblemDetail error handling + manual curl verification.
- Day 7: README, architecture-plan.md/PROGRESS.md finalization, push to GitHub, LinkedIn update.
