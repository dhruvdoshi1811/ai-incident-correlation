package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.entity.Alert;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public float[] embed(Alert alert) {
        return embeddingModel.embed(alert.getSourceSystem() + " " + alert.getTitle());
    }
}
