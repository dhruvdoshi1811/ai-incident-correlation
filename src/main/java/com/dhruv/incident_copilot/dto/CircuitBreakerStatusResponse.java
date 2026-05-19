package com.dhruv.incident_copilot.dto;

public record CircuitBreakerStatusResponse(
        String state,
        float failureRate,
        int bufferedCalls,
        int failedCalls,
        int successfulCalls,
        long notPermittedCalls
) {
}
