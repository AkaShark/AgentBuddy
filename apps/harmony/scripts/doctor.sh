#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
printf 'Source: %s\nBuild/cache root: %s\nSDK: %s\n' "$HARMONY_SOURCE" "$HARMONY_WORK_ROOT" "$DEVECO_SDK_HOME"
test -x "$CARGO_TARGET_AARCH64_UNKNOWN_LINUX_OHOS_LINKER"
rustc --version
rustup target list --installed
hdc list targets
