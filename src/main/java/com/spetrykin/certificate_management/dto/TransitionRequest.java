package com.spetrykin.certificate_management.dto;

import com.spetrykin.certificate_management.domain.CertState;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TransitionRequest(
        @NotNull CertState target,
        @NotBlank String actor,
        @NotBlank String reason
) {
}
