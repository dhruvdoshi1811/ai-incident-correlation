package com.dhruv.incident_copilot;

import com.dhruv.incident_copilot.dto.AlertRequest;
import com.dhruv.incident_copilot.dto.AnalysisResponse;
import com.dhruv.incident_copilot.dto.PostmortemRequest;
import com.dhruv.incident_copilot.entity.AnalysisStatus;
import com.dhruv.incident_copilot.entity.NotificationStatus;
import com.dhruv.incident_copilot.entity.OutboundNotification;
import com.dhruv.incident_copilot.entity.Severity;
import com.dhruv.incident_copilot.repository.AlertRepository;
import com.dhruv.incident_copilot.repository.OutboundNotificationRepository;
import com.dhruv.incident_copilot.service.AlertService;
import com.dhruv.incident_copilot.service.AnalysisService;
import com.dhruv.incident_copilot.service.NotificationPublisher;
import com.dhruv.incident_copilot.service.PostmortemService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// one continuous run through correlation -> analysis -> notification, both the healthy and degraded paths
class FullLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AlertService alertService;
    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private PostmortemService postmortemService;
    @Autowired
    private AnalysisService analysisService;
    @Autowired
    private NotificationPublisher notificationPublisher;
    @Autowired
    private OutboundNotificationRepository outboundNotificationRepository;
    @Autowired
    private FakeChatModel fakeChatModel;

    @AfterEach
    void restoreHealthyChatModel() {
        fakeChatModel.setFailing(false);
    }

    private UUID ingestAndAwaitIncident(String cluster, String titleSuffix) {
        var alert = alertService.ingest(new AlertRequest(
                "datadog", "lifecycle-" + UUID.randomUUID(), Severity.CRITICAL,
                "[[cluster:" + cluster + "]] " + titleSuffix, "{}")).alert();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(alertRepository.findById(alert.id()).orElseThrow().getIncidentId()).isNotNull());
        return alertRepository.findById(alert.id()).orElseThrow().getIncidentId();
    }

    private OutboundNotification notificationFor(UUID incidentId, UUID analysisRequestId) {
        return outboundNotificationRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId).stream()
                .filter(n -> n.getAnalysisRequestId().equals(analysisRequestId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void wholePipelineWorksEndToEndAndNotifiesEitherWay() {
        String healthyCluster = "checkout-outage-" + UUID.randomUUID();
        postmortemService.create(new PostmortemRequest(
                "[[cluster:" + healthyCluster + "]] Checkout outage postmortem",
                "Root cause was a downstream payment provider timeout; fixed by adding a circuit breaker."));

        Set<UUID> incidentIds = IntStream.range(0, 4)
                .mapToObj(i -> ingestAndAwaitIncident(healthyCluster, "Checkout errors spiking on host-" + i))
                .collect(Collectors.toSet());
        assertThat(incidentIds).hasSize(1);
        UUID healthyIncidentId = incidentIds.iterator().next();

        AnalysisResponse healthySubmission = analysisService.submit(healthyIncidentId);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(analysisService.getById(healthySubmission.id()).status()).isEqualTo(AnalysisStatus.COMPLETED));

        OutboundNotification healthyNotification = notificationFor(healthyIncidentId, healthySubmission.id());
        assertThat(healthyNotification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        notificationPublisher.publishPending();
        assertThat(outboundNotificationRepository.findById(healthyNotification.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.SENT);

        UUID degradedIncidentId = ingestAndAwaitIncident(
                "unrelated-outage-" + UUID.randomUUID(), "Disk usage critical on standalone-host");
        assertThat(degradedIncidentId).isNotEqualTo(healthyIncidentId);

        fakeChatModel.setFailing(true);
        AnalysisResponse degradedSubmission = analysisService.submit(degradedIncidentId);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(analysisService.getById(degradedSubmission.id()).status()).isEqualTo(AnalysisStatus.DEGRADED));

        OutboundNotification degradedNotification = notificationFor(degradedIncidentId, degradedSubmission.id());
        assertThat(degradedNotification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        notificationPublisher.publishPending();
        assertThat(outboundNotificationRepository.findById(degradedNotification.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.SENT);
    }
}
