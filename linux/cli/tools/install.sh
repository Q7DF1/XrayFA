#!/bin/sh
set -eu

usage() {
    cat <<'EOF'
Usage: install.sh --cli PATH [--core PATH] [--prefix DIR] [--bin-dir DIR]
       install.sh --rollback [--prefix DIR] [--bin-dir DIR]
       install.sh --dry-run --cli PATH [--core PATH] [--prefix DIR] [--bin-dir DIR]

Installs a verified CLI/core pair without touching network state. Existing
artifacts are retained as timestamped .previous backups before replacement.
The operation refuses to run while /run/xrayfa/session.json exists.
EOF
}

die() { echo "install: $*" >&2; exit 10; }
refuse() { echo "install: $*" >&2; exit 8; }

CLI_SRC=
CORE_SRC=
PREFIX=${XRAYFA_PREFIX:-/usr/local/lib/xrayfa}
BIN_DIR=${XRAYFA_BIN_DIR:-/usr/local/bin}
ROLLBACK=0
DRY_RUN=0

while [ "$#" -gt 0 ]; do
    case "$1" in
        --cli) [ "$#" -ge 2 ] || { usage >&2; exit 2; }; CLI_SRC=$2; shift 2 ;;
        --core) [ "$#" -ge 2 ] || { usage >&2; exit 2; }; CORE_SRC=$2; shift 2 ;;
        --prefix) [ "$#" -ge 2 ] || { usage >&2; exit 2; }; PREFIX=$2; shift 2 ;;
        --bin-dir) [ "$#" -ge 2 ] || { usage >&2; exit 2; }; BIN_DIR=$2; shift 2 ;;
        --rollback) ROLLBACK=1; shift ;;
        --dry-run) DRY_RUN=1; shift ;;
        --help|-h) usage; exit 0 ;;
        *) usage >&2; exit 2 ;;
    esac
done

[ "${PREFIX#/}" != "$PREFIX" ] || die "--prefix must be an absolute path"
[ "${BIN_DIR#/}" != "$BIN_DIR" ] || die "--bin-dir must be an absolute path"
[ "$ROLLBACK" -eq 0 ] || [ -z "$CLI_SRC" ] || die "--rollback cannot be combined with --cli"
[ "$ROLLBACK" -eq 1 ] || [ -n "$CLI_SRC" ] || { usage >&2; exit 2; }
[ -x "$(command -v sha256sum 2>/dev/null || true)" ] || die "sha256sum is required"

if [ "$(id -u)" -ne 0 ] && { [ "$PREFIX" = /usr/local/lib/xrayfa ] || [ "$BIN_DIR" = /usr/local/bin ]; }; then
    die "root is required for the default system destination"
fi

if [ "$(id -u)" -ne 0 ] && [ -d /run/xrayfa ] && { [ ! -r /run/xrayfa ] || [ ! -x /run/xrayfa ]; }; then
    die "root is required to inspect the runtime journal"
fi

if [ -e /run/xrayfa/session.json ]; then
    refuse "an active or retained runtime journal exists; stop/recover it first"
fi

check_executable() {
    path=$1
    [ -f "$path" ] || die "not a regular file: $path"
    [ ! -L "$path" ] || die "symbolic links are not accepted as input: $path"
    [ -x "$path" ] || die "not executable: $path"
}

validate_directory_targets() {
    [ ! -L "$PREFIX" ] || refuse "--prefix must not be a symbolic link: $PREFIX"
    [ ! -L "$BIN_DIR" ] || refuse "--bin-dir must not be a symbolic link: $BIN_DIR"
}

hash_of() { sha256sum "$1" | awk '{print $1}'; }

install_one() {
    source=$1
    target=$2
    mode=$3
    temp=$(mktemp "$target.new.XXXXXX") || die "cannot create temporary file beside $target"
    trap 'rm -f "$temp"' EXIT HUP INT TERM
    install -m "$mode" "$source" "$temp" || die "cannot stage $target"
    mv -f "$temp" "$target" || die "cannot atomically replace $target"
    trap - EXIT HUP INT TERM
}

prepare_dirs() {
    if [ "$DRY_RUN" -eq 0 ]; then
        install -d -m 0755 "$PREFIX" "$BIN_DIR"
    fi
}

validate_link_target() {
    link=$BIN_DIR/xrayfa
    if [ -e "$link" ] || [ -L "$link" ]; then
        [ -L "$link" ] || refuse "$link exists and is not a managed symlink"
        current=$(readlink "$link")
        [ "$current" = "$PREFIX/xrayfa" ] || refuse "$link points outside the managed prefix"
    fi
}

