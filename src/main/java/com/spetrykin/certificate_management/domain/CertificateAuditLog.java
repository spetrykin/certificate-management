package com.spetrykin.certificate_management.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Matches the {@code certificate_audit_log} table in V1__init_schema.sql.
 * {@code occurredAt} corresponds to that migration's {@code occurred_at}
 * column (named to avoid clashing with the SQL TIMESTAMP type, see the
 * migration's comments).
 * <p>
 * Deliberately has no setters: an audit log entry is a record of something
 * that already happened and should never be mutated after it is written.
 * Per CLAUDE.md, an entry is only ever created inside the same
 * {@code @Transactional} method as the state transition it records.
 */
@Entity
@Table(name = "certificate_audit_log")
public class CertificateAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "certificate_id", nullable = false)
    private Long certificateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 30)
    private CertState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 30)
    private CertState toState;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "actor", nullable = false)
    private String actor;

    protected CertificateAuditLog() {
        // required by JPA
    }

    public CertificateAuditLog(
            Long certificateId,
            CertState fromState,
            CertState toState,
            LocalDateTime occurredAt,
            String actor
    ) {
        this.certificateId = certificateId;
        this.fromState = fromState;
        this.toState = toState;
        this.occurredAt = occurredAt;
        this.actor = actor;
    }

    public Long getId() {
        return id;
    }

    public Long getCertificateId() {
        return certificateId;
    }

    public CertState getFromState() {
        return fromState;
    }

    public CertState getToState() {
        return toState;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getActor() {
        return actor;
    }
}
