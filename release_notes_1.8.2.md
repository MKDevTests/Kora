# Kora 1.8.2

Opening a library used to send eleven requests. Six of them filled a panel
that was closed. This release is what an exhaustive performance audit found,
and — just as usefully — what it disproved.

## Four fan-outs nothing on screen was waiting for

**A library screen no longer loads its filter panel.** Genres, tags, release
dates, age ratings, publishers, languages: six lookups, about four seconds
of server, and nothing outside the filter panel ever reads them. They cost
the screen twice — once for themselves, and once because the three queries
that actually paint the grid were queueing behind them, measured waiting up
to 1058 ms for a connection. The panel now fetches them when it opens, which
is the only moment they matter.

```
library open:  11 requests  ->  6
```

**A series no longer downloads its collections.** Belonging to a collection
meant fetching every series in it — 270 of them, 1764 ms — on a tab that was
never opened. That now happens on the tab, and a second visit costs nothing.

**Search no longer dumps every author in the library.** With nothing typed,
the Authors tab asked for the complete author list of each counted role,
unpaged, three requests: 1.3 s + 2.0 s + 0.8 s on an idle server, 5.3 s +
6.0 s + 0.7 s on a busy one — replayed in full every time you tapped a
library chip. An empty query has nothing to say about authors. Type
something and the search works exactly as before.

```
search screen, empty:  5 requests  ->  2
library chip change:   3 author requests  ->  0
```

**And the startup asked for the library list twice**, for a number it
already had in memory.

## What was disproved

The audit's headline finding was that the same request costs four to five
times more in a burst than alone — 5197 ms in a wave of four, 1158 ms on its
own seconds later, with no waiting on our side either time. The obvious
conclusion was to cap how many requests Kora has in flight at once.

So that cap was built, made adjustable without rebuilding, and swept across
four values over eight cold starts. Half the hypothesis held: lowering the
cap does make each request dramatically faster at the server, from 3885 ms
down to 444 ms, a factor of nine.

The other half did not. **The total never moved.** The last shelf landed
between 4.8 and 9.6 seconds at every setting, ranges fully overlapping. The
wait was not removed, only relocated — out of the server and into our own
queue.

Worse, it damaged the very thing that started the audit. Tapping into a
volume while the home screen is still loading:

| in-flight cap | how long that tap waited |
|---|---|
| current (8) | 2 ms |
| 4 | 30 ms |
| 2 | 2924 ms, then 2282 ms on a repeat |

That is not noise, it is arithmetic: the queue is fair, so a tap arriving
mid-load falls in behind the eight shelf requests already in line. A global
cap cannot tell your finger from a background refill. The problem was never
throughput — it was interactive requests stuck behind bulk ones, and a cap
just adds one more place to be stuck.

The code was reverted. Knowing which fix not to ship is the point of
measuring first.

## One number for next time

**Three seconds pass before a cold start sends its first request** — 3.19,
3.06 and 3.08 s over three runs. No server, no cap, no connection touches
that: it is local startup. The first shelf with anything in it lands at 5.2
to 7.3 s, so more than half of that wait is over before the network is
involved at all.

Nearly a second of it has a name. `MainActivity.onCreate` calls
`WebView.setWebContentsDebuggingEnabled(false)`, and that one static call
loads the whole Chromium provider — at 0.95 s on every run, to the
millisecond — on a launch that opens the home screen and may never touch an
EPUB.

That is the better thing left to chase, and it will be measured before
anything is changed.

---

# Kora 1.8.2

Ouvrir une bibliothèque envoyait onze requêtes. Six d'entre elles
remplissaient un panneau qui était fermé. Cette version, c'est ce qu'un
audit de performance exhaustif a trouvé — et, tout aussi utile, ce qu'il a
démenti.

## Quatre rafales que rien à l'écran n'attendait

**L'écran de bibliothèque ne charge plus son panneau de filtres.** Genres,
tags, années de sortie, classifications, éditeurs, langues : six
interrogations, environ quatre secondes de serveur, et rien en dehors du
panneau de filtres ne les lit jamais. Elles coûtaient deux fois — pour
elles-mêmes, et parce que les trois requêtes qui peignent réellement la
grille faisaient la queue derrière, mesurées à attendre jusqu'à 1058 ms un
créneau de connexion. Le panneau va les chercher à son ouverture, le seul
moment où elles servent.

