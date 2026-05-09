package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.entity.Incident;
import com.dhruv.incident_copilot.entity.IncidentStatus;
import com.dhruv.incident_copilot.repository.AnalysisRequestRepository;
import com.dhruv.incident_copilot.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisDebounceSchedulerTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private AnalysisRequestRepository analysisRequestRepository;
    @Mock
    private AnalysisService analysisService;

    private AnalysisDebounceScheduler scheduler;

    private Incident staleIncident() {
        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setStatus(IncidentStatus.OPEN);
        incident.setLastAlertAttachedAt(Instant.now().minusSeconds(60));
        return incident;
    }

    @Test
    void submitsAnalysisForStaleUnanalyzedIncident() {
        scheduler = new AnalysisDebounceScheduler(incidentRepository, analysisRequestRepository, analysisService, 30);
        Incident incident = staleIncident();
        when(incidentRepository.findByStatusInAndLastAlertAttachedAtBefore(anyList(), any(Instant.class)))
                .thenReturn(List.of(incident));
        when(analysisRequestRepository.existsByIncidentIdAndRequestedAtAfter(incident.getId(), incident.getLastAlertAttachedAt()))
                .thenReturn(false);

        scheduler.sweep();

        verify(analysisService).submit(incident.getId());
    }

    @Test
    void doesNotResubmitWhenAlreadyAnalyzedSinceLastAttach() {
        scheduler = new AnalysisDebounceScheduler(incidentRepository, analysisRequestRepository, analysisService, 30);
        Incident incident = staleIncident();
        when(incidentRepository.findByStatusInAndLastAlertAttachedAtBefore(anyList(), any(Instant.class)))
                .thenReturn(List.of(incident));
        when(analysisRequestRepository.existsByIncidentIdAndRequestedAtAfter(incident.getId(), incident.getLastAlertAttachedAt()))
                .thenReturn(true);

        scheduler.sweep();

        verify(analysisService, never()).submit(any());
    }

    @Test
    void noCandidatesMeansNoSubmissions() {
        scheduler = new AnalysisDebounceScheduler(incidentRepository, analysisRequestRepository, analysisService, 30);
        when(incidentRepository.findByStatusInAndLastAlertAttachedAtBefore(anyList(), any(Instant.class)))
                .thenReturn(List.of());

        scheduler.sweep();

        verify(analysisService, never()).submit(any());
    }
}
