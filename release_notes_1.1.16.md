### English

**Performance: the app no longer stampedes the server**

Several screens fired one server request per item all at once — the Genre tab
(one count per genre, ~22 at once), the Favorites screen (one lookup per
favorite), and the upcoming-releases scan. Bursts like these saturated Komga's
database connection pool, so any other request happening at the same time (a
library grid, for instance) queued behind them: the same page load was measured
swinging from ~1s idle to ~20s during a genre open. Every one of these fan-outs
is now capped at a few requests in flight. Library and tab switches are much
snappier as a result.

**Performance: hidden-series sync throttled**

The list of admin-hidden series was re-scanned from the server on every sign-in
event. It now refreshes in the background at most once a day (the set only
changes when an admin hides or unhides something), while hiding/unhiding and
pull-to-refresh still sync immediately.

**Fix / cleanup: logging**

The reader wrote a log line per page and per server event, at a level that kept
them even in release builds — tens of megabytes of logs and constant disk
writes. Those are now debug-only, and log writing is buffered off the main path.

---

### Français

**Performances : l'application ne sature plus le serveur**

Plusieurs écrans lançaient une requête serveur par élément d'un seul coup —
l'onglet Genre (un comptage par genre, ~22 à la fois), l'écran Favoris (une
résolution par favori) et le scan des prochaines sorties. Ces rafales saturaient
le pool de connexions de la base Komga : toute autre requête simultanée (le
chargement d'une bibliothèque, par exemple) faisait alors la queue derrière —
un même chargement de page mesuré passant de ~1 s au repos à ~20 s pendant
l'ouverture d'un genre. Chacune de ces rafales est désormais plafonnée à
quelques requêtes en vol. Les changements de bibliothèque et d'onglet en sont
nettement plus réactifs.

**Performances : synchro des séries masquées throttlée**

La liste des séries masquées par l'admin était re-scannée depuis le serveur à
chaque événement de connexion. Elle se rafraîchit maintenant en arrière-plan au
plus une fois par jour (le set ne change que lorsqu'un admin masque ou démasque
une série), tandis que masquer/démasquer et le tirer-pour-rafraîchir se
synchronisent toujours immédiatement.

**Correction / nettoyage : journalisation**

Le lecteur écrivait une ligne de log par page et par événement serveur, à un
niveau qui les conservait même en version publique — des dizaines de méga-octets
de journaux et des écritures disque permanentes. Ces traces sont désormais
réservées au débogage, et l'écriture des journaux est bufferisée hors du chemin
principal.
