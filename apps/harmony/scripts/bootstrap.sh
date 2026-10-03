#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
[[ -x "$OHOS_NATIVE/llvm/bin/aarch64-unknown-linux-ohos-clang" ]] || {
  echo "Install DevEco Studio with the HarmonyOS SDK first (or set DEVECO_ROOT)." >&2; exit 1;
}
command -v rustup >/dev/null
# A separate rustup home prevents adding targets or updating the user's global toolchain.
rustup toolchain install stable --profile minimal --target aarch64-unknown-linux-ohos
bindgen_revision=56018734abafc1de7e9d124badb3a33d49243f59
generator="$HARMONY_WORK_ROOT/tools/uniffi-bindgen-arkts-src"
if [[ ! -d "$generator/.git" ]]; then
  git clone https://github.com/ohos-rs/uniffi-bindgen-arkts.git "$generator"
fi
git -C "$generator" checkout --detach "$bindgen_revision"
if [[ ! -x "$HARMONY_WORK_ROOT/tools/bin/uniffi-bindgen-arkts" ]] || \
   [[ "$(cat "$HARMONY_WORK_ROOT/tools/bindgen-revision" 2>/dev/null || true)" != "$bindgen_revision" ]]; then
  CARGO_TARGET_DIR="$HARMONY_WORK_ROOT/build/bindgen" cargo install \
    --path "$generator" --locked --root "$HARMONY_WORK_ROOT/tools" --force
  printf '%s\n' "$bindgen_revision" > "$HARMONY_WORK_ROOT/tools/bindgen-revision"
fi
echo "HarmonyOS tools ready: $HARMONY_WORK_ROOT"
