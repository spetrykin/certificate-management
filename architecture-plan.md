# Architecture Plan

## Domain Model

Four entities: `Certificate`, `Device`, `RenewalTask`, `CertificateAuditLog`.

### Certificate

- `id` (PK)
- `serialNumber`
- `commonName`
- `state` (`CertState`, transitioned only via `CertificateStateMachine`)
- `issuedAt`
- `expiresAt`
- `version` (`@Version`, optimistic locking)
- Relationship: `Certificate` → `Device` (many-to-one; a certificate is issued for one device)

### Device

- `id` (PK)
- `identifier` (kept minimal per the one-week cut list — no metadata, no device-level endpoints)

### RenewalTask

- `id` (PK)
- `certificate` (FK → `Certificate`, many-to-one)
- `createdAt`
- `status`
- Relationship: `RenewalTask` → `Certificate` (many-to-one)

### CertificateAuditLog

- `id` (PK)
- `certificate` (FK → `Certificate`, many-to-one)
- `fromState`
- `toState`
- `timestamp`
- `actor`
- Relationship: `CertificateAuditLog` → `Certificate` (many-to-one)

## State Machine

### ALLOWED_TRANSITIONS

| From                    | To                                          |
|-------------------------|----------------------------------------------|
| PENDING_CSR              | ISSUED                                       |
| ISSUED                   | ACTIVE                                       |
| ACTIVE                   | EXPIRING_SOON                                |
| EXPIRING_SOON             | RENEWAL_IN_PROGRESS                          |
| RENEWAL_IN_PROGRESS       | RENEWED, EXPIRED, REVOKED                    |
| RENEWAL_IN_PROGRESS       | EXPIRING_SOON (failure path)                 |

### Terminal States

- `EXPIRED`
- `REVOKED`

### Reasoning for the Failure-Path Transition

`RENEWAL_IN_PROGRESS` can fail back to `EXPIRING_SOON` because a renewal attempt (e.g., CA unreachable, CSR rejected, transient infrastructure failure) is not guaranteed to succeed. Rather than stranding the certificate in an in-progress state or forcing it directly to a terminal state on any failure, the state machine allows it to fall back to `EXPIRING_SOON` so the scheduled scanner and application logic can retry the renewal through the normal path. This models renewal as a resumable process rather than a one-shot operation.

## Concurrency

### Race Scenario

Multiple scheduled-job scans (e.g., overlapping cron executions, multiple app instances) can concurrently query for certificates in `EXPIRING_SOON` and each attempt to transition the same certificate to `RENEWAL_IN_PROGRESS` and create a corresponding `RenewalTask`. Without a guard, this results in duplicate transitions and/or duplicate renewal tasks for the same certificate.

### Why Optimistic Locking Is the Primary Guard

`Certificate` carries a `@Version` column. Each state transition is a read-modify-write on the certificate row; when two concurrent transactions attempt to transition the same certificate, the second commit fails with an optimistic lock exception because the version it read is stale. This is the primary concurrency guard because the state transition itself is the point of contention, and JPA optimistic locking directly protects that write.

### Why RenewalTask Uniqueness Is Secondary/Application-Level

`RenewalTask` uniqueness (at most one active renewal task per certificate) is enforced in the application/service layer rather than via a database constraint. MySQL/InnoDB does not support partial unique indexes (unlike PostgreSQL), so a straightforward "unique per certificate where status = active" constraint cannot be expressed directly at the DB level. Since the optimistic lock on `Certificate` already prevents the double state-transition that would otherwise trigger duplicate task creation, application-level uniqueness checking on `RenewalTask` is a secondary, defense-in-depth measure rather than the primary safeguard.

### Deferred: MySQL Generated-Column Unique-Index Trick

A MySQL generated-column trick (e.g., a generated column that collapses to a constant value only when `status = 'ACTIVE'`, with a unique index on that column, to emulate a partial unique index) was considered as a way to enforce `RenewalTask` uniqueness at the database level. This was explicitly deferred: it adds schema complexity disproportionate to the one-week timeline and is not required given that optimistic locking on `Certificate` already closes the primary race. It may be revisited post-timeline if stronger DB-level guarantees are desired.

### Finding: a Second Failure Mode Beyond the Version Mismatch

