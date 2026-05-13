package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.entity.NotificationStatus;
import com.dhruv.incident_copilot.entity.OutboundNotification;
import com.dhruv.incident_copilot.repository.OutboundNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final OutboundNotificationRepository outboundNotificationRepository;
    private final NotificationSender notificationSender;
    private final int maxAttempts;
    private final long backoffBaseSeconds;
    private final long backoffMaxSeconds;

    public NotificationPublisher(
            OutboundNotificationRepository outboundNotificationRepository,
            NotificationSender notificationSender,
            @Value("${notification.max-attempts}") int maxAttempts,
            @Value("${notification.backoff-base-seconds}") long backoffBaseSeconds,
            @Value("${notification.backoff-max-seconds}") long backoffMaxSeconds) {
        this.outboundNotificationRepository = outboundNotificationRepository;
        this.notificationSender = notificationSender;
        this.maxAttempts = maxAttempts;
        this.backoffBaseSeconds = backoffBaseSeconds;
        this.backoffMaxSeconds = backoffMaxSeconds;
    }

    @Scheduled(fixedDelayString = "${notification.publish-interval-seconds}000")
    public void publishPending() {
        for (OutboundNotification notification :
                outboundNotificationRepository.findByStatusAndNextAttemptAtBefore(NotificationStatus.PENDING, Instant.now())) {
            attempt(notification);
        }
    }

    private void attempt(OutboundNotification notification) {
        try {
            notificationSender.send(notification);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
        } catch (Exception e) {
            int attempts = notification.getAttempts() + 1;
            notification.setAttempts(attempts);
            if (attempts >= maxAttempts) {
                log.warn("Notification {} exhausted retries, marking FAILED", notification.getId());
                notification.setStatus(NotificationStatus.FAILED);
            } else {
                notification.setNextAttemptAt(Instant.now().plusSeconds(backoff(attempts)));
            }
        }
        outboundNotificationRepository.save(notification);
    }

    private long backoff(int attempts) {
        return Math.min(backoffBaseSeconds * (1L << attempts), backoffMaxSeconds);
    }
}
