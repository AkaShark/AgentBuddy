#!/usr/bin/env bash
set -euo pipefail

# AgentBuddy adaptations are committed in the fork. Never apply or undo patches.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
SUBMODULE_DIR="$REPO_DIR/shared/third_party/codex"

SYNC_MODE="${1:---preserve-current}"
case "$SYNC_MODE" in
    --preserve-current|--recorded-gitlink)
        ;;
    *)
        echo "usage: $(basename "$0") [--preserve-current|--recorded-gitlink]" >&2
        exit 1
        ;;
esac

echo "==> Syncing codex submodule..."
if [ ! -e "$SUBMODULE_DIR/.git" ] || ! git -C "$SUBMODULE_DIR" rev-parse --verify HEAD >/dev/null 2>&1; then
    git -C "$REPO_DIR" submodule update --init --recursive shared/third_party/codex
elif [ "$SYNC_MODE" = "--recorded-gitlink" ]; then
    git -C "$REPO_DIR" submodule update --init --recursive shared/third_party/codex
else
    recorded_commit="$(git -C "$REPO_DIR" ls-files --stage shared/third_party/codex | awk 'NR == 1 { print $2 }')"
    current_commit="$(git -C "$SUBMODULE_DIR" rev-parse HEAD)"

    if [ -z "$recorded_commit" ]; then
        echo "error: could not resolve recorded submodule gitlink for shared/third_party/codex" >&2
        exit 1
    fi

    if [ "$current_commit" = "$recorded_commit" ]; then
        echo "==> codex submodule already at recorded gitlink ${current_commit:0:9}"
    else
        echo "==> Preserving current codex checkout ${current_commit:0:9} (recorded gitlink ${recorded_commit:0:9})"
    fi
fi

echo "==> codex fork ready at $(git -C "$SUBMODULE_DIR" rev-parse --short HEAD)"
