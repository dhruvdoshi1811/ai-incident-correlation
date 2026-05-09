package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.entity.Incident;
import com.dhruv.incident_copilot.entity.IncidentStatus;
import com.dhruv.incident_copilot.repository.AnalysisRequestRepository;
import com.dhruv.incident_copilot.repository.IncidentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class AnalysisDebounceScheduler {

    private static final List<IncidentStatus> ACTIVE_STATUSES =
            List.of(IncidentStatus.OPEN, IncidentStatus.INVESTIGATING);

    private final IncidentRepository incidentRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisService analysisService;
    private final long debounceWindowSeconds;

    public AnalysisDebounceScheduler(
            IncidentRepository incidentRepository,
            AnalysisRequestRepository analysisRequestRepository,
            AnalysisService analysisService,
            @Value("${analysis.debounce-window-seconds}") long debounceWindowSeconds) {
        this.incidentRepository = incidentRepository;
        this.analysisRequestRepository = analysisRequestRepository;
        this.analysisService = analysisService;
        this.debounceWindowSeconds = debounceWindowSeconds;
    }

    @Scheduled(fixedDelayString = "${analysis.debounce-check-interval-seconds}000")
    public void sweep() {
        Instant cutoff = Instant.now().minusSeconds(debounceWindowSeconds);
        for (Incident incident : incidentRepository.findByStatusInAndLastAlertAttachedAtBefore(ACTIVE_STATUSES, cutoff)) {
            boolean alreadyAnalyzed = analysisRequestRepository
                    .existsByIncidentIdAndRequestedAtAfter(incident.getId(), incident.getLastAlertAttachedAt());
            if (!alreadyAnalyzed) {
                analysisService.submit(incident.getId());
            }
        }
    }
}
