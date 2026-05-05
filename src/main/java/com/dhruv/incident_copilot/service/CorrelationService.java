package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.entity.Alert;
import com.dhruv.incident_copilot.exception.ResourceNotFoundException;
import com.dhruv.incident_copilot.repository.AlertRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CorrelationService {

    private static final int MAX_ATTACH_ATTEMPTS = 3;

    private final AlertRepository alertRepository;
    private final EmbeddingService embeddingService;
    private final CorrelationAttachmentService correlationAttachmentService;

    public CorrelationService(
            AlertRepository alertRepository,
            EmbeddingService embeddingService,
            CorrelationAttachmentService correlationAttachmentService) {
        this.alertRepository = alertRepository;
        this.embeddingService = embeddingService;
        this.correlationAttachmentService = correlationAttachmentService;
    }

    public void correlate(UUID alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + alertId));

        float[] embedding = embeddingService.embed(alert.getSourceSystem() + " " + alert.getTitle());

        for (int attempt = 1; attempt <= MAX_ATTACH_ATTEMPTS; attempt++) {
            try {
                correlationAttachmentService.decideAndAttach(alertId, embedding);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_ATTACH_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }
}
