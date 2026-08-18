# Pin Maven dependency resolution

**What** — generate and commit `maven_install.json` so every artifact resolves to a fixed
version and hash.

```bash
bazel run @maven//:pin
```

`maven.install` currently resolves without a lock file. `MODULE.bazel.lock` does **not**
close this: it records extension fingerprints, not resolved artifact versions. Two machines,
or the same machine on two dates, can resolve differently while every declared version
string stays identical.

**Why deferred** — the generated file is large, likely over a thousand lines. The commit
rules set a 400-line hard maximum, and burying the Aeron dependency change under a generated
lock file would have made it unreviewable. Deferred deliberately when Aeron 1.52.2 was added,
not overlooked.

Confirmed by the user at the time: take the dependency now, pin later.

**Trigger** — any one of these makes it due:

1. A second machine or a CI runner builds this repository. Unpinned resolution is invisible
   while exactly one machine ever builds.
2. Anything is deployed or measured for latency. A benchmark whose dependency versions are
   not fixed is not reproducible, and `.claude/rules/trading-latency.md` requires the
   machine, JVM, and flags to be recorded with any number — the library versions belong in
   that list.
3. A dependency is added on the hot path beyond the current set.

**Cost of delay** — a silent upgrade. The global rule says dependencies must never be
upgraded silently, and today nothing enforces that. Aeron's version governs cluster log and
wire compatibility, so a resolution that quietly moves is not a cosmetic difference. The
failure would appear as a behaviour change with no diff to explain it, which is among the
most expensive kinds to debug.

**Note when doing this** — the lock file is generated, so it should land in its own commit
and be exempt from the line limit, the same way documentation is. Say so in the commit
message rather than leaving a reviewer to wonder whether the cap was ignored.
