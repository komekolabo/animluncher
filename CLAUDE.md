# AnimLauncher — notes pour Claude

App Android de Will (GitHub : komekolabo). Elle joue une **animation plein écran à l'ouverture
de chaque app** (Claude, Google, Duolingo…), sur son Samsung Galaxy Z Flip6 (Android 16).
Objectif : la publier sur le Google Play Store, avec une pub non invasive.

> **⚠️ BRANCHE DE TRAVAIL : `version-store`.** C'est sur elle qu'on travaille (la version Store est la
> priorité). Toujours partir de `version-store` et y envoyer les changements, **jamais directement sur
> `main`** (le 09/11, le tirage équilibré a été poussé sur `main` par erreur, puis reporté ici).

## Qui est Will (à respecter)
- **Débutant en code** : toujours expliquer ce que fait le code, en français simple.
  Les commentaires du code sont en français et pédagogiques (garder ce style).
- **Animations** : thèmes sombres, **jamais de flash lumineux**, mouvements doux.
- Avant d'intégrer une nouvelle animation : lui montrer une **vidéo d'aperçu VERTICALE (portrait) uniquement**.
  Aperçus faits avec skia-python (même moteur que le Canvas Android) + ffmpeg : un petit « moteur » Python
  qui imite `Outils.kt` (halo, tache, révélations…), écran 540 x 1320, vitesse réelle puis ralenti x0,5.
  Il ne vit pas dans le repo : à recréer si besoin (la session précédente l'avait dans /home/claude/apercu).
- **Les arcs / horizons / anneaux de planète en bas d'écran sont réservés aux animations de Claude.**
  (Exception voulue par Will le 10/11 : le tour lumineux de la réaction sonore passe aussi en bas.)
- **Varier les révélations** (pas toujours un cercle) : Outils propose cercle doux, balayage, bandes,
  trame de points, colonnes, tuiles, fente.
- Textes affichés dans les animations : **en anglais**.
- Il n'a **pas Android Studio** : tout se compile sur GitHub (voir plus bas). Il teste sur son téléphone.
- Aperçus Python : une image skia est en **BGRA** : avant d'envoyer à ffmpeg en `rgba`, faire
  `img.convert(colorType=skia.kRGBA_8888_ColorType, alphaType=skia.kUnpremul_AlphaType)` (oublié le 09/11 :
  rouge et bleu inversés dans les vidéos ; c'est d'ailleurs comme ça que la palette chaude a été choisie).
- Aperçus Python : dans skia-python, `Image.makeShader()` est très lent → créer les shaders d'image UNE fois
  (petites textures 128 x 128 répétées). Rendre une vidéo prend ~20 s à 1,5 min.

## Les deux versions (Gradle « flavors », voir app/build.gradle.kts)
| | Perso | Store |
|---|---|---|
| Identifiant | `com.will.animlauncher` | `com.will.animlauncher.store` |
| Nom | AnimLauncher Perso | AnimLauncher |
| Logo | identique (« Étincelle halo ») | identique (« Étincelle halo ») |
| Accessibilité | complète (`src/main/res/xml/accessibility_config.xml`) | allégée, sans lecture d'écran (`src/store/res/xml/…`) |
| Extras | journal envoyé sur GitHub, mode standard, outils de dev | écran d'explication Google, pas d'Internet |

- Code commun : `src/main`. Spécifique : `src/perso`, `src/store`.
- Dans le code : `BuildConfig.STORE` (true seulement en Store).
- Les deux s'installent côte à côte : n'en activer **qu'une** dans l'accessibilité.

## Carte du code (app/src/main/java/com/will/animlauncher)
- `AppWatcherService.kt` : **le cœur**. Service d'accessibilité : détecte le clic sur une icône
  de l'accueil (`typeViewClicked`, avant l'ouverture de l'app) et les changements de fenêtre ;
  gère « seulement au démarrage », l'anti-rebond, les relais (Gemini → Google), les widgets,
  les apps récentes, la rotation.
- `Rideau.kt` : la fenêtre des animations, installée une fois et invisible (alpha 0) entre deux.
- `Catalogue.kt` : **quelle animation pour quelle app** (nom technique → scène, catégorie de durée,
  variantes tirées au hasard). Pour ajouter une app : une ligne.
- `Outils.kt` : `Scene`, `Instant` (secondes, intro 0→1, revelation 0→1), `Categorie` (durées),
  et les outils de dessin (halo, tache, poussières, bokeh, vignette, révélations douces…).
- `Scenes*.kt` : les animations, par lots (IA, Google, système, social, finance, jeux…).
  Lot 2 des IA : `ScenesEspace.kt` (ChatGPT : Collision de galaxies — film précalculé en arrière-plan —
  et Lancement), `ScenesPuces.kt` (Gemini : TPU, Quantique), `ScenesCourseIA.kt` (la course à l'IA en
  chiffres : Benchmarks/ChatGPT, Power/Gemini, Intelligence/Grok ; « Autonomy » de Claude retirée du catalogue le 09/11 ;
  ses classes `Autonomie` et l'ancienne `Pellicule` d'Instagram restent dans le code, inutilisées : à supprimer un jour).
  Instagram (09/11, remplace « Pellicule ») : `ScenesInstagram.kt` = « Défilement sans fin » (Reels : swipes
  nets qui accélèrent, l'app arrive comme le swipe suivant ; 8 décors détaillés précalculés dans `FondsReels`,
  flou de mouvement via RenderNode sur Android 12+) et `ScenesMurNuit.kt` = « Mur dans la nuit » (20 polaroïds
  qui s'allument, guirlande, parallaxe ; photos et sapins précalculés dans `FondsMur`). Aperçus Python :
  /home/claude/apercu/reels.py, reels_hq.py, nuit.py, photos.py.
  Détails Instagram : `Reels` garde UN état (`tRev` = l'instant où la révélation commence) car le swipe de
  l'app doit tomber dans le rythme des swipes ; swipes à x0,82 avec un minimum de 0,07 s (sinon ça converge
  vers ~2 s). Dans l'app, les 20 photos du mur sont FIXES (les petites animations de l'aperçu — vapeur,
  phare, feux d'artifice — n'ont pas été portées). Will a rejeté : « Pellicule », Filtres/île, Le fil,
  Mosaïque cœur, mur→arbre, tour de Babel ; il a validé Reels (swipes nets qui accélèrent) et le mur cosy.
  Les variantes d'une app sont tirées au hasard, en TIRAGE ÉQUILIBRÉ (09/11, `Reglages.tirerVariante`) :
  chaque variante a un compteur d'attente (carnet « attente_<app> »), poids = 1 + attente (max 6) ;
  celle qui se joue repart à 0, les autres cochées +1 → la même ne retombe 2 fois de suite que ~6 % du
  temps (20 % au hasard pur). L'aperçu des réglages ne touche pas aux compteurs. On ajoute les nouvelles À LA FIN de la liste
  (les réglages retiennent les variantes décochées par leur numéro).
  Chaque scène hérite de `Scene` et dessine **toute l'image à chaque instant** (pas d'état caché).
  Elles sont calibrées sur un écran « modèle » de 540 de large (k = largeur / 540).
