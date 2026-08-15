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
import re
import time
from pathlib import Path

MODEL = "Helsinki-NLP/opus-mt-en-fr"

# Ends a sentence and is followed by the start of another. Kept deliberately
# narrow so 'Mr. Smith' and '...' do not get cut.
SENTENCE_END = re.compile(r"(?<=[.!?])\s+(?=[\"'“]?[A-Z])")


def split_sentences(text: str) -> list[str]:
    """
    What a real integration does before handing text to the model, and what ML
    Kit does inside itself.

    Without it a Marian model given three sentences at once answers with one:
    'Damn it! It's a breach of contract! I'm gonna sue!' comes back as 'C'est
    une rupture de contrat !'. Comparing an engine that splits against one that
    does not measures the splitting, not the engines.
    """
    parts = [p.strip() for p in SENTENCE_END.split(text)]
    return [p for p in parts if p] or [text]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("corpus", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--model", default=MODEL)
    parser.add_argument("--batch", type=int, default=16)
    parser.add_argument("--whole-blocks", action="store_true",
                        help="feed each block in one piece instead of sentence by sentence")
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

    def translate(texts: list[str]) -> list[str]:
        out: list[str] = []
        for start in range(0, len(texts), args.batch):
            chunk = texts[start : start + args.batch]
            batch = tokenizer(chunk, return_tensors="pt", padding=True)
            generated = model.generate(**batch, max_new_tokens=128, num_beams=4)
            out += [tokenizer.decode(t, skip_special_tokens=True) for t in generated]
        return out

    for name, pile in corpus.items():
        # Every sentence of every block goes through in one flat list, so the
        # batching still works and the pieces are put back per block afterwards.
        pieces: list[str] = []
        spans: list[tuple[int, int]] = []
        for entry in pile:
            parts = [entry["text"]] if args.whole_blocks else split_sentences(entry["text"])
            spans.append((len(pieces), len(pieces) + len(parts)))
            pieces += parts

        translated = translate(pieces)
        for entry, (start, end) in zip(pile, spans):
            entry["translation"] = " ".join(translated[start:end])
        count += len(pile)
        print(f"  {name}: {len(pile)} blocks, {len(pieces)} sentences")

    elapsed = time.perf_counter() - started
    args.out.write_text(json.dumps(corpus, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{count} sentences in {elapsed:.1f}s ({elapsed / count * 1000:.0f} ms each)")
    print(f"-> {args.out}")


if __name__ == "__main__":
    main()
