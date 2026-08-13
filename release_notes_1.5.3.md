# Kora 1.5.3

## Opening a volume is much faster

Measured over six cold opens against a real server: **from ~12 seconds down to
~4**, and under a second when the server is responsive.

Opening a book fired five requests at once and waited for all of them. Four
were cheap. The fifth looked for the volume *after* the one being opened — and
that is the only one that can miss: on the last volume of a series, Komga
answers "no next volume", and Kora then walks the series list and every volume
of the series that follows to find where reading should continue. It measured
2.8, 11.9 and 14.4 seconds on three consecutive opens, and the open time
tracked it to within a tenth of a second.

Nobody needs that answer to read page one. It now runs after the page is on
screen. Turning past the end of a volume still waits for it, so the
end-of-volume page never wrongly claims the series is over.

Opening a volume from a series screen also no longer re-fetches the series the
screen is already showing.

## Under the hood

Timings for opening a volume and for a series' volume list are now recorded
like the rest, and show up in **Settings → Diagnostics → Server latency**.
Useful for telling "the server is slow today" apart from "the app has a bug".

---

# Kora 1.5.3

## L'ouverture d'un tome est bien plus rapide

Mesuré sur six ouvertures à froid contre un vrai serveur : **d'environ 12
secondes à environ 4**, et moins d'une seconde quand le serveur répond bien.

Ouvrir un tome lançait cinq requêtes d'un coup et les attendait toutes. Quatre
étaient bon marché. La cinquième cherchait le tome *suivant celui qu'on
ouvrait* — et c'est la seule qui peut échouer : sur le dernier tome d'une
série, Komga répond « pas de tome suivant », et Kora parcourt alors la liste
des séries puis tous les tomes de la série d'après pour savoir où continuer la
lecture. Elle a mesuré 2,8, 11,9 et 14,4 secondes sur trois ouvertures
consécutives, et le temps d'ouverture la suivait au dixième de seconde près.

Personne n'a besoin de cette réponse pour lire la page une. Elle se fait
maintenant une fois la page affichée. Tourner la page après la fin d'un tome
l'attend toujours, donc la page de fin de tome n'annonce jamais à tort que la
série est terminée.

Ouvrir un tome depuis l'écran d'une série ne recharge plus non plus la série
que cet écran affiche déjà.

## Sous le capot

Les durées d'ouverture d'un tome et de chargement de la liste des tomes d'une
série sont désormais enregistrées comme le reste, et apparaissent dans
**Paramètres → Diagnostics → Latence serveur**. Utile pour distinguer « le
serveur est lent aujourd'hui » de « l'application a un bug ».
