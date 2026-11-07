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

    // La dernière VRAIE app au premier plan (pas le clavier ni les popups).
    private var derniereApp: String? = null
    private var vueEnCours: AnimationView? = null
    private val minuteur = Handler(Looper.getMainLooper())

    // LE TABLEAU QUI ASSOCIE CHAQUE APP À SON ANIMATION.
    private val animations = mapOf(
        "com.anthropic.claude" to AnimationView.Style.ECLAIR,
        "com.google.android.googlequicksearchbox" to AnimationView.Style.ONDE
    )

    // Apps qui surgissent toutes seules et qu'on ignore (Samsung Wallet).
    private val ignorees = setOf("com.samsung.android.spay")

    // Le nom de l'écran d'accueil du téléphone, demandé à Android.
    // "by lazy" : calculé une seule fois, la première fois qu'on en a besoin.
    private val ecranAccueil: String? by lazy {
        val intention = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(intention, 0)?.activityInfo?.packageName
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val app = event.packageName?.toString() ?: return
        if (app == packageName || app in ignorees) return

        // FILTRE 1 : on ignore tout ce qui n'est pas une vraie app.
        // Une vraie app a une icône, donc une "intention de lancement".
        // Le clavier, la barre système et les popups n'en ont pas.
        val estVraieApp = app == ecranAccueil ||
            packageManager.getLaunchIntentForPackage(app) != null
        if (!estVraieApp) return

        if (app == derniereApp) return   // toujours la même app : rien à faire
        val precedente = derniereApp
        derniereApp = app
        noter(app)

        if (!Reglages.actif(this)) return               // kill switch
        val style = animations[app] ?: return           // pas dans le tableau

        // FILTRE 2 : seulement si on arrive de l'écran d'accueil (appui sur l'icône).
        if (precedente != ecranAccueil) return

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
            }, 800)
        } catch (e: Exception) {
            noter("Erreur animation : ${e.message}")
        }
    }

    // Retire l'animation et écrit le rapport du MOUCHARD dans le carnet.
    private fun retirer(gestionnaire: WindowManager, raison: String) {
        val vue = vueEnCours ?: return
        try { gestionnaire.removeView(vue) } catch (_: Exception) {}
        vueEnCours = null
        val ms = SystemClock.uptimeMillis() - vue.creeeA
        noter("  ↳ ${vue.images} images, ${ms} ms, $raison")
    }

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
