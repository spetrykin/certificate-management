package com.spetrykin.certificate_management.service;

import com.spetrykin.certificate_management.domain.Device;
import com.spetrykin.certificate_management.domain.DeviceNotFoundException;
import com.spetrykin.certificate_management.repository.DeviceRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain Mockito unit test, no Spring context — same reasoning as
 * CertificateServiceTest: this is a single delegating call to the
 * repository, nothing that depends on a real persistence provider.
 */
@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Test
    void createDevicePersistsAndReturnsTheDevice() {
        DeviceService deviceService = new DeviceService(deviceRepository);
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Device result = deviceService.createDevice("device-01.example.com");

        assertThat(result.getIdentifier()).isEqualTo("device-01.example.com");
        verify(deviceRepository).save(any(Device.class));
    }

    @Test
    void getDeviceByIdReturnsTheDeviceWhenFound() {
        DeviceService deviceService = new DeviceService(deviceRepository);
        Device device = new Device("device-01.example.com");
        ReflectionTestUtils.setField(device, "id", 1L);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

        Device result = deviceService.getDeviceById(1L);

        assertThat(result).isSameAs(device);
    }

    @Test
    void getDeviceByIdThrowsDeviceNotFoundExceptionWhenMissing() {
        DeviceService deviceService = new DeviceService(deviceRepository);
        when(deviceRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.getDeviceById(404L))
                .isInstanceOf(DeviceNotFoundException.class);
    }
}
