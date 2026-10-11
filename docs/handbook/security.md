# Soukromí a bezpečnost

Soukromí je hlavní hodnota projektu: žádná analytika, žádné sledování, žádné
prodávání dat. Aplikace komunikuje jen s homeserverem Pokecio.

## Zásady pro vývoj

- Hesla, tokeny ani klíče nikdy do kódu, gitu ani logů.
- Přihlašovací údaje pro testy jen přes proměnné prostředí
  (`POCKY_TEST_USER`, `POCKY_TEST_PASSWORD`).
- Heslo zadávat přes `read -s`, aby nezůstalo v historii shellu:

  ```sh
  read -s "POCKY_TEST_PASSWORD?Heslo: " && export POCKY_TEST_PASSWORD; echo
  ```

## Co je kde uložené (Android)

| Data | Umístění | Ochrana |
|---|---|---|
| Session (user_id, device_id, tokeny) | `files/secure/session.bin` | AES-256-GCM, klíč v Android Keystore |
| Heslo k databázi (32 náhodných bajtů) | `files/secure/db_key.bin` | AES-256-GCM, klíč v Android Keystore |
| Stav, kryptografické klíče, cache událostí, média | `files/matrix/*.sqlite3` | šifrováno heslem k databázi |

- Klíč v Keystore nejde ze zařízení vyexportovat.
- Když soubor nejde dešifrovat (např. obnova na jiném zařízení), aplikace ho
  smaže a vyžádá nové přihlášení.
- `android:allowBackup="false"` – data nejdou do záloh Androidu.

## Session v kódu

- V Rustu má `Session` vlastní `Debug`, který tokeny vynechává (je na to test).
- Kotlin dostává session jen jako neprůhledný JSON řetězec. Nemá k tokenům
  pojmenovaná pole, takže je nevypíše ani omylem přes `toString()`.

## Odhlášení

`logout` v pořadí:

1. zastaví synchronizaci,
2. odhlásí zařízení na serveru (token přestane platit),
3. zavře klienta,
4. smaže celou složku `files/matrix` včetně klíčů,
5. Android smaže `session.bin` a `db_key.bin`.

Lokální data se smažou i bez sítě. Na serveru pak zařízení zůstane, dokud ho
uživatel neodstraní z jiného klienta.

## Nové přihlášení

`createClient` vždy začíná s prázdnou datovou složkou. Staré klíče zařízení se
tak nikdy nepřenesou do nové session.

## Síť a TLS

- HTTPS přes rustls.
- Certifikáty ověřuje systémové úložiště Androidu (`rustls-platform-verifier`),
  stejně jako u ostatních aplikací. Respektuje i certifikáty přidané uživatelem.

## Šifrování zpráv (E2EE)

Nastavení klienta (`encryption::settings()`):

| Volba | Hodnota | Proč |
|---|---|---|
| `auto_enable_cross_signing` | ne | nesmí přepsat identitu založenou v jiném klientovi |
| `auto_enable_backups` | ne | nesmí založit novou zálohu místo existující |
| `backup_download_strategy` | při chybě dešifrování | chybějící klíč se stáhne ze zálohy až když je potřeba |

### Ověření zařízení klíčem pro obnovení

- V seznamu místností se ukáže pruh „Toto zařízení není ověřené“, pokud zařízení
  ověřené není a účet má úložiště tajemství nebo jiné ověřené zařízení.
- Obrazovka „Ověření zařízení“ přijme klíč pro obnovení (48 znaků) i frázi.
  Mezery a zalomení řádků v klíči nevadí.
- Klíč se nikam neukládá ani nevypisuje. Pole používá klávesnici pro hesla,
  takže si ho klávesnice nezapamatuje.
- Po úspěchu matrix-sdk naimportuje cross-signing klíče a klíč zálohy, zařízení
  se ověří a zálohu nových klíčů zapne.
- Jádro pak na pozadí stáhne ze zálohy klíče pro všechny šifrované místnosti
  (a při otevření místnosti znovu pro ni). Nedešifrované zprávy se dešifrují samy.

### Ověření jiným zařízením (emoji)

- Z obrazovky „Ověření zařízení“ tlačítkem „Ověřit jiným zařízením“ (jen pokud
  má účet jiné ověřené zařízení), nebo přijetím žádosti z jiného zařízení –
  obrazovka se otevře sama.
- Přijímají se jen žádosti od vlastního účtu. Když už jedno ověření běží, další
  žádost se zruší.
- Zobrazí se 7 emoji s českými názvy podle specifikace Matrixu. Uživatel
  potvrdí „Shodují se“ nebo „Neshodují se“ (to ověření zruší jako podezřelé).
- Po dokončení pošle druhé zařízení klíč zálohy. Jakmile se záloha zapne,
  jádro samo stáhne klíče pro všechny šifrované místnosti (`watch_encryption`).

### Záloha klíčů pro účty, které ji nemají

