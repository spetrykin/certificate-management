package com.spetrykin.certificate_management.controller;

import com.spetrykin.certificate_management.domain.CertState;
import com.spetrykin.certificate_management.domain.Certificate;
import com.spetrykin.certificate_management.domain.CertificateNotFoundException;
import com.spetrykin.certificate_management.domain.DeviceNotFoundException;
import com.spetrykin.certificate_management.domain.IllegalStateTransitionException;
import com.spetrykin.certificate_management.dto.CertificateCreateRequest;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest}, not {@code @SpringBootTest} — same reasoning as
 * {@code AuthControllerSecurityTest}: the web + security layer through real
 * HTTP requests, nothing from persistence. {@link CertificateService} is
 * mocked via {@code @MockitoBean} (Spring's current replacement for the
 * deprecated {@code @MockBean}) — the only dependency {@link
 * CertificateController} has, now that get/list go through the service
 * too instead of a repository. {@link AuthController} is included in the
 * controller slice, and {@link JwtTestSupport} reused from Day 5, so tests
 * can obtain a real ADMIN or VIEWER JWT via an actual POST /auth/login
 * round trip rather than fabricating a SecurityContext.
 */
@WebMvcTest(controllers = {CertificateController.class, AuthController.class})
@Import({SecurityConfig.class, DemoUserDetailsConfig.class, JwtService.class})
@TestPropertySource(properties = "jwt.signing-key=test-only-jwt-signing-key-for-certificate-controller-test-000")
class CertificateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CertificateService certificateService;

    private static Certificate certificateWithId(Long id, CertState state) {
        Certificate certificate = new Certificate(1L, "device-01.example.com", state);
        ReflectionTestUtils.setField(certificate, "id", id);
        return certificate;
    }

    private String adminToken() throws Exception {
        return JwtTestSupport.login(mockMvc, objectMapper, "admin", "admin-demo-pw");
    }

    private String viewerToken() throws Exception {
        return JwtTestSupport.login(mockMvc, objectMapper, "viewer", "viewer-demo-pw");
    }

    @Test
    void createCertificateSucceeds() throws Exception {
        Certificate created = certificateWithId(10L, CertState.PENDING_CSR);
        when(certificateService.createCertificate(1L, "device-01.example.com")).thenReturn(created);

        mockMvc.perform(post("/api/certificates")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CertificateCreateRequest(1L, "device-01.example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.deviceId").value(1))
                .andExpect(jsonPath("$.commonName").value("device-01.example.com"))
                .andExpect(jsonPath("$.state").value("PENDING_CSR"));
    }

    @Test
    void createCertificateAgainstNonexistentDeviceReturns404() throws Exception {
        when(certificateService.createCertificate(999L, "whatever"))
                .thenThrow(new DeviceNotFoundException(999L));

        mockMvc.perform(post("/api/certificates")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CertificateCreateRequest(999L, "whatever"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCertificateSucceeds() throws Exception {
        Certificate certificate = certificateWithId(5L, CertState.ACTIVE);
        when(certificateService.getCertificateById(5L)).thenReturn(certificate);

        mockMvc.perform(get("/api/certificates/5").header("Authorization", "Bearer " + viewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.state").value("ACTIVE"));
    }

    @Test
    void getCertificateReturns404WhenMissing() throws Exception {
        when(certificateService.getCertificateById(404L))
                .thenThrow(new CertificateNotFoundException(404L));

        mockMvc.perform(get("/api/certificates/404").header("Authorization", "Bearer " + viewerToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listCertificatesSucceeds() throws Exception {
        Certificate first = certificateWithId(1L, CertState.ACTIVE);
        Certificate second = certificateWithId(2L, CertState.ISSUED);
        when(certificateService.listCertificates()).thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/certificates").header("Authorization", "Bearer " + viewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void transitionSucceeds() throws Exception {
        Certificate transitioned = certificateWithId(7L, CertState.ISSUED);
        when(certificateService.transitionCertificate(7L, CertState.ISSUED, "admin", "issued by CA"))
                .thenReturn(transitioned);

        mockMvc.perform(post("/api/certificates/7/transitions")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionRequest(CertState.ISSUED, "admin", "issued by CA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ISSUED"));
    }

    @Test
    void transitionToIllegalTargetReturns409() throws Exception {
        when(certificateService.transitionCertificate(eq(7L), eq(CertState.RENEWED), any(), any()))
                .thenThrow(new IllegalStateTransitionException("Illegal certificate state transition: ISSUED -> RENEWED"));

        mockMvc.perform(post("/api/certificates/7/transitions")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionRequest(CertState.RENEWED, "admin", "skip ahead"))))
                .andExpect(status().isConflict());
    }

    @Test
    void viewerTokenCannotTransition() throws Exception {
        mockMvc.perform(post("/api/certificates/7/transitions")
                        .header("Authorization", "Bearer " + viewerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionRequest(CertState.ISSUED, "viewer", "trying anyway"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void certificateResponseDoesNotLeakVersionField() throws Exception {
        Certificate certificate = certificateWithId(11L, CertState.ACTIVE);
        when(certificateService.getCertificateById(11L)).thenReturn(certificate);

        mockMvc.perform(get("/api/certificates/11").header("Authorization", "Bearer " + viewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").doesNotExist());
    }
}
