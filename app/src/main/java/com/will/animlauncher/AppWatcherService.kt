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
    private val minuteur = Handler(Looper.getMainLooper())

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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val app = event.packageName?.toString() ?: return
        if (app == packageName || app in ignorees) return
        val classe = event.className?.toString()?.substringAfterLast('.') ?: "?"

        // L'APP EST-ELLE PRÊTE ? Dès que le vrai écran de l'app arrive (autre chose que
        // l'écran de lancement), ou qu'une autre app passe devant, on lance la révélation.
        vueEnCours?.let { vue ->
            if (app != appAnimee || classe != "FrameLayout") {
                vue.signalerPret()
                if (app == appAnimee) {
                    noter("  .. app prête après ${SystemClock.uptimeMillis() - vue.creeeA} ms (écran $classe)")
                }
            }
        }

        val estVraieApp = app == ecranAccueil ||
            packageManager.getLaunchIntentForPackage(app) != null
        if (!estVraieApp) return

        if (app == derniereApp) return
        val precedente = derniereApp
        derniereApp = app

        if (app == ecranAccueil) {
            noter(app)
            return
        }

        // FrameLayout = écran de lancement = l'app DÉMARRE.
        val demarrage = classe == "FrameLayout"
        noter("$app | écran $classe | " + if (demarrage) "DÉMARRAGE" else "reprise")

        if (!Reglages.actif(this)) return
        val entree = Catalogue.pour(app) ?: return
        if (precedente != ecranAccueil) return
        if (Reglages.seulementDemarrage(this) && !demarrage) return

        // Si c'est une simple reprise, l'app est déjà prête : version courte.
        afficher(entree, app, dejaPrete = !demarrage)
    }

    private fun afficher(entree: Entree, app: String, dejaPrete: Boolean) {
        val gestionnaire = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        retirer(gestionnaire, "nouvelle animation")

        // La VRAIE taille de l'écran, barres et encoche comprises.
        val ecran = (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY)
        val taille = Point()
        @Suppress("DEPRECATION") ecran.getRealSize(taille)

        val vue = AnimationView(this, entree.fabrique(), entree.categorie)
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
            if (dejaPrete) vue.signalerPret()
            vue.jouer { if (vueEnCours === vue) retirer(gestionnaire, "fin normale") }
            // Filet de sécurité : 3 s d'attente max + intro + révélation + marge.
            minuteur.postDelayed({
                if (vueEnCours === vue) retirer(gestionnaire, "filet de sécurité")
            }, 4500)
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
