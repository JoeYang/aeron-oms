---
paths: ["src/main/java/**", "**/BUILD.bazel", "MODULE.bazel"]
---
# Architecture — tiers and import direction

## Tiers

| Tier | Package | Responsibility | May import |
|---|---|---|---|
| gateway | `io.joeyang.oms.gateway` | Protocol adapters (FIX, SBE, admin). Decode inbound, encode outbound. No business logic. | `common` |
| sequencer | `io.joeyang.oms.sequencer` | Aeron cluster: total ordering, consensus, journal, replay driver. | `common` |
| domain | `io.joeyang.oms.domain` | Deterministic state machine — order lifecycle, matching, risk, positions. | `common` |
| egress | `io.joeyang.oms.egress` | Outbound publication of acks, fills, drops, snapshots. | `common` |
| common | `io.joeyang.oms.common` | SBE codecs, buffer flyweights, shared value objects. | (nothing) |

Enforce with Bazel `visibility` on each `java_library` target, not by convention alone.
A tier boundary that only exists in this document is not a boundary.

## The determinism rule

`domain/` is the replay-critical core. Given the same ordered input stream it MUST produce
identical state. Inside `domain/`:

- No wall-clock reads — time arrives as a field on the inbound command, stamped by the sequencer
- No `Random`, no generated UUIDs — any seed or identifier comes from the command
- No iteration over `HashMap`/`HashSet` where order affects output — use ordered or Agrona
  collections with deterministic iteration
- No I/O, no blocking logging, no threads
- No dependency on `gateway`, `sequencer`, or `egress`

A determinism break is a correctness bug, not a style preference: replay diverges and the
journal stops being a source of truth.

## Everything enters through the sequencer

The governing principle: **all inputs are sequenced.** Not only order flow, but also market
data, reference data, configuration, and time.

The consequence is that a journal becomes a complete description of a run. Nothing reaches
the state machine from outside the ordered stream, so replay reproduces the run exactly, and
journal testing (@.claude/rules/testing.md) can serve as the primary test method.

> **Design deferred.** The principle is recorded here; the mechanism is not designed yet.
> Open questions include how reference-data and config updates are framed as commands, how
> time is injected and at what granularity, and what all of this costs on the ingress path.
> Do not implement against this section until that discussion has happened.

## Import direction

Dependencies point inward toward `common`. `domain` never imports a tier that performs I/O.
A `PostToolUse` hook flags `domain/*.java` files importing `gateway`/`sequencer`/`egress` —
treat that warning as a blocker, not a hint. The hook is a backstop for the Bazel visibility
rules, not a replacement for them.

## Adding a tier

A new tier needs a row in the table above and a matching Bazel visibility rule, in the same
commit. Do not create a package that no table row describes.
