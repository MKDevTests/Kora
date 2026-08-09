# Tests de performance sur appareil — protocole, résultats et intérêt

## 1. Objet du document

Ce document décrit les premiers tests de performance exécutés sur un appareil Android physique avec la branche `agent/debug-performance-battery`. Il précise :

- ce qui a réellement été mesuré ;
- la manière dont chaque mesure a été obtenue ;
- l’intérêt de chaque test pour la fluidité, la mémoire et la batterie ;
- les limites des chiffres ;
- les tests encore nécessaires pour calculer un véritable gain avant/après.

Il ne faut pas interpréter ces mesures comme une comparaison directe avec `main`. La version de référence non optimisée n’a pas été mesurée dans les mêmes conditions. Les valeurs ci-dessous constituent donc une **baseline de la branche optimisée**, pas encore un pourcentage de gain par rapport à la release officielle.

## 2. Règles de sécurité des tests

Les prochains benchmarks doivent être séparés du processus de build et d’installation.

- Le script de mesure ne construit et n’installe aucun APK.
- Il cible uniquement `io.github.mkdevtests.kora.debug`.
- Il ne doit jamais appeler `adb uninstall`, `pm clear` ou une migration de données.
- Il ne doit jamais viser `io.github.mkdevtests.kora`, le paquet release.
- Les builds et installations sont exécutés manuellement par le propriétaire du projet.
- En cas d’échec d’une mise à jour ADB, il faut s’arrêter. Une désinstallation forcée détruirait les données applicatives.
- Une migration `KoraDebug -> Kora` n’est pas un mécanisme de mise à jour normal et ne doit jamais être appliquée sur une release déjà configurée.

Un incident de configuration a été constaté après la mise à jour de KoraDebug. Même si `adb install -r` est normalement conçu pour conserver les données lorsque le paquet et la signature restent identiques, les futurs tests devront considérer toute installation comme une opération distincte et précédée d’une sauvegarde vérifiée.

## 3. Environnement mesuré

| Élément | Valeur |
|---|---|
| Appareil | Samsung Galaxy Tab S7 FE, `SM-T733` |
| Nom de l’appareil Android | `gts7fewifi` |
| Android | 14 |
| API | 34 |
| Architecture | arm64-v8a |
| Définition | 1 600 × 2 560 |
| Densité | 340 dpi |
| Paquet testé | `io.github.mkdevtests.kora.debug` |
| Version | 1.4.6, versionCode 10406 |
| Branche | `agent/debug-performance-battery` |
| État thermique initial | Normal, statut Android 0 |
| Alimentation | USB |

L’alimentation USB empêche de déduire une autonomie réelle à partir de la variation du pourcentage de batterie. Les résultats batterie de ce document concernent donc les **indicateurs indirects** : CPU, réveils, réseau, mémoire et température.

## 4. Résumé des résultats

| Mesure | Résultat |
|---|---:|
| Démarrage, minimum | 1 922 ms |
| Démarrage, médiane | 1 939 ms |
| Démarrage, moyenne | 1 951,5 ms |
| Démarrage, p90 | 1 956 ms |
| Démarrage, maximum | 2 047 ms |
| CPU au repos, Home visible | 5,05 % en moyenne ; 25 % maximum |
| CPU au repos, arrière-plan | 0,35 % en moyenne ; 5 % maximum |
| Réduction premier plan → arrière-plan | environ 93 % de CPU |
| Mémoire au premier plan | 340 528 KiB PSS ; 419 708 KiB RSS |
| Mémoire en arrière-plan | 283 008 KiB PSS ; 365 988 KiB RSS |
| Réduction de PSS en arrière-plan | 57 520 KiB, environ 17 % |
| Threads du processus | 89 |
| Défilement Home | 405 frames ; 38 janky modernes, soit 9,38 % |
| Latence du défilement | p50 24 ms ; p90 36 ms ; p95 57 ms ; p99 200 ms |
| Température après scénario | AP 36 °C ; batterie 30,7 °C ; peau 32,4 °C |
| Throttling thermique | Aucun, statut Android 0 |
| Crash ou ANR du paquet testé | Aucun observé |

