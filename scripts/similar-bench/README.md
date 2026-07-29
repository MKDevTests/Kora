# Similar-series bench

Tunes the "Similar series" weights against the real library, without building the
app. One pass over the server, then every experiment is instant and local.

Python 3.10+, standard library only.

```bash
cd /mnt/c/Users/mathi/Downloads/Dev/Sipurra-Myversion/Sipurra-myversion/scripts/similar-bench
export KOMGA_URL=http://<serveur>:25600 KOMGA_USER=<login> KOMGA_PASSWORD=<mdp>
python bench.py fetch --library BD
python bench.py show --library BD --series "Blacksad" --series "Sillage"
```

`fetch` writes `index_<library>.json` (terms only — no summaries, no covers) and
is the only command that touches the server. Everything after works offline.

## Reading the output

```
   1. 0.512  Le Scorpion                          a:desberg, g:aventure, t:vatican
```

The score is a cosine (0 to 1); the terms after it are why the series was picked,
strongest first. If the reasons look like `g:action, t:bd` — generic terms
everything shares — that setting is producing noise, not suggestions.

## Changing the weights

```bash
python bench.py show  --library BD --series "Blacksad" --genre 1.4 --tag 0.3
python bench.py sweep --library BD --series "Blacksad" --param genre=0.6,1.0,1.4
```

Flags: `--author --genre --tag --book-tag --publisher --max-per-author --limit`.
The defaults mirror `SimilarityWeights.kt`; once a setting reads well on series
you know, port the numbers there.

## Keeping the bench honest

`kora_similar.py` is a port of the Kotlin engine, and a bench that scores
*almost* like the app is worse than no bench — it sends the tuning the wrong way
without ever looking wrong. Both sides therefore score `fixture.json`, and
`SimilarityEngineFixtureTest` fails the build if the results differ.

After changing scoring on **either** side:

```bash
python bench.py emit-expected
```

then, from the repo root:

```bash
./gradlew :komelia-domain:core:testDebugUnitTest --tests "*SimilarityEngineFixtureTest*"
```

`index_*.json` files are local scratch data and are not committed.
