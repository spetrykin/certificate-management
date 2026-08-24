package com.spetrykin.certificate_management.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Matches the {@code renewal_task} table in V1__init_schema.sql. No unique
 * constraint on certificateId at this layer either — uniqueness (at most one
 * active task per certificate) is an application-level rule enforced by the
 * service layer on Day 2/Day 4, not a DB or entity-level constraint. See
 * architecture-plan.md, Concurrency section.
 */
@Entity
@Table(name = "renewal_task")
public class RenewalTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "certificate_id", nullable = false)
    private Long certificateId;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected RenewalTask() {
        // required by JPA
    }

    public RenewalTask(Long certificateId, String status, LocalDateTime createdAt) {
        this.certificateId = certificateId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getCertificateId() {
        return certificateId;
    }

    public void setCertificateId(Long certificateId) {
        this.certificateId = certificateId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
