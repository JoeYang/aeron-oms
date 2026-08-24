#!/usr/bin/env bash
# Record a golden tape: run node + gateway, freeze the journal as journal/<name>.tar.gz
# with a manifest and the golden expected outputs, then verify the tape recovers.
#
#   scripts/record-tape.sh <name> [count]
#
# A tape is immutable — an existing name is refused. Record-time timestamps are wall
# clock, so a tape can never be regenerated identically; a new scenario is a new name.
#
# Env: NODE_FLAGS / GW_FLAGS — extra "--jvm_flag=-D..." strings for scale recordings
# (e.g. the tuned profile; a 100M-message recording at the default profile takes ~12
# hours, tuned ~15-20 minutes). SKIP_GOLDENS=1 omits the golden-outputs file for
# local scale tapes — at 100M messages it is a 2 GB file nothing reads; the count is
# still verified and the omission is recorded in the manifest.
set -euo pipefail

NAME=${1:?usage: record-tape.sh <name> [count]}
COUNT=${2:-3000}
PORT=${PORT:-22112}   # isolated: distinct from dev (9002) and perf (22102)
NODE_FLAGS=${NODE_FLAGS:-}
GW_FLAGS=${GW_FLAGS:-}
SKIP_GOLDENS=${SKIP_GOLDENS:-}

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TAPE="$ROOT/journal/$NAME.tar.gz"
if [ -e "$TAPE" ]; then
  echo "refusing: journal/$NAME.tar.gz exists — tapes are immutable, pick a new name" >&2
  exit 1
fi
mkdir -p "$ROOT/journal"

WORK=$(mktemp -d)
DATA="$WORK/data"

cd "$ROOT"
bazel build //cluster-node:cluster-node //gateway:gateway > "$WORK/build.log" 2>&1

# shellcheck disable=SC2086
bazel-bin/cluster-node/cluster-node \
  "--jvm_flag=-Doms.data.dir=$DATA" --jvm_flag=-Doms.cluster.clean=true \
  "--jvm_flag=-Doms.cluster.port=$PORT" \
  $NODE_FLAGS > "$WORK/node.log" 2>&1 &
NODE_PID=$!
stop_node() {
  # Kill children first: the bazel launcher may run java as a child rather
  # than exec it, and a surviving JVM keeps the ports and the journal open.
  pkill -TERM -P "$NODE_PID" 2>/dev/null || true
  kill "$NODE_PID" 2>/dev/null || true
  for _ in 1 2 3 4 5; do kill -0 "$NODE_PID" 2>/dev/null || break; sleep 1; done
  pkill -KILL -P "$NODE_PID" 2>/dev/null || true
  kill -9 "$NODE_PID" 2>/dev/null || true
  wait "$NODE_PID" 2>/dev/null || true
}
trap stop_node EXIT

for _ in $(seq 1 150); do
  grep -q "cluster-node up" "$WORK/node.log" && break
  sleep 0.2
done
grep -q "cluster-node up" "$WORK/node.log" \
  || { echo "FAIL: node did not start"; tail -5 "$WORK/node.log"; exit 1; }

# The data dir lets IPC-mode gateways find the node's media driver; UDP mode ignores it.
# shellcheck disable=SC2086
bazel-bin/gateway/gateway \
  "--jvm_flag=-Doms.gateway.count=$COUNT" --jvm_flag=-Doms.gateway.interval.ms=0 \
  "--jvm_flag=-Doms.cluster.port=$PORT" "--jvm_flag=-Doms.data.dir=$DATA" \
  $GW_FLAGS > "$WORK/gateway.log" 2>&1

stop_node
trap - EXIT

# Golden outputs: the ordered sequenced timestamps the service echoed at record time.
# Replay is deterministic from the log, so these are the expected outputs forever.
# (Derivable later from the tape itself via tape-cat if skipped here.)
if [ -n "$SKIP_GOLDENS" ]; then
  GOT=$(grep -c 'sequenced=' "$WORK/gateway.log")
else
  grep -oP 'sequenced=\K\d+' "$WORK/gateway.log" > "$ROOT/journal/$NAME.golden-outputs.txt"
  GOT=$(wc -l < "$ROOT/journal/$NAME.golden-outputs.txt")
fi
[ "$GOT" -eq "$COUNT" ] || { echo "FAIL: captured $GOT/$COUNT outputs"; exit 1; }

{
  echo "name: $NAME"
  echo "messages: $COUNT"
  echo "golden-outputs: $([ -n "$SKIP_GOLDENS" ] && echo 'skipped (SKIP_GOLDENS)' || echo "$NAME.golden-outputs.txt")"
  echo "schema-version: $(grep -oP 'version="\K[0-9]+' "$ROOT/sbe/message-schema.xml" | head -1)"
  echo "commit: $(git -C "$ROOT" rev-parse HEAD)"
  echo "recorded: $(date -Is)"
  echo "machine: $(uname -sr) / $(lscpu | grep 'Model name' | sed 's/.*: *//')"
} > "$ROOT/journal/$NAME.manifest.txt"

# The tape: archive (segments + catalog) and consensus (recording.log, node-state).
# Mark files are runtime liveness state and the driver dir is transient — excluded.
# --sparse keeps the 128 MiB segment at its ~few-hundred-KiB real size.
tar --sparse --exclude='*-mark*.dat' -czf "$TAPE" -C "$DATA/node-0" archive consensus

# Verify: a node must recover from the tape before we call it golden.
CHECK=$(mktemp -d)
mkdir -p "$CHECK/data/node-0"
tar -xzf "$TAPE" -C "$CHECK/data/node-0"
bazel-bin/cluster-node/cluster-node \
  "--jvm_flag=-Doms.data.dir=$CHECK/data" \
  "--jvm_flag=-Doms.cluster.port=$PORT" > "$CHECK/node.log" 2>&1 &
NODE_PID=$!
trap stop_node EXIT
# Recovery replays the whole journal before "up"; scale tapes need the longer wait.
for _ in $(seq 1 600); do
  grep -q "cluster-node up" "$CHECK/node.log" && break
  sleep 0.2
done
grep -q "cluster-node up" "$CHECK/node.log" \
  || { echo "FAIL: recorded tape does not recover"; tail -5 "$CHECK/node.log"; exit 1; }
stop_node
trap - EXIT

ls -la "$ROOT/journal/$NAME"*
echo "OK: journal/$NAME.tar.gz recorded ($COUNT messages) and recovery-verified"
