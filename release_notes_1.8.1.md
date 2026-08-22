# Kora 1.8.1

Opening a series took ten seconds. It takes a fifth of one. The cause was
not the server, and not the series screen — it was the upcoming-releases
calendar, refreshing itself in the background at every launch.

## What was actually happening

The Home screen shows the next volumes due in your library. That list is
built from tags you write in Komga by hand, and building it meant asking
the server, tag by tag, which series carried each one: **179 queries**,
about three seconds of server each.

Nothing said how much that cost, so nothing said it was a problem. On the
tablet, 44 seconds after a cold start:

```
45 x POST /api/v1/series/list?size=1   queue=2ms   server=3042ms
```

Four minutes of a server doing almost nothing else. Anything you did in
that window was answered by a server busy with us — a book list that
`curl` returns in 1.2 seconds took 10.4 seconds in the app, and opening a
series from one of its volumes took nineteen. Twice, the server stopped
accepting connections altogether and had to be restarted.

Worse, four minutes is longer than a tablet's screen timeout. The device
would doze mid-scan, the pending requests would die on wake reporting
sixteen minutes of freeze, and the scan — never finished — was never
recorded as done. So it ran again from zero on the next launch, and
re-created the conditions for its own failure.

## What changed

**The scan asks for twenty tags at a time instead of one.** 179 queries
became nine. One query for all 174 at once was tried first and is not an
option: the server never answers it.

**A finished scan is remembered on disk.** The list it produced already
was; the *date* it ran was not, so the half-hour it was trusted for never
survived closing the app.

**And it is trusted for a week, not half an hour.** The unit cost does not
move — the expense is the tag lookup itself, and no batching removes it —
so what had to change is how often it is paid. This is a release calendar
you tag by hand: a volume announced for March does not change between
Tuesday and Wednesday. Opening the Prochaines sorties screen still forces
a fresh scan, so wanting one now costs a tap.

The same measurements, after:

| | during a scan | now |
|---|---|---|
| open a series from a volume | 10 793 – 19 270 ms | **78 – 225 ms** |
| load its volumes | 6 652 – 60 913 ms | **136 – 244 ms** |
| whole screen | up to a minute | **about half a second** |

## Coming back to a screen no longer closes the app

Kora keys each screen on what it shows — a series screen on the series, a
volume screen on the volume. Compose refuses two identical keys at once,
so any path that led back to something already open killed the
application outright:

```
IllegalArgumentException: Key 0R875EY6FB432:screen was used multiple times
```

It was one gesture away: a series, its other edition, and that edition's
other edition is the first series again. Or a series, one of its volumes,
and the volume's parent-series button. The guards that existed compared
the target to the current screen only, which neither path trips.

Now you go back to the copy that is already open — which is also the
better answer: you land where you were in it, instead of on a second,
empty copy with the first buried underneath.

## Three wrong answers, and what settled them

The first diagnosis was that the series screen fires too many requests at
once. The app's own log was silent for fifty-three seconds of the wait,
so there was nothing to queue behind. The second was that the search
endpoint is slow; `curl` answered it in 1.2 seconds. The third was that
the server is slow; it was, but only because we were the ones keeping it
busy.

What settled it was measuring inside the HTTP client rather than around
it — splitting each slow call into time spent waiting for a slot, time
spent waiting for the server, and time spent reading the answer. That
probe ships with this release, reporting only calls over two seconds.

---

# Kora 1.8.1

Ouvrir une série prenait dix secondes. Elle en prend un cinquième. La
cause n'était ni le serveur, ni l'écran de série — c'était le calendrier
des prochaines sorties, qui se reconstruisait en arrière-plan à chaque
lancement.

## Ce qui se passait vraiment

L'accueil affiche les prochains tomes attendus dans ta bibliothèque.
Cette liste est bâtie à partir de tags que tu écris à la main dans Komga,
et la bâtir voulait dire demander au serveur, tag par tag, quelle série
portait chacun : **179 requêtes**, environ trois secondes de serveur
chacune.