La réduction de CPU et de mémoire entre premier plan et arrière-plan décrit le comportement de la même version selon son état. Elle ne constitue pas un gain de la branche par rapport à `main`.

## 5. Test de démarrage

### Méthode

Dix lancements ont été exécutés avec `am start -W -S`. L’option `-S` arrête le processus du paquet debug avant chaque lancement, mais ne supprime pas ses données. Aucun reset du cache ART et aucune suppression de données n’ont été réalisés.

Résultats en millisecondes :

```text
2 047, 1 951, 1 956, 1 952, 1 937,
1 937, 1 941, 1 936, 1 936, 1 922
```

### Intérêt

Ce test mesure la durée visible entre la demande de lancement et l’affichage de l’activité. Il détecte notamment :

- les initialisations synchrones trop précoces ;
- les ouvertures de bases de données sur le chemin critique ;
- le chargement prématuré de bibliothèques natives ;
- les lectures disque et allocations qui retardent la première frame.

### Interprétation

Le temps est stable, mais reste proche de deux secondes. Un lancement graphique isolé a produit :

- 133 frames ;
- 8 frames janky selon la métrique moderne, soit 6,02 % ;
- p90 à 38 ms, p95 à 89 ms et p99 à 1 050 ms ;
- un message `Skipped 60 frames` ;
- un GC concurrent de 202 ms après environ 31 MiB libérés.

Le GPU est rapide sur la majorité des frames. Le pic initial semble davantage lié au thread principal, aux allocations, au décodage et à l’initialisation applicative.

## 6. CPU au repos au premier plan

### Méthode

Après stabilisation de l’écran Home réel, le CPU du processus a été échantillonné une fois par seconde pendant vingt secondes avec `top`.

Résultat : 5,05 % de moyenne, avec des pointes régulières jusqu’à 25 %.

### Intérêt

Une application visuellement immobile devrait effectuer très peu de travail. Un CPU périodiquement actif sur un écran statique peut entraîner :

- davantage de réveils du processeur ;
- une impossibilité d’entrer dans des états de repos profonds ;
- une consommation supplémentaire écran allumé ;
- des interférences avec les interactions de l’utilisateur.

### Interprétation

Les pointes proviennent principalement de :

- `OkHttp TaskRunner` ;
- coroutines `DefaultDispatcher` ;
- connexion SSE au serveur local ;
- nettoyeur de références Komelia ;
- chargement ou traitement différé de données et d’images.

Le journal montre également un `TaskQueueStatus(count=0)` environ toutes les dix secondes. Cet événement est reçu, loggé en debug, diffusé et observable par l’UI même lorsque sa valeur reste identique. Un filtrage des doublons pourrait diminuer une partie de ces réveils.

## 7. CPU en arrière-plan

### Méthode

La touche Home Android a été envoyée, puis le même processus a été mesuré pendant vingt secondes sans le tuer.

Résultat : 0,35 % de CPU moyen, avec un maximum ponctuel de 5 %.

### Intérêt

Ce test vérifie que l’application réduit bien son activité lorsqu’elle n’est plus visible. Il est particulièrement important pour :

- SSE et reconnexions réseau ;
- WorkManager ;
- widgets ;
- synchronisation offline ;
- lecteurs ou tâches natives qui pourraient survivre à l’écran.

### Interprétation

Le passage de 5,05 % à 0,35 % représente environ 93 % de CPU en moins. Le lifecycle de l’écran réduit donc fortement le travail UI. Ce bon résultat court ne remplace cependant pas un test de veille de trente à soixante minutes, car les travaux périodiques espacés peuvent ne pas apparaître dans une fenêtre de vingt secondes.

## 8. Mémoire

### Méthode

La mémoire a été relevée avec `dumpsys meminfo` au premier plan, puis après le passage en arrière-plan.

### Intérêt

- Le PSS estime la part de mémoire réellement attribuable à l’application.
- Le RSS inclut toutes les pages présentes dans le processus, y compris les pages partagées.
- Une mémoire élevée augmente le risque de GC, de destruction par Android et de rechargement coûteux lors du retour dans l’application.

