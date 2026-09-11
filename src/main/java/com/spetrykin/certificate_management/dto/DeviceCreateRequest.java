package com.spetrykin.certificate_management.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceCreateRequest(@NotBlank String identifier) {
}
