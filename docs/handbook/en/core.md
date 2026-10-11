# Rust core – `pocky-core`

Path: `core/pocky-core/src/`. The crate has no dependency on UniFFI.

## Modules

| File | Content |
|---|---|
| `lib.rs` | public exports, `version()` |
| `client.rs` | building the client, `SERVER_NAME`, `delete_store` |
| `auth.rs` | sign-in, session restore, sign-out, display name, account deletion |
| `session.rs` | the `Session` type and its JSON |
| `error.rs` | `CoreError` |
| `sync.rs` | `SyncService`, sync state |
| `room_info.rs` | room details, members and roles, edits, invites, removals |
| `rooms.rs` | room list, message previews |
| `timeline.rs` | room timeline, sending, replies, edits, reactions, deletions, photos, read state, forwarding |
| `media.rs` | downloading and decrypting media |
| `people.rs` | people search, new direct chat, new group, invites |
| `picture.rs` | preparing a photo for sending (rotation, resizing, no metadata) |
| `preferences.rs` | device preferences from the onboarding |
| `push.rs` | push registration on Synapse, the message for a notification |
| `profile.rs` | changing the display name and the profile photo |
| `status.rs` | own status (text + expiry) in account data, published through presence |
| `encryption.rs` | encryption state, recovery, key backup, security phrase, new identity, password change |
| `app_lock.rs` | app lock PIN, attempts and waiting |
| `changelog.rs` | new features and fixes of each version, bundled from `core/pocky-core/changelog.json` |
| `trust.rs` | verifying other people, changes of their encryption identity |
| `verification.rs` | emoji verification: own devices and other people |
| `presence.rs` | online state: sharing your own, fetching others' |
| `typing.rs` | "typing…": observing and sending |

## client.rs

```rust
pub const SERVER_NAME: &str = "pokecio.com";

pub enum Server<'a> {
    Discover(&'a str),   // discovery through .well-known
    Url(&'a str),        // direct homeserver URL
}

pub async fn build_client(server: Server<'_>, data_dir: &Path, passphrase: Option<&str>) -> Result<Client>;
pub async fn delete_store(data_dir: &Path) -> Result<()>;
```

The client has an SQLite store (state, crypto, event cache, media) encrypted with `passphrase`, and
automatic history back-pagination enabled.

## auth.rs

```rust
pub async fn login(client, username, password, device_name) -> Result<Session>;
pub async fn restore_session(client, session) -> Result<()>;
pub async fn restore_client(session, data_dir, passphrase) -> Result<Client>;
pub async fn logout(client) -> Result<()>;
pub async fn delete_account(client, password) -> Result<()>;
pub fn current_session(client) -> Option<Session>;
```

- `login` accepts both `karel` and `@karel:pokecio.com`. An empty name or password returns
  `InvalidCredentials` without asking the server.
- `logout` invalidates the device on the server. It does **not** delete local data – the FFI layer
  does that through `delete_store`.
- `delete_account` calls `/account/deactivate` with `erase: true`, first without authentication and
  then with the password and the UIA session from the server's answer.

## session.rs

```rust
pub struct Session {
    pub homeserver_url: String,
    pub user_id: String,
    pub device_id: String,
    pub access_token: String,
    pub refresh_token: Option<String>,
}
```

The `Debug` output leaves the tokens out. `to_json` / `from_json` hand the session to the platform,
which only encrypts and stores the JSON.

## error.rs

| Variant | When |
|---|---|
| `InvalidCredentials` | wrong name/password (`M_FORBIDDEN`, also inside a UIA answer) |
| `UserDeactivated` | the account is deactivated |
| `RateLimited` | too many attempts |
| `ServerNotFound` | `.well-known` or the server name cannot be resolved |
| `Network` | network error |
| `NotLoggedIn` | the operation requires sign-in |
| `InvalidSession` | the stored session is broken |
| `InvalidRecoveryKey` | wrong recovery key or security phrase |
| `WeakPassphrase` | the security phrase is too weak or equals the account password |
| `WeakPassword` | the new password is too short or the server rejected it (`M_WEAK_PASSWORD`) |
| `InvalidPin` | the app lock PIN is not six digits or is trivial |
| `Sync`, `Timeline`, `Media` | errors of the individual parts |
| `Storage` | file system error |
| `ClientBuild`, `Matrix` | other matrix-sdk errors |

## sync.rs

