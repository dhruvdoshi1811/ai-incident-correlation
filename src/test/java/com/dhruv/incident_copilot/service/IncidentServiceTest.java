package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.dto.ImpactLevel;
import com.dhruv.incident_copilot.dto.IncidentResolveRequest;
import com.dhruv.incident_copilot.dto.PostmortemCategory;
import com.dhruv.incident_copilot.entity.Incident;
import com.dhruv.incident_copilot.entity.IncidentStatus;
import com.dhruv.incident_copilot.exception.InvalidRequestException;
import com.dhruv.incident_copilot.exception.ResourceNotFoundException;
import com.dhruv.incident_copilot.repository.AlertRepository;
import com.dhruv.incident_copilot.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private PostmortemService postmortemService;

    @InjectMocks
    private IncidentService incidentService;

    private IncidentResolveRequest sampleResolveRequest() {
        return new IncidentResolveRequest(
                "Query timeout postmortem",
                PostmortemCategory.INFRASTRUCTURE,
                ImpactLevel.HIGH,
                "Checkout errors spiked",
                "Slow query held connections open",
                "Added index and increased pool size",
                "Added slow-query alert");
    }

    @Test
    void getByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(incidentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolveMarksOpenIncidentResolved() {
        UUID id = UUID.randomUUID();
        Incident incident = new Incident();
        incident.setId(id);
        when(incidentRepository.findById(id)).thenReturn(Optional.of(incident));

        var response = incidentService.resolve(id, sampleResolveRequest());

        assertThat(response.status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(response.resolvedAt()).isNotNull();
        assertThat(response.rootCauseSummary()).contains("Slow query held connections open");
    }

    @Test
    void resolveRejectsAlreadyResolvedIncident() {
        UUID id = UUID.randomUUID();
        Incident incident = new Incident();
        incident.setId(id);
        incident.setStatus(IncidentStatus.RESOLVED);
        when(incidentRepository.findById(id)).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> incidentService.resolve(id, sampleResolveRequest()))
                .isInstanceOf(InvalidRequestException.class);
    }
}
