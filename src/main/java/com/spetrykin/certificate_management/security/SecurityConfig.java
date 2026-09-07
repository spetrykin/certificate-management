package com.spetrykin.certificate_management.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless, JWT-only security configuration.
 * <p>
 * <b>Authorization convention for Day 6.</b> Certificate endpoints don't
 * exist yet — this class deliberately defines no placeholder routes for
 * them — but the rule structure below already commits to where and how
 * they'll be gated: mounted under {@code /api/**}, with {@code GET}
 * requiring {@code ADMIN} or {@code VIEWER} and every other method
 * requiring {@code ADMIN}. Day 6's controller needs no SecurityConfig
 * changes to be protected correctly; it only needs to live under that
 * prefix.
 * <p>
 * No actuator health path is permitted here because no actuator dependency
 * is present in this project (see pom.xml). If one is added later, its
 * health endpoint should be added to the permit-all list below.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] SWAGGER_PATHS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private static final String API_PATH_PREFIX = "/api/**";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        // Constructed directly, not injected as a bean — see
        // JwtAuthenticationFilter's Javadoc for why it must not also be a
        // Spring-managed @Component.
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptionHandling ->
                        exceptionHandling.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(SWAGGER_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, API_PATH_PREFIX).hasAnyRole("ADMIN", "VIEWER")
                        .requestMatchers(API_PATH_PREFIX).hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
