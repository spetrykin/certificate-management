package com.spetrykin.certificate_management.domain;

/**
 * Thrown when a {@link Certificate} lookup by id finds nothing. Lives
 * alongside {@link IllegalStateTransitionException} as a domain exception,
 * distinct from the state-machine's own validation failure.
 */
public class CertificateNotFoundException extends RuntimeException {

    public CertificateNotFoundException(Long certificateId) {
        super("Certificate not found: id=" + certificateId);
    }
}