### Interprétation

Le PSS passe d’environ 341 MiB à 283 MiB en arrière-plan. Une part importante de la mémoire est classée comme code/DEX et provient probablement du build debug et de l’ensemble des bibliothèques embarquées. Une variante benchmark proche de la release est nécessaire pour connaître l’empreinte réellement représentative de la version publiée.

La présence de WebView/Chromium, ONNX, NCNN, libvips et de plusieurs pools réseau/base de données contribue aussi au nombre de threads et à l’empreinte du processus. Leur initialisation paresseuse reste une piste prioritaire.

## 9. Fluidité du défilement Home

### Méthode

Les compteurs `gfxinfo` du paquet debug ont été remis à zéro. Six balayages vers le bas puis six vers le haut ont été exécutés sur l’écran Home, qui contenait plusieurs rangées et de nombreuses couvertures.

### Intérêt

Ce test exerce simultanément :

- Compose et ses recompositions ;
- la mesure et le placement des listes ;
- Coil et le décodage des couvertures ;
- les allocations de bitmaps ;
- les commandes de dessin et le GPU.

### Interprétation

Le taux de jank moderne atteint 9,38 %. La médiane de 24 ms dépasse déjà le budget d’une frame à 60 Hz, qui est d’environ 16,7 ms. Le p99 à 200 ms correspond à des blocages nettement perceptibles.

Les prochaines investigations doivent distinguer :

- décodage d’images pendant le scroll ;
- tailles de couvertures supérieures à la taille affichée ;
- recompositions de rangées complètes ;
- clés instables dans les listes ;
- travail réseau ou base de données publié pendant l’interaction.

## 10. Température et batterie

### Méthode

Les capteurs Android ont été relevés après les scénarios de démarrage et de défilement.

### Intérêt

La température permet de détecter deux risques :

1. une charge soutenue inutile qui consomme la batterie ;
2. du throttling qui fausse les résultats de performance en ralentissant le processeur ou le GPU.

### Interprétation

Le processeur est monté à 36 °C, avec une batterie à 30,7 °C et un statut thermique Android nul. Les mesures courtes n’ont donc pas été limitées par la température.

Pour mesurer réellement l’autonomie, il faudra :

- débrancher l’USB ;
- fixer la luminosité, le volume, le Wi-Fi et le taux de rafraîchissement ;
- partir d’une température comparable ;
- exécuter chaque scénario trente à soixante minutes ;
- répéter chaque scénario au moins trois fois ;
- comparer médiane et dispersion, pas une seule variation de pourcentage.

## 11. Améliorations présentes sur la branche et gain attendu

| Amélioration | Effet recherché | État de la mesure |
|---|---|---|
| Snapshot `logcat` différé et exécuté hors du démarrage principal | Réduire l’I/O et le travail avant la première frame | Pas encore comparé à `main` |
| Backoff exponentiel et jitter SSE | Éviter les reconnexions fréquentes quand le serveur est indisponible | Test hors ligne restant |
| Fermeture déterministe des sessions SSE | Éviter les connexions et threads fantômes | Test de changements de serveur restant |
| Buffers offline bornés et concurrence limitée | Réduire les pointes mémoire/CPU pendant une synchronisation | Test de charge restant |
| Contraintes réseau WorkManager | Éviter les travaux impossibles sans réseau | Test réseau restant |
| TTL, mutex et déduplication des widgets | Réduire les fetchs et réveils redondants | Repos court encourageant, pas d’A/B |
| Suppression des pollings audio redondants à 2 Hz | Réduire réveils et recompositions pendant l’audio | Test audio restant |
| Offsets audio précalculés | Éviter les recherches répétées de piste | Test audio restant |
| Ticker transcription ramené à 1 Hz | Jusqu’à 80 % de ticks en moins face à un ticker à 5 Hz | Gain algorithmique, énergie non mesurée |
| Prélecture transcription suspendue en pause | Éviter le décodage PCM inutile | Test pause/reprise restant |
| Tampon PCM primitif réutilisé | Réduire boxing, allocations et GC | Profil Whisper restant |
| Fermeture explicite NCNN | Libérer les ressources natives/GPU à la fermeture | Test lecteur image restant |
| Fermeture de `SyncManager` | Éviter les ressources conservées entre serveurs | Test multi-serveur restant |

