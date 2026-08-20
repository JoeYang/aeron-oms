## Context

No code reads time yet. The rules already require a supplied time source and forbid ambient
clock reads in deterministic code, but nothing implements or enforces that.

Aeron Cluster hands a `ClusteredService` its timestamp as a parameter on every callback. That
value is written into the replicated log, which is what makes it reproducible on replay.

## Goals / Non-Goals

**Goals:**
- One port for time, narrow enough that a test double is trivial
- A deterministic implementation whose only input is a sequenced timestamp
- A wall-clock implementation for code outside the replay boundary
- Tests that fail if the deterministic implementation stops being deterministic

**Non-Goals:**
- Wiring into a `ClusteredService` — none exists
- Choosing the cluster `timeUnit` — a consensus module configuration decision
- The ingress timestamp for latency measurement — a different concern
- Mechanically preventing `SystemClock` use in deterministic code — deferred, see risks

## Decisions

### One method, nanoseconds since the epoch

```java
public interface Clock {
  long timeNanos();
}
```

Nanoseconds because the latency budgets are stated in microseconds; a millisecond-resolution
port cannot express them. Since the epoch, rather than a monotonic reading, because the value
must be meaningful in an audit record and comparable across processes.

Named `timeNanos()` rather than `nanoTime()` deliberately: `System.nanoTime()` is monotonic
and has no epoch, and reusing that name would invite the wrong assumption. It matches Aeron's
own `ClusterClock.timeNanos()`.

Single-method, so a lambda is a valid test double and `@FunctionalInterface` documents that.

Alternative considered: reuse Agrona's `EpochClock` or `NanoClock`. Rejected — both would put
Agrona on `//core`'s compile path for an interface that needs no dependency, and `EpochClock`
is millisecond-resolution.

### The deterministic clock is a latch, not an Aeron type

`SequencedClock` holds a `long` and is advanced by whoever received it from the log:

```java
clock.update(timestamp);   // once, at the top of each callback
```

It therefore needs no Aeron import. That keeps the whole port compilable and testable with no
media driver running, which is exactly the constructibility test `design.md` demands. The
Aeron-specific part is the wiring, and the wiring belongs to `cluster-service` when it exists.

Alternative considered: an adapter implementing `Clock` over Aeron's `ClusterClock`. Rejected
as premature — it would add a dependency to serve no current caller, and the latch already
models what the cluster provides.

### The caller converts units, not the clock

`update(long timeNanos)` takes nanoseconds. Aeron Cluster's timestamp unit is configurable and
defaults to milliseconds, so the conversion must happen where the unit is known — at the
service boundary, using `Cluster.timeUnit()` or `ClusterClock.convertToNanos`.

Putting the conversion inside the clock would mean storing a unit it cannot verify, and would
silently manufacture precision that the configured cluster clock does not have.

### Time may repeat, but never go backwards

`update` accepts a timestamp equal to the current one and rejects a smaller one.

Equal must be allowed: a cluster delivers several messages within one clock tick, and
rejecting that would break normal operation. Backwards must be rejected: it means either
mis-wiring or a corrupt log, and a silently accepted regression corrupts state in a way that
replays identically and so looks correct.

Rejection throws. This is an invariant violation, not an expected runtime condition like
back-pressure, so the no-throw hot-path rule does not apply — the cost in correct operation is
one predictable branch, and an exception allocated only when the system is already broken.

## Risks / Trade-offs

- **`SystemClock` is not barred from deterministic code** → nothing stops a future
  `ClusteredService` importing it, which is exactly the failure this port exists to prevent.
  Bazel visibility could enforce it by splitting the target, but there is no
  `cluster-service` code to protect yet and the split would be structure with no reader.
  Recorded in `todo/` with the trigger that makes it due.

- **Nanosecond precision may be fictional** → if the cluster runs a millisecond clock, values
  are millisecond-resolution numbers scaled to nanoseconds. The port cannot detect this.
  Mitigated by documenting it and by placing conversion at the boundary that knows the unit.

- **A latch is only as correct as its caller** → `SequencedClock` cannot tell whether the
  value it was handed came from the log or from `System.currentTimeMillis()`. No type can
  enforce that; it is a review and wiring concern.

## Open Questions

- The cluster `timeUnit`. Milliseconds cannot express a 50 µs budget, so this needs settling
  before latency work, but it is a consensus module configuration decision and not part of
  this change.
