# Security

## Authentication and Authorization

Admin APIs under `/api/admin/**` are protected by Spring Security. Admin tokens resolve to configured principals with a real `actorId`, `tenantId`, roles, and optional attributes. Tokens can be supplied through either the `X-Admin-Token` header or the `echat_admin_session` HTTP-only cookie created by `/api/admin/auth/login`. The legacy `ADMIN_TOKEN` fallback still works for local compatibility, but deployments should configure explicit `echat.security.admin-principals` entries.

RBAC is enforced at the admin boundary. `VIEWER` and `AUDITOR` principals can read allowed admin resources, `AUDITOR` can read audit logs, and write operations require `ADMIN` or `SUPER_ADMIN`. ABAC tenant checks use the optional `X-Tenant-Id` header or `tenantId` request parameter; non-`SUPER_ADMIN` principals can only operate in their configured tenant.

Chat APIs support anonymous sessions through `anonymousSessionId` and reject conversation creation unless either `userId` or `anonymousSessionId` is present. A later enterprise phase should replace this with tenant-aware identity, scoped user tokens, and RBAC/ABAC permissions.

## Secrets

Provider API keys are encrypted before they are stored in `provider_configs.encrypted_api_key`. Admin responses expose only `hasApiKey` and optional external `apiKeySecretRef`; plaintext keys are never returned. Production deployments should set `API_KEY_ENCRYPTION_SECRET` to a high-entropy value and prefer a managed secret store for long-lived provider credentials.

## Input and Abuse Controls

DTO validation enforces field length limits. Chat messages are normalized, capped at 8000 characters, reject unsupported control characters, and block common instruction-override phrases before context assembly. `/api/chat/**` and `/api/admin/**` use Redis-backed per-client rate limits with process-local fallback.

## Logging and Audit

`requestId` and `traceId` are propagated through headers and MDC. Audit logs record provider, model, chatbot, context policy, and chat runtime events without storing message bodies or API keys. Admin audit events include the resolved actor id, tenant id, display name, roles, and attributes so configuration changes can be traced to a real principal instead of a shared token label. Metadata sanitization filters fields with names such as `apiKey`, `secret`, `password`, `token`, and `content`.

## Browser and Edge Controls

CORS is configured by `CORS_ALLOWED_ORIGINS`. Keep it restricted to deployed frontend origins. Terminate TLS before the service, forward client IP headers from trusted proxies only, and protect metrics endpoints at the edge.