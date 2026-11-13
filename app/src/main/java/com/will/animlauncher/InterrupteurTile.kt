package com.will.animlauncher

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

// Le bouton du volet des réglages rapides (comme Wi-Fi, lampe torche...).
// "TileService" = un service qu'Android appelle quand on ouvre le volet
// ou qu'on appuie sur notre bouton.
class InterrupteurTile : TileService() {

    // Appelée quand le volet s'ouvre : on affiche le bon état.
    override fun onStartListening() {
        mettreAJour()
    }

    // Appelée quand on appuie sur le bouton : on inverse l'état.
    override fun onClick() {
        Reglages.basculer(this)
        mettreAJour()
    }

    private fun mettreAJour() {
        val tuile = qsTile ?: return
        val actif = Reglages.actif(this)
        // STATE_ACTIVE = bouton allumé (en couleur), STATE_INACTIVE = grisé.
        tuile.state = if (actif) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tuile.label = "AnimEngine"
        // Le sous-titre n'existe qu'à partir d'Android 10 (API 29).
        if (Build.VERSION.SDK_INT >= 29) {
            tuile.subtitle = getString(if (actif) R.string.etat_on else R.string.tuile_off)
        }
        tuile.updateTile()   // envoie les changements à l'écran
    }
}
