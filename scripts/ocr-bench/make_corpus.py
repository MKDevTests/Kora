#!/usr/bin/env python3
"""Builds a translation corpus out of bubbles the merge actually produced.

    python scripts/ocr-bench/make_corpus.py <report-dir>... --out corpus.json

Reads the report.txt written by VolumeReplayTest, so what comes out is exactly
what the reader would hand to the translator on those pages — not hand-picked
sentences, and not text invented for the occasion.

Sound effects are dropped: the reader does not translate them. Everything else
is kept, deduplicated, and split into two piles:

  plain  — ordinary dialogue, for measuring the everyday case
  idiom  — contractions, slang markers, interjections, all-caps emphasis, the
           lines where a small model is expected to struggle

A bench that only holds easy sentences would tell us the two engines are
equivalent no matter which one is better.
"""
from __future__ import annotations

import argparse
import json
import random
import re
from pathlib import Path

BLOCK = re.compile(
    r"^\s+block\s+\d+\s+\[[^\]]*\]\s+w=\s*\d+%\s+fill=\s*\d+%\s+"
    r"lines=(\d+)\s+h=[\d.]+x(\s+SFX)?\s+(.*)$"
)

# Lines that lean on register rather than vocabulary. Deliberately narrow: these
# are markers of informal speech, not an attempt to detect idioms, which is what
# the bench is meant to find out.
INFORMAL = re.compile(
    r"\b(gonna|wanna|gotta|ain't|dang|heck|damn|jeez|geez|yikes|whoa|huh|"
    r"gosh|golly|crap|dude|guys|kinda|sorta|y'all|nope|yep|yeah|hey|oh|ugh|"
    r"screw|freakin|darn|shoot|bro|man)\b",
    re.IGNORECASE,
)


def sentences(report: Path):
    for raw in report.read_text(encoding="utf-8", errors="replace").splitlines():
        m = BLOCK.match(raw)
        if not m or m.group(2):  # sound effects are never translated
            continue
        text = m.group(3).strip()
        # A word split across two lines is rejoined by the reader before it
        # translates; do the same so the corpus is not full of 'CHEER- FUL'.
        text = re.sub(r"(\w)- (\w)", r"\1\2", text)
        yield text


def is_worth_translating(text: str) -> bool:
    words = [w for w in re.split(r"\s+", text) if w]
    if len(words) < 3 or len(words) > 40:
        return False
    letters = sum(c.isalpha() for c in text)
    return letters >= len(text) * 0.6


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reports", type=Path, nargs="+", help="directories holding report.txt")
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--size", type=int, default=200, help="sentences per pile")
    parser.add_argument("--seed", type=int, default=17)
    args = parser.parse_args()

    seen: dict[str, str] = {}
    for directory in args.reports:
        report = directory / "report.txt"
        if not report.exists():
            print(f"skipping {directory}: no report.txt")
            continue
        for text in sentences(report):
            if is_worth_translating(text):
                seen.setdefault(text, directory.name)

    plain, idiom = [], []
    for text, source in sorted(seen.items()):
        (idiom if INFORMAL.search(text) else plain).append({"text": text, "volume": source})

    random.seed(args.seed)
    random.shuffle(plain)
    random.shuffle(idiom)
    corpus = {
        "plain": plain[: args.size],
        "idiom": idiom[: args.size],
    }
    args.out.write_text(json.dumps(corpus, ensure_ascii=False, indent=1), encoding="utf-8")

    print(f"{len(seen)} distinct sentences found")
    print(f"  plain: {len(plain)} available, {len(corpus['plain'])} kept")
    print(f"  idiom: {len(idiom)} available, {len(corpus['idiom'])} kept")
    print(f"-> {args.out}")


if __name__ == "__main__":
    main()
