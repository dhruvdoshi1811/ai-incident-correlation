package com.dhruv.incident_copilot.dto;

import com.dhruv.incident_copilot.entity.NotificationChannel;
import com.dhruv.incident_copilot.entity.NotificationStatus;
import com.dhruv.incident_copilot.entity.OutboundNotification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID incidentId,
        UUID analysisRequestId,
        NotificationChannel channel,
        String payload,
        NotificationStatus status,
        int attempts,
        Instant createdAt,
        Instant sentAt
) {
    public static NotificationResponse from(OutboundNotification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getIncidentId(),
                notification.getAnalysisRequestId(),
                notification.getChannel(),
                notification.getPayload(),
                notification.getStatus(),
                notification.getAttempts(),
                notification.getCreatedAt(),
                notification.getSentAt()
        );
    }
}
