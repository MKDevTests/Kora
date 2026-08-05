### English

**Kora speaks French**

Settings → Appearance → **Interface language**: *System language* (default), *English* or *Français*. It applies immediately, everywhere, and it does not follow the phone — a French reader on an English device could never ask for French before.

Translated: the navigation, home, libraries, series, books, the reader (image, EPUB 3, audio, OCR), every dialog, every settings screen including Komf and the upscaler, statistics, upcoming releases, offline mode and the login screen. Around 900 places, 815 catalogue entries.

Two details worth knowing:

- **Home shelf names are yours.** They are stored per user and can be renamed, so a shelf is only translated while its name is still the one shipped with the app. Rename it and your name stays, in whatever language you typed it.
- Proper nouns are left alone: Komga, Kavita, Komf, Discord, Whisper, ONNX Runtime. Translating them would only make them harder to match with the tools they name.

The English build no longer shows French either: the labels Kora had written in French from the start (*Favoris*, *À lire*, *Prochaines sorties*, the Toolkit screen) now have an English side.

**Also in this version**

- Toolchain: Kotlin 2.4.10, Compose Multiplatform 1.11.1, Gradle 9.3.1. The atomicfu plugin is gone — applied by two modules, used by none.

- The library **tag filter** listed series tags only, so every tag carried by books was missing from it.
- The offline **RAR** reader leaked a file descriptor on every page read.
- **Predictive back** (Android 13+) is enabled, so the reader's back gesture is animated.

---

### Français

**Kora parle français**

Réglages → Apparence → **Langue de l'interface** : *Langue du système* (par défaut), *English* ou *Français*. Le changement est immédiat, partout, et ne dépend pas du téléphone — un lecteur francophone sur un appareil en anglais n'avait jusqu'ici aucun moyen de demander du français.

Traduits : la navigation, l'accueil, les bibliothèques, les séries, les tomes, le lecteur (images, EPUB 3, audio, OCR), tous les dialogues, tous les écrans de réglages y compris Komf et l'agrandissement, les statistiques, les prochaines sorties, le mode hors ligne et l'écran de connexion. Environ 900 emplacements, 815 entrées de catalogue.

Deux points à connaître :

- **Les noms de vos étagères d'accueil vous appartiennent.** Ils sont enregistrés par utilisateur et renommables : une étagère n'est traduite que tant que son nom est celui livré avec l'application. Renommez-la, et c'est votre nom qui reste, dans la langue que vous avez tapée.
- Les noms propres ne sont pas traduits : Komga, Kavita, Komf, Discord, Whisper, ONNX Runtime. Les traduire ne ferait que les rendre plus difficiles à rapprocher des outils qu'ils désignent.

La version anglaise n'affiche plus de français non plus : les libellés que Kora écrivait en français depuis le début (*Favoris*, *À lire*, *Prochaines sorties*, l'écran Toolkit) ont désormais leur pendant anglais.

**Également dans cette version**

- Chaîne de compilation : Kotlin 2.4.10, Compose Multiplatform 1.11.1, Gradle 9.3.1. Le plugin atomicfu disparaît — appliqué par deux modules, utilisé par aucun.

- Le **filtre Tags** d'une bibliothèque ne listait que les tags de séries : tous les tags portés par les tomes en étaient absents.
- Le lecteur **RAR** hors ligne fuyait un descripteur de fichier à chaque page lue.
- Le **retour prédictif** (Android 13+) est activé : le geste de retour du lecteur est animé.
