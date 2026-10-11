# API pro platformy – `pocky-ffi`

Cesta: `core/pocky-ffi/src/`. Kotlin package: `com.pokecio.app.core`
(nastaveno v `core/pocky-ffi/uniffi.toml`).

Bindingy se generují skriptem `scripts/gen-bindings.sh`, ručně je neupravovat.

## Funkce

```kotlin
coreVersion(): String

suspend createClient(dataDir: String, passphrase: String?): PockyClient
suspend restoreClient(session: String, dataDir: String, passphrase: String?): PockyClient
```

- `createClient` je pro **nové přihlášení**: smaže `dataDir` a dohledá server
  `pokecio.com` přes `.well-known`.
- `restoreClient` použije uloženou session (JSON) a existující úložiště.

## PockyClient

```kotlin
homeserver(): String
userId(): String?
isLoggedIn(): Boolean
exportSession(): String?

suspend login(username: String, password: String, deviceName: String): String   // session JSON
suspend logout()

suspend startSync()
suspend stopSync()
suspend observeRooms(listener: RoomListListener): TaskHandle
suspend observeSyncStatus(listener: SyncStatusListener): TaskHandle

suspend encryptionStatus(): EncryptionInfo
suspend recover(key: String): EncryptionInfo                 // klíč pro obnovení nebo fráze
observeEncryption(listener: EncryptionListener): TaskHandle
suspend logoutWarning(): LogoutWarning
suspend enableRecovery(password: String, passphrase: String): String   // nový klíč pro obnovení
suspend resetIdentity(password: String, passphrase: String): String    // nový klíč pro obnovení
suspend changeRecoveryPassphrase(passphrase: String): String           // nový klíč pro obnovení
suspend requestSelfVerification(): Verification
observeVerificationRequests(listener: VerificationRequestListener): TaskHandle

suspend changePassword(current: String, newPassword: String, signOutOthers: Boolean)
suspend deleteAccount(password: String)

suspend displayName(): String
suspend preferences(): DevicePreferences
suspend savePreferences(preferences: DevicePreferences)

suspend registerPusher(pushKey: String, deviceName: String, lang: String)
suspend unregisterPusher(pushKey: String)
suspend notification(roomId: String, eventId: String): NotificationInfo?

suspend presence(userId: String): PresenceInfo
observeTyping(listener: TypingListener): TaskHandle
suspend sendTyping(roomId: String, typing: Boolean)

suspend timeline(roomId: String): RoomTimeline
suspend roomMedia(roomId: String): RoomTimeline     // jen fotky, videa, hlasovky a soubory
suspend roomHistory(roomId: String): RoomTimeline   // jen stavové změny
suspend forwardMessage(fromRoom: String, eventId: String, toRoom: String)
suspend loadMedia(source: String, width: UInt, height: UInt): ByteArray

close()   // generované UniFFI, uvolní klienta
```

`logout` zastaví sync, odhlásí zařízení na serveru, zavře klienta a **smaže
celou datovou složku**. Data smaže i při výpadku sítě; pak vrátí `Network`, ale
lokálně je uživatel odhlášený.

## RoomTimeline

```kotlin
observe(listener: TimelineListener): TaskHandle
suspend paginateBackwards(): Boolean   // true = dosažen začátek místnosti
suspend sendText(text: String)
suspend markAsRead()
close()
suspend sendReply(text: String, inReplyTo: String)        // event id
suspend edit(itemKey: String, text: String)
suspend toggleReaction(itemKey: String, key: String): Boolean
suspend redact(itemKey: String)
suspend sendImage(bytes: ByteArray, caption: String?, fullQuality: Boolean)
```

## Verification

```kotlin
flowId(): String
isIncoming(): Boolean
observe(listener: VerificationListener): TaskHandle      // onStep(VerificationStep)
suspend accept()        // příchozí žádost
suspend confirm()       // emoji se shodují
suspend mismatch()      // emoji se neshodují
suspend cancel()
```

