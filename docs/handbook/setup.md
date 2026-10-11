# Instalace a sestavení

## Potřebné nástroje

| Nástroj | Verze / poznámka |
|---|---|
| Rust | stable (ověřeno 1.99), přes `rustup` |
| Rust cíle pro Android | `aarch64-linux-android`, `armv7-linux-androideabi`, `x86_64-linux-android` |
| `cargo-ndk` | 4.x (`cargo install cargo-ndk`) |
| Android Studio | obsahuje JDK (JBR) – systémová Java není potřeba |
| Android SDK | platforma 37 (compileSdk), NDK 30 |
| `rustfmt`, `clippy` | `rustup component add rustfmt clippy` |

Jednorázové nastavení:

```sh
rustup default stable
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
rustup component add rustfmt clippy
cargo install cargo-ndk
```

Pro Gradle z terminálu je potřeba JDK z Android Studia:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

`apps/android/local.properties` (není v gitu) obsahuje cestu k SDK:

```
sdk.dir=/Users/<uživatel>/Library/Android/sdk
```

## Struktura repozitáře

```
Cargo.toml                 workspace
core/
  pocky-core/              logika nad matrix-sdk (bez závislosti na UniFFI)
  pocky-ffi/               UniFFI vrstva, cdylib pro platformy
  uniffi-bindgen/          generátor bindingů
apps/
  android/                 Gradle projekt (:app, :core-bindings)
  apple/                   zatím jen README
design/tokens.json         barvy značky
design/icons/              sada ikon (SVG)
docs/                      dokumentace
scripts/
  build-android.sh         cargo-ndk + generování bindingů
  gen-bindings.sh          jen generování Kotlin bindingů
  gen-icons.py             ikony z design/icons → Android drawables
  release-android.sh       release APK (optimalizované jádro, R8, podpis)
```

## Rust jádro

```sh
cargo build
cargo test
cargo clippy --all-targets
cargo fmt
```

První build trvá kolem 1,5 minuty (matrix-sdk je velký), další jsou rychlé.

## Android

```sh
cd apps/android
./gradlew assembleDebug
```

Gradle před každým buildem spustí `scripts/build-android.sh` (task `buildRustCore`
v modulu `:core-bindings`). Skript:

1. přeloží `pocky-ffi` přes `cargo-ndk` do
   `apps/android/core-bindings/src/main/jniLibs/<abi>/libpocky_ffi.so`,
2. vygeneruje Kotlin bindingy do
   `apps/android/core-bindings/src/main/kotlin/com/pokecio/app/core/pocky_ffi.kt`.

Obojí je generované a je v `.gitignore`.

Výchozí nastavení skriptu lze změnit proměnnými:

| Proměnná | Výchozí | Význam |
|---|---|---|
| `POCKY_ABIS` | `arm64-v8a` | ABI pro Android, např. `"arm64-v8a x86_64"` |
| `POCKY_PROFILE` | `android-dev` | Cargo profil (`release` pro produkci) |
| `ANDROID_HOME` | `~/Library/Android/sdk` | cesta k SDK |

Profil `android-dev` je neoptimalizovaný build bez ladicích informací – rychlý
překlad, ale velká `.so` (kolem 100 MB). Release build s LTO bude výrazně menší.

### Instalace na emulátor / telefon

```sh
~/Library/Android/sdk/emulator/emulator -avd Pokecio_Test &
cd apps/android
./gradlew installDebug
```

### Android Studio

Otevřít složku `apps/android`. Build přes Studio spouští stejný `buildRustCore`,
takže není potřeba nic pouštět ručně.

## Verze knihoven

| Knihovna | Verze |
|---|---|
| matrix-sdk, matrix-sdk-ui | 0.19.1 |
| UniFFI | 0.32.2 |
| Gradle | 9.8.0 |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.4.20 |
| Compose BOM | 2026.09.00 |
| MapLibre Android | 13.6.1 |
| minSdk / targetSdk / compileSdk | 28 / 36 / 37 |

Verze Android knihoven jsou v `apps/android/gradle/libs.versions.toml`.

## Mapa

