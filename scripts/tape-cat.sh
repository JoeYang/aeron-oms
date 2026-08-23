#!/usr/bin/env bash
# View a golden tape: one line per decoded log entry. Read-only.
#
#   scripts/tape-cat.sh <name> [--json]     e.g. scripts/tape-cat.sh heartbeats-v1
set -euo pipefail

NAME=${1:?usage: tape-cat.sh <name> [--json]}
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TAPE="$ROOT/journal/$NAME.tar.gz"
[ -f "$TAPE" ] || { echo "no such tape: journal/$NAME.tar.gz" >&2; exit 1; }

WORK=$(mktemp -d)
tar -xzf "$TAPE" -C "$WORK"

cd "$ROOT"
bazel build //cluster-node:tape-cat > "$WORK/build.log" 2>&1
# ${2:+"$2"} expands to nothing (not an empty argument) when no flag is given.
exec bazel-bin/cluster-node/tape-cat "$WORK/archive" ${2:+"$2"}
