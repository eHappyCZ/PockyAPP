# Rust jádro – `pocky-core`

Cesta: `core/pocky-core/src/`. Crate nemá žádnou závislost na UniFFI.

## Moduly

| Soubor | Obsah |
|---|---|
| `lib.rs` | veřejné exporty, `version()` |
| `client.rs` | sestavení klienta, `SERVER_NAME`, `delete_store` |
| `auth.rs` | přihlášení, obnova session, odhlášení, zobrazované jméno, smazání účtu |
| `session.rs` | typ `Session` a jeho JSON |
| `error.rs` | `CoreError` |
| `sync.rs` | `SyncService`, stav synchronizace |
| `room_info.rs` | detail místnosti, členové a role, úpravy, pozvání, odebrání |
| `rooms.rs` | seznam místností, náhledy zpráv |
| `timeline.rs` | timeline místnosti, odesílání, odpovědi, úpravy, reakce, mazání, fotky, přečtení |
| `media.rs` | stahování a dešifrování médií |
| `people.rs` | hledání lidí, nový soukromý chat, nová skupina, pozvánky |
| `picture.rs` | příprava fotky k odeslání (otočení, zmenšení, bez metadat) |
| `preferences.rs` | volby zařízení z průvodce |
| `push.rs` | registrace push na Synapse, zpráva pro oznámení |
| `profile.rs` | změna zobrazovaného jména a profilové fotky |
| `status.rs` | vlastní stav (text + platnost) v account data, zveřejnění přes presence |
| `encryption.rs` | stav šifrování, obnova, záloha klíčů, bezpečnostní fráze, nová identita, změna hesla |
| `app_lock.rs` | PIN zámku aplikace, pokusy a čekání |
| `changelog.rs` | novinky a opravy jednotlivých verzí, přibalené z `core/pocky-core/changelog.json` |
| `trust.rs` | ověření jiných lidí, změny jejich šifrovací identity |
| `verification.rs` | ověření emoji: vlastní zařízení i jiní lidé |
| `presence.rs` | online stav: sdílení vlastního, zjišťování cizího |
| `typing.rs` | „píše…“: sledování a odesílání |

## client.rs

```rust
pub const SERVER_NAME: &str = "pokecio.com";

pub enum Server<'a> {
    Discover(&'a str),   // dohledání přes .well-known
    Url(&'a str),        // přímá adresa homeserveru
}

pub async fn build_client(server: Server<'_>, data_dir: &Path, passphrase: Option<&str>) -> Result<Client>;
pub async fn delete_store(data_dir: &Path) -> Result<()>;
```

Klient má SQLite úložiště (stav, kryptografie, cache událostí, média) zašifrované
heslem `passphrase` a zapnuté automatické dohledávání historie.

## auth.rs

```rust
pub async fn login(client, username, password, device_name) -> Result<Session>;
pub async fn restore_session(client, session) -> Result<()>;
pub async fn restore_client(session, data_dir, passphrase) -> Result<Client>;
pub async fn logout(client) -> Result<()>;
pub async fn delete_account(client, password) -> Result<()>;   // /account/deactivate s erase: true
pub fn current_session(client) -> Option<Session>;
```

- `login` přijme `karel` i `@karel:pokecio.com`. Prázdné jméno nebo heslo vrátí
  `InvalidCredentials` bez dotazu na server.
- `logout` zneplatní zařízení na serveru. Lokální data **nemaže** – to dělá FFI
  vrstva přes `delete_store`.

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

`Debug` výpis tokeny vynechává. `to_json` / `from_json` slouží k předání
platformě, která JSON jen zašifruje a uloží.

## error.rs

| Varianta | Kdy |
|---|---|
| `InvalidCredentials` | špatné jméno/heslo (`M_FORBIDDEN`) |
| `UserDeactivated` | účet deaktivován |
| `RateLimited` | příliš mnoho pokusů |
| `ServerNotFound` | `.well-known` nebo jméno serveru nejde dohledat |
| `Network` | síťová chyba |
| `NotLoggedIn` | operace vyžaduje přihlášení |
| `InvalidSession` | uložená session je poškozená |
| `Sync`, `Timeline`, `Media` | chyby jednotlivých částí |
| `Storage` | chyba souborového systému |
| `ClientBuild`, `Matrix` | ostatní chyby z matrix-sdk |

