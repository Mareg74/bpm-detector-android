#!/usr/bin/env bash
# Mareg (Thor) vs référence BPM sur Mac — Spotify sur Mac uniquement, micro Thor.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=ensure-java.sh
source "$repo_root/scripts/ensure-java.sh"
ensure_java_home 2>/dev/null || true

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
DEVICE="${DEVICE:-e653283c}"
START_X="${START_X:-1750}"
START_Y="${START_Y:-402}"
MEASURE_SEC="${MEASURE_SEC:-8}"
TOLERANCE="${TOLERANCE:-2}"
REF_BPM="${REF_BPM:-}"
POLL_MS="${POLL_MS:-500}"
LOCK_MAX_SEC="${LOCK_MAX_SEC:-3}"

ref_track() {
  osascript -e 'tell application "Spotify" to get {name, artist} of current track' 2>/dev/null \
    || echo "Spotify indisponible"
}

# Tente Auto BPM, BPM DETECTOR, ou REF_BPM explicite.
ref_bpm_mac() {
  if [[ -n "$REF_BPM" ]]; then
    echo "$REF_BPM"
    return
  fi
  local raw
  raw="$(osascript <<'APPLESCRIPT' 2>/dev/null || true
set patterns to {"Auto BPM", "BPM DETECTOR", "BPM Detector"}
repeat with pname in patterns
  try
    tell application "System Events"
      if exists (process pname) then
        tell process pname
          repeat with w in windows
            repeat with t in (value of every static text of w)
              if t is not missing value and t is not "" then
                if t contains "BPM" or t matches "[0-9]+[\\./,][0-9]+" then return t as text
              end if
            end repeat
          end repeat
        end tell
      end if
    end tell
  end try
end repeat
return ""
APPLESCRIPT
)"
  echo "$raw" | grep -oE '[0-9]+[.,][0-9]+' | head -1 | tr ',' '.' || true
}

mareg_bpm_from_ui() {
  "$ADB" -s "$DEVICE" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  "$ADB" -s "$DEVICE" shell cat /sdcard/ui.xml 2>/dev/null \
    | tr '>' '\n' \
    | sed -n 's/.*text="\([0-9][0-9.,]*\)".*/\1/p' \
    | head -1 \
    | tr ',' '.'
}

mareg_bpm_from_log() {
  "$ADB" -s "$DEVICE" logcat -d 2>/dev/null \
    | grep BpmNovelty \
    | tail -5 \
    | sed -n 's/.* smooth=\([0-9,.]*\).*/\1/p' \
    | tail -1 \
    | tr ',' '.'
}

mareg_bpm() {
  local ui log
  log="$(mareg_bpm_from_log)"
  ui="$(mareg_bpm_from_ui)"
  if [[ -n "$log" ]]; then echo "$log"; elif [[ -n "$ui" && "$ui" != "—" ]]; then echo "$ui"; fi
}

mareg_stable_smooth() {
  "$ADB" -s "$DEVICE" logcat -d 2>/dev/null \
    | grep BpmNovelty \
    | tail -8 \
    | sed -n 's/.* smooth=\([0-9,.]*\).*/\1/p' \
    | tr ',' '.' \
    | awk 'NF{if($1+0>60)sum+=$1;n++} END {if(n>2) printf "%.1f", sum/n; else if(n==1) print $1}'
}

run_measure_start() {
  "$ADB" -s "$DEVICE" logcat -c 2>/dev/null || true
  "$ADB" -s "$DEVICE" shell am force-stop com.Mareg74.bpmdetector 2>/dev/null || true
  sleep 0.4
  "$ADB" -s "$DEVICE" shell pm grant com.Mareg74.bpmdetector android.permission.RECORD_AUDIO 2>/dev/null || true
  "$ADB" -s "$DEVICE" shell am start -n com.Mareg74.bpmdetector/.MainActivity
  sleep 1.0
  "$ADB" -s "$DEVICE" shell input tap "$START_X" "$START_Y"
}

lock_time_sec() {
  local start_ts=$1
  local end_ts elapsed bpm
  end_ts=$((start_ts + LOCK_MAX_SEC * 2))
  while [[ $(date +%s) -lt $end_ts ]]; do
    bpm="$(mareg_bpm_from_log)"
    if [[ -n "$bpm" ]]; then
      elapsed=$(awk -v now="$(date +%s)" -v s="$start_ts" 'BEGIN {printf "%.1f", now - s}')
      echo "$elapsed"
      return 0
    fi
    sleep 0.25
  done
  echo "—"
}

cmp_ok() {
  awk -v a="$1" -v b="$2" -v t="$TOLERANCE" 'BEGIN {exit !(a!="" && b!="" && (a+0-b)>-t && (a+0-b)<t)}'
}

echo "=== Thor auto-calibrate (device=$DEVICE) ==="
echo "Piste Mac: $(ref_track)"
ref="$(ref_bpm_mac)"
echo "Réf. BPM Mac: ${ref:-non lue — définir REF_BPM=144.3}"

run_measure_start
start_ts=$(date +%s)
lock="$(lock_time_sec "$start_ts" "$ref")"
sleep "$((MEASURE_SEC > LOCK_MAX_SEC ? MEASURE_SEC - LOCK_MAX_SEC : 0))"

mareg="$(mareg_stable_smooth)"
[[ -z "$mareg" ]] && mareg="$(mareg_bpm)"
echo "Mareg: ${mareg:-—}"
echo "Lock: ${lock}s (cible <${LOCK_MAX_SEC}s)"

if [[ -n "$ref" && -n "$mareg" ]]; then
  delta=$(awk -v m="$mareg" -v r="$ref" 'BEGIN {printf "%.1f", m - r}')
  echo "Δ ref−Mareg: $delta (tol ±$TOLERANCE)"
  if cmp_ok "$mareg" "$ref"; then
    echo "OK: dans la tolérance"
    exit 0
  else
    echo "FAIL: hors tolérance"
    exit 1
  fi
fi
echo "→ Comparer manuellement ou fixer REF_BPM."