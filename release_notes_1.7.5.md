# Kora 1.7.5

A page that fails to load can now be reloaded on its own, and the reason
it kept failing has been fixed.

## One page, one reload

A page that failed came back as a black panel with a line of red text,
and there was nothing to do about it: turning back and forth changed
nothing, only leaving the book and opening it again did. There is now a
Reload button on the failed page, and the error says which error it was
rather than only that there was one.

The button matters because of what sat underneath it. A page already
being loaded is not requested twice — the reader keeps the pending work
and waits on it. But a piece of work that had *finished badly* looked
exactly like one still in flight, so every later request for that page
was answered with the stored failure, instantly and forever. That is
why one page could stay broken while the next two loaded fine: nothing
was retrying, the failure was being served from memory. Reloading now
drops it first.

## The server was never the problem

The page failures were timeouts, and the reason for them was ours.

Measured on the same server, the same evening: the shelf query that was
dying answered `curl` in 1.6 seconds with the app closed, and took 23.1,
24.1 and 25.6 seconds from inside the app. Twice it went past the
30-second limit and the shelf gave up after 31.2 seconds without having
received a single byte.

Nothing was slow. Eleven shelves were being asked for at once, on a
server that was also sending covers, and each request waited behind the
others. The reader did the same thing with its page downloads, and the
refresh that runs when a book is closed did it a third time — worse, it
ran two and three times *concurrently*, because closing the reader,
returning to the home screen and the server's own progress notification
each started their own copy of it. The same query went out three times
in parallel, taking 7.8, 9.3 and 9.4 seconds, while the book being
opened queued behind all three.

Page downloads, home shelves and progress refreshes are now capped at
four requests at a time, and overlapping refreshes share one. The
network timeout moved from 30 to 60 seconds as a floor, not as the fix.

On the first run after the change: no timeout at all, the first four
shelves in 2.3 seconds, and 73 pages read end to end without a single
failure. That is not proof the fault is gone — 73 clean pages put its
rate below roughly 4%, no lower — but it is the first session in a while
with nothing in it.

## Japanese: three more misread characters

Two Chinese character forms and one misreading are now corrected before
translation, and a chapter heading with a stray bracket between the word
and its number keeps the word "chapter" instead of losing it. Nine
balloons across seven volumes — 0.14% of the 6 899 scanned, and reported
as the small thing it is.

One change was measured and refused: rewriting the stat-screen labels.
Every variant tried either broke a phrase elsewhere or invented a name,
and rewriting one label moved the translation of its neighbours without
them being touched.

## The bench was lying

The tool these Japanese fixes are measured with was loading two glossary
entries instead of the shipped 257, whenever it ran alongside the other
tests. It reported the reader as worse than it is — 13 changed lines
where there were 9, and 26 across the 5 213 balloons of the reserve
volumes.

Nothing shipped was affected; every measurement taken with it was. It
does not break when it goes wrong, it answers, which is the kind of
failure worth naming.

---

# Kora 1.7.5

Une page qui échoue peut maintenant être rechargée toute seule, et la
raison pour laquelle elle restait en échec est corrigée.

## Une page, un rechargement

Une page en échec revenait en cadre noir avec une ligne rouge, sans
recours : revenir en arrière et repasser n'y changeait rien, il fallait
quitter le tome et le rouvrir. Un bouton Recharger est désormais posé
sur la page fautive, et le message dit de quelle erreur il s'agit, pas
seulement qu'il y en a eu une.

Ce bouton compte à cause de ce qu'il y avait dessous. Une page déjà en
cours de chargement n'est pas redemandée deux fois : le lecteur garde le
travail en attente et s'y accroche. Mais un travail *terminé en échec*
ressemblait trait pour trait à un travail encore en cours, si bien que
toute demande ultérieure de cette page recevait l'échec mémorisé,
immédiatement et pour toujours. C'est pour cela qu'une page pouvait
rester cassée pendant que les deux suivantes s'affichaient : rien ne
réessayait, l'échec était servi depuis la mémoire. Recharger le jette
d'abord.

## Le serveur n'y était pour rien

Ces échecs de page étaient des dépassements de délai, et la cause était
chez nous.

Mesuré sur le même serveur, le même soir : la requête d'étagère qui
mourait répondait à `curl` en 1,6 seconde avec l'application fermée, et
prenait 23,1, 24,1 puis 25,6 secondes depuis l'application. Deux fois
elle a dépassé la limite de 30 secondes et l'étagère a abandonné au bout
de 31,2 secondes sans avoir reçu un seul octet.

Rien n'était lent. Onze étagères étaient demandées d'un coup, à un
serveur qui envoyait aussi les couvertures, et chaque requête attendait
derrière les autres. Le lecteur faisait la même chose avec ses
téléchargements de pages, et le rafraîchissement déclenché à la
fermeture d'un tome une troisième fois — pire, il partait deux et trois
fois *en parallèle*, parce que quitter le lecteur, revenir à l'accueil
et la notification de progression du serveur en lançaient chacun leur
exemplaire. La même requête est partie trois fois de front, en 7,8, 9,3
et 9,4 secondes, pendant que le tome qu'on ouvrait faisait la queue
derrière les trois.

Téléchargements de pages, étagères d'accueil et rafraîchissements sont
désormais plafonnés à quatre requêtes simultanées, et les
rafraîchissements qui se chevauchent n'en font plus qu'un. Le délai
réseau passe de 30 à 60 secondes comme filet, pas comme correctif.

Au premier essai après la correction : aucun dépassement, les quatre
premières étagères en 2,3 secondes, et 73 pages lues d'affilée sans un
seul échec. Ce n'est pas une preuve que la panne a disparu — 73 pages
propres placent son taux sous 4% environ, pas plus bas — mais c'est la
première séance depuis longtemps où il n'y a rien à signaler.

## Japonais : trois caractères mal lus de plus

Deux formes chinoises et une confusion de caractère sont corrigées avant
traduction, et un titre de chapitre portant une parenthèse égarée entre
le mot et son numéro conserve le mot « chapitre » au lieu de le perdre.
Neuf bulles sur sept tomes — 0,14% des 6 899 examinées, et annoncé pour
la petite chose que c'est.

Une modification a été mesurée puis refusée : réécrire les intitulés des
fiches de statistiques. Chaque variante essayée cassait une phrase
ailleurs ou inventait un nom, et réécrire un intitulé déplaçait la
traduction de ses voisins sans qu'on y touche.

## Le banc mentait

L'outil avec lequel ces correctifs japonais sont mesurés chargeait deux
entrées de glossaire au lieu des 257 embarquées, dès qu'il tournait avec
les autres tests. Il donnait le lecteur pour plus mauvais qu'il n'est —
13 lignes modifiées là où il y en a 9, et 26 sur les 5 213 bulles des
tomes de réserve.

Rien de ce qui est livré n'était touché ; toutes les mesures prises avec
l'étaient. Il ne casse pas quand il se trompe, il répond, et c'est le
genre de panne qui mérite d'être nommé.
