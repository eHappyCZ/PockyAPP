#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
export POCKY_PROFILE=release
export POCKY_ABIS="${POCKY_ABIS:-arm64-v8a}"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"

if [ -z "${POCKY_KEYSTORE:-}" ]; then
    echo "POCKY_KEYSTORE není nastavené – APK se podepíše vývojovým klíčem, jen k testování." >&2
fi

cd "$root/apps/android"
./gradlew assembleRelease

apk="app/build/outputs/apk/release/app-release.apk"
echo "$apk ($(du -h "$apk" | cut -f1))"

if [ -z "${POCKY_KEYSTORE:-}" ]; then
    echo "Na web se nekopíruje, není podepsané tvým klíčem." >&2
    exit 0
fi

label="$(sed -nE 's/.*versionName = "([^"]+)".*/\1/p' app/build.gradle.kts | head -1)"
version="${label% - *}"
code="$(sed -nE 's/.*versionCode = ([0-9]+).*/\1/p' app/build.gradle.kts | head -1)"
slug="$(printf '%s' "$version" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9.]+/-/g; s/-+$//')"
name="pockyapp-$slug.apk"
web="$root/Promo-web"

mkdir -p "$web/public/download"
cp "$apk" "$web/public/download/$name"
sha="$(shasum -a 256 "$web/public/download/$name" | cut -d' ' -f1)"
size="$(stat -f%z "$web/public/download/$name")"

cat > "$web/src/data/apk.json" <<JSON
{
  "version": "$version",
  "versionCode": $code,
  "file": "/download/$name",
  "sha256": "$sha",
  "size": $size
}
JSON

echo "Web: public/download/$name, SHA-256 $sha"
echo "Nezapomeň na záznam v core/pocky-core/changelog.json (novinky v new, opravy ve fixed) a sestavení webu."
