package com.dhruv.incident_copilot.repository;

import com.dhruv.incident_copilot.entity.NotificationStatus;
import com.dhruv.incident_copilot.entity.OutboundNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboundNotificationRepository extends JpaRepository<OutboundNotification, UUID> {

    List<OutboundNotification> findByIncidentIdOrderByCreatedAtDesc(UUID incidentId);

    List<OutboundNotification> findByStatusAndNextAttemptAtBefore(NotificationStatus status, Instant cutoff);

    List<OutboundNotification> findByStatusOrderByCreatedAtDesc(NotificationStatus status);
}
