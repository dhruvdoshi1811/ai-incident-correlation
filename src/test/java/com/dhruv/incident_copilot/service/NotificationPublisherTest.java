package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.entity.NotificationChannel;
import com.dhruv.incident_copilot.entity.NotificationStatus;
import com.dhruv.incident_copilot.entity.OutboundNotification;
import com.dhruv.incident_copilot.repository.OutboundNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    @Mock
    private OutboundNotificationRepository outboundNotificationRepository;
    @Mock
    private NotificationSender notificationSender;

    private NotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new NotificationPublisher(outboundNotificationRepository, notificationSender, 3, 10, 300);
    }

    private OutboundNotification notification(int attempts) {
        OutboundNotification n = new OutboundNotification();
        n.setId(UUID.randomUUID());
        n.setIncidentId(UUID.randomUUID());
        n.setAnalysisRequestId(UUID.randomUUID());
        n.setChannel(NotificationChannel.SLACK);
        n.setPayload("{}");
        n.setStatus(NotificationStatus.PENDING);
        n.setAttempts(attempts);
        return n;
    }

    @Test
    void successfulSendMarksSent() {
        OutboundNotification n = notification(0);
        when(outboundNotificationRepository.findByStatusAndNextAttemptAtBefore(eq(NotificationStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(n));
        doNothing().when(notificationSender).send(n);

        publisher.publishPending();

        ArgumentCaptor<OutboundNotification> captor = ArgumentCaptor.forClass(OutboundNotification.class);
        verify(outboundNotificationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(captor.getValue().getSentAt()).isNotNull();
    }

    @Test
    void failureBelowMaxAttemptsStaysPendingWithAdvancedRetryTime() {
        OutboundNotification n = notification(0);
        Instant before = n.getNextAttemptAt();
        when(outboundNotificationRepository.findByStatusAndNextAttemptAtBefore(eq(NotificationStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(n));
        doThrow(new RuntimeException("send failed")).when(notificationSender).send(n);

        publisher.publishPending();

        ArgumentCaptor<OutboundNotification> captor = ArgumentCaptor.forClass(OutboundNotification.class);
        verify(outboundNotificationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(captor.getValue().getAttempts()).isEqualTo(1);
        assertThat(captor.getValue().getNextAttemptAt()).isAfter(Instant.now());
    }

    @Test
    void failureAtMaxAttemptsMarksFailed() {
        OutboundNotification n = notification(2);
        when(outboundNotificationRepository.findByStatusAndNextAttemptAtBefore(eq(NotificationStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(n));
        doThrow(new RuntimeException("send failed")).when(notificationSender).send(n);

        publisher.publishPending();

        ArgumentCaptor<OutboundNotification> captor = ArgumentCaptor.forClass(OutboundNotification.class);
        verify(outboundNotificationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(captor.getValue().getAttempts()).isEqualTo(3);
    }
}
