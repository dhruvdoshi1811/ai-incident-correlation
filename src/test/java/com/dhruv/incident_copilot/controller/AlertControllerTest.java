package com.dhruv.incident_copilot.controller;

import com.dhruv.incident_copilot.AbstractIntegrationTest;
import com.dhruv.incident_copilot.TestAuthHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AlertControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String authHeader() throws Exception {
        String token = TestAuthHelper.registerAndGetToken(
                mockMvc, objectMapper, "alerts-user-" + java.util.UUID.randomUUID() + "@incident-copilot.test");
        return "Bearer " + token;
    }

    private String alertBody(String sourceSystem, String externalAlertId) {
        return """
                {"sourceSystem":"%s","externalAlertId":"%s","severity":"CRITICAL","title":"CPU pegged at 100%%","rawPayload":"{\\"cpu\\":100}"}
                """.formatted(sourceSystem, externalAlertId);
    }

    @Test
    void ingestingNewAlertReturns201() throws Exception {
        String auth = authHeader();

        mockMvc.perform(post("/alerts")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alertBody("datadog", "alert-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceSystem").value("datadog"))
                .andExpect(jsonPath("$.incidentId").doesNotExist());
    }

    @Test
    void duplicateAlertReturns200WithSameId() throws Exception {
        String auth = authHeader();
        String body = alertBody("pagerduty", "alert-dup");

        String firstResponse = mockMvc.perform(post("/alerts")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(firstResponse).get("id").asText();

        mockMvc.perform(post("/alerts")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId));
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        String auth = authHeader();
        mockMvc.perform(get("/alerts/" + java.util.UUID.randomUUID())
                        .header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void ingestWithoutAuthIsUnauthorized() throws Exception {
        mockMvc.perform(post("/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alertBody("datadog", "alert-2")))
                .andExpect(status().isUnauthorized());
    }
}
