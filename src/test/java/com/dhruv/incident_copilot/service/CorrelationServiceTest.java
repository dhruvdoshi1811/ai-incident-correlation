package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.entity.Alert;
import com.dhruv.incident_copilot.exception.ResourceNotFoundException;
import com.dhruv.incident_copilot.repository.AlertRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationServiceTest {

    @Mock
    private AlertRepository alertRepository;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private CorrelationAttachmentService correlationAttachmentService;

    @InjectMocks
    private CorrelationService correlationService;

    private Alert alertWithId(UUID id) {
        Alert alert = new Alert();
        alert.setId(id);
        return alert;
    }

    @Test
    void correlateSucceedsOnFirstAttempt() {
        UUID alertId = UUID.randomUUID();
        float[] embedding = new float[]{0.1f};
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alertWithId(alertId)));
        when(embeddingService.embed(anyString())).thenReturn(embedding);
        doNothing().when(correlationAttachmentService).decideAndAttach(alertId, embedding);

        correlationService.correlate(alertId);

        verify(correlationAttachmentService, times(1)).decideAndAttach(alertId, embedding);
    }

    @Test
    void correlateRetriesOnOptimisticLockConflictThenSucceeds() {
        UUID alertId = UUID.randomUUID();
        float[] embedding = new float[]{0.1f};
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alertWithId(alertId)));
        when(embeddingService.embed(anyString())).thenReturn(embedding);
        doThrow(new ObjectOptimisticLockingFailureException("Incident", alertId))
                .doNothing()
                .when(correlationAttachmentService).decideAndAttach(alertId, embedding);

        correlationService.correlate(alertId);

        verify(correlationAttachmentService, times(2)).decideAndAttach(alertId, embedding);
    }

    @Test
    void correlateGivesUpAfterMaxAttempts() {
        UUID alertId = UUID.randomUUID();
        float[] embedding = new float[]{0.1f};
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alertWithId(alertId)));
        when(embeddingService.embed(anyString())).thenReturn(embedding);
        doThrow(new ObjectOptimisticLockingFailureException("Incident", alertId))
                .when(correlationAttachmentService).decideAndAttach(alertId, embedding);

        assertThatThrownBy(() -> correlationService.correlate(alertId))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(correlationAttachmentService, times(3)).decideAndAttach(alertId, embedding);
    }

    @Test
    void correlateThrowsWhenAlertMissing() {
        UUID alertId = UUID.randomUUID();
        when(alertRepository.findById(alertId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> correlationService.correlate(alertId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
