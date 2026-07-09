CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE tenants (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(160) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE provider_configs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(160) NOT NULL DEFAULT 'default' REFERENCES tenants(tenant_id) ON DELETE RESTRICT,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(64) NOT NULL,
    base_url VARCHAR(1024),
    api_key_secret_ref VARCHAR(512),
    encrypted_api_key TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT provider_configs_type_check CHECK (type IN (
        'OPENAI_COMPATIBLE', 'ANTHROPIC', 'AZURE_OPENAI', 'GEMINI', 'OLLAMA', 'CUSTOM'
    )),
    CONSTRAINT provider_configs_tenant_name_unique UNIQUE (tenant_id, name)
);

CREATE TABLE model_configs (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL REFERENCES provider_configs(id) ON DELETE RESTRICT,
    display_name VARCHAR(160) NOT NULL,
    model_name VARCHAR(200) NOT NULL,
    model_type VARCHAR(64) NOT NULL,
    max_context_tokens INTEGER,
    default_temperature DOUBLE PRECISION,
    default_top_p DOUBLE PRECISION,
    supports_streaming BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT model_configs_provider_model_unique UNIQUE (provider_id, model_name),
    CONSTRAINT model_configs_type_check CHECK (model_type IN ('CHAT', 'EMBEDDING', 'RERANKER')),
    CONSTRAINT model_configs_max_context_tokens_check CHECK (max_context_tokens IS NULL OR max_context_tokens > 0),
    CONSTRAINT model_configs_temperature_check CHECK (default_temperature IS NULL OR default_temperature BETWEEN 0 AND 2),
    CONSTRAINT model_configs_top_p_check CHECK (default_top_p IS NULL OR default_top_p BETWEEN 0 AND 1)
);

CREATE TABLE chatbot_configs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(160) NOT NULL DEFAULT 'default' REFERENCES tenants(tenant_id) ON DELETE RESTRICT,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chatbot_configs_tenant_name_unique UNIQUE (tenant_id, name)
);

CREATE TABLE chatbot_workflow_nodes (
    id BIGSERIAL PRIMARY KEY,
    chatbot_id BIGINT NOT NULL REFERENCES chatbot_configs(id) ON DELETE CASCADE,
    node_key VARCHAR(120) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    dsl_content TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    model_id BIGINT REFERENCES model_configs(id) ON DELETE RESTRICT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_start BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chatbot_workflow_nodes_node_key_check CHECK (node_key <> ''),
    CONSTRAINT chatbot_workflow_nodes_name_check CHECK (name <> ''),
    CONSTRAINT chatbot_workflow_nodes_dsl_check CHECK (dsl_content <> ''),
    CONSTRAINT chatbot_workflow_nodes_version_check CHECK (version > 0),
    CONSTRAINT chatbot_workflow_nodes_key_unique UNIQUE (chatbot_id, node_key)
);

CREATE UNIQUE INDEX chatbot_workflow_nodes_start_unique_idx
    ON chatbot_workflow_nodes(chatbot_id)
    WHERE is_start AND enabled;

CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(160) NOT NULL DEFAULT 'default' REFERENCES tenants(tenant_id) ON DELETE RESTRICT,
    chatbot_id BIGINT NOT NULL REFERENCES chatbot_configs(id) ON DELETE RESTRICT,
    user_id VARCHAR(128),
    anonymous_session_id VARCHAR(128),
    current_workflow_node_id BIGINT REFERENCES chatbot_workflow_nodes(id) ON DELETE SET NULL,
    workflow_state JSONB NOT NULL DEFAULT '{}'::jsonb,
    title VARCHAR(240),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT conversations_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DELETED')),
    CONSTRAINT conversations_actor_check CHECK (user_id IS NOT NULL OR anonymous_session_id IS NOT NULL)
);

CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    token_count INTEGER,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT messages_role_check CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    CONSTRAINT messages_token_count_check CHECK (token_count IS NULL OR token_count >= 0)
);

