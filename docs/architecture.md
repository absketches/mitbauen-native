# Architecture Notes

## Intent

Mitbauen Native is a reset of the current app with these constraints:

- one repo
- static frontend
- Java backend
- PostgreSQL under our control
- OAuth handled in the backend
- local Docker onboarding for contributors

## Backend principles

- Nano event-driven architecture for HTTP and internal request flow
- plain JDBC, no ORM
- SQL-first design
- DB-backed opaque sessions
- backend-owned OAuth
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

## Frontend principles

- static assets only
- no server-side rendering requirement
- all data via backend APIs
- browser only knows whether the user is authenticated; backend owns sessions

## Testing principles

- backend tests should be integration-style only; no backend unit-test layer
- backend integration tests should follow the `NanoNative/dev-console` pattern: JUnit, Surefire disabled for normal test execution, Failsafe running integration tests during `integration-test` and `verify`
- backend end-to-end and integration coverage should use Nano's built-in HTTP client instead of a browser
- browser-based frontend tests should stay lightweight and focus on component and page behavior in a real browser
- Vitest Browser Mode is the default browser test layer for frontend components; use the Playwright provider for reliable headless CI runs
- add a small number of full browser app smoke tests only when a milestone truly needs cross-page verification

## Packaging principles

- test code must not be packaged into deployment artifacts for the Pi
- Maven test dependencies stay in `test` scope, and we do not produce a `test-jar`
- frontend browser-test tooling stays in `devDependencies`
- production images should copy only the backend runtime artifact and built frontend assets, never test dependencies or browser binaries

## Deployment principles

- Nginx at the edge
- backend behind Nginx
- Postgres private to the host/network
- same-domain routing preferred for simpler cookie auth
