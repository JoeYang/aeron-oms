#!/usr/bin/env bash
# Benchmark golden-tape replay in both modes. The two numbers answer different
# questions and are not directly comparable: app mode is pure decode-and-apply (how
# fast can state be rebuilt); cluster mode is the production recovery path (driver,
# archive, consensus module included).
#
#   scripts/replay-bench.sh <name>     e.g. scripts/replay-bench.sh heartbeats-v1
set -euo pipefail

NAME=${1:?usage: replay-bench.sh <name>}
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "tape     : journal/$NAME ($(grep 'messages:' "$ROOT/journal/$NAME.manifest.txt"))"
echo "commit   : $(git -C "$ROOT" rev-parse --short HEAD)"
echo "machine  : $(lscpu | grep 'Model name' | sed 's/.*: *//')"
echo "governor : $(sort -u /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor 2>/dev/null | tr '\n' ' ')"
echo

"$ROOT/scripts/replay-app.sh" "$NAME"
"$ROOT/scripts/replay-cluster.sh" "$NAME"

echo
echo "note: app mode = bare state machine; cluster mode = full recovery path."
