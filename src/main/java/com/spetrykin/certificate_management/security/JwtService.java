package com.spetrykin.certificate_management.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Issues and validates signed JWTs.
 * <p>
 * The signing key comes exclusively from the {@code jwt.signing-key}
 * property, which application.yaml binds to the {@code JWT_SIGNING_KEY}
 * environment variable with no default (see application-local.yml.example)
 * — a missing value fails application startup loudly rather than falling
 * back to something guessable. Never hardcode a signing key anywhere,
 * including tests: tests pass their own throwaway key straight to this
 * class's constructor, bypassing the {@code @Value}/environment-variable
 * mechanism entirely rather than touching it.
 */
@Service
public class JwtService {

    /**
     * Default token lifetime. One hour: long enough that a demo session
     * doesn't force re-authentication every few minutes, short enough to
     * bound how long a leaked token stays usable — a real constraint here
     * since this one-week scope has no refresh-token mechanism at all (see
     * architecture-plan.md, Security section), so whatever is issued has to
     * remain valid for the entire time it's needed with no way to renew it
     * short of logging in again.
     */
    static final Duration DEFAULT_TOKEN_LIFETIME = Duration.ofHours(1);

    private static final String ROLES_CLAIM = "roles";

    private final SecretKey signingKey;

    public JwtService(@Value("${jwt.signing-key}") String signingKey) {
        this.signingKey = Keys.hmacShaKeyFor(signingKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Issues a signed JWT for {@code username} carrying {@code roles} (e.g.
     * {@code ["ROLE_ADMIN"]}) as a claim, issued now and expiring after
     * {@link #DEFAULT_TOKEN_LIFETIME}.
     */
    public String generateToken(String username, List<String> roles) {
        return generateToken(username, roles, DEFAULT_TOKEN_LIFETIME);
    }

    /**
     * Package-private overload exposing an explicit {@code lifetime}, used
     * by tests to construct an already-expired token deterministically —
     * without waiting out {@link #DEFAULT_TOKEN_LIFETIME} or manipulating
     * the system clock. Not part of the public API surface.
     */
    String generateToken(String username, List<String> roles, Duration lifetime) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(ROLES_CLAIM, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and validates {@code token} against the configured signing
     * key, returning its subject (username) and roles claim.
     *
     * @throws JwtException if the signature is invalid, the token is
     *                       malformed, or it has expired
     */
    public ValidatedToken parseAndValidate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get(ROLES_CLAIM, List.class);
        return new ValidatedToken(claims.getSubject(), roles);
    }

    /**
     * Result of successfully parsing and validating a token.
     */
    public record ValidatedToken(String username, List<String> roles) {
    }
}
