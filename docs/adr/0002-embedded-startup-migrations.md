# 0002 - Run embedded database migrations during application startup

Date: 2026-04-30

Status: Accepted

Commit provenance: `e4f6f1e` ("Add project flows and clean migration history") and `62ea77e` ("Refactor DB startup and report CI coverage").

## Context

The app needs PostgreSQL schema changes to be applied reliably across local
development, CI, and the Pi deployment. Requiring a separate migration command
or external migration service would make single-artifact deployment harder.

## Decision

Keep SQL migrations embedded in the backend artifact and run pending migrations
during normal application startup before serving requests.

## Consequences

- Deploying a new app version is enough to apply pending schema changes.
- Migrations must be forward-only and safe to run at startup.
- Startup fails early if the database is unavailable or migration state is invalid.
- Production does not need `psql` or a separate migration runner for normal releases.
