# Product Roadmap

## Goal

Build Mitbauen Native as a sequence of small vertical slices, where each slice is usable on its own and proven with automated tests.

We are not trying to port the entire upstream app in one pass. The priority is to establish a reliable delivery rhythm:

- one user-visible capability at a time
- database, backend, and frontend baked into one working app path
- tests at the right levels before moving on

## Delivery Rules

Each milestone should meet these conditions before we start the next one:

- the feature works through the packaged app artifact in local Docker
- the database shape is created by versioned SQL migrations
- backend behavior is covered by integration tests
- frontend behavior is covered by browser-based component/page tests where useful
- only add a full browser journey when the slice truly needs it
- scope stays intentionally narrow; adjacent features wait for the next milestone
- deployment keeps a persistent Postgres volume and only applies unapplied migrations

## Test Strategy

- Backend test model: integration tests only, following the `NanoNative/dev-console` Maven pattern
- Backend integration/E2E framework: JUnit with Nano's built-in HTTP client
- Maven execution model: Surefire skipped for normal tests, Failsafe runs integration tests in `integration-test` and `verify`
- Frontend browser test layer: Vitest Browser Mode
- CI browser provider for frontend tests: Playwright via `@vitest/browser-playwright`
- Backend slices should be exercised through Nano's event-driven HTTP flow, not only by directly calling repository code
- Browser tests should stay sparse and high-value; most API-path coverage should live in faster Nano-driven backend integration tests
- Deployment artifacts must exclude test code, test dependencies, and browser binaries

## Recommended First Slice

Start with a read-only public project feed.

Why this should be first:

- it exercises the full stack without needing auth or session infrastructure
- it captures the core product idea early: visible projects with founder commitment
- it gives us a stable data model to build later write flows on top of
- it is small enough to finish cleanly, including backend integration tests and one minimal browser check

## Milestone 1: Public Project Feed

### Outcome

Visitors can open the app and browse projects ordered by lifecycle state and recency, with founder commitment shown prominently.

### Scope

- create the first schema for `users`, `projects`, and `project_roles`
- seed a few representative projects for local development
- add a backend read endpoint for listing projects
- add a frontend page that renders the project feed
- serve the SPA shell and API through the same packaged app artifact

### Keep Out for Now

- auth
- project creation
- voting
- applications
- comments
- messaging

### Done When

- the feed loads from the real database, not hardcoded JSON
- cards show title, short description, status, founder role, commitment, and open roles
- ordering matches product intent: `active`, then `completed`, then `dormant`, with deterministic secondary sorting

### Tests

- migration test or startup smoke test proving schema can be applied cleanly
- backend integration test for feed ordering and mapping through Nano-backed route handling
- backend HTTP test through Nano's client for the list endpoint response shape
- frontend browser test for project card rendering and feed page loading
- optional single smoke browser flow: visitor loads the feed and sees seeded projects in the expected order

## Milestone 2: Invite-Only Sign-In And Sessions

### Outcome

A user can register from an invite link, sign in with email and password, and land in the app with a secure session cookie.

### Scope

- `invite_links`, `invite_redemptions`, `password_credentials`, `users`, and `sessions` tables
- invite validation endpoint in the backend
- registration, login, logout, and session endpoints in the backend
- secure cookie session handling
- frontend authenticated-shell awareness

### Keep Out for Now

- profile editing
- password reset and email verification

### Tests

- backend integration tests for session creation and cookie behavior
- backend integration tests for invite validation, acceptance, and reuse
- backend HTTP tests through Nano's client for register, login, logout, and session
- one browser smoke flow for invite registration and sign-in through the real app shell

## Milestone 3: Create And Edit A Project

### Outcome

An authenticated user can create a project and later edit its core fields.

### Scope

- create project form
- edit project form
- validation rules for title, description, why it matters, commitment, and requested roles
- owner-only edit authorization

### Keep Out for Now

- applications
- voting
- comments

### Tests

- backend integration tests for validation and authorization
- backend HTTP tests for create and edit flows
- frontend browser tests for form validation states
- one browser smoke flow: sign in, create a project, reload, edit it, and verify feed/project page updates

## Milestone 4: Apply To A Role And Owner Review

### Outcome

A signed-in user can apply to a project role, and the owner can accept or reject that application.

### Scope

- application creation
- duplicate prevention
- owner cannot apply to own project
- owner review controls on the project page
- basic application status model

### Keep Out for Now

- private messaging
- notifications

### Tests

- backend integration tests for duplicate and owner-guard rules
- backend integration tests for owner decision transitions
- backend HTTP tests for application submit and decision routes
- one browser smoke flow: applicant applies, owner reviews, status changes are visible

## Milestone 5: Private Messaging Per Application

### Outcome

Each application gets a private thread between applicant and project owner.

### Scope

- message thread per application
- thread view on the project page or dedicated message page
- inbox list ordered by last update

### Keep Out for Now

- email delivery
- rich notification rules

### Tests

- backend integration tests for thread access control
- backend integration tests for message ordering
- backend HTTP tests for thread fetch and reply flows
- one browser smoke flow: applicant sends a message, owner replies, both can reopen the thread

## Milestone 6: Comments And In-App Notifications

### Outcome

Users can comment on projects, and relevant users receive in-app notifications.

### Scope

- project comments
- notification table and unread state
- navbar notification UI
- mark-as-read behavior

### Keep Out for Now

- email fan-out
- advanced notification preferences

### Tests

- backend integration tests for notification fan-out rules
- backend HTTP tests for comment creation and notification read state
- frontend browser tests for unread badge behavior
- one browser smoke flow: new comment creates a visible in-app notification for the owner

## Milestone 7: Basic Profiles And Account Operations

### Outcome

Users have a simple profile page and a small set of account-level actions.

### Scope

- own profile page
- public profile page
- account settings or account details surface

### Tests

- backend integration tests for profile ownership and visibility rules
- backend HTTP tests for profile read and update endpoints
- one browser smoke flow for viewing and updating a basic profile

## Milestone 8: Hardening And Parity Review

### Outcome

The rebuilt app covers the intended core workflows and is ready for deliberate parity-gap review against the upstream app.

### Scope

- email outbox for notifications
- project lifecycle transitions
- delete project and delete account flows
- test and CI hardening
- parity audit against upstream behavior

### Tests

- regression coverage for destructive flows
- CI path that runs backend integration tests and frontend browser suites predictably

## Suggested Build Order Summary

1. Public project feed
2. Invite-only sign-in and sessions
3. Create and edit project
4. Apply to a role and owner review
5. Private messaging
6. Comments and in-app notifications
7. Invite management and profiles
8. Hardening and parity review

## Immediate Next Step

If we follow this roadmap, the next implementation target should be Milestone 1.

That gives us the first truly end-to-end feature with a tight test story:

- indexed SQL migration for the first project tables
- Nano-backed event-driven HTTP route for listing projects
- frontend feed page
- seeded local data
- fast backend HTTP tests using Nano's client
- one small browser smoke test, if the slice needs it
