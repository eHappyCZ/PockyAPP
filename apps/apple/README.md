# Apple

SwiftUI aplikace pro iOS a iPadOS nad společným jádrem v Rustu.

```
PockyCore/          Swift Package: XCFramework s jádrem a vygenerované Swift bindingy
PockyAPP/           zdrojáky aplikace
project.yml         definice projektu pro XcodeGen
```

`PockyAPP.xcodeproj`, `PockyFFI.xcframework` a `PockyCore.swift` se generují a nejsou v gitu.

## Sestavení na Macu

Jednorázově:

```sh
xcode-select --install
brew install xcodegen
rustup target add aarch64-apple-ios aarch64-apple-ios-sim
```

Pak vždy, když se změní jádro:

```sh
scripts/build-apple.sh          # jádro pro iPhone i simulátor + Swift bindingy
cd apps/apple
xcodegen generate               # vytvoří PockyAPP.xcodeproj
open PockyAPP.xcodeproj
```

V Xcode vyber simulátor a spusť (⌘R). Pro skutečný iPhone nastav v targetu PockyAPP svůj tým
v Signing & Capabilities.

`POCKY_PROFILE=release scripts/build-apple.sh` sestaví optimalizované jádro pro vydání.
Jen pro simulátor stačí `POCKY_APPLE_TARGETS=aarch64-apple-ios-sim`.

## Co umí

Přihlášení k libovolnému Matrix serveru, session a klíč k databázi v Keychainu (jen v tomto
zařízení), seznam chatů s filtry, hledáním, nepřečtenými zprávami a avatary, odhlášení.
