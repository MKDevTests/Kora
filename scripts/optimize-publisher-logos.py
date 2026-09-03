#!/usr/bin/env python3
"""Shrink the bundled publisher logos, then write the two sidecars the app reads.

The pack ships whatever resolution the source site served -- up to 3260x672 --
while every screen clamps a logo to 120.dp wide (ImmersiveDetailScaffold) or
100.dp (the three immersive contents). rememberPublisherLogo decodes the PNG at
full resolution with no downsampling, so opening one series page allocated up to
18 MB of bitmap for a badge a centimetre tall.

384x320 covers 120.dp x 96.dp even on a 3x screen.

Two files are written next to the images because Compose Resources cannot list a
directory at runtime:

  _index.txt  every shipped key, so the matcher can fall back from "Pika" to
              "pika_dition" without guessing.
  _tint.txt   the keys the hero badge has to tint white, see needs_white_tint.

Run this again after regenerating the pack.
"""
from PIL import Image, ImageFile
import colorsys
import glob
import io
import os
import sys

ImageFile.LOAD_TRUNCATED_IMAGES = True  # one logo in the pack is truncated

MAX_W, MAX_H = 384, 320
DIR = os.path.join(os.path.dirname(__file__), "..",
                   "komelia-ui/src/commonMain/composeResources/files/publishers")


def is_image(path):
    try:
        Image.open(path).verify()
        return True
    except Exception:
        return False


def needs_white_tint(path):
    """True when the hero badge would swallow this logo.

    ImmersiveDetailScaffold draws the badge on a black pill at 60% opacity.
    Near-black line art on a transparent ground disappears into it -- measured
    on the real pack, that is 126 logos that looked like they were never
    bundled at all. Coloured art is left alone: a red disc reads fine on black,
    and tinting it white would flatten the logo into a blob. So does opaque
    art, which brings its own background.
    """
    try:
        im = Image.open(path).convert("RGBA")
        im.load()
    except Exception:
        return False  # map_ini.png is a text note the generator left behind
    im.thumbnail((96, 96))
    px = list(im.getdata())
    visible = [(r, g, b) for r, g, b, a in px if a > 40]
    if not visible:
        return False
    if min(a for *_, a in px) > 250:
        return False
    lum = sum(0.2126 * r + 0.7152 * g + 0.0722 * b for r, g, b in visible) / len(visible)
    sat = sum(colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)[1]
              for r, g, b in visible) / len(visible)
    return lum < 90 and sat < 0.25


def main():
    apply = "--apply" in sys.argv
    before = after = 0
    resized = removed = reencoded = 0

    for path in sorted(glob.glob(os.path.join(DIR, "*.png"))):
        size = os.path.getsize(path)
        before += size
        # Empty files are failed downloads from the pack generator. They render
        # nothing today (decodeToImageBitmap throws, runCatching swallows it),
        # so dropping them costs no logo and removes bundle entries.
        if size == 0:
            removed += 1
            if apply:
                os.remove(path)
            continue
        try:
            im = Image.open(path)
            im.load()
        except Exception:
            after += size  # not an image (map_ini.png): leave it alone
            continue
        w, h = im.size
        scale = min(MAX_W / w, MAX_H / h, 1.0)
        if scale < 1.0:
            im = im.convert("RGBA").resize(
                (max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)
            resized += 1
        else:
            if im.format != "PNG" or im.mode != "RGBA":
                reencoded += 1
            im = im.convert("RGBA")
        if apply:
            im.save(path, "PNG", optimize=True)
            after += os.path.getsize(path)
        else:
            buf = io.BytesIO()
            im.save(buf, "PNG", optimize=True)
            after += buf.tell()

    if apply:
        keys = sorted(os.path.splitext(os.path.basename(f))[0]
                      for f in glob.glob(os.path.join(DIR, "*.png"))
                      if os.path.getsize(f) > 0 and is_image(f))
        with open(os.path.join(DIR, "_index.txt"), "w",
                  encoding="utf-8", newline="\n") as fh:
            fh.write("\n".join(keys) + "\n")
        print("index: %d keys" % len(keys))

        tint = sorted(k for k in keys
                      if needs_white_tint(os.path.join(DIR, k + ".png")))
        with open(os.path.join(DIR, "_tint.txt"), "w",
                  encoding="utf-8", newline="\n") as fh:
            fh.write("\n".join(tint) + "\n")
        print("tint: %d logos need a white tint on the dark badge" % len(tint))

    verb = "applied" if apply else "dry run"
    print("%s: %d resized, %d re-encoded, %d empty removed"
          % (verb, resized, reencoded, removed))
    print("%.2f MB -> %.2f MB (-%.2f MB)"
          % (before / 1048576, after / 1048576, (before - after) / 1048576))
    if not apply:
        print("re-run with --apply to write the files")


if __name__ == "__main__":
    main()
