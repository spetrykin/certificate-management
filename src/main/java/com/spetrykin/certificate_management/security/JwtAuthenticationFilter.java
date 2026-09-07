package com.spetrykin.certificate_management.security;

import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads {@code Authorization: Bearer <token>} and, if present and valid,
 * populates the {@link SecurityContextHolder} with the authenticated user
 * and their authorities via {@link JwtService}.
 * <p>
 * Deliberately never rejects a request itself: an absent or invalid token
 * just leaves the security context unauthenticated, and it's
 * {@code authorizeHttpRequests} (see {@link SecurityConfig}) that decides
 * downstream whether that's acceptable for the requested path. Keeping
 * "is this token valid" and "is this request allowed" as separate concerns
 * is what lets an unauthenticated request to a permit-all path (e.g.
 * {@code /auth/login}) pass through this filter untouched instead of being
 * rejected here before authorization rules even get a say.
 * <p>
 * Deliberately <b>not</b> a {@code @Component}: {@link SecurityConfig}
 * constructs it directly and places it in the chain via {@code
 * addFilterBefore}. Also registering it as a generic Spring bean would make
 * Spring Boot's servlet-filter auto-registration add it a second time,
 * running once ahead of Spring Security's own chain — where anything it
 * sets on the {@link SecurityContextHolder} gets overwritten moments later
 * by {@code SecurityContextHolderFilter} establishing a fresh context for
 * the request — and then a second time inside the chain, where {@code
 * OncePerRequestFilter}'s own already-filtered guard silently skips it. Net
 * effect: no request is ever actually authenticated, with no error to
 * point at why. Confirmed by hitting exactly this failure mode with
 * {@code @Component} present before settling on this approach.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                JwtService.ValidatedToken validated = jwtService.parseAndValidate(token);
                List<SimpleGrantedAuthority> authorities = validated.roles().stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(validated.username(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                // IllegalArgumentException alongside JwtException: an empty
                // token (Authorization: "Bearer " with nothing after it)
                // doesn't reach jjwt's own format/signature checks at all —
                // jjwt's internal Assert.hasText rejects it first with a
                // plain IllegalArgumentException, which does NOT extend
                // JwtException. Confirmed empirically: catching only
                // JwtException left that case as an uncaught exception,
                // which would 500 instead of leaving the request
                // unauthenticated for authorizeHttpRequests to reject with
                // 401/403 like every other invalid-token case.
                log.debug("Ignoring invalid JWT on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
