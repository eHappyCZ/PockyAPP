# Android aplikace

Cesta: `apps/android/`. Package a `applicationId`: `com.pokecio.app`.

## Moduly

| Modul | Obsah |
|---|---|
| `:core-bindings` | generované Kotlin API, `libpocky_ffi.so`, `TlsVerifier`, JNA |
| `:app` | aplikace v Compose |

`:core-bindings` stahuje i Android část `org.rustls:rustls-platform-verifier`.
Verzi bere z `Cargo.lock` (balíček `rustls-platform-verifier-android`), aby
vždy odpovídala Rust části.

## Struktura `:app`

```
com.pokecio.app
  PockyApp.kt               Application, inicializace TLS
  MainActivity.kt           Compose, přepínání obrazovek, popředí/pozadí
  MainViewModel.kt          přihlášení, obnova, odhlášení, otevřená místnost
  data/
    SecureStore.kt          AES-GCM šifrování souborů klíčem z Keystore
    SessionStore.kt         session + heslo k databázi
    ClientHolder.kt         jediný PockyClient pro celý proces (UI i push)
  push/
    PushRegistrar.kt        Firebase token → pusher na Synapse
    PushService.kt          příjem „zvonku“ z Firebase, stažení zprávy, oznámení
    Notifier.kt             kanály, vzhled oznámení podle volby z průvodce
    NotificationActions.kt  Odpovědět / Přečteno přímo z oznámení
    MessageCheckWorker.kt   záloha bez Google služeb: kontrola každých 15 min
  rooms/
    RoomListController.kt   seznam místností a stav syncu jako StateFlow
    RoomController.kt       otevřená timeline, historie, odesílání, přečtení
  encryption/
    EncryptionController.kt stav šifrování, obnova, ověření emoji, záloha
  preferences/
    PreferencesController.kt volby zařízení a zobrazované jméno jako StateFlow
  presence/
    PresenceController.kt   kdo kde píše, online stav sledovaných lidí
  media/
    MediaLoader.kt          načítání a cache obrázků
    MatrixImage.kt          rememberMatrixImage, MatrixImage
  ui/
    theme/                  barvy z design/tokens.json, Material 3 téma
    components/             sdílené prvky (GradientButton, ControlShape)
    login/                  přihlášení, animované logo, pole, ikony
    onboarding/             průvodce po přihlášení
    rooms/                  seznam místností, avatar, formátování náhledů a času
    room/                   obrazovka místnosti, řádky, texty, vzhled bublin a aura
    encryption/             obnova klíčem, ověření emoji, záloha, dialog odhlášení
```

## Stav aplikace

```kotlin
sealed interface AppState {
    data object Loading
    data class LoggedOut(busy, error)
    data class LoggedIn(userId, rooms: RoomListController, media: MediaLoader,
                        encryption: EncryptionController,
                        preferences: PreferencesController,
                        openRoom: RoomController?, overlay, logoutPrompt, busy)
}
```

- `MainViewModel` vlastní `PockyClient` i všechny controllery a při odhlášení /
  zániku je zavře.
- Navigace (první platná vyhrává): probíhající ověření → obnova / záloha →
  volby se načítají (prázdné pozadí) → průvodce (`!onboardingDone`) →
  místnost (`openRoom != null`) → seznam. Zpět řeší `BackHandler`.
- `MainActivity` volá `onForeground` / `onBackground` přes `LifecycleStartEffect`;
  sync tak běží jen v popředí.

## Obrazovky

### Přihlášení (`LoginScreen`)

- Logo `PockyLogo`: „P“ s přechodem `logo-light` → `logo-deep`, tři tečky
  v bříšku (animace psaní), pulzující fialová záře za ním.
- „PockyAPP“ se zlatým štítkem BETA, nadpis „Chat, který žije.“ (slovo „žije“
  v `accent-soft`), podnadpis „Přihlas se svým Matrix účtem.“
- Pole jména s popiskem, pevným „@“ a „:pokecio.com“; zadává se jen jméno.
  Vložené celé ID (`@karel:pokecio.com`) se zkrátí na jméno.
- Heslo s ikonou oka pro zobrazení / skrytí.
- Tlačítko s přechodem `gradient-start` → `gradient-end`, indikátor, chyba.
- Neaktivní (jen vzhled), parametry `LoginScreen` jsou zatím `null`:
  - `onQrLogin` – „Přihlásit QR kódem“,
  - `onRegister` – „Nemáš účet? Vytvořit účet“,
  - `onChangeServer` – „Server pokecio.com · Změnit“.
- Přepínač CS / EN zatím není, přijde s anglickými texty.

Pole podporují automatické vyplňování hesel. Heslo se po přihlášení zahodí.

### Průvodce po přihlášení (`OnboardingScreen`)

Ukáže se po každém novém přihlášení na zařízení (volby se mažou s databází při
odhlášení) a jde znovu otevřít z menu seznamu chatů → **Průvodce**. Ve stejném
vzhledu jako přihlášení: pozadí `bg` s fialovou září nahoře, `GradientButton`.

- Nahoře šipka zpět (od 2. kroku), ukazatel ze šesti dílků a „1/6“.
- Číslo kroku drží `MainActivity` (`rememberSaveable`), aby přežilo odskok do
  obnovy klíčů nebo ověření emoji.
- Každá změna volby se hned ukládá přes `PreferencesController` do šifrované
  databáze jádra; na server nejde nic. „Do chatů“ uloží `onboardingDone = true`.

