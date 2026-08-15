#!/usr/bin/env python3
"""Turns a logcat into the per-page cost of translating a chapter.

    adb logcat -d > kora.log
    python scripts/ocr-bench/perf_breakdown.py kora.log

Reads the KoraPerf lines PerfTrace writes and reports each stage as a median
and a 95th percentile. The median says what a page usually costs; the p95 says
what the reader actually notices, and the two are far apart when a scan queues
behind another one.

The queue is the point of the report. Every stage is timed from the moment it
starts, which is after the mutex is taken, so a page that waited four seconds
behind another scan looks instant in each stage and slow to the reader. The
wait is what is left over when the stages are subtracted from the page total,
and that leftover is what the architecture document calls TimeToReady.

Nothing here decides anything. It exists so the prefetch work starts from
measured numbers rather than from an assumption about which stage dominates.
"""
from __future__ import annotations

import argparse
import re
import statistics
from collections import defaultdict
from pathlib import Path

# "reader.ocr.small took 3912ms (47 items)" -- the count is optional.
SAMPLE = re.compile(r"KoraPerf.*?\b([a-z][\w.]*) took (\d+)ms(?: \((\d+) items\))?")

# Printed in pipeline order rather than alphabetically, so the report reads the
# way a page is processed.
ORDER = [
    "reader.page.total",
    "reader.ocr.bitmap",
    "reader.ocr.small",
    "reader.ocr.tiny",
    "reader.ocr.mlkit",
    "reader.ocr.merge",
    "reader.translate",
]


def percentile(values: list[int], fraction: float) -> int:
    ordered = sorted(values)
    index = min(int(len(ordered) * fraction), len(ordered) - 1)
    return ordered[index]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("log", type=Path)
    args = parser.parse_args()

    # PowerShell's `>` writes UTF-16, so a logcat captured the obvious way is
    # unreadable as UTF-8 and every line silently fails to match -- which looks
    # exactly like an app that logged nothing. Detected rather than demanded.
    raw = args.log.read_bytes()
    encoding = "utf-16" if raw[:2] in (b"\xff\xfe", b"\xfe\xff") else "utf-8"

    samples: dict[str, list[int]] = defaultdict(list)
    counts: dict[str, list[int]] = defaultdict(list)
    for line in raw.decode(encoding, errors="replace").splitlines():
        match = SAMPLE.search(line)
        if not match:
            continue
        samples[match.group(1)].append(int(match.group(2)))
        if match.group(3):
            counts[match.group(1)].append(int(match.group(3)))

    if not samples:
        print("No KoraPerf lines in that log. Translation has to have run at least once.")
        return 1

    labels = [l for l in ORDER if l in samples] + sorted(set(samples) - set(ORDER))
    width = max(len(l) for l in labels)
    print(f"{'stage'.ljust(width)}   n   median      p95    items")
    for label in labels:
        values = samples[label]
        item = counts.get(label)
        shown = f"{statistics.median(item):.0f}" if item else ""
        print(
            f"{label.ljust(width)} {len(values):3d} "
            f"{statistics.median(values):7.0f}ms {percentile(values, 0.95):7d}ms  {shown:>6}"
        )

    total = samples.get("reader.page.total")
    if total:
        # Only the page pipeline. The same logger carries Komga's server calls
        # -- getOne, getOneSeries, prev, background -- and the first version of
        # this summed those too, which produced a page whose stages cost four
        # times the page and a negative remainder.
        stages = [l for l in labels
                  if l.startswith("reader.") and l != "reader.page.total"]
        # Per-page, not median-of-medians: the stages of one page add up, the
        # medians of different pages do not.
        inner = sum(statistics.median(samples[l]) for l in stages if samples[l])
        wait = statistics.median(total) - inner
        print()
        print(f"median page {statistics.median(total):.0f}ms, "
              f"stages {inner:.0f}ms, unaccounted {wait:.0f}ms")
        print("The unaccounted part is time queued behind another scan, and it is")
        print("what a prefetch would have to remove. If it is small, prefetching")
        print("moves the cost rather than saving it.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
