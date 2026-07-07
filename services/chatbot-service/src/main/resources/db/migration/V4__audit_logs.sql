CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_type VARCHAR(64) NOT NULL,
    actor_id VARCHAR(160),
    event_type VARCHAR(128) NOT NULL,
    resource_type VARCHAR(128) NOT NULL,
    resource_id VARCHAR(128),
    request_id VARCHAR(128),
    trace_id VARCHAR(128),
    remote_address VARCHAR(128),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX audit_logs_occurred_at_idx ON audit_logs(occurred_at DESC);
CREATE INDEX audit_logs_resource_idx ON audit_logs(resource_type, resource_id, occurred_at DESC);
CREATE INDEX audit_logs_event_type_idx ON audit_logs(event_type, occurred_at DESC);