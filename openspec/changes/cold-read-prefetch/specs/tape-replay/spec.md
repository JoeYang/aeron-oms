# tape-replay — delta

## ADDED Requirements

### Requirement: Cluster recovery can prefetch the archive ahead of the replayer

When started with `oms.replay.prefetch=true`, the cluster node SHALL concurrently stream
every regular file in the archive directory through the page cache using
`oms.replay.prefetch.threads` named daemon threads (default 4), striped by file, and the
threads SHALL exit when all files are read. Prefetching SHALL be off by default, SHALL NOT
change the replay report or any recovery output, and SHALL be failure-contained: a missing
archive directory completes as a no-op, an unreadable file is counted and skipped while the
remaining files are still read, and no prefetch error propagates to the node.

#### Scenario: Cold fat recovery with prefetch enabled

- **GIVEN** the extracted `local-fatheartbeats-1m` tape with its pages evicted from the
  page cache
- **WHEN** the node recovers with `oms.replay.prefetch=true`
- **THEN** the replay report counts 1,000,000 messages, identical to a run without the flag,
  and the wall time improves materially on the clean-cold baseline

#### Scenario: Prefetch failure cannot fail recovery

- **GIVEN** an archive directory in which one file is unreadable
- **WHEN** prefetch runs
- **THEN** the unreadable file is recorded as skipped, every other file is fully read, and
  the node's recovery proceeds unaffected
