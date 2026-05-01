package com.dhruv.incident_copilot.dto;

import com.dhruv.incident_copilot.entity.Alert;
import com.dhruv.incident_copilot.entity.Severity;

import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        String sourceSystem,
        String externalAlertId,
        Severity severity,
        String title,
        String rawPayload,
        UUID incidentId,
        Instant receivedAt
) {
    public static AlertResponse from(Alert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getSourceSystem(),
                alert.getExternalAlertId(),
                alert.getSeverity(),
                alert.getTitle(),
                alert.getRawPayload(),
                alert.getIncidentId(),
                alert.getReceivedAt()
        );
    }
}
