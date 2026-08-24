# 0005 - Store project descriptions as authored German and English fields

Date: 2026-06-06

Status: Accepted

Commit provenance: `5c9161a` ("Project description supports both DE & EN").

## Context

Mitbauen needs German and English project descriptions. A single free-text
description field cannot represent which language the creator authored, and it
forces viewers into a lossy interpretation.

## Decision

Store project descriptions as explicit `description_de` and `description_en`
fields. A project must provide at least one authored description, but either
language may be left empty.

## Consequences

- The API exposes both authored descriptions.
- The UI can choose the viewer's selected language without guessing.
- Editors can preserve one language while adding or changing the other.
- Validation applies per language field and requires at least one populated field.
