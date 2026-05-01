package com.dhruv.incident_copilot.controller;

import com.dhruv.incident_copilot.AbstractIntegrationTest;
import com.dhruv.incident_copilot.TestAuthHelper;
import com.dhruv.incident_copilot.entity.Alert;
import com.dhruv.incident_copilot.entity.Incident;
import com.dhruv.incident_copilot.entity.Severity;
import com.dhruv.incident_copilot.repository.AlertRepository;
import com.dhruv.incident_copilot.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IncidentControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private AlertRepository alertRepository;

    private String authHeader() throws Exception {
        String token = TestAuthHelper.registerAndGetToken(
                mockMvc, objectMapper, "incidents-user-" + UUID.randomUUID() + "@incident-copilot.test");
        return "Bearer " + token;
    }

    private Incident seedIncident() {
        Incident incident = new Incident();
        return incidentRepository.save(incident);
    }

    @Test
    void getByIdReturnsSeededIncident() throws Exception {
        Incident incident = seedIncident();
        mockMvc.perform(get("/incidents/" + incident.getId()).header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        mockMvc.perform(get("/incidents/" + UUID.randomUUID()).header("Authorization", authHeader()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAlertsReturnsAlertsAttachedToIncident() throws Exception {
        Incident incident = seedIncident();

        Alert alert = new Alert();
        alert.setSourceSystem("datadog");
        alert.setExternalAlertId("incident-test-" + UUID.randomUUID());
        alert.setSeverity(Severity.HIGH);
        alert.setTitle("disk usage high");
        alert.setRawPayload("{}");
        alert.setIncidentId(incident.getId());
        alertRepository.save(alert);

        mockMvc.perform(get("/incidents/" + incident.getId() + "/alerts").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(alert.getId().toString()));
    }

    @Test
    void resolveMarksIncidentResolved() throws Exception {
        Incident incident = seedIncident();
        String auth = authHeader();

        mockMvc.perform(post("/incidents/" + incident.getId() + "/resolve").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists());

        mockMvc.perform(post("/incidents/" + incident.getId() + "/resolve").header("Authorization", auth))
                .andExpect(status().isBadRequest());
    }
}
