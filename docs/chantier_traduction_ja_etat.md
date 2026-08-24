# Traduction japonaise — état du chantier

Date : 19/08/2026. Tous les chiffres de ce document sont mesurés, jamais estimés.
La source est `scripts/ocr-bench/corpus_ja.tsv` (versionné) et les 7 volumes du
banc `_bench-ja` (1 686 bulles, 1 646 uniques).

---

## 1. Où on en est

**72,0 % des bulles japonaises sont correctes** (69,3 % avant les correctifs du
19/08).

| Verdict  | avant | après |    |
|----------|-------|-------|----|
| correct  | 104   | **108** | **69,3 % → 72,0 %** |
| dégradé  |  21   |  20   |    |
| faux     |  25   |  22   |    |

**Le progrès est démontré, pas supposé.** A/B apparié sur les mêmes 198 bulles :

| avant | après | n |
|---|---|---|
| faux | correct | **5** |
| dégradé | correct | 2 |
| faux | dégradé | 2 |
| *toute régression* | | **0** |

9 gains, 0 régression, McNemar p = 0,002. C'est exactement pourquoi l'apparié
était nécessaire : +2,7 points bruts se noient dans une marge de ±7,4, alors que
9 transitions favorables et aucune défavorable ne se noient pas.

Mesuré sur 150 bulles tirées au hasard proportionnellement dans les 7 volumes,
lues une par une. C'est le premier chiffre global du chantier : jusqu'à ce jour,
chaque décision se prenait sur un mécanisme isolé (« la table Kansai tire 11 fois
dont 6 à tort ») sans jamais savoir ce que ça représentait.

**Marge d'incertitude** : 69,3 % sur n=150 donne un intervalle de confiance à
95 % de **±7,4 points**. Une variation de 69,3 % à 70,5 % sur un nouveau tirage
ne démontre donc rien. La bonne méthode pour juger un correctif est l'**A/B
apparié** : les mêmes bulles avant et après, en regardant les transitions. Pour
resserrer le taux global à ±5 points il faudrait 323 bulles, à ±4 points 504.

**Limite à connaître** : les 198 verdicts sont un jugement unique, le mien. Ils
sont versionnés ligne par ligne, donc contestables, mais ce n'est pas une vérité
terrain indépendante.

---

## 2. La chaîne, et où ça casse

```
image ──OCR──▶ japonais ──tables──▶ japonais ──ja-en──▶ anglais ──en-fr──▶ français
               PP-OCRv6             kansai/katakana     Bergamot           Bergamot
                                    phrase book         41,9 Mo            (livré)
```

Répartition des 46 échecs de l'échantillon :

| Étage       |  n  |   %    | Commentaire |
|-------------|-----|--------|-------------|
| **ja→en**   | 27  | 58,7 % | le gros morceau |
| **OCR**     | 13  | 28,3 % | dakuten traité, reste homoglyphes + furigana |
| **en→fr**   |  5  | 10,9 % | **la fiche le donnait à « ~0 » : faux** |
| crédits     |  1  |  2,2 % | rangé, voir §4 |

### La strate qui décroche

| Strate       | % correct (tirage aléatoire) | % correct (tirage difficile) |
|--------------|------------------------------|------------------------------|
| long         | 83,3 %                       | 75,0 % |
| tiny         | 73,7 %                       | 83,3 % |
| dialecte     | 75,0 %                       | 58,3 % |
| plain        | 71,4 %                       | — |
| **katakana** | **43,8 %**                   | **25,0 %** |

Le katakana est deux fois plus mauvais que le dialogue ordinaire, dans les deux
tirages. **20,5 % de toutes les bulles** (337 sur 1 646) contiennent un run
katakana d'au moins 3 caractères.

---

## 3. Ce qui vient d'être livré

### Réparation du dakuten — commit `6d1d3f9b`

L'OCR perd les deux petits traits (゛) et le petit rond (゜). Sur `パーティー` — le
groupe d'aventuriers, nom le plus courant du genre — **il se trompe plus souvent
qu'il ne réussit** : `ハーティー` 14 fois contre `パーティー` 11.

