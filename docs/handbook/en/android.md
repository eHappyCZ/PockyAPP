# Android app

Path: `apps/android/`. Package and `applicationId`: `com.pokecio.app`.

## Modules

| Module | Content |
|---|---|
| `:core-bindings` | the generated Kotlin API, `libpocky_ffi.so`, `TlsVerifier`, JNA |
| `:app` | the Compose app |

`:core-bindings` also pulls in the Android part of `org.rustls:rustls-platform-verifier`. It reads
the version from `Cargo.lock` (the `rustls-platform-verifier-android` package), so it always
matches the Rust side.

## `:app` structure

```
com.pokecio.app
  PockyApp.kt               Application, TLS initialisation
  MainActivity.kt           Compose, screen switching, foreground/background (FragmentActivity)
  MainViewModel.kt          sign-in, restore, sign-out, account deletion, the open room
  data/
    SecureStore.kt          AES-GCM file encryption with a Keystore key
    SessionStore.kt         session + database passphrase
    ClientHolder.kt         a single PockyClient for the whole process (UI and push)
  push/
    PushRegistrar.kt        Firebase token → pusher on Synapse
    PushService.kt          receives the "bell" from Firebase, fetches the message, notification
    Notifier.kt             channels, notification look according to the preferences
    NotificationActions.kt  Reply / Mark as read straight from the notification
    MessageCheckWorker.kt   fallback without Google services: a check every 15 min
  rooms/
    RoomListController.kt   room list and sync state as StateFlow
    RoomController.kt       the open timeline, history, sending, read state, forwarding
    RoomMediaController.kt  the chat album timeline
  encryption/
    EncryptionController.kt encryption state, recovery, emoji verification, backup, phrase, password
  lock/
    AppLockController.kt    app lock state, background timing, PIN checks
    Biometrics.kt           fingerprint / face prompt
  preferences/
    PreferencesController.kt device preferences and the display name as StateFlow
  presence/
    PresenceController.kt   who is typing where, online state of followed people
  media/
    MediaLoader.kt          loading and caching images
    MatrixImage.kt          rememberMatrixImage, MatrixImage
    AnimatedImage.kt        animated GIF / WebP playback
    MediaSaver.kt           saving to the gallery and sharing
    MediaDownloads.kt       bulk downloads with progress
  update/                   in-app updates
  ui/
    theme/                  colours from design/tokens.json, Material 3 theme, PockyIcons
    components/             shared parts (GradientButton, SecureWindow, ComingSoon)
    login/                  sign-in, animated logo, fields
    onboarding/             onboarding after sign-in
    rooms/                  room list, avatar, preview and time formatting
    room/                   room screen, rows, texts, bubble look and aura, album
    encryption/             recovery, emoji verification, backup, phrase, password, account deletion
    lock/                   lock screen, PIN pad, lock settings
    home/                   home, Me tab (profile and settings), devices, release notes
```

## App state

```kotlin
sealed interface AppState {
    data object Loading
    data class LoggedOut(busy, error)
    data class LoggedIn(userId, rooms: RoomListController, media: MediaLoader,
                        downloads: MediaDownloads, lock: AppLockController,
                        encryption: EncryptionController,
                        preferences: PreferencesController, …,
                        openRoom: RoomController?, overlay, logoutPrompt, busy)
}
```

- `MainViewModel` owns the `PockyClient` and all controllers and closes them on sign-out or
  destruction.
- Navigation (the first valid one wins): a running verification → recovery / backup → preferences
  loading (empty background) → onboarding (`!onboardingDone`) → room (`openRoom != null`) → list.
  Back is handled by `BackHandler`.
- `MainActivity` calls `onForeground` / `onBackground` through `LifecycleStartEffect`; sync
  therefore runs only in the foreground.

## Screens

### Sign-in (`LoginScreen`)

- The `PockyLogo`: a "P" with a `logo-light` → `logo-deep` gradient, three dots in the bowl (a
  typing animation), a pulsing purple glow behind it.
- "PockyAPP" with a gold BETA label, the heading "Chat, který žije." (the word "žije" in
  `accent-soft`), the subtitle "Přihlas se svým Matrix účtem."
- A name field with a label, a fixed "@" and ":pokecio.com"; only the name is typed. A pasted full
  ID (`@karel:pokecio.com`) is shortened to the name.
- A password field with an eye icon to show / hide it.
- A button with a `gradient-start` → `gradient-end` gradient, a progress indicator, an error.
- Inactive (look only), the `LoginScreen` parameters are `null` for now:
  - `onQrLogin` – "Sign in with a QR code",
  - `onRegister` – "No account? Create one",
  - `onChangeServer` – "Server pokecio.com · Change".

The fields support password autofill. The password is discarded after sign-in.

### Onboarding after sign-in (`OnboardingScreen`)

