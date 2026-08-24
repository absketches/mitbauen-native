# 0003 - Use invite-only local authentication with backend-owned sessions

Date: 2026-04-29

Status: Accepted

Commit provenance: `caf86ee` ("Auth Service, Flyway migration refactor and gh workflows added for native builds"), `2b6c00b` ("Restrict invites to the bootstrap user"), and `00c60ae` ("Simplify invite auth and clean docs").

## Context

Mitbauen is a small community platform, not a broad public signup product. Access
should be intentionally limited while still keeping the deployment self-contained.

## Decision

Use local invite-only authentication. The backend validates invite hashes,
stores password credentials, owns opaque session cookies, and decides whether a
request is authenticated.

## Consequences

- No external identity provider is required for the core app.
- Signup is controlled through invite links.
- Session and email-verification behavior can be tested through backend APIs.
- The browser treats authentication as backend state rather than owning session logic.
