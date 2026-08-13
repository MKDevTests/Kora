# Kora 1.5.2

## Fixes

- **The page you had just left no longer flashes back when changing volume.**
  The end-of-book page was cleared before the next volume had been fetched,
  which handed the screen back to the pager — still parked on the finished
  book's last page. It now stays up, with a spinner, until the new volume is
  painted.

- **Changing volume is faster.** The reader was looking up the volume *after*
  the one being opened before showing anything. That lookup is one call in the
  middle of a series, but at the end of one it walks the series list and then
  every book page of the series that follows. It now happens in the background,
  once you are already reading.

- **Home updates after reading again.** It only ever learned about read
  progress from the server's event stream, so a book opened from a series
  screen, from search or from the widget left "Keep reading" showing what was
  true before — until a manual refresh. Progress written by the app is now
  signalled directly; the server stream stays for books read on another device.

- **Chapter series no longer leak into book shelves.** The "hide chapter
  series" switch only filtered lists of series, so "On deck", "Keep reading",
  "Forgotten" and book search still showed volumes belonging to a **(Chap)**
  series. Opening such a series directly still shows everything it contains,
  and reading straight through it still works.

---

# Kora 1.5.2

## Corrections

- **La page que l'on vient de quitter ne réapparaît plus au changement de
  tome.** La page de fin de tome était effacée avant que le tome suivant soit
  chargé, ce qui rendait l'écran au lecteur — resté sur la dernière page du
  tome terminé. Elle reste maintenant affichée, avec un indicateur de
  chargement, jusqu'à ce que le nouveau tome soit prêt.

- **Le changement de tome est plus rapide.** Le lecteur cherchait le tome
  *suivant celui qu'il ouvrait* avant d'afficher quoi que ce soit. C'est un
  appel au milieu d'une série, mais en fin de série cela parcourt la liste des
  séries puis toutes les pages de livres de la série d'après. Cette recherche
  se fait désormais en arrière-plan, une fois la lecture commencée.

- **L'accueil se met à jour après une lecture.** Il n'apprenait les
  progressions que par le flux d'événements du serveur : un tome ouvert depuis
  une série, depuis la recherche ou depuis le widget laissait « Reprendre la
  lecture » sur son état d'avant — jusqu'à un rafraîchissement manuel. Les
  progressions écrites par l'application sont maintenant signalées
  directement ; le flux serveur reste pour les lectures faites sur un autre
  appareil.

- **Les séries de chapitres ne débordent plus sur les étagères de tomes.**
  L'option « masquer les séries de chapitres » ne filtrait que les listes de
  séries : « À la suite », « Reprendre la lecture », « Oubliés » et la
  recherche de tomes affichaient encore des tomes appartenant à une série
  **(Chap)**. Ouvrir une telle série directement montre toujours tout son
  contenu, et la lecture d'un tome au suivant y fonctionne toujours.