```rust
pub enum SyncState { Idle, Running, Offline, Error, Terminated }

pub async fn create_sync_service(client) -> Result<SyncService>;
pub async fn watch_sync_state(service, on_state: impl FnMut(SyncState));
```

`create_sync_service` enables the event cache and builds a `SyncService` with offline mode.
`watch_sync_state` first sends the current state, then every change.

## rooms.rs

```rust
pub struct RoomSummary {
    pub id, name, avatar_url, last_message: Option<MessagePreview>,
    pub timestamp_ms, unread_messages, unread_mentions,
    pub is_encrypted, is_direct, is_invite,
}

pub async fn watch_rooms(service, on_update: impl FnMut(Vec<RoomSummary>)) -> Result<()>;
```

- Leaves out rooms the user has left and old versions of upgraded rooms.
- Turns on latest-message tracking for every room (`latest_events`).
- Name: the stored one, otherwise computed from the members.
- Avatar: the room picture, otherwise the avatar of the single "hero" (direct chats).
- Preview (`PreviewKind`): text and emote shortened to the first line and 200 characters, image,
  video, audio, file, location (live included), sticker, poll, encrypted message, invite.

## timeline.rs

```rust
pub async fn open_timeline(client, sync: Option<&SyncService>, room_id) -> Result<Timeline>;
pub async fn watch_timeline(timeline, on_diffs: impl FnMut(Vec<TimelineDiff>));
pub async fn paginate_backwards(timeline) -> Result<bool>;   // true = start of the room
pub async fn send_text(timeline, text) -> Result<()>;         // Markdown → HTML
pub async fn mark_as_read(timeline) -> Result<()>;
pub async fn forward_message(client, from_room, event_id, to_room) -> Result<()>;
```

- `open_timeline` subscribes the room in sliding sync for a full sync and turns on read receipt
  tracking (`track_read_marker_and_receipts(MessageLikeEvents)`; off by default in matrix-sdk-ui).
- The filter hides technical state events: power levels, join rules, history visibility, guest
  access, aliases, ACLs, pinned events, third-party invites, policy rules and space events.
- A history page is 30 events (`PAGE_SIZE`).
- `forward_message` loads the original event and sends the same content to another room without
  the reply relation. Encrypted attachments are not re-uploaded; the same encrypted file with its
  key is sent.

### Item types

```
TimelineEntry { id, kind }
EntryKind: DateDivider | ReadMarker | TimelineStart | Event(EventEntry)

EventEntry: event_id, sender_id, sender_name, sender_avatar, timestamp_ms,
            is_own, is_edited, content, reactions, in_reply_to, send_state, read_by

EventContent: Text / Notice / Emote { body, html },
              Image { caption, source, original, width, height, name, size, mime },
              Video { caption, source, thumbnail, width, height, duration_ms, size, name, mime },
              Audio, File, Location, LiveLocation, Sticker, Poll,
              Redacted, UnableToDecrypt, State(StateChange), Other

StateChange: Membership { user_id, name, change }, DisplayName, Avatar,
             RoomName, RoomTopic, RoomAvatar, RoomCreated, EncryptionEnabled, Other
```

matrix-sdk-ui merges live location sharing (`beacon_info` + `beacon`) into one item.

## Message actions (timeline.rs)

```rust
pub async fn send_reply(timeline, text, in_reply_to: &str /* event id */) -> Result<()>;
pub async fn edit_text(timeline, item_key, text) -> Result<()>;
pub async fn toggle_reaction(timeline, item_key, key) -> Result<bool>; // true = added
pub async fn redact(timeline, item_key) -> Result<()>;
pub async fn send_image(timeline, bytes, caption: Option<&str>, full_quality) -> Result<()>;
pub async fn send_voice(timeline, bytes, mime, duration_ms, levels: &[f32]) -> Result<()>;
pub async fn send_file(timeline, bytes, filename, mime) -> Result<()>;
pub async fn send_video(timeline, VideoClip, caption) -> Result<()>;   // the thumbnail goes through prepare_image
pub async fn max_upload_size(client) -> Result<u64>;
```

- `EventEntry.item_key` identifies a message even before it is sent: a sent one = event id (`$…`),
  a local echo = `txn:<transaction id>`.
- `EventEntry` also carries `can_reply`, `can_edit` (according to matrix-sdk) and `can_redact`
  (only own and not yet redacted messages).
