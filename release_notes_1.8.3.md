# Kora 1.8.3

The audit continued, and this time it went after the things you wait for
without noticing: a statistics card that asked the server to count every
book you have ever read, and a progress bar that downloaded an entire
collection to draw itself.

## A number that changes by one, counted the expensive way

The reading card on Home has thirteen steps. Twelve of them are cheap.
Instrumented one by one, across two cold starts:

```
9 local database steps, together     217 ms   /   169 ms
lifetime series (server)             649 ms   /   421 ms
recently read series (server)        519 ms   /   284 ms
books ever finished (server)        5558 ms   /  7474 ms
whole card                          6953 ms   /  8358 ms
```

**One step was 80 to 89% of the card.** It counts every read book on the
server — for a number that changes by one when you finish a volume.

Two guesses about this card turned out to be wrong, and the measurement
is why: the local database steps do not cost ~240 ms each (they cost 15
to 75 ms), and the reading-streak query was not the culprit (16 ms).

So the server is now asked at most once a week. Its answer is kept along
with the moment it was given, and in between the total is that figure
plus the volumes you have finished in Kora since. Those are counted
exactly, so the number stays live to the second — for one indexed local
lookup instead of a full server count. On the tablet: 455 ms on the run
that asks, **8 ms on the runs that don't**.

What it cannot see is a book marked read somewhere else — the Komga web
interface, another client — since the last time it asked. That gap is at
most a week wide and closes itself.

**It travels in your backup**, per Komga user, because nothing else in
the file can produce it: the per-book history in a backup is Kora's own
and only covers what you read in Kora.

## The card also waits its turn now

It used to compute while the Home shelves were still loading, competing
with them for the same connections. It now waits until every shelf has
landed, and computes once per session rather than on every visit to Home.

A volume finished five minutes ago may not be in the card yet. Opening
the full statistics screen always recomputes, which is how to ask for
exact numbers on purpose.

## A progress bar that cost a whole collection

The Collections and Read Lists tabs draw a small progress bar on each
tile. To know how far through a collection you are, Kora needs its entire
membership — Komga publishes no summary for it — so every tile downloaded
the lot, unpaged. Measured on a library with 64 collections and 9 read
lists:

```
collections:  12 requests, 2193 to 7540 ms each, up to 5717 ms of it
              spent waiting for a free connection
read lists:    9 requests, worst 15284 ms, of which 12433 waiting
```

Twelve seconds queuing. Those requests were saturating the connection
pool by themselves, and it happens per visible tile — scrolling all 64
collections meant 64 complete downloads.

That cost cannot be made smaller, only rarer. The result is now kept for
a day, so only the first visit pays; pulling to refresh clears it for
that tab, which is the deliberate way to ask for exact bars. And no more
than two of those downloads run at once, so a first visit no longer
starves the rest of the screen.

```
read lists tab, 9 bars:   worst request 15.3 s   ->   1.05 s for all nine
second visit, either tab:     everything refetched   ->   nothing
after a cold restart:         everything refetched   ->   nothing
```

## Chromium loads when you open a book, not when you open Kora

`MainActivity` had one line that touched a WebView static, and that
single call loaded the entire Chromium provider — 0.95 s on every launch,
to the millisecond — on a start that opens Home and may never touch an
EPUB. It now happens the first time a WebView actually appears. Across
two cold starts and a walk through Home, statistics, settings and a
backup export, the provider is never loaded at all.

## What was disproved

Startup still spends about 1.8 seconds in one long frame before Home can
paint. The obvious suspect was class loading and just-in-time
compilation, which is exactly what baseline profiles exist to fix.

That was testable without writing any of it: compiling the whole app
ahead of time is the absolute ceiling of what a profile could ever
achieve. Over seven cold starts:

| | normal | fully compiled ahead of time |
|---|---|---|
| dropped frames | 41-42 then 62-67 | 36 then 60-61 |
| longest frame | 1804-1887 ms | 1676-1686 ms |

**About 170 ms out of 1850 — nine percent**, in the best case that will
ever exist. A real profile covers only some classes and would do less.
Baseline profiles are closed as an option, by measurement this time
rather than by inconvenience.

That same measurement cleared the statistics card of causing the dropped
frames, which an earlier note had suspected: the stall ends 2.8 seconds
before the card even starts.

## One number for next time

**1.68 seconds of the first frame is genuine main-thread work**, and it
logs nothing at all. A system trace puts every millisecond of it inside a
single drawing pass with no further detail available — naming what is in
there needs a build made specifically to answer that, so nothing is being
claimed about it yet.

One thing beside it does have a name: during that same frame, three
background threads sat blocked for 353, 325 and 297 ms on a single lock
inside the cover-image disk cache. Home asks for all its thumbnails at
once and they queue up behind one another. Whether that causes the stall
or merely accompanies it is not established, so it is written down rather
than acted on.

---

# Kora 1.8.3

L'audit continue, et cette fois il s'attaque à ce qu'on attend sans s'en
rendre compte : une carte de statistiques qui demandait au serveur de
compter tous les livres jamais lus, et une barre de progression qui
téléchargeait une collection entière pour se dessiner.

## Un nombre qui change de 1, compté à la manière chère

La carte de lecture de l'accueil a treize étapes. Douze sont bon marché.
Instrumentées une par une, sur deux démarrages à froid :

```
9 étapes de base locale, cumulées    217 ms   /   169 ms
séries terminées (serveur)           649 ms   /   421 ms
séries lues récemment (serveur)      519 ms   /   284 ms
tomes terminés à vie (serveur)      5558 ms   /  7474 ms
carte entière                       6953 ms   /  8358 ms
```

**Une seule étape pesait 80 à 89 % de la carte.** Elle décompte tous les
livres lus du serveur — pour un nombre qui change de 1 quand on finit un
tome.

