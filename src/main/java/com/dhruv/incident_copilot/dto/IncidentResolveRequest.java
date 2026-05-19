package com.dhruv.incident_copilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IncidentResolveRequest(
        @NotBlank String title,
        @NotNull PostmortemCategory category,
        @NotNull ImpactLevel impact,
        @NotBlank String whatHappened,
        @NotBlank String rootCauseDetail,
        @NotBlank String resolutionSteps,
        @NotBlank String preventionActions
) {
}
