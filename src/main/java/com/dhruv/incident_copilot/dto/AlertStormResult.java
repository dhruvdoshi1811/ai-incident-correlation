package com.dhruv.incident_copilot.dto;

import java.util.List;
import java.util.UUID;

public record AlertStormResult(
        int alertsFired,
        boolean allCorrelated,
        List<UUID> resultingIncidentIds
) {
}
