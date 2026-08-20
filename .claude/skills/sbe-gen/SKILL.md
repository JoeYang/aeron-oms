---
name: sbe-gen
description: Review, regenerate and test SBE message schema changes. Classifies every schema edit as append-only or breaking, checks versioning and field alignment, regenerates codecs, diffs the wire identity, and runs the codec tests. Use whenever sbe/message-schema.xml is added to or changed.
compatibility: Requires bazel and the //sbe-java package.
metadata:
  author: aeron-oms
  version: "2.0"
---

Review an SBE schema change, then regenerate and test it.

## This is a review, not a build wrapper

Codecs are produced by a `genrule` on every build and nothing generated is committed, so
`bazel build` already *is* the generator. There is no manual generation step to orchestrate.

The work that needs judgement is deciding whether a schema edit is safe. **Schema id, message
id, field id and version are permanent once a message has been written to a cluster log.** The
log is append-only and it is the audit record, so there is no migration path. A breaking change
does not fail loudly at the point of the edit — it fails later, when an old log cannot be
replayed.

## Step 1 — classify the change

Diff the schema against what is committed and classify **every** edit before doing anything else.

```bash
git diff -- sbe/message-schema.xml
git diff origin/main -- sbe/message-schema.xml   # if mid-branch
```

| Edit | Verdict |
|---|---|
| New message with an unused `id`, appended | **safe** |
| New field appended to the **end** of a message's block, with `sinceVersion` | **safe** |
| New repeating group or var-length field appended **after** all existing ones | **safe** |
| Widening documentation: `description`, `semanticType` | **safe** |
| Marking a field or message `deprecated="N"` | **safe** |
| Removing a field or message | **BREAKING** |
| Reordering existing fields | **BREAKING** |
| Changing a field's `id` | **BREAKING** |
| Changing a field's `primitiveType` or `type` | **BREAKING** |
| Inserting a field anywhere but the end | **BREAKING** |
| Changing `blockLength` on an existing message | **BREAKING** |
| Any change to the `messageHeader` composite | **CATASTROPHIC** |
| Changing `schemaId` or `byteOrder` | **CATASTROPHIC** |
| Reusing the id of a deleted field or message | **BREAKING**, and silently so |

Report the classification to the user. If anything is worse than **safe**, stop and escalate —
schema changes are on the "ask first" list in CLAUDE.md. Do not proceed on your own judgement.

**Why append-only works:** the header carries `blockLength`. A newer encoder writes a longer
block; an older decoder reads the fields it knows and skips to `offset + blockLength`. That is
the entire compatibility mechanism, and it only holds if new fields go on the end.

## Step 2 — versioning

- Increment `version` on `<sbe:messageSchema>` for **every** schema change, additive or not.
- Mark every newly added field, group or message with `sinceVersion="<new version>"`. It
  defaults to `0`, which claims the field has always existed — wrong, and it makes an old
  decoder's behaviour undefined.
- Use `deprecated="<version>"` to retire a field. **Never delete one.**
- `version` is the wire contract. `semanticVersion` is documentation and means nothing to a
  decoder. Do not confuse them.

### What an older message looks like to a newer decoder

Verified, not assumed. A field declared `sinceVersion="1"`, read from a version-`0` message,
returns its **null value** rather than garbage. For an `int64` that is `Long.MIN_VALUE`.

Two consequences, and the second is a trap:

- Every read of a field with `sinceVersion > 0` must handle the null case explicitly. It is not
  optional — old messages are in the log forever.
- **The null sentinel is a legal value of the type.** `Long.MIN_VALUE` is a perfectly valid
  `int64`, so "this field did not exist yet" and "the sender really meant `Long.MIN_VALUE`" are
  indistinguishable. Constrain the range with `minValue`, or pick a type whose sentinel cannot
  arise naturally, before relying on the distinction.

## Step 3 — layout, alignment and padding

SBE lays fields out in declaration order at consecutive offsets. Nothing pads for you.

- **Order fields largest-first**: `int64`, then `int32`, then `int16`, then `int8`/`char`.
  Declaring `int8` before `int64` puts the 8-byte field on a 1-byte boundary.
- **Keep every field naturally aligned** — an 8-byte field on an 8-byte offset, 4 on 4, 2 on 2.
  Unaligned access works on x86 but costs, and can straddle a 64-byte cache line.
