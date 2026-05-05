package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.dto.AnalysisResponse;
import com.dhruv.incident_copilot.entity.AnalysisRequest;
import com.dhruv.incident_copilot.entity.AnalysisStatus;
import com.dhruv.incident_copilot.exception.ResourceNotFoundException;
import com.dhruv.incident_copilot.repository.AnalysisRequestRepository;
import com.dhruv.incident_copilot.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock
    private AnalysisRequestRepository analysisRequestRepository;
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private AnalysisWorker analysisWorker;

    @InjectMocks
    private AnalysisService analysisService;

    @Test
    void submitCreatesPendingRequestAndDispatchesWorker() {
        UUID incidentId = UUID.randomUUID();
        when(incidentRepository.existsById(incidentId)).thenReturn(true);
        when(analysisRequestRepository.findFirstByIncidentIdAndStatusIn(eq(incidentId), anyList()))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> {
            AnalysisRequest r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        }).when(analysisRequestRepository).save(any(AnalysisRequest.class));

        AnalysisResponse response = analysisService.submit(incidentId);

        assertThat(response.status()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(response.incidentId()).isEqualTo(incidentId);

        ArgumentCaptor<AnalysisRequest> captor = ArgumentCaptor.forClass(AnalysisRequest.class);
        verify(analysisRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getIncidentId()).isEqualTo(incidentId);

        verify(analysisWorker).process(any(UUID.class));
    }

    @Test
    void submitReturnsExistingInFlightRequestInsteadOfCreatingNew() {
        UUID incidentId = UUID.randomUUID();
        AnalysisRequest existing = new AnalysisRequest();
        existing.setId(UUID.randomUUID());
        existing.setIncidentId(incidentId);
        existing.setStatus(AnalysisStatus.RUNNING);

        when(incidentRepository.existsById(incidentId)).thenReturn(true);
        when(analysisRequestRepository.findFirstByIncidentIdAndStatusIn(eq(incidentId), anyList()))
                .thenReturn(Optional.of(existing));

        AnalysisResponse response = analysisService.submit(incidentId);

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.status()).isEqualTo(AnalysisStatus.RUNNING);
        verify(analysisRequestRepository, never()).save(any());
        verify(analysisWorker, never()).process(any());
    }

    @Test
    void submitThrowsWhenIncidentMissing() {
        UUID incidentId = UUID.randomUUID();
        when(incidentRepository.existsById(incidentId)).thenReturn(false);

        assertThatThrownBy(() -> analysisService.submit(incidentId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(analysisWorker, never()).process(any());
    }

    @Test
    void getByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(analysisRequestRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analysisService.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByIncidentIdMapsAllRequests() {
        UUID incidentId = UUID.randomUUID();
        AnalysisRequest r1 = new AnalysisRequest();
        r1.setId(UUID.randomUUID());
        r1.setIncidentId(incidentId);
        when(analysisRequestRepository.findByIncidentIdOrderByRequestedAtDesc(incidentId))
                .thenReturn(List.of(r1));

        List<AnalysisResponse> responses = analysisService.getByIncidentId(incidentId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(r1.getId());
    }
}
