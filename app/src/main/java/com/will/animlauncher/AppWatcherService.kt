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

    // --- MESURES POUR DÉTECTER LES DÉMARRAGES ---
    // Heure du dernier appui sur l'écran d'accueil (0 = aucun).
    private var heureClicAccueil = 0L
    // Les apps déjà ouvertes depuis que le service tourne (donc depuis l'allumage).
    // "mutableSetOf" = un ensemble modifiable, sans doublons.
    private val dejaOuvertes = mutableSetOf<String>()

    // LE TABLEAU QUI ASSOCIE CHAQUE APP À SON ANIMATION.
    private val animations = mapOf(
        "com.anthropic.claude" to AnimationView.Style.ECLAIR,
        "com.google.android.googlequicksearchbox" to AnimationView.Style.ONDE
    )

    private val ignorees = setOf(
        "com.samsung.android.spay",
        "com.samsung.android.honeyboard"
    )

    private val ecranAccueil: String? by lazy {
        val intention = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(intention, 0)?.activityInfo?.packageName
    }

    // Appelée quand le service démarre : après l'allumage du téléphone,
    // ou quand on réactive l'accessibilité.
    override fun onServiceConnected() {
        noter("=== Service démarré (allumage ou réactivation) ===")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val maintenant = SystemClock.uptimeMillis()

        // Un appui sur l'écran d'accueil (sur une icône, en général) : on note l'heure.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (event.packageName?.toString() == ecranAccueil) heureClicAccueil = maintenant
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val app = event.packageName?.toString() ?: return
        if (app == packageName || app in ignorees) return

        val estVraieApp = app == ecranAccueil ||
            packageManager.getLaunchIntentForPackage(app) != null
        if (!estVraieApp) return

        if (app == derniereApp) return
        val precedente = derniereApp
        derniereApp = app

        // --- LE RAPPORT DE MESURE ---
        // "add" renvoie vrai si l'app n'était pas encore dans l'ensemble.
        val premiereFois = dejaOuvertes.add(app)
        if (app == ecranAccueil) {
            noter(app)
        } else {
            // Délai entre l'appui sur l'icône et l'arrivée de l'app
            // (seulement si on vient de l'accueil et que l'appui date de moins de 10 s).
            val delai = maintenant - heureClicAccueil
            val texteDelai = if (precedente == ecranAccueil && heureClicAccueil > 0 && delai < 10_000)
                "délai $delai ms" else "délai ?"
            val classe = event.className?.toString()?.substringAfterLast('.') ?: "?"
            val marque = if (premiereFois) " | 1RE FOIS depuis allumage" else ""
            noter("$app | $texteDelai | écran $classe$marque")
        }
        heureClicAccueil = 0L

        if (!Reglages.actif(this)) return               // kill switch
        val style = animations[app] ?: return           // pas dans le tableau
        if (precedente != ecranAccueil) return          // depuis l'accueil seulement

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
