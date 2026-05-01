package com.dhruv.incident_copilot.dto;

import com.dhruv.incident_copilot.entity.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AlertRequest(
        @NotBlank String sourceSystem,
        @NotBlank String externalAlertId,
        @NotNull Severity severity,
        @NotBlank String title,
        @NotBlank String rawPayload
) {
}
