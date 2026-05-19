package com.dhruv.incident_copilot.dto;

import com.dhruv.incident_copilot.entity.Postmortem;

import java.util.UUID;

public record PostmortemResponse(UUID id, String title, String content, UUID sourceIncidentId) {
    public static PostmortemResponse from(Postmortem postmortem) {
        return new PostmortemResponse(
                postmortem.getId(),
                postmortem.getTitle(),
                postmortem.getContent(),
                postmortem.getSourceIncidentId());
    }
}
