# Kora 1.1.12

## Nouveautés

**Défilement intelligent pour les webtoons.** Dans le lecteur continu, un appui sur l'écran ne fait plus avancer d'une distance fixe et aveugle : il traverse les gouttières blanches d'un seul coup et s'arrête sur les frontières des cases et des bulles. Réglable dans le lecteur et dans les réglages généraux (« Webtoon smart scroll »).

Mesuré sur 215 pages réelles issues de 4 séries : 16 % d'appuis en moins, un tiers de blanc en moins à l'écran, et les cases qui n'étaient jamais montrées entières passent de 54 % à 0,2 %. Le calage sur les bulles fait tomber les écrans qui coupent une bulle de 53 % à 17 %.

## Corrections

**L'inversion des bulles fonctionne enfin sur les webtoons.** Elle n'avait en réalité jamais marché sur ce format : le détecteur redimensionne la page en 640×640, ce qu'une bande de 720×15160 ne supporte pas — chaque bulle s'y réduisait à quelques pixels. Sur une vraie page, 5 bulles étaient trouvées contre 44 en découpant la bande en tranches. Les planches classiques de manga et de BD passent par le même chemin qu'avant, sans changement.

**Le bouton aléatoire n'est plus un cul-de-sac.** Après un tirage, « série suivante » et « série précédente » restaient bloqués, et la lecture d'un tome jusqu'au bout affichait « pas de série disponible avec les filtres actuels ». La navigation se calcule à partir d'une position dans la liste filtrée, or un tirage aléatoire côté serveur n'en a aucune : le tirage se fait désormais sur un décalage aléatoire dans le tri courant, ce qui rend la série ouverte indiscernable d'une série atteinte en naviguant.

**Le dé de l'écran série respecte les filtres.** Il ne gardait que la bibliothèque et jetait silencieusement tous les autres filtres. Il souffrait du même défaut de position, ce qui faisait que les flèches suivant/précédent renvoyaient toujours la même série, quel que soit le tirage.

---

# Kora 1.1.12

## New

**Webtoon smart scroll.** In the continuous reader, tapping the screen no longer advances a blind fixed distance: it crosses blank gutters whole and stops on panel and speech-bubble boundaries. Available both in the reader settings and the global ones ("Webtoon smart scroll").

Measured over 215 real pages from 4 series: 16% fewer taps, a third less blank space on screen, and panels that were never shown whole drop from 54% to 0.2%. Bubble alignment takes screens that cut through a speech bubble from 53% down to 17%.

## Fixes

**Speech-bubble inversion now works on webtoons.** It never actually worked on that format: the detector resizes a page to 640×640, which a 720×15160 strip cannot survive — every bubble was reduced to a few pixels. On a real page, 5 bubbles were found against 44 when slicing the strip first. Regular manga and comic pages take the same path as before, unchanged.

**The random button is no longer a dead end.** After a roll, "next series" and "previous series" were stuck, and finishing a book showed "no next series with the current filters". Sibling navigation is computed from a position in the filtered list, and a server-side random sort has none: the draw now uses a random offset under the current sort, which makes the opened series indistinguishable from one reached by browsing.

**The dice on the series screen honours your filters.** It kept only the library and silently dropped every other filter. It had the same missing-position flaw, which made the next/previous arrows always return the same series no matter the roll.
