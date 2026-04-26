# Mitbauen Native

Fresh reboot of Mitbauen as a single-repo system with:

- static frontend
- Java backend
- PostgreSQL
- local Docker Compose for contributor onboarding
- Nginx as the front door in local/prod-style setups

## Planned stack

- Backend: Java 21, Nano-oriented structure, plain JDBC
- Database: PostgreSQL
- DB driver: `org.postgresql:postgresql`
- Pooling: `HikariCP`
- Migrations: `Flyway`
- Frontend: Vite + React + TypeScript
- Local infra: Docker Compose
- Edge/front door: Nginx

## Current scaffold status

This repo is intentionally just the initial skeleton:

- folder layout
- local Docker setup
- migration layout
- backend bootstrap
- frontend bootstrap
- Nginx reverse proxy

The backend currently uses a very small JDK HTTP bootstrap so contributors can start the stack immediately while the first Nano HTTP slice is wired deliberately. Nano is already included as a dependency and remains the intended backend direction.

## Repo layout

```text
backend/        Java backend
frontend/       Static SPA
db/migrations/  SQL migrations
infra/nginx/    Local reverse-proxy config
docs/           Architecture notes and roadmap
build.sh        Root build orchestrator
deploy.sh       Root deployment bundle script
```

See [docs/product-roadmap.md](docs/product-roadmap.md) for the incremental delivery plan.

## Local development

1. Copy `.env.example` to `.env`
2. Start everything:

```bash
docker compose up --build
```

The local stack uses [infra/nginx/default.conf](infra/nginx/default.conf) to proxy `/api` to the backend and everything else to the frontend dev server.
Before the application containers start, Docker Compose now runs a dedicated `browser-tests` container that executes the frontend browser suite. The backend and frontend services wait for that container to complete successfully.

If you only want to run the browser suite, use:

```bash
docker compose up --build browser-tests
```

If you want to run the backend from IntelliJ or another local IDE while keeping PostgreSQL in Docker, start the database and migrations first:

```bash
cp .env.example .env
docker compose up -d postgres flyway
```

Useful checks for that flow:

```bash
docker compose ps
docker compose logs -f postgres flyway
```

## Build and deployment structure

This repo now mirrors the root-level orchestration style from the other mixed-stack project, but keeps Mitbauen's runtime split:

- `build.sh` builds the frontend and backend, then stages deployable artifacts under `.artifacts/`
- `deploy.sh` creates a deployment bundle and can optionally upload it to a remote host
- `docker-compose.yml` remains the local development stack
- `docker-compose.deploy.yml` is the deployment-oriented compose file
- `Dockerfile` packages the staged backend JAR for deployment
- [infra/nginx/deploy.conf](infra/nginx/deploy.conf) serves the built frontend and proxies API traffic to the backend
- the local Compose-only `browser-tests` service is intentionally not part of deployment

The deployment flow is intentionally not a Quarkus-style single image that embeds the frontend into the backend artifact. For this repo, the cleaner equivalent is:

- backend packaged as a Java runtime image
- frontend built once into static assets
- Nginx serving the SPA and forwarding `/api` to the backend

## Local URLs

- App: `http://localhost:8088`
- Frontend dev server: `http://localhost:5173`
- Backend: `http://localhost:8080`
- Postgres: `localhost:5432`

## First implementation goals

1. Replace the bootstrap HTTP layer with the first Nano-backed route slice
2. Add OAuth start/callback flows in the backend
3. Add DB-backed sessions with secure cookies
4. Port the Mitbauen domain model incrementally
