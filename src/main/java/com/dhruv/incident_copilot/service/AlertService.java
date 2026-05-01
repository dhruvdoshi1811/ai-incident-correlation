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
    private final AlertIngestWriter alertIngestWriter;

    public AlertService(AlertRepository alertRepository, AlertIngestWriter alertIngestWriter) {
        this.alertRepository = alertRepository;
        this.alertIngestWriter = alertIngestWriter;
    }

    public AlertIngestResult ingest(AlertRequest request) {
        var existing = alertRepository.findBySourceSystemAndExternalAlertId(
                request.sourceSystem(), request.externalAlertId());
        if (existing.isPresent()) {
            return new AlertIngestResult(AlertResponse.from(existing.get()), false);
        }

        try {
            Alert alert = alertIngestWriter.insert(request);
            return new AlertIngestResult(AlertResponse.from(alert), true);
        } catch (DataIntegrityViolationException e) {
            // lost a race against a concurrent duplicate delivery of the same alert
            Alert winner = alertRepository
                    .findBySourceSystemAndExternalAlertId(request.sourceSystem(), request.externalAlertId())
                    .orElseThrow(() -> e);
            return new AlertIngestResult(AlertResponse.from(winner), false);
        }
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
