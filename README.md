# eChat

eChat is a phased enterprise chatbot monorepo. Phase 1 creates a runnable skeleton for the user chat app, admin app, and Spring Boot backend.

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
CREATE ROLE echat LOGIN PASSWORD 'Test.132';
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

## Test and Build

Backend tests:

```bash
npm run test:backend
```

PostgreSQL-backed integration tests use Testcontainers with a pgvector PostgreSQL image. They run when Docker is available and are skipped by JUnit when Docker is unavailable.

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

API keys must not be committed. Provider configuration responses never return plaintext API keys; submitted keys are encrypted before storage.

## Phase 1 Acceptance Criteria

- Backend starts with Spring Boot 4.1.0.
- `GET /api/health` returns service health metadata.
- chat-web starts through Vite.
- admin-web starts through Vite.
- Backend includes and runs a health check test.

## Next Phases

See [docs/architecture.md](docs/architecture.md) for the architecture, module boundaries, Context Engine direction, and staged roadmap.