# Kora 1.8.0

Japanese is now translated through a chain that repairs what the
recogniser gets wrong before the translator ever sees it, the reader can
retry a page that failed, and the application no longer closes because a
server was slow.

## Japanese: the recogniser is corrected before the translator runs

Reading fifty pages of a Japanese volume and classifying all 231 distinct
balloons by hand gave the split this release was built on: 34 balloons
where the recogniser had already corrupted the Japanese, 71 where the
Japanese was clean and the French was wrong. Recognition confidence
predicts neither — a balloon read at 60% translates correctly and one
read at 99% comes back reversed.

So the work is in stages, each measured on its own.

**Furigana is dropped.** Furigana is the small kana printed beside a
kanji to give its reading. Nobody wants it translated: it spells out a
word already on the page, arrives at the translator as pure phonetics,
and comes back as a panel of gibberish painted next to the line it
belongs to. It only became a problem as the scans got taller — a
controlled run of the same twenty-five pages at 1351x1920 and at 1200px
finds 12% more characters at full height, and 83% of that gain is
furigana.

**Dialect is rewritten into standard Japanese.** The engine does not
merely lose the register, it inverts the line: 見つからへんど, "you
won't find one", came back as "peut être trouvé". Over the 231 balloons,
19 were touched, 9 were clearly better, none was worse, and four of the
nine reversals were put back the right way round.

**The dialect table no longer fires inside ordinary words.** On seven
volumes containing no dialect at all it fired eleven times: five right,
six wrong, three of them producing だろうう, which is not a word in any
language. Every short all-hiragana entry now carries a guard.

**Missing voicing marks are restored on katakana.** パーティー, the
commonest noun in the genre, was read wrong more often than right — 14
times against 11 over seven volumes — and the damage surfaced two stages
later looking nothing like a recognition fault: アレンのハーティー came
back as "Allen Est Copieux". A short word list arbitrates, so a word
that never had marks does not get them.

**Characters reached for out of the wrong script are corrected.**
Simplified Chinese forms Japanese has no use for, an ASCII hyphen
standing in for the long-vowel mark (ペ-ジ was answered "127Peg" on six
pages), 世 read for せ, 句 and 自 read for 匂 between two い, and a
chapter heading with a bracket wedged between the word and its number.
The stat screens the genre is full of are where these land: 年龄:21
性别:女 came back "Année 龄:21 性别:女 race: personnes".

**Three genre words the model never learned are rewritten**, each
measured in context on up to eight real balloons — a term judged alone is
a different term, since パーティー alone returns "partie" and in a
sentence "fête". 勇者 over 27 occurrences: 4 gains, 0 losses.
パーティー over 24: 7 gains, 0 losses. 流石 over 4: 3 gains, 0 losses.

**A name is no longer rewritten into a noun.** ユメ is the word for
dream and it is also Yume. Over 1 686 balloons the rule fired twice and
both were the name — どうした？ユメ would have asked a dream how it was
doing.

## Both languages: credits, sentence cuts, and a looping decoder

**Credits stay in the language they were printed in.** The translator
takes a byline apart: "By Kouji Miura" came back "B y you ji Miur a". A
name is the one thing a small model has no business rewriting, and a
colophon is almost nothing but names — 58 of 1 803 balloons, 3.2%.
Nothing is removed: a block that never reaches the translator has no
panel painted over it, so the line stays as printed. Being wrong is
cheap here, which is the point.

**A sentence shared by two balloons is cut on a word that can hold the
cut.** Of the twenty-four multi-balloon groups in the corpus, eleven
ended a balloon on an article, a preposition or an auxiliary — "il
s'agit du rythme du | jeu" — which is the fragmentation the assembler
exists to undo, put back one stage later.

