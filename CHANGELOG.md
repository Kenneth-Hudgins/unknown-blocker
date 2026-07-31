# Changelog

All notable changes to **Unknown Blocker** are documented here.

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
