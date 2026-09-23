#!/usr/bin/env bash
# Build the agentbuddy daemon (services/kittylitter) and place it where the
# Tauri bundler expects sidecars: src-tauri/binaries/agentbuddy-<triple>.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DESKTOP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_DIR="$(cd "$DESKTOP_DIR/../.." && pwd)"
export PATH="$HOME/.cargo/bin:$PATH"

TRIPLE="${1:-$(rustc --print host-tuple)}"
MANIFEST="$REPO_DIR/services/kittylitter/Cargo.toml"
OUT_DIR="$DESKTOP_DIR/src-tauri/binaries"
SRC="$REPO_DIR/services/kittylitter/target/$TRIPLE/release/agentbuddy"

echo "==> Building agentbuddy sidecar for $TRIPLE"
rustup target add "$TRIPLE" >/dev/null
cargo build --release --manifest-path "$MANIFEST" --target "$TRIPLE"

mkdir -p "$OUT_DIR"
cp "$SRC" "$OUT_DIR/agentbuddy-$TRIPLE"
chmod +x "$OUT_DIR/agentbuddy-$TRIPLE"
echo "==> $OUT_DIR/agentbuddy-$TRIPLE"
"$OUT_DIR/agentbuddy-$TRIPLE" --version
