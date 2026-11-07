package com.will.animlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Un "service d'accessibilité" tourne en arrière-plan.
// Android le prévient à chaque fois qu'une fenêtre change à l'écran.
class AppWatcherService : AccessibilityService() {

    // La dernière app vue, pour ne pas la noter plusieurs fois de suite.
    private var derniereApp: String? = null

    // Appelée par Android à chaque événement.
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // On ne garde que les changements de fenêtre (une app passe au premier plan).
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // Le nom technique de l'app, ex : "com.anthropic.claude".
        val app = event.packageName?.toString() ?: return

        if (app == derniereApp) return   // même app qu'avant : rien à faire
        derniereApp = app

        // On ignore notre propre app et la barre système.
        if (app == packageName || app == "com.android.systemui") return

        noter(app)
    }

    // Ajoute l'app en haut d'une liste enregistrée dans le téléphone.
    // "SharedPreferences" = un petit carnet où une app peut ranger du texte,
    // qui reste même quand l'app est fermée. L'écran principal le relira.
    private fun noter(app: String) {
        val carnet = getSharedPreferences(CARNET, Context.MODE_PRIVATE)
        val heure = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date())
        val ancienne = carnet.getString(CLE_LISTE, "") ?: ""
        // Nouvelle ligne en haut, puis on garde seulement les 15 dernières.
        val nouvelle = (listOf("$heure  $app") + ancienne.lines().filter { it.isNotBlank() })
            .take(15)
            .joinToString("\n")
        carnet.edit().putString(CLE_LISTE, nouvelle).apply()
    }

    // Obligatoire : appelée si Android interrompt le service. Rien à faire ici.
    override fun onInterrupt() {}

    // "companion object" = des valeurs partagées, que MainActivity peut aussi utiliser.
    companion object {
        const val CARNET = "detections"
        const val CLE_LISTE = "liste"
    }
}