Les dégâts n'apparaissent qu'au bout de la chaîne et ne ressemblent pas du tout à
un problème d'OCR :

| lu par l'OCR | rendu en français |
|---|---|
| `アレンのハーティー` | « Allen Est Copieux » |
| `ユイのハーティーで支援職をしている` | « J'ai les trente heures du moi » |
| `より適切なサホート` | « un sakhot plus approprié » |

**Le piège** : la règle naïve « ajouter les diacritiques manquantes » est fausse
une fois sur quatre. `ハーレム` (harem) est correct **sans** marque (4 occurrences
contre 1 pour `バーレム`), et l'OCR n'en perd pas seulement — il en **invente de
fausses**. C'est un lexique court qui arbitre, refusant tout mot dont le
squelette est lui-même un mot : `ボール` renommerait `ホール` (un hall), `ビール`
renommerait `ヒール` (heal, présent dans les 7 volumes), `プレイヤー` renommerait
`フレイヤー`, qui est un personnage.

Résultat : **15 gains, 0 régression** sur les 16 bulles touchées.

### La limite que ça révèle

16 bulles sur 1 686 = **0,9 %**. Même parfaite, cette correction ne peut pas
déplacer le taux de base de plus d'un point.

**C'est le principal enseignement du chantier** : les correctifs ponctuels ne
bougeront pas l'aiguille. Tout chantier futur doit être jugé d'abord sur le
nombre d'**erreurs qu'il corrige** — voir §5, où cette question posée en
« bulles touchées » plutôt qu'en « erreurs corrigées » avait produit un
classement faux.

---

## 4. Pistes fermées — ne pas y revenir

Chacune a été mesurée et a échoué. Listées pour éviter de refaire le travail.

| Piste | Mesure | Verdict |
|---|---|---|
| Modèle ja-en plus gros (58 Mo desktop vs 43 Mo Android) | 12 gains / 7 pertes | pas une solution |
| Traduction directe ja→fr (`opus-mt-ja-fr`) | hallucine des noms propres | rejetée, le pivot gagne |
| Grouper les bulles par lot pour donner du contexte | aucun effet | réfutée |
| Monter la résolution OCR | 83 % du texte gagné est du **furigana** | inutile |
| Segmentation ML des bulles | ~770 ms/page, ne parallélise pas | abandonnée |
| Crédits japonais (`原作`, `作画`…) | 24 phrases = **1,4 %** | rangé, non prioritaire |
| Réécrire `パーティー` → `一行` | « d'autres **lignes** », « en une seule ligne » | rejetée (ambigu) |
| Réécrire `パーティー` → `仲間` | 7 gains / 3 pertes sur 13 phrases | pas assez net, à revoir |
| **Beam search 4 sur ja→en** | annotation aveugle sur 149 bulles : **35 mieux / 28 moins bien / 86 égales**, binomial **p = 0,45** | **fermée** — indiscernable du hasard |

---

## 5. Chantiers ouverts, classés

Filtre appliqué : **combien d'erreurs réelles le chantier corrige-t-il ?** — et
non plus « combien de bulles il touche », qui surestimait les noms propres.

Comptage des 65 échecs annotés (les deux piles), par ce qui cause l'échec :

| Cause de l'échec | n | Chantier |
|---|---|---|
| **terme de genre mal compris** (`パーティー`, `ラスボス`, `モブライフ`, `ハーレム`, `ガッツリ`, `バレバレ`, `魔力`, `流石`, `器量`) | **~12** | glossaire de domaine |
| **nom propre traduit ou perdu** (`シリカ`→silice, `マリーダ`→Mary, `アルベルト` disparu) | **3 à 5** | noms propres |

**C'est un renversement par rapport à la première version de ce document.** Les
noms propres y étaient chantier 1 sur la foi des 337 bulles portant un run
katakana — mais un run katakana n'est pas un nom, et la mesure le confirme : le
taux d'erreur est de 41,4 % avec run katakana contre 28,1 % sans, un écart réel
mais bien plus faible que ce que « 20,5 % du corpus » laissait espérer.

