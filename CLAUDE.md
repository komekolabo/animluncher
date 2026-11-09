# AnimLauncher — notes pour Claude

App Android de Will (GitHub : komekolabo). Elle joue une **animation plein écran à l'ouverture
de chaque app** (Claude, Google, Duolingo…), sur son Samsung Galaxy Z Flip6 (Android 16).
Objectif : la publier sur le Google Play Store, avec une pub non invasive.

## Qui est Will (à respecter)
- **Débutant en code** : toujours expliquer ce que fait le code, en français simple.
  Les commentaires du code sont en français et pédagogiques (garder ce style).
- **Animations** : thèmes sombres, **jamais de flash lumineux**, mouvements doux.
- Avant d'intégrer une nouvelle animation : lui montrer une **vidéo d'aperçu VERTICALE (portrait) uniquement**.
  Aperçus faits avec skia-python (même moteur que le Canvas Android) + ffmpeg : un petit « moteur » Python
  qui imite `Outils.kt` (halo, tache, révélations…), écran 540 x 1320, vitesse réelle puis ralenti x0,5.
  Il ne vit pas dans le repo : à recréer si besoin (la session précédente l'avait dans /home/claude/apercu).
- **Les arcs / horizons / anneaux de planète en bas d'écran sont réservés aux animations de Claude.**
- **Varier les révélations** (pas toujours un cercle) : Outils propose cercle doux, balayage, bandes,
  trame de points, colonnes, tuiles, fente.
- Textes affichés dans les animations : **en anglais**.
- Il n'a **pas Android Studio** : tout se compile sur GitHub (voir plus bas). Il teste sur son téléphone.

## Les deux versions (Gradle « flavors », voir app/build.gradle.kts)
| | Perso | Store |
|---|---|---|
| Identifiant | `com.will.animlauncher` | `com.will.animlauncher.store` |
| Nom | AnimLauncher Perso | AnimLauncher |
| Logo | comète ambre | comète violette |
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
  chiffres : Benchmarks/ChatGPT, Autonomy/Claude, Power/Gemini, Intelligence/Grok).
  Les variantes d'une app sont tirées au hasard ; on ajoute les nouvelles À LA FIN de la liste
  (les réglages retiennent les variantes décochées par leur numéro).
  Chaque scène hérite de `Scene` et dessine **toute l'image à chaque instant** (pas d'état caché).
  Elles sont calibrées sur un écran « modèle » de 540 de large (k = largeur / 540).
- `Reglages.kt` : kill switch, « seulement au démarrage », réglages par app (durée, variantes).
- `MainActivity.kt` : écran principal (logo animé, interrupteurs, écran d'explication Store).
- `GuideDeblocage.kt` : guide plein écran pour l'accessibilité **grisée** (APK installé hors Play Store).
- `LogoAnime.kt` + `res/drawable/logo_avant.xml` : le logo (icône adaptative + version animée).
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
- Signature : clé debug publique (`app/debug.keystore.b64`). **À remplacer avant le Play Store.**

## Décisions déjà prises (et pourquoi)
- **Accessibilité gardée** pour le Store : c'est la seule méthode qui prévient AVANT l'ouverture
  de l'app. Version Store allégée + écran d'explication (« prominent disclosure ») exigé par Google.
  Ne jamais déclarer `isAccessibilityTool` (ce n'est pas un outil pour personnes handicapées).
- **Marques** : pas de logos, pas de noms de marques affichés, pas de copie d'interface.
  S'inspirer des couleurs/ambiances, c'est OK. La scène Maps a un habillage spécial Store.
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
- ESPION DES APPS RÉCENTES (expérience en cours, journal « [récentes] ») : on écoute les annonces
  (TYPE_ANNOUNCEMENT), changements de contenu et défilements de l'écran d'accueil pendant la visite
  des apps récentes, pour trouver un signal « telle carte a été fermée » utilisable SANS lire l'écran.
- Mesure du démarrage (journal « .. mesure : ») : Instagram reprend en ~40 ms même après
  « Tout fermer » (processus encore vivant), ~500 ms quand Android l'avait vraiment tuée.
- Le journal envoyé sur GitHub est celui de la version **Perso** : si Will teste la version Store,
  le journal Perso ne montre aucune animation (interrupteur Perso éteint).

## Feuille de route
1. Tester la version Store (le clic sur les icônes doit marcher sans lecture d'écran).
   Demandé par Will : **bien détecter quand une app se ferme** (glissement d'une seule carte,
   fermeture par Android), pour remplacer la « fermeture possible » provisoire.
2. Fusionner `version-store` dans `main`.
3. Vraie clé de signature (Secrets GitHub), fichier `.aab`, retirer `debug.keystore.b64`.
4. Compte Google Play, test fermé (~12 testeurs, 14 jours), politique de confidentialité,
   formulaire « sécurité des données », déclaration d'accessibilité + vidéo, fiche Store.
5. Pub non invasive : pub récompensée volontaire + achat Pro unique + consentement RGPD (UMP).
6. Plus tard : fonds d'écran communautaires (format « recette » de réglages, pas de code),
   mode expert Shizuku, éventuel vrai fond d'écran animé (`WallpaperService`).
