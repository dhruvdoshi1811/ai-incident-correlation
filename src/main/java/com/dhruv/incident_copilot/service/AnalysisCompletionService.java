package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.entity.AnalysisRequest;
import com.dhruv.incident_copilot.entity.AnalysisStatus;
import com.dhruv.incident_copilot.entity.NotificationChannel;
import com.dhruv.incident_copilot.entity.OutboundNotification;
import com.dhruv.incident_copilot.repository.AnalysisRequestRepository;
import com.dhruv.incident_copilot.repository.OutboundNotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalysisCompletionService {

    private final AnalysisRequestRepository analysisRequestRepository;
    private final OutboundNotificationRepository outboundNotificationRepository;
    private final ObjectMapper objectMapper;
    private final NotificationChannel defaultChannel;

    public AnalysisCompletionService(
            AnalysisRequestRepository analysisRequestRepository,
            OutboundNotificationRepository outboundNotificationRepository,
            ObjectMapper objectMapper,
            @Value("${notification.default-channel}") NotificationChannel defaultChannel) {
        this.analysisRequestRepository = analysisRequestRepository;
        this.outboundNotificationRepository = outboundNotificationRepository;
        this.objectMapper = objectMapper;
        this.defaultChannel = defaultChannel;
    }

    @Transactional
    public void complete(UUID requestId, AnalysisStatus status, String summary) {
        complete(requestId, status, summary, null);
    }

    @Transactional
    public void complete(UUID requestId, AnalysisStatus status, String summary, String retrievedContext) {
        AnalysisRequest request = analysisRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Analysis request disappeared: " + requestId));

        request.setStatus(status);
        request.setResultSummary(summary);
        request.setRetrievedPostmortemTitles(retrievedContext);
        request.setCompletedAt(Instant.now());
        analysisRequestRepository.save(request);

        if (status == AnalysisStatus.COMPLETED || status == AnalysisStatus.DEGRADED) {
            OutboundNotification notification = new OutboundNotification();
            notification.setIncidentId(request.getIncidentId());
            notification.setAnalysisRequestId(request.getId());
            notification.setChannel(defaultChannel);
            notification.setPayload(buildPayload(request, status, summary));
            outboundNotificationRepository.save(notification);
        }
    }

    private String buildPayload(AnalysisRequest request, AnalysisStatus status, String summary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("incidentId", request.getIncidentId().toString());
        payload.put("analysisRequestId", request.getId().toString());
        payload.put("status", status.name());
        payload.put("summary", summary.length() > 500 ? summary.substring(0, 500) + "..." : summary);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"incidentId\":\"" + request.getIncidentId() + "\",\"status\":\"" + status + "\"}";
        }
    }
}
