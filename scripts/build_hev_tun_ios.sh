#!/usr/bin/env bash
# Build HevSocks5Tunnel.xcframework for iOS (hev-socks5-tunnel submodule).
# Prerequisites: Xcode 16+, make, libtool, lipo.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HEV_DIR="${ROOT}/tun2socks/src/main/jni/hev-socks5-tunnel"
OUTPUT="${HEV_DIR}/HevSocks5Tunnel.xcframework"

if [[ ! -d "${HEV_DIR}" ]]; then
  echo "hev-socks5-tunnel not found at ${HEV_DIR}"
  exit 1
fi

cd "${HEV_DIR}"
git submodule update --init --recursive 2>/dev/null || true
./build-apple.sh

if [[ ! -d "${OUTPUT}" ]]; then
  echo "Expected ${OUTPUT} after build-apple.sh"
  exit 1
fi

echo "Built ${OUTPUT}"
