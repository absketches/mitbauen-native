# Architecture Notes

## Intent

- one repo
- one packaged app artifact that serves the SPA shell and `/api`
- Java backend
- PostgreSQL under our control
- invite-only local auth with backend-validated invite hashes
- local Docker onboarding for contributors

## Backend principles

- Nano event-driven architecture for HTTP and internal request flow
- plain JDBC, no ORM
- SQL-first design
- DB-backed opaque sessions
- backend-owned password hashing and session cookies
- explicit module boundaries

## Planned backend modules

- auth
- users
- invites
- projects
- roles
- applications
- messages
- comments
- notifications
- email
- db
- security
- shell

## Frontend principles

- static assets only
- no server-side rendering requirement
- all data via backend APIs
- browser only knows whether the user is authenticated; backend owns sessions
- frontend build output is baked into the backend artifact for jar and native deployments

## Testing principles

- backend tests should be integration-style only; no backend unit-test layer
- backend end-to-end and integration coverage should use Nano's built-in HTTP client instead of a browser
- browser-based frontend tests should stay lightweight and focus on component and page behavior in a real browser
- Vitest Browser Mode is the default browser test layer for frontend components; use the Playwright provider for reliable headless CI runs
- add a small number of full browser app smoke tests only when a milestone truly needs cross-page verification

## Packaging principles

- test code must not be packaged into deployment artifacts for the Pi
- Maven test dependencies stay in `test` scope, and we do not produce a `test-jar`
- deployment should center on a single app artifact that contains backend code, built frontend assets, and embedded SQL migrations
- production artifacts must not require `psql`, a separate static-file deployment just to serve the app shell

## Deployment principles

- the same artifact should be what CI tests and what the Pi runs under `systemd`
- Postgres is managed separately from the app process and keeps its persistent volume across releases
- schema migrations are forward-only and tracked in `schema_migrations`
- the app should run pending embedded SQL migrations during normal startup before serving requests
- the app artifact serves both frontend routes and backend APIs directly