**A decoder that loops no longer ruins the sentence around it.** Over
1 646 Japanese balloons it repeats a word on nine, twice destroying a
line that was otherwise right. The run is collapsed rather than the
translation rejected, because rejecting throws away the half that was
good. The obvious rule — collapse anything repeated three times — was
measured on the English corpus and refused: all twelve of its changes
there were losses, because in comics the repetition is in the art.

## A slow server no longer closes the application

The reader was shut down mid-volume by a timeout on the request that
fetches the *next* volume, ahead of time, which the reader is built to do
without. That prefetch ran with no catch, so the exception reached the
crash screen. It is now handled where it happens.

## One page, one reload

A page that failed came back as a black panel with a line of red text and
nothing to do about it: turning back and forth changed nothing, only
leaving the book and reopening it did. There is now a Reload button on
the failed page, and the message says which error it was.

The button matters because of what sat underneath it. A page already
being loaded is not requested twice — the reader keeps the pending work
and waits on it. But work that had *finished badly* looked exactly like
work still in flight, so every later request for that page was answered
with the stored failure, instantly and forever. That is why one page
could stay broken while the next two loaded fine: nothing was retrying.

## The server was never the problem

Measured on the same server, the same evening: the shelf query that was
dying answered `curl` in 1.6 seconds with the app closed, and took 23.1,
24.1 and 25.6 seconds from inside the app. Twice it passed the 30-second
limit and gave up after 31.2 seconds without receiving a single byte.

Eleven shelves were being asked for at once, on a server also sending
covers. The reader did the same with its page downloads, and the refresh
that runs when a book closes did it a third time — worse, two and three
times concurrently, because closing the reader, returning to the home
screen and the server's own progress notification each started their own
copy. The same query went out three times in parallel, taking 7.8, 9.3
and 9.4 seconds, while the book being opened queued behind all three.

All three are now capped at four requests at a time, and overlapping
refreshes share one. The network timeout moved from 30 to 60 seconds as
a floor, not as the fix.

On the first run after the change: no timeout at all, the first four
shelves in 2.3 seconds, and 73 pages read end to end without a failure.
Not proof the fault is gone — 73 clean pages put its rate below roughly
4%, no lower — but the first session in a while with nothing in it.

## What was measured and refused

A wider beam on the translator: annotated blind on 149 balloons, better
on 23% and worse often enough to be a wash, at several times the decode
cost on a tablet. A stronger Japanese-to-English model: it repaired 6 of
19 wrong balloons and regressed 11 of 30 that were already right, by
confident invention. Rewriting the stat-screen labels: every variant
broke a phrase elsewhere or invented a name.

And the measuring tool itself was found to be lying — it loaded two
glossary entries instead of the shipped 257 whenever it ran beside the
other tests, reporting the reader as worse than it is. It does not break
when it goes wrong, it answers.

---

# Kora 1.8.0

Le japonais passe désormais par une chaîne qui répare ce que la
reconnaissance a mal lu avant que le traducteur n'y touche, le lecteur
peut réessayer une page en échec, et l'application ne se ferme plus
parce qu'un serveur a été lent.

## Japonais : la reconnaissance est corrigée avant la traduction

Cinquante pages d'un tome japonais lues et leurs 231 bulles distinctes
classées à la main ont donné le partage sur lequel cette version est
construite : 34 bulles où la reconnaissance avait déjà corrompu le
japonais, 71 où le japonais était propre et le français faux. L'indice
de confiance ne prédit ni l'un ni l'autre — une bulle lue à 60% se
traduit correctement, une lue à 99% revient inversée.

Le travail est donc en étages, chacun mesuré séparément.

**Les furigana sont retirés.** Les furigana sont les petits kana
imprimés à côté d'un kanji pour en donner la lecture. Personne ne veut
les traduire : ils épellent un mot déjà présent sur la page, arrivent au
traducteur en phonétique pure et reviennent en panneau de charabia peint
à côté de la ligne à laquelle ils appartiennent. Le problème n'est
apparu qu'avec les scans plus hauts — les mêmes vingt-cinq pages passées
en 1351x1920 puis en 1200px donnent 12% de caractères en plus en pleine
hauteur, et 83% de ce gain est du furigana.