- Nabízí se jen účtům bez úložiště tajemství a bez zálohy na serveru, a to jen
  pokud je zařízení ověřené nebo účet ještě nemá šifrovací identitu. Jinak by
  nová záloha mohla přepsat nastavení z jiného klienta.
- Pokud chybí cross-signing identita, založí se. Server k tomu chce heslo účtu
  (UIA); heslo se použije jen pro tento požadavek.
- Vygenerovaný klíč pro obnovení se ukáže jednou. Kopírování ho označí jako
  citlivý obsah schránky (Android 13+ ho neukazuje v náhledu).
- Povinná je **bezpečnostní fráze** (`with_passphrase`). Na novém zařízení
  stačí zadat ji, klíč zůstává jako náhradní cesta.

### Bezpečnostní fráze

- Klíč se z fráze odvodí v zařízení: PBKDF2-SHA512, 500 000 opakování,
  náhodná sůl. Na server jde jen sůl a počet opakování, nikdy fráze ani klíč.
- Síla (`passphrase_strength` v jádře, sdílí Android i web): méně než 8 znaků
  je krátká; bez speciálního znaku (ne písmeno, číslice ani mezera, třeba
  ! ? - _ #) se nepřijme; méně než 5 různých znaků je slabá; jinak dostatečná;
  od 16 znaků se 3 slovy nebo 3 druhy znaků silná. Aplikace doporučuje 16
  a víc znaků: 3 až 4 slova spojená znaky („modrý-kopec-ráno-káva“).
- Fráze nesmí být stejná jako heslo k účtu. Heslo dostává server při každém
  přihlášení, takže fráze odvozená z hesla by šifrování zbytečně oslabila.
- **Změna fráze** (Já → Zabezpečení, jen ověřené zařízení se zapnutou zálohou):
  `change_recovery_passphrase` přes `recovery().reset_key()` založí nové
  úložiště tajemství s novou frází a novým klíčem. Starý klíč i fráze přestanou
  platit, záloha zpráv zůstává.

### Změna hesla k účtu

Já → Profil → Změnit heslo k účtu. `change_password` pošle požadavek nejdřív
bez ověření a s relací z odpovědi serveru (UIA) ho zopakuje se současným
heslem. Nové heslo aspoň 8 znaků a jiné než současné; slabé heslo odmítne
i server (`M_WEAK_PASSWORD`). Volba „Odhlásit ostatní zařízení“ je ve výchozím
stavu vypnutá (`logout_devices: false`). Živý test
`live_change_password_and_back` heslo účtu test2 změní a vrátí:

```sh
source ~/.pockyapp/test.env
cargo test -p pocky-core --lib live_change_password_and_back -- --ignored --nocapture
```

### Odhlášení

Před odhlášením aplikace zjistí, jestli je to poslední zařízení účtu a jestli
je zapnutá obnova. Pokud by odhlášení znamenalo ztrátu historie, dialog varuje
a nabídne nejdřív zálohu.

### Zákaz snímků obrazovky

- Android `FLAG_SECURE` (`SecureWindow`). Obrazovky s klíčem, frází, hesly,
  ověřením, zámkem a smazáním účtu (`Screen.sensitive`) a přihlášení jsou
  chráněné vždy.
- Volba „Zakázat snímky obrazovky“ (Já → Zabezpečení, `block_screenshots`
  v předvolbách zařízení) chrání celou aplikaci, ve výchozím stavu zapnutá.
- Dialogy v Compose (prohlížeč fotek, menu) mají `SecureFlagPolicy.Inherit`,
  takže ochranu přebírají od okna.

### Zámek aplikace

- Jádro (`app_lock.rs`): 6místný PIN, PBKDF2-SHA256 (100 000 opakování,
  náhodná sůl) v šifrovaném úložišti klienta, nikdy v čitelné podobě.
  Triviální PINy (000000, 123456, 654321…) jádro odmítne (`pin_allowed`).
- Po 5 špatných pokusech čekání 30 s, každý další pokus ho zdvojnásobí,
  nejvýš hodina. Počet pokusů i čekání přežijí restart aplikace.
- Odemknutí otiskem nebo obličejem (`androidx.biometric`, jen
  `BIOMETRIC_STRONG`) volitelně; úspěch vynuluje počítadlo pokusů.
- Zamyká se při spuštění a po návratu z pozadí po zvolené době (hned, 1, 5,
  15 minut). Pod zámkem zůstává aplikace v paměti, ale obsah je skrytý
  i pro čtečku obrazovky (`clearAndSetSemantics`) a okno má `FLAG_SECURE`.
- Se zapnutým zámkem oznámení nikdy neukazují text zprávy.
- Zapomenutý PIN se nedá obnovit; jediná cesta je odhlášení.

### Smazání účtu

Já → úplně dole „Smazat účet“. Potvrzuje se heslem a napsáním SMAZAT.
Jádro `delete_account` zavolá `/account/deactivate` s `erase: true` přes UIA
a pak smaže úložiště v zařízení. Synapse účet zablokuje, smaže profil,
odebere ho z místností a jeho zprávy skryje lidem, kteří se přidají později.
