# Stav a plán

## Hotovo

| Krok | Obsah |
|---|---|
| 1 | Monorepo, Rust workspace, tokeny barev |
| 2 | matrix-sdk, klient se šifrovaným SQLite úložištěm |
| 3 | UniFFI vrstva, generování Kotlin bindingů |
| 4 | Android projekt, Compose, téma, sestavení Rustu přes cargo-ndk |
| 5 | Přihlášení, obnova session, odhlášení, Keystore, TLS na Androidu |
| 6 | Sliding sync, seznam místností, náhledy, offline stav |
| 7 | Timeline, historie, odesílání textu, přečtení, avatary, obrázky |
| 8 | Stav šifrování, ověření klíčem nebo emoji, stažení klíčů ze zálohy, záloha pro nové účty, varování při odhlášení |
| 9 | Průvodce po přihlášení (7 kroků), volby jen v zařízení, vzhled chatu s aurou |
| 10 | Aura kolem avataru: online, mimo, píše…; sdílení vlastního stavu a psaní podle voleb, vzájemně |
| 11 | Odpovědi, úpravy, reakce, mazání, posílání fotek (zmenšení, bez EXIF) |
| 12 | Oznámení: Firebase jen jako zvonek, dešifrování v zařízení, volba náhledu, odpověď z oznámení, záloha bez Googlu |
| 13 | Nový seznam chatů: celá obrazovka pod systémové lišty, hledání, Teď online, filtry, spodní lišta, záložka Já |
| 14 | Seznam chatů: pozdrav a stav, záložka Lidé, připnutí, ztlumení, opuštění, přejetí řádku, fajfky, české časy |
| 15 | Záložka Já jako nastavení: soukromí, oznámení, vzhled, poloha, zabezpečení, verze |
| 16 | Nový vzhled chatu: hlavička nad aurou, bubliny s ocáskem, „píše…“ v konverzaci, pilulka pro psaní, tlačítko dolů, fotky na celou obrazovku, hovor/video/menu v hlavičce |
| 17 | Vlastní jednotná sada 41 ikon v `design/icons`, generátor pro Android, bez Material ikon |
| 18 | Profil: změna jména (přezdívky) a profilové fotky |
| 19 | Nový chat (hledání lidí), nová skupina, přijímání a odmítání pozvánek |
| 20 | Moje zařízení: seznam, stav ověření, odhlášení jiného zařízení heslem |
| 21 | Release build: optimalizované jádro, R8, podpis přes proměnné prostředí |
| 22 | Hlasové zprávy se skutečnou waveformou, fotoaparát, víc fotek najednou, galerie fotek v chatu |
| 23 | Soubory (posílání, stažení, otevření) a video (bez metadat, přehrávač) |
| 24 | Informace o chatu: úprava skupiny, členové a role, pozvání a odebrání lidí, profil protějšku |
| 25 | Stavy: vlastní stav s dobou platnosti, pás stavů místo „Teď online“, barevný předěl |
| 26 | Poloha: vlastní mapový server (pmtiles), mapa jen ČR a SR, výběr polohy s přesností, bublina s mapou a Navigovat, živá poloha na pozadí, záložka Mapa |
| 27 | Přechody mezi obrazovkami (posun a prolnutí), oprava aury v hlavičce chatu |
| 28 | Tablety: rozpoznání velikosti obrazovky, boční lišta, seznam + chat vedle sebe, třetí panel, omezené šířky, mapa s bočním seznamem, otočení |
| 29 | Verze 0.9.4 - BETA, aktualizace přímo v aplikaci (okno Nová verze, ověření SHA-256 a podpisu) |
| 30 | Promo web pokecio.com: Astro, čeština a angličtina, funkce, soukromí, stažení, novinky, changelog, RSS, stav služeb s Uptime Kuma, právní texty |
| 31 | Web: realistické náhledy telefonu, tabletu a webu s vymyšlenými daty, pět podrobných článků o funkcích, souhrn dne, kontakt dev@pokecio.com |
| 32 | Vlastní stav v account data `com.pokecio.status` (jádro; Android a web čekají) |
| 33 | Nová šifrovací identita („nemám klíč ani jiné zařízení“) |
| 34 | Ověřování jiných lidí, pruh při změně identity, štíty u zpráv |
| 35 | Libovolné emoji k reakci, potvrzení přečtení s volbou soukromí |
| 36 | V chatu jen důležité stavové zprávy, ostatní v Historii změn |
| 37 | Album: informace o souboru, stažení jedné fotky, výběru i celého alba, sdílení a přeposlání; Informace o zprávě; potvrzení přečtení v chatu opravdu zapnutá; filtry Vše, Lidé, Skupiny, Nepřečtené; kontrola aktualizací při každém spuštění |
| 38 | Bezpečnostní fráze pro zálohu klíčů s ukazatelem síly, změna fráze, změna hesla k účtu, náhled hlasovky před odesláním |
| 39 | Zákaz snímků obrazovky, zámek aplikace (PIN a otisk), smazání účtu, GIFy z klávesnice a animované GIFy, datum v changelogu jen u nejnovější verze |

