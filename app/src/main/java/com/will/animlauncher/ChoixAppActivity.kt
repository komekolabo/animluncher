package com.will.animlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// AJOUTER UNE APP À UN DOSSIER (09/11) : la liste de toutes les apps du téléphone qui ne sont pas
// encore dans le groupe, avec une barre de recherche. Un appui sur une app l'ajoute au groupe.
// On arrive ici par le « + » du dossier animé (DossierActivity) : le dossier se rouvre alors tout seul,
// avec la nouvelle app dedans. Ou par le bouton « ＋ Ajouter une app » de l'écran des dossiers.
class ChoixAppActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val groupe = intent.getStringExtra("groupe") ?: run { finish(); return }
        val depuisDossier = intent.getBooleanExtra("depuisDossier", false)
        val dp = resources.displayMetrics.density
        val nomGroupe = Groupes.categorie(groupe)?.nom ?: "?"

        // Toutes les apps qui ont une icône, sauf AnimLauncher et celles déjà dans le dossier.
        val dejaDedans = Groupes.apps(this, groupe).toSet()
        // Le dossier est plein (60 apps) : on ne peut plus rien ajouter.
        if (dejaDedans.size >= Groupes.MAX) {
            Toast.makeText(this, "Dossier plein : ${Groupes.MAX} apps maximum", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val intention = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intention, 0)
            .map { it.activityInfo.packageName }.distinct()
            .filter { it != packageName && it !in dejaDedans }
            .map { it to Groupes.nomApp(this, it) }
            .sortedBy { it.second.lowercase() }

        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * dp).toInt(), (40 * dp).toInt(), (22 * dp).toInt(), (40 * dp).toInt())
        }
        colonne.addView(TextView(this).apply {
            text = "Ajouter une app à « $nomGroupe »"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, (14 * dp).toInt())
        })
        val recherche = EditText(this).apply {
            hint = "🔍  Rechercher une app"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A0C4"))
        }
        colonne.addView(recherche)

        // Une ligne par app : son icône et son nom. On garde chaque ligne avec son nom « sans accents »
        // pour la recherche (« ecran » trouve « Écran »).
        val lignes = ArrayList<Pair<View, String>>()
        for ((pkg, nom) in apps) {
            val ligne = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
                isClickable = true
                setOnClickListener {
                    Groupes.ajouter(this@ChoixAppActivity, groupe, pkg)
                    if (depuisDossier) {
                        // le dossier se rouvre, avec la nouvelle app qui éclot à sa place
                        startActivity(Intent(this@ChoixAppActivity, DossierActivity::class.java).putExtra("groupe", groupe)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                    finish()
                }
            }
            ligne.addView(ImageView(this).apply {
                try { setImageDrawable(packageManager.getApplicationIcon(pkg)) } catch (_: Exception) {}
            }, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply { rightMargin = (14 * dp).toInt() })
            ligne.addView(TextView(this).apply {
                text = nom
                textSize = 17f
                setTextColor(Color.WHITE)
            })
            colonne.addView(ligne)
            lignes.add(ligne to AppsActivity.sansAccents(nom))
        }

        // La recherche : à chaque lettre tapée, on cache les apps dont le nom ne contient pas le texte.
        recherche.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val cherche = AppsActivity.sansAccents(s?.toString() ?: "")
                for ((vue, cle) in lignes) vue.visibility = if (cle.contains(cherche)) View.VISIBLE else View.GONE
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1B2340"))
            addView(colonne)
        })
    }
}
