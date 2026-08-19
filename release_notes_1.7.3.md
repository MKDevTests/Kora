# Kora 1.7.3

Seven changes on top of 1.7.2, all in the reader's translation, all of them
found by reading four series on the tablet and counting every balloon that came
out wrong.

## A family name keeps its capital

1.7.2 taught the reader to keep the capital on a name someone is being
addressed by. That rule only fires after a comma, so a surname standing behind
a given name still went down to lowercase with the rest of the balloon, and the
translator then read it as a common noun.

A lowercase word sitting behind a capitalised given name now keeps its capital,
unless the built-in dictionary knows it as ordinary English — so a character's
full name survives, and "Peter walks" is left alone.

## Two more ways the recogniser misreads a letter

The recogniser reads clean lettering well and stumbles on two specific shapes.

A **u** drawn as a single thin stroke came back as an **l**, turning a word into
something no dictionary carries; the repair now tries that substitution when the
result is a word it knows. A **zero** drawn as a round **o** inside a number was
worse than a typo — a date came out as a word, and the translation of the
sentence changed meaning entirely. A token made only of digits and o's is now
read as a number.

Both repairs are deliberately narrow. They only fire when the token is unknown
and the correction is a word the dictionary carries, which is why they gained
fourteen balloons without breaking any.

## Words run together are split more often

Comic lettering drops the space between two words often enough that the reader
already splits them back apart. The list of words allowed to lead such a split
was missing the auxiliaries and the quantifiers — *have*, *been*, *could*,
*which*, *there*, *some*, *every*, *still*.

Prefixes that make real words were deliberately left out. Adding *out*, *over*,
*up* and *back* was measured first and it broke ordinary English: "outmatched"
became "out matched" and stopped being translated properly.

## Twenty-four more set phrases

The translator renders an idiom word for word, and no amount of tuning fixes
that on a model this size. Each of these was seen failing on a real page:
"head over heels in love" came back as heels over a head, "get your act
together" as an act being performed together, "gasp" as an order to haul
something.

The answers also get their capital back. Most of the shipped table is written
lowercase because it was built as a dictionary of expressions rather than of
lines; a balloon that opens with a capital now gets an answer that does too.

Twenty-two balloons over twenty-four pages, measured on the tablet.

## The reader's OCR speed switch is gone

The reader offered a choice between two recognisers. The faster one was
measured at nearly the same speed as the normal one and gave visibly worse
text, so it was never the right choice. The switch and the second model are
removed; the reader always uses the one that reads better.

---

# Kora 1.7.3

Sept changements par-dessus la 1.7.2, tous dans la traduction du lecteur, tous
trouvés en lisant quatre séries sur la tablette et en comptant chaque bulle
sortie fausse.

## Un nom de famille garde sa majuscule

La 1.7.2 avait appris au lecteur à garder la majuscule d'un nom par lequel on
interpelle quelqu'un. Cette règle ne se déclenche qu'après une virgule : un nom
de famille placé derrière un prénom redescendait donc en minuscules avec le
reste de la bulle, et le traducteur le prenait alors pour un nom commun.

Un mot en minuscules placé derrière un prénom en majuscule garde désormais sa
capitale, sauf si le dictionnaire embarqué le connaît comme de l'anglais
courant — le nom complet d'un personnage survit, et « Peter walks » n'est pas
touché.

## Deux façons de plus dont la reconnaissance se trompe de lettre

La reconnaissance lit bien un lettrage propre et bute sur deux formes précises.

Un **u** tracé d'un seul trait fin revenait en **l**, transformant un mot en
quelque chose qu'aucun dictionnaire ne porte ; la réparation tente maintenant
cette substitution quand le résultat est un mot qu'elle connaît. Un **zéro**
tracé comme un **o** rond à l'intérieur d'un nombre était pire qu'une coquille :
une date sortait en toutes lettres et le sens de la phrase changeait
complètement. Un mot composé uniquement de chiffres et de o est désormais lu
comme un nombre.

Les deux réparations sont volontairement étroites. Elles ne se déclenchent que
si le mot est inconnu et si la correction est un mot du dictionnaire, ce qui
leur a permis de gagner quatorze bulles sans en casser aucune.

## Les mots collés sont séparés plus souvent

Le lettrage des comics supprime assez souvent l'espace entre deux mots pour que
le lecteur les resépare déjà. Il manquait à la liste des mots autorisés à ouvrir
une telle coupe les auxiliaires et les quantifieurs — *have*, *been*, *could*,
*which*, *there*, *some*, *every*, *still*.

Les préfixes qui forment de vrais mots ont été délibérément écartés. Ajouter
*out*, *over*, *up* et *back* a été mesuré d'abord, et cassait de l'anglais
correct : « outmatched » devenait « out matched » et cessait d'être traduit
convenablement.

## Vingt-quatre expressions figées de plus

Le traducteur rend une expression mot à mot, et aucun réglage ne corrige ça sur
un modèle de cette taille. Chacune de celles-ci a été vue échouer sur une page
réelle : « head over heels in love » revenait en talons par-dessus la tête,
« get your act together » en un numéro joué ensemble, « gasp » en un ordre de
haler quelque chose.

Les réponses récupèrent aussi leur majuscule. L'essentiel de la table livrée est
écrit en minuscules parce qu'elle a été construite comme un dictionnaire
d'expressions et non de répliques ; une bulle qui commence par une capitale
reçoit maintenant une réponse qui en a une aussi.

Vingt-deux bulles sur vingt-quatre pages, mesuré sur la tablette.

## Le choix de vitesse de reconnaissance disparaît

Le lecteur proposait de choisir entre deux moteurs de reconnaissance. Le plus
rapide a été mesuré à une vitesse presque identique au normal pour un texte
visiblement moins bon : ce n'était jamais le bon choix. Le réglage et le second
modèle sont retirés, le lecteur utilise toujours celui qui lit le mieux.
