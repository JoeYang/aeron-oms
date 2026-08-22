#!/usr/bin/env bash
#
# Run one of the process binaries.
#
#   scripts/run.sh cluster-node
#   scripts/run.sh gateway --some-flag
#
# Arguments after the process name are passed to the process, not to Bazel.

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

readonly PROCESSES="cluster-node gateway driver"

usage() {
  echo "usage: scripts/run.sh <process> [args...]" >&2
  echo "processes: ${PROCESSES}" >&2
}

if ! command -v bazel >/dev/null 2>&1; then
  echo "error: bazel not found on PATH" >&2
  exit 127
fi

if [ "$#" -lt 1 ]; then
  usage
  exit 2
fi

process="$1"
shift

case " ${PROCESSES} " in
  *" ${process} "*) ;;
  *)
    echo "error: unknown process '${process}'" >&2
    usage
    exit 2
    ;;
esac

# Build under the Bazel lock, then exec the wrapper directly so the lock is released.
# A plain `bazel run` holds the workspace lock for the process's whole lifetime, which
# makes the two-terminal demo (node + gateway) deadlock on "Another command is running".
bazel build "//${process}:${process}"

# Optional launch-layer CPU pinning: OMS_TASKSET="0-7" confines every thread of the
# process — including JVM-internal ones, by mask inheritance — to the given cores.
# This is one of the four pinning layers (trading-latency.md); the per-thread runtime
# layer arrives with the FFM sched_setaffinity work, and the kernel isolation layer
# needs boot parameters this script cannot supply.
if [ -n "${OMS_TASKSET:-}" ]; then
  exec taskset -c "${OMS_TASKSET}" "bazel-bin/${process}/${process}" "$@"
fi
exec "bazel-bin/${process}/${process}" "$@"
