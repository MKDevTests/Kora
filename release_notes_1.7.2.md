# Kora 1.7.2

Two fixes on top of 1.7.1, both in the reader's translation.

## Pages are no longer recognised twice

The last of it. Every page used to be read by the recogniser twice over,
five seconds apart, for identical results; 1.7.1 removed most of that and
left two pages in fifty still doing it.

The cause turned out to be one line in the wrong place. A scan is
de-duplicated by a lock, but the result was stored *after* that lock was
released — so a second scan that had been waiting behind the first for its
whole duration took the lock the instant it came free, looked in the cache
before the first had written to it, and started again from scratch. The
result is stored under the lock now.

Verified over forty pages: twenty-seven recognitions for twenty-seven
distinct pages, none repeated. That is roughly three seconds of work saved
every dozen pages, and the same in battery.

## A character's name is no longer translated

Comic lettering is all capitals, so a balloon is lowered to ordinary
sentence case before being translated. A character's name went down with
it, and the translator then read it as a common noun: someone addressed by
name came back with an article bolted on, or lost the name altogether.

A word the built-in dictionary does not know, sitting where a name is
spoken to, keeps its capital now.

The narrow rule is deliberate. Applied to the whole sentence it was
measured over 1 208 balloons and did more harm than good — ordinary
English the dictionary happens not to carry stopped being translated at
all, and the capitals leaked into the French. Restricted to a name being
spoken to, the same measurement gives about twenty corrections against two
regressions.

---

# Kora 1.7.2

Deux correctifs par-dessus la 1.7.1, tous deux dans la traduction du lecteur.

## Les pages ne sont plus reconnues deux fois

C'en est fini. Chaque page était lue deux fois par la reconnaissance, à cinq
secondes d'intervalle, pour un résultat identique ; la 1.7.1 en avait retiré
l'essentiel et laissait deux pages sur cinquante continuer.

La cause était une ligne au mauvais endroit. Un scan est dédoublonné par un
verrou, mais le résultat était rangé **après** la libération de ce verrou :
un second scan, en attente derrière le premier pendant toute sa durée,
prenait le verrou à l'instant où il se libérait, regardait dans le cache
avant que le premier n'y ait écrit, et repartait de zéro. Le résultat est
maintenant rangé sous le verrou.

Vérifié sur quarante pages : vingt-sept reconnaissances pour vingt-sept
pages distinctes, aucune répétée. Cela représente environ trois secondes de
travail économisées toutes les douzaines de pages, et autant de batterie.

## Le nom d'un personnage n'est plus traduit

Le lettrage des comics est tout en capitales : une bulle est donc rabaissée
en casse normale avant d'être traduite. Le nom d'un personnage descendait
avec, et le traducteur le prenait alors pour un nom commun — quelqu'un qu'on
interpelle revenait avec un article collé devant, ou perdait son nom.

Un mot que le dictionnaire embarqué ne connaît pas, placé là où l'on
interpelle quelqu'un, garde désormais sa majuscule.

La règle est volontairement étroite. Appliquée à la phrase entière, elle a
été mesurée sur 1 208 bulles et faisait plus de mal que de bien : l'anglais
courant que le dictionnaire ne contient pas cessait d'être traduit, et les
majuscules débordaient dans le français. Restreinte à un nom qu'on
interpelle, la même mesure donne une vingtaine de corrections contre deux
régressions.