- Replies and edits send the text as Markdown, just like `send_text`.
- A voice message follows MSC3245: `m.audio` with `org.matrix.msc3245.voice` and
  `org.matrix.msc1767.audio` (duration + waveform 0–1024). `waveform()` turns raw volume levels into
  100 bars (maximum per segment, normalised to the loudest). `EventContent::Audio` carries
  `source`, `duration_ms`, `waveform` and `voice`.
- Files and videos check the server limit before uploading → `CoreError::FileTooLarge`. The file
  name loses any path. `EventContent::File` has `source`, `size`, `mime`.

## devices.rs

```rust
pub async fn list_devices(client) -> Result<Vec<DeviceInfo>>;  // current first, then by activity
pub async fn sign_out_device(client, device_id, password) -> Result<()>;
DeviceInfo { device_id, name, last_seen_ms, is_current, verified }
```

Sign-out first tries without authentication; when the server asks for UIAA, it sends the password
(a wrong password → `InvalidCredentials`). The current device cannot be signed out this way – that
is what `logout` is for.

## people.rs

```rust
pub async fn search_people(client, term) -> Result<Vec<Person>>;
pub async fn open_direct_chat(client, user_id) -> Result<String>;          // room id
pub async fn create_group(client, name, members: &[String]) -> Result<String>;
pub async fn invite_info(client, room_id) -> Result<InviteInfo>;
pub async fn accept_invite(client, room_id) -> Result<()>;
pub async fn decline_invite(client, room_id) -> Result<()>;
```

- Search: the Synapse user directory (max. 20) + a direct profile lookup when the text looks like a
  user name (`karel` → `@karel:<own server>`, or a full `@…:…`). By default Synapse's directory only
  finds people who share a room. It never returns yourself.
- Direct chat: first looks for an existing one (`m.direct`, then also unmarked 1:1 rooms), otherwise
  `create_dm` (encrypted).
- Group: private (`PrivateChat`), encrypted from the start, with invites.

## picture.rs

```rust
pub fn prepare_image(bytes: &[u8], full_quality: bool) -> Result<PreparedImage>;
PreparedImage { bytes, mime, width, height }
```

- The picture is rotated by its EXIF orientation and re-encoded → **EXIF (location, camera) is
  gone**.
- Without `full_quality` the longer side is reduced to `MAX_SIDE` (2048 px).
- A PNG with transparency stays PNG; everything else → JPEG quality 85. GIFs and animated WebP are
  sent unchanged (to keep the animation).
- A format the `image` crate does not know (e.g. HEIC) returns `CoreError::Media`; Android then
  converts it to JPEG with `ImageDecoder` and tries again.

## media.rs

```rust
pub async fn load_media(client, source: &str, width: u32, height: u32) -> Result<Vec<u8>>;
```

- `source` is `mxc://…` or the JSON of an encrypted `MediaSource`.
- Unencrypted media + a non-zero size → a thumbnail from the server.
- Encrypted media → the whole file, decrypted.
- The cache lives in the encrypted matrix-sdk store.

## preferences.rs

```rust
pub async fn load_preferences(client) -> Result<Preferences>;
pub async fn save_preferences(client, &Preferences) -> Result<()>;

Preferences { onboarding_done, chat_style, notification_preview, location_precision,
              share_presence (default false), share_typing (default true),
              send_read_receipts (default true), block_screenshots (default true) }
ChatStyle: Classic (default), Aura
NotificationPreview: Always, WhenUnlocked (default), Never
LocationPrecision: Exact, Neighborhood (~500 m), City (~5 km, default)
```

- The preferences live **on the device only**: they are stored in the encrypted matrix-sdk SQLite
  database (`custom value` under the `pocky.preferences` key) and never go to the server.
- Sign-out and a new sign-in delete the database, so the onboarding shows again after every new
  sign-in.
- Missing or broken data → defaults.

`auth::display_name` returns the display name from the server. When it is not set or the server
does not answer, it returns the part of the ID before the colon (`@jana:pokecio.com` → `jana`).

## push.rs

```rust
pub const PUSH_GATEWAY: &str; // https://push.pokecio.com/_matrix/push/v1/notify
pub const PUSH_APP_ID: &str;  // com.pokecio.app.android
pub async fn register_pusher(client, push_key, device_name, lang) -> Result<()>;
pub async fn unregister_pusher(client, push_key) -> Result<()>;
pub async fn fetch_notification(client, sync: Arc<SyncService>, room_id, event_id)
    -> Result<Option<PushNotification>>;
```

