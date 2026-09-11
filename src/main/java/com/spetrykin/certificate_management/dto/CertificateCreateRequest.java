package com.spetrykin.certificate_management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CertificateCreateRequest(
        @NotNull Long deviceId,
        @NotBlank String commonName
) {
}
