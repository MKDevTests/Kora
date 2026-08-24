# Analyse révisée Bergamot : réponse mesurée

Réponse au document *« Révision de l'analyse Bergamot après benchmark réel —
Reader Android sur tablette »* (19 août 2026), qui répond lui-même à
[reponse_segmentation_bergamot.md](reponse_segmentation_bergamot.md).

**Verdict d'ensemble.** Le document a raison sur son point central et sur son
inversion de diagnostic : il retire le `LongBubbleSegmenter`, garde
`beam-size: 1`, refuse le gros modèle. Mais sa nouvelle feuille de route (§36,
§57) place en tête trois briques qui, mesurées sur le corpus, corrigent **zéro
bulle** — et elle réintroduit en P2 exactement le piège qu'elle vient d'éviter.

Ce qui a réellement de la valeur est en fin de document : §40-43.

Toutes les mesures ci-dessous portent sur le corpus du banc (1 808 bulles,
8 tomes, mangas et comics) ou sur les 1 094 bulles distinctes relevées en
lecture réelle sur la tablette, dédoublonnées sur `(sent, src)`.

---

## 1. Tableau de décision

| § | proposition | mesure | verdict |
|---|---|---|---|
| 1 | retirer le `LongBubbleSegmenter` | 0,04 % des phrases | **d'accord** |
| 2 | le découpage en phrases est déjà fait par le moteur | 40/40 sorties identiques | **d'accord** |
| 3 | ne pas toucher `max-length-break` | médiane 10 tokens/bulle | **d'accord** |
| 4 | diagnostic inversé (OCR / expression / fragment / plafond du modèle) | confirmé par le comptage à la main | **d'accord** |
| 7 | PhraseBook v2 : variantes de contraction et morphologiques | **0 bulle rattrapée sur 1 808** | à ne pas faire |
| 8 | `precision > recall` pour le carnet | — | déjà la doctrine du code |
| 12 | `OcrTextNormalizer` | 0 apostrophe détachée réelle, 3 défauts bénins | sans objet |
| 13 | réparations caractère par caractère, contextuelles | 14 bulles gagnées | **fait**, et c'est bien contextuel |
| 14-15 | `WordJoinRepair`, exemple `dontknow → don't know` | 1 occurrence, faux positif | la brique existe, l'exemple n'existe pas |
| 16 | le vrai problème est l'inverse : une phrase sur plusieurs bulles | 4,8 % des bulles groupées | **exact** |
| 17-19 | `DynamicBubbleAssembler`, plafond 3 → 4 ou 5 | **1 seul groupe de 3** dans tout le corpus | toucherait un groupe |
| 20 | ne pas inventer le locuteur | — | d'accord, rien dans le code ne le fait |
| 21-25 | `OversizePolicy` + `CreditsHandler` / `StatsHandler` / `NotesHandler` | 6 bulles, 0,33 % | **contradiction, voir §3 ci-dessous** |
| 27-28 | garder `beam-size: 1`, ne pas sacrifier la tablette | beam 4 : 2,2× le temps, pas mieux | **d'accord** |
| 29-32 | pas de gros modèle, rester en classe mobile | nous sommes déjà au plafond publié | **d'accord** |
| 33 | fine-tuning domaine dialogue | vise la cause n°1 mesurée | **la seule vraie piste**, mais chantier lourd |
| 36-37 | plan de travail P0-P3, ce qu'on retire | — | l'ordre est à revoir |
| 40-41 | corpus `translation_errors.tsv` + catégories | — | **à faire** |
| 42-43 | KPI « net corrected bubbles » | — | **déjà le critère appliqué** |
| 44-46 | instrumentation carnet / OCR / assembleur | — | utile, coût faible |
| 53-55 | budget produit et critère final | — | d'accord |

---

## 2. Les trois briques de tête corrigent zéro bulle

### §7 — PhraseBook v2, variantes de contraction

Le document propose de regrouper `you've got to be kidding me`,
`you gotta be kidding me`, `you have got to be kidding me` dans un même groupe
sémantique.

