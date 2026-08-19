#!/usr/bin/env python3
"""Turns a reading session's log into rows of a translation error corpus.

The corpus is the thing every design document asked for and none of them could
have built: a versioned file of balloons that came out wrong, each with enough
of the pipeline's own account of itself to say *why*. Counting errors by hand
in a chat window worked once, for 242 balloons, and none of that survived.

What this cannot do is decide which balloons are wrong. That is a judgement,
and it stays a human one -- the script fills in every column the instrumented
log already knows and leaves `error_category` blank. A row with no category is
an unreviewed balloon, not an error.

Usage:

    adb logcat -d -s KoraTranslate > session.txt
    python scripts/ocr-bench/collect_errors.py session.txt

New balloons are appended; ones already in the file keep whatever categories
and expected translations were written against them, so re-running after a
longer read never loses review work.
"""

import argparse
import csv
import re
import sys
from pathlib import Path

CORPUS = Path(__file__).resolve().parent / "translation_errors.tsv"

# Filled from the log.
AUTOMATIC = [
    "book", "page", "block",
    "source",            # what the translator was given, after repairs and casing
    "repairs",           # RULE:'before'>'after', space separated, blank if none
    "grouped_source",    # the joined sentence, when this balloon was part of one
    "phrasebook",        # TIER:key, blank when the engine answered
    "seam",              # why this balloon was or was not joined to the next
    "output",            # what the reader painted
]
# Filled by a person, and never overwritten by a later run.
MANUAL = [
    "error_category",    # A idiom, B fragment, C construction, D OCR, E1 case, E2 register
    "expected_fr",       # only where one right answer exists: A and D
    "acceptable_fr",     # several, separated by | -- for C and E2
    "severity",          # meaning_changed | awkward | cosmetic
    "notes",
]
COLUMNS = AUTOMATIC + MANUAL

# "page 0R9KSZ1QCAQXQ_1 block 14 rect=[...] conf=.. fill=.. lines=.. src='...'
#  [sent='...' ][fix=.. ][pb=.. ][seam=.. ]-> '...'"
LINE = re.compile(
    r"page (?P<page>\S+) block (?P<block>\d+) rect=\S+ [\d,]*\S*\s*"
    r"conf=\S+ fill=\S+ lines=\d+ "
    r"src='(?P<src>.*?)' "
    r"(?:sent='(?P<sent>.*?)' )?"
    r"(?P<fixes>(?:fix=\S+:'.*?'>'.*?' )*)"
    r"(?:pb=(?P<pb>\S+:'.*?') )?"
    r"(?:seam=(?P<seam>\w+) )?"
    r"-> '(?P<out>.*)'$"
)


def key_of(row):
    """What makes a row the same balloon on a later run.

    The source text is part of it, not just the page and block. Rows seeded by
    hand carry no page or block -- they were read on a screen, not parsed out of
    a log -- and keying without the text collapsed every one of them into a
    single row, silently, on the first run. The cost is that a balloon whose
    source changes under an OCR fix appears as a new row rather than a refreshed
    one, which is the safer of the two failures: it never deletes a review.
    """
    return row["book"], row["page"], row["block"], row["source"]


def parse(text):
    """Every instrumented balloon in the log, newest occurrence winning."""
    rows = {}
    for line in text.splitlines():
        m = LINE.search(line)
        if not m:
            continue
        page = m.group("page")
        # PageId is "<bookId>_<page number>"; the book half is what identifies
        # the volume across sessions, the number is where to look.
        book, _, number = page.rpartition("_")
        row = {
            "book": book or page,
            "page": number,
            "block": m.group("block"),
            "source": m.group("src"),
            "repairs": " ".join(
                f.removeprefix("fix=") for f in (m.group("fixes") or "").split() if f
            ),
            "grouped_source": m.group("sent") or "",
            "phrasebook": m.group("pb") or "",
            "seam": m.group("seam") or "",
            "output": m.group("out"),
        }
        rows[key_of(row)] = row
    return rows


def existing():
    if not CORPUS.is_file():
        return {}
    with CORPUS.open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f, delimiter="\t")
        return {key_of(r): r for r in reader}


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("log", type=Path, help="a logcat dump of the KoraTranslate tag")
    args = ap.parse_args()
    if not args.log.is_file():
        sys.exit(f"no such log: {args.log}")

    found = parse(args.log.read_text(encoding="utf-8", errors="replace"))
    if not found:
        sys.exit(
            "no instrumented balloons in that log.\n"
            "  A build before the instrumentation logs no 'page ... block ...' lines,\n"
            "  and a page answered from the scan cache logs none either -- restart the\n"
            "  app and read pages you have not read since it started."
        )

    kept = existing()
    added = updated = 0
    for key, row in found.items():
        if key in kept:
            # The pipeline's account may have changed under a fix; the review
            # written against it has not, and is the expensive half.
            before = {c: kept[key].get(c, "") for c in AUTOMATIC}
            if before != row:
                kept[key].update(row)
                updated += 1
        else:
            kept[key] = {**row, **{c: "" for c in MANUAL}}
            added += 1

    with CORPUS.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, COLUMNS, delimiter="\t", extrasaction="ignore")
        writer.writeheader()
        for key in sorted(kept, key=lambda k: (k[0], int(k[1] or 0), int(k[2] or 0), k[3])):
            writer.writerow({c: kept[key].get(c, "") for c in COLUMNS})

    reviewed = sum(1 for r in kept.values() if r.get("error_category"))
    print(f"{CORPUS.name}: {len(kept)} balloons  (+{added} new, {updated} refreshed)")
    print(f"  reviewed: {reviewed}    awaiting a category: {len(kept) - reviewed}")
    if added:
        print("\n  fill error_category on the new rows; a blank one means "
              "'not looked at yet', not 'correct'.")


if __name__ == "__main__":
    main()
