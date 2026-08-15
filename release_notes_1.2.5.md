### English

**Fix: update check failed with "Can't parse version number"**

The update check read the tag of every release in the repository as a version
number. A release that isn't an app build — an asset pack, for instance — has no
version-shaped tag, and the whole check failed on it, hiding every real release.

Such releases are now skipped instead of breaking the check.

---

### Français

**Correction : le check de mise à jour échouait avec « Can't parse version number »**

Le check de mise à jour lisait le tag de chaque release du dépôt comme un numéro
de version. Une release qui n'est pas une version de l'application — un pack de
ressources, par exemple — n'a pas de tag au format version, et tout le check
échouait dessus, masquant toutes les vraies releases.

Ces releases sont désormais ignorées au lieu de casser le check.
