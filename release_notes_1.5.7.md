# Kora 1.5.7

A speed release. Nothing new to look at — the same screens, waiting less.

## Covers no longer take the whole device

A cover costs a download and an image decode, and Kora ran up to sixty-four
of them at the same time, on the same threads the local database uses. On a
loaded library that was enough to make a two-row database read take half a
second instead of one, and to leave every other request queued behind a page
of thumbnails.

Covers are now limited to a handful at a time, scaled to the device — two on
a four-core phone, four on a tablet — and the app keeps free connections for
whatever screen you are actually on. Kora also asks Android for a larger
memory allowance, so covers you have already seen are kept rather than
fetched again while you scroll.

## Opening a series no longer waits for a panel you did not open

The volumes of a series were held back by two requests that fill the filter
panel's tag and author lists. That panel is closed, and its lists are of no
use until you open it — but the volumes waited for them anyway, remembered
list included. Measured on a busy server, that was between two and fifteen
seconds of an empty area, for a list that was already on the device.

Those two requests now happen when you open the filter panel. A series you
open and read costs nothing for them.

## Library counts step aside for the list

The collection, read-list and genre counts on the library tabs are drawn from
what Kora already knows, so they are on screen almost instantly. Refreshing
them, however, went out at that same moment — three of the slowest requests
there are, competing with the series list you came for.

The refresh now waits for the list to arrive. The counts are no more stale
than before; they simply stop taking the road first.

## A filter change no longer looks ignored

Picking a letter, a sort or a filter keeps the previous results on screen
while the new ones load, which is the right thing to do. But when the server
takes ten seconds, nothing moves and the tap looks lost. A thin progress bar
under the letters now runs for the wait.

---

# Kora 1.5.7

Une version de vitesse. Rien de nouveau à regarder — les mêmes écrans, moins
d'attente.

## Les couvertures ne prennent plus tout l'appareil

Une couverture coûte un téléchargement et un décodage d'image, et Kora en
lançait jusqu'à soixante-quatre en même temps, sur les threads que la base de
données locale utilise aussi. Sur une bibliothèque chargée, cela suffisait à
faire passer une lecture de deux lignes en base de une milliseconde à un
demi-seconde, et à laisser toutes les autres requêtes derrière une page de
vignettes.

Les couvertures sont désormais limitées à quelques-unes à la fois, selon
l'appareil — deux sur un téléphone à quatre cœurs, quatre sur une tablette —
et l'application garde des connexions libres pour l'écran que vous regardez.
Kora demande aussi à Android une réserve de mémoire plus grande, pour garder
les couvertures déjà vues au lieu de les retélécharger en défilant.

## Ouvrir une série n'attend plus un panneau que vous n'avez pas ouvert

Les tomes d'une série étaient retenus par deux requêtes qui remplissent les
listes de tags et d'auteurs du panneau de filtres. Ce panneau est fermé, et
ses listes ne servent à rien tant qu'on ne l'ouvre pas — pourtant les tomes
les attendaient, y compris la liste déjà mémorisée. Mesuré sur un serveur
chargé, cela représentait deux à quinze secondes de zone vide, pour une liste
déjà présente sur l'appareil.

Ces deux requêtes partent maintenant à l'ouverture du panneau. Une série
qu'on ouvre et qu'on lit ne les paie plus du tout.

## Les compteurs de la bibliothèque laissent passer la liste

Les compteurs de collections, de listes de lecture et de genres sont dessinés
à partir de ce que Kora sait déjà : ils sont à l'écran presque
instantanément. Leur rafraîchissement, lui, partait au même moment — trois
des requêtes les plus lentes qui soient, en concurrence avec la liste de
séries pour laquelle vous étiez venu.

Ce rafraîchissement attend désormais l'arrivée de la liste. Les compteurs ne
sont pas plus périmés qu'avant ; ils cessent simplement de passer devant.

## Un changement de filtre ne semble plus ignoré

Choisir une lettre, un tri ou un filtre garde les résultats précédents à
l'écran pendant le chargement, et c'est le bon comportement. Mais quand le
serveur met dix secondes, rien ne bouge et le tap paraît perdu. Une fine
barre de progression sous les lettres accompagne maintenant l'attente.
