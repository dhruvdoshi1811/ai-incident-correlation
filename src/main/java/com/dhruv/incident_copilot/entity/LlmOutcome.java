package com.dhruv.incident_copilot.entity;

public enum LlmOutcome {
    SUCCESS,
    CIRCUIT_OPEN,
    RATE_LIMITED,
    TIMEOUT,
    ERROR
}
