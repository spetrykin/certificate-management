# Certificate Management

A backend service for managing the lifecycle of X.509-style certificates issued to devices — from CSR through issuance, activity, expiry, renewal, and revocation. The domain model centers on an explicit certificate state machine, with the engineering focus on what it takes to run that state machine correctly under concurrency: optimistic-locking-guarded state transitions, transactional audit logging that can never drift from the state it's supposed to describe, and a REST API that exposes only what a consumer needs — never the persistence model itself. It's built on Spring Boot with JWT-based authentication and role-based authorization (`ADMIN`/`VIEWER`).

## Tech Stack

Java 25 · Spring Boot 4.1.0 · MySQL/InnoDB · Flyway · Spring Data JPA · Spring Security · jjwt · springdoc-openapi · Testcontainers

## Notable Engineering Decisions

- **Explicit state machine, not scattered `if`s.** All `Certificate` state changes go through `CertificateStateMachine`, driven by a single `ALLOWED_TRANSITIONS` table — there's exactly one place that can decide a transition is legal, and `Certificate.setState()` is not exposed outside it.
- **Optimistic locking as the primary concurrency guard, RenewalTask uniqueness as defense-in-depth.** `Certificate.version` (`@Version`) is what actually stops two concurrent transitions on the same certificate from both succeeding. A secondary application-level uniqueness check on `RenewalTask` backs it up, because MySQL/InnoDB has no partial unique index to enforce "one active task per certificate" at the schema level the way Postgres could.
- **Audit log writes are never separated from the transition they describe.** The state change and its `CertificateAuditLog` row are written in the same `@Transactional` method, always — so the audit trail can't end up out of sync with reality because one write succeeded and the other didn't.
- **A real bug that no test slice would have caught.** Manual curl verification against a running server (not `@WebMvcTest`/MockMvc, which never exercises the container's real error-page forward) found that a correct 403 from `AccessDeniedHandlerImpl` was being silently turned into a misleading 401 — Spring Boot's default `/error` handling forwards the decided response through the filter chain a second time as an anonymous request. Fixed with an explicit `permitAll()` on `/error`. Full root-cause writeup in `architecture-plan.md`.
- **A DTO boundary drawn on purpose.** Controllers never return or accept JPA entities — only DTOs with static `from(entity)` factories. `CertificateResponse` deliberately omits `version`; it's a locking implementation detail, not API surface.

## Running It

**Docker Compose (recommended):**

```bash
cp application-local.yml.example application-local.yml
# edit application-local.yml — fill in real values for JWT_SIGNING_KEY and the DB credentials
docker compose up --build
```

This builds the app image (multi-stage: `eclipse-temurin:25-jdk` to compile, `eclipse-temurin:25-jre` to run), starts MySQL, waits for it to be healthy, then starts the app. Flyway migrates the schema automatically on startup. The app listens on `http://localhost:8080`.

**Without Docker (dev-run against Testcontainers):**

```bash
export JWT_SIGNING_KEY=some-throwaway-256-bit-dev-key-not-for-real-use-00000000
./mvnw spring-boot:test-run
```

(or run `TestCertificateManagementApplication`'s `main` method from your IDE with the same env var set). This spins up a throwaway MySQL container via Testcontainers and wires it in automatically — no DB credentials or `application-local.yml` needed for this path. `JWT_SIGNING_KEY` is the one exception: it has no Testcontainers equivalent to bypass it, so it still needs to be set, same as the Docker Compose path.

## API Docs

Once running, interactive API docs (Swagger UI) are at:

```
http://localhost:8080/swagger-ui.html
```

A terminal walkthrough (login, legal/illegal transitions, validation failure, VIEWER read/write, 401, concurrent transition race) is in [`./demo/demo.sh`](./demo/demo.sh).

## Status

All 7 days of the planned build are complete. This was built in a one-week, CV-readiness scope — see `architecture-plan.md` for full design rationale, including the documented, deliberate cuts: `RenewalTask` retry/backoff logic, a minimal `Device` entity, and no refresh token rotation/revocation.

## Built with Claude Code

This project was developed over one week with Claude Code as the implementing agent, under human architecture, review, and decision-making. The author was the architect and reviewer; the agent handled implementation; all deployments, commits, and pushes were performed by the author.
