#!/usr/bin/env bash
set -euo pipefail

export PATH="$HOME/.cargo/bin:$PATH"

root="$(cd "$(dirname "$0")/.." && pwd)"
profile="${POCKY_PROFILE:-apple-dev}"
targets="${POCKY_APPLE_TARGETS:-aarch64-apple-ios aarch64-apple-ios-sim}"
package="$root/apps/apple/PockyCore"
work="$root/target/apple"

case "$profile" in
    dev) dir="debug" ;;
    *) dir="$profile" ;;
esac

cd "$root"

libraries=()
for target in $targets; do
    cargo build -p pocky-ffi --target "$target" --profile "$profile"
    libraries+=("target/$target/$dir/libpocky_ffi.a")
done

rm -rf "$work"
mkdir -p "$work/swift" "$work/headers"

cargo run -q -p uniffi-bindgen -- generate \
    --library "${libraries[0]}" \
    --language swift \
    --out-dir "$work/swift"

cp "$work/swift/PockyFFI.h" "$work/headers/"
cp "$work/swift/PockyFFI.modulemap" "$work/headers/module.modulemap"

args=()
for library in "${libraries[@]}"; do
    args+=(-library "$library" -headers "$work/headers")
done

rm -rf "$package/PockyFFI.xcframework"
xcodebuild -create-xcframework "${args[@]}" -output "$package/PockyFFI.xcframework"

mkdir -p "$package/Sources/PockyCore"
cp "$work/swift/PockyCore.swift" "$package/Sources/PockyCore/PockyCore.swift"

echo "$package je připravený"
