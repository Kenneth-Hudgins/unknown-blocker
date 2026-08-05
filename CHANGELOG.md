# Changelog

All notable changes to **Unknown Blocker** are documented here.

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
