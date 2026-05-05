package com.dhruv.incident_copilot.dto;

import com.dhruv.incident_copilot.entity.AnalysisRequest;
import com.dhruv.incident_copilot.entity.AnalysisStatus;

import java.time.Instant;
import java.util.UUID;

public record AnalysisResponse(
        UUID id,
        UUID incidentId,
        AnalysisStatus status,
        Instant requestedAt,
        Instant completedAt,
        String resultSummary
) {
    public static AnalysisResponse from(AnalysisRequest request) {
        return new AnalysisResponse(
                request.getId(),
                request.getIncidentId(),
                request.getStatus(),
                request.getRequestedAt(),
                request.getCompletedAt(),
                request.getResultSummary()
        );
    }
}
