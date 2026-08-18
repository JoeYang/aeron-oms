# Bar SystemClock from deterministic code

**What** — make it a build error, not a review comment, for `cluster-service` to depend on
`SystemClock`.

Split the clock types across two Bazel targets and restrict the wall-clock one:

```
//core:core          Clock, SequencedClock, FixedClock   — public
//core:system-clock   SystemClock                        — visibility: gateway, driver,
                                                            cluster-node only
```

**Why deferred** — there is no `cluster-service` code to protect. The split would be structure
with no reader today, which `CLAUDE.md` forbids scaffolding. The port itself was the necessary
part; the enforcement only becomes real once something can violate it.

**Trigger** — the first commit that puts code in `cluster-service`. Do it in that commit or
the one before it, never after: once state-machine code exists, an ambient clock read can land
at any time, and it will not announce itself.

**Cost of delay** — a `ClusteredService` that reads the wall clock replays differently every
time. The failure is not an exception; it is two nodes reaching different state from the same
log, or a journal test that passes and fails at random. `.claude/rules/architecture.md` calls a
determinism break a correctness bug rather than a style preference, and this is the specific
break it is warning about.

The whole argument for the `Clock` port is that ambient time should be impossible rather than
discouraged. Until this split lands, it is only discouraged, and the port is documentation with
a compiler-shaped hole in it.

**Note when doing this** — `core/BUILD.bazel` currently globs
`src/main/java/**/*.java` into one target. The split needs an `exclude` on the glob plus a
second `java_library`, and a row in `.claude/rules/architecture.md`, whose "Adding a package"
section requires the table and the visibility declaration to land in the same commit.
