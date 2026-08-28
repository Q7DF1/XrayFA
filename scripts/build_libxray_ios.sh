#!/usr/bin/env bash
# Build LibXrayLite.xcframework for iOS device + simulator (gomobile).
# Prerequisites: Go, Xcode, gomobile/gobind on PATH (go install golang.org/x/mobile/cmd/gomobile@latest).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIB_DIR="${ROOT}/AndroidLibXrayLite"
OUTPUT="${LIB_DIR}/LibXrayLite.xcframework"

export PATH="${PATH}:$(go env GOPATH)/bin"

if ! command -v gomobile >/dev/null 2>&1; then
  echo "gomobile not found; install with: go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init"
  exit 1
fi

cd "${LIB_DIR}"
go mod tidy -v
gomobile bind -v \
  -target ios,iossimulator \
  -trimpath \
  -ldflags='-s -w -buildid=' \
  -o "${OUTPUT}" \
  ./

echo "Built ${OUTPUT}"
