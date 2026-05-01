package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.dto.AlertResponse;
import com.dhruv.incident_copilot.dto.IncidentResponse;
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

    public IncidentService(IncidentRepository incidentRepository, AlertRepository alertRepository) {
        this.incidentRepository = incidentRepository;
        this.alertRepository = alertRepository;
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
    public IncidentResponse resolve(UUID id) {
        Incident incident = findOrThrow(id);
        if (incident.getStatus() == IncidentStatus.RESOLVED) {
            throw new InvalidRequestException("Incident is already resolved: " + id);
        }
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(Instant.now());
        return IncidentResponse.from(incident);
    }

    private Incident findOrThrow(UUID id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));
    }
}
