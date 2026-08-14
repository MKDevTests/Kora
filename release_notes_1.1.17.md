### English

**Reader: end-of-volume progress fixes**

- Finishing a volume no longer loses its progress. A race in the progress-sync
  code could write "page 1" onto the volume you just finished, which also
  un-completed it so the reader would re-serve the same volume. Progress is now
  captured as one consistent snapshot, and the book transition never leaves a
  window where the outgoing volume gets the incoming page.
- The "next volume" button (and reading through the end) now marks the volume
  you leave as read. Going back to the previous volume marks the one you leave
  as unread — the mirror action.
- Opening a specific volume from a series now opens THAT volume. It previously
  restored the last-read volume's id and opened it instead, because every reader
  instance shared one saved-state slot.

**Reader: faster volume opening**

Opening a volume issued 6-8 server requests one after another (≈2s each on a
slow server, so 13-21s measured). They now run in parallel, bounded by the
slowest call rather than their sum — measured ≈5s worst case, near-instant when
warm. Continuous webtoon page streaming is unchanged; its speed is bound by how
fast the server sends each large page.

**Tooling**

New `scripts/export-kora-logs.ps1` collects the on-device logs (rolling log +
logcat + crash report) into one timestamped folder for bug reports.

---

### Français

**Lecteur : corrections de progression en fin de tome**

- Terminer un tome ne perd plus sa progression. Une concurrence dans la synchro
  pouvait écrire « page 1 » sur le tome qu'on venait de finir, ce qui le
  dé-complétait et faisait relancer le même tome. La progression est désormais
  capturée en un instantané cohérent, et le passage d'un tome à l'autre ne laisse
  plus de fenêtre où le tome sortant reçoit la page entrante.
- Le bouton « tome suivant » (et la lecture jusqu'au bout) marque le tome que
  l'on quitte comme lu. Revenir au tome précédent marque celui que l'on quitte
  comme non lu — l'action miroir.
- Ouvrir un tome précis depuis une série ouvre bien CE tome. Auparavant l'id du
  dernier tome lu était restauré et ouvert à la place, car toutes les instances
  du lecteur partageaient un même emplacement de sauvegarde.

**Lecteur : ouverture plus rapide**

Ouvrir un tome enchaînait 6-8 requêtes serveur à la suite (~2 s chacune sur un
serveur lent, soit 13-21 s mesurées). Elles partent maintenant en parallèle,
bornées par la plus lente et non par leur somme — ~5 s au pire, quasi instantané
à chaud. Le défilement continu des webtoons est inchangé : sa vitesse dépend de
la rapidité avec laquelle le serveur envoie chaque grande page.

**Outillage**

Nouveau `scripts/export-kora-logs.ps1` : rassemble les logs de l'appareil
(journal glissant + logcat + rapport de crash) dans un dossier horodaté pour les
rapports de bug.
