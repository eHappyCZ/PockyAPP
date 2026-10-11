#!/usr/bin/env bash
set -euo pipefail

export PATH="$HOME/.cargo/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

root="$(cd "$(dirname "$0")/.." && pwd)"
jni="$root/apps/android/core-bindings/src/main/jniLibs"
profile="${POCKY_PROFILE:-android-dev}"
abis="${POCKY_ABIS:-arm64-v8a}"

targets=()
for abi in $abis; do
    targets+=(-t "$abi")
done

cd "$root"
cargo ndk "${targets[@]}" -P 28 -o "$jni" build -p pocky-ffi --profile "$profile"

"$root/scripts/gen-bindings.sh"
