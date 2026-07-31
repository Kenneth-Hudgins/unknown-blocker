# Unknown Blocker

Simple Android app that blocks **incoming calls from numbers not in your contacts**, with a one-tap on/off toggle. Built for real-world spam/scam call fatigue — flip it off when you're on-call for work and need random help-desk numbers to ring through.

**Min Android:** 10 (API 29)  
**Package:** `com.example.unknownblocker`

---

## What it does

| Feature | Status |
|--------|--------|
| Toggle blocking on/off | ✅ Persisted in SharedPreferences |
| Block non-contact **calls** | ✅ via `CallScreeningService` |
| Log blocked numbers in-app | ✅ Call + SMS attempts, with timestamp |
| Status checklist (role + permissions) | ✅ |
| Clear / refresh blocked history | ✅ |
| Suppress non-contact **SMS** | ⚠️ Best-effort only (Android limitation) |

### Android limitations (important)

- **Calls:** Fully supported when this app is set as the system **Call Screening** app.
- **SMS:** Non-default SMS apps **cannot** fully prevent delivery. This app can try to suppress the broadcast/notification; the message still appears in your default Messages app. Full SMS blocking requires becoming the default SMS app (which replaces your normal messaging app).

---

## Setup on your phone

1. Build & install the debug APK (Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**), or use a release-signed APK.
2. Open **Unknown Blocker**.
3. Flip **Blocking** ON.
4. Grant **Contacts** (and optional SMS / notifications) when prompted.
5. Accept the system prompt to make this the **Call Screening** app.
6. Confirm the status panel shows call screening + contacts as granted.

When you're on-call for work: turn the toggle **OFF**. Contacts still ring either way; only unknown numbers are affected when ON.

---

## Project structure

```
app/src/main/java/com/example/unknownblocker/
  MainActivity.kt          # UI: toggle, status, blocked history list
  ScreeningService.kt      # Call screening / reject logic
  SmsBlockerReceiver.kt    # Best-effort SMS handling + log
  BlockLog.kt              # Persistent blocked-number history
  ContactUtils.kt          # Shared contacts lookup
```

---

## Build from source

**Requirements**

- Android Studio (recent) or JDK 17 + Android SDK
- SDK platform matching `compileSdk` in `app/build.gradle.kts` (currently 37)

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

On Windows:

```bat
gradlew.bat assembleDebug
```

---

## Privacy

- No network access, no ads, no analytics.
- Contacts are read **only** to decide allow vs block; nothing is uploaded.
- Blocked history stays on-device in app SharedPreferences (max 200 entries).

---

## License

MIT — see [LICENSE](LICENSE).
