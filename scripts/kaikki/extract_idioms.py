#!/usr/bin/env python3
"""Turns a Kaikki (Wiktionary) dump into the reader's phrase table.

    python scripts/kaikki/extract_idioms.py raw-wiktextract-data.jsonl --out phrases.json

Kaikki publishes Wiktionary as one JSON object per line. What the reader needs
is a narrow slice of it: English multi-word entries that are marked idiomatic,
colloquial, slang or a phrase, and that carry a French translation. Everything
else -- single words, etymologies, pronunciations, the other 200 languages --
is dropped here rather than shipped and filtered on the tablet.

The output feeds PhraseBook, whose matching rule is whole-utterance and exact.
That rule is the reason this script is aggressive about throwing entries away:
a phrase with three candidate translations cannot be resolved without context,
and PhraseBook has none. Only entries where Wiktionary gives one unambiguous
French reading are kept, and the rest are written to a review file so the
judgement stays visible instead of silent.

The dump is large (tens of GB uncompressed) and is read line by line; nothing
here holds it in memory.
"""
from __future__ import annotations

import argparse
import json
import sys
from collections import defaultdict
from pathlib import Path

# Wiktionary's own labels. An entry has to carry one of these to be considered:
# without it, "the matter" is just a noun phrase and belongs to the engine.
IDIOM_TAGS = {"idiomatic", "colloquial", "slang", "informal", "figuratively", "phrase"}

# Parts of speech that hold set expressions. "phrase" and "proverb" are the
# obvious ones; verbs and nouns qualify only through the tags above.
IDIOM_POS = {"phrase", "proverb", "prep_phrase", "verb", "noun", "intj"}

# Below this a "phrase" is a single word, which the engine already handles and
# which is where a table like this starts doing damage: overriding "matter" or
# "break" everywhere would be far worse than any idiom it fixed.
MIN_WORDS = 2


def french_translations(entry: dict) -> list[str]:
    """Every distinct French reading Wiktionary lists for this entry."""
    out = []
    for translation in entry.get("translations", []):
        if translation.get("lang_code") != "fr":
            continue
        word = (translation.get("word") or "").strip()
        # Wiktionary marks uncertain or dialect-specific readings; neither
        # belongs in a table consulted without context.
        if not word or translation.get("tags"):
            continue
        if word not in out:
            out.append(word)
    return out


def is_idiom(entry: dict) -> bool:
    if entry.get("lang_code") != "en":
        return False
    if entry.get("pos") not in IDIOM_POS:
        return False
    word = entry.get("word") or ""
    if len(word.split()) < MIN_WORDS:
        return False
    tags = set(entry.get("tags") or [])
    for sense in entry.get("senses", []):
        tags.update(sense.get("tags") or [])
    return bool(tags & IDIOM_TAGS)


def normalise(text: str) -> str:
    """Must match PhraseBook.normalise, or a key here never matches at runtime."""
    kept = [c for c in text.lower().replace("’", "'") if c.isalnum() or c in " '\t\n"]
    return " ".join("".join(kept).split())


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("dump", type=Path, help="Kaikki JSONL, one entry per line")
    parser.add_argument("--out", type=Path, default=Path("phrases.json"))
    parser.add_argument("--review", type=Path, default=None,
                        help="where the ambiguous entries go (default: alongside --out)")
    args = parser.parse_args()

    if not args.dump.exists():
        return f"No such dump: {args.dump}"

    candidates: dict[str, list[str]] = defaultdict(list)
    seen = kept = 0
    with args.dump.open(encoding="utf-8") as handle:
        for line in handle:
            seen += 1
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                # One malformed line must not lose the other ten million.
                continue
            if not is_idiom(entry):
                continue
            translations = french_translations(entry)
            if not translations:
                continue
            key = normalise(entry["word"])
            if not key:
                continue
            for translation in translations:
                if translation not in candidates[key]:
                    candidates[key].append(translation)
            kept += 1

    # One reading or nothing. See the module docstring: PhraseBook matches
    # without context, so a second candidate is a reason to abstain, not to pick.
    unambiguous = {k: v[0] for k, v in candidates.items() if len(v) == 1}
    ambiguous = {k: v for k, v in candidates.items() if len(v) > 1}

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(
        json.dumps(dict(sorted(unambiguous.items())), ensure_ascii=False, indent=1),
        encoding="utf-8",
    )
    review = args.review or args.out.with_suffix(".review.json")
    review.write_text(
        json.dumps(dict(sorted(ambiguous.items())), ensure_ascii=False, indent=1),
        encoding="utf-8",
    )

    print(f"{seen} lines, {kept} idiomatic entries with a French reading")
    print(f"{len(unambiguous)} unambiguous -> {args.out}")
    print(f"{len(ambiguous)} with several readings -> {review} (not shipped)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
