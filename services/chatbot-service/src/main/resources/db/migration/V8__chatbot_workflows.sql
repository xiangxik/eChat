CREATE TABLE chatbot_workflow_nodes (
    id BIGSERIAL PRIMARY KEY,
    chatbot_id BIGINT NOT NULL REFERENCES chatbot_configs(id) ON DELETE CASCADE,
    node_key VARCHAR(120) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    context_policy_id BIGINT NOT NULL REFERENCES context_policies(id) ON DELETE RESTRICT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_start BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chatbot_workflow_nodes_node_key_check CHECK (node_key <> ''),
    CONSTRAINT chatbot_workflow_nodes_name_check CHECK (name <> ''),
    CONSTRAINT chatbot_workflow_nodes_key_unique UNIQUE (chatbot_id, node_key)
);

CREATE UNIQUE INDEX chatbot_workflow_nodes_start_unique_idx
    ON chatbot_workflow_nodes(chatbot_id)
    WHERE is_start AND enabled;

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

ALTER TABLE conversations
    ADD COLUMN current_workflow_node_id BIGINT REFERENCES chatbot_workflow_nodes(id) ON DELETE SET NULL,
    ADD COLUMN workflow_state JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX chatbot_workflow_nodes_chatbot_idx ON chatbot_workflow_nodes(chatbot_id);
CREATE INDEX chatbot_workflow_nodes_context_policy_idx ON chatbot_workflow_nodes(context_policy_id);
CREATE INDEX chatbot_workflow_transitions_from_idx
    ON chatbot_workflow_transitions(chatbot_id, from_node_id, enabled, priority ASC, id ASC);
CREATE INDEX chatbot_workflow_transitions_to_idx ON chatbot_workflow_transitions(to_node_id);
CREATE INDEX conversations_current_workflow_node_idx ON conversations(current_workflow_node_id);

DROP INDEX IF EXISTS chatbot_configs_context_policy_id_idx;
ALTER TABLE chatbot_configs DROP COLUMN IF EXISTS context_policy_id;