/**
 * REST controllers. Consume and return DTOs only — JPA entities are never exposed
 * directly here.
 * <p>
 * {@link com.spetrykin.certificate_management.controller.AuthController} (Day 5)
 * is the sole exception to full dto/-package conventions, deliberately kept
 * minimal — see its Javadoc. Day 6 adds {@link
 * com.spetrykin.certificate_management.controller.DeviceController} and
 * {@link com.spetrykin.certificate_management.controller.CertificateController},
 * both mounted under {@code /api/**} per SecurityConfig's authorization
 * convention, and {@link com.spetrykin.certificate_management.controller.ApiExceptionHandler},
 * the {@code @RestControllerAdvice} mapping domain/validation exceptions to
 * {@link org.springframework.http.ProblemDetail} responses for all
 * controllers in this package.
 */
package com.spetrykin.certificate_management.controller;