Manual verification with the demo script (`demo/demo.sh`), not the automated test suite — the same category of discovery as the `/error` bug found during Day 6's curl verification — surfaced a real second concurrency failure mode: two truly-simultaneous `POST /api/certificates/{id}/transitions` requests against the same certificate can trigger a genuine MySQL/InnoDB deadlock (`CannotAcquireLockException`, `Deadlock found when trying to get lock; try restarting transaction`) on the `UPDATE certificate ...` / `INSERT certificate_audit_log ...` pair, rather than the expected stale-version `ObjectOptimisticLockingFailureException`. This is a different mechanism from a stale read — it's InnoDB's own row-lock contention detector, not JPA's version check — and `ApiExceptionHandler` didn't catch it, so it fell through to the generic 500 handler as an opaque, misleading "unexpected error" instead of the same 409 the version-mismatch case gets.

Fixed by widening `ApiExceptionHandler`'s handler from `ObjectOptimisticLockingFailureException` to its shared ancestor `org.springframework.dao.ConcurrencyFailureException` — confirmed against the real Spring Framework 7.0.8 jars that both `OptimisticLockingFailureException` (and its subclass `ObjectOptimisticLockingFailureException`) and `PessimisticLockingFailureException` (and its subclass `CannotAcquireLockException`) descend from `ConcurrencyFailureException`, so this one handler now covers both cases with the same "retry, someone else touched this concurrently" response, and the existing optimistic-lock coverage is an unchanged strict subset of the new handler's scope.

## Security

- JWT-based authentication (`JwtService`, `jjwt` 0.13.0) via a stateless Spring Security filter chain (`SecurityConfig`). Signing key is sourced exclusively from the `JWT_SIGNING_KEY` environment variable, no default — a missing value fails startup loudly rather than falling back to something guessable, the same pattern used for DB credentials (see `application-local.yml.example`).
- `JwtAuthenticationFilter` is deliberately not a Spring bean — it's constructed directly by `SecurityConfig` and wired in via `addFilterBefore`. Making it a `@Component` was tried and reverted: Spring Boot's generic servlet-filter auto-registration would then add it a second time ahead of the security chain, where anything it set on the `SecurityContextHolder` got silently overwritten before the chain's own pass ran. Documented in the filter's own Javadoc.
- Roles: `ADMIN`, `VIEWER`. Authorization convention: everything under `/api/**` requires authentication; `GET` requires `ADMIN` or `VIEWER`, every other method requires `ADMIN`.
- `/error` is explicitly `permitAll`. Found during Day 6's manual curl verification, not by any `@WebMvcTest` slice (MockMvc never exercises Spring Boot's real container-level error-page forward): an authenticated-but-wrong-role request correctly got a 403 from `AccessDeniedHandlerImpl`, but the client actually received a 401. Root cause — Spring Boot's default error handling internally forwards the already-decided response to `/error`, which re-enters the same filter chain as a fresh, anonymous request; without an explicit `permitAll`, that unrelated second pass failed authentication and its 401 silently overwrote the correct 403. This is the concrete example, beyond any abstract argument, of why the REST layer needs to be exercised against a real running server at least once rather than trusted to slice tests alone.
- Refresh token rotation and revocation are explicitly out of scope — deferred as a separate session-management concern, not core to certificate lifecycle logic.

## API

All endpoints below are mounted under `/api/**` except `/auth/login`. Controllers call only their respective services — no repository is ever injected into a controller; the service layer is the sole layer that talks to repositories. Request/response bodies are DTOs (`dto` package, plain records with a static `from(entity)` factory each) — JPA entities are never serialized directly. `CertificateResponse` deliberately omits `version`: it's a JPA/optimistic-locking implementation detail, not something an API consumer needs to see or set.

