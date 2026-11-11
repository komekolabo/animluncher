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
