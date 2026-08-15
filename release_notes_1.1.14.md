### English

**Fix: the upcoming-releases calendar could empty itself permanently**

The scan fired one Komga query per `nextrelease:` tag, all at once with no
limit. Past a certain number of tags the burst timed out — and because every
failure was converted into an empty list, the scan reported "no upcoming
releases" instead of failing, overwriting the saved calendar with nothing. It
then stayed blank even though the tags were still there.

- A failed scan now fails instead of returning an empty result.
- At most 4 lookups run at a time, so a large tag set can no longer stampede
  the server. The scan is slightly slower and far more reliable.
- An empty result from an incomplete scan can never replace a good calendar.
- The home card re-scanned on *every* return to the home screen; it now trusts
  a successful scan for 30 minutes. Opening the calendar screen still forces a
  fresh scan. This should also noticeably reduce battery use.
- Leaving the screen mid-scan is no longer logged as an error.

**Fix: shelf detail didn't refresh after reading**

Opening a book from a full-screen shelf and coming back left the reading
progress stale until a manual pull-to-refresh.

---

### Français

**Correction : le calendrier des prochaines sorties pouvait se vider définitivement**

Le scan lançait une requête Komga par tag `nextrelease:`, toutes en même temps
et sans limite. Passé un certain nombre de tags, la rafale finissait en timeout
— et comme chaque échec était converti en liste vide, le scan annonçait « aucune
sortie à venir » au lieu d'échouer, écrasant le calendrier enregistré par du
vide. Il restait alors blanc alors que les tags étaient toujours là.

- Un scan en échec échoue désormais, au lieu de renvoyer un résultat vide.
- Au plus 4 requêtes simultanées : un gros volume de tags ne peut plus saturer
  le serveur. Le scan est un peu plus lent et bien plus fiable.
- Un résultat vide issu d'un scan incomplet ne remplace jamais un calendrier
  valide.
- La carte de l'accueil relançait un scan à *chaque* retour sur l'accueil ; elle
  fait maintenant confiance à un scan réussi pendant 30 minutes. Ouvrir l'écran
  du calendrier force toujours un scan frais. Cela devrait aussi réduire
  nettement la consommation de batterie.
- Quitter l'écran pendant un scan n'est plus journalisé comme une erreur.

**Correction : le détail d'étagère ne se rafraîchissait pas après lecture**

Ouvrir un tome depuis une étagère en plein écran puis revenir laissait la
progression de lecture figée jusqu'à un tirer-pour-rafraîchir manuel.
