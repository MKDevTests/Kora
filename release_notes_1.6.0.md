# Kora 1.6.0

Page translation, and a lot of work on making it readable.

**This feature is still in development.** It is on by choice, not by default,
and it will get things wrong. What follows is what changed, not a promise.

## Reading a page in French

Kora can recognise the lettering on a page and show a French translation over
each balloon. Everything happens on the device: no account, no key, no quota,
and nothing leaves your server.

Two sets of files are needed, and both are downloaded from inside the app:

- **Text recognition** — Settings, Image reader, under the OCR section.
- **Translation** — the OCR tab of the reader's settings panel, where a
  second engine can be fetched. It reads noticeably better French than the
  one bundled with Android, and Kora uses it whenever it is present.

Common expressions ship with the app itself; there is nothing to download for
those.

## Balloons are read in the right order

Nothing decided the order of the balloons on a page — they reached the
translator in whatever order the recogniser happened to emit them. A
conversation therefore arrived shuffled, and each balloon was translated as
though it stood alone.

Balloons are now read by row, right to left or left to right depending on the
book. Rows are worked out by band rather than by comparing edges: two
balloons meant to be read one after the other are never level to the pixel,
and comparing them decides on the noise.

## A sentence spread over several balloons is translated as one sentence

Comic lettering routinely runs a sentence across two or three balloons. Each
one used to be translated by itself, which is most of why the result read as
nonsense — a translator given two words of a sentence returns two words of
nonsense.

Kora now recognises the convention: a balloon that spills over ends in an
ellipsis and the next one opens with one. Both sides have to agree, since one
alone is ordinary trailing off. The sentence is translated whole and the
result spread back over the balloons it came from.

## Expressions that a translator gets wrong word by word

"Something the matter?" came back as "Quelque chose la matière ?". A small
translation model has no idiomatic reading to reach for, so it goes word by
word, and no amount of tuning reaches that.

Around two thousand everyday English expressions now ship with their French
and are answered before the engine sees them. Where an expression has more
than one possible reading in French, Kora deliberately does not choose — it
hands it to the engine rather than guessing.

## Fewer black rectangles over the artwork

- **Sound effects lettered onto the drawing** were being translated and then
  covered with an opaque panel. So were the runs of nonsense that recognition
  invents over hatching and screentone. Both are dropped now.
- **An animal's cry in a speech balloon** is a sound, not dialogue. A cat gets
  a balloon like anyone else, so "Meow!" arrives punctuated like a line and
  was translated as one.
- **Names** are held back and put back afterwards, so they come through as
  themselves.

## Vertical strips

Long vertical pages could leave the recogniser spinning without ever
finishing, and could take the app down with it. A page eleven times taller
than it is wide is far outside what the recogniser is built for. Those pages
are now read in bands, with enough overlap that a line of lettering sitting on
a seam is still read whole.

## Known limitations

- Lettering drawn over artwork, or with the balloon's own outline touching it,
  is still misread. The recogniser is given the whole box and reads what is in
  it.
- Japanese is not supported yet.
- A page takes a few seconds. Almost all of it is text recognition; speeding
  that up is the next piece of work.

---

# Kora 1.6.0

La traduction de page, et beaucoup de travail pour la rendre lisible.

**Cette fonctionnalité est encore en cours de développement.** Elle s'active
volontairement, pas par défaut, et elle se trompera. Ce qui suit décrit ce qui
a changé, pas une promesse.

## Lire une page en français

Kora peut reconnaître le lettrage d'une page et afficher une traduction
française par-dessus chaque bulle. Tout se passe sur l'appareil : aucun
compte, aucune clé, aucun quota, et rien ne sort de votre serveur.

Deux jeux de fichiers sont nécessaires, tous deux téléchargeables depuis
l'application :

- **Reconnaissance de texte** — Réglages, Lecteur d'images, section OCR.
- **Traduction** — onglet OCR du panneau de réglages du lecteur, où un second
  moteur peut être récupéré. Il rend un français nettement meilleur que celui
  fourni avec Android, et Kora l'utilise dès qu'il est présent.

Les expressions courantes sont livrées avec l'application ; rien à
télécharger pour celles-là.

## Les bulles sont lues dans le bon ordre

Rien ne décidait de l'ordre des bulles sur une page : elles arrivaient au
traducteur dans l'ordre où le module de reconnaissance les avait émises. Une
conversation arrivait donc mélangée, et chaque bulle était traduite comme si
elle était seule.

Les bulles sont maintenant lues par rangée, de droite à gauche ou l'inverse
selon l'ouvrage. Les rangées sont déterminées par bande plutôt qu'en comparant
les bords : deux bulles à lire l'une après l'autre ne sont jamais alignées au
pixel près, et les comparer revient à trancher sur du bruit.

## Une phrase étalée sur plusieurs bulles est traduite comme une phrase

Le lettrage BD étale couramment une phrase sur deux ou trois bulles. Chacune
était traduite séparément, ce qui explique l'essentiel du résultat
incompréhensible : donnez deux mots d'une phrase à un traducteur, il rend deux
mots incompréhensibles.

Kora reconnaît maintenant la convention : une bulle qui déborde se termine par
des points de suspension et la suivante en ouvre. Les deux côtés doivent être
d'accord, car un seul signifie simplement une phrase laissée en suspens. La
phrase est traduite entière, puis le résultat réparti sur les bulles d'origine.

## Les expressions qu'un traducteur rend mot à mot

« Something the matter? » revenait en « Quelque chose la matière ? ». Un petit
modèle de traduction n'a aucune lecture idiomatique à aller chercher, il
traduit donc mot à mot, et aucun réglage n'y change quoi que ce soit.

Environ deux mille expressions anglaises courantes sont désormais livrées avec
leur équivalent français et traitées avant que le moteur ne les voie. Quand
une expression a plusieurs lectures possibles en français, Kora s'abstient
volontairement de choisir et la confie au moteur plutôt que de deviner.

## Moins de rectangles noirs sur le dessin

- **Les onomatopées dessinées sur l'image** étaient traduites puis recouvertes
  d'un panneau opaque. Les suites de charabia que la reconnaissance invente
  sur les hachures et les trames aussi. Les deux sont maintenant écartées.
- **Le cri d'un animal dans une bulle** est un son, pas du dialogue. Un chat a
  une bulle comme tout le monde, donc « Meow ! » arrive ponctué comme une
  réplique et était traduit comme telle.
- **Les noms propres** sont mis de côté puis restitués, afin de revenir tels
  qu'ils sont.

## Bandes verticales

Les pages verticales très longues pouvaient laisser la reconnaissance tourner
sans jamais aboutir, et emporter l'application avec elles. Une page onze fois
plus haute que large sort très largement de ce pour quoi le modèle est conçu.
Ces pages sont désormais lues par bandes, avec assez de recouvrement pour
qu'une ligne de lettrage à cheval sur une jointure soit lue entière.

## Limites connues

- Le lettrage dessiné sur l'image, ou dont le contour de bulle touche le
  texte, reste mal lu. Le modèle reçoit la boîte entière et lit ce qui s'y
  trouve.
- Le japonais n'est pas encore pris en charge.
- Une page prend quelques secondes. La quasi-totalité est de la reconnaissance
  de texte ; l'accélérer est le prochain chantier.
