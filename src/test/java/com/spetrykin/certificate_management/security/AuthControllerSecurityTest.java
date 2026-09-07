package com.spetrykin.certificate_management.security;

import com.spetrykin.certificate_management.controller.AuthController;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} rather than {@code @SpringBootTest}: this needs the
 * web + security layer ({@link AuthController}, {@link SecurityConfig},
 * {@link JwtAuthenticationFilter}, {@link JwtService}, the demo {@link
 * DemoUserDetailsConfig}) exercised through real HTTP requests via
 * MockMvc, but nothing from the persistence layer. {@code @WebMvcTest}
 * loads only the servlet-related slice — no JPA/DataSource/Flyway
 * autoconfiguration — so this needs no Testcontainers, matching Day 5's
 * "no Testcontainers" scope directly, and stays fast. {@link
 * DummyApiController} is a test-only stand-in for Day 6's certificate
 * endpoints, included solely to exercise the {@code /api/**}
 * GET-vs-mutating authorization rule without inventing a throwaway
 * production route.
 */
@WebMvcTest(controllers = {AuthController.class, DummyApiController.class})
@Import({SecurityConfig.class, DemoUserDetailsConfig.class, JwtService.class})
@TestPropertySource(properties = "jwt.signing-key=test-only-jwt-signing-key-for-auth-security-test-not-for-real-use")
class AuthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    // tools.jackson.databind.ObjectMapper (Jackson 3), not com.fasterxml —
    // Spring Boot 4.1's own Jackson autoconfiguration is Jackson 3-based;
    // com.fasterxml.jackson.databind.ObjectMapper (Jackson 2) is also on
    // the classpath, but only as jjwt-jackson's internal dependency for
    // JwtService's own JWT claims handling, and isn't Spring-managed here.
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void validAdminCredentialsReturnToken() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.LoginRequest("admin", "admin-demo-pw"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void invalidPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.LoginRequest("admin", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownUsernameIsRejected() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.LoginRequest("nobody", "whatever"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedResourceWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/dummy"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emptyBearerTokenIsRejectedNotServerError() throws Exception {
        mockMvc.perform(get("/api/dummy").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedBearerTokenIsRejectedNotServerError() throws Exception {
        mockMvc.perform(get("/api/dummy").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminTokenIsAcceptedForAdminGatedMutatingRequest() throws Exception {
        String token = loginAndExtractToken("admin", "admin-demo-pw");

        mockMvc.perform(post("/api/dummy").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void viewerTokenIsRejectedForAdminGatedMutatingRequest() throws Exception {
        String token = loginAndExtractToken("viewer", "viewer-demo-pw");

        mockMvc.perform(post("/api/dummy").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerTokenIsAcceptedForGetRequest() throws Exception {
        String token = loginAndExtractToken("viewer", "viewer-demo-pw");

        mockMvc.perform(get("/api/dummy").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String loginAndExtractToken(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthController.LoginResponse.class).token();
    }
}
