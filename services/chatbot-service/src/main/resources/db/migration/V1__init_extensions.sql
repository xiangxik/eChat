CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS app_schema_version_marker (
    id BIGSERIAL PRIMARY KEY,
    marker VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO app_schema_version_marker (marker)
VALUES ('phase-1-skeleton')
ON CONFLICT (marker) DO NOTHING;