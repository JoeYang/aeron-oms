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
  # The bazel launcher may run java as a child rather than exec it: kill the
  # children first (while the wrapper is still alive to be their parent), then
  # the wrapper — or the JVM survives the script and keeps the ports.
  pkill -TERM -P "$NODE_PID" 2>/dev/null || true
  kill "$NODE_PID" 2>/dev/null || true
  for _ in 1 2 3 4 5; do kill -0 "$NODE_PID" 2>/dev/null || break; sleep 1; done
  pkill -KILL -P "$NODE_PID" 2>/dev/null || true
  kill -9 "$NODE_PID" 2>/dev/null || true
  wait "$NODE_PID" 2>/dev/null || true
}
trap stop_node EXIT

# Wait for the complete line ("msg/s" is its tail) — matching the prefix races
# a partially flushed write and prints a truncated report.
for _ in $(seq 1 600); do
  grep -q "msg/s" "$WORK/node.log" && break
  sleep 0.1
done
grep "cluster-replay: .*msg/s" "$WORK/node.log" \
  || { echo "FAIL: no replay report"; tail -5 "$WORK/node.log"; exit 1; }
