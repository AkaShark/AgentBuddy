#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ICON_PATH="${1:-$SCRIPT_DIR/../Sources/AgentBuddy/AppIcon.icon}"
DEVELOPER_PATH="${DEVELOPER_DIR:-$(xcode-select -p)}"
ICTOOL="$DEVELOPER_PATH/../Applications/Icon Composer.app/Contents/Executables/ictool"
OUTPUT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/agentbuddy-icon-check.XXXXXX")"
trap 'rm -rf "$OUTPUT_DIR"' EXIT

# JSON syntax checks cannot validate Icon Composer's typed document format.
# Use its exporter directly: actool can exit 0 after an icon export failure.
"$ICTOOL" "$ICON_PATH" \
  --export-image --output-file "$OUTPUT_DIR/watch-icon.png" \
  --platform watchOS --rendition Default --width 1024 --height 1024 --scale 1
test -s "$OUTPUT_DIR/watch-icon.png"
echo "Watch app icon export validated."
