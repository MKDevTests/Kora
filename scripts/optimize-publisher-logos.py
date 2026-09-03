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
from collections import Counter, deque
import colorsys
import glob
import io
import os
import sys

ImageFile.LOAD_TRUNCATED_IMAGES = True  # one logo in the pack is truncated

MAX_W, MAX_H = 384, 320
TINT_MAX_LUM, TINT_MAX_SAT = 90, 0.25
DIR = os.path.join(os.path.dirname(__file__), "..",
                   "komelia-ui/src/commonMain/composeResources/files/publishers")


def _stats(im):
    """(mean luminance, mean saturation, mean alpha) over visible pixels."""
    t = im.copy()
    t.thumbnail((96, 96))
    px = list(t.getdata())
    visible = [(r, g, b, a) for r, g, b, a in px if a > 40]
    if not visible:
        return 255.0, 0.0, 0.0
    lum = sum(0.2126 * r + 0.7152 * g + 0.0722 * b for r, g, b, _ in visible) / len(visible)
    sat = sum(colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)[1]
              for r, g, b, _ in visible) / len(visible)
    alpha = sum(a for *_, a in visible) / len(visible)
    return lum, sat, alpha


def _would_tint(im):
    px = list(im.copy().getdata())
    if not px:
        return False
    if min(a for *_, a in px) > 250:
        return False  # opaque: it brings its own background
    lum, sat, _ = _stats(im)
    return lum < TINT_MAX_LUM and sat < TINT_MAX_SAT


def strip_white_plate(im):
    """Clear a flat white sheet that reaches the image border.

    152 logos ship opaque on white and drew as a white slab on the badge's dark
    pill. Only pixels connected to an edge are cleared, so the white inside a
    letter's counter survives.

    Refused when the result would not read on black: noeve_grafx is mid-grey
    line art -- too light to be tinted white, too dark to see once its sheet is
    gone -- so it keeps the ugly-but-legible slab.
    """
    im = im.convert("RGBA")
    w, h = im.size
    px = im.load()
    edge = ([px[x, 0] for x in range(w)] + [px[x, h - 1] for x in range(w)] +
            [px[0, y] for y in range(h)] + [px[w - 1, y] for y in range(h)])
    (r, g, b, a), count = Counter(edge).most_common(1)[0]
    if a < 250 or count / len(edge) < 0.55 or min(r, g, b) < 200:
        return im, False

    out = im.copy()
    opx = out.load()
    seen = bytearray(w * h)
    queue = deque()
    for x in range(w):
        queue.append((x, 0))
        queue.append((x, h - 1))
    for y in range(h):
        queue.append((0, y))
        queue.append((w - 1, y))
    cleared = 0
    while queue:
        x, y = queue.popleft()
        if x < 0 or y < 0 or x >= w or y >= h or seen[y * w + x]:
            continue
        pr, pg, pb, _ = opx[x, y]
        if abs(pr - r) > 30 or abs(pg - g) > 30 or abs(pb - b) > 30:
            continue
        seen[y * w + x] = 1
        opx[x, y] = (pr, pg, pb, 0)
        cleared += 1
        queue.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    if cleared < 0.08 * w * h:
        return im, False

    lum, _, _ = _stats(out)
    if not _would_tint(out) and lum < 140:
        return im, False  # would go dark-on-dark
    return out, True


def densify(im):
    """Lift thin semi-transparent strokes so a white tint reads as white.

    zenescope's hairline came out grey. A gamma on the alpha keeps the
    antialiasing instead of hard-edging it. Skipped once the art is already
    dense, which keeps repeated runs from compounding.
    """
    _, _, alpha = _stats(im)
    if alpha > 200:
        return im
    channel = im.getchannel("A").point(lambda v: int(255 * ((v / 255) ** 0.55)))
    return Image.merge("RGBA", tuple(im.split()[:3]) + (channel,))


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
    return lum < TINT_MAX_LUM and sat < TINT_MAX_SAT


def main():
    apply = "--apply" in sys.argv
    before = after = 0
    resized = removed = reencoded = plates = 0

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
        im, stripped = strip_white_plate(im)
        if stripped:
            plates += 1
        if _would_tint(im):
            im = densify(im)
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
    print("%s: %d resized, %d re-encoded, %d white plates stripped, %d empty removed"
          % (verb, resized, reencoded, plates, removed))
    print("%.2f MB -> %.2f MB (-%.2f MB)"
          % (before / 1048576, after / 1048576, (before - after) / 1048576))
    if not apply:
        print("re-run with --apply to write the files")


if __name__ == "__main__":
    main()