## sync.rs

```rust
pub enum SyncState { Idle, Running, Offline, Error, Terminated }

pub async fn create_sync_service(client) -> Result<SyncService>;
pub async fn watch_sync_state(service, on_state: impl FnMut(SyncState));
```

`create_sync_service` zapne cache událostí a sestaví `SyncService` s offline
režimem. `watch_sync_state` nejdřív pošle aktuální stav, pak každou změnu.

## rooms.rs

```rust
pub struct RoomSummary {
    pub id, name, avatar_url, last_message: Option<MessagePreview>,
    pub timestamp_ms, unread_messages, unread_mentions,
    pub is_encrypted, is_direct, is_invite,
}

pub async fn watch_rooms(service, on_update: impl FnMut(Vec<RoomSummary>)) -> Result<()>;
```

- Vynechává místnosti, ze kterých uživatel odešel, a staré verze upgradovaných místností.
- U každé místnosti zapne sledování poslední zprávy (`latest_events`).
- Název: uložený, jinak dopočítaný z členů.
- Avatar: obrázek místnosti, jinak avatar jediného „hrdiny“ (přímé konverzace).
- Náhled (`PreviewKind`): text a emote zkrácené na první řádek a 200 znaků,
  obrázek, video, audio, soubor, poloha (včetně živé), sticker, anketa,
  šifrovaná zpráva, pozvánka.

## timeline.rs

```rust
pub async fn open_timeline(client, sync: Option<&SyncService>, room_id) -> Result<Timeline>;
pub async fn watch_timeline(timeline, on_diffs: impl FnMut(Vec<TimelineDiff>));
pub async fn paginate_backwards(timeline) -> Result<bool>;   // true = začátek místnosti
pub async fn send_text(timeline, text) -> Result<()>;         // Markdown → HTML
pub async fn mark_as_read(timeline) -> Result<()>;
```

- `open_timeline` přihlásí místnost v sliding sync k plné synchronizaci.
- Filtr skrývá technické stavové události: oprávnění, pravidla vstupu,
  viditelnost historie, přístup hostů, aliasy, ACL, připnuté zprávy, pozvánky
  třetích stran, policy pravidla a události spaces.
- Stránka historie je 30 událostí (`PAGE_SIZE`).

### Typy položek

```
TimelineEntry { id, kind }
EntryKind: DateDivider | ReadMarker | TimelineStart | Event(EventEntry)

EventEntry: event_id, sender_id, sender_name, sender_avatar, timestamp_ms,
            is_own, is_edited, content, reactions, in_reply_to, send_state, read_by

EventContent: Text / Notice / Emote { body, html },
              Image { caption, source, width, height },
              Video { caption, thumbnail, width, height },
              Audio, File, Location, LiveLocation, Sticker, Poll,
              Redacted, UnableToDecrypt, State(StateChange), Other

StateChange: Membership { user_id, name, change }, DisplayName, Avatar,
             RoomName, RoomTopic, RoomAvatar, RoomCreated, EncryptionEnabled, Other
```

Živé sdílení polohy (`beacon_info` + `beacon`) sloučí matrix-sdk-ui do jedné položky.

## Akce nad zprávami (timeline.rs)

```rust
pub async fn send_reply(timeline, text, in_reply_to: &str /* event id */) -> Result<()>;
pub async fn edit_text(timeline, item_key, text) -> Result<()>;
pub async fn toggle_reaction(timeline, item_key, key) -> Result<bool>; // true = přidáno
pub async fn redact(timeline, item_key) -> Result<()>;
pub async fn send_image(timeline, bytes, caption: Option<&str>, full_quality) -> Result<()>;
pub async fn send_voice(timeline, bytes, mime, duration_ms, levels: &[f32]) -> Result<()>;
pub async fn send_file(timeline, bytes, filename, mime) -> Result<()>;
pub async fn send_video(timeline, VideoClip, caption) -> Result<()>;   // náhled projde prepare_image
pub async fn max_upload_size(client) -> Result<u64>;
```

