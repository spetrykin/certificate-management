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
 * <b>Authorization convention.</b> Certificate/device endpoints are mounted
 * under {@code /api/**}: {@code GET} requires {@code ADMIN} or
 * {@code VIEWER}, every other method requires {@code ADMIN}.
 * <p>
 * No actuator health path is permitted here because no actuator dependency
 * is present in this project (see pom.xml). If one is added later, its
 * health endpoint should be added to the permit-all list below.
 * <p>
 * <b>{@code /error} must be permitAll.</b> Found the hard way during Day
 * 6's curl verification: an authenticated-but-wrong-role request correctly
 * got a 403 from {@code AccessDeniedHandlerImpl} — visible in the security
 * debug log — but the client actually received a 401. Spring Boot's
 * default error handling then internally forwards the already-403'd
 * response to {@code /error} (its default error page controller), and
 * that forwarded request re-enters this same filter chain as a fresh,
 * anonymous request. Without an explicit permitAll for {@code /error}, it
 * falls through to {@code anyRequest().authenticated()}, fails again, and
 * that second failure is what actually lands in the response — silently
 * overwriting the correct 403 with a misleading 401. Confirmed by
 * reproducing with {@code logging.level.org.springframework.security=DEBUG}
 * and reading the two-request sequence directly.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] SWAGGER_PATHS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private static final String ERROR_PATH = "/error";

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
                        .requestMatchers(ERROR_PATH).permitAll()
                        .requestMatchers(HttpMethod.GET, API_PATH_PREFIX).hasAnyRole("ADMIN", "VIEWER")
                        .requestMatchers(API_PATH_PREFIX).hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
