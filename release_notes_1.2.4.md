### English

**Faster home screen and series lists**

Several screens fetched the series they display one at a time, waiting for each
server round-trip before starting the next. They now fetch them together.

- The Favorites home shelf resolved up to 20 series one by one — it now loads
  them in parallel and fills in a fraction of the time.
- Same fix for the ignore list and hidden-series screens (which had no limit at
  all), for the AniList franchise linking, and for hiding or unhiding several
  series at once.
- Home shelves now appear as soon as each one is ready, instead of all waiting
  for the slowest. Coming back from the reader, "Keep reading" refreshes right
  away rather than after every other shelf has answered.

Requests stay capped at four at a time, so a big library never floods the Komga
server.

---

### Français

**Écran d'accueil et listes de séries plus rapides**

Plusieurs écrans récupéraient les séries affichées une par une, en attendant
chaque aller-retour serveur avant de lancer le suivant. Elles sont désormais
récupérées ensemble.

- L'étagère Favoris de l'accueil résolvait jusqu'à 20 séries une par une : elle
  les charge maintenant en parallèle et se remplit en une fraction du temps.
- Même correction pour les écrans liste d'ignorés et séries masquées (qui
  n'avaient aucune limite), pour la liaison de franchise AniList, et pour le
  masquage/démasquage de plusieurs séries à la fois.
- Les étagères de l'accueil s'affichent dès qu'elles sont prêtes, au lieu
  d'attendre toutes la plus lente. En sortant du lecteur, « Keep reading » se met
  à jour immédiatement plutôt qu'après toutes les autres.

Les requêtes restent plafonnées à quatre simultanées : une grosse bibliothèque
ne sature jamais le serveur Komga.
