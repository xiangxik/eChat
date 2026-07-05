CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE app_schema_version_marker (
    id BIGSERIAL PRIMARY KEY,
    marker VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO app_schema_version_marker (marker)
VALUES ('phase-1-skeleton')
ON CONFLICT (marker) DO NOTHING;

CREATE TABLE provider_configs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL UNIQUE,
    type VARCHAR(64) NOT NULL,
    base_url VARCHAR(1024),
    api_key_secret_ref VARCHAR(512),
    encrypted_api_key TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT provider_configs_type_check CHECK (type IN (
        'OPENAI_COMPATIBLE', 'ANTHROPIC', 'AZURE_OPENAI', 'GEMINI', 'OLLAMA', 'CUSTOM'
    ))
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

CREATE TABLE context_policies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(160) NOT NULL UNIQUE,
    description TEXT,
    dsl_content TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    model_id BIGINT REFERENCES model_configs(id) ON DELETE SET NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT context_policies_version_check CHECK (version > 0)
);

CREATE TABLE chatbot_configs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(160) NOT NULL UNIQUE,
    description TEXT,
    context_policy_id BIGINT REFERENCES context_policies(id) ON DELETE SET NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    chatbot_id BIGINT NOT NULL REFERENCES chatbot_configs(id) ON DELETE RESTRICT,
    user_id VARCHAR(128),
    anonymous_session_id VARCHAR(128),
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
    chatbot_id BIGINT NOT NULL REFERENCES chatbot_configs(id) ON DELETE CASCADE,
    user_id VARCHAR(128),
    scope VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    embedding vector(${embeddingDimension}),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT memory_items_scope_check CHECK (scope IN ('SHORT_TERM', 'LONG_TERM', 'GLOBAL'))
);

CREATE INDEX model_configs_provider_id_idx ON model_configs(provider_id);
CREATE INDEX context_policies_model_id_idx ON context_policies(model_id);
CREATE INDEX chatbot_configs_context_policy_id_idx ON chatbot_configs(context_policy_id);
CREATE INDEX conversations_chatbot_status_updated_idx ON conversations(chatbot_id, status, updated_at DESC);
CREATE INDEX conversations_user_updated_idx ON conversations(user_id, updated_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX conversations_anonymous_updated_idx ON conversations(anonymous_session_id, updated_at DESC) WHERE anonymous_session_id IS NOT NULL;
CREATE INDEX messages_conversation_created_idx ON messages(conversation_id, created_at ASC);
CREATE INDEX memory_items_chatbot_scope_updated_idx ON memory_items(chatbot_id, scope, updated_at DESC);
CREATE INDEX memory_items_chatbot_user_updated_idx ON memory_items(chatbot_id, user_id, updated_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX memory_items_metadata_gin_idx ON memory_items USING GIN (metadata);
CREATE INDEX memory_items_embedding_hnsw_idx ON memory_items USING hnsw (embedding vector_cosine_ops) WHERE embedding IS NOT NULL;