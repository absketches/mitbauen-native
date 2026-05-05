# Mitbauen Native

Fresh reboot of Mitbauen as a single-repo system with:

- one packaged app artifact that serves both SPA routes and `/api`
- Java 25 + Nano on the backend
- PostgreSQL as a separately managed datastore
- Vite + React + TypeScript on the frontend
- local Docker Compose for contributor onboarding
- host deployment through `systemd`

## Planned stack

- Backend: Java 25, Nano-oriented structure, plain JDBC
- Database: PostgreSQL
- DB driver: `org.postgresql:postgresql`
- Pooling: `HikariCP`
- Migrations: versioned SQL embedded in the app artifact
- Frontend: Vite + React + TypeScript
- Local infra: Docker Compose
- Packaging: single jar or native binary with baked frontend assets

## Current scaffold status

This repo is intentionally still early-stage, but the first end-to-end slices are in place:

- public project feed
- invite-only registration and login
- project create, detail, and edit flow
- DB-backed sessions
- embedded SQL migrations
- single-binary app shell
- local Docker setup

## Repo layout

```text
backend/    Java backend, resources, and embedded SQL migrations
frontend/   Static SPA source
systemd/    Host service templates
docs/       Architecture notes
build.sh    Root build orchestrator
deploy.sh   Host deployment bundle + install script
```

## Local development

1. Copy `.env.example` to `.env`
2. Start everything:

```bash
docker compose up --build
```

Before the application container starts, Docker Compose runs a dedicated `browser-tests` container that executes the frontend browser suite. The app then runs migrations through the same packaged backend artifact before starting the HTTP server.

If you only want to run the browser suite, use:

```bash
docker compose up --build browser-tests
```

Backend integration tests run against a real PostgreSQL Docker container. `mvn verify` in `backend/` therefore expects a working local Docker daemon in addition to Java 25.

CI behavior:

- `Native E2E` runs on pushes to every branch and on pull requests
- `Native Release` runs only after a successful `Native E2E` run for a `main` push, then publishes the tested Raspberry Pi `arm64` artifacts

If you want to run the backend from IntelliJ or another local IDE while keeping PostgreSQL in Docker:

```bash
cp .env.example .env
docker compose up -d postgres
cd frontend && npm run build
```

Then start the backend app from the IDE with `jdbc_database_url`, `jdbc_database_user`, and `jdbc_database_password` set in the run configuration. The app shell is served from the built frontend assets, so that one frontend build step matters for IDE-based runs.

Useful checks:

```bash
docker compose ps
docker compose logs -f postgres backend
```

## Host deployment structure

The Pi deployment follows a single-binary host setup rather than Docker on the target machine:

- `build.sh` builds the frontend first, then packages the backend artifact with baked frontend assets and embedded SQL migrations
- `deploy.sh` packages the backend artifact, a `systemd` unit, and an env template, or downloads the latest published deploy bundle from GitHub Packages
- the backend runs as a `systemd` service on the Pi
- PostgreSQL is managed separately on the host

The generated host deployment expects:

- PostgreSQL
- `systemd`
- a remote user with `ssh` access and `sudo` privileges
- Java 25 only when deploying the jar mode
- `curl` when pulling the deploy bundle from GitHub Packages

For the preferred native-image deploy, no host Java runtime is required.

The deployed shape is:

- backend jar under `/opt/mitbauen/backend`
- or, when `DEPLOY_BACKEND_MODE=native`, a native backend binary under `/opt/mitbauen/backend`
- backend env file at `/etc/mitbauen/mitbauen.env`
- `systemd` unit at `/etc/systemd/system/mitbauen-backend.service`
- PostgreSQL data in its normal host-managed data directory or volume

If the remote env file does not exist yet, `deploy.sh` will create it from the example template and stop, so you can fill in the real database credentials before rerunning the deployment. On a normal deploy, the installed `systemd` unit starts the app directly, and the app runs any pending embedded SQL migrations before opening the HTTP server.

For email verification, add these runtime variables to `/etc/mitbauen/mitbauen.env`:

- `app_public_base_url=https://www.mitbauen.space`
- `app_email_from=Mitbauen <no-reply@mail.mitbauen.space>`
- `resend_api_key=...`

The production database volume is not meant to be cleaned up between releases. Forward-only migrations are tracked in `schema_migrations`, so new app versions only apply the SQL files that have not already been recorded.

For a jar-based host deploy:

```bash
DEPLOY_REMOTE_USER=your-user \
DEPLOY_REMOTE_HOST=your-pi-host \
DEPLOY_BACKEND_MODE=jar \
./deploy.sh
```

For the default native-image host deploy:

```bash
DEPLOY_REMOTE_USER=your-user \
DEPLOY_REMOTE_HOST=your-pi-host \
./deploy.sh
```

If you already have a prepared native artifact or deploy bundle from CI, set `DEPLOY_USE_EXISTING_ARTIFACTS=1` to skip rebuilding locally before packaging or uploading it.

To install the latest published native deploy bundle directly on a Pi, use the GitHub Packages mode:

```bash
GITHUB_PACKAGES_USERNAME=your-github-login \
GITHUB_PACKAGES_TOKEN=your-classic-pat \
DEPLOY_SOURCE=github-packages \
DEPLOY_INSTALL_LOCAL=1 \
./deploy.sh
```

Optional knobs for that path:

- `DEPLOY_RELEASE_VERSION=2026.04.120930` to pin a specific published version instead of the latest one
- `GITHUB_PACKAGES_OWNER` / `GITHUB_PACKAGES_REPO` if the package source changes

GitHub’s Maven package registry currently requires authentication with a personal access token (classic), including for package installation: https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry

## Local URLs

- App and API: `http://localhost:8080`
- Postgres: `localhost:5432`

## Near-term goals

1. Harden the single-binary startup flow for production deployment
2. Complete the invite-only auth slice with more production safeguards
3. Port the remaining Mitbauen domain model incrementally
