# Banc OCR / traduction de page

Régler la fusion des bulles et le nettoyage du texte **sans tablette et sans build APK**.

Un tome se donne une fois ; ensuite chaque idée se teste en secondes au lieu d'un
build, une installation et une lecture.

## 1. Passer l'OCR sur un tome

```bash
python scripts/ocr-bench/run_ocr.py /chemin/tome.cbz
python scripts/ocr-bench/run_ocr.py dossier_de_pages/ --fast --limit 20
```

Accepte un `.cbz`/`.zip`, un dossier d'images, ou une seule image. Écrit un
`out/<page>.boxes.json` par page : les lignes détectées brutes, **avant** toute
fusion, plus la couleur échantillonnée autour de chaque ligne.

Utilise les mêmes fichiers ONNX PP-OCRv6 que l'app télécharge (attendus dans
`~/Downloads/rapidocr-v6-pack/`) et les mêmes réglages : `text_score` 0.6,
classifieur d'orientation désactivé, détecteur `small` par défaut.

**`--fast` (détecteur tiny) ne sert qu'aux mangas simples.** `small` est la
référence. Sur un comic, tiny perd précisément ce qui compte — le lettrage
artistique posé sur le dessin — et ne doit pas être mesuré comme un candidat au
remplacement.
La reconnaissance est le portage Python de la bibliothèque utilisée côté Android,
pas une réécriture.

## 2. Vérifier la géométrie

```bash
./gradlew :ocr-bench:test
```

Le module `ocr-bench` **compile directement les fichiers livrés** —
`OcrMergeUtils.kt`, `OcrElementBox.kt`, `TranslationTextUtils.kt` — au lieu d'en
copier la logique. Le banc ne peut donc pas diverger de ce qui tourne sur la
tablette sans cesser de compiler.

Il est volontairement hors du graphe de l'app : la cible desktop de
`komelia-domain:offline` ne compile pas dans ce fork (`DesktopOfflineModule`
référence un `PdfExtractor` qui n'existe qu'en `androidMain`), et tirer tout le
graphe transformerait une vérification de deux secondes en build.

## Ce que le banc ne teste pas

- **Le rendu à l'écran** : il donne les rectangles et les couleurs de panneau,
  pas la mise en page finale.
- **Le partage d'une phrase entre ses bulles** : `bench_en.py` compare la
  phrase envoyée au moteur, pas les morceaux redistribués à chaque bulle. Une
  phrase groupée peut être juste et mal découpée à l'écran.

Ces deux points restent une vérification sur tablette. Tout le reste — quelles
lignes forment une bulle, dans quel ordre elles se lisent, la taille du panneau
peint, les onomatopées, les mots coupés en fin de ligne, les honorifiques, et
depuis `bench_en.py` **le français lui-même** — se règle ici.

> Corrigé le 19/08/2026 : cette section affirmait que le banc ne donnait pas le
> français. C'était vrai du temps de ML Kit ; `bench_en.py` fait tourner le vrai
> binaire Bergamot depuis, et consulte le carnet d'expressions comme le lecteur.

## Le carnet d'expressions dans le banc

`bench_en.py` donne le dernier mot au carnet, exactement comme le lecteur. Le
rapport affiche chaque réponse **en face de ce que Bergamot aurait rendu** :

```
  phrase book answers, against what Bergamot would have returned:
    src   Shove off!
    book  Fiche le camp !
    was   Pellez !
```

C'est la seule vue qui distingue une entrée utile d'une entrée inutile — ici
« Pellez ! » montre que le moteur avait lu *shovel*, alors que `What's up?`
donne le même résultat des deux côtés et ne gagne rien.

Ça n'a pas toujours été le cas : le Kotlin écrivait ses réponses dans
`answers.txt` et le rapport ne lisait que la sortie de Bergamot. Un « 0 changed »
après un ajout d'entrées ne voulait donc rien dire.

## Le corpus d'erreurs

`translation_errors.tsv` recense les bulles sorties fausses, avec le compte-rendu
que le pipeline fait de lui-même. Il s'alimente depuis la tablette :

```bash
adb logcat -d -s KoraTranslate > session.txt
python scripts/ocr-bench/collect_errors.py session.txt
```

Le script remplit les neuf colonnes que le log connaît (`source`, `repairs`,
`grouped_source`, `phrasebook`, `seam`, `output`…) et laisse `error_category`
vide. **Une ligne sans catégorie est une bulle non relue, pas une bulle
correcte.** Les colonnes remplies à la main ne sont jamais écrasées.

Catégories : `A` expression figée, `B` fragment mal groupé, `C` construction
ratée par le moteur, `D` source cassée par l'OCR, `E1` casse ou nom propre,
`E2` registre.

`expected_fr` ne se remplit que là où une seule bonne réponse existe — `A` et
`D`. Pour `C` et `E2`, plusieurs formulations conviennent : les mettre dans
`acceptable_fr`, séparées par `|`.

Deux pièges : un build antérieur à l'instrumentation n'écrit pas les lignes
`page … block …`, et une page servie par le cache de scan (en mémoire) n'écrit
rien du tout — relancer l'app avant de lire.

## Ajouter un cas

Quand une page se traduit mal, mettre ses lignes dans
`ocr-bench/src/test/kotlin/snd/komelia/image/OcrMergeUtilsTest.kt` (les
coordonnées sortent de `run_ocr.py`), vérifier que le test échoue, puis corriger.
Un cas ajouté est un cas qui ne peut plus revenir.

## Japonais : le pivot bat le direct (mesuré, 15/08/2026)

Les deux documents de conception recommandent `opus-mt-ja-fr` **direct** comme
moteur V1. Mesuré sur les mêmes phrases (`ja-probe.txt`), il ne gagne pas.

`run_pivot_ja.sh` fait JA→EN→FR avec le moteur qui ship déjà. Le pack `ja-en`
existe chez Mozilla (v2.1, 52 Mo), au même endroit et au même format que
`en-fr` : **un pack de plus, aucun nouveau runtime, rien à convertir ni à
héberger.**

Le direct a été testé à armes égales — `beam 1` comme Bergamot, puis `beam 4`
pour lui laisser sa meilleure chance. Deux comportements disqualifiants
survivent aux deux réglages :

    仕方ないだろ。   -> « Tu n'as pas le choix, Nimah. »   nom propre inventé
    もう十分だ。     -> « C'est assez, Nimah. »            le même fantôme
    まさか……        -> « Non, non, non, non, non, ... »   répétition dégénérée

Un nom de personnage fabriqué dans une bulle est pire qu'une traduction plate :
il est faux et il a l'air sûr de lui. Le pivot, lui, échoue à plat
(しょうがねぇな → « Je ne peux pas l'aider ») — c'est exactement le défaut
structurel que le document annonçait, et c'est le moins grave des deux.

À armes égales le score est serré (4 partout sur 15). Ce qui tranche, c'est
que le direct est un *teacher* fp32 de 76M paramètres — la conversion int8
Bergamot ne peut que le dégrader — contre les 31M du student que le pivot
utilise déjà.

**Non validé** : 15 phrases choisies à la main, pas de vraies bulles. L'étape
suivante est l'OCR japonais sur un tome réel.
