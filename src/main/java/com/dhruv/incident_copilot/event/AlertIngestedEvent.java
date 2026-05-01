package com.dhruv.incident_copilot.event;

import java.util.UUID;

public record AlertIngestedEvent(UUID alertId) {
}
