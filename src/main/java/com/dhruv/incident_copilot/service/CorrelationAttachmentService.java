package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.entity.Alert;
import com.dhruv.incident_copilot.entity.Incident;
import com.dhruv.incident_copilot.repository.AlertEmbeddingRepository;
import com.dhruv.incident_copilot.repository.AlertRepository;
import com.dhruv.incident_copilot.repository.IncidentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CorrelationAttachmentService {

    private static final long CORRELATION_LOCK_KEY = 8234659123L;

    private final AlertRepository alertRepository;
    private final IncidentRepository incidentRepository;
    private final AlertEmbeddingRepository alertEmbeddingRepository;
    private final JdbcTemplate jdbcTemplate;
    private final double similarityThreshold;

    public CorrelationAttachmentService(
            AlertRepository alertRepository,
            IncidentRepository incidentRepository,
            AlertEmbeddingRepository alertEmbeddingRepository,
            JdbcTemplate jdbcTemplate,
            @Value("${correlation.similarity-threshold}") double similarityThreshold) {
        this.alertRepository = alertRepository;
        this.incidentRepository = incidentRepository;
        this.alertEmbeddingRepository = alertEmbeddingRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.similarityThreshold = similarityThreshold;
    }

    @Transactional
    public void decideAndAttach(UUID alertId, float[] embedding) {
        alertEmbeddingRepository.saveEmbedding(alertId, embedding);

        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + CORRELATION_LOCK_KEY + ")");

        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalStateException("Alert disappeared during correlation: " + alertId));

        var candidate = alertEmbeddingRepository.findNearestActiveIncident(alertId, embedding);

        if (candidate.isPresent() && candidate.get().distance() < similarityThreshold) {
            attachToIncident(alert, candidate.get().incidentId());
        } else {
            createIncidentAndAttach(alert);
        }
    }

    private void attachToIncident(Alert alert, UUID incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalStateException("Matched incident disappeared: " + incidentId));
        incident.setCorrelatedAlertCount(incident.getCorrelatedAlertCount() + 1);
        incident.setLastAlertAttachedAt(Instant.now());
        incidentRepository.save(incident);

        alert.setIncidentId(incidentId);
        alertRepository.save(alert);
    }

    private void createIncidentAndAttach(Alert alert) {
        Incident incident = new Incident();
        incident.setCorrelatedAlertCount(1);
        incident.setLastAlertAttachedAt(Instant.now());
        incidentRepository.save(incident);

        alert.setIncidentId(incident.getId());
        alertRepository.save(alert);
    }
}
