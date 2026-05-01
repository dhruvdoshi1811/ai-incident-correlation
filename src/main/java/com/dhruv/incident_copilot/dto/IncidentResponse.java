package com.dhruv.incident_copilot.dto;

import com.dhruv.incident_copilot.entity.Incident;
import com.dhruv.incident_copilot.entity.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        IncidentStatus status,
        int correlatedAlertCount,
        String rootCauseSummary,
        Instant createdAt,
        Instant resolvedAt
) {
    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getStatus(),
                incident.getCorrelatedAlertCount(),
                incident.getRootCauseSummary(),
                incident.getCreatedAt(),
                incident.getResolvedAt()
        );
    }
}
