package com.will.animlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout

// Le service qui surveille quelle app passe au premier plan,
// et qui joue l'écran de chargement correspondant (voir Catalogue.kt).
class AppWatcherService : AccessibilityService() {

    private var derniereApp: String? = null
    private var seanceEnCours: Rideau.Seance? = null   // l'animation qui joue en ce moment
    private var appAnimee: String? = null      // l'app pour laquelle l'animation tourne
    private var appApparue = false             // la fenêtre de cette app est-elle déjà arrivée ?
    // LES APPS OUVERTES EN ARRIÈRE-PLAN : l'app -> la dernière fois qu'on l'a quittée.
    // Une app y entre quand elle s'affiche ; elle en sort quand on appuie sur « Tout fermer »
    // dans les apps récentes, ou quand on ne l'a plus vue depuis 30 min (Android l'a sans doute fermée).
    private val dejaOuvertes = HashMap<String, Long>()

    // Nom affiché sous l'icône -> nom technique de l'app (ex. "Claude" -> "com.anthropic.claude").
    private var noms: Map<String, String> = emptyMap()
    private var nomsCalculesA = 0L
    // Dernière app lancée par un clic (pour ne pas rejouer l'animation quand sa fenêtre arrive).
    private var appCliquee: String? = null
    private var clicA = 0L
    // Dernier appui (n'importe lequel) sur l'écran d'accueil, et en a-t-on déjà reçu ?
    private var dernierClicAccueil = 0L
    private var clicsRecus = false
    // Dernier appui sur l'accueil qui n'était ni une icône connue ni un widget reconnu.
    private var clicInconnuA = 0L
    // L'app qu'on vient de quitter (retour à l'accueil) et quand.
    private var appQuittee: String? = null
    private var quitteeA = 0L
    private val minuteur = Handler(Looper.getMainLooper())
    // ANTI-REBOND : quand l'animation a été jouée pour une app, on note l'heure. Pendant 10 s,
    // une nouvelle fenêtre de cette app ne la relance pas (certaines apps enchaînent plusieurs
    // écrans en s'ouvrant, et l'écran d'accueil glisse parfois un signal entre deux).
    // Un vrai nouvel appui sur l'icône, lui, relance toujours l'animation.
    private val animeeA = HashMap<String, Long>()

    private val ignorees = setOf(
        "com.samsung.android.spay",
        "com.samsung.android.honeyboard",
        "com.samsung.android.app.spage"
    )

    private val ecranAccueil: String? by lazy {
        val intention = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(intention, 0)?.activityInfo?.packageName
    }