- `EventEntry.item_key` určuje zprávu i před odesláním: odeslaná = event id
  (`$…`), místní kopie = `txn:<transaction id>`.
- `EventEntry` nese i `can_reply`, `can_edit` (podle matrix-sdk) a
  `can_redact` (jen vlastní a dosud nesmazané zprávy).
- Odpověď a úprava posílají text jako Markdown, stejně jako `send_text`.
- Hlasová zpráva podle MSC3245: `m.audio` s `org.matrix.msc3245.voice` a
  `org.matrix.msc1767.audio` (délka + waveforma 0–1024). Hrubé úrovně
  hlasitosti převede `waveform()` na 100 sloupečků (maximum v úseku,
  normalizace na nejhlasitější). `EventContent::Audio` nese `source`,
  `duration_ms`, `waveform` a `voice`.
- Soubor a video před nahráním kontrolují limit serveru → `CoreError::FileTooLarge`.
  Název souboru se zbaví cesty. `EventContent::File` má `source`, `size`, `mime`;
  `Video` navíc `source`, `duration_ms`, `size`.

## devices.rs

```rust
pub async fn list_devices(client) -> Result<Vec<DeviceInfo>>;  // aktuální první, pak podle aktivity
pub async fn sign_out_device(client, device_id, password) -> Result<()>;
DeviceInfo { device_id, name, last_seen_ms, is_current, verified }
```

Odhlášení zkusí bez ověření; když server chce UIAA, pošle heslo (špatné heslo
→ `InvalidCredentials`). Aktuální zařízení tudy odhlásit nejde – na to je
`logout`.

## people.rs

```rust
pub async fn search_people(client, term) -> Result<Vec<Person>>;
pub async fn open_direct_chat(client, user_id) -> Result<String>;          // id místnosti
pub async fn create_group(client, name, members: &[String]) -> Result<String>;
pub async fn invite_info(client, room_id) -> Result<InviteInfo>;
pub async fn accept_invite(client, room_id) -> Result<()>;
pub async fn decline_invite(client, room_id) -> Result<()>;
```

- Hledání: adresář uživatelů Synapse (max. 20) + přímé dohledání profilu, když
  hledaný text vypadá jako uživatelské jméno (`karel` → `@karel:<vlastní
  server>`, nebo celé `@…:…`). Synapse totiž v adresáři ve výchozím stavu
  najde jen lidi se sdílenou místností. Sebe sama nevrací.
- Soukromý chat: nejdřív hledá existující (`m.direct`, pak i neoznačené 1:1),
  jinak `create_dm` (šifrovaný).
- Skupina: soukromá (`PrivateChat`), šifrovaná od začátku, s pozvánkami.

## picture.rs

```rust
pub fn prepare_image(bytes: &[u8], full_quality: bool) -> Result<PreparedImage>;
PreparedImage { bytes, mime, width, height }
```

- Snímek se otočí podle EXIF orientace a znovu zakóduje → **EXIF (poloha,
  fotoaparát) zmizí**.
- Bez `full_quality` se delší strana zmenší na `MAX_SIDE` (2048 px).
- PNG s průhledností zůstane PNG, ostatní → JPEG kvalita 85. GIF a animované
  WebP se posílají beze změny (kvůli animaci).
- Formát, který `image` nezná (např. HEIC), vrátí `CoreError::Media`;
  Android ho pak převede přes `ImageDecoder` na JPEG a zkusí znovu.

## media.rs

```rust
pub async fn load_media(client, source: &str, width: u32, height: u32) -> Result<Vec<u8>>;
```

- `source` je `mxc://…` nebo JSON šifrovaného `MediaSource`.
- Nešifrované médium + nenulová velikost → zmenšenina ze serveru.
- Šifrované médium → celý soubor, dešifrovaný.
- Cache je ve šifrovaném úložišti matrix-sdk.

