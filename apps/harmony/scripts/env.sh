#!/usr/bin/env bash
# Source this file: all writable build state stays in the user-selected directory.
set -euo pipefail
HARMONY_SOURCE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$HARMONY_SOURCE/../.." && pwd)"
export HARMONY_WORK_ROOT="${AGENTBUDDY_HARMONY_HOME:-$HOME/.agentBuddy/harmony}"
export HARMONY_SIGNING_HOME="${AGENTBUDDY_HARMONY_SIGNING_HOME:-$HOME/.agentBuddy/signing/harmony}"
export DEVECO_ROOT="${DEVECO_ROOT:-/Applications/DevEco-Studio.app/Contents}"
export DEVECO_SDK_HOME="${DEVECO_SDK_HOME:-$DEVECO_ROOT/sdk}"
export OHOS_NDK_HOME="$DEVECO_SDK_HOME/default/openharmony"
OHOS_NATIVE="$OHOS_NDK_HOME/native"
export CARGO_HOME="$HARMONY_WORK_ROOT/cache/cargo"
export CARGO_TARGET_DIR="$HARMONY_WORK_ROOT/build/rust"
export RUSTUP_HOME="$HARMONY_WORK_ROOT/cache/rustup"
export RUSTUP_TOOLCHAIN=stable
export HVIGOR_USER_HOME="$HARMONY_WORK_ROOT/cache/hvigor"
export npm_config_cache="$HARMONY_WORK_ROOT/cache/npm"
export XDG_CACHE_HOME="$HARMONY_WORK_ROOT/cache/xdg"
export TMPDIR="$HARMONY_WORK_ROOT/tmp"
export PATH="$HARMONY_WORK_ROOT/tools/bin:$HOME/.cargo/bin:$DEVECO_ROOT/tools/node/bin:$DEVECO_ROOT/tools/ohpm/bin:$DEVECO_ROOT/sdk/default/openharmony/toolchains:$OHOS_NATIVE/build-tools/cmake/bin:$PATH"
export CARGO_TARGET_AARCH64_UNKNOWN_LINUX_OHOS_LINKER="$OHOS_NATIVE/llvm/bin/aarch64-unknown-linux-ohos-clang"
export CC_aarch64_unknown_linux_ohos="$CARGO_TARGET_AARCH64_UNKNOWN_LINUX_OHOS_LINKER"
export CXX_aarch64_unknown_linux_ohos="$OHOS_NATIVE/llvm/bin/aarch64-unknown-linux-ohos-clang++"
export AR_aarch64_unknown_linux_ohos="$OHOS_NATIVE/llvm/bin/llvm-ar"
export RANLIB_aarch64_unknown_linux_ohos="$OHOS_NATIVE/llvm/bin/llvm-ranlib"
export CARGO_INCREMENTAL=0
unset RUSTC_WRAPPER
mkdir -p "$CARGO_HOME" "$TMPDIR" "$HVIGOR_USER_HOME" "$npm_config_cache" \
  "$HARMONY_WORK_ROOT/logs" "$HARMONY_WORK_ROOT/artifacts"
