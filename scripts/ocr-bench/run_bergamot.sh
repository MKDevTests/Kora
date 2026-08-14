#!/bin/bash
# Translates the bench corpus with Bergamot, the engine TranslateKit wraps.
#
#     ./scripts/ocr-bench/run_bergamot.sh bench-in.txt bergamot-out.txt
#
# One line in, one line out, same order, so the result lines up with what the
# tablet produced and with the offline reference.
#
# Setup, once (see scripts/ocr-bench/README.md):
#   - build bergamot-translator with -DUSE_RUY=ON -DUSE_RUY_SGEMM=ON. Both:
#     marian adds -lruy to the link line under either, but only builds the
#     vendored ruy under USE_RUY. Without a BLAS and without ruy it compiles
#     fine and then aborts at the first float matmul, after emitting one word,
#     which reads like a truncation bug rather than a missing library.
#   - fetch the model from Mozilla Remote Settings. The copies in the
#     firefox-translations-models repository are git-lfs pointers whose blobs
#     the server no longer has (410).
set -e

IN="${1:?usage: run_bergamot.sh <in.txt> <out.txt>}"
OUT="${2:?usage: run_bergamot.sh <in.txt> <out.txt>}"
ROOT="${BERGAMOT_ROOT:-$HOME/bergamot-bench}"
MODELS="${BERGAMOT_MODELS:-/mnt/c/Users/mathi/bergamot-models/enfr}"
BIN="$ROOT/bergamot-translator/build/app/bergamot"

[[ -x "$BIN" ]] || { echo "no bergamot binary at $BIN"; exit 1; }

CONF="$ROOT/enfr.yml"
cat > "$CONF" <<EOF
models:
  - $MODELS/model.enfr.intgemm.alphas.bin
vocabs:
  - $MODELS/vocab.enfr.spm
  - $MODELS/vocab.enfr.spm
shortlist:
  - $MODELS/lex.50.50.enfr.s2t.bin
  - false
beam-size: 1
normalize: 1.0
word-penalty: 0
max-length-break: 128
mini-batch-words: 1024
workspace: 128
max-length-factor: 2.0
skip-cost: true
cpu-threads: 4
quiet: true
gemm-precision: int8shiftAlphaAll
alignment: soft
EOF

lines=$(wc -l < "$IN")
started=$(date +%s%N)
"$BIN" --model-config-paths "$CONF" --log-level off < "$IN" > "$OUT"
elapsed=$(( ($(date +%s%N) - started) / 1000000 ))

out_lines=$(wc -l < "$OUT")
echo "$lines lines in, $out_lines out, ${elapsed}ms ($(( elapsed / lines ))ms each)"
[[ "$lines" == "$out_lines" ]] || echo "WARNING: line counts differ, the files will not line up"
