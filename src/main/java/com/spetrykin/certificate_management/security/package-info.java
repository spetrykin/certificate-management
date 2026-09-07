/**
 * JWT-based authentication and authorization (Day 5): {@link
 * com.spetrykin.certificate_management.security.JwtService} issues and
 * validates tokens, {@link com.spetrykin.certificate_management.security.JwtAuthenticationFilter}
 * populates the security context from an incoming request's token, {@link
 * com.spetrykin.certificate_management.security.SecurityConfig} wires the
 * stateless filter chain and authorization rules, and {@link
 * com.spetrykin.certificate_management.security.DemoUserDetailsConfig}
 * provides the one-week-timeline demo user source.
 */
package com.spetrykin.certificate_management.security;