## preferences.rs

```rust
pub async fn load_preferences(client) -> Result<Preferences>;
pub async fn save_preferences(client, &Preferences) -> Result<()>;

Preferences { onboarding_done, chat_style, notification_preview, location_precision,
              share_presence (výchozí false), share_typing (výchozí true) }
ChatStyle: Classic (výchozí), Aura
NotificationPreview: Always, WhenUnlocked (výchozí), Never
LocationPrecision: Exact, Neighborhood (~500 m), City (~5 km, výchozí)
```

- Volby jsou **jen v zařízení**: ukládají se do šifrované SQLite databáze
  matrix-sdk (`custom value` pod klíčem `pocky.preferences`), na server nejdou.
- Odhlášení i nové přihlášení databázi smaže, proto se průvodce po každém novém
  přihlášení ukáže znovu.
- Chybějící nebo poškozená data → výchozí hodnoty.

`auth::display_name` vrátí zobrazované jméno ze serveru. Když není nastavené
nebo server neodpoví, vrátí část ID před dvojtečkou (`@jana:pokecio.com` → `jana`).

## push.rs

```rust
pub const PUSH_GATEWAY: &str; // https://push.pokecio.com/_matrix/push/v1/notify
pub const PUSH_APP_ID: &str;  // com.pokecio.app.android
pub async fn register_pusher(client, push_key, device_name, lang) -> Result<()>;
pub async fn unregister_pusher(client, push_key) -> Result<()>;
pub async fn fetch_notification(client, sync: Arc<SyncService>, room_id, event_id)
    -> Result<Option<PushNotification>>;
```

- Pusher má formát `event_id_only` – brána ani Firebase nevidí obsah.
- `fetch_notification` používá `NotificationClient` z matrix-sdk-ui
  (`SingleProcess`, Android budí hlavní proces aplikace); zprávu stáhne a
  dešifruje. Zpráva odfiltrovaná pravidly / od ignorovaného uživatele /
  smazaná → `None`.

## profile.rs

```rust
pub async fn set_display_name(client, name) -> Result<String>; // vrací očištěné jméno
pub async fn set_avatar(client, bytes) -> Result<String>;       // mxc:// URL
pub async fn remove_avatar(client) -> Result<()>;
```

- Jméno: mezery na krajích pryč, vícenásobné mezery sloučené, 1–64 znaků,
  jinak `CoreError::InvalidName`.
- Fotka projde `picture::prepare_avatar`: otočení podle EXIF, výřez středového
  čtverce, zmenšení na 512 × 512 (menší se nezvětšuje), JPEG bez metadat.

## status.rs

```rust
pub async fn set_status(client, text, until_ms: Option<u64>) -> Result<OwnStatus>;
pub async fn clear_status(client) -> Result<()>;
pub async fn own_status(client) -> Result<Option<OwnStatus>>;
pub async fn expire_status(client, now_ms) -> Result<bool>;
pub async fn watch_own_status(client, on_update) -> Result<()>;
```

- Text (1–60 znaků) jde jako `status_msg` v Matrix presence – vidí ho lidé se
  sdílenou místností, i v jiných klientech. Smazání posílá prázdný text.
- Stav (text + `until_ms`) je v account data `com.pokecio.status`, takže ho
  vidí všechna vlastní zařízení. Zrušený stav je prázdný obsah `{}`
  (account data nejdou smazat).
- `own_status` vrací jen platný stav; vypršelý se bere jako žádný, takže další
  odeslání presence (např. změna „Ukazovat, že jsem online“) starý text nepošle.
- `expire_status` se před smazáním zeptá serveru, aby nesmazal novější stav
  z jiného zařízení, když tu neběží synchronizace.
- Převod starého lokálního stavu na server spouští i `create_sync_service` (na pozadí, při chybě se zkusí při dalším spuštění).
- `watch_own_status` nejdřív převede starý lokální stav (`pocky.status`) na
  server, pokud tam žádný není, pak hlásí aktuální stav a změny z jiných zařízení.