```
ouverture de bibliothèque :  11 requêtes  ->  6
```

**Une série ne télécharge plus ses collections.** Appartenir à une
collection voulait dire récupérer toutes les séries qu'elle contient — 270,
1764 ms — pour un onglet jamais ouvert. Ça se passe maintenant sur
l'onglet, et une seconde visite ne coûte rien.

**La recherche ne déverse plus tous les auteurs de la bibliothèque.** Champ
vide, l'onglet Auteurs demandait la liste complète des auteurs de chaque
rôle compté, sans pagination, trois requêtes : 1,3 s + 2,0 s + 0,8 s sur un
serveur au repos, 5,3 s + 6,0 s + 0,7 s sur un serveur chargé — rejouées
intégralement à chaque clic sur une puce de bibliothèque. Une recherche vide
n'a rien à dire sur les auteurs. Tape quelque chose et la recherche
fonctionne exactement comme avant.

```
écran Recherche, vide :        5 requêtes  ->  2
changement de puce :           3 requêtes auteurs  ->  0
```

**Et le démarrage demandait deux fois la liste des bibliothèques**, pour un
nombre qu'il avait déjà en mémoire.

## Ce qui a été démenti

Le constat principal de l'audit était qu'une même requête coûte quatre à
cinq fois plus cher en rafale que seule — 5197 ms dans une vague de quatre,
1158 ms toute seule quelques secondes plus tard, sans attente de notre côté
dans aucun des deux cas. La conclusion évidente était de plafonner le nombre
de requêtes que Kora garde en vol.

Ce plafond a donc été construit, rendu réglable sans reconstruire, et balayé
sur quatre valeurs en huit démarrages à froid. La moitié de l'hypothèse a
tenu : baisser le plafond rend bien chaque requête spectaculairement plus
rapide au serveur, de 3885 ms à 444 ms, un facteur neuf.

L'autre moitié, non. **Le total n'a jamais bougé.** La dernière étagère
arrivait entre 4,8 et 9,6 secondes à tous les réglages, plages entièrement
superposées. L'attente n'était pas supprimée, seulement déplacée — hors du
serveur, dans notre propre file.

Pire, ça abîmait précisément ce qui avait déclenché l'audit. Ouvrir un tome
pendant que l'accueil charge encore :

| plafond en vol | attente de ce geste |
|---|---|
| actuel (8) | 2 ms |
| 4 | 30 ms |
| 2 | 2924 ms, puis 2282 ms au second passage |

Ce n'est pas du bruit, c'est de l'arithmétique : la file est équitable, donc
un geste qui arrive en cours de chargement se range derrière les huit
requêtes d'étagères déjà en ligne. Un plafond global ne sait pas distinguer
ton doigt d'un remplissage d'arrière-plan. Le problème n'a jamais été le
débit — c'était des requêtes interactives coincées derrière des requêtes de
fond, et un plafond ajoute juste un endroit de plus où se coincer.

Le code a été retiré. Savoir quel correctif ne pas livrer, c'est
exactement à ça que sert de mesurer d'abord.

## Un chiffre pour la prochaine fois

**Trois secondes s'écoulent avant qu'un démarrage à froid n'envoie sa
première requête** — 3,19, 3,06 et 3,08 s sur trois passages. Aucun serveur,
aucun plafond, aucune connexion n'y touche : c'est du démarrage local. La
première étagère non vide arrive entre 5,2 et 7,3 s, donc plus de la moitié
de cette attente est passée avant que le réseau ne soit impliqué.

Près d'une seconde a un nom. `MainActivity.onCreate` appelle
`WebView.setWebContentsDebuggingEnabled(false)`, et cet unique appel statique
charge tout le fournisseur Chromium — à 0,95 s à chaque passage, à la
milliseconde près — sur un lancement qui ouvre l'accueil et ne touchera
peut-être jamais un EPUB.

C'est le meilleur reste à gratter, et il sera mesuré avant que quoi que ce
soit ne soit changé.
