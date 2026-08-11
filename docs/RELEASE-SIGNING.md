# Release signing (sideload APK)

This project can produce a **release-signed** APK that anyone can download from
GitHub Releases and install (sideload). Signing material is **not** in git.

## Where secrets live (this machine)

| Item | Path |
|------|------|
| Keystore | `C:\Users\Kenneth\AppData\Local\unknown-blocker-signing\unknown-blocker-release.jks` |
| Passwords | `C:\Users\Kenneth\AppData\Local\unknown-blocker-signing\PASSWORDS.txt` |
| Gradle props | `C:\Users\Kenneth\AppData\Local\unknown-blocker-signing\keystore.properties` |
| Project copy (gitignored) | `keystore.properties` at repo root |

**Back up the entire `unknown-blocker-signing` folder** (USB / password manager / encrypted drive).  
If you lose the keystore or passwords, users must **uninstall** before installing a newly signed app.

## Build signed release APK

From the project root (with `keystore.properties` present):

```bat
gradlew.bat assembleRelease
```

Output:

```
app\build\outputs\apk\release\app-release.apk
```

## First install vs debug builds

Debug APKs use Android’s **debug** keystore. Release uses **your** keystore.

- Installing release **over** an existing debug install usually **fails** (signature mismatch).
- Fix: **uninstall** the debug app once, then install `app-release.apk`.
- Later release updates (same key) install as normal updates.

## Publish on GitHub

1. Tag a version (e.g. `v1.2.7`).
2. Create a GitHub Release.
3. Attach `app-release.apk` (and optional `.sha256`).
4. In notes: Android 10+, enable Install unknown apps, Call Screening role, reboot tip.

## Never commit

- `*.jks` / `*.keystore`
- `keystore.properties`
- `PASSWORDS.txt`