- `fetch_presence` vrací `status` vždy; stav online jen při vlastním sdílení.

## encryption.rs: bezpečnostní fráze, nová identita, heslo

```rust
pub fn passphrase_strength(passphrase) -> PassphraseStrength;
pub async fn enable_recovery(client, password, passphrase) -> Result<String>;
pub async fn reset_identity(client, password, passphrase) -> Result<String>;
pub async fn change_recovery_passphrase(client, passphrase) -> Result<String>;
pub async fn change_password(client, current, new_password, sign_out_others) -> Result<()>;
```

- Funkce zakládající zálohu vyžadují bezpečnostní frázi (viz [security.md](security.md))
  a vrací nový klíč pro obnovení jako náhradní cestu.

- Pro situaci „nemám klíč ani jiné zařízení“. Smaže zálohu klíčů na serveru,
  vypne úložiště tajemství, vytvoří nové cross-signing klíče (potvrzení heslem
  přes UIAA) a založí novou zálohu. Vrací nový klíč pro obnovení.
- Záloha se smaže ještě před ověřením hesla (tak to dělá matrix-sdk). Při špatném
  hesle vrátí `InvalidCredentials` a reset jde bezpečně zopakovat.
- Server s OAuth (schválení v prohlížeči) zatím není podporovaný.

## app_lock.rs

```rust
pub fn pin_allowed(pin) -> bool;
pub async fn enable_app_lock(client, pin, biometrics, timeout_seconds) -> Result<()>;
pub async fn unlock_app(client, pin) -> Result<UnlockResult>;   // Unlocked, WrongPin, Blocked
pub async fn disable_app_lock(client, pin) -> Result<UnlockResult>;
```

Hash PINu (PBKDF2-SHA256) a počítadlo pokusů jsou v šifrovaném úložišti pod
`pocky.app_lock`. 5 pokusů, pak čekání 30 s, které se zdvojnásobuje, nejvýš hodina.

## trust.rs: ověřování lidí

```rust
pub async fn user_trust(client, user_id) -> Result<TrustLevel>;
pub async fn acknowledge_identity(client, user_id) -> Result<()>;
pub async fn watch_identity_warnings(client, room_id, on_update) -> Result<()>;
pub async fn user_display_name(client, user_id) -> Option<String>;
```

- `TrustLevel`: `NoIdentity`, `Unverified`, `Verified`, `VerificationBroken`
  (ověřený člověk, kterému se pak změnila identita).
- `watch_identity_warnings` sleduje změny identit členů chatu přes
  `subscribe_to_identity_status_changes`. Hlásí seznam lidí, kterým se identita
  změnila (`was_verified` říká, jestli byli předtím ověření).
- `acknowledge_identity` je „Rozumím“: u neověřeného člověka si zapamatuje novou
  identitu (`pin`), u dříve ověřeného zruší požadavek na ověření
  (`withdraw_verification`).
- `VerificationFlow::request_user` začne ověření jiného člověka. Žádost jde do
  společného chatu 1:1 (když chybí, SDK ho založí). `watch_verification_requests`
  zachytí i příchozí žádosti v chatu, nejen žádosti z vlastních zařízení.

### Stavové zprávy a Historie změn (timeline.rs)

- Hlavní časová osa (`open_timeline`) ukazuje jen důležité stavové události
  (`in_chat`): zapnuté šifrování, název a fotku místnosti a sdílení polohy.
- Příchody, odchody, pozvánky, změny jmen a fotek členů, popis, vytvoření chatu
  a ostatní stavové události
  ukazuje `open_history`: druhá časová osa jen se stavovými událostmi. Na
  platformě je to stejný objekt `RoomTimeline` (stránkování, odběr změn).

### Album chatu (timeline.rs)

- `open_media` je samostatná časová osa jen s fotkami, videi, zvukem a soubory
  (plus šifrované události, které se mohou dešifrovat na médium).
- U fotek je `source` náhled (`thumbnail_source`, pokud ho odesílatel přiložil,
  jinak originál) a `original` vždy plná fotka. Bubliny a album načítají jen
  náhled, prohlížeč dotáhne originál.

