package com.spetrykin.certificate_management.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit test, no Spring context: proves the legal/illegal transition
 * behavior of CertificateStateMachine per CLAUDE.md's Day 1 cut-list
 * requirement.
 */
class CertificateStateMachineTest {

    @Test
    void allowsLegalTransitionFromPendingCsrToIssued() {
        Certificate certificate = new Certificate(1L, "device-01.example.com", CertState.PENDING_CSR);

        CertificateStateMachine.transition(certificate, CertState.ISSUED, "test-actor", "CSR approved");

        assertThat(certificate.getState()).isEqualTo(CertState.ISSUED);
    }

    @Test
    void rejectsIllegalTransitionFromPendingCsrToActive() {
        Certificate certificate = new Certificate(1L, "device-01.example.com", CertState.PENDING_CSR);

        assertThatThrownBy(() ->
                CertificateStateMachine.transition(certificate, CertState.ACTIVE, "test-actor", "skip issuance"))
                .isInstanceOf(IllegalStateTransitionException.class);

        assertThat(certificate.getState()).isEqualTo(CertState.PENDING_CSR);
    }
}
