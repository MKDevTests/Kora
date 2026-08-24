# Audit de performance — 24 août 2026

Build `audit/perf` (`e1839e89`), tablette R52T604648B, serveur Komga au repos.
Sonde `SlowCallListener` à `SLOW_MILLIS = 0` (toutes les requêtes sont
journalisées) + traces `KoraPerf`.

**Aucun correctif n'a été codé.** Ce document est le préalable.

---

## 1. Le tableau, classé par gain attendu

| # | Surface | Symptôme mesuré | Cause | Coût du correctif | Risque |
|---|---------|-----------------|-------|-------------------|--------|
| 1 | Accueil / partout | La **même** requête met 5 197 ms en vague de 4 et 1 158 ms seule | 21 `Semaphore(4)` non coordonnés + `maxRequestsPerHost=8` : on sature Komga nous-mêmes | Moyen (un budget global) | **Moyen** — le débit total peut baisser, à valider en A/B |
| 2 | Bibliothèque | 6 requêtes référentielles à l'ouverture, ~4 s serveur | tags / genres / release-dates / age-ratings / publishers / languages chargés d'avance pour un panneau de filtres derrière un bouton | **Faible** — charger à l'ouverture du panneau | Faible |
| 3 | Recherche | 3 × `authors?unpaged=true`, jusqu'à **12 s** cumulés, rejouées à chaque changement de puce bibliothèque | Liste complète des auteurs préchargée pour l'autocomplétion | Faible — `getAuthors(search)` existe déjà | Moyen (change l'UX du filtre auteur) |
| 4 | Bibliothèque | `readlists?size=0` 1 989 ms + `collections?size=0` 2 081 ms | 4 s de serveur pour **deux nombres** affichés sur des onglets | Faible — différer ou cacher | Faible |
| 5 | Série | `collections/{id}/series?size=500` → **270 séries**, 1 764 ms | On télécharge toute la collection pour un rail qui en montre 6 | Faible — paginer à 20 | Faible |
| 6 | Démarrage | `GET /api/v1/libraries` appelé **2 fois** | Deux consommateurs, pas de coalescence | Trivial | Nul |
| 7 | Transverse | **17,14 %** de trames en retard, p99 = **300 ms** | Non identifié — demande un profil dédié | À chiffrer | — |
| 8 | Transverse | **408 Mo** de PSS (Graphics 127 Mo) | Probablement le cache bitmap du lecteur ; relié à l'OOM déjà documenté | À chiffrer | — |
| 9 | Lecteur image | **Rien à gagner côté client** | decode 0–1 ms, queue 1–4 ms : 100 % serveur | — | **Ne pas y toucher** |

---

## 2. Les mesures, surface par surface

### Démarrage à froid (2 passages)

```
am start -W  TotalTime: 1718 ms  /  1456 ms
31 requêtes HTTP dans les 30 premières secondes
Accueil complet à ~16 s
```

Vague 1 (4 étagères simultanées) :

```
POST /books/list?size=20&sort=readProgress.readDate,desc   server=7120ms
POST /books/list?size=20&...                               server=7127ms
POST /books/list?size=20&...                               server=7160ms
POST /books/list?size=20&...                               server=7268ms
```

Vague 2, quelques secondes plus tard, **même forme de requête** :

```
POST /books/list?size=20&sort=readProgress.readDate,desc   total=1191ms queue=2ms server=1158ms
```

C'est la mesure n° 1 du tableau : le serveur n'est pas lent, il est saturé
par nous. Vignettes derrière la vague : `queue=3150ms`.

Un livre est entièrement chargé depuis l'accueil (`/books/{id}`, `/pages`,
`/thumbnails`, `/thumbnails/{id}`) — à confirmer si c'est nécessaire.

### Accueil (retour)

```
home.shelf 'Keep reading'              3180ms
home.shelf 'Recently released books'   3189ms
home.shelf 'On deck'                   3210ms
home.shelf 'Recently added books'      3206ms
home.shelf 'Recently added series'      971ms
home.shelf 'Recently read books'        951ms
home.shelf 'Recently updated series'   1000ms
```

Même signature : 4 à ~3,2 s, puis 3 à ~1 s.

### Bibliothèque — ouverture (11 requêtes, ~5,1 s)

