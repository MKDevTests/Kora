# Kora 1.7.0

Japanese pages can now be translated.

**Page translation is still in development**, and Japanese is the newest and
roughest part of it. It is on by choice, not by default, and it will get things
wrong. What follows is what changed, not a promise.

## Reading a Japanese page

Turn translation on, set the source language to Japanese, and the rest happens
by itself: the recogniser switches to Japanese, the columns are read right to
left and top to bottom the way the page is lettered, and the models are fetched
in one step.

The translation goes through English. No one publishes a Japanese-to-French
model for the engine Kora uses, and the direct model that does exist was
measured against this route and lost — it invents character names, which is
worse than a flat translation, and it is more than twice the size. Two hops
cost about a third of a second per page, which is a tenth of what recognising
the page costs.

This means one known weakness, and it is worth stating plainly: an ambiguity
resolved wrongly in English cannot be recovered in French. Those failures read
as bland rather than as nonsense, which is the better of the two.

## Katakana that used to become character names

Japanese prose writes a word one way; a manga writes it another to make it
sound rough, or to shout. The engine has never seen the second spelling, so it
spells it out in Latin letters and the result reads as somebody's name — a
balloon asking who the target is came back naming a character who does not
exist.

Kora now rewrites those spellings into ordinary Japanese before translating.
257 of them, and each one was decided by running it through the engine both
ways rather than by judgement: it is only in the table if the engine measurably
did better with it. Three quarters of the words considered were left alone
because the engine already read them correctly, and rewriting those made the
result worse.

A set phrase table for Japanese comes with it, on the same rule the English one
uses: a balloon that *is* the phrase, exactly, never a fragment inside a longer
sentence.

## What is not done

Sound effects are not translated. Onomatopoeia has no ordinary-Japanese form to
rewrite into, and that was measured rather than assumed.

Recognition still misreads, and a misread word cannot be translated back into
the right one. That is the next piece of work.

---

# Kora 1.7.0

Les pages en japonais peuvent maintenant être traduites.

**La traduction de page est toujours en développement**, et le japonais en est
la partie la plus récente et la plus fragile. Elle s'active par choix, pas par
défaut, et elle se trompera. Ce qui suit décrit ce qui a changé, pas une
promesse.

## Lire une page japonaise

Activez la traduction, choisissez le japonais comme langue source, et le reste
suit tout seul : la reconnaissance bascule en japonais, les colonnes sont lues
de droite à gauche et de haut en bas comme la page est lettrée, et les modèles
sont récupérés en une seule fois.

La traduction passe par l'anglais. Personne ne publie de modèle japonais vers
français pour le moteur qu'utilise Kora, et le modèle direct qui existe a été
mesuré contre ce chemin et a perdu : il invente des noms de personnages, ce qui
est pire qu'une traduction plate, et il pèse plus du double. Les deux étapes
coûtent environ un tiers de seconde par page, soit un dixième de ce que coûte
la reconnaissance.

Cela implique une faiblesse connue, et autant la dire franchement : une
ambiguïté tranchée de travers en anglais ne peut plus être rattrapée en
français. Ces échecs-là sont plats plutôt qu'absurdes, ce qui est le moins grave
des deux.

## Les katakana qui devenaient des noms de personnages

Le japonais courant écrit un mot d'une façon ; un manga l'écrit autrement pour
le faire sonner brutal, ou pour crier. Le moteur n'a jamais vu la seconde
graphie : il la transcrit en lettres latines et le résultat se lit comme le nom
de quelqu'un — une bulle qui demandait quelle était la cible revenait en citant
un personnage qui n'existe pas.

Kora réécrit désormais ces graphies en japonais ordinaire avant de traduire.
257 d'entre elles, et chacune a été tranchée en la passant dans le moteur des
deux façons plutôt qu'au jugé : elle n'est dans la table que si le moteur a fait
mesurablement mieux avec. Les trois quarts des mots examinés ont été laissés
tels quels parce que le moteur les lisait déjà correctement, et les réécrire
dégradait le résultat.

Une table d'expressions figées en japonais l'accompagne, avec la même règle que
la version anglaise : une bulle qui **est** l'expression, exactement, jamais un
fragment au milieu d'une phrase plus longue.

## Ce qui n'est pas fait

Les onomatopées ne sont pas traduites. Elles n'ont pas de forme en japonais
courant vers laquelle les réécrire, et cela a été mesuré, pas supposé.

La reconnaissance se trompe encore, et un mot mal lu ne peut pas être retraduit
vers le bon. C'est le chantier suivant.
