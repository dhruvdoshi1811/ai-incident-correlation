package com.dhruv.incident_copilot;

import com.dhruv.incident_copilot.dto.AlertRequest;
import com.dhruv.incident_copilot.dto.ImpactLevel;
import com.dhruv.incident_copilot.dto.IncidentResolveRequest;
import com.dhruv.incident_copilot.dto.IncidentResponse;
import com.dhruv.incident_copilot.dto.PostmortemCategory;
import com.dhruv.incident_copilot.entity.IncidentStatus;
import com.dhruv.incident_copilot.entity.Postmortem;
import com.dhruv.incident_copilot.entity.Severity;
import com.dhruv.incident_copilot.repository.AlertRepository;
import com.dhruv.incident_copilot.repository.PostmortemRepository;
import com.dhruv.incident_copilot.service.AlertService;
import com.dhruv.incident_copilot.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class IncidentResolveIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private PostmortemRepository postmortemRepository;

    @Test
    void resolvingAnIncidentWritesAnEmbeddedPostmortemLinkedBackToIt() {
        var alert = alertService.ingest(new AlertRequest(
                "datadog", "resolve-test-" + UUID.randomUUID(), Severity.CRITICAL,
                "Checkout errors spiking on host-resolve-test", "{}")).alert();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(alertRepository.findById(alert.id()).orElseThrow().getIncidentId()).isNotNull());
        UUID incidentId = alertRepository.findById(alert.id()).orElseThrow().getIncidentId();

        String postmortemTitle = "Resolve test postmortem " + UUID.randomUUID();
        IncidentResolveRequest request = new IncidentResolveRequest(
                postmortemTitle,
                PostmortemCategory.INFRASTRUCTURE,
                ImpactLevel.HIGH,
                "Checkout errors spiked for 20 minutes",
                "A slow query held connections open under load",
                "Added an index and increased the connection pool size",
                "Added a slow-query alert to catch this earlier");

        IncidentResponse resolved = incidentService.resolve(incidentId, request);

        assertThat(resolved.status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(resolved.resolvedAt()).isNotNull();
        assertThat(resolved.rootCauseSummary())
                .contains("A slow query held connections open under load")
                .contains("Added an index and increased the connection pool size");

        Postmortem written = postmortemRepository.findByTitle(postmortemTitle).orElseThrow();
        assertThat(written.getSourceIncidentId()).isEqualTo(incidentId);
        assertThat(written.getContent()).contains("Added a slow-query alert to catch this earlier");
    }
}
