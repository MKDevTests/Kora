# Kora 1.5.5

## Fixed: the app closed while linking two series

Opening a series' Links tab, choosing "add a link" and searching could close
the app outright. It happened when one of the search results was a series
whose cover was also on screen behind the dialog — most easily the series you
were linking *from*.

A dialog on Android is its own window, with its own layout. The result rows
were drawn with the component that also drives the cover animation between
the library and a series page, and that animation needs both covers to live
in the same window. With one in the dialog and one behind it, Compose could
not measure the distance between them and gave up.

The dialog now draws the same covers without taking part in that animation,
which it never needed to. Opening a series from the library still animates
its cover as before.

This dates back to 1.4.6, not to 1.5.4: it took a search result that was
already visible behind the dialog to trigger it.

---

# Kora 1.5.5

## Corrigé : l'application se fermait pendant la création d'un lien

Ouvrir l'onglet Liens d'une série, choisir « ajouter un lien » et lancer une
recherche pouvait fermer l'application net. Cela arrivait quand l'un des
résultats était une série dont la couverture était aussi affichée derrière la
fenêtre — au plus simple, la série depuis laquelle on créait le lien.

Sur Android, une fenêtre de dialogue est une fenêtre à part, avec sa propre
mise en page. Les lignes de résultats étaient dessinées avec le composant qui
sert aussi à l'animation de couverture entre la bibliothèque et la page d'une
série, et cette animation a besoin que les deux couvertures soient dans la
même fenêtre. Avec l'une dans le dialogue et l'autre derrière, Compose ne
pouvait plus mesurer la distance entre elles et abandonnait.

Le dialogue affiche désormais les mêmes couvertures sans participer à cette
animation, ce dont il n'a jamais eu besoin. Ouvrir une série depuis la
bibliothèque anime toujours sa couverture comme avant.

Le problème date de la 1.4.6, pas de la 1.5.4 : il fallait qu'un résultat de
recherche soit déjà visible derrière le dialogue pour le déclencher.
