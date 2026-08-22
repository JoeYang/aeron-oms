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
exec "bazel-bin/${process}/${process}" "$@"
