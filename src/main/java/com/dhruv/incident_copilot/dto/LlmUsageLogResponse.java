package com.dhruv.incident_copilot.dto;

import com.dhruv.incident_copilot.entity.LlmOutcome;
import com.dhruv.incident_copilot.entity.LlmUsageLog;

import java.time.Instant;
import java.util.UUID;

public record LlmUsageLogResponse(UUID id, Instant timestamp, long latencyMs, LlmOutcome outcome) {
    public static LlmUsageLogResponse from(LlmUsageLog log) {
        return new LlmUsageLogResponse(log.getId(), log.getTimestamp(), log.getLatencyMs(), log.getOutcome());
    }
}