Deux suppositions sur cette carte se sont révélées fausses, et c'est la
mesure qui l'a dit : les étapes de base locale ne coûtent pas ~240 ms
chacune (elles coûtent 15 à 75 ms), et la série de jours consécutifs
n'était pas la coupable (16 ms).

Le serveur n'est donc plus interrogé qu'une fois par semaine au plus. Sa
réponse est conservée avec l'instant où elle a été donnée, et entre deux
fois le total vaut ce chiffre plus les tomes finis dans Kora depuis. Ces
tomes-là sont comptés exactement, donc le nombre reste juste à la seconde
près — pour une lecture locale indexée au lieu d'un décompte serveur
complet. Sur la tablette : 455 ms sur le passage qui demande, **8 ms sur
ceux qui ne demandent pas**.

Ce qu'il ne voit pas : un livre marqué lu ailleurs — l'interface web
Komga, un autre client — depuis la dernière demande. L'écart fait au plus
une semaine et se referme tout seul.

**Il voyage dans ta sauvegarde**, par utilisateur Komga, parce que rien
d'autre dans le fichier ne peut le reconstituer : l'historique par tome
d'une sauvegarde est celui de Kora et ne couvre que ce qui a été lu dans
Kora.

## La carte attend aussi son tour

Elle se calculait pendant que les étagères de l'accueil chargeaient
encore, en concurrence avec elles sur les mêmes connexions. Elle attend
désormais que toutes les étagères soient arrivées, et se calcule une fois
par session au lieu d'à chaque passage sur l'accueil.

Un tome fini il y a cinq minutes peut ne pas encore y figurer. L'écran
complet des statistiques, lui, recalcule toujours — c'est la façon de
demander des chiffres exacts volontairement.

## Une barre de progression qui coûtait toute une collection

Les onglets Collections et Listes de lecture dessinent une petite barre
de progression sur chaque tuile. Pour savoir où tu en es d'une
collection, Kora a besoin de tout son contenu — Komga ne publie aucun
résumé pour ça — donc chaque tuile téléchargeait le tout, sans
pagination. Mesuré sur une bibliothèque de 64 collections et 9 listes de
lecture :

```
collections :  12 requêtes, 2193 à 7540 ms chacune, dont jusqu'à 5717 ms
               passées à attendre une connexion libre
listes      :   9 requêtes, la pire à 15284 ms, dont 12433 d'attente
```

Douze secondes de file. Ces requêtes saturaient le pool de connexions à
elles seules, et ça se produit par tuile visible — faire défiler les 64
collections, c'était 64 téléchargements complets.

Ce coût ne peut pas être réduit, seulement rendu plus rare. Le résultat
est maintenant conservé une journée, donc seule la première visite paie ;
tirer pour rafraîchir le vide pour cet onglet-là, ce qui est la façon
volontaire de demander des barres exactes. Et deux de ces
téléchargements au maximum tournent en même temps, pour qu'une première
visite n'affame plus le reste de l'écran.

```
onglet Listes, 9 barres :   pire requête 15,3 s   ->   1,05 s pour les neuf
2e visite, les 2 onglets :  tout refait           ->   rien
après redémarrage à froid : tout refait           ->   rien
```

## Chromium se charge quand tu ouvres un livre, pas quand tu ouvres Kora

`MainActivity` avait une ligne qui touchait une donnée statique de
WebView, et cet unique appel chargeait tout le fournisseur Chromium —
0,95 s à chaque lancement, à la milliseconde près — sur un démarrage qui
ouvre l'accueil et ne touchera peut-être jamais un EPUB. Ça se produit
maintenant à la première apparition réelle d'une WebView. Sur deux
démarrages à froid et un parcours accueil → statistiques → réglages →
export de sauvegarde, le fournisseur n'est jamais chargé.

## Ce qui a été démenti

Le démarrage passe encore environ 1,8 seconde dans une seule longue frame
avant que l'accueil ne puisse se peindre. Le suspect évident était le
chargement de classes et la compilation à la volée — précisément ce à
quoi servent les profils de référence.

C'était testable sans en écrire un seul : compiler l'application entière
à l'avance, c'est le plafond absolu de ce qu'un profil pourra jamais
apporter. Sur sept démarrages à froid :

| | normal | compilé entièrement à l'avance |
|---|---|---|
| frames sautées | 41-42 puis 62-67 | 36 puis 60-61 |
| frame la plus longue | 1804-1887 ms | 1676-1686 ms |

**Environ 170 ms sur 1850 — neuf pour cent**, dans le meilleur cas qui
puisse exister. Un vrai profil ne couvre qu'une partie des classes et
ferait moins. Les profils de référence sont écartés, cette fois par la
mesure et non par la contrainte.

Cette même mesure a innocenté la carte de statistiques, qu'une note
précédente soupçonnait de causer les frames sautées : le blocage se
termine 2,8 secondes avant que la carte ne commence.

## Un chiffre pour la prochaine fois

**1,68 seconde de la première frame est du vrai travail de fil
principal**, et il ne journalise rien du tout. Une trace système place
chacune de ces millisecondes dans une unique passe de dessin, sans plus
de détail disponible — nommer ce qu'il y a dedans demande un build fait
exprès pour répondre à ça, donc rien n'est affirmé pour l'instant.

Une chose à côté a un nom : pendant cette même frame, trois threads de
fond sont restés bloqués 353, 325 et 297 ms sur un unique verrou du cache
disque des couvertures. L'accueil demande toutes ses vignettes d'un coup
et elles s'empilent les unes derrière les autres. Que ce soit la cause du
blocage ou seulement son voisin n'est pas établi — c'est donc écrit, pas
corrigé.
