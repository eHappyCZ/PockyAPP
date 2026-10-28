#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
profile="${POCKY_PROFILE:-release}"
tool="${POCKY_BINDGEN_CS:-$root/target/bindgen-cs/target/release/uniffi-bindgen-cs.exe}"
core="$root/apps/windows/PockyAPP/Core"

case "$profile" in
    dev) dir="debug" ;;
    *) dir="$profile" ;;
esac

if [ ! -x "$tool" ]; then
    rm -rf "$root/target/bindgen-cs"
    git clone -q https://github.com/NordSecurity/uniffi-bindgen-cs "$root/target/bindgen-cs"
    git -C "$root/target/bindgen-cs" checkout -q "v0.11.1+v0.31.0"
    git -C "$root/target/bindgen-cs" apply "$root/scripts/uniffi-bindgen-cs.patch"
    (cd "$root/target/bindgen-cs" && cargo build --release -p uniffi-bindgen-cs)
fi

cd "$root"
cargo build -p pocky-ffi --profile "$profile"

mkdir -p "$core"
rm -f "$core/pocky_ffi.cs"
"$tool" --library "target/$dir/pocky_ffi.dll" --out-dir "$core" --no-format
cp "target/$dir/pocky_ffi.dll" "$core/pocky_ffi.dll"

echo "$core je pripraveny"
