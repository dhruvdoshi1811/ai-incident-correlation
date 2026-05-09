CREATE TABLE llm_usage_log (
    id UUID PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL,
    latency_ms BIGINT NOT NULL,
    outcome VARCHAR(20) NOT NULL
);

ALTER TABLE incidents ADD COLUMN last_alert_attached_at TIMESTAMP;
