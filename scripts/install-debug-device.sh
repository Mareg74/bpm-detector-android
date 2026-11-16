#!/usr/bin/env bash
# Install BPM Detector debug APK on a USB-connected Android device (e.g. AYN Thor).
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

pick_adb() {
  if [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]]; then
    echo "$ANDROID_HOME/platform-tools/adb"
  elif [[ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]]; then
    echo "$HOME/Library/Android/sdk/platform-tools/adb"
  elif command -v adb >/dev/null 2>&1; then
    command -v adb
  else
    echo "Install platform-tools: brew install --cask android-platform-tools" >&2
    exit 1
  fi
}

ADB="$(pick_adb)"
echo "Using adb: $ADB"

# Avoid mixed adb daemons (common cause of 'protocol fault' on macOS).
if command -v pkill >/dev/null 2>&1; then
  pkill -9 adb 2>/dev/null || true
fi
"$ADB" kill-server 2>/dev/null || true
sleep 1
"$ADB" start-server

echo "--- adb devices ---"
if ! "$ADB" devices -l; then
  cat >&2 <<'EOF'

ADB failed (e.g. "protocol fault" on Mac):
  1. Unplug/replug USB; on Thor: Developer options → USB debugging ON, mode "File transfer".
  2. Use one adb only: export ANDROID_HOME=$HOME/Library/Android/sdk
  3. Quit Android Studio if its adb fights the CLI (or use Studio's adb path only).
  4. Try: brew reinstall --cask android-platform-tools
  5. Wireless fallback: adb pair IP:PORT (Android 11+ wireless debugging).
EOF
  exit 1
fi

if ! "$ADB" devices | grep -w device >/dev/null; then
  echo "No device in 'device' state. Authorize RSA prompt on the Thor, then rerun." >&2
  exit 1
fi

if [[ ! -f local.properties ]]; then
  sdk="$HOME/Library/Android/sdk"
  if [[ -d "$sdk" ]]; then
    printf 'sdk.dir=%s\n' "$sdk" >local.properties
  fi
fi

./gradlew :app:installDebug
"$ADB" shell am start -n com.Mareg74.bpmdetector/.MainActivity
echo "Launched. Grant microphone, hold landscape, play a steady beat."