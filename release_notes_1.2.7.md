### English

**A much lighter app**

The release build now runs R8's optimization pass on top of code shrinking: the
compiled code drops from 52 MB to 24.5 MB. No behaviour change — the flag that
had been disabling it turned out to fix nothing.

**Random sorting is finally stable**

Sorting a library randomly re-drew a brand new order on every request, so paging
forward could show the same series twice while others were unreachable, and any
refresh reshuffled everything.

- The drawn order is now kept for the session: pages already seen never move,
  and going back to a previous page costs no request.
- It reshuffles only when you ask — pull to refresh, or change the sort/filter.

**Favorites and "To read" no longer reload the screen**

Adding or removing an entry re-queried whatever was on screen, which on a
randomly-sorted library reshuffled the whole thing for nothing. The lists update
on their own now.

**Home shelf fixes**

- The Favorites shelf honours the libraries excluded from Favorites, including
  for an entry that was just added.
- Tapping the Favorites shelf opens the full Favorites screen, with its
  per-library filter, instead of the read-only shelf view.
- Choosing a single genre cover opens the file browser instead of the photo
  gallery, so an image kept in Download can be picked.

---

### Français

**Application nettement plus légère**

Le build de release applique désormais la passe d'optimisation de R8 en plus de
l'élagage : le code compilé passe de 52 Mo à 24,5 Mo. Aucun changement de
comportement — l'option qui la désactivait ne corrigeait en réalité rien.

**Le tri aléatoire est enfin stable**

Trier une bibliothèque au hasard retirait un ordre entièrement neuf à chaque
requête : en avançant d'une page tu pouvais revoir la même série pendant que
d'autres restaient inaccessibles, et le moindre rafraîchissement remélangeait
tout.

- L'ordre tiré est conservé pour la session : les pages déjà vues ne bougent
  plus, et revenir en arrière ne coûte aucune requête.
- Le mélange ne change que si tu le demandes — tirer pour rafraîchir, ou
  modifier le tri ou un filtre.

**Favoris et « À lire » ne rechargent plus l'écran**

Ajouter ou retirer une entrée relançait la requête de l'écran affiché, ce qui sur
une bibliothèque en tri aléatoire remélangeait tout pour rien. Les listes se
mettent à jour toutes seules.

**Corrections sur l'accueil**

- L'étagère Favoris respecte les bibliothèques exclues des favoris, y compris
  pour une série qu'on vient d'ajouter.
- Appuyer sur l'étagère Favoris ouvre la vraie page Favoris, avec son filtre par
  bibliothèque, au lieu de la vue en lecture seule.
- Choisir une couverture de genre ouvre le gestionnaire de fichiers et non la
  galerie photos, pour pouvoir prendre une image rangée dans Téléchargements.
