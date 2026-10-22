# Unknown Blocker

Simple Android app that blocks **incoming calls from numbers not in your contacts**, with a one-tap on/off toggle. Built for real-world spam/scam call fatigue — flip it off when you're on-call for work, or keep it on and allow whole work area codes (e.g. help desk `254`).

**Min Android:** 10 (API 29)  
**Package:** `com.example.unknownblocker`  
**Current version:** 1.3.3  
**Repo:** https://github.com/Kenneth-Hudgins/unknown-blocker


---

## Download (sideload APK)

Pre-built **release-signed** APKs are attached to [GitHub Releases](https://github.com/Kenneth-Hudgins/unknown-blocker/releases).

1. Open the latest release and download `UnknownBlocker-1.x.x.apk` (or `app-release.apk`).
2. On your phone: allow **Install unknown apps** for your browser/Files app.
3. Open the APK → Install.
4. If you previously installed a **debug** build from Android Studio, **uninstall it first** (different signature), then install the release APK.
5. Open the app → Blocking ON → grant Call Screening (+ Notification access if using VM mute) → reboot recommended.

Source stays free and open. No Play Store required. Signing details for maintainers: [docs/RELEASE-SIGNING.md](docs/RELEASE-SIGNING.md).

---

## Features (v1.3.3)

### Core (v1.0)

- **Master toggle** — enable/disable blocking; state persists across restarts
- **Block non-contact calls** via Android `CallScreeningService` (Android 10+)
- **Contacts always allowed** — anyone in your address book still rings
- **Best-effort SMS handling** — limited by Android (non-default SMS apps cannot fully block texts)
- **Privacy-first** — no network, no ads, no analytics; contacts stay on-device
- **Sideload APK** — release-signed builds on GitHub Releases; debug builds for Studio

### Added in v1.1.0

- **Blocked history** — number, type, timestamp (capped)
- **Clear / refresh** history controls
- **Status checklist** — blocking on/off, call-screening role, contacts/SMS permissions, allowed area codes
- **Allowed area codes** — while blocking stays ON, entire NANP area codes (e.g. `254`) still ring even if not in contacts
- **Add / remove area codes** in the UI (3-digit codes; handles `+1`, dashes, parentheses)
- **Shared allow rules** — decision order: contacts → allowed area code → block

### Added in v1.2.x

- **Sticky hide voicemail alerts after blocked calls** (optional; needs Notification access)
  - Arms on a blocked call — **not** a short timer
  - Stays muted until a VM looks like a contact/allowed caller (then alerts work again)
  - Does **not** delete carrier voicemail — only the notification
- Scrollable main UI; Status: VM mute ACTIVE vs idle
- **Optional diagnostic listener log** (default **OFF**)
  - On-device only; no upload/collection
  - Auto-deletes at **2 MB**
  - Privacy note + link to audit this repo

### Added in v1.3.3

- **Day-grouped blocked-call history** — newest day first; tap a day to expand details (calls only in the list)
- **Mute all voicemail alerts** toggle (default **OFF**) — continuous mute until you turn it off; contact calls/texts do not re-enable it
- **VM dismiss = cancel + 6-hour snooze** — cuts down alert bounce after dismiss
- **Safer VM detection** — SMS/chat notifications are not treated as voicemail
- Sticky after-block mute still works when mute-all is off

### Expected: brief screen wake on block

When a spam/unknown call is blocked and registered, the **phone screen may light up briefly**. If you leave the device alone, it should **turn back off / sleep like normal**. That wake is normal Android/dialer behavior around screened calls — not the app forcing the display to stay on.

---

## Real-device validation

### v1.1.0 (call blocking + area codes)

Tested by installing the debug APK as an **update** over the previous working build:

| Check | Result |
|--------|--------|
| Update install over existing app | ✅ Offered update; installed cleanly |
| UI: toggle, status, area codes, history | ✅ Present after install |
| Blocking after install | ⚠️ Required a **phone restart** before screening behavior stuck |
| Non-contact / non-allowlisted number | ✅ Went to voicemail after restart |
| After adding caller’s area code | ✅ Next call was allowed through |

### v1.2.x (VM notification mute — Galaxy S22+)

| Check | Result |
|--------|--------|
| Call blocking still correct | ✅ Same contacts / area-code rules |
| Timed 15‑min mute missed delayed spam VM | ❌ Observed — motivated sticky mute |
| Sticky mute after block | ✅ Delayed VM alert suppressed while armed |
| Real/allowed path | ✅ Designed to re-enable on allowed-looking VM |

### v1.3.3 (history + mute-all + 6h snooze)

| Check | Result |
|--------|--------|
| Day-grouped history + mute-all + SMS-safe VM mute | ✅ Feature line used on device |
| Unwanted VM notifications for several days | ✅ User report on pre-release line before merge to main |
| Brief screen on when a block is registered | ℹ️ Expected; screen returns to sleep if left alone |

**Practical tip:** After installing or updating, **reboot the phone** (or re-confirm Call Screening role + Notification access), then verify with a real call before relying on it for on-call shifts.

---

## Android limitations (important)

- **Calls:** Fully supported when this app is set as the system **Call Screening** app.
- **SMS:** Non-default SMS apps **cannot** fully prevent delivery. This app can try to suppress the broadcast/notification; the message still appears in the default Messages app. Full SMS blocking requires becoming the default SMS app (which replaces normal messaging).
- **Emulator:** Fine for UI smoke tests; real call-screening behavior needs a **physical phone**.

---

## Setup on your phone

1. Build & install the debug APK  
   (`Build → Build Bundle(s) / APK(s) → Build APK(s)` in Android Studio), or copy:

   `app/build/outputs/apk/debug/app-debug.apk`

2. Open **Unknown Blocker**.
3. Flip **Blocking** ON.
4. Grant **Contacts** (and optional SMS / notifications) when prompted.
5. Accept the system prompt to make this the **Call Screening** app.
6. Confirm the status panel shows call screening + contacts as granted.
7. **Reboot the phone** after first install/update (recommended).
8. Optional: add work **area codes** (e.g. `254`) under **Allowed area codes**.

### On-call options

- Turn blocking **OFF**, or  
- Leave blocking **ON** and allow work area codes so help-desk ranges still ring without opening the floodgates to every spam number.

Contacts always ring either way. Allowed area codes apply to US/Canada-style (NANP) numbers, including `+1` prefixes.

---

## Project structure

```
app/src/main/java/com/example/unknownblocker/
  MainActivity.kt                      # UI: toggle, status, area codes, history, VM mute
  ScreeningService.kt                  # Call screening / reject + arm VM mute
  SmsBlockerReceiver.kt                # Best-effort SMS handling + log
  AllowRules.kt                        # contacts OR allowed area code → allow
  AreaCodeAllowlist.kt                 # Persist + match NANP area codes (e.g. 254)
  BlockLog.kt                          # Persistent blocked-number history
  ContactUtils.kt                      # Shared contacts lookup
  BlockerSettings.kt                   # Prefs, sticky VM mute arm/disarm
  VoicemailNotificationListener.kt     # Optional VM notification suppress
  NotificationProbe.kt                 # Listener log file
  RecentCallBlocks.kt                  # Recent screened numbers for VM matching
```

---

## Build from source

**Requirements**

- Android Studio (recent) or JDK 17+ + Android SDK
- SDK platform matching `compileSdk` in `app/build.gradle.kts` (currently 37)

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Windows:

```bat
gradlew.bat assembleDebug
```

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

---

## Privacy

- No network access, no ads, no analytics.
- Contacts are read **only** to decide allow vs block; nothing is uploaded.
- Blocked history and area-code allowlist stay on-device in app SharedPreferences.

---

## License

MIT — see [LICENSE](LICENSE).
