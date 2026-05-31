# Kora — un client Komga pour Android

[English](README.md) · **Français**

Kora est un client Android pour les serveurs [Komga](https://komga.org). C'est un fork de
[Sipurra](https://github.com/eserero/Sipurra) — lui-même un fork de [Komelia](https://github.com/Snd-R/Komelia) —
et il hérite de toutes les fonctionnalités des deux projets amont. Kora se concentre sur l'expérience de
lecture sur téléphone et tablette : suivi de lecture, sécurité des données et finitions du quotidien.

> Kora vise Android. Les cibles desktop et web (Wasm) sont héritées de l'amont et conservées pour
> faciliter les merges, mais elles ne sont ni maintenues ni distribuées ici.

---

## Nouveautés de Kora

Voici ce que Kora ajoute par-dessus Sipurra, regroupé par thème. Pour le journal complet version par
version, voir les [notes de version](https://github.com/MKDevTests/Kora/releases).

### Statistiques de lecture
- Une carte de statistiques sur l'accueil et un écran dédié, combinant l'API Komga et un journal local
  `reading_events`.
- Des tuiles « livres terminés » et « pages lues », plus un graphique d'historique avec sélecteur de
  période (7 jours / 30 jours / 12 mois) et un compteur de complétions sous chaque barre.

### Notes
- Des notes par série (étoiles), stockées localement.
- Une invite optionnelle « tu viens de finir — noter cette série ? » et un menu par appui long sur les
  couvertures pour les actions rapides.

### Accueil & découverte
- Des étagères d'accueil supplémentaires : « Continuer la lecture », « À suivre », « Presque terminé » et
  « Oubliés ».
- Une étagère aléatoire mise en cache qui survit à la navigation (le tirer-pour-rafraîchir force un
  nouveau tirage) et un raccourci de reprise de lecture.

### Sauvegarde & restauration
- Export et import JSON en un geste des réglages, filtres d'accueil, filtres de bibliothèque, surcharges
  de lecture par série, notes et historique de lecture.
- Des sauvegardes automatiques vers un dossier de ton choix, planifiées, avec purge automatique des
  anciennes copies.
- Un format de sauvegarde versionné avec un **aperçu pré-vol** validé qui montre exactement ce qu'une
  restauration va changer avant de l'appliquer ; les entrées invalides sont ignorées et signalées.
  Couvert par un test d'aller-retour.

### Widget d'écran d'accueil
- Un widget d'écran d'accueil « Prochain livre » (Glance) qui se rafraîchit quand l'app passe en
  arrière-plan, avec des notifications en cas d'échec d'installation (au lieu de les ignorer
  silencieusement).

### Recherche
- Un onglet « Auteurs » pour parcourir par auteur et accéder à ses séries et livres.

### Lecteur
- Le titre du livre affiché dans la barre supérieure du lecteur, à côté du compteur de pages.
- Une interface minimale optionnelle pendant la lecture (un bandeau de progression fin qui se déploie en
  contrôles complets à la demande).
- De nombreux correctifs du lecteur (reprise du lecteur paginé sur appuis rapides, crash de page blanche
  avec le rognage des bords, auto-détection webtoon, arrêt net en fin de livre en mode continu, etc.).

### Diagnostics
- Un écran Diagnostics (Réglages → Réglages de l'app) : version de l'app, mode en ligne/hors-ligne et
  serveur actif, tailles de cache, stockage des téléchargements hors-ligne, état des tâches de fond, et un
  visualiseur de logs in-app avec export redacté et plafond de taille de logs configurable. Inclut une
  action sûre « Vider le cache images ».

### Navigation
- Un sélecteur de bibliothèque dans le titre de page pour basculer en un geste.
- Un écran de démarrage configurable (Accueil ou dernière bibliothèque ouverte).

### Durcissement pour la diffusion publique
- Les builds de release sont non-debuggables et signés de façon cohérente pour des mises à jour in-app
  transparentes.
- Une politique de confidentialité réécrite et exacte ([PRIVACY_POLICY.MD](PRIVACY_POLICY.MD)) et une
  documentation de build ([BUILDING.md](BUILDING.md)).
- Un test qui protège l'enregistrement des migrations de base de données contre un piège récurrent.

---

## Hérité de Sipurra et Komelia

Sipurra est un fork de [Komelia](https://github.com/Snd-R/Komelia) axé sur l'expérience Android. Il
conserve tout ce qu'offre Komelia et y ajoute beaucoup :

- Une UX entièrement repensée.
- **Support multi-serveurs** : ajout et bascule entre plusieurs comptes Komga.
- Un nouveau lecteur EPUB 3 prenant en charge la lecture immersive audio + texte (basé sur Storyteller).
- Lecture d'audiolivres avec **transcription en direct**.
- Un lecteur de bandes dessinées plus riche (**sélection de texte par OCR**, upscaling par IA, options de
  navigation supplémentaires, mode panneau amélioré, et plus).
- Marque-pages et annotations pour les livres et audiolivres, surlignage pour les livres et BD,
  synchronisés entre appareils.
- **Support des fichiers locaux** : prise en charge native des PDF, CBR et CBZ avec persistance des pages
  et des marque-pages.

### Nouvelle UX et thèmes
- **Material 3** : `TopAppBar` standardisée, menu épuré, menu hamburger de bibliothèque, FAB de filtre
  flottant et barre de navigation standardisée.
- **Gestion multi-serveurs** : ajouter, basculer et gérer plusieurs connexions Komga, avec état de
  session, connexion et synchro hors-ligne par serveur.
- **Vignettes plus petites** : une option à 3 vignettes par ligne (taille 110) au lieu de deux, avec des
  options d'affichage (texte sous la vignette, fond de texte transparent).
- **Nouveaux thèmes clair et sombre** : couleurs plus nettes et effet « haze » optionnel (flou +
  transparence) sur les barres d'outils et les éléments flottants.

### Écran Bibliothèque
- Un design plus épuré avec un accès au tri facilité.
- Un bandeau horizontal « Continuer la lecture » des livres récemment lus par bibliothèque.
- Une interface de filtrage améliorée.
- Une navigation qui mémorise la dernière bibliothèque consultée.

| Bibliothèque (clair) | Bibliothèque (sombre) | Bibliothèque avec « Continuer » |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Library Screen Light Modern Theme.jpg" width="250"> | <img src="screenshots/New UI 2/Library Screen Dark Modern theme.jpg" width="250"> | <img src="screenshots/New UI 2/Library Screen Light modern theme with Continue reading.jpg" width="250"> |

### Écran d'accueil
- Un design cohérent avec l'écran Bibliothèque, avec la gestion des sections sur un FAB en bas à droite
  pour un usage à une main.
- Une disposition horizontale (Continuer la lecture, À suivre, etc.) pour un tableau de bord compact et
  facile à explorer.
- Un basculement hors-ligne rapide dans la barre supérieure.

### Recherche
- Une `SearchBar` Material 3 complète avec animations fluides, retour natif et effacement du texte.

### Écrans de détail immersifs (livre, série, oneshot)
- Des couvertures pleine page derrière la carte d'informations, avec navigation par balayage entre les
  livres et boutons flottants de lecture/téléchargement.
- Icônes d'éditeur (quand une correspondance existe).
- Couleurs de carte adaptatives échantillonnées depuis la couverture (configurable).
- Cartes surélevées Material 3 avec transitions d'éléments partagés depuis la liste de bibliothèque.

| Série immersive (repliée) | Série immersive (déployée) | Série immersive (variante) |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Immersive Series Screen Collapsed.jpg" width="250"> | <img src="screenshots/New UI 2/Immersive Series Screen Expanded.jpg" width="250"> | <img src="screenshots/New UI 2/Immersive Series Screen Collapsed 2.jpg" width="250"> |

| Série immersive (variante déployée) | Vue livre immersive | Livre immersif (déployé) |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Immersive Series Screen Expanded 2.jpg" width="250"> | <img src="screenshots/New UI 2/Immersive Book view.jpg" width="250"> | <img src="screenshots/New UI 2/Immersive Book with expanded button.jpg" width="250"> |

### Lecteur d'images / BD
- De nouveaux contrôles alignés sur le lecteur EPUB et les couleurs de carte adaptatives, avec accès
  rapide aux modes de lecture (page, continu, panneau), à l'upscaling et au verrouillage de rotation.
- **Sélection de texte par OCR** via Google ML Kit et RapidOCR, avec scan à la demande et un mode
  auto-scan ; les zones détectées sont fusionnées selon le sens de lecture (LTR/RTL).
- **Actions de texte** : traduire, copier ou annoter le texte sélectionné par OCR (notes épinglées à la
  zone concernée).
- Un carrousel de vignettes horizontal avec synchronisation par auto-défilement.
- Des fonds immersifs adaptatifs qui échantillonnent les couleurs des bords en temps réel (modes page et
  panneau).
- Un upscaling GPU performant (NCNN) optimisé pour le matériel Android.
- Une navigation par balayage fluide en mode page et un pan-and-zoom amélioré en mode panneau.
- Sauvegarde de l'image courante dans Téléchargements (appui long), zoom par double-tap configurable,
  plusieurs configurations de zones de tap, historique de navigation avec bouton retour flottant, et une
  option pour garder l'écran allumé.

**Fonds adaptatifs**
| Thème clair | Thème sombre | Contrôles masqués |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Comic Reader - Adaptive Background with Light Modern Theme.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader - Adaptive Background with Dark Modern Theme.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader - Adaptive Background without controls.jpg" width="250"> |

**Comparaison de l'upscaling GPU**
| Sans upscaling | Avec upscaling |
| :---: | :---: |
| <img src="screenshots/New UI 2/Comic Reader - Adaptive backgrouund without upscaling.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader - adaptive background with upscaling.jpg" width="250"> |

| Sans upscaling (contrôles) | Avec upscaling (contrôles) |
| :---: | :---: |
| <img src="screenshots/New UI 2/Comic Reader - Comic page with controls without upscaling.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader - Comic page with controls with upscaling.jpg" width="250"> |

| Sans upscaling (N&B) | Avec upscaling (N&B) |
| :---: | :---: |
| <img src="screenshots/New UI 2/Comic Reader - Comic page with controls without upscaling B&W.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader - Comic page with controls with upscaling B&W.jpg" width="250"> |

### Lecteur EPUB avec couche audio EPUB 3 et audiolivres
- Un nouveau lecteur EPUB basé sur Storyteller et le Readium Kotlin Toolkit, prenant en charge les EPUB
  avec couche audio pour une lecture combinée texte + audio.
- Appui long sur le texte pour un menu contextuel natif (traduire, copier, rechercher).
- Un lecteur d'audiolivres intégré avec navigation par chapitres, mini-lecteur et interface plein écran :
  - **Transcription en direct** pour les audiolivres en dossier via Whisper (local/natif) et ML Kit, avec
    une interface de transcription défilante.
  - Extraction et mise en cache automatiques des métadonnées de chapitres embarquées.
  - Une boîte de dialogue de métadonnées pour les tags de piste et les chapitres embarqués.
- Un nouvel écran de réglages (thèmes, marges, polices, audio), une navigation par balayage/défilement,
  des marque-pages et une recherche de texte, plus un réglage maître pour garder l'écran allumé.

**Contrôles et lecteur audio**
| Thème clair | Thème sombre | Mini-lecteur |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Epub3 reader with control panel and mini audio player Light Theme.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader with control panel and mini audio player Dark Theme.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader with mini audio controls.jpg" width="250"> |

**Lecteur audio déployé**
| Thème clair | Thème sombre |
| :---: | :---: |
| <img src="screenshots/New UI 2/Epu3 reader expanded audio player light theme.jpg" width="250"> | <img src="screenshots/New UI 2/Epu3 reader expanded audio player Dark theme.jpg" width="250"> |

**Fonctions du lecteur**
| Table des matières | Marque-pages | Recherche |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Epub3 reader Table of Content.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader Bookmarks.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader Search.jpg" width="250"> |

### Réglages
- Une structure de menu Material 3 refondue et plus moderne.
- Gestion de cache avancée : limites configurables pour les lecteurs d'images et EPUB, purge LRU et vidage
  manuel.
- De nouveaux réglages visuels : curseurs d'intensité des couleurs immersives, préréglages d'accent à
  l'échelle de l'app, et un interrupteur maître pour la nouvelle UI de bibliothèque.
- Personnalisation poussée : tap-to-zoom par mode, zones de tap-navigation configurables, et réglages fins
  des fonds adaptatifs.

**Réglages du lecteur de BD**
| Modes de lecture | Réglages d'image | Navigation |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Comic Reader Settings - Reading Modes.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader Settings - Image Settings.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader Settings -  Navigation.jpg" width="250"> |

**Réglages du lecteur EPUB**
| Apparence | Polices | Audio |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Epub3 reader settings - Appearance.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader settings - Fonts.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader settings - audio.jpg" width="250"> |

### Général
- **Préférer les fichiers locaux** : les fichiers téléchargés sont utilisés à la place du serveur quand ils
  sont disponibles.
- Support hors-ligne amélioré : PDF et CBR locaux natifs, en plus d'EPUB et CBZ.
- Ouverture de fichiers locaux CBZ, CBR, PDF et EPUB via le menu partager/ouvrir d'Android, avec
  persistance des pages et des marque-pages.
- Re-scan des fichiers locaux existants pour les relier après un transfert d'appareil ou une installation
  neuve.

---

## Captures d'écran

<details>
  <summary>Mobile</summary>
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="Kora" width="270">
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" alt="Kora" width="270">
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" alt="Kora" width="270">
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" alt="Kora" width="270">
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" alt="Kora" width="270">
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" alt="Kora" width="270">
</details>

<details>
  <summary>Tablette</summary>
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/1.jpg" alt="Kora" width="400" height="640">
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/2.jpg" alt="Kora" width="400" height="640">
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/3.jpg" alt="Kora" width="400" height="640">
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/4.jpg" alt="Kora" width="400" height="640">
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/5.jpg" alt="Kora" width="400" height="640">
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/6.jpg" alt="Kora" width="400" height="640">
</details>

---

## Téléchargements

- **Kora (ce fork)** : https://github.com/MKDevTests/Kora/releases — un APK Android signé, récupéré
  automatiquement par le système de mise à jour in-app.
- Sipurra (amont, Android) : https://github.com/eserero/Sipurra/releases
- Komelia (original, toutes plateformes) : https://github.com/Snd-R/Komelia/releases — aussi sur Google
  Play, F-Droid et l'AUR (identifiants de paquet différents, installés séparément de Kora).

---

## Compilation

Kora est une app Android, compilée et signée via les scripts de `scripts/`. Voir
**[BUILDING.md](BUILDING.md)** pour les instructions complètes, dont la configuration unique des
bibliothèques natives (libvips et SQLite JNI) et le workflow de release.

## Confidentialité

Kora est local-first et ne contient aucun analytics ni télémétrie. Il ne communique qu'avec le serveur
Komga que tu configures, avec GitHub pour la vérification des mises à jour, et — en option — avec les
endpoints de modèles/Komf que tu actives. Voir **[PRIVACY_POLICY.MD](PRIVACY_POLICY.MD)** pour les
détails.

## Remerciements

Kora s'appuie sur le travail d'autres projets :

- **[Komelia](https://github.com/Snd-R/Komelia)** — le client Komga Kotlin Multiplatform original.
- **[Sipurra](https://github.com/eserero/Sipurra)** — le fork axé Android sur lequel Kora est basé.
- **[Storyteller](https://gitlab.com/storyteller-platform/storyteller)** — le moteur EPUB 3 audio + texte
  sur lequel s'appuie le lecteur immersif.
- **[waifu2x-ncnn-vulkan](https://github.com/nihui/waifu2x-ncnn-vulkan)** — l'implémentation de l'upscaling
  NCNN sur Android.
- **[RealSR-NCNN-Android](https://github.com/tumuyan/RealSR-NCNN-Android)** — des modèles NCNN
  supplémentaires (RealSR, RealCUGAN, etc.).
