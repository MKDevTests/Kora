### English

**Screens that stop waiting on the server**

Measured against a real server before touching anything: entering a library asked for three counts and waited on the slowest — 839 ms for the collections, 4.5 s for the genres, 6.9 s for the read lists — so the Genres / Collections / Read lists chips appeared seven seconds after the screen they belong to. Opening a series showed nothing where the volumes go for one to three seconds, and its Links tab resolved every link with its own request, all fired at once: over ten seconds, every visit.

What each screen already knew is now kept on disk and drawn immediately, then corrected behind:

| | Before | Now |
|---|---|---|
| Library tab chips | 6.9 s | **0–65 ms** |
| Keep reading | 5–10 s | **instant**, refreshed behind |
| Series volumes | 1–3 s | **7–14 ms** |
| Series links | 10 s+ | **15–240 ms** |
| Reading-order graph | 2–3 s | **19–31 ms** |

Three causes behind those numbers, and none of them was the network:

- **Requests were chained that had nothing to do with each other.** "Keep reading" started only after the counts refresh; the Links tab only after the volumes and the collections had both come back. They are independent, and now run as such.
- **Answers were forgotten between app starts.** The counts, the keep-reading row, the volumes, the links: all cached in memory only, i.e. empty exactly when the wait is most visible.
- **The genre count downloaded every tag of the library** — 3482 of them on a manga library — to end up with a number under thirty. It is read from the local index instead.

The reading-order graph was already cached, but the cache was keyed by the franchise's root, and finding that root meant walking the links first: up to twelve requests before a graph that was already on disk could be shown. It is now found by any series it contains.

**Suggestions follow the server**

The term index behind "Similar" and "For you" used to be a snapshot: built once per library, then stale until someone pressed "Re-analyse library". It now follows Komga's change events — a series whose metadata moved is re-read alone, batched behind a five-second quiet period, and past fifty changes at once the library is rebuilt instead, which costs fewer requests.

**Fixes**

- The book you just read moved to the head of "Keep reading" but stayed off-screen to the left — the one place you were about to look.
- Two build-level chores cleared while Gradle 10 is still far away: a deprecated property and a source directory in the old Android layout.

---

### Français

**Des écrans qui n'attendent plus le serveur**

Mesuré sur un vrai serveur avant de toucher à quoi que ce soit : entrer dans une bibliothèque demandait trois compteurs et attendait le plus lent — 839 ms pour les collections, 4,5 s pour les genres, 6,9 s pour les listes de lecture — si bien que les puces Genres / Collections / Listes arrivaient sept secondes après l'écran auquel elles appartiennent. Ouvrir une série laissait la zone des tomes vide pendant une à trois secondes, et son onglet Liens résolvait chaque lien par une requête distincte, toutes lancées d'un coup : plus de dix secondes, à chaque visite.

Ce que chaque écran savait déjà est désormais conservé sur disque et affiché immédiatement, puis corrigé derrière :

| | Avant | Maintenant |
|---|---|---|
| Puces d'une bibliothèque | 6,9 s | **0–65 ms** |
| Continuer la lecture | 5–10 s | **immédiat**, rafraîchi derrière |
| Tomes d'une série | 1–3 s | **7–14 ms** |
| Liens d'une série | plus de 10 s | **15–240 ms** |
| Graphe d'ordre de lecture | 2–3 s | **19–31 ms** |

Trois causes derrière ces chiffres, et aucune n'était le réseau :

- **Des requêtes sans rapport étaient enchaînées.** « Continuer la lecture » ne démarrait qu'après le rafraîchissement des compteurs ; l'onglet Liens qu'après le retour des tomes et des collections. Elles sont indépendantes, elles s'exécutent désormais comme telles.
- **Les réponses étaient oubliées d'un lancement à l'autre.** Compteurs, ligne de lecture en cours, tomes, liens : tout en mémoire seulement, donc vide précisément quand l'attente se voit le plus.
- **Le comptage des genres téléchargeait tous les tags de la bibliothèque** — 3482 sur une biblio manga — pour aboutir à un nombre inférieur à trente. Il est lu dans l'index local.

Le graphe d'ordre de lecture était déjà en cache, mais indexé par la racine de la franchise, et trouver cette racine imposait de parcourir les liens : jusqu'à douze requêtes avant de pouvoir afficher un graphe déjà présent sur le disque. Il est maintenant retrouvé par n'importe quelle série qu'il contient.

**Les suggestions suivent le serveur**

L'index de termes derrière « Similar » et « For you » était un instantané : construit une fois par bibliothèque, puis périmé jusqu'à ce qu'on pense à « Réanalyser la bibliothèque ». Il suit désormais les événements de Komga — une série dont les métadonnées bougent est relue seule, les changements sont groupés derrière cinq secondes de silence, et au-delà de cinquante d'un coup la bibliothèque est reconstruite, ce qui coûte moins de requêtes.

**Corrections**

- Le tome que vous veniez de lire remontait en tête de « Continuer la lecture » mais restait hors écran à gauche — le seul endroit où vous alliez regarder.
- Deux dettes de compilation soldées pendant que Gradle 10 est encore loin : une propriété dépréciée et un dossier de sources à l'ancien format Android.
