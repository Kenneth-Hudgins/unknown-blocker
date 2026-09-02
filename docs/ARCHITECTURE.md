# Architecture notes (maintainers)

Unknown Blocker is a small Android 10+ app. **Privacy-first**: no network permission,
no ads, no analytics. Core job is **call screening**; voicemail handling is optional
best-effort **notification** control only (cannot delete carrier voicemail).

## Process / entry points

| Component | Role |
|-----------|------|
| `MainActivity` | UI: toggles, status, area codes, blocked-call history by day, diagnostic log |
| `ScreeningService` | System **Call Screening** role — allow or reject+silence inbound calls |
| `SmsBlockerReceiver` | Best-effort SMS broadcast abort (message still lands in default SMS app) |
| `VoicemailNotificationListener` | Optional NLS: cancel (+ 6h snooze) voicemail-looking status-bar alerts |
| `FileProvider` | Share on-device diagnostic log file to another app |

## Shared prefs (`blocker_prefs`)

All durable state lives in one prefs file (see `BlockerSettings`, `BlockLog`, etc.):

- Master blocking on/off  
- Sticky VM mute armed flag + feature toggles (after-block / mute-all)  
- Probe logging on/off  
- Area codes, blocked history JSON, recent screened numbers JSON  
- First-open timestamp  

Use **`commit()`** (not only `apply()`) when the notification listener or screening
service must see the value **immediately** across processes.

## Call allow decision

```
blocking OFF → allow everyone
blocking ON  → contact OR allowlisted NANP area code → allow
             → else block + log + arm sticky VM mute (if that feature is on)
```

Implemented in `AllowRules` + `ContactUtils` + `AreaCodeAllowlist`.

## Voicemail notification modes

1. **Mute all VM alerts** (default off): while on, every **voicemail-looking**
   notification is dismissed; contact calls/texts do not re-enable.  
2. **Hide after blocked calls** (sticky): block **arms** mute; allowed-looking VM
   or a more recent allowed screen **disarms**.  

Detection must **not** match SMS/chat (`looksLikeVoicemail`). Messaging packages
are excluded. Dismiss = `cancelNotification` + `snoozeNotification` (6 hours) so
the same key is less likely to bounce immediately. OEMs may still post **new**
keys later — that is a platform limit.

## Signing / releases

See `docs/RELEASE-SIGNING.md`. Keystore is **outside** the repo. Debug and release
signatures differ — users must uninstall debug once before installing release.

## Branches

- `main` — last shipped stable when released  
- Feature work — e.g. history UI, mute-all, snooze  
- `docs/maintainer-comments` — documentation-only comments (no intentional behavior change)
