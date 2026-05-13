package com.dhruv.incident_copilot;

import com.dhruv.incident_copilot.dto.AlertRequest;
import com.dhruv.incident_copilot.dto.AnalysisResponse;
import com.dhruv.incident_copilot.entity.AnalysisStatus;
import com.dhruv.incident_copilot.entity.NotificationStatus;
import com.dhruv.incident_copilot.entity.OutboundNotification;
import com.dhruv.incident_copilot.entity.Severity;
import com.dhruv.incident_copilot.repository.AlertRepository;
import com.dhruv.incident_copilot.repository.OutboundNotificationRepository;
import com.dhruv.incident_copilot.service.AlertService;
import com.dhruv.incident_copilot.service.AnalysisService;
import com.dhruv.incident_copilot.service.NotificationPublisher;
import com.dhruv.incident_copilot.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class NotificationCrashSafetyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AlertService alertService;
    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private AnalysisService analysisService;
    @Autowired
    private OutboundNotificationRepository outboundNotificationRepository;
    @Autowired
    private NotificationPublisher notificationPublisher;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private FakeNotificationSender fakeNotificationSender;

    @AfterEach
    void restoreHealthyChannel() {
        fakeNotificationSender.setFailing(false);
    }

    // the debounce scheduler runs for the whole suite, so look up "our" notification by analysisRequestId, never by incident alone
    private AnalysisResponse completeAnalysisAndGetResponse() {
        var alert = alertService.ingest(new AlertRequest(
                "datadog", "crash-safety-" + UUID.randomUUID(), Severity.CRITICAL,
                "Crash safety proof alert", "{}")).alert();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(alertRepository.findById(alert.id()).orElseThrow().getIncidentId()).isNotNull());
        UUID incidentId = alertRepository.findById(alert.id()).orElseThrow().getIncidentId();

        AnalysisResponse submitted = analysisService.submit(incidentId);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(analysisService.getById(submitted.id()).status())
                        .isIn(AnalysisStatus.COMPLETED, AnalysisStatus.DEGRADED));

        return submitted;
    }

    private OutboundNotification notificationFor(UUID incidentId, UUID analysisRequestId) {
        return outboundNotificationRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId).stream()
                .filter(n -> n.getAnalysisRequestId().equals(analysisRequestId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void notificationSurvivesPublishFailureAndEventuallySucceeds() {
        AnalysisResponse submitted = completeAnalysisAndGetResponse();

        OutboundNotification beforeAnyPublishAttempt = notificationFor(submitted.incidentId(), submitted.id());
        UUID notificationId = beforeAnyPublishAttempt.getId();
        assertThat(beforeAnyPublishAttempt.getStatus()).isEqualTo(NotificationStatus.PENDING);

        fakeNotificationSender.setFailing(true);
        notificationPublisher.publishPending();

        OutboundNotification afterFailedAttempt = outboundNotificationRepository.findById(notificationId).orElseThrow();
        assertThat(afterFailedAttempt.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(afterFailedAttempt.getAttempts()).isEqualTo(1);
        assertThat(afterFailedAttempt.getNextAttemptAt()).isAfter(java.time.Instant.now());

        fakeNotificationSender.setFailing(false);
        // force nextAttemptAt into the past so the retry is immediately eligible, instead of waiting on real backoff
        afterFailedAttempt.setNextAttemptAt(java.time.Instant.now().minusSeconds(1));
        outboundNotificationRepository.save(afterFailedAttempt);

        notificationPublisher.publishPending();

        OutboundNotification sent = outboundNotificationRepository.findById(notificationId).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(sent.getSentAt()).isNotNull();
        assertThat(fakeNotificationSender.getSent()).extracting(OutboundNotification::getId).contains(notificationId);
    }

    @Test
    void notificationReachesTerminalFailedAfterMaxAttemptsButIsNeverLostAndCanBeManuallyRetried() {
        AnalysisResponse submitted = completeAnalysisAndGetResponse();
        UUID notificationId = notificationFor(submitted.incidentId(), submitted.id()).getId();

        fakeNotificationSender.setFailing(true);
        for (int i = 0; i < 5; i++) {
            OutboundNotification current = outboundNotificationRepository.findById(notificationId).orElseThrow();
            current.setNextAttemptAt(java.time.Instant.now().minusSeconds(1));
            outboundNotificationRepository.save(current);
            notificationPublisher.publishPending();
        }

        OutboundNotification failed = outboundNotificationRepository.findById(notificationId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notificationService.getByStatus(NotificationStatus.FAILED))
                .extracting(r -> r.id()).contains(notificationId);

        fakeNotificationSender.setFailing(false);
        notificationService.retry(notificationId);

        OutboundNotification afterManualRetry = outboundNotificationRepository.findById(notificationId).orElseThrow();
        assertThat(afterManualRetry.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(afterManualRetry.getAttempts()).isEqualTo(0);

        notificationPublisher.publishPending();

        OutboundNotification finalState = outboundNotificationRepository.findById(notificationId).orElseThrow();
        assertThat(finalState.getStatus()).isEqualTo(NotificationStatus.SENT);
    }
}
