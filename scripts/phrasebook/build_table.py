#!/usr/bin/env python3
"""Turns the curated EN-FR expression file into the resource PhraseBook loads.

    python scripts/phrasebook/build_table.py \
        anglais_expressions_v5_3000_enrichie.json \
        anglais_expressions_v5_1_diff.json

Several sources are read in order and the first reading of a key wins, so a
later file adds to the table without ever overriding it. That is what the diff
files are: V5.1 says outright that it modifies no V5 entry, and the build
prints the overlap so the claim is checked rather than trusted.

The source carries fifty fields per entry -- register, CEFR level, examples,
usage notes. Two of them ship: the expression and its French reading. The rest
is what the list was built for, not what a reader needs at a page turn.

Entries with more than one French reading are dropped, not resolved. PhraseBook
matches a whole utterance with no context, so a second candidate is a reason to
abstain: "Come on" is "Allez !" or "sérieusement !" and nothing at runtime can
tell which. Roughly a quarter of the file goes this way and that is correct.

normalise() below must stay identical to PhraseBook.normalise. A drift there
does not fail loudly -- the keys simply never match and the table silently does
nothing. PhraseBookTableTest checks a sample of the shipped keys against the
Kotlin side for exactly that reason.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

DEFAULT_OUT = Path("komelia-ui/src/commonMain/composeResources/files/phrasebook/en-fr.json")


def normalise(text: str) -> str:
    lowered = text.lower().replace("\u2019", "'")
    kept = "".join(c for c in lowered if c.isalnum() or c in " '" or c.isspace())
    return " ".join(kept.split())


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("sources", type=Path, nargs="+")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()

    table: dict[str, str] = {}
    total = 0
    for source in args.sources:
        entries = json.loads(source.read_text(encoding="utf-8"))["entries"]
        total += len(entries)
        added = ambiguous = skipped = 0
        # Collisions with an EARLIER file, reported separately from collisions
        # inside this one: the first says two sources disagree about a key and
        # is worth a look, the second is just the same expression listed twice.
        clashes: list[str] = []
        for entry in entries:
            readings = entry.get("translations_fr") or []
            if len(readings) != 1:
                ambiguous += 1
                continue
            key = normalise(entry.get("expression") or "")
            # A single word here would override that word everywhere it appears,
            # which is far more damage than any expression it fixes.
            if not key or " " not in key:
                skipped += 1
                continue
            reading = readings[0].strip()
            # First wins: the file is ordered by usage rank, so the earlier entry
            # is the more common reading of a duplicated key.
            if key in table:
                if table[key] != reading:
                    clashes.append(f"{key}: kept '{table[key]}', ignored '{reading}'")
                continue
            table[key] = reading
            added += 1
        print(f"{source.name}: {len(entries)} entries -> +{added} keys "
              f"({ambiguous} ambiguous, {skipped} single words, {len(clashes)} already known)")
        for clash in clashes[:10]:
            print(f"    {clash}")

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(
        json.dumps(dict(sorted(table.items())), ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    size = args.out.stat().st_size / 1024
    print(f"{total} entries read -> {len(table)} keys ({size:.1f} KB)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
