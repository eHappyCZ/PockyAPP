# Architecture

Detailed handbook (Czech): [handbook/README.md](handbook/README.md).

- `core/pocky-core` – client logic on top of matrix-rust-sdk.
- `core/pocky-ffi` – UniFFI layer exposing the core to platform apps.
- `apps/android` – Kotlin + Jetpack Compose (`com.pokecio.app`).
- `apps/apple` – SwiftUI, planned.
- `design/tokens.json` – brand colors shared by all platforms.

## Bindings

`scripts/gen-bindings.sh` builds `pocky-ffi` and generates Kotlin bindings
into `apps/android/core-bindings` (package `com.pokecio.app.core`).

## Android build

`apps/android` is a Gradle project with two modules:

- `:core-bindings` – generated Kotlin API and `libpocky_ffi.so`.
  Its `preBuild` runs `scripts/build-android.sh` (cargo-ndk + bindgen).
- `:app` – Compose UI.

Defaults: ABI `arm64-v8a`, Cargo profile `android-dev`.
Override with `POCKY_ABIS` and `POCKY_PROFILE`.

Core work runs on a dedicated tokio runtime inside `pocky-ffi`, so objects
can be created and released from any thread.

## TLS on Android

reqwest uses `rustls-platform-verifier`, which validates certificates through
the Android trust store. It needs a JVM handle, so `PockyApp.onCreate` calls
`TlsVerifier.init(context)` (JNI entry in `core/pocky-ffi/src/android.rs`)
before any network request. The matching Kotlin component
`org.rustls:rustls-platform-verifier` is resolved from the version pinned in
`Cargo.lock`.

## Sessions and storage

- `createClient` always starts from an empty store; `restoreClient` reuses it.
- The session is an opaque JSON string for platforms. Android keeps it, and a
  random SQLite passphrase, encrypted with an AES-GCM key from Android Keystore
  (`data/SecureStore.kt`).
- `logout` revokes the device on the server and deletes the local store.

## Sync and room list

- `pocky-core` builds a `SyncService` (sliding sync, offline mode) and exposes
  `watch_rooms` / `watch_sync_state` as callback loops.
- Automatic back-pagination is enabled on the client so the latest message can
  be found when the newest event is a reaction, membership change or similar.
- `pocky-ffi` runs these loops as tasks and returns a `TaskHandle`; dropping or
  cancelling it stops the task.
- Android: `RoomListController` turns listeners into `StateFlow`s. Sync runs
  only while the activity is started (`LifecycleStartEffect`).

## Timeline

- `pocky-core::timeline` wraps the matrix-sdk-ui `Timeline` and maps items to
  plain types (`TimelineEntry`, `EventContent`, structured `StateChange`).
  Technical state events (power levels, join rules, ACLs, …) are filtered out.
- Updates reach platforms as diffs (`TimelineChange`), not full snapshots.
- Android: `RoomController` applies diffs to a list, `buildRows` groups
  consecutive messages by sender (5 min window), merges repeated state changes
  and marks own messages read up to the newest event with a foreign receipt.
- Navigation is a plain `openRoom` state in `MainViewModel` + `BackHandler`.

## Media

- Media is referenced by an opaque source string: a plain `mxc://` URI or the
  JSON of an encrypted `MediaSource`. `load_media(source, w, h)` asks the
  server for a thumbnail when possible and otherwise downloads (and decrypts)
  the file. Results are cached in the encrypted SQLite media store.
- Rooms without their own avatar fall back to the only hero's avatar
  (typical for direct chats).
- Android: `MediaLoader` decodes with downsampling, keeps an in-memory LRU
  (1/8 of heap) and de-duplicates parallel requests. It is provided through
  `LocalMediaLoader`.
