---
paths: ["src/main/java/**/gateway/**", "**/*.proto", "**/sbe/*.xml"]
---
# Order-entry interface design

## SBE / wire schema

- Schema files are versioned artifacts — every schema carries an explicit `version`, bumped
  on every change
- Field ids are permanent: never reuse, renumber, or repurpose an id once deployed. Removals
  reserve the id.
- Additive change only within a major version
- Decode into flyweights over the received buffer — never copy a whole frame to read one field
- Validate on decode at the boundary: length, template id, schema version, and field ranges.
  A malformed frame is rejected at the gateway and never reaches `domain`.
- Generated codec sources are build outputs, not committed artifacts

## FIX sessions

- Session-level and application-level concerns stay separate. Sequence numbers, heartbeats,
  logon, and resend logic belong to the session layer — never to `domain`.
- Venue-specific tag dialects are absorbed by the adapter; `domain` sees one internal
  representation
- Persist session state (sequence numbers) durably enough to resume without a venue reset

## Control plane (gRPC or REST, if added)

- Version all paths and services (`/v1/...`); never expose an unversioned endpoint
- Canonical error codes and a consistent error shape carrying a request id for correlation
- Auth on every control-plane call — verify identity and permission separately
- The control plane must not bypass the sequencer to mutate state. A read-only query may go
  direct; anything that changes state is a sequenced command.

## General

- Never expose internal ids, stack traces, or system details in an outbound message
- Idempotency: a client order id replayed must not create a second order
- Back-pressure is part of the published contract — define what a client observes when the
  gateway cannot accept more, rather than dropping silently
