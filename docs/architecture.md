# eChat Architecture

## Goal

eChat is an enterprise chatbot platform split into three deployable surfaces:

- `apps/chat-web`: user-facing chatbot experience.
- `apps/admin-web`: Ant Design style operations console for configuring providers, models, prompts, context policy, and safety controls.
- `services/chatbot-service`: Spring Boot API service that owns persistence, runtime orchestration, context assembly, security controls, and integrations.

## Current Phase

Phase 1 creates a runnable monorepo skeleton:

- React + TypeScript + Vite chatbot placeholder.
- React + TypeScript + Vite + Ant Design admin placeholder.
- Spring Boot 4.1.0 service with JPA, PostgreSQL, Redis, Flyway, Validation, Actuator, OpenAPI, and tests.
- `GET /api/health` for smoke testing.

## Module Boundaries

### chat-web

Owns end-user conversation UX. It should call backend APIs only through a typed API client and keep local UI state lightweight. Later phases will add streaming chat, conversation history, upload/context controls, and user preference views.

### admin-web

Owns operational configuration. It will manage LLM providers, model defaults, context templates, retrieval policy, safety policy, audit views, and tenant/project configuration. It should not contain secret material beyond masked display values.

### chatbot-service

Owns backend business capabilities:

- LLM provider and model configuration.
- API key storage with encryption or external secret manager integration.
- Context Engine and prompt/context assembly.
- Conversation, short-term memory, long-term memory, retrieval, tool result, runtime, and metadata records.
- RBAC, validation, sensitive information filtering, audit logging, observability, and exception handling.

## Backend Layers

```text
api          REST controllers, request/response DTOs, validation
application  use cases and transaction boundaries
domain       domain models, policies, Context Engine contracts
infra        JPA repositories, Redis adapters, LLM provider adapters, encryption, audit sinks
config       Spring configuration, properties, security, OpenAPI
```

The first phase only implements the `api` health endpoint and configuration foundations. Later business modules should follow these boundaries rather than placing orchestration logic in controllers.

## Context Engine Direction

The Context Engine will provide deterministic context assembly before an LLM call. It should support a declarative DSL inspired by React/XML composition without copying proprietary agent implementations.

Planned built-in variables:

- `conversation`
- `context`
- `shortTermMemory`
- `longTermMemory`
- `userProfile`
- `retrievalResults`
- `toolResults`
- `runtime`
- `metadata`

Example future DSL shape:

```xml
<Context maxTokens="12000">
  <System source="context.systemPolicy" />
  <Conversation limit="20" />
  <Memory type="shortTermMemory" strategy="recent" />
  <Retrieval source="retrievalResults" topK="8" />
  <Metadata include="tenantId,locale,channel" />
</Context>
```

The first implementation should parse this into a neutral internal tree, evaluate it against a context variable map, and emit ordered prompt/context blocks. A later rule engine can reuse the same internal model for conditional inclusion, ranking, redaction, and budget allocation.

## Security Baseline

Security requirements are designed into the architecture from the first phase:

- Store API keys encrypted or in a managed secret store.
- Never return raw secrets through admin APIs.
- Validate all inbound DTOs.
- Resolve admin tokens to real principals with tenant, roles, and attributes before allowing configuration access.
- Enforce RBAC on admin reads/writes and ABAC tenant checks through `X-Tenant-Id` or `tenantId` scope.
- Filter sensitive values from prompts, logs, traces, and audit views.
- Record audit events for provider, model, prompt, and context policy changes with actor id and tenant id.

## Observability

The service includes Spring Boot Actuator. Later phases should add structured JSON logs, trace IDs, request metrics, LLM latency/cost metrics, context token metrics, and audit event exports.

## API Notes

Current endpoint:

```http
GET /api/health
```

Response:

```json
{
  "status": "UP",
  "service": "chatbot-service",
  "version": "0.1.0"
}
```

## Roadmap

1. Phase 1: runnable monorepo skeleton and health check.
2. Phase 2: persistence model for provider/model configuration with encrypted secret handling.
3. Phase 3: admin CRUD screens with RBAC-ready APIs and audit logging.
4. Phase 4: Context Engine parser, evaluator, validation, and DSL documentation.
5. Phase 5: streaming chat endpoint, chat-web SSE client, conversation storage, and token budgeting.
6. Phase 6: retrieval with pgvector, long-term memory, observability dashboards, and production hardening.