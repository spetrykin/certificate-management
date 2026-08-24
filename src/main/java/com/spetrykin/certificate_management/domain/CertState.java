package com.spetrykin.certificate_management.domain;

/**
 * Certificate lifecycle states. See {@link CertificateStateMachine} for the
 * allowed transitions between these values.
 */
public enum CertState {
    PENDING_CSR,
    ISSUED,
    ACTIVE,
    EXPIRING_SOON,
    RENEWAL_IN_PROGRESS,
    RENEWED,
    EXPIRED,
    REVOKED
}
