# Architektura

## Vrstvy

```
┌──────────────────────────────────────────────┐
│ Android UI (Compose)                         │  obrazovky, jen zobrazení
├──────────────────────────────────────────────┤
│ Android stav (ViewModel, Controllery)        │  StateFlow, životní cyklus
├──────────────────────────────────────────────┤
│ Kotlin bindingy (generované UniFFI)          │  com.pokecio.app.core
├──────────────────────────────────────────────┤
│ pocky-ffi (Rust)                             │  FFI typy, tokio runtime, úlohy
├──────────────────────────────────────────────┤
│ pocky-core (Rust)                            │  veškerá logika klienta
├──────────────────────────────────────────────┤
│ matrix-sdk, matrix-sdk-ui                    │  protokol, šifrování, úložiště
└──────────────────────────────────────────────┘
```

Základní pravidlo: do UI nepatří logika. Co by potřebovaly dvě platformy,
patří do `pocky-core`.

## Proč dva Rust crates

- **`pocky-core`** je čistý Rust bez UniFFI. Dá se testovat běžnými testy a bude
  sloužit všem platformám beze změny.
- **`pocky-ffi`** jen převádí typy jádra na typy pro UniFFI, drží tokio runtime a
  spouští dlouhé úlohy (sync, sledování seznamů). Žádná vlastní logika.

## Tok dat

### Přihlášení

```
LoginScreen → MainViewModel.login()
  → createClient(dataDir, passphrase)        nové prázdné úložiště
  → client.login(user, password, device)     vrací session jako JSON
  → SessionStore.saveSession(json)           šifrovaně (Keystore)
  → RoomListController + MediaLoader         stav LoggedIn
```

### Start aplikace

```
MainViewModel.restore()
  → SessionStore.loadSession()
  → restoreClient(session, dataDir, passphrase)   funguje i offline
  → LoggedIn, sync se spustí, když je aplikace v popředí
```

### Synchronizace a seznam místností

- `SyncService` z matrix-sdk-ui běží přes **sliding sync** (Synapse ho podporuje
  nativně, `org.matrix.simplified_msc3575`).
- Je v offline režimu – při výpadku sítě přejde do stavu `Offline` a sám se vrátí.
- Seznam místností posílá jádro jako **celý seřazený seznam** při každé změně.
  Pro desítky až stovky místností to stačí a Kotlin je jednodušší.
- Sync běží jen v popředí (`LifecycleStartEffect` v `MainActivity`).

### Timeline

- `Timeline` z matrix-sdk-ui řeší úpravy, smazání, reakce, odpovědi, potvrzení
  přečtení, dešifrování i odesílací frontu.
- Do Kotlinu jdou **jen rozdíly** (`TimelineChange`: insert, set, remove, reset…),
  protože timeline se mění často a může mít stovky položek.
- Kotlin (`RoomController`) rozdíly aplikuje na seznam a `buildRows` z něj
  sestaví řádky pro zobrazení.

### Média

- Zdroj média je neprůhledný řetězec: `mxc://…` nebo JSON šifrovaného souboru.
- `loadMedia(source, w, h)` u nešifrovaných médií žádá server o zmenšeninu,
  šifrovaná stáhne a dešifruje. Výsledek se cachuje v šifrovaném úložišti.
- Android drží dekódované obrázky v paměťové LRU cache.

## Vlákna a runtime

- `pocky-ffi` má vlastní vícevláknový tokio runtime (`pocky-core` vlákna).
- Každé volání z Kotlinu se spustí na tomto runtime (`run()` v `lib.rs`).
- Objekty z jádra (`PockyClient`, `RoomTimeline`) se uvolňují v kontextu runtime.
  Bez toho SQLite pool z matrix-sdk při uvolnění na hlavním vlákně Androidu
  zpanikařil a aplikace spadla.
- Dlouhé úlohy (sledování seznamu, timeline, stavu) vrací `TaskHandle`. Úloha
  skončí po `cancel()` nebo když Kotlin objekt zahodí.

## Server

| Co | Hodnota |
|---|---|
| Jméno serveru | `pokecio.com` |
| Homeserver (z `.well-known`) | `https://matrix.pokecio.com` |
| Přihlášení | jen `m.login.password` |
| Registrace | vypnutá (403) |
| Sliding sync | ano |
| OIDC / QR přihlášení | ne |

Klient při novém přihlášení dohledá homeserver přes `.well-known`. Při obnově
session použije uloženou adresu homeserveru, takže start funguje i bez sítě.

## Klíčová rozhodnutí

| Rozhodnutí | Důvod |
|---|---|
| Session je pro Kotlin jen JSON řetězec | Token se v Kotlinu nikdy neobjeví jako pole a nevypíše se omylem do logu |
| Vlastní tokio runtime v FFI | Bezpečné uvolňování objektů z libovolného vlákna |
| Seznam místností celý, timeline po rozdílech | Jednoduchost tam, kde stačí; výkon tam, kde je potřeba |
| Stavové změny jako struktura, ne věta | Text si sestaví každá platforma ve svém jazyce |
| Technické stavové události skryté | Oprávnění, pravidla vstupu, ACL apod. jen zahlcují timeline |
| Automatické dohledávání historie zapnuté | Jinak chybí náhled poslední zprávy u místností, kde je nahoře reakce nebo změna členství |
| Navigace bez Compose Navigation | Dvě obrazovky, stav v ViewModelu přežije otočení displeje |
| Načítání obrázků bez Coil | Cache a dešifrování řeší jádro, v Kotlinu stačí dekódování |