## V plánu

Pořadí v rámci skupin odpovídá prioritě. Čísla kroků se přidělí, až se na věc dostane.

### Další na řadě (Android)

| Funkce | Poznámka |
|---|---|
| Blokování lidí a nahlašování zpráv | pro komunitu nutnost, nahlášení jde správci serveru |
| Mizející zprávy | 1 hodina, 1 den, 7 dní |
| Hledání v chatu | dnes „Připravujeme“ v menu chatu |
| Připnuté zprávy | připnutí v menu zprávy, přehled v informacích o chatu |
| Sdílení šifrované historie s novými členy skupiny | nový člen uvidí i starší zprávy |
| Světlý motiv | dnes jen tmavý |
| Angličtina v aplikaci | texty jsou připravené v `strings.xml`, chybí překlad |
| Hledání emoji podle názvu | ve výběru emoji pro reakce |

### Webová aplikace (dorovnání s Androidem)

- album chatu se stažením, sdílením a přeposláním,
- štíty u zpráv, ověřování lidí a informace o zprávě,
- zapnutí zálohy klíčů, změna bezpečnostní fráze a hesla, smazání účtu,
- víc fotek najednou a plná kvalita, posílání videa, nahrávání hlasovek,
- zakládání a správa skupin, úprava profilu,
- nastavení přesnosti polohy a vzhledu bublin,
- instalace jako aplikace (PWA).

### Větší celky

| Celek | Poznámka |
|---|---|
| Hlasové a videohovory | Element Call / MatrixRTC, dnes „Připravujeme“ v hlavičce chatu |
| Timeline | veřejná nástěnka komunity, dnes „Připravujeme“ ve spodní liště |
| Novinky | dnes „Připravujeme“ ve spodní liště |
| Registrace účtu v aplikaci | dnes jen přihlášení k existujícímu účtu |
| Soukromé přezdívky a vlastní pozadí chatu | jen v zařízení |

### Platformy

- iOS a macOS (SwiftUI),
- Windows (WinUI 3),
- Linux (GTK4).

### Technický dluh a provoz

- Firebase: přechod z `getToken` na nové `register()` API (na emulátoru zatím nefungovalo),
- oznámení přes ntfy / UnifiedPush jako volba pro pokročilé,
- automatické testy UI a sestavení v CI (GitHub Actions),
- šifrované zálohy serveru mimo VPS, `security.txt` na webu,
- nezávislý bezpečnostní audit před ukončením bety.

### Čeká na ověření v praxi

- zámek aplikace s otiskem, GIFy z klávesnice, zákaz snímků v přehledu aplikací (0.9.5-Fix #6),
- odemčení zálohy jen bezpečnostní frází na novém zařízení, změna fráze na vlastním účtu,
- reset šifrovací identity, potvrzení přečtení ve skupině, živá poloha na pozadí se dvěma účty.

## Známá omezení

- Vývojové APK má kolem 200 MB kvůli neoptimalizované Rust knihovně; release asi 70 MB.
