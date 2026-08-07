#!/usr/bin/env bash
# Generate iosApp/iosApp.xcodeproj from project.yml (requires xcodegen).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/iosApp"

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "xcodegen not found. Install with: brew install xcodegen" >&2
  exit 1
fi

xcodegen generate
echo "Generated iosApp/iosApp.xcodeproj"
