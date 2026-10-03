#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
IOS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_DIR="$(cd "$IOS_DIR/../.." && pwd)"
source "$REPO_DIR/tools/scripts/load-sccache-aws-creds.sh"
RUST_BRIDGE_DIR="$REPO_DIR/shared/rust-bridge"
CARGO_TARGET_DIR_EFFECTIVE="${CARGO_TARGET_DIR:-$RUST_BRIDGE_DIR/target}"
FRAMEWORKS_DIR="$IOS_DIR/Frameworks"
GENERATED_SWIFT_DIR="$RUST_BRIDGE_DIR/generated/swift"
UNIFFI_OUT="$IOS_DIR/Sources/AgentBuddy/Bridge/UniFFICodexClient.generated.swift"
GENERATED_RUST_DIR="$IOS_DIR/GeneratedRust"
GENERATED_HEADERS_DIR="$GENERATED_RUST_DIR/Headers"
GENERATED_DEVICE_DIR="$GENERATED_RUST_DIR/ios-device"
GENERATED_SIM_DIR="$GENERATED_RUST_DIR/ios-sim"
BINDINGS_HASH_FILE="$GENERATED_RUST_DIR/.swift-bindings.hash"
IOS_DEPLOYMENT_TARGET="${IOS_DEPLOYMENT_TARGET:-18.0}"
SUBMODULE_DIR="$REPO_DIR/shared/third_party/codex"
IOS_CLANGXX_WRAPPER="$SCRIPT_DIR/ios-clangxx-wrapper.sh"
SYNC_MODE="--preserve-current"
DEVICE_ONLY=0
FAST_DEVICE=0
SIM_ONLY=0
FAST_SIM=0
FORCE_BINDINGS=0
SKIP_BINDINGS=0
CARGO_FEATURES=""
PROFILE="release"
CARGO_PROFILE_FLAG="--release"
IOS_RUST_PROFILE="${IOS_RUST_PROFILE:-release}"

if [ "$IOS_RUST_PROFILE" != "release" ]; then
  PROFILE="$IOS_RUST_PROFILE"
  CARGO_PROFILE_FLAG="--profile $IOS_RUST_PROFILE"
fi

for arg in "$@"; do
  case "$arg" in
    --preserve-current|--recorded-gitlink)
      SYNC_MODE="$arg"
      ;;
    --device-only)
      DEVICE_ONLY=1
      ;;
    --fast-device)
      FAST_DEVICE=1
      DEVICE_ONLY=1
      PROFILE="ios-dev"
      CARGO_PROFILE_FLAG="--profile ios-dev"
      ;;
    --fast-sim)
      FAST_SIM=1
      SIM_ONLY=1
      PROFILE="ios-dev"
      CARGO_PROFILE_FLAG="--profile ios-dev"
      ;;
    --force-bindings)
      FORCE_BINDINGS=1
      ;;
    --skip-bindings)
      SKIP_BINDINGS=1
      ;;
    --rpc-trace)
      CARGO_FEATURES="--features rpc-trace"
      ;;
    *)
      echo "usage: $(basename "$0") [--preserve-current|--recorded-gitlink] [--device-only] [--fast-device] [--fast-sim] [--force-bindings] [--skip-bindings] [--rpc-trace]" >&2
      exit 1
      ;;
  esac
done

mkdir -p "$FRAMEWORKS_DIR" "$GENERATED_HEADERS_DIR"
if [ "$SIM_ONLY" -eq 0 ]; then mkdir -p "$GENERATED_DEVICE_DIR"; fi
if [ "$DEVICE_ONLY" -eq 0 ]; then mkdir -p "$GENERATED_SIM_DIR"; fi

if [ -z "${RUSTC_WRAPPER:-}" ] && [ "${CARGO_INCREMENTAL:-}" != "1" ] && command -v sccache >/dev/null 2>&1; then
  export RUSTC_WRAPPER="$(command -v sccache)"
fi

"$REPO_DIR/tools/scripts/update-alleycat-main.sh" --shared

# libghostty static libs + headers must exist before the Rust crate is
# compiled (codex-mobile-client links against them). Build them on demand
# when missing so this script is self-sufficient for CI workflows that
# invoke it directly (without going through the Makefile's stamp dep).
LIBGHOSTTY_DEVICE_LIB="$GENERATED_DEVICE_DIR/libghostty.a"
LIBGHOSTTY_SIM_LIB="$GENERATED_SIM_DIR/libghostty.a"
NEEDS_GHOSTTY=0
if [ "$DEVICE_ONLY" -eq 1 ] && [ ! -f "$LIBGHOSTTY_DEVICE_LIB" ]; then
  NEEDS_GHOSTTY=1
