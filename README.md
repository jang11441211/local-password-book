# Local Password Book

[中文说明](README-CN.md) | English

An offline Android password manager. Everything is encrypted locally with a master password, and the app requests **no network permission at all**.

## Features

- **Master password gate** — the vault can only be opened with the master password (optional fingerprint unlock as a convenience).
- **Encrypted storage** — entry name, account, password, sub-password and notes are stored as a single AES-256-GCM ciphertext.
- **Groups** — organize entries by owner or purpose (e.g. "Father", "Mother", "Work"); filter and group the list by them.
- **Sub-password** — each entry can store a second secret (e.g. login password + payment password).
- **Password generator** — one tap to generate a random password with mixed character classes.
- **Strength indicator** — strength is shown below every password field.
- **Auto lock** — the vault locks itself after a configurable idle timeout or when the app goes to the background.
- **Clipboard hygiene** — copied passwords are cleared from the clipboard after 30 seconds.
- **Screen protection** — `FLAG_SECURE` blocks screenshots and screen recording of the app.
- **No backup leakage** — backups and device-to-device transfer are disabled; data lives in `noBackupFilesDir`.

## Security design

```
master password ──PBKDF2-HMAC-SHA256 (600,000 iterations, 32-byte salt)──> KEK
                                                                           │
                                                       AES-256-GCM wrap    │
                                                                           ▼
random 256-bit DEK ────────────────────────────────────────────────> wrapped DEK
        │
        └── AES-256-GCM ──> vault payload (JSON: groups + entries)
```

- **DEK / KEK separation** — the payload is encrypted with a random data encryption key (DEK); the DEK is wrapped by a key encryption key (KEK) derived from the master password. Changing the master password only re-wraps the DEK.
- **AES-256-GCM** — 128-bit tag, a fresh random 12-byte IV per encryption, and AAD binding each ciphertext to its context.
- **Fingerprint (optional)** — an AES-256 key inside the Android Keystore wraps the same DEK. The key requires user authentication and stays usable for 30 seconds after a successful biometric check. The Keystore never exports the key; enrolling a new fingerprint permanently invalidates it, and the app then asks for the master password.
- **Atomic writes** — temp file + `fsync` + rename, so a crash cannot leave a half-written vault.
- **No network** — there is no `INTERNET` permission in the manifest; `usesCleartextTraffic` is false.
- **Memory hygiene** — the DEK lives only in memory, is zeroed on lock, and password `CharArray`s are wiped after use.

Threat model: this app protects data at rest on the device. It cannot defend against a compromised OS, a rooted device with a running hook, or a keylogger.

## Requirements

| Item | Version |
| --- | --- |
| Android | 8.0+ (API 26), target API 35 |
| JDK | 17 or 21 |
| Gradle | 8.9 (wrapper included) |
| Android Gradle Plugin | 8.7.3 |

## Build

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. For a release build, configure your own signing key in `app/build.gradle.kts` first.

## Usage

1. Install the APK and open the app.
2. Set a master password (at least 8 characters). It cannot be recovered — if you forget it, the vault is lost by design.
3. Add entries: name, account, password, optional sub-password, note, and a group.
4. Optionally enable fingerprint unlock in Settings. You can disable it at any time; the master password always works.

## Project structure

```
app/src/main/java/com/localvault/passwordbook/
├── MainActivity.kt              # single activity, lifecycle & secure flags
├── VaultViewModel.kt            # state machine, unlock/enable-biometric flows
├── data/                        # envelope, payload models, atomic file store
├── security/                    # PBKDF2 + AES-GCM, Keystore wrapping, generator
└── ui/                          # Compose screens (setup, lock, list, editor, detail, groups, settings)
```

## Known limitations

- Fingerprint unlock depends on the device Keystore; some OEM ROMs have quirks. If the bound key becomes invalid, unlock with the master password and enable fingerprint again.
- No cloud sync, no export, no password recovery.

## License

[MIT](LICENSE)
