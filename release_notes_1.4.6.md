### English

**Other editions, where you actually look**

A series linked to another version — another language, a colour edition, another printing — now says so in its details, under **Also available as**, right below the genres. The chip says what the link *is* ("Colour edition", "Another language · EN"), because the other edition is the same work and usually carries the same title: a chip repeating the name you are already looking at tells you nothing. The name appears only when it differs. Tapping it opens that edition.

**Genres you can follow**

Genres are chips now rather than a comma-separated line, and they lead to the genre listing of the same library. They also appear on a volume, read from the local index — the volume screen never loads its series, and asking the server for three words would be absurd.

**Adding a link shows you what you are linking**

The search results carried the title alone, which is useless exactly when it matters: several series share it. Each result now shows its cover, language, publisher and volume count.

**Rebuilding the index, from one place**

Settings → Maintenance rebuilds the term index of every library, with progress. Until now it could only be done one library at a time, from the Similar tab of some series inside it.

**Fixes**

- **Crash** when tapping, in a reading-order graph, the box of the series you are already on. The screen is keyed by its series id, so it was pushed onto the stack twice.
- Author roles were never translatable — the code capitalised the raw value instead of naming it. They read Scénario, Dessin, Encrage, Couleurs… in French now.
- The Home shelf patterns kept their English constant names (Forgotten, AlmostFinished, Discover) in the editor: the dropdowns printed the enum itself.
- A genre chip filtered on the slug instead of the tag, so Fantasy on a series returned five results where the genre holds five hundred and forty-five.

---

### Français

**Les autres éditions, là où vous regardez**

Une série liée à une autre version — autre langue, édition couleur, autre tirage — l'annonce désormais dans ses détails, sous **Également disponible en**, juste sous les genres. La puce dit ce qu'**est** le lien (« Édition couleur », « Autre langue · EN »), parce que l'autre édition est la même œuvre et porte le plus souvent le même titre : une puce répétant le nom que vous avez sous les yeux n'apprend rien. Le nom n'apparaît que s'il diffère. Un appui ouvre cette édition.

**Des genres qu'on peut suivre**

Les genres sont des puces plutôt qu'une ligne de texte, et mènent à la liste du genre dans la même bibliothèque. Ils apparaissent aussi sur un tome, lus dans l'index local — l'écran d'un tome ne charge jamais sa série, et interroger le serveur pour trois mots n'aurait pas de sens.

**Ajouter un lien montre ce que vous liez**

Les résultats de recherche n'affichaient que le titre, ce qui est inutile précisément quand ça compte : plusieurs séries le partagent. Chaque résultat montre maintenant sa couverture, sa langue, son éditeur et son nombre de tomes.

**Reconstruire l'index, depuis un seul endroit**

Réglages → Maintenance reconstruit l'index de termes de toutes les bibliothèques, avec la progression. Jusqu'ici, c'était possible uniquement bibliothèque par bibliothèque, depuis l'onglet Similar d'une série.

**Corrections**

- **Plantage** en touchant, dans un graphe d'ordre de lecture, la case de la série où l'on se trouve déjà. L'écran est identifié par l'identifiant de la série : il était empilé deux fois.
- Les rôles d'auteur n'étaient pas traduisibles — le code mettait une majuscule à la valeur brute au lieu de la nommer. Ils affichent Scénario, Dessin, Encrage, Couleurs…
- Les motifs d'étagères d'accueil gardaient leurs noms de constantes anglais (Forgotten, AlmostFinished, Discover) dans l'éditeur : les listes déroulantes affichaient l'énumération elle-même.
- Une puce de genre filtrait sur le slug au lieu du tag : Fantasy sur une série renvoyait cinq résultats là où le genre en compte cinq cent quarante-cinq.
