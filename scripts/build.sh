#!/usr/bin/env bash
#
# Build the project.
#
#   scripts/build.sh              # everything
#   scripts/build.sh //core:core  # a specific target
#
# Extra arguments are passed straight through to Bazel.

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if ! command -v bazel >/dev/null 2>&1; then
  echo "error: bazel not found on PATH" >&2
  exit 127
fi

if [ "$#" -gt 0 ]; then
  exec bazel build "$@"
fi

exec bazel build //...
