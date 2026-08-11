### English

**The reader stops redoing work it has already done**

While a webtoon scrolls, Kora recalculates what each visible page should show ten times a second. A page that had stopped moving was going through that whole calculation — the position of every tile of the image — to arrive at exactly what was already on screen. It now recognises a request identical to the one it has just applied and drops it. Changing stretch, the downsampling kernel, colour correction or upscaling still redraws immediately: those go down a different path, on purpose.

**Bubble detection waits for you to stop**

In webtoon mode with smart scrolling, every page entering the screen had a bubble analysis launched at it — about three quarters of a second of computation each. Scrolling quickly through twenty pages therefore started twenty analyses for the one or two pages you were actually going to read. Kora now waits for the scroll to settle before looking, and looks at the pages that are there then, not the ones that were passing through.

**Read-ahead waits too**

The paged reader keeps three spreads of lead so that turning a page is instant, and that stays. But those preloads were also fired while you dragged the progress bar, and nothing cancelled them: crossing a book with the slider queued dozens of decodes nobody would ever see — all of them competing with the page you were actually waiting for. The lead is now built once you have stopped. The current page still loads immediately, so turning pages feels exactly as it did.

**The automatic backup asks for decent conditions**

It was the only recurring task Kora schedules and it had no conditions at all, so Android was free to wake the device to write a backup at 3% battery or with no room left to write it. It now waits for a charged-enough battery and free space. The "Back up now" button is unchanged: you asked for it now.

**A bigger cover cache**

Two settings were fighting over the same value and the smaller one was winning, capping the cover cache at 64 MB where the device allows about 100. Every cover pushed out is a cover decoded again the next time you scroll past it. The cap is gone, and the size now follows the device instead of a number written years ago.

---

### Français

**Le lecteur arrête de refaire ce qu'il a déjà fait**

Pendant qu'un webtoon défile, Kora recalcule dix fois par seconde ce que chaque page visible doit afficher. Une page qui ne bougeait plus traversait tout de même ce calcul — la position de chaque tuile de l'image — pour arriver exactement à ce qui était déjà à l'écran. Il reconnaît maintenant une demande identique à celle qu'il vient d'appliquer et l'abandonne. Changer l'étirement, le noyau de sous-échantillonnage, la correction couleur ou l'agrandissement redessine toujours immédiatement : ceux-là passent par un autre chemin, volontairement.

**La détection des bulles attend que vous vous arrêtiez**

En mode webtoon avec le défilement intelligent, chaque page entrant à l'écran se voyait lancer une analyse de bulles — environ trois quarts de seconde de calcul chacune. Défiler vite sur vingt pages démarrait donc vingt analyses pour la ou les deux pages que vous alliez réellement lire. Kora attend désormais que le défilement se calme avant de regarder, et regarde les pages qui sont là à ce moment-là, pas celles qui passaient.

**Le préchargement attend aussi**

Le lecteur paginé garde trois planches d'avance pour que tourner une page soit instantané, et cela ne change pas. Mais ces préchargements partaient aussi pendant que vous faisiez glisser la barre de progression, et rien ne les annulait : traverser un tome au curseur mettait en file des dizaines de décodages que personne ne verrait jamais — tous en concurrence avec la page que vous attendiez vraiment. L'avance se construit maintenant une fois que vous vous êtes arrêté. La page courante, elle, charge toujours immédiatement : tourner les pages est exactement comme avant.

**La sauvegarde automatique demande des conditions correctes**

C'était la seule tâche récurrente que Kora planifie et elle n'avait aucune condition, si bien qu'Android pouvait réveiller l'appareil pour écrire une sauvegarde à 3 % de batterie ou sans place pour l'écrire. Elle attend désormais une batterie suffisante et de l'espace libre. Le bouton « Sauvegarder maintenant » ne change pas : vous l'avez demandée maintenant.

**Un cache de couvertures plus grand**

Deux réglages se disputaient la même valeur et c'était le plus petit qui gagnait, plafonnant le cache des couvertures à 64 Mo là où l'appareil en autorise une centaine. Chaque couverture évincée est une couverture redécodée au prochain passage devant elle. Le plafond a disparu, et la taille suit maintenant l'appareil plutôt qu'un chiffre écrit il y a des années.
