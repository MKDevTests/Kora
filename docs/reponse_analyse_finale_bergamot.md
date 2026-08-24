# Analyse finale Bergamot : réponse

Réponse au document *« Bergamot EN→FR sur tablette Android — Analyse finale
après benchmarks réels et priorisation des améliorations »* (19 août 2026),
troisième d'une série qui répond à
[reponse_analyse_revisee_bergamot.md](reponse_analyse_revisee_bergamot.md), elle-même
réponse à [reponse_segmentation_bergamot.md](reponse_segmentation_bergamot.md).

**Verdict d'ensemble.** Le document reprend les mesures sans les déformer et
rejette tout ce que le corpus avait invalidé. C'est le premier des trois qui ne
propose aucune brique à zéro bulle. Il me corrige aussi sur un point chiffré, et
il a raison.

Il reste quatre problèmes, tous d'ordonnancement, et un angle mort partagé.

---

## 1. Il me corrige, vérification faite

Le §42 conteste les métriques que j'avais citées pour le modèle installé.
Vérifié à la source qu'il donne — `moz-fx-translations-data.../db/models.json`,
consulté le 19 août 2026 :

| métrique (flores200-plus) | valeur au registre |
|---|---|
| BLEU | 48,8533 |
| chrF | 70,8001 |
| chrF++ | 68,8238 |
| COMET22 | 0,8653 |
| spBLEU | 53,8402 |
| statut | Release |

**Mes chiffres étaient faux.** J'avais écrit « BLEU 49,6, COMET 0,870 », et
mentionné un `tiny/enfr` de 17,1 Mo à 48,5 BLEU. Ni l'un ni l'autre ne figure au
registre : **une seule entrée en-fr existe, `base-memory`, et il n'y a pas de
variante `tiny` pour cette paire.**

La correction renforce la conclusion produit au lieu de l'affaiblir. Je disais
« nous sommes sur le meilleur modèle publié pour cette paire ». La formulation
exacte est : **nous sommes sur le seul.** Il n'y a pas d'autre modèle en-fr à
essayer, ni au-dessus ni en dessous.

Le doc précédent a été corrigé en conséquence.

Réserve sur le §41 : « paramètres : 31 248 966 ». C'est plausible — un fichier
intgemm int8 approche un octet par paramètre, et le fichier fait 31 561 787
octets — mais je ne l'ai pas mesuré. À ne pas citer comme un chiffre maison.

---

## 2. Tableau de décision

| § | contenu | verdict |
|---|---|---|
| 1-9 | rejets confirmés (segmenter, splitter Kotlin, `max-length-break`, beam) | **d'accord**, ce sont mes mesures |
| 10-11 | taux de fautes 33 %, taxonomie A-E | **d'accord**, c'est mon comptage |
| 12-15 | carnet par relevé réel, `precision > recall` | **d'accord** |
| 16-17 | rejet de l'`OcrTextNormalizer` générique | **d'accord** |
| 18-21 | garder les réparations OCR et le découpeur existants | **d'accord** |
| 22-25 | ne pas toucher au plafond, mesurer les groupements manqués | **d'accord**, et le §25 est la bonne question |
| 26-27 | pas d'architecture oversize, une condition suffit | **d'accord** |
| 28-31 | le banc ne voit pas le carnet, priorité absolue | **d'accord**, avec un coût caché — §3 ci-dessous |
| 32-35 | corpus d'erreurs et taxonomie | **d'accord sur le principe**, réserve sur `expected_fr` |
| 36-37 | instrumentation `ruleId` / `groupReason` | **d'accord**, mais mal placée dans la file |
| 38-40 | KPI net, pistes déjà rejetées | **d'accord** |
| 41 | 31 248 966 paramètres | plausible, non vérifié ici |
| 42-43 | correction des métriques, pas de `tiny` | **exact, et j'avais tort** |
| 44-45 | Mozilla documente la faiblesse sur phrases courtes | **apport réel**, voir §4 |
| 46-54 | fine-tuning compact, budget tablette, architecture inchangée | d'accord, mais il manque la question qui décide — §6 |
| 55 | roadmap P0-P6 | **ordre à corriger**, voir §5 |
| 56-61 | ce qui sort, pipeline final, critères | d'accord ; `TimeToReady(N+1)` est un bon garde-fou |

---

## 3. Le coût caché du banc end-to-end (§28-31)

