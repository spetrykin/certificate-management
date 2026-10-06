# Progress

## Current State

All 7 days complete and committed.

Day 7: README.md added — project description, tech stack, notable
engineering decisions (state machine, optimistic locking as primary
concurrency guard with RenewalTask uniqueness as documented
defense-in-depth, transactional audit logging, the /error security bug
found via live verification, the DTO boundary), how to run it (Docker
Compose or the Testcontainers dev-run path), API docs location,
and status. architecture-plan.md brought current: Security
section now describes the actual SecurityConfig/JwtAuthenticationFilter
shape and the /error fix's full root cause, not just the abstract
JWT/roles concept from earlier in the week; a new API section
documents every endpoint, its auth requirement, and the ProblemDetail
error-mapping table; Deployment section now describes the real,
verified docker-compose setup rather than aspirational Day-1 text.
CLAUDE.md's stale "no device-level endpoints" claim corrected (Day 6
added exactly that). Dockerfile (multi-stage, eclipse-temurin:25-jdk
build / eclipse-temurin:25-jre runtime) and docker-compose.yml (app +
mysql services, mysql pinned to 8.4.11 matching the Testcontainers pin,
app gated on mysql's healthcheck) added; application.yaml gained
spring.datasource.* reading from DB_URL/DB_USERNAME/DB_PASSWORD with
no defaults, same pattern as jwt.signing-key; application-local.yml.example
documents all required keys.

One more real bug found and fixed during Day 7 verification, on top of
Day 6's /error finding: the mysql healthcheck's `-p$MYSQL_ROOT_PASSWORD`
argument looked like it validated credentials but never did — Docker's
CMD (exec-form) healthchecks never invoke a shell, so the variable was
never expanded inside the container regardless of Compose's `$`/`$$`
escaping, and `mysqladmin ping`'s exit code reflects reachability only,
not authentication (confirmed empirically: a deliberately wrong
password still exited 0). Fixed by simplifying the healthcheck to what
it actually is — a reachability/liveness gate — with the misleading
credential arguments removed and the reasoning documented inline.
Verified end-to-end multiple times with `env -i` (a stripped host
shell environment), including a full fresh-clone simulation following
the README's own instructions literally, confirming no undocumented
manual step and no hidden dependency on session-local environment
state.

44/44 tests passing throughout Day 7 (no test changes this day — pure
documentation, configuration, and deployment-infrastructure work).

Post-Day-7 work: manual verification with the demo script surfaced a
real InnoDB deadlock under two simultaneous transition requests; fixed by
widening ApiExceptionHandler to ConcurrencyFailureException, so
optimistic-lock conflicts and deadlocks both map to 409. Added an
HTTP-layer handler test (ApiExceptionHandlerConcurrencyTest), verified
with a mutation check that it fails against the old narrower handler,
and a terminal demo script (demo/demo.sh). 47/47 tests now passing.

## Next Step

None.

Full architecture decisions — see architecture-plan.md.

## Seven-Day Plan

- Day 1: Scaffold, Flyway migration, entities, state machine, one unit test. — Done.
- Day 2: Service layer — transition orchestration, transactional audit write, RenewalTask creation on EXPIRING_SOON. — Done.
- Day 3: Optimistic locking wired end-to-end + one integration test (Testcontainers) proving the race is caught. — Done.
- Day 4: Scheduled expiry scanner + application-level RenewalTask uniqueness check. — Done.
- Day 5: Spring Security — JWT filter chain, ADMIN/VIEWER roles. — Done.
- Day 6: REST controllers + DTOs + ProblemDetail error handling + manual curl verification. — Done.
- Day 7: README, architecture-plan.md/PROGRESS.md finalization, push to GitHub. — Done.
