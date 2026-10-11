#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
web="$root/apps/web"

"$root/scripts/build-web-core.sh"

cd "$web"
npm ci
npm run map
npm run check
npm run build
node deploy/headers.mjs

find dist -type f \( -name "*.html" -o -name "*.js" -o -name "*.mjs" -o -name "*.css" -o -name "*.json" \
    -o -name "*.wasm" -o -name "*.svg" -o -name "*.pbf" -o -name "*.txt" \) -print0 |
    xargs -0 gzip -9 -k -f

du -sh dist
echo "dist/ je připravené k nahrání"
