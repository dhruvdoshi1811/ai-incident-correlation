package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.dto.AlertResponse;

public record AlertIngestResult(AlertResponse alert, boolean created) {
}
