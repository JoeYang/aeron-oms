## Context

The build works, the tier layout is enforced by Bazel `visibility`, and every package holds
a placeholder. Aeron is absent, so no cluster work can start.

This is a hot-path dependency, and `CLAUDE.md` lists adding one as an "ask first" item. The
version also governs cluster log and wire compatibility, which makes it harder to change
later than an ordinary library.

Findings below were measured on this machine against JDK 25.0.3, not assumed.

## Goals / Non-Goals

**Goals:**
- Aeron Cluster artifacts declared and resolvable through Bazel
- The JVM configuration Agrona requires, present for every binary and test
- Evidence, in the test suite, that the stack actually runs rather than merely resolves

**Non-Goals:**
- Any `ClusteredService`, SBE schema, ingress adapter, or snapshot logic
- CPU pinning or any latency work
- `maven_install.json` pinning — deliberately deferred, tracked in `todo/`
- Wiring Aeron into packages that have no Aeron code

## Decisions

### Version 1.52.2

The Maven Central **search API is stale** and reports `1.48.0` as latest.
`maven-metadata.xml` is authoritative and reports `1.52.2`. Four minor versions apart.
Always read `maven-metadata.xml`.

Release timing argues for 1.52.2 over holding back:

| Version | Released |
|---|---|
| 1.51.1 | 2026-06-30 |
| 1.52.0 | 2026-07-03 |
| 1.52.1 | 2026-07-08 |
| 1.52.2 | 2026-07-10 |

Three releases in seven days, then five weeks of silence. The burst-then-quiet pattern is
the signal that the line settled. The alternative, 1.51.1, was superseded three days after
release, so it carries less real soak time despite being older.

### Granular artifacts, not `aeron-all`

The dependency graph is a linear chain:

```
aeron-cluster ─→ aeron-archive ─→ aeron-driver ─→ aeron-client ─→ org.agrona:agrona 2.5.0
```

`aeron-all` bundles the same classes plus Agrona into one jar. Rejected: it defeats
per-target dependency declaration, which is the mechanism the tier layout relies on, and
bundling Agrona invites duplicate classes on the compile path.

Granular artifacts cost one extra line each and keep each target's stated dependency honest.

### `--add-exports java.base/jdk.internal.misc=ALL-UNNAMED` in `.bazelrc`

Not a tuning choice. Without it, the first `UnsafeBuffer` construction fails:

```
java.lang.IllegalAccessError: class org.agrona.UnsafeApi cannot access class
jdk.internal.misc.Unsafe (in module java.base) because module java.base does not
export jdk.internal.misc to unnamed module
```

`jdeps --jdk-internals` across all five jars shows Agrona is the **only** one touching JDK
internals, and only `jdk.internal.misc`. So exactly one flag is required, and no
`--add-opens` at all.

It goes in `.bazelrc` rather than per-target `jvm_flags`, following the precedent already
set by `--enable-native-access=ALL-UNNAMED`. Anything that touches a buffer needs it, which
is everything; repeating it per target would drift.

### Attach the dependency only to the proof test

Placeholder packages get no Aeron dependency. A declared dependency that no code calls is
scaffolding, and `CLAUDE.md` forbids building speculative structure. The correct tier wiring
also cannot be judged before the code that needs it exists — guessing now means editing it
again later.

Alternative considered: wire all five packages along the tier layout immediately. Rejected
on the scaffolding rule, and because it would add unused dependencies whose correctness
nothing checks.

## Risks / Trade-offs

- **Unpinned resolution** → `maven.install` without `maven_install.json` records extension
  fingerprints, not resolved versions, so two machines can resolve differently. Accepted
  deliberately for this change to keep the diff reviewable; recorded in `todo/` with the
  trigger that closes it.

- **Aeron needs writable shared memory, Bazel tests are sandboxed** → the proof test must
  place its aeron directory under the test's own temporary directory rather than the default
  `/dev/shm` path, or it will fail or leak state between runs. Use `ThreadingMode.SHARED` and
  delete the directory on start and shutdown.

- **A media driver test is heavier than a unit test** → it launches threads and maps files,
  so it is slower and more environment-sensitive than the rest of the suite. Accepted: a
  dependency that resolves but cannot run is exactly the failure this change exists to
  prevent, and only a running driver proves otherwise.

- **Version governs wire and log compatibility** → harder to change later than a normal
  library. Mitigated by choosing the settled line now and by recording how the version was
  determined, so the next reader does not repeat the stale-search-API mistake.

## Open Questions

- Which packages ultimately depend on which Aeron artifacts. Deferred until real code
  exists; see the decision above.
- Whether the proof test stays a permanent suite member or becomes a tagged smoke test if
  it proves slow in practice. Keep it in the default suite until measured.