L'audit des 6 échecs imputés à `en→fr` enfonce le clou : **un seul** est un nom
propre (`シリカ`). Les autres sont un terme de genre (`パーティー`→« parti »), un
faux ami (`commissioned`→« commandés »), une nuance (`Warm`→« Chaleureux »), un
mot anglais inconnu du modèle (`gutsy`) et une segmentation d'honorifique
(`Mr. Haku's chest`→« M. La poitrine de Haku »). La protection des noms n'aurait
absorbé qu'un sixième de ce poste.

### 1. Glossaire de termes de genre — recommandé

- **Cible** : ~12 des 65 échecs annotés
- **Écriture** : la majorité des cas sont en **katakana**, donc traitables par le
  mécanisme existant ; `魔力`, `流石`, `器量` sont en kanji et demandent un second
  mécanisme
- **Méthode** : piloté par la fréquence, jamais par une liste théorique. Pour
  chaque terme candidat : combien d'occurrences, combien d'erreurs, combien de
  gains, combien de pertes. `魔力` (fréquent, souvent faux) avant `流石`
  (2 occurrences).
- **Réserve** : réécrire en japonais peut introduire une nuance fausse. Le test
  `パーティー`→`仲間` donne 7/3 et `一行` est à rejeter (ambigu avec « une ligne
  de texte »). Chaque terme se mesure séparément.

### 2. Protéger les noms propres — rétrogradé

- **Cible réelle** : 3 à 5 des 65 échecs annotés — pas les 337 bulles katakana
- **Symptômes** : `シリカ` → « magicien de **silice** », `マリーダ` → « Mary »,
  `アルベルト` purement et simplement disparu de la phrase
- **Le signal de détection** : l'**honorifique japonais**. 17 runs katakana du
  corpus sont suivis de `さん`/`様`/`君`/`ちゃん`/`殿`, dans 56 bulles, et ils sont
  tous des noms : `ジン`(15), `アルベルト`(9), `マリーダ`(9), `ハク`(5), `マーリン`,
  `オラリア`, `ローサ`, `ユイ`… C'est un détecteur à haute précision, immédiat, et
  bien plus simple qu'un score multi-critères.
- **Principe** : geler les noms en jetons avant `ja→en`, les restaurer après
  `en→fr` — le moteur ne peut alors ni les traduire ni les déformer
- **Outillage** : `scripts/ocr-bench/run_placeholders.py` existe déjà
- **Questions ouvertes** : détecter un nom propre **sans dictionnaire**, et
  savoir ce que les jetons coûtent au moteur (un jeton opaque peut casser la
  syntaxe de la phrase)

### 2. Glossaire de termes de genre

- **Cible** : ja→en, 58,7 % des échecs — le plus gros gisement
- **Symptômes mesurés** : `魔力` → « yam » (igname), `流石` lu littéralement →
  « pierre de dérive », `器量` → « niveau de force », `ラスボス` → « Russs »,
  `モブライフ` → « vie de foule »
- **Mécanisme** : `JapaneseKatakanaGlossary` couvre les termes **en katakana**
  (`パーティー`, `ラスボス`, `モブライフ`…), soit la majorité des cas mesurés. Il ne
  peut **pas** traiter `魔力`, `流石`, `器量` : son appariement se fait sur des
  runs katakana (`katakanaRunEnds`), donc le kanji lui est invisible. Ces 3 cas
  demandent un second mécanisme, à ne pas greffer sur celui-ci — l'emphase
  katakana et le vocabulaire de genre sont deux phénomènes différents.
- **Réserve** : le levier modèle est fermé (voir §4), et le seul test fait
  (`仲間`) donne 7/3 — réel mais pas net. Plusieurs sessions de constitution et
  de validation.

### 3. Homoglyphes chinois + tiret ASCII

- **Cible** : le reste de l'OCR (~20 % des échecs)
- **Symptômes** : `ペ-ジ` (tiret ASCII au lieu de `ー`) → « 127Peg » ;
  `年龄:21 性别:女` (caractères chinois simplifiés) → non traduit ; furigana
  résiduel (`世かい`, `世んいん`) que le filtre laisse passer
- **Nature** : petit, sûr, rapide. Du polish, pas une priorité.

