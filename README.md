# Mitbauen Native

Fresh reboot of Mitbauen as a single-repo system with:

- static frontend
- Java backend
- PostgreSQL
- local Docker Compose for contributor onboarding
- Nginx as the front door in local and host deployments

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

This repo is intentionally still early-stage, but the first end-to-end slices are in place:

- public project feed
- invite-only registration and login
- DB-backed sessions
- local Docker setup
- migration layout
- Nginx reverse proxy

## Repo layout

```text
backend/        Java backend
frontend/       Static SPA
db/migrations/  SQL migrations
infra/nginx/    Local reverse-proxy config
docs/           Architecture notes and roadmap
build.sh        Root build orchestrator
deploy.sh       Host deployment bundle + install script
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
docker compose up -d postgres migrate
```

Useful checks for that flow:

```bash
docker compose ps
docker compose logs -f postgres migrate
```

## Host deployment structure

The Pi deployment now follows a host-style setup rather than Docker on the target machine:

- `build.sh` builds the frontend and backend, then stages deployable artifacts under `.artifacts/`
- `migrate.sh` applies the SQL migrations explicitly through `psql`
- `deploy.sh` packages the backend artifact, frontend `dist`, DB migrations, the migration script, a `systemd` unit, and a host `Nginx` config
- the backend runs as a `systemd` service on the Pi
- host `Nginx` serves the built SPA and proxies `/api` to the backend on `localhost`
- local `docker-compose.yml` remains development-only

The generated host deployment expects:

- Java 21
- PostgreSQL client tools (`psql`)
- Nginx
- PostgreSQL
- `systemd`
- a remote user with `ssh` access and `sudo` privileges

The deployed shape is:

- backend jar under `/opt/mitbauen/backend`
- or, when `DEPLOY_BACKEND_MODE=native`, a native backend binary under `/opt/mitbauen/backend`
- frontend files under `/var/www/mitbauen`
- migrations under `/opt/mitbauen/db/migrations`
- migration script at `/opt/mitbauen/migrate.sh`
- backend env file at `/etc/mitbauen/mitbauen.env`
- `systemd` unit at `/etc/systemd/system/mitbauen-backend.service`
- host `Nginx` site config at `/etc/nginx/sites-available/mitbauen.conf`

If the remote env file does not exist yet, `deploy.sh` will create it from the example template and stop, so you can fill in the real database credentials before rerunning the deployment. On a normal deploy, the script runs `migrate.sh` on the Pi before restarting the backend service.

For a jar-based host deploy:

```bash
DEPLOY_REMOTE_USER=your-user \
DEPLOY_REMOTE_HOST=your-pi-host \
./deploy.sh
```

For a native-image host deploy:

```bash
DEPLOY_REMOTE_USER=your-user \
DEPLOY_REMOTE_HOST=your-pi-host \
DEPLOY_BACKEND_MODE=native \
./deploy.sh
```

If you already have a prepared native artifact or deploy bundle from CI, set `DEPLOY_USE_EXISTING_ARTIFACTS=1` to skip rebuilding locally before packaging or uploading it.

## Local URLs

- App: `http://localhost:8088`
- Frontend dev server: `http://localhost:5173`
- Backend: `http://localhost:8080`
- Postgres: `localhost:5432`

## First implementation goals

1. Replace the bootstrap HTTP layer with the first Nano-backed route slice
2. Harden the invite-only auth flow for production deployment
3. Port the remaining Mitbauen domain model incrementally