Mapové dlaždice jsou na vlastním serveru `maps.pokecio.com`, kde běží
go-pmtiles (`pmtiles serve`) za nginx. Archiv je `cz-sk.pmtiles` (Protomaps
Basemap, Česko a Slovensko, zoom 0–15) ve složce
`/www/wwwroot/maps.pokecio.com/tiles`.

- TileJSON: `https://maps.pokecio.com/tiles/cz-sk.json`
- dlaždice: `https://maps.pokecio.com/tiles/cz-sk/{z}/{x}/{y}.mvt`

Styl mapy, písma a ikony jsou přibalené v aplikaci
(`apps/android/app/src/main/assets/map`, asi 0,9 MB). Generuje je
`scripts/map-style`:

```sh
cd scripts/map-style
npm install
node build.mjs
```

Barvy bere z `design/tokens.json`. Písma (Noto Sans, latinka a interpunkce) a
ikony se stáhnou jednorázově z podkladů Protomaps. Aplikace pak za běhu
komunikuje jen s `maps.pokecio.com`.

Mapa ukazuje jen Česko a Slovensko. Generátor stáhne hranice z OpenStreetMap
(relace 51684 a 14296 přes polygons.openstreetmap.fr), sloučí je, zjednoduší na
asi 30 m a vloží do stylu jako GeoJSON. Mimo ČR a SR je mapa úplně černá.
Německo, Rakousko a Polsko (`UPCOMING`) jsou tmavé siluety s obrysem a nápisem
„Připravujeme“ uprostřed. Hranici ČR a SR zvýrazňuje fialová linka. Kameru `PockyMap` drží nad oblastí archivu a nedovolí oddálit pod
zoom 5,5.

Při výměně archivu (třeba za celou Evropu) se mění `TILES` a `COUNTRIES`
v `build.mjs` (nebo se maska vypustí) a `CoveredArea` a `HomeCenter`
v `ui/map/PockyMap.kt`.

## Místo na disku

Složka `target/` rychle roste (Mac + Android buildy). Profil `dev` proto generuje
ladicí informace jen pro naše crates, ne pro závislosti. Když dojde místo:

```sh
cargo clean
cd apps/android && ./gradlew clean
```

## Release APK

```sh
scripts/release-android.sh
```

- Jádro v profilu `release` (LTO, 1 codegen unit, bez symbolů), jen
  `arm64-v8a` (jiné ABI přes `POCKY_ABIS`).
- Android kód přes R8 (`isMinifyEnabled`, `isShrinkResources`), pravidla pro
  JNA / UniFFI / rustls jsou v `core-bindings/consumer-rules.pro`.
- První build trvá kolem 18 minut (LTO nad matrix-sdk), další jsou rychlejší.
- Výsledek: `apps/android/app/build/outputs/apk/release/app-release.apk`,
  zhruba 70 MB (vývojové APK ~200 MB). Většinu tvoří `libpocky_ffi.so`,
  uložená nekomprimovaně kvůli rychlejšímu startu (rozhodnutí: nechat tak,
  komprese by ušetřila při stahování, ale první start by byl pomalejší
  a aplikace by v telefonu zabrala víc místa).

### Podpis

Klíč nikdy do gitu ani do kódu – jen přes proměnné prostředí:

```sh
keytool -genkeypair -v -keystore ~/pockyapp-release.jks -alias pockyapp \
  -keyalg RSA -keysize 4096 -validity 10000

export POCKY_KEYSTORE=~/pockyapp-release.jks
read -s "POCKY_KEYSTORE_PASSWORD?Heslo k úložišti: " && export POCKY_KEYSTORE_PASSWORD && echo
export POCKY_KEY_ALIAS=pockyapp
read -s "POCKY_KEY_PASSWORD?Heslo ke klíči: " && export POCKY_KEY_PASSWORD && echo
scripts/release-android.sh
```

Bez `POCKY_KEYSTORE` se APK podepíše vývojovým klíčem – jen k testování,
nešířit. Soubor `.jks` je v `.gitignore`; zálohuj ho bezpečně, bez něj nejde
vydat aktualizaci.
