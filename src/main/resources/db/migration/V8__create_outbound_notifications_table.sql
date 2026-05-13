CREATE TABLE outbound_notifications (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incidents(id),
    analysis_request_id UUID NOT NULL REFERENCES analysis_requests(id),
    channel VARCHAR(20) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,
    next_attempt_at TIMESTAMP NOT NULL
);
