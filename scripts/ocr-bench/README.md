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
