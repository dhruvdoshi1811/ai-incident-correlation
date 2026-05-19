package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.entity.Alert;
import com.dhruv.incident_copilot.entity.AnalysisRequest;
import com.dhruv.incident_copilot.entity.AnalysisStatus;
import com.dhruv.incident_copilot.entity.Incident;
import com.dhruv.incident_copilot.repository.AlertRepository;
import com.dhruv.incident_copilot.repository.AnalysisRequestRepository;
import com.dhruv.incident_copilot.repository.IncidentRepository;
import com.dhruv.incident_copilot.repository.PostmortemEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(AnalysisWorker.class);

    private static final String SYSTEM_PROMPT = """
            You are an SRE incident-analysis assistant. Given a set of correlated
            infrastructure alerts and, where available, relevant historical postmortems,
            provide a concise likely root cause and recommended next steps for the
            on-call engineer.
            """;

    private final AnalysisRequestRepository analysisRequestRepository;
    private final IncidentRepository incidentRepository;
    private final AlertRepository alertRepository;
    private final EmbeddingService embeddingService;
    private final PostmortemEmbeddingRepository postmortemEmbeddingRepository;
    private final ProtectedChatClient protectedChatClient;
    private final AnalysisCompletionService analysisCompletionService;
    private final int retrievalTopK;

    public AnalysisWorker(
            AnalysisRequestRepository analysisRequestRepository,
            IncidentRepository incidentRepository,
            AlertRepository alertRepository,
            EmbeddingService embeddingService,
            PostmortemEmbeddingRepository postmortemEmbeddingRepository,
            ProtectedChatClient protectedChatClient,
            AnalysisCompletionService analysisCompletionService,
            @Value("${postmortem.retrieval.top-k}") int retrievalTopK) {
        this.analysisRequestRepository = analysisRequestRepository;
        this.incidentRepository = incidentRepository;
        this.alertRepository = alertRepository;
        this.embeddingService = embeddingService;
        this.postmortemEmbeddingRepository = postmortemEmbeddingRepository;
        this.protectedChatClient = protectedChatClient;
        this.analysisCompletionService = analysisCompletionService;
        this.retrievalTopK = retrievalTopK;
    }

    @Async("analysisExecutor")
    public void process(UUID requestId) {
        AnalysisRequest request = analysisRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Analysis request disappeared: " + requestId));

        request.setStatus(AnalysisStatus.RUNNING);
        analysisRequestRepository.save(request);

        List<Alert> alerts;
        try {
            Incident incident = incidentRepository.findById(request.getIncidentId())
                    .orElseThrow(() -> new IllegalStateException("Incident disappeared: " + request.getIncidentId()));
            alerts = alertRepository.findByIncidentId(incident.getId());
        } catch (Exception e) {
            log.error("Analysis failed for request {} - could not load incident/alerts", requestId, e);
            analysisCompletionService.complete(requestId, AnalysisStatus.FAILED, "Analysis failed: " + e.getMessage(), null);
            return;
        }

        List<PostmortemMatch> matches;
        String retrievedContext;
        try {
            String retrievalQuery = buildRetrievalQuery(alerts);
            float[] queryEmbedding = embeddingService.embed(retrievalQuery);
            matches = postmortemEmbeddingRepository.findTopKSimilar(queryEmbedding, retrievalTopK);
            retrievedContext = matches.isEmpty()
                    ? null
                    : matches.stream().map(PostmortemMatch::title).collect(java.util.stream.Collectors.joining(" | "));
        } catch (Exception e) {
            log.warn("Postmortem retrieval failed for request {}: {}", requestId, e.getMessage());
            matches = List.of();
            retrievedContext = null;
        }

        try {
            String summary = protectedChatClient.call(SYSTEM_PROMPT, buildUserPrompt(alerts, matches));
            analysisCompletionService.complete(requestId, AnalysisStatus.COMPLETED, summary, retrievedContext);
        } catch (Exception e) {
            log.warn("Analysis degraded for request {}: {}", requestId, e.getMessage());
            analysisCompletionService.complete(requestId, AnalysisStatus.DEGRADED, buildDegradedSummary(alerts), retrievedContext);
        }
    }

    private String buildRetrievalQuery(List<Alert> alerts) {
        return alerts.stream()
                .map(a -> a.getSourceSystem() + " " + a.getTitle())
                .reduce("", (a, b) -> a + " " + b);
    }

    private String buildUserPrompt(List<Alert> alerts, List<PostmortemMatch> matches) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Correlated alerts:\n");
        for (Alert alert : alerts) {
            prompt.append("- [%s] %s: %s%n"
                    .formatted(alert.getSeverity(), alert.getSourceSystem(), alert.getTitle()));
        }

        if (matches.isEmpty()) {
            prompt.append("\nNo relevant historical postmortems were found.\n");
        } else {
            prompt.append("\nPotentially relevant historical postmortems:\n");
            for (PostmortemMatch match : matches) {
                prompt.append("- %s: %s%n".formatted(match.title(), match.content()));
            }
        }

        prompt.append("\nProvide a likely root cause and recommended next steps.");
        return prompt.toString();
    }

    private String buildDegradedSummary(List<Alert> alerts) {
        StringBuilder summary = new StringBuilder();
        summary.append("AI analysis is temporarily unavailable. Showing raw correlated alerts for manual review:\n\n");
        for (Alert alert : alerts) {
            summary.append("- [%s] %s: %s%n"
                    .formatted(alert.getSeverity(), alert.getSourceSystem(), alert.getTitle()));
        }
        return summary.toString();
    }
}
