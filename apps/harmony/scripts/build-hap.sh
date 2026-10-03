#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
if [[ -z "${HARMONY_SIGNING_CONFIG:-}" ]]; then
  node "$HARMONY_SOURCE/scripts/preserve-signing.cjs"
fi
python3 "$HARMONY_SOURCE/scripts/validate-signing.py"
python3 "$HARMONY_SOURCE/scripts/prepare-project.py"
cd "$HARMONY_WORK_ROOT/build/project"
export NODE_PATH="$PWD/node_modules"
ohpm install --all --cache "$HARMONY_WORK_ROOT/cache/ohpm"
node "$DEVECO_ROOT/tools/hvigor/hvigor/bin/hvigor.js" --mode module -p product=default assembleHap --no-daemon
find entry/build/default/outputs -name '*.hap' -exec cp {} "$HARMONY_WORK_ROOT/artifacts/" \;
