package com.will.animlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
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
import android.view.accessibility.AccessibilityEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Le service qui surveille quelle app passe au premier plan,
// et qui joue l'écran de chargement correspondant (voir Catalogue.kt).
class AppWatcherService : AccessibilityService() {

    private var derniereApp: String? = null
    private var vueEnCours: AnimationView? = null
    private var appAnimee: String? = null      // l'app pour laquelle l'animation tourne
    private var appApparue = false             // la fenêtre de cette app est-elle déjà arrivée ?
    private val dejaOuvertes = HashSet<String>()   // apps déjà vues depuis le démarrage du service

    // Nom affiché sous l'icône -> nom technique de l'app (ex. "Claude" -> "com.anthropic.claude").
    private var noms: Map<String, String> = emptyMap()
    private var nomsCalculesA = 0L
    // Dernière app lancée par un clic (pour ne pas rejouer l'animation quand sa fenêtre arrive).
    private var appCliquee: String? = null
    private var clicA = 0L
    // Dernier appui (n'importe lequel) sur l'écran d'accueil, et en a-t-on déjà reçu ?
    private var dernierClicAccueil = 0L
    private var clicsRecus = false
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
        noter("=== Service démarré (allumage ou réactivation) ===")
        noms = calculerNoms()      // préparé à l'avance : le clic doit être traité instantanément
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> clic(event)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> fenetre(event)
        }
    }

    // 1) LE CLIC SUR L'ICÔNE. Le lanceur (l'écran d'accueil) nous dit sur quoi on a appuyé,
    // AVANT que l'app ne s'ouvre. On retrouve l'app grâce au nom écrit sous l'icône,
    // et l'animation démarre tout de suite : elle couvre l'ouverture de l'app.
    private fun clic(event: AccessibilityEvent) {
        if (event.packageName?.toString() != ecranAccueil) return
        clicsRecus = true
        dernierClicAccueil = SystemClock.uptimeMillis()
        val textes = event.text.map { it.toString() } + listOfNotNull(event.contentDescription?.toString())
        val app = trouverApp(textes)
        if (app == null) {
            noter("clic accueil « ${textes.joinToString(" / ")} » : pas une app connue")
            return
        }
        if (app in ignorees) return
        if (!Reglages.actif(this)) return
        if (Catalogue.pour(app) == null) return
        // Réglages de l'app : désactivée, durée 0 s, ou aucune variante cochée -> rien
        val lancement = Catalogue.lancement(this, app)
        if (lancement == null) { noter("clic -> $app : animation désactivée dans les réglages"); return }
        // Mode « seulement au démarrage » : si l'app a déjà été ouverte, c'est sûrement
        // une reprise -> on laisse l'ancienne méthode décider (elle ignore les reprises).
        if (Reglages.seulementDemarrage(this) && app in dejaOuvertes) return
        // Le même appui signalé deux fois : l'animation tourne déjà pour cette app, on ne la recommence pas.
        if (vueEnCours != null && appAnimee == app) return
        appCliquee = app
        clicA = SystemClock.uptimeMillis()
        afficher(lancement, app, dejaPrete = false)      // d'abord l'animation, le journal après
        noter("clic sur « ${textes.firstOrNull() ?: "?"} » -> $app : animation lancée")
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
        vueEnCours?.let { vue ->
            // RELAIS : certaines icônes ouvrent en fait une autre app (ex. Gemini s'ouvre
            // dans l'app Google). Si une autre vraie app arrive avant celle attendue, on la suit.
            if (!appApparue && app != appAnimee && app != ecranAccueil && estVraieApp) {
                noter("  .. relais : $appAnimee -> $app")
                appAnimee = app
                animeeA[app] = SystemClock.uptimeMillis()
            }
            if (app == appAnimee) {
                appApparue = true
                if (classe != "FrameLayout") {
                    vue.signalerPret()
                    noter("  .. app prête après ${SystemClock.uptimeMillis() - vue.creeeA} ms (écran $classe)")
                }
            } else if (appApparue) {
                vue.signalerPret()
            }
        }

        if (!estVraieApp) return

        if (app == derniereApp) return
        val precedente = derniereApp
        derniereApp = app
        if (app != ecranAccueil) dejaOuvertes.add(app)

        // L'animation tourne déjà pour cette app (lancée au clic) : rien de plus à faire.
        if ((vueEnCours != null && appAnimee == app) ||
            (app == appCliquee && SystemClock.uptimeMillis() - clicA < 6000)) {
            noter("$app | écran $classe | animation déjà lancée au clic")
            return
        }

        if (app == ecranAccueil) {
            noter(app)
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
        if (Reglages.seulementDemarrage(this) && !demarrage) return
        // Une app peut faire apparaître une fenêtre sans qu'on l'ait ouverte (mini-lecteur
        // de YouTube quand on la quitte, etc.). On n'anime donc que si on vient d'appuyer
        // sur l'écran d'accueil (moins de 2 s). Si le téléphone ne nous signale jamais
        // les appuis, on garde l'ancien comportement.
        if (clicsRecus && SystemClock.uptimeMillis() - dernierClicAccueil > 2000) {
            noter("  (pas d'appui sur l'accueil juste avant : on ne lance rien)")
            return
        }
        // Sans information d'appui : une fenêtre de l'app qu'on vient de quitter, dans les
        // 5 secondes, est presque toujours un reste de sa fermeture (mini-lecteur…), pas une ouverture.
        if (!clicsRecus && app == appQuittee && SystemClock.uptimeMillis() - quitteeA < 5000) {
            noter("  (fenêtre de l'app qu'on vient de quitter : on ne lance rien)")
            return
        }

        // Anti-rebond : cette app vient déjà d'avoir son animation (moins de 10 s) -> on ne la rejoue pas.
        animeeA[app]?.let { avant ->
            val ecart = SystemClock.uptimeMillis() - avant
            if (ecart < 10_000) {
                noter("  (animation déjà jouée il y a $ecart ms : on ne la relance pas)")
                return
            }
        }

        // Si c'est une simple reprise, l'app est déjà prête : version courte.
        afficher(lancement, app, dejaPrete = !demarrage)
        appApparue = true      // ici, la fenêtre de l'app est déjà là
    }

    private fun afficher(lancement: Lancement, app: String, dejaPrete: Boolean) {
        val gestionnaire = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        retirer(gestionnaire, "nouvelle animation")

        // La VRAIE taille de l'écran, barres et encoche comprises.
        val ecran = (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY)
        val taille = Point()
        @Suppress("DEPRECATION") ecran.getRealSize(taille)

        val vue = AnimationView(this, lancement.scene, lancement.categorie, lancement.facteur)
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
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            gestionnaire.addView(vue, reglages)
            vueEnCours = vue
            appAnimee = app
            animeeA[app] = SystemClock.uptimeMillis()
            appApparue = false
            if (dejaPrete) vue.signalerPret()
            vue.jouer { if (vueEnCours === vue) retirer(gestionnaire, "fin normale") }
            // Filet de sécurité : attente max + révélation + une marge d'une seconde.
            minuteur.postDelayed({
                if (vueEnCours === vue) retirer(gestionnaire, "filet de sécurité")
            }, vue.dureeMax + 1000)
        } catch (e: Exception) {
            noter("ERREUR animation : ${e.message}")
        }
    }

    private fun retirer(gestionnaire: WindowManager, raison: String) {
        val vue = vueEnCours ?: return
        vue.arreter()
        try { gestionnaire.removeView(vue) } catch (_: Exception) {}
        vueEnCours = null
        appAnimee = null
        appApparue = false
        val ms = SystemClock.uptimeMillis() - vue.creeeA
        val surface = if (vue.surfaceCreee) "surface OK" else "PAS de surface"
        noter("  -> ${vue.images} images, $surface, $ms ms, $raison")
    }

    private fun noter(texte: String) {
        val carnet = getSharedPreferences(CARNET, Context.MODE_PRIVATE)
        val heure = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date())
        val ancienne = carnet.getString(CLE_LISTE, "") ?: ""
        val nouvelle = (listOf("$heure $texte") + ancienne.lines().filter { it.isNotBlank() })
            .take(200)
            .joinToString("\n")
        carnet.edit().putString(CLE_LISTE, nouvelle).apply()
    }

    override fun onInterrupt() {}

    companion object {
        const val CARNET = "detections"
        const val CLE_LISTE = "liste"
    }
}