| Krok | Soubor | Obsah |
|---|---|---|
| 1 | `WelcomeStep` | „Ahoj, <jméno>.“ a slib: zprávy čtete jen vy, žádné reklamy ani prodej dat, žádný algoritmus |
| 2 | `UnlockStep` | podle `EncryptionInfo`: zadat heslo pro obnovu (otevře `RecoveryScreen`), ověřit z jiného zařízení (spustí ověření emoji), „Nemám heslo“ s vysvětlením; ověřené zařízení → „Starší zprávy jsou odemčené“, účet bez klíčů → „Není co odemykat“ |
| 3 | `ChatStyleStep` | náhled malého chatu, Klasický / Živý s aurou |
| 4 | `NotificationStep` | náhled zamčené obrazovky, Vždy text / Jen na odemčeném telefonu (doporučeno) / Nikdy |
| 5 | `LocationStep` | kreslená mapka bez stahování dlaždic, kruh podle volby: Přesně / Okolí ~500 m / Město ~5 km (doporučeno) |
| 6 | `VisibilityStep` | „Kdo uvidí, že jsi online?“ – ukázka tří avatarů s aurou, přepínače „Ukazovat, že jsem online“ (výchozí vypnuto) a „Ukazovat, že píšu“ (výchozí zapnuto), poznámka o vzájemnosti |
| 7 | `DoneStep` | shrnutí voleb, „Změnit“ vrací na daný krok |

Společné karty (`ChoiceCard`, `ActionCard`, `NoticeCard`, `PromiseRow`) jsou
v `OnboardingCards.kt`.

### Práce se zprávami

- **Podržení bubliny** → `MessageActions` (spodní panel): řada rychlých reakcí
  ❤️ 👍 😂 😮 😢 🔥 (vlastní zvýrazněné), Odpovědět, Upravit, Kopírovat, Smazat
  (s potvrzením). Nabídka se řídí `canReply` / `canEdit` / `canRedact`.
- **Přejetí doprava** (`SwipeToReply`) – po 64 dp zavibruje, po puštění připraví
  odpověď.
- Odpověď / úprava: `RoomController.composing` (`Composing.Message | Reply |
  Edit`), nad polem `ComposingBar` s citací a křížkem; při úpravě se text načte
  do pole a tlačítko fotky zmizí.
- **Reakce pod zprávou** jsou klikací – přidají / odeberou.
- **Fotka:** ikona vlevo od pole → systémový Photo Picker (bez oprávnění) →
  `ImageSendDialog` s náhledem, popiskem, „Poslat v plné kvalitě“ a poznámkou o
  odstranění polohy. Bajty čte `MainActivity` přes `ContentResolver`, zbytek
  `RoomController.sendImage` + jádro.
- Chyby akcí ukazuje snackbar („Nepovedlo se, zkus to znovu.“).
- Vlastní ikony (odpověď, kopírovat, fotka) jsou v `RoomIcons.kt`.

### Oznámení

Tok: Synapse → Sygnal → Firebase pošle jen `room_id` + `event_id` →
`PushService` vezme klienta z `ClientHolder` (obnoví ho, i když aplikace
neběžela) → `client.notification()` zprávu stáhne a dešifruje → `Notifier`.

- Firebase: vypnutá automatická inicializace, analytika i export metrik
  (`AndroidManifest.xml`). `google-services.json` je v `.gitignore`.
- Kanály: **Soukromé zprávy** (vysoká důležitost), **Skupiny** (výchozí).
  Jedno oznámení na místnost (`MessagingStyle`, nové zprávy se přidávají).
- Podle `notificationPreview`:
  - **vždy** – jméno i text,
  - **jen na odemčeném** – při zamčeném telefonu jen jméno a „Nová zpráva ·
    odemkni pro zobrazení“; po odemknutí (`ACTION_USER_PRESENT`) se doplní text,
  - **nikdy** – „PockyAPP · Nová zpráva“.
- Akce: **Odpovědět** (odpověď na konkrétní zprávu) a **Přečteno**; obojí bez
  otevření aplikace (`NotificationActions`, `goAsync`).
- Klepnutí otevře přímo místnost (`MainActivity.EXTRA_ROOM_ID`). Otevření
  místnosti její oznámení smaže; pro právě otevřenou místnost se nic neukáže.
- Oprávnění (Android 13+): žádost po kroku „Co uvidíš v oznámení?“ v průvodci
  a pruh „Oznámení jsou vypnutá“ v seznamu chatů (při opakovaném odmítnutí
  otevře systémové nastavení).
- **Bez Google služeb** (nepodaří se získat token): `MessageCheckWorker` každých
  15 minut krátce spustí sync a upozorní na nepřečtené zprávy, které ještě
  nehlásil. S tokenem se úloha zruší. Na emulátoru bez přihlášeného Google účtu
  vrací Firebase `AUTHENTICATION_FAILED`, takže tam běží právě tahle záloha.

### Vzhled chatu (aura)

`LocalChatLook` (v `ui/room/ChatLookStyle.kt`) nastavuje `MainActivity` podle
voleb zařízení.

- **Klasický:** vlastní bubliny `bubble-out`, cizí `surface`, pozadí `bg`.
- **Živý s aurou:** za timeline `AuraBackground` – tři rozmazané záře
  (`gradient-start`, `gradient-end`, `accent`) pomalu krouží (18 s cyklus);
  vlastní bubliny mají přechod `gradient-start` → `gradient-end` s fialovým
  stínem, cizí jsou poloprůhledné `surface` s tenkým okrajem `accent-soft`.
- `bubbleSurface` a `bubbleShape` používá i náhled v průvodci.

### Aura kolem avataru (`AuraAvatar`)

`auraOf(presence, typing)` vybere kroužek; „píše“ má přednost před stavem.

| Stav | Vzhled |
|---|---|
| online | zelený (`success`) kroužek, který dýchá, s jemnou září |
| mimo | zlatý (`gold`) kroužek bez pohybu |
| píše… | oblouk `accent` → `accent-soft` se září, otáčí se (1,1 s) |
| offline / neznámý | nic |
| vlastní (bez sdílení) | kruh v přechodu `gradient-start` → `accent-soft` → `gradient-end`, pomalu se otáčí |

