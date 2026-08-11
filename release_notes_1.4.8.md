### English

**Kora has written no log file since 1.2.7**

Enabling R8 in 1.2.7 stripped the logback appenders that write `komelia.log`, because they are named in an XML file and nothing in the code refers to them. Release builds have logged to nothing ever since — six versions during which Diagnostics → Export logs handed you a file frozen in May. The appenders are kept now, and the log is being written again.

The same pass pinned R8 to a version that can read Kotlin 2.4 metadata, which silences the several hundred parsing warnings every release build was printing.

**Downloads wait instead of failing**

A download was queued with no conditions attached: Android ran it whether or not there was a network, whether or not there was room to write, it failed, and the book was quietly lost. It now waits for a connection and for free space, and retries with a widening delay. Asking twice for the same book no longer cancels the download already in flight and starts it over.

**The widget stops re-downloading what it already has**

Every redraw of the "Continue reading" widget re-queried the server and re-encoded three covers — and a redraw fires once per widget instance, on every finished book, and on every trip to the background. It now fetches at most once every thirty minutes, five if the last attempt failed, and the instances behind the first do nothing at all. Finishing a book and the refresh button still go straight to the server, because those are the two moments you expect it to look.

**Reconnections back off**

When the connection to the server dropped, Kora retried every ten seconds, for as long as it took — a tunnel, a night, a server being restarted. The delay now widens from one second to two minutes, and resets the moment an event arrives.

**Switching server leaves nothing behind**

The event stream, the offline sync and the upscaler were not closed when the dependency graph was replaced, so each server switch left a copy running. They are closed now.

**Startup no longer captures the log on the main thread**

The previous session's logcat snapshot is what makes a crash report readable, but it was taken on the UI thread at startup. It moved to a background thread, without being delayed — a delay would let the current session's own startup lines push the crash out of the window.

---

### Français

**Kora n'écrivait plus aucun fichier de log depuis la 1.2.7**

L'activation de R8 en 1.2.7 a supprimé les appenders logback qui écrivent `komelia.log`, parce qu'ils sont nommés dans un fichier XML et qu'aucun code ne les référence. Les versions de production ont donc journalisé dans le vide depuis — six versions pendant lesquelles Diagnostics → Exporter les logs vous rendait un fichier figé en mai. Les appenders sont conservés désormais, et le log s'écrit à nouveau.

La même passe a figé R8 sur une version capable de lire les métadonnées Kotlin 2.4, ce qui fait taire les quelques centaines d'avertissements d'analyse que chaque build de production affichait.

**Les téléchargements attendent au lieu d'échouer**

Un téléchargement était mis en file sans aucune condition : Android l'exécutait qu'il y ait du réseau ou non, qu'il y ait de la place ou non, il échouait, et le tome était perdu sans un mot. Il attend maintenant une connexion et de l'espace libre, et réessaie avec un délai qui s'élargit. Redemander le même tome n'annule plus le téléchargement déjà en cours pour le relancer de zéro.

**Le widget cesse de retélécharger ce qu'il a déjà**

Chaque redessin du widget « Reprendre la lecture » réinterrogeait le serveur et ré-encodait trois couvertures — et un redessin se déclenche une fois par instance du widget, à chaque tome terminé, et à chaque passage en arrière-plan. Il interroge désormais le serveur au plus une fois par demi-heure, cinq minutes si la dernière tentative a échoué, et les instances derrière la première ne font plus rien du tout. Terminer un tome et le bouton de rafraîchissement vont toujours directement au serveur, parce que ce sont les deux moments où vous attendez qu'il regarde.

**Les reconnexions s'espacent**

Quand la connexion au serveur tombait, Kora réessayait toutes les dix secondes, aussi longtemps qu'il le fallait — un tunnel, une nuit, un serveur en cours de redémarrage. Le délai s'élargit maintenant d'une seconde à deux minutes, et repart à zéro dès qu'un événement arrive.

**Changer de serveur ne laisse plus rien derrière**

Le flux d'événements, la synchronisation hors-ligne et l'agrandisseur d'image n'étaient pas fermés quand le graphe de dépendances était remplacé : chaque changement de serveur laissait donc une copie tourner. Ils sont fermés désormais.

**Le démarrage ne capture plus le log sur le thread principal**

L'instantané logcat de la session précédente est ce qui rend un rapport de plantage lisible, mais il était pris sur le thread d'interface au démarrage. Il est passé sur un thread de fond, sans être différé — un délai laisserait les lignes de démarrage de la session courante chasser le plantage hors de la fenêtre.
