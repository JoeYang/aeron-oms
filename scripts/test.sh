#!/usr/bin/env bash
#
# Run the test suite.
#
#   scripts/test.sh                    # everything
#   scripts/test.sh //core:core_test   # a specific target
#
# Extra arguments are passed straight through to Bazel.
#
# This is the completion gate: no task is done and no PR is opened until this
# exits zero. See .claude/rules/process.md.

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if ! command -v bazel >/dev/null 2>&1; then
  echo "error: bazel not found on PATH" >&2
  exit 127
fi

if [ "$#" -gt 0 ]; then
  exec bazel test "$@"
fi

exec bazel test //...