- The pusher uses the `event_id_only` format – neither the gateway nor Firebase sees the content.
- `fetch_notification` uses `NotificationClient` from matrix-sdk-ui (`SingleProcess`, Android wakes
  the app's main process); it downloads and decrypts the message. A message filtered by push rules,
  from an ignored user or redacted → `None`.

## profile.rs

```rust
pub async fn set_display_name(client, name) -> Result<String>; // returns the cleaned name
pub async fn set_avatar(client, bytes) -> Result<String>;       // mxc:// URL
pub async fn remove_avatar(client) -> Result<()>;
```

- Name: surrounding spaces removed, repeated spaces merged, 1–64 characters, otherwise
  `CoreError::InvalidName`.
- The photo goes through `picture::prepare_avatar`: rotation by EXIF, a centred square crop,
  resized to 512 × 512 (smaller ones are not enlarged), JPEG without metadata.

## status.rs

```rust
pub async fn set_status(client, text, until_ms: Option<u64>) -> Result<OwnStatus>;
pub async fn clear_status(client) -> Result<()>;
pub async fn own_status(client) -> Result<Option<OwnStatus>>;
pub async fn expire_status(client, now_ms) -> Result<bool>;
pub async fn watch_own_status(client, on_update) -> Result<()>;
```

- The text (1–60 characters) goes as `status_msg` in Matrix presence – people sharing a room see
  it, in other clients too. Clearing sends an empty text.
- The status (text + `until_ms`) lives in the `com.pokecio.status` account data, so all own
  devices see it. A cleared status is the empty content `{}` (account data cannot be deleted).
- `own_status` returns only a valid status; an expired one counts as none, so the next presence
  update (e.g. changing "Show that I'm online") does not send the old text.
- `expire_status` asks the server before deleting, so it does not delete a newer status from
  another device when sync is not running here.
- `create_sync_service` also migrates the old local status to the server (in the background; on
  failure it is retried on the next start).
- `watch_own_status` first migrates the old local status (`pocky.status`) to the server if there is
  none there, then reports the current status and changes from other devices.
- `fetch_presence` always returns `status`; the online state only when sharing your own.

## encryption.rs: security phrase, new identity, password

```rust
pub fn passphrase_strength(passphrase) -> PassphraseStrength;  // TooShort, NoSymbol, Weak, Fair, Strong
pub async fn enable_recovery(client, password, passphrase) -> Result<String>;
pub async fn reset_identity(client, password, passphrase) -> Result<String>;
pub async fn change_recovery_passphrase(client, passphrase) -> Result<String>;
pub async fn change_password(client, current, new_password, sign_out_others) -> Result<()>;
```

- Every function that creates a backup requires a security phrase (at least 8 characters with a
  special character, see [security.md](security.md)) and returns a new recovery key as a fallback.
- `reset_identity` is for the "I have neither the key nor another device" case. It deletes the key
  backup on the server, disables secret storage, creates new cross-signing keys (confirmed with the
  password through UIAA) and creates a new backup.
- The backup is deleted before the password is checked (that is how matrix-sdk does it). With a
  wrong password it returns `InvalidCredentials` and the reset can safely be repeated.
- A server with OAuth (approval in the browser) is not supported yet.
- `change_recovery_passphrase` uses `recovery().reset_key()` and works only when recovery is
  enabled on this device.

## app_lock.rs

```rust
pub fn pin_allowed(pin) -> bool;
pub async fn app_lock_settings(client) -> Result<AppLockSettings>;
pub async fn enable_app_lock(client, pin, biometrics, timeout_seconds) -> Result<()>;
pub async fn update_app_lock(client, biometrics, timeout_seconds) -> Result<()>;
pub async fn unlock_app(client, pin) -> Result<UnlockResult>;      // Unlocked, WrongPin, Blocked
pub async fn unlock_app_with_biometrics(client) -> Result<()>;
pub async fn disable_app_lock(client, pin) -> Result<UnlockResult>;
```

- The PIN hash (PBKDF2-SHA256, random salt) and the attempt counter live in the encrypted store
  under `pocky.app_lock`.
- 5 free attempts, then a 30 s wait that doubles with every further wrong PIN, at most one hour.

## trust.rs: verifying people

```rust
pub async fn user_trust(client, user_id) -> Result<TrustLevel>;
pub async fn acknowledge_identity(client, user_id) -> Result<()>;
pub async fn watch_identity_warnings(client, room_id, on_update) -> Result<()>;
pub async fn user_display_name(client, user_id) -> Option<String>;
```

- `TrustLevel`: `NoIdentity`, `Unverified`, `Verified`, `VerificationBroken` (a verified person
  whose identity changed afterwards).
- `watch_identity_warnings` follows identity changes of chat members through
  `subscribe_to_identity_status_changes`. It reports the people whose identity changed
  (`was_verified` says whether they were verified before).
- `acknowledge_identity` is "I understand": for an unverified person it remembers the new identity
  (`pin`), for a previously verified one it withdraws the verification requirement
  (`withdraw_verification`).
- `VerificationFlow::request_user` starts verifying another person. The request goes to the shared
  1:1 chat (the SDK creates it when missing). `watch_verification_requests` also catches incoming
  requests in chats, not only requests from own devices.

### State messages and the change history (timeline.rs)

- The main timeline (`open_timeline`) shows only important state events (`in_chat`): encryption
  enabled, room name and picture, and location sharing.
- Joins, leaves, invites, member name and picture changes, the topic, room creation and other state
  events are shown by `open_history`: a second timeline with state events only. On the platform it
  is the same `RoomTimeline` object (pagination, change subscription).

### Chat album (timeline.rs)

- `open_media` is a separate timeline with photos, videos, audio and files only (plus encrypted
  events that may decrypt into media).
- For photos `source` is the thumbnail (`thumbnail_source` if the sender attached one, otherwise
  the original) and `original` is always the full photo. Bubbles and the album load only the
  thumbnail; the viewer fetches the original.

### Read receipts (timeline.rs)

- `EventEntry.read_by: Vec<ReadReceipt>` (`user_id`, `timestamp_ms`) without your own receipt.
- The `send_read_receipts` preference (on by default). When it is off, `mark_as_read` sends
  `m.read.private`: own devices stay in sync, others see nothing.

### Shields on messages (timeline.rs)

`EventEntry.shield: Option<Shield>` with `danger` (red / grey) and `reason`. It comes from
`get_shield(false)`, the mild variant: messages from unverified people have no shield; only real
problems get one (an unverified or unknown device, a changed identity, an unencrypted message in an
encrypted chat, a spoofed sender).

## changelog.rs

```rust
pub fn changelog(lang, up_to_version_code) -> Vec<Release>;
```

- The single source is `core/pocky-core/changelog.json`. The website reads it (the Changelog
  page, the update manifest) and the core bundles it with `include_str!`, so the app has it offline.
- Every version has `new` (new features) and `fixed` (fixes), both in `cs` and `en`.
- It returns versions from the newest and only up to `up_to_version_code`, so an entry prepared for
  the next version does not show in an older app.

## presence.rs

```rust
pub async fn apply_presence_preference(client) -> Result<()>;
pub async fn fetch_presence(client, user_id: &str) -> Result<UserPresence>;

UserPresence { user_id, presence: Online | Unavailable | Offline | Unknown, last_active_ms }
```

- matrix-sdk sends your own presence with every sync (default "online"). `create_sync_service`
  therefore calls `apply_presence_preference` first: with sharing off, sync reports `offline`.
- Changing `share_presence` in `save_preferences` sends the state to the server immediately.
- Sliding sync does not deliver other people's presence, so `fetch_presence` calls
  `GET /presence/{userId}/status` for a specific person.
- **Mutuality:** without sharing your own, `fetch_presence` always returns `Unknown`.
- A server error (e.g. presence disabled on Synapse) → `Unknown`.
- "Online" but inactive for more than 10 minutes → `Unavailable`.

## typing.rs

```rust
pub async fn watch_typing(client, on_update: impl FnMut(room_id: String, Vec<TypingUser>)) -> Result<()>;
pub async fn send_typing(client, room_id: &str, typing: bool) -> Result<()>;

TypingUser { user_id, name: Option<String> }
```

- One `m.typing` handler for all rooms (the sliding sync typing extension is enabled by
  `RoomListService`); your own user is filtered out.
- **Mutuality:** with `share_typing = false`, `watch_typing` ends immediately and `send_typing`
  sends nothing.
- matrix-sdk also throttles sending (`typing_notice`).

## Room actions (rooms.rs)

```rust
pub async fn set_favourite(client, room_id, favourite: bool) -> Result<()>; // the m.favourite tag
pub async fn set_muted(client, room_id, muted: bool) -> Result<()>;         // the Mute notification rule
pub async fn mark_room_read(client, room_id) -> Result<()>;                 // receipt on the last message
pub async fn leave_room(client, room_id) -> Result<()>;
```

`RoomSummary` also has `is_favourite`, `is_muted`; `MessagePreview.delivery`
(`Sending | Failed | Sent | Read`) only for your own last message – `Read` when anyone else has a
read receipt on it. `watch_rooms` sorts pinned rooms to the top and recomputes the list when
notification rules change too.

## room_info.rs

```rust
pub async fn room_details(client, room_id) -> Result<RoomDetails>;
pub async fn set_room_name / set_room_topic / set_room_avatar(client, room_id, …);
pub async fn invite_people(client, room_id, user_ids) -> Result<()>;
pub async fn remove_member(client, room_id, user_id) -> Result<()>;
```

- `RoomDetails`: name, topic, picture (for 1:1 the other person's photo), encryption, members
  (`MemberInfo` with the role Admin ≥ 100 / Moderator ≥ 50 / Member, invited, `can_remove`),
  permissions `can_edit_name/topic/avatar`, `can_invite` according to power levels.
- Member order: joined before invited, admins first, then by name.
- The group picture goes through `prepare_avatar` (a 512 px square, no metadata).

## Direct chats without `m.direct` (rooms.rs)

`is_direct` also applies to a room that is not marked in `m.direct` but has no name of its own, at
most 2 active members and one "hero". `direct_user_id` is then that hero. Thanks to that, round
avatars, the Groups filter and online state work for older conversations too.

## location.rs

```rust
pub fn blur(point, precision) -> GeoPoint;
pub fn geo_uri(point, precision) -> String;
pub fn parse_geo_uri(uri) -> Option<GeoPoint>;
pub async fn send_location(timeline, point, precision) -> Result<()>;
pub async fn start_live_location(client, room_id, LiveDuration) -> Result<()>;
pub async fn send_beacon(client, room_id, point, precision) -> Result<()>;
pub async fn stop_live_location(client, room_id) -> Result<()>;
```

- Precision comes from `LocationPrecision`: exact, neighbourhood (500 m grid), city (5 km grid).
  The point moves to the centre of its cell, so nearby places give the same result and the real
  location cannot be computed from it. `u=` in the `geo:` URI is the cell size; the number of
  decimal places is reduced (6 / 4 / 3).
- Live location (MSC3489): `beacon_info` is a state event and is **not encrypted**. The server can
  therefore see that and for how long someone shares, so no description is put into it. The
  coordinates go in `m.beacon`, which is encrypted.
- Sharing duration: 15 min, 1 h, 8 h (`LiveDuration`).
- `should_send_beacon(last, fix, precision, rooms)` decides whether to send a new position (so as
  not to flood Synapse, whose default limit is 1 message / 5 s):
  - at least 5 s × the number of shared chats between two sends;
  - exact location: in a car (≥ 5 m/s) every 5 s, walking (≥ 0.7 m/s) every 10 s, otherwise 60 s,
    and only after moving more than the GPS accuracy (min. 10 m);
  - without movement once every 3 min (a sign that sharing is running);
  - approximate location: only when moving into another grid cell, otherwise after 5 min.
- After expiry or stop, `send_beacon` returns `CoreError::LiveLocationEnded`; the app then stops
  tracking the location. `stop_live_location` is safe to call repeatedly.
- The description of a received location is not shown if it contains only a `geo:` URI (that is
  how both PockyAPP and Element send points).

## live_location.rs

```rust
pub async fn own_live_shares(client) -> Vec<OwnLiveShare>;
pub async fn watch_live_locations(client, on_update: FnMut(Vec<LiveShare>));
```

- `own_live_shares`: in which rooms I have a live `beacon_info` and until when (`ts + timeout`). The
  app uses it to resume sharing after a restart.
- `watch_live_locations`: a `LiveLocationsObserver` for every joined room; every 30 s it adds new
  ones and drops left ones. It sends the list of active shares (name, photo, last `geo:` URI, time,
  end, `is_own`). Expired shares drop out at the next tick at the latest. The same list is not sent
  again.

## A note on `recursion_limit`

Async types from matrix-sdk-ui are deeply nested, so both `pocky-core` and `pocky-ffi` have
`#![recursion_limit = "256"]` in `lib.rs`. matrix-sdk does the same.
