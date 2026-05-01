CREATE TABLE alerts (
    id UUID PRIMARY KEY,
    source_system VARCHAR(100) NOT NULL,
    external_alert_id VARCHAR(255) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    raw_payload TEXT NOT NULL,
    incident_id UUID REFERENCES incidents(id),
    received_at TIMESTAMP NOT NULL,
    UNIQUE (source_system, external_alert_id)
);
