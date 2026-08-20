# Two-way SBE tool for recorded logs

**What** — a tool that reads an Aeron Archive recording and renders each message as text using
the SBE schema, and can go the other way: turn authored messages into a log.

**Why** — the log is the only complete description of a run, and right now it is opaque bytes.
Three jobs need it readable:

- **Debugging.** "What actually happened at position 4,182,016" is currently unanswerable.
- **Journal testing.** The stated test strategy is replay-based. Authoring a log and asserting
  the resulting state is far cheaper than driving a cluster through a scenario by hand.
- **Audit.** The security rules require reconstructing the full order lifecycle for any point
  in time. That obligation is unmet while nothing can read the record.

## Blocked by

Nothing structural, but it is worth little until there are real messages. Against a single
`Heartbeat` it would print a timestamp.

## Traps

### 1. A cluster log is not a stream of your messages

This is the one that wastes a day. Each entry is nested framing:

```
Aeron data header (32 bytes)
  └─ Aeron Cluster SessionMessageHeader   schemaId 111, blockLength 24
       └─ your payload                    schemaId 1
```

Verified against 1.52.2. Decoding with only our schema reads Aeron's envelope as if it were our
message, and produces confident garbage rather than an error. The tool must decode schema 111
first, then hand the remainder to schema 1 — and it must skip entries that are neither, because
the log also carries session open/close, timers, and snapshot markers.

### 2. Decode each message at its own version, not the current one

Every message carries its `blockLength` and `version` in the header. A tool that decodes
everything with today's constants silently misreads history the first time the schema is
extended.

This works only because the schema stays append-only. The tool's correctness is therefore
downstream of the discipline the `sbe-gen` skill enforces — if a breaking change ever lands, old
entries become undecodable and no tool can recover them.

### 3. The write direction contradicts a security rule

`.claude/rules/security.md` states the journal is "append-only, never edited in place". A tool
that writes into logs is a tool that can violate the audit record.

The resolution has to be structural, not a convention: the write path may only ever **create a
new log**, never open an existing one for modification. If that cannot be enforced by
construction, do not build the write direction at all — an audit record with an edit tool beside
it is not an audit record.

### 4. Synthesising a valid recording is harder than writing bytes

An Archive recording is not just a file: there is a catalog, recording ids, positions, and
segment layout to satisfy. A hand-built recording will most likely be rejected on replay, or —
worse — accepted and subtly wrong.

The cheaper approach for test fixtures is to invert it: drive a single-node cluster with the
authored messages and let the cluster produce the log. The log becomes an *output* of the
fixture rather than an input to be forged. Slower per test, and correct by construction.

### 5. Aeron already ships tooling — extend rather than rebuild

`io.aeron.archive.ArchiveTool` and `io.aeron.cluster.ClusterTool` both exist. `ArchiveTool`
already offers `describeAll`, `describeRecording`, and catalog inspection. The genuinely missing
piece is payload decoding against *our* schema, not recording navigation.

Start by wrapping them and adding schema-aware rendering. Rebuilding recording traversal is
weeks of work to reach parity with something already in the dependency.

## Shape, if it gets built

- **Read first, and ship it alone.** Reading is safe and immediately useful. Writing is
  dangerous and needs the structural guarantee above.
- Output should be diffable text, so two runs can be compared — that is what makes it useful in
  a test assertion rather than only at a terminal.
- Filter by position range, session, and template id. A full log dump is unreadable at any
  realistic volume.
