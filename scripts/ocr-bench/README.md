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

- **La traduction elle-même** : ML Kit Translate est Android uniquement. Le banc
  donne le texte source des blocs, pas le français.
- **Le rendu à l'écran** : il donne les rectangles et les couleurs de panneau,
  pas la mise en page finale.

Ces deux points restent une vérification sur tablette. Tout le reste — quelles
lignes forment une bulle, dans quel ordre elles se lisent, la taille du panneau
peint, les onomatopées, les mots coupés en fin de ligne, les honorifiques — se
règle ici.

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