**Protocole.** Table complète (2 105 clés : le fichier livré plus les entrées
curées du Kotlin), 20 paires de contraction usuelles dans les deux sens
(`it's ↔ it is`, `gonna ↔ going to`, `let's ↔ let us`…). Pour chaque bulle du
corpus : si la clé normalisée rate, on essaie toutes les variantes.

| | bulles | part |
|---|---|---|
| déjà captées par le carnet | 48 | 2,7 % |
| **captées en plus par les variantes** | **0** | **0,00 %** |

La raison est dans le code : `normalise()`
([PhraseBook.kt:85](../komelia-domain/core/src/commonMain/kotlin/snd/komelia/image/PhraseBook.kt#L85))
écrase déjà la casse, toute la ponctuation, et l'apostrophe typographique. Le
tier « variantes » ajouterait de la surface d'erreur — deux clés qui se
rejoignent sur une variante et se contredisent — pour un gain nul.

Ce qui marche sur le carnet, mesuré : **ajouter des entrées vues échouer sur une
page réelle**. 24 entrées ajoutées cette semaine, 22 bulles corrigées. C'est du
travail de relevé, pas d'architecture.

### §12 — OcrTextNormalizer

Le document liste `I ' m → I'm`, les apostrophes typographiques, les guillemets,
la ponctuation répétée aberrante.

| défaut visé | détections | réellement fautifs |
|---|---|---|
| apostrophe détachée (`I ' m`) | 28 (1,55 %) | **0** |
| apostrophes typographiques | 0 | 0 |
| ponctuation répétée aberrante | 1 (0,06 %) | 1 |
| espaces multiples | 2 (0,11 %) | 2 |

Les 28 « apostrophes détachées » sont, après vérification une par une,
`He's so freakin' cute!`, `Adventurers' guild`, `I don't like not knowin' What
I just lost to.` — des élisions d'argot et des possessifs pluriels parfaitement
corrects. **Le normaliseur les casserait.** PP-OCRv6 ne détache pas les
apostrophes chez nous : le défaut visé par le §12 n'existe pas dans le corpus,
et le détecteur qui le cherche produit 28 faux positifs pour 3 vrais défauts
bénins.

### §14-15 — WordJoinRepair et son exemple

La brique existe et elle est gardée : le découpeur de mots collés gagne 5 bulles.
Mais l'exemple canonique du document (`dontknow → don't know`) suppose une coupe
qui exige de réinsérer une apostrophe.

**Mesure.** 397 tokens de 6 lettres ou plus hors lexique dans le corpus. Ceux
dont la coupe exigerait une apostrophe : **1**, et c'est `IMAGECOMICS` — un logo
de crédits, faux positif du test.

Le découpeur actuel refuse les apostrophes par conception. C'est un bon refus :
il n'a rien à réparer de ce côté.

---

## 3. La contradiction : OversizePolicy

Le §1 retire le `LongBubbleSegmenter` avec cet argument, qui est le bon :

> coût d'implémentation > bénéfice réel — 0,04 % des phrases, et ce sont des
> copyrights, des crédits, des feuilles de stats.

Les §21-25 réintroduisent, pour **ces six bulles exactement**, un `OversizePolicy`
routant vers un `CreditsHandler`, un `StatsHandler` et un `NotesHandler`. Le §36
les classe en **P2**, devant la classification des erreurs réelles.

Les six bulles en question, seules au-dessus de 96 tokens sur 1 808 :

| tokens | contenu |
|---|---|
| 488 | pavé de copyright Skybound |
| 136 | feuille de statistiques (`Name: Yuto Race: Halfling…`) |
| 133 | crédits Image Comics |
| 127 | crédits Dynamite |
| 125 | feuille de statistiques |
| 100 | note de traduction de fin de chapitre |

Soit **0,33 % du corpus**, huit fois le seuil qui vient de faire rejeter le
segmenter — pour trois classes au lieu de dix, sur des pages que personne ne
lit. Un lecteur ne demande pas la traduction d'un copyright.

Si on veut vraiment traiter ce cas, la version honnête tient en une ligne :
au-dessus d'un seuil de tokens **et** avec une forte densité de `:` ou de
chiffres, **ne pas traduire du tout**. Pas de handler, pas de route spéciale.

### §17-19 — DynamicBubbleAssembler

Le document a raison de dire (§16) que le vrai problème de segmentation est
l'inverse : une phrase répartie sur plusieurs bulles. Mais il propose de tester
un plafond à 4 ou 5.

**Mesure** sur les 1 094 bulles distinctes des quatre lectures tablette :

| | nombre | part |
|---|---|---|
| bulles regroupées | 53 | 4,8 % |
| groupes de 2 bulles | 25 | — |
| **groupes de 3 bulles (plafond atteint)** | **1** | — |

Le plafond `MAX_BUBBLES_PER_UTTERANCE = 3` est atteint **une seule fois**. Le
passer à 4 ou 5 modifierait au mieux un groupe, et ouvrirait le risque §19
(fusion de deux locuteurs) sur les 25 autres.

Ce qui vaudrait la peine, c'est l'inverse du réglage : mesurer les phrases que
l'assembleur **ne groupe pas** alors qu'il devrait. Ça, on ne l'a pas mesuré, et
le §18 donne les bons indices pour le faire (ponctuation terminale absente,
minuscule en tête de la suivante, connecteur grammatical).

---

## 4. Où sont réellement les fautes

Comptage à la main, quatre séries, bulles réellement affichées sur la tablette :

| série | taux de fautes |
|---|---|
| Arpeggio of Blue Steel | 21 % |
| Ramen Aka Neko | 24 % |
| The 100 Girlfriends (214 bulles, 73 fautes) | 34 % |
| Altered Carbon | 40 % |
| **total : 740 bulles, 242 fautes** | **33 %** |

Taxonomie des 53 fautes restantes de 100 Girlfriends, après les correctifs de
cette semaine :

| cause | nombre | ce que le document en dit |
|---|---|---|
| C — construction Bergamot (le modèle rate la tournure) | ~19 | §33, fine-tuning domaine |
| D — source cassée par l'OCR | ~16 | §13, déjà en place |
| A — expression figée non couverte | ~15 | §7, mais par relevé, pas par variantes |
| E — casse / registre | ~8 | non traité |
| B — fragments non groupés | ~3 | §18, à mesurer dans l'autre sens |

**La cause dominante est le modèle lui-même**, pas l'entrée. Le §48 du document
(« traiter les erreurs avant que le petit NMT ait à les interpréter ») est juste
mais ne couvre que les 16 fautes D. Les 19 fautes C ne se corrigent ni par
normalisation ni par regroupement.

Et le plafond est atteint : le modèle installé est
`model.enfr.intgemm.alphas.bin`, 31 561 787 octets, sha256 `6322e296…` — c'est
`base-memory/enfr` de Mozilla, **le seul modèle en-fr publié au registre**
(BLEU 48,8533, chrF 70,8001, COMET22 0,8653, spBLEU 53,8402, sur flores200-plus,
statut *Release*). Il n'existe **pas** de variante `tiny` pour cette paire.

> Correction du 19/08 : une version antérieure de cette note citait « BLEU 49,6,
> COMET 0,870 » et un « tiny/enfr de 17,1 Mo à 48,5 BLEU ». Ces chiffres ne
> figurent pas au registre Mozilla et ne doivent pas être retenus. Vérifié à la
> source : `moz-fx-translations-data.../db/models.json`. La décision produit ne
> change pas — elle se renforce : il n'y a aucun autre modèle en-fr à essayer.

Le §33 est donc la seule proposition qui attaque la cause n°1. C'est aussi la
plus coûteuse : corpus parallèle EN/FR de dialogues, entraînement Marian,
quantification INT8, validation. Plusieurs jours, sans garantie.

---

## 5. Un angle mort du banc, à corriger avant tout `translation_errors.tsv`

Le banc `bench_en.py` rejoue le vrai code Kotlin (fusion, découpeur, casse) puis
appelle le vrai Bergamot. **Mais il ne voit pas le carnet d'expressions.**
`PhraseBook.lookup()` est appelé côté application, dans
[ReaderState.kt:1648](../komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ReaderState.kt#L1648),
en aval de ce que le banc reproduit.

Conséquence directe : un « 0 changed » du banc après un ajout au carnet ne veut
rien dire. Ça a failli faire conclure qu'un lot d'entrées était inutile alors
qu'il corrigeait 22 bulles sur la tablette. Un test unitaire
(`PhraseBookMangaTest`) tient lieu de garde-fou depuis, mais le banc devrait
appeler le carnet.

C'est le prérequis du §40 : un `translation_errors.tsv` alimenté par un banc qui
saute une étape du pipeline mesurerait la mauvaise chose.

---

## 6. Ce que je retiens du document

**§40-41, corpus d'erreurs.** C'est exactement le comptage fait à la main
ci-dessus, mais versionné et rejouable. Les colonnes proposées sont les bonnes,
avec une réserve : `expected_fr` demande une traduction de référence écrite à la
main pour chaque ligne. Sur 242 fautes c'est faisable ; ça ne l'est pas sur
1 808 bulles. Le corpus doit donc rester un **corpus de fautes**, pas un corpus
de référence complet.

Les catégories §41 recoupent la taxonomie A-E ci-dessus. Je garderais les onze
telles quelles.

**§42-43, `net corrected bubbles` comme KPI.** C'est déjà le critère appliqué
cette semaine, et il a servi à rejeter :

| piste | mesure | décision |
|---|---|---|
| beam-size 4 | change 40/40, pas meilleur, 2,2× le temps | rejetée |
| réécriture tu/vous | 98 bulles réécrites, casse accords et pluriel | rejetée |
| mots-préfixes dans les mots-fonctions | `outmatched → out matched` | rejetée |
| découpage en phrases côté Kotlin | 40/40 identiques | rejetée |
| modèle plus gros | déjà le plus gros publié | sans objet |

**§8, `precision > recall`.** Déjà écrit dans l'en-tête de `PhraseBook.kt`, avec
le même exemple que le document (`give me a break`, dont la traduction dépend du
contexte). Convergence rassurante.

**§44-46, instrumentation.** Faible coût, vraie valeur. Le log actuel donne
`src=` et la sortie ; il ne dit pas **quelle règle** a agi. Ajouter un `ruleId`
sur les réparations OCR et un `reason` sur les groupes rendrait le comptage à la
main beaucoup plus rapide.

---

## 7. Ordre de travail proposé, en remplacement du §57

1. **Faire voir le carnet au banc** (prérequis de tout le reste).
2. **Corpus de fautes** `translation_errors.tsv`, alimenté par les 242 fautes
   déjà relevées, catégories §41.
3. **Instrumentation** `ruleId` / `reason` (§45-46).
4. **Relevé d'expressions** : continuer à ajouter au carnet ce qu'on voit
   échouer — c'est le levier le mieux mesuré (22 bulles / 24 pages).
5. **Mesurer les groupements manqués** (§18 dans l'autre sens), pas le plafond.

Écartés : `OcrTextNormalizer` (§12), variantes de contraction (§7), plafond
dynamique (§17-19), `OversizePolicy` avec handlers (§21-25).

Le fine-tuning domaine (§33) reste la seule piste capable de bouger les 19
fautes de construction, et ne devrait être ouvert qu'une fois le corpus de
fautes constitué — il en sera le jeu de validation.

---

## 8. Reproduire les mesures

Variantes de contraction du carnet, et défauts visés par l'`OcrTextNormalizer` :

```bash
cd /mnt/c/Users/mathi/Downloads/Dev/Sipurra-Myversion/Sipurra-myversion
python3 scripts/ocr-bench/audit_document_claims.py
```

Distribution des tokens avec le vrai vocabulaire SentencePiece, et le banc
complet : voir
[reponse_segmentation_bergamot.md §9](reponse_segmentation_bergamot.md).
