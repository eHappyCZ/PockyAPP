#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
profile="${POCKY_PROFILE:-web}"
out="$root/apps/web/src/core"
export RUST_MIN_STACK="${RUST_MIN_STACK:-67108864}"

cd "$root"
cargo build -p pocky-web --target wasm32-unknown-unknown --profile "$profile"

wasm-bindgen --target web --out-dir "$out" --out-name pocky \
    "target/wasm32-unknown-unknown/$profile/pocky_web.wasm"

if command -v wasm-opt >/dev/null; then
    wasm-opt -Oz --enable-bulk-memory --enable-nontrapping-float-to-int \
        "$out/pocky_bg.wasm" -o "$out/pocky_bg.wasm"
fi

echo "$out/pocky_bg.wasm ($(du -h "$out/pocky_bg.wasm" | cut -f1))"