| Method | Path                              | Auth         | Body                    | Notes |
|--------|------------------------------------|--------------|-------------------------|-------|
| POST   | `/auth/login`                      | none         | username/password       | Returns a JWT on success, 401 otherwise. |
| POST   | `/api/devices`                     | `ADMIN`      | `DeviceCreateRequest`   | 201 + `DeviceResponse`. |
| GET    | `/api/devices/{id}`                | `ADMIN`/`VIEWER` | —                    | 404 via `DeviceNotFoundException` if missing. |
| POST   | `/api/certificates`                | `ADMIN`      | `CertificateCreateRequest` | 201 + `CertificateResponse`; 404 if the device doesn't exist. |
| GET    | `/api/certificates/{id}`           | `ADMIN`/`VIEWER` | —                    | 404 via `CertificateNotFoundException` if missing. |
| GET    | `/api/certificates`                | `ADMIN`/`VIEWER` | —                    | Unfiltered `findAll()` — no pagination, out of scope for this timeline. |
| POST   | `/api/certificates/{id}/transitions` | `ADMIN`    | `TransitionRequest`     | Drives `CertificateService.transitionCertificate`; 409 on an illegal transition or a concurrent-modification conflict. |

Errors are RFC 7807 `ProblemDetail` responses via `ApiExceptionHandler` (`@RestControllerAdvice`): `CertificateNotFoundException`/`DeviceNotFoundException` → 404; `IllegalStateTransitionException` → 409; `ObjectOptimisticLockingFailureException` → 409 as well — confirmed reachable from `POST /api/certificates/{id}/transitions` (two concurrent requests transitioning the same certificate race on `Certificate.version` exactly as the Concurrency section's scheduler race does, just driven by HTTP requests instead of scan invocations); Bean Validation failures on `@RequestBody` → 400 with field-level detail; any other unhandled `RuntimeException` → 500, logged server-side, with a generic detail message that never leaks the exception's own message into the response body. Errors originating in the security filter chain itself (401/403, e.g. missing or insufficient-role tokens) are not routed through this handler — they're decided before the request ever reaches a controller — and come back as Spring Security's own default bodies, not `ProblemDetail`.

Interactive API docs: springdoc-openapi serves Swagger UI at `/swagger-ui.html` once the app is running.

## Deployment

- Local-only via `docker compose up --build` (`Dockerfile`, multi-stage: `eclipse-temurin:25-jdk` build stage running `./mvnw package`, `eclipse-temurin:25-jre` runtime stage; `docker-compose.yml`: `app` + `mysql` services, `app` gated on `mysql`'s healthcheck via `depends_on: condition: service_healthy`). Verified end-to-end: image builds, MySQL initializes and creates the app database/user, Flyway migrates cleanly, the app starts, and both `/auth/login` and `/swagger-ui.html` respond from the host.
- All environment-specific configuration (`JWT_SIGNING_KEY`, `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`, the `mysql` image's own `MYSQL_ROOT_PASSWORD`/`MYSQL_DATABASE`/`MYSQL_USER`/`MYSQL_PASSWORD`) comes from `application-local.yml` (gitignored; `application-local.yml.example` documents the required keys), wired in as `env_file` for both services — never hardcoded in `docker-compose.yml`.
- Known caveat, not fixed given deployment infrastructure is explicitly out of scope this week (see below): the mysql image's entrypoint briefly runs a "temporary server" (used only to execute init SQL — create the database/user) before restarting as the real, persistent server, and the healthcheck (`mysqladmin ping`) can in principle succeed against that temporary server, letting `depends_on` report `mysql` healthy slightly before the persistent server is actually the one serving connections. In practice the app's own startup time (Spring context + Tomcat init) comfortably absorbed this window in testing, but it isn't a guaranteed-safe ordering. The healthcheck intentionally validates reachability only, not credentials — see docker-compose.yml's inline comment for why.
- Flyway migrations run automatically on app startup.
- Rationale: within a one-week build window, deployment infrastructure is out of scope — the differentiating engineering content is the state machine, concurrency handling, and transactional audit design.

Testcontainers (used for tests, and for the `TestCertificateManagementApplication` dev-run entrypoint used throughout the week's manual verification) is pinned to `mysql:8.4.11` (LTS), not `latest` — matched by the same pin in `docker-compose.yml`. As of April 2026, MySQL 8.0 reached End of Life; MySQL 8.4 is the current LTS release in the 8.x line and matches the InnoDB/utf8mb4_0900_ai_ci dialect assumptions documented in V1__init_schema.sql. Pinning to a specific LTS version (rather than `latest`, which resolves to whatever Innovation release is current — e.g. 26.7 as of this writing, under MySQL's new calendar-versioning scheme) keeps the test environment — and now the deployment environment — reproducible across machines and over time.
