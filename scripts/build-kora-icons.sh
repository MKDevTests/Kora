#!/bin/bash
# Regenerate Android launcher icons from scripts/kora-icon-source.png.
# Requires ImageMagick (`magick` in PATH or under /c/Program Files/ImageMagick-*).

set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

SRC="scripts/kora-icon-source.png"
RES="komelia-app/src/androidMain/res"

[[ -f "$SRC" ]] || { echo "Source $SRC not found"; exit 1; }

if command -v magick >/dev/null 2>&1; then
    MAGICK=magick
else
    MAGICK="/c/Program Files/ImageMagick-7.1.2-Q16-HDRI/magick"
fi

mkdir -p "$RES/mipmap-mdpi" "$RES/mipmap-hdpi" "$RES/mipmap-xhdpi" "$RES/mipmap-xxhdpi" "$RES/mipmap-xxxhdpi"

echo "==> Generating density-specific launcher PNGs"
for entry in 48:mdpi 72:hdpi 96:xhdpi 144:xxhdpi 192:xxxhdpi; do
    size="${entry%:*}"
    dpi="${entry#*:}"
    "$MAGICK" "$SRC" -resize "${size}x${size}" "$RES/mipmap-${dpi}/ic_launcher.png"
    cp "$RES/mipmap-${dpi}/ic_launcher.png" "$RES/mipmap-${dpi}/ic_launcher_round.png"
done

echo "==> Generating adaptive icon foreground (icon centered with 33% safe-zone padding)"
"$MAGICK" "$SRC" -resize 264x264 -background none -gravity center -extent 432x432 \
    "$RES/drawable/ic_launcher_foreground.png"

echo "==> Generating adaptive icon background (#0B0F1A solid)"
"$MAGICK" -size 432x432 canvas:"#0B0F1A" "$RES/drawable/ic_launcher_background.png"

echo "==> Done"