```kotlin
sealed class VerificationStep: Requested, Incoming, Ready, Starting,
    Emojis(emojis: List<VerificationEmoji>), Confirmed, Done, Cancelled(byUs, reason)

data class VerificationEmoji(index: UByte, symbol: String, description: String)
```

`index` je pořadí emoji ve specifikaci Matrixu (0–63); Android podle něj
bere český název z `res/values/emoji.xml`.

## Bezpečnostní fráze a zámek aplikace

```kotlin
passphraseStrength(passphrase: String): PassphraseStrength   // TOO_SHORT, NO_SYMBOL, WEAK, FAIR, STRONG
minPassphraseChars(): UInt
recommendedPassphraseChars(): UInt

appLockPinLength(): UInt
appLockPinAllowed(pin: String): Boolean

client.appLock(): AppLock                       // enabled, biometrics, timeoutSeconds, blockedUntilMs
client.enableAppLock(pin, biometrics, timeoutSeconds)
client.updateAppLock(biometrics, timeoutSeconds)
client.unlockApp(pin): UnlockOutcome            // Unlocked, WrongPin(attemptsLeft), Blocked(untilMs)
client.unlockAppWithBiometrics()
client.disableAppLock(pin): UnlockOutcome
```

## TaskHandle

```kotlin
cancel()
isFinished(): Boolean
close()
```

Úloha skončí po `cancel()`, nebo když se objekt uvolní.

## Listenery

```kotlin
interface RoomListListener   { fun onUpdate(rooms: List<Room>) }
interface SyncStatusListener { fun onUpdate(status: SyncStatus) }
interface TimelineListener   { fun onChanges(changes: List<TimelineChange>) }
interface EncryptionListener { fun onUpdate(info: EncryptionInfo) }
interface VerificationListener { fun onStep(step: VerificationStep) }
interface VerificationRequestListener { fun onRequest(verification: Verification) }
```

Volají se z vláken jádra, ne z hlavního vlákna. V Kotlinu proto jen zapisovat do
`StateFlow` (je thread-safe), UI nikdy neupravovat přímo.

## Typy

### Seznam místností

```kotlin
data class Room(id, name, avatarUrl, lastMessage: LastMessage?, timestampMs: ULong?,
                unreadMessages: ULong, unreadMentions: ULong,
                isEncrypted, isDirect, isInvite)

data class LastMessage(senderId, senderName, isOwn, content: MessageContent)

sealed class MessageContent: Text(body), Emote(body), Image, Video, Audio, File,
                             Location, Sticker, Poll, Encrypted, Invite, Other

enum class SyncStatus { IDLE, RUNNING, OFFLINE, ERROR, TERMINATED }
```

### Timeline

```kotlin
sealed class TimelineChange: Reset(items), Append(items), Clear, PushFront(item),
    PushBack(item), PopFront, PopBack, Insert(index, item), Set(index, item),
    Remove(index), Truncate(length)

data class TimelineItem(id: String, kind: TimelineItemKind)

sealed class TimelineItemKind: DateDivider(timestampMs), ReadMarker, TimelineStart,
                               Event(event: TimelineEvent)

data class TimelineEvent(eventId, senderId, senderName, senderAvatar, timestampMs,
    isOwn, isEdited, content: TimelineContent, reactions: List<TimelineReaction>,
    inReplyTo: TimelineReply?, sendState: TimelineSendState, readBy: List<String>)

sealed class TimelineContent: Text/Notice/Emote(body, html),
    Image(caption, source, width, height), Video(caption, thumbnail, width, height),
    Audio(caption), File(name), Location(geoUri, description),
    LiveLocation(isLive, geoUri), Sticker(body, source, width, height),
    Poll(question), Redacted, UnableToDecrypt, State(change), Other

sealed class TimelineStateChange: Membership(userId, name, change), DisplayName(userId, old, new),
    Avatar(userId, name), RoomName(name), RoomTopic(topic), RoomAvatar,
    RoomCreated, EncryptionEnabled, Other

enum class Membership { JOINED, LEFT, INVITED, INVITATION_ACCEPTED, INVITATION_REJECTED,
    INVITATION_REVOKED, KICKED, BANNED, UNBANNED, KNOCKED, OTHER }

data class TimelineReaction(key, count: UInt, reactedByMe)
data class TimelineReply(eventId, senderId?, senderName?, content: MessageContent?)
enum class TimelineSendState { SENDING, SENT, FAILED }
```

