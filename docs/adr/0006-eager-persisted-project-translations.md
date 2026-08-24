# 0006 - Eagerly warm and persist project description translations

Date: 2026-08-24

Status: Accepted

Commit provenance: `f599608` ("feat: Add project description translation fallback") and `8da4af1` ("undo temporary backfill logic for project translations").

## Context

Projects may have only a German or only an English creator-authored description.
Viewers should get a useful fallback in their selected language when one exists,
but generated translation should not be confused with creator-authored text.

Calling a translation provider during every read would add latency, cost, and failure modes to normal project browsing. A one-off backfill script would add an
operational path that would help backfill translations for existing projects.

## Decision

Generate missing-language project description translations eagerly from project
create/update flow by sending an asynchronous translation warmup event. Store
successful results in `project_description_translations`.

Read APIs resolve `descriptionViews` from authored descriptions plus persisted
translation rows. Translated views are marked as tool translations so the UI can
show a disclaimer. Temporary manual backfill code and scripts have been removed in commit `8da4af1`.

## Consequences

- Project reads can use stored translations instead of calling the provider.
- Translation provider failures do not block ordinary read responses.
- Translation rows include source language, target language, source text hash,
  provider and model so stale generated text can be detected and replaced.
- The UI must distinguish creator authored text from tool generated text.
- Tests seed stored translation rows rather than calling the live provider.
