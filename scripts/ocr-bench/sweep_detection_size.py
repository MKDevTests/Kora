"""Sweeps the detection input size and reports the trade, not just the speed.

Detection time is taken from the library's own elapse, not from the wall clock
around the whole run: recognition is more than a third of a page and a wrapper
that finds fewer boxes finishes sooner for the wrong reason.

Boxes lost against full resolution is the other column, and it is the one that
decides. A balloon the reader can ignore is cheaper than a balloon that is not
there.
"""
import statistics
import sys
from pathlib import Path

import cv2

sys.path.insert(0, str(Path("scripts/ocr-bench")))
from run_ocr import build_engine  # noqa: E402

PAGES = sorted(Path("C:/Users/mathi/AppData/Local/Temp/claude/ramen167").glob("*.webp"))


def run(limit: int, limit_type: str):
    engine = build_engine(False, "small", limit, limit_type)
    det_times, texts = [], {}
    for page in PAGES:
        image = cv2.imread(str(page))
        result, elapse = engine(image)
        # elapse is [det, cls, rec]; cls is off so the middle is None.
        det_times.append(elapse[0] or 0)
        texts[page.name] = {(r[1] or "").strip() for r in (result or [])}
    return statistics.median(det_times), texts


if __name__ == "__main__":
    base_time, base_texts = run(0, "")
    total = sum(len(v) for v in base_texts.values())
    print(f"{'limite':>8} {'det median':>11} {'boites':>7} {'perdues':>8} {'nouvelles':>10}")
    print(f"{'defaut':>8} {base_time:>10.2f}s {total:>7} {0:>8} {0:>10}")
    for limit in (1600, 1280, 960):
        t, texts = run(limit, "max")
        n = sum(len(v) for v in texts.values())
        lost = sum(len(base_texts[k] - v) for k, v in texts.items())
        new = sum(len(v - base_texts[k]) for k, v in texts.items())
        print(f"{limit:>8} {t:>10.2f}s {n:>7} {lost:>8} {new:>10}")
