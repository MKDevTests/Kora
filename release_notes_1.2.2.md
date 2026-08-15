### English

**Smaller app (R8 code shrinking)**

The Android release build now runs R8 code shrinking. Unused code is stripped
from the app, roughly halving the compiled code size (dex 105 MB → 52 MB).

- No behavior change: same features, same UI. Only the install is lighter.
- Native libraries (image decoding, ONNX bubble/panel detection, upscaler) are
  untouched, so the total download shrinks modestly — the native code still
  dominates the package size.
- R8's aggressive optimization pass is intentionally left off for now: it
  exposed a rare image-teardown crash. Code shrinking (the main win) stays on.

---

### Français

**Application plus légère (élagage de code R8)**

Le build de release Android exécute désormais l'élagage de code R8. Le code
inutilisé est retiré de l'app, ce qui divise à peu près par deux la taille du
code compilé (dex 105 Mo → 52 Mo).

- Aucun changement de comportement : mêmes fonctions, même interface. Seule
  l'installation est plus légère.
- Les bibliothèques natives (décodage d'images, détection de bulles/panels ONNX,
  upscaler) ne sont pas touchées : le téléchargement total baisse modérément, le
  code natif dominant toujours le poids du paquet.
- La passe d'optimisation agressive de R8 est volontairement laissée désactivée
  pour l'instant : elle exposait un crash rare au démontage d'image. L'élagage
  de code (le gain principal) reste actif.