### Potvrzení přečtení (timeline.rs)

- `EventEntry.read_by: Vec<ReadReceipt>` (`user_id`, `timestamp_ms`) bez
  vlastního potvrzení.
- Volba `send_read_receipts` (výchozí zapnutá). Když je vypnutá, `mark_as_read`
  posílá `m.read.private`: vlastní zařízení se sladí, ostatní nic nevidí.

### Štíty u zpráv (timeline.rs)

`EventEntry.shield: Option<Shield>` s `danger` (červený / šedý) a `reason`. Bere se
z `get_shield(false)`, tedy mírná varianta: zprávy od neověřených lidí štít
nemají, štít dostanou jen skutečné problémy (neověřené nebo neznámé zařízení,
změněná identita, nezašifrovaná zpráva v šifrovaném chatu, podvržený odesílatel).

## changelog.rs

```rust
pub fn changelog(lang, up_to_version_code) -> Vec<Release>;
```

- Jediný zdroj je `core/pocky-core/changelog.json`. Čte ho web (stránka
  Changelog, manifest aktualizace) a jádro ho přibalí přes `include_str!`, takže
  aplikace ho má i bez připojení.
- Každá verze má `new` (novinky) a `fixed` (opravy), obojí `cs` i `en`.
- Vrací verze od nejnovější a jen do `up_to_version_code`, takže záznam
  připravený pro příští verzi se ve starší aplikaci neukáže.

## presence.rs

```rust
pub async fn apply_presence_preference(client) -> Result<()>;
pub async fn fetch_presence(client, user_id: &str) -> Result<UserPresence>;

UserPresence { user_id, presence: Online | Unavailable | Offline | Unknown, last_active_ms }
```

- matrix-sdk posílá při každé synchronizaci vlastní stav (výchozí „online“).
  `create_sync_service` proto nejdřív zavolá `apply_presence_preference`:
  sdílení vypnuté → synchronizace hlásí `offline`.
- Změna `share_presence` v `save_preferences` stav odešle na server hned.
- Sliding sync cizí stav nedoručuje, proto `fetch_presence` volá
  `GET /presence/{userId}/status` pro konkrétního člověka.
- **Vzájemnost:** bez vlastního sdílení vrací `fetch_presence` vždy `Unknown`.
- Chyba serveru (např. vypnutá presence na Synapse) → `Unknown`.
- „Online“, ale neaktivní déle než 10 minut → `Unavailable`.

## typing.rs

```rust
pub async fn watch_typing(client, on_update: impl FnMut(room_id: String, Vec<TypingUser>)) -> Result<()>;
pub async fn send_typing(client, room_id: &str, typing: bool) -> Result<()>;

TypingUser { user_id, name: Option<String> }
```

- Jeden handler `m.typing` pro všechny místnosti (typing rozšíření sliding
  sync zapíná `RoomListService`); vlastní uživatel je odfiltrovaný.
- **Vzájemnost:** při `share_typing = false` `watch_typing` hned skončí a
  `send_typing` nic neposílá.
- Omezování četnosti odesílání řeší i matrix-sdk (`typing_notice`).

## Akce nad místností (rooms.rs)

```rust
pub async fn set_favourite(client, room_id, favourite: bool) -> Result<()>; // značka m.favourite
pub async fn set_muted(client, room_id, muted: bool) -> Result<()>;         // pravidlo oznámení Mute
pub async fn mark_room_read(client, room_id) -> Result<()>;                 // potvrzení na poslední zprávu
pub async fn leave_room(client, room_id) -> Result<()>;
```

`RoomSummary` má navíc `is_favourite`, `is_muted`; `MessagePreview.delivery`
(`Sending | Failed | Sent | Read`) jen u vlastní poslední zprávy – `Read`, když
na ní má potvrzení přečtení kdokoli jiný. `watch_rooms` řadí připnuté nahoru a
přepočítá seznam i při změně pravidel oznámení.

## room_info.rs

