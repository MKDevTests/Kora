#!/usr/bin/env python3
"""Puts the shipping translator next to an off-device one over the same bubbles.

    python scripts/ocr-bench/compare_translations.py \
        opusmt.json bench-out.txt --show 20

`opusmt.json` comes from run_translation.py and carries the corpus plus the PC
engine's output; `bench-out.txt` is what TranslationBenchActivity pulled off the
tablet, one line per corpus line in the same order.

The headline number is not fluency, which needs a human, but omission. French
runs longer than English, so an output much SHORTER than its source has dropped
something — and a dropped clause is the one error a reader cannot see. 'Damn it!
It's a breach of contract! I'm gonna sue!' coming back as 'C'est une rupture de
contrat !' looks perfectly good on the page.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

WORDS = re.compile(r"[\w']+")


def words(text: str) -> int:
    return len(WORDS.findall(text))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path, help="output of run_translation.py")
    parser.add_argument("device", type=Path, help="bench-out.txt pulled from the tablet")
    parser.add_argument("--show", type=int, default=12, help="disagreements to print")
    args = parser.parse_args()

    reference = json.loads(args.reference.read_text(encoding="utf-8"))
    names = list(reference.keys())
    order = [entry for name in names for entry in reference[name]]
    lines = args.device.read_text(encoding="utf-8").splitlines()
    if len(lines) != len(order):
        sys.exit(f"{len(lines)} lines from the device, {len(order)} in the corpus")
    for entry, line in zip(order, lines):
        entry["device"] = line

    for name in names:
        pile = reference[name]
        long_enough = [e for e in pile if words(e["text"]) >= 6]
        if not long_enough:
            continue
        print(f"\n=== {name}: {len(pile)} sentences, {len(long_enough)} long enough to judge")
        for label, key in (("device ", "device"), ("offline", "translation")):
            lost = [e for e in long_enough if words(e[key]) < words(e["text"]) * 0.6]
            ratio = sum(words(e[key]) / words(e["text"]) for e in long_enough) / len(long_enough)
            print(f"  {label}: {len(lost):3d} outputs dropped 40%+ of the words "
                  f"(mean length ratio {ratio:.2f})")

    print("\n" + "=" * 78)
    print("WHERE THE OFFLINE ENGINE SHORTENED THE MOST")
    worst = sorted(
        (e for e in order if words(e["text"]) >= 8),
        key=lambda e: words(e["translation"]) / words(e["text"]),
    )
    for entry in worst[: args.show]:
        print(f"\n  EN      {entry['text'][:92]}")
        print(f"  offline {entry['translation'][:92]}")
        print(f"  device  {entry['device'][:92]}")


if __name__ == "__main__":
    main()
