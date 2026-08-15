### English

**Reader: near-instant volume opening**

Opening a volume ran its ~6 server calls one after another (≈2s each on a slow
server, 13-21s measured). They now fire in a single parallel wave, seeded with
the book the calling screen already holds, and every book's page list is cached
on disk keyed by its file hash. A previously-opened volume now opens in about a
second instead of a dozen.

**Diagnostics: server latency readout**

A new "Server latency (this session)" section shows the median, p95, slowest
call and failure count across the app's measured server operations — so "the
server is slow today" is distinguishable from "the app has a bug" at a glance.

**Admin: maintenance — expired release tags**

A new admin-only Settings → Admin → Maintenance screen lists `nextrelease:`
tags whose date has passed (they otherwise pile up invisibly) and lets you purge
them one by one or all at once. Only the targeted tag is removed; every other
tag on the series is preserved. A no-spam banner on the upcoming-releases screen
points admins here when there is something to clean.

**Widget: per-library filter**

The "Next book up" widget can now be limited to a single library, chosen in
Settings → Navigation. Applied on the widget's next refresh.

---

### Français

**Lecteur : ouverture de tome quasi instantanée**

Ouvrir un tome enchaînait ses ~6 appels serveur à la suite (~2 s chacun sur un
serveur lent, 13-21 s mesurées). Ils partent maintenant en une seule vague
parallèle, amorcée avec le livre que l'écran appelant possède déjà, et la liste
de pages de chaque livre est mise en cache sur disque, clé par empreinte de
fichier. Un tome déjà ouvert s'ouvre désormais en une seconde environ au lieu
d'une douzaine.

**Diagnostics : mesure de latence serveur**

Une nouvelle section « Server latency (this session) » affiche la médiane, le
p95, l'appel le plus lent et le nombre d'échecs sur les opérations serveur
mesurées — pour distinguer d'un coup d'œil « le serveur est lent aujourd'hui »
de « l'app a un bug ».

**Admin : maintenance — tags de sortie périmés**

Un nouvel écran admin Réglages → Admin → Maintenance liste les tags
`nextrelease:` dont la date est passée (ils s'accumulent sinon en silence) et
permet de les purger un par un ou tous d'un coup. Seul le tag visé est retiré ;
tous les autres tags de la série sont préservés. Une bannière sans spam sur
l'écran des prochaines sorties y renvoie l'admin quand il y a à faire.

**Widget : filtre par bibliothèque**

Le widget « Prochain tome » peut désormais être limité à une seule bibliothèque,
choisie dans Réglages → Navigation. Appliqué à la prochaine mise à jour du
widget.