elif [ "$SIM_ONLY" -eq 1 ] && [ ! -f "$LIBGHOSTTY_SIM_LIB" ]; then
  NEEDS_GHOSTTY=1
elif [ "$DEVICE_ONLY" -eq 0 ] && [ "$SIM_ONLY" -eq 0 ] &&
  { [ ! -f "$LIBGHOSTTY_DEVICE_LIB" ] || [ ! -f "$LIBGHOSTTY_SIM_LIB" ]; }; then
  NEEDS_GHOSTTY=1
fi
if [ "$NEEDS_GHOSTTY" -eq 1 ]; then
  echo "==> libghostty artifacts missing; building (use 'make ghostty-ios' to invoke with stamp caching)"
  "$REPO_DIR/apps/ios/scripts/build-ghostty.sh"
fi

ensure_host_llvm_on_path() {
  local candidate
  local llvm_candidates=(
    "/opt/homebrew/opt/llvm/bin"
    "/usr/local/opt/llvm/bin"
    "/opt/local/bin"
  )

  if [ -n "${ANDROID_NDK_HOME:-}" ]; then
    for candidate in "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/bin; do
      llvm_candidates+=("$candidate")
    done
  fi

  if [ -n "${ANDROID_NDK_ROOT:-}" ]; then
    for candidate in "$ANDROID_NDK_ROOT"/toolchains/llvm/prebuilt/*/bin; do
      llvm_candidates+=("$candidate")
    done
  fi

  for candidate in /opt/homebrew/share/android-commandlinetools/ndk/*/toolchains/llvm/prebuilt/*/bin; do
    llvm_candidates+=("$candidate")
  done

  for candidate in "${llvm_candidates[@]}"; do
    if [ -x "$candidate/clang" ] && [ -x "$candidate/ld.lld" ]; then
      case ":$PATH:" in
        *":$candidate:"*) ;;
        *) export PATH="$candidate:$PATH" ;;
      esac
      return
    fi
  done
}

ensure_host_llvm_on_path

export CXX_aarch64_apple_ios="$IOS_CLANGXX_WRAPPER"
export CXX_aarch64_apple_ios_sim="$IOS_CLANGXX_WRAPPER"
export IPHONEOS_DEPLOYMENT_TARGET="$IOS_DEPLOYMENT_TARGET"

# bindgen 0.70 maps `aarch64-apple-ios-sim` -> `arm64-apple-ios-sim`, which
# clang rejects ("version 'sim' in target triple ... is invalid"). The correct
# clang triple uses the `-simulator` environment suffix. Override per-target so
# bindgen-driven build scripts (e.g. ish-embed-host) point at the iPhoneSimulator
# SDK when cross-compiling for the simulator.
IPHONESIM_SDK="$(xcrun --sdk iphonesimulator --show-sdk-path 2>/dev/null || true)"
if [ -n "$IPHONESIM_SDK" ]; then
  export BINDGEN_EXTRA_CLANG_ARGS_aarch64_apple_ios_sim="--target=arm64-apple-ios${IOS_DEPLOYMENT_TARGET}-simulator -isysroot ${IPHONESIM_SDK}"
fi

bindings_inputs() {
  cat <<EOF
$RUST_BRIDGE_DIR/codex-mobile-client/src/lib.rs
$RUST_BRIDGE_DIR/codex-mobile-client/src/conversation_uniffi.rs
$RUST_BRIDGE_DIR/codex-mobile-client/src/discovery_uniffi.rs
$RUST_BRIDGE_DIR/codex-mobile-client/Cargo.toml
$RUST_BRIDGE_DIR/Cargo.lock
$RUST_BRIDGE_DIR/../third_party/codex/codex-rs/app-server-protocol/src/protocol/common.rs
$RUST_BRIDGE_DIR/../third_party/codex/codex-rs/app-server-protocol/src/protocol/v1.rs
$RUST_BRIDGE_DIR/../third_party/codex/codex-rs/app-server-protocol/src/protocol/v2.rs
$RUST_BRIDGE_DIR/../third_party/codex/codex-rs/protocol/src/account.rs
$RUST_BRIDGE_DIR/../third_party/codex/codex-rs/protocol/src/config_types.rs
$RUST_BRIDGE_DIR/../third_party/codex/codex-rs/protocol/src/models.rs
$RUST_BRIDGE_DIR/../third_party/codex/codex-rs/protocol/src/openai_models.rs
$RUST_BRIDGE_DIR/../third_party/codex/codex-rs/protocol/src/parse_command.rs
$RUST_BRIDGE_DIR/../third_party/codex/codex-rs/protocol/src/protocol.rs
EOF
  find "$RUST_BRIDGE_DIR/codex-mobile-client/src" -type f -name '*.rs' | sort
}

compute_bindings_hash() {
  local file
  {
    while IFS= read -r file; do
      [ -f "$file" ] || continue
      shasum -a 256 "$file"
    done < <(bindings_inputs | sort)
  } | shasum -a 256 | awk '{print $1}'
}

sync_generated_headers() {
  cp "$GENERATED_SWIFT_DIR/codex_mobile_clientFFI.h" "$GENERATED_HEADERS_DIR/codex_mobile_clientFFI.h"
  cp "$GENERATED_SWIFT_DIR/codex_mobile_clientFFI.modulemap" "$GENERATED_HEADERS_DIR/codex_mobile_clientFFI.modulemap"
  cp "$GENERATED_SWIFT_DIR/module.modulemap" "$GENERATED_HEADERS_DIR/module.modulemap"
}

maybe_generate_swift_bindings() {
  local current_hash existing_hash
  if [ "$SKIP_BINDINGS" -eq 1 ]; then
    echo "==> Skipping UniFFI Swift bindings (--skip-bindings)"
    return
  fi

  current_hash="$(compute_bindings_hash)"
  existing_hash=""
  if [ -f "$BINDINGS_HASH_FILE" ]; then
    existing_hash="$(cat "$BINDINGS_HASH_FILE")"
  fi

  if [ "$FORCE_BINDINGS" -eq 0 ] &&
    [ "$current_hash" = "$existing_hash" ] &&
    [ -f "$GENERATED_SWIFT_DIR/codex_mobile_client.swift" ] &&
    [ -f "$GENERATED_SWIFT_DIR/codex_mobile_clientFFI.h" ] &&
    [ -f "$UNIFFI_OUT" ]; then
    echo "==> Swift bindings unchanged; reusing generated output"
    sync_generated_headers
    return
  fi

  echo "==> Regenerating UniFFI Swift bindings -> $UNIFFI_OUT"
  cd "$RUST_BRIDGE_DIR"
  "$RUST_BRIDGE_DIR/generate-bindings.sh" --swift-only
  cp "$GENERATED_SWIFT_DIR/codex_mobile_client.swift" "$UNIFFI_OUT"
  sync_generated_headers
  printf '%s\n' "$current_hash" >"$BINDINGS_HASH_FILE"
}

copy_device_artifact() {
  cp "$CARGO_TARGET_DIR_EFFECTIVE/aarch64-apple-ios/$PROFILE/libcodex_mobile_client.a" \
    "$GENERATED_DEVICE_DIR/libcodex_mobile_client.a"
}

copy_sim_artifact() {
  local sim_lib="$1"
  cp "$sim_lib" "$GENERATED_SIM_DIR/libcodex_mobile_client.a"
}

echo "==> Preparing codex submodule..."
"$SCRIPT_DIR/sync-codex.sh" "$SYNC_MODE"

maybe_generate_swift_bindings

echo "==> Installing iOS targets..."
if [ "$DEVICE_ONLY" -eq 1 ]; then
  rustup target add aarch64-apple-ios
elif [ "$SIM_ONLY" -eq 1 ]; then
  rustup target add aarch64-apple-ios-sim
else
  rustup target add aarch64-apple-ios aarch64-apple-ios-sim
fi
# litter-ish builds a small Linux supervisor into the embedded rootfs. Older
# releases used i686, current releases use AArch64; keep both targets available
# so the git-tracked dependency can move without breaking iOS builds.
rustup target add i686-unknown-linux-musl aarch64-unknown-linux-musl

if [ "$DEVICE_ONLY" -eq 1 ]; then
  echo "==> Building codex-mobile-client for aarch64-apple-ios ($PROFILE)..."
  cargo rustc --manifest-path "$RUST_BRIDGE_DIR/Cargo.toml" -p codex-mobile-client $CARGO_PROFILE_FLAG --target aarch64-apple-ios --crate-type staticlib $CARGO_FEATURES
  copy_device_artifact
elif [ "$SIM_ONLY" -eq 1 ]; then
  echo "==> Building codex-mobile-client for aarch64-apple-ios-sim ($PROFILE)..."
  cargo rustc --manifest-path "$RUST_BRIDGE_DIR/Cargo.toml" -p codex-mobile-client $CARGO_PROFILE_FLAG --target aarch64-apple-ios-sim --crate-type staticlib $CARGO_FEATURES
  copy_sim_artifact "$CARGO_TARGET_DIR_EFFECTIVE/aarch64-apple-ios-sim/$PROFILE/libcodex_mobile_client.a"
else
  # Build device and simulator targets in parallel
  echo "==> Building codex-mobile-client for device and simulator targets ($PROFILE) in parallel..."

  build_device() {
    cargo rustc --manifest-path "$RUST_BRIDGE_DIR/Cargo.toml" -p codex-mobile-client $CARGO_PROFILE_FLAG --target aarch64-apple-ios --crate-type staticlib $CARGO_FEATURES
  }

  build_sim() {
    cargo rustc --manifest-path "$RUST_BRIDGE_DIR/Cargo.toml" -p codex-mobile-client $CARGO_PROFILE_FLAG --target aarch64-apple-ios-sim --crate-type staticlib $CARGO_FEATURES
  }

  build_device &
  DEVICE_PID=$!
  build_sim &
  SIM_PID=$!
  FAILED=0
  if ! wait "$DEVICE_PID"; then
    echo "ERROR: device build (aarch64-apple-ios) failed" >&2
    FAILED=1
  fi
  if ! wait "$SIM_PID"; then
    echo "ERROR: simulator build (aarch64-apple-ios-sim) failed" >&2
    FAILED=1
  fi
  [ "$FAILED" -eq 0 ] || exit 1

  copy_device_artifact
  copy_sim_artifact "$CARGO_TARGET_DIR_EFFECTIVE/aarch64-apple-ios-sim/$PROFILE/libcodex_mobile_client.a"
fi

if [ "$FAST_DEVICE" -eq 1 ]; then
  echo "==> Fast device build complete"
  echo "==> Device staticlib: $GENERATED_DEVICE_DIR/libcodex_mobile_client.a"
  echo "==> Headers: $GENERATED_HEADERS_DIR"
  echo "==> Swift bindings: $UNIFFI_OUT"
  exit 0
fi

if [ "$FAST_SIM" -eq 1 ]; then
  echo "==> Fast simulator build complete"
  echo "==> Simulator staticlib: $GENERATED_SIM_DIR/libcodex_mobile_client.a"
  echo "==> Headers: $GENERATED_HEADERS_DIR"
  echo "==> Swift bindings: $UNIFFI_OUT"
  exit 0
fi

echo "==> Creating xcframework..."
rm -rf "$FRAMEWORKS_DIR/codex_bridge.xcframework" "$FRAMEWORKS_DIR/codex_mobile_client.xcframework"
if [ "$DEVICE_ONLY" -eq 1 ]; then
  xcodebuild -create-xcframework \
    -library "$GENERATED_DEVICE_DIR/libcodex_mobile_client.a" \
    -headers "$GENERATED_HEADERS_DIR" \
    -output "$FRAMEWORKS_DIR/codex_mobile_client.xcframework"
else
  xcodebuild -create-xcframework \
    -library "$GENERATED_DEVICE_DIR/libcodex_mobile_client.a" \
    -headers "$GENERATED_HEADERS_DIR" \
    -library "$GENERATED_SIM_DIR/libcodex_mobile_client.a" \
    -headers "$GENERATED_HEADERS_DIR" \
    -output "$FRAMEWORKS_DIR/codex_mobile_client.xcframework"
fi

echo "==> Done: $FRAMEWORKS_DIR/codex_mobile_client.xcframework"
echo "==> Raw device staticlib: $GENERATED_DEVICE_DIR/libcodex_mobile_client.a"
if [ "$DEVICE_ONLY" -eq 0 ]; then
  echo "==> Raw simulator staticlib: $GENERATED_SIM_DIR/libcodex_mobile_client.a"
fi
echo "==> Headers: $GENERATED_HEADERS_DIR"
echo "==> Swift bindings: $UNIFFI_OUT"
