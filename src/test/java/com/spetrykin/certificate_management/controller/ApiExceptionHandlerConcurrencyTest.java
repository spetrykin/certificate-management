package com.spetrykin.certificate_management.controller;

import com.spetrykin.certificate_management.domain.CertState;
import com.spetrykin.certificate_management.domain.Certificate;
import com.spetrykin.certificate_management.dto.TransitionRequest;
import com.spetrykin.certificate_management.security.DemoUserDetailsConfig;
import com.spetrykin.certificate_management.security.JwtService;
import com.spetrykin.certificate_management.security.JwtTestSupport;
import com.spetrykin.certificate_management.security.SecurityConfig;
import com.spetrykin.certificate_management.service.CertificateService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer proof that {@link ApiExceptionHandler} maps {@code
 * ConcurrencyFailureException} subclasses to a 409 ProblemDetail, and that
 * the mapping is not a catch-all (a plain {@link RuntimeException} still
 * falls through to the generic 500).
 * <p>
 * Same {@code @WebMvcTest} slice as {@link CertificateControllerTest} — no
 * database involved, so no Testcontainers: {@link CertificateService} is
 * mocked and each test makes it throw. Security stays fully enabled; the
 * ADMIN token comes from a real POST /auth/login round trip via {@link
 * JwtTestSupport}. {@code @RestControllerAdvice} beans are part of the
 * {@code @WebMvcTest} slice automatically.
 */
@WebMvcTest(controllers = {CertificateController.class, AuthController.class})
@Import({SecurityConfig.class, DemoUserDetailsConfig.class, JwtService.class})
@TestPropertySource(properties = "jwt.signing-key=test-only-jwt-signing-key-for-concurrency-handler-test-000")
class ApiExceptionHandlerConcurrencyTest {

    private static final String TRANSITION_PATH = "/api/certificates/7/transitions";
    private static final String CONCURRENT_DETAIL = "modified concurrently";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CertificateService certificateService;

    private String adminToken() throws Exception {
        return JwtTestSupport.login(mockMvc, objectMapper, "admin", "admin-demo-pw");
    }

    private String transitionBody() throws Exception {
        return objectMapper.writeValueAsString(new TransitionRequest(CertState.EXPIRING_SOON, "admin", "race"));
    }

    @Test
    void optimisticLockingFailureMapsTo409() throws Exception {
        when(certificateService.transitionCertificate(eq(7L), eq(CertState.EXPIRING_SOON), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Certificate.class, 7L));

        mockMvc.perform(post(TRANSITION_PATH)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(CONCURRENT_DETAIL)));
    }

    @Test
    void deadlockCannotAcquireLockMapsTo409() throws Exception {
        when(certificateService.transitionCertificate(eq(7L), eq(CertState.EXPIRING_SOON), any(), any()))
                .thenThrow(new CannotAcquireLockException("Deadlock found when trying to get lock"));

        mockMvc.perform(post(TRANSITION_PATH)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(CONCURRENT_DETAIL)));
    }

    @Test
    void plainRuntimeExceptionStillMapsTo500() throws Exception {
        when(certificateService.transitionCertificate(eq(7L), eq(CertState.EXPIRING_SOON), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post(TRANSITION_PATH)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionBody()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(CONCURRENT_DETAIL))));
    }
}
