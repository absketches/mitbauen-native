# 0001 - Package the app as a single deployable artifact

Date: 2026-04-27

Status: Accepted

Commit provenance: `b3ab318` ("Consolidate single-binary packaging and migrations"), with later deployment refinement in `1738248` ("Refine CI release flow and package-based deploys").

## Context

Mitbauen is intended to run comfortably on a small host such as a Raspberry Pi.
The project has a Java backend, a React frontend, PostgreSQL persistence, and a
native-image deployment path.

Maintaining a separate static frontend deployment, reverse proxy routing, and
backend process would add operational complexity for a small installation.

## Decision

Package the backend, built frontend assets, and embedded SQL migrations into one
deployable app artifact. The app artifact serves both the SPA shell and `/api`.

## Consequences

- Production deploys center on one artifact under `systemd`.
- CI can test the same application shape that the Pi runs.
- The backend owns serving frontend routes and API routes.
- Frontend assets must be built before packaging the backend artifact.