`TimelineItem.id` je stabilní – vhodný jako klíč v `LazyColumn`.

### Šifrování

```kotlin
data class EncryptionInfo(recovery: Recovery, backupOnServer, secretStorage, hasIdentity,
    deviceVerified, otherDevices: UInt, verifiedOtherDevices: UInt,
    canRecover, canVerifyWithDevice, canEnableRecovery)

enum class Recovery { UNKNOWN, ENABLED, DISABLED, INCOMPLETE }

data class LogoutWarning(lastDevice, recoveryEnabled, losesHistory)
```

### Volby zařízení

```kotlin
data class DevicePreferences(onboardingDone, chatStyle: ChatLook,
    notificationPreview: NotificationText, locationPrecision: LocationArea,
    sharePresence: Boolean, shareTyping: Boolean, sendReadReceipts: Boolean,
    blockScreenshots: Boolean)

enum class ChatLook { CLASSIC, AURA }
enum class NotificationText { ALWAYS, WHEN_UNLOCKED, NEVER }
enum class LocationArea { EXACT, NEIGHBORHOOD, CITY }
```

### Online stav a psaní

```kotlin
data class PresenceInfo(userId, status: PresenceStatus, lastActiveMs: ULong?)
enum class PresenceStatus { ONLINE, UNAVAILABLE, OFFLINE, UNKNOWN }

data class Typist(userId, name: String?)
interface TypingListener { fun onUpdate(roomId: String, typists: List<Typist>) }
```

`Room` má navíc `directUserId: String?` – protějšek soukromé konverzace
(jen když je právě jeden).

### Poloha

```kotlin
data class GeoLocation(latitude: Double, longitude: Double, accuracyM: Double?)
enum class LiveLength { MINUTES15, HOUR1, HOURS8 }

timeline.sendLocation(location, area: LocationArea)
client.startLiveLocation(roomId, length)
client.sendLiveLocation(roomId, location, area)
client.stopLiveLocation(roomId)
parseGeoUri(uri): GeoLocation?
client.ownLiveLocations(): List<OwnLiveLocation>   // roomId, endsMs
client.observeLiveLocations(listener): TaskHandle  // LiveLocationListener.onUpdate(List<LivePerson>)
blurLocation(location, area): GeoLocation
liveLengthMs(length): ULong
```

## Chyby

`PockyException` (sealed): `InvalidCredentials`, `UserDeactivated`, `RateLimited`,
`ServerNotFound`, `Network`, `NotLoggedIn`, `InvalidSession`, `InvalidRecoveryKey`,
`WeakPassphrase`, `WeakPassword`, `InvalidPin`, `InvalidName`, `FileTooLarge`, `LiveLocationEnded`, `Other`.

## Android: inicializace TLS

Síť v Rustu ověřuje certifikáty systémovým úložištěm Androidu
(`rustls-platform-verifier`). Ten potřebuje JVM, takže **před prvním síťovým
voláním** musí proběhnout:

```kotlin
TlsVerifier.init(context)   // com.pokecio.app.tls, volá PockyApp.onCreate
```

JNI funkce je v `core/pocky-ffi/src/android.rs`. Bez inicializace každý síťový
požadavek zpanikaří s hláškou „Expect rustls-platform-verifier to be initialized“.

## Přidání nové funkce do API

1. Logiku napsat v `pocky-core` (+ testy).
2. V `pocky-ffi` přidat tenkou obálku: převod typů, volání přes `run()`.
3. `./scripts/gen-bindings.sh` (nebo rovnou Gradle build).
4. Použít v Kotlinu.

Metodu nepojmenovávat `close` – koliduje s generovanou `close()` v Kotlinu.
