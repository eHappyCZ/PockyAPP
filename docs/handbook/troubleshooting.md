# Řešení problémů

## Aplikace spadne při startu nebo odhlášení (SIGABRT, `panic_in_cleanup`)

**Příčina:** objekt z jádra se uvolnil mimo tokio runtime. SQLite pool z
matrix-sdk při zavírání runtime potřebuje.

**Řešení:** všechny objekty v `pocky-ffi` uvolňovat s `RUNTIME.enter()` (viz
`Drop` u `PockyClient` a `RoomTimeline`). Nové FFI objekty držící `Client` nebo
`Timeline` musí dělat totéž.

Jak najít příčinu nativního pádu:

```sh
adb logcat -d -b crash
```

Adresy z výpisu převést na symboly přes `llvm-nm` z NDK nad
`target/aarch64-linux-android/android-dev/libpocky_ffi.so`.

## „Expect rustls-platform-verifier to be initialized“

Síť v Rustu na Androidu se volá před `TlsVerifier.init(context)`. Zkontrolovat,
že `AndroidManifest.xml` má `android:name=".PockyApp"` a že `PockyApp.onCreate`
inicializaci volá. V UI se to projeví obecnou chybou „Něco se pokazilo“.

## Rust panika na Androidu není v logcatu

Rust píše paniky na stderr, které Android zahazuje. Výjimky z jádra přicházejí
do Kotlinu jako `PockyException.Other` se zprávou paniky – dočasně je vypsat
(`Log.w`) a po vyřešení výpis odstranit.

## `queries overflow the depth limit!`

Hluboce vnořené async typy z matrix-sdk-ui. Řeší `#![recursion_limit = "256"]`
v `lib.rs` (je v obou crates). U testů pomůže i `Box::pin(...)` kolem budoucnosti.

## Kotlin: „Conflicting overloads: fun close()“

FFI metoda se jmenuje `close`. UniFFI generuje vlastní `close()`; metodu přejmenovat
nebo odstranit.

## Seznam místností nemá náhledy posledních zpráv

matrix-sdk počítá poslední zprávu líně a u místností, kde nahoře není zpráva
(reakce, členství), musí dohledat historii. Vyžaduje to:

- `with_enable_automatic_back_pagination(true)` v `build_client`,
- `latest_events().listen_to_room(...)` pro každou místnost (dělá `watch_rooms`).

## Šifrované zprávy se nezobrazují

- **Zařízení není ověřené:** v seznamu místností je pruh „Toto zařízení není
  ověřené“. Ověřit klíčem pro obnovení nebo frází.
- **Zařízení je ověřené, ale staré zprávy zůstaly nečitelné:** strategie
  „stáhnout klíč při chybě dešifrování“ se uplatní jen v okamžiku, kdy zpráva
  poprvé dorazí. Zprávy přijaté před obnovou se znovu nezkusí. Proto
  `recover()` po úspěchu stáhne klíče ze zálohy pro všechny šifrované místnosti
  a `open_timeline` je stáhne pro otevíranou místnost. matrix-sdk pak zprávy v
  cache dešifruje sám.
- **Klíč v záloze není:** zprávu odeslalo zařízení, které zálohu nemělo
  zapnutou. Pomůže jen ověření emoji z tohoto zařízení (krok 8c), pokud klíč
  ještě má.

## Disk je plný

`target/` dokáže narůst na desítky GB:

```sh
cargo clean
cd apps/android && ./gradlew clean
```

## Gradle: „Could not find org.rustls:rustls-platform-verifier:“

Verze se čte z `Cargo.lock`. Zkontrolovat, že tam je
`rustls-platform-verifier-android` a že repozitář v `settings.gradle.kts`
ukazuje na `https://github.com/rustls/rustls-platform-verifier/raw/maven-archive/android-release-support/maven/`.

## Gradle: AAR metadata vyžadují vyšší compileSdk

Nové verze Compose vyžadují novější platformu. Zvednout `compileSdk` v obou
modulech a nainstalovat platformu přes SDK Manager.

## Emulátor: „Try out your stylus“ přes obrazovku

Systémová nabídka emulátoru při zaostření textového pole. S aplikací nesouvisí,
zavřít tlačítkem Zpět.

## Čas a dny v aplikaci anglicky

Formát se řídí jazykem systému. Emulátor je anglicky; s češtinou v systému se
ukáže „10:39“ a „po“.
