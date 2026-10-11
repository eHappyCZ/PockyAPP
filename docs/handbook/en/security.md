# Privacy and security

Privacy is the core value of the project: no analytics, no tracking, no selling of data. The app
talks only to the Pokecio servers.

## Development rules

- Passwords, tokens and keys never go into code, git or logs.
- Test credentials come only from environment variables (`POCKY_TEST_USER`,
  `POCKY_TEST_PASSWORD`).
- Enter passwords with `read -s` so they do not stay in the shell history:

  ```sh
  read -s "POCKY_TEST_PASSWORD?Password: " && export POCKY_TEST_PASSWORD; echo
  ```

## What is stored where (Android)

| Data | Location | Protection |
|---|---|---|
| Session (user_id, device_id, tokens) | `files/secure/session.bin` | AES-256-GCM, key in the Android Keystore |
| Database passphrase (32 random bytes) | `files/secure/db_key.bin` | AES-256-GCM, key in the Android Keystore |
| State, cryptographic keys, event cache, media | `files/matrix/*.sqlite3` | encrypted with the database passphrase |

- The Keystore key cannot be exported from the device.
- When a file cannot be decrypted (for example after a restore on another device), the app deletes
  it and asks for a new sign-in.
- `android:allowBackup="false"` – the data does not go into Android backups.

## The session in code

- In Rust, `Session` has its own `Debug` that leaves the tokens out (covered by a test).
- Kotlin receives the session only as an opaque JSON string. It has no named fields for the tokens,
  so it cannot print them by accident through `toString()`.

## Sign-out

`logout` in this order:

1. stops sync,
2. signs the device out on the server (the token stops working),
3. closes the client,
4. deletes the whole `files/matrix` folder including keys,
5. Android deletes `session.bin` and `db_key.bin`.

Local data is deleted even without network. The device then stays on the server until the user
removes it from another client.

## New sign-in

`createClient` always starts with an empty data folder. Old device keys are never carried over into
a new session.

## Network and TLS

- HTTPS through rustls.
- Certificates are verified by the Android system store (`rustls-platform-verifier`), just like in
  other apps. User-added certificates are respected too.

## Message encryption (E2EE)

Client settings (`encryption::settings()`):

| Option | Value | Why |
|---|---|---|
| `auto_enable_cross_signing` | no | must not overwrite an identity created in another client |
| `auto_enable_backups` | no | must not create a new backup instead of the existing one |
| `backup_download_strategy` | on decryption failure | a missing key is downloaded from the backup only when needed |

### Device verification with the recovery key

- The room list shows a "This device is not verified" bar when the device is not verified and the
  account has secret storage or another verified device.
- The "Device verification" screen accepts the recovery key (48 characters) or the security phrase.
  Spaces and line breaks in the key do not matter.
- The key is never stored or printed. The field uses the password keyboard, so the keyboard does
  not remember it.
- After success, matrix-sdk imports the cross-signing keys and the backup key, the device is
  verified and backup of new keys is enabled.
- The core then downloads keys from the backup for all encrypted rooms in the background (and again
  for a room when it is opened). Undecrypted messages decrypt on their own.

### Verification with another device (emoji)

- From the "Device verification" screen with "Verify with another device" (only when the account
  has another verified device), or by accepting a request from another device – the screen opens
  on its own.
- Self-verification requests are accepted only from your own account. When a verification is
  already running, another request is cancelled.
- Seven emoji are shown with Czech names according to the Matrix specification. The user confirms
  "They match" or "They don't match" (which cancels the verification as suspicious).
- When done, the other device sends the backup key. As soon as backup is enabled, the core
  downloads keys for all encrypted rooms on its own (`watch_encryption`).

### Key backup for accounts that do not have one

- Offered only to accounts without secret storage and without a backup on the server, and only when
  the device is verified or the account has no encryption identity yet. Otherwise a new backup
  could overwrite the setup made in another client.
