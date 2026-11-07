package com.will.animlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Le service qui surveille quelle app passe au premier plan,
// et qui joue l'animation correspondante.
class AppWatcherService : AccessibilityService() {

    private var derniereApp: String? = null
    private var vueEnCours: AnimationView? = null
    private val minuteur = Handler(Looper.getMainLooper())

    // LE TABLEAU QUI ASSOCIE CHAQUE APP À SON ANIMATION.
    private val animations = mapOf(
        "com.anthropic.claude" to AnimationView.Style.ECLAIR,
        "com.google.android.googlequicksearchbox" to AnimationView.Style.ONDE
    )

    // Apps qui surgissent toutes seules et qu'on ignore :
    // Samsung Wallet, clavier Samsung, panneau Samsung à gauche de l'accueil.
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

        // --- DÉTECTION DU DÉMARRAGE ---
        // Quand une app démarre, Android affiche d'abord son écran de lancement
        // ("splash screen" : fond uni + icône). Cet écran est un FrameLayout.
        // Quand l'app est juste reprise, on voit directement son vrai écran (MainActivity...).
        val classe = event.className?.toString()?.substringAfterLast('.') ?: "?"
        val demarrage = classe == "FrameLayout"
        noter("$app | écran $classe | " + if (demarrage) "DÉMARRAGE" else "reprise")

        if (!Reglages.actif(this)) return               // kill switch
        val style = animations[app] ?: return           // pas dans le tableau
        if (precedente != ecranAccueil) return          // depuis l'accueil seulement
        // Mode "seulement au démarrage" : on ignore les simples reprises.
        if (Reglages.seulementDemarrage(this) && !demarrage) return

        afficher(style)
    }

    private fun afficher(style: AnimationView.Style) {
        val gestionnaire = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        retirer(gestionnaire, "nouvelle animation")

        val vue = AnimationView(this, style)
        val reglages = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )

        try {
            gestionnaire.addView(vue, reglages)
            vueEnCours = vue
            vue.jouer { if (vueEnCours === vue) retirer(gestionnaire, "fin normale") }
            minuteur.postDelayed({
                if (vueEnCours === vue) retirer(gestionnaire, "filet de sécurité")
            }, 1000)
        } catch (e: Exception) {
            noter("ERREUR animation : ${e.message}")
        }
    }

    private fun retirer(gestionnaire: WindowManager, raison: String) {
        val vue = vueEnCours ?: return
        vue.arreter()
        try { gestionnaire.removeView(vue) } catch (_: Exception) {}
        vueEnCours = null
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
