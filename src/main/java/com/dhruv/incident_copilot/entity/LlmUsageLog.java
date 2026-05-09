package com.dhruv.incident_copilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "llm_usage_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LlmUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LlmOutcome outcome;

    public LlmUsageLog(long latencyMs, LlmOutcome outcome) {
        this.timestamp = Instant.now();
        this.latencyMs = latencyMs;
        this.outcome = outcome;
    }
}