Shown after every new sign-in on a device (the preferences are deleted with the database on
sign-out) and can be reopened from Me → **Onboarding**. Same look as the sign-in: `bg` background
with a purple glow on top, `GradientButton`.

- On top a back arrow (from step 2), a six-part progress bar and "1/6".
- `MainActivity` holds the step number (`rememberSaveable`) so it survives a detour to key recovery
  or emoji verification.
- Every change of a preference is saved immediately through `PreferencesController` into the
  core's encrypted database; nothing goes to the server. "To the chats" saves
  `onboardingDone = true`.

| Step | File | Content |
|---|---|---|
| 1 | `WelcomeStep` | "Hi, <name>." and the promise: only you read your messages, no ads or data selling, no algorithm |
| 2 | `UnlockStep` | according to `EncryptionInfo`: enter the recovery key or phrase (opens `RecoveryScreen`), verify from another device (starts emoji verification), "I don't have it" with an explanation; a verified device → "Older messages are unlocked", an account without keys → "Nothing to unlock" |
| 3 | `ChatStyleStep` | a small chat preview, Classic / Live with an aura |
| 4 | `NotificationStep` | a lock screen preview, Always text / Only on an unlocked phone (recommended) / Never |
| 5 | `LocationStep` | a drawn map without tile downloads, a circle according to the choice: Exact / Area ~500 m / City ~5 km (recommended) |
| 6 | `VisibilityStep` | "Who will see that you are online?" – three avatars with an aura, the switches "Show that I'm online" (off by default) and "Show that I'm typing" (on by default), a note on mutuality |
| 7 | `DoneStep` | a summary of the choices, "Change" returns to the step |

Shared cards (`ChoiceCard`, `ActionCard`, `NoticeCard`, `PromiseRow`) are in `OnboardingCards.kt`.

### Working with messages

- **Long press on a bubble** → `MessageActions` (bottom sheet): a row of quick reactions
  ❤️ 👍 😂 😮 😢 🔥 (own ones highlighted), more emoji, Reply, Edit, Copy, Forward, Message info,
  Delete (with confirmation). The menu follows `canReply` / `canEdit` / `canRedact`.
- **Swipe right** (`SwipeToReply`) – vibrates after 64 dp, prepares a reply when released.
- Reply / edit: `RoomController.composing` (`Composing.Message | Reply | Edit`), a `ComposingBar`
  with a quote and a cross above the field; when editing the text is loaded into the field and the
  photo button disappears.
- **Reactions under a message** are clickable – they add / remove.
- **Photo:** the icon left of the field → the system Photo Picker (no permission) →
  `ImageSendDialog` with a preview, caption, "Send in full quality" and a note about removing the
  location. `MainActivity` reads the bytes through `ContentResolver`; the rest is
  `RoomController.sendImage` + the core.
- Action errors are shown in a snackbar or a toast.

### Notifications

Flow: Synapse → Sygnal → Firebase sends only `room_id` + `event_id` → `PushService` takes the client
from `ClientHolder` (restores it even if the app was not running) → `client.notification()`
downloads and decrypts the message → `Notifier`.

- Firebase: automatic initialisation, analytics and metrics export are disabled
  (`AndroidManifest.xml`). `google-services.json` is in `.gitignore`.
- Channels: **Direct messages** (high importance), **Groups** (default). One notification per room
  (`MessagingStyle`, new messages are appended).
- According to `notificationPreview`:
  - **always** – name and text,
  - **only when unlocked** – with a locked phone only the name and "New message · unlock to view";
    after unlocking (`ACTION_USER_PRESENT`) the text is filled in,
  - **never** – "PockyAPP · New message".
- With the app lock on, notifications behave as **never**.
- Actions: **Reply** (to the specific message) and **Mark as read**; both without opening the app
  (`NotificationActions`, `goAsync`).
- A tap opens the room directly (`MainActivity.EXTRA_ROOM_ID`). Opening a room clears its
  notification; nothing is shown for the room that is currently open.
- Permission (Android 13+): requested after the "What will you see in a notification?" onboarding
  step and through the "Notifications are off" bar in the chat list (after repeated refusal it
  opens the system settings).
- **Without Google services** (no token can be obtained): `MessageCheckWorker` briefly runs sync
  every 15 minutes and announces unread messages it has not reported yet. With a token the job is
  cancelled. On an emulator without a signed-in Google account Firebase returns
  `AUTHENTICATION_FAILED`, so this fallback runs there.

### Chat look (aura)

`LocalChatLook` (in `ui/room/ChatLookStyle.kt`) is set by `MainActivity` from the device
preferences.

