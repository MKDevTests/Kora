# Kora 1.7.4

Two fixes in the reader's translation, and the groundwork that found them.

## A sentence split across two balloons is read as one again

Comic lettering routinely runs one sentence across two balloons, marking
the seam with an ellipsis at the end of the first and the start of the
second. The reader has always joined those back together before
translating, because handing half a sentence to a translator produces
nonsense.

The recogniser sometimes reads one dot of a lettered ellipsis as its own
character, leaving it just outside the group. The seam then looked like
ordinary punctuation and the two balloons were translated separately —
one of them coming back as a fragment with no verb, the other as a
sentence that started nowhere.

Measured across nine volumes and 1 927 balloon boundaries: five
sentences recovered, none lost. A single full stop still ends a
sentence, which is what keeps two different speakers from being welded
together.

## A shouted line is no longer answered flatly

Some phrases are answered from a built-in table rather than by the
translator, because a small translation model renders an idiom word for
word. That table is written as a dictionary of expressions, so its
answers carry no punctuation at all — and a balloon lettered with an
exclamation came out flat, when the translator it replaced had kept it.

The answer now takes the balloon's ending as it already took its
capital, and only when it has none of its own: an entry that deliberately
ends in a question mark is left exactly as written.

Six of the forty-four balloons the table answers were losing their
ending this way.

## Where the two came from

Neither was visible before this release. The reader's diagnostic log
said what a balloon started as and what it came back as, with nothing in
between — no way to tell a bad translation from a repair that fired on
the wrong word, or from two balloons that should have been read
together.

The log now names the rule that rewrote each word, whether the built-in
table answered and on which entry, and why each balloon was or was not
joined to the next. Both fixes above came out of the first page read
with it on.

None of this changes what the reader does. It changes what can be found
out about what it does, which is what the last several releases have
been short of.

---

# Kora 1.7.4

Deux correctifs dans la traduction du lecteur, et le travail de fond qui
les a mis au jour.

## Une phrase répartie sur deux bulles se lit de nouveau d'un seul tenant

Le lettrage des comics répartit couramment une phrase sur deux bulles,
en marquant la coupure par des points de suspension à la fin de la
première et au début de la seconde. Le lecteur a toujours recollé ces
bulles avant de traduire : donner une demi-phrase à un traducteur ne
produit rien de compréhensible.

La reconnaissance lit parfois l'un des points d'une ellipse lettrée
comme un caractère à part, qui se retrouve juste en dehors du groupe.
La coupure ressemblait alors à de la ponctuation ordinaire et les deux
bulles étaient traduites séparément — l'une revenant en fragment sans
verbe, l'autre en phrase qui ne commençait nulle part.

Mesuré sur neuf tomes et 1 927 frontières de bulles : cinq phrases
récupérées, aucune perdue. Un point simple termine toujours une phrase,
et c'est ce qui empêche de souder deux interlocuteurs différents.

## Une réplique criée n'est plus rendue à plat

Certaines expressions reçoivent leur réponse d'une table embarquée
plutôt que du traducteur, parce qu'un petit modèle rend une expression
figée mot à mot. Cette table est écrite comme un dictionnaire
d'expressions : ses réponses ne portent aucune ponctuation — et une
bulle lettrée avec un point d'exclamation ressortait plate, là où le
traducteur qu'elle remplaçait l'avait gardé.

La réponse prend désormais la fin de la bulle comme elle prenait déjà sa
majuscule, et seulement si elle n'en a pas : une entrée qui se termine
volontairement par un point d'interrogation reste exactement telle
qu'elle est écrite.

Six des quarante-quatre bulles auxquelles la table répond perdaient leur
fin de cette façon.

## D'où viennent ces deux correctifs

Aucun des deux n'était visible avant cette version. Le journal de
diagnostic du lecteur disait ce qu'une bulle contenait au départ et ce
qu'elle devenait, sans rien entre les deux — impossible de distinguer
une mauvaise traduction d'une réparation qui s'est déclenchée sur le
mauvais mot, ou de deux bulles qui auraient dû être lues ensemble.

Le journal nomme maintenant la règle qui a réécrit chaque mot, indique
si la table embarquée a répondu et sur quelle entrée, et pourquoi chaque
bulle a été ou non rattachée à la suivante. Les deux correctifs
ci-dessus sont sortis de la première page lue avec.

Rien de tout cela ne change ce que fait le lecteur. Cela change ce qu'on
peut savoir de ce qu'il fait, et c'est ce qui manquait aux dernières
versions.
