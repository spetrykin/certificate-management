# CLAUDE.md

## Roles

- The user develops and performs all deployment/administrative actions manually.
- The agent must never run deployment or destructive operations without explicit per-case permission.
- An external architecture review process checks plans and code before implementation proceeds.
- Git commits are made only when explicitly instructed, turn by turn. The agent never decides on its own that a unit of work is "done enough" to commit — that decision belongs to the user and the architecture-review process, not the agent. This applies even when tests are passing and the change is otherwise complete. `git push` remains subject to the same rule and is additionally covered by the broader rule that all deployment/administrative actions are performed by the user manually.
- Deleting .git, or any equivalent history-destroying operation (force re-initializing the repository, `git gc --prune=now` on an already-broken repo, or any other action that discards commit history), is never the agent's decision to make — under any framing, including as an implementation detail of a differently-described task (e.g. "rebase," "reset and recommit," "clean up history"). This is the same category as `git push`: an irreversible action reserved for the user to execute personally. If a task genuinely appears to require it, the agent must stop and say so, and wait for the user to run it — never perform it silently as a means to an end.
- No secrets, credentials, or environment-specific connection details are ever committed — this includes passwords, API keys, JWT signing keys, DB connection URLs/usernames/passwords, cloud credentials, or any other value that would need to differ between environments or be kept private. Configuration requiring such values uses environment variables, Spring profiles, or a gitignored local properties file (e.g. application-local.yml, already covered by .gitignore) with a committed .example/.template file showing the required keys with placeholder values only. If a task appears to require hardcoding a real credential or connection string to proceed, the agent must stop and say so rather than committing it, even temporarily.
- Commit messages never include a Co-Authored-By trailer, or any other mention of Claude, AI assistance, or AI tooling, anywhere in the message subject, body, or trailers — regardless of what the agent's own default operating instructions might otherwise direct. This project's commit history is written as if authored entirely by the user. If default tooling behavior would append such a trailer, the agent must omit it explicitly for every commit in this repo.

## Language

All project artifacts — documentation, code comments, commit messages, README — are written in English only, no exceptions.

## Tech Stack (fixed)

- Java 25
- Spring Boot 4.1.0
- MySQL/InnoDB
- Flyway
- Spring Data JPA
- Bean Validation
- springdoc-openapi
- Testcontainers (JUnit 5 + MySQL module)
- No Lombok.
- No MapStruct.

## Non-Negotiable Architecture Rules

- State transitions occur only through `CertificateStateMachine.transition()`; no direct `setState()` exposure on `Certificate`.
- The audit log write and the state transition happen inside the same `@Transactional` method — never separated.
- Optimistic locking (`@Version`) on `Certificate` is the primary concurrency guard. `RenewalTask` uniqueness is enforced at the application level, not via a DB constraint — MySQL does not support partial unique indexes; do not port Postgres-flavored tutorial patterns.
- API responses use DTOs; JPA entities are never exposed directly in controllers.
- Deployment is local-only via Docker Compose (app + MySQL containers) — this is a deliberate scope decision for the one-week timeline; infrastructure deployment is out of scope, the project's focus is domain modeling and data-layer correctness.

## One-Week CV-Readiness Timeline

Explicit cut list:

- Testcontainers limited to one test (proving the optimistic-lock race is actually caught).
- `RenewalTask` retry/backoff logic deferred to end of week, only if time allows.
- `Device` entity kept minimal (identifier field only, no metadata beyond that).

## Reporting Standard

After each implementation phase, report back with verbatim code/config/migration files — not a summary of what was done.