```
POST /books/list?size=20&sort=readProgress.readDate,desc  server=2679ms
POST /books/list?size=1&sort=readProgress.readDate,desc   server=2678ms
POST /series/list?size=50&sort=metadata.titleSort,asc     server=2669ms
GET  /tags?library_id=…                 server=837ms
GET  /genres?library_id=…               server=845ms
GET  /series/release-dates?library_id=… server=1005ms
GET  /age-ratings?library_id=…          queue=865ms  server=475ms
GET  /publishers?library_id=…           queue=868ms  server=469ms
GET  /languages?library_id=…            queue=1058ms server=283ms
GET  /readlists?library_id=…&size=0     server=1970ms
GET  /collections?library_id=…&size=0   server=2070ms
```

Traces : `library.series page=1` 2 744 ms, `library.keepReading` 2 768 ms,
`library.counts` 2 127 ms.

Les six référentiels (tags → languages) alimentent le **panneau de
filtres**. Il n'est pas ouvert. Les deux `size=0` ne servent qu'à écrire un
nombre sur les onglets « Collections » et « Listes ».

### Bascule de bibliothèque (Mangas → Light Novels)

12 requêtes. `library.counts` 2 491 ms, `library.series page=1` 2 064 ms,
`library.counts.genres` 2 457 ms. Acceptable.

### Recherche

```
GET /api/v2/authors?role=writer&unpaged=true     server=5345ms
GET /api/v2/authors?role=penciller&unpaged=true  server=5968ms
GET /api/v2/authors?role=editor&unpaged=true     server=748ms
```

À froid (serveur au repos) : 1 321 / 2 031 / 766 ms. Rejouées entièrement à
chaque clic sur une puce bibliothèque. Code : `SearchViewModel.loadAuthorNames()`.

### Série (8 requêtes, ~2 s)

```
series.collections.members n=1   2027ms  (270 items)
series.links                     1293ms
series.siblings                  1168ms
series.collections.list          1145ms
series.books page=1               900ms
```

L'appel coûteux est `GET /collections/{id}/series?size=500` → 1 764 ms pour
270 séries téléchargées et désérialisées.

### Tome (3 requêtes, ~390 ms) — sain

```
GET  /books/{id}/readlists                          server=176ms
POST /books/list?unpaged=true&sort=numberSort,asc    server=96ms
GET  /series/{id}                                   server=93ms
```

Note : le `unpaged=true` récupère **tous** les tomes de la série. Invisible
ici (10 tomes), à surveiller sur une série à 200 chapitres.

### Genre (drill-down)

`library.series page=1` 1 751 ms. Rien d'autre.

### Lecteur image — le verdict est net

Ouverture :

```
reader.open getOne        683ms
reader.open currentPages  679ms (203 items)
reader.open prev          677ms
reader.open CRITICAL      690ms
```

Régime établi, 27 pages tournées :

```
image.page.fetch    min=212   médiane=435   max=1272 ms
image.page.decode   min=0     médiane=0     max=1 ms
serveur (HTTP)      min=107   médiane=307   max=1151 ms
queue (HTTP)        min=1     médiane=1     max=4 ms
```

Le décodage est gratuit (libvips), la file d'attente est vide. **Tout le
temps est chez Komga.** Il n'y a aucun gain client à aller chercher ici.

### Transverses

```
TOTAL PSS   417 850 Ko  (408 Mo)   —  Graphics 127 Mo, Native 87 Mo, Java 55 Mo
TOTAL RSS   498 944 Ko

Total frames rendered: 4328
Janky frames:           742 (17,14 %)
p50 20ms   p90 36ms   p95 57ms   p99 300ms
Number Missed Vsync: 147   Number Slow UI thread: 289
```

---

## 3. Ce qui n'a pas été mesuré, et pourquoi

- **Lecture EPUB.** Le seul point d'entrée « Lire » que je savais atteindre
  de façon fiable sur l'écran de tome est voisin d'un bouton « marquer comme
  non lu » — que j'ai déclenché une fois par erreur (voir § 4). Je n'ai pas
  voulu recommencer sans ton feu vert. Les sondes `epub.ttsu.generate` et
  `epub.komga.getOne` sont en place, la mesure prend 2 minutes dès que tu me
  dis comment ouvrir un EPUB sans risque.
- **Collections** (écran dédié) : non atteint.
- **Cache froid.** Interdit par ta consigne (aucune suppression de données) :
  toutes les mesures sont *process-cold*, jamais *cache-cold*.

---

## 4. Deux points hors performance

### Un bug reproductible dans le lecteur — RETIRÉ, c'était moi

**Corrigé le 24/08 après mesure : il n'y a pas de bug.**

J'avais écrit que le lecteur restait bloqué sur « Il n'y a pas de tome
précédent », insensible à 44 taps et balayages. Une trace posée dans
`nextPage()`/`previousPage()` a montré ce qui se passait vraiment :

```
READERNAV prev spreads=203 requested=0 transition=BookStart    (tap x=200,  y=1200)
READERNAV prev spreads=203 requested=0 transition=BookStart    (tap x=1400, y=1200)
READERNAV next spreads=203 requested=0 transition=BookStart    (tap x=800,  y=2200)
READERNAV next spreads=203 requested=1 transition=null
```

Le mode de navigation tactile est **horizontal-split** : moitié **haute** =
page précédente, moitié **basse** = page suivante. L'abscisse ne compte
pas. Tous mes taps étaient à y=1200 sur un écran de 2560 — dans la moitié
haute, donc 44 demandes de reculer depuis la première page. Et les
balayages ne tournent aucune page : le pager a `userScrollEnabled = false`.

Un tap dans la moitié basse sort de l'écran de transition immédiatement.
Le lecteur faisait exactement ce qu'on lui demandait.

La leçon est la mienne : j'ai conclu « bloqué » d'une absence de réaction
sans vérifier ce que mes gestes déclenchaient. La trace a coûté deux
minutes et a tranché ; l'hypothèse aurait coûté un correctif faux.

### Une progression de lecture que j'ai effacée

Pendant la navigation, un de mes taps a déclenché :

```
DELETE /api/v1/books/0Q937AHKPJQBY/read-progress
```

C'est un tome de **404 Demons**. Le bouton se trouve dans la rangée
flottante, à la position que j'utilisais pour « Lire ». Je ne sais pas quel
était son état avant — je n'ai pas de mesure antérieure, donc je ne
l'invente pas. Vérifie la série ; si tu me dis quel tome et quel état, je le
remets.

---

## 5. Avant toute livraison

`SLOW_MILLIS = 0L` dans `SlowCallListener.kt` doit repasser à `2_000`.

---

## 6. Vérification du lot 1 sur tablette (24/08, après build)

