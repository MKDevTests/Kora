# Kora 1.6.2

Page translation gets three fixes, each one traced to a measurement rather than
a guess.

**This feature is still in development.** It is on by choice, not by default,
and it will get things wrong. What follows is what changed, not a promise.

## A balloon no longer runs into the one next to it

A sentence lettered across two balloons is sent to the translator whole, because
half a sentence translates into half an idea. The rule for deciding that two
balloons belong together read a full stop as if it were an ellipsis — so a
balloon that had simply finished its sentence was joined to whatever came after
it, translated as one sentence, and then split back across both.

On a real page that put two different speakers into one sentence and cut a word
in half across the seam: one balloon ended on "un chat non…" and its neighbour
opened on "…formé". Now only a real ellipsis joins two balloons. A full stop
ends the sentence.

## Misread letters are repaired before translating

A single stroke can invert a sentence. Comic lettering draws `u` in a way the
recogniser reads back as two thin strokes, so `UNTRAINED` came back as
`UINTRAINED` — and a translator handed a word it does not know drops the part it
cannot place and returns a confident sentence meaning the opposite. "An
untrained cat" became "a trained cat".

Kora now checks each word against a list of 37,000 English words. A word that is
not in the list, and has exactly one reading under those known confusions that
is, gets corrected. Two possible readings means it is left alone — guessing
between them is how a repair starts inventing words of its own. Over 28 pages
this corrected seven words and damaged none.

## The overlay is lettered, not captioned

Translated text is drawn in bold. Comic lettering is heavy, and a balloon filled
with ordinary body text reads as a caption pasted onto the drawing rather than
as something a character said.

## More expressions recognised

The expression table grows from 1,990 to 2,046 entries, mostly ordinary
workplace and effort vocabulary — the kind a slice-of-life volume is full of.
These are answered from the table before the translator sees them, because a
small model has no idiomatic reading to reach for. Nothing to download; they
ship with the app.

## If translations look poor, check which engine is running

Kora uses the downloadable translation engine when its files are present and
falls back to the one bundled with Android when they are not. That fallback used
to be silent, which made a fresh install look like a quality regression. It is
now written to the log at startup of the first translated page.

The bundled engine leaves words it does not know in English. If you see one, the
downloadable engine is not installed — fetch it from the OCR tab of the reader's
settings panel.

---

# Kora 1.6.2

Trois correctifs sur la traduction de page, chacun remonté à une mesure et non à
une intuition.

**Cette fonctionnalité est toujours en développement.** Elle s'active par choix,
pas par défaut, et elle se trompera. Ce qui suit décrit ce qui a changé, pas une
promesse.

## Une bulle ne déborde plus sur sa voisine

Une phrase lettrée sur deux bulles part entière chez le traducteur, parce qu'une
demi-phrase se traduit en demi-idée. La règle qui décidait que deux bulles vont
ensemble lisait un point final comme des points de suspension — une bulle qui
avait simplement terminé sa phrase était donc soudée à la suivante, traduite
comme une seule phrase, puis redécoupée entre les deux.

Sur une vraie page, ça mettait deux locuteurs différents dans la même phrase et
coupait un mot en deux sur la couture : une bulle finissait par « un chat non… »
et sa voisine ouvrait sur « …formé ». Désormais, seuls de vrais points de
suspension réunissent deux bulles. Un point final termine la phrase.

## Les lettres mal lues sont réparées avant la traduction

Un seul trait peut inverser une phrase. Le lettrage BD dessine le `u` d'une
façon que la reconnaissance rend en deux traits fins : `UNTRAINED` revenait en
`UINTRAINED`, et un traducteur à qui l'on donne un mot inconnu abandonne la
partie qu'il ne situe pas et renvoie une phrase assurée qui dit le contraire.
« Un chat non dressé » devenait « un chat dressé ».

Kora vérifie maintenant chaque mot contre une liste de 37 000 mots anglais. Un
mot absent de la liste, qui a exactement une lecture valide parmi ces confusions
connues, est corrigé. Deux lectures possibles : on n'y touche pas — choisir
entre les deux, c'est le moment où une réparation se met à inventer ses propres
mots. Sur 28 pages, sept mots corrigés, aucun abîmé.

## L'incrustation est lettrée, pas sous-titrée

Le texte traduit est dessiné en gras. Le lettrage BD est épais, et une bulle
remplie de texte courant se lit comme une légende collée sur le dessin plutôt
que comme une parole de personnage.

## Plus d'expressions reconnues

La table d'expressions passe de 1 990 à 2 046 entrées, surtout du vocabulaire
courant de travail et d'effort — celui dont une tranche de vie est remplie. Ces
expressions sont résolues avant même que le traducteur les voie, parce qu'un
petit modèle n'a aucune lecture idiomatique à sa disposition. Rien à télécharger,
elles sont dans l'application.

## Si les traductions semblent mauvaises, vérifiez le moteur

Kora utilise le moteur de traduction téléchargeable quand ses fichiers sont
présents, et retombe sur celui fourni avec Android sinon. Cette bascule était
silencieuse, ce qui faisait passer une installation neuve pour une régression de
qualité. Elle est maintenant écrite dans le journal à la première page traduite.

Le moteur d'Android laisse en anglais les mots qu'il ne connaît pas. Si vous en
voyez un, c'est que le moteur téléchargeable n'est pas installé — récupérez-le
depuis l'onglet OCR du panneau de réglages du lecteur.
