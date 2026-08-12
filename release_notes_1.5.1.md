# Kora 1.5.1

## Hide chapter series

Libraries that hold both a collected edition and its chapter-by-chapter
release showed each work twice. A switch in every library's filter menu hides
the series whose title ends in **(Chap)**.

The filtering happens in the app, not on the server: asking Komga for "titles
that do not end in (Chap)" is a leading-wildcard scan no index can serve —
measured at 420 seconds on a real library. Client-side, it is instant.

## Chapter management (admin)

**Settings → Administration → Chapter management.** Pick a library and it
lists its chapter series, opening on the ones that are not yet paired with
their volumes.

- **One click per series**, or select several and match them in one go.
- Matching looks for titles beginning with the chapter title minus its marker
  — one indexed query — and falls back to a full-text search only when that
  finds nothing.
- A single confident match is applied on its own. **Two are never guessed**: a
  library holding two series called "Berserk" gives no ground to prefer
  either, and a wrong link is silent once written. Those are handed back with
  their cover, language, publisher, volume count and a similarity score.
- **A report when the run ends**, naming every series and what happened to it:
  linked and to which volumes, left to decide, no match, or failed with the
  reason.
- Linked series can be unlinked from the same screen.

Links made here are published to Komga exactly like links made from a series'
Links tab — when link sharing is on and you are an admin. The screen says so
up front when that is not the case, rather than pairing a whole library into a
table that never leaves the device.

## Fixes

- A link made from the admin screen now refreshes the rest of the app. A
  series screen already open kept the state it loaded before, so the link
  looked like it was never made.
- Unlinking removed only the local copy and left the published link to come
  back on the next load.
- Chapters/Volumes are now offered as chips in the "add a link" dialog, so
  every kind fits on screen without scrolling.
- The random series button no longer does nothing when its draw lands on a
  hidden chapter series.

---

# Kora 1.5.1

## Masquer les séries de chapitres

Les bibliothèques qui contiennent à la fois une édition reliée et sa
publication chapitre par chapitre affichaient chaque œuvre deux fois. Un
interrupteur dans le menu de filtres de chaque bibliothèque masque les séries
dont le titre finit par **(Chap)**.

Le filtrage se fait dans l'application, pas sur le serveur : demander à Komga
« les titres qui ne finissent pas par (Chap) » est un balayage qu'aucun index
ne peut servir — mesuré à 420 secondes sur une vraie bibliothèque. Côté
client, c'est instantané.

## Gestion des chapitres (admin)

**Réglages → Administration → Gestion des chapitres.** Choisissez une
bibliothèque et l'écran liste ses séries de chapitres, en commençant par
celles qui ne sont pas encore associées à leurs tomes.

- **Un clic par série**, ou sélectionnez-en plusieurs et associez-les d'un
  coup.
- La recherche cherche les titres commençant par le titre du chapitre sans son
  marqueur — une seule requête indexée — et ne bascule sur la recherche plein
  texte que si elle ne trouve rien.
- Une correspondance sûre et unique est appliquée seule. **Deux ne sont jamais
  devinées** : une bibliothèque contenant deux séries « Berserk » ne donne
  aucune raison de préférer l'une, et un mauvais lien est silencieux une fois
  écrit. Elles vous sont rendues avec leur couverture, leur langue, leur
  éditeur, leur nombre de tomes et un indice de similarité.
- **Un rapport à la fin de l'opération**, nommant chaque série et ce qui lui
  est arrivé : liée et à quels tomes, à trancher, sans correspondance, ou en
  échec avec la raison.
- Les séries liées peuvent être déliées depuis le même écran.

Les liens créés ici sont publiés sur Komga exactement comme ceux créés depuis
l'onglet Liens d'une série — si le partage des liens est activé et que vous
êtes administrateur. L'écran le dit d'emblée quand ce n'est pas le cas, plutôt
que d'associer toute une bibliothèque dans une table qui ne quitte jamais
l'appareil.

## Corrections

- Un lien créé depuis l'écran d'administration rafraîchit maintenant le reste
  de l'application. Une fiche série déjà ouverte gardait l'état chargé avant :
  le lien semblait n'avoir jamais été créé.
- Délier ne supprimait que la copie locale et laissait le lien publié revenir
  au chargement suivant.
- Chapitres/Tomes sont proposés en puces dans la fenêtre « ajouter un lien » :
  toutes les catégories tiennent à l'écran sans défiler.
- Le bouton « série au hasard » ne reste plus sans effet quand son tirage tombe
  sur une série de chapitres masquée.
