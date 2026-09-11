package com.spetrykin.certificate_management.controller;

import com.spetrykin.certificate_management.domain.Device;
import com.spetrykin.certificate_management.domain.DeviceNotFoundException;
import com.spetrykin.certificate_management.dto.DeviceCreateRequest;
import com.spetrykin.certificate_management.security.DemoUserDetailsConfig;
import com.spetrykin.certificate_management.security.JwtService;
import com.spetrykin.certificate_management.security.JwtTestSupport;
import com.spetrykin.certificate_management.security.SecurityConfig;
import com.spetrykin.certificate_management.service.DeviceService;

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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} — see {@code CertificateControllerTest}'s Javadoc for
 * the same reasoning. {@link DeviceService} is the only dependency mocked
 * here, now that {@link DeviceController}'s by-id lookup also goes through
 * the service instead of a repository.
 */
@WebMvcTest(controllers = {DeviceController.class, AuthController.class})
@Import({SecurityConfig.class, DemoUserDetailsConfig.class, JwtService.class})
@TestPropertySource(properties = "jwt.signing-key=test-only-jwt-signing-key-for-device-controller-test-0000")
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DeviceService deviceService;

    private String adminToken() throws Exception {
        return JwtTestSupport.login(mockMvc, objectMapper, "admin", "admin-demo-pw");
    }

    private String viewerToken() throws Exception {
        return JwtTestSupport.login(mockMvc, objectMapper, "viewer", "viewer-demo-pw");
    }

    @Test
    void createDeviceSucceeds() throws Exception {
        Device device = new Device("device-01.example.com");
        ReflectionTestUtils.setField(device, "id", 1L);
        when(deviceService.createDevice("device-01.example.com")).thenReturn(device);

        mockMvc.perform(post("/api/devices")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceCreateRequest("device-01.example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.identifier").value("device-01.example.com"));
    }

    @Test
    void createDeviceWithBlankIdentifierReturns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/devices")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceCreateRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.identifier").exists());
    }

    @Test
    void getDeviceSucceeds() throws Exception {
        Device device = new Device("device-01.example.com");
        ReflectionTestUtils.setField(device, "id", 1L);
        when(deviceService.getDeviceById(1L)).thenReturn(device);

        mockMvc.perform(get("/api/devices/1").header("Authorization", "Bearer " + viewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.identifier").value("device-01.example.com"));
    }

    @Test
    void getDeviceReturns404WhenMissing() throws Exception {
        when(deviceService.getDeviceById(404L)).thenThrow(new DeviceNotFoundException(404L));

        mockMvc.perform(get("/api/devices/404").header("Authorization", "Bearer " + viewerToken()))
                .andExpect(status().isNotFound());
    }
}
