## Why

No message exists, so nothing can be sequenced, journalled, or replayed. Every remaining
capability waits on this: the `ClusteredService` needs something to decode, journal tests need
something to replay, and the `Clock` port has no caller.

The schema is also the most expensive thing in the repository to change. Schema id, message ids,
and field ids become permanent the moment a message reaches a cluster log, because a later
renumbering makes existing logs undecodable. That argues for establishing the *pipeline* and its
guard rails before committing to a message set.

## What Changes

- Add `sbe/`, holding the message schema XML and nothing else.
- Add `//sbe-java`, which **generates** Java codecs from that schema on every build. No
  generated source is committed anywhere.
- Add `uk.co.real-logic:sbe-tool` 1.39.0 to the dependency set and repin the lock.
- Define one message, `Heartbeat`, carrying a single timestamp — enough to prove generate,
  encode, decode, and round-trip, without committing to field types not yet chosen.
- Pin the wire identity in a test, so schema id, template id, version, and block length cannot
  change silently.

Not in scope: the OMS message set. `NewOrder`, `Cancel`, `Amend`, `Fill` and `Reject` force
decisions about price representation, identifier types, and which venue semantics to model
toward. That is a separate conversation, and `ideas/exchange-simulator.md` already warns that an
invented protocol teaches the gateway the wrong lessons.

## Capabilities

### New Capabilities
- `message-schema`: messages are defined once in a schema, and the codecs used at runtime are
  generated from it rather than maintained beside it.

### Modified Capabilities
- `build-toolchain`: gains a requirement that generated sources are produced by the build.

## Impact

- `sbe/` — new package, schema XML only
- `sbe-java/` — new package, generated library plus its round-trip tests
- `MODULE.bazel` and `maven_install.json` — sbe-tool added, lock repinned to 40 artifacts
- `.claude/rules/architecture.md` — two new rows; `//core` no longer described as holding codecs
- `README.md`, `CLAUDE.md` — package list

The Agrona version is unchanged: sbe-tool 1.39.0 depends on agrona 2.5.0, which is already
pinned, so no second Agrona reaches the path.
