package com.spetrykin.certificate_management.domain;

/**
 * Thrown by {@link CertificateStateMachine#transition} when the requested
 * target state is not in the allowed-transitions set for the certificate's
 * current state.
 */
public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(String message) {
        super(message);
    }
}