Les gains indiqués comme « attendus » ne doivent pas être présentés comme des mesures. Le seul chiffre algorithmique direct est la réduction de fréquence d’un ticker de 5 Hz à 1 Hz, soit 80 % d’exécutions en moins pour ce ticker précis. Cela ne signifie pas 80 % de batterie gagnée par l’application entière.

## 12. Pourquoi aucun gain global avant/après n’est encore annoncé

Un gain nécessite deux mesures comparables :

```text
gain (%) = (baseline - version optimisée) / baseline × 100
```

La baseline `main` n’a pas été mesurée sur le même appareil avec le même état de données, la même température et le même protocole. Comparer les résultats actuels à un chiffre historique ou à une autre variante produirait un pourcentage trompeur.

La méthode sûre consiste à créer deux variantes indépendantes qui ne touchent ni à KoraDebug ni à la release :

- `io.github.mkdevtests.kora.baseline`, construite depuis `main` ;
- `io.github.mkdevtests.kora.benchmark`, construite depuis la branche optimisée.

Les deux variantes doivent utiliser un jeu de données de test contrôlé, sans identifiants personnels, puis subir exactement le même scénario. Les builds et installations resteront manuels.

## 13. Tests restant à réaliser

### Priorité 1 — comparaison A/B sûre

- démarrage baseline contre branche optimisée ;
- CPU Home au repos ;
- mémoire après stabilisation ;
- défilement avec la même bibliothèque de test.

### Priorité 2 — réseau et lifecycle

- serveur inaccessible pendant trente minutes ;
- coupure et retour du Wi-Fi ;
- vingt changements de serveur ;
- comptage des sessions SSE, threads et sockets ;
- vérification des widgets et WorkManager en veille.

### Priorité 3 — lecteur d’images

- pinch continu et navigation rapide ;
- NCNN activé puis désactivé ;
- fermeture pendant une inférence ;
- mémoire native, température, jank et temps par page.

### Priorité 4 — audio et transcription

- lecture, pause, reprise et seek ;
- changement de piste ;
- Whisper actif puis inactif ;
- CPU, allocations, GC, température et délai de transcription.

### Priorité 5 — autonomie

- Home visible ;
- application en arrière-plan ;
- lecture image ;
- audio sans transcription ;
- audio avec transcription ;
- au moins trois répétitions de trente à soixante minutes par scénario.

## 14. Script de mesure

Le script `scripts/measure-kora-debug.ps1` automatise le démarrage, le CPU, la mémoire, le défilement et les relevés thermiques. Il écrit des résultats horodatés dans :

```text
komelia-app/build/perf/<horodatage>/
```

Les principaux fichiers produits sont :

- `summary.json` : synthèse exploitable par un outil ou une CI ;
- `startup.csv` : détail de chaque lancement ;
- `meminfo-foreground.txt` et `meminfo-background.txt` ;
- `gfxinfo-home-scroll.txt` ;
- `battery-before.txt` et `battery-after.txt` ;
- `thermal-before.txt` et `thermal-after.txt`.

Le script doit être lancé uniquement après que l’utilisateur a lui-même construit, vérifié et installé la variante debug souhaitée. Il ne constitue pas une autorisation d’installation ou de migration.

## 15. Conclusion

La branche présente un comportement arrière-plan encourageant sur la courte fenêtre observée, mais l’écran Home conserve une activité CPU périodique, une empreinte mémoire élevée et un défilement perfectible. Le démarrage est stable, sans être encore rapide, et contient un blocage initial important.

Les optimisations déjà développées ciblent des causes pertinentes pour la batterie — reconnexions, polling, tâches orphelines, widgets, PCM et ressources natives — mais leur gain global ne pourra être annoncé qu’après une comparaison A/B isolée et reproductible. Jusqu’à cette comparaison, les résultats doivent rester formulés comme une baseline et des pistes validées par le code, non comme un pourcentage d’autonomie gagné.
