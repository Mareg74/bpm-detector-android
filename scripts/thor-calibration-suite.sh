#!/usr/bin/env bash
# Spotify (Mac) → Mareg (Thor). Morceaux listés dans calibration-tracks.tsv.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
CAL="$repo_root/scripts/thor-auto-calibrate.sh"
TRACKS="$repo_root/scripts/calibration-tracks.tsv"
TOLERANCE="${TOLERANCE:-2.5}"
MEASURE_SEC="${MEASURE_SEC:-14}"
WARMUP_SEC="${WARMUP_SEC:-6}"
LOG="$repo_root/build/thor-calibration-results.tsv"
mkdir -p "$(dirname "$LOG")"

spotify_play_uri() {
  osascript -e "tell application \"Spotify\" to play track \"${1}\"" 2>/dev/null || true
  sleep "$WARMUP_SEC"
}

track_label() {
  osascript -e 'tell application "Spotify" to get {name, artist} of current track' 2>/dev/null | tr ',' ' ' || echo "?"
}

run_measure() {
  local ref_bpm="$1"
  local out exitcode=0
  set +e
  out="$(REF_BPM="$ref_bpm" MEASURE_SEC="$MEASURE_SEC" TOLERANCE="$TOLERANCE" "$CAL" </dev/null 2>&1)"
  exitcode=$?
  set -e
  echo "$out"
  local mareg delta lock
  mareg="$(echo "$out" | sed -n 's/^Mareg: //p' | tail -1)"
  lock="$(echo "$out" | sed -n 's/^Lock: \(.*\)s.*/\1/p' | tail -1)"
  delta="$(echo "$out" | grep '^Δ' | tail -1 | sed 's/^Δ ref−Mareg: //;s/ (tol.*//' | tr ',' '.')"
  set +e
  local title
  title="$(track_label)"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$(date '+%H:%M:%S')" "$title" "$ref_bpm" "${mareg:-—}" "${delta:-—}" "${lock:-—}" \
    "$([[ $exitcode -eq 0 ]] && echo OK || echo FAIL)" >>"$LOG"
  set -e
  return "$exitcode"
}

echo "=== Thor calibration suite $(date) ==="
echo -e "time\ttitle\tref\tmareg\tdelta\tlock\tstatus" >"$LOG"
failures=0

set +e
while IFS=$'\t' read -r uri ref comment || [[ -n "${uri:-}" ]]; do
  [[ "$uri" =~ ^#|^$ ]] && continue
  echo ">> ${comment:-$uri} (ref $ref)"
  spotify_play_uri "$uri"
  run_measure "$ref" || failures=$((failures + 1))
  echo ""
done <"$TRACKS"
set -e

echo "Journal: $LOG"
column -t -s $'\t' "$LOG" 2>/dev/null || cat "$LOG"
echo "Échecs: $failures"
exit "$failures"