# eChat

eChat is a phased enterprise chatbot monorepo for a user chat app, an Ant Design admin console, and a Spring Boot backend with provider/model configuration, context assembly, chat runtime, audit logs, and production hardening foundations.

## Structure

```text
apps/
  chat-web/          React + TypeScript chatbot UI
  admin-web/         React + TypeScript Ant Design admin UI
services/
  chatbot-service/   Spring Boot backend service
docs/                Architecture and API notes
```

## Prerequisites

- Node.js 22 LTS or newer
- npm 10 or newer
- Java 25.0.3 LTS
- Maven 3.9.16
- PostgreSQL 18 with pgvector
- Redis

## Install Frontend Dependencies

```bash
npm install
```

## Run

Create the local PostgreSQL databases as a PostgreSQL admin user. Flyway runs as the application user, so that user must own the database or have `CREATE` permission on the target schema.

```sql
CREATE ROLE echat LOGIN PASSWORD 'change-me-local-postgres';
CREATE DATABASE echat OWNER echat;
CREATE DATABASE echat_smoke OWNER echat;

\connect echat
CREATE EXTENSION IF NOT EXISTS vector;
ALTER SCHEMA public OWNER TO echat;
GRANT USAGE, CREATE ON SCHEMA public TO echat;

\connect echat_smoke
CREATE EXTENSION IF NOT EXISTS vector;
ALTER SCHEMA public OWNER TO echat;
GRANT USAGE, CREATE ON SCHEMA public TO echat;
```

If the databases already exist, ensure they are owned by `echat` or run the `ALTER SCHEMA` and `GRANT` statements above in each database.

Start the backend:

```bash
mvn -f services/chatbot-service/pom.xml spring-boot:run
```

The backend seeds default disabled providers on startup. For an isolated smoke database, use the smoke profile against PostgreSQL. By default it connects to the `echat_smoke` database unless `DB_NAME` is set; the database must exist and have pgvector available.

PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'smoke'; mvn -f services/chatbot-service/pom.xml spring-boot:run
```

Bash:

```bash
SPRING_PROFILES_ACTIVE=smoke mvn -f services/chatbot-service/pom.xml spring-boot:run
```

Start the chatbot web app:

```bash
npm run dev:chat
```

Start the admin web app:

```bash
npm run dev:admin
```

Default local URLs:

- Backend: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- chat-web: `http://localhost:5173`
- admin-web: `http://localhost:5174`

## Docker Compose One-Command Startup

Create a local Compose environment file from the checked-in template. Keep real provider API keys out of source control.

PowerShell:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Bash:

```bash
cp .env.example .env
docker compose up --build
```

Compose starts PostgreSQL + pgvector 18, Redis, the Spring Boot backend, chat-web, and admin-web. The browser-facing API URL is controlled by `VITE_API_BASE_URL` in `.env`; for local Compose it should usually remain `http://localhost:8080`.

Default Compose URLs:

- Backend: `http://localhost:8080`
- Backend health: `http://localhost:8080/api/health`
- Actuator health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- chat-web: `http://localhost:5173`
- admin-web: `http://localhost:5174`

Useful Compose commands:

```bash
docker compose ps
docker compose logs -f chatbot-service
docker compose down
docker compose down -v
```

Use `docker compose down -v` only when you intentionally want to delete the local PostgreSQL and Redis volumes.

## Database Migrations

Flyway migrations live in `services/chatbot-service/src/main/resources/db/migration`. They run automatically when `chatbot-service` starts. `V1__init_schema.sql` creates the `vector` extension and core tables, and later migrations evolve memory, eval, and audit features.

For local Docker Compose, the `pgvector/pgvector:pg18` image creates the configured database and the app runs migrations against it using:

- `DB_HOST=postgres`
- `DB_PORT=5432`
- `DB_NAME=${POSTGRES_DB}`
- `DB_USERNAME=${POSTGRES_USER}`
- `DB_PASSWORD=${POSTGRES_PASSWORD}`

If Flyway fails after local schema experimentation, reset only local Compose data with:

```bash
docker compose down -v
docker compose up --build
```

## Configure the First Chatbot

1. Start the stack with `docker compose up --build`.
2. Open `http://localhost:5174` and sign in with the `ADMIN_TOKEN` value from `.env`.
3. In Providers, create an `OPENAI_COMPATIBLE` provider. Set the provider base URL, for example `https://api.openai.com/v1`, and paste your API key only in the admin form.
4. In Models, create a `CHAT` model for that provider. Use the provider's model name, for example `gpt-4.1-mini`, enable streaming if the provider supports it, and save.
5. In Context Policies, create a policy from the default DSL template, select the model, validate it, then save it.
6. In Chatbots, create an enabled chatbot and attach the context policy.
7. Set `VITE_CHATBOT_ID` in `.env` to that chatbot id if it is not `1`, then restart chat-web with `docker compose up -d --build chat-web`.
8. Open `http://localhost:5173` and send a message.

## Smoke Test

After `docker compose up --build`, verify the stack from another terminal:

PowerShell:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
docker compose ps
```

Bash:

```bash
curl -fsS http://localhost:8080/api/health
docker compose ps
```

Expected health response:

```json
{"status":"UP","service":"chatbot-service","version":"0.1.0"}
```

Then check:

- `http://localhost:5174` loads admin-web and can sign in with `ADMIN_TOKEN`.
- Admin can create provider, model, context policy, and chatbot records.
- `http://localhost:5173` loads chat-web and can send one message to the configured chatbot.

## Troubleshooting

- `chatbot-service` cannot connect to PostgreSQL: check `docker compose ps postgres`, `POSTGRES_*` values in `.env`, and `docker compose logs postgres`.
- Flyway cannot create `vector`: ensure the image is `pgvector/pgvector:pg18`, then recreate local volumes with `docker compose down -v` if the database was initialized from a different image.
- Admin login returns 401: use the exact `ADMIN_TOKEN` value from `.env`; changing it requires restarting `chatbot-service`.
- Browser calls the wrong API URL: set `VITE_API_BASE_URL=http://localhost:8080` in `.env` and rebuild/restart the frontend container. The Docker image writes `/env.js` at container startup from this value.
- Chat returns provider/model errors: create and enable provider, model, context policy, and chatbot in that order. A real provider API key must be entered through admin-web before real LLM calls can succeed.
- Port already in use: change `SERVER_PORT`, `POSTGRES_PORT`, or `REDIS_PORT` in `.env`. For frontend host ports, edit the `5173:80` or `5174:80` mappings in `docker-compose.yml`.

## Test and Build

Backend tests:

```bash
npm run test:backend
```

PostgreSQL-backed integration tests use the local PostgreSQL/pgvector database with an isolated `echat_test` schema. Override `TEST_DB_*` variables when the test database differs from local defaults.

Frontend builds:

```bash
npm run build:chat
npm run build:admin
```

Lint frontend code:

```bash
npm run lint
```

## Backend Configuration

The backend reads local infrastructure and LLM defaults from environment variables. Useful defaults are defined in `services/chatbot-service/src/main/resources/application.yml`.

Common variables:

- `SERVER_PORT`
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
- `LLM_PROVIDER`, `LLM_MODEL`, `LLM_BASE_URL`, `LLM_API_KEY`
- `CONTEXT_EMBEDDING_DIMENSION` defaults to `1536` for pgvector memory embeddings
- `API_KEY_ENCRYPTION_SECRET` protects provider API keys before they are stored
- `ADMIN_TOKEN` authenticates admin API calls and admin-web cookie sessions
- `ADMIN_ACTOR_ID`, `ADMIN_DISPLAY_NAME`, `ADMIN_TENANT_ID`, `ADMIN_ROLES` map the local admin token to a real audit actor, tenant, and RBAC roles
- `ADMIN_COOKIE_SECURE` sets the admin session cookie `Secure` flag; use `true` behind HTTPS
- `CORS_ALLOWED_ORIGINS` controls browser origins for `/api/**`
- `CHAT_RATE_LIMIT_PER_MINUTE`, `ADMIN_RATE_LIMIT_PER_MINUTE` control Redis-backed request limits

API keys must not be committed. Provider configuration responses never return plaintext API keys; submitted keys are encrypted before storage.

## Production Notes

- Set a high-entropy `API_KEY_ENCRYPTION_SECRET` and rotate provider keys through the admin API or an external secret manager before production use.
- Replace the default `ADMIN_TOKEN`, configure explicit admin principals with least-privilege roles, terminate TLS at the edge, and set `ADMIN_COOKIE_SECURE=true` when serving admin-web over HTTPS.
- Restrict `CORS_ALLOWED_ORIGINS` to deployed frontend origins, not broad localhost patterns.
- Run PostgreSQL migrations before deploying new application versions and back up `provider_configs`, `context_policies`, `chatbot_configs`, conversations, memory, and audit tables.
- Keep Redis available for distributed rate limiting and chat stream state. The service has a local fallback, but it is per-process only.
- Expose only required Actuator endpoints through the edge. Health can be public; metrics should be protected by infrastructure auth.
- Ship logs to a centralized sink with `requestId`/`traceId`, and avoid logging message bodies or provider secrets.
- Review [docs/security.md](docs/security.md), [docs/context-engine.md](docs/context-engine.md), and [docs/harness-engineering.md](docs/harness-engineering.md) before enabling external users.

## MVP Acceptance Criteria

- Backend starts with Spring Boot 4.1.0.
- `GET /api/health` returns service health metadata.
- Admin API is protected by Spring Security token/cookie authentication.
- Provider API keys are encrypted at rest and never returned in responses.
- Audit logs are recorded and visible in admin-web.
- Redis-backed request rate limiting is enabled with local fallback.
- Actuator health/info/metrics are available according to configuration.
- Backend tests and frontend builds pass.

## Next Phases

See [docs/architecture.md](docs/architecture.md) for the architecture, module boundaries, Context Engine direction, and staged roadmap.