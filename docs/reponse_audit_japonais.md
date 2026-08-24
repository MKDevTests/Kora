# Réponse à « Traduction japonaise JA→FR sur tablette »

Vérifications du 19 août 2026, Kora 1.7.4 puis `main` après fusion.

**Convention.** Chaque affirmation porte sa source :

- **[vérifié]** — mesuré ou lu dans le dépôt aujourd'hui, avec la commande.
- **[réfuté]** — le document affirme quelque chose que la vérification contredit.
- **[déjà fait]** — le document demande un travail qui existe.

---

## En un paragraphe

Le document est juste sur son diagnostic central et sur les chiffres du modèle,
où **il me corrige**. Mais sa feuille de route est décalée d'un cran : son
chantier n°1 est déjà écrit et validé, son chantier n°6 est déjà mesuré et
négatif, et l'un des trois modèles qu'il propose de comparer n'existe pas.
Surtout, il propose de commencer par instrumenter et constituer un corpus — sans
savoir que **l'outil de mesure japonais est infidèle** : il exécute la chaîne
anglaise sur du texte japonais et jette 97 % des bulles. C'est le seul vrai
blocage du dossier, et il précède tout le reste.

---

## 1. Ce sur quoi le document a raison, et me corrige

### 1.1 La taille du modèle ja→en — j'avais tort [vérifié]

Mon audit écrivait « student de 31 M paramètres ». Le document dit ~43,57 M.
Interrogation du registre que le téléchargeur de l'app utilise lui-même
(`BergamotModelDownloader.RECORDS_URL`) :

```powershell
Invoke-RestMethod 'https://firefox.settings.services.mozilla.com/v1/buckets/main/
  collections/translations-models/records?fromLang=ja&toLang=en&fileType=model'
```

| version | taille | `filter_expression` |
|---|---|---|
| **2.1** | 41,94 MiB | `env.appinfo.OS == 'Android'` |
| 2.0 | 56,75 MiB | `env.appinfo.OS != 'Android' \|\| env.channel != 'release'` |
| 2.0a1 | 56,75 MiB | canaux de développement |

41,94 MiB = 43,98 Mo décimaux, 56,75 MiB = 59,50 Mo. **Les deux chiffres du
document sont exacts.** Un modèle int8 de 42 Mo ne peut pas porter 31 M de
paramètres ; mon chiffre venait du modèle en-fr et n'avait rien à faire là.

Le pack complet ja-en fait bien **52,24 Mo** (modèle 41,94 + lexique 8,92 +
vocabulaire 1,38), ce qui confirme le commentaire de `BergamotModels.kt`.

### 1.2 Le diagnostic central

`ja→en ≈ 2/3 des échecs, OCR ≈ 1/4, en→fr ≈ 0`. Le document en tire la bonne
conséquence : ne pas travailler sur `en→fr` pour le japonais. Accord complet.

### 1.3 Les principes de méthode

`filter before merge` (§3), matching en bulle entière et jamais `contains()`
(§10), pas de convertisseur katakana→kanji générique (§7), `net_corrected` comme
KPI (§54), étiquette « expérimental » dans l'interface (§49) : tous justes, et
les trois premiers sont déjà la règle dans le code.

---

## 2. Ce qui est déjà fait

### 2.1 Le FuriganaFilter — tout le P3 [déjà fait]

Le document consacre sept sections (§22-28) à concevoir un `FuriganaFilter`, à
le placer après le filtre de script et avant la fusion, à le lancer en *shadow
mode*, puis à mesurer ses faux positifs.

Il existe. `JapaneseFuriganaFilter.kt`, 129 lignes, 145 lignes de test, écrit le
18/08/2026, **appliqué exactement où le document le demande** — après
`OcrScriptFilter.keepCjk`, avant `mergeOcrBoxes`.

