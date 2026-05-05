package com.dhruv.incident_copilot.dto;

import jakarta.validation.constraints.NotBlank;

public record PostmortemRequest(
        @NotBlank String title,
        @NotBlank String content
) {
}