CREATE TABLE memory_items (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(160) NOT NULL DEFAULT 'default' REFERENCES tenants(tenant_id) ON DELETE RESTRICT,
    chatbot_id BIGINT NOT NULL REFERENCES chatbot_configs(id) ON DELETE CASCADE,
    user_id VARCHAR(128),
    scope VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    embedding vector(${embeddingDimension}),
    embedding_dimension INTEGER,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT memory_items_scope_check CHECK (scope IN ('SHORT_TERM', 'LONG_TERM', 'GLOBAL'))
);

CREATE TABLE chatbot_workflow_transitions (
    id BIGSERIAL PRIMARY KEY,
    chatbot_id BIGINT NOT NULL REFERENCES chatbot_configs(id) ON DELETE CASCADE,
    from_node_id BIGINT NOT NULL REFERENCES chatbot_workflow_nodes(id) ON DELETE CASCADE,
    to_node_id BIGINT NOT NULL REFERENCES chatbot_workflow_nodes(id) ON DELETE RESTRICT,
    name VARCHAR(160) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    condition_expression TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chatbot_workflow_transitions_name_check CHECK (name <> ''),
    CONSTRAINT chatbot_workflow_transitions_priority_check CHECK (priority >= 0),
    CONSTRAINT chatbot_workflow_transitions_condition_check CHECK (condition_expression <> '')
);

