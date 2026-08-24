# Réponse à « Roadmap corrigée après audit du pipeline réel et du banc de mesure »

Vérifications du 19 août 2026, sur `main` après les fusions du jour.

---

## En un paragraphe

Le document est exact sur tout ce qu'il reprend, et il a **raison contre moi**
sur un point d'architecture. Mais il décrit comme prioritaires deux chantiers qui
ont été faits entre sa rédaction et sa lecture, il se contredit entre son §18 et
son §20, et il illustre son test de parité avec des chiffres inventés. Surtout,
il désigne le normaliseur Kansai comme « la prochaine expérience la plus
intéressante non encore mesurée » — elle vient d'être faite, et **elle trouve une
régression**.

---

## 1. Deux chantiers déjà terminés

### 1.1 P1, corriger le banc — fait et fusionné

Le §15 liste huit divergences, le §16 le chiffre `210 → 7`, le §17 en tire les
conséquences, le P1 en fait le premier chantier. Tout cela est juste, et vient de
mon propre relevé. **Le correctif est fusionné dans `main`** (`07297a68`).

Le rejeu trouvait deux divergences de plus que celles listées :

- **le filtre furigana n'était pas appliqué du tout** par le banc — la glose
  phonétique imprimée à côté des kanji entrait dans la fusion, c'est-à-dire
  exactement le bruit que ce filtre existe pour retirer ;
- **la jointure des colonnes insérait une espace**, là où le japonais n'en a
  pas, ce qui recoupait les mots que le détecteur avait déjà coupés.

Résultat, sur les trois tomes :

| | avant | après |
|---|---|---|
| Otome-Game-01 | 7 | **208** |
| Zeccho-01 | 3 | **155** |
| Yushapati-01 | 11 | **235** |

Et la preuve visible que le filtre furigana fait son travail :

```
avant   なまみ けっとう 生身で鎧と決闘なんて どきょう 度胸あるじゃねーの
après   生身で鎧と決闘なんて度胸あるじゃねーの
```

Non-régression sur l'anglais vérifiée, ce code étant partagé : Blue Box 139,
Akane-banashi 180 et A Late Start Tamer reviennent tous `0 changed, 0 new,
0 gone`.

### 1.2 P4, rejouer les 120 pages — fait

Conséquence du précédent. **614 blocs fusionnés**, 598 phrases envoyées. Le
glossaire katakana a été remesuré dessus : toujours **0 déclenchement sur 257
entrées**, cette fois sur des blocs correctement construits. Le résultat tenait.

---

## 2. Le document a raison contre moi — §18

> « Corriger le banc en recopiant tous les `if (japanese)` de `ReaderState`
> créerait deux implémentations susceptibles de diverger à nouveau. »

**C'est exactement ce que j'ai fait.** Le correctif fusionné recopie les neuf
branches. Il rend le banc fidèle aujourd'hui et ne garantit rien pour demain :
la prochaine règle ajoutée à `ReaderState` recréera l'écart, et personne ne le
verra — c'est précisément ainsi que les 97 % sont apparus.

C'est une dette, et je l'assume comme telle. Elle avait une raison : le banc ne
peut pas dépendre de `:komelia-domain:core` (la cible desktop de ce module ne
compile pas dans ce fork), il compile une **liste de fichiers sources**. Or
`translateBlocks` vit dans `komelia-ui`, qui tire tout le graphe de l'application.

Mais cette raison ne condamne pas la solution du document, elle en dessine la
forme : **il faut extraire la partie déterministe dans
`komelia-domain/core/commonMain`**, à côté de `BubbleAssembler`, `PhraseBook` et
des tables japonaises qui y sont déjà. Le banc l'ajoute à sa liste `include`, le
lecteur l'appelle. Aucun des deux ne le réécrit. C'est faisable et c'est la
bonne cible.

---

## 3. Une contradiction interne — §18/§19 contre §20

Le §19 demande un pipeline **partagé** entre `ReaderState` et
`VolumeReplayTest`. Le §20 demande ensuite un test de parité :

```
ProductionPipeline(page) == ReplayPipeline(page)
```

**Les deux ne peuvent pas coexister.** Si le pipeline est partagé, il n'y a plus
qu'une implémentation, et ce test compare une chose avec elle-même : il passe
toujours, quoi qu'il arrive, ce qui est pire qu'aucun test. S'il y a deux
implémentations à comparer, c'est que le partage n'a pas eu lieu et que le §19
n'est pas appliqué.

