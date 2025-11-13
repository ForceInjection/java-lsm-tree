#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

rm -rf "$ROOT_DIR/test-suite/results/sessions" || true

echo "Workspace cleaned."

