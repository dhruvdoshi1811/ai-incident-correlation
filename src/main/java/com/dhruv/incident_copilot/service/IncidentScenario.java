package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.entity.Severity;

import java.util.List;

public enum IncidentScenario {

    DB_CONNECTION_POOL_EXHAUSTION(
            "Database Connection Pool Exhaustion",
            "Slow queries hold connections under load until the pool runs dry, cascading into checkout failures.",
            List.of(
                    new AlertTemplate("datadog", Severity.CRITICAL, "HikariCP connection pool exhausted on payments-db"),
                    new AlertTemplate("newrelic", Severity.HIGH, "P95 query latency above 2000ms on payments-db"),
                    new AlertTemplate("pagerduty", Severity.CRITICAL, "5xx error rate spike on checkout-service")
            ),
            "2025-11 Payments DB Connection Pool Exhaustion",
            """
            A missing index on the orders table caused a routine query to scan sequentially under peak load. \
            Each slow query held a HikariCP connection for 8-12 seconds instead of the usual 50ms, and the pool \
            (sized for the fast case) was exhausted within minutes, causing checkout-service to fail with \
            connection timeouts. Resolved by adding the missing index, setting a 3s statement timeout on \
            payments-db, and increasing the pool size from 10 to 25. Follow-up: added a slow-query alert so \
            this class of issue pages before the pool empties.
            """),

    MEMORY_LEAK_OOM(
            "Memory Leak / OOM Kill",
            "An unbounded in-memory cache grows until the pod is OOM-killed and restarted, degrading recommendations.",
            List.of(
                    new AlertTemplate("kubernetes", Severity.CRITICAL, "OOMKilled: recommendation-service pod restarted"),
                    new AlertTemplate("datadog", Severity.HIGH, "Heap usage above 90% on recommendation-service"),
                    new AlertTemplate("newrelic", Severity.MEDIUM, "GC pause time elevated on recommendation-service")
            ),
            "2025-09 recommendation-service OOM from unbounded cache",
            """
            recommendation-service cached per-user recommendation results in an in-memory HashMap with no \
            eviction policy. Under sustained traffic the cache grew unbounded until the JVM heap was exhausted \
            and the pod was OOMKilled by Kubernetes, causing repeated restarts and degraded recommendation \
            quality during warm-up. Fixed by replacing the HashMap with a Caffeine cache with a size-based \
            eviction policy and a 30-minute TTL. Follow-up: added a heap-usage alert at 80% so this is caught \
            before an OOM kill.
            """),

    AUTH_TOKEN_CASCADE_FAILURE(
            "Auth Token Validation Cascade Failure",
            "A signing-key rotation that didn't reach every auth-gateway replica causes a wave of downstream 401s.",
            List.of(
                    new AlertTemplate("datadog", Severity.CRITICAL, "JWT validation failures spiking on auth-gateway"),
                    new AlertTemplate("pagerduty", Severity.CRITICAL, "401 rate spike across downstream services"),
                    new AlertTemplate("newrelic", Severity.HIGH, "auth-gateway CPU throttled")
            ),
            "2025-08 Auth Gateway Signing Key Rotation Incident",
            """
            The JWT signing key was rotated as part of routine credential hygiene, but the rollout only reached \
            2 of 5 auth-gateway replicas before the deploy stalled on a failed health check. Requests routed to \
            the un-rotated replicas rejected tokens signed with the new key, producing a spike of 401s across \
            every downstream service. Resolved by completing the rollout to all replicas and adding a readiness \
            check that verifies all replicas serve the same active signing key before a rotation is considered \
            complete. Follow-up: key rotations now go through a canary-then-full-rollout runbook instead of a \
            single rolling deploy.
            """),

    PAYMENT_GATEWAY_TIMEOUT(
            "Payment Gateway Timeout",
            "The external payment provider degrades, checkout success rate collapses, and the circuit breaker trips.",
            List.of(
                    new AlertTemplate("datadog", Severity.CRITICAL, "Timeout calling external payment gateway"),
                    new AlertTemplate("pagerduty", Severity.CRITICAL, "Checkout success rate below 50%"),
                    new AlertTemplate("newrelic", Severity.HIGH, "Circuit breaker OPEN for payment-gateway-client")
            ),
            "2025-06 Payment Gateway Provider Outage",
            """
            The third-party payment provider experienced a regional outage, causing payment-gateway-client calls \
            to time out at a high rate. The circuit breaker correctly tripped OPEN after the failure threshold, \
            which stopped cascading timeouts but also stopped all checkout traffic since there was no fallback \
            provider configured. Mitigated by manually failing over to the backup payment provider and notifying \
            customers of the temporary payment method restriction. Follow-up: added automatic failover to the \
            backup provider when the primary's circuit breaker opens, instead of requiring a manual switch.
            """);

    private final String displayName;
    private final String description;
    private final List<AlertTemplate> alertTemplates;
    private final String postmortemTitle;
    private final String postmortemContent;

    IncidentScenario(
            String displayName,
            String description,
            List<AlertTemplate> alertTemplates,
            String postmortemTitle,
            String postmortemContent) {
        this.displayName = displayName;
        this.description = description;
        this.alertTemplates = alertTemplates;
        this.postmortemTitle = postmortemTitle;
        this.postmortemContent = postmortemContent;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public List<AlertTemplate> alertTemplates() {
        return alertTemplates;
    }

    public String postmortemTitle() {
        return postmortemTitle;
    }

    public String postmortemContent() {
        return postmortemContent;
    }

    public record AlertTemplate(String sourceSystem, Severity severity, String title) {
    }
}
