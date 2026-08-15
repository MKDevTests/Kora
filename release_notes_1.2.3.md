### English

**Shared series links: now reliable and fast**

Series links shared through Komga (the "Share links via Komga" option) could
fail to appear when opening a series, or take a very long time to show up.

- They now display correctly on first open. The screen was reading the sharing
  setting before it had finished loading, and silently dropped the shared links.
- Loading is much faster: the linked series are now resolved in parallel instead
  of one by one, and a redundant metadata fetch on every open was removed. On a
  series with several links this cuts the wait roughly in half.

No change to how links are created or stored.

---

### Français

**Liens de séries partagés : fiables et rapides**

Les liens de séries partagés via Komga (option « Share links via Komga »)
pouvaient ne pas apparaître à l'ouverture d'une série, ou mettre très longtemps
à s'afficher.

- Ils s'affichent désormais correctement dès la première ouverture. L'écran
  lisait le réglage de partage avant qu'il ait fini de charger et abandonnait
  silencieusement les liens partagés.
- Le chargement est bien plus rapide : les séries liées sont maintenant résolues
  en parallèle au lieu d'une par une, et un rechargement de métadonnées inutile à
  chaque ouverture a été supprimé. Sur une série avec plusieurs liens, l'attente
  est réduite d'environ moitié.

Aucun changement dans la façon de créer ou de stocker les liens.
