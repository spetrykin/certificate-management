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

## Security

- JWT-based authentication via the Spring Security filter chain.
- Roles: `ADMIN`, `VIEWER`.
- Refresh token rotation and revocation are explicitly out of scope — deferred as a separate session-management concern, not core to certificate lifecycle logic.

## Deployment

- Local-only via `docker-compose up` (app container + MySQL container).
- Flyway migrations run automatically on app startup.
- Rationale: within a one-week build window, deployment infrastructure is out of scope — the differentiating engineering content is the state machine, concurrency handling, and transactional audit design.

Testcontainers is pinned to `mysql:8.4.11` (LTS), not `latest`. As of April 2026, MySQL 8.0 reached End of Life; MySQL 8.4 is the current LTS release in the 8.x line and matches the InnoDB/utf8mb4_0900_ai_ci dialect assumptions documented in V1__init_schema.sql. Pinning to a specific LTS version (rather than `latest`, which resolves to whatever Innovation release is current — e.g. 26.7 as of this writing, under MySQL's new calendar-versioning scheme) keeps the test environment reproducible across machines and over time.
