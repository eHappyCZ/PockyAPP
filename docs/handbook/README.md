# PockyAPP – příručka vývojáře

[English](en/README.md)

Nativní Matrix klient komunity Pokecio. Homeserver je Synapse na `matrix.pokecio.com`,
uživatelé se přihlašují jménem serveru `pokecio.com`.

Sdílené jádro je v Rustu nad `matrix-rust-sdk`. Nativní platformy ho volají přes
UniFFI, prohlížeč přes WebAssembly. Hotová je aplikace pro Android (Kotlin + Jetpack
Compose) a webová aplikace (Svelte + TypeScript); iOS, macOS, Windows a Linux jsou v plánu.

## Obsah

| Dokument | O čem je |
|---|---|
| [setup.md](setup.md) | Co nainstalovat, jak sestavit jádro a Android aplikaci |
| [architecture.md](architecture.md) | Vrstvy, tok dat a hlavní rozhodnutí |
| [core.md](core.md) | Rust jádro `pocky-core` – moduly a funkce |
| [ffi.md](ffi.md) | API, které jádro nabízí platformám (Kotlin) |
| [android.md](android.md) | Struktura Android aplikace, obrazovky, stav |
| [security.md](security.md) | Soukromí, úložiště, klíče, zámek aplikace, odhlášení |
| [testing.md](testing.md) | Testy bez sítě a živé testy proti serveru |
| [troubleshooting.md](troubleshooting.md) | Známé problémy a jejich řešení |
| [roadmap.md](roadmap.md) | Co je hotové, co chybí a co přijde dál |

## Stav v kostce

Na Androidu funguje:

- přihlášení, obnova relace, odhlášení, šifrované lokální úložiště,
- sliding sync, seznam chatů s náhledy, filtry a stavy,
- chaty s odpověďmi, úpravami, reakcemi, potvrzením přečtení, informacemi o zprávě a přeposíláním,
- fotky, videa, soubory, hlasovky, GIFy, album chatu,
- koncové šifrování: ověření zařízení i lidí, záloha klíčů s bezpečnostní frází,
  nová identita, štíty u zpráv,
- oznámení dešifrovaná v zařízení, živá poloha, mapy, tablety,
- zámek aplikace, zákaz snímků obrazovky, změna hesla, smazání účtu, aktualizace v aplikaci.

Webová aplikace umí čtení a psaní, ověřování, stavy, polohu a soubory; co jí oproti
Androidu chybí, je v [roadmap.md](roadmap.md).

## Rychlý start

```sh
rustup default stable
cargo build && cargo test

cd apps/android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

Podrobnosti v [setup.md](setup.md).