Kroužek se kreslí mimo plochu avataru, rozložení se nemění. Použití: seznam
chatů (protějšek soukromé konverzace, píšící v místnosti), hlavička místnosti
(+ „píše…“ / „online“ / „mimo“ pod jménem), avatary autorů zpráv a pruh
„… píše“ s tečkami nad polem pro psaní (`TypingIndicator.kt`).

`PresenceController` zjišťuje stav jen na popředí, každých 60 s, nejvýš pro
40 lidí: protějšky soukromých chatů ze seznamu a autory zpráv v otevřené
místnosti. Vlastní „píšu“ posílá `RoomController.onDraftChanged` – při změně a
při psaní nejvýš jednou za 4 s; odeslání zprávy nebo zavření místnosti ho ukončí.

### Domovská obrazovka (`HomeScreen`)

Spodní lišta: **Chaty**, Timeline, Novinky, **Mapa**, **Já** – Timeline a
Novinky ukážou „Připravujeme“. Plovoucí tlačítko s tužkou
otevře Nový chat. Zpět na záložce Já vrací na Chaty.

### Nový chat a skupina (`ui/people`)

- **Nový chat** (`NewChatScreen`): vyhledávací pole (min. 2 znaky, 300 ms
  prodleva), nahoře „Nová skupina“, bez hledání „Nedávné konverzace“
  (protějšky soukromých chatů). Klepnutí na člověka otevře existující soukromý
  chat, jinak založí nový (šifrovaný, `m.direct`).
- **Nová skupina** (`NewGroupScreen`): 1. výběr lidí (zaškrtávání, vybraní
  v pásu nahoře s křížkem), 2. název (max. 64 znaků) – „Vytvořit skupinu“
  založí soukromou šifrovanou skupinu a pozve vybrané.
- Po založení se chat rovnou otevře. Logika v `PeopleController`.

### Pozvánky

Pozvánky se v seznamu chatů neukazují mezi běžnými chaty, ale nahoře jako
karty „POZVÁNKY · N“ (zlatý okraj): název, „X ti chce napsat“ / „Zve tě X“,
tlačítka Odmítnout a Přijmout. Přijetí chat rovnou otevře.

Celá aplikace se kreslí pod stavový řádek i systémová tlačítka
(`enableEdgeToEdge`, vypnutý kontrastní závoj `is*BarContrastEnforced`).

### Seznam chatů (`RoomListScreen`)

Pozdrav, pás stavů a filtry stojí nahoře na místě, posouvají se jen pozvánky a chaty. Lišty s posuvným ukazatelem (filtry, záložky alba) musí mít pevnou výšku, jinak se ukazatel roztáhne.

- Hlavička: „Ahoj, <jméno> 👋“ a pod tím stav – počet nepřečtených zpráv
  (fialově, české tvary 1 / 2–4 / 5+), „Vše přečteno ✓“ (zeleně), „Offline“
  (zlatě s tečkou) nebo „Načítám chaty…“. Vpravo kulaté tlačítko hledání a
  vlastní avatar s gradientním kroužkem (otevře Já).
- Hledání filtruje podle názvu bez ohledu na diakritiku a velikost písmen.
- **Stavy** (`StatusStrip`, bez nadpisu): první je vlastní avatar s
  „Přidat stav“ / vlastním stavem, za ním protějšky soukromých chatů, které
  mají stav, jsou online nebo píšou (stav v bublině nad avatarem, zelená tečka
  online, kroužící aura při psaní; se stavem napřed). Bublina `StatusBubble`:
  jemný svislý přechod, okraj `accent-soft` slábnoucí dolů, fialový stín a
  „myšlenkový“ ocásek ze dvou koleček; prázdný vlastní stav je čárkovaný obrys. Pod pásem `GlowDivider` –
  linka v přechodu značky se září – odděluje horní panel od chatů.
- Vlastní stav: `StatusEditorSheet` – text (max. 60 znaků), rychlé nápady,
  platnost 1 h / 4 h / dnes / 24 h / do zrušení, Uložit / Smazat stav.
  Cizí stav: `StatusCardSheet` s celým textem a tlačítkem Napsat.
- Stav je oddělený od „Ukazovat, že jsem online“ (stavy ostatních se načítají
  vždy). Po vypršení ho smaže `StatusExpiryWorker`, při startu syncu to
  kontroluje i jádro.
- Přepínač Vše / Nepřečtené / Lidé / Skupiny: vybraná část v gradientu
  značky se při přepnutí posune (spring animace); počet nepřečtených chatů je
  malý odznak u slova Nepřečtené. Lidé = soukromé chaty 1:1.
- Řádky: soukromé chaty kulatý avatar, skupiny zaoblený čtverec (aura kopíruje
  tvar), iniciály ze dvou písmen, nepřečtené = fialový čas a počet.
- Pruhy (ověření, záloha, oznámení) jsou pod hlavičkou; offline ukazuje
  stavový řádek v hlavičce.
- Přepínač zůstává při posouvání nahoře (`stickyHeader`), pás Teď online odjede.
- Řádky se při změně pořadí posouvají plynule (`animateItem`); načítání má
  třpytivé zástupné řádky; prázdné filtry mají vlastní text a emoji.
- Vlastní avatar (hlavička i Já) má auru: zelenou dýchající při sdílení online
  stavu, jinak `AvatarAura.OWN` – pomalu kroužící (6 s) přechod barev značky.
- Čas / datum je v pravém sloupci zarovnaný pod sebou; jméno se zkrátí dřív.
- **Podržení řádku** → `RoomActionsSheet`: Označit jako přečtené, Připnout
  nahoru / Odepnout, Ztlumit / Zrušit ztlumení, Opustit (s potvrzením).
