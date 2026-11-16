# BPM Detector v1 — Manual QA checklist

**App:** BPM Detector · **Package / applicationId:** `com.Mareg74.bpmdetector`  
**Orientation:** landscape only · **Repo:** https://github.com/Mareg74/bpm-detector-android

Use this checklist before calling v1 “ready” on a physical device. Mark each row **Pass / Fail / N/A** and note build SHA or `versionName` from About / `adb`.

---

## Device setup (AYN Thor or any Android 8+)

### Prerequisites

- [ ] Android Studio SDK installed (`minSdk` 26; internal capture needs **API 29+**).
- [ ] USB debugging enabled on device; RSA fingerprint accepted.
- [ ] **RECORD_AUDIO** will be requested at first START (Mic / USB / Internal).
- [ ] Test audio: a known **~128 BPM** track (e.g. four-on-the-floor house) on speaker or headphones.

### Install via `adb` (recommended: repo script)

1. Clone or pull latest `main`:

   ```bash
   git clone https://github.com/Mareg74/bpm-detector-android.git
   cd bpm-detector-android
   git pull origin main
   ```

2. Point the build at your SDK (required for Gradle):

   ```bash
   export ANDROID_HOME="$HOME/Library/Android/sdk"   # macOS default; adjust on Linux/Windows
   echo "sdk.dir=$ANDROID_HOME" > local.properties
   ```

3. Ensure **platform-tools** `adb` is used (not a conflicting Homebrew daemon):

   ```bash
   export PATH="$ANDROID_HOME/platform-tools:$PATH"
   which adb
   adb devices -l
   ```

   Expect one line in state `device`. On AYN Thor: Developer options → USB debugging ON, USB mode **File transfer / MTP**, accept the RSA prompt.

4. Install debug build and launch:

   ```bash
   chmod +x scripts/install-debug-device.sh
   ./scripts/install-debug-device.sh
   ```

   The script picks `$ANDROID_HOME/platform-tools/adb`, restarts the adb server, runs `./gradlew :app:installDebug`, and starts `com.Mareg74.bpmdetector/.MainActivity`.

### Manual install (without script)

```bash
./gradlew :app:installDebug
adb shell am start -n com.Mareg74.bpmdetector/.MainActivity
```

### AYN Thor on Mac — troubleshooting

If `adb` reports **protocol fault** or no device:

1. Unplug/replug USB; confirm Thor shows **USB debugging connected**.
2. Use a **single** `adb`: `export ANDROID_HOME=$HOME/Library/Android/sdk`.
3. `pkill -9 adb; sleep 1; $ANDROID_HOME/platform-tools/adb start-server`.
4. Quit Android Studio if its embedded `adb` conflicts with CLI.
5. Re-run `./scripts/install-debug-device.sh`.

---

## Functional checklist

| # | Scenario | Steps | Expected | Pass |
|---|----------|-------|----------|------|
| 1 | **Mic START → Tracking** | Settings → source **Mic**. Play ~128 BPM near mic. Tap **START**. | Status **Starting…** then **Tracking**; BPM near **128** (± few BPM). No ghost BPM: **—** while Starting. | [ ] |
| 2 | **Decimal toggle** | Settings → enable **Show decimal**. | Integer large; fractional digit smaller. Disable → integer only. | [ ] |
| 3 | **Genre House vs Wide** | Settings → genre **House/Techno**, then **Wide** on same track. | Range / lock behavior differs; BPM stays plausible for the track. | [ ] |
| 4 | **Force resync** | While Tracking, switch to a track with clearly different BPM (e.g. 90 → 140). | Status **Resyncing…**; last BPM **dimmed**; settles on new tempo or recoverable state. | [ ] |
| 5 | **Waveform / history panels** | Tap **Show panels**. | Waveform moves with audio; history line updates over session. **Hide panels** removes column. | [ ] |
| 6 | **Day / night theme** | Settings → theme **Night** then **Day**. | UI switches dark / light scheme immediately. | [ ] |
| 7 | **Background stop** | START → Tracking, press Home or switch app. Return. | Measurement **stopped** (Idle, STOP shown); v1 does not measure in background. | [ ] |
| 8 | **Internal capture** | On **API 29+**: Settings → **Internal**; START; approve **screen capture** + mic if prompted. Play media on device. | Capture works or clear error banner if OEM blocks capture. On **API &lt; 29** or unsupported: Internal **disabled** in settings or banner **Android 10+** if forced. | [ ] |
| 9 | **USB audio** | If **USB audio interface / headset** present: Settings → **USB**, START. | Tracking from USB input; disconnect mid-session shows **USB audio disconnected.** banner (optional: verify reconnect UX). If no USB hardware: **N/A**. | [ ] |

---

## Permissions & errors (spot checks)

| # | Scenario | Expected | Pass |
|---|----------|----------|------|
| P1 | Deny **RECORD_AUDIO** on START | Error banner: mic permission required; stays **Idle**. | [ ] |
| P2 | Internal START, deny **screen capture** | Banner: screen capture required; stays **Idle**. | [ ] |
| P3 | USB disconnect while Tracking (if tested) | Banner **USB audio disconnected.** | [ ] |

---

## Sign-off

| Field | Value |
|-------|-------|
| Tester | |
| Device / API | |
| Build (`git rev-parse --short HEAD`) | |
| Date | |
| Overall | Pass / Fail |

**Notes:**

---

_Last updated: 2026-11-16 — aligns with Tasks 10–13 (measure UI, settings, panels, permissions, lifecycle stop)._