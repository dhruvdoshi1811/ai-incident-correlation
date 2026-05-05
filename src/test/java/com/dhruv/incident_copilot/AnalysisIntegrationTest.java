package com.dhruv.incident_copilot;

import com.dhruv.incident_copilot.dto.AlertRequest;
import com.dhruv.incident_copilot.dto.AnalysisResponse;
import com.dhruv.incident_copilot.dto.PostmortemRequest;
import com.dhruv.incident_copilot.entity.AnalysisStatus;
import com.dhruv.incident_copilot.entity.Severity;
import com.dhruv.incident_copilot.repository.AlertRepository;
import com.dhruv.incident_copilot.service.AnalysisService;
import com.dhruv.incident_copilot.service.AlertService;
import com.dhruv.incident_copilot.service.PostmortemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class AnalysisIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private PostmortemService postmortemService;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private FakeChatModel fakeChatModel;

    @Test
    void submitAnalysisRetrievesPostmortemAndCompletesWithGroundedPrompt() {
        String cluster = "db-outage-" + UUID.randomUUID();
        fakeChatModel.setCannedResponse("Root cause: connection pool exhaustion. Increase pool size.");

        postmortemService.create(new PostmortemRequest(
                "[[cluster:" + cluster + "]] DB connection pool exhaustion postmortem",
                "Root cause was too many open connections; fixed by increasing pool size and adding a circuit breaker."));

        var alert = alertService.ingest(new AlertRequest(
                "datadog", "analysis-test-" + UUID.randomUUID(), Severity.CRITICAL,
                "[[cluster:" + cluster + "]] Database connection errors spiking", "{}")).alert();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(alertRepository.findById(alert.id()).orElseThrow().getIncidentId()).isNotNull());
        UUID incidentId = alertRepository.findById(alert.id()).orElseThrow().getIncidentId();

        AnalysisResponse submitted = analysisService.submit(incidentId);
        assertThat(submitted.status()).isEqualTo(AnalysisStatus.PENDING);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(analysisService.getById(submitted.id()).status()).isEqualTo(AnalysisStatus.COMPLETED));

        AnalysisResponse completed = analysisService.getById(submitted.id());
        assertThat(completed.resultSummary()).isEqualTo("Root cause: connection pool exhaustion. Increase pool size.");
        assertThat(completed.completedAt()).isNotNull();

        assertThat(fakeChatModel.getLastPromptText())
                .contains("Database connection errors spiking")
                .contains("DB connection pool exhaustion postmortem")
                .contains("too many open connections");
    }
}
