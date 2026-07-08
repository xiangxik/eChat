ALTER TABLE context_policies
    ADD COLUMN system_managed BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO context_policies (name, description, dsl_content, version, model_id, enabled, system_managed)
VALUES (
    'Default Context Policy',
    'System managed welcome policy for the built-in Start workflow node.',
    '<contextPolicy name="default-welcome" maxTokens="512">
  <system priority="100">Reply with exactly: Welcome! How can I help you today?</system>
  <output>
    <section name="system" />
  </output>
</contextPolicy>',
    1,
    NULL,
    TRUE,
    TRUE
)
ON CONFLICT (name) DO UPDATE SET
    description = EXCLUDED.description,
    dsl_content = EXCLUDED.dsl_content,
    version = EXCLUDED.version,
    enabled = TRUE,
    system_managed = TRUE,
    updated_at = NOW();

UPDATE chatbot_workflow_nodes
SET node_key = 'Start',
    name = 'Start',
    context_policy_id = (SELECT id FROM context_policies WHERE name = 'Default Context Policy'),
    enabled = TRUE,
    is_start = TRUE,
    updated_at = NOW()
WHERE is_start = TRUE;