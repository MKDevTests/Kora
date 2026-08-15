#!/bin/bash
# Translates Japanese to French by pivoting through English, on the engine the
# app already ships.
#
#     ./scripts/ocr-bench/run_pivot_ja.sh ja.txt fr.txt
#
# One line in, one line out, same order. The English is left in
# /tmp/pivot-en.txt so the two hops can be judged separately -- which matters,
# because the pivot's known weakness is an ambiguity resolved wrongly in English
# that the French model then cannot recover.
#
# Why a pivot at all, when both design documents recommend a direct ja-fr model:
#
#   Mozilla publishes a Bergamot ja-en pack (v2.1, model 41.9MB + vocab 1.4MB +
#   shortlist 8.9MB) through the same Remote Settings collection the app already
#   downloads en-fr from. So Japanese costs one more pack and no new runtime, no
#   model conversion and nothing to host. Our own BergamotModels.kt says
#   "Japanese is not on the list at all" -- that was checked against the live
#   endpoint and is wrong.
#
#   The direct route was measured against this one on the same sentences, using
#   Helsinki-NLP/opus-mt-ja-fr, and did not win. See the notes in
#   scripts/ocr-bench/README.md.
#
# Setup: same bergamot build as run_bergamot.sh, plus the ja-en pack in
# $BERGAMOT_MODELS/../jaen. Download it the way the app does: the records at
# firefox.settings.services.mozilla.com, newest version that has all three file
# types, attachments from firefox-settings-attachments.cdn.mozilla.net.
set -e

IN="${1:?usage: run_pivot_ja.sh <ja.txt> <fr.txt>}"
OUT="${2:?usage: run_pivot_ja.sh <ja.txt> <fr.txt>}"
ROOT="${BERGAMOT_ROOT:-$HOME/bergamot-bench}"
MODELS="${BERGAMOT_MODELS_ROOT:-/mnt/c/Users/mathi/bergamot-models}"
BIN="$ROOT/bergamot-translator/build/app/bergamot"

[[ -x "$BIN" ]] || { echo "no bergamot binary at $BIN"; exit 1; }

# Both hops take the same settings the app runs with: beam 1 and int8shift. A
# bench that quietly gave itself beam 4 would be measuring a different engine
# from the one the reader has.
hop() { # <pair> <in> <out>
    local pair="$1" dir="$MODELS/$1" conf="$ROOT/$1.yml"
    [[ -d "$dir" ]] || { echo "missing model pack: $dir"; exit 1; }
    cat > "$conf" <<EOF
models:
  - $dir/model.$pair.intgemm.alphas.bin
vocabs:
  - $dir/vocab.$pair.spm
  - $dir/vocab.$pair.spm
shortlist:
  - $dir/lex.50.50.$pair.s2t.bin
  - false
beam-size: 1
normalize: 1.0
word-penalty: 0
max-length-break: 128
mini-batch-words: 1024
workspace: 128
max-length-factor: 2.0
skip-cost: true
gemm-precision: int8shiftAlphaAll
alignment: soft
quiet: true
quiet-translation: true
EOF
    "$BIN" --model-config-paths "$conf" --cpu-threads 4 --log-level critical < "$2" > "$3"
}

hop jaen "$IN" /tmp/pivot-en.txt
hop enfr /tmp/pivot-en.txt "$OUT"
echo "english kept at /tmp/pivot-en.txt"
