# Tasks — golden-tape-replay

## 1. Spec (PR a)

- [ ] 1.1 Proposal, design, specs, tasks written; `openspec validate golden-tape-replay`
      passes; spec PR opened.

## 2. Record the tape (PR b)

- [x] 2.1 `scripts/record-tape.sh <name> [count]`: node + gateway on an isolated port,
      clean journal, stop node, sparse-tar `archive/` + `consensus/` (exclude
      `*-mark.dat`) to `journal/<name>.tar.gz`; write manifest and golden-outputs;
      refuse an existing name.
- [x] 2.2 Record `journal/heartbeats-v1` (3,000 heartbeats) and commit tape, manifest,
      golden outputs, and a short `journal/README.md` (immutability rule).

## 3. App-mode replay (PR c, TDD)

- [x] 3.1 Failing journal tests first: replay the committed tape → count equals
      manifest; outputs equal golden file; twice → identical; truncated copy → loud
      failure.
- [x] 3.2 Implement the frame-walking reader and capturing session; `TapeReplayMain` as
      a second `java_binary` in `//cluster-node`; tests green.
- [x] 3.3 `scripts/replay-app.sh <tape>` unpacks to a temp dir and runs the binary.

## 4. Cluster-mode replay (PR d)

- [x] 4.1 Failing test first: node over the unpacked tape with
      `-Doms.replay.report=true` reaches leader and reports the manifest count.
- [x] 4.2 Implement the counting wrapper in `cluster-node`; wire the property in
      `ClusterNodeMain`; off-by-default proven by existing tests staying green.
- [x] 4.3 `scripts/replay-cluster.sh <tape>` unpacks, starts the node with the report,
      waits for the summary line, stops the node.

## 5. Benchmark and close

- [x] 5.1 `scripts/replay-bench.sh <tape>`: run both modes, print per-mode
      messages / wall time / msgs/sec plus machine metadata; paste results into PR d.
- [ ] 5.2 All PRs merged bottom-up, `bazel test //...` green on `main`,
      `openspec archive golden-tape-replay`.
