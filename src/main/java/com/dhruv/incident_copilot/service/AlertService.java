package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.dto.AlertRequest;
import com.dhruv.incident_copilot.dto.AlertResponse;
import com.dhruv.incident_copilot.entity.Alert;
import com.dhruv.incident_copilot.exception.ResourceNotFoundException;
import com.dhruv.incident_copilot.repository.AlertRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public AlertIngestResult ingest(AlertRequest request) {
        var existing = alertRepository.findBySourceSystemAndExternalAlertId(
                request.sourceSystem(), request.externalAlertId());
        if (existing.isPresent()) {
            return new AlertIngestResult(AlertResponse.from(existing.get()), false);
        }

        Alert alert = new Alert();
        alert.setSourceSystem(request.sourceSystem());
        alert.setExternalAlertId(request.externalAlertId());
        alert.setSeverity(request.severity());
        alert.setTitle(request.title());
        alert.setRawPayload(request.rawPayload());

        try {
            alertRepository.save(alert);
        } catch (DataIntegrityViolationException e) {
            // lost a race against a concurrent duplicate delivery of the same alert
            Alert winner = alertRepository
                    .findBySourceSystemAndExternalAlertId(request.sourceSystem(), request.externalAlertId())
                    .orElseThrow(() -> e);
            return new AlertIngestResult(AlertResponse.from(winner), false);
        }

        return new AlertIngestResult(AlertResponse.from(alert), true);
    }

    public AlertResponse getById(UUID id) {
        return AlertResponse.from(alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + id)));
    }

    public List<AlertResponse> getByIncidentId(UUID incidentId) {
        return alertRepository.findByIncidentId(incidentId).stream()
                .map(AlertResponse::from)
                .toList();
    }
}
