package com.will.animlauncher

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// L'écran JOURNAL : affiche tout le journal, avec Copier (pour le partager) et Effacer.
class JournalActivity : Activity() {

    private lateinit var contenu: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val boutonCopier = Button(this).apply {
            text = "Copier"
            setOnClickListener { copier() }
        }
        val boutonEffacer = Button(this).apply {
            text = "Effacer"
            setOnClickListener { effacer() }
        }

        // Une rangée horizontale avec les deux boutons, chacun prend la moitié (poids 1).
        val barre = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val moitie = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(boutonCopier, moitie)
            addView(boutonEffacer, LinearLayout.LayoutParams(moitie))
        }

        contenu = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#DFE4F7"))
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, 24, 0, 24)
        }

        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B2340"))
            setPadding(32, 120, 32, 32)
            addView(barre)
            // Le texte défile et prend toute la place restante (hauteur 0 + poids 1).
            addView(
                ScrollView(this@JournalActivity).apply { addView(contenu) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            )
        }
        setContentView(colonne)
    }

    override fun onResume() {
        super.onResume()
        contenu.text = texteDuJournal()
    }

    // Le texte complet : version de l'app en tête, puis le journal.
    private fun texteDuJournal(): String {
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        val carnet = getSharedPreferences(AppWatcherService.CARNET, Context.MODE_PRIVATE)
        val lignes = carnet.getString(AppWatcherService.CLE_LISTE, "") ?: ""
        return "AnimLauncher version $version\n\n" + lignes.ifBlank { "(journal vide)" }
    }

    // Met le texte dans le presse-papiers, prêt à coller dans Claude.
    private fun copier() {
        val pressePapiers = getSystemService(ClipboardManager::class.java)
        pressePapiers.setPrimaryClip(ClipData.newPlainText("Journal AnimLauncher", texteDuJournal()))
        Toast.makeText(this, "Journal copié", Toast.LENGTH_SHORT).show()
    }

    private fun effacer() {
        getSharedPreferences(AppWatcherService.CARNET, Context.MODE_PRIVATE)
            .edit().remove(AppWatcherService.CLE_LISTE).apply()
        contenu.text = texteDuJournal()
    }
}
