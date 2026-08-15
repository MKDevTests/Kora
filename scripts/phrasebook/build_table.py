#!/usr/bin/env python3
"""Turns the curated EN-FR expression file into the resource PhraseBook loads.

    python scripts/phrasebook/build_table.py anglais_expressions_v5_3000_enrichie.json

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

DEFAULT_OUT = Path("komelia-domain/core/src/commonMain/composeResources/files/phrasebook/en-fr.json")


def normalise(text: str) -> str:
    lowered = text.lower().replace("\u2019", "'")
    kept = "".join(c for c in lowered if c.isalnum() or c in " '" or c.isspace())
    return " ".join(kept.split())


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()

    entries = json.loads(args.source.read_text(encoding="utf-8"))["entries"]

    table: dict[str, str] = {}
    ambiguous = skipped = 0
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
        # First wins: the file is ordered by usage rank, so the earlier entry is
        # the more common reading of a duplicated key.
        table.setdefault(key, readings[0].strip())

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(
        json.dumps(dict(sorted(table.items())), ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    size = args.out.stat().st_size / 1024
    print(f"{len(entries)} entries -> {len(table)} keys ({size:.1f} KB)")
    print(f"{ambiguous} had several French readings, {skipped} were single words")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