- **Přejetí řádku** (`SwipeRoom`): doprava = přečteno (jen nepřečtené),
  doleva = ztlumit / zapnout.
- Připnuté chaty řadí jádro nahoru (značka `m.favourite`), mají připínáček u
  času. Ztlumené mají přeškrtnutý zvonek a šedý počet; zmínka (@) je zlatá.
- U vlastní poslední zprávy fajfky: hodiny = odesílá se, ✓ = odesláno,
  ✓✓ fialově = přečteno (potvrzení přečtení od kohokoli jiného), ! = chyba.
- Časy jsou vždy česky (`AppLocale`): „13:09“, „Včera“, „Út“, „13. 9.“.
  `MainActivity.attachBaseContext` nastaví češtinu i pro tvary podle čísla
  (1 člen / 2–4 členové / 5 členů) nezávisle na jazyce systému.

### Já – profil a nastavení (`ProfileScreen`)

Otevře se ze spodní lišty nebo klepnutím na vlastní avatar v hlavičce.

- Nahoře velký avatar s aurou (odznak fotoaparátu → nabídka fotky), jméno
  s tužkou (→ dialog jména) a Matrix ID.
- **Profil:** Jméno (dialog, max. 64 znaků, Uložit až po změně) a Profilová
  fotka (Vybrat z galerie přes Photo Picker / Odebrat fotku). Během nahrávání
  je přes avatar ukazatel průběhu. Ukládá `PreferencesController`
  (`rename`, `changeAvatar`, `removeAvatar`) přes jádro.
- **Soukromí:** přepínače „Ukazovat, že jsem online“ a „Ukazovat, že píšu“
  (+ poznámka o vzájemnosti).
- **Aplikace:** Oznámení, Vzhled chatu, Poloha – řádek ukazuje aktuální volbu,
  klepnutí otevře spodní panel se stejnými kartami a náhledy jako průvodce
  (`NotificationStep`, `ChatStyleStep`, `LocationStep`).
- **Zabezpečení:** Toto zařízení (Ověřené / Neověřené → obnova klíčů),
  Záloha klíčů (Zapnutá / Nastavit → nastavení zálohy), Změnit bezpečnostní
  frázi, Moje zařízení, Zámek aplikace, Zakázat snímky obrazovky.
- **O aplikaci:** Průvodce, Novinky a opravy, Aktualizace, Hledat aktualizace
  automaticky, Verze (aplikace · jádro).
- Odhlásit se, pod tím červeně **Smazat účet**.

### Moje zařízení (`DevicesScreen`)

Toto zařízení nahoře, pod ním další seřazená podle poslední aktivity; u
každého název, „Naposledy aktivní: …“ a štítek Ověřené / Neověřené. Klepnutí
na jiné zařízení → dialog s heslem (server ho vyžaduje) → odhlášení.
IP adresy se záměrně neukazují. Logika v `DevicesController`.

### Novinky a opravy (`ChangelogScreen`)

Já → O aplikaci → Novinky a opravy. Karta pro každou verzi (číslo, kanál,
datum, štítek „Nainstalováno“). Novinky mají fialovou hvězdičku, opravy zelenou
fajfku. Data z `changelog()` v jádru podle nainstalovaného `versionCode`.

### Připravujeme

Záložky Timeline a Novinky, ikony hovoru a videohovoru v hlavičce chatu a
„Hledat v chatu“ jsou ztlumené a po klepnutí ukážou krátké hlášení
„Připravujeme“ (`ui/components/ComingSoon.kt`).

### Vlastní stav

`PresenceController` odebírá `observe_own_status` (v jádru `watch_own_status`):
stav nastavený na jiném zařízení se ukáže hned a naplánuje se i jeho vypršení.

### Album chatu (`RoomGallery`)

Menu chatu → Fotky a soubory. Čtyři záložky s počty: Fotky, Videa, Hlasovky,
Dokumenty. Fotky a videa v mřížce po 3, hlasovky a dokumenty v seznamu (přehrání
a otevření přímo v něm), všechno od nejnovějšího a rozdělené po měsících. Data má
`RoomMediaController`: vlastní časová osa z `room_media`, po otevření alba sama
stránkuje až na začátek chatu (s pauzou 150 ms mezi stránkami) a drží se, dokud je
chat otevřený. Prohlížeč fotek ukáže nejdřív náhled a pak plnou kvalitu (`original`).

- **Prohlížeč fotky a videa** má nahoře lištu `MediaActionBar`: Informace (od koho,
  kdy, název, velikost, rozměry, délka, typ), Stáhnout, Sdílet a Přeposlat. Funguje
  z alba i z chatu. Údaje bere `TimelineEvent.mediaDetails()`, jádro k fotkám a
  videím posílá i `name`, `size` a `mime`.
- **Výběr:** podržení dlaždice zapne výběr, další klepnutí přidávají a odebírají.
  Nahoře „Vybráno: N“ se Stáhnout, Sdílet a Přeposlat, tlačítko Vybrat vše.
- **Stáhnout vše** stáhne celou aktuální záložku (až po načtení celé historie).
- **Ukládání** (`MediaSaver`): Android 10+ přes MediaStore bez oprávnění, fotky do
  `Pictures/PockyAPP`, videa do `Movies/PockyAPP`, hlasovky do `Music/PockyAPP`,
  ostatní do `Download/PockyAPP`. Android 9 si jednou řekne o
  `WRITE_EXTERNAL_STORAGE` (v manifestu jen do SDK 28). Názvy
  `PockyAPP_IMG_20261008_231300.jpg`, stejné jméno MediaStore očísluje.