**Le dialecte est réécrit en japonais standard.** Le moteur ne perd pas
seulement le registre, il inverse la réplique : 見つからへんど, « tu n'en
trouveras pas », revenait en « peut être trouvé ». Sur les 231 bulles :
19 touchées, 9 nettement meilleures, aucune dégradée, et quatre des neuf
inversions remises à l'endroit.

**La table de dialecte ne se déclenche plus à l'intérieur de mots
ordinaires.** Sur sept tomes qui n'en contiennent pas une ligne, elle
s'est déclenchée onze fois : cinq à raison, six à tort, dont trois
produisant だろうう, qui n'est un mot dans aucune langue. Toute entrée
courte en hiragana porte désormais une garde.

**Les marques de sonorisation manquantes sont rétablies sur les
katakana.** パーティー, le nom le plus courant du genre, était lu faux
plus souvent que juste — 14 fois contre 11 sur sept tomes — et le dégât
ressortait deux étages plus loin sans ressembler à un problème de
lecture : アレンのハーティー revenait en « Allen Est Copieux ». Une
courte liste de mots arbitre, pour qu'un mot qui n'a jamais porté de
marque n'en reçoive pas.

**Les caractères pris dans la mauvaise écriture sont corrigés.** Formes
chinoises simplifiées dont le japonais n'a aucun usage, tiret ASCII à la
place du signe d'allongement (ペ-ジ recevait « 127Peg » sur six pages),
世 lu pour せ, 句 et 自 lus pour 匂 entre deux い, et un titre de
chapitre portant une parenthèse coincée entre le mot et son numéro.
C'est dans les fiches de statistiques, dont le genre est plein, que ça
tombe : 年龄:21 性别:女 revenait en « Année 龄:21 性别:女 race:
personnes ».

**Trois termes de genre que le modèle n'a jamais appris sont réécrits**,
chacun mesuré en contexte sur jusqu'à huit bulles réelles — un terme
jugé seul est un autre terme, puisque パーティー seul revient « partie »
et dans une phrase « fête ». 勇者 sur 27 occurrences : 4 gains, 0 perte.
パーティー sur 24 : 7 gains, 0 perte. 流石 sur 4 : 3 gains, 0 perte.

**Un nom propre n'est plus réécrit en nom commun.** ユメ est le mot pour
« rêve » et c'est aussi Yume. Sur 1 686 bulles la règle s'est déclenchée
deux fois et les deux étaient le prénom — どうした？ユメ aurait demandé à
un rêve comment il allait.

## Les deux langues : crédits, coupures de phrase, décodeur qui boucle

**Les crédits restent dans la langue où ils ont été imprimés.** Le
traducteur démonte une signature : « By Kouji Miura » revenait en « B y
you ji Miur a ». Un nom est la seule chose qu'un petit modèle n'a aucune
raison de réécrire, et un ours n'est presque que des noms — 58 bulles
sur 1 803, soit 3,2%. Rien n'est supprimé : un bloc qui n'atteint jamais
le traducteur ne reçoit aucun panneau par-dessus, la ligne reste telle
qu'imprimée. Se tromper ne coûte rien ici, et c'est tout l'intérêt.

**Une phrase partagée par deux bulles est coupée sur un mot qui peut
porter la coupure.** Sur les vingt-quatre groupes multi-bulles du
corpus, onze terminaient une bulle sur un article, une préposition ou un
auxiliaire — « il s'agit du rythme du | jeu » — c'est-à-dire la
fragmentation que l'assembleur existe pour défaire, réintroduite un
étage plus loin.

