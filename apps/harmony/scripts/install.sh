#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
hap="$HARMONY_WORK_ROOT/artifacts/entry-default-signed.hap"
[[ -f "$hap" ]] || {
  echo "Signed HAP missing. Sign the staged project in DevEco, then make harmony-hap." >&2; exit 1;
}
device="${HARMONY_DEVICE_SERIAL:-}"
if [[ -z "$device" ]]; then
  devices=()
  while IFS= read -r serial; do
    [[ -z "$serial" || "$serial" == '[Empty]' ]] || devices+=("$serial")
  done < <(hdc list targets)
  [[ ${#devices[@]} == 1 ]] || { echo "Set HARMONY_DEVICE_SERIAL for one authorized device." >&2; exit 1; }
  device="${devices[0]}"
fi
hdc -t "$device" install -r "$hap"
hdc -t "$device" shell aa start -a EntryAbility -b com.akashark.agentbuddy.mobile
