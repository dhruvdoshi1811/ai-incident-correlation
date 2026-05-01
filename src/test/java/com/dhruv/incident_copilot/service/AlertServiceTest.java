package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.dto.AlertRequest;
import com.dhruv.incident_copilot.entity.Alert;
import com.dhruv.incident_copilot.entity.Severity;
import com.dhruv.incident_copilot.exception.ResourceNotFoundException;
import com.dhruv.incident_copilot.repository.AlertRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private AlertService alertService;

    private AlertRequest request() {
        return new AlertRequest("datadog", "ext-1", Severity.CRITICAL, "CPU high", "{}");
    }

    @Test
    void ingestCreatesNewAlertWhenNoneExists() {
        when(alertRepository.findBySourceSystemAndExternalAlertId("datadog", "ext-1"))
                .thenReturn(Optional.empty());

        AlertIngestResult result = alertService.ingest(request());

        assertThat(result.created()).isTrue();
        verify(alertRepository).save(any(Alert.class));
    }

    @Test
    void ingestReturnsExistingAlertWithoutSavingWhenDuplicate() {
        Alert existing = new Alert();
        existing.setSourceSystem("datadog");
        existing.setExternalAlertId("ext-1");
        existing.setSeverity(Severity.CRITICAL);
        existing.setTitle("CPU high");
        existing.setRawPayload("{}");
        when(alertRepository.findBySourceSystemAndExternalAlertId("datadog", "ext-1"))
                .thenReturn(Optional.of(existing));

        AlertIngestResult result = alertService.ingest(request());

        assertThat(result.created()).isFalse();
        assertThat(result.alert().sourceSystem()).isEqualTo("datadog");
    }

    @Test
    void ingestFallsBackToExistingRowOnConcurrentDuplicateInsert() {
        Alert winner = new Alert();
        winner.setSourceSystem("datadog");
        winner.setExternalAlertId("ext-1");
        winner.setSeverity(Severity.CRITICAL);
        winner.setTitle("CPU high");
        winner.setRawPayload("{}");

        when(alertRepository.findBySourceSystemAndExternalAlertId("datadog", "ext-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(alertRepository.save(any(Alert.class))).thenThrow(new DataIntegrityViolationException("dup"));

        AlertIngestResult result = alertService.ingest(request());

        assertThat(result.created()).isFalse();
        assertThat(result.alert().sourceSystem()).isEqualTo("datadog");
    }

    @Test
    void getByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(alertRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
