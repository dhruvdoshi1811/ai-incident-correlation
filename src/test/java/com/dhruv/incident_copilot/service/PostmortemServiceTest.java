package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.dto.PostmortemRequest;
import com.dhruv.incident_copilot.dto.PostmortemResponse;
import com.dhruv.incident_copilot.entity.Postmortem;
import com.dhruv.incident_copilot.repository.PostmortemEmbeddingRepository;
import com.dhruv.incident_copilot.repository.PostmortemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostmortemServiceTest {

    @Mock
    private PostmortemRepository postmortemRepository;
    @Mock
    private PostmortemEmbeddingRepository postmortemEmbeddingRepository;
    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private PostmortemService postmortemService;

    @Test
    void createSavesEntityAndEmbedsTitleAndContent() {
        UUID id = UUID.randomUUID();
        doAnswer(invocation -> {
            Postmortem p = invocation.getArgument(0);
            p.setId(id);
            return p;
        }).when(postmortemRepository).save(any(Postmortem.class));
        float[] embedding = new float[]{0.1f, 0.2f};
        when(embeddingService.embed(anyString())).thenReturn(embedding);

        PostmortemResponse response = postmortemService.create(new PostmortemRequest("DB outage", "root cause: connection pool exhaustion"));

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.title()).isEqualTo("DB outage");

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(embeddingService).embed(textCaptor.capture());
        assertThat(textCaptor.getValue()).contains("DB outage").contains("connection pool exhaustion");

        verify(postmortemEmbeddingRepository).saveEmbedding(id, embedding);
    }

    @Test
    void getAllMapsAllEntities() {
        Postmortem p1 = new Postmortem();
        p1.setId(UUID.randomUUID());
        p1.setTitle("A");
        p1.setContent("content-a");
        Postmortem p2 = new Postmortem();
        p2.setId(UUID.randomUUID());
        p2.setTitle("B");
        p2.setContent("content-b");
        when(postmortemRepository.findAll()).thenReturn(List.of(p1, p2));

        List<PostmortemResponse> responses = postmortemService.getAll();

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(PostmortemResponse::title).containsExactly("A", "B");
    }
}
