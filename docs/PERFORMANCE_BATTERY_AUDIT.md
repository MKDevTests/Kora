# Audit de performance et de consommation énergétique de Kora

**Dépôt audité :** [MKDevTests/Kora](https://github.com/MKDevTests/Kora)
**Révision de référence :** [`9b83935ca673c4fd2c3c13583acb2cf479871662`](https://github.com/MKDevTests/Kora/commit/9b83935ca673c4fd2c3c13583acb2cf479871662) — Kora v1.4.6
**Date de l’audit :** 9 août 2026
**Nature de l’audit :** analyse statique en lecture seule
**Plateforme prioritaire :** Android, avec prise en compte du code KMP partagé

---

## 1. Résumé exécutif

Kora possède une base technique globalement sérieuse : architecture modulaire, Kotlin Multiplatform, caches bornés à plusieurs endroits, téléchargements persistants via WorkManager, journalisation asynchrone, R8 actif en release et limitation de l’APK Android à `arm64-v8a`.

L’audit révèle néanmoins plusieurs coûts structurels importants. Les plus préoccupants ne sont pas de petites inefficacités Compose : ils concernent des traitements natifs, des boucles de fond, des files sans limite et des opérations répétées sur des images ou des flux audio volumineux.

Les quatre axes prioritaires sont :

1. **réduire le travail déclenché au démarrage**, notamment les opérations `logcat` et l’initialisation anticipée des bibliothèques natives ;
2. **rendre les cycles de vie déterministes**, pour qu’une session SSE, un scope, un modèle ONNX ou un contexte GPU ne survive jamais à son propriétaire ;
3. **limiter et annuler réellement les traitements image**, particulièrement le tiling pendant le zoom, l’upscaling NCNN et la détection de panneaux ONNX ;
4. **repenser la chaîne audio/transcription**, actuellement caractérisée par du polling fréquent, la recréation répétée de `MediaCodec` et des allocations massives d’objets.

### 1.1 Impact attendu par scénario

| Scénario | Sources de coût dominantes | Optimisations les plus importantes |
|---|---|---|
| Démarrage froid | Dump `logcat`, chargements natifs, migrations et création du graphe | Retirer le dump du thread principal, initialisation paresseuse |
| Application en arrière-plan | Reconnexion SSE, scopes survivants, widgets, synchronisation | Backoff réseau, fermeture déterministe, contraintes de travail |
| Lecture de BD/manga | Tiling, NCNN, ONNX, préchargement, animations | Tuiles visibles seulement, files bornées, préchargement adaptatif |
| Lecture audio | Polling de position, recherches répétées | Source de temps unique, offsets pré-calculés |
| Audio avec transcription | Recréation de codec, Whisper, copies PCM, ticker à 5 Hz | Décodeur persistant, buffers primitifs, traitement lié à la lecture |
| Bibliothèque offline importante | Synchronisation en N+1 et tâches non limitées | Sync delta/bulk, worker pool borné, WorkManager contraint |

### 1.2 Priorisation

| Priorité | Constat | Impact probable | Effort indicatif | Confiance |
|---|---|---:|---:|---:|
| P0 | Dump `logcat` synchrone dans `Application.onCreate` | Démarrage, I/O | Faible | Très élevée |
| P0 | File NCNN illimitée et inférence `NonCancellable` | RAM, GPU, batterie | Moyen | Très élevée |
| P0 | Retiling complet pendant le pinch-to-zoom | Jank, CPU/GPU | Élevé | Très élevée |
| P0 | Recréation de `MediaExtractor`/`MediaCodec` par bloc de 2 s | CPU, batterie, GC | Élevé | Très élevée |
| P0 | Ressources natives et scopes non fermés par leur propriétaire | Fuites, activité fantôme | Moyen | Élevée |
| P1 | Reconnexion SSE toutes les 10 s sans backoff | Batterie en veille, radio | Faible à moyen | Très élevée |
| P1 | Détection ONNX sur page courante, précédente et suivante | CPU, batterie | Moyen | Très élevée |
| P1 | Polling audio/transcription à 2–5 Hz | CPU continu, recompositions | Moyen | Très élevée |
| P1 | Synchronisation offline en N+1 | Réseau, radio, durée | Élevé | Très élevée |
| P1 | Tâches et buffers sans borne | Pics de RAM/CPU | Moyen | Très élevée |
| P1 | Téléchargements sans contraintes et sans retry transitoire | Réseau, batterie | Faible | Très élevée |
| P2 | Widgets recompilant systématiquement les couvertures | Réseau, CPU, disque | Moyen | Élevée |
| P2 | Home rechargeant toutes les étagères après des événements larges | Réseau, latence | Moyen | Élevée |
| P2 | Transactions SQLite systématiquement non annulables | I/O après navigation | Moyen | Très élevée |
| P2 | Modèles et bibliothèques rares présents dans le chemin initial | Démarrage, taille | Élevé | Élevée |

> Les priorités expriment l’ordre de traitement recommandé, pas la gravité d’un défaut fonctionnel. Les gains réels devront être mesurés sur appareil physique.

---

## 2. Méthode et limites

L’analyse a porté sur le snapshot du dépôt au commit indiqué plus haut. Le dépôt contient environ 3 918 fichiers suivis, dont environ 1 268 sources Kotlin. Les modules application, domaine, UI, offline, base de données, lecteurs d’images, audio, transcription et bibliothèques natives ont été parcourus.

L’audit a été réalisé sans checkout local du dépôt Kora, sans build, sans lancement d’émulateur, sans modification de fichier distant et sans écriture GitHub. L’archive a été consultée en mémoire et les fichiers ont été lus via les interfaces GitHub.

Cette méthode permet d’identifier avec une forte confiance :

- les opérations synchrones dans des chemins critiques ;
- les boucles de polling ;
- les ressources sans fermeture explicite ;
- les algorithmes à complexité défavorable ;
- les queues et buffers non bornés ;
- les appels réseau répétitifs ;
- les allocations et copies évitables.

Elle ne permet pas de fournir directement :

- le TTID/TTFD réel sur un téléphone donné ;
- les millisecondes de frame P95/P99 ;
- la consommation en mAh ;
- le coût relatif exact de VIPS, ONNX, NCNN ou Whisper selon le SoC ;
- la taille finale exacte d’un APK/AAB après packaging.

Les recommandations sont donc accompagnées d’un plan de mesure afin de transformer les hypothèses statiques en décisions quantitatives.

---

## 3. Démarrage de l’application

### 3.1 Dump `logcat` synchrone sur le thread principal — P0

#### Constat

Dans [`App.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-app/src/androidMain/kotlin/snd/komelia/App.kt#L39-L50), `Application.onCreate()` appelle directement `saveLogcatSnapshot()`. Cette fonction lance un processus `logcat`, lit entièrement sa sortie, écrit un fichier et attend la fin du processus, avec un timeout pouvant atteindre cinq secondes : [`App.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-app/src/androidMain/kotlin/snd/komelia/App.kt#L157-L175).

Le thread principal est ainsi exposé à :

- la création d’un processus système ;
- la lecture d’un flux texte ;
- la création de la chaîne complète en mémoire ;
- une écriture disque ;
- une attente bloquante.

Même si le cas nominal est rapide, ce travail est effectué à chaque création du processus Android et augmente la variance du démarrage froid.

#### Correction recommandée

Option préférée : **ne plus produire automatiquement de snapshot au démarrage**.

Le snapshot peut être généré :

- après détection d’un crash précédent ;
- lorsque l’utilisateur ouvre Diagnostics ;
- via une action explicite « Exporter les logs » ;
- uniquement dans une variante debug/internal.

Si la conservation automatique est indispensable, le déclenchement doit être asynchrone après le premier affichage, avec une limite stricte de taille et de durée.

Exemple indicatif :

```kotlin
class App : Application() {
    private val diagnosticsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initLogging()
        GlobalExceptionHandler.initialize(this)

        if (shouldCapturePreviousSessionDiagnostics()) {
            diagnosticsScope.launch {
                runCatching { captureBoundedLogcatSnapshot() }
                    .onFailure { logger.warn(it) { "Logcat snapshot failed" } }
            }
        }

        initializeCriticalServices()
    }
}
```

`captureBoundedLogcatSnapshot()` devrait lire le flux progressivement vers un fichier temporaire, imposer une taille maximale, puis renommer le fichier de manière atomique. Il faut éviter `readText()` sur une sortie arbitraire.

#### Validation

- activer `StrictMode` en debug pour détecter les I/O disque du thread principal ;
- mesurer 30 démarrages froids avant/après avec `StartupTimingMetric` ;
- comparer TTID et TTFD P50/P95 ;
- vérifier le démarrage avec un stockage presque plein et sous forte contention I/O.

### 3.2 Chargement anticipé de VIPS, ONNX et NCNN — P1

#### Constat

[`AndroidAppModule.beforeInit`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-app/src/androidMain/kotlin/snd/komelia/AndroidAppModule.kt#L138-L159) charge VIPS, ONNX Runtime et NCNN puis installe le modèle de bulles avant que l’utilisateur n’ouvre nécessairement le lecteur correspondant.

Le graphe commun crée ensuite l’environnement ONNX et les composants de traitement : [`AppModule.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-app/src/commonMain/kotlin/snd/komelia/AppModule.kt#L276-L307). La factory du lecteur crée et initialise l’upscaler NCNN : [`AndroidAppModule.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-app/src/androidMain/kotlin/snd/komelia/AndroidAppModule.kt#L346-L367).

Enfin, `AndroidNcnnUpscaler.initialize()` crée une instance GPU sans d’abord vérifier que la fonctionnalité est activée et que le modèle sera utilisé : [`AndroidNcnnUpscaler.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-domain/core/src/androidMain/kotlin/snd/komelia/image/AndroidNcnnUpscaler.kt#L101-L129).

#### Correction recommandée

Découper le démarrage en trois niveaux :

1. **critique avant premier écran** : logging minimal, préférences nécessaires, navigation et authentification ;
2. **après premier affichage** : cache, vérification de mise à jour, préchauffage léger ;
3. **à la demande** : VIPS, ONNX, NCNN, OCR, modèles, transcription.

Un composant paresseux peut sérialiser l’initialisation et mémoriser son résultat :

```kotlin
class LazyNativeRuntime<T : AutoCloseable>(
    private val factory: suspend () -> T,
) : AutoCloseable {
    private val mutex = Mutex()
    private var instance: T? = null

    suspend fun get(): T = mutex.withLock {
        instance ?: factory().also { instance = it }
    }

    override fun close() {
        instance?.close()
        instance = null
    }
}
```

La création du contexte GPU NCNN devrait être conditionnée par :

- upscaling activé ;
- modèle installé ;
- lecteur d’images actuellement ouvert ;
- absence de mode économie d’énergie sévère ;
- état thermique acceptable.

Pour éviter le coût de création/destruction lors de navigations rapides, conserver le runtime pendant un délai court, par exemple 15–30 secondes après la fermeture du dernier lecteur, puis le fermer s’il n’est pas réutilisé.

#### Validation

- tracer séparément `loadVips`, `loadOnnx`, `loadNcnn`, `createGpuInstance` et `installModel` ;
- mesurer le démarrage d’un utilisateur qui reste sur Home ;
- mesurer le premier accès au lecteur après lazy-loading ;
- vérifier qu’une initialisation concurrente n’instancie jamais deux runtimes.

### 3.3 Copie du modèle de bulles lors de chaque version applicative — P2

#### Constat

Le modèle de détection de bulles, d’environ 11 Mio, est copié des assets vers le stockage privé lorsque le marqueur ne correspond pas à `versionName` : [`AndroidAppModule.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-app/src/androidMain/kotlin/snd/komelia/AndroidAppModule.kt#L169-L198).

Une nouvelle version de Kora recopie donc le modèle même si le fichier est identique.

#### Correction recommandée

- versionner le modèle indépendamment de l’application ;
- embarquer son SHA-256 ou une constante `BUBBLE_MODEL_VERSION` ;
- copier uniquement si version/hash/taille diffèrent ;
- différer l’extraction jusqu’à l’activation de l’inversion des bulles ;
- écrire dans un fichier temporaire, puis renommer atomiquement.

Exemple de marqueur :

```kotlin
private const val BUBBLE_MODEL_VERSION = "rf-detr-bubbles-v3"

if (target.isFile && marker.readTextOrNull() == BUBBLE_MODEL_VERSION) {
    return
}
```

---

## 4. Cycles de vie, ressources natives et scopes

### 4.1 Propriété incomplète des ressources — P0

#### Constat

[`AppModule.close()`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-app/src/commonMain/kotlin/snd/komelia/AppModule.kt#L647-L655) ferme le module offline, les scopes principaux, Ktor et Coil. [`AndroidAppModule.close()`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-app/src/androidMain/kotlin/snd/komelia/AndroidAppModule.kt#L537-L543) ferme les bases et la pool OkHttp.

Il ne ferme toutefois pas explicitement `ncnnUpscaler`, alors que [`AndroidNcnnUpscaler.close()`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-domain/core/src/androidMain/kotlin/snd/komelia/image/AndroidNcnnUpscaler.kt#L328-L340) libère le modèle et l’instance GPU.

Autres ressources sans propriété suffisamment explicite :

- `ManagedKomgaEvents`, qui crée deux scopes et possède une session SSE ;
- `SyncManager`, qui crée son propre scope mais n’est pas conservé par `OfflineModule` ;
- le détecteur de panneaux et ses objets ONNX ;
- certains scopes de flush du lecteur ;
- les collectors de transcription relancés dans un scope externe.

[`ManagedKomgaEvents`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-domain/core/src/commonMain/kotlin/snd/komelia/ManagedKomgaEvents.kt#L40-L88) ne propose pas de `close()`. [`OfflineModule.close()`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-domain/offline/src/commonMain/kotlin/snd/komelia/offline/OfflineModule.kt#L139-L146) ne peut pas fermer le `SyncManager` créé localement.

#### Risque

Après un changement de serveur ou la fermeture d’un lecteur, un ancien graphe peut rester référencé par son propre scope. Il peut continuer à :

- observer des flows ;
- essayer de se reconnecter ;
- conserver un cache ou un client ;
- détenir un contexte GPU ;
- recevoir des événements ;
- empêcher la collecte d’un graphe complet.

Ce type de fuite est particulièrement coûteux : la RAM augmente, mais la batterie peut également être touchée si les jobs survivants se réveillent.

#### Correction recommandée

Adopter une propriété arborescente :

```text
Application
└── ServerSessionManager
    └── AppModule du serveur courant
        ├── Ktor / OkHttp / Coil
        ├── ManagedKomgaEvents
        ├── OfflineModule
        │   ├── SyncManager
        │   └── TaskProcessor
        └── NativeFeatureRegistry
            ├── ONNX Runtime
            ├── PanelDetector
            └── NCNN Upscaler / GPU
```

Chaque nœud doit fermer tous ses enfants avant de fermer ses propres ressources.

Une abstraction simple peut réduire les oublis :

```kotlin
class CloseStack : AutoCloseable {
    private val resources = ArrayDeque<AutoCloseable>()

    fun <T : AutoCloseable> own(resource: T): T {
        resources.addFirst(resource)
        return resource
    }

    override fun close() {
        resources.forEach { resource ->
            runCatching { resource.close() }
        }
        resources.clear()
    }
}
```

Une fermeture de module devrait suivre cet ordre :

1. empêcher la création de nouveaux jobs ;
2. arrêter les producteurs d’événements ;
3. annuler les scopes ;
4. attendre les jobs critiques bornés ;
5. fermer les sessions réseau ;
6. libérer ONNX/NCNN/GPU ;
7. fermer caches et bases.

Ne pas utiliser `runBlocking` sur le thread principal pour attendre la libération native. Préférer un `suspend fun close()` ou une phase de fermeture sur `Dispatchers.IO/Default`.

#### Validation

- effectuer 20 changements de serveur et suivre heap Java, heap native et nombre de threads ;
- ouvrir/fermer 20 fois chaque lecteur ;
- vérifier que le nombre de connexions SSE revient à un ;
- tracer chaque création/fermeture de runtime avec un identifiant ;
- utiliser LeakCanary en debug Android ;
- ajouter des tests de cycle de vie vérifiant l’annulation des scopes.

---

## 5. Réseau, SSE et activité de fond

### 5.1 Reconnexion SSE fixe toutes les dix secondes — P1

#### Constat

[`RemoteApi.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-domain/core/src/commonMain/kotlin/snd/komelia/api/RemoteApi.kt#L49-L84) tente de créer la session SSE dans une boucle. En cas de `ClientRequestException`, la boucle attend 10 000 ms puis recommence.

Limites :

- délai fixe ;
- absence de jitter ;
- absence d’observation de la connectivité ;
- absence de distinction claire entre erreur d’authentification, serveur indisponible et erreur transitoire ;
- pas de suspension liée au foreground/background ;
- risque qu’un ancien scope continue après remplacement du module.

#### Correction recommandée

Mettre en place une machine d’état de reconnexion :

```kotlin
private suspend fun connectWithBackoff() {
    var attempt = 0

    while (currentCoroutineContext().isActive) {
        networkState.first { it.isConnected }
        foregroundState.first { it || backgroundSseRequired() }

        try {
            val session = factory.sseSession()
            attempt = 0
            session.incoming.collect(events::emit)
        } catch (e: AuthenticationException) {
            authenticationEvents.emit(AuthenticationRequired)
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val base = minOf(1_000L shl attempt.coerceAtMost(8), 300_000L)
            val jitter = Random.nextLong(0, base / 4 + 1)
            delay(base + jitter)
            attempt++
        }
    }
}
```

Conseils :

- plafond entre 2 et 5 minutes ;
- remise à zéro seulement après connexion stable, pas après un simple handshake ;
- pas de retry automatique rapide sur HTTP 401/403 ;
- observabilité : compteur de tentatives, durée de déconnexion, dernière erreur ;
- si Kora n’a pas besoin des événements instantanés en arrière-plan, arrêter la session à `onStop` et la recréer au foreground.

#### Validation

Tester : Wi-Fi coupé, serveur éteint, DNS invalide, token expiré, réseau basculant Wi-Fi/4G, arrière-plan pendant une heure. Mesurer les réveils et bytes réseau par heure.

### 5.2 Synchronisation offline en N+1 — P1

#### Constat

[`SyncManager.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-domain/offline/src/commonMain/kotlin/snd/komelia/offline/sync/SyncManager.kt#L61-L133) synchronise les données locales en demandant successivement :

- chaque bibliothèque ;
- chaque série ;
- chaque livre.

La fréquence est limitée à une fois toutes les six heures, mais le coût d’une exécution reste proportionnel au nombre total d’objets offline.

#### Correction recommandée

Ordre de préférence :

1. **API delta serveur** : récupérer tout ce qui a changé depuis un curseur/timestamp ;
2. **API bulk** : demander les IDs locaux par lots et recevoir les objets existants/modifiés ;
3. **pagination par date de mise à jour** ;
4. si aucune API n’est possible, parallélisme faible et borné, par exemple 2–4 requêtes, avec regroupement des écritures.

Schéma delta idéal :

```text
GET /api/sync/changes?cursor=<opaque>

{
  "nextCursor": "...",
  "libraries": [...],
  "series": [...],
  "books": [...],
  "deletedIds": [...]
}
```

Le curseur doit être mis à jour uniquement après validation de la transaction locale.

Si seul le client peut être modifié :

- récupérer les pages serveur triées par `lastModified` ;
- arrêter dès que les éléments sont antérieurs au dernier sync ;
- utiliser `If-None-Match`/ETag lorsque possible ;
- écrire par chunks dans une transaction ;
- vérifier l’annulation entre les chunks.

La synchronisation persistante devrait être exprimée comme travail unique WorkManager avec contraintes `CONNECTED`, et éventuellement `BatteryNotLow`/`UNMETERED` selon la préférence utilisateur.

### 5.3 Téléchargements WorkManager — P1

#### Constat

[`AndroidDownloadManager.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-domain/offline/src/androidMain/kotlin/snd/komelia/offline/sync/AndroidDownloadManager.kt#L15-L32) crée un `OneTimeWorkRequest` sans `Constraints`. [`DownloadWorker.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-domain/offline/src/androidMain/kotlin/snd/komelia/offline/sync/DownloadWorker.kt#L35-L115) autorise quatre jobs et retourne `failure()` en cas d’échec.

#### Correction recommandée

Base minimale :

```kotlin
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .setRequiresStorageNotLow(true)
    .build()

val request = OneTimeWorkRequestBuilder<DownloadWorker>()
    .setConstraints(constraints)
    .setBackoffCriteria(
        BackoffPolicy.EXPONENTIAL,
        30,
        TimeUnit.SECONDS,
    )
    .setInputData(input)
    .build()
```

Classer les erreurs :

- annulation utilisateur : `failure()` ou arrêt normal sans retry ;
- 404/403 : `failure()` ;
- timeout, DNS temporaire, 5xx, perte réseau : `retry()` ;
- stockage plein : notification explicite et `failure()`.

Adapter la concurrence :

- mode normal, Wi-Fi et batterie correcte : 2–4 ;
- réseau mesuré ou économie d’énergie : 1 ;
- batterie critique : suspendre les téléchargements non explicitement urgents.

WorkManager prend nativement en charge les contraintes réseau, batterie, charge et stockage : [documentation Android](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).

### 5.4 Rafraîchissement des widgets — P2

#### Constat

[`WidgetRefresher.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-app/src/androidMain/kotlin/snd/komelia/widget/WidgetRefresher.kt#L20-L70) rafraîchit au démarrage, après la fin d’un livre et lors du passage en arrière-plan. Chaque ID Glance est mis à jour séparément.

[`NextBookWidget.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-app/src/androidMain/kotlin/snd/komelia/widget/NextBookWidget.kt#L81-L139) refait la requête et recharge les couvertures. [`WidgetCache.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-app/src/androidMain/kotlin/snd/komelia/widget/WidgetCache.kt#L49-L76) réécrit les images WebP.

#### Correction recommandée

Séparer deux opérations :

1. **mise à jour des données partagées du widget**, exécutée une seule fois ;
2. **rendu des instances**, réutilisant les mêmes données et fichiers.

Algorithme :

```text
si aucun widget installé -> sortir
si cache frais et aucune donnée marquée sale -> sortir
récupérer une seule fois les prochains livres
comparer (bookId, thumbnailVersion) avec le cache
pour chaque couverture modifiée seulement : charger + écrire
sauver atomiquement l’index
mettre à jour toutes les instances
```

Ajouter :

- TTL de 15–60 minutes selon le besoin ;
- dirty flag sur progression/fin de livre ;
- `Mutex` global de refresh ;
- `debounce` sur les événements rapprochés ;
- absence de refresh systématique à chaque `onStop` si les données sont inchangées.

---

## 6. Lecteur d’images, tiling et traitements IA

### 6.1 Retiling complet pendant le zoom — P0

#### Constat

[`TilingReaderImage.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-domain/core/src/commonMain/kotlin/snd/komelia/image/TilingReaderImage.kt#L98-L109) traite les mises à jour avec un délai de 50 ms, soit jusqu’à environ 20 calculs par seconde.

Lorsque `scaleFactor` diffère du précédent, le calcul couvre l’image entière. Pendant un pinch-to-zoom, le facteur change continuellement. La page peut donc être retuilée intégralement à chaque étape du geste. Le code recherche en plus une tuile existante avec `oldTiles.find` pour chaque nouvelle tuile : [`TilingReaderImage.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-domain/core/src/commonMain/kotlin/snd/komelia/image/TilingReaderImage.kt#L366-L416).

#### Correction recommandée

Mettre en place trois qualités temporelles :

1. **pendant le geste** : réutiliser les tuiles existantes et les mettre à l’échelle visuellement ;
2. **court idle, 80–150 ms** : charger les tuiles visibles au niveau quantifié le plus proche ;
3. **fin du geste, 200–300 ms** : produire les tuiles haute qualité nécessaires.

Quantifier les niveaux :

```kotlin
private fun quantizeScale(scale: Float): Float {
    val levels = floatArrayOf(0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)
    return levels.minBy { abs(it - scale) }
}
```

Indexer les tuiles :

```kotlin
data class TileKey(val level: Int, val x: Int, val y: Int)

val oldByKey: Map<TileKey, Tile> = oldTiles.associateBy(Tile::key)
val existing = oldByKey[newKey]
```

Limiter le travail à :

- viewport visible ;
- marge d’une tuile dans la direction du mouvement ;
- budget maximum de pixels ;
- nombre maximum de décodages concurrents.

Il faut également distinguer une transformation visuelle temporaire d’un nouveau niveau de décodage. Un changement de quelques pourcents ne devrait pas forcer un nouveau bitmap source.

#### Validation

- trace Perfetto pendant un pinch de 5 secondes ;
- nombre de tuiles créées/détruites par seconde ;
- allocations bitmap et heap native ;
- jank P95/P99 ;
- comparer page 2 000 × 3 000 et image extrême 10 000 × 20 000 ;
- répéter avec upscaling et traitement de bulles activés.

### 6.2 File NCNN illimitée — P0

#### Constat

[`AndroidNcnnUpscaler.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-domain/core/src/androidMain/kotlin/snd/komelia/image/AndroidNcnnUpscaler.kt#L45-L95) utilise `Channel.UNLIMITED`. La génération invalide logiquement les anciennes requêtes, mais ne vide pas immédiatement la file. Le travail actif est entouré de `withContext(NonCancellable)`.

Une requête peut capturer des objets image volumineux. La file peut donc retenir plusieurs bitmaps même si l’utilisateur a déjà quitté les pages correspondantes.

#### Correction recommandée

Une queue dédiée au lecteur doit répondre à ces règles :

- taille bornée ;
- priorité page courante, puis page suivante ;
- remplacement des requêtes pour la même page ;
- suppression immédiate des générations obsolètes ;
- aucune conversion bitmap lourde avant admission ;
- annulation coopérative entre les tuiles.

Exemple de file simple :

```kotlin
private val requests = Channel<UpscaleRequest>(capacity = 2)

suspend fun submit(request: UpscaleRequest): KomeliaImage? {
    requests.trySend(request).onFailure {
        request.disposeInput()
        request.result.complete(null)
    }
    return request.result.await()
}
```

Pour une vraie priorité, préférer un scheduler possédant un petit `PriorityQueue` protégé par `Mutex`, avec signal conflated. À chaque changement de page :

1. incrémenter la génération ;
2. retirer et disposer toutes les anciennes demandes ;
3. demander l’annulation du travail actif ;
4. insérer la page courante en tête.

Éviter `NonCancellable` autour de l’inférence complète. Si l’API native n’est pas interruptible, diviser l’image en tuiles et vérifier l’annulation entre les appels natifs. Le résultat d’une inférence obsolète doit être immédiatement libéré, jamais publié dans un cache.

### 6.3 Création GPU non conditionnelle — P1

Le contexte GPU est créé dès l’initialisation de l’upscaler. La vérification devrait précéder la création :

```kotlin
combine(
    settings.upscalingEnabled,
    modelInstalled,
    readerVisible,
    powerPolicy.allowHeavyGpuWork,
) { enabled, installed, visible, allowed ->
    enabled && installed && visible && allowed
}.distinctUntilChanged().collectLatest { shouldRun ->
    if (shouldRun) ensureGpuCreated() else releaseGpuAfterGracePeriod()
}
```

Il faut tester la destruction/création répétée sur plusieurs pilotes Vulkan. Si elle est instable ou très coûteuse, garder le contexte pour la session de lecture, mais pas pour toute la vie du processus.

### 6.4 Pré-détection des panneaux sur trois pages — P1

#### Constat

[`PanelsReaderState.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/panels/PanelsReaderState.kt#L545-L607) passe par `launchDownload()` pour la page courante et les pages adjacentes. Ce chemin réalise la préparation de l’image puis `detect()`.

#### Correction recommandée

Politique suggérée :

- page courante : priorité immédiate ;
- suivante : traitement après 300–800 ms sans changement de page ;
- précédente : uniquement si la direction de lecture récente le justifie ;
- sauts rapides : annuler tout sauf la destination ;
- économie d’énergie : aucune détection anticipée.

Ajouter un cache persistant :

```text
PanelCacheKey = hashDuFichier
              + indexPage
              + versionModèle
              + réglagesPrétraitement
              + versionAlgorithme
```

Les résultats sont petits par rapport au coût de l’inférence. Leur persistance est donc particulièrement rentable. Une modification de modèle ou de prétraitement invalide naturellement les anciennes entrées.

### 6.5 Préchargement adaptatif

Les caches bornés actuels sont positifs, mais la quantité de préchargement devrait dépendre du coût réel de la page :

```kotlin
data class ReaderWorkPolicy(
    val pagesAhead: Int,
    val pagesBehind: Int,
    val allowAiOnPrefetch: Boolean,
    val allowUpscaleOnPrefetch: Boolean,
)
```

Exemples :

| Situation | Devant | Derrière | IA sur préfetch |
|---|---:|---:|---|
| Batterie normale, page légère | 2–3 | 1 | Éventuellement |
| Image très haute résolution | 1 | 0–1 | Non |
| Mode panneaux | 1 | 0 | Après idle |
| Économie d’énergie | 1 | 0 | Non |
| Température élevée | 0–1 | 0 | Non |

### 6.6 Animations hors écran

Une image animée conservée dans un cache peut continuer à produire des frames si sa boucle dépend seulement de la durée de vie de l’objet. La boucle doit recevoir un état de visibilité :

```kotlin
visibleFlow.distinctUntilChanged().collectLatest { visible ->
    if (!visible) return@collectLatest
    playFramesUntilHidden()
}
```

Suspendre également les animations lorsque le processus passe en arrière-plan.

### 6.7 Extraction de couleur immersive — P2

[`ColorExtraction.android.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-ui/src/androidMain/kotlin/snd/komelia/ui/common/immersive/ColorExtraction.android.kt#L11-L21) copie une bitmap hardware complète en ARGB avant `Palette`.

Correction : demander dès le départ une miniature software de 64–128 px, puis mettre en cache le résultat par clé de miniature.

```kotlin
val request = ImageRequest.Builder(context)
    .data(source)
    .size(96, 96)
    .allowHardware(false)
    .build()
```

Recycler explicitement uniquement les bitmaps dont le composant est propriétaire ; ne jamais recycler une bitmap fournie et partagée par Coil.

---

## 7. Audio et transcription

### 7.1 Polling de position redondant — P1

#### Constat

[`AudiobookFolderController.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/AudiobookFolderController.kt#L501-L515) interroge le lecteur toutes les 500 ms. À chaque itération, il :

- lit l’index courant ;
- lit la position ;
- recalcule la somme des durées précédentes ;
- recherche le chapitre ;
- vérifie si la position correspond à un signet.

Un autre flux de progression existe dans le lecteur audio, ce qui multiplie les sources temporelles.

#### Correction recommandée

Créer un unique `PlaybackClock` :

```kotlin
interface PlaybackClock {
    val position: StateFlow<PlaybackPosition>
    val isPlaying: StateFlow<Boolean>
}

data class PlaybackPosition(
    val trackIndex: Int,
    val trackPositionMs: Long,
    val bookPositionMs: Long,
)
```

Le ticker :

- fonctionne uniquement lorsque `isPlaying == true` ;
- publie à 500 ms pour l’UI, voire 1 s si la précision visuelle suffit ;
- publie immédiatement sur seek/changement de piste ;
- utilise `distinctUntilChanged` sur les valeurs dérivées.

Pré-calculer les offsets :

```kotlin
val trackOffsetsMs = LongArray(tracks.size)
var total = 0L
tracks.forEachIndexed { index, track ->
    trackOffsetsMs[index] = total
    total += track.durationMs
}

val bookPosition = trackOffsetsMs[index] + trackPosition
```

Le chapitre courant peut être trouvé par recherche binaire sur les positions de début, plutôt que par scan complet.

### 7.2 Ticker transcription à 5 Hz — P1

#### Constat

[`LiveTranscriptEngine.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-infra/audiobook-transcription/src/main/java/snd/komelia/transcription/LiveTranscriptEngine.kt#L43-L65) exécute une boucle toutes les 200 ms. Elle reconstruit les segments visibles et une chaîne de diagnostic.

[`TranscriptStore.visibleSegmentsForPlayback`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-infra/audiobook-transcription/src/main/java/snd/komelia/transcription/TranscriptStore.kt#L47-L53) n’utilise pas son argument `playbackMs` et retourne pratiquement toute la liste.

#### Correction recommandée

- ticker à 500–1 000 ms uniquement pendant la lecture ;
- publication immédiate lors d’un nouveau segment final/intermédiaire ;
- calcul réel d’une fenêtre autour de `playbackMs` ;
- ne pas reconstruire l’état de diagnostic en release à chaque tick ;
- `distinctUntilChanged()` sur la liste visible ou sur une clé `(firstId, lastId, interimId)`.

Exemple :

```kotlin
fun visibleSegmentsForPlayback(playbackMs: Long): List<TranscriptSegment> {
    val from = playbackMs - 30_000L
    val to = playbackMs + 5_000L
    return segments.value.filter { it.endMs >= from && it.startMs <= to }
}
```

Pour une longue session, conserver les segments triés et effectuer deux recherches binaires afin d’éviter un filtrage complet.

### 7.3 Recréation de `MediaCodec` par bloc de 2 secondes — P0

#### Constat

[`AudioPreReader.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-infra/audiobook-transcription/src/main/java/snd/komelia/transcription/AudioPreReader.kt#L27-L65) demande des blocs de deux secondes. [`decodeChunkAt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-infra/audiobook-transcription/src/main/java/snd/komelia/transcription/AudioPreReader.kt#L82-L162) recrée pour chaque bloc un `MediaExtractor` et un `MediaCodec`, refait la sélection de piste et un seek, puis détruit les objets.

#### Correction recommandée

Créer une session de décodage persistante par piste :

```kotlin
class TrackDecoderSession(...) : AutoCloseable {
    private val extractor = MediaExtractor()
    private val codec: MediaCodec
    private var decodedPositionMs = 0L

    init {
        extractor.setDataSource(context, uri, null)
        // sélectionner la piste, configurer et démarrer une seule fois
    }

    fun decodeNext(maxDurationMs: Long): PcmChunk? {
        // décodage séquentiel sans seek ni recréation
    }

    fun seekTo(positionMs: Long) {
        extractor.seekTo(positionMs * 1_000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        codec.flush()
        decodedPositionMs = positionMs
    }

    override fun close() {
        runCatching { codec.stop() }
        codec.release()
        extractor.release()
    }
}
```

`AudioPreReader` garde la session tant que :

- la piste ne change pas ;
- aucun seek non séquentiel n’intervient ;
- la transcription reste active.

Le bloc peut être un peu plus grand, par exemple 5–10 secondes, si la latence de la transcription le permet. Cela amortit les passages Kotlin/native. Il faut toutefois conserver une limite mémoire et des points d’annulation.

Éviter `ByteArrayOutputStream.toByteArray()` lorsque possible, car il ajoute une copie. Un pool de buffers primitifs ou un buffer direct réutilisable serait préférable.

#### Validation

- compter les créations de codec par minute : cible proche de 1 par piste, pas 30 par minute ;
- mesurer CPU par minute audio ;
- mesurer allocations et GC ;
- tester MP3, AAC, Opus et fichiers à sample rate/canaux différents ;
- tester seek rapide et changement de piste ;
- vérifier la libération en cas d’annulation.

### 7.4 Boxing des échantillons PCM — P1

#### Constat

[`WhisperTranscriptionBackend.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-infra/audiobook-transcription/src/main/java/snd/komelia/transcription/WhisperTranscriptionBackend.kt#L50-L62) transforme le buffer en `ShortArray`, puis en `List<Short>` via `toList()`.

Chaque sample devient un objet boxé. À 16 kHz, dix secondes représentent 160 000 échantillons et donc potentiellement 160 000 objets temporaires, hors autres copies.

#### Correction recommandée

Utiliser un ring buffer primitif :

```kotlin
class ShortRingBuffer(private val capacity: Int) {
    private val data = ShortArray(capacity)
    private var readIndex = 0
    private var size = 0

    fun write(source: ShortArray, count: Int = source.size) { /* copie circulaire */ }
    fun readInto(target: ShortArray, count: Int): Int { /* sans boxing */ }
}
```

Si Whisper attend des floats, convertir directement les bytes PCM vers un `FloatArray` réutilisé :

```kotlin
out[i] = sample.toFloat() / 32768f
```

Réduire les transformations :

```text
ByteArray décodé
  -> resampling vers FloatArray 16 kHz mono
  -> fenêtre Whisper dans buffer réutilisé
```

Éviter la chaîne actuelle potentielle :

```text
ByteArray -> ShortArray -> List<Short> -> nouvelle liste -> FloatArray
```

### 7.5 Jobs de transcription et `GlobalScope`

La libération native lancée dans `GlobalScope` n’est pas reliée au propriétaire. Les collectors de `engine.state` et `engine.visibleSegments` sont lancés dans un scope externe sans conservation explicite des jobs ; des démarrages répétés peuvent accumuler des collectors.

Correction :

- un `transcriptionSessionJob` unique ;
- garde contre un double `start()` ;
- `stopAndJoin()` qui annule le ticker, le pré-reader, les collectors et attend la libération native ;
- aucun `GlobalScope` ;
- état `Starting/Active/Stopping/Idle` sérialisé par `Mutex`.

```kotlin
suspend fun stopTranscription() = lifecycleMutex.withLock {
    val session = currentSession ?: return
    currentSession = null
    session.stopAndJoin()
}
```

### 7.6 Transcription liée à l’état de lecture

`AudioPreReader` reste à environ 12 secondes d’avance et se réveille toutes les 500 ms lorsqu’il est suffisamment en avance. Il ne reçoit pas directement un signal `isPlaying`.

Le traitement devrait être suspendu sans polling lorsque l’audio est en pause :

```kotlin
isPlaying.first { it }
```

Sur pause :

- arrêter la production de nouveaux blocs ;
- conserver éventuellement l’état Whisper pendant un délai court ;
- stopper totalement après un timeout ou au passage en arrière-plan prolongé ;
- reprendre depuis la position courante.

---

## 8. Accueil, événements et Compose

### 8.1 Chargement de toutes les étagères Home — P2

#### Constat

[`HomeViewModel.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-ui/src/commonMain/kotlin/snd/komelia/ui/home/HomeViewModel.kt#L185-L240) affiche d’abord un snapshot disque, ce qui est une bonne optimisation. Il lance ensuite une coroutine par étagère et attend leur fin.

Lors d’événements `BookEvent`, `SeriesEvent` ou progression, un rechargement global est demandé. La boucle effectue `load()` puis attend cinq secondes. Le délai est donc un throttle après le coût, pas un debounce avant le coût.

#### Correction recommandée

Utiliser un vrai quiet-period debounce :

```kotlin
reloadRequests
    .debounce(1_500)
    .mapLatest { reason -> refreshAffectedShelves(reason) }
    .launchIn(screenModelScope)
```

Remplacer `Unit` par une raison structurée :

```kotlin
sealed interface HomeInvalidation {
    data class BookChanged(val bookId: String) : HomeInvalidation
    data class SeriesChanged(val seriesId: String) : HomeInvalidation
    data object LibraryChanged : HomeInvalidation
    data object ManualRefresh : HomeInvalidation
}
```

Associer chaque étagère à ses dépendances. Une progression de lecture affecte probablement « Continuer à lire » et « Récemment lu », pas nécessairement toutes les sélections aléatoires.

Limiter la concurrence réseau avec un sémaphore global. Charger d’abord les étagères visibles et déclencher les autres lors de l’approche du viewport ou après idle.

### 8.2 Réglages UI collectés séparément — P3

`MainView` crée de nombreux `LaunchedEffect` pour chaque préférence. Le coût unitaire est faible, mais cela disperse les mises à jour et complique l’identification des recompositions.

Créer un snapshot :

```kotlin
data class UiPreferences(
    val theme: Theme,
    val accentColor: Color?,
    val immersiveColorEnabled: Boolean,
    // ...
)
```

Le repository peut combiner les flows puis exposer un seul `StateFlow<UiPreferences>`. Utiliser `collectAsStateWithLifecycle` sur Android lorsque compatible avec l’architecture KMP.

Cette optimisation est secondaire. Elle ne doit pas retarder les corrections image/audio/réseau.

### 8.3 Points positifs UI

- absence détectée de boucles Compose `rememberInfiniteTransition`/`infiniteRepeatable` dans les sources Kotlin ;
- caches de pages bornés ;
- état Home persistant affiché avant le réseau ;
- cache spécifique des étagères aléatoires ;
- maintien de l’écran EPUB conditionné par le réglage utilisateur et désactivé lors du dispose.

---

## 9. Base de données et traitement offline

### 9.1 Toutes les transactions sont `NonCancellable` — P2

#### Constat

[`ExposedRepository.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/ExposedRepository.kt#L12-L18) exécute les transactions dans `Dispatchers.IO + NonCancellable`.

Une lecture ou opération bulk déclenchée par un écran continue donc après navigation ou changement de serveur.

#### Correction recommandée

- garder les lectures annulables ;
- vérifier l’annulation entre les chunks ;
- réserver `NonCancellable` à une phase de commit/cleanup très courte ;
- éviter d’englober la transformation métier et les appels réseau dans une transaction non annulable.

```kotlin
suspend fun <T> readTransaction(block: suspend Transaction.() -> T): T =
    withContext(Dispatchers.IO) {
        transaction { ensureActive(); block() }
    }
```

Pour une écriture critique : préparer les données de façon annulable, puis limiter la zone non annulable à l’application atomique finale.

### 9.2 SQLite et durabilité

Les choix suivants sont positifs :

- WAL ;
- une connexion Hikari principale, évitant une contention excessive SQLite ;
- écritures bulk découpées à plusieurs endroits.

Pour les bases qui ne contiennent que des caches reconstruisibles, évaluer `PRAGMA synchronous=NORMAL` afin de réduire les fsync. Ne pas appliquer aveuglément ce réglage aux annotations, signets ou préférences utilisateur. La politique de durabilité devrait dépendre de la nature des données.

Vérifier également :

- stratégie de checkpoint WAL ;
- taille du WAL après longues sessions ;
- migrations effectuées sur le chemin critique ;
- index utilisés par les requêtes principales ;
- transactions réellement regroupées lors des synchronisations.

### 9.3 `TaskProcessor` sans limite explicite — P1

#### Constat

[`TaskProcessor.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-domain/offline/src/commonMain/kotlin/snd/komelia/offline/tasks/TaskProcessor.kt#L49-L100) utilise un buffer de fin de tâche illimité et lance un job par tâche disponible dans `processorScope`.

Un grand backlog peut provoquer :

- de nombreux jobs vivants ;
- concurrence réseau/disque excessive ;
- forte consommation mémoire ;
- batterie et température défavorables.

#### Correction recommandée

Worker pool fixe :

```kotlin
private val queue = Channel<OfflineTask>(capacity = 64)

fun start(workerCount: Int = 2) {
    repeat(workerCount) {
        processorScope.launch {
            for (task in queue) {
                processTask(task)
            }
        }
    }
}
```

Autres règles :

- unicité par clé métier lorsque deux tâches se remplacent ;
- coalescence des mises à jour de progression ;
- limite séparée réseau/disque/CPU ;
- priorité aux actions explicitement déclenchées par l’utilisateur ;
- persistance de l’état et retry borné.

### 9.4 Buffers `Int.MAX_VALUE`

Les flux de touches, événements offline et fins de tâches utilisent parfois `extraBufferCapacity = Int.MAX_VALUE`. Un buffer illimité transforme une surcharge temporaire en pression mémoire différée.

Choisir une sémantique explicite :

- touches volume : buffer très petit avec `DROP_OLDEST` ;
- invalidations UI : conflation ;
- événements qui doivent être durables : base de données/queue persistante ;
- progression : dernière valeur seulement ;
- tâches : channel borné et backpressure.

---

## 10. Taille, packaging et coûts d’installation

### 10.1 Points positifs

[`komelia-app/build.gradle.kts`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-app/build.gradle.kts#L116-L208) montre :

- R8 actif en release ;
- release non debuggable ;
- ABI Android limitée à `arm64-v8a` ;
- règles et variante de test R8 documentées ;
- exclusions de ressources et résolution explicite d’ONNX Runtime.

### 10.2 Ressources potentiellement coûteuses

L’application rassemble dans sa distribution de base plusieurs piles lourdes :

- VIPS ;
- ONNX Runtime ;
- NCNN ;
- FFmpeg/audio ;
- OCR ;
- modèles de détection et de transcription.

Pistes :

- modèles téléchargés lors de la première activation ;
- packs optionnels par fonction ;
- variantes de distribution « lecteur léger » / « IA complète » si pertinent ;
- compression/noCompress adaptée aux besoins de mmap du runtime ;
- vérification du contenu réel de l’AAB avec APK Analyzer.

`isShrinkResources` est désactivé volontairement à cause de Flyway. Une expérimentation pourrait conserver explicitement les migrations et activer le shrink sur une variante de test. Cela doit être validé par installation propre et migration depuis plusieurs anciennes versions.

### 10.3 Fichiers du dépôt

Le fichier `komelia-infra/jni/unused_libs/libonnxruntime.so.backup`, d’environ 17 Mio, semble être un artefact de dépôt. Sans preuve de packaging, son impact principal est le clone, le cache CI et le stockage Git, pas nécessairement l’APK.

Les nombreuses grandes images PNG de catalogues peuvent être étudiées pour :

- WebP lossless ;
- résolution réduite à la taille réellement affichée ;
- chargement distant et cache ;
- suppression des métadonnées inutiles.

---

## 11. Politique centrale d’économie d’énergie

Les optimisations précédentes seraient plus cohérentes avec un objet de politique partagé :

```kotlin
data class PowerPolicy(
    val isPowerSaveMode: Boolean,
    val isBatteryLow: Boolean,
    val thermalLevel: ThermalLevel,
    val isMeteredNetwork: Boolean,
    val isCharging: Boolean,
) {
    val allowAiPrefetch: Boolean
        get() = !isPowerSaveMode && !isBatteryLow && thermalLevel < ThermalLevel.MODERATE

    val downloadConcurrency: Int
        get() = if (isPowerSaveMode || isMeteredNetwork) 1 else 3

    val readerPrefetchPages: Int
        get() = if (allowAiPrefetch) 2 else 1
}
```

### 11.1 Comportement recommandé

| Fonction | Normal | Économie d’énergie |
|---|---|---|
| Préchargement pages | 2–3 légères | 0–1 |
| Upscaling préchargé | Selon réglage | Désactivé |
| Détection panneaux anticipée | Après idle | Page courante seulement |
| OCR/bulles anticipés | Possible | À la demande |
| Téléchargements simultanés | 2–4 | 1 |
| Sync automatique | Réseau disponible | Différée |
| Widget | TTL normal | TTL allongé |
| SSE arrière-plan | Selon besoin | Suspendu/backoff long |
| Transcription | Threads configurés | Threads réduits ou pause |

La politique doit rester contrôlable par l’utilisateur. Une action explicitement demandée, comme télécharger immédiatement un livre, peut dépasser temporairement certaines restrictions, avec un choix clair.

---

## 12. Instrumentation et mesures

### 12.1 Infrastructure manquante

Le projet possède [`PerfTrace.kt`](https://github.com/MKDevTests/Kora/blob/9b83935ca673c4fd2c3c13583acb2cf479871662/komelia-domain/core/src/commonMain/kotlin/snd/komelia/perf/PerfTrace.kt), qui conserve seulement 100 mesures et journalise des opérations grossières. C’est une bonne base de diagnostic applicatif.

Je n’ai cependant trouvé aucune preuve d’un module Macrobenchmark Android, d’un Baseline Profile propre à Kora ou d’une suite automatisée de mesure énergétique.

Android recommande Macrobenchmark pour les parcours utilisateur complets et les interfaces Compose : [documentation Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview). Les Baseline Profiles précompilent les chemins critiques dès l’installation et ciblent notamment démarrage, navigation et scrolling : [documentation Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview).

### 12.2 Macrobenchmarks recommandés

Créer une variante benchmark proche de release :

- non debuggable ;
- R8/minification identiques à la production ;
- signature locale ;
- données de test stables ;
- serveur de test à latence contrôlée.

Scénarios :

1. `coldStartupAuthenticated` ;
2. `warmStartupAuthenticated` ;
3. `homeFirstContent` ;
4. `libraryScrollLargeDataset` ;
5. `openImageReader` ;
6. `pageTurnBurst` ;
7. `pinchZoomLargePage` ;
8. `panelModeNavigation` ;
9. `openEpub` ;
10. `audioPlayback` ;
11. `audioPlaybackWithTranscription`.

Métriques :

- `StartupTimingMetric` ;
- `FrameTimingMetric` ;
- `TraceSectionMetric` pour VIPS/ONNX/NCNN/audio ;
- allocations et heap via profils complémentaires ;
- bytes réseau via environnement de test ;
- puissance sur appareil compatible.

### 12.3 Baseline Profile

Parcours à inclure :

- démarrage authentifié ;
- rendu Home ;
- navigation Home → bibliothèque → série → livre ;
- scroll de listes Compose ;
- ouverture du lecteur ;
- première page et changement de page.

Ne pas inclure aveuglément tous les chemins IA rares. Le profil doit cibler les méthodes fréquemment utilisées dont l’interprétation/JIT affecte l’expérience.

### 12.4 Traces applicatives

Ajouter des sections Android `Trace` autour de :

```text
Kora/Startup/LogcatSnapshot
Kora/Startup/NativeLibraries
Kora/Reader/DecodePage
Kora/Reader/BuildTiles
Kora/Reader/NcnnUpscale
Kora/Reader/OnnxPanelDetection
Kora/Audio/DecodeChunk
Kora/Audio/Resample
Kora/Audio/WhisperInference
Kora/Offline/Sync
Kora/Home/RefreshShelf
```

Ces sections doivent être grossières et échantillonnées. Éviter un log INFO par page/tile/événement, qui aurait lui-même un coût.

### 12.5 Mesure batterie

Android indique que Battery Historian n’est plus activement maintenu et recommande plutôt system tracing, Macrobenchmark power metrics ou Power Profiler lorsque possible : [documentation Android](https://developer.android.com/topic/performance/power/battery-historian).

Protocoles proposés :

#### Veille réseau

- batterie chargée et température stabilisée ;
- serveur inaccessible ;
- application en arrière-plan pendant 60 minutes ;
- relever tentatives réseau, réveils, CPU et perte de batterie ;
- comparer retry fixe et backoff/lifecycle.

#### Lecture d’images

- luminosité fixe ;
- même livre et mêmes pages ;
- 30 minutes avec scénario automatisé ;
- comparer préchargement normal, réduit et IA désactivée ;
- relever énergie/page, jank, heap native et température.

#### Audio/transcription

- écran éteint ;
- même fichier audio ;
- 30 minutes audio seul puis transcription ;
- comparer décodeur recréé et décodeur persistant ;
- relever CPU total, créations de codec, GC, énergie/minute.

### 12.6 Budgets de régression suggérés

À calibrer sur les appareils de référence :

| Indicateur | Budget initial suggéré |
|---|---:|
| Régression TTID P95 | < 10 % |
| Frames lentes sur scroll | < 5 % |
| Retiling pendant zoom | Pas plus d’un rebuild HQ après geste |
| Queue image | Bornée à 2–4 travaux lourds |
| Instances SSE | Exactement 0 ou 1 selon lifecycle |
| Créations codec transcription | Environ 1 par piste/seek |
| Tentatives réseau serveur offline | Backoff plafonné, pas 6/min indéfiniment |
| Heap après 20 ouvertures lecteur | Retour proche de la baseline |

---

## 13. Feuille de route proposée

### Phase 0 — Mesures de référence

Durée indicative : 2–4 jours.

- ajouter la variante Macrobenchmark ;
- définir deux appareils physiques de référence, milieu et bas de gamme ;
- capturer démarrage, zoom, navigation panneaux et transcription ;
- documenter taille APK/AAB, TTID/TTFD, jank, mémoire et énergie.

Cette phase peut avancer en parallèle des corrections évidentes, notamment le dump `logcat`.

### Phase 1 — Corrections rapides et garde-fous

Durée indicative : 3–6 jours.

1. retirer le snapshot `logcat` du thread principal ;
2. ajouter le backoff SSE et l’observation réseau/lifecycle ;
3. fermer explicitement `ManagedKomgaEvents`, `SyncManager`, NCNN et détecteurs ;
4. remplacer les buffers `Int.MAX_VALUE` ;
5. ajouter contraintes et retry WorkManager ;
6. empêcher les doubles démarrages de transcription.

Critère de sortie : aucun ancien module ne reste actif après changement de serveur ; aucune queue lourde n’est illimitée.

### Phase 2 — Lecteur d’images

Durée indicative : 1–2 sprints.

1. index O(1) des tuiles ;
2. viewport + marge au lieu de l’image complète ;
3. niveaux de zoom quantifiés ;
4. reconstruction HQ après geste ;
5. scheduler NCNN borné et priorisé ;
6. cache de résultats panneaux ;
7. préchargement adaptatif et politique batterie.

Critère de sortie : le pinch ne déclenche plus de décodage complet continu et une page quittée ne continue pas une longue chaîne de travaux.

### Phase 3 — Audio/transcription

Durée indicative : 1–2 sprints.

1. horloge audio unique ;
2. offsets et chapitres pré-calculés ;
3. session `MediaCodec` persistante ;
4. buffer PCM primitif ;
5. suppression des copies intermédiaires ;
6. ticker lié à `isPlaying` ;
7. cycle de vie de session explicite ;
8. limite de threads Whisper selon politique d’énergie.

Critère de sortie : nombre de créations de codec proche du nombre de pistes/seeks, aucune allocation proportionnelle au nombre d’échantillons sous forme d’objets Kotlin.

### Phase 4 — Réseau, offline et widgets

Durée indicative : 1–2 sprints, potentiellement plus si l’API serveur doit évoluer.

- sync delta/bulk ;
- worker pool offline borné ;
- invalidations Home ciblées ;
- chargement des étagères visibles en priorité ;
- widget partagé, diff de contenu et TTL ;
- réglages Wi-Fi/charge/batterie.

### Phase 5 — Taille et optimisation de livraison

- analyser APK/AAB ;
- expérimenter le resource shrinking avec règles Flyway ;
- déplacer les modèles rares hors du package de base ;
- compresser les assets ;
- générer et valider le Baseline Profile à chaque release.

---

## 14. Points positifs à préserver

Les changements ne doivent pas dégrader les bonnes décisions existantes :

- R8 actif en release ;
- ABI Android unique, réduisant la taille ;
- journalisation fichier via appender asynchrone ;
- niveau INFO en release plutôt que DEBUG généralisé ;
- caches Coil/OkHttp bornés ;
- caches de pages bornés ;
- affichage du snapshot Home avant le réseau ;
- cache des étagères aléatoires ;
- écritures de progression de téléchargement limitées en fréquence ;
- téléchargement persistant et unique par livre ;
- flux réseau de téléchargement traité progressivement ;
- certaines opérations bulk déjà découpées en chunks ;
- WAL SQLite ;
- échantillons `PerfTrace` limités à 100 ;
- maintien de l’écran contrôlé par préférence utilisateur.

La priorité est de renforcer les frontières de cycle de vie et les politiques de charge, pas de remplacer l’architecture entière.

---

## 15. Checklist de correction

### Démarrage

- [ ] Aucun processus externe ni I/O disque bloquante dans `Application.onCreate`.
- [ ] VIPS/ONNX/NCNN initialisés uniquement lorsque nécessaires.
- [ ] Contexte GPU créé uniquement si l’upscaler est activé.
- [ ] Modèle de bulles versionné indépendamment de l’application.
- [ ] StrictMode propre sur le démarrage debug.

### Cycles de vie

- [ ] Chaque `CoroutineScope` custom a un propriétaire et une fermeture.
- [ ] Chaque session SSE est annulée au remplacement de module.
- [ ] NCNN, GPU, ONNX et détecteurs sont explicitement libérés.
- [ ] Aucun `GlobalScope` dans les chemins applicatifs.
- [ ] Tests répétés d’ouverture/fermeture sans croissance mémoire durable.

### Réseau/offline

- [ ] Backoff exponentiel avec jitter.
- [ ] Attente explicite du réseau.
- [ ] Politique foreground/background définie.
- [ ] Sync delta/bulk ou parallélisme faible et borné.
- [ ] WorkManager avec contraintes et retry classifié.
- [ ] Worker pool offline borné.

### Lecteur d’images

- [ ] Tuiles indexées en O(1).
- [ ] Pas de retile complet continu pendant le pinch.
- [ ] File NCNN bornée.
- [ ] Travail obsolète réellement annulé ou abandonné entre tuiles.
- [ ] Pas d’IA sur les pages préchargées en mode économie.
- [ ] Résultats de panneaux persistés et versionnés.
- [ ] Animations suspendues hors écran.

### Audio/transcription

- [ ] Une seule horloge de position.
- [ ] Offsets de pistes pré-calculés.
- [ ] `MediaCodec` persistant par piste.
- [ ] Aucun `List<Short>` pour le PCM.
- [ ] Ticker arrêté en pause.
- [ ] Jobs de session annulés et joints à `stop`.
- [ ] Limite de threads/inférence adaptée au mode énergie.

### UI/widgets

- [ ] Invalidation Home ciblée et debounced avant le chargement.
- [ ] Concurrence des étagères bornée.
- [ ] Widget sans recompression des couvertures inchangées.
- [ ] Un seul fetch partagé pour toutes les instances.
- [ ] Aucun refresh de widget inutile à chaque passage arrière-plan.

### Mesures

- [ ] Macrobenchmarks sur appareil physique.
- [ ] Baseline Profile généré et vérifié.
- [ ] Traces dédiées aux traitements natifs.
- [ ] Tests batterie veille, image et transcription.
- [ ] Budgets de régression intégrés à la CI.

---

## 16. Journal d’implémentation sur la branche de travail

Cette section distingue l’audit initial des corrections effectivement développées et validées le 9 août 2026. Tous les changements sont isolés sur la branche [`agent/debug-performance-battery`](https://github.com/MKDevTests/Kora/tree/agent/debug-performance-battery). La branche `main` est restée sur `9b83935ca673c4fd2c3c13583acb2cf479871662`. Aucune tâche Gradle release, signature release, installation sur appareil, fusion ou création de tag n’a été exécutée.

### 16.1 Commits publiés

| Commit | Lot | Résultat principal |
|---|---|---|
| `c7452dc9` | Réseau, démarrage et offline | Snapshot `logcat` sorti du démarrage principal, reconnexion SSE avec backoff/jitter, buffers offline bornés, processeur de tâches limité à deux travaux |
| `f22d7eeb` | Téléchargements et widgets | Réseau requis par WorkManager, conservation du travail identique en cours, TTL/backoff/mutex de widget et invalidations ciblées |
| `a5a54502` | Horloge audio | Suppression des pollings redondants à 500 ms, réutilisation du callback du lecteur et offsets de pistes pré-calculés |
| `ed16b10c` | Cycle de vie | `SyncManager` possédé et fermé par `OfflineModule`, fermeture explicite de NCNN hors du thread principal |
| `166d6bc1` | Transcription | Ticker ramené à 1 Hz, prélecture suspendue en pause, tampon PCM primitif, scopes enfants rattachés, suppression de `GlobalScope`, démarrage unique et collecteurs annulables |

### 16.2 État par recommandation

| Domaine | État | Détail et limite actuelle |
|---|---|---|
| Dump `logcat` au démarrage | Implémenté | Différé de cinq secondes, exécuté hors du thread principal, flux écrit directement dans le fichier et processus borné par timeout |
| Reconnexion SSE | Implémenté en grande partie | Backoff exponentiel de 1 à 120 secondes, jitter de 25 %, remise à zéro après événement, arrêt sur 401/403 et annulation explicite de session ; l’observation dédiée de la connectivité reste à ajouter |
| Buffers et tâches offline | Implémenté en grande partie | Buffers critiques bornés et concurrence limitée à deux ; la synchronisation serveur N+1 reste inchangée faute d’API bulk/delta validée |
| WorkManager | Partiel | Contrainte réseau `CONNECTED` et déduplication `KEEP` ajoutées ; la classification fine des erreurs transitoires, le retry et les réglages Wi-Fi/charge restent à faire |
| Widgets | Implémenté pour les réveils principaux | TTL succès de 30 minutes, backoff échec de 5 minutes, mutex partagé, cache réutilisé et invalidation sur événements utiles |
| Horloge audio | Implémenté | Les contrôleurs n’ajoutent plus leur propre boucle à 2 Hz ; ils consomment la publication de position déjà produite par `AudiobookPlayer` |
| Offsets audio | Implémenté | Table d’offsets calculée une fois au chargement et réutilisée pour la position globale, les seeks et les pistes de transcription |
| Transcription en pause | Implémenté | Le prélecteur ne décode plus de nouveaux blocs PCM en pause ; le ticker ne rescane plus les segments dans cet état |
| Boxing PCM Whisper | Implémenté | `MutableList<Short>` et `ShortArray.toList()` supprimés au profit d’un `ShortArray` extensible et réutilisé |
| Cycle de vie transcription | Amélioré, à tester sur appareil | Démarrage doublon bloqué, collecteurs conservés/annulés, scopes rattachés au parent, contexte chargé par une session annulée immédiatement libéré ; l’attente explicite de tous les jobs à `stop()` reste à finaliser |
| Création répétée de `MediaCodec` | Non implémenté | Refonte native sensible : nécessite des tests de seek, changement de piste, pause/reprise et compatibilité codec sur plusieurs appareils |
| Fermeture NCNN | Implémenté, validation runtime requise | L’instance est explicitement fermée sur `Dispatchers.Default` lors de la fermeture du module ; les pilotes Vulkan doivent être testés sur appareil réel |
| File NCNN bornée/priorisée | Non implémenté | Le canal illimité et l’inférence `NonCancellable` nécessitent une refonte du scheduler et des tests de destruction de bitmaps/contexte GPU |
| Tiling pendant le pinch | Non implémenté | Demande des mesures de viewport, jank et heap bitmap sur appareil ; une modification aveugle risquerait une régression visuelle ou native |
| Pré-détection panneaux adaptative | Non implémenté | Nécessite une politique de préchargement et un cache versionné, puis des mesures ONNX réelles |
| Macrobenchmark et mesure batterie | Mesure initiale sur appareil effectuée | Démarrage, CPU premier plan/arrière-plan, mémoire, défilement et thermique mesurés sur une Galaxy Tab S7 FE ; la décharge batterie longue durée reste à mesurer hors alimentation USB |

### 16.3 Validation effectuée

- compilation `:komelia-infra:audiobook-transcription:compileDebugKotlinAndroid` réussie ;
- compilation `:komelia-app:compileDebugSources` réussie ;
- script officiel `scripts/build-kora-debug.sh` réussi sur la branche de travail ;
- APK contrôlé avec `aapt` : package `io.github.mkdevtests.kora.debug`, version `1.4.6`, `minSdk 26`, `targetSdk 36` ;
- APK produit dans `komelia-app/build/outputs/apk/debug/kora-app-debug.apk` ;
- graphe Graphify reconstruit après le dernier changement : 23 381 nœuds et 35 109 arêtes ;
- sous-modules restés sur leurs commits verrouillés ;
- APK debug installé avec `adb install -r` sur une Samsung Galaxy Tab S7 FE, sans désinstaller ni modifier le paquet release ;
- paquet contrôlé après installation : `io.github.mkdevtests.kora.debug`, version `1.4.6` ;
- campagne runtime détaillée dans la section suivante ; aucun effacement de données, aucun reset de `batterystats` et aucun test release.

Le build émet encore des avertissements D8 de réécriture de métadonnées Kotlin (`Should never be called`). Ils étaient déjà reproductibles avant les derniers lots, n’empêchent pas la génération de l’APK et doivent faire l’objet d’un chantier séparé d’alignement Kotlin/AGP/R8.

### 16.4 Mesures runtime sur appareil physique

Mesures réalisées le 9 août 2026 sur une Samsung Galaxy Tab S7 FE (`SM-T733`, Android 14/API 34, arm64-v8a, 1600 × 2560). La tablette était alimentée par USB, avec un état thermique Android nominal (`Thermal Status: 0`). Ces chiffres décrivent donc la charge CPU, mémoire et graphique, mais pas encore une autonomie en pourcentage par heure.

Le protocole est non destructif : paquet debug uniquement, données applicatives conservées, aucune installation release, aucun reset ART, aucune remise à zéro globale de `batterystats`. Les démarrages utilisent `am start -W -S`, ce qui arrête seulement le processus debug entre deux passages.

| Mesure | Résultat | Interprétation |
|---|---:|---|
| Démarrage, 10 passages | min 1 922 ms ; médiane 1 939 ms ; moyenne 1 951,5 ms ; p90 1 956 ms ; max 2 047 ms | Temps très stable, mais proche de deux secondes |
| Démarrage graphique isolé | 133 frames ; 8 janky modernes (6,02 %) ; p90 38 ms ; p95 89 ms ; p99 1 050 ms | Le chemin critique initial contient un blocage long |
| Repos au premier plan, 20 s | CPU moyen 5,05 % ; max 25 % ; 89 threads | Réveils périodiques visibles tant que Home est affiché |
| Repos en arrière-plan, 20 s | CPU moyen 0,35 % ; max 5 % | La charge résiduelle chute fortement une fois Home masqué |
| Mémoire au premier plan | PSS 340 528 KiB ; RSS 419 708 KiB | Empreinte élevée, en partie propre au build debug et aux bibliothèques natives |
| Mémoire en arrière-plan | PSS 283 008 KiB ; RSS 365 988 KiB | Environ 57,5 MiB de PSS rendus/récupérés après passage sur Home Android |
| Défilement Home, 12 balayages | 405 frames ; 38 janky (9,38 %) ; p50 24 ms ; p90 36 ms ; p95 57 ms ; p99 200 ms | Fluidité perfectible sur une page réelle riche en couvertures |
| Température après scénario | AP 36 °C ; batterie 30,7 °C ; peau 32,4 °C ; statut 0 | Aucun throttling thermique pendant la mesure courte |

Le démarrage isolé a produit `Skipped 60 frames` et un GC concurrent de 202 ms après environ 31 MiB libérés. Le GPU reste rapide sur la majorité des frames ; le coût initial se situe surtout sur le thread principal, les allocations et le chargement applicatif.

Le profil par thread au premier plan attribue l’essentiel des pointes à `OkHttp TaskRunner`, aux `DefaultDispatcher`, au nettoyeur de références Komelia et à la connexion SSE vers le serveur local. `RenderThread` reste presque inactif hors interaction. Le journal montre aussi un `TaskQueueStatus(count=0)` reçu environ toutes les dix secondes : même vide, cet événement est loggé en debug, diffusé et observable par l’UI. Il ne suffit pas à expliquer seul tout le CPU moyen, mais constitue un réveil réseau/coroutine mesurable et une piste de coalescence ou de filtrage (`distinctUntilChanged`) pour l’état identique.

Un script reproductible a été ajouté dans `scripts/measure-kora-debug.ps1`. Il :

- refuse de s’exécuter depuis `main` ;
- cible en dur `io.github.mkdevtests.kora.debug` ;
- vérifie ADB, l’appareil et l’installation du paquet debug ;
- mesure les démarrages, le CPU au premier plan et en arrière-plan, la mémoire, le défilement et la température ;
- écrit `summary.json`, `startup.csv` et les dumps bruts sous `komelia-app/build/perf/<horodatage>` ;
- ne construit, n’installe, n’efface ni ne réinitialise rien.

La mesure courte de décharge n’est pas pertinente tant que la tablette est alimentée en USB. Un protocole batterie valable devra être réalisé batterie débranchée, luminosité et Wi-Fi fixés, après stabilisation thermique, sur des fenêtres de 30 à 60 minutes répétées au moins trois fois par scénario.

### 16.5 Prochaine validation recommandée

Connecter un appareil Android de test avec débogage USB, installer uniquement l’APK au suffixe `.debug`, puis exécuter dans cet ordre :

1. vingt changements de serveur en observant connexions SSE, jobs, threads et heap ;
2. trente minutes de veille écran éteint pour mesurer les réveils réseau et widgets ;
3. lecture audio et transcription Whisper avec séquences pause/reprise/seek/changement de piste ;
4. fermeture du lecteur pendant le chargement du modèle et pendant une inférence ;
5. lecture image avec pinch continu, NCNN activé puis désactivé, en relevant jank, heap native, température et énergie ;
6. seulement après ces mesures, refonte du décodeur persistant, de la file NCNN et du tiling.

---

## 17. Conclusion

Kora ne semble pas souffrir d’un unique défaut général d’architecture. Les coûts viennent surtout de quelques mécanismes puissants dont la politique d’exécution est trop permissive : bibliothèques natives initialisées tôt, préchargement IA, queues sans limite, scopes indépendants et polling fréquent.

Les gains les plus probables sont :

1. **démarrage plus stable** en supprimant le snapshot `logcat` synchrone et en différant les runtimes natifs ;
2. **forte réduction du travail résiduel** grâce à la fermeture déterministe et au backoff SSE ;
3. **lecture d’images plus fluide et moins énergivore** grâce au tiling visible, à l’annulation NCNN et au cache panneaux ;
4. **réduction majeure du CPU de transcription** grâce au décodeur persistant et aux buffers primitifs ;
5. **réduction du réseau et des réveils** grâce au delta sync, aux contraintes WorkManager et à la coalescence des widgets/Home.

Il serait prématuré d’annoncer un pourcentage de batterie sans instrumentation. En revanche, les problèmes P0/P1 identifiés sont suffisamment directs pour justifier une mesure et une correction prioritaires. Le meilleur ordre est : garde-fous et cycles de vie, lecteur d’images, audio/transcription, puis optimisation réseau/offline et taille de livraison.