- **Classic:** own bubbles `bubble-out`, others `surface`, background `bg`.
- **Live with an aura:** an `AuraBackground` behind the timeline – three blurred glows
  (`gradient-start`, `gradient-end`, `accent`) slowly circling (18 s cycle); own bubbles have a
  `gradient-start` → `gradient-end` gradient with a purple shadow, others are translucent `surface`
  with a thin `accent-soft` border.
- `bubbleSurface` and `bubbleShape` are used by the onboarding preview too.

### Aura around the avatar (`AuraAvatar`)

`auraOf(presence, typing)` picks the ring; "typing" wins over the state.

| State | Look |
|---|---|
| online | a breathing green (`success`) ring with a soft glow |
| away | a still gold (`gold`) ring |
| typing… | an `accent` → `accent-soft` arc with a glow, rotating (1.1 s) |
| offline / unknown | nothing |
| own (without sharing) | a `gradient-start` → `accent-soft` → `gradient-end` ring, slowly rotating |

The ring is drawn outside the avatar area; the layout does not change. Used in: the chat list (the
other person of a direct chat, people typing in a room), the room header (+ "typing…" / "online" /
"away" under the name), message authors' avatars and the "… is typing" bar with dots above the
composer (`TypingIndicator.kt`).

`PresenceController` fetches state only in the foreground, every 60 s, for at most 40 people: the
other people of direct chats from the list and the authors of messages in the open room. Own
"typing" is sent by `RoomController.onDraftChanged` – on change and while typing at most once every
4 s; sending a message or closing the room ends it.

### Home screen (`HomeScreen`)

Bottom bar: **Chats**, Timeline, News, **Map**, **Me** – Timeline and News show "Coming soon".
A floating pencil button opens New chat. Back on the Me tab returns to Chats.

### New chat and group (`ui/people`)

- **New chat** (`NewChatScreen`): a search field (min. 2 characters, 300 ms debounce), "New group"
  on top, without a search "Recent conversations" (the other people of direct chats). Tapping a
  person opens the existing direct chat, otherwise creates a new one (encrypted, `m.direct`).
- **New group** (`NewGroupScreen`): 1. picking people (checkboxes, selected ones in a strip on top
  with a cross), 2. the name (max. 64 characters) – "Create group" creates a private encrypted
  group and invites the selected people.
- After creation the chat opens right away. The logic is in `PeopleController`.

### Invites

Invites are not shown among regular chats in the list, but on top as "INVITES · N" cards (gold
border): the name, "X wants to write to you" / "X invites you", Decline and Accept buttons.
Accepting opens the chat right away.

The whole app is drawn under the status bar and the system buttons (`enableEdgeToEdge`, the
contrast scrim `is*BarContrastEnforced` disabled).

### Chat list (`RoomListScreen`)

The greeting, the status strip and the filters stay fixed on top; only invites and chats scroll.
Bars with a sliding indicator (filters, album tabs) must have a fixed height, otherwise the
indicator stretches.

- Header: "Ahoj, <name> 👋" and the state below it – the number of unread messages (purple, Czech
  plural forms 1 / 2–4 / 5+), "Vše přečteno ✓" (green), "Offline" (gold with a dot) or "Loading
  chats…". On the right a round search button and the own avatar with a gradient ring (opens Me).
- Search filters by name regardless of diacritics and case.
- **Statuses** (`StatusStrip`, no heading): first the own avatar with "Add status" / the own status,
  then the other people of direct chats who have a status, are online or are typing (the status in a
  bubble above the avatar, a green online dot, a circling aura while typing; those with a status
  first). The `StatusBubble`: a soft vertical gradient, an `accent-soft` border fading downwards, a
  purple shadow and a "thought" tail of two dots; an empty own status is a dashed outline. Below the
  strip a `GlowDivider` – a line in the brand gradient with a glow – separates the top panel from
  the chats.
- Own status: `StatusEditorSheet` – text (max. 60 characters), quick ideas, validity 1 h / 4 h /
  today / 24 h / until cleared, Save / Clear status. Someone else's status: `StatusCardSheet` with
  the full text and a Write button.