Rien ne disait ce que ça coûtait, donc rien ne disait que c'était un
problème. Sur la tablette, 44 secondes après un démarrage à froid :

```
45 x POST /api/v1/series/list?size=1   queue=2ms   server=3042ms
```

Quatre minutes de serveur qui ne fait presque que ça. Tout ce que tu
faisais pendant cette fenêtre était servi par un serveur occupé par nous
— une liste de tomes que `curl` rend en 1,2 seconde prenait 10,4 secondes
dans l'application, et ouvrir une série depuis un de ses tomes en prenait
dix-neuf. Deux fois, le serveur a cessé d'accepter les connexions et a dû
être redémarré.

Pire, quatre minutes, c'est plus long que l'extinction d'écran d'une
tablette. L'appareil s'endormait en plein scan, les requêtes en vol
mouraient au réveil en annonçant seize minutes de gel, et le scan — jamais
terminé — n'était jamais enregistré comme fait. Il repartait donc de zéro
au lancement suivant, recréant les conditions de son propre échec.

## Ce qui change

**Le scan demande vingt tags à la fois au lieu d'un.** 179 requêtes sont
devenues neuf. Une seule requête pour les 174 a été essayée d'abord et
n'est pas une option : le serveur n'y répond jamais.

**Un scan terminé est retenu sur disque.** La liste qu'il produisait
l'était déjà ; la *date* à laquelle il avait tourné ne l'était pas, si
bien que la demi-heure de confiance ne survivait jamais à la fermeture de
l'application.

**Et cette confiance dure une semaine, plus une demi-heure.** Le coût
unitaire ne bouge pas — la dépense est la recherche par tag elle-même, et
aucun regroupement ne l'enlève — donc ce qui devait changer, c'est la
fréquence à laquelle on la paie. C'est un calendrier de sorties que tu
tagues à la main : un tome annoncé pour mars ne change pas entre mardi et
mercredi. L'écran Prochaines sorties force toujours un scan frais, donc
en vouloir un tout de suite coûte un geste.

Les mêmes mesures, après :

| | pendant un scan | maintenant |
|---|---|---|
| ouvrir une série depuis un tome | 10 793 – 19 270 ms | **78 – 225 ms** |
| charger ses tomes | 6 652 – 60 913 ms | **136 – 244 ms** |
| écran complet | jusqu'à une minute | **environ une demi-seconde** |

## Revenir sur un écran ne ferme plus l'application

Kora identifie chaque écran par ce qu'il montre — un écran de série par la
série, un écran de tome par le tome. Compose refuse deux identifiants
identiques en même temps, si bien que tout chemin ramenant à quelque chose
de déjà ouvert tuait l'application net :

```
IllegalArgumentException: Key 0R875EY6FB432:screen was used multiple times
```

C'était à un geste : une série, son autre édition, et l'autre édition de
celle-ci est la première série. Ou une série, un de ses tomes, et le
bouton « série parente » du tome. Les gardes qui existaient comparaient la
cible à l'écran courant seulement, ce qu'aucun de ces chemins ne
déclenche.

Désormais tu reviens sur la copie déjà ouverte — ce qui est aussi la
meilleure réponse : tu retombes où tu en étais, au lieu d'une seconde
copie vide avec la première enterrée dessous.

## Trois réponses fausses, et ce qui a tranché

Le premier diagnostic disait que l'écran de série lance trop de requêtes à
la fois. Le journal de l'application était muet pendant cinquante-trois
secondes de l'attente : il n'y avait rien devant quoi faire la queue. Le
deuxième disait que l'endpoint de recherche est lent ; `curl` y répond en
1,2 seconde. Le troisième disait que le serveur est lent ; il l'était,
mais seulement parce que c'était nous qui l'occupions.

Ce qui a tranché, c'est de mesurer **dans** le client HTTP plutôt
qu'autour : découper chaque appel lent en temps d'attente d'un créneau,
temps d'attente du serveur, et temps de lecture de la réponse. Cette sonde
est livrée avec cette version, et ne rapporte que les appels de plus de
deux secondes.
