# Traduction japonaise — la suite du plan

Date : 19/08/2026, fin de journée. Complète `chantier_traduction_ja_etat.md`,
qui reste le document d'état ; celui-ci ne traite que de la suite.

---

## 1. Où on en est : 72,0 %

| | avant | après |
|---|---|---|
| correct | 104 (69,3 %) | **108 (72,0 %)** |
| dégradé | 21 | 20 |
| faux | 25 | 22 |

Démontré par A/B apparié sur les mêmes 198 bulles : **9 gains, 0 régression**,
McNemar p = 0,002.

Cinq livraisons dans la journée :

| commit | quoi | poids |
|---|---|---|
| `f8be5035` | le corpus de contrôle et le taux de base | — |
| `6d1d3f9b` | dakuten perdu par l'OCR | 0,9 % des bulles |
| `35d34d16` | glossaire de termes de genre | 2,9 % |
| `7e682119` | re-mesure appariée | — |
| `b9e1ac32` | boucles inventées par le décodeur | 0,5 % |

---

## 2. Le problème du plan actuel

Les trois correctifs livrés pèsent **0,9 %, 2,9 % et 0,5 %** des bulles. Ils sont
propres — 15/0, 11/0 et 9/0 — mais leur taille plafonne le résultat.

J'ai mesuré tout ce qui reste d'attaquable par table :

| Chantier restant | bulles | % du corpus |
|---|---|---|
| Écrans de statut (`武勇：19 統率：21`, caractères chinois) | 10 | 0,6 % |
| Furigana résiduel (`世んいん船員`) | 8 | 0,5 % |
| Tiret ASCII (`ペ-ジ` → « 127Peg ») | 6 | 0,4 % |
| Caractères chinois hors tableaux | 0 | 0,0 % |

**Total : 24 bulles, 1,5 %.** Tout faire parfaitement mènerait à ~73–74 %.

Ce n'est pas un plan, c'est une fin de gisement. Les tables ont donné ce
qu'elles avaient.

---

## 3. Ce qui reste vraiment : les erreurs de modèle

Sur les 42 échecs restants de l'échantillon aléatoire :

| Cause | n | Traitable par table ? |
|---|---|---|
| ja→en | 25 (60 %) | **~10 sont des erreurs de modèle pures — non** |
| OCR | 12 (29 %) | oui, mais 1,5 % du corpus |
| en→fr | 4 (10 %) | marginalement |
| crédits | 1 (2 %) | rangé |

Les erreurs de modèle pures sont d'une autre nature :

