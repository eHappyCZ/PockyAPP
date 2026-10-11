#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/apps/android/core-bindings/src/main/kotlin"

cd "$root"
cargo build -p pocky-ffi

case "$(uname)" in
    Darwin) lib="target/debug/libpocky_ffi.dylib" ;;
    *) lib="target/debug/libpocky_ffi.so" ;;
esac

rm -rf "$out/com/pokecio/app/core"
cargo run -q -p uniffi-bindgen -- generate \
    --library "$lib" \
    --language kotlin \
    --no-format \
    --out-dir "$out"
