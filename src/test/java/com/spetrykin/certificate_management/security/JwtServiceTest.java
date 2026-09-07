package com.spetrykin.certificate_management.security;

import io.jsonwebtoken.JwtException;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit test, no Spring context. {@link JwtService} is constructed
 * directly with a literal, throwaway test key — this deliberately bypasses
 * the {@code @Value}/{@code JWT_SIGNING_KEY} environment-variable
 * mechanism entirely rather than touching it, per Day 5's requirement that
 * tests never rely on the real signing-key source.
 */
class JwtServiceTest {

    private static final String TEST_SIGNING_KEY =
            "test-only-jwt-signing-key-never-used-outside-this-test-class-0";

    private final JwtService jwtService = new JwtService(TEST_SIGNING_KEY);

    @Test
    void validTokenRoundTripsCorrectly() {
        String token = jwtService.generateToken("alice", List.of("ROLE_ADMIN", "ROLE_VIEWER"));

        JwtService.ValidatedToken validated = jwtService.parseAndValidate(token);

        assertThat(validated.username()).isEqualTo("alice");
        assertThat(validated.roles()).containsExactly("ROLE_ADMIN", "ROLE_VIEWER");
    }

    @Test
    void expiredTokenFailsValidation() {
        // Negative lifetime: the expiration claim is already in the past
        // the instant the token is minted, deterministically, without
        // waiting out the real 1-hour default or manipulating the clock.
        String token = jwtService.generateToken("alice", List.of("ROLE_ADMIN"), Duration.ofSeconds(-1));

        assertThatThrownBy(() -> jwtService.parseAndValidate(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithADifferentKeyFailsValidation() {
        JwtService otherKeyService = new JwtService("different-test-only-signing-key-also-not-for-real-use-0000000");
        String token = otherKeyService.generateToken("alice", List.of("ROLE_ADMIN"));

        assertThatThrownBy(() -> jwtService.parseAndValidate(token))
                .isInstanceOf(JwtException.class);
    }
}
