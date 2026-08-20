## 1. Tests first

- [x] 1.1 Write `ClockTest` covering substitutability through a lambda double
- [x] 1.2 Write `SequencedClockTest`: reads reflect last update, repeated reads are stable,
      equal timestamp accepted, regression rejected, negative rejected, value retained after
      a rejected update
- [x] 1.3 Write the replay-determinism test: two instances fed the same sequence agree at
      every step
- [x] 1.4 Write `SystemClockTest`: plausible current time, non-decreasing across reads
- [x] 1.5 Write `FixedClockTest`: value never changes
- [x] 1.6 Add boundary cases: zero, `Long.MAX_VALUE`, and an update at the maximum
- [x] 1.7 Run the suite and confirm it fails for the right reason

## 2. The port

- [x] 2.1 Add `Clock` as a single-method `@FunctionalInterface` returning `timeNanos()`
- [x] 2.2 Document the unit and epoch on the interface, and that callers convert

## 3. Implementations

- [x] 3.1 Add `SequencedClock` with `update(long)` and the monotonicity invariant
- [x] 3.2 Add `SystemClock` reading the host clock
- [x] 3.3 Add `FixedClock`
- [x] 3.4 Confirm none of them import Aeron or Agrona

## 4. Verification

- [x] 4.1 `bazel test //...` exits zero with the new tests reported as executed
- [x] 4.2 Confirm the new tests are picked up by the existing test target
- [x] 4.3 `scripts/lint.sh` exits zero
- [x] 4.4 Confirm `core/BUILD.bazel` gained no dependency

## 5. Deferred work

- [x] 5.1 Record in `todo/` that `SystemClock` is not yet mechanically barred from
      deterministic code, with the trigger that makes the Bazel visibility split due
