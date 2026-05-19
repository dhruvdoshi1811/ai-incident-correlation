package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.dto.AlertResponse;
import com.dhruv.incident_copilot.dto.IncidentResolveRequest;
import com.dhruv.incident_copilot.dto.IncidentResponse;
import com.dhruv.incident_copilot.dto.PostmortemRequest;
import com.dhruv.incident_copilot.entity.Incident;
import com.dhruv.incident_copilot.entity.IncidentStatus;
import com.dhruv.incident_copilot.exception.InvalidRequestException;
import com.dhruv.incident_copilot.exception.ResourceNotFoundException;
import com.dhruv.incident_copilot.repository.AlertRepository;
import com.dhruv.incident_copilot.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final AlertRepository alertRepository;
    private final PostmortemService postmortemService;

    public IncidentService(
            IncidentRepository incidentRepository,
            AlertRepository alertRepository,
            PostmortemService postmortemService) {
        this.incidentRepository = incidentRepository;
        this.alertRepository = alertRepository;
        this.postmortemService = postmortemService;
    }

    public List<IncidentResponse> getAll() {
        return incidentRepository.findAll().stream()
                .map(IncidentResponse::from)
                .toList();
    }

    public IncidentResponse getById(UUID id) {
        return IncidentResponse.from(findOrThrow(id));
    }

    public List<AlertResponse> getAlerts(UUID id) {
        findOrThrow(id);
        return alertRepository.findByIncidentId(id).stream()
                .map(AlertResponse::from)
                .toList();
    }

    @Transactional
    public IncidentResponse resolve(UUID id, IncidentResolveRequest request) {
        Incident incident = findOrThrow(id);
        if (incident.getStatus() == IncidentStatus.RESOLVED) {
            throw new InvalidRequestException("Incident is already resolved: " + id);
        }

        String content = buildPostmortemContent(request);
        postmortemService.create(new PostmortemRequest(request.title(), content, id));

        incident.setRootCauseSummary(content);
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(Instant.now());
        return IncidentResponse.from(incident);
    }

    private String buildPostmortemContent(IncidentResolveRequest request) {
        return """
                Category: %s
                Impact: %s

                What happened:
                %s

                Root cause:
                %s

                Resolution steps:
                %s

                Prevention / follow-up:
                %s"""
                .formatted(
                        request.category(),
                        request.impact(),
                        request.whatHappened(),
                        request.rootCauseDetail(),
                        request.resolutionSteps(),
                        request.preventionActions());
    }

    private Incident findOrThrow(UUID id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));
    }
}
