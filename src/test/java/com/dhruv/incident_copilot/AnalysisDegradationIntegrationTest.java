package com.dhruv.incident_copilot;

import com.dhruv.incident_copilot.dto.AlertRequest;
import com.dhruv.incident_copilot.dto.AnalysisResponse;
import com.dhruv.incident_copilot.entity.AnalysisStatus;
import com.dhruv.incident_copilot.entity.LlmOutcome;
import com.dhruv.incident_copilot.entity.Severity;
import com.dhruv.incident_copilot.repository.AlertRepository;
import com.dhruv.incident_copilot.repository.LlmUsageLogRepository;
import com.dhruv.incident_copilot.service.AlertService;
import com.dhruv.incident_copilot.service.AnalysisService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class AnalysisDegradationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private FakeChatModel fakeChatModel;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private LlmUsageLogRepository llmUsageLogRepository;

    @BeforeEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("llmCall").reset();
    }

    @AfterEach
    void restoreHealthyState() {
        fakeChatModel.setFailing(false);
        circuitBreakerRegistry.circuitBreaker("llmCall").reset();
    }

    @Test
    void repeatedLlmFailuresOpenCircuitAndEveryRequestStillDegradesGracefully() {
        fakeChatModel.setFailing(true);

        var alert = alertService.ingest(new AlertRequest(
                "datadog", "degradation-test-" + UUID.randomUUID(), Severity.CRITICAL,
                "Persistent LLM failure test alert", "{}")).alert();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(alertRepository.findById(alert.id()).orElseThrow().getIncidentId()).isNotNull());
        UUID incidentId = alertRepository.findById(alert.id()).orElseThrow().getIncidentId();

        for (int i = 0; i < 7; i++) {
            AnalysisResponse submitted = analysisService.submit(incidentId);

            await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                    assertThat(analysisService.getById(submitted.id()).status())
                            .isIn(AnalysisStatus.DEGRADED));

            AnalysisResponse completed = analysisService.getById(submitted.id());
            assertThat(completed.status()).isEqualTo(AnalysisStatus.DEGRADED);
            assertThat(completed.resultSummary())
                    .contains("AI analysis is temporarily unavailable")
                    .contains("Persistent LLM failure test alert");
            assertThat(completed.completedAt()).isNotNull();
        }

        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("llmCall");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThat(llmUsageLogRepository.findAllByOrderByTimestampDesc())
                .extracting("outcome")
                .contains(LlmOutcome.CIRCUIT_OPEN);
    }
}
