#!/usr/bin/env bash
set -euo pipefail

# Keeps the Alleycat git dependencies pinned, or (opt-in) moves them to the
# latest `main` of the AkaShark/alleycat fork.
#
# WHY THIS IS OFF BY DEFAULT
# --------------------------
# This script is a prerequisite of `make alleycat-main`, which every Rust
# target depends on, and it is also invoked directly by
# apps/ios/scripts/build-rust.sh, tools/scripts/build-android-rust.sh,
# shared/rust-bridge/generate-bindings.sh. Upstream litter used it to float
# the deps to the newest alleycat `main` on every build. AgentBuddy pins alleycat to a
# fixed commit (see rev = "..." in shared/rust-bridge/Cargo.toml and
# services/kittylitter/Cargo.toml) for reproducible builds and GPLv3
# source-correspondence, so a build must never silently rewrite that rev.
#
# Set AGENTBUDDY_REFRESH_ALLEYCAT=1 to run the original refresh logic
# (ls-remote the fork's main, then `cargo update --precise`). The legacy
# LITTER_SKIP_ALLEYCAT_UPDATE=1 is still honoured and always wins.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

ALLEYCAT_REPO_URL="${ALLEYCAT_REPO_URL:-https://github.com/AkaShark/alleycat.git}"
ALLEYCAT_REPO_LABEL="AkaShark/alleycat"

MODE="all"
case "${1:-}" in
  "")
    ;;
  --all|--shared|--kittylitter)
    MODE="${1#--}"
    ;;
  *)
    echo "usage: $(basename "$0") [--all|--shared|--kittylitter]" >&2
    exit 1
    ;;
esac

if [ "${LITTER_SKIP_ALLEYCAT_UPDATE:-0}" = "1" ]; then
  echo "==> Skipping Alleycat main refresh (LITTER_SKIP_ALLEYCAT_UPDATE=1)"
  exit 0
fi

if [ "${AGENTBUDDY_REFRESH_ALLEYCAT:-0}" != "1" ]; then
  echo "==> Keeping Alleycat deps pinned (set AGENTBUDDY_REFRESH_ALLEYCAT=1 to move them to $ALLEYCAT_REPO_LABEL main)"
  exit 0
fi

if ! command -v cargo >/dev/null 2>&1; then
  echo "error: cargo is required" >&2
  exit 1
fi

ALLEYCAT_MAIN_SHA="$(
  git ls-remote "$ALLEYCAT_REPO_URL" refs/heads/main \
    | awk '{ print $1; exit }'
)"
if [ -z "$ALLEYCAT_MAIN_SHA" ]; then
  echo "error: could not resolve $ALLEYCAT_REPO_LABEL main" >&2
  exit 1
fi

update_shared() {
  echo "==> Resolving shared Rust Alleycat deps to $ALLEYCAT_REPO_LABEL main ($ALLEYCAT_MAIN_SHA)..."
  for package in \
    alleycat-bridge-core \
    alleycat-pi-bridge \
    alleycat-claude-bridge \
    alleycat-opencode-bridge
  do
    cargo update \
      --quiet \
      --manifest-path "$REPO_DIR/shared/rust-bridge/Cargo.toml" \
      -p "$package" \
      --precise "$ALLEYCAT_MAIN_SHA"
  done
}

update_kittylitter() {
  echo "==> Resolving kittylitter Alleycat dep to $ALLEYCAT_REPO_LABEL main ($ALLEYCAT_MAIN_SHA)..."
  cargo update \
    --quiet \
    --manifest-path "$REPO_DIR/services/kittylitter/Cargo.toml" \
    -p alleycat \
    --precise "$ALLEYCAT_MAIN_SHA"
}

case "$MODE" in
  all)
    update_shared
    update_kittylitter
    ;;
  shared)
    update_shared
    ;;
  kittylitter)
    update_kittylitter
    ;;
esac
