package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.entity.LlmOutcome;

public class LlmUnavailableException extends RuntimeException {

    private final LlmOutcome outcome;

    public LlmUnavailableException(LlmOutcome outcome, Throwable cause) {
        super("LLM call unavailable: " + outcome, cause);
        this.outcome = outcome;
    }

    public LlmOutcome getOutcome() {
        return outcome;
    }
}
