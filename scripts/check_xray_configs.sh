#!/usr/bin/env bash
set -euo pipefail

# Run after :domain:testDebugUnitTest. Uses the source dependency, not an existing AAR.
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixtures="${1:-$project_root/domain/build/generated/xray-config-fixtures}"
if [[ ! -d "$fixtures" ]]; then
    echo "Generate fixtures first: ./gradlew :domain:testDebugUnitTest -PenableIosTargets=false" >&2
    exit 1
fi
fixtures="$(cd "$fixtures" && pwd)"
core_version="$(awk '$1 == "github.com/xtls/xray-core" { print $2; exit }' "$project_root/AndroidLibXrayLite/go.mod")"
[[ -n "$core_version" ]]
validator_dir="$(mktemp -d "${TMPDIR:-/tmp}/xrayfa-config-validator.XXXXXX")"
trap 'rm -rf "$validator_dir"' EXIT
# Keep the wrapper's transitive version pins as well as its core commit/toolchain.
sed '1s/.*/module xrayfa-config-validator/' "$project_root/AndroidLibXrayLite/go.mod" > "$validator_dir/go.mod"
cp "$project_root/AndroidLibXrayLite/go.sum" "$validator_dir/go.sum"
cat > "$validator_dir/main.go" <<'EOF'
package main

import (
    "fmt"
    "os"
    "path/filepath"
    "strings"
    "github.com/xtls/xray-core/infra/conf/serial"
    _ "github.com/xtls/xray-core/main/distro/all"
)

func main() {
    if err := os.Setenv("xray.location.asset", os.Args[2]); err != nil { panic(err) }
    files, err := filepath.Glob(filepath.Join(os.Args[1], "*.json"))
    if err != nil || len(files) == 0 {
        panic("no generated configuration fixtures")
    }
    for _, file := range files {
        data, err := os.ReadFile(file)
        if err != nil { panic(err) }
        if _, err = serial.LoadJSONConfig(strings.NewReader(string(data))); err != nil {
            panic(fmt.Sprintf("%s: %v", filepath.Base(file), err))
        }
        fmt.Println("PASS", filepath.Base(file))
    }
    fmt.Printf("Loaded %d generated configurations\n", len(files))
}
EOF
echo "Checking configurations against source dependency: $core_version"
cd "$validator_dir"
go run -mod=mod . "$fixtures" "$project_root/AndroidLibXrayLite/assets"
