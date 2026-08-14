### English

**Komga Toolkit automation (admin, code-locked)**

Kora can now drive a self-hosted Komga Toolkit's automation API from an
admin-only screen (Settings → Admin → Komga Toolkit), unlocked by a local access
code you set on first use and re-entered on every visit.

- Configure the Toolkit URL and bearer token. The URL is pre-filled from the
  connected Komga server + Toolkit's default port; both are stored encrypted at
  rest, and nothing is baked into the app.
- Four functions: upcoming-releases and release-tracking, each via Manga News or
  MangaBaka. Each runs the same flow — preview → review the proposed changes →
  explicit confirmation → apply → summary.
- The analysis can take minutes; it runs in the background, so you can leave the
  screen and come back without losing it. Cancellable.
- Buttons to test connectivity and to open the Toolkit web UI.

Everything is optional and personal: with no URL/token set, Kora makes no
connection to any Toolkit server.

---

### Français

**Automatisation Komga Toolkit (admin, verrouillée par code)**

Kora peut désormais piloter l'API d'automatisation d'un Komga Toolkit auto-hébergé
depuis un écran réservé aux administrateurs (Réglages → Admin → Komga Toolkit),
déverrouillé par un code d'accès local que tu définis à la première utilisation
et ressaisis à chaque visite.

- Configure l'URL Toolkit et le jeton. L'URL est pré-remplie à partir du serveur
  Komga connecté + le port par défaut de Toolkit ; les deux sont stockés chiffrés
  au repos, et rien n'est embarqué en dur dans l'application.
- Quatre fonctions : prochaines sorties et suivi des sorties, chacune via Manga
  News ou MangaBaka. Toutes suivent le même parcours — aperçu → vérification des
  changements proposés → confirmation explicite → application → bilan.
- L'analyse peut durer plusieurs minutes ; elle s'exécute en arrière-plan, tu
  peux donc quitter l'écran et y revenir sans la perdre. Annulable.
- Boutons pour tester la connexion et ouvrir le WebUI de Toolkit.

Tout est optionnel et personnel : sans URL ni jeton, Kora n'établit aucune
connexion vers un serveur Toolkit.
