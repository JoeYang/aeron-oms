#!/usr/bin/env bash
# Replay a golden tape against the bare state machine (no cluster, no media driver)
# and verify count and outputs against the tape's manifest and golden outputs.
#
#   scripts/replay-app.sh <name>     e.g. scripts/replay-app.sh heartbeats-v1
set -euo pipefail

NAME=${1:?usage: replay-app.sh <name>}
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TAPE="$ROOT/journal/$NAME.tar.gz"
[ -f "$TAPE" ] || { echo "no such tape: journal/$NAME.tar.gz" >&2; exit 1; }

WORK=$(mktemp -d)
tar -xzf "$TAPE" -C "$WORK"

cd "$ROOT"
bazel build //cluster-node:tape-replay > "$WORK/build.log" 2>&1
exec bazel-bin/cluster-node/tape-replay \
  "$WORK/archive" "$ROOT/journal/$NAME.manifest.txt" "$ROOT/journal/$NAME.golden-outputs.txt"
