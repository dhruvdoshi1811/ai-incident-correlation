package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.entity.AnalysisRequest;
import com.dhruv.incident_copilot.entity.AnalysisStatus;
import com.dhruv.incident_copilot.entity.NotificationChannel;
import com.dhruv.incident_copilot.entity.OutboundNotification;
import com.dhruv.incident_copilot.repository.AnalysisRequestRepository;
import com.dhruv.incident_copilot.repository.OutboundNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisCompletionServiceTest {

    @Mock
    private AnalysisRequestRepository analysisRequestRepository;
    @Mock
    private OutboundNotificationRepository outboundNotificationRepository;

    private AnalysisCompletionService service;

    private AnalysisRequest request(UUID id, UUID incidentId) {
        AnalysisRequest request = new AnalysisRequest();
        request.setId(id);
        request.setIncidentId(incidentId);
        return request;
    }

    @BeforeEach
    void setUp() {
        service = new AnalysisCompletionService(
                analysisRequestRepository, outboundNotificationRepository, new ObjectMapper(), NotificationChannel.SLACK);
    }

    @Test
    void completedStatusWritesNotificationInSameCall() {
        UUID requestId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        when(analysisRequestRepository.findById(requestId)).thenReturn(Optional.of(request(requestId, incidentId)));

        service.complete(requestId, AnalysisStatus.COMPLETED, "root cause summary");

        ArgumentCaptor<AnalysisRequest> requestCaptor = ArgumentCaptor.forClass(AnalysisRequest.class);
        verify(analysisRequestRepository).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(requestCaptor.getValue().getCompletedAt()).isNotNull();

        ArgumentCaptor<OutboundNotification> notificationCaptor = ArgumentCaptor.forClass(OutboundNotification.class);
        verify(outboundNotificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getIncidentId()).isEqualTo(incidentId);
        assertThat(notificationCaptor.getValue().getAnalysisRequestId()).isEqualTo(requestId);
        assertThat(notificationCaptor.getValue().getChannel()).isEqualTo(NotificationChannel.SLACK);
        assertThat(notificationCaptor.getValue().getPayload()).contains("root cause summary");
    }

    @Test
    void degradedStatusAlsoWritesNotification() {
        UUID requestId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        when(analysisRequestRepository.findById(requestId)).thenReturn(Optional.of(request(requestId, incidentId)));

        service.complete(requestId, AnalysisStatus.DEGRADED, "raw alerts fallback");

        verify(outboundNotificationRepository).save(any(OutboundNotification.class));
    }

    @Test
    void failedStatusDoesNotWriteNotification() {
        UUID requestId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        when(analysisRequestRepository.findById(requestId)).thenReturn(Optional.of(request(requestId, incidentId)));

        service.complete(requestId, AnalysisStatus.FAILED, "could not load incident");

        verify(analysisRequestRepository).save(any(AnalysisRequest.class));
        verify(outboundNotificationRepository, never()).save(any());
    }
}