- If the cross-signing identity is missing, it is created. The server asks for the account
  password for that (UIA); the password is used only for that request.
- The generated recovery key is shown once. Copying it marks it as sensitive clipboard content
  (Android 13+ does not show it in the preview).
- A **security phrase** is required (`with_passphrase`). On a new device entering it is enough; the
  key remains as a fallback.

### Security phrase

- The key is derived from the phrase on the device: PBKDF2-SHA512, 500,000 iterations, random
  salt. Only the salt and the iteration count go to the server, never the phrase or the key.
- Strength (`passphrase_strength` in the core, shared by Android and the web): fewer than 8
  characters is too short; without a special character (not a letter, digit or space, e.g.
  ! ? - _ #) it is refused; fewer than 5 distinct characters is weak; otherwise fair; from 16
  characters with 3 words or 3 character classes it is strong. The app recommends 16 or more
  characters: 3 to 4 words joined by symbols ("blue-hill-morning-coffee").
- The phrase must not equal the account password. The server receives the password on every
  sign-in, so a phrase derived from it would weaken the encryption for no reason.
- **Changing the phrase** (Me → Security, only on a verified device with backup enabled):
  `change_recovery_passphrase` through `recovery().reset_key()` creates new secret storage with a
  new phrase and a new key. The old key and phrase stop working; the message backup stays.

### Changing the account password

Me → Profile → Change account password. `change_password` sends the request first without
authentication and repeats it with the current password and the session from the server's answer
(UIA). The new password must have at least 8 characters and differ from the current one; the
server rejects a weak password too (`M_WEAK_PASSWORD`). A password rejected inside the UIA answer
(`auth_error`) maps to `InvalidCredentials`. The "Sign out other devices" option is off by default
(`logout_devices: false`). The live test `live_change_password_and_back` changes the password of
the second test account and restores it:

```sh
source ~/.pockyapp/test.env
cargo test -p pocky-core --lib live_change_password_and_back -- --ignored --nocapture
```

### Sign-out warning

Before sign-out the app checks whether this is the account's last device and whether recovery is
enabled. If signing out would mean losing history, a dialog warns and offers to set up the backup
first.

### Screenshot blocking

- Android `FLAG_SECURE` (`SecureWindow`). Screens with the key, the phrase, passwords,
  verification, the lock and account deletion (`Screen.sensitive`) and the sign-in screen are always
  protected.
- The "Block screenshots" option (Me → Security, `block_screenshots` in the device preferences)
  protects the whole app and is on by default.
- Compose dialogs (photo viewer, menus) use `SecureFlagPolicy.Inherit`, so they take the protection
  from the window.

### App lock

- Core (`app_lock.rs`): a 6-digit PIN, PBKDF2-SHA256 (100,000 iterations, random salt) in the
  client's encrypted store, never in readable form. Trivial PINs (000000, 123456, 654321…) are
  refused by the core (`pin_allowed`).
- After 5 wrong attempts a 30 s wait, doubled by every further attempt, at most one hour. The
  attempt count and the wait survive an app restart.
- Optional unlock with fingerprint or face (`androidx.biometric`, `BIOMETRIC_STRONG` only); success
  resets the attempt counter.
- The app locks on start and when it returns from the background after the chosen time (immediately,
  1, 5 or 15 minutes). Under the lock the app stays in memory, but the content is hidden even from
  screen readers (`clearAndSetSemantics`) and the window has `FLAG_SECURE`.
- With the lock on, notifications never show the message text.
- A forgotten PIN cannot be recovered; the only way out is signing out.

### Account deletion

Me → at the very bottom "Delete account". Confirmed with the password and by typing SMAZAT (the
Czech word for "delete"). The core `delete_account` calls `/account/deactivate` with `erase: true`
through UIA and then deletes the store on the device. Synapse disables the account, deletes the
profile, removes it from rooms and hides its messages from people who join later.
