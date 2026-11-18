# AnimLauncher — notes complémentaires (lues en plus de CLAUDE.md)

Ce fichier évite de réécrire le gros CLAUDE.md à chaque ajout (le connecteur GitHub exige le contenu complet
de chaque fichier envoyé). Au prochain passage tranquille, ces notes pourront être fusionnées dans CLAUDE.md.

## FONDS D'ÉCRAN ANIMÉS (12/11, étape 1 — PAS ENCORE TESTÉ PAR WILL)
- Fichiers : `FondsAnimes.kt` (dessins `DessinFond` Nuit/Galets = les fonds des cartes 3 et 2 de l'accueil,
  déjà vus par Will ; `CarnetFond` = carnet « fond » ; fichiers importés dans filesDir/fonds),
  `FondService.kt` (DEUX WallpaperService : `FondDessinService` = dessins, GIF, images, dessinés avec
  lockHardwareCanvas + Choreographer ; `FondVideoService` = MediaPlayer branché DIRECTEMENT sur la surface du
  fond, le plus économe), `FondsActivity.kt` (galerie 3 colonnes + import), `FondApercuActivity.kt` (aperçu
  plein écran + fausse horloge/icônes + feuille de réglages + Appliquer). Textes : `res/values*/strings_fonds.xml`.
- Déclarations : dans `src/store/AndroidManifest.xml` ET `src/perso/AndroidManifest.xml` (même bloc), pour ne pas
  toucher au gros manifeste commun. Fiches Android : `res/xml/fond_dessin.xml`, `fond_video.xml`.
- POURQUOI 2 SERVICES : une surface déjà dessinée au Canvas ne peut plus être donnée au lecteur vidéo
  (« already connected »). Chaque service a ses cases (« dessin_* » / « video_* ») dans le carnet : annuler
  l'écran d'Android ne change pas le fond actuel. Même service déjà actif -> changement INSTANTANÉ (écouteur du
  carnet), sinon `ACTION_CHANGE_LIVE_WALLPAPER` (confirmation d'Android obligatoire).
- Économies : rien quand caché (onVisibilityChanged), Éco 30 i/s par défaut (Fluide 60), option « figer après
  10 s sans toucher l'accueil » (ralentit sur 1,5 s, repart au toucher / changement de page), image fixe dessinée
  une seule fois. `GardienBatterie` : économie d'énergie ou THERMAL_STATUS_SEVERE -> RALENTIR (dessins à 15 i/s,
  vidéo continue) ; seul THERMAL_STATUS_CRITICAL fige.
- 12/11 00h30 : Will a vu les 3 fonds (Nuit, Galets, sa vidéo) TOUJOURS FIGÉS sur l'accueil. Cause la plus
  probable : l'économie d'énergie figeait tout (corrigé ci-dessus). Ajouté aussi : un SECOURS si Android n'envoie
  jamais onVisibilityChanged (après 1,5 s, visible = écran allumé), et un DIAGNOSTIC : chaque moteur note dans le
  carnet « fond_diag » (visible ✓/✗, signal reçu, images dessinées, état du lecteur vidéo, économie, chaleur,
  erreur de dessin) ; affiché en bas de l'écran Fonds d'écran (texte copiable). Demander à Will de le recopier.
- Import (sélecteur de photos, sans permission) : vidéo = piste vidéo seule recopiée par MediaExtractor/MediaMuxer
  (SON RETIRÉ, sinon `isMusicActive` allumerait la réaction sonore) ; image réduite à l'écran (JPEG) ; GIF copié et
  lu avec `Movie` peint dans une petite image puis agrandie. Appui long sur un fond importé = le supprimer.
