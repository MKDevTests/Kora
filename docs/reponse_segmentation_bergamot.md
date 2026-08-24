# Segmentation des bulles longues : réponse mesurée

Réponse au document *« Gestion des bulles trop longues avec Bergamot dans un
reader Android — Stratégie de segmentation intelligente avant traduction »*
(19 août 2026).

**Verdict : ne pas implémenter.** Le document est techniquement juste, mais son
diagnostic de départ ne correspond pas à ce que contiennent nos pages. Les trois
mesures ci-dessous ont été faites sur le corpus du banc — 1 808 bulles, 8 tomes,
mangas et comics — avec le vrai binaire Bergamot et le vrai vocabulaire
SentencePiece du modèle installé.

---

## 1. Ce que le document propose

Insérer un `LongBubbleSegmenter` entre l'assemblage des bulles et le traducteur :

```
BubbleAssembler → ParagraphSplitter → SentenceSplitter (ICU) → TokenCounter
                → ClauseSplitter si > softLimit → Bergamot → Reassembler
```

Avec `softLimit = 96` tokens, `hardLimit = 120`, pour empêcher le texte
d'atteindre le `max-length-break: 128` de Bergamot et son découpage mécanique.

L'argument central (§4, §5, §53) : une bulle longue dépasse la limite, Bergamot
coupe au token N sans égard pour la grammaire, la traduction se dégrade.

---

## 2. Mesure — le découpage en phrases est déjà fait par Bergamot

Le niveau 1 du document (ICU `BreakIterator`, §11-13) découpe la bulle en
phrases avant de les envoyer au moteur.

**Protocole.** 40 bulles du corpus, toutes ≥ 110 caractères et contenant au
moins deux phrases. Traduites deux fois par `run_bergamot.sh`, sur le même
binaire et le même modèle :

- **A** — la bulle entière sur une ligne ;
- **B** — chaque phrase sur sa propre ligne, puis résultats recollés.

**Résultat : 40 sur 40 strictement identiques.** Pas une différence de
caractère.

Bergamot applique déjà son propre découpage en phrases (le document l'évoque
lui-même en §33-34 avec `ssplit-mode`). Écrire un `SentenceSplitter` côté Kotlin
produirait exactement le texte que le moteur produit déjà.

---

## 3. Mesure — le seuil de 128 tokens n'est presque jamais atteint

Compté avec `vocab.enfr.spm`, le vocabulaire SentencePiece du modèle que Kora
utilise — pas une estimation en caractères, que le document déconseille à juste
titre (§19).

| unité | médiane | p95 | p99 | max |
|---|---|---|---|---|
| tokens par bulle | **10** | 31 | 48 | 488 |
| tokens par phrase | **8** | 24 | 38 | 261 |

Et au-dessus des seuils proposés, sur 2 292 phrases :

| seuil | phrases | part |
|---|---|---|
| > 96 (`softLimit` proposé) | 5 | 0,22 % |
| > 120 (`hardLimit` proposé) | 4 | 0,17 % |
| > 128 (`max-length-break`) | **1** | **0,04 %** |

Le document écrit en §53 qu'il ne faut pas *« laisser Bergamot effectuer
régulièrement le hard split natif »*. Chez nous, c'est une phrase sur deux mille
trois cents.

---

## 4. Mesure — les bulles longues ne sont pas du dialogue

Les six seules bulles au-dessus de 96 tokens :

| tokens | contenu |
|---|---|
| 488 | pavé de copyright Skybound |
| 136 | feuille de statistiques de jeu (`Name: Yuto Race: Halfling…`) |
| 133 | crédits Image Comics |
| 127 | crédits Dynamite |
| 125 | feuille de statistiques |
| 100 | note de traduction en fin de chapitre |

**Aucune bulle de dialogue.** Le segmenter travaillerait exclusivement sur des
pages de crédits et des tableaux de stats.

---

## 5. Ce que le pipeline fait déjà

Plusieurs briques que le document propose de créer existent, à d'autres fins.