- **Hromadné stahování** (`MediaDownloads`, jedna instance na přihlášení) běží i po
  zavření alba, průběh „Stahuji 3 z 12“ s možností zrušit, na konci toast.
  Soubory, které ještě nebyly v mezipaměti, po uložení z mezipaměti maže.
- **Sdílení** přes systémové menu (FileProvider, `cache/open`).
- **Přeposlání** (`ForwardDialog` + `RoomController.forward`): výběr chatu
  s hledáním, jádro `forward_message` načte původní událost a pošle stejný obsah
  bez vazby na odpověď. U šifrovaných souborů se znovu nenahrávají, jde stejný
  zašifrovaný soubor s klíčem. Výsledek ukáže toast.

### Historie změn (`RoomHistory`)

Informace o chatu → „Historie změn“. Seznam stavových změn od nejnovější,
rozdělený podle dnů, opakované změny od stejné osoby sloučené. Data má
`RoomHistoryController` (vlastní časová osa z `room_history`, otevírá se jen při
zobrazení panelu a stránkuje, dokud nenajde dalších 20 změn). Na tabletu na
šířku se ukazuje v bočním panelu jako galerie.

### GIFy a obrázky z klávesnice

- Pole pro psaní je `BasicTextField(TextFieldState)` s `contentReceiver`.
  Obrázek nebo GIF z klávesnice (Gboard, Samsung) se hned zkopíruje do
  `cache/keyboard` (oprávnění klávesnice je dočasné) a otevře se stejný náhled
  jako u fotek z galerie.
- Jádro posílá GIF a animované WebP beze změny (bez zmenšení), jinak by se
  animace ztratila.
- `AnimatedMatrixImage` přehrává GIF a WebP přes `ImageDecoder`
  a `AnimatedImageDrawable` v bublině i v prohlížeči.

### Hlasovky: náhled před odesláním

Mikrofon spustí nahrávání (`RecordingBar`). Tlačítko vpravo nahrávání zastaví
a místo okamžitého odeslání ukáže `RecordingReview`: Zahodit, Přehrát/Pozastavit,
vlna s posouváním a délka, vpravo Odeslat. Náhled přehrává vlastní `MediaPlayer`
z dočasného souboru v `cache`, který se po odeslání nebo zahození smaže.

### Reakce a potvrzení přečtení

- **Další emoji:** v menu zprávy je za rychlými reakcemi tlačítko se smajlíkem.
  Otevře `EmojiPickerView` z `androidx.emoji2:emoji2-emojipicker` (kategorie a
  naposledy použité, uložené jen v zařízení). Hledání podle názvu zatím není.
- **Přečteno:** pod zprávou, kam až někdo dočetl (`ReadReceipts`). Ve skupině
  malé avatary (nejvýš 4 a „+N“), v chatu 1:1 „Přečteno 12:30“ pod vlastní
  zprávou. Klepnutí ukáže seznam, kdo a kdy četl. Jména a avatary bere
  `RoomController.members`.
- **Informace o zprávě** (menu zprávy, `MessageInfoDialog`): Odesláno na server
  s časem, Upraveno, a Přečteno. V chatu 1:1 čas, ve skupině „Přečteno: 3 z 5“
  a seznam lidí s časy. Matrix nemá potvrzení o doručení do zařízení, proto
  jen „na server“. Kdo v chatu později napsal, ten předchozí zprávy četl:
  `readersOf` i fajfky v bublinách (`buildRows`) takovou odpověď počítají jako
  přečtení i bez potvrzení.
- **Přeposlat** je v menu každé zprávy.
- Timeline musí mít zapnuté sledování potvrzení
  (`track_read_marker_and_receipts(MessageLikeEvents)` v `open_timeline`).
  V matrix-sdk-ui je ve výchozím stavu vypnuté. Do 0.9.5-Fix #3 chybělo, a proto
  se „Přečteno“ v chatu nikdy neukázalo.
- **Volba „Potvrzení o přečtení“** v Já → Soukromí. Platí vzájemně: když je
  vypnutá, posílá se jen soukromé potvrzení a cizí potvrzení se nezobrazují
  (ani dvojitá fajfka).

### Ověřování lidí a štíty

- **Informace o chatu 1:1** (šifrovaný chat): řádek „Ověřit osobu“, „Ověřeno“
  nebo „Identita se změnila“ (`TrustRow`). Klepnutí spustí ověření emoji na
  stejné obrazovce jako u vlastních zařízení (`VerificationScreen` s `person`).
  Po dokončení se stav v informacích o chatu načte znovu.
- **Pruh v chatu** (`IdentityBanner`): když se někomu z chatu změní šifrovací
  identita. Žlutý pro neověřené, červený pro dříve ověřené. Volby Ověřit a Rozumím.
  Data z `RoomController.identityChanges`.
- **Štít u zprávy** (`ShieldMark`): malá ikona vedle času, klepnutí ukáže
  vysvětlení. Červená pro vážné problémy.
- Ikony `shield-check` a `shield-alert` jsou v `design/icons`.

### Nová šifrovací identita (`ResetIdentityScreen`)

Na obrazovce ověření (`RecoveryScreen`) je dole „Nemám klíč ani jiné
zařízení“. Otevře varování (starší zprávy nepůjdou přečíst, ostatní zařízení
bude potřeba ověřit znovu, lidé uvidí změnu identity), zaškrtnutí „Rozumím“,
heslo k účtu a tlačítko „Vytvořit novou identitu“. Pak se ukáže nový klíč pro
obnovení stejně jako u zapnutí zálohy (`KeyReady`). Logika je v
`EncryptionController.resetIdentity`.



### Místnost (`RoomScreen`)

Kreslí se přes celou obrazovku; při vzhledu s aurou je za vším `AuraBackground`
a hlavička je poloprůhledná.

