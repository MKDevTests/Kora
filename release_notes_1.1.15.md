### English

**Fix: the upcoming-releases calendar stayed empty**

The scan was started from the home card's composition and from the calendar
screen's model scope. Both are tied to the screen's lifecycle, so navigating
away cancelled the scan — and since a scan costs one Komga query per
`nextrelease:` tag, it rarely survived long enough to finish. The logs were full
of cancellations and the calendar never filled.

- The scan now runs in a process-scoped coroutine (`NextReleasesScanner`), so
  leaving the screen no longer cancels it. Only one scan runs at a time, and
  both the home card and the calendar screen observe its result.
- A library whose tag query times out no longer costs the whole calendar: the
  libraries that answer still contribute, and the partial result is marked
  incomplete so it can never overwrite a better cached one.
- Two scan attempts with backoff, to ride out a dropped connection without
  hammering a slow server.
- A successful scan is now logged, so this path can be diagnosed at all.

**Fix: exporting logs crashed the app**

The export concatenated every log file into a single string and then ran three
whole-string regexes over it — roughly four copies of the entire log set in
memory, which ran out of memory and killed the app. Redaction is now done line
by line while streaming, the export is capped, and the most recent log file is
exported first.

---

### Français

**Correction : le calendrier des prochaines sorties restait vide**

Le scan était lancé depuis la composition de la carte d'accueil et depuis le
scope de l'écran du calendrier. Les deux sont liés au cycle de vie de l'écran :
quitter l'écran annulait le scan — et comme un scan coûte une requête Komga par
tag `nextrelease:`, il allait rarement au bout. Les journaux étaient remplis
d'annulations et le calendrier ne se remplissait jamais.

- Le scan tourne désormais dans une coroutine à l'échelle du processus
  (`NextReleasesScanner`) : quitter l'écran ne l'annule plus. Un seul scan à la
  fois, et la carte d'accueil comme l'écran du calendrier observent son résultat.
- Une bibliothèque dont la requête de tags expire ne coûte plus tout le
  calendrier : celles qui répondent contribuent quand même, et le résultat
  partiel est marqué incomplet pour ne jamais écraser un cache meilleur.
- Deux tentatives avec temporisation, pour encaisser une coupure réseau sans
  matraquer un serveur lent.
- Un scan réussi est désormais journalisé, ce qui rend enfin ce chemin
  diagnosticable.

**Correction : l'export des journaux faisait planter l'application**

L'export concaténait tous les fichiers de log en une seule chaîne puis y
appliquait trois expressions régulières sur l'intégralité du texte — soit
environ quatre copies de l'ensemble des journaux en mémoire, ce qui provoquait
un dépassement mémoire et tuait l'application. L'expurgation se fait maintenant
ligne par ligne en flux, l'export est plafonné, et le fichier de log le plus
récent est exporté en premier.
