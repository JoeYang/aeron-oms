---
name: sbe-gen
description: Regenerate SBE codecs from the message schema, inspect what was generated, and run the codec tests. Use when adding or changing a message in sbe/message-schema.xml, or when you need to see the generated encoder and decoder API.
compatibility: Requires bazel and the //sbe-java package.
metadata:
  author: aeron-oms
  version: "1.0"
---

Regenerate SBE codecs, show what came out, and prove it still round-trips.

## First, the thing that makes this dangerous

**Schema id, message id, field id and version are permanent.** Once a message has been written
to a cluster log, changing any of them makes existing logs undecodable. There is no migration —
the log is the audit record and it is append-only.

So this skill is not a build wrapper. Its job is to make a wire-format change **visible before
it is committed**, because nothing else will.

## There is no manual generation step

Codecs are produced by a `genrule` on every build. You never run `SbeTool` by hand, and no
generated source is committed. `bazel build` regenerates; that is the whole mechanism.

If you find yourself reaching for the generator directly, stop — the build already did it.

## Workflow

### 1. Capture the wire identity before you change anything

Skip this only if you are adding a brand-new message and touching nothing existing.

```bash
bazel build //sbe-java:codecs_srcjar
T=$(mktemp -d) && unzip -q -o bazel-bin/sbe-java/codecs.srcjar -d "$T"
find "$T" -name '*Encoder.java' -exec grep -HoP '(SCHEMA_ID|TEMPLATE_ID|BLOCK_LENGTH|SCHEMA_VERSION) = [0-9]+' {} \; \
  | sed "s|$T/||" | sort > /tmp/sbe-identity-before.txt
cat /tmp/sbe-identity-before.txt
```

### 2. Edit the schema

`sbe/message-schema.xml` is the only file to edit. Choose ids deliberately:

- A new message gets the next unused `id`, never a reused one
- A new field gets the next unused `id` **within that message**, and is appended
- Never renumber, never reorder existing fields, never change a field's `primitiveType`

### 3. Regenerate and look at what you got

```bash
bazel build //sbe-java:codecs_srcjar
unzip -Z1 bazel-bin/sbe-java/codecs.srcjar | sort
```

A schema error fails the build here. `stop.on.error` and `warnings.fatal` are both on, so a
warning is a failure — that is deliberate, do not turn it off to get past a message.

To read the generated API, extract and open the encoder or decoder:

```bash
T=$(mktemp -d) && unzip -q -o bazel-bin/sbe-java/codecs.srcjar -d "$T" && echo "$T"
```

### 4. Compare the wire identity

```bash
T=$(mktemp -d) && unzip -q -o bazel-bin/sbe-java/codecs.srcjar -d "$T"
find "$T" -name '*Encoder.java' -exec grep -HoP '(SCHEMA_ID|TEMPLATE_ID|BLOCK_LENGTH|SCHEMA_VERSION) = [0-9]+' {} \; \
  | sed "s|$T/||" | sort > /tmp/sbe-identity-after.txt
diff /tmp/sbe-identity-before.txt /tmp/sbe-identity-after.txt && echo "wire identity unchanged"
```

**If anything changed for a message that already existed, stop and say so.** Report the exact
diff and ask whether the log-format change is intended. Do not proceed on your own judgement.

### 5. Add tests for anything new

Every message needs, in `sbe-java/src/test/java/io/joeyang/oms/sbe/`:

- a round-trip test asserting each field survives encode then decode
- boundary values for each fixed-width field — minimum, maximum, zero, and negative one
- a non-zero offset test, because messages never sit at offset zero in a real stream
- an entry in the wire-identity test pinning its schema id, template id and block length

### 6. Run the gate

```bash
bazel test //sbe-java:sbe_java_test --nocache_test_results --test_output=all
scripts/test.sh
scripts/lint.sh
```

`--nocache_test_results` matters: a cached pass does not prove the codecs were exercised after
your edit.

## When `wireIdentityIsPinned` fails

This is the failure that matters, and the obvious reaction is wrong.

**Do not update the constants in the test to match the new output.** That silently converts a
broken log format into a green build, which is the exact outcome the test exists to prevent.

The failure means one of two things:

| Cause | What to do |
|---|---|
| Accidental — a field was added, reordered, or retyped in a way that moved the layout | Revert the schema edit |
| Intentional format change | Stop. Report it to the user and get an explicit decision — this is on the "ask first" list in CLAUDE.md |

Only after an explicit decision does the pinned constant change, and it changes in the same
commit as the schema.

## Committing

Schema changes are **always their own commit**, per the commit rules. Do not fold a schema
change in with the code that uses it.

Nothing generated is committed. If you find generated `.java` files staged, something is wrong —
they belong only inside `bazel-bin`.