- `LogoWidget.kt` + `res/layout/widget_logo.xml` + `res/drawable/logo_widget(_anime).xml` : le WIDGET
  « logo animé » 1 x 1 (09/11), à poser à la place de l'icône (bouton sur l'écran principal). Astuce :
  un widget ne peut pas lancer notre code de dessin, mais une ProgressBar joue en boucle son
  `indeterminateDrawable` -> on lui donne un AnimatedVectorDrawable (lévitation, tiers de tour du
  triangle toutes les 4,5 s, étoile qui scintille ; tout en `repeatCount="infinite"`).
  REMPLACE l'ancien essai « icône vivante » (fenêtre posée par-dessus l'icône, supprimé le 09/11) :
  sans lire l'écran, impossible de savoir la page affichée / si le tiroir d'apps est ouvert
  (Samsung n'envoie qu'un « contenu changé » sans direction quand on ouvre/ferme le tiroir).
  Ne pas refaire. Pistes écartées : Shizuku (dépendance + relance à chaque redémarrage), captures d'écran.
- `WidgetApp.kt` + `res/layout/widget_app*.xml` + `res/drawable/widget_galet*.xml` : ESSAI (09/11) du widget
  « une app dans un galet », posé depuis Animations par app → l'app → « Mettre le widget sur l'accueil »
  (requestPinAppWidget + rappel qui donne le numéro du widget ; app retenue par widget). Galet IMMOBILE,
  dessiné par l'app avec la COUCHE DE FOND de l'icône (Will veut la vraie couleur : le orange de Claude),
  reflet + liseré de verre par-dessus ; le LOGO
  de l'app flotte dedans avec son ombre sur le fond du galet, bande de lumière toutes les 6 s. Le logo est une
  image du téléphone (couche avant de l'icône adaptative) : on dessine les images de la flottaison au moment
  de poser le widget et un ViewFlipper les fait défiler (« folioscope », 60 images à 20 images/s ; 12/s saccadait). Will a refusé le halo
  (« juste l'ombre ») et veut que ce soit le logo qui flotte, pas le galet. Aperçus : /home/claude/apercu/widgets_*.py.
- DOSSIERS ANIMÉS (09/11) : `Groupes.kt` range les apps par catégorie (AI, Social, Music & Video, Photos, Games,
  Finance, Maps & Travel, Productivity, News) : d'abord NOTRE liste d'apps connues, sinon `ApplicationInfo.category` ;
  apps décochables (carnet « groupes »). `GroupesActivity.kt` (bouton « ✦ Dossiers animés » de l'écran principal) :
  un groupe = une carte, bouton « Voir » et « Mettre sur l'accueil ». `WidgetGroupe.kt` + `widget_groupe.xml` +
  `etoile_ia(_anime).xml` : galet + étoile à 4 branches (dessin à nous) qui tourne (20 s/tour) et respire.
  Appui -> `DossierActivity.kt` : écran TRANSPARENT (thème `Theme.Dossier`, taskAffinity vide, hors récents) qui
  dessine l'animation validée par Will (aperçu /home/claude/apercu/dossier.py) : voile sombre, l'étoile part du
  widget (`intent.sourceBounds`), monte au centre en faisant un tour, se pose près du titre, traînées de poussière
  vers chaque app, apps qui apparaissent l'une après l'autre. Appui sur une app -> `AppWatcherService.actif
  ?.depuisDossier(app)` (le lanceur n'envoie pas de clic pour NOTRE écran) puis on ouvre l'app.
  V2 (09/11, validée sur aperçu) : PALETTE CHAUDE (étoile orange -> rose -> vert-jaune, galet brun
  `widget_galet_chaud.xml`, panneau brun) ; le widget joue une « mini-vidéo » de 12 s (étoile -> 4 points qui
  tournent en traçant un anneau -> 4 tuiles qui ondulent -> l'étoile renaît), dans `etoile_ia(_anime).xml`
  FABRIQUÉS par `outils/generer_avd.py` (raccourcis d'interpolateurs dans `res/interpolator/`) ; le voile
  arrive en 2 BANDES (haut et bas) ; les apps ÉCLOSENT (ressort + petite rotation + onde fine, nom qui glisse) ;
  case « + » en dernier -> `ChoixAppActivity.kt` (ajout retenu dans « avec_<groupe> », `Groupes.tous()`).
  Will a choisi la palette chaude, a écarté 6 pistes de logo (Facette, Constellation, Orbite, Pétales…).
  V5 (09/11) : bulle « + » = ARC qui épouse le coin en haut à droite (à distance) ; dès 55 apps elle affiche les
  places restantes ; `Groupes.MAX = 60` apps par dossier. Cadre : 2 colonnes (≤ 4 apps) sinon 3, au plus 4 lignes,
  puis PAGES (glisser à gauche, points en bas). Sous chaque app : « Open » (point vert, `AppWatcherService.estOuverte`)
  ou « 5m ago » (carnet « ouvertures », `noterOuverture` dans `fenetre()`). Appui sur une app = ouverture IMMÉDIATE
  (dès 0,4 s). Geste « accueil » : l'écran du dossier disparaît sans pouvoir dessiner -> `onPause` demande au
  service de dessiner le retour de l'étoile dans une fenêtre d'accessibilité temporaire (`RetourEtoile.kt`).
- RÉACTION SONORE (10/11) : les bords de l'écran réagissent à la musique du téléphone, PARTOUT (hors de l'app aussi).
  `EcouteSon.kt` = l'outil Android `Visualizer(0)` (niveau du son en direct, rien d'enregistré ; exige les
  permissions RECORD_AUDIO + MODIFY_AUDIO_SETTINGS, ajoutées au manifest commun) -> 5 valeurs 0..1 : grave, medium,
  aigu, hauteur (note dominante), claque (son sec). `ReactionSonore.kt` = carnet « son » (actif, style) + `VueSonore`,
  une fenêtre d'accessibilité transparente et non touchable posée par le service (c'est `Rideau.onAttachedToWindow`
  qui appelle `ReactionSonore.demarrer` : pas besoin de toucher à AppWatcherService). Elle ne dessine que si
  `AudioManager.isMusicActive`, et s'efface en plein écran (barre d'état cachée = vidéo / jeu) et en paysage.
  UN SEUL DESSIN depuis le 10/11 (Aurore, Cordes, Verre liquide retirés) : le PRISME en TOUR COMPLET de l'écran
  (bas compris, suit l'arrondi des coins via `RoundedCorner`), ligne à 5 dp du bord ; la BANDE entre la ligne et
  le bord se colore selon le son à chaque endroit (vers le blanc quand c'est fort), + halo qui déborde vers
  l'intérieur. « Piano » : graves en bas (vague qui monte des deux côtés), mélodie sur les côtés, aigus en haut.
  2 palettes (carnet « son », clé « style ») : 0 = CHAUDE (braise -> orange -> or), 1 = FROIDE (violet -> bleu ->
  turquoise). La fenêtre fait la taille RÉELLE de l'écran (`getRealSize` + `setFitInsetsTypes(0)`, comme le rideau).
  Aperçu validé : /home/claude/apercu/prisme2.py (prisme_tour_complet.mp4).
  PLEIN ÉCRAN : notre fenêtre est au-dessus de la barre d'état et n'en reçoit pas les infos -> 1re version
  croyait toujours être en plein écran et ne dessinait jamais (corrigé : repli sur `currentWindowMetrics`,
  sinon « pas plein écran »). La fenêtre du rond affiche un DIAGNOSTIC en direct (✓/✗) quand le mode est allumé.
  `ReactionSonoreUi.kt` = `BoutonSon` (rond en bas à droite de l'accueil) + `FenetreSon` (feuille du bas :
  explication « rien n'est enregistré », choix de la palette, diagnostic, Activer -> question micro d'Android).
  Textes dans `res/values*/strings_son.xml` (6 langues). Aperçus : /home/claude/apercu/premium.py (avec musique).
  Testé par Will le 10/11 : la ligne s'affiche (après la correction du plein écran). Reste à confirmer : la
  réaction quand l'app est en arrière-plan, et la disparition en vidéo plein écran.
  Play Store : la permission micro devra être justifiée dans la fiche (option éteinte de base, écran d'explication).
  V3 DU SOIR (10/11, validée sur aperçus `reel.py` et `refrain.py`) — REMPLACE ce qui est dit plus haut sur les
  palettes et la ligne : (1) la ligne reste DROITE (la ligne poussée par le spectre faisait des bosses, « moins
  beau ») ; (2) la lumière suit les ATTAQUES, plus le niveau : `EcouteSon.attaques` (40 tranches : montée du
  moment comparée à la montée moyenne sur 1,5 s, retombe en 0,22 s) et `EcouteSon.frappe` (pareil pour les
  basses, 0,25 s) ; intensité d'un segment = 0,03 + 0,85·frappe + 0,65·attaque -> presque noir entre les coups.
  Les mesures identiques (Android en redonne) sont ignorées pour ces calculs ; (3) COULEUR AUTOMATIQUE :
  `EcouteSon.fort` (fonction `moment()`) = puissance (somme du spectre + 3 x basses, lissée 0,35 s / 1 s)
  comparée au sommet de la dernière minute ; > 62 % -> « ça envoie », < 38 % -> calme ; en plus il faut avoir
  entendu un passage nettement plus calme (sommet > 1,6 x creux), sinon un début de morceau serait pris pour un
  refrain. Fondu de 0,25 s dans `VueSonore` (`melangeFort`) ; (4) 2 COULEURS réglables (carnet « son » :
  `teinte_calme` 215 = bleu, `teinte_fort` 6 = rouge-orangé ; `ReactionSonore.palette()` en tire 4 couleurs) :
  fenêtre du rond -> lignes « Calme » / « Quand ça envoie » -> `RoueTeinte` (roue maison ; pendant le réglage
  `ReactionSonore.montrer()` allume le tour dans cette couleur même sans musique). Les anciennes palettes
  chaude/froide (clé « style ») n'existent plus. Le diagnostic affiche « Moment : calme/ça envoie (xx %) ».
  PAS ENCORE TESTÉ sur le téléphone : les seuils (62/38, 1,6) et la sensibilité des attaques ont été réglés sur
  le son propre d'une vidéo, pas sur les mesures 8 bits du Visualizer -> à ajuster selon le retour de Will.
  V4 (10/11, 21h30, envoyée SANS aperçu à la demande de Will) — CORRIGE la V3 : (1) Will préfère l'accompagnement
  d'avant : l'intensité redevient le NIVEAU du spectre (0,85·spectre + 0,30·coup), `attaques`/`frappe` sont encore
  calculés mais plus utilisés ; la ligne reste droite ; (2) « petits rectangles » visibles -> 360 segments au lieu
  de 180 + lissage (1/4, 1/2, 1/4 deux fois) ; le halo reste dessiné par paires (180) pour ne pas alourdir ;
  (3) 3 COULEURS : `EcouteSon.moment` 0 = ÉCOUTE (8 premières secondes, vert 135), 1 = calme, 2 = ça envoie ;
  carnet « son » : `teinte_ecoute`, `teinte_calme`, `teinte_fort`. Protocole dans `EcouteSon.situer()` : repères
  « habituels » qui bougent (descendent en 8 s, montent en 30 s, 90 s pendant un « ça envoie »), 3 indices qui
  votent (niveau > 1,35 x, basses > 1,5 x, cadence > 1,3 x ; 2 sur 3 ; sortie à 1,15 / 1,2 / 1,1), couleur tenue
  au moins 2 s, signature du drop (creux bref puis coup de basse fort -> rouge immédiat), silence > 1 s -> retour
  à l'écoute. Rien de tout ça n'a été testé sur le téléphone ni même en aperçu : seuils à régler avec Will
  (le diagnostic affiche « Moment : … (xx %) » = niveau / habituel).
  Idées batterie proposées, sans réponse : pause écran éteint, arrêt en économie d'énergie, mode 30 images/s.
  Will aimait aussi le style « Souffle » (les cartes de l'accueil qui respirent sur les basses) : pas encore codé.
- `Reglages.kt` : kill switch, « seulement au démarrage », réglages par app (durée, variantes).
- `MainActivity.kt` : écran principal (logo animé, interrupteurs, écran d'explication Store).
- `GuideDeblocage.kt` : guide plein écran pour l'accessibilité **grisée** (APK installé hors Play Store).
- `LogoAnime.kt` + `res/drawable/logo_avant.xml` (+ `logo_mono.xml` pour les icônes thématisées) : le logo
  « Étincelle halo » choisi par Will le 09/11 (triangle aux 3 côtés coupés au milieu, étoile à 4 branches en
  dégradé bleu/vert/jaune/rouge façon Play Store adoucie, halo jaune doux). Pistes sur le canevas Design
  https://claude.ai/artifact/LBzsAiPhrmc24HJDBLnHei . Réduit à 80 % pour tenir dans la zone sûre de l'icône.
- `Journal.kt` / `JournalActivity.kt` : journal de bord. **En Perso, il est envoyé dans un ticket
  GitHub du repo** : c'est la meilleure source pour déboguer (lisible via l'API GitHub publique,
  dernier commentaire du ticket le plus récent).
- `ModeStandard.kt` : prototype SANS accessibilité (journal d'utilisation Android). Testé :
  l'app est visible 0,2-0,3 s avant l'animation → gardé seulement comme mode de secours (Perso).
- `InterrupteurTile.kt` : le kill switch dans le volet des réglages rapides.

## Compiler et publier
- **Pas de SDK Android dans l'environnement de Claude** (dl.google.com et maven.google.com bloqués) :
  on ne peut pas compiler localement. On pousse, et **GitHub Actions** compile
  (`.github/workflows/build.yml`), puis publie la release `derniere-version` :
  `AnimLauncher-Perso.apk` et `AnimLauncher-Store.apk`.
- Vérifier une compilation : `https://api.github.com/repos/komekolabo/animluncher/actions/runs`.
- **Pousser** : `git push` est refusé dans cette session ; on passe par le connecteur GitHub
  (`push_files`), qui exige le **contenu complet** de chaque fichier. Après chaque envoi,
  faire un `git fetch` et un `diff` pour vérifier que GitHub a reçu exactement le bon fichier.
- Le connecteur **ne peut pas modifier `.github/workflows/`** : c'est Will qui le fait sur github.com.
- Branche de travail actuelle : `version-store` (pas encore fusionnée dans `main`).
- **Plusieurs instances de Claude travaillent parfois en même temps** sur ce repo. `push_files` REMPLACE
  des fichiers entiers : toujours `git fetch` juste avant d'envoyer, repartir de la version GitHub du
  fichier, et éviter qu'elles touchent au même fichier en même temps (surtout `Catalogue.kt` et ce CLAUDE.md).
- Envoyer dans UN SEUL `push_files` tous les fichiers qui dépendent les uns des autres : chaque envoi
  déclenche une compilation, et un envoi partiel échoue (arrivé le 09/11).
- Signature : clé debug publique (`app/debug.keystore.b64`). **À remplacer avant le Play Store.**

## Décisions déjà prises (et pourquoi)
- **Accessibilité gardée** pour le Store : c'est la seule méthode qui prévient AVANT l'ouverture
  de l'app. Version Store allégée + écran d'explication (« prominent disclosure ») exigé par Google.
  Ne jamais déclarer `isAccessibilityTool` (ce n'est pas un outil pour personnes handicapées).
- **Marques** : pas de logos, pas de noms de marques affichés, pas de copie d'interface.
  S'inspirer des couleurs/ambiances, c'est OK. La scène Maps a un habillage spécial Store.
- **Animations génériques** (apps hors catalogue) : deux séries proposées et rejetées le 09/11 (une douce,
  une façon Lamborghini). Plan de Will : plus tard, faire la liste des apps les plus téléchargées et leur
  faire une animation dédiée ; en dessous d'environ 200 000 téléchargements → animations génériques.
- **Déclaration d'accessibilité Google Play** : la règle demande d'utiliser une API plus ciblée « quand c'est
  possible ». Argument à donner : le journal d'utilisation (ModeStandard) a été testé et montre l'app
  0,2-0,3 s AVANT l'animation ; seule l'accessibilité prévient à temps. Vidéo obligatoire : ouverture de l'app
  → écran d'explication → consentement. Monétisation réaliste : surtout de l'argent de poche, sauf succès viral.
- **Alternatives étudiées et écartées** pour l'instant : launcher complet (trop long), raccourcis
  épinglés (pas d'animation depuis le tiroir), Shizuku / contrôleur d'activités (idée « mode expert »,
  mise de côté ; nécessite le Wi-Fi au démarrage).

## Pièges connus
- « Ouvrir directement la page d'un service d'accessibilité » (`ACCESSIBILITY_DETAILS_SETTINGS`)
  est **réservé aux apps système** : Samsung renvoie sur l'accueil de l'accessibilité.
- APK hors Play Store (Android 13+) : AnimLauncher apparaît **grisé**. Il faut d'abord **essayer
  de l'activer** (message « L'accès a été refusé à cette appli » = attendu), puis infos de l'app
  → ⋮ → « Autoriser les paramètres restreints ». L'étape 1 est obligatoire (testé).
- Un appui sur une **carte des apps récentes** arrive comme un clic sur l'accueil sans nom d'app :
  ne pas le prendre pour un widget (sinon l'animation arrive en retard).
- Samsung : les apps comme AnimLauncher sont dans Accessibilité → « Applications installées ».
- Mode « seulement au démarrage » : l'app est jugée « encore ouverte » si on l'a vue il y a moins de
  30 min. La version Store ne peut pas lire les apps récentes : elle ne voit pas les fermetures et
  compte sur le RATTRAPAGE (écran de démarrage < 300 ms après le clic = l'app était fermée).
  La liste « affiche toujours son écran de démarrage » (`splash_toujours_v2`) bloque ce rattrapage :
  elle ne doit retenir une app qu'avec prudence (Claude y avait été mis à tort).
- « FERMETURE POSSIBLE » (essayée le 09/11, RETIRÉE) : rejouer l'animation après tout passage
  apps récentes → accueil déclenchait l'animation presque à chaque fois. Ne pas refaire.
- **ANNONCES DE FERMETURE** (trouvées le 09/11) : quand on glisse une carte dans les apps récentes,
  le lanceur Samsung envoie un `TYPE_ANNOUNCEMENT` « application Instagram fermée » (et « Toutes les
  applications ont été fermées » pour « Tout fermer »). Reçu SANS lire l'écran → marche en Store.
  `AppWatcherService.annonce()` retire l'app de la liste des apps ouvertes. Textes en français :
  on cherche « ferm »/« closed » + un nom d'app connu.
- Mesure du démarrage (journal « .. mesure : ») : Instagram reprend en ~40 ms même après
  « Tout fermer » (processus encore vivant), ~500 ms quand Android l'avait vraiment tuée.
- Le journal envoyé sur GitHub est celui de la version **Perso** : si Will teste la version Store,
  le journal Perso ne montre aucune animation (interrupteur Perso éteint).

## Feuille de route
1. Tester la version Store (le clic sur les icônes doit marcher sans lecture d'écran).
   Fermeture d'une carte : réglée par les annonces (à confirmer en Store sur le téléphone).
   Reste : la fermeture en douce par Android (mesure du délai de démarrage, piste en cours).
2. Fusionner `version-store` dans `main`.
3. Vraie clé de signature (Secrets GitHub), fichier `.aab`, retirer `debug.keystore.b64`.
4. Compte Google Play, test fermé (~12 testeurs, 14 jours), politique de confidentialité,
   formulaire « sécurité des données », déclaration d'accessibilité + vidéo, fiche Store.
5. Pub non invasive : pub récompensée volontaire + achat Pro unique + consentement RGPD (UMP).
6. Plus tard : fonds d'écran communautaires (format « recette » de réglages, pas de code),
   mode expert Shizuku, éventuel vrai fond d'écran animé (`WallpaperService`).
