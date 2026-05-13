package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.dto.NotificationResponse;
import com.dhruv.incident_copilot.entity.NotificationStatus;
import com.dhruv.incident_copilot.entity.OutboundNotification;
import com.dhruv.incident_copilot.exception.InvalidRequestException;
import com.dhruv.incident_copilot.exception.ResourceNotFoundException;
import com.dhruv.incident_copilot.repository.OutboundNotificationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final OutboundNotificationRepository outboundNotificationRepository;

    public NotificationService(OutboundNotificationRepository outboundNotificationRepository) {
        this.outboundNotificationRepository = outboundNotificationRepository;
    }

    public List<NotificationResponse> getByIncidentId(UUID incidentId) {
        return outboundNotificationRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    public List<NotificationResponse> getByStatus(NotificationStatus status) {
        return outboundNotificationRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    public NotificationResponse retry(UUID id) {
        OutboundNotification notification = outboundNotificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));

        if (notification.getStatus() != NotificationStatus.FAILED) {
            throw new InvalidRequestException("Only FAILED notifications can be retried: " + id);
        }

        notification.setStatus(NotificationStatus.PENDING);
        notification.setAttempts(0);
        notification.setNextAttemptAt(Instant.now());
        outboundNotificationRepository.save(notification);

        return NotificationResponse.from(notification);
    }
}
