#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
DEST="app/src/main/res/drawable"
mkdir -p "$DEST"

# hex (filename in LightMode/DarkMode) -> resource theme name
declare -a MAP=(
  "8B5CF6 violet"  "6366F1 indigo" "3B82F6 blue"   "06B6D4 cyan"
  "10B981 emerald" "84CC16 lime"   "F59E0B amber"  "F97316 orange"
  "F43F5E rose"    "64748B slate"  "CBD5E1 silver"
)
for pair in "${MAP[@]}"; do
  hex="${pair%% *}"; name="${pair##* }"
  cwebp -q 80 "DarkMode/#${hex}.png"  -o "${DEST}/bg_${name}_dark.webp"
  cwebp -q 80 "LightMode/#${hex}.png" -o "${DEST}/bg_${name}_light.webp"
done
echo "Done: $(ls "${DEST}"/bg_*_*.webp | wc -l) webp files"
