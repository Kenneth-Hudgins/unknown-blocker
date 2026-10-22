# Changelog

All notable changes to **Unknown Blocker** are documented here.

## [1.3.3] — 2026-10-22

Stable release of the feature line that was previously published as **v1.3.3-pre** / `1.3.3-snooze-6h`. Merged from `feat/blocked-history-by-date` into **`main`**.

### Added

- **Day-grouped blocked-call history** — days with blocks listed newest-first (e.g. `9/1/2026 (3)`); tap a day to expand number + time details. **Calls only** in the UI (SMS may still be logged internally).
- **Mute all voicemail alerts** toggle (default **OFF**) — while ON, continuously dismisses voicemail-looking notifications until you turn it off. Contact calls/texts do **not** re-enable alerts. Independent of sticky after-block mute.
- **First-open / install day** tracking (used for history context on upgrades).

### Changed / fixed

- **VM dismiss = cancel + 6-hour snooze** — reduces Phone/Visual Voicemail repost bounce after a dismiss (earlier short snoozes could duck media audio; 6h is the tuned value).
- **SMS/chat are not treated as voicemail** when muting — tighter detection so mute-all / sticky modes do not cancel normal messaging notifications.
- Sticky **hide VM after blocked call** still available when mute-all is off (arms on block; disarms on allowed-looking VM).

### Device behavior note (expected)

- When a call is blocked and logged, the **phone screen may briefly turn on** (or wake) as Android/the dialer registers the event. If you leave the phone alone, it should **go back to sleep / turn off like normal**. This is normal system behavior around screened/rejected calls — not a crash and not the app keeping the screen on.

### Unchanged

- Core blocking: contacts + allowed NANP area codes (e.g. `254`)
- Free, no ads, no tracking, no network backend
- Optional diagnostic listener log (default OFF, on-device only, 2 MB auto-wipe)
- Cannot delete carrier voicemail — only best-effort **notification** mute

### Version

- App version **1.3.3** (`versionCode` 16)

### Install notes

- Prefer the **release-signed** APK from GitHub Releases to update over prior release builds (v1.2.x).
- Debug-signed and release-signed builds **do not** update over each other — uninstall once if switching.
- After install/update: re-confirm **Call Screening** + **Notification access** (for VM features), then **reboot** recommended.

---

## [1.2.8] — 2026-08-12

### Changed

- VM notification handling on `main`: **cancel without snooze** (interim line before 1.3.3’s 6-hour snooze returned on the feature branch)

### Version

- App version **1.2.8** (`versionCode` 11)

---

## [1.2.7] — 2026-08-10

### Changed
- VM notification snooze extended to **60 minutes** (reduces media volume duck loop)
- **Release-signed** APK available on GitHub Releases for public sideload

### Version
- App version **1.2.7** (`versionCode` 10)

---

## [1.2.6] — 2026-08-05

### Added / improved (since 1.2.3)

- **Diagnostic log toggle** — “Write diagnostic log file” defaults to **OFF**; no listener log writes unless the user opts in
- **Privacy note** in the log section: diagnostics only, no data collection/upload; tappable link to audit the open-source repo  
  https://github.com/Kenneth-Hudgins/unknown-blocker
- **2 MB auto-delete** of `notification_listener_log.txt` so the log cannot grow unbounded
- Status line reflects Logging ON/OFF and approximate size

### Unchanged

- Call blocking (contacts + allowed area codes)
- Sticky voicemail **notification** mute after blocked calls

### Version

- App version **1.2.6** (`versionCode` 9)

---

## [1.2.3] — 2026-08-04

Real-device validated on Galaxy S22+ (debug APK).

### Added

- **Sticky voicemail alert mute** after a blocked call:
  - Arms on block (no 15-minute timer)
  - Dismisses voicemail-looking notifications while armed
  - Disarms when a VM looks like a contact/allowed number (or a non-blocked caller rang more recently)
- Optional **Notification access** path (`NotificationListenerService`) for VM alert suppress
- **Notification listener log file** (absolute timestamps) with Open / Clear at bottom of UI
- Status lines: Hide VM alerts on/off, VM mute ACTIVE vs idle, notification access

### Changed

- Main screen fully **scrollable**; ActionBar removed so title/subtitle are not covered
- Broader Samsung dialer/phone VM detection + cancel/snooze retries
- Screened-number memory for VM text matching kept **24 hours** (mute itself is sticky, not timed)
- App version **1.2.3** (`versionCode` 6)

### Notes from device testing

- Call blocking (contacts + area codes) still works as in v1.1.0
- VM notification mute improved vs timed window; delayed spam VMs after 15+ minutes stay muted while armed
- Carrier voicemail **messages** may still exist — only the **notification** is suppressed
- After install/update: reboot and re-toggle Notification access if needed

### Known limitations

- Cannot stop carrier from accepting a voicemail; only best-effort hide of the alert
- Ambiguous VMs with no number may stay muted until an allowed signal
- Behavior varies by Samsung One UI / carrier Visual Voicemail

---

## [1.1.0] — 2026-07-30

Real-device validated (debug APK update install on physical phone).

### Added

- In-app **blocked history** list (number, call/SMS type, timestamp; capped at 200 entries)
- **Clear** and **Refresh** controls for blocked history
- **Status checklist** on the main screen:
  - Blocking on/off
  - Call screening role granted/missing
  - Contacts / SMS permissions
  - Currently allowed area codes
- **Allowed area codes** allowlist:
  - Add 3-digit NANP codes (e.g. `254` for work help desk)
  - Remove codes from the UI
  - Matching numbers ring while blocking stays ON, even if not in contacts
  - Supports common formats: `(254) 555-1234`, `254-555-1234`, `+12545551234`
- Shared **`AllowRules`** decision path used by call screening and SMS receiver  
  Order: **in contacts → allowed area code → block**
- **`BlockLog`**, **`AreaCodeAllowlist`**, **`ContactUtils`** helpers
- Docs: expanded README, Git refresher (`docs/GIT-REFRESHER.md`)

### Changed

- Modernized permission and call-screening role requests (`ActivityResultLauncher`)
- Main layout reworked for status + area codes + history under the toggle
- App version bumped to **1.1.0** (`versionCode` 2)

### Notes from device testing

- Updating over the previous install worked (same application id).
- A **phone restart** was required after install before call screening used the new logic reliably.
- After reboot: non-allowlisted unknown numbers went to voicemail; adding the caller’s area code allowed the next call through.

---

## [1.0.0] — 2026-06

Initial working build (pre-GitHub / golden phone build).

### Added

- Master **blocking toggle** (SharedPreferences)
- **`CallScreeningService`** — reject/silence calls from numbers not in contacts
- **`SmsBlockerReceiver`** — best-effort SMS broadcast handling (Android limits apply)
- Request **Call Screening** role and runtime permissions
- Basic Material / AppCompat UI with enable/disable switch

### Known limitations (still apply)

- SMS cannot be fully blocked without becoming the default SMS app
- Call screening requires the system Call Screening role
- Debug signing only (not Play Store release)
