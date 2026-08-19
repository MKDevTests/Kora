#!/usr/bin/env python3
"""Builds the Japanese control corpus -- the base rate that has been missing.

    python scripts/ocr-bench/make_corpus_ja.py _bench-ja --out corpus_ja.tsv

Every Japanese decision so far was measured on whichever bubbles happened to be
in front of us: the Kansai table on a Kansai-speaking volume, the katakana
glossary on three volumes, the credit words on the pages that have credits.
Each of those numbers is true and none of them says what fraction of the
reader's bubbles are actually right, so no fix can be reported as a gain.

Two draws, because one sample cannot answer both questions:

  base   a proportional random draw across all volumes. This is the only pile
         whose percentages mean anything -- weighting it toward hard sentences
         would make the base rate a number about our sampling.
  hard   a deliberate draw from the strata a small model is expected to fail:
         very short bubbles with no context, long ones, dialect markers,
         katakana runs. Diagnosis only, never a rate.

Text comes from sentences.txt, which is what the reader hands the translator --
already merged, repaired and rewritten by the real Kotlin. report.txt is read
only to recover which page a bubble came from, by consuming its kept blocks in
order; the two are cross-checked and the script refuses to write a misaligned
corpus rather than produce one quietly.
"""
from __future__ import annotations

import argparse
import random
import re
import sys
from pathlib import Path

PAGE = re.compile(r"^== (\S+)\s")
BLOCK = re.compile(r"^   block\s+\d+\s")

# Markers of the strata a small model is expected to struggle with. Narrow on
# purpose: these say "look here", they do not claim the bubble is wrong.
KANSAI = re.compile(r"(せや|ちゃう|あかん|やねん|へん|やろ|なんぼ|ほんま|おおきに|わい|やで|やわ)")
KATAKANA_RUN = re.compile(r"[\u30a0-\u30ff]{4,}")


def kept_blocks(report: Path) -> list[tuple[str, str]]:
    """(page, text) for every block that reached the translator, in order."""
    out: list[tuple[str, str]] = []
    page = ""
    for line in report.read_text(encoding="utf-8").splitlines():
        m = PAGE.match(line)
        if m:
            page = m.group(1)
            continue
        if not BLOCK.match(line):
            continue
        # The report marks a dropped block in the MIDDLE of the line, between the
        # height column and the text -- "h=1.0x out  テキスト". Matching it at the
        # end of the line silently keeps every dropped block, which is what the
        # count cross-check below caught.
        tail = line.split("h=", 1)[-1].split(None, 1)
        if len(tail) != 2:
            continue
        rest = tail[1]
        if rest == "out" or rest.startswith("out "):
            continue
        out.append((page, rest.strip()))
    return out


def stratum(text: str) -> str:
    n = len(text)
    if KANSAI.search(text):
        return "dialect"
    if KATAKANA_RUN.search(text):
        return "katakana"
    if n <= 4:
        return "tiny"
    if n >= 30:
        return "long"
    return "plain"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("root", type=Path)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--base", type=int, default=150)
    ap.add_argument("--hard", type=int, default=50)
    ap.add_argument("--seed", type=int, default=20260819)
    args = ap.parse_args()

    rows: list[tuple[str, str, str]] = []  # volume, page, text
    for vol in sorted(p for p in args.root.iterdir() if p.is_dir()):
        sentences = vol / "sentences.txt"
        report = vol / "report.txt"
        if not sentences.exists() or not report.exists():
            continue
        lines = [l for l in sentences.read_text(encoding="utf-8").splitlines() if l.strip()]
        blocks = kept_blocks(report)
        if len(lines) != len(blocks):
            print(
                f"{vol.name}: {len(lines)} sentences but {len(blocks)} kept blocks -- "
                "the report and the sentences disagree, so the page numbers would be "
                "wrong. Refusing to write a corpus that looks aligned and is not.",
                file=sys.stderr,
            )
            return 1
        for (page, _), text in zip(blocks, lines):
            rows.append((vol.name, page, text))

    # Deduplicate on text: the same bubble recurs across a volume ("はい"), and a
    # corpus that counts it forty times measures how often it appears, not how
    # often it is right.
    seen: set[str] = set()
    unique = []
    for vol, page, text in rows:
        if text in seen:
            continue
        seen.add(text)
        unique.append((vol, page, text))

    rng = random.Random(args.seed)
    base = rng.sample(unique, min(args.base, len(unique)))
    chosen = {t for _, _, t in base}

    by_stratum: dict[str, list] = {}
    for row in unique:
        if row[2] in chosen:
            continue
        by_stratum.setdefault(stratum(row[2]), []).append(row)
    hard: list = []
    targets = ["dialect", "katakana", "tiny", "long"]
    per = max(1, args.hard // len(targets))
    for name in targets:
        pool = by_stratum.get(name, [])
        hard += rng.sample(pool, min(per, len(pool)))

    args.out.write_text(
        "pile\tvolume\tpage\tstratum\tja\ten\tfr\tverdict\tcause\n"
        + "".join(
            f"{pile}\t{v}\t{p}\t{stratum(t)}\t{t}\t\t\t\t\n"
            for pile, rowset in (("base", base), ("hard", hard))
            for v, p, t in rowset
        ),
        encoding="utf-8",
    )

    print(f"{len(rows)} bubbles, {len(unique)} unique")
    print(f"base: {len(base)}   hard: {len(hard)}   -> {args.out}")
    counts: dict[str, int] = {}
    for _, _, t in unique:
        counts[stratum(t)] = counts.get(stratum(t), 0) + 1
    print("strata over the whole corpus: " + "  ".join(f"{k}={v}" for k, v in sorted(counts.items())))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
