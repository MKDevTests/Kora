#!/usr/bin/env python3
"""Builds the English word list OcrSpellRepair checks its repairs against.

    python scripts/lexicon/build_lexicon.py en_50k.txt words_alpha.txt

Two inputs, both free and offline once fetched:

  en_50k.txt     hermitdave/FrequencyWords, 2018 English, ranked by frequency
                 in film subtitles. That register is the point: it is dialogue,
                 which is what a comic balloon holds, rather than prose.
  words_alpha    dwyl/english-words. Used only to strip the frequency list of
                 what is not a word -- usernames, fragments, transcription
                 noise.

The intersection is what ships. It is deliberately not a full dictionary: the
list only ever answers "is this a word", and every extra obscure entry is one
more chance for a misread to land on something real and be left alone. 37k
words is enough to cover comic dialogue and small enough to read at the first
page turn.

Apostrophe forms are added back from the frequency list unfiltered, because
words_alpha has none and a comic is mostly "don't", "it's", "we'll".

    curl -o en_50k.txt https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_50k.txt
    curl -o words_alpha.txt https://raw.githubusercontent.com/dwyl/english-words/master/words_alpha.txt
"""
from __future__ import annotations

import argparse
from pathlib import Path

DEFAULT_OUT = Path("komelia-ui/src/commonMain/composeResources/files/lexicon/en.txt")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("frequency", type=Path)
    parser.add_argument("dictionary", type=Path)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()

    ranked = [
        line.split()[0]
        for line in args.frequency.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    known = {w.strip() for w in args.dictionary.read_text(encoding="utf-8").split()}

    plain = {w for w in ranked if w.isalpha() and len(w) >= 2 and w in known}
    contracted = {w for w in ranked if "'" in w and w.replace("'", "").isalpha()}
    words = sorted(plain | contracted)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text("\n".join(words) + "\n", encoding="utf-8")
    size = args.out.stat().st_size / 1024
    print(f"{len(ranked)} ranked -> {len(words)} words ({size:.1f} KB)")
    print(f"    {len(plain)} plain, {len(contracted)} with an apostrophe")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
