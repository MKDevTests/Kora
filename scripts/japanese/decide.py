# -*- coding: utf-8 -*-
"""Decides each ja_normal candidate on the shipped pivot, then writes v0.8.

    python decide.py probe    -> writes decide-probe.txt (2 lines per candidate)
    <run the pivot on it, keeping the English at decide-en.txt>
    python decide.py apply    -> reads the English, keeps the winners, writes v0.8

The verdict is mechanical: a candidate is kept only when the katakana original
leaks an untranslatable token into the English hop and the candidate does not.
Neither leaking means the engine already handles the word, and rewriting it is
how a table like this starts making things worse -- that was measured, ホント
rewritten to 本当 turned "I think it's true" into "Vraiment troublé".
"""
import io, json, re, sys
from candidates import CANDIDATES, NEW_ENTRIES, FRAMES

SRC = r"C:\Users\mathi\Downloads\japonais_katakana_mots_v0_7_grammar_lot2.json"
OUT = r"C:\Users\mathi\Downloads\japonais_katakana_mots_v0_8.json"
LEX = (r"C:\Users\mathi\Downloads\Dev\Sipurra-Myversion\Sipurra-myversion"
       r"\komelia-ui\src\commonMain\composeResources\files\lexicon\en.txt")

doc = json.load(open(SRC, encoding="utf-8"))
todo = [x for x in doc["entries"] if "ja_normal" not in x and x["expression"] in CANDIDATES]


def frame(entry, word):
    return FRAMES[entry["category"]].format(word)


if sys.argv[1] == "probe":
    with io.open("decide-probe.txt", "w", encoding="utf-8") as f:
        for x in todo:
            f.write(frame(x, x["expression"]) + "\n")
            f.write(frame(x, CANDIDATES[x["expression"]]) + "\n")
        for expr, normal, _, _, cat in NEW_ENTRIES:
            f.write(FRAMES[cat].format(expr) + "\n")
            f.write(FRAMES[cat].format(normal) + "\n")
    print(len(todo) + len(NEW_ENTRIES), "candidats,", (len(todo) + len(NEW_ENTRIES)) * 2, "lignes")
    sys.exit()

lex = {w.strip().lower() for w in io.open(LEX, encoding="utf-8")}
# The shipped lexicon is built from a frequency list intersected with a word
# list, and single letters did not survive that: "i" is absent, so "I'm"
# stripped of its clitic came out unknown and every sentence in the first
# person counted as a leak. That only ever caused false rejections -- a keep
# requires the candidate side to be clean -- but it cost real wins.
lex |= {"i", "a", "m", "s", "t", "d", "o"}
# Words a French reader would not call untranslated. The test is whether the
# output is readable French, not whether an English word list has heard of it.
lex |= {
    "yakuza", "manga", "otaku", "ninja", "samurai", "geisha", "sensei",
    "kimono", "sumo", "karaoke", "tsunami", "hikikomori", "tsundere",
}
CLIT = re.compile(r"'(s|re|m|ll|ve|d|t)$")


CJK = re.compile(r"[぀-ヿ一-鿿]")

# Candidates whose own English hop is still wrong in a way the leak test cannot
# see. Kept explicit rather than silently dropped, because each is a judgement
# and the next person should be able to disagree with it.
VETO = {
    # 娑婆 comes back "saba", which the word list happens to contain. Same
    # failure as the katakana it was meant to fix.
    "シャバ",
    # 格好悪い came back "it's cool" -- the model dropped the negation, which
    # is worse than the invented name it replaced. ださい, 野暮ったい and
    # 時代遅れ were tried too ("Sig", "Je suis sauvage", "Désalté").
    "ダサイ",
    # げっそり and やつれる both come back "bâclé", which is not the sense.
    "ゲッソリ",
}

# Second candidate, measured after the first one was kept but read badly.
# にわか gave "it's coming"; 素人 gives "Les amateurs sont venus".
OVERRIDES = {"ニワカ": "素人"}


def leaks(english):
    """True when the English hop left a token no English word list knows.

    Contraction-aware: without stripping the clitic, "it's" and "I'm" count as
    unknown and the leak rate comes out half again too high.

    Japanese surviving into the English counts too: ぐにゃぐにゃ came back as
    itself, which is not a translation even though it contains no romaji.
    """
    if CJK.search(english):
        return True
    for w in re.findall(r"[A-Za-z][A-Za-z'-]+", english):
        w = w.lower()
        if w not in lex and CLIT.sub("", w) not in lex:
            return True
    return False


