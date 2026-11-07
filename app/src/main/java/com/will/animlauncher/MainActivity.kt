package com.will.animlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// L'écran principal : dit si la détection est active,
// et affiche la liste des dernières apps détectées.
class MainActivity : Activity() {

    // "lateinit" : ces variables seront remplies plus tard, dans onCreate.
    private lateinit var statut: TextView
    private lateinit var liste: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Une colonne qui empile : statut, bouton, titre, liste.
        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(56, 140, 56, 56)
        }

        statut = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        val bouton = Button(this).apply {
            text = "Ouvrir les réglages d'accessibilité"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val titre = TextView(this).apply {
            text = "Dernières apps détectées"
            textSize = 18f
            setTextColor(Color.parseColor("#FF7A5C"))
            setPadding(0, 64, 0, 16)
        }

        liste = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#DFE4F7"))
            typeface = Typeface.MONOSPACE   // police à chasse fixe, pour aligner les heures
            setTextIsSelectable(true)       // appui long pour copier le texte
        }

        colonne.addView(statut)
        colonne.addView(bouton)
        colonne.addView(titre)
        colonne.addView(liste)

        // ScrollView : permet de faire défiler si la liste est longue.
        val ecran = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1B2340"))
            addView(colonne)
        }
        setContentView(ecran)
    }

    // Appelée à chaque retour sur l'écran : on met tout à jour.
    override fun onResume() {
        super.onResume()

        statut.text = if (serviceActif()) {
            "✅ Détection activée\nOuvre d'autres apps, puis reviens ici."
        } else {
            "Détection désactivée\nActive AnimLauncher dans les réglages d'accessibilité."
        }

        // On relit le "carnet" rempli par le service.
        val carnet = getSharedPreferences(AppWatcherService.CARNET, Context.MODE_PRIVATE)
        val texte = carnet.getString(AppWatcherService.CLE_LISTE, "") ?: ""
        liste.text = texte.ifBlank { "(rien pour l'instant)" }
    }

    // Regarde si notre service fait partie des services d'accessibilité activés.
    private fun serviceActif(): Boolean {
        val actifs = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return actifs.contains("$packageName/")
    }
}
