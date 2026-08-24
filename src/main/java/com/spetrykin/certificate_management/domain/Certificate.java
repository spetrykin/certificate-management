package com.spetrykin.certificate_management.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * Matches the {@code certificate} table in V1__init_schema.sql.
 * <p>
 * {@code state} has no public mutator. It can only be changed by
 * {@link CertificateStateMachine#transition}, which lives in this same
 * package and calls the package-private {@link #changeState(CertState)}.
 * This is what CLAUDE.md means by "no direct setState() exposure."
 */
@Entity
@Table(
        name = "certificate",
        uniqueConstraints = @UniqueConstraint(name = "uk_certificate_serial_number", columnNames = "serial_number")
)
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "common_name", nullable = false)
    private String commonName;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 30)
    private CertState state;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Certificate() {
        // required by JPA
    }

    public Certificate(Long deviceId, String commonName, CertState state) {
        this.deviceId = deviceId;
        this.commonName = commonName;
        this.state = state;
    }

    /**
     * Package-private on purpose: the only caller allowed to invoke this is
     * {@link CertificateStateMachine#transition}, and it being in the same
     * package is what makes that possible without a public setState().
     */
    void changeState(CertState newState) {
        this.state = newState;
    }

    public Long getId() {
        return id;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public CertState getState() {
        return state;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getVersion() {
        return version;
    }
}
