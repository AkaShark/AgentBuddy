#!/usr/bin/env bash
# Keep the submission ID even when Apple's queue outlives this CI run.
set -euo pipefail

BUNDLE_DIR="${1:?usage: notarize-dmg.sh <dmg-directory> <diagnostics-directory>}"
DIAGNOSTICS_DIR="${2:?missing diagnostics directory}"
: "${APPLE_API_KEY_PATH:?missing API key path}"
: "${APPLE_API_KEY:?missing API key ID}"
: "${APPLE_API_ISSUER:?missing API issuer ID}"
mkdir -p "$DIAGNOSTICS_DIR"
shopt -s nullglob
DMGS=("$BUNDLE_DIR"/*.dmg)
if [[ ${#DMGS[@]} -ne 1 ]]; then
  echo "Expected exactly one DMG in $BUNDLE_DIR; found ${#DMGS[@]}" >&2
  exit 1
fi
DMG="${DMGS[0]}"
AUTH=(--key "$APPLE_API_KEY_PATH" --key-id "$APPLE_API_KEY" --issuer "$APPLE_API_ISSUER")

# Submit without --wait, so the ID is persisted before any long polling.
xcrun notarytool submit "$DMG" "${AUTH[@]}" --no-wait --output-format json \
  > "$DIAGNOSTICS_DIR/submission.json"
SUBMISSION_ID="$(jq -er '.id' "$DIAGNOSTICS_DIR/submission.json")"
echo "Notarization submission: $SUBMISSION_ID"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  printf 'Notarization submission: `%s`\n' "$SUBMISSION_ID" >> "$GITHUB_STEP_SUMMARY"
fi

# A timeout does not cancel Apple's work. Inspect the final known status and
# preserve the ID so a later info/log query can diagnose or resume this build.
xcrun notarytool wait "$SUBMISSION_ID" "${AUTH[@]}" \
  --timeout "${NOTARY_TIMEOUT:-60m}" > "$DIAGNOSTICS_DIR/wait.log" 2>&1 || true
cat "$DIAGNOSTICS_DIR/wait.log"
xcrun notarytool info "$SUBMISSION_ID" "${AUTH[@]}" --output-format json \
  > "$DIAGNOSTICS_DIR/status.json"
STATUS="$(jq -er '.status' "$DIAGNOSTICS_DIR/status.json")"
echo "Notarization status: $STATUS"
if [[ "$STATUS" != "In Progress" ]]; then
  xcrun notarytool log "$SUBMISSION_ID" "${AUTH[@]}" \
    "$DIAGNOSTICS_DIR/notary-log.json" || true
fi
if [[ "$STATUS" != "Accepted" ]]; then
  echo "Notarization $SUBMISSION_ID is $STATUS; see the retained diagnostics. DMG will not be released." >&2
  exit 1
fi
xcrun stapler staple "$DMG"
xcrun stapler validate "$DMG"