    override fun onServiceConnected() {
        Journal.installerFilet(this)
        noter("=== Service démarré (allumage ou réactivation) ===")
        noms = calculerNoms()      // préparé à l'avance : le clic doit être traité instantanément
        installerRideau()          // la fenêtre des animations, prête d'avance (voir Rideau.kt)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Si quelque chose se passe mal pendant le traitement d'un signal, on l'écrit dans le
        // journal au lieu de laisser planter le service (un service planté = plus aucune animation).
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> { diagnostic(event); clic(event) }
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> fenetre(event)
                else -> diagnostic(event)
            }
        } catch (e: Exception) {
            noter("ERREUR (rattrapée) : " + Journal.pile(e))
        }
    }

    // MOUCHARD DES DOSSIERS : pendant 5 s après un appui sur l'écran d'accueil (par exemple
    // l'ouverture d'un dossier), on note dans le journal TOUS les signaux reçus, avec leur type
    // et leur texte (12 lignes au plus). Ça permet de voir ce que le lanceur Samsung envoie
    // quand on touche une icône À L'INTÉRIEUR d'un dossier.
    private var diagLignes = 0
    private fun diagnostic(event: AccessibilityEvent) {
        if (SystemClock.uptimeMillis() - dernierClicAccueil > 5000 || diagLignes >= 12) return
        // seulement les signaux de l'écran d'accueil (ou sans expéditeur) : on ne note jamais
        // ce qui se passe à l'intérieur des autres apps
        val expediteur = event.packageName?.toString()
        if (expediteur != null && expediteur != ecranAccueil) return
        val textes = event.text.map { it.toString() } + listOfNotNull(event.contentDescription?.toString())
        if (textes.all { it.isBlank() }) return
        diagLignes++
        noter("  [signal] ${AccessibilityEvent.eventTypeToString(event.eventType)} " +
            "de ${event.packageName ?: "(inconnu)"} « ${textes.joinToString(" / ")} »")
    }

    // 1) LE CLIC SUR L'ICÔNE. Le lanceur (l'écran d'accueil) nous dit sur quoi on a appuyé,
    // AVANT que l'app ne s'ouvre. On retrouve l'app grâce au nom écrit sous l'icône,
    // et l'animation démarre tout de suite : elle couvre l'ouverture de l'app.
    private fun clic(event: AccessibilityEvent) {
        // L'appui doit venir de l'écran d'accueil. Certains écrans (comme l'intérieur d'un dossier)
        // envoient un appui SANS dire de quelle app il vient : on l'accepte si l'écran d'accueil
        // est bien au premier plan.
        val expediteur = event.packageName?.toString()
        if (expediteur == null) {
            if (derniereApp != ecranAccueil) return
        } else if (expediteur != ecranAccueil) return
        clicsRecus = true
        if (SystemClock.uptimeMillis() - dernierClicAccueil > 5000) diagLignes = 0
        dernierClicAccueil = SystemClock.uptimeMillis()
        val textes = event.text.map { it.toString() } + listOfNotNull(event.contentDescription?.toString())
        var app = trouverApp(textes)
        var parWidget = false
        if (app == null) {
            // Pas une icône : c'est peut-être un WIDGET (voir appDuWidget).
            app = appDuWidget(event)
            parWidget = app != null
        }
        if (app == null) {
            // « Tout fermer » dans les apps récentes : plus aucune app n'est ouverte en arrière-plan.
            if (textes.any { val t = it.trim().lowercase(); t == "tout fermer" || t == "fermer tout" || t == "close all" }) {
                dejaOuvertes.clear()
                noter("« Tout fermer » : toutes les apps sont fermées")
                return
            }
            clicInconnuA = dernierClicAccueil
            noter("clic accueil « ${textes.joinToString(" / ")} » : pas une app connue")
            return
        }
        if (app in ignorees) return
        if (!Reglages.actif(this)) return
        if (Catalogue.pour(app) == null) return
        // Réglages de l'app : désactivée, durée 0 s, ou aucune variante cochée -> rien
        val lancement = Catalogue.lancement(this, app)
        if (lancement == null) { noter("clic -> $app : animation désactivée dans les réglages"); return }
        // Le même appui signalé deux fois : l'animation tourne déjà pour cette app, on ne la recommence pas.
        if (seanceEnCours != null && appAnimee == app) return
        // Mode « seulement au démarrage » (général ou réglé pour cette app) : si l'app est encore
        // ouverte en arrière-plan, on ne joue rien. Avant, on regardait si son écran de lancement
        // apparaissait, mais Gmail ou YouTube en affichent un à chaque fois, même en arrière-plan.
        // Un widget, lui, ouvre toujours l'app volontairement : l'animation est jouée dans tous les cas.
        if (!parWidget && Reglages.seulementDemarrage(this, app) && enArrierePlan(app)) {
            noter("clic sur « ${textes.firstOrNull() ?: "?"} » -> $app : déjà ouverte en arrière-plan, pas d'animation")
            return
        }
        appCliquee = app
        clicA = SystemClock.uptimeMillis()
        afficher(lancement, app, dejaPrete = false)   // d'abord l'animation, le journal après
        noter((if (parWidget) "clic sur un widget" else "clic sur « ${textes.firstOrNull() ?: "?"} »") + " -> $app : animation lancée")
    }

    // LES WIDGETS. Un widget est affiché par l'écran d'accueil, donc l'appui semble venir du
    // lanceur, et son texte ("12:30", "Lecture"…) n'est pas le nom d'une app. Mais chaque élément
    // d'un widget porte un identifiant qui commence par le nom technique de l'app qui l'a dessiné,
    // par exemple "com.spotify.music:id/bouton_lecture". On regarde donc l'élément touché, puis
    // ses parents (le cadre du widget), et on prend le premier identifiant qui vient d'une autre
    // app que le lanceur. On ne lit rien d'autre de l'écran.
    private fun appDuWidget(event: AccessibilityEvent): String? = try {
        chercherDansWidget(event)
    } catch (e: Exception) {
        noter("ERREUR widget (rattrapée) : " + Journal.pile(e))
        null
    }

    private fun chercherDansWidget(event: AccessibilityEvent): String? {
        var noeud = try { event.source } catch (_: Exception) { null } ?: return null
        val aVoir = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        // l'élément touché et ses parents…
        for (niveau in 0 until 10) {
            aVoir.add(noeud)
            noeud = noeud.parent ?: break
        }
        // …puis les enfants directs de l'élément touché (si lui-même n'a pas d'identifiant)
        val touche = aVoir.first()
        for (i in 0 until minOf(touche.childCount, 20)) touche.getChild(i)?.let { aVoir.add(it) }
        for (n in aVoir) {
            val id = n.viewIdResourceName ?: continue
            val pkg = id.substringBefore(":id/", "")
            if (pkg.isEmpty() || pkg == ecranAccueil || pkg == "android" || pkg == packageName) continue
            if (packageManager.getLaunchIntentForPackage(pkg) == null) continue
            return pkg
        }
        return null
    }

    private fun trouverApp(textes: List<String>): String? {
        chercher(textes)?.let { return it }
        // Pas trouvé (un dossier, un widget… ou une app installée depuis) :
        // on refait la liste, au plus une fois toutes les 30 secondes.
        val maintenant = SystemClock.uptimeMillis()
        if (maintenant - nomsCalculesA < 30_000) return null
        noms = calculerNoms()
        return chercher(textes)
    }

    private fun chercher(textes: List<String>): String? {
        for (t in textes) {
            val propre = t.trim()
            noms[propre]?.let { return it }
            // Samsung ajoute parfois des infos après une virgule ("Claude, 2 notifications")
            noms[propre.substringBefore(',').trim()]?.let { return it }
        }
        return null
    }

    // Toutes les apps de l'écran d'accueil : nom affiché -> nom technique.
    // Si deux apps ont le même nom (ex. deux « Wallet »), on garde celle qui a une animation.
    private fun calculerNoms(): Map<String, String> {
        val intention = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        nomsCalculesA = SystemClock.uptimeMillis()
        val resultat = HashMap<String, String>()
        for (info in packageManager.queryIntentActivities(intention, 0)) {
            val nom = info.loadLabel(packageManager).toString().trim()
            val pkg = info.activityInfo.packageName
            if (resultat[nom] == null || Catalogue.pour(pkg) != null) resultat[nom] = pkg
        }
        return resultat
    }

    // 2) LES CHANGEMENTS DE FENÊTRE : l'app qui passe au premier plan.
    private fun fenetre(event: AccessibilityEvent) {
        val app = event.packageName?.toString() ?: return
        if (app == packageName || app in ignorees) return
        val classe = event.className?.toString()?.substringAfterLast('.') ?: "?"

        // L'APP EST-ELLE PRÊTE ? Dès que le vrai écran de l'app arrive (autre chose que
        // l'écran de lancement FrameLayout), on lance la révélation. Si l'utilisateur part
        // ailleurs après l'arrivée de l'app, on révèle aussi. Avant l'arrivée de l'app, on
        // ignore les autres fenêtres (le lanceur bouge encore pendant le clic).
        val estVraieApp = app == ecranAccueil ||
            packageManager.getLaunchIntentForPackage(app) != null
        seanceEnCours?.let { vue ->
            // RELAIS : certaines icônes ouvrent en fait une autre app (ex. Gemini s'ouvre
            // dans l'app Google). Si une autre vraie app arrive avant celle attendue, on la suit.
            if (!appApparue && app != appAnimee && app != ecranAccueil && estVraieApp) {
                noter("  .. relais : $appAnimee -> $app")
                appAnimee = app
                animeeA[app] = SystemClock.uptimeMillis()
            }
            if (seanceEnCours === vue && app == appAnimee) {
                appApparue = true
                if (classe != "FrameLayout") {
                    vue.signalerPret()
                    noter("  .. app prête après ${SystemClock.uptimeMillis() - vue.creeeA} ms (écran $classe)")
                }
            } else if (appApparue) {
                vue.signalerPret()
            }
        }

        // LES APPS RÉCENTES : quand on les ouvre, on regarde régulièrement quelles apps y sont.
        // Une app qui disparaît de la liste pendant la visite a été fermée (glissée vers le haut).
        if (app == ecranAccueil && classe == "RecentsActivity") debutRecents()
        else if (dansRecents && ((app == ecranAccueil && classe == "Launcher") || (app != ecranAccueil && estVraieApp))) finRecents()

        if (!estVraieApp) return

        if (app == derniereApp) return
        val precedente = derniereApp
        derniereApp = app
        // l'app qu'on quitte : on note l'heure (elle reste ouverte en arrière-plan)
        if (precedente != null && precedente != ecranAccueil) dejaOuvertes[precedente] = SystemClock.uptimeMillis()
        val etaitOuverte = app != ecranAccueil && enArrierePlan(app)
        if (app != ecranAccueil) dejaOuvertes[app] = SystemClock.uptimeMillis()

        // L'animation tourne déjà pour cette app (lancée au clic) : rien de plus à faire.
        if ((seanceEnCours != null && appAnimee == app) ||
            (app == appCliquee && SystemClock.uptimeMillis() - clicA < 6000)) {
            noter("$app | écran $classe | animation déjà lancée au clic")
            return
        }

        if (app == ecranAccueil) {
            noter("$app | écran $classe")
            // On est revenu sur l'accueil après avoir vu l'app cliquée : ce clic-là est terminé.
            // (Sinon, rouvrir la même app dans les 6 s suivantes était pris pour « le même clic ».)
            if (precedente == appCliquee) appCliquee = null
            appQuittee = precedente
            quitteeA = SystemClock.uptimeMillis()
            return
        }

        // FrameLayout = écran de lancement = l'app DÉMARRE.
        val demarrage = classe == "FrameLayout"
        noter("$app | écran $classe | " + if (demarrage) "DÉMARRAGE" else "reprise")

        if (!Reglages.actif(this)) return
        val lancement = Catalogue.lancement(this, app) ?: return
        if (precedente != ecranAccueil) return
        // Un appui sur l'accueil qui n'était pas une icône (sans doute un widget qu'on n'a pas su
        // reconnaître) juste avant que l'app arrive : l'utilisateur l'a ouverte exprès -> animation,
        // même si elle était déjà ouverte en arrière-plan.
        val apresWidget = clicInconnuA == dernierClicAccueil && SystemClock.uptimeMillis() - clicInconnuA < 2500
        if (apresWidget) noter("  (ouverte depuis un widget)")
        if (Reglages.seulementDemarrage(this, app) && !apresWidget && (!demarrage || etaitOuverte)) return
        // Une app peut faire apparaître une fenêtre sans qu'on l'ait ouverte (mini-lecteur
        // de YouTube quand on la quitte, etc.). On n'anime donc que si on vient d'appuyer
        // sur l'écran d'accueil (moins de 2 s). Si le téléphone ne nous signale jamais
        // les appuis, on garde l'ancien comportement.
        if (clicsRecus && SystemClock.uptimeMillis() - dernierClicAccueil > 2000) {
            // SAUF pour certains WIDGETS (ex. celui de Strike), qui n'envoient aucun signal d'appui :
            // si l'app DÉMARRE vraiment (écran de lancement) depuis l'accueil, on joue l'animation,
            // à condition que ce ne soit pas l'app qu'on vient juste de quitter (mini-lecteur…).
            val justeQuittee = app == appQuittee && SystemClock.uptimeMillis() - quitteeA < 1500
            if (demarrage && !justeQuittee) {
                noter("  (pas d'appui signalé, mais l'app démarre : sans doute un widget)")
            } else {
                noter("  (pas d'appui sur l'accueil juste avant : on ne lance rien)")
                return
            }
        }
        // Sans information d'appui : une fenêtre de l'app qu'on vient de quitter, dans les
        // 5 secondes, est presque toujours un reste de sa fermeture (mini-lecteur…), pas une ouverture.
        if (!clicsRecus && app == appQuittee && SystemClock.uptimeMillis() - quitteeA < 5000) {
            noter("  (fenêtre de l'app qu'on vient de quitter : on ne lance rien)")
            return
        }

        // Anti-rebond : cette app vient déjà d'avoir son animation (moins de 10 s) -> on ne la rejoue pas…
        // SAUF si l'utilisateur est vraiment revenu sur l'écran d'accueil entre-temps : soit il y est
        // resté plus de 0,7 s, soit il y a appuyé sur quelque chose (une icône, un dossier).
        // (Le « faux » passage par l'accueil que l'anti-rebond doit filtrer, lui, dure une fraction de seconde.)
        animeeA[app]?.let { avant ->
            val maintenant = SystemClock.uptimeMillis()
            val ecart = maintenant - avant
            val resteSurAccueil = quitteeA > avant && maintenant - quitteeA > 700
            val appuiDepuis = dernierClicAccueil > avant
            if (ecart < 10_000 && !resteSurAccueil && !appuiDepuis) {
                noter("  (animation déjà jouée il y a $ecart ms : on ne la relance pas)")
                return
            }
        }

        // Si c'est une simple reprise, l'app est déjà prête : version courte.
        afficher(lancement, app, dejaPrete = !demarrage)
        appApparue = true      // ici, la fenêtre de l'app est déjà là
    }

    // LE RIDEAU (voir Rideau.kt) : la fenêtre des animations, installée une fois pour toutes,
    // transparente et invisible (alpha 0) quand elle ne sert pas.
    private var rideau: Rideau? = null
    private var cadreRideau: FrameLayout? = null
    private var reglagesRideau: WindowManager.LayoutParams? = null

    private fun installerRideau() {
        if (rideau != null) return
        val gestionnaire = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val taille = tailleEcran()
        val reglages = WindowManager.LayoutParams(
            taille.x, taille.y,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            // Au tout début, alpha 1 (le rideau est transparent, donc invisible quand même) :
            // Android ne crée la surface de dessin que si la fenêtre est « visible ». Une fois la
            // surface prête, on passe à alpha 0 (voir plus bas) ; elle reste prête ensuite.
            alpha = 1f
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        // Le rideau est posé dans un CADRE (sans cadre, Android 16 pouvait planter :
        // « requestTransparentRegion on a null object »).
        val r = Rideau(this)
        val cadre = FrameLayout(this)
        cadre.addView(r, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        try {
            gestionnaire.addView(cadre, reglages)
            rideau = r
            cadreRideau = cadre
            reglagesRideau = reglages
            minuteur.postDelayed({ if (seanceEnCours == null) montrerRideau(false) }, 2000)
        } catch (e: Exception) {
            noter("ERREUR installation du rideau : ${e.message}")
        }
    }

    // Rend le rideau visible (1) ou invisible (0).
    private fun montrerRideau(visible: Boolean) {
        val cadre = cadreRideau ?: return
        val reglages = reglagesRideau ?: return
        val a = if (visible) 1f else 0f
        if (reglages.alpha == a) return
        reglages.alpha = a
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(cadre, reglages)
        } catch (e: Exception) {
            noter("ERREUR rideau : ${e.message}")
        }
    }

    private fun afficher(lancement: Lancement, app: String, dejaPrete: Boolean) {
        retirer("nouvelle animation", cacher = false)
        installerRideau()
        val r = rideau ?: return
        ajusterTaille()
        val seance = Rideau.Seance(lancement.scene, lancement.categorie, lancement.facteur) { fini ->
            if (seanceEnCours === fini) retirer("fin normale")
        }
        seanceEnCours = seance
        appAnimee = app
        animeeA[app] = SystemClock.uptimeMillis()
        appApparue = false
        if (dejaPrete) seance.signalerPret()
        r.jouer(seance)          // d'abord on lance le dessin…
        montrerRideau(true)      // …puis on rend le rideau visible
        if (!r.surfacePrete) noter("  (le rideau n'était pas encore prêt)")
        // au cas où l'écran tournerait sans prévenir (ex. une app qui s'ouvre à l'horizontale)
        for (delai in longArrayOf(300L, 700L, 1200L)) minuteur.postDelayed({ if (seanceEnCours === seance) ajusterTaille() }, delai)
        // Filet de sécurité : attente max + révélation + une marge d'une seconde.
        minuteur.postDelayed({
            if (seanceEnCours === seance) retirer("filet de sécurité")
        }, seance.dureeMax + 1000)
    }

    // Arrête l'animation en cours ; « cacher » = rendre le rideau invisible (non quand une autre suit).
    private fun retirer(raison: String, cacher: Boolean = true) {
        val seance = seanceEnCours ?: return
        seance.arreter()
        seanceEnCours = null
        appAnimee = null
        appApparue = false
        if (cacher) montrerRideau(false)
        val ms = SystemClock.uptimeMillis() - seance.creeeA
        val premiere = if (seance.premiereImageA > 0) "1re image après ${seance.premiereImageA - seance.creeeA} ms"
                       else "aucune image"
        noter("  -> ${seance.images} images, $premiere, $ms ms, $raison")
    }

    // Une ligne dans le journal (voir Journal.kt), qui partira ensuite sur GitHub.
    private fun noter(texte: String) {
        Journal.noter(this, texte)
        Journal.programmerEnvoi(this)
    }

    // L'ÉCRAN TOURNE (ex. Balatro, qui ne s'affiche qu'à l'horizontale) : l'animation avait été
    // créée à la taille de l'écran vertical, elle ne couvrait plus que la moitié de l'écran.
    // On la redimensionne à la nouvelle taille de l'écran.
    override fun onConfigurationChanged(nouvelle: Configuration) {
        super.onConfigurationChanged(nouvelle)
        ajusterTaille()
    }

    private fun ajusterTaille() {
        val cadre = cadreRideau ?: return
        val reglages = reglagesRideau ?: return
        val taille = tailleEcran()
        if (reglages.width == taille.x && reglages.height == taille.y) return
        reglages.width = taille.x
        reglages.height = taille.y
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(cadre, reglages)
            noter("  .. écran tourné : animation redimensionnée en ${taille.x} x ${taille.y}")
        } catch (e: Exception) {
            noter("ERREUR redimensionnement : ${e.message}")
        }
    }

    // La VRAIE taille de l'écran, barres et encoche comprises (dans le sens actuel du téléphone).
    private fun tailleEcran(): Point {
        val ecran = (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY)
        val taille = Point()
        @Suppress("DEPRECATION") ecran.getRealSize(taille)
        return taille
    }

    // ---------------- LES APPS RÉCENTES ----------------
    private var dansRecents = false
    private val vusRecents = HashSet<String>()      // toutes les apps vues pendant cette visite
    private var dernierReleve: Set<String> = emptySet()

    private fun debutRecents() {
        if (dansRecents) return
        dansRecents = true
        vusRecents.clear()
        dernierReleve = emptySet()
        minuteur.postDelayed(object : Runnable {
            override fun run() {
                if (!dansRecents) return
                releverRecents()
                minuteur.postDelayed(this, 250)
            }
        }, 150)
    }

    // Lit les noms des apps affichées dans l'écran des apps récentes (rien d'autre).
    private fun releverRecents() {
        val racine = try { rootInActiveWindow } catch (_: Exception) { null } ?: return
        if (racine.packageName?.toString() != ecranAccueil) return
        val trouvees = HashSet<String>()
        // Pour être sûr de lire l'écran des apps récentes (et pas l'écran d'accueil, qui affiche
        // lui aussi des noms d'apps), on cherche son bouton « Tout fermer », ou le message
        // « Aucune application récente » quand la liste est vide.
        var boutonFermer = false
        var listeVide = false
        val pile = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        pile.add(racine)
        var vus = 0
        while (pile.isNotEmpty() && vus < 400) {
            val n = pile.removeLast()
            vus++
            val textes = listOfNotNull(n.text?.toString(), n.contentDescription?.toString())
            for (t in textes) {
                val m = t.trim().lowercase()
                if (m == "tout fermer" || m == "fermer tout" || m == "close all") boutonFermer = true
                if (m.contains("aucune application récente") || m.contains("no recent")) listeVide = true
            }
            if (textes.isNotEmpty()) chercher(textes)?.let { trouvees.add(it) }
            for (i in 0 until n.childCount) n.getChild(i)?.let { pile.add(it) }
        }
        if (listeVide) trouvees.clear()
        else if (!boutonFermer) return          // ce n'est pas (ou plus) l'écran des apps récentes
        vusRecents.addAll(trouvees)
        dernierReleve = trouvees
    }

    private fun finRecents() {
        dansRecents = false
        // les apps vues pendant la visite mais absentes du dernier relevé ont été fermées
        val fermees = vusRecents - dernierReleve
        for (app in fermees) dejaOuvertes.remove(app)
        noter("  (apps récentes : ${vusRecents.size} app(s) vue(s)" +
            (if (fermees.isNotEmpty()) ", fermée(s) : ${fermees.joinToString()})" else ", aucune fermée)"))
        vusRecents.clear()
    }

    // L'app est-elle encore ouverte en arrière-plan ? (vue il y a moins de 30 min, sans « Tout fermer »)
    private fun enArrierePlan(app: String): Boolean {
        val vue = dejaOuvertes[app] ?: return false
        return SystemClock.uptimeMillis() - vue < 30 * 60_000L
    }

    override fun onInterrupt() {}

    companion object {
        const val CARNET = Journal.CARNET
        const val CLE_LISTE = Journal.CLE_LISTE
    }
}
