package com.spetrykin.certificate_management.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Kept intentionally minimal for the one-week timeline: identifier only, no
 * metadata, no device-level endpoints (see CLAUDE.md cut list). Matches
 * the {@code device} table in V1__init_schema.sql.
 */
@Entity
@Table(
        name = "device",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_identifier", columnNames = "identifier")
)
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "identifier", nullable = false)
    private String identifier;

    protected Device() {
        // required by JPA
    }

    public Device(String identifier) {
        this.identifier = identifier;
    }

    public Long getId() {
        return id;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
}
