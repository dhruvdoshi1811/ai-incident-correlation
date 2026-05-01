package com.dhruv.incident_copilot;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class TestAuthHelper {

    private TestAuthHelper() {
    }

    public static String registerAndGetToken(MockMvc mockMvc, ObjectMapper objectMapper, String email)
            throws Exception {
        String body = objectMapper.writeValueAsString(new RegisterPayload(email, "password123"));

        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("token").asText();
    }

    private record RegisterPayload(String email, String password) {
    }
}
