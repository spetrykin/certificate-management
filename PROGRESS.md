# Progress

## Current State

Days 1 through 5 complete and committed, not yet pushed to origin/main.

Day 5: Spring Security added. JWT issuance/validation via JwtService
(jjwt 0.13.0), signing key sourced exclusively from JWT_SIGNING_KEY
(no default — missing value fails startup loudly, same pattern as DB
credentials); application-local.yml.example documents the required
key with a placeholder. JwtAuthenticationFilter reads the Bearer
token, populates SecurityContext on valid tokens, and otherwise leaves
the request unauthenticated for authorizeHttpRequests to decide
downstream — deliberately not a Spring bean (constructed directly by
SecurityConfig) to avoid Boot's servlet-filter auto-registration
double-running it, a real bug caught and documented in the class's
Javadoc. SecurityConfig: stateless JWT-only auth, CSRF disabled,
/auth/login and springdoc paths permitted, /api/** convention
established for Day 6 (GET → ADMIN or VIEWER, other methods → ADMIN
only) with no placeholder routes. Two demo users (ADMIN, VIEWER roles,
BCrypt-hashed passwords) via an in-memory UserDetailsService,
documented as a deliberate one-week-scope shortcut, not a real user
store. AuthController exposes POST /auth/login, returning a JWT on
valid credentials, 401 otherwise.

Two real bugs found and fixed during Day 5, not just typos: (1) Boot
4.1's Jackson 3 migration coexists with jjwt-jackson's Jackson 2
dependency — different consumers, no conflict, but caught a test
failure from importing the wrong ObjectMapper. (2) An empty Bearer
token ("Bearer " with nothing after it) throws a plain
IllegalArgumentException from jjwt's own internal precondition check,
before reaching JWT-format parsing — this does not extend JwtException,
so it wasn't originally caught by the filter and would have surfaced
as an HTTP 500 instead of leaving the request unauthenticated.
Reproduced empirically, then fixed by broadening the filter's catch.

Tests: JwtServiceTest (valid round-trip, expired token, wrong signing
key). AuthControllerSecurityTest (@WebMvcTest + MockMvc, no database)
covers login success/failure, unauthenticated access rejected,
role-gated authorization (ADMIN token succeeds on a mutating request,
VIEWER token gets 403, VIEWER token succeeds on GET), and both
malformed and empty bearer tokens returning 401 rather than 500. Two
pre-existing full-context tests updated with a throwaway test-only
signing key via @TestPropertySource, required by the new no-default
property — no logic changes. 23/23 tests passing (2 domain +
3 JwtServiceTest + 9 AuthControllerSecurityTest + 5 service +
2 scheduled + 1 concurrency + 1 context-load smoke test).

## Next Step

Day 6 — REST controllers for certificate endpoints under /api/**
(matching Day 5's established authorization convention), DTOs (never
expose JPA entities directly, per CLAUDE.md), ProblemDetail error
handling (including 409 for IllegalStateTransitionException, per the
reasoning fixed early in the project), manual curl verification. Not
yet started.

Full architecture decisions — see architecture-plan.md.

## Seven-Day Plan

- Day 1: Scaffold, Flyway migration, entities, state machine, one unit test.
- Day 2: Service layer — transition orchestration, transactional audit write, RenewalTask creation on EXPIRING_SOON.
- Day 3: Optimistic locking wired end-to-end + one integration test (Testcontainers) proving the race is caught.
- Day 4: Scheduled expiry scanner + application-level RenewalTask uniqueness check.
- Day 5: Spring Security — JWT filter chain, ADMIN/VIEWER roles.
- Day 6: REST controllers + DTOs + ProblemDetail error handling + manual curl verification.
- Day 7: README, architecture-plan.md/PROGRESS.md finalization, push to GitHub, LinkedIn update.
