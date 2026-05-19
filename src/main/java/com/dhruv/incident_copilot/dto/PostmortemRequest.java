package com.dhruv.incident_copilot.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record PostmortemRequest(
        @NotBlank String title,
        @NotBlank String content,
        UUID sourceIncidentId
) {
    public PostmortemRequest(String title, String content) {
        this(title, content, null);
    }
}