---

## 6. Méthode — les règles qui ont émergé

1. **Un seul pipeline.** `TranslationInput.prepare()` est appelé par le lecteur
   ET par le banc. Quand il y en avait deux, la copie du banc appliquait les
   règles latines au japonais et jetait 203 blocs sur 210 — le banc tournait, le
   rapport était plein, les tests passaient.
2. **Deux tirages, jamais un seul.** Un tirage aléatoire donne le taux ; un
   tirage ciblé diagnostique. Les mélanger fait dire au taux quelque chose sur
   l'échantillonnage plutôt que sur la traduction.
3. **Mesurer avant de coder.** La règle du dakuten paraissait évidente et était
   fausse une fois sur quatre. Le prototype Python sur le corpus l'a montré en
   dix minutes, avant la moindre ligne de Kotlin.
4. **Un refactor se vérifie au bit près.** Sortie identique, ou différences
   justifiées ligne par ligne.
5. **Juger un chantier au volume d'abord** — mais au volume d'**erreurs
   corrigées**, pas de bulles touchées. C'est la confusion qui avait mis les
   noms propres en tête. Et le coût d'implémentation entre dans l'arbitrage :
   un correctif sûr de 2 % livrable en une heure passe devant un chantier de
   20 % qui prend trois jours et n'est précis qu'à moitié.
6. **Une matrice de transitions, pas un solde.** `corrigé − dégradé` perd de
   l'information : faire passer 10 bulles de « faux » à « dégradé » est un vrai
   progrès pour le lecteur alors que le solde est nul. Compter séparément :

   | avant | après | |
   |---|---|---|
   | faux | correct | gain majeur |
   | faux | dégradé | gain |
   | dégradé | correct | gain |
   | correct | dégradé | régression |
   | correct | faux | **régression majeure** |

---

## 7. Pièges de mesure rencontrés

- **`KORA_BENCH_VERTICAL` oubliée** → 45 pages deviennent 3 blocs,
  `sentences.txt` contient deux lignes de romaji, **et le test rapporte un
  succès**. Le garde-fou anti-effondrement exigeait 30 blocs pour juger : la
  panne qui détruit les blocs était précisément celle qu'il ne pouvait pas voir.
  Corrigé dans `6d1d3f9b`.
- **Le moteur n'est pas reproductible au batch près** : lors de l'A/B, une bulle
  dont le japonais était rigoureusement identique a rendu un français différent
  (« bonne beauté blonde » → « bonne grande beauté »). Bergamot regroupe ses
  mini-batches selon le contenu du fichier, donc deux exécutions n'alignent pas
  les mêmes phrases. Conséquence : une différence isolée n'est pas une preuve,
  seule une transition de verdict en est une.
- **`--rerun-tasks` obligatoire** : sans lui Gradle rapporte UP-TO-DATE et un
  succès périmé.
- **Mesurer sur le mauvais fichier** : `sentences.txt` est le texte **après**
  réécriture des tables. Pour savoir si une table a tiré, il faut `report.txt`.
- **Corpus trop petit** : j'ai conclu « le glossaire katakana est inerte, 0/257 »
  sur 3 volumes ; sur 7 il tire 6 fois. C'est exactement l'erreur reprochée aux
  documents de conception.
- **La sortie filtrée de Gradle ment** : le message d'une assertion va dans le
  XML (`ocr-bench/build/test-results/test/*.xml`), pas dans stdout.

---

## 8. Prochain pas

Chantier 1 (glossaire de termes de genre), piloté par la fréquence : établir
pour chaque terme candidat le nombre d'occurrences, d'erreurs, de gains et de
pertes avant d'en ajouter un seul.

Le chantier « noms propres » attend, et son entrée en matière n'est plus un
détecteur mais un **prototype de jetons** : rien ne garantit qu'un `__NAME_0__`
traverse Marian intact, ni qu'il ne dégrade pas la syntaxe autour. Le critère
n'est pas « le jeton est retrouvé » mais « la phrase française est meilleure ».

**Chaque chantier se mesure désormais contre le corpus : 69,3 % → ?**
