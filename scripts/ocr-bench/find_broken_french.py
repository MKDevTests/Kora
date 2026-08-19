#!/usr/bin/env python3
"""Flags balloons whose French contains a word that is not French.

Not a quality metric -- read the next paragraph before believing a number
from this. It is a triage tool: it turns "read 1 800 balloons to find the bad
ones" into "read 45 candidates", which is the difference between a measurement
that gets done and one that does not.

What it does NOT catch, and this is most of the problem: the engine's usual
failure is a real French word chosen wrongly. "I throw my die twice" came back
"je jette ma mort", "who's right and who's left" as "qui a raison et qui
reste". Every word there exists. Measured on the bench corpus, this script
finds 43 balloons; hand-reading twelve pages of one volume found 21 faults of
which only 4 were of the kind flagged here. So a low count means nothing.

What it does catch is the decoder coming apart -- inventing a verb form that
does not exist ("frappeons", "partageions"), or emitting a word made of
fragments ("spemilere", "reecarite"). Those are worth knowing about on their
own, because no local rule can fix them and they are the argument for adapting
the model rather than the pipeline.

Precision is around one in two: wordfreq scores by corpus frequency, so rare
but perfectly good forms ("nagerons", "contredra", "banaliserait") come back
unknown. Every hit needs a human read. Requires `pip install wordfreq`.

    python scripts/ocr-bench/find_broken_french.py
"""

import re
import sys
from collections import Counter
from pathlib import Path

try:
    from wordfreq import zipf_frequency
except ImportError:
    sys.exit("needs wordfreq:  pip install wordfreq")

ROOT = Path(__file__).resolve().parents[2]
LEXICON = ROOT / "komelia-ui/src/commonMain/composeResources/files/lexicon/en.txt"
BASELINES = ROOT / "_bench-en"

WORD = re.compile(r"\b[a-zà-ÿ][a-zà-ÿ'-]{3,}\b")
STEM = re.compile(r"[a-z]{3,}")


def main():
    english = {w.strip().lower() for w in LEXICON.read_text(encoding="utf-8").splitlines() if w.strip()}
    hits, words, balloons = [], 0, 0

    for baseline in sorted(BASELINES.glob("*/baseline.tsv")):
        for line in baseline.read_text(encoding="utf-8").splitlines():
            if "\t" not in line:
                continue
            source, french = line.split("\t", 1)
            balloons += 1
            # Anything the source already contained is a name or an untranslated
            # word, not something the decoder made up -- and names are the bulk
            # of what an unfiltered run returns. Matched on a four-letter stem so
            # "Rentaro's" covers "rentaro".
            carried = set(STEM.findall(source.lower()))
            for word in WORD.findall(french.lower()):
                words += 1
                if zipf_frequency(word, "fr") > 0 or word in english:
                    continue
                stem = word.split("'")[0]
                if any(stem.startswith(c[:4]) or c.startswith(stem[:4]) for c in carried):
                    continue
                hits.append((word, source, french, baseline.parent.name))

    print(f"{balloons} balloons, {words} French words, {len(hits)} candidates "
          f"in {len({h[2] for h in hits})} balloons "
          f"({100 * len(hits) / max(words, 1):.2f}% of words)")
    print("about half will be good French that wordfreq does not carry -- read them\n")
    for word, source, french, volume in hits:
        print(f"  {word:<15} [{volume[:22]}]")
        print(f"      {source[:70]}")
        print(f"   -> {french[:70]}")
    if hits:
        print(f"\nmost frequent: {', '.join(w for w, _ in Counter(h[0] for h in hits).most_common(8))}")


if __name__ == "__main__":
    main()
