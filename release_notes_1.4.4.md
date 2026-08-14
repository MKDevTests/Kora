### English

**Toolchain**

Kotlin 2.4.10, Compose Multiplatform 1.11.1, Gradle 9.3.1. Nothing changes in what the app does — this is the compiler and UI framework moving forward, and everything was re-tested on device: reading, home, libraries, For you, genres, read lists, zoom, inverted bubbles and webtoon smart scroll.

The atomicfu plugin is gone: two modules applied it, none used it, and it is pinned to a Kotlin version — it would have broken on a future upgrade for nothing.

Android Gradle Plugin 9 was evaluated and deliberately left aside. It would bring no feature, only alignment with upstream Komelia, and it demands restructuring four things at once: the protobuf source directory this app still reads, two native modules carrying CMake and an NDK version, and the application module itself — which holds the build flavors, the signing setup, the R8 configuration and the APK paths the release scripts rely on. That is its own piece of work, not a version bump.

---

### Français

**Chaîne de compilation**

Kotlin 2.4.10, Compose Multiplatform 1.11.1, Gradle 9.3.1. Rien ne change dans ce que fait l'application — c'est le compilateur et le framework d'interface qui avancent, et tout a été retesté sur l'appareil : lecture, accueil, bibliothèques, For you, genres, listes de lecture, zoom, bulles inversées et défilement intelligent webtoon.

Le plugin atomicfu disparaît : deux modules l'appliquaient, aucun ne s'en servait, et il est épinglé à une version de Kotlin — il aurait cassé à une prochaine montée de version pour rien.

L'Android Gradle Plugin 9 a été évalué puis volontairement écarté. Il n'apporte aucune fonctionnalité, seulement l'alignement avec Komelia en amont, et il exige de restructurer quatre choses d'un coup : le dossier de sources protobuf que l'application lit encore, deux modules natifs qui portent CMake et une version du NDK, et le module applicatif lui-même — celui qui contient les variantes de build, la signature, la configuration R8 et les chemins d'APK dont dépendent les scripts de release. C'est un chantier en soi, pas une montée de version.
