package com.dhruv.incident_copilot.controller;

import com.dhruv.incident_copilot.dto.CircuitBreakerStatusResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CircuitBreakerStatusController {

    private static final String INSTANCE_NAME = "llmCall";

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public CircuitBreakerStatusController(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @GetMapping("/admin/circuit-breaker-status")
    @PreAuthorize("hasRole('ADMIN')")
    public CircuitBreakerStatusResponse getStatus() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(INSTANCE_NAME);
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();

        return new CircuitBreakerStatusResponse(
                circuitBreaker.getState().name(),
                metrics.getFailureRate(),
                metrics.getNumberOfBufferedCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfSuccessfulCalls(),
                metrics.getNumberOfNotPermittedCalls()
        );
    }
}