Le document classe « faire voir le `PhraseBook` au banc » en priorité absolue.
C'est juste, et c'est bien le défaut le plus grave relevé jusqu'ici : un
« 0 changed » du banc après un ajout au carnet ne veut rien dire, alors que la
tablette montrait 22 bulles corrigées.

Mais il ne mentionne pas ce que ça coûte le jour où on le branche.

Le banc compare chaque sortie à un `baseline.tsv` **béni**, un par tome. Brancher
le carnet fait bouger d'un coup toutes les bulles couvertes par le carnet, sur
les huit tomes. Concrètement :

1. le premier passage sort des centaines de lignes changées, toutes légitimes ;
2. il faut relire les diffs à la main avant d'accepter, sinon on bénit une
   régression avec ;
3. tant que ce n'est pas fait, le banc ne détecte plus aucune régression.

Ce n'est pas une raison de ne pas le faire. C'est une raison de le faire **en
une seule passe dédiée**, sans autre changement en vol, et de ne pas le
programmer un soir où on veut aussi livrer autre chose.

---

## 4. L'apport neuf : Mozilla connaît le problème (§44-45)

C'est le premier élément externe du dossier, et il est solide. Mozilla a
documenté des difficultés sur les phrases très courtes et les mots isolés dans
son propre entraînement, et évoque comme piste de conserver davantage de
phrases courtes au nettoyage du corpus.

Le rapprochement avec notre workload est direct :

| | valeur |
|---|---|
| médiane tokens par bulle | **10** |
| médiane tokens par phrase | **8** |
| fautes de catégorie C (construction du modèle) | ~19 sur 53 |

Notre corpus se situe précisément dans la zone que Mozilla reconnaît comme
faible. Ça fait passer le diagnostic de « le modèle plafonne » à « le modèle
plafonne sur ce profil-là, et c'est un défaut connu de son entraînement ».

Conséquence pratique : la catégorie C n'est pas une fatalité de la taille du
modèle, c'est une inadéquation de domaine. Ça rend le §47 (fine-tuning) plus
crédible qu'il ne l'était — sous réserve du §6 ci-dessous.

---

## 5. La roadmap est bien raisonnée et mal ordonnée (§55)

Deux corrections.

### P1 et P2 sont inversés

Le §33 veut un corpus avec les colonnes `source_after_repairs`,
`source_after_grouping`, `phrasebook_hit`. **Aucune n'est disponible aujourd'hui**
— c'est exactement ce que le §36 propose d'ajouter aux logs, et le §36 est classé
en P2, après.

Remplir le corpus avant d'instrumenter, c'est le remplir à la main puis le
refaire. L'instrumentation doit passer devant : elle sert au corpus **et** au
comptage manuel, qui est aujourd'hui la seule méthode de mesure de qualité.

### Le seul levier prouvé est en P3, derrière huit étapes

Le relevé d'expressions est, de toutes les pistes mesurées, celle qui a le
meilleur rendement :

| levier | gain mesuré |
|---|---|
| relevé d'expressions au carnet | **22 bulles / 24 pages** |
| réparations OCR ciblées | 14 bulles, dont 2 contresens de dates |
| découpeur de mots collés | 5 bulles |

La roadmap le place en P3, après le banc, le corpus et l'instrumentation — soit
onze étapes numérotées avant la première bulle corrigée. Sur un projet mené le
soir, c'est le meilleur moyen de construire beaucoup d'outillage et de ne jamais
arriver aux corrections.

Le relevé ne dépend d'aucun outil : il se fait en lisant, ce qu'on fait déjà. Il
doit sortir de la file et tourner en continu, en parallèle du reste.

### Réserve sur `expected_fr` (§34)

Écrire 242 traductions de référence à la main est faisable en volume. Le
problème est ailleurs : sur les catégories **C** (construction ratée) et **E**
(registre), la « bonne » traduction est un jugement, pas un fait. Un jeu de
validation rempli de références discutables donnera des verdicts discutables,
avec l'autorité d'un chiffre.

Proposition : ne remplir `expected_fr` que pour **A** (expression figée) et **D**
(OCR), où la réponse attendue est objective et vérifiable. Pour C et E,
enregistrer la faute et sa catégorie sans référence — elles servent au comptage,
pas à la validation automatique.

---

## 6. Ce que le fine-tuning ne dit pas (§47-52)

Le document décrit correctement l'objectif (adaptation de domaine à taille
constante), le pipeline, la quantification, le risque d'overfitting. Il ne pose
pas la question qui décide de la faisabilité :

