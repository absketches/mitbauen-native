# Mitbauen Lokal

Mitbauen Lokal is a small, community-focused platform for discovering local projects, sharing what help is needed, and keeping contributors in the loop. It is built as a lightweight full-stack app: a React frontend, a Java/Nano backend, PostgreSQL persistence, and a native-image deployment path for running comfortably on a small host such as a Raspberry Pi.

The goal is simple: make it easier for people to find nearby initiatives and join in without turning contribution into paperwork. The codebase should feel the same way.

## Project Shape

- `frontend/` contains the React + TypeScript app built with Vite.
- `backend/` contains the Java backend, database migrations, API services, and integration tests.
- `docs/` contains longer notes such as architecture and operational reset guides.
- `systemd/` contains deployment templates for the production service.
- `build.sh`, `deploy.sh`, and `run-native-e2e.sh` are the main project-level helper scripts.

## Prerequisites

For everyday development you will want:

- Node.js with npm
- Java 25
- Maven
- Docker, if you want local PostgreSQL or containerized checks
- Playwright browsers for frontend E2E work

The backend integration tests expect PostgreSQL to be available through the test helpers in the repository. If a test fails because a local service is missing, check `docker-compose.yml` and the test output before chasing application code.

## Getting Started

Install frontend dependencies:

```sh
cd frontend
npm install
```

Run the frontend dev server:

```sh
VITE_API_BASE_URL=http://127.0.0.1:8080/api
npm run dev
```

Use `VITE_API_BASE_URL` when Vite runs separately from the backend.

Build the frontend:

```sh
npm run build
```

Run backend verification from the backend directory:

```sh
cd backend
mvn -q verify
```

Build both frontend and backend artifacts from the repository root:

```sh
./build.sh
```

By default this creates a runnable backend JAR under `.artifacts/backend/app.jar`. To build the native binary instead:

```sh
BUILD_NATIVE=1 ./build.sh
```

## Local Runtime

The backend is configured through lowercase environment variables. The most important ones are:

```sh
app_service_http_port=8080
jdbc_database_url=jdbc:postgresql://127.0.0.1:5432/mitbauen
jdbc_database_user=mitbauen
jdbc_database_password=mitbauen_dev_password
app_public_base_url=http://127.0.0.1:8080
app_email_from='Mitbauen <no-reply@mail.mitbauen.space>'
resend_api_key=test-api-key
app_translation_openai_api_key=test-key
app_translation_openai_model=gpt-5-mini
```

Project description translation is optional. When `app_translation_openai_api_key` and `app_translation_openai_model` are set, the backend can generate display-only fallback descriptions for the missing language and marks them as tool translations in API responses.

For a local database, use the `postgres` service in `docker-compose.yml` or your own PostgreSQL instance. The application applies SQL migrations from `backend/src/main/resources/db/migrations`.

## Tests

Frontend build and browser-mode component tests:

```sh
cd frontend
npm run build
npm run test:browser
```

Backend integration tests:

```sh
cd backend
mvn -q verify
```

Native end-to-end tests expect a native backend binary to exist at `.artifacts/backend/mitbauen-native-backend` unless `BACKEND_BINARY` is set:

```sh
BUILD_NATIVE=1 ./build.sh
./run-native-e2e.sh
```

Useful E2E overrides:

```sh
APP_PORT=18082 PLAYWRIGHT_BASE_URL=http://127.0.0.1:18082 ./run-native-e2e.sh
```

## Branching Strategy

The project uses a simple staged flow:

- `main` is the stable branch. It should only receive changes that are ready to ship.
- `stg` is the integration branch. Feature work should land here first so it can be tested together.
- Feature branches should branch from `stg` and use a short, descriptive name, for example `feature/project-field-limits` or `fix/password-reset-copy`.
- Open pull requests back into `stg` unless a maintainer explicitly asks for a different target.
- Keep pull requests focused. A small PR with a clear reason is much easier to review than a heroic one.
- After `stg` has been verified, promote it into `main` through a PR or merge according to the release needs.

Commit messages should be plain and specific.

## Contribution Notes

Please keep user-facing text available in both English and German. In the frontend this usually means updating `frontend/src/i18n.ts`; in the backend prefer stable error codes that the UI can translate instead of hardcoded English responses.

When changing validation rules, update all three layers together:

- frontend form validation and limits
- backend validation constants
- database column/check constraints via a new migration

When changing API behavior, add or update backend integration tests. When changing a visible workflow, update browser or E2E tests where it makes sense.

Most importantly: contributions do not have to be huge. A tidy bug fix, a clearer error state, or a test that captures expected behavior all help the project become easier for the next person.

## Deployment

Production deployment is designed around a small host running systemd. `deploy.sh` can create a deployment bundle from local artifacts or download one from GitHub Packages. The templates live in `systemd/`.

For more operational detail, see:

- `docs/architecture.md`