**Un décodeur qui boucle ne ruine plus la phrase autour.** Sur 1 646
bulles japonaises il répète un mot sur neuf, et deux fois il détruisait
une ligne par ailleurs juste. La répétition est repliée plutôt que la
traduction rejetée, parce que rejeter jette aussi la moitié qui était
bonne. La règle évidente — replier tout ce qui est répété trois fois — a
été mesurée sur le corpus anglais et refusée : ses douze modifications y
étaient douze pertes, parce qu'en bande dessinée la répétition est dans
le dessin.

## Un serveur lent ne ferme plus l'application

Le lecteur était coupé en plein tome par un dépassement de délai sur la
requête qui va chercher le tome *suivant*, en avance, et dont le lecteur
sait très bien se passer. Ce préchargement tournait sans filet :
l'exception arrivait à l'écran de plantage. Elle est désormais traitée
là où elle se produit.

## Une page, un rechargement

Une page en échec revenait en cadre noir avec une ligne rouge, sans
recours : revenir en arrière et repasser n'y changeait rien, il fallait
quitter le tome et le rouvrir. Un bouton Recharger est désormais posé
sur la page fautive, et le message dit de quelle erreur il s'agit.

Ce bouton compte à cause de ce qu'il y avait dessous. Une page déjà en
cours de chargement n'est pas redemandée deux fois : le lecteur garde le
travail en attente et s'y accroche. Mais un travail *terminé en échec*
ressemblait trait pour trait à un travail encore en cours, si bien que
toute demande ultérieure recevait l'échec mémorisé, immédiatement et
pour toujours. D'où une page qui restait cassée pendant que les deux
suivantes s'affichaient : rien ne réessayait.

## Le serveur n'y était pour rien

Mesuré sur le même serveur, le même soir : la requête d'étagère qui
mourait répondait à `curl` en 1,6 seconde avec l'application fermée, et
prenait 23,1, 24,1 puis 25,6 secondes depuis l'application. Deux fois
elle a dépassé la limite de 30 secondes et abandonné au bout de 31,2
secondes sans avoir reçu un seul octet.

Onze étagères étaient demandées d'un coup, à un serveur qui envoyait
aussi les couvertures. Le lecteur faisait pareil avec ses pages, et le
rafraîchissement déclenché à la fermeture d'un tome une troisième fois —
pire, deux et trois fois en parallèle, parce que quitter le lecteur,
revenir à l'accueil et la notification de progression du serveur en
lançaient chacun leur exemplaire. La même requête est partie trois fois
de front, en 7,8, 9,3 et 9,4 secondes, pendant que le tome qu'on ouvrait
faisait la queue derrière les trois.

Les trois sont désormais plafonnés à quatre requêtes simultanées, et les
rafraîchissements qui se chevauchent n'en font plus qu'un. Le délai
réseau passe de 30 à 60 secondes comme filet, pas comme correctif.

Au premier essai après la correction : aucun dépassement, les quatre
premières étagères en 2,3 secondes, et 73 pages lues d'affilée sans un
échec. Ce n'est pas une preuve que la panne a disparu — 73 pages propres
placent son taux sous 4% environ, pas plus bas — mais c'est la première
séance depuis longtemps où il n'y a rien à signaler.

## Ce qui a été mesuré puis refusé

Un faisceau plus large sur le traducteur : annoté en aveugle sur 149
bulles, meilleur dans 23% des cas et moins bon assez souvent pour que ce
soit nul, à plusieurs fois le coût de décodage sur une tablette. Un
modèle japonais-anglais plus fort : il réparait 6 bulles fausses sur 19
et en dégradait 11 sur 30 déjà justes, par invention assurée. Réécrire
les intitulés des fiches de statistiques : chaque variante cassait une
phrase ailleurs ou inventait un nom.

Et l'outil de mesure lui-même a été pris en défaut : il chargeait deux
entrées de glossaire au lieu des 257 embarquées dès qu'il tournait avec
les autres tests, donnant le lecteur pour plus mauvais qu'il n'est. Il
ne casse pas quand il se trompe, il répond.
