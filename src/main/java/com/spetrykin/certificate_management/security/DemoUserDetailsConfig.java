package com.spetrykin.certificate_management.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Demo-scope user source: exactly two hardcoded users, one {@code
 * ROLE_ADMIN} and one {@code ROLE_VIEWER}, backed by Spring Security's
 * in-memory {@link InMemoryUserDetailsManager} rather than any persistent
 * store.
 * <p>
 * This is a deliberate one-week-timeline cut, the same category of
 * documented shortcut as {@code Device}'s minimalism (see CLAUDE.md): a
 * real system would back authentication with a persistent user store — its
 * own entity, repository, registration/password-reset flow, and so on —
 * all explicitly out of scope here. What is not cut, even for a demo: the
 * passwords below are BCrypt-encoded through the injected {@link
 * PasswordEncoder} and never stored, compared, or logged as plaintext.
 */
@Configuration
public class DemoUserDetailsConfig {

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("admin")
                        .password(passwordEncoder.encode("admin-demo-pw"))
                        .roles("ADMIN")
                        .build(),
                User.withUsername("viewer")
                        .password(passwordEncoder.encode("viewer-demo-pw"))
                        .roles("VIEWER")
                        .build()
        );
    }
}
