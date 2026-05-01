package com.dhruv.incident_copilot;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FakeEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 768;
    private static final Pattern CLUSTER_MARKER = Pattern.compile("\\[\\[cluster:([a-zA-Z0-9_-]+)\\]\\]");

    @Override
    public float[] embed(String text) {
        String clusterKey = extractClusterKey(text);
        java.util.random.RandomGenerator clusterRandom = new java.util.Random(clusterKey.hashCode());
        java.util.random.RandomGenerator jitterRandom = new java.util.Random(text.hashCode());

        float[] vector = new float[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] = clusterRandom.nextFloat() * 10f + jitterRandom.nextFloat() * 0.01f;
        }
        return vector;
    }

    private String extractClusterKey(String text) {
        Matcher matcher = CLUSTER_MARKER.matcher(text);
        return matcher.find() ? matcher.group(1) : text;
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = request.getInstructions().stream()
                .map(text -> new Embedding(embed(text), 0))
                .toList();
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }
}
