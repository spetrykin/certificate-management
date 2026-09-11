package com.spetrykin.certificate_management.security;

import com.spetrykin.certificate_management.controller.AuthController;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared MockMvc login helper for {@code @WebMvcTest} slices that need a
 * real JWT for one of the two Day 5 demo users, so each test class doesn't
 * reimplement the same POST /auth/login round trip — originally written
 * inline in {@link AuthControllerSecurityTest}, extracted here on Day 6 so
 * the new controller tests can reuse it instead of copying it a second and
 * third time.
 * <p>
 * Requires the caller's {@code @WebMvcTest(controllers = ...)} list to
 * include {@link AuthController AuthController.class} alongside whatever
 * controller is actually under test, and the same
 * {@code SecurityConfig}/{@code DemoUserDetailsConfig}/{@code JwtService}
 * {@code @Import} plus {@code jwt.signing-key} {@code @TestPropertySource}
 * setup {@link AuthControllerSecurityTest} uses.
 */
public final class JwtTestSupport {

    private JwtTestSupport() {
        // static utility, not instantiable
    }

    public static String login(MockMvc mockMvc, ObjectMapper objectMapper, String username, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthController.LoginResponse.class).token();
    }
}
