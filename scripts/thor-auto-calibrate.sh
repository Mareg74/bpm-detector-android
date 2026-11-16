#!/usr/bin/env bash
# Mesure Mareg sur Thor vs référence BPM lue sur le Mac (Spotify + Auto BPM / BPM DETECTOR).
# Spotify ne tourne PAS sur le Thor : audio Mac → haut-parleurs → micro Thor.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=ensure-java.sh
source "$repo_root/scripts/ensure-java.sh"
ensure_java_home 2>/dev/null || true

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
DEVICE="${DEVICE:-e653283c}"
START_X="${START_X:-1750}"
START_Y="${START_Y:-420}"
MEASURE_SEC="${MEASURE_SEC:-8}"

ref_track() {
  osascript -e 'tell application "Spotify" to get {name, artist} of current track' 2>/dev/null \
    || echo "Spotify indisponible"
}

ref_bpm_autobpm() {
  osascript <<'APPLESCRIPT' 2>/dev/null || true
tell application "System Events"
  tell process "Auto BPM"
    set out to {}
    repeat with t in (value of every static text of windows)
      if t is not missing value and t is not "" then set end of out to t
    end repeat
    return out as text
  end tell
end tell
APPLESCRIPT
}

mareg_bpm_from_log() {
  "$ADB" -s "$DEVICE" logcat -d 2>/dev/null \
    | grep BpmNovelty \
    | tail -3 \
    | sed -n 's/.* smooth=\([0-9,.]*\).*/\1/p' \
    | tail -1 \
    | tr ',' '.'
}

run_measure_cycle() {
  "$ADB" -s "$DEVICE" logcat -c 2>/dev/null || true
  "$ADB" -s "$DEVICE" shell am force-stop com.Mareg74.bpmdetector 2>/dev/null || true
  sleep 0.4
  "$ADB" -s "$DEVICE" shell pm grant com.Mareg74.bpmdetector android.permission.RECORD_AUDIO 2>/dev/null || true
  "$ADB" -s "$DEVICE" shell am start -n com.Mareg74.bpmdetector/.MainActivity
  sleep 1.2
  "$ADB" -s "$DEVICE" shell input tap "$START_X" "$START_Y"
  sleep "$MEASURE_SEC"
}

echo "=== Thor auto-calibrate ==="
echo "Réf. piste (Mac Spotify): $(ref_track)"
echo "Réf. Auto BPM (Mac): $(ref_bpm_autobpm)"
echo "Mesure Mareg ${MEASURE_SEC}s (micro Thor, musique Mac)…"
run_measure_cycle
mareg="$(mareg_bpm_from_log)"
echo "Mareg (log smooth): ${mareg:-—}"
echo "→ Saisir le BPM affiché sur Auto BPM / BPM DETECTOR Mac et comparer à Mareg."