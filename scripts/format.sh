#!/usr/bin/env bash
#
# Format all Java sources in place with google-java-format.
#
#   scripts/format.sh
#
# This rewrites files. Use scripts/lint.sh for a read-only check.

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
readonly ROOT="${PWD}"

if ! command -v bazel >/dev/null 2>&1; then
  echo "error: bazel not found on PATH" >&2
  exit 127
fi

# Absolute paths: `bazel run` executes the tool from the output base, not here.
mapfile -t sources < <(find . -name '*.java' -not -path './bazel-*' -printf "${ROOT}/%P\n")

if [ "${#sources[@]}" -eq 0 ]; then
  echo "no Java sources found"
  exit 0
fi

echo "formatting ${#sources[@]} file(s)..."
bazel run --ui_event_filters=-info,-debug --noshow_progress //tools:google-java-format -- \
  --replace "${sources[@]}"
echo "done"