Il faut choisir, et le bon choix est le §19. Ce qui remplace utilement le test de
parité, une fois le code partagé, est un **test de non-régression sur valeurs
figées** : le banc rejoue les 120 pages et compare les compteurs par étage à des
nombres écrits dans le test. Cela n'atteste pas d'une égalité entre deux
implémentations — cela ne veut plus rien dire — mais cela fait échouer le jour où
un changement modifie silencieusement ce qui part au traducteur.

---

## 4. Les chiffres du §20 sont inventés

Le §20 illustre son test avec une cascade :

```
raw blocks           210 / 210
after script         197 / 197
after furigana       184 / 184
after merge          162 / 162
translation units    158 / 158
```

Elle est plausible et elle est fausse. Les vrais nombres, mesurés sur le même
tome :

| étage | réel |
|---|---|
| lignes OCR brutes | 703 |
| blocs après filtre, furigana **et** fusion | **211** |
| phrases envoyées au traducteur | **208** |

L'écart n'est pas un détail d'échelle : la cascade du document suggère une
décroissance régulière où chaque étage retire quelques pourcents, alors que la
réalité est **une chute unique à la fusion** (703 → 211, les colonnes d'une même
bulle se rassemblant) puis **presque plus rien** (211 → 208). Un test écrit sur
la forme supposée aurait été calibré sur un comportement qui n'existe pas.

C'est le troisième document de la série où des chiffres précis apparaissent sans
source — après les métriques du `tiny` ja-en et les BLEU du modèle en-fr. Le
défaut est constant : la structure du raisonnement est bonne, les nombres qui
l'illustrent sont produits par vraisemblance.

---

## 5. La mesure que le document réclame — et ce qu'elle trouve

Le §13 désigne le normaliseur Kansai comme la prochaine expérience utile et
demande de compter déclenchements, corrections et dégradations. Le banc étant
maintenant fidèle, la mesure est possible. Je l'ai instrumenté pour qu'il dise
**quelle table** a réécrit quoi (`rewrites.txt`), puisque le dialecte et le
glossaire s'appliquent l'un après l'autre sur la même phrase.

Sur les **598 bulles** des trois tomes :

| table | déclenchements |
|---|---|
| réparation d'homoglyphes | **0** |
| glossaire katakana | **0** |
| normaliseur Kansai | **1** |
| carnet japonais | 1 réponse |

Et l'unique déclenchement du Kansai est **une dégradation** :

```
四人しかいない  ->  四人しかない
```

`かいな` est bien une particule finale du Kansai, et la règle `かいな → かな` est
juste **pour cette particule**. Ici elle a atterri à l'intérieur de
`し-かいな-い`, à travers une frontière de mot qui n'existe pas. `四人しかいない`
= « il n'y en a que quatre » ; `四人しかない` change le verbe d'existence.

C'est exactement le défaut que le §10 du document précédent interdisait — jamais
de `contains()` en japonais — et que la classe elle-même documente comme sa
raison d'être : son en-tête raconte `やな` mordant dans `やない` et `われ` dans
`言われた`.

**Le mécanisme de protection existe et n'est presque pas utilisé** : 3 entrées
sur 187 déclarent une garde. Et **39 entrées sont des chaînes tout-hiragana de
trois caractères ou moins** — `ゆう → 言う`, `へん → ない`, `ねん → んだ`,
`ほな → それでは` — c'est-à-dire la classe de clé qui peut atterrir n'importe où.
Le tri par longueur décroissante protège `やな` de `やない` uniquement parce que
`やない` figure aussi dans la table ; rien ne joue ce rôle pour `かいな`.

**Ce que cela change dans la lecture des mesures existantes.** Le 19 touchées / 9
mieux / 0 régression du Kansai a été obtenu sur un tome **dont les personnages
parlent Kansai**. La table n'avait jamais été confrontée à du japonais ordinaire.
Or 99 % des mangas sont en japonais ordinaire, et sur ce terrain le seul verdict
disponible est : **0 correction, 1 dégradation**.

Un seul cas ne fait pas une statistique, et je ne prétends pas le contraire. Mais
le mécanisme est compris, il est reproductible, et il concerne 39 règles.

Le §13 avertit : « normaliser davantage n'est pas automatiquement meilleur ». La
mesure dit quelque chose de plus dur — ici, il faut normaliser **moins**, ou
poser une garde sur les particules finales avant de continuer.

