### English

**Tell the suggestions what you think**

Two buttons on every suggestion card, in the library's "For you" tab and in a series' "Similar" tab:

- **To read** — adds the series to your "to read" list without opening it.
- **Not interested** — the card goes away and never comes back. It also counts against your profile: what that series is made of loses weight, so you don't have to repeat the same tap on every near-identical series. "Reset not interested (N)" in the "For you" header undoes all of it.

**"For you" now says which series it is extrapolating from**

Suggestions are grouped under headings like **"Because you liked Berserk"**, and each card shows the terms it shares with *that* series rather than the top term of your whole profile. A series you only finished says "Because you read X" — a series is not something you liked until you say so, and a rating is what says it.

**The suggestion engine was listening to the wrong series**

Three problems found on the real libraries and fixed with measurements, not guesses:

- A series carrying a **single indexed term** put its entire weight on that term. One such series — its only term was its publisher — made "Publisher: Kurokawa" the headline reason of nine suggestions out of ten, pulling horror and comedy in under a historical drama. Profile series are now floored at half the library's average length: no effect on a normal profile, decisive on that one.
- **Well-tagged series were structurally buried.** Explaining a suggestion divided by the source's full length, so a rich series needed shared terms proportional to the square root of its own size to compete: a 73-term series rated 5 stars headed 1 card out of 40. It now heads 5.
- A **fresh rating lost to a pile of unrated reads** when headings were handed out. Rated and favourited series come first now, and earn a heading on two cards instead of three.

A section can no longer rest on the publisher alone, nor on a series carrying fewer than three terms: an imprint is not a taste, and a series that says nothing about itself cannot explain another one.

**Fixes from upstream Komelia**

- The library **tag filter** listed series tags only, so every tag carried by books was missing from it.
- The offline **RAR** reader leaked a file descriptor on every page read.
- **Predictive back** (Android 13+) was never enabled, so the reader's back gesture had no animation.

---

### Français

**Dites aux suggestions ce que vous en pensez**

Deux boutons sur chaque carte de suggestion, dans l'onglet « For you » d'une bibliothèque comme dans l'onglet « Similar » d'une série :

- **À lire** — ajoute la série à votre liste « à lire » sans l'ouvrir.
- **Pas intéressé** — la carte disparaît et ne revient jamais. Cela compte aussi contre votre profil : ce dont la série est faite perd du poids, pour ne pas avoir à répéter le même geste sur chaque série quasi identique. « Reset not interested (N) », dans l'en-tête de « For you », annule le tout.

**« For you » dit maintenant de quelle série il part**

Les suggestions sont regroupées sous des titres du type **« Because you liked Berserk »**, et chaque carte affiche les termes qu'elle partage avec *cette* série, au lieu du terme dominant de votre profil entier. Une série seulement terminée donne « Because you read X » : une série n'est pas aimée tant que vous ne l'avez pas dit, et c'est la note qui le dit.

**Le moteur de suggestions écoutait les mauvaises séries**

Trois problèmes trouvés sur les vraies bibliothèques et corrigés par la mesure, pas à l'intuition :

- Une série ne portant **qu'un seul terme indexé** mettait tout son poids dessus. L'une d'elles — son seul terme était son éditeur — faisait de « Publisher: Kurokawa » la raison n°1 de neuf suggestions sur dix, ramenant de l'horreur et de la comédie sous un drame historique. Les séries du profil sont désormais plafonnées par le bas à la moitié de la longueur moyenne de la bibliothèque : sans effet sur un profil normal, décisif sur ce cas.
- Les **séries bien taguées étaient structurellement enterrées.** Expliquer une suggestion divisait par la longueur complète de la source : une série riche devait partager un nombre de termes proportionnel à la racine carrée de sa propre taille. Une série de 73 termes notée 5 étoiles ne portait qu'une carte sur 40 ; elle en porte cinq.
- Une **note fraîche perdait contre une pile de séries lues non notées** au moment d'attribuer les titres de section. Les séries notées et favorites passent devant et suffisent à deux cartes au lieu de trois.

Une section ne peut plus reposer sur le seul éditeur, ni sur une série de moins de trois termes : une maison d'édition n'est pas un goût, et une série qui ne dit rien d'elle-même ne peut pas en expliquer une autre.

**Corrections venues de Komelia (amont)**

- Le **filtre Tags** d'une bibliothèque ne listait que les tags de séries : tous les tags portés par les tomes en étaient absents.
- Le lecteur **RAR** hors ligne fuyait un descripteur de fichier à chaque page lue.
- Le **retour prédictif** (Android 13+) n'était pas activé : le geste de retour du lecteur n'avait aucune animation.
