#!/usr/bin/env bash
# Replay a golden tape through the real cluster recovery path: unpack the tape, start a
# node over it (no clean flag) with the replay report enabled, print the report, stop.
#
#   scripts/replay-cluster.sh <name>     e.g. scripts/replay-cluster.sh heartbeats-v1
set -euo pipefail

NAME=${1:?usage: replay-cluster.sh <name>}
PORT=${PORT:-22122}   # isolated: distinct from dev (9002), perf (22102), record (22112)
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TAPE="$ROOT/journal/$NAME.tar.gz"
[ -f "$TAPE" ] || { echo "no such tape: journal/$NAME.tar.gz" >&2; exit 1; }

WORK=$(mktemp -d)
mkdir -p "$WORK/data/node-0"
tar -xzf "$TAPE" -C "$WORK/data/node-0"

cd "$ROOT"
bazel build //cluster-node:cluster-node > "$WORK/build.log" 2>&1

bazel-bin/cluster-node/cluster-node \
  "--jvm_flag=-Doms.data.dir=$WORK/data" \
  "--jvm_flag=-Doms.cluster.port=$PORT" \
  --jvm_flag=-Doms.replay.report=true > "$WORK/node.log" 2>&1 &
NODE_PID=$!
stop_node() {
  kill "$NODE_PID" 2>/dev/null || true
  wait "$NODE_PID" 2>/dev/null || true
}
trap stop_node EXIT

for _ in $(seq 1 300); do
  grep -q "cluster-replay:" "$WORK/node.log" && break
  sleep 0.1
done
grep "cluster-replay:" "$WORK/node.log" \
  || { echo "FAIL: no replay report"; tail -5 "$WORK/node.log"; exit 1; }
