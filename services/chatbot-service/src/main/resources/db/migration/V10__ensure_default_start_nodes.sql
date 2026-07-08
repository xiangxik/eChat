INSERT INTO chatbot_workflow_nodes (chatbot_id, node_key, name, description, context_policy_id, enabled, is_start, metadata)
SELECT chatbot.id,
       'Start',
       'Start',
       'Built-in workflow entry node',
       default_policy.id,
       TRUE,
       TRUE,
       '{"x":56,"y":64}'::jsonb
FROM chatbot_configs chatbot
CROSS JOIN context_policies default_policy
WHERE default_policy.name = 'Default Context Policy'
  AND NOT EXISTS (
      SELECT 1
      FROM chatbot_workflow_nodes existing_start
      WHERE existing_start.chatbot_id = chatbot.id
        AND existing_start.enabled = TRUE
        AND existing_start.is_start = TRUE
  )
  AND NOT EXISTS (
      SELECT 1
      FROM chatbot_workflow_nodes existing_start_key
      WHERE existing_start_key.chatbot_id = chatbot.id
        AND existing_start_key.node_key = 'Start'
  );