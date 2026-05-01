CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    correlated_alert_count INT NOT NULL DEFAULT 0,
    root_cause_summary TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP
);
