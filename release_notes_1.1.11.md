### English

**New — Invert speech bubbles (accessibility)**

A reading comfort option for anyone who finds the bright white of speech bubbles glaring: bubbles become black with white text, while the artwork around them is left completely untouched. Bubbles that are already dark are kept as they are.

- Turn it on from the **in-reader settings** or from **Settings → Image Reader** — it is available in both places.
- Detection uses a bundled speech-bubble model (RT-DETR, Apache-2.0, ~11 MB, shipped inside the app — nothing to download or configure).
- Inside each detected bubble the exact bubble shape is rebuilt before inverting, so nothing bleeds into the artwork at the corners of an oval bubble.

This replaces an earlier contour-based approach that was measured on full volumes and never got good enough: it missed every bubble with a tail or a spiky outline, every bubble clipped by a panel edge, and essentially all European comics — 0 of 12 bubbles on a sample page where the model now finds 12 of 12.

**Fixed — "Keep reading" now updates when you come back from a book**

Leaving a book you had just been reading left the Home shelf stale until you pulled to refresh. Home now refreshes on exit — while "Discover" shelves keep their current picks (only a manual pull-to-refresh re-rolls those).

**Fixed — webtoons**
- Webtoons are detected more reliably: the "tall page" threshold went from 4.0 to 3.0, which was missing series shipped as 720×2752 tiles (ratio 3.82). The test is strictly height ÷ width, so wide double-page spreads can never trigger it.
- Webtoons no longer open in the panel-by-panel reader. Its panel ordering breaks down on tall strips — reading would start mid-page or at the end and skip several image zones. They now use plain vertical scrolling, which suits the format.
- Panel detection now runs on the untouched page, so it keeps working when bubble inversion is on (previously the two together disabled panel splitting entirely).

---

### Français

**Nouveau — Inversion des bulles (accessibilité)**

Une option de confort de lecture pour qui trouve le blanc des bulles éblouissant : les bulles deviennent noires avec le texte en blanc, et le dessin autour reste totalement intact. Les bulles déjà sombres sont laissées telles quelles.

- Activable depuis les **réglages du lecteur** ou depuis **Réglages → Image Reader** — disponible aux deux endroits.
- La détection s'appuie sur un modèle de bulles embarqué (RT-DETR, Apache-2.0, ~11 Mo, livré dans l'app — rien à télécharger ni à configurer).
- Dans chaque bulle détectée, la forme exacte est reconstruite avant inversion : rien ne déborde sur le dessin dans les coins d'une bulle ovale.

Cela remplace une première approche par contours qui, mesurée sur des tomes entiers, n'a jamais atteint le niveau requis : elle ratait toute bulle à queue ou à contour hérissé, toute bulle coupée par un bord de case, et quasiment toute la BD franco-belge — 0 bulle sur 12 sur une page test, là où le modèle en trouve 12 sur 12.

**Corrigé — « Keep reading » se met à jour au retour d'un tome**

En sortant d'un tome qu'on venait de lire, l'étagère de Home restait figée jusqu'à un rafraîchissement manuel. Home se rafraîchit désormais à la sortie — tandis que les étagères « Discover » conservent leurs tirages (seul un pull-to-refresh manuel les renouvelle).

**Corrigé — webtoons**
- Les webtoons sont détectés plus fidèlement : le seuil de « page très haute » passe de 4,0 à 3,0, ce qui laissait passer les séries livrées en tuiles 720×2752 (ratio 3,82). Le test porte strictement sur hauteur ÷ largeur : une double page, plus large que haute, ne peut donc jamais le déclencher.
- Les webtoons ne s'ouvrent plus dans le lecteur case par case. Son ordre de lecture se disloque sur les bandes très hautes — la lecture démarrait au milieu ou à la fin de la page en sautant plusieurs zones d'image. Ils utilisent désormais le défilement vertical simple, adapté au format.
- La détection des cases travaille désormais sur la page non modifiée : elle continue donc de fonctionner quand l'inversion des bulles est active (auparavant, les deux ensemble désactivaient purement et simplement le découpage).