- **Hlavička** (`RoomHeader`): šipka zpět, avatar s aurou (soukromý chat
  kulatý, skupina zaoblená), jméno, zámek u šifrovaných, pod tím „píše…“ /
  „online“ / „mimo“. Vpravo Hovor a Videohovor (zatím neaktivní, ztlumené)
  a menu ⋮: Hledat v chatu, Fotky a soubory, Informace o chatu (neaktivní),
  Ztlumit / Zrušit ztlumení, Připnout / Odepnout, Opustit (s potvrzením,
  pak zavře chat) – funkční přes `RoomListController`.
- **Bubliny**: tvar `BubbleShape` – u poslední zprávy ve skupině „ocásek“
  (vlastní vpravo, cizí vlevo); čas, „upraveno“ a stav (ikony hodiny / ✓ / ✓✓
  zeleně / !) uvnitř bubliny.
- **Datum** jako plovoucí štítek, **Nové zprávy** jako gradientní štítek s
  linkami do ztracena.
- **„Píše…“** je bublina se skákajícími tečkami na konci konverzace (ve skupině
  s avatarem píšícího).
- **Tlačítko dolů** se objeví po odscrolování; odznak ukazuje počet cizích
  zpráv, které přišly mezitím.
- **Pole pro psaní**: zaoblená „pilulka“ (fotka vlevo, text), vedle kulaté
  tlačítko odeslat v gradientu značky; při úpravě ikona ✓.
- **Fotka**: klepnutí otevře `ImageViewer` přes celou obrazovku – přiblížení
  prsty, dvojklep 2,5× / zpět, popisek dole.
- **Přílohy**: tlačítko fotky → `AttachSheet` Vyfotit (systémový fotoaparát,
  soubor přes `FileProvider` do `cache/camera`, bez oprávnění CAMERA) /
  Galerie (Photo Picker, až 10 fotek). `ImageSendDialog` ukáže jednu velkou
  nebo pás náhledů; fotky se posílají postupně, popisek dostane první.
- **Soubor**: třetí volba v `AttachSheet` (systémový výběr dokumentů) →
  `FileSendDialog` (název, velikost) → `RoomController.sendFile`. Bublina
  `FileMessage`: ikona s příponou, název, velikost; klepnutí soubor stáhne,
  dešifruje, zkopíruje do `cache/open` a otevře přes `FileProvider` v aplikaci
  telefonu.
- **Video**: Galerie vybírá fotky i videa; videa → `VideoSendDialog`.
  `VideoPrep` zjistí rozměry (včetně otočení), délku a náhled a MP4 přebalí
  přes `MediaExtractor`/`MediaMuxer` bez metadat (bez polohy), jiné formáty
  posílá beze změny. Bublina: náhled s ▶ a délkou → `VideoPlayerDialog`
  (systémový `VideoView`, pauza, posuvník, čas).
- Při překročení limitu serveru hláška „Soubor je na server moc velký“.
  Odhlášení maže celou `cacheDir` (stažené a dešifrované soubory).
- **Informace o chatu** (`RoomInfoScreen`, klepnutí na hlavičku nebo menu ⋮):
  velká fotka (správce ji mění přes 📷), název a popis (správce s ✏️),
  štítek Šifrováno; Ztlumit, Připnout, Fotky a soubory; u skupin seznam členů
  (štítky Správce / Moderátor, „Pozvánka čeká“), Přidat lidi (výběr jako u
  nové skupiny v režimu Pozvat) a odebrání člena s potvrzením; dole Opustit.
  U soukromého chatu „Profil“ protějšku (fotka, ID, online stav). Logika v
  `RoomInfoController`; co jde měnit, určují oprávnění z jádra.
- **Fotky a soubory** (menu ⋮) → `RoomGallery`: mřížka 3 × N z načtené
  historie, „Načíst starší“, klepnutí otevře prohlížeč.
- **Hlasové zprávy**: prázdné pole → tlačítko mikrofonu (oprávnění
  RECORD_AUDIO až při prvním použití). `VoiceRecorder` nahrává Ogg/Opus
  (Android 10+) nebo AAC (Android 9), každých 60 ms bere hlasitost →
  `RecordingBar` s živou waveformou, časem, 🗑 a ➤. Kratší než 0,7 s se
  zahodí. Bublina `VoiceMessage`: ▶/⏸, waveforma (přehraná část zvýrazněná,
  klepnutí/tažení = posun), čas. Přehrává `VoicePlayer` (vždy jen jedna,
  soubor z `MediaLoader.file` v `cache/media`, maže se při odhlášení).
- Podržení, přejetí pro odpověď, reakce a posílání fotek viz Práce se zprávami.

## Ikony

Jednotná vlastní sada pro všechny platformy: `design/icons/*.svg`
(mřížka 24 × 24, obrys 1,8, zaoblené konce a rohy, bez výplní,
barva `currentColor`).

- Android: `python3 scripts/gen-icons.py` vygeneruje vektory
  `res/drawable/pi_*.xml` (staré `pi_*` nejdřív smaže).
- V Compose přes `PockyIcons` (`ui/theme/PockyIcons.kt`), např.
  `Icon(PockyIcons.Search, …)`; barvu dává `tint`.
- Material ikony aplikace nepoužívá (závislost `material-icons-core` je pryč).
- Výjimka: `ic_notification.xml` – plné „P“ do stavového řádku.

Nová ikona: přidat SVG do `design/icons/`, pustit skript a doplnit řádek do
`PockyIcons`.

## Barvy

`ui/theme/Color.kt` odpovídá `design/tokens.json`:

