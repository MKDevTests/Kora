#!/usr/bin/env python3
"""Finds the user-visible literals left in the UI, grouped by screen area.

The translation itself is hand-written — this only does the counting and the
mechanical rewriting, because 2000 literals spread over 562 files is not a job
for a human reading diffs.

  python scripts/i18n-extract.py count            # what is left, by area
  python scripts/i18n-extract.py list <area>      # unique literals of one area
"""
from __future__ import annotations

import re
import sys
from collections import Counter
from pathlib import Path

UI = Path(__file__).resolve().parent.parent / "komelia-ui" / "src"

# Areas, in the order a user meets them. First match wins.
AREAS = [
    ("series", ("/ui/series/",)),
    ("book", ("/ui/book/",)),
    ("library", ("/ui/library/", "/ui/home/", "/ui/search/")),
    ("reader", ("/ui/reader/",)),
    ("dialogs", ("/ui/dialogs/",)),
    ("menus", ("/ui/common/menus/",)),
    ("common", ("/ui/common/",)),
    ("settings", ("/ui/settings/",)),
    ("other", ("",)),
]

# Conservative on purpose: a literal that is not obviously a label stays put.
PATTERNS = [
    re.compile(r'\bText\(\s*"((?:[^"\\]|\\.)+)"\s*[,)]'),
    re.compile(r'contentDescription\s*=\s*"((?:[^"\\]|\\.)+)"'),
    re.compile(r'placeholder\s*=\s*\{\s*Text\(\s*"((?:[^"\\]|\\.)+)"\s*\)\s*\}'),
]

# Not text the user reads: keys, ids, tags, format helpers.
SKIP = re.compile(r'^(\s*|[a-z_]+:[a-z_]+|%[sd]|\d+|[A-Za-z]{1,2})$')


def area_of(path: Path) -> str:
    posix = path.as_posix()
    for name, prefixes in AREAS:
        if any(p in posix or p == "" for p in prefixes):
            return name
    return "other"


def literals(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8", errors="replace")
    found: list[str] = []
    for pattern in PATTERNS:
        for match in pattern.finditer(text):
            value = match.group(1)
            if SKIP.match(value) or "$" in value:
                continue
            found.append(value)
    return found


def scan() -> dict[str, Counter]:
    by_area: dict[str, Counter] = {}
    for path in UI.rglob("*.kt"):
        if "/build/" in path.as_posix():
            continue
        found = literals(path)
        if found:
            by_area.setdefault(area_of(path), Counter()).update(found)
    return by_area


def main() -> None:
    command = sys.argv[1] if len(sys.argv) > 1 else "count"
    by_area = scan()
    if command == "count":
        total_uses = total_unique = 0
        for name, _ in AREAS:
            counter = by_area.get(name)
            if not counter:
                continue
            uses, unique = sum(counter.values()), len(counter)
            total_uses, total_unique = total_uses + uses, total_unique + unique
            print(f"{name:10s} {uses:5d} occurrences  {unique:5d} uniques")
        print(f"{'TOTAL':10s} {total_uses:5d} occurrences  {total_unique:5d} uniques")
    elif command == "list":
        area = sys.argv[2]
        for value, count in by_area.get(area, Counter()).most_common():
            print(f"{count:4d}\t{value}")
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
