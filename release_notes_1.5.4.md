# Kora 1.5.4

## Search stops making you wait

Opening search loads the newest additions. Typing during that load did
nothing until the list came back — the collector watching the search box was
only started *after* the load, so those seconds were spent watching nothing.

Now the first letter cancels the newest-additions request outright. The
results already on screen stay there while the next search runs, with the
progress line in the search bar rather than a full-screen spinner, and the
three requests behind a search (series, books, authors) go out together
instead of one after the other.

## The Authors tab is about writing and drawing

It listed translators, inkers, letterers and editors next to the actual
authors, because the endpoint it used takes a search string and no role at
all. It now asks for one role at a time and merges the answers.

Picking a name was wrong in the same way, and separately: the query matched
*any* role, so selecting an author still pulled in the series that person had
only translated. The role is pinned there too now.

Which roles count is not a new setting. **Settings → Appearance** already
answers that question for the series and book screens; search reads the same
answer. Left off, it means writing and drawing. The library chip now scopes
the Authors tab as well, which it did not before.

## Reading order, redrawn

A row of text boxes with arrows, scrolling sideways, with the branches piled
underneath as one flat list. It held up for three series and fell apart on a
real franchise — Fairy Tail is about a dozen.

It is a vertical timeline now: a numbered rail for the main line, a cover per
entry, and the branches grouped by what they are — start here, no declared
order, read after, spin-offs, same world — instead of one line per link. Each
group stops at four entries with a "show all". The point it has to make is
unchanged: a prequel is not where you start.

The caps that cut the picture short are gone with it. Two of them were display
budgets from the old shape and cost nothing; the third was real, and was
exactly the size of the franchise it was cutting.

## The EPUB reader

Four tap-navigation modes, matching the image reader's zones and its diagrams,
now on a tab of their own rather than four chips at the bottom of Appearance.
Fifteen bundled fonts, and a default font that exists. The page no longer
flickers under the floating control bar.

## Smaller things

- **Return home** from the reader, next to return to book, series and library.
- On a phone, the sort and continue-reading buttons were pushed off the screen
  edge by the navigation island. They move to their own row above it.
- Settings: the "Experimental" section is called **Content** — it has not been
  experimental for a while.
- A fresh install now starts with genres, the new interface and floating
  navigation bar, series links via Komga, upcoming releases and reading stats
  already on. Nothing changes for an existing install.

---

# Kora 1.5.4

## La recherche ne te fait plus attendre

Ouvrir la recherche charge les derniers ajouts. Taper pendant ce chargement ne
servait à rien tant que la liste n'était pas revenue : ce qui surveillait la
barre de recherche n'était lancé qu'*après* le chargement, donc ces
secondes-là ne regardaient rien.

La première lettre annule maintenant la requête des nouveautés. Les résultats
déjà affichés restent à l'écran pendant la recherche suivante, avec la barre
de progression de la barre de recherche au lieu d'un indicateur plein écran,
et les trois requêtes d'une recherche (séries, tomes, auteurs) partent
ensemble au lieu de se suivre.

## L'onglet Auteurs, c'est scénario et dessin

Il listait traducteurs, encreurs, lettreurs et éditeurs à côté des auteurs,
parce que l'endpoint utilisé ne prend qu'une chaîne de recherche, sans aucun
rôle. Il demande maintenant un rôle à la fois et fusionne les réponses.

Choisir un nom était faux de la même façon, et séparément : la requête
acceptait *n'importe quel* rôle, donc sélectionner un auteur ramenait encore
les séries qu'il avait seulement traduites. Le rôle y est épinglé aussi.

Quels rôles comptent n'est pas un nouveau réglage. **Paramètres → Apparence**
répond déjà à cette question pour les écrans série et tome ; la recherche lit
la même réponse. Laissé désactivé, cela veut dire scénario et dessin. Le
filtre par bibliothèque cadre désormais l'onglet Auteurs également, ce qu'il
ne faisait pas.

## L'ordre de lecture, redessiné

Une rangée de boîtes de texte avec des flèches, qui défilait sur le côté, et
les branches empilées dessous en une liste plate. Ça tenait pour trois séries
et ça tombait sur une vraie franchise — Fairy Tail en fait une douzaine.

C'est une frise verticale maintenant : un rail numéroté pour le fil principal,
une couverture par entrée, et les branches regroupées par nature — commencer
ici, ordre non déclaré, à lire après, hors-séries, même univers — au lieu
d'une ligne par lien. Chaque groupe s'arrête à quatre entrées avec un « tout
afficher ». Ce que l'affichage doit dire n'a pas changé : une préquelle n'est
pas là où on commence.

Les plafonds qui coupaient l'image partent avec. Deux d'entre eux étaient des
budgets d'affichage de l'ancienne forme et ne coûtaient rien ; le troisième
était réel, et faisait exactement la taille de la franchise qu'il coupait.

## Le lecteur EPUB

Quatre modes de navigation au toucher, alignés sur les zones du lecteur
d'images et sur ses schémas, maintenant dans un onglet à eux plutôt qu'en
quatre puces au fond de l'onglet Apparence. Quinze polices fournies, et une
police par défaut qui existe. La page ne scintille plus sous la barre de
contrôles flottante.

## Plus petit

- **Retour à l'accueil** depuis le lecteur, à côté du retour au tome, à la
  série et à la bibliothèque.
- Sur un téléphone, les boutons tri et reprise de lecture étaient poussés hors
  de l'écran par l'îlot de navigation. Ils passent sur leur propre rangée
  au-dessus.
- Paramètres : la section « Expérimental » s'appelle **Contenu** — elle n'est
  plus expérimentale depuis un moment.
- Une nouvelle installation démarre avec les genres, la nouvelle interface et
  la barre de navigation flottante, les liens de séries via Komga, les
  prochaines sorties et les statistiques de lecture déjà activés. Rien ne
  change pour une installation existante.