link_target() {
    validate_link_target
    if [ "$DRY_RUN" -eq 0 ]; then
        temp=$(mktemp "$BIN_DIR/.xrayfa-link.XXXXXX") || die "cannot create temporary symlink"
        rm -f "$temp"
        ln -s "$PREFIX/xrayfa" "$temp" || die "cannot stage CLI symlink"
        mv -f "$temp" "$link" || die "cannot atomically replace CLI symlink"
    fi
}

validate_existing_targets() {
    validate_directory_targets
    if [ -e "$PREFIX/xrayfa" ] || [ -L "$PREFIX/xrayfa" ]; then
        [ ! -L "$PREFIX/xrayfa" ] || refuse "$PREFIX/xrayfa is a symbolic link"
        [ -f "$PREFIX/xrayfa" ] || die "$PREFIX/xrayfa is not a regular file"
    fi
    if [ -n "$CORE_SRC" ] && { [ -e "$PREFIX/xray" ] || [ -L "$PREFIX/xray" ]; }; then
        [ ! -L "$PREFIX/xray" ] || refuse "$PREFIX/xray is a symbolic link"
        [ -f "$PREFIX/xray" ] || die "$PREFIX/xray is not a regular file"
    fi
    if [ -e "$PREFIX/manifest.txt" ] || [ -L "$PREFIX/manifest.txt" ]; then
        [ ! -L "$PREFIX/manifest.txt" ] || refuse "$PREFIX/manifest.txt is a symbolic link"
        [ -f "$PREFIX/manifest.txt" ] || die "$PREFIX/manifest.txt is not a regular file"
    fi
    # This is read-only in --dry-run and also validates the managed symlink
    # before any destination directory or artifact is changed.
    validate_link_target
}

