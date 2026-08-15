### English

**Tappable home shelves**

Tapping a shelf title on the home screen now opens that shelf full-screen, as a
grid of up to 50 items instead of the handful that fit in the carousel. A chevron
next to each title marks the shelves as tappable.

Every shelf type is supported, including the ones that have no server-side
equivalent — "Almost finished", "Favorites" and the book shelves ("Keep reading",
"Forgotten"). The detail screen is a plain view: no sorting, filtering or
multi-selection. Long-press still opens the usual series/book actions menu, and
pull-to-refresh reloads the shelf.

**Genres tab in the series editor (admins only)**

The series edit dialog gains a GENRES tab listing the 22 curated genres as
checkboxes, alphabetically, with a limit of 4 per series. Current genres are
pre-checked; once four are picked the rest grey out.

Genres are stored as `kora:genre:*` Komga tags — the same ones the Genre tab, the
genre drill-down and the series "Genres:" line already read, so an edit shows up
everywhere immediately. Only those tags are rewritten on save: any other tag on
the series, including `kora:hidden`, is carried over untouched.

---

### Français

**Étagères de l'accueil cliquables**

Appuyer sur le titre d'une étagère de l'accueil ouvre désormais cette étagère en
plein écran, sous forme de grille pouvant aller jusqu'à 50 éléments au lieu des
quelques-uns visibles dans le carrousel. Un chevron à côté de chaque titre
signale les étagères cliquables.

Tous les types d'étagères sont couverts, y compris ceux qui n'ont pas
d'équivalent côté serveur — « Presque fini », « Favoris » et les étagères de
livres (« Keep reading », « Oubliés »). L'écran de détail reste un affichage
simple : ni tri, ni filtres, ni sélection multiple. L'appui long ouvre toujours
le menu d'actions série/livre habituel, et le tirer-pour-rafraîchir recharge
l'étagère.

**Onglet Genres dans l'édition de série (admin uniquement)**

La fenêtre d'édition de série gagne un onglet GENRES qui liste les 22 genres de
la taxonomie en cases à cocher, par ordre alphabétique, avec une limite de 4 par
série. Les genres actuels sont pré-cochés ; une fois quatre genres choisis, les
autres passent en grisé.

Les genres sont stockés sous forme de tags Komga `kora:genre:*` — exactement ceux
que lisent déjà l'onglet Genre, le drill-down par genre et la ligne « Genres : »
de la fiche série, si bien qu'une modification se répercute partout
immédiatement. Seuls ces tags-là sont réécrits à l'enregistrement : tous les
autres tags de la série, `kora:hidden` compris, sont conservés tels quels.
