package com.will.animlauncher

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

// L'écran principal : interrupteur général, état de la détection,
// et liste des dernières apps détectées.
class MainActivity : Activity() {

    private lateinit var statut: TextView
    private lateinit var liste: TextView
    private lateinit var interrupteur: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(56, 140, 56, 56)
        }

        // 1. L'INTERRUPTEUR GÉNÉRAL (kill switch dans l'app).
        interrupteur = Switch(this).apply {
            text = "Animations activées"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 40)
            // Quand on le bascule, on enregistre le nouvel état.
            setOnCheckedChangeListener { _, coche -> Reglages.definir(this@MainActivity, coche) }
        }

        // 2. Bouton pour ajouter le kill switch au volet des réglages rapides.
        //    Android 13+ sait le proposer directement ; avant, il faut l'ajouter à la main.
        val boutonVolet = Button(this).apply {
            text = "Ajouter le bouton au volet rapide"
            setOnClickListener { proposerTuile() }
        }

        statut = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#DFE4F7"))
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 24)
        }

        val boutonAccessibilite = Button(this).apply {
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
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }

        colonne.addView(interrupteur)
        colonne.addView(boutonVolet)
        colonne.addView(statut)
        colonne.addView(boutonAccessibilite)
        colonne.addView(titre)
        colonne.addView(liste)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1B2340"))
            addView(colonne)
        })
    }

    // À chaque retour sur l'écran, on remet tout à jour
    // (l'état a pu changer depuis le volet rapide).
    override fun onResume() {
        super.onResume()
        interrupteur.isChecked = Reglages.actif(this)

        statut.text = if (serviceActif()) {
            "✅ Détection activée"
        } else {
            "⚠️ Détection désactivée : active AnimLauncher dans l'accessibilité."
        }

        val carnet = getSharedPreferences(AppWatcherService.CARNET, Context.MODE_PRIVATE)
        val texte = carnet.getString(AppWatcherService.CLE_LISTE, "") ?: ""
        liste.text = texte.ifBlank { "(rien pour l'instant)" }
    }

    // Demande à Android d'ajouter notre bouton au volet (Android 13+ seulement).
    private fun proposerTuile() {
        if (Build.VERSION.SDK_INT >= 33) {
            val barre = getSystemService(StatusBarManager::class.java)
            barre.requestAddTileService(
                ComponentName(this, InterrupteurTile::class.java),
                "AnimLauncher",
                Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                mainExecutor
            ) { }
        } else {
            statut.text = "Ouvre le volet rapide, appuie sur le crayon ✏️ et glisse le bouton AnimLauncher."
        }
    }

    private fun serviceActif(): Boolean {
        val actifs = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return actifs.contains("$packageName/")
    }
}