- The status is separate from "Show that I'm online" (other people's statuses are always loaded).
  After it expires `StatusExpiryWorker` clears it; the core checks it at sync start too.
- The All / People / Groups / Unread switch: the selected part in the brand gradient slides when
  switching (spring animation); the number of unread chats is a small badge next to Unread. People
  = 1:1 direct chats.
- Rows: direct chats have a round avatar, groups a rounded square (the aura follows the shape),
  two-letter initials, unread = purple time and count.
- Bars (verification, backup, notifications) are under the header; offline is shown by the state
  line in the header.
- Rows move smoothly when the order changes (`animateItem`); loading shows shimmering placeholder
  rows; empty filters have their own text and emoji.
- The own avatar (header and Me) has an aura: a breathing green one when sharing the online state,
  otherwise `AvatarAura.OWN` – a slowly circling (6 s) gradient of the brand colours.
- Time / date is aligned in the right column; the name is shortened first.
- **Long press on a row** → `RoomActionsSheet`: Mark as read, Pin to top / Unpin, Mute / Unmute,
  Leave (with confirmation).
- **Swiping a row** (`SwipeRoom`): right = read (unread only), left = mute / unmute.
- Pinned chats are sorted to the top by the core (the `m.favourite` tag) and have a pin next to the
  time. Muted ones have a crossed-out bell and a grey count; a mention (@) is gold.
- Ticks for the own last message: a clock = sending, ✓ = sent, ✓✓ green = read (a read receipt
  from anyone else), ! = error.
- Times are always Czech (`AppLocale`): "13:09", "Včera", "Út", "13. 9.".
  `MainActivity.attachBaseContext` sets Czech for plural forms too (1 člen / 2–4 členové / 5 členů)
  regardless of the system language.

### Me – profile and settings (`ProfileScreen`)

Opened from the bottom bar or by tapping the own avatar in the header.

- On top a large avatar with an aura (a camera badge → photo menu), the name with a pencil (→ name
  dialog) and the Matrix ID.
- **Profile:** Name (dialog, max. 64 characters, Save only after a change), Profile photo (Pick from
  the gallery through the Photo Picker / Remove photo) and Change account password. During upload a
  progress indicator is shown over the avatar.
- **Privacy:** the switches "Show that I'm online", "Show that I'm typing" and "Read receipts"
  (+ notes on mutuality).
- **App:** Notifications, Chat look, Location – the row shows the current choice; a tap opens a
  bottom sheet with the same cards and previews as the onboarding.
- **Security:** This device (Verified / Not verified → key recovery), Key backup (On / Set up →
  backup setup), Change security phrase, My devices, App lock, Block screenshots.
- **About:** Onboarding, Release notes, Updates, Check for updates automatically, Version (app ·
  core).
- Sign out, and below it in red **Delete account**.

### My devices (`DevicesScreen`)

This device on top, below it the others sorted by last activity; each with its name, "Last active:
…" and a Verified / Not verified label. Tapping another device → a password dialog (the server
requires it) → sign-out. IP addresses are deliberately not shown. The logic is in
`DevicesController`.

### Release notes (`ChangelogScreen`)

Me → About → Release notes. A card for every version (number, channel, an "Installed" label; the
date only for the latest one). New features have a purple sparkle, fixes a green check. Data from
`changelog()` in the core according to the installed `versionCode`.

### Coming soon

The Timeline and News tabs, the call and video call icons in the chat header and "Search in chat"
are dimmed and show a short "Coming soon" message on tap (`ui/components/ComingSoon.kt`).

### Own status

`PresenceController` subscribes to `observe_own_status` (in the core `watch_own_status`): a status
set on another device shows up immediately and its expiry is scheduled too.

### Chat album (`RoomGallery`)

Chat menu → Photos and files. Four tabs with counts: Photos, Videos, Voice messages, Documents.
Photos and videos in a grid of 3, voice messages and documents in a list (playback and opening
directly in it), everything from the newest and grouped by month. The data comes from
`RoomMediaController`: its own timeline from `room_media`; after the album opens it paginates on its
own to the start of the chat (with a 150 ms pause between pages) and stays while the chat is open.
The photo viewer first shows the thumbnail and then the full quality (`original`).

- **The photo and video viewer** has a `MediaActionBar` on top: Info (from whom, when, name, size,
  dimensions, duration, type), Save, Share and Forward. It works from the album and from the chat.
  The details come from `TimelineEvent.mediaDetails()`; the core sends `name`, `size` and `mime` for
  photos and videos too.
- **Selection:** a long press on a tile starts selection, further taps add and remove. On top
  "Selected: N" with Save, Share and Forward, and a Select all button.
- **Save all** saves the whole current tab (only after the whole history has loaded).
- **Saving** (`MediaSaver`): Android 10+ through MediaStore without a permission, photos to
  `Pictures/PockyAPP`, videos to `Movies/PockyAPP`, voice messages to `Music/PockyAPP`, the rest to
  `Download/PockyAPP`. Android 9 asks once for `WRITE_EXTERNAL_STORAGE` (in the manifest only up to
  SDK 28). Names like `PockyAPP_IMG_20261008_231300.jpg`; MediaStore numbers duplicate names.
- **Bulk download** (`MediaDownloads`, one instance per session) keeps running after the album is
  closed, with progress "Saving 3 of 12" and a cancel option, and a toast at the end. Files that were
  not cached before are removed from the cache after saving.