---

## 5 bis. Le correctif, et ce que l'élargissement du corpus change

Le corpus a été porté à **sept tomes, 315 pages, 1 686 bulles**, parce qu'un
seul déclenchement ne permet pas de concevoir une garde. Ce qui change avec le
volume :

### 5 bis.1 Le Kansai : 11 déclenchements, 6 fautifs

| avant | après | règle en cause |
|---|---|---|
| 触ってやろうか | 触って**だろうう**か | `やろ → だろう` |
| 一生懸命やろうって | 一生懸命**だろうう**って | `やろ` |
| 聞いてやろうぜ | 聞いて**だろうう**ぜ | `やろ` |
| 四人しかいない | 四人しか**ない** | `かいな` |
| そのへんで | その**ない**で | `へん` |
| そうきゆう早急に | そうき**言う**早急に | `ゆう` |

Trois de ces sorties contiennent `だろうう`, qui n'est un mot d'aucune langue.
Les cinq déclenchements corrects étaient `ワシ → 俺` (quatre fois, protégé par la
garde katakana) et `あかん → だめ`.

**Précision mesurée sur du japonais ordinaire : 5/11, soit 45 %.**

### 5 bis.2 Trois gardes, linguistiques et non ad hoc

| clé | garde | justification |
|---|---|---|
| `へん` | le kana précédent est dans la **colonne -a** | une négation Kansai s'attache au 未然形 |
| `やろ` | le caractère suivant **n'est pas `う`** | la copule や+ろ n'est pas la queue du volitif ~やろう |
| `かいな` | **fin de proposition** | c'est une particule finale, rien d'autre |

Et `ゆう → 言う` est retirée : sur 1 686 bulles elle se déclenche une fois, à
tort, jamais utilement. `ゆうた` et `ゆうて` gardent leurs entrées propres.

**Résultat : 11 → 5 déclenchements, les cinq corrects, aucun gain perdu.**
Quatre tests ajoutés, chacun bâti sur une ligne réelle du corpus.

Un coût à nommer : la garde `NEGATIVE_STEM` restreint la règle générique `へん`
aux verbes godan, dont la base négative finit en -a. Les ichidan (`食べへん`,
`見えへん`, `できへん`) la perdent — mais les plus courants figurent déjà
individuellement dans la table, et sur le corpus disponible la perte est nulle.

### 5 bis.3 Je dois corriger ma conclusion sur le glossaire katakana

J'ai écrit que le glossaire était **inerte, 0 déclenchement sur 257 entrées**.
C'était vrai sur trois tomes. Sur sept, il se déclenche **six fois** :

```
なんかイヤだ！              ->  なんかいやだ！          correct
テメェの連れ                ->  お前の連れ              correct
テメエらの国                ->  お前たちの国            correct
キモイ                      ->  気持ち悪い              correct
ユメ(变装中)                ->  夢(变装中)              douteux
どうした？ユメ              ->  どうした？夢            douteux
```

**Ma conclusion était trop rapide, et l'erreur est la même que celle que je
reproche au document : conclure d'un corpus trop petit.** Le glossaire n'est pas
inerte, il est simplement rare — six déclenchements sur 1 686 bulles, 0,36 %.

### 5 bis.4 Un problème que personne n'avait vu : les prénoms en katakana

Les deux déclenchements douteux sont le même : `ユメ` est réécrit en `夢`. Or
dans `どうした？ユメ` la position est celle d'un vocatif — c'est un **prénom**,
Yume. La sortie devient « comment vas-tu, rêve ? ».

C'est le défaut symétrique de celui que le glossaire corrige. Il existe pour
empêcher le moteur de lire un mot d'emphase comme un nom propre ; ici il lit un
nom propre comme un mot ordinaire. Et le corpus est un isekai, donc **plein**
de prénoms en katakana — `リオン`, `クレア`, `ユメ`.

Ce n'est pas corrigé, et je ne propose pas de le corriger à l'aveugle : une
garde possible serait la position (après une ponctuation d'adresse, ou en fin de
bulle isolée), une autre le fait que le mot apparaisse souvent dans le tome, ce
qui trahit un personnage. Les deux demandent une mesure, et le corpus est
maintenant là pour la faire.

---

## 6. Les crédits japonais — §25 validé par la mesure

La liste proposée (`原作`, `作画`, `構成`, `制作`, `キャラクター原案`) donne, sur
les 598 phrases : **7 correspondances, 7 vrais crédits, 0 faux positif.**

