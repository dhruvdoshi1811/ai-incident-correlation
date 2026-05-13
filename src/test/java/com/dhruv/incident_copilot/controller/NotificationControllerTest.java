package com.dhruv.incident_copilot.controller;

import com.dhruv.incident_copilot.AbstractIntegrationTest;
import com.dhruv.incident_copilot.TestAuthHelper;
import com.dhruv.incident_copilot.entity.AnalysisRequest;
import com.dhruv.incident_copilot.entity.Incident;
import com.dhruv.incident_copilot.entity.NotificationChannel;
import com.dhruv.incident_copilot.entity.NotificationStatus;
import com.dhruv.incident_copilot.entity.OutboundNotification;
import com.dhruv.incident_copilot.repository.AnalysisRequestRepository;
import com.dhruv.incident_copilot.repository.IncidentRepository;
import com.dhruv.incident_copilot.repository.OutboundNotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class NotificationControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IncidentRepository incidentRepository;
    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;
    @Autowired
    private OutboundNotificationRepository outboundNotificationRepository;

    @Value("${app.admin.email}")
    private String adminEmail;
    @Value("${app.admin.password}")
    private String adminPassword;

    private String userAuthHeader() throws Exception {
        String token = TestAuthHelper.registerAndGetToken(
                mockMvc, objectMapper, "notif-user-" + UUID.randomUUID() + "@incident-copilot.test");
        return "Bearer " + token;
    }

    private String adminAuthHeader() throws Exception {
        String token = TestAuthHelper.loginAndGetToken(mockMvc, objectMapper, adminEmail, adminPassword);
        return "Bearer " + token;
    }

    private OutboundNotification seedNotification(NotificationStatus status) {
        Incident incident = incidentRepository.save(new Incident());
        AnalysisRequest request = new AnalysisRequest();
        request.setIncidentId(incident.getId());
        request = analysisRequestRepository.save(request);

        OutboundNotification notification = new OutboundNotification();
        notification.setIncidentId(incident.getId());
        notification.setAnalysisRequestId(request.getId());
        notification.setChannel(NotificationChannel.SLACK);
        notification.setPayload("{\"status\":\"" + status + "\"}");
        notification.setStatus(status);
        return outboundNotificationRepository.save(notification);
    }

    @Test
    void getByIncidentIdReturnsNotificationsForThatIncident() throws Exception {
        OutboundNotification notification = seedNotification(NotificationStatus.SENT);

        mockMvc.perform(get("/incidents/" + notification.getIncidentId() + "/notifications")
                        .header("Authorization", userAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(notification.getId().toString()));
    }

    @Test
    void adminListByStatusRequiresAdminRole() throws Exception {
        seedNotification(NotificationStatus.FAILED);

        mockMvc.perform(get("/admin/notifications?status=FAILED").header("Authorization", userAuthHeader()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/notifications?status=FAILED").header("Authorization", adminAuthHeader()))
                .andExpect(status().isOk());
    }

    @Test
    void retryOnlyWorksOnFailedNotification() throws Exception {
        OutboundNotification sent = seedNotification(NotificationStatus.SENT);

        mockMvc.perform(post("/admin/notifications/" + sent.getId() + "/retry").header("Authorization", adminAuthHeader()))
                .andExpect(status().isBadRequest());

        OutboundNotification failed = seedNotification(NotificationStatus.FAILED);

        mockMvc.perform(post("/admin/notifications/" + failed.getId() + "/retry").header("Authorization", adminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0));
    }
}