Serveur **chargé** pendant cette session (12 113 ms sur une étagère
d'accueil ordinaire, deux sockets fermés) : les latences ci-dessous ne sont
pas comparables à celles du § 2. Seuls les **décomptes de requêtes** sont
concluants.

| Vérification | Avant | Après |
|---|---|---|
| `GET /api/v1/libraries` au démarrage | 2× | **1×** |
| Ouverture de bibliothèque | 11 requêtes | **6** (3 grille + 3 compteurs différés) |
| Écran Recherche, champ vide | 5 requêtes dont 3 `authors` | **2, zéro `authors`** |
| Changement de puce bibliothèque, champ vide | 3 `authors` | **0** |
| Écran de série au chargement | `collections/{id}/series?size=500` | **absent** |

Comportements confirmés :

- Le panneau de filtres déclenche ses six référentiels **à son ouverture**
  (470–1215 ms) et ses listes sont remplies.
- Changer de bibliothèque invalide bien les options : la réouverture du
  panneau interroge le nouvel `library_id`.
- L'onglet Collections d'une série charge ses membres au clic
  (2 737 ms, 270 séries) et un second clic ne rejoue rien.
- La saisie d'un terme dans Recherche relance bien `/authors?search=…`.
- Le sélecteur d'affichage 100 / 200 / 500 est intact.

Non vérifié : le coût réel d'`/authors` **avec** un terme de recherche. Il a
été mesuré à 10 187 ms ici, mais une liste ordinaire coûtait 8 579 ms au
même moment — impossible de séparer le terme de l'état du serveur.

---

## 7. Lot 2 — le plafond global de requêtes : RÉFUTÉ par la mesure

Construit, mesuré, retiré. La ligne n° 1 du tableau du § 1 tombe.

### Le balayage (8 démarrages à froid, plafond changé à chaud)

| plafond | 1re étagère | dernière étagère | `books/list` serveur (médiane) |
|---|---|---|---|
| 8 (actuel) | 2,50 s / 2,98 s | 5,99 s / 7,97 s | 2 316 / 3 885 ms |
| 4 | 3,00 s / 2,37 s | 9,61 s / 8,30 s | 4 674 / 4 627 ms |
| 2 | 2,37 s / 2,81 s | 7,48 s / 7,01 s | 766 / 595 ms |
| 1 | 2,45 s / 2,34 s | 8,97 s / 4,84 s | 459 / 444 ms |

L'hypothèse est **à moitié** vérifiée. Baisser le plafond rend bien chaque
requête beaucoup plus rapide au serveur — 3 885 ms à 8, 444 ms à 1, un
facteur neuf. Mais **le temps total ne bouge pas** : la dernière étagère
arrive entre 4,8 s et 9,6 s à tous les plafonds, plages entièrement
superposées. On ne fait que déplacer l'attente de leur serveur vers notre
file.

Chronologie à plafond 1, qui montre le mécanisme :

```
   0 s     request budget = 1
1,48 s     GET /users/me            queue=176ms  server=30ms
2,75 s     POST /books/list         queue=33ms   server=322ms
3,05 s     POST /books/list         queue=562ms  server=91ms
5,52 s     POST /books/list         queue=652ms  server=2452ms
 5,6 s     POST /books/list         queue=2036ms server=82ms
 6,4 s     POST /books/list         queue=2634ms server=63ms
```

### Et surtout : ça aggrave le cas qui a déclenché l'audit

Ouvrir un tome **pendant** le chargement de l'accueil, la plainte
d'origine. Tap à t+4,5 s après le lancement :

| plafond | file d'attente de la requête du tap | file, toutes requêtes (méd / max) |
|---|---|---|
| 8 | **2 ms** | 17 ms / 1 175 ms |
| 4 | 30 ms | 5 ms / 207 ms |
| 2 | **2 924 ms**, puis **2 282 ms** | 365 ms / 2 282 ms |

Ce n'est pas du bruit, c'est mécanique : le sémaphore est équitable, donc
une requête interactive qui arrive en cours de route passe **derrière** les
huit requêtes d'étagères déjà en file. Un plafond global n'a aucun moyen de
distinguer un geste de l'utilisateur d'un remplissage d'arrière-plan.

**Le plafond global est le mauvais outil.** Le problème n'a jamais été le
débit total, c'était qu'une requête interactive se retrouve coincée derrière
des requêtes de fond — et un plafond ne fait qu'ajouter un endroit de plus
où se coincer. Ce qu'il faudrait est une *priorité*, que la couche HTTP ne
sait pas exprimer : un `POST /books/list` est tantôt l'un, tantôt l'autre.

Code retiré. Le commit reste dans l'historique si on veut y revenir.

### Le vrai plancher du démarrage à froid, découvert au passage

**1,48 s s'écoulent avant la première requête HTTP.** Aucun plafond, aucun
serveur n'y change quoi que ce soit : c'est de l'initialisation locale. Et
la première étagère non vide arrive à 3,62 s, dont 1,48 s de plancher.

C'est probablement le meilleur reste à gratter sur le démarrage, et ce
n'est pas du réseau. À instrumenter avant d'y toucher.

## 8. La carte de statistiques — mesurée, puis démontée

### 8.1 La mesure

Treize étapes instrumentées une par une (`PerfTrace`), deux démarrages à froid :

| Étape | Passage 1 | Passage 2 |
|---|---|---|
| 9 étapes SQL locales, cumulées | 217 ms | 169 ms |
| `stats.api.lifetimeSeries` | 649 ms | 421 ms |
| `stats.api.recentSeries` | 519 ms | 284 ms |
| **`stats.api.lifetimeBooks`** | **5 558 ms** | **7 474 ms** |
| `stats.total` | **6 953 ms** | **8 358 ms** |

Deux de mes prédictions ont été fausses et sont corrigées ici :

1. J'avais annoncé « les étapes SQL coûtent ~240 ms chacune, beaucoup pour du
   SQLite local ». Elles coûtent 15 à 75 ms, 169 à 217 ms pour les neuf.
2. J'avais désigné `stats.streak` (`distinctDates(limit=365)`) comme principal
   suspect. Il coûte 16 ms.

Le local est innocent. **Une seule étape pèse 80 à 89 % de la carte** :
`POST /books/list?size=1` avec `readStatus=READ`, dont on ne lit que
`totalElements`. C'est un décompte de tous les livres lus du serveur, pour un
nombre qui change de 1 quand on finit un tome.

### 8.2 Ce qui a été fait

Trois choses, dans l'ordre de leur effet :

1. **La carte attend les étagères.** Elle partait en concurrence avec elles.
   Premier essai raté : le garde s'appuyait sur `LoadState.Success`, que
   `HomeViewModel` pose sur *la première* étagère qui répond — les stats
   partaient à 5,88 s alors que la dernière étagère arrivait à 12,38 s. Corrigé
   par un `shelvesSettled` dédié, posé après `awaitAll()`.
2. **Elle ne se recalcule plus à chaque composition** (`ReadingStatsCache`,
   6 h de vie). Un tome fini il y a cinq minutes peut ne pas encore être compté
   dans la carte ; l'écran complet, lui, recalcule toujours.
3. **Le décompte à vie n'est plus demandé au serveur qu'une fois par semaine.**
   La réponse est stockée avec l'instant où elle a été donnée, et entre deux
   demandes le total vaut *ce socle + les événements `COMPLETED` enregistrés
   depuis*. Ces événements sont exactement les tomes finis dans Kora : le
   nombre reste juste à la seconde près, pour une requête locale indexée au
   lieu d'un décompte serveur complet.

Ce que ça ne voit pas : un livre marqué lu ailleurs (interface web Komga, autre
client) depuis le dernier socle. L'écart est borné par les 7 jours et se corrige
au rafraîchissement suivant. C'est dit, pas caché.

### 8.3 La sauvegarde

Le socle voyage dans la sauvegarde (`lifetime_books_baseline` +
`lifetime_books_baseline_at`, par utilisateur Komga), parce qu'il n'est
dérivable de rien d'autre dans le fichier : l'historique par tome qu'il contient
est celui de Kora, et ne couvre que ce qui a été lu dans Kora. Sans lui, une
réinstallation restaurée afficherait un total à vie amputé, puis attendrait un
décompte serveur complet.

Deux pièges traités au passage :

- La fenêtre glissante de 365 jours somme les événements trop vieux dans un
  report de **pages**. Un socle qui vieillirait au-delà y aurait versé un nombre
  de **livres**. Il est donc exclu explicitement des deux branches.
- `ReadingEvent.Type.valueOf` sur une valeur inconnue faisait exploser tout le
  listing — donc toute la sauvegarde — dès qu'un Kora plus récent avait écrit un
  type que ce build ne connaît pas. La lecture ignore désormais la ligne au lieu
  de perdre le fichier.

Aucune migration : la ligne sentinelle vit dans la table existante, comme le
report de pages avant elle.

### 8.4 Vérification sur tablette (24/08, 19h16-19h20)

Serveur au repos ce soir-là : le décompte serveur coûtait 424 ms, pas les
5,5 à 7,5 s mesurées plus tôt. La variabilité 2-3× encore une fois — donc c'est
le **nombre d'appels** qui est vérifié ici, pas un gain en secondes.

| | Passage 1 (premier lancement) | Passage 2 (après force-stop) |
|---|---|---|
| `stats.api.lifetimeBooks` | 424 ms | **absent** |
| `stats.lifetimeBooks` | 455 ms | **8 ms** |
| `stats.total` | 1580 ms | 623 ms |

Et sur l'écran de statistiques complet ouvert 44 s après le passage 1 :
`stats.lifetimeBooks 42 ms`, toujours sans ligne `stats.api.*`.

Le garde tient dans les deux passages : dernière étagère à 19:16:24.268, stats
à 19:16:24.766 ; dernière étagère à 19:17:40.790, stats à 19:17:41.291.

Le nombre affiché ne bouge pas : **3525 tomes terminés** au passage 1 comme au
passage 2. La ligne en base, relue avec `run-as` :

```
LIFETIME_BOOKS_BASELINE | _booksbaseline_0P1J3G8PHB562
timestamp 2026-08-24T19:16:24.755 | count 3525 | user 0P1J3G8PHB562
```

Export de sauvegarde réel (`kora-backup-20260824-192016.json`, 40 001 octets),
relu au parseur :

```
lifetime_books_baseline       = 3525
lifetime_books_baseline_at    = 1787591784755   (= 19:16:24.755)
pages_read_lifetime_carryover = 0               (le socle n'a pas fui dedans)
reading_events                = 19, tous COMPLETED
"LIFETIME_BOOKS_BASELINE" présent dans le fichier : non
```

La sentinelle voyage donc dans ses deux champs et nulle part ailleurs, ce qui
est exactement ce que le test unitaire de round-trip vérifie — mais ici sur les
vraies données.

**WebView différée** : `WebViewFactory` n'apparaît dans aucun des deux
démarrages, ni pendant la navigation accueil → statistiques → réglages →
export. Kora ne charge plus le fournisseur Chromium du tout tant qu'aucune
WebView n'apparaît.

Deux points restent à la charge de Mathieu, parce qu'ils touchent sa
bibliothèque : finir un tome non lu (le socle doit passer à 3526 sans requête
serveur) et ouvrir un EPUB (l'appel différé doit toujours partir).
