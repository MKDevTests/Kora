# Traduction japonaise → français dans Kora — audit complet

**Document destiné à une relecture externe.** Il est autonome : aucun contexte
préalable du projet n'est nécessaire.

Date : 19/08/2026. Tous les chiffres cités sont mesurés. Quand une valeur est une
estimation ou une hypothèse, c'est écrit.

---

## 1. Le contexte en une page

**Kora** est un lecteur de manga et de bande dessinée sur tablette Android
(fork d'un client Komga). Une de ses fonctions traduit les bulles d'une page à
la volée, hors ligne, sur l'appareil — aucun service distant, aucune clé d'API,
aucun abonnement. C'est une contrainte de conception, pas une préférence.

### La chaîne

```
image ──OCR──▶ japonais ──tables──▶ japonais ──ja→en──▶ anglais ──en→fr──▶ français
       PP-OCRv6            réécritures        Bergamot            Bergamot
       (ONNX)              locales            41,9 Mo             (déjà livré)
```

**Pourquoi un pivot par l'anglais et non une traduction directe ja→fr :** Mozilla
publie un paquet Bergamot `ja-en` via le même canal de distribution que le
`en-fr` que l'application télécharge déjà. Le japonais coûte donc **un paquet de
plus et aucun nouveau moteur**. La traduction directe a été testée
(`Helsinki-NLP/opus-mt-ja-fr`) et perd : elle hallucine des noms propres.

**Contrainte matérielle :** c'est une tablette. Tout gain de qualité qui coûterait
un facteur de temps notable par page est rejeté d'office.

### Le moteur

Bergamot (Marian) en int8, `beam-size: 1`. **Vérifié dans le code**, pas supposé :
`translator.cpp:49` construit un config minimal avec `beam-size: 1` quand
l'appelant n'en fournit aucun, et `BergamotTranslationEngine` passe
`configYaml = null`.

---

## 2. L'instrument de mesure

Le point de bascule de ce chantier a été la construction d'un **corpus de
contrôle**. Avant lui, chaque décision se prenait sur un mécanisme isolé
(« cette table se déclenche 11 fois dont 6 à tort ») sans jamais savoir quelle
fraction du texte lu par l'utilisateur était concernée.

### Composition

| | |
|---|---|
| Source | 7 volumes japonais, 1 686 bulles, **1 646 uniques** |
| Corpus annoté | **198 bulles**, fichier `scripts/ocr-bench/corpus_ja.tsv` (versionné) |
| Pile « base » | **150 bulles**, tirage aléatoire proportionnel par volume |
| Pile « hard » | **48 bulles**, tirage ciblé sur les strates difficiles |

**Deux piles, et c'est délibéré.** Seule la pile *base* produit un taux : elle est
aléatoire, donc ses pourcentages parlent du texte réel. La pile *hard*
sur-représente les cas durs pour diagnostiquer, et **ne doit jamais servir à
annoncer un taux**.

Chaque ligne porte : pile, volume, page, strate, japonais source, anglais
intermédiaire, français final, verdict, cause.

### Les trois verdicts

- **correct** — le lecteur comprend ce que dit la bulle
- **dégradé** — compréhensible mais tordu, une nuance perdue
- **faux** — le sens est autre, ou la sortie est du charabia

### Les quatre causes

`ocr` · `ja-en` · `en-fr` · `credits`. Le pivot permet cette attribution parce
que l'anglais intermédiaire est conservé, donc les deux sauts se jugent
séparément.

### Limites de l'instrument, à connaître

1. **Annotateur unique** (moi). Les verdicts sont versionnés ligne par ligne,
   donc contestables, mais ce n'est pas une vérité terrain indépendante.
2. **n = 150** donne un intervalle de confiance à 95 % de **±7,4 points**. Le
   taux global est donc un ordre de grandeur, pas une mesure fine. Pour ±5
   points il faudrait 323 bulles, pour ±4 points 504.
3. **Corollaire méthodologique** : aucune décision ne se prend sur la variation
   du taux global. Toutes se prennent en **A/B apparié** — les mêmes bulles avant
   et après, en comptant les transitions de verdict. C'est incomparablement plus
   sensible.

---

## 3. Où en est le produit

**72,7 % des bulles japonaises sont correctes** (contre 69,3 % au début de la
journée).

| Verdict | départ | actuel |
|---|---|---|
| correct | 104 (69,3 %) | **109 (72,7 %)** |
| dégradé | 21 | 22 |
| faux | 25 | **19** |

Progrès démontré en A/B apparié, pas déduit d'une variation de taux :

| avant → après | n |
|---|---|
| faux → correct | 8 |
| dégradé → correct | 2 |
| faux → dégradé | 4 |
| **correct → dégradé** | **1** |
| correct → faux | 0 |

McNemar sur la première série (avant P1) : p = 0,002.

### Répartition des 41 échecs restants (pile base)

| Cause | n | % |
|---|---|---|
| **ja→en** | 27 | 66 % |
| OCR | 9 | 22 % |
| en→fr | 4 | 10 % |
| crédits | 1 | 2 % |

### Par strate

| Strate | ok / total | % correct |
|---|---|---|
| long | 5/6 | 83 % |
| plain | 79/105 | 75 % |
| tiny (≤ 4 caractères) | 14/19 | 74 % |
| **katakana** | **9/16** | **56 %** |
| dialecte | 2/4 | 50 % |

**Recalculé le 19/08/2026 depuis `corpus_ja.tsv`.** La version précédente de ce
tableau — katakana 44 %, plain 71 %, dialecte 75 % — avait été calculée avant la
re-annotation et n'avait jamais été refaite.

La conclusion qu'on en tirait, « le katakana est le point noir », **ne tient plus
non plus**. Test exact de Fisher sur la pile base : strate katakana contre plain,
p = 0,14 ; découpage par présence d'un run katakana d'au moins 3 caractères
(17/26 contre 92/124), p = 0,47. L'écart brut reste visible, mais n = 16 est trop
petit pour l'affirmer. 21,2 % des bulles du corpus complet contiennent un mot
katakana d'au moins 3 caractères : c'est une zone de risque plausible, pas un
fait mesuré.

---

## 4. Ce qui a été fait

Tous ces correctifs sont livrés et vérifiés au banc (rejeu des 7 volumes, chaque
ligne modifiée devant s'expliquer exactement par la règle).

| # | Correctif | Mécanisme | Bulles touchées | Gains / pertes |
|---|---|---|---|---|
| 1 | **Pipeline partagé** | `TranslationInput` | — | voir ci-dessous |
| 2 | **Corpus de contrôle** | `corpus_ja.tsv` | — | l'instrument |
| 3 | **Dakuten perdu** | `JapaneseOcrRepair` | 16 (0,9 %) | 15 / 0 |
| 4 | **Glossaire de genre** | `JapaneseDomainGlossary` | 49 (2,9 %) | 11 / 0 |
| 5 | **Boucles du décodeur** | `TranslationOutputRepair` | 9 (0,5 %) | 9 / 0 |
| 6 | **Écritures étrangères** | `JapaneseOcrRepair` | 17 (1,0 %) | 5 / 1 |

### 1. Le pipeline partagé — la dette la plus grave corrigée

Le travail entre les boîtes de l'OCR et les phrases envoyées au moteur existait
**en double** : une fois dans le lecteur, une fois dans le banc. Les deux ont
divergé, et la divergence était invisible dans les deux sens.

- **Japonais** : la copie du banc appliquait les règles latines au texte
  vertical. Le test des onomatopées et le nettoyeur anglais jetaient **203 blocs
  sur 210**. Le banc tournait, le rapport était plein, les tests passaient.
- **Anglais** : la règle des lignes de crédits, livrée dans le lecteur, n'avait
  jamais atteint le banc — 37 lignes que l'application laisse en anglais étaient
  encore traduites dans le corpus qui sert à la juger.

`TranslationInput.prepare()` est désormais l'unique implémentation, appelée par
les deux. Vérifié comme doit l'être un refactor : sortie identique au bit près
sur les 7 volumes japonais, et côté anglais les seules différences sont les 37
lignes de crédits, relues une par une.

### 3. Le dakuten

L'OCR perd les diacritiques du katakana (゛ et ゜). Sur `パーティー` — le groupe
d'aventuriers, nom le plus courant du genre — **il se trompe plus souvent qu'il
ne réussit** : `ハーティー` 14 fois contre `パーティー` 11.

Les dégâts n'apparaissent qu'en bout de chaîne et ne ressemblent pas à un
problème de reconnaissance : `アレンのハーティー` revenait « Allen Est Copieux ».

**Le piège, et pourquoi la règle évidente est fausse :** « ajouter les
diacritiques manquantes » se trompe une fois sur quatre. `ハーレム` (harem) est
correct **sans** marque (4 occurrences contre 1 pour `バーレム`), et l'OCR n'en
perd pas seulement — il en **invente de fausses**. C'est donc un lexique court
qui arbitre, et il refuse tout mot dont le squelette est lui-même un mot :
`ボール` renommerait `ホール` (un hall), `ビール` renommerait `ヒール` (heal,
présent dans les 7 volumes), `プレイヤー` renommerait `フレイヤー`, un personnage.

### 4. Le glossaire de termes de genre

Trois entrées, chacune mesurée **en contexte** sur jusqu'à 8 bulles réelles —
parce qu'un terme jugé isolé n'est pas le même terme : `パーティー` seul revient
« partie », dans une phrase « fête ».

| | occ. | → | gains / pertes |
|---|---|---|---|
| `勇者` | 27 | `英雄` | 4 / 0 |
| `パーティー` | 24 | `チーム` | 7 / 0 |
| `流石` | 4 | `さすが` | 3 / 0 |

`勇者` est un titre (le Héros) et « brave » est un adjectif. `流石` est un *ateji* —
le mot **est** `さすが`, écrit en kanji le modèle lit les caractères et répond
« Ryuseki », « une pierre de dérive ».

**Table séparée du glossaire katakana existant, délibérément** : celui-là
normalise l'emphase d'un lettreur et s'apparie sur des séquences katakana, donc
`勇者` et `流石` lui sont invisibles. Deux phénomènes, deux tables.

### 5. Les boucles du décodeur

Un décodeur en beam 1 tombe parfois en boucle. Sur les 1 646 bulles il le fait
9 fois, et deux fois il gâche une phrase par ailleurs correcte :

```
そういやハクは呪いに効く温泉を探してたっけ
  → « Non, non, non, non. Haku cherchait une source chaude… »
```

D'où une réduction de la boucle plutôt qu'un rejet de la traduction : rejeter
jetterait la moitié utile.

**Le résultat le plus instructif de la journée est venu de la vérification côté
anglais.** La règle évidente — « un mot répété trois fois, on réduit » —
appliquée aux 1 753 bulles anglaises produit **12 modifications, 12
régressions** : en bande dessinée la répétition est *dans le dessin*.
« Mwa ha ha ha ha ha! » deviendrait « Mwa ha ! », et « The 100 Girlfriends Who
Really, Really, Really, Really, REALLY Love You » **est le titre d'une série**.

Le bon critère n'est donc pas « la sortie répète » mais **« la source
répétait-elle déjà ? »**. Avec cette condition : 9 corrections en japonais,
**0 modification en anglais**.

### 6. Les écritures étrangères

| règle | tirs | protections |
|---|---|---|
| `别`→`別`, `龄`→`齢` (chinois simplifié) | 5 | inconditionnel |
| `-`→`ー` entre katakana | 6 | `内政:5-魅力:31` garde son vrai tiret |
| `世`→`せ` sans kanji adjacent | 9 | `世界`, `世間`, `前世`, `今世`, `出世`, `異世界` — **42 protégés** |

Le cas `世` mérite d'être noté : il était classé « furigana résiduel, difficile,
demande un dictionnaire de lectures ». C'est en fait un **homoglyphe**, qui prend
le même garde-fou que `ニ`/`二` déjà en place, retourné : `ニ` a besoin d'un kanji
à côté pour *être* un kanji ; `世` a besoin qu'il n'y en ait pas pour être un
kana.

---

## 5. Ce qui a été mis de côté ou annulé

Chaque ligne a été mesurée. Elles sont listées pour éviter qu'un futur analyste
refasse le travail.

| Piste | Mesure | Verdict |
|---|---|---|
| **Beam search 4 sur ja→en** | annotation **aveugle** sur 149 bulles : 35 mieux / 28 moins bien / 86 égales. Binomial **p = 0,45** | **fermée** — indiscernable du hasard |
| Beam 2 sur ja→en | modifie 120 bulles contre 149 pour beam 4 | non testé : ne peut pas battre un résultat nul |
| Beam 4 sur en→fr | en→fr ne pèse que 10 % des échecs | non testé, faible espérance |
| Modèle ja-en plus gros (58 Mo desktop contre 43 Mo Android) | 12 gains / 7 pertes | pas une solution |
| Traduction directe ja→fr (`opus-mt-ja-fr`) | hallucine des noms propres | rejetée, le pivot gagne |
| Grouper les bulles pour donner du contexte au modèle | aucun effet | réfutée |
| Monter la résolution de l'OCR | 83 % du texte gagné est du **furigana** | inutile |
| Segmentation des bulles par réseau de neurones | ~770 ms/page, ne parallélise pas | abandonnée |
| Crédits japonais (`原作`, `作画`…) | 24 phrases = 1,4 % du corpus | rangé, non prioritaire |
| `パーティー` → `一行` | « d'autres **lignes** », « en une seule ligne » | rejeté (ambigu) |
| `パーティー` → `仲間` | 7 gains / 3 pertes | battu par `チーム` (7/0) |
| `ハーレム` → `後宮` | « La guerre de Harlem » → « Gogusenki1 » | rejeté |
| `亜人` → `獣人` | créature différente, et ne gagne même pas | rejeté |
| `ラスボス` → `魔王` | **3 gains / 0 perte** | **rejeté quand même** — voir ci-dessous |

### Le cas `ラスボス` mérite un mot

Cette réécriture gagnait — 3 gains, 0 perte — et n'a pas été retenue. Un *last
boss* n'est pas toujours le roi démon, et l'une des trois phrases parle du
deuxième jeu d'une série. Réparer le français en changeant ce que dit la phrase
n'est pas une réparation.

### Le cas beam search mérite plus qu'un mot

C'était la piste la plus prometteuse identifiée : elle modifiait 89 % des
sorties, là où la meilleure table n'en touche que 3 %.

**Le premier test était mal posé.** Le script du banc applique un seul `beam-size`
aux **deux** sauts du pivot ; « beam 4 » montait donc ja→en et en→fr ensemble,
sans pouvoir attribuer quoi que ce soit. Isolés : ja→en modifie 149 bulles,
en→fr 118, et **91 changent sous les deux**.

**L'annotation a été faite en aveugle** — les deux sorties présentées en X/Y dans
un ordre aléatoire, jugées sans savoir laquelle était laquelle, révélées ensuite.
C'était nécessaire : j'annotais mon propre correctif.

Résultat : 35 / 28 / 86, **p = 0,45**. À comparer aux tables livrées, qui tournent
à 15/0, 11/0 et 9/0. Beam 4 échange des erreurs contre d'autres erreurs — il
répare `待て待て俺の方が` (« Attends ×8 » → « Attends, attends, c'est moi ! ») et
casse `きゃ` en `くゃ`.

---

## 6. Ce qui reste

### P2 — Élargir le corpus

150 bulles donnent ±7,4 points. 323 pour ±5, 504 pour ±4. **Non bloquant** tant
que les décisions se prennent en apparié, mais nécessaire dès qu'on voudra
annoncer un chiffre plutôt que comparer deux versions.

**Correction du 19/08/2026 — « coût : de l'annotation, rien d'autre » était faux.**
Il faut aussi passer l'OCR sur de nouveaux volumes, et surtout le matériel est
borné : le partage japonais ne contient que **5 séries, 21 volumes**, dont les 7
déjà passés au banc couvrent déjà les 5 séries. Il reste 14 volumes inutilisés,
répartis sur 3 séries seulement (Yushapati 9, Sekai 4, Zeccho 1). Un corpus
indépendant est donc possible **par volume, pas par titre** : toute
recommandation du type « 8 à 12 nouveaux titres » est irréalisable ici.

### P2bis — Ce corpus est devenu un jeu de développement

À signaler franchement à quiconque lit ce document : les 198 bulles ont servi à
observer les erreurs, écrire les règles, rejeter des pistes et mesurer le beam.
Elles restent un bon instrument pour l'A/B apparié et la non-régression, mais
**72,7 % n'est plus une mesure indépendante** de ce que Kora ferait d'un manga
jamais vu — le chiffre est optimiste d'une quantité inconnue.

Corollaire sur le KPI : le taux de bulles **franchement fausses** (19/150 =
12,7 %, IC 95 % 8,3–18,9) mérite d'être suivi autant que le taux correct, parce
que c'est le contresens qui gêne la lecture, pas la formulation maladroite. Sur
les 19 fausses, **14 viennent de ja→en** (73,7 %), contre 66 % sur l'ensemble des
échecs : le saut japonais→anglais domine encore plus les erreurs graves que les
erreurs en général.

### P3 — Protéger les noms propres

**Gisement mesuré : 3 à 5 des 65 échecs annotés.** Cette piste a d'abord été
classée n°1 sur la foi des « 337 bulles contenant du katakana », ce qui était une
erreur de raisonnement : un mot katakana n'est pas un nom propre. La mesure a
tranché — le taux d'erreur est de 35 % avec katakana contre 26 % sans, et le test
exact de Fisher donne p = 0,47 : l'écart n'est pas démontré. Et sur les 6 échecs imputés à `en→fr`,
**un seul** est un nom propre (`シリカ` → « magicien de **silice** »).

**Signal de détection disponible** : l'honorifique japonais. 17 séquences katakana
du corpus sont suivies de `さん`/`様`/`君`/`ちゃん`/`殿`, dans 56 bulles, et **toutes
sont des noms**. Détecteur d'une ligne, haute précision.

**Point technique favorable** : un mécanisme de jetons latins (`Xqz0`) existe déjà
dans le lecteur pour le glossaire par série, donc l'expérimentation de
*placeholders* est en partie écrite. Reste à vérifier qu'un jeton opaque traverse
Marian sans dégrader la syntaxe autour — ce n'est pas garanti.

### P4 — Fine-tuning ja→en

**Devenu le seul levier restant à fort potentiel**, maintenant que le beam est
fermé. Les ~27 échecs `ja-en` restants sont majoritairement des **erreurs de
modèle pures**, qu'aucune table n'atteint :

| japonais | rendu | défaut |
|---|---|---|
| `くっ来るなっ！！` (« n'approche pas ! ») | « Allez ! » | négation perdue |
| `こんだけあれば今夜は旨いもんが食えるな` | « ne mangez pas de bonne nourriture » | négation **inventée** |
| `ハクさんがかわいそうですよオラリアさん！` | destinataire et sujet échangés | rôles inversés |

Coût : données, entraînement, simulation 8 bits, quantification, export au format
Bergamot. Plusieurs semaines, résultat inconnu. **À ne pas lancer sans décision
explicite.**

### Estimation de plafond

Avec P2 et P3 : de l'ordre de **73-74 %**, et le gisement des correctifs
déterministes sera épuisé. C'est une estimation, pas une mesure.

---

## 7. Questions posées au relecteur

1. **Le corpus est-il assez solide pour ce qu'on lui fait dire ?** Annotateur
   unique, 150 bulles pour le taux. Les décisions se prennent en apparié, ce qui
   contourne partiellement le problème — est-ce suffisant ?

2. **Le résultat beam est-il correctement interprété ?** 35/28 sur 149 avec
   p = 0,45. Faut-il conclure « aucun effet » ou « effet trop faible pour être
   détecté à cette taille » ? La distinction change la suite : le second cas
   justifierait de ré-annoter sur un corpus élargi.

3. **Existe-t-il un levier non exploré ?** Ont été fermés : modèle plus gros,
   traduction directe, mise en lot, résolution OCR, beam search. Restent le
   fine-tuning et les tables. **Quelque chose manque-t-il à cette liste ?**

4. **La stratégie « corriger autour du modèle » a-t-elle atteint sa limite ?** Six
   correctifs ont produit +3,4 points au total. Chacun est propre ; aucun ne pèse
   plus de 3 % des bulles. Est-ce le signe qu'il faut changer d'approche, ou
   qu'il faut simplement en écrire davantage ?

5. **Le compromis produit est-il le bon ?** 72,7 % de bulles correctes, hors
   ligne, gratuit, sur une tablette. Faut-il investir plusieurs semaines dans un
   fine-tuning pour un gain inconnu, ou considérer que le confort de lecture est
   atteint ?

---

## Annexe A — Inventaire du pipeline

| Composant | Lignes | Rôle |
|---|---|---|
| `TranslationInput` | 191 | boîtes OCR → phrases, **partagé lecteur/banc** |
| `BubbleAssembler` | 301 | regroupement des bulles d'une phrase, redistribution |
| `JapaneseOcrRepair` | 215 | homoglyphes, diacritiques, écritures étrangères |
| `JapaneseKansaiNormaliser` | 260 | dialecte du Kansai → japonais standard (186 entrées) |
| `JapaneseKatakanaGlossary` | 171 | katakana d'emphase → japonais courant (257 entrées) |
| `JapaneseFuriganaFilter` | 129 | retrait de la glose phonétique (géométrique) |
| `TranslationOutputRepair` | 142 | boucles inventées par le décodeur |
| `CreditLine` | 158 | crédits laissés dans la langue d'origine |
| `JapaneseDomainGlossary` | 71 | vocabulaire de genre (3 entrées) |
| `JapanesePhraseBook` | 60 | réponses toutes faites (6 000 entrées) |

## Annexe B — Le banc

`VolumeReplayTest` rejoue des captures de boîtes OCR (`NNN.boxes.json`) à travers
**le vrai code Kotlin**, jusqu'à la phrase remise au moteur. La traduction elle-même
est faite hors banc par le binaire Bergamot réel, avec les mêmes réglages que
l'application.

Deux garde-fous, chacun né d'une panne réelle :

1. **Rendement en blocs** — un volume produisant moins d'un dixième de blocs par
   rapport à ses lignes reconnues échoue. Cause : rejouer les volumes japonais
   sans la variable d'environnement adéquate fait passer du texte vertical dans
   le filtre latin. 45 pages devenaient 3 blocs, et **le test rapportait un
   succès** — l'autre garde-fou exige 30 blocs pour juger, donc la panne qui
   détruit les blocs était précisément celle qu'il ne pouvait pas voir.
2. **Taux de survie** — si moins de 70 % des blocs atteignent le traducteur, une
   règle écrite pour l'autre langue est probablement en train de manger celle-ci.

## Annexe C — Règles de méthode dégagées

1. **Mesurer avant de coder.** La règle du dakuten paraissait évidente et était
   fausse une fois sur quatre. Ma liste de « caractères chinois » contenait `国`,
   `来` et `学` — du japonais ordinaire — et les remplacer aurait abîmé 62 bulles
   saines pour en réparer 5.
2. **Un prototype dans une autre langue valide une décision, jamais une
   implémentation.** Une règle validée en Python sur les deux corpus n'a rien
   fait dans l'application : `\w` est Unicode en Python et `[a-zA-Z0-9_]` en
   Kotlin, donc ni `待て` ni un mot accentué n'était un « mot ».
3. **Vérifier le corpus opposé.** La règle des boucles semblait acquise jusqu'à
   son application au corpus anglais : 12 modifications, 12 régressions.
4. **Annoter en aveugle** quand on juge son propre correctif.
5. **Un chiffre de temps mesuré une fois n'est pas une mesure.** Un « +19 % de
   CPU » annoncé pour beam 4 s'est révélé être du bruit de chargement.
6. **Une seule implémentation.** Un garde-fou rattrape un effondrement après
   coup ; seul le code partagé empêche la règle suivante d'être ajoutée d'un
   seul côté.
7. **Juger un chantier au nombre d'erreurs qu'il corrige**, pas au nombre de
   bulles qu'il touche. C'est la confusion qui avait mis les noms propres en
   tête du plan.
