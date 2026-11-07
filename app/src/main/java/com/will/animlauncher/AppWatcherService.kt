package com.will.animlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
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

    // Un "Handler" permet de programmer une action pour plus tard.
    private val minuteur = Handler(Looper.getMainLooper())

    // LE TABLEAU QUI ASSOCIE CHAQUE APP À SON ANIMATION.
    // Pour ajouter une app : une ligne "nom technique" to AnimationView.Style.XXX
    private val animations = mapOf(
        "com.anthropic.claude" to AnimationView.Style.ECLAIR,
        "com.google.android.googlequicksearchbox" to AnimationView.Style.ONDE
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val app = event.packageName?.toString() ?: return
        if (app == derniereApp) return
        derniereApp = app
        if (app == packageName || app == "com.android.systemui") return

        noter(app)
        animations[app]?.let { style -> afficher(style) }
    }

    // Pose une fenêtre transparente par-dessus tout l'écran et y joue l'animation.
    private fun afficher(style: AnimationView.Style) {
        val gestionnaire = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        retirer(gestionnaire)   // enlève une éventuelle ancienne animation

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
            vue.jouer { if (vueEnCours === vue) retirer(gestionnaire) }

            // FILET DE SÉCURITÉ : quoi qu'il arrive, on retire l'animation
            // au bout de 0,8 seconde. Plus jamais de rond bloqué à l'écran.
            minuteur.postDelayed({
                if (vueEnCours === vue) retirer(gestionnaire)
            }, 800)
        } catch (e: Exception) {
            noter("Erreur animation : ${e.message}")
        }
    }

    private fun retirer(gestionnaire: WindowManager) {
        vueEnCours?.let {
            try { gestionnaire.removeView(it) } catch (_: Exception) {}
        }
        vueEnCours = null
    }

    // Note une ligne dans le "carnet" affiché par l'écran principal.
    private fun noter(texte: String) {
        val carnet = getSharedPreferences(CARNET, Context.MODE_PRIVATE)
        val heure = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date())
        val ancienne = carnet.getString(CLE_LISTE, "") ?: ""
        val nouvelle = (listOf("$heure  $texte") + ancienne.lines().filter { it.isNotBlank() })
            .take(15)
            .joinToString("\n")
        carnet.edit().putString(CLE_LISTE, nouvelle).apply()
    }

    override fun onInterrupt() {}

    companion object {
        const val CARNET = "detections"
        const val CLE_LISTE = "liste"
    }
}
