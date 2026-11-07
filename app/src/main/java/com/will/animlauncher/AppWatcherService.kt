package com.will.animlauncher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

// Un "service d'accessibilité" tourne en arrière-plan.
// Android le prévient à chaque fois que quelque chose change à l'écran.
class AppWatcherService : AccessibilityService() {

    // On retient la dernière app vue, pour ne pas afficher
    // son nom plusieurs fois de suite.
    private var derniereApp: String? = null

    // Appelée par Android à chaque événement.
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // On ne s'intéresse qu'aux changements de fenêtre
        // (= une app ou un écran passe au premier plan).
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // Le nom technique de l'app, ex : "com.anthropic.claude".
        // "?: return" : si le nom est vide, on s'arrête là.
        val app = event.packageName?.toString() ?: return

        if (app == derniereApp) return   // même app qu'avant : rien à faire
        derniereApp = app

        // On ignore notre propre app et la barre système d'Android.
        if (app == packageName || app == "com.android.systemui") return

        // Affiche le nom dans une petite bulle en bas de l'écran.
        Toast.makeText(this, app, Toast.LENGTH_SHORT).show()
    }

    // Obligatoire : appelée si Android interrompt le service. Rien à faire ici.
    override fun onInterrupt() {}
}
