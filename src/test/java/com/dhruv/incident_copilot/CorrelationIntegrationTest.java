package com.dhruv.incident_copilot;

import com.dhruv.incident_copilot.dto.AlertRequest;
import com.dhruv.incident_copilot.entity.Severity;
import com.dhruv.incident_copilot.repository.AlertRepository;
import com.dhruv.incident_copilot.service.AlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CorrelationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertRepository alertRepository;

    private AlertRequest alertRequest(String cluster, String externalId) {
        return new AlertRequest(
                "datadog", externalId, Severity.CRITICAL,
                "[[cluster:" + cluster + "]] High CPU usage detected", "{}");
    }

    private UUID incidentIdOf(UUID alertId) {
        return alertRepository.findById(alertId).orElseThrow().getIncidentId();
    }

    @Test
    void sameClusterAlertsCorrelateIntoOneIncident() {
        String cluster = "cpu-" + UUID.randomUUID();
        var first = alertService.ingest(alertRequest(cluster, "a-" + UUID.randomUUID())).alert();
        var second = alertService.ingest(alertRequest(cluster, "b-" + UUID.randomUUID())).alert();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(incidentIdOf(first.id())).isNotNull();
            assertThat(incidentIdOf(second.id())).isNotNull();
        });

        assertThat(incidentIdOf(first.id())).isEqualTo(incidentIdOf(second.id()));
    }

    @Test
    void differentClusterAlertGetsItsOwnIncident() {
        String clusterA = "diskfull-" + UUID.randomUUID();
        String clusterB = "netpartition-" + UUID.randomUUID();
        var a = alertService.ingest(alertRequest(clusterA, "a-" + UUID.randomUUID())).alert();
        var b = alertService.ingest(alertRequest(clusterB, "b-" + UUID.randomUUID())).alert();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(incidentIdOf(a.id())).isNotNull();
            assertThat(incidentIdOf(b.id())).isNotNull();
        });

        assertThat(incidentIdOf(a.id())).isNotEqualTo(incidentIdOf(b.id()));
    }

    @Test
    void concurrentBurstOfSimilarAlertsCollapsesIntoOneIncident() throws InterruptedException {
        String cluster = "storm-" + UUID.randomUUID();
        int alertCount = 15;
        CountDownLatch startingGate = new CountDownLatch(1);
        CountDownLatch completionGate = new CountDownLatch(alertCount);
        ExecutorService executor = Executors.newFixedThreadPool(alertCount);
        List<UUID> alertIds = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        IntStream.range(0, alertCount).forEach(i -> executor.submit(() -> {
            try {
                startingGate.await();
                var result = alertService.ingest(alertRequest(cluster, "burst-" + i + "-" + UUID.randomUUID()));
                alertIds.add(result.alert().id());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                completionGate.countDown();
            }
        }));

        startingGate.countDown();
        completionGate.await();
        executor.shutdown();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(alertRepository.findAllById(alertIds))
                        .allSatisfy(alert -> assertThat(alert.getIncidentId()).isNotNull()));

        var distinctIncidents = alertRepository.findAllById(alertIds).stream()
                .map(alert -> alert.getIncidentId())
                .collect(Collectors.toSet());

        assertThat(distinctIncidents).hasSize(1);
    }
}
