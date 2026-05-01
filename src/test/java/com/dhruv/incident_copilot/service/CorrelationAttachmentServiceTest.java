package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.entity.Alert;
import com.dhruv.incident_copilot.entity.Incident;
import com.dhruv.incident_copilot.repository.AlertEmbeddingRepository;
import com.dhruv.incident_copilot.repository.AlertRepository;
import com.dhruv.incident_copilot.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationAttachmentServiceTest {

    private static final double THRESHOLD = 0.15;

    @Mock
    private AlertRepository alertRepository;
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private AlertEmbeddingRepository alertEmbeddingRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private CorrelationAttachmentService service;
    private final float[] embedding = new float[]{0.1f, 0.2f, 0.3f};

    @BeforeEach
    void setUp() {
        service = new CorrelationAttachmentService(
                alertRepository, incidentRepository, alertEmbeddingRepository, jdbcTemplate, THRESHOLD);
    }

    private Alert alertWithId(UUID id) {
        Alert alert = new Alert();
        alert.setId(id);
        return alert;
    }

    @Test
    void attachesToMatchedIncidentWhenWithinThreshold() {
        UUID alertId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        Incident incident = new Incident();
        incident.setId(incidentId);
        incident.setCorrelatedAlertCount(3);

        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alertWithId(alertId)));
        when(alertEmbeddingRepository.findNearestActiveIncident(alertId, embedding))
                .thenReturn(Optional.of(new CorrelationCandidate(incidentId, 0.05)));
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));

        service.decideAndAttach(alertId, embedding);

        ArgumentCaptor<Incident> incidentCaptor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(incidentCaptor.capture());
        assertThat(incidentCaptor.getValue().getCorrelatedAlertCount()).isEqualTo(4);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getIncidentId()).isEqualTo(incidentId);
    }

    @Test
    void createsNewIncidentWhenNoCandidateFound() {
        UUID alertId = UUID.randomUUID();
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alertWithId(alertId)));
        when(alertEmbeddingRepository.findNearestActiveIncident(alertId, embedding))
                .thenReturn(Optional.empty());

        service.decideAndAttach(alertId, embedding);

        ArgumentCaptor<Incident> incidentCaptor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(incidentCaptor.capture());
        assertThat(incidentCaptor.getValue().getCorrelatedAlertCount()).isEqualTo(1);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getIncidentId()).isEqualTo(incidentCaptor.getValue().getId());
    }

    @Test
    void createsNewIncidentWhenCandidateBeyondThreshold() {
        UUID alertId = UUID.randomUUID();
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alertWithId(alertId)));
        when(alertEmbeddingRepository.findNearestActiveIncident(alertId, embedding))
                .thenReturn(Optional.of(new CorrelationCandidate(UUID.randomUUID(), 0.9)));

        service.decideAndAttach(alertId, embedding);

        verify(incidentRepository).save(any(Incident.class));
        verify(incidentRepository, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void acquiresAdvisoryLockBeforeReadingCandidates() {
        UUID alertId = UUID.randomUUID();
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alertWithId(alertId)));
        when(alertEmbeddingRepository.findNearestActiveIncident(alertId, embedding))
                .thenReturn(Optional.empty());

        service.decideAndAttach(alertId, embedding);

        verify(jdbcTemplate).execute(anyString());
    }
}