| japonais | rendu |
|---|---|
| `くっ来るなっ！！` (« n'approche pas ! ») | « Allez ! » — négation perdue |
| `こんだけあれば今夜は旨いもんが食えるな` | « ne mangez pas de bonne nourriture » — négation **inventée** |
| `ハクさんがかわいそうですよオラリアさん！` | destinataire et sujet échangés |

Aucun dictionnaire n'atteint ça. Il faut agir sur le décodage lui-même.

---

## 4. La piste beam — mesurée, et fermée

Le banc et l'app tournent en `beam-size: 1`. **Vérifié dans le code** et non
supposé : `translator.cpp:49` construit un config minimal avec `beam-size: 1`
dès que l'appelant ne fournit rien, et `BergamotTranslationEngine` passe
`configYaml = null`. Le banc mesure donc bien le moteur du lecteur.

### La première mesure était mal posée

`run_pivot_ja.sh` applique le même beam **aux deux sauts**. Mon premier test
« beam 4 » montait donc ja→en ET en→fr simultanément : 176 bulles sur 198
changeaient, sans qu'on puisse dire lequel des deux décodeurs en était la cause.

Isolés :

| config | ja→en | en→fr | bulles modifiées |
|---|---|---|---|
| B | 4 | 1 | 149 (75 %) |
| C | 1 | 4 | 118 (60 %) |
| D | 4 | 4 | 176 (89 %) |
| B2 | 2 | 1 | 120 (61 %) |

91 bulles changent par les deux sauts — les effets se mélangeaient bien.

### L'annotation aveugle tranche : non

Les 149 bulles que ja→en beam 4 modifie, présentées en X/Y dans un ordre
aléatoire, jugées sans savoir laquelle était laquelle, puis révélées :

| | n | |
|---|---|---|
| beam 4 meilleur | 35 | 23 % |
| **beam 1 meilleur** | **28** | 19 % |
| équivalent | 86 | 58 % |

**Solde +7. Test binomial bilatéral sur les 63 discordantes : p = 0,45.**
Indiscernable du hasard.

À comparer avec ce qui a été livré :

| correctif | gains / pertes |
|---|---|
| dakuten | 15 / **0** |
| glossaire de domaine | 11 / **0** |
| boucles décodeur | 9 / **0** |
| **beam 4 ja→en** | **35 / 28** |

Beam 4 échange des erreurs contre d'autres erreurs. Il corrige `待て待て俺の方が`
(« Attends ×8 » → « Attends, attends, c'est moi ! ») et `体力も魔力も回復` (« et
l'igname » → « votre force physique et votre magie »), et casse ailleurs
autant : `きゃ` devient `くゃ`, un sujet change de personne, un nom propre est
translittéré différemment.

**Piste fermée.** Beam 2 n'est pas testé : il modifie moins que beam 4 (120
bulles contre 149) et ne peut donc pas faire mieux qu'un résultat déjà nul.
en→fr beam 4 n'est pas testé non plus — en→fr ne pèse que 10 % des échecs.

### Ce que cette mesure invalide aussi

Mon « +19 % de coût CPU » annoncé la veille était **du bruit**. En chronométrant
les cinq configurations, beam 1 partout ressort comme la plus lente des cinq —
c'est du temps de chargement, pas du décodage. Je l'avais présenté comme un
fait ; il ne l'était pas.

---

## 5. Le plan, après la fermeture du beam

### P1 — OCR polish

Les 24 bulles du §2 : tiret ASCII, furigana résiduel, écrans de statut. Petit,
sûr, déterministe, quelques heures. À livrer parce que c'est peu coûteux, pas
parce que c'est important — critère de la revue : *le coût d'implémentation
entre dans la priorité, pas seulement la couverture*.

Plafond attendu : ~73–74 %.

### P2 — Élargir le corpus

150 bulles donnent ±7,4 points. Pour suivre le taux global à ±5 il en faut 323,
à ±4 il en faut 504. Non bloquant tant qu'on mesure en apparié, mais nécessaire
dès qu'on voudra annoncer un chiffre au lieu de comparer deux versions.

### P3 — Noms propres

3 à 5 des 65 échecs. Un mécanisme de jetons Latin (`Xqz0`) existe déjà dans
`ReaderState` pour le glossaire de série, donc le prototype de placeholders que
la revue réclamait est en partie écrit. Petit gisement, coût réduit.

### P4 — Fine-tuning ja→en

**Devenu le seul levier restant à fort potentiel**, maintenant que le beam est
fermé. Lourd : données, entraînement, simulation 8 bits, quantification, export
Bergamot. Plusieurs semaines, résultat incertain. À ne pas lancer sans une
décision explicite.

---

## 6. Le point de décision

La journée a fermé la dernière piste bon marché. Ce qui reste :

| Option | Effort | Résultat attendu |
|---|---|---|
| **A. S'arrêter à 72 %** | consolidation seule | 72 % |
| **B. P1 + P2 + P3** | une à deux soirées | ~74 %, puis plafond ferme |
| **C. Fine-tuning ja→en** | plusieurs semaines | inconnu |

**Ma recommandation : B, puis s'arrêter.** Les trois chantiers restants sont
petits mais sûrs (chacun mesurable, chacun à 0 régression attendue), et à leur
issue le plafond de l'approche « corriger autour du modèle » sera atteint pour
de bon.

C n'a de sens que si 74 % te paraît insuffisant à l'usage. C'est une question de
confort de lecture, pas une question technique — et elle t'appartient.

---

## 7. Ce que cette journée a appris sur la méthode

1. **Le résultat négatif est un résultat.** Beam search paraissait le levier
   évident : 89 % des sorties changeaient. Après annotation aveugle il ne vaut
   rien. Sans l'annotation il serait parti en production.
2. **L'aveuglement n'est pas une coquetterie.** J'annotais mon propre correctif ;
   présenter les deux sorties en X/Y aléatoire est ce qui rend le 35/28
   crédible.
3. **Un test mal posé produit un chiffre net et faux.** « beam 4 » sur un pivot
   ne veut rien dire — deux décodeurs, deux paramètres. La première mesure a
   montré 89 % de changement sans pouvoir l'attribuer.
4. **Vérifier avant de mesurer.** Le beam de l'app était supposé valoir 1 ; il
   fallait lire `translator.cpp` pour le savoir. Si le défaut avait été 6, tout
   le corpus aurait mesuré un moteur que le lecteur n'exécute pas.
5. **Un chiffre de temps mesuré une fois n'est pas une mesure.** Mon « +19 % »
   était du bruit de chargement, et je l'avais annoncé comme un fait.
