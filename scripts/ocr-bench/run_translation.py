#!/usr/bin/env python3
"""Translates the bench corpus EN->FR with an offline NMT model, off the tablet.

    python scripts/ocr-bench/run_translation.py corpus.json --out opusmt.json

The engine here is OPUS-MT en-fr run through CTranslate2, which is NOT the engine
the app would ship. It stands in for one, deliberately:

  Bergamot's en-fr is ~31M parameters, distilled from an OPUS-MT-class teacher.
  OPUS-MT en-fr is ~74M and undistilled. Whatever it scores on these bubbles is
  therefore an upper bound on what Bergamot can reach, which is exactly the
  number needed to decide whether integrating a second native runtime is worth
  it. If the upper bound does not beat what ships today, the question is closed
  without writing any JNI.

The output file has the same shape as the corpus with a `translation` field
added, so the tablet-side run of the shipping engine can be diffed against it
line for line.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path


def ensure_model(source: str, target: str) -> None:
    import argostranslate.package

    installed = argostranslate.package.get_installed_packages()
    if any(p.from_code == source and p.to_code == target for p in installed):
        return
    print(f"installing the {source}->{target} package (one time, ~100 MB)")
    argostranslate.package.update_package_index()
    available = argostranslate.package.get_available_packages()
    match = next(
        (p for p in available if p.from_code == source and p.to_code == target), None
    )
    if match is None:
        sys.exit(f"no {source}->{target} package published")
    argostranslate.package.install_from_path(match.download())


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("corpus", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--from", dest="source", default="en")
    parser.add_argument("--to", dest="target", default="fr")
    args = parser.parse_args()

    ensure_model(args.source, args.target)
    import argostranslate.translate

    corpus = json.loads(args.corpus.read_text(encoding="utf-8"))
    started = time.perf_counter()
    count = 0
    for pile in corpus.values():
        for entry in pile:
            entry["translation"] = argostranslate.translate.translate(
                entry["text"], args.source, args.target
            )
            count += 1

    elapsed = time.perf_counter() - started
    args.out.write_text(json.dumps(corpus, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{count} sentences in {elapsed:.1f}s ({elapsed / count * 1000:.0f} ms each)")
    print(f"-> {args.out}")


if __name__ == "__main__":
    main()
