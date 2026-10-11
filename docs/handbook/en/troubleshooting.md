# Troubleshooting

## The app crashes on start or sign-out (SIGABRT, `panic_in_cleanup`)

**Cause:** a core object was dropped outside the tokio runtime. The SQLite pool from matrix-sdk
needs the runtime when it closes.

**Fix:** drop every object in `pocky-ffi` with `RUNTIME.enter()` (see `Drop` for `PockyClient` and
`RoomTimeline`). New FFI objects holding a `Client` or a `Timeline` must do the same.

How to find the cause of a native crash:

```sh
adb logcat -d -b crash
```

Translate the addresses from the dump into symbols with `llvm-nm` from the NDK over
`target/aarch64-linux-android/android-dev/libpocky_ffi.so`.

## "Expect rustls-platform-verifier to be initialized"

Rust networking on Android is called before `TlsVerifier.init(context)`. Check that
`AndroidManifest.xml` has `android:name=".PockyApp"` and that `PockyApp.onCreate` calls the
initialisation. In the UI this shows up as the generic "Something went wrong" error.

## A Rust panic on Android is missing from logcat

Rust writes panics to stderr, which Android discards. Exceptions from the core reach Kotlin as
`PockyException.Other` with the panic message – log them temporarily (`Log.w`) and remove the log
once fixed. Errors from matrix-sdk and verification are forwarded to logcat under the `PockyCore`
tag.

## `queries overflow the depth limit!`

Deeply nested async types from matrix-sdk-ui. Fixed by `#![recursion_limit = "256"]` in `lib.rs`
(present in both crates). In tests `Box::pin(...)` around the future helps too.

## Kotlin: "Conflicting overloads: fun close()"

An FFI method is called `close`. UniFFI generates its own `close()`; rename or remove the method.

## The room list has no last-message previews

matrix-sdk computes the latest message lazily, and for rooms where the top item is not a message
(a reaction, a membership change) it has to fetch history. This requires:

- `with_enable_automatic_back_pagination(true)` in `build_client`,
- `latest_events().listen_to_room(...)` for every room (done by `watch_rooms`).

## Read receipts never show up in a chat

The timeline must be built with `track_read_marker_and_receipts(...)`; matrix-sdk-ui keeps receipt
tracking off by default. `open_timeline` turns it on for message-like events.

## Encrypted messages are not displayed

- **The device is not verified:** the room list shows a "This device is not verified" bar. Verify
  with the recovery key or the security phrase.
- **The device is verified, but old messages stay unreadable:** the "download the key on a
  decryption failure" strategy applies only when a message first arrives. Messages received before
  recovery are not retried. That is why `recover()` downloads keys from the backup for all
  encrypted rooms after success and `open_timeline` downloads them for the room being opened.
  matrix-sdk then decrypts the cached messages on its own.
- **The key is not in the backup:** the message was sent by a device that did not have backup
  turned on. Only emoji verification from that device helps, if it still has the key.

## The disk is full

`target/` can grow to tens of GB:

```sh
cargo clean
cd apps/android && ./gradlew clean
```

## Gradle: "Could not find org.rustls:rustls-platform-verifier:"

The version is read from `Cargo.lock`. Check that `rustls-platform-verifier-android` is there and
that the repository in `settings.gradle.kts` points to
`https://github.com/rustls/rustls-platform-verifier/raw/maven-archive/android-release-support/maven/`.

## Gradle: AAR metadata requires a higher compileSdk

New Compose versions need a newer platform. Raise `compileSdk` in both modules and install the
platform through the SDK Manager.

## Gradle daemon crashes in R8 (SIGSEGV)

After many hours of builds the daemon can crash during the release build. Stop it and build again:

```sh
cd apps/android && ./gradlew --stop
```

## Emulator: "Try out your stylus" over the screen

The emulator's system prompt when a text field gets focus. Not related to the app; close it with
Back.

## Time and days shown in English

The format follows the system language. The emulator is in English; with Czech as the system
language it shows "10:39" and "po".