- **Sharing** through the system menu (FileProvider, `cache/open`).
- **Forwarding** (`ForwardDialog` + `RoomController.forward`): a chat picker with search; the core
  `forward_message` loads the original event and sends the same content without the reply
  relation. Encrypted files are not re-uploaded; the same encrypted file with its key is sent. The
  result is shown in a toast.

### Change history (`RoomHistory`)

Chat info → "Change history". A list of state changes from the newest, grouped by day, repeated
changes by the same person merged. The data comes from `RoomHistoryController` (its own timeline
from `room_history`, opened only while the panel is shown, paginating until it finds 20 more
changes). On a landscape tablet it is shown in the side panel like the album.

### GIFs and images from the keyboard

- The composer is a `BasicTextField(TextFieldState)` with `contentReceiver`. An image or GIF from
  the keyboard (Gboard, Samsung) is copied right away into `cache/keyboard` (the keyboard's
  permission is temporary) and the same preview as for gallery photos opens.
- The core sends GIFs and animated WebP unchanged (not resized), otherwise the animation would be
  lost.
- `AnimatedMatrixImage` plays GIF and WebP through `ImageDecoder` and `AnimatedImageDrawable` in the
  bubble and in the viewer.

### Voice messages: preview before sending

The microphone starts recording (`RecordingBar`). The button on the right stops the recording and,
instead of sending right away, shows `RecordingReview`: Discard, Play/Pause, a seekable waveform and
the duration, Send on the right. The preview plays through its own `MediaPlayer` from a temporary
file in `cache`, which is deleted after sending or discarding.

### Reactions and read receipts

- **More emoji:** in the message menu, after the quick reactions, a smiley button opens
  `EmojiPickerView` from `androidx.emoji2:emoji2-emojipicker` (categories and recently used, stored
  on the device only). Search by name is not there yet.
- **Read:** under the message up to which someone has read (`ReadReceipts`). In a group small
  avatars (at most 4 and "+N"), in a 1:1 chat "Read 12:30" under the own message. A tap shows who
  read it and when. Names and avatars come from `RoomController.members`.
- **Message info** (message menu, `MessageInfoDialog`): Sent to the server with the time, Edited,
  and Read. In a 1:1 chat the time, in a group "Read: 3 of 5" and a list of people with times.
  Matrix has no delivery-to-device receipts, hence only "to the server". Whoever wrote in the chat
  later has read the earlier messages: `readersOf` and the bubble ticks (`buildRows`) count such a
  reply as read even without a receipt.
- **Forward** is in the menu of every message.
- The timeline must have receipt tracking switched on (`track_read_marker_and_receipts(MessageLikeEvents)`
  in `open_timeline`). It is off by default in matrix-sdk-ui; until 0.9.5-Fix #3 it was missing, so
  "Read" never showed in chats.
- **The "Read receipts" option** in Me → Privacy. It is mutual: when off, only a private receipt is
  sent and other people's receipts are not shown (not even the double tick).

### Verifying people and shields

- **1:1 chat info** (encrypted chat): a "Verify person", "Verified" or "Identity changed" row
  (`TrustRow`). A tap starts emoji verification on the same screen as for own devices
  (`VerificationScreen` with `person`). After it finishes, the state in the chat info is reloaded.
- **Banner in the chat** (`IdentityBanner`): when the encryption identity of someone in the chat
  changes. Yellow for unverified, red for previously verified people. Options Verify and I
  understand. Data from `RoomController.identityChanges`.
- **Shield on a message** (`ShieldMark`): a small icon next to the time; a tap shows an
  explanation. Red for serious problems.
- The `shield-check` and `shield-alert` icons are in `design/icons`.

### New encryption identity (`ResetIdentityScreen`)

At the bottom of the verification screen (`RecoveryScreen`) there is "I have neither the key nor
another device". It opens a warning (older messages will not be readable, other devices will need
to be verified again, people will see the identity change), an "I understand" checkbox, the account
password, a new security phrase and the "Create new identity" button. Then the new recovery key is
shown just like when enabling backup (`KeyReady`). The logic is in
`EncryptionController.resetIdentity`.

### Security phrase, password and account (`ui/encryption`)

- `PassphraseFields`: the phrase entered twice with a strength meter (`passphraseStrength` from the
  core) and the recommendation; used by backup setup, identity reset and `ChangePassphraseScreen`.
- `ChangePasswordScreen`: current password, new password twice, "Sign out other devices".
- `DeleteAccountScreen`: what will happen, the password and typing SMAZAT; the delete button is in
  `PockyColors.Danger`.

### App lock and screenshots (`ui/lock`, `SecureWindow`)

- `LockScreen` is drawn over the whole logged-in content (the content stays composed but hidden from
  accessibility with `clearAndSetSemantics`), with `PinDots`, `PinPad`, a biometric key and "Forgot
  your PIN?" → sign-out.
