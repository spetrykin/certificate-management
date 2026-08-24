package com.spetrykin.certificate_management.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * The only path by which a {@link Certificate}'s state may change. Per
 * CLAUDE.md: "State transitions occur only through
 * CertificateStateMachine.transition(); no direct setState() exposure on
 * Certificate."
 * <p>
 * Deliberately contains no transitions beyond what architecture-plan.md
 * specifies (e.g. no ACTIVE -&gt; RENEWED shortcut) — that document is the
 * source of truth for this table.
 */
public final class CertificateStateMachine {

    public static final Map<CertState, Set<CertState>> ALLOWED_TRANSITIONS = buildAllowedTransitions();

    private static Map<CertState, Set<CertState>> buildAllowedTransitions() {
        Map<CertState, Set<CertState>> transitions = new EnumMap<>(CertState.class);
        transitions.put(CertState.PENDING_CSR, Set.of(CertState.ISSUED));
        transitions.put(CertState.ISSUED, Set.of(CertState.ACTIVE));
        transitions.put(CertState.ACTIVE, Set.of(CertState.EXPIRING_SOON, CertState.REVOKED));
        transitions.put(CertState.EXPIRING_SOON,
                Set.of(CertState.RENEWAL_IN_PROGRESS, CertState.EXPIRED, CertState.REVOKED));
        transitions.put(CertState.RENEWAL_IN_PROGRESS,
                Set.of(CertState.RENEWED, CertState.EXPIRING_SOON, CertState.REVOKED));
        transitions.put(CertState.RENEWED, Set.of(CertState.EXPIRING_SOON, CertState.REVOKED));
        transitions.put(CertState.EXPIRED, Set.of());
        transitions.put(CertState.REVOKED, Set.of());
        return Collections.unmodifiableMap(transitions);
    }

    private CertificateStateMachine() {
        // static utility, not instantiable
    }

    /**
     * Validates {@code target} against {@link #ALLOWED_TRANSITIONS} for
     * {@code cert}'s current state and, if legal, applies it.
     *
     * @param cert   the certificate to transition
     * @param target the desired new state
     * @param actor  who/what is requesting the transition (included in the
     *               exception message on failure; threaded through for the
     *               Day 2 service layer to use when writing the
     *               corresponding audit log entry in the same transaction)
     * @param reason why the transition is being requested (same as actor:
     *               carried for the caller's audit log write)
     * @throws IllegalStateTransitionException if target is not allowed from
     *                                          cert's current state
     */
    public static void transition(Certificate cert, CertState target, String actor, String reason) {
        CertState current = cert.getState();
        Set<CertState> allowedTargets = ALLOWED_TRANSITIONS.get(current);
        if (allowedTargets == null || !allowedTargets.contains(target)) {
            throw new IllegalStateTransitionException(
                    "Illegal certificate state transition: " + current + " -> " + target
                            + " (actor=" + actor + ", reason=" + reason + ")"
            );
        }
        cert.changeState(target);
    }
}
