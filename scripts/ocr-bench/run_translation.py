#!/usr/bin/env python3
"""Translates the bench corpus EN->FR with an offline NMT model, off the tablet.

    python scripts/ocr-bench/run_translation.py corpus.json --out opusmt.json

The engine here is OPUS-MT en-fr, which is NOT the engine the app would ship.
It stands in for one, deliberately:

  Bergamot's en-fr is ~31M parameters, distilled from an OPUS-MT-class teacher.
  OPUS-MT en-fr is ~74M and undistilled. Whatever it produces on these bubbles
  is therefore an upper bound on what Bergamot can reach, which is exactly the
  number needed to decide whether integrating a second native runtime is worth
  it. If the upper bound does not beat what ships today, the question is closed
  without writing any JNI.

Runs entirely from the local HuggingFace cache: no network, so nothing here
depends on a service staying up or on the machine's TLS interception.

The output has the same shape as the corpus with a `translation` field added, so
the tablet-side run of the shipping engine can be diffed against it line for
line.
"""
from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

MODEL = "Helsinki-NLP/opus-mt-en-fr"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("corpus", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--model", default=MODEL)
    parser.add_argument("--batch", type=int, default=16)
    parser.add_argument(
        "--allow-download",
        action="store_true",
        help="fetch the model if it is not already cached (off by default)",
    )
    args = parser.parse_args()

    from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

    local_only = not args.allow_download
    tokenizer = AutoTokenizer.from_pretrained(args.model, local_files_only=local_only)
    model = AutoModelForSeq2SeqLM.from_pretrained(args.model, local_files_only=local_only)
    model.eval()

    corpus = json.loads(args.corpus.read_text(encoding="utf-8"))
    started = time.perf_counter()
    count = 0
    for name, pile in corpus.items():
        for start in range(0, len(pile), args.batch):
            chunk = pile[start : start + args.batch]
            batch = tokenizer([e["text"] for e in chunk], return_tensors="pt", padding=True)
            generated = model.generate(**batch, max_new_tokens=128, num_beams=4)
            for entry, tokens in zip(chunk, generated):
                entry["translation"] = tokenizer.decode(tokens, skip_special_tokens=True)
            count += len(chunk)
        print(f"  {name}: {len(pile)} done")

    elapsed = time.perf_counter() - started
    args.out.write_text(json.dumps(corpus, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{count} sentences in {elapsed:.1f}s ({elapsed / count * 1000:.0f} ms each)")
    print(f"-> {args.out}")


if __name__ == "__main__":
    main()