| Token | Barva | Použití |
|---|---|---|
| bg | #08070c | pozadí |
| surface | #121019 | cizí bubliny, pole, plochy |
| accent | #8b5cf6 | primární barva, odznaky, odkazy na jména |
| accent-soft | #b8a1fb | zvýraznění v textu (slovo „žije“ na přihlášení) |
| bubble-out | #7c3aed | vlastní bubliny |
| ink | #f4f2f9 | text |
| ink-muted | #a4a0b3 | vedlejší text |
| success | #34d399 | přečteno |
| logo-light | #dccff7 | horní část loga |
| logo-deep | #a466de | spodní část loga |
| gradient-start | #4340a0 | přechod hlavního tlačítka (vlevo) |
| gradient-end | #8e52a6 | přechod hlavního tlačítka (vpravo) |
| gold | #f3cd66 | štítek BETA |
| danger | #ef4444 | smazání účtu |

## Texty

Všechny texty jsou v `res/values/strings.xml` česky. Stavové změny používají
tvary „připojil(a)“, protože Matrix nezná pohlaví uživatele.

## Média

`MediaLoader`:

- volá `client.loadMedia` (cache a dešifrování řeší jádro),
- dekóduje se zmenšením (`inSampleSize`) na požadovanou velikost,
- drží dekódované obrázky v `LruCache` (1/8 paměti aplikace),
- souběžné požadavky na stejný obrázek spojí do jednoho.

V UI se používá přes `LocalMediaLoader` a `rememberMatrixImage(source, w, h)`.

## Mapa a poloha

- `ui/map/PockyMap.kt`: společný obal nad MapLibre `MapView`, napojený na
  životní cyklus. Styl `asset://map/style.json`, bez loga, bez vestavěné
  atribuce, popisek „© OpenStreetMap“ kreslí Compose. Rotace a náklon jsou
  vypnuté. Na ní stojí výběr polohy, později bublina v chatu a záložka Mapa.
- `location/DeviceLocation.kt`: poloha přes systémový `LocationManager`
  (fused / GPS / síť), bez Google služeb. Čerstvá poloha do 60 s s přesností
  pod 50 m se použije hned, jinak jednorázový požadavek s limitem 20 s.
- `ui/room/LocationPicker.kt`: příloha Poloha. Uprostřed pevný špendlík, mapa se
  posouvá pod ním. Tlačítko Moje poloha (žádost o oprávnění až po klepnutí,
  pokud ještě není). Volba Přesně / Okolí ~500 m / Město ~5 km, výchozí podle
  nastavení z průvodce. U přibližné polohy kruh ukazuje přesně oblast, která
  odejde (`blurLocation` z jádra).
- Přesnost GPS se posílá jen tehdy, když špendlík stojí na vlastní poloze.
  Po posunutí mapy jde čistý bod bez `u=`.
- `abiFilters` v `app/build.gradle.kts` bere `POCKY_ABIS` (výchozí
  `arm64-v8a`), aby MapLibre nepřibalila knihovny pro další architektury.

- `ui/map/MapPreviews.kt`: statické snímky mapy (`MapSnapshotter`) pro bubliny,
  mezipaměť posledních 24 snímků v paměti. Snímek běží ve vlastním scope
  nezávisle na bublině (jinak by ho zrušilo posouvání seznamu a náhled by
  zůstal černý), limit 30 s, neúspěch se neukládá. Souběžné žádosti o stejný
  snímek se počítají jen jednou. Atribuci kreslí sám snapshotter.
- `ui/room/LocationMessages.kt`: bublina polohy 240 × 150 dp. Přesná poloha
  (do 150 m) má špendlík, přibližná kruh a popisek „Přibližně · okolí ~500 m“.
  Zoom náhledu se volí tak, aby byl kruh celý vidět. Živá poloha ukazuje
  poslední bod se zelenou tečkou. Klepnutí otevře mapu přes celou obrazovku
  s tlačítkem Navigovat (`geo:` intent s výběrem aplikace, jen po klepnutí).
- `projection.getMetersPerPixelAtLatitude` vrací metry na dp, ne na pixel.
  Poloměr se proto přepočítává přes `.dp.toPx()`.

- `location/LiveLocationService.kt`: živá poloha na pozadí. Služba na popředí
  typu `location` s trvalou notifikací „Sdílíš polohu živě · Do 14:35 · 2 chaty“
  a akcí Ukončit. Poloha ze systému každé 2 s, o odeslání rozhoduje
  `shouldSendLiveLocation` z jádra (zvlášť pro každý chat). Po vypršení nebo
  `LiveLocationEnded` chat vypadne, s posledním se služba vypne.
- Sdílení přežije zavření aplikace i ukončení procesu: seznam (chat, konec,
  přesnost) je v `SharedPreferences` `live_location`, služba je `START_STICKY`
  a po novém spuštění si ho načte. Po restartu telefonu `LiveLocationBoot`
  ukáže notifikaci „Sdílení polohy se přerušilo“, po otevření aplikace se
  sdílení obnoví (`resumeOwn`).
