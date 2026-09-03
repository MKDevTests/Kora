#!/usr/bin/env python3
"""Shrink the bundled publisher logos to the size the UI actually draws.

The pack ships whatever resolution the source site served -- up to 3260x672 --
while every screen clamps a logo to 120.dp wide (ImmersiveDetailScaffold) or
100.dp (the three immersive contents). rememberPublisherLogo decodes the PNG at
full resolution with no downsampling and no cache, so opening one series page
allocated up to 18 MB of bitmap for a badge a centimetre tall.

384x320 covers 120.dp x 96.dp even on a 3x screen. Run this again after
regenerating the pack.
"""
from PIL import Image, ImageFile
import glob, os, sys

ImageFile.LOAD_TRUNCATED_IMAGES = True  # one logo in the pack is truncated

MAX_W, MAX_H = 384, 320
DIR = os.path.join(os.path.dirname(__file__), "..",
                   "komelia-ui/src/commonMain/composeResources/files/publishers")

def main():
    apply = "--apply" in sys.argv
    before = after = 0
    resized = removed = reencoded = 0
    for path in sorted(glob.glob(os.path.join(DIR, "*.png"))):
        size = os.path.getsize(path)
        before += size
        # Empty files are failed downloads from the pack generator. They render
        # nothing today (decodeToImageBitmap throws, runCatching swallows it),
        # so dropping them costs no logo and removes 41 bundle entries.
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
            import io
            buf = io.BytesIO()
            im.save(buf, "PNG", optimize=True)
            after += buf.tell()

    if apply:
        # Compose Resources cannot list a directory at runtime, and the
        # matcher needs to know what is on the shelf before it can fall back
        # from "Pika" to "pika_dition". So the shelf is written out here.
        keys = sorted(os.path.splitext(os.path.basename(f))[0]
                      for f in glob.glob(os.path.join(DIR, "*.png"))
                      if os.path.getsize(f) > 0)
        with open(os.path.join(DIR, "_index.txt"), "w",
                  encoding="utf-8", newline=chr(10)) as fh:
            fh.write(chr(10).join(keys) + chr(10))
        print(f"index: {len(keys)} keys")

    verb = "applied" if apply else "dry run"
    print(f"{verb}: {resized} resized, {reencoded} re-encoded, {removed} empty removed")
    print(f"{before / 1048576:.2f} MB -> {after / 1048576:.2f} MB "
          f"(-{(before - after) / 1048576:.2f} MB)")
    if not apply:
        print("re-run with --apply to write the files")

if __name__ == "__main__":
    main()