- L'aperçu retire dans `onPostCreate` l'écart « sous les barres » posé par AnimApp (le fond doit couvrir l'écran).
- Personnages sous licence (ex. 2B de NieR) : seulement par import perso, JAMAIS dans les fonds de l'app ni la fiche Store.
- ÉTAPE 2 prévue : écran de cadrage (zoom/position, zones horloge/icônes, début/fin de boucle avec fondu),
  conversion GIF -> MP4, nouveaux fonds de l'app (aperçus vidéo verticaux à montrer à Will avant),
  fond accueil ≠ verrouillage (WallpaperDescription d'Android 16). Samsung : vérifier l'écran de verrouillage.

## Vérifier le Kotlin sans SDK Android (trouvé le 12/11)
- `android.jar` d'Android 36 : https://raw.githubusercontent.com/Reginer/aosp-android-jar/main/android-36/android.jar
  (raw.githubusercontent.com passe ; github.com/.../raw et Maven Central sont bloqués).
- kotlinc 2.0.20 : https://github.com/JetBrains/kotlin/releases/download/v2.0.20/kotlin-compiler-2.0.20.zip
- Générer de faux `R` et `BuildConfig` (objets Kotlin avec des `const val … = 0`) à partir des noms trouvés dans
  res/, puis : `kotlinc -J-Xmx4g -cp android.jar -jvm-target 17 -nowarn -d out stubs.kt $(find app/src/main/java -name '*.kt')`
  (~50 s). Ne vérifie QUE le Kotlin : ni les ressources, ni le manifeste.

## AMÉLIORER UNE IMAGE (18/11 — PAS ENCORE TESTÉ PAR WILL SUR LE TÉLÉPHONE)
- Demande de Will : un « dépixeliseur » local, optionnel, pour les photos importées comme fonds d'écran.
- `AmeliorationImage.kt` : `AmeliorationImage` (outil + calcul par carreaux de 192 avec 16 de marge, x4) et
  `AmeliorationUi` (la ligne « Netteté : Originale / Améliorée » ajoutée dans la feuille de `FondApercuActivity`,
  seulement pour les images). Textes : `res/values*/strings_net.xml` (6 langues).
- Modèle : Real-ESRGAN `realesr-general-x4v3` (BSD 3 clauses), converti en TFLite poids 16 bits = 2,5 Mo
  (`outils/convertir_amelioration.py` + `outils/lire_pth.py`, sans PyTorch : download.pytorch.org est bloqué).
  Entrée [1,192,192,3] float 0..1, sortie [1,768,768,3] bornée 0..1. Téléchargé à la demande depuis la branche
  `modeles` (`amelioration_photos_v1.tflite`, taille exacte vérifiée : 2 480 168 octets). Même moteur que
  `DetourageDessins` (TFLite des services Google, 4 fils).
- UN SEUL modèle : le modèle « anime » (`realesr-animevideov3`) a été essayé, le général fait aussi bien sur les
  dessins, et aucun test simple ne distingue photo et dessin de façon fiable (aplats / contours : essayé, raté).
- Fichiers : l'originale `X.jpg` n'est jamais modifiée ; l'améliorée = `X_net.jpg` à côté. `CarnetFond.importes`
  cache l'originale quand l'améliorée existe ; supprimer l'améliorée (appui long) fait réapparaître l'originale.
- Taille de travail : au plus 40 % du grand côté de l'écran en entrée (résultat ramené à l'écran). Vitesse mesurée
  seulement sur le serveur de Claude (0,5 s par carreau sur 2 cœurs) : À MESURER sur le téléphone de Will.
- Le connecteur GitHub ne sait pas envoyer un fichier binaire : c'est Will qui dépose le `.tflite` dans la branche
  `modeles` (github.com → branche modeles → Add file → Upload files). Tant qu'il n'y est pas : « Téléchargement impossible ».
- Pas fait : fonds 3D, dossier de fonds, GIF / vidéos ; bouton pour supprimer l'outil (2,5 Mo seulement).

## OUVERTURES DES DOSSIERS TIRÉES AU HASARD (18/11 — PAS ENCORE TESTÉ PAR WILL SUR LE TÉLÉPHONE)
- Les dossiers sans transition à eux (tous sauf Maps, Health, Photos, Finance) tirent à chaque ouverture entre la
  poussière d'origine, ORBITE et CARTES (`OuverturesDossier.kt`), par `Reglages.tirerVariante(c, "ouverture_dossier", 3, …)`.
- Orbite : l'anneau d'icônes sort du widget (anneau de poussière en comète), les apps le quittent une à une.
  Cartes : le logo distribue des cartes de verre fumé, une vague les retourne (perspective = `skew`, reflet doux).
- Hooks ajoutés à `EffetOuverture` : `panneauDuCentre`, `horsCadre` (apps dessinées hors du cadre),
  `dessinerDos`, `surIcone` ; `Arrivee` a `decalX`, `largeur`, `cisaille`, `dos`, `flot` ; `CadreDossier` a `departX/Y`.
- Aperçu (JavaScript dans Chrome + ffmpeg, pas skia) : /home/claude/apercu_dossier/propositions.html (pas dans le repo).
  Will a vu et validé la v2 d'Orbite et de Cartes ; « Goutte » (liquide) proposée, non retenue.
- `git push` marche dans cette session une fois le dépôt ajouté (add_repo) : plus besoin de push_files ici.
- Compilation en échec « 429 Too Many Requests » sur repo.maven.apache.org = Maven surchargé : relancer (`gh run rerun`).
- (18/11, suite) WIDGETS DOSSIER : 2 nouvelles boucles dans `WidgetDossier.Boucle`, CARROUSEL (logo en petit au centre,
  apps sur un anneau incliné en 3D) et MOSAÏQUE (logo fendu en tuiles qui se retournent en vague, reflet). Chaque
  dossier tire son style (`tirerVariante(c, "widget_<groupe>", 2, …)`) puis ALTERNE au plus toutes les 30 min
  (à l'arrivée sur l'accueil). L'ancienne boucle reste (style ANCIENNE), non tirée. Aperçu : /home/claude/apercu_dossier/widgets.html.
  La lueur du logo (`dossier_lueur_anime.xml`) suit encore l'ancien rythme : à recaler si Will la trouve décalée.
- (18/11, suite 2) WIDGETS « GRANDES ICÔNES » : boîte 54 dp (`COTE_DP`, layout `widget_dossier.xml` marges 4 / 2,76 dp),
  Carrousel = 4 apps max sur l'anneau (une app remplacée par la suivante quand elle passe au fond), Mosaïque = tuiles 2 x 2
  dont les retournements servent de PAGES. `gesteVisible` : logo au repos COMPLET après son geste (sinon Finance/Maps/Health/
  News se vidaient). INFOS UTILES : pastille « apps ouvertes » et rond « dernière app », vues à part mises à jour par
  `partiallyUpdateAppWidget` (`mettreAJourInfos`, à l'arrivée sur l'accueil). Will n'a pas encore vu ça sur le téléphone.
- ⚠️ 18/11 : un envoi `push_files` d'une autre session (polices) a ÉCRASÉ WidgetDossier.kt avec une vieille copie ; rétabli
  (41f9429). Avant d'envoyer un fichier entier, TOUJOURS repartir de la version GitHub du moment (git fetch juste avant).

## STYLE AUTO (18/11 — PAS ENCORE TESTÉ PAR WILL SUR LE TÉLÉPHONE)
- Demande de Will : une option bien visible pour habiller tout le téléphone d'un style beau et cohérent, d'après
  ses apps. Maquette validée : canevas Design https://claude.ai/artifact/L3zhTGzM4aqeq1M1ToVjDc (3 écrans).
- Bouton « Style auto » sous le titre de l'accueil (`MainActivity.banniereStyle`, anneau `AnneauStyle` aux couleurs
  des dossiers). Écrans : `StyleAutoActivity.kt` (ANALYSE → CHOIX avec aperçu `ApercuStyle` → RECAP avec interrupteurs
  → FINI avec un bouton « Poser » par dossier, car Android montre sa fenêtre pour chaque widget).
  Textes : `res/values*/strings_style.xml` (6 langues).
- Moteur : `StyleAuto.kt`. Analyse = histogramme des teintes OKLCH des icônes (32 x 32, pixels pondérés par leur
  intensité, 24 bacs de 15°) → teinte de chaque dossier + dominante du téléphone. 5 styles : Assorti (teintes des
  icônes, écartées d'au moins 24°), Dégradé (dominante −55° → +65°), Monochrome, Nuit néon (dominante / +150°),
  Coucher de soleil (22° → 77°). `StyleAuto.roue()` retrouve l'angle HSV de la roue qui donne la teinte OKLCH voulue.
- Applique : couleurs des dossiers (`Groupes.definirCouleur`), 3 teintes de la réaction sonore, fond Nuit/Galets
  TEINTÉ (nouvelle palette `CarnetFond.CLE_PALETTE` = « dessin_palette », lue par `FondNuit(palette)` /
  `FondGalets(palette)`), animations des apps compatibles. SAUVEGARDE avant (carnet « style_auto ») : « Annuler ce
  style » remet couleur_*, teinte_*, dessin_*, animations_actives et off_*. Limite : si le fond du téléphone n'était
  pas le nôtre, l'annulation ne remet pas l'ancien fond d'Android.
