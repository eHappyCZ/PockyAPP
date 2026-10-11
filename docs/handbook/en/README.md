# PockyAPP – developer handbook

[Česky](../README.md)

Native Matrix client of the Pokecio community. The homeserver is Synapse on `matrix.pokecio.com`;
users sign in with the server name `pokecio.com`.

The shared core is written in Rust on top of `matrix-rust-sdk`. Native platforms call it through
UniFFI, the browser through WebAssembly. The Android app (Kotlin + Jetpack Compose) and the web app
(Svelte + TypeScript) are done; iOS, macOS, Windows and Linux are planned.

## Contents

| Document | What it covers |
|---|---|
| [setup.md](setup.md) | What to install, how to build the core and the Android app |
| [architecture.md](architecture.md) | Layers, data flow and the main decisions |
| [core.md](core.md) | The Rust core `pocky-core` – modules and functions |
| [ffi.md](ffi.md) | The API the core offers to platforms (Kotlin) |
| [android.md](android.md) | Android app structure, screens, state |
| [security.md](security.md) | Privacy, storage, keys, app lock, sign-out |
| [testing.md](testing.md) | Offline tests and live tests against the server |
| [troubleshooting.md](troubleshooting.md) | Known problems and their fixes |
| [roadmap.md](roadmap.md) | What is done, what is missing and what comes next |

## Status at a glance

Working on Android:

- sign-in, session restore, sign-out, encrypted local storage,
- sliding sync, chat list with previews, filters and statuses,
- chats with replies, edits, reactions, read receipts, message info and forwarding,
- photos, videos, files, voice messages, GIFs, chat album,
- end-to-end encryption: device and user verification, key backup with a security phrase,
  identity reset, trust shields,
- notifications decrypted on the device, live location, maps, tablets,
- app lock, screenshot blocking, password change, account deletion, in-app updates.

The web app covers reading and writing, verification, presence, location and files; the gaps
compared to Android are listed in [roadmap.md](roadmap.md).

## Quick start

```sh
rustup default stable
cargo build && cargo test

cd apps/android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

Details in [setup.md](setup.md).
