# Tests

## Offline tests

```sh
cargo test
cargo clippy --all-targets
```

They run without network and without credentials. They cover:

| Area | What is checked |
|---|---|
| Client | building with a server URL, deleting the store |
| Sign-in | rejecting empty credentials, offline session restore, sign-out without a session |
| Session | JSON round trip, broken JSON, invalid user_id, `Debug` without tokens |
| Previews | text, image, encrypted message, location, state event, text shortening |
| Timeline | HTML in text, media, location, hiding technical events, membership mapping |
| Media | `mxc://` and encrypted sources round trip, invalid source |
| Encryption | security phrase strength, phrase must differ from the account password |
| App lock | PIN format, trivial PINs refused, attempt counting and growing wait |
| FFI | dropping the client outside the runtime, session restore and export, empty store for a new client, sync state, sync requires sign-in, sign-out stops sync and deletes data, unknown room |

## Live tests against pokecio.com

They are marked `#[ignore]` and are started by hand. Tests that sign in always sign out at the end,
so no test device is left on the account. Credentials come only from environment variables; the
test accounts are kept in `~/.pockyapp/test.env` outside the repository.

```sh
source ~/.pockyapp/test.env
cargo test -p pocky-core -- --ignored --nocapture
```

| Test | What it does | Credentials |
|---|---|---|
| `live_wrong_password_is_rejected` | wrong password → `InvalidCredentials` | no |
| `live_login_and_logout` | server discovery, sign-in, sign-out, token no longer valid | yes |
| `live_room_list` | sync, room list (prints names and preview kinds) | yes |
| `live_encryption_status` | prints the account's encryption state (yes/no and counts only) | yes |
| `live_wrong_recovery_key_is_rejected` | fake recovery key → `InvalidRecoveryKey` | yes |
| `live_timeline` | opens an unencrypted room, loads a page of history (prints item kinds only) | yes |
| `live_user_verification` | two accounts verify each other with emoji | two accounts |
| `live_self_verification` | a new device of the same account gets verified | yes |
| `live_change_password_and_back` | changes the second account's password and restores it; a wrong current password is rejected | second account |

A single test: `cargo test -p pocky-core live_timeline -- --ignored --nocapture`.

matrix-sdk logs:

```sh
RUST_LOG=matrix_sdk::latest_events=debug,matrix_sdk::event_cache=info cargo test ...
```

Live test output never contains tokens or message text.

## Android

There are no automated Android tests yet. Manual check on the `Pokecio_Test` emulator
(Android 17, arm64):

1. sign-in with a wrong password → "Wrong name or password.",
2. sign-in → placeholder rows, then the room list,
3. app restart → stays signed in,
4. new message from another client → room on top with a badge,
5. opening the room → the badge disappears, scrolling up loads history,
6. sending a message → ○, then ✓,
7. airplane mode → "Offline" bar, the message waits and is sent after reconnecting,
8. sign-out → login screen, the device disappears from the server's session list.

Screens with keys, passwords and the app lock are protected against screenshots
(`FLAG_SECURE`); on the emulator they show up black and can be checked through the accessibility
tree instead:

```sh
adb exec-out screencap -p > screen.png
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
```
