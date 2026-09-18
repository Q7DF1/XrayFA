#!/usr/bin/env bash
# Shared Android native + F-Droid prep. The fdroiddata recipe should only call this.
#
# Usage (repo root):
#   scripts/fdroid.sh prebuild [abi]
#   scripts/fdroid.sh bind [abi] [go_srclib]
#
# abi: armeabi-v7a | arm64-v8a | x86 | x86_64
#      Omit on GitHub CI to bind all ABIs and keep universal APK splits.
# go_srclib: F-Droid $$go$$ path. Omit to use the Go already on PATH.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

prop() {
  grep "^${1}=" "$ROOT/gradle.properties" | cut -d= -f2- | tr -d '\r'
}

download() {
  local url=$1 dest=$2
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL --retry 3 -o "$dest" "$url"
  else
    wget -qO "$dest" "$url"
  fi
}

set_gradle_prop() {
  local key=$1 val=$2 file=$ROOT/gradle.properties
  local tmp
  tmp="$(mktemp)"
  # Literal key match — awk regex would treat dots in org.gradle.* as wildcards.
  awk -v k="$key" -v v="$val" '
    BEGIN { found = 0 }
    index($0, k "=") == 1 { print k "=" v; found = 1; next }
    { print }
    END { if (!found) print k "=" v }
  ' "$file" > "$tmp"
  mv "$tmp" "$file"
}

gomobile_target() {
  case "$1" in
    armeabi-v7a) echo android/arm ;;
    arm64-v8a) echo android/arm64 ;;
    x86) echo android/386 ;;
    x86_64) echo android/amd64 ;;
    *)
      echo "unknown ABI: $1" >&2
      exit 1
      ;;
  esac
}

sync_geo() {
  local ip_ver site_ver assets
  ip_ver="$(prop GEO_IP_VERSION)"
  site_ver="$(prop GEO_SITE_VERSION)"
  assets="$ROOT/AndroidLibXrayLite/assets"
  mkdir -p "$assets"
  echo "Syncing geoip.dat ${ip_ver} and geosite.dat ${site_ver}"
  download \
    "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/download/${ip_ver}/geoip.dat" \
    "$assets/geoip.dat"
  download \
    "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/download/${site_ver}/geosite.dat" \
    "$assets/geosite.dat"
}

pin_abi() {
  local abi=$1
  gomobile_target "$abi" >/dev/null
  # F-Droid runs `gradle assembleRelease` with no extra -P flags.
  # Do not commit this line; it exists only in the buildserver checkout.
  set_gradle_prop fdroidAbi "$abi"
}

ensure_android_sdk() {
  if ! command -v sdkmanager >/dev/null 2>&1; then
    return 0
  fi
  local compile_sdk
  compile_sdk="$(
    grep -E '^[[:space:]]*compileSdk[[:space:]]*=' "$ROOT/androidApp/build.gradle.kts" \
      | head -1 \
      | grep -oE '[0-9]+'
  )"
  sdkmanager "platforms;android-${compile_sdk}" "build-tools;${compile_sdk}.0.0"
}

cmd_prebuild() {
  local abi=${1:-}
  # Validate ABI before any downloads so a typo does not fetch geo/SDK first.
  if [[ -n "$abi" ]]; then
    gomobile_target "$abi" >/dev/null
  fi
  sync_geo
  # sdkmanager is F-Droid-only. GitHub runners often have it on PATH and
  # would block on licenses if we called it from CI `prebuild` (no ABI).
  if [[ -n "$abi" ]]; then
    ensure_android_sdk
    pin_abi "$abi"
  fi
}

bootstrap_go() {
  local go_src=$1
  local go_ver
  go_ver="$(prop GO_VERSION)"
  git -C "$go_src" checkout -f "go${go_ver}"
  (
    cd "$go_src/src"
    ./make.bash
  )
  export GOROOT="$go_src"
  export GOPATH="${GOPATH:-$HOME/go}"
  export PATH="$GOROOT/bin:$GOPATH/bin:$PATH"
}

bind_aar() {
  local abi=${1:-}
  local lib=$ROOT/AndroidLibXrayLite

  export GOFLAGS="${GOFLAGS:--modcacherw -buildvcs=false -trimpath}"
  export CGO_LDFLAGS="${CGO_LDFLAGS:--Wl,--build-id=none}"
  export CGO_CFLAGS="-ffile-prefix-map=${lib}=."
  export CGO_CXXFLAGS="-ffile-prefix-map=${lib}=."

  mkdir -p "$HOME/go/bin"
  export PATH="${PATH}:$(go env GOPATH)/bin"

  go install golang.org/x/mobile/cmd/gomobile@latest
  go install golang.org/x/mobile/cmd/gobind@latest

  cd "$lib"
  gomobile init
  go mod tidy -v

  if [[ -n "$abi" ]]; then
    gomobile bind -v -trimpath -androidapi 21 \
      -target="$(gomobile_target "$abi")" \
      -ldflags="-s -w -buildid= -checklinkname=0" \
      ./
  else
    gomobile bind -v -trimpath -androidapi 21 \
      -ldflags="-s -w -buildid= -checklinkname=0" \
      ./
  fi

  mkdir -p "$ROOT/androidApp/libs"
  cp -f "$lib/libv2ray.aar" "$ROOT/androidApp/libs/"
}

cmd_bind() {
  local abi=${1:-}
  local go_src=${2:-}
  if [[ -n "$go_src" ]]; then
    bootstrap_go "$go_src"
  fi
  bind_aar "$abi"
}

usage() {
  echo "Usage: $0 prebuild [abi]" >&2
  echo "       $0 bind [abi] [go_srclib]" >&2
  exit 2
}

cmd=${1:-}
shift || true
case "$cmd" in
  prebuild) cmd_prebuild "${1:-}" ;;
  bind) cmd_bind "${1:-}" "${2:-}" ;;
  *) usage ;;
esac