Il était sur `feat/japanese-kansai`, jamais fusionnée. **Fusionné dans `main` le
19/08/2026** avec deux autres commits japonais que le document ne mentionne pas
(normalisation du dialecte du Kansai, réparation des homoglyphes d'OCR).

La règle mesurée, à trois conditions toutes requises :

1. texte entièrement en kana ;
2. boîte plus fine que 0,75 × la médiane de la page ;
3. une boîte au moins 1,6× plus épaisse court le long, à moins de 1,8 × sa
   largeur, avec 45 % de recouvrement sur l'axe de lecture.

La condition 3 est celle qui protège `はい` ou `うん` — une bulle courte tout en
kana n'a pas de compagne plus épaisse à côté d'elle.

Résultats déjà obtenus, sur tablette, 98 pages, 4 résolutions :

- rappel **82 %** ;
- à 1351×1920 : **2 furigana passent encore sur 269 bulles** (0,7 %), contre
  ~26 % des boîtes avant filtre ;
- **contrôle de sûreté** : sur un scan 835×1200, la règle marque **1 boîte sur
  601**, et c'est `サラリー`, un morceau de サラリーマン coupé par le détecteur.
  Aucun dialogue perdu.

Ce dernier chiffre est le plus important : le filtre est **inerte là où il n'y a
pas de furigana**. C'est ce que le *shadow mode* du document cherchait à
établir, et c'est établi.

### 2.2 La correction du glossaire vers le kanji — §6 [déjà fait, et sans matière]

Le document recommande `サカナ → 魚` plutôt que `サカナ → さかな`, sur la base des
deux pertes mesurées.

Les deux entrées visées sont déjà corrigées : `チンポ → 陰茎`, `ションベン →
小便`, `サカナ → 獲物`. Et `ヨダレ` / `キンタマ` ont été retirées faute de forme
japonaise qui traduise mieux — ce que l'en-tête du fichier documente.

Reste à savoir si la règle s'applique ailleurs. Sur les 257 entrées, 92 ont un
remplacement entièrement en hiragana. Lecture des 92 : **environ 75 sont des
mots grammaticaux** — `こと`, `もの`, `ところ`, `わけ`, `から`, `より`, `など`,
`けど`, `のに`, `なら`, `たら`, `れば` — dont le hiragana **est** la graphie
normale du japonais moderne. Réécrire `こと` en `事` rendrait la phrase moins
naturelle, pas plus.

Le reste sont des adjectifs familiers (`やばい`, `でかい`, `かっこいい`) qui
n'ont pas de kanji courant. **Le §6 généralise à 92 entrées une règle qui vaut
pour une poignée de mots lexicaux, tous déjà traités.**

---

## 3. Ce qui est réfuté

### 3.1 Le `tiny` ja→en n'existe pas [réfuté]

Le §17 propose de comparer trois variantes, dont « C — Contrôle léger : Mozilla
tiny JA→EN, ~25,5 MB ». Le §18 lui attribue des métriques précises : spBLEU
26,5, chrF 54,8, COMET22 0,851.

Le registre de production ne contient **aucun** enregistrement `tiny` pour
ja-en : trois versions, toutes du même modèle (cf. §1.1). Le document l'admet
lui-même au §42 — « aucun tiny JA→EN public clairement identifié n'est
actuellement disponible » — sans retirer la proposition ni les métriques.

C'est la même erreur que celle relevée dans le dossier anglais : des chiffres
précis attribués à un modèle qui n'est pas publié.

### 3.2 Le modèle « qualité » de 59,5 Mo est l'ancienne version [réfuté]

Le §16 le présente comme un modèle plus riche à essayer, le §21 propose un mode
« Qualité » dans les réglages, le P6 en fait un chantier.

Les `filter_expression` disent autre chose. Android reçoit la **2.1**, le desktop
est resté sur la **2.0**. Le petit modèle est le **plus récent**. Ce n'est pas
un arbitrage taille contre qualité, c'est une succession de versions dont
Mozilla n'a livré la dernière qu'à Android.

Et le test a déjà eu lieu : **12 gains / 7 pertes** — chiffre que le §18 cite
lui-même sans en tirer la conséquence. Revenir à la 2.0 pour un solde de +5
formulations sur un modèle plus vieux et 15 Mo plus lourd n'est pas un chantier.
**P6 est clos.**

### 3.3 Le « 83 % de furigana » ne dit pas ce que le document lui fait dire

Le §22 fonde la priorité du furigana sur « l'augmentation de résolution OCR
faisait remonter 83 % de furigana ».

La mesure d'origine porte sur les **83 lignes trouvées seulement à 1920 px** et
absentes à 1200 px : 83 % d'entre elles sont tout-kana. C'est 83 % du texte
**gagné en montant la résolution**, pas 83 % du texte lu. À 1200 px il n'y a
presque pas de furigana détecté — d'où le contrôle de sûreté à 1 boîte sur 601.

La conclusion du document reste bonne. Sa prémisse est plus étroite qu'il ne
l'écrit, et cela change la priorité : le furigana est un problème **des scans
haute résolution**, pas du japonais en général.

---

## 4. Le blocage réel, que le document ne voit pas

### 4.1 Le banc japonais mesure un pipeline qui n'existe pas [vérifié]

Le P1 du document demande d'instrumenter, le P2 de constituer un corpus
d'erreurs japonais. Les deux passent par l'outil de rejeu, `VolumeReplayTest`,
qui rejoue le vrai code Kotlin sur des pages capturées.

Il a bien un mode `vertical`, mais **il ne s'en sert que pour trois choses** : le
filtre CJK, l'ordre de lecture des colonnes, et l'assertion de chevauchement.
Tout le traitement du texte reste la chaîne anglaise.

Confronté à `ReaderState.translateBlocks`, sept divergences :

| étage | l'application, en japonais | le banc |
|---|---|---|
| `rejoinLineBreaks` | ignoré | appliqué |
| `OcrSpellRepair` | ignoré | appliqué |
| `toSentenceCase` | ignoré | appliqué |
| `isSoundEffect` | **désactivé** | **filtre** |
| `EnglishTextCleaner.isTranslatable` | **désactivé** | **filtre** |
| longueur minimale | `letters >= 2` | `letters >= 2 && length >= 3` |
| groupement / carnet | une bulle = une phrase, `JapanesePhraseBook` | `BubbleAssembler.group`, carnet **anglais** |

Les cinq premières lignes viennent des branches `if (japanese)` de
`translateBlocks` ; le banc n'en applique aucune.

**Effet mesuré**, rejeu de `Otome Game Kyouwakoku-hen 01`, 40 pages :

```
210 blocs entrent  ->  7 phrases sortent
```

**97 % des bulles japonaises sont jetées** par des règles écrites pour l'anglais.
`第01話` et `メゲー世界は` sont marqués `SFX` par un détecteur d'onomatopées
latines que l'application désactive en japonais.

### 4.2 Pourquoi cela passe avant tout le reste

Le document veut mesurer par étage (`error_stage`), compter les déclenchements du
carnet et du glossaire, comparer deux modèles sur un corpus d'erreurs. Toutes ces
mesures passeraient par cet outil. **Aucune ne serait valide.**

C'est aussi une raison d'être prudent avec les mesures japonaises existantes : il
faut vérifier, pour chacune, si elle est passée par le banc ou par la tablette.
Celles validées sur tablette — le filtre furigana, l'écart ARM/x86 — ne sont pas
concernées.

### 4.3 Le corpus japonais n'existait pas [vérifié]

Autre prérequis silencieux du P2 : il n'y avait **aucun corpus japonais**.
`_bench-en` seulement, et `ja-probe.txt` fait 15 lignes.

Constitué le 19/08/2026, trois tomes de genres différents, 120 pages, en lecture
seule sur le partage, avec le classifieur d'orientation que le japonais active :

```
_bench-ja/Otome-Game-01    703 lignes OCR    1351x1920
_bench-ja/Yushapati-01     582 lignes OCR     984x1400
_bench-ja/Zeccho-01        342 lignes OCR     850x1200
```

---

## 5. Une mesure neuve : le glossaire katakana est inerte

Faite sur les **lignes OCR brutes**, donc indépendante du banc défectueux.

**0 déclenchement sur 257 entrées, sur 1 627 lignes.**

Port du matching vérifié contre le Kotlin avant de conclure : `ナイフ` ne
déclenche pas `ナイ` (protection alignée sur le run), `狙うサカナは？` déclenche
bien `サカナ → 獲物`, `コノヤロウ！` et `ヤベェな` déclenchent.

**Ce n'est pas un défaut du glossaire, c'est le genre.** Ces trois tomes sont des
isekai : le katakana y sert aux noms propres — `リオン・フォウ・バルトファルト`,
`ユリウス・ラファ・ホルファート` — et aux mots étrangers (`キャラクター`), pas à
l'emphase que le glossaire vise. La composition le confirme : 57 % hiragana,
24 % kanji, 12 % katakana, dont l'essentiel en noms.

Deux conséquences, opposées :

1. **Le glossaire ne nuit pas** : zéro déclenchement, zéro régression possible.
2. **Il ne sert à rien sur ces séries.** Sa mesure fondatrice (9 gains / 2
   pertes) portait sur 20 entrées d'un tome de yakuza ; les 237 ajoutées depuis
   n'ont jamais été évaluées, et le corpus disponible ne permet pas de les
   évaluer.

Le §36 du document — « mesurer le gain net par entrée, retirer les entrées à
perte » — est donc **juste mais inapplicable** en l'état : il n'y a rien à
mesurer tant que le corpus ne contient pas un titre où le katakana d'emphase est
courant (seinen, délinquants, yakuza).

---

## 6. La feuille de route corrigée

| doc | chantier | statut réel |
|---|---|---|
| — | **rendre le banc japonais fidèle** | **prérequis de tout, non fait** |
| P0 | valider 1.7.4 sur tablette | à faire, d'accord |
| P1 | instrumenter par étage | à faire **après** le banc |
| P2 | corpus d'erreurs japonais | corpus OCR **fait** ; le classement attend le banc |
| P3 | FuriganaFilter | **fait et fusionné** |
| P4 | nettoyer le glossaire | **sans matière** sur le corpus disponible |
| P5 | alimenter le carnet japonais | à faire, d'accord |
| P6 | modèle `base` 59,5 Mo | **clos** — ancienne version, 12/7 déjà mesuré |
| P7 | mode Qualité | sans objet, découle de P6 |
| P8 | fine-tuning ja→en | reste la seule voie de fond, mêmes blocages de licence que l'anglais |

**Deux ajouts que le document ne propose pas :**

1. **Les crédits japonais.** Le rejeu montre en tête de tome
   `原作三嶋与夢 作画行々狸 キャラクター原案：孟達 構成マツリセイシロウ` — une
   ligne de crédits. Le filtre `CreditLine` écrit le 19/08 est **anglais
   uniquement** ; les mots japonais correspondants (`原作`, `作画`, `構成`,
   `制作`, `キャラクター原案`) sont une liste courte et fermée, comme leur
   équivalent anglais.

2. **Le langage familier en hiragana.** Le corpus est plein de `いらねーんだよ`,
   `じゃねーの`, `度胸あるじゃねーの` — des contractions familières écrites en
   **hiragana**, que le glossaire katakana ne peut pas voir par construction. Le
   `JapaneseKansaiNormaliser` qui vient d'être fusionné traite une partie de ce
   terrain ; son taux de déclenchement sur ce corpus n'est pas encore mesuré,
   et il le sera par le banc corrigé.

---

## 7. Ce que je retire de mon propre audit

- « student de 31 M paramètres » : **faux**, c'est ~43,5 M. Le document a raison.
- « aucun filtre furigana dans le code » : **faux**. Il existe, il est testé et
  validé sur tablette ; il n'était pas sur `main`. La question posée était la
  bonne, la conclusion était trop rapide.
- « glossaire katakana : 20 entrées » : le fichier livré en compte **257**. La
  mesure 9/2 ne couvre que les 20 premières.
