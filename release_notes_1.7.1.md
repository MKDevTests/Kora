# Kora 1.7.1

The reader no longer closes itself while reading a comic, and it now uses a
third of the memory it did.

## The crash

Reading a comic in English, the app would disappear without a trace — no error,
nothing in the logs. Android had killed it for running out of memory, which is
the one failure that leaves no crash report behind.

Two things caused it, and both are fixed.

The reader kept decoded pages by **count**, not by size. Sixteen pages of a
manga and sixteen pages of a comic are not the same amount of memory: a comic
page is more than twice the pixels. The limit is now a size, so a manga behaves
exactly as it did and a comic stops well before the edge.

The larger cause was the text recogniser. Its engine asks the system for a
large pool of memory and never gives any of it back, and nothing in the library
can free it afterwards. Kora now refuses that pool. Measured on the same volume
and the same twenty pages: **1 594 MB before, 651 MB after**, with no
measurable change in how long a page takes to read.

For scale: the reader on its own, with recognition off, uses 273 MB.

## Translation

Three defects, each found by replaying five volumes — 1 208 balloons — through
the real pipeline rather than by reading pages on a tablet.

**Characters stopped being renamed.** The spelling repair did not know that
`-kun` and `-san` are honorifics: it turned every `-kun` into a real English
word it recognised, and it rewrote the name in front of it too. Both halves are
left alone now. A hyphen followed by an honorific is the only reliable sign of
a person's name on a page lettered entirely in capitals, and it is used for
that and nothing else.

**A name in the middle of a sentence keeps its capital.** Comic lettering is
all capitals, so a balloon is lowered to ordinary sentence case before being
translated — and a name in the middle came out lowered, which made the
translator read it as a common noun and give it an article.

**Run-together words are split far less often, and much better.** The
recogniser drops the space between words often enough to matter, but the repair
for it was doing more harm than good: over those 1 208 balloons it fired 108
times and was wrong about three times in five, taking proper names apart. It
now fires 15 times and is right in 13 of them.

## What is not done

Two pages in fifty are still recognised twice. It used to be every page, so
this is a residue rather than the original problem, and the cause is not yet
proven.

A word the recogniser reads wrongly still cannot be translated back into the
right one. That remains the ceiling.

---

# Kora 1.7.1

Le lecteur ne se ferme plus tout seul pendant la lecture d'un comic, et il
consomme désormais trois fois moins de mémoire.

## Le plantage

En lisant un comic en anglais, l'application disparaissait sans laisser de
trace — aucune erreur, rien dans les journaux. Android l'avait tuée par manque
de mémoire, la seule défaillance qui ne laisse aucun rapport derrière elle.

Deux causes, toutes deux corrigées.

Le lecteur gardait les pages décodées **en nombre**, pas en taille. Seize pages
de manga et seize pages de comic ne représentent pas la même mémoire : une page
de comic, c'est plus du double de pixels. La limite est maintenant une taille,
donc un manga se comporte exactement comme avant et un comic s'arrête bien
avant la limite.

La cause principale était la reconnaissance de texte. Son moteur réclame au
système une grande réserve de mémoire et n'en rend jamais rien, et rien dans la
bibliothèque ne permet de la libérer après coup. Kora refuse désormais cette
réserve. Mesuré sur le même tome et les mêmes vingt pages : **1 594 Mo avant,
651 Mo après**, sans écart mesurable sur le temps de lecture d'une page.

Pour situer : le lecteur seul, reconnaissance désactivée, occupe 273 Mo.

## Traduction

Trois défauts, tous trouvés en rejouant cinq tomes — 1 208 bulles — dans la
vraie chaîne de traitement, plutôt qu'en lisant des pages sur une tablette.

**Les personnages ne sont plus renommés.** La correction orthographique ignorait
que `-kun` et `-san` sont des marques de politesse : elle transformait chaque
`-kun` en un vrai mot anglais qu'elle reconnaissait, et réécrivait aussi le nom
qui précédait. Les deux moitiés sont désormais laissées intactes. Un trait
d'union suivi d'une marque de politesse est le seul indice fiable d'un nom de
personne sur une page lettrée entièrement en capitales, et il ne sert qu'à ça.

**Un nom au milieu d'une phrase garde sa majuscule.** Le lettrage des comics est
tout en capitales : une bulle est donc rabaissée en casse normale avant d'être
traduite — et un nom au milieu en ressortait en minuscules, ce qui le faisait
prendre pour un nom commun et lui collait un article.

**Les mots recollés sont séparés beaucoup moins souvent, et bien mieux.** La
reconnaissance perd assez souvent l'espace entre deux mots pour que ça compte,
mais la correction faisait plus de mal que de bien : sur ces 1 208 bulles elle
se déclenchait 108 fois et se trompait trois fois sur cinq, en découpant des
noms propres. Elle se déclenche maintenant 15 fois et a raison 13 fois.

## Ce qui n'est pas fait

Deux pages sur cinquante sont encore reconnues deux fois. C'était auparavant
chaque page : il s'agit donc d'un résidu et non du problème initial, et la
cause n'est pas encore établie.

Un mot mal lu par la reconnaissance ne peut toujours pas être retraduit vers le
bon. C'est le plafond.
