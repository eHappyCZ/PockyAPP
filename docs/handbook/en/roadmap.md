# Status and plan

## Done

| Step | Content |
|---|---|
| 1 | Monorepo, Rust workspace, colour tokens |
| 2 | matrix-sdk, client with an encrypted SQLite store |
| 3 | UniFFI layer, Kotlin binding generation |
| 4 | Android project, Compose, theme, building Rust with cargo-ndk |
| 5 | Sign-in, session restore, sign-out, Keystore, TLS on Android |
| 6 | Sliding sync, room list, previews, offline state |
| 7 | Timeline, history, sending text, read state, avatars, images |
| 8 | Encryption state, verification with a key or emoji, downloading keys from the backup, backup for new accounts, sign-out warning |
| 9 | Onboarding after sign-in (7 steps), device-only preferences, chat look with an aura |
| 10 | Aura around the avatar: online, away, typing…; sharing your own presence and typing according to preferences, mutually |
| 11 | Replies, edits, reactions, deletions, sending photos (resized, no EXIF) |
| 12 | Notifications: Firebase only as a bell, decryption on the device, preview option, reply from the notification, fallback without Google |
| 13 | New chat list: full screen under the system bars, search, Online now, filters, bottom bar, Me tab |
| 14 | Chat list: greeting and status, People tab, pinning, muting, leaving, row swipe, ticks, Czech times |
| 15 | The Me tab as settings: privacy, notifications, look, location, security, version |
| 16 | New chat look: header over the aura, bubbles with tails, "typing…" in the conversation, composer pill, scroll-down button, full-screen photos, call/video/menu in the header |
| 17 | Own consistent set of 41 icons in `design/icons`, Android generator, no Material icons |
| 18 | Profile: changing the name (nickname) and the profile photo |
| 19 | New chat (people search), new group, accepting and declining invites |
| 20 | My devices: list, verification state, signing another device out with the password |
| 21 | Release build: optimised core, R8, signing through environment variables |
| 22 | Voice messages with a real waveform, camera, several photos at once, chat photo gallery |
| 23 | Files (sending, downloading, opening) and video (no metadata, player) |
| 24 | Chat info: editing a group, members and roles, inviting and removing people, the other person's profile |
| 25 | Statuses: custom status with expiry, status strip instead of "Online now", coloured divider |
| 26 | Location: own map server (pmtiles), map of CZ and SK only, location picker with precision, map bubble and Navigate, live location in the background, Map tab |
| 27 | Screen transitions (slide and fade), aura fix in the chat header |
| 28 | Tablets: screen size detection, side rail, list + chat side by side, third pane, limited widths, map with a side list, rotation |
| 29 | Version 0.9.4 - BETA, in-app updates ("New version" dialog, SHA-256 and signature check) |
| 30 | Website pokecio.com: Astro, Czech and English, features, privacy, download, news, changelog, RSS, service status with Uptime Kuma, legal texts |
| 31 | Website: realistic phone, tablet and web previews with made-up data, five detailed feature articles, daily summary, contact dev@pokecio.com |
| 32 | Own status in account data `com.pokecio.status` (core; Android and web pending) |
| 33 | New encryption identity ("I have neither the key nor another device") |
| 34 | Verifying other people, identity change banner, shields on messages |
| 35 | Any emoji as a reaction, read receipts with a privacy option |
| 36 | Only important state messages in the chat, the rest in the change history |
| 37 | Album: file details, saving one photo, a selection or the whole album, sharing and forwarding; message info; read receipts really enabled in chats; filters All, People, Groups, Unread; update check on every start |
| 38 | Security phrase for the key backup with a strength meter, phrase change, account password change, voice message preview before sending |
| 39 | Screenshot blocking, app lock (PIN and biometrics), account deletion, GIFs from the keyboard and animated GIFs, date only for the latest version in the release notes |
| 40 | Any Matrix homeserver: server choice at sign-in on the web and on Android, web deployment config, self-hosting guide, the web app in the public repository |

## Planned

Within each group, the order reflects priority. Step numbers are assigned when the work starts.

### Next up (Android)

| Feature | Note |
|---|---|
| Blocking people and reporting messages | a must for the community; reports go to the server admin |
| Disappearing messages | 1 hour, 1 day, 7 days |
| Search in a chat | "Coming soon" in the chat menu today |
| Pinned messages | pin from the message menu, overview in chat info |
| Sharing encrypted history with new group members | new members see older messages too |
| Light theme | dark only today |
| English in the app | texts are ready in `strings.xml`, the translation is missing |
| Emoji search by name | in the emoji picker for reactions |

### Web app (catching up with Android)

- chat album with saving, sharing and forwarding,
- shields on messages, verifying people and message info,
- enabling key backup, changing the security phrase and the password, account deletion,
- several photos at once and full quality, sending video, recording voice messages,
- creating and managing groups, editing the profile,
- location precision and bubble look settings,
- installing as an app (PWA).

### Larger pieces

| Piece | Note |
|---|---|
| Voice and video calls | Element Call / MatrixRTC, "Coming soon" in the chat header today |
| Timeline | public community board, "Coming soon" in the bottom bar today |
| News | "Coming soon" in the bottom bar today |
| Account registration in the app | today only signing in to an existing account |
| Private nicknames and custom chat backgrounds | device only |

### Platforms

- iOS and macOS (SwiftUI),
- Windows (WinUI 3),
- Linux (GTK4).

### Technical debt and operations

- Firebase: moving from `getToken` to the new `register()` API (did not work on the emulator yet),
- notifications through ntfy / UnifiedPush as an option for advanced users,
- automated UI tests and builds in CI (GitHub Actions),
- encrypted server backups outside the VPS, `security.txt` on the website,
- an independent security audit before the beta ends.

### Waiting for real-world verification

- app lock with biometrics, GIFs from the keyboard, screenshot blocking in the app switcher
  (0.9.5-Fix #6),
- unlocking the backup with the security phrase only on a new device, changing the phrase on a real
  account,
- identity reset, read receipts in a group, live location in the background with two accounts.

## Known limitations

- The debug APK is around 200 MB because of the unoptimised Rust library; the release APK is
  about 70 MB.
