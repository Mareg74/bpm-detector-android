#!/usr/bin/env bash
# Local Thor pilot — run on Mac with AYN Thor USB (self-hosted Cursor worker).
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
ADB="${ANDROID_HOME}/platform-tools/adb"

usage() {
  cat <<'EOF'
Usage: ./scripts/thor-pilot.sh <command>

  devices   List adb devices (expect model:AYN_Thor)
  install   Build + installDebug + launch MainActivity
  launch    Start app only
  logs      Tail logcat (bpmdetector / AndroidRuntime), Ctrl+C to stop
  smoke     connectedDebugAndroidTest (MeasureScreenSmokeTest)
  qa        Print link to docs/qa/bpm-detector-manual-checklist.md

Run from branch cursor/local-thor-pilot-95bf on Greg's Mac worker.
EOF
}

cmd="${1:-}"
case "$cmd" in
  devices)
    "$ADB" devices -l
    ;;
  install)
    exec "$repo_root/scripts/install-debug-device.sh"
    ;;
  launch)
    "$ADB" shell am start -n com.Mareg74.bpmdetector/.MainActivity
    ;;
  logs)
    "$ADB" logcat --pid="$("$ADB" shell pidof -s com.Mareg74.bpmdetector 2>/dev/null || echo 0)" 2>/dev/null \
      || "$ADB" logcat | grep -iE 'bpmdetector|AndroidRuntime'
    ;;
  smoke)
    ./gradlew :app:connectedDebugAndroidTest
    ;;
  qa)
    sed -n '1,25p' "$repo_root/docs/qa/bpm-detector-manual-checklist.md"
    ;;
  ""|-h|--help)
    usage
    ;;
  *)
    echo "Unknown command: $cmd" >&2
    usage >&2
    exit 1
    ;;
esac