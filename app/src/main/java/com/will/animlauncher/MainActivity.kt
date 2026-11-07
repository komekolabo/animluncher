package com.will.animlauncher

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
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

// L'écran principal : interrupteurs, état de la détection, accès au journal.
class MainActivity : Activity() {

    private lateinit var statut: TextView
    private lateinit var interrupteur: Switch
    private lateinit var interrupteurDemarrage: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(56, 140, 56, 56)
        }

        // 1. L'interrupteur général (kill switch).
        interrupteur = Switch(this).apply {
            text = "Animations activées"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 32)
            setOnCheckedChangeListener { _, coche -> Reglages.definir(this@MainActivity, coche) }
        }

        // 2. Le mode : à chaque ouverture, ou seulement quand l'app démarre.
        interrupteurDemarrage = Switch(this).apply {
            text = "Seulement au démarrage de l'app"
            textSize = 18f
            setTextColor(Color.parseColor("#DFE4F7"))
            setPadding(0, 0, 0, 40)
            setOnCheckedChangeListener { _, coche ->
                Reglages.definirSeulementDemarrage(this@MainActivity, coche)
            }
        }

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
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        val boutonJournal = Button(this).apply {
            text = "Ouvrir le journal (logs)"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, JournalActivity::class.java))
            }
        }

        val version = TextView(this).apply {
            text = "Version " + packageManager.getPackageInfo(packageName, 0).versionName
            textSize = 14f
            setTextColor(Color.parseColor("#94A0C4"))
            gravity = Gravity.CENTER
            setPadding(0, 64, 0, 0)
        }

        colonne.addView(interrupteur)
        colonne.addView(interrupteurDemarrage)
        colonne.addView(boutonVolet)
        colonne.addView(statut)
        colonne.addView(boutonAccessibilite)
        colonne.addView(boutonJournal)
        colonne.addView(version)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1B2340"))
            addView(colonne)
        })
    }

    override fun onResume() {
        super.onResume()
        interrupteur.isChecked = Reglages.actif(this)
        interrupteurDemarrage.isChecked = Reglages.seulementDemarrage(this)
        statut.text = if (serviceActif()) {
            "✅ Détection activée"
        } else {
            "⚠️ Détection désactivée : active AnimLauncher dans l'accessibilité."
        }
    }

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
