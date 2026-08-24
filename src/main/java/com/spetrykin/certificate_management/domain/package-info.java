/**
 * Domain model: JPA entities ({@code Certificate}, {@code Device}, {@code RenewalTask},
 * {@code CertificateAuditLog}), the {@code CertState} enum, {@code CertificateStateMachine},
 * and domain-specific exceptions.
 * <p>
 * State transitions occur only through {@code CertificateStateMachine.transition()} —
 * no direct {@code setState()} exposure on {@code Certificate}. See architecture-plan.md.
 */
package com.spetrykin.certificate_management.domain;
