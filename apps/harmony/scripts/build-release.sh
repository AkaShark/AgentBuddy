#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
export HARMONY_SIGNING_CONFIG="${HARMONY_SIGNING_CONFIG:-$HARMONY_SIGNING_HOME/release-signing-config.json}"
python3 "$HARMONY_SOURCE/scripts/validate-signing.py" --release
python3 "$HARMONY_SOURCE/scripts/prepare-project.py"
cd "$HARMONY_WORK_ROOT/build/project"
export NODE_PATH="$PWD/node_modules"
ohpm install --all --cache "$HARMONY_WORK_ROOT/cache/ohpm"
build_marker="$(mktemp "$HARMONY_WORK_ROOT/tmp/release-build.XXXXXX")"
trap 'rm -f "$build_marker"' EXIT
node "$DEVECO_ROOT/tools/hvigor/hvigor/bin/hvigor.js" --mode project -p product=default -p buildMode=release assembleApp --no-daemon
python3 - "$build_marker" "$HARMONY_WORK_ROOT/artifacts/release" <<'PY'
from pathlib import Path
import shutil
import sys

marker, destination = map(Path, sys.argv[1:])
packages = [p for p in Path('build/outputs').rglob('*-signed.app')
            if p.is_file() and p.stat().st_size > 0
            and p.stat().st_mtime_ns >= marker.stat().st_mtime_ns]
if not packages:
    raise SystemExit('No fresh release .app produced; previous artifacts must not be submitted')
if len({p.name for p in packages}) != len(packages):
    raise SystemExit('Release package filenames collide; inspect build outputs before submission')
destination.mkdir(parents=True, exist_ok=True)
for package in packages:
    output = destination / package.name
    shutil.copy2(package, output)
    print(f'Release package: {output}')
PY
