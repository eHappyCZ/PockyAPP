# Vlastní nasazení webové aplikace

Webová aplikace PockyAPP je statický web: HTML, JavaScript a jádro v Rustu přeložené do
WebAssembly. Nepotřebuje žádný vlastní serverový kód a funguje s libovolným Matrix homeserverem,
který povoluje přihlášení heslem. Tenhle návod ukazuje, jak ji sestavit a provozovat pro vlastní
server.

## Co je potřeba

- **Matrix homeserver** dostupný přes HTTPS, který má:
  - přihlášení heslem (`m.login.password`); servery, které nabízejí jen přihlášení v prohlížeči
    (OIDC / Matrix Authentication Service), zatím podporované nejsou,
  - nativní (zjednodušený) sliding sync – novější Synapse ho má vestavěný,
  - `/.well-known/matrix/client` na jméně serveru, pokud homeserver běží na jiné adrese
    (např. `example.org` → `https://matrix.example.org`).
- **Webový server** s HTTPS (příklady jsou pro nginx).
- **Nástroje pro sestavení:** Rust stable s cílem `wasm32-unknown-unknown`, `wasm-bindgen-cli`
  ve verzi z `Cargo.lock`, Node.js a npm. `wasm-opt` z balíku binaryen je volitelný a jádro
  zmenší.

```sh
rustup target add wasm32-unknown-unknown
cargo install wasm-bindgen-cli --version 0.2.129 --locked
```

## Nastavení

Vše, co se liší podle nasazení, je v `apps/web/pocky.config.json`:

```json
{
  "defaultServer": "example.org",
  "allowOtherServers": false,
  "connect": ["https://example.org", "https://matrix.example.org"],
  "mapTiles": ""
}
```

| Klíč | Význam |
|---|---|
| `defaultServer` | Jméno serveru na přihlašovací obrazovce (část za `:` v ID uživatelů) nebo přímá adresa homeserveru. |
| `allowOtherServers` | `false`: přihlásit se jde jen k `defaultServer`. `true`: na přihlašovací obrazovce je „Změnit“ a kdokoli může použít svůj server. |
| `connect` | Adresy, se kterými smí prohlížeč mluvit, když je `allowOtherServers` `false`: jméno serveru (kvůli `.well-known`) a samotný homeserver. |
| `mapTiles` | Adresa TileJSON vektorových dlaždic ve schématu Protomaps basemap. Prázdná hodnota skryje mapy i posílání polohy. |

Z tohoto souboru se generuje Content Security Policy (`apps/web/security.js`):

- při `allowOtherServers: false` smí prohlížeč na adresy z `connect` a na mapové dlaždice,
- při `allowOtherServers: true` na jakoukoli adresu `https:`, protože homeserver předem neznáme.
  Skripty, rámce, formuláře a pluginy zůstávají zakázané v obou případech.

`mapTiles` prosím nesměrujte na `maps.pokecio.com`, ten server slouží jen nasazení Pokecio. Malá
vlastní alternativa je [go-pmtiles](https://github.com/protomaps/go-pmtiles) s výřezem
[Protomaps basemap](https://docs.protomaps.com/basemaps/downloads). Přibalený styl mapy generuje
`scripts/map-style` (viz [setup.md](setup.md#mapa)); pohled je omezený na Česko a Slovensko, pro
jinou oblast upravte `COUNTRIES`.

## Sestavení

```sh
scripts/build-web.sh
```

Skript přeloží jádro pro web (poprvé asi 8 minut), zkopíruje styl mapy, zkontroluje typy,
sestaví aplikaci, zapíše `apps/web/deploy/headers.conf` s bezpečnostními hlavičkami a přidá
`.gz` kopie pro `gzip_static`. Výsledek je v `apps/web/dist/`.

## Nasazení s nginx

1. Obsah `apps/web/dist/` zkopírujte do kořene webu, např. `/var/www/pockyapp`.
2. `apps/web/deploy/headers.conf` zkopírujte do `/etc/nginx/snippets/pockyapp-headers.conf`.
   Po každé změně `pocky.config.json` nebo `security.js` ho nahrajte znovu.
3. Obsah `apps/web/deploy/nginx.example.conf` vložte do svého bloku `server { … }` s HTTPS
   a upravte cesty.
4. `nginx -t` a znovu načíst nginx.

Příklad posílá `index.html` s `no-cache`, soubory s otiskem v `/assets/` s mezipamětí na rok
a styl mapy v `/map/` na den. Před statické soubory nedávejte reverzní proxy a bezpečnostní
hlavičky nechte v každém bloku `location`; nginx `add_header` nedědí do bloků, které mají
vlastní.

## Kontrola homeserveru

- `https://<jméno serveru>/.well-known/matrix/client` vrací adresu homeserveru a povoluje dotazy
  z jiných domén (`Access-Control-Allow-Origin: *`).
- Homeserver odpovídá na `/_matrix/client/versions` z prohlížeče (Synapse hlavičky CORS posílá
  sám).
- Přihlášení heslem je zapnuté. Registrace zatím probíhá mimo aplikaci.

## Co webová aplikace ukládá

Všechno zůstává v prohlížeči: relace a šifrovací klíče jsou v IndexedDB, zašifrované náhodným
heslem, které je v klíči WebCrypto bez možnosti exportu. Odhlášení vše smaže. Aplikace neposílá
žádnou analytiku a nenačítá cizí skripty.

## Android a jiné servery

I aplikace pro Android se umí přihlásit k jinému serveru (Server · Změnit na přihlašovací
obrazovce). Oznámení pak dál chodí přes push bránu Pokecio (dostane jen ID události a místnosti,
nikdy obsah zprávy) a mapy jsou z `maps.pokecio.com`.
