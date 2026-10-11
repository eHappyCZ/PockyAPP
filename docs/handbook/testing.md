# Testy

## Testy bez sítě

```sh
cargo test
cargo clippy --all-targets
```

Běží bez sítě a bez přihlašovacích údajů. Pokrývají:

| Oblast | Co se ověřuje |
|---|---|
| Klient | sestavení s adresou serveru, mazání úložiště |
| Přihlášení | odmítnutí prázdných údajů, obnova session offline, odhlášení bez session |
| Session | JSON tam a zpět, poškozený JSON, neplatné user_id, `Debug` bez tokenů |
| Náhledy | text, obrázek, šifrovaná zpráva, poloha, stavová událost, zkracování textu |
| Timeline | HTML u textu, média, poloha, skrývání technických událostí, mapování členství |
| Média | zdroj `mxc://` i šifrovaný tam a zpět, neplatný zdroj |
| FFI | uvolnění klienta mimo runtime, obnova a export session, prázdné úložiště u nového klienta, stav syncu, sync vyžaduje přihlášení, odhlášení zastaví sync a smaže data, neznámá místnost |

## Živé testy proti pokecio.com

Jsou označené `#[ignore]` a pouští se ručně. Testy s účtem se na konci vždy
odhlásí, takže na účtu nezůstane testovací zařízení.

```sh
read -s "POCKY_TEST_PASSWORD?Heslo: " && export POCKY_TEST_PASSWORD; echo
POCKY_TEST_USER=pokeapp cargo test -p pocky-core -- --ignored --nocapture
```

| Test | Co dělá | Údaje |
|---|---|---|
| `live_wrong_password_is_rejected` | špatné heslo → `InvalidCredentials` | ne |
| `live_login_and_logout` | dohledání serveru, přihlášení, odhlášení, token neplatí | ano |
| `live_room_list` | sync, seznam místností (vypíše názvy a druhy náhledů) | ano |
| `live_encryption_status` | vypíše stav šifrování účtu (jen ano/ne a počty) | ano |
| `live_wrong_recovery_key_is_rejected` | falešný klíč pro obnovení → `InvalidRecoveryKey` | ano |
| `live_timeline` | otevře nešifrovanou místnost, načte stránku historie (vypíše jen druhy položek) | ano |
| `live_user_verification` | dva účty se navzájem ověří přes emoji | dva účty |
| `live_self_verification` | nové zařízení stejného účtu se ověří | ano |
| `live_change_password_and_back` | změní heslo druhého účtu a vrátí ho; špatné současné heslo odmítne | druhý účet |

Jeden test: `cargo test -p pocky-core live_timeline -- --ignored --nocapture`.

Logy matrix-sdk:

```sh
RUST_LOG=matrix_sdk::latest_events=debug,matrix_sdk::event_cache=info cargo test ...
```

Výpisy živých testů nikdy neobsahují tokeny ani text zpráv.

## Android

Automatické testy pro Android zatím nejsou. Ruční kontrola na emulátoru
`Pokecio_Test` (Android 17, arm64):

1. přihlášení se špatným heslem → „Nesprávné jméno nebo heslo.“,
2. přihlášení → zástupné řádky, pak seznam místností,
3. restart aplikace → zůstane přihlášený,
4. nová zpráva z jiného klienta → místnost nahoře s odznakem,
5. otevření místnosti → odznak zmizí, posun nahoru načte historii,
6. odeslání zprávy → ○, pak ✓,
7. režim Letadlo → pruh „Offline“, zpráva čeká a po připojení se odešle,
8. odhlášení → login, zařízení zmizí ze seznamu relací na serveru.

Screenshot z emulátoru:

```sh
adb exec-out screencap -p > screen.png
```
