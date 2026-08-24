# 0004 - Keep the project feed members-only

Date: 2026-05-10

Status: Accepted

Commit provenance: `79301b0` ("Make project feed members only").

## Context

Projects can contain contributor needs, founder details, discussion entry points,
and other community context that should not be broadly public by default.

## Decision

Require an authenticated, email-verified member session to view the project feed
and project details.

## Consequences

- Anonymous visitors see the public shell and authentication entry points, not the feed.
- Feed and detail APIs enforce access on the backend.
- Browser tests cover anonymous and member-visible states.
- Public-facing profile surfaces must be deliberate exceptions, not accidental feed leakage.
