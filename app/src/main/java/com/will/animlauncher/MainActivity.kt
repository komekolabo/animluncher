package com.will.animlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

// L'écran principal : il dit si la détection est active,
// et propose un bouton pour aller l'activer.
class MainActivity : Activity() {

    // "lateinit" : la variable sera remplie plus tard, dans onCreate.
    private lateinit var statut: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Une colonne (LinearLayout vertical) qui empile le texte et le bouton.
        val ecran = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1B2340"))
            setPadding(64, 64, 64, 64)
        }

        statut = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)   // espace sous le texte
        }

        val bouton = Button(this).apply {
            text = "Ouvrir les réglages d'accessibilité"
            // Au clic : ouvre directement Paramètres > Accessibilité.
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        ecran.addView(statut)
        ecran.addView(bouton)
        setContentView(ecran)
    }

    // onResume est appelée à chaque fois qu'on revient sur l'écran
    // (par exemple en sortant des réglages) : on met le texte à jour.
    override fun onResume() {
        super.onResume()
        statut.text = if (serviceActif()) {
            "✅ Détection activée\n\nOuvre une app : son nom s'affichera en bas de l'écran."
        } else {
            "Détection désactivée\n\nActive AnimLauncher dans les réglages d'accessibilité."
        }
    }

    // Demande à Android la liste des services d'accessibilité activés,
    // et regarde si le nôtre en fait partie.
    private fun serviceActif(): Boolean {
        val actifs = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return actifs.contains("$packageName/")
    }
}
