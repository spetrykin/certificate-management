-- V1__init_schema.sql
-- Flyway baseline migration.
--
-- Dialect: MySQL 8.x / InnoDB, written explicitly for that engine.
-- Do NOT port Postgres-flavored patterns here (SERIAL, partial unique indexes,
-- etc.) — see architecture-plan.md, Concurrency section, for why the
-- renewal_task uniqueness rule below is deliberately application-level rather
-- than a DB constraint.
--
-- Tables are created in FK dependency order: device, certificate,
-- renewal_task, certificate_audit_log.

-- ---------------------------------------------------------------------------
-- device
-- Kept intentionally minimal for the one-week timeline: identifier only,
-- no metadata, no device-level endpoints (see CLAUDE.md cut list).
-- ---------------------------------------------------------------------------
CREATE TABLE device (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    identifier VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_identifier (identifier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- certificate
-- version: optimistic-locking column backing JPA's @Version. This is the
-- primary concurrency guard for state transitions (see architecture-plan.md).
--
-- serial_number is nullable: a certificate has no CA-issued serial number
-- until it leaves PENDING_CSR. InnoDB unique indexes permit multiple NULLs
-- (NULLs are not considered equal to one another), so uniqueness is still
-- enforced once a serial number is actually assigned.
--
-- issued_at / expires_at are nullable for the same reason: unset while the
-- certificate is still in PENDING_CSR.
--
-- state stores the CertState enum name (VARCHAR, not ordinal) for
-- readability in ad-hoc queries; longest current value is
-- RENEWAL_IN_PROGRESS (20 chars), so 30 leaves headroom.
-- ---------------------------------------------------------------------------
CREATE TABLE certificate (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    device_id     BIGINT       NOT NULL,
    serial_number VARCHAR(255) NULL,
    common_name   VARCHAR(255) NOT NULL,
    state         VARCHAR(30)  NOT NULL,
    issued_at     DATETIME(6)  NULL,
    expires_at    DATETIME(6)  NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_certificate_serial_number (serial_number),
    KEY idx_certificate_device_id (device_id),
    KEY idx_certificate_state (state),
    CONSTRAINT fk_certificate_device
        FOREIGN KEY (device_id) REFERENCES device (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- renewal_task
-- No unique constraint on certificate_id. MySQL/InnoDB has no partial unique
-- index, so "at most one active renewal task per certificate" cannot be
-- expressed as a plain column-level uniqueness rule without also blocking
-- legitimate historical/completed tasks for the same certificate. This is
-- enforced at the application level instead; optimistic locking on
-- certificate.version is the primary guard against the concurrent-scan race
-- that would otherwise create duplicates. A generated-column unique-index
-- trick to emulate a partial index was considered and explicitly deferred
-- (see architecture-plan.md).
-- ---------------------------------------------------------------------------
CREATE TABLE renewal_task (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    certificate_id BIGINT      NOT NULL,
    status         VARCHAR(30) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_renewal_task_certificate_id (certificate_id),
    CONSTRAINT fk_renewal_task_certificate
        FOREIGN KEY (certificate_id) REFERENCES certificate (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- certificate_audit_log
-- from_state is nullable to represent the initial creation event (no prior
-- state to record). to_state and actor are always known.
--
-- The "timestamp" field from architecture-plan.md is named occurred_at here
-- rather than `timestamp`, to avoid any ambiguity with the SQL TIMESTAMP
-- type/function of the same name — a naming choice, not a schema deviation.
--
-- Per CLAUDE.md: this table is only ever written inside the same
-- @Transactional method as the state transition it records — never
-- separated.
-- ---------------------------------------------------------------------------
CREATE TABLE certificate_audit_log (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    certificate_id BIGINT      NOT NULL,
    from_state     VARCHAR(30) NULL,
    to_state       VARCHAR(30) NOT NULL,
    occurred_at    DATETIME(6) NOT NULL,
    actor          VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_certificate_audit_log_certificate_id (certificate_id),
    CONSTRAINT fk_certificate_audit_log_certificate
        FOREIGN KEY (certificate_id) REFERENCES certificate (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
