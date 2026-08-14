### English

**Favorites and "To read" can be scoped to a library**

Both lists gain a filter row: "All" plus one chip per library that actually
holds an entry.

- Pick a library to see only its entries.
- The filter button at the end of the row chooses which libraries count towards
  "All" — handy to keep a catch-all library out of the global view while still
  being able to browse it from its own chip. An excluded library is struck
  through, and the setting applies to both lists.
- Switching library no longer re-reads the whole list: each entry's library is
  remembered locally, so the filter is applied before anything is fetched. The
  first load after updating still reads everything once to learn them.
- "To read" also gained the request cap Favorites already had, so a long list no
  longer floods the server.

**Genre covers: import a whole folder at once**

The Genre tab gains an "Import covers (folder)" button. Pick the folder holding
your images and the file names decide which genre each one belongs to —
accent-, case- and separator-insensitive, and a leading library name (`BD_`,
`Comics `, `Manga_`) binds a file to that library.

One cover per genre: a file named for the open library always wins, a file with
no library prefix is only a fallback, and a file named for another library is
never applied. The result is reported, naming the genres left without an image
so a missing or misnamed file is easy to spot.

---

### Français

**Favoris et « À lire » peuvent être limités à une bibliothèque**

Les deux listes gagnent une barre de filtres : « Toutes » plus une puce par
bibliothèque contenant effectivement une entrée.

- Sélectionne une bibliothèque pour n'afficher que ses entrées.
- Le bouton filtre en bout de barre choisit quelles bibliothèques comptent dans
  « Toutes » — pratique pour tenir une bibliothèque fourre-tout hors de la vue
  globale tout en pouvant la consulter depuis sa puce. Une bibliothèque exclue
  est barrée, et le réglage vaut pour les deux listes.
- Changer de bibliothèque ne relit plus toute la liste : la bibliothèque de
  chaque entrée est mémorisée localement, donc le filtre s'applique avant tout
  téléchargement. Le premier chargement après la mise à jour lit encore tout une
  fois pour les apprendre.
- « À lire » bénéficie aussi du plafond de requêtes que les favoris avaient
  déjà : une longue liste ne sature plus le serveur.

**Couvertures de genres : importer un dossier entier d'un coup**

L'onglet Genres gagne un bouton « Importer des couvertures (dossier) ».
Choisis le dossier contenant tes images et les noms de fichiers décident du
genre de chacune — insensible aux accents, à la casse et aux séparateurs, et un
nom de bibliothèque en tête (`BD_`, `Comics `, `Manga_`) rattache un fichier à
cette bibliothèque.

Une seule couverture par genre : un fichier nommé pour la bibliothèque ouverte
gagne toujours, un fichier sans préfixe n'est qu'un repli, et un fichier nommé
pour une autre bibliothèque ne s'applique jamais. Le bilan est affiché, en
nommant les genres restés sans image pour repérer facilement un fichier manquant
ou mal nommé.