CREATE TABLE eval_datasets (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(160) NOT NULL DEFAULT 'default' REFERENCES tenants(tenant_id) ON DELETE RESTRICT,
    name VARCHAR(180) NOT NULL,
    description TEXT,
    chatbot_id BIGINT NOT NULL REFERENCES chatbot_configs(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT eval_datasets_tenant_name_unique UNIQUE (tenant_id, name)
);

CREATE TABLE eval_cases (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL REFERENCES eval_datasets(id) ON DELETE CASCADE,
    input TEXT NOT NULL,
    expected_behavior TEXT,
    expected_keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE eval_runs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(160) NOT NULL DEFAULT 'default' REFERENCES tenants(tenant_id) ON DELETE RESTRICT,
    dataset_id BIGINT NOT NULL REFERENCES eval_datasets(id) ON DELETE RESTRICT,
    chatbot_id BIGINT NOT NULL REFERENCES chatbot_configs(id) ON DELETE RESTRICT,
    model_id BIGINT REFERENCES model_configs(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT eval_runs_status_check CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE eval_results (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES eval_runs(id) ON DELETE CASCADE,
    case_id BIGINT NOT NULL REFERENCES eval_cases(id) ON DELETE RESTRICT,
    output TEXT,
    context_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    token_budget_report JSONB NOT NULL DEFAULT '{}'::jsonb,
    scores JSONB NOT NULL DEFAULT '{}'::jsonb,
    passed BOOLEAN NOT NULL DEFAULT FALSE,
    error TEXT
);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(160) NOT NULL DEFAULT 'default',
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

CREATE TABLE admin_permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE admin_roles (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE admin_role_permissions (
    role_id BIGINT NOT NULL REFERENCES admin_roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES admin_permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE admin_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(160) NOT NULL DEFAULT 'default' REFERENCES tenants(tenant_id) ON DELETE RESTRICT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    system_managed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE admin_user_roles (
    user_id BIGINT NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES admin_roles(id) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE admin_user_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX model_configs_provider_id_idx ON model_configs(provider_id);
CREATE INDEX provider_configs_tenant_name_idx ON provider_configs(tenant_id, name);
CREATE INDEX chatbot_configs_tenant_name_idx ON chatbot_configs(tenant_id, name);
CREATE INDEX chatbot_workflow_nodes_chatbot_idx ON chatbot_workflow_nodes(chatbot_id);
CREATE INDEX chatbot_workflow_nodes_model_idx ON chatbot_workflow_nodes(model_id);
CREATE INDEX conversations_chatbot_status_updated_idx ON conversations(tenant_id, chatbot_id, status, updated_at DESC);
CREATE INDEX conversations_user_updated_idx ON conversations(user_id, updated_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX conversations_anonymous_updated_idx ON conversations(anonymous_session_id, updated_at DESC) WHERE anonymous_session_id IS NOT NULL;
CREATE INDEX conversations_current_workflow_node_idx ON conversations(current_workflow_node_id);
CREATE INDEX messages_conversation_created_idx ON messages(conversation_id, created_at ASC);
CREATE INDEX memory_items_chatbot_scope_updated_idx ON memory_items(tenant_id, chatbot_id, scope, updated_at DESC);
CREATE INDEX memory_items_chatbot_user_updated_idx ON memory_items(chatbot_id, user_id, updated_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX memory_items_metadata_gin_idx ON memory_items USING GIN (metadata);
CREATE INDEX memory_items_embedding_hnsw_idx ON memory_items USING hnsw (embedding vector_cosine_ops) WHERE embedding IS NOT NULL;
CREATE INDEX chatbot_workflow_transitions_from_idx
    ON chatbot_workflow_transitions(chatbot_id, from_node_id, enabled, priority ASC, id ASC);
CREATE INDEX chatbot_workflow_transitions_to_idx ON chatbot_workflow_transitions(to_node_id);
CREATE INDEX eval_datasets_chatbot_idx ON eval_datasets(chatbot_id);
CREATE INDEX eval_cases_dataset_idx ON eval_cases(dataset_id, id ASC);
CREATE INDEX eval_runs_dataset_status_idx ON eval_runs(dataset_id, status);
CREATE INDEX eval_runs_chatbot_started_idx ON eval_runs(chatbot_id, started_at DESC);
CREATE INDEX eval_results_run_idx ON eval_results(run_id, id ASC);
CREATE INDEX eval_results_case_idx ON eval_results(case_id);
CREATE INDEX audit_logs_occurred_at_idx ON audit_logs(occurred_at DESC);
CREATE INDEX audit_logs_tenant_occurred_at_idx ON audit_logs(tenant_id, occurred_at DESC);
CREATE INDEX audit_logs_resource_idx ON audit_logs(resource_type, resource_id, occurred_at DESC);
CREATE INDEX audit_logs_event_type_idx ON audit_logs(event_type, occurred_at DESC);
CREATE INDEX admin_user_roles_role_id_idx ON admin_user_roles(role_id);
CREATE INDEX admin_role_permissions_permission_id_idx ON admin_role_permissions(permission_id);
CREATE INDEX admin_users_tenant_enabled_idx ON admin_users(tenant_id, enabled);
CREATE INDEX admin_user_sessions_user_id_idx ON admin_user_sessions(user_id);
CREATE INDEX admin_user_sessions_expires_at_idx ON admin_user_sessions(expires_at);

INSERT INTO tenants (tenant_id, name, enabled)
VALUES ('default', 'Default Tenant', TRUE)
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO admin_permissions (code, name, description)
VALUES
    ('ADMIN_READ', 'Admin Read', 'Read admin console resources'),
    ('ADMIN_WRITE', 'Admin Write', 'Create, update, and delete admin console resources'),
    ('AUDIT_READ', 'Audit Read', 'Read audit logs'),
    ('RBAC_MANAGE', 'RBAC Manage', 'Manage admin users, roles, and permissions')
ON CONFLICT (code) DO NOTHING;

INSERT INTO admin_roles (code, name, description, system_role)
VALUES
    ('SUPER_ADMIN', 'Super Admin', 'Full administrative access across tenants', TRUE),
    ('ADMIN', 'Admin', 'Administrative read/write access for one tenant', TRUE),
    ('AUDITOR', 'Auditor', 'Audit log and read-only access', TRUE),
    ('VIEWER', 'Viewer', 'Read-only admin console access', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO admin_role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM admin_roles role
JOIN admin_permissions permission ON permission.code IN ('ADMIN_READ', 'ADMIN_WRITE', 'AUDIT_READ', 'RBAC_MANAGE')
WHERE role.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM admin_roles role
JOIN admin_permissions permission ON permission.code IN ('ADMIN_READ', 'ADMIN_WRITE')
WHERE role.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM admin_roles role
JOIN admin_permissions permission ON permission.code IN ('ADMIN_READ', 'AUDIT_READ')
WHERE role.code = 'AUDITOR'
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM admin_roles role
JOIN admin_permissions permission ON permission.code = 'ADMIN_READ'
WHERE role.code = 'VIEWER'
ON CONFLICT DO NOTHING;