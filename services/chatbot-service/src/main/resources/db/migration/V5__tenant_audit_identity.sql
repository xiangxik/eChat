ALTER TABLE audit_logs
    ADD COLUMN tenant_id VARCHAR(160) NOT NULL DEFAULT 'default';

CREATE INDEX audit_logs_tenant_occurred_at_idx ON audit_logs(tenant_id, occurred_at DESC);