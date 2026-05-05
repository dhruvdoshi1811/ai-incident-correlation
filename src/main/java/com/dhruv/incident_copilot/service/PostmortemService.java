package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.dto.PostmortemRequest;
import com.dhruv.incident_copilot.dto.PostmortemResponse;
import com.dhruv.incident_copilot.entity.Postmortem;
import com.dhruv.incident_copilot.repository.PostmortemEmbeddingRepository;
import com.dhruv.incident_copilot.repository.PostmortemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostmortemService {

    private final PostmortemRepository postmortemRepository;
    private final PostmortemEmbeddingRepository postmortemEmbeddingRepository;
    private final EmbeddingService embeddingService;

    public PostmortemService(
            PostmortemRepository postmortemRepository,
            PostmortemEmbeddingRepository postmortemEmbeddingRepository,
            EmbeddingService embeddingService) {
        this.postmortemRepository = postmortemRepository;
        this.postmortemEmbeddingRepository = postmortemEmbeddingRepository;
        this.embeddingService = embeddingService;
    }

    public PostmortemResponse create(PostmortemRequest request) {
        Postmortem postmortem = new Postmortem();
        postmortem.setTitle(request.title());
        postmortem.setContent(request.content());
        postmortemRepository.save(postmortem);

        float[] embedding = embeddingService.embed(request.title() + " " + request.content());
        postmortemEmbeddingRepository.saveEmbedding(postmortem.getId(), embedding);

        return PostmortemResponse.from(postmortem);
    }

    public List<PostmortemResponse> getAll() {
        return postmortemRepository.findAll().stream()
                .map(PostmortemResponse::from)
                .toList();
    }
}