- **Use `offset` to pad deliberately** where ordering alone cannot align a field. `offset` is
  the only alignment control SBE has.
- **Watch the 64-byte cache line.** A hot-path message whose block fits in one line is
  meaningfully faster. Flag it when a block crosses 64 bytes.
- **Consider reserving `blockLength`.** Setting it explicitly, larger than the sum of the
  fields, leaves room for future appends *without blockLength ever changing*. The layout never
  moves and old decoders keep working. This is the padding that pays; propose it for messages
  expected to grow.
- **`presence="constant"` costs zero wire bytes.** Use it for genuinely fixed values.
- **`presence="optional"` burns a sentinel** from the field's range via `nullValue`. Cheap for
  an `int64`, expensive for a small integer.

## Step 4 — one-way doors in sizing

These look like micro-optimisations and are irreversible:

| Choice | Cap | Widening it later is |
|---|---|---|
| `numInGroup` as `uint8` | 255 entries per group | **BREAKING** |
| var-data `length` as `uint8` | 255 bytes | **BREAKING** |
| enum `encodingType` as `uint8` | 255 values | **BREAKING** |

Pick the width for the worst case you can defend, not for today's data.

## Step 5 — regenerate and inspect

Capture the wire identity **before** editing, unless the message is brand new:

```bash
bazel build //sbe-java:codecs_srcjar
T=$(mktemp -d) && unzip -q -o bazel-bin/sbe-java/codecs.srcjar -d "$T"
find "$T" -name '*Encoder.java' -exec grep -HoP '(SCHEMA_ID|TEMPLATE_ID|BLOCK_LENGTH|SCHEMA_VERSION) = [0-9]+' {} \; \
  | sed "s|$T/||" | sort > /tmp/sbe-identity-before.txt
```

Regenerate and see what came out:

```bash
bazel build //sbe-java:codecs_srcjar
unzip -Z1 bazel-bin/sbe-java/codecs.srcjar | sort
```

A schema error fails the build here. `stop.on.error` and `warnings.fatal` are both on, so a
warning is a failure. **Do not turn them off to get a message through.**

Diff the identity:

```bash
T=$(mktemp -d) && unzip -q -o bazel-bin/sbe-java/codecs.srcjar -d "$T"
find "$T" -name '*Encoder.java' -exec grep -HoP '(SCHEMA_ID|TEMPLATE_ID|BLOCK_LENGTH|SCHEMA_VERSION) = [0-9]+' {} \; \
  | sed "s|$T/||" | sort > /tmp/sbe-identity-after.txt
diff /tmp/sbe-identity-before.txt /tmp/sbe-identity-after.txt && echo "wire identity unchanged"
```

A `BLOCK_LENGTH` that moved on an **existing** message means the layout changed. Report it.

## Step 6 — tests

In `sbe-java/src/test/java/io/joeyang/oms/sbe/`, every message needs:

- round trip: every field survives encode then decode
- boundary values per fixed-width field: minimum, maximum, zero, negative one
- a non-zero offset test — messages never sit at offset zero in a real stream
- an entry in the wire-identity test pinning schema id, template id and block length

And for any schema that has been extended, the test that proves the compatibility claim:

- **forward compatibility**: encode a message at the new version, decode it with the previous
  version's `blockLength` and `version`, and assert the older fields still read correctly.
  Without this, "append-only is safe" is a documentation claim rather than a fact.

Run the gate:

```bash
bazel test //sbe-java:sbe_java_test --nocache_test_results --test_output=all
scripts/test.sh
scripts/lint.sh
```

`--nocache_test_results` matters: a cached pass does not prove the codecs were exercised after
the edit.

## When `wireIdentityIsPinned` fails

The obvious reaction is wrong.

**Do not update the constants in the test to match the new output.** That converts a broken log
format into a green build, which is exactly what the test exists to prevent.

| Cause | Action |
|---|---|
| Accidental — a field was added, reordered or retyped and moved the layout | Revert the schema edit |
| Intentional format change | Stop, report it, get an explicit decision |

The pinned constant changes only after that decision, and in the same commit as the schema.

## Committing

Schema changes are **always their own commit**, per the commit rules. Never fold one in with the
code that uses it.

Nothing generated is committed. If generated `.java` files are ever staged, something is wrong —
they exist only inside `bazel-bin`.