| proposition | état dans le code |
|---|---|
| §26-28 `ProtectedSpan` pour les termes de glossaire | **fait** — `glossary.protect()`, [ReaderState.kt:1635](../komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ReaderState.kt#L1635). Les termes partent en marqueurs et reviennent intacts, parce que « Meryl Strife » revenait « Meryl Conflife » |
| §32 regroupement des bulles d'une même phrase | **fait, dans l'autre sens** — [BubbleAssembler.group()](../komelia-domain/core/src/commonMain/kotlin/snd/komelia/image/BubbleAssembler.kt#L28) *réunit* jusqu'à 3 bulles quand une phrase est lettrée à cheval, au lieu de les découper |
| §22 `TranslationReassembler` | **fait** — la traduction du groupe est redistribuée aux bulles d'origine, [ReaderState.kt:1672](../komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ReaderState.kt#L1672) |
| §27 réutiliser une détection d'expressions | **fait** — `PhraseBook.lookup()` répond avant le moteur, [ReaderState.kt:1648](../komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ReaderState.kt#L1648) |

Le problème réel du lettrage de comics est **l'inverse** de celui que le document
décrit : une phrase est répartie sur plusieurs bulles trop petites, pas
concentrée dans une bulle trop grande. C'est pour ça que `BubbleAssembler`
regroupe.

---

## 6. Points du document, un par un

| § | affirmation | verdict |
|---|---|---|
| 2 | `max-length-break` se compte en tokens SentencePiece, pas en caractères | **exact**, et c'est pourquoi la mesure ci-dessus utilise le vrai vocabulaire |
| 4-5 | un découpage mécanique au token N dégrade la traduction | **exact en principe**, mais il concerne 0,04 % des phrases |
| 11-13 | découper en phrases avec ICU | **sans effet** — 40/40 identiques, le moteur le fait déjà |
| 14-17 | `ClauseSplitter` pour une phrase unique trop longue | **sans objet** — une seule phrase du corpus dépasse 128 tokens |
| 19 | ne pas estimer les tokens en caractères | **exact**, respecté ici |
| 25 | pas de fenêtres avec recouvrement | **exact**, et rien dans le code n'en fait |
| 33 | tester `ssplit-mode: paragraph` | notre configuration n'en spécifie aucun. Sans effet observable puisque le découpage interne donne déjà le même résultat que le découpage manuel |
| 42 | corriger la segmentation avant de toucher au beam | l'ordre est sage, mais **les deux ont été mesurés** : beam 4 change les 40 bulles, n'améliore pas, et coûte 2,2× le temps |
| 44 | `segmentationVersion` dans la clé de cache | bonne pratique, sans objet sans segmenter |
| 51 | cas de test (`Mr. Wayne`, `3.14`, citations) | bons cas — pour un splitter qu'on n'écrira pas |
| 56 | « coût très faible par rapport à changer de modèle » | vrai dans l'absolu ; ici le coût est faible **et** le gain est nul |

---

## 7. Ce que ça corrige dans nos propres notes

L'explication écrite plus tôt dans la session — « le taux de faute suit la
longueur des phrases » — est trop rapide et doit être corrigée. Avec une bulle
médiane à **10 tokens**, les fautes comptées sont sur des bulles courtes.
Altered Carbon a des bulles plus longues *et* plus de fautes, mais l'écart est
de 30 tokens contre 15, pas de 128. Ce qui casse la traduction, c'est la
**capacité du modèle sur des tournures difficiles**, pas une troncature
mécanique.

Le plafond reste celui que Mozilla publie pour ce modèle : **49,6 BLEU**
(`base-memory/enfr`, 31,6 Mo, celui que Kora utilise — hash vérifié identique).

---

## 8. Recommandation

Ne pas implémenter le `LongBubbleSegmenter`. Ce serait une dizaine de classes,
une extension JNI pour compter les tokens et un jeu de métriques, pour agir sur
0,04 % des phrases, toutes situées dans des pages de crédits.

Ce qui donne des résultats mesurés, sur le même corpus :

| levier | gain constaté |
|---|---|
| carnet d'expressions (bulles entières) | 22 bulles sur 24 pages |
| réparation OCR ciblée (`u` lu `l`, zéro lu `o`) | 14 bulles, dont deux contresens de dates |
| découpeur de mots collés | 5 bulles |

Ces trois-là traitent l'entrée du moteur ou court-circuitent sa sortie sur des
tournures figées. Ils ne se heurtent pas au plafond du modèle.

---

## 9. Reproduire les mesures

Distribution des tokens, avec le vocabulaire du modèle :

```bash
python - <<'PY'
import re, pathlib, sentencepiece as spm
sp = spm.SentencePieceProcessor(model_file="C:/Users/mathi/bergamot-models/enfr/vocab.enfr.spm")
bubbles = [l.split("\t")[0].strip()
           for f in pathlib.Path("_bench-en").glob("*/baseline.tsv")
           for l in f.read_text(encoding="utf-8").splitlines() if l.strip()]
sent = [len(sp.encode(s)) for b in bubbles
        for s in re.split(r"(?<=[.!?…]) +(?=[A-Z\"'])", b) if s.strip()]
print(len(sent), "phrases,", sum(1 for x in sent if x > 128), "au-dessus de 128 tokens")
PY
```

Comparaison bulle entière / phrase par phrase : écrire les deux fichiers puis

```bash
./scripts/ocr-bench/run_bergamot.sh entier.txt entier.fr.txt
```

Le banc complet, qui rejoue le vrai code Kotlin puis le vrai moteur :

```bash
python scripts/ocr-bench/bench_en.py Altered-Carbon-One-Life-One-Death-2022
```
