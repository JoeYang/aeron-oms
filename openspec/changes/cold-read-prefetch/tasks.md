# Tasks — cold-read-prefetch

- [x] 1. Spec: proposal, design, tape-replay delta; `openspec validate cold-read-prefetch`
- [x] 2. Failing tests: `ArchivePrefetcherTest` — full directory read, missing directory
      no-op, unreadable file skipped while others complete, single-thread mode
- [x] 3. Implement `ArchivePrefetcher` (cluster-node), tests green
- [x] 4. Wire `oms.replay.prefetch` / `oms.replay.prefetch.threads` into `ClusterNodeMain`
- [x] 5. Measure: clean-cold fat recovery, flag off vs on, eviction protocol from the
      fat-heartbeat attribution; record in `measurements.md`
- [x] 6. `bazel test //...` green; format + lint; PR
