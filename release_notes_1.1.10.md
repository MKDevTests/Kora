### English

**Faster — Home and your libraries now open instantly from a cold start**

Opening the app used to mean staring at an empty screen for several seconds before anything appeared. Home and the library grid now paint immediately from a local snapshot, then refresh silently in the background. Measured on a real library (11 shelves, 7 libraries):

| | Before | After |
|---|---|---|
| Home content | 7-9 s | ~0.15 s |
| Library grid (first page) | 7-9 s | instant |
| Covers | always re-downloaded | served from disk |

Nothing about the server got faster — the app simply stopped making you wait for it.

**Fixed — covers were never cached on disk, and were re-downloaded on every single launch**
- Kora fetches thumbnails through its own Komga fetchers, which returned an already-decoded image. Coil's disk cache can only store bytes, so it had nothing to write: the cache was configured but never used, for every cover, forever.
- On top of that, the disk cache was explicitly wiped on every startup — an upstream debug leftover.
- Covers are now cached properly. Invalidation was already implemented (Kora listens to Komga's thumbnail events) but had never done anything, since nothing was ever cached; it now works. Change a cover in Komga and it updates in Kora.

**Fixed — a serialization bug that made the new caches unreadable**
- `komga-client`'s reading-direction serializer *writes* null but cannot *read* it back, and the field is required — so any series without a reading direction could not survive a save/load round-trip. It never showed against a live server (Komga always sends a value); it only appears when the app re-encodes its own data, which is exactly what a cache does. Worked around using the library's own convention (a blank string decodes back to null).

**Fixed — the panel-by-panel webtoon reader (PANELS) and the ONNX features now actually work**
- The entire **ONNX Runtime** section was silently missing from Settings → Image Reader, the panel-detection model could not be downloaded, and webtoons fell back to continuous scroll.
- Two things were needed: the native libraries (`libomp.so`, `libkomelia_onnxruntime.so`) had never made it into a release build, and the bundled ONNX Runtime was pinned to **1.23.0** while the native bridge is built against **1.25.0** — the versioned symbol `OrtGetApiBase@VERS_1.25.0` could not resolve, so nothing loaded. Both sides are now aligned.
- The failure was invisible by design: on Android the load error is swallowed and the section simply disappears. That is why it went unnoticed for so long.

**Changed — "Discover" shelves keep their picks until you refresh**
- Random shelves used to re-roll on every app launch, which meant re-downloading covers you had never seen and swapping the content out seconds after it appeared. They now keep their selection; **pull-to-refresh still rolls a brand-new pick**, as before.
- Pull-to-refresh no longer blanks the screen either — the current content stays visible while the update runs.

**Build (developers only)**
- `cmake/*.sh` are now forced to LF. On a Windows checkout they came out as CRLF, so the shebang became `#!/bin/bash\r` and the native Docker build died instantly on a misleading `exec ./cmake/android-build.sh: no such file or directory`.

---

### Français

**Plus rapide — Home et vos bibliothèques s'ouvrent instantanément à froid**

Ouvrir l'app, c'était fixer un écran vide pendant plusieurs secondes avant que quoi que ce soit n'apparaisse. Home et la grille des bibliothèques se peignent désormais immédiatement depuis un instantané local, puis se rafraîchissent en silence en arrière-plan. Mesuré sur une vraie bibliothèque (11 étagères, 7 bibliothèques) :

| | Avant | Après |
|---|---|---|
| Contenu de Home | 7-9 s | ~0,15 s |
| Grille de bibliothèque (1ʳᵉ page) | 7-9 s | instantané |
| Couvertures | retéléchargées à chaque fois | servies depuis le disque |

Le serveur n'est pas devenu plus rapide — l'app a simplement cessé de vous le faire attendre.

**Corrigé — les couvertures n'étaient jamais mises en cache disque, et étaient retéléchargées à chaque lancement**
- Kora récupère les vignettes via ses propres fetchers Komga, qui renvoyaient une image **déjà décodée**. Le cache disque de Coil ne sait stocker que des octets : il n'avait donc rien à écrire. Le cache était configuré mais jamais utilisé, pour chaque couverture, depuis toujours.
- Par-dessus, ce cache disque était explicitement vidé à chaque démarrage — un reliquat de debug venu de l'amont.
- Les couvertures sont désormais correctement cachées. L'invalidation était déjà écrite (Kora écoute les événements de vignette de Komga) mais n'avait jamais rien fait, faute de cache à purger ; elle est maintenant opérationnelle. Changez une couverture dans Komga, elle se met à jour dans Kora.

**Corrigé — un bug de sérialisation qui rendait les nouveaux caches illisibles**
- Le sérialiseur de sens de lecture de `komga-client` **écrit** `null` mais ne sait pas le **relire**, et le champ est obligatoire — donc toute série sans sens de lecture ne survivait pas à un aller-retour écriture/lecture. Invisible face à un vrai serveur (Komga envoie toujours une valeur) : ça ne sort que lorsque l'app ré-encode ses propres données, c'est-à-dire exactement ce que fait un cache. Contourné via la convention prévue par la lib elle-même (une chaîne vide se relit en `null`).

**Corrigé — le lecteur webtoon case par case (PANELS) et les fonctions ONNX fonctionnent enfin**
- Toute la section **ONNX Runtime** était absente sans un mot des Réglages → Image Reader, le modèle de détection de cases ne pouvait pas être téléchargé, et les webtoons basculaient en défilement continu.
- Il manquait deux choses : les bibliothèques natives (`libomp.so`, `libkomelia_onnxruntime.so`) n'avaient jamais été intégrées à une release, et l'ONNX Runtime embarqué était figé en **1.23.0** alors que le pont natif est compilé contre la **1.25.0** — le symbole versionné `OrtGetApiBase@VERS_1.25.0` ne pouvait pas être résolu, donc rien ne se chargeait. Les deux côtés sont alignés.
- L'échec était invisible par construction : sur Android l'erreur de chargement est avalée et la section disparaît simplement. D'où des mois sans que personne ne le voie.

**Modifié — les étagères « Discover » gardent leurs tirages jusqu'au rafraîchissement**
- Les étagères aléatoires retiraient au sort à chaque lancement, ce qui retéléchargeait des couvertures jamais vues et remplaçait le contenu quelques secondes après son apparition. Elles conservent désormais leur sélection ; **le pull-to-refresh tire toujours une nouvelle sélection**, comme avant.
- Le pull-to-refresh ne vide plus l'écran non plus : le contenu actuel reste visible pendant la mise à jour.

**Build (développeurs uniquement)**
- Les `cmake/*.sh` sont forcés en LF. Sur un checkout Windows ils arrivaient en CRLF, le shebang devenait `#!/bin/bash\r`, et le build natif Docker mourait aussitôt sur un trompeur `exec ./cmake/android-build.sh: no such file or directory`.