- `location/BackgroundRun.kt`: při spuštění živé polohy, pokud telefon může
  aplikaci uspávat, nabídne dialog vypnutí úspory baterie
  (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`). `active` (roomId → konec) čte chat i Mapa.
  Spouští se jen z popředí (klepnutím), proto stačí oprávnění „při používání“,
  `ACCESS_BACKGROUND_LOCATION` není potřeba.
- `location/LiveLocationController.kt`: sleduje živé polohy ostatních
  (`observeLiveLocations`), spouští sdílení a po přihlášení obnoví vlastní
  sdílení, která ještě nevypršela. Při odhlášení vše ukončí.
- Výběr polohy má volbu Sdílet živou polohu (15 min / 1 h / 8 h). V chatu je
  pod hlavičkou lišta „Sdílíš polohu živě · do …“ s tlačítkem Ukončit.
- `ui/map/MapTab.kt`: záložka Mapa. Avatary lidí, kteří sdílí živou polohu,
  na jejich místě (poloha starší 10 min je vybledlá), vlastní tečka, tlačítko
  Moje poloha, karty lidí (klepnutí = přesun mapy, Napsat = chat), lišta
  vlastního sdílení s Ukončit vše. Mapa je základ pro další GPS funkce.

Oprávnění: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`,
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `RECEIVE_BOOT_COMPLETED`.

Emulátor: polohu nastavíš přes Extended controls → Location. `adb emu geo fix`
se do novějších obrazů nepropíše. Na emulátoru se v přehledu celé republiky
(zoom 6) nemusí vykreslit východní část. Dlaždice se stáhnou, ale softwarové
GPU emulátoru je nevykreslí. Od zoomu 7 je vše v pořádku.

## Přechody mezi obrazovkami

`ui/navigation/Transitions.kt`:

- `Screen`: každá obrazovka má hloubku (seznam 0, chat 1, nový chat a zařízení 2,
  nová skupina a pozvání 3). `MainActivity` z ní vybírá obrazovku a
  `AnimatedContent` se `screenTransition()`: krok hlouběji přijede zprava,
  zpět odjede doprava, předchozí obrazovka se posune o čtvrtinu a prolne.
  Ověření, záloha a průvodce se jen prolnou.
- Při zavírání chatu se ještě 300 ms kreslí naposledy otevřená místnost
  (`lastRoom`), i když `openRoom` už je `null`.
- Záložky ve spodní liště: `Crossfade` 180 ms.
- `PushedLayer` (zprava): informace o chatu, galerie, mapa polohy.
  `RaisedLayer` (zespodu): výběr polohy.

## Tablety a velké obrazovky

`ui/layout/ScreenSize.kt` určuje velikost podle šířky okna
(`material3-adaptive`, funguje i pro rozdělenou obrazovku a Samsung DeX):

| Velikost | Šířka | Rozložení |
|---|---|---|
| `COMPACT` | < 600 dp | telefon: spodní lišta, obrazovky přes sebe |
| `MEDIUM` | 600–839 dp | boční lišta, seznam 320 dp + chat |
| `EXPANDED` | 840–1239 dp | boční lišta, seznam 380 dp + chat |
| `LARGE` | ≥ 1240 dp | jako EXPANDED + třetí panel 400 dp |

Velikost je v `LocalScreenSize`, obrazovky se podle ní řídí samy:

- `HomeScreen`: od MEDIUM boční lišta (`SideRail`) s tlačítkem Nový chat
  místo spodní lišty.
- `ui/layout/Panes.kt` `ChatsLayout`: seznam chatů a vpravo otevřený chat
  (`roomContent` v `MainActivity` se používá pro telefon i panel), bez chatu
  `EmptyChatPane`. Šipka Zpět v hlavičce chatu je jen na telefonu.
- `SidePanel`: na LARGE se informace o chatu, galerie a mapa polohy otevřou
  vpravo vedle chatu. Na menších šířkách přes chat (`PushedLayer`).
- `Readable`: nastavení, nový chat, skupina, zařízení, průvodce a ověření jsou
  na tabletu vycentrované s max. šířkou 680 dp. Bubliny max. 480 dp.
- Mapa: na tabletu seznam lidí vpravo nahoře místo karet dole.
- Otočení zachová otevřený chat i panely (stav je ve `ViewModel` a
  `rememberSaveable`).

Testovací emulátor: `Medium_Tablet` (2560 × 1600, 1280 dp na šířku = LARGE,
na výšku 800 dp = MEDIUM).

## Aktualizace aplikace (self-update)

**Automatické hledání** (volba „Hledat aktualizace automaticky“, výchozí zapnutá):

- Při každém spuštění aplikace a při návratu z pozadí nejvýš jednou za
  15 minut. Když je novější verze, samo vyskočí okno Nová verze. Známá nová
  verze se ukáže při každém návratu do aplikace, dokud se nezvolí Později;
  odložená verze se znovu nabídne po 24 hodinách.
- Na pozadí každých 6 hodin `UpdateCheckWorker` (WorkManager, jen s připojením).
  Najde-li novou verzi a aplikace zrovna neběží, ukáže oznámení v kanálu
  Aktualizace. Pro každou verzi jen jednou, verzi odloženou v posledních 24 hodinách nehlásí. Klepnutí
  otevře aplikaci rovnou s oknem Nová verze. Ptá se jen pokecio.com.
- Vypnutí volby zruší i kontrolu na pozadí.

- Jádro (`update.rs`): `check_update` stáhne
  `https://pokecio.com/updates/android.json`, porovná `versionCode` a `minSdk`
  a vrátí novinky a opravy v jazyce aplikace. Adresa APK musí začínat
  `https://pokecio.com/`, jinak se odmítne. `download_update` stahuje po
  částech s průběhem, počítá SHA-256 a nedokončený soubor (`.part`) nikdy
  nepoužije.
- `update/UpdateController.kt`: před instalací porovná podpisový certifikát staženého APK s nainstalovanou aplikací. Pak
  `PackageInstaller` a systémový dialog. Napoprvé je potřeba povolit
  „Instalovat neznámé aplikace“.
- `update/UpdateInstallReceiver.kt`: otevře potvrzení instalace a hlásí chyby.
- `ui/update/UpdateDialog.kt`: okno „Nová verze“ s novinkami, opravami,
  průběhem stahování a tlačítky Aktualizovat a Později. Je nad všemi
  obrazovkami, takže funguje i bez přihlášení.
- Nastavení → O aplikaci: řádek Aktualizace (stav, ruční kontrola) a přepínač
  Hledat aktualizace automaticky.

Oprávnění: `REQUEST_INSTALL_PACKAGES`. 