```rust
pub async fn room_details(client, room_id) -> Result<RoomDetails>;
pub async fn set_room_name / set_room_topic / set_room_avatar(client, room_id, …);
pub async fn invite_people(client, room_id, user_ids) -> Result<()>;
pub async fn remove_member(client, room_id, user_id) -> Result<()>;
```

- `RoomDetails`: název, popis, fotka (u 1:1 fotka protějšku), šifrování,
  členové (`MemberInfo` s rolí Admin ≥ 100 / Moderator ≥ 50 / Member, pozvaní,
  `can_remove`), oprávnění `can_edit_name/topic/avatar`, `can_invite` podle
  power levels.
- Řazení členů: přijatí před pozvanými, správci první, pak podle jména.
- Fotka skupiny projde `prepare_avatar` (čtverec 512 px, bez metadat).

## Soukromé chaty bez `m.direct` (rooms.rs)

`is_direct` platí i pro místnost, která není označená v `m.direct`, ale nemá
vlastní název, má nejvýš 2 aktivní členy a jednoho „hero“. `direct_user_id` je
pak ten hero. Díky tomu fungují kulaté avatary, filtr Skupiny a online stav i u
starších konverzací.

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

- Přesnost bere z `LocationPrecision`: přesně, čtvrť (mřížka 500 m), město
  (mřížka 5 km). Bod se přesune do středu buňky, takže blízká místa dají stejný
  výsledek a skutečná poloha z něj nejde dopočítat. `u=` v `geo:` URI je
  velikost buňky, počet desetinných míst se zkrátí (6 / 4 / 3).
- Živá poloha (MSC3489): `beacon_info` je stavová událost a **není šifrovaná**.
  Server tak vidí, že a jak dlouho člověk sdílí, proto se do ní nedává popisek.
  Souřadnice jdou v `m.beacon` a ty šifrované jsou.
- Délka sdílení: 15 min, 1 h, 8 h (`LiveDuration`).
- `should_send_beacon(last, fix, precision, rooms)` rozhoduje, jestli poslat
  novou polohu (aby se nezahltil Synapse, výchozí limit je 1 zpráva / 5 s):
  - mezi dvěma odesláními aspoň 5 s × počet chatů, do kterých se sdílí;
  - přesná poloha: v autě (≥ 5 m/s) každých 5 s, chůze (≥ 0,7 m/s) každých
    10 s, jinak 60 s, a jen při posunu větším než přesnost GPS (min. 10 m);
  - bez pohybu jednou za 3 min (znamení, že sdílení běží);
  - přibližná poloha: jen při přechodu do jiného čtverce mřížky, jinak po 5 min.
- `send_beacon` po vypršení nebo ukončení vrátí `CoreError::LiveLocationEnded`,
  aplikace pak zastaví sledování polohy. `stop_live_location` je bezpečné volat
  opakovaně.
- Popisek u přijaté polohy se nezobrazí, pokud obsahuje jen `geo:` URI
  (tak posílá body PockyAPP i Element).

## live_location.rs

```rust
pub async fn own_live_shares(client) -> Vec<OwnLiveShare>;
pub async fn watch_live_locations(client, on_update: FnMut(Vec<LiveShare>));
```

- `own_live_shares`: v kterých místnostech mám živý `beacon_info` a do kdy
  (`ts + timeout`). Aplikace podle toho po restartu obnoví sdílení.
- `watch_live_locations`: `LiveLocationsObserver` pro každou připojenou
  místnost, každých 30 s přibere nové a zahodí opuštěné. Posílá seznam
  aktivních sdílení (jméno, fotka, poslední `geo:` URI, čas, konec, `is_own`).
  Prošlá sdílení vypadnou nejpozději při dalším ticku. Stejný seznam se
  znovu neposílá.

## Poznámka k `recursion_limit`

Async typy z matrix-sdk-ui jsou hluboce vnořené, proto mají `pocky-core` i
`pocky-ffi` v `lib.rs` `#![recursion_limit = "256"]`. matrix-sdk to dělá stejně.
