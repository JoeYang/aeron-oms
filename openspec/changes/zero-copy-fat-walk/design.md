# Design — zero-copy fat walk

## Decision 1: slices to the tool, not a gather API into the service

| option | pros | cons |
|---|---|---|
| Slice handler in the walker; replay tool checksums in place (chosen) | deterministic core untouched; goldens still gate the outcome end-to-end | fat chains bypass the service object during zero-copy replay |
| Gather-view parameter on the service | one code path everywhere | changes the replay-critical core's contract for a tool-only win; the cluster runtime always delivers contiguous buffers, so the gather path would exist only for replay |
| Fuse checksum into the reassembly copy | trivial | adds work: the service still checksums the copy afterwards |

The cluster's own log adapter reassembles fragments before the service ever sees a message,
so a gather-capable service API would be exercised by exactly one caller — this tool. The
walker seam is where fragmentation is visible; that is where the optimization belongs.

## Decision 2: why bypassing the service stays honest

Golden outputs are captured at record time from what the real service echoed. A zero-copy
replay that matches all 1M golden checksums has demonstrated, message by message, that the
in-place incremental checksum equals the service's contiguous one on real data — on top of
the exhaustive split-equivalence unit tests. The copy path stays the default; CI's journal
gate still runs the real service. Recorded limitation: during zero-copy replay the
`SequencedClock` monotonicity invariant is enforced only for entries that still pass through
the service (unfragmented ones), not for fat chains.

## Decision 3: carry-at-boundary incremental checksum

`PayloadChecksum` folds little-endian longs; fragment boundaries are not 8-byte aligned, so
the incremental form keeps `(partialWord, partialBytes)` between slices: bytes accumulate
little-endian into the partial word, every completed word folds with rotate-XOR, and
`finish()` folds a non-empty partial as the zero-padded tail — bit-identical to the
contiguous tail rule. The slice hot loop still folds aligned 8-byte words with `getLong`
once the carry is filled; only the few bytes at each boundary go byte-wise (~7 bytes per
23 fragments per message, noise against 32 KB).

## Decision 4: fall back rather than fail on odd chains

A chain whose BEGIN fragment is too short to hold the cluster session headers, or whose
entry is not a session message, silently takes the existing copy path. These do not occur
on real tapes (first fragments carry ~1376 bytes of body); the fallback keeps the walker's
"one decode path" property for everything the slice mode does not claim.