en = [l.rstrip() for l in io.open("decide-en.txt", encoding="utf-8")]
rows = [(x["expression"], CANDIDATES[x["expression"]], x["category"], x) for x in todo]
rows += [(e, n, c, None) for e, n, _, _, c in NEW_ENTRIES]
assert len(en) == 2 * len(rows), (len(en), len(rows))

# The corpus outranks the carrier sentence. These six were seen failing in the
# reader's own log on a real volume -- "Barlow", "Mendo", "L'Atto", "y.", "une
# crinière amateur" -- and the fix was measured on those same balloons. The
# synthetic frame misses them because their romanisation happens to be an
# English word ("Mend.", "At."), which is a limit of the leak test, not
# evidence that the entry works.
FROM_CORPUS = {e for e, _, _, _, _ in NEW_ENTRIES}

kept, rejected, already = [], [], []
for i, (expr, normal, cat, entry) in enumerate(rows):
    before, after = leaks(en[2 * i]) or expr in FROM_CORPUS, leaks(en[2 * i + 1]) or expr in VETO
    if before and not after:
        kept.append((expr, normal, cat, entry, en[2 * i], en[2 * i + 1]))
    elif before and after:
        rejected.append((expr, normal, en[2 * i], en[2 * i + 1]))
    else:
        already.append((expr, en[2 * i]))

print(f"gardes {len(kept)} | rejetes {len(rejected)} | deja bons {len(already)}")
import collections
print("gardes par categorie:", dict(collections.Counter(c for _, _, c, _, _, _ in kept).most_common()))

keep = {expr: normal for expr, normal, _, _, _, _ in kept}
keep.update({k: v for k, v in OVERRIDES.items() if k in keep})
last = max(int(x["id"].split("_")[1]) for x in doc["entries"])
added = 0
for expr, normal, fr, reg, cat in NEW_ENTRIES:
    if expr in keep and not any(x["expression"] == expr for x in doc["entries"]):
        last += 1
        doc["entries"].append({
            "id": f"jakat_{last:04d}", "expression": expr, "ja_normal": normal,
            "translation_fr": fr, "register": reg, "category": cat,
            "priority_rank": last,
        })
        added += 1
for x in doc["entries"]:
    if "ja_normal" not in x and x["expression"] in keep:
        x["ja_normal"] = keep[x["expression"]]

m = doc["metadata"]
m["version"] = "0.8"
m["inherits_version"] = "0.7"
m["entry_count"] = len(doc["entries"])
m["ja_normal_policy"] = (
    "Seul champ consomme par le moteur, toutes categories confondues. Une valeur "
    "n'est ajoutee que si elle a ete mesuree sur le pivot embarque : le katakana "
    "laisse fuir du romaji dans l'anglais intermediaire et la forme normale non. "
    "Une entree sans ja_normal est une entree que le moteur traduit deja."
)
m.pop("grammar_policy", None)
m.pop("integrity_v0_4", None)
m["ja_normal_coverage"] = {
    "entries_with_value": sum(1 for x in doc["entries"] if x.get("ja_normal")),
    "entries_null": sum(1 for x in doc["entries"] if "ja_normal" in x and x["ja_normal"] is None),
    "entries_absent": sum(1 for x in doc["entries"] if "ja_normal" not in x),
}
m["ja_normal_batch"] = {
    "batch": 3,
    "method": "candidat propose puis valide sur le pivot embarque (phrase porteuse identique des deux cotes)",
    "candidates_tested": len(rows),
    "kept": len(kept),
    "rejected_candidate_leaks_too": len(rejected),
    "rejected_engine_already_correct": len(already),
    "new_entries_from_corpus": added,
}
json.dump(doc, io.open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("ecrit", OUT, "|", m["ja_normal_coverage"])

io.open("decide-report.txt", "w", encoding="utf-8").write(
    "GARDES\n" + "\n".join(f"{e} -> {n}  [{a}] => [{b}]" for e, n, _, _, a, b in kept) +
    "\n\nREJETES (le candidat fuit aussi)\n" +
    "\n".join(f"{e} -> {n}  [{a}] => [{b}]" for e, n, a, b in rejected) +
    "\n\nDEJA BONS (moteur correct, on ne touche pas)\n" +
    "\n".join(f"{e}  [{a}]" for e, a in already)
)