manifest() {
    cli_hash=$1
    core_hash=${2:-}
    temp=$(mktemp "$PREFIX/.manifest.XXXXXX") || die "cannot create manifest"
    {
        echo "schemaVersion=1"
        echo "cliPath=$PREFIX/xrayfa"
        echo "cliSha256=$cli_hash"
        if [ -n "$core_hash" ]; then
            echo "corePath=$PREFIX/xray"
            echo "coreSha256=$core_hash"
        fi
        echo "installedUtc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    } > "$temp" || die "cannot write manifest"
    chmod 0644 "$temp"
    mv -f "$temp" "$PREFIX/manifest.txt" || die "cannot publish manifest"
}

TXN_ACTIVE=0
TXN_STAGE_CLI=
TXN_STAGE_CORE=
TXN_OLD_CLI=0
TXN_OLD_CORE=0
TXN_OLD_MANIFEST=0
TXN_BACKUP_CLI=
TXN_BACKUP_CORE=
TXN_BACKUP_MANIFEST=

transaction_cleanup() {
    [ -z "$TXN_STAGE_CLI" ] || rm -f "$TXN_STAGE_CLI"
    [ -z "$TXN_STAGE_CORE" ] || rm -f "$TXN_STAGE_CORE"
}

transaction_restore() {
    target=$1
    backup=$2
    existed=$3
    mode=$4
    if [ "$existed" -eq 1 ]; then
        install -m "$mode" "$backup" "$target" 2>/dev/null || true
    else
        rm -f "$target" 2>/dev/null || true
    fi
}

transaction_abort() {
    status=$?
    [ "$TXN_ACTIVE" -eq 1 ] || exit "$status"
    transaction_restore "$PREFIX/xrayfa" "$TXN_BACKUP_CLI" "$TXN_OLD_CLI" 0755
    if [ -n "$CORE_SRC" ]; then
        transaction_restore "$PREFIX/xray" "$TXN_BACKUP_CORE" "$TXN_OLD_CORE" 0755
    fi
    transaction_restore "$PREFIX/manifest.txt" "$TXN_BACKUP_MANIFEST" "$TXN_OLD_MANIFEST" 0644
    transaction_cleanup
    TXN_ACTIVE=0
    trap - EXIT HUP INT TERM
    exit "$status"
}

rollback() {
    latest=
    for candidate in "$PREFIX"/xrayfa.previous.*; do
        [ -f "$candidate" ] || continue
        latest=$candidate
    done
    [ -n "$latest" ] || refuse "no CLI rollback artifact exists in $PREFIX"
    validate_directory_targets
    [ ! -L "$PREFIX/xrayfa" ] || refuse "$PREFIX/xrayfa is a symbolic link"
    [ ! -L "$PREFIX/xray" ] || refuse "$PREFIX/xray is a symbolic link"
    if [ -e "$PREFIX/manifest.txt" ] || [ -L "$PREFIX/manifest.txt" ]; then
        [ ! -L "$PREFIX/manifest.txt" ] || refuse "$PREFIX/manifest.txt is a symbolic link"
        [ -f "$PREFIX/manifest.txt" ] || die "$PREFIX/manifest.txt is not a regular file"
    fi
    validate_link_target
    suffix=${latest##*.}
    core_backup=$PREFIX/xray.previous.$suffix
    [ "$DRY_RUN" -eq 1 ] || prepare_dirs
    if [ "$DRY_RUN" -eq 1 ]; then
        echo "would rollback CLI=$latest"
        [ -f "$core_backup" ] && echo "would rollback core=$core_backup"
        return 0
    fi
    stamp=$(date -u +%Y%m%dT%H%M%SZ)
    [ -f "$PREFIX/xrayfa" ] && cp -p "$PREFIX/xrayfa" "$PREFIX/xrayfa.before-rollback.$stamp"
    install_one "$latest" "$PREFIX/xrayfa" 0755
    if [ -f "$core_backup" ]; then
        [ -f "$PREFIX/xray" ] && cp -p "$PREFIX/xray" "$PREFIX/xray.before-rollback.$stamp"
        install_one "$core_backup" "$PREFIX/xray" 0755
    fi
    link_target
    manifest "$(hash_of "$PREFIX/xrayfa")" "$( [ -f "$PREFIX/xray" ] && hash_of "$PREFIX/xray" || true )"
    echo "rolled back CLI=$PREFIX/xrayfa"
}

if [ "$ROLLBACK" -eq 1 ]; then
    [ -d "$PREFIX" ] || refuse "no installation prefix exists: $PREFIX"
    rollback
    exit 0
fi

check_executable "$CLI_SRC"
[ -z "$CORE_SRC" ] || check_executable "$CORE_SRC"
"$CLI_SRC" --version >/dev/null 2>&1 || die "CLI version probe failed: $CLI_SRC"
[ -z "$CORE_SRC" ] || "$CORE_SRC" version >/dev/null 2>&1 || die "core version probe failed: $CORE_SRC"
cli_hash=$(hash_of "$CLI_SRC")
core_hash=
[ -z "$CORE_SRC" ] || core_hash=$(hash_of "$CORE_SRC")

validate_existing_targets

if [ "$DRY_RUN" -eq 1 ]; then
    echo "would install CLI sha256=$cli_hash prefix=$PREFIX"
    [ -z "$core_hash" ] || echo "would install core sha256=$core_hash"
    exit 0
fi

prepare_dirs
stamp=$(date -u +%Y%m%dT%H%M%SZ)
TXN_ACTIVE=1
trap transaction_abort EXIT HUP INT TERM
if [ -e "$PREFIX/xrayfa" ] || [ -L "$PREFIX/xrayfa" ]; then
    TXN_OLD_CLI=1
    TXN_BACKUP_CLI="$PREFIX/xrayfa.previous.$stamp"
    cp -p "$PREFIX/xrayfa" "$TXN_BACKUP_CLI"
fi
if [ -n "$CORE_SRC" ] && { [ -e "$PREFIX/xray" ] || [ -L "$PREFIX/xray" ]; }; then
    TXN_OLD_CORE=1
    TXN_BACKUP_CORE="$PREFIX/xray.previous.$stamp"
    cp -p "$PREFIX/xray" "$TXN_BACKUP_CORE"
fi
if [ -e "$PREFIX/manifest.txt" ] || [ -L "$PREFIX/manifest.txt" ]; then
    TXN_OLD_MANIFEST=1
    TXN_BACKUP_MANIFEST="$PREFIX/manifest.previous.$stamp"
    cp -p "$PREFIX/manifest.txt" "$TXN_BACKUP_MANIFEST"
fi
TXN_STAGE_CLI=$(mktemp "$PREFIX/.xrayfa-stage.XXXXXX") || die "cannot create CLI staging file"
install -m 0755 "$CLI_SRC" "$TXN_STAGE_CLI" || die "cannot stage CLI"
if [ -n "$CORE_SRC" ]; then
    TXN_STAGE_CORE=$(mktemp "$PREFIX/.xray-core-stage.XXXXXX") || die "cannot create core staging file"
    install -m 0755 "$CORE_SRC" "$TXN_STAGE_CORE" || die "cannot stage core"
fi
mv -f "$TXN_STAGE_CLI" "$PREFIX/xrayfa" || die "cannot publish CLI"
TXN_STAGE_CLI=
if [ -n "$CORE_SRC" ]; then
    mv -f "$TXN_STAGE_CORE" "$PREFIX/xray" || die "cannot publish core"
    TXN_STAGE_CORE=
fi
link_target
manifest "$cli_hash" "$core_hash"
TXN_ACTIVE=0
trap - EXIT HUP INT TERM
echo "installed CLI sha256=$cli_hash path=$PREFIX/xrayfa"
[ -z "$core_hash" ] || echo "installed core sha256=$core_hash path=$PREFIX/xray"