- `AppLockSettingsScreen`: enable (PIN twice, trivial PINs refused by the core), biometrics, lock
  after (immediately / 1 / 5 / 15 min), change PIN, disable.
- `SecureWindow(enabled)` sets `FLAG_SECURE`; `Screen.sensitive` screens are always protected, the
  rest according to `blockScreenshots`.

### Room (`RoomScreen`)

Drawn over the whole screen; with the aura look there is an `AuraBackground` behind everything and
the header is translucent.

- **Header** (`RoomHeader`): back arrow, avatar with an aura (a direct chat round, a group rounded),
  the name, a lock for encrypted chats, below it "typing…" / "online" / "away". On the right Call
  and Video call ("Coming soon") and a ⋮ menu: Search in chat, Photos and files, Chat info,
  Mute / Unmute, Pin / Unpin, Leave (with confirmation, then closes the chat).
- **Bubbles**: the `BubbleShape` – the last message in a group has a "tail" (own on the right,
  others on the left); time, "edited" and the state (clock / ✓ / ✓✓ green / ! icons) inside the
  bubble.
- **Date** as a floating label, **New messages** as a gradient label with fading lines.
- **"Typing…"** is a bubble with bouncing dots at the end of the conversation (in a group with the
  typing person's avatar).
- **The scroll-down button** appears after scrolling away; its badge shows how many messages from
  others arrived meanwhile.
- **Composer**: a rounded "pill" (photo on the left, text), next to it a round send button in the
  brand gradient; a ✓ icon when editing.
- **Photo**: a tap opens the full-screen `ImageViewer` – pinch zoom, double tap 2.5× / back, the
  caption at the bottom, the action bar on top.
- **Attachments**: the photo button → `AttachSheet` Take a photo (the system camera, the file through
  `FileProvider` into `cache/camera`, no CAMERA permission) / Gallery (Photo Picker, up to 10
  photos) / File / Location. `ImageSendDialog` shows one large picture or a strip of previews;
  photos are sent one by one, the caption goes with the first.
- **File**: the system document picker → `FileSendDialog` (name, size) →
  `RoomController.sendFile`. The `FileMessage` bubble: an icon with the extension, name, size; a tap
  downloads the file, decrypts it, copies it into `cache/open` and opens it through `FileProvider`
  in an app on the phone.
- **Video**: the Gallery picks photos and videos; videos → `VideoSendDialog`. `VideoPrep` gets the
  dimensions (including rotation), the duration and a thumbnail and remuxes MP4 through
  `MediaExtractor`/`MediaMuxer` without metadata (no location); other formats are sent unchanged.
  The bubble: a thumbnail with ▶ and the duration → `VideoPlayerDialog` (the system `VideoView`,
  pause, seek bar, time).
- Above the server limit the message "The file is too large for the server". Sign-out deletes the
  whole `cacheDir` (downloaded and decrypted files).
- **Chat info** (`RoomInfoScreen`, a tap on the header or the ⋮ menu): a large picture (an admin
  changes it through 📷), the name and topic (an admin with ✏️), an Encrypted label; Mute, Pin,
  Photos and files, Change history; for groups the member list (Admin / Moderator labels, "Invite
  pending"), Add people (picker like for a new group in Invite mode) and removing a member with
  confirmation; Leave at the bottom. For a direct chat the other person's "Profile" (photo, ID,
  online state) and verification. The logic is in `RoomInfoController`; what can be changed is
  decided by permissions from the core.
- **Voice messages**: an empty field → the microphone button (the RECORD_AUDIO permission only on
  first use). `VoiceRecorder` records Ogg/Opus (Android 10+) or AAC (Android 9) and samples the
  volume every 60 ms → `RecordingBar` with a live waveform, time, 🗑 and ■ (stop). Recordings shorter
  than 0.7 s are discarded. The `VoiceMessage` bubble: ▶/⏸, the waveform (the played part
  highlighted, tap/drag = seek), time. Played by `VoicePlayer` (only one at a time, the file from
  `MediaLoader.file` in `cache/media`, deleted on sign-out).

## Icons

One consistent custom set for all platforms: `design/icons/*.svg` (a 24 × 24 grid, 1.8 stroke,
round caps and joins, no fills, colour `currentColor`).

- Android: `python3 scripts/gen-icons.py` generates the vectors `res/drawable/pi_*.xml` (deleting
  the old `pi_*` first).
- In Compose through `PockyIcons` (`ui/theme/PockyIcons.kt`), e.g. `Icon(PockyIcons.Search, …)`; the
  colour comes from `tint`.
- The app does not use Material icons (the `material-icons-core` dependency is gone).
- Exception: `ic_notification.xml` – a solid "P" for the status bar.

A new icon: add an SVG to `design/icons/`, run the script and add a line to `PockyIcons`.

## Colours

`ui/theme/Color.kt` matches `design/tokens.json`:

| Token | Colour | Use |
|---|---|---|
| bg | #08070c | background |
| surface | #121019 | others' bubbles, fields, surfaces |
| accent | #8b5cf6 | primary colour, badges, name links |
| accent-soft | #b8a1fb | highlights in text (the word "žije" on the sign-in screen) |
| bubble-out | #7c3aed | own bubbles |
| ink | #f4f2f9 | text |
| ink-muted | #a4a0b3 | secondary text |
| success | #34d399 | read |
| logo-light | #dccff7 | top part of the logo |
| logo-deep | #a466de | bottom part of the logo |
| gradient-start | #4340a0 | main button gradient (left) |
| gradient-end | #8e52a6 | main button gradient (right) |
| gold | #f3cd66 | the BETA label |
| danger | #ef4444 | account deletion |

## Texts

All texts are in Czech in `res/values/strings.xml`. User-facing texts are written gender-neutral;
state changes use forms like "připojil(a)", because Matrix does not know the user's gender.

## Media

`MediaLoader`:

- calls `client.loadMedia` (caching and decryption are handled by the core),
- decodes with downsampling (`inSampleSize`) to the requested size,
- keeps decoded images in an `LruCache` (1/8 of the app memory),
- merges concurrent requests for the same image into one.

In the UI it is used through `LocalMediaLoader` and `rememberMatrixImage(source, w, h)`.

## Map and location

- `ui/map/PockyMap.kt`: a shared wrapper over the MapLibre `MapView`, tied to the lifecycle. The
  style is `asset://map/style.json`, no logo, no built-in attribution; the "© OpenStreetMap" label
  is drawn by Compose. Rotation and tilt are disabled.
- `location/DeviceLocation.kt`: location through the system `LocationManager` (fused / GPS /
  network), without Google services. A fresh location within 60 s with accuracy under 50 m is used
  right away, otherwise a one-off request with a 20 s limit.
- `ui/room/LocationPicker.kt`: the Location attachment. A fixed pin in the middle, the map moves
  under it. A My location button (the permission is requested only after the tap, if not granted
  yet). The Exact / Area ~500 m / City ~5 km choice, defaulting to the onboarding setting. For an
  approximate location the circle shows exactly the area that will be sent (`blurLocation` from the
  core).
- The GPS accuracy is sent only when the pin stays on the own location. After moving the map a
  plain point without `u=` is sent.
- `abiFilters` in `app/build.gradle.kts` takes `POCKY_ABIS` (default `arm64-v8a`) so MapLibre does
  not bundle libraries for other architectures.
- `ui/map/MapPreviews.kt`: static map snapshots (`MapSnapshotter`) for bubbles, an in-memory cache
  of the last 24 snapshots. A snapshot runs in its own scope independent of the bubble (otherwise
  scrolling the list would cancel it and the preview would stay black), with a 30 s limit; failures
  are not cached. Concurrent requests for the same snapshot are computed only once. The snapshotter
  draws the attribution itself.
- `ui/room/LocationMessages.kt`: a 240 × 150 dp location bubble. An exact location (within 150 m)
  has a pin, an approximate one a circle and the label "Approximately · area ~500 m". The preview
  zoom is chosen so the whole circle is visible. A live location shows the last point with a green
  dot. A tap opens a full-screen map with a Navigate button (a `geo:` intent with an app chooser,
  only after the tap).
- `projection.getMetersPerPixelAtLatitude` returns metres per dp, not per pixel. The radius is
  therefore converted with `.dp.toPx()`.
- `location/LiveLocationService.kt`: live location in the background. A foreground service of type
  `location` with a persistent notification "Sharing your live location · Until 14:35 · 2 chats" and
  a Stop action. The system location every 2 s; whether to send is decided by
  `shouldSendLiveLocation` from the core (separately for every chat). After expiry or
  `LiveLocationEnded` the chat drops out; with the last one the service stops.
- Sharing survives closing the app and process death: the list (chat, end, precision) is in the
  `live_location` `SharedPreferences`, the service is `START_STICKY` and reloads it after a restart.
  After a phone restart `LiveLocationBoot` shows a "Location sharing was interrupted" notification;
  opening the app resumes sharing (`resumeOwn`).
- `location/BackgroundRun.kt`: when live location starts and the phone may put the app to sleep, it
  offers to disable battery optimisation (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`). `active`
  (roomId → end) is read by the chat and the Map. It is started only from the foreground (by a tap),
  so the "while using" permission is enough; `ACCESS_BACKGROUND_LOCATION` is not needed.
- `location/LiveLocationController.kt`: follows other people's live locations
  (`observeLiveLocations`), starts sharing and after sign-in resumes own shares that have not expired
  yet. Ends everything on sign-out.
- The location picker has a Share live location option (15 min / 1 h / 8 h). In the chat, below the
  header, a "Sharing your live location · until …" bar with a Stop button.
- `ui/map/MapTab.kt`: the Map tab. Avatars of people sharing their live location at their place (a
  location older than 10 min is faded), an own dot, a My location button, people cards (tap = move
  the map, Write = chat), an own sharing bar with Stop all.

Permissions: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_LOCATION`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `RECEIVE_BOOT_COMPLETED`.

Emulator: set the location through Extended controls → Location. `adb emu geo fix` does not reach
newer images. On the emulator the eastern part may not render in the whole-country overview (zoom
6): the tiles download, but the emulator's software GPU does not draw them. From zoom 7 everything
is fine.

## Screen transitions

`ui/navigation/Transitions.kt`:

- `Screen`: every screen has a depth (list 0, chat 1, new chat, devices and settings screens 2, new
  group and invite 3) and a `sensitive` flag. `MainActivity` picks the screen from it and
  `AnimatedContent` with `screenTransition()`: a step deeper slides in from the right, back slides
  out to the right, the previous screen shifts by a quarter and fades. Verification, backup and the
  onboarding only fade.
- When closing a chat, the last open room is still drawn for 300 ms (`lastRoom`), even though
  `openRoom` is already `null`.
- Bottom bar tabs: a 180 ms `Crossfade`.
- `PushedLayer` (from the right): chat info, album, location map. `RaisedLayer` (from the bottom):
  the location picker.

## Tablets and large screens

`ui/layout/ScreenSize.kt` determines the size from the window width (`material3-adaptive`, works
for split screen and Samsung DeX too):

| Size | Width | Layout |
|---|---|---|
| `COMPACT` | < 600 dp | phone: bottom bar, screens on top of each other |
| `MEDIUM` | 600–839 dp | side rail, 320 dp list + chat |
| `EXPANDED` | 840–1239 dp | side rail, 380 dp list + chat |
| `LARGE` | ≥ 1240 dp | like EXPANDED + a 400 dp third pane |

The size is in `LocalScreenSize`; screens adapt on their own:

- `HomeScreen`: from MEDIUM a side rail (`SideRail`) with a New chat button instead of the bottom
  bar.
- `ui/layout/Panes.kt` `ChatsLayout`: the chat list and the open chat on the right (`roomContent` in
  `MainActivity` serves both the phone and the pane), without a chat `EmptyChatPane`. The back arrow
  in the chat header is shown on phones only.
- `SidePanel`: on LARGE, chat info, the album and the location map open on the right next to the
  chat. On smaller widths over the chat (`PushedLayer`).
- `Readable`: settings, new chat, group, devices, onboarding and verification are centred on
  tablets with a max. width of 680 dp. Bubbles max. 480 dp.
- Map: on tablets the list of people top right instead of cards at the bottom.
- Rotation keeps the open chat and panels (the state is in the `ViewModel` and
  `rememberSaveable`).

Test emulator: `Medium_Tablet` (2560 × 1600, 1280 dp wide = LARGE, 800 dp in portrait = MEDIUM).

## In-app updates (self-update)

**Automatic checks** (the "Check for updates automatically" option, on by default):

- On every app start and on return from the background at most once every 15 minutes. When a newer
  version exists, the New version dialog pops up on its own. A known new version is shown on every
  return to the app until Later is chosen; a postponed version is offered again after 24 hours.
  The dialog is not shown over the app lock.
- In the background every 6 hours `UpdateCheckWorker` (WorkManager, only with a connection). If it
  finds a new version and the app is not running, it shows a notification in the Updates channel.
  Only once per version; it does not announce a version postponed in the last 24 hours. A tap opens
  the app straight with the New version dialog. It only asks pokecio.com.
- Turning the option off cancels the background check too.

- Core (`update.rs`): `check_update` downloads `https://pokecio.com/updates/android.json`, compares
  `versionCode` and `minSdk` and returns the new features and fixes in the app language. The APK URL
  must start with `https://pokecio.com/`, otherwise it is refused. `download_update` downloads in
  chunks with progress, computes the SHA-256 and never uses an unfinished file (`.part`).
- `update/UpdateController.kt`: before installing, compares the signing certificate of the
  downloaded APK with the installed app. Then `PackageInstaller` and the system dialog. The first
  time "Install unknown apps" has to be allowed.
- `update/UpdateInstallReceiver.kt`: opens the installation confirmation and reports errors.
- `ui/update/UpdateDialog.kt`: the "New version" dialog with new features, fixes, download progress
  and the Update and Later buttons. It sits above all screens, so it works without sign-in too.
- Settings → About: the Updates row (state, manual check) and the Check for updates automatically
  switch.

Permission: `REQUEST_INSTALL_PACKAGES`. 
