# Les trois tables japonaises

Ce dossier tient les **sources** ; ce qui part dans l'APK est une version
réduite sous `komelia-ui/src/commonMain/composeResources/files/`.

| source | ressource embarquée | lue par |
|---|---|---|
| `katakana-master.json` | `files/japanese/katakana.json` | `JapaneseKatakanaGlossary` |
| `kansai-master.json` | `files/japanese/kansai.json` | `JapaneseKansaiNormaliser` |
| `expressions-master.json` | `files/phrasebook/ja-fr.json` | `JapanesePhraseBook` |

L'ordre dans le lecteur est imposé et il compte : réparation OCR
(`JapaneseOcrRepair`) → dialecte → katakana → expressions figées → traduction.
`ワシャ` doit devenir `俺は` pendant que c'est encore une suite de katakana, et
`ニ丁` doit redevenir `二丁` avant que quoi que ce soit d'autre le lise.

## Régénérer les ressources

```bash
python - <<'PY'
import json, io
d = json.load(open("scripts/japanese/katakana-master.json", encoding="utf-8"))
keep = [{"expression": x["expression"], "ja_normal": x["ja_normal"]}
        for x in d["entries"] if x.get("ja_normal")]
io.open("komelia-ui/src/commonMain/composeResources/files/japanese/katakana.json",
        "w", encoding="utf-8").write(
    json.dumps({"version": d["metadata"]["version"], "entries": keep},
               ensure_ascii=False, separators=(",", ":")))

d = json.load(open("scripts/japanese/kansai-master.json", encoding="utf-8"))
keep = [{k: v for k, v in x.items() if k in ("expression", "ja_normal", "guard")}
        for x in d["entries"]]
io.open("komelia-ui/src/commonMain/composeResources/files/japanese/kansai.json",
        "w", encoding="utf-8").write(
    json.dumps({"version": d["metadata"]["version"], "entries": keep},
               ensure_ascii=False, separators=(",", ":")))
PY
```

## Le kansai-ben

`kansai-master.json` part du fichier de Mathieu
(`japonais_kansai_ben_normalisation_v0_1.json`, 147 entrées + 24 règles) et y
ajoute ce que la mesure a réclamé. Entrées et règles sont fusionnées en **une**
table triée du plus long au plus court : c'est ce tri qui fait gagner `やないかい`
sur `やな` sans avoir à écrire une priorité à la main.

Mesuré sur les 231 bulles distinctes de 50 pages lues : **19 bulles touchées, 9
franchement meilleures, aucune régression franche**, dont quatre inversions de
sens remises à l'endroit (`見つからへんど` répondait « peut être trouvé »).

Les cinq corrections apportées au fichier d'origine, chacune avec la bulle qui
l'a exigée, sont dans `metadata.added` et `metadata.veto` du master. La plus
importante : `ちゃう` seul est la contraction de `~てしまう` bien plus souvent
que `違う`, donc il ne tire qu'en tête de bulle ou après `ん`.

## Les homoglyphes OCR

`JapaneseOcrRepair` n'a pas de fichier : deux paires tiennent dans le code.
Neuf paires visuellement identiques ont été essayées sur 722 lignes reconnues
de deux tomes ; sept n'ont produit **aucune** réparation correcte et une
réparation fausse (`3カ月` → `3力月`). Ne pas les remettre sans les remesurer.

Seuls `expression` et `ja_normal` partent. `translation_fr` reste ici : c'est
une glose de contrôle, **le moteur ne doit jamais la lire**. Une version du
fichier portait la réécriture dans ce champ pour une catégorie, et la lire a
injecté le mot français « je » dans quatre phrases japonaises.

## Décider un `ja_normal`, sans deviner

`decide.py` place chaque candidat et son katakana d'origine dans la **même**
phrase porteuse, passe les deux dans le pivot embarqué, et ne garde le candidat
que si le katakana laisse fuir du romaji dans l'anglais intermédiaire et que le
candidat non.

```bash
cd scripts/japanese && python decide.py probe
./scripts/ocr-bench/run_pivot_ja.sh decide-probe.txt /tmp/fr.txt   # garde l'anglais
cp /tmp/pivot-en.txt decide-en.txt
python decide.py apply
```

Sur 273 candidats : **73 gardés, 7 rejetés, 193 déjà corrects**. Les 193 sont le
résultat qui compte — le moteur traduit déjà les trois quarts de la table, et
les réécrire dégrade. `ホント` réécrit en `本当` transforme « c'est vrai » en
« Vraiment troublé ».

## Les quatre pièges, tous payés une fois

1. **Phrase porteuse agrammaticale.** `デカイだと思う` n'est pas du japonais et
   fabriquait un tiers des fausses alertes. Une phrase par catégorie, correcte.
2. **Contractions anglaises.** `i` n'est pas dans `files/lexicon/en.txt`, donc
   `I'm` comptait comme une fuite et faisait rejeter des gains réels.
3. **Le japonais qui traverse.** `ぐにゃぐにゃ` ressort tel quel : pas de romaji,
   pas une traduction non plus. Testé aussi.
4. **La romanisation qui est un mot anglais.** `メンド` sort en « Mend. » et
   passe le test. Ces cas-là ne se voient que dans le corpus, jamais dans la
   phrase porteuse — d'où `FROM_CORPUS`, forcé depuis le log du lecteur.

## Le banc n'est pas la tablette

Mêmes paramètres de décodage (vérifié dans `translator.cpp` : `beam-size 1`,
`int8shiftAlphaAll`), même composition de lot (testé) — et pourtant
`マズイんじゃないっスか！？` donne « N'est-ce pas mauvais ? » au banc et
« C'est pas mal » sur la tablette. Il reste le binaire : noyaux int8 x86 contre
ARM.

**Conséquence** : le banc tranche les fuites de romaji, qui sont du vocabulaire
manquant. Il ne tranche pas une nuance. Toute entrée dont le verdict tient à un
mot près se vérifie sur l'appareil.