```
原作三嶋与夢作画行々狸キャラクター原案：孟達構成マツリセイシロウ制作:FTops
漫画・惊野わさび原作水月穹キャラクター原案・DeeCHA
キャラクター原案:DeeCHA
原作：水月穹
原作
原作：まるせい
```

C'est **plus propre que la version anglaise**, qui a besoin du rapport de forme
et de la position dans le tome pour écarter `STORY!` ou `TRANSLATION NOTE!`. Les
marqueurs japonais sont des mots de métier qui n'apparaissent pas en dialogue.

Un ajout que la mesure suggère : `漫画` figure dans l'un des sept et n'est pas
dans la liste.

Et la même règle de sûreté que pour l'anglais doit s'appliquer : **ne rien
supprimer, laisser le texte original non traduit.** Un bloc écarté n'a pas de
panneau peint dessus ; une erreur ne peut donc rien cacher.

---

## 7. Ce que je retiens du document

Justes et conservés : le diagnostic `JA→EN` (§1), les chiffres du modèle (§2), la
clôture de l'ancien modèle 59,5 Mo (§3), l'absence de `tiny` (§4), la nuance sur
les 83 % (§7), le refus de généraliser katakana→kanji (§9), les trois corpus
(§21), les catégories d'erreur (§24), le pipeline partagé (§19).

À corriger : le §20 (contradictoire avec le §19, et chiffré au jugé).

Une réserve sur le §21 : `JA-CONTROL`, « les bulles actuellement bien traduites »,
suppose qu'on sache lesquelles le sont. Cela demande une lecture manuelle du
corpus, bulle par bulle. C'est faisable — le corpus anglais a été construit ainsi
— mais c'est le poste le plus coûteux de toute la feuille de route, et le
document le présente comme une simple sélection.

---

## 8. Feuille de route mise à jour

| doc | chantier | statut |
|---|---|---|
| P0 | smoke test tablette | **à faire — seul point qui demande un build** |
| P1 | corriger le banc | **fait** (`07297a68`), dette d'architecture assumée |
| — | **garde sur les 39 règles Kansai non protégées** | **nouveau, trouvé par la mesure** |
| P2 | test de parité | à remplacer par des compteurs figés, cf. §3 |
| P3 | instrumentation | **commencée** — `rewrites.txt` nomme la table qui a réécrit |
| P4 | rejouer les 120 pages | **fait** |
| P5 | JA-ERRORS / JA-CONTROL | à faire ; coût de lecture manuelle à assumer |
| P6 | mesurer le Kansai | **fait, et corrigé** — 11 déclenchements, 6 fautifs, ramenés à 5 corrects |
| — | **prénoms en katakana réécrits en mots** | **nouveau, `ユメ → 夢`, non corrigé** |
| P7 | carnet et groupements | carnet mesuré : 1 réponse sur 598 |
| P8 | katakana avec corpus adapté | bloqué sur l'absence de corpus seinen/yakuza |
| P9 | fine-tuning ja→en | inchangé, et toujours les blocages de licence de l'anglais |
| — | crédits japonais | mesuré, 7/7, prêt à écrire |

**L'ordre que je propose**, différent du sien : la garde Kansai était le premier
chantier parce que c'était le seul défaut mesuré atteignant ce que l'utilisateur
lit ; **elle est faite**. Le suivant est le pipeline partagé (§19), qui empêche
le défaut du banc de revenir. Puis les prénoms en katakana, qui sont désormais le
seul défaut connu et non corrigé de la chaîne japonaise.

---

## 9. Ce que cette série de documents m'a appris sur ma propre méthode

Trois fois dans ce dossier, j'ai conclu trop vite à partir d'un corpus trop
petit :

- « aucun filtre furigana dans le code » — il existait, sur une branche ;
- « le glossaire katakana est inerte » — trois tomes ne suffisaient pas ;
- « le Kansai se déclenche une fois » — il se déclenche onze fois.

À chaque fois, la correction est venue du même geste : **élargir le corpus avant
de conclure**, pas raisonner mieux. C'est le reproche que je fais aux documents
sur leurs chiffres inventés, et il vaut aussi pour moi lorsque je mesure sur trop
peu. Le corpus est maintenant à 1 686 bulles ; c'est encore un seul genre, et il
faudra un titre où le katakana d'emphase est courant avant d'auditer les 257
règles du glossaire.
