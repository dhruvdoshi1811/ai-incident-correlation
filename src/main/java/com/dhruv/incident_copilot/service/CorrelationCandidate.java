package com.dhruv.incident_copilot.service;

import java.util.UUID;

public record CorrelationCandidate(UUID incidentId, double distance) {
}
