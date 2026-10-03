#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
cargo build --manifest-path "$REPO_ROOT/shared/rust-bridge/Cargo.toml" \
  -p codex-mobile-client --target aarch64-unknown-linux-ohos --profile harmony-dev --locked
CORE_LIBRARY="$CARGO_TARGET_DIR/aarch64-unknown-linux-ohos/harmony-dev/libcodex_mobile_client.so"
uniffi-bindgen-arkts generate "$CORE_LIBRARY" \
  --manifest-path "$REPO_ROOT/shared/rust-bridge/codex-mobile-client/Cargo.toml" \
  --out-dir "$HARMONY_WORK_ROOT/build/generated/core" --package-name @agentbuddy/core \
  --module-name agentbuddy_core --crate-name codex_mobile_client --manual-load --no-stage-library
python3 "$HARMONY_SOURCE/scripts/fix-native-bindings.py" "$HARMONY_WORK_ROOT/build/generated/core/native"
cp "$HARMONY_SOURCE/native-Cargo.lock" "$HARMONY_WORK_ROOT/build/generated/core/native/Cargo.lock"
UNIFFI_ARKTS_LINK_SEARCH_DIR="$(dirname "$CORE_LIBRARY")" cargo build \
  --manifest-path "$HARMONY_WORK_ROOT/build/generated/core/native/Cargo.toml" \
  --target aarch64-unknown-linux-ohos --release --locked
mkdir -p "$HARMONY_WORK_ROOT/artifacts/native"
cp "$CORE_LIBRARY" "$HARMONY_WORK_ROOT/artifacts/native/"
cp "$CARGO_TARGET_DIR/aarch64-unknown-linux-ohos/release/libagentbuddy_core.so" "$HARMONY_WORK_ROOT/artifacts/native/"
"$OHOS_NATIVE/llvm/bin/llvm-strip" --strip-unneeded "$HARMONY_WORK_ROOT/artifacts/native/"*.so
