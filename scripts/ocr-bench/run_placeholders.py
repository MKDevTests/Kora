#!/usr/bin/env python3
"""Finds a placeholder the translator will not touch.

    python scripts/ocr-bench/run_placeholders.py <sentences.txt>... --out ph.json
    ./scripts/ocr-bench/run_bergamot.sh ph-in.txt ph-out.txt
    python scripts/ocr-bench/run_placeholders.py --score ph.json ph-out.txt

Why this exists. The glossary first tried to protect a name by putting its
capital back before translating, and that was measured on the tablet not to be
enough: the reader sent

    My name is Meryl Strife, and I represent the bernardelli insurance society

and ML Kit returned "Mon nom de Meryl Conflife". The capital travelled, the name
did not. So the term has to leave as something the engine has no translation
for, and come back as itself — which only works if the placeholder survives the
round trip intact, in one piece, exactly once.

Which placeholder that is, is not something to guess. This bench takes real
bubbles that contain a real name, swaps the name for each candidate, and counts
how many come back whole.

The sentences are the ones VolumeReplayTest wrote: already merged, rejoined and
sentence-cased by the shipping Kotlin. Rebuilding any of that here is how a
bench starts lying.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

# Names seen surviving OCR in the eight replayed volumes, lowercased by
# toSentenceCase before they reach the translator. This is what the glossary
# exists to protect, so it is what the bench must carry.
NAMES = [
    "bernardelli", "meryl", "vash", "patricia", "florence", "henrietta",
    "sabine", "triela", "rico", "claes", "tohm", "caul", "shens", "coruscant",
    "geonosis", "raithal", "palpatine", "hasegawa", "ichimiya", "yamagami",
    "lucy", "chihiro", "hakozaki", "tanaka", "karin", "nano", "kusuri",
]
NAME_RE = re.compile(r"(?<![\w'])(" + "|".join(NAMES) + r")(?![\w'])", re.IGNORECASE)

# One per family, so the result says something about the shape rather than about
# one lucky string.
#
#   Word-like    an unknown proper noun, which is what the term actually is.
#                The engine has no entry for it and copies it through — that is
#                the hope, and also how a name is meant to behave grammatically.
#   Bracketed    the usual machine-translation convention. Rare glyphs are the
#                point: nothing in the training data looks like them.
#   Coded        short and alphanumeric, on the theory that models copy digits.
#
# The index matters: a bubble can hold two protected terms, and a placeholder
# that cannot tell them apart is useless.
CANDIDATES = {
    "baseline": "{name}",          # today's behaviour, for the failure rate
    "word_short": "Xqz{i}",
    "word_long": "Zylthar{i}",
    "bracket_math": "⟦{i}⟧",
    "bracket_guillemet": "«{i}»",
    "coded_hash": "#{i}#",
    "coded_at": "@{i}@",
}


def load_sentences(paths: list[Path]) -> list[str]:
    seen: dict[str, None] = {}
    for path in paths:
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            # Only the lines the glossary would ever act on. A sentence with no
            # name in it says nothing about whether a placeholder survives.
            if line and NAME_RE.search(line):
                seen[line] = None
    return list(seen)


def substitute(sentence: str, template: str) -> tuple[str, list[str]]:
    """Replaces every name with an indexed placeholder, in order."""
    placeholders: list[str] = []

    def swap(match: re.Match[str]) -> str:
        token = template.format(i=len(placeholders), name=match.group(1))
        placeholders.append(token)
        return token

    return NAME_RE.sub(swap, sentence), placeholders


def build(paths: list[Path], out: Path, limit: int) -> None:
    sentences = load_sentences(paths)[:limit]
    if not sentences:
        raise SystemExit("no sentence in those files contains a known name")

    plan: list[dict] = []
    lines: list[str] = []
    for name, template in CANDIDATES.items():
        for sentence in sentences:
            swapped, placeholders = substitute(sentence, template)
            plan.append({
                "candidate": name,
                "source": sentence,
                "sent": swapped,
                "placeholders": placeholders,
            })
            # One line each, no blanks: the engine is line-oriented and a blank
            # line would silently shift every later result by one.
            lines.append(swapped.replace("\n", " "))

    out.write_text(json.dumps(plan, ensure_ascii=False, indent=1), encoding="utf-8")
    in_file = out.with_name(out.stem + "-in.txt")
    in_file.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{len(sentences)} sentences x {len(CANDIDATES)} candidates = {len(lines)} lines")
    print(f"  plan: {out}")
    print(f"  feed: {in_file}")


def score(plan_path: Path, translated_path: Path) -> None:
    plan = json.loads(plan_path.read_text(encoding="utf-8"))
    got = translated_path.read_text(encoding="utf-8").splitlines()
    if len(got) != len(plan):
        raise SystemExit(f"{len(plan)} lines sent, {len(got)} came back — they will not line up")

    stats: dict[str, dict[str, int]] = {}
    examples: dict[str, list[str]] = {}
    for entry, result in zip(plan, got):
        row = stats.setdefault(
            entry["candidate"],
            {"lines": 0, "terms": 0, "intact": 0, "loose": 0, "dup": 0},
        )
        row["lines"] += 1
        lower = result.lower()
        for token in entry["placeholders"]:
            row["terms"] += 1
            count = result.count(token)
            # Case-insensitively too, and it matters. ML Kit upper-cases a
            # word-like placeholder (Xqz0 -> XQZ0) and capitalises a name it
            # recognises (vash -> Vash), which the strict count reads as a loss
            # although the term plainly survived. Counting only the strict form
            # made the baseline look far worse than it is; the two columns keep
            # that honest without deciding for the code, whose restore step is
            # case-sensitive today.
            loose = lower.count(token.lower())
            if count == 1:
                row["intact"] += 1
            if loose == 1:
                row["loose"] += 1
            elif loose > 1:
                # Worse than losing it: restoring would then put the term in a
                # place the sentence never had it.
                row["dup"] += 1
            else:
                examples.setdefault(entry["candidate"], []).append(
                    f"{entry['sent']}\n      -> {result}"
                )

    print(f"{'candidate':<18} {'exact':>8} {'any case':>9} {'lost':>7} {'dup':>5}")
    for name in CANDIDATES:
        row = stats.get(name)
        if not row:
            continue
        lost = row["terms"] - row["loose"] - row["dup"]
        exact = 100.0 * row["intact"] / row["terms"] if row["terms"] else 0.0
        loose = 100.0 * row["loose"] / row["terms"] if row["terms"] else 0.0
        print(f"{name:<18} {exact:7.1f}% {loose:8.1f}% {lost:7d} {row['dup']:5d}")
    print()
    for name, misses in examples.items():
        print(f"  {name}, {len(misses)} miss(es), first two:")
        for miss in misses[:2]:
            print(f"      {miss}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("inputs", nargs="+", type=Path)
    parser.add_argument("--out", type=Path, default=Path("placeholders.json"))
    parser.add_argument("--limit", type=int, default=200)
    parser.add_argument("--score", action="store_true",
                        help="second form: <plan.json> <translated.txt>")
    args = parser.parse_args()

    if args.score:
        if len(args.inputs) != 2:
            raise SystemExit("--score takes the plan json and the translated file")
        score(args.inputs[0], args.inputs[1])
    else:
        build(args.inputs, args.out, args.limit)


if __name__ == "__main__":
    main()
