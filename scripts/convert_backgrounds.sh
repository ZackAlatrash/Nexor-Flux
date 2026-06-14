#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
DEST="app/src/main/res/drawable"
mkdir -p "$DEST"

# Gaussian blur baked into every background (0xSIGMA). Matches the old
# pre-blurred bg_glass_orbs_blurred asset: softens the glossy orbs so text/cards
# read well, and avoids a costly per-frame runtime blur behind the parallax.
# A mode-aware veil (LocalAppColors.scrim) is layered on top at runtime in
# GlassOrbBackground for the final "frosted" readability pass.
BLUR="0x40"

# hex (filename in LightMode/DarkMode) -> resource theme name
declare -a MAP=(
  "8B5CF6 violet"  "6366F1 indigo" "3B82F6 blue"   "06B6D4 cyan"
  "10B981 emerald" "84CC16 lime"   "F59E0B amber"  "F97316 orange"
  "F43F5E rose"    "64748B slate"  "CBD5E1 silver"
)
for pair in "${MAP[@]}"; do
  hex="${pair%% *}"; name="${pair##* }"
  magick "DarkMode/#${hex}.png"  -blur "$BLUR" -quality 80 "${DEST}/bg_${name}_dark.webp"
  magick "LightMode/#${hex}.png" -blur "$BLUR" -quality 80 "${DEST}/bg_${name}_light.webp"
done
echo "Done: $(ls "${DEST}"/bg_*_*.webp | wc -l) webp files (blur $BLUR)"
