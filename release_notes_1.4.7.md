### English

**A release about the battery**

Kora was doing a surprising amount of work for an app nobody was looking at. This release is the result of going through the idle and reading paths and removing what nothing asked for. Nothing changes in what you see — except that "Keep reading" is finally right.

**The event stream no longer runs all night**

Kora held its connection to Komga open around the clock. Komga keeps that connection busy even with an idle library, so an app you last opened in the evening was still holding a socket and waking the radio at four in the morning. The connection now follows the app: it drops a minute after you leave, and comes back when you return. The minute of grace matters — switching apps for a few seconds must not tear the connection down and rebuild it, which would cost more than leaving it open.

Komga also re-announces its task queue every ten seconds whether or not anything changed, and Kora used to wake every screen in the app for it. Only real changes get through now. The queue indicator behaves exactly as before.

**Keep reading, from wherever you opened the book**

The shelf only refreshed when you had started the book from Home. Open a volume from a series page, a book page or the widget and the shelf still showed where you were before you read. It is now driven by read progress itself rather than by the path you took, so it is right in every case. The refresh also got cheaper: it re-queries the two or three shelves that depend on progress instead of all of them.

**Panel mode does a third of the work**

Turning a page ran the panel detector on three pages — the one you were on, the next, and the previous one you had just left. The previous one was already in memory, so that third run bought nothing. And because the prefetch was not cancellable, flipping through a volume started a detection per page and let every one run to the end. Kora now warms only the next page, and only once you have stayed put for a moment.

**Zooming stops rebuilding the whole page**

Any change of scale rebuilt every tile of the page, and a pinch produces a new scale on nearly every frame. The resolution used to decode now snaps to quarter steps, which turns a gesture into a handful of rebuilds instead of twenty a second. It rounds up, never down, so a zoomed page is never softer than before. Reading page by page, without zoom, goes through exactly the same code as it always did.

**Home stays calm during a library scan**

The shelves reloaded, then waited five seconds, then reloaded again — a throttle rather than a debounce. With Home on screen during a Komga scan, which emits an event per book, that meant reloading every shelf every five seconds for the whole scan. Kora now waits for the server to go quiet and reloads once. If the burst never stops, it gives in after thirty seconds so the screen does not go stale.

---

### Français

**Une version pour la batterie**

Kora travaillait beaucoup pour une application que personne ne regardait. Cette version est le résultat d'une relecture des chemins « au repos » et « en lecture », d'où a été retiré tout ce que personne n'avait demandé. Rien ne change à l'écran — sauf « Reprendre la lecture », enfin juste.

**Le flux d'événements ne tourne plus toute la nuit**

Kora gardait sa connexion à Komga ouverte en permanence. Komga l'occupe même avec une bibliothèque au repos : une application ouverte pour la dernière fois le soir tenait encore une socket et réveillait la radio à quatre heures du matin. La connexion suit désormais l'application : elle est coupée une minute après votre départ et revient à votre retour. Cette minute compte — passer quelques secondes dans une autre application ne doit pas détruire puis reconstruire la connexion, ce qui coûterait plus cher que de la laisser ouverte.

Komga réannonce par ailleurs sa file de tâches toutes les dix secondes, que quelque chose ait changé ou non, et Kora réveillait tous ses écrans pour l'occasion. Seuls les vrais changements passent maintenant. L'indicateur de file se comporte exactement comme avant.

**Reprendre la lecture, quel que soit l'endroit d'où vous avez ouvert le tome**

L'étagère ne se mettait à jour que si vous aviez commencé le tome depuis l'accueil. Ouvert depuis une fiche série, une fiche livre ou le widget, elle affichait encore votre position d'avant la lecture. Elle suit désormais la progression elle-même plutôt que le chemin emprunté, donc elle est juste dans tous les cas. Le rafraîchissement est aussi devenu moins coûteux : il ne réinterroge que les deux ou trois étagères liées à la progression, au lieu de toutes.

**Le mode panneaux fait un tiers du travail**

Tourner une page lançait le détecteur de panneaux sur trois pages : la vôtre, la suivante, et la précédente que vous veniez de quitter. Cette dernière était déjà en mémoire : la troisième détection ne servait à rien. Et comme le préchargement n'était pas annulable, feuilleter un tome lançait une détection par page et les laissait toutes aller au bout. Kora ne préchauffe plus que la page suivante, et seulement après un instant d'immobilité.

**Le zoom ne reconstruit plus la page entière**

Tout changement d'échelle reconstruisait chaque tuile de la page, et un pincement produit une nouvelle échelle à presque chaque image. La résolution de décodage se cale maintenant sur des quarts, ce qui transforme un geste en quelques reconstructions au lieu d'une vingtaine par seconde. L'arrondi se fait vers le haut, jamais vers le bas : une page zoomée n'est donc jamais plus floue qu'avant. La lecture page par page, sans zoom, emprunte exactement le même code qu'auparavant.

**L'accueil reste calme pendant une analyse de bibliothèque**

Les étagères se rechargeaient, attendaient cinq secondes, puis se rechargeaient — une limitation *après* le coût plutôt qu'une temporisation avant. Accueil affiché pendant une analyse Komga, qui émet un événement par livre, cela revenait à recharger toutes les étagères toutes les cinq secondes pendant toute l'analyse. Kora attend maintenant que le serveur se taise et ne recharge qu'une fois. Si la rafale ne s'arrête jamais, il cède au bout de trente secondes pour que l'écran ne reste pas figé.