> **avec quelles données ?**

Il n'existe pas de corpus parallèle EN/FR de comics librement exploitable. Le
§50 liste ce qu'il faudrait — dialogues courts, argot, interjections,
contractions — sans nommer une seule source réelle.

Le seul candidat sérieux est **OpenSubtitles** : registre parlé, phrases très
courtes, forte densité d'interjections et de contractions, volume EN/FR
important, licence exploitable. C'est proche de notre profil sans y être
identique (les sous-titres n'ont ni onomatopées lettrées, ni fragments répartis
sur plusieurs bulles).

Tant que cette vérification n'est pas faite, les §47-52 sont une intention
cohérente, pas un plan. C'est une demi-journée de vérification, à faire avant de
s'engager sur plusieurs jours d'entraînement.

---

## 7. L'angle mort partagé : la catégorie E

Ni ce document ni mes notes précédentes ne traitent la catégorie **E — casse et
registre (~8 fautes sur 53)**. Le §55 P5 propose de la *mesurer*. Aucune étape ne
propose de la *corriger*.

C'est pourtant le seul poste où la faute est **entièrement de notre côté**.

Le lettrage des comics est tout en capitales. Notre code abaisse la bulle en
casse normale avant de la traduire — c'est nécessaire, un modèle NMT traduit mal
du texte tout en majuscules. Mais cet abaissement est le nôtre, et les fautes
qu'il produit ne dépendent :

- ni du modèle Bergamot ;
- ni de son entraînement chez Mozilla ;
- ni d'un fine-tuning ;
- ni de l'OCR ;
- ni du regroupement des bulles.

Deux correctifs de cette semaine y touchaient déjà (la majuscule d'un nom
interpellé en 1.7.2, celle d'un nom de famille en 1.7.3), et les deux ont donné
des gains nets positifs. Il reste le registre proprement dit : le tutoiement, le
niveau de langue, les interjections rendues trop soutenues.

La piste tu/vous a été mesurée et rejetée à juste titre — 98 bulles réécrites,
accords et pluriels cassés, le « vous » français portant à la fois la politesse
et le nombre. Mais « la réécriture automatique ne marche pas » ne veut pas dire
« rien n'est possible » : une entrée de carnet pour une interjection mal rendue
règle le cas sans toucher à la grammaire.

À ce titre, E n'est pas une catégorie séparée à outiller : c'est du relevé
d'expressions, qui est déjà le meilleur levier.

---

## 8. Ordre de travail proposé

En remplacement du §55, avec les mêmes étapes et un ordre différent.

**En continu, sans attendre quoi que ce soit**

1. Relevé d'expressions en lisant — meilleur rendement mesuré, aucun prérequis.

**Outillage, dans cet ordre**

2. Instrumentation : `ruleId` sur les réparations, `groupReason` sur les groupes,
   `phraseBookHit` / `phraseBookKey` (§36). Sert au corpus et au comptage manuel.
3. Banc end-to-end : brancher `PhraseBook.lookup()`, en une passe dédiée, avec
   relecture des diffs et re-bénédiction des huit baselines (§3 ci-dessus).
4. Corpus `translation_errors.tsv`, alimenté par les 242 fautes relevées,
   `expected_fr` sur A et D seulement.

**Mesures qui restent à faire**

5. Groupements manqués (§25) : pour chaque paire de bulles non groupées, vérifier
   si elles formaient une phrase. C'est la seule mesure du dossier qui n'a jamais
   été faite.

**Seulement ensuite**

6. Vérifier la disponibilité d'un corpus d'entraînement (OpenSubtitles) avant
   d'ouvrir le fine-tuning.

---

## 9. Reproduire les vérifications

Métriques du modèle au registre Mozilla :

```
https://storage.googleapis.com/moz-fx-translations-data--303e-prod-translations-data/db/models.json
```

Chercher l'entrée `en` → `fr`. Une seule existe, `base-memory`, statut *Release*.

Audit des défauts visés par les documents précédents, sur le corpus du banc :

```bash
cd /mnt/c/Users/mathi/Downloads/Dev/Sipurra-Myversion/Sipurra-myversion
python3 scripts/ocr-bench/audit_document_claims.py
```

Distribution des tokens et comparaison bulle entière / phrase par phrase : voir
[reponse_segmentation_bergamot.md §9](reponse_segmentation_bergamot.md).
