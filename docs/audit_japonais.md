# Traduction du japonais : dossier d'audit

État au 19 août 2026, Kora 1.7.4.

**Convention de lecture.** Ce dossier distingue deux sources :

- **[code]** — vérifié dans le dépôt le 19/08/2026, avec le fichier et la ligne.
- **[note]** — mesure d'une session antérieure, consignée dans les fiches de
  travail. Reproductible mais non revérifiée aujourd'hui.

---

## 0. Le point qui change tout

**Le japonais est actif dans la version livrée.**

Une note de travail affirmait qu'il avait été retiré le 18/08/2026 par le commit
`b8d92156`. Vérification faite, ce commit existe mais vit sur la branche
`chore/remove-japanese`, **jamais fusionnée dans `main`** [code] :

```
$ git merge-base --is-ancestor b8d92156 main   ->  NON
$ git log --oneline main..chore/remove-japanese
b8d92156 chore(translation): drop Japanese as a source language
```

Et `JAPANESE` figure toujours dans l'énumération des langues source
([TranslationSettings.kt:26](../komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/model/TranslationSettings.kt#L26)) [code].

Conséquence pour l'audit : tout ce qui est décrit ci-dessous **tourne chez
l'utilisateur**. Ce n'est pas du code mort.

---

## 1. La chaîne, étage par étage

```
page japonaise
   ↓
PP-OCRv6 (reconnaissance verticale)
   ↓
OcrScriptFilter.keepCjk         ← filtre AVANT la fusion
   ↓
mergeOcrBoxes(vertical = true)  ← colonnes, droite à gauche
   ↓
JapaneseKatakanaGlossary        ← réécrit le katakana d'emphase en japonais courant
   ↓
JapanesePhraseBook              ← répond aux formules figées avant le moteur
   ↓
Bergamot ja→en                  ← pack Mozilla, 52 Mo
   ↓
Bergamot en→fr                  ← le même pack que l'anglais
   ↓
français
```

### 1.1 Filtre de script — 46 lignes

[OcrScriptFilter.kt](../komelia-domain/core/src/commonMain/kotlin/snd/komelia/image/OcrScriptFilter.kt) [code]

PP-OCRv6 lit 50 langues, donc sur une page de manga il lit aussi le japonais
dessiné dans le décor — enseignes, onomatopées, noren. Ces boîtes voisinent les
bulles, la fusion les colle en un bloc couvrant un quart de page, et le lecteur
peint un panneau opaque par-dessus.

Deux fonctions : `keepLatin` et `keepCjk`. Tolérance `CJK_TOLERANCE = 0.2` — un
glyphe CJK isolé dans une ligne anglaise ne fait pas jeter la bulle.

**Contrainte structurante** : le filtrage doit avoir lieu **avant** la fusion,
sinon la fusion a déjà joint les blocs.

### 1.2 Fusion verticale

[OcrMergeUtils.kt:15-78](../komelia-domain/core/src/commonMain/kotlin/snd/komelia/image/OcrMergeUtils.kt#L15) [code]

Le paramètre `vertical` inverse les axes de la fusion : ce qui est « empilé » et
ce qui est « en ligne » échangent leurs rôles, pour un lettrage en colonnes lues
de droite à gauche.

### 1.3 Glossaire katakana — 171 lignes

[JapaneseKatakanaGlossary.kt](../komelia-domain/core/src/commonMain/kotlin/snd/komelia/image/JapaneseKatakanaGlossary.kt) [code]

Le problème traité : la prose japonaise écrit 魚, 小便, ほど ; un manga écrit
サカナ, ションベン, ホド pour sonner brutal ou pour crier. Le modèle n'a jamais
vu la seconde graphie, il la translittère, et le résultat se lit comme un nom de
personnage — 狙うサカナは？ revenait « Qui est la cible, Sakana ? ».

C'est une **réécriture, jamais une traduction** : le remplacement est du japonais
et entre dans la phrase japonaise. Y mettre du français donnerait au moteur une
phrase bilingue.

**Mesuré** : 20 entrées sur le pivot livré → **9 gains nets, 2 pertes** [code,
documenté dans l'en-tête du fichier]. Les deux pertes viennent d'un choix de
hiragana là où le mot a un kanji courant.

Piège connu [note] : le champ utile du glossaire est `ja_normal` (le mot
japonais), pas la traduction française ; et le matching doit être aligné sur les
runs, sous peine de traverser des frontières de mots qui n'existent pas.

### 1.4 Carnet d'expressions japonais — 60 lignes

[JapanesePhraseBook.kt](../komelia-domain/core/src/commonMain/kotlin/snd/komelia/image/JapanesePhraseBook.kt) [code]

Même doctrine que le carnet anglais : bulle entière, correspondance exacte. Le
japonais rend la règle plus critique encore — sans espaces entre les mots, une
recherche « contient » matche à travers des frontières inexistantes.

**Mesuré avant livraison** : sur 474 bulles fusionnées d'un tome réel, la table
en bulle entière **ne s'est déclenchée aucune fois** ; en préfixe elle aurait
touché 2 % des phrases [code, en-tête du fichier]. Le tome mesuré est un titre
de yakuza avec très peu de conversation courante.

### 1.5 Le pivot ja → en → fr

[BergamotModels.kt:38-88](../komelia-domain/core/src/androidMain/kotlin/snd/komelia/image/BergamotModels.kt#L38) [code]

Mozilla ne publie **pas** de modèle ja→fr. La route passe par l'anglais :

```kotlin
private val SUPPORTED = setOf(
    ENGLISH to FRENCH,
    FRENCH to ENGLISH,
    JAPANESE to ENGLISH,
)
```

`route()` cherche d'abord un modèle direct, puis une route à deux sauts en
passant **uniquement par l'anglais** — « essayer toutes les langues comme pivot
trouverait des routes dont personne ne veut lire la sortie ».

Pack `ja-en` : **52 Mo**, endpoint et format identiques à `en-fr` (v2.1,
vocabulaire partagé) [code]. Coût : un téléchargement, aucun code nouveau.

Le téléchargeur ne récupère que ce qui manque [code,
[BergamotModelDownloader.kt:65-88](../komelia-domain/core/src/androidMain/kotlin/snd/komelia/image/BergamotModelDownloader.kt#L65)].

---

## 2. Décisions prises, avec leurs mesures

| décision | mesure | source |
|---|---|---|
| pivot ja→en→fr plutôt que direct | `opus-mt-ja-fr` **invente des noms propres** — « Tu n'as pas le choix, Nimah » pour 仕方ないだろ — à beam 1 comme à beam 4 | [code] |
| refus du modèle direct malgré sa taille | c'est un *teacher* fp32 de **76 M paramètres** ; le pivot tourne sur le student de **31 M**. Une traduction plate bat une traduction fabriquée | [code] |
| glossaire katakana conservé | 9 gains / 2 pertes sur 20 entrées | [code] |
| carnet japonais conservé malgré 0 déclenchement | le tome de mesure est atypique ; la table ne coûte rien | [code] |
| grouper les bulles avant traduction | **levier réfuté** : aucun gain mesuré | [note] |
| monter la résolution de l'OCR | **sans effet** : 83 % du texte gagné est du **furigana** — du bruit, pas du dialogue | [note] |
| variante « grosse » du modèle ja-en | Mozilla envoie 43 Mo à Android et 58 Mo au desktop ; le gros donne **12 gains / 7 pertes** — pas une solution | [note] |

### 2.1 La mesure qui a conclu

Sur **100 bulles communes** à un avant/après, après une journée de réglages :
**10 changements, 2 en mieux, 2 en pire** [note, reprise dans le message du
commit `b8d92156`].

Le message du commit formule la conclusion : *« La limite est le modèle student
de 31 M paramètres, pas la plomberie autour. »*

### 2.2 Répartition des échecs

| étage | part des échecs | source |
|---|---|---|
| ja→en (premier saut) | **≈ 2/3** | [note] |
| OCR japonais | ≈ 1/4 | [note] |
| en→fr (second saut) | ≈ 0 | [note] |

C'est le résultat le plus important du dossier : **le second saut n'est pas le
problème**. Le pivot n'ajoute presque rien à l'erreur ; c'est la traduction
japonais→anglais qui échoue.

---

## 3. Limitations connues et acceptées

### 3.1 L'ambiguïté perdue au premier saut

Documentée dans le code [code] : une ambiguïté résolue de travers en anglais ne
peut plus être rattrapée en français.

```
しょうがねぇな  →  "i can't help it"  →  « Je ne peux pas l'aider »
```

Le japonais dit « on n'y peut rien ». L'anglais choisit une lecture, le français
la traduit fidèlement, et l'erreur est irrécupérable — aucun étage en aval ne
peut savoir que la lecture était mauvaise.

### 3.2 Le plafond du modèle

31 M paramètres, quantifié int8. C'est la même classe que le modèle en-fr, et
c'est ce que la tablette peut faire tourner. Aucun réglage de la chaîne ne
compense un modèle qui ne comprend pas la phrase.

### 3.3 Écart banc / tablette

**42 % des bulles diffèrent** entre le banc (x86) et la tablette (ARM), à cause
des noyaux int8 [note]. Vérifié comme **neutre en qualité** : le service et les
modèles ont été innocentés par la mesure.

Conséquence pour un audit : **le banc tranche le déterministe, pas la nuance.**
Une différence de formulation entre banc et appareil n'est pas un bug.

### 3.4 Le furigana

Les petits kana en marge des kanji sont lus par l'OCR et se retrouvent dans le
texte. Monter la résolution en ramène davantage, pas mieux [note]. Il n'existe
**aucun filtre furigana dans le code** — le commit de retrait en mentionne un,
mais le grep ne le trouve pas sur `main` [code].

À vérifier lors de l'audit : ce filtre a-t-il jamais existé sur `main`, ou
seulement sur la branche de retrait ?

---

## 4. Blocages

| blocage | nature | contournable ? |
|---|---|---|
| pas de modèle ja→fr publié par Mozilla | données | non — le pivot est la seule route |
| le direct `opus-mt-ja-fr` hallucine | qualité | non — mesuré à beam 1 et 4 |
| 2/3 des échecs sont dans ja→en | modèle | seulement par un autre modèle ja→en |
| 52 Mo de pack supplémentaire | produit | acceptable, téléchargement à la demande |
| le student 31 M plafonne | modèle | fine-tuning, mêmes blocages de licence que l'anglais |

**Il n'y a pas de blocage technique dans notre code.** La chaîne fonctionne de
bout en bout — OCR vertical, filtre de script, fusion en colonnes, glossaire,
carnet, pivot. Ce qui échoue est le modèle ja→en, et il n'existe pas
d'alternative dans la classe mobile.

---

## 5. La décision en suspens

Une branche `chore/remove-japanese` retire la fonctionnalité et **n'a pas été
fusionnée**. Son message documente un piège qui compte pour l'audit :

> Les lignes existantes portent le nom « JAPANESE » dans `translation_source`,
> et ce champ était lu par un `valueOf` nu — il aurait levé une exception et
> fait échouer **tout le chargement des réglages du lecteur**, pas seulement ce
> champ.

Autrement dit : **retirer `JAPANESE` de l'énumération sans passer par
`enumOrDefault` casse l'application** pour tout utilisateur ayant déjà
sélectionné le japonais. La branche corrige ce point ; si la fusion est un jour
décidée, ce correctif doit venir avec.

Trois options :

1. **Laisser tel quel** — la fonctionnalité est livrée, imparfaite, et ne gêne
   personne qui ne l'active pas. Coût : 277 lignes de code japonais et 19
   branches `japanese` dans `ReaderState` [code] à maintenir.
2. **Fusionner la branche de retrait** — simplifie le lecteur, libère le pack de
   52 Mo. Perte : tout le travail décrit ici, à refaire si un meilleur modèle
   ja→en paraît.
3. **Garder et documenter comme expérimental** — l'étiqueter dans les réglages
   pour que l'attente soit juste.

---

## 6. Ce qu'un audit devrait vérifier

1. **Le japonais est-il réellement utilisable aujourd'hui ?** Ouvrir un tome
   japonais, activer la traduction, vérifier que le pack `ja-en` se télécharge
   et que du français sort. Aucune mesure récente ne le confirme sur 1.7.4.
2. **Le filtre furigana existe-t-il ?** Le commit de retrait en parle, le code
   sur `main` n'en contient pas trace.
3. **Le carnet japonais sert-il à quelque chose ?** 0 déclenchement sur 474
   bulles. À remesurer sur un titre de conversation courante avant de le garder.
4. **Le glossaire katakana tient-il hors du tome de calibration ?** 9 gains / 2
   pertes ont été mesurés sur 20 entrées, sur un seul corpus.
5. **Que coûte le japonais à l'anglais ?** 19 branches `japanese` dans
   `ReaderState` traversent le chemin de traduction anglais. Chacune est une
   occasion de régression sur le cas courant.

---

## 7. Reproduire les mesures

Le banc japonais existe :

```bash
cd /mnt/c/Users/mathi/Downloads/Dev/Sipurra-Myversion/Sipurra-myversion
./scripts/ocr-bench/run_pivot_ja.sh
```

Les sondes de comparaison direct / pivot sont dans
`scripts/ocr-bench/ja-probe.txt`.

Le banc anglais, qui partage toute la plomberie sauf le premier saut :

```bash
python scripts/ocr-bench/bench_en.py <nom-du-tome>
```
