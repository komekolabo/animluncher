package com.will.animlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// L'ÉCRAN « DOSSIERS ANIMÉS » (09/11) : AnimLauncher a rangé les apps du téléphone par catégorie
// (voir Groupes.kt). Pour chaque groupe d'au moins 2 apps : la liste des apps (on peut en décocher),
// un bouton pour voir le dossier, et un bouton pour poser son widget sur l'accueil (WidgetGroupe).
class GroupesActivity : Activity() {

    private var dp = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dp = resources.displayMetrics.density
    }

    // L'écran est (re)construit à chaque retour ici : on voit tout de suite l'app qu'on vient d'ajouter.
    override fun onResume() {
        super.onResume()
        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * dp).toInt(), (40 * dp).toInt(), (22 * dp).toInt(), (40 * dp).toInt())
        }
        colonne.addView(TextView(this).apply {
            text = "Dossiers animés"
            textSize = 26f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, (8 * dp).toInt())
        })
        colonne.addView(TextView(this).apply {
            text = "AnimLauncher a rangé tes apps par catégorie. Pose un dossier sur l'accueil : " +
                "une étoile animée qui s'ouvre sur les apps du groupe. Décoche une app pour la retirer du dossier, " +
                "ou ajoute-en une avec « ＋ »."
            textSize = 14f
            setTextColor(Color.parseColor("#94A0C4"))
            setPadding(0, 0, 0, (18 * dp).toInt())
        })

        var aucun = true
        for (cat in Groupes.CATEGORIES) {
            val liste = Groupes.tous(this, cat.id)
            if (liste.size < 2) continue          // un dossier d'une seule app n'a pas d'intérêt
            aucun = false
            colonne.addView(carteGroupe(cat, liste))
        }
        if (aucun) colonne.addView(TextView(this).apply {
            text = "Aucun groupe d'au moins 2 apps n'a été trouvé."
            textSize = 16f
            setTextColor(Color.WHITE)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1B2340"))
            addView(colonne)
        })
    }

    // Une carte par groupe : le titre, les apps (avec leur icône et une case à cocher), les boutons.
    private fun carteGroupe(cat: Groupes.Categorie, liste: List<String>): LinearLayout {
        val carte = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor("#242E52")); cornerRadius = 18 * dp }
            setPadding((18 * dp).toInt(), (16 * dp).toInt(), (18 * dp).toInt(), (16 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = (14 * dp).toInt() }
        }
        val titre = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        carte.addView(titre)
        fun majTitre() {
            val n = liste.size - Groupes.retirees(this, cat.id).count { it in liste }
            titre.text = "✦  ${cat.nom}   ·   $n apps"
        }
        majTitre()

        val retirees = Groupes.retirees(this, cat.id)
        for (pkg in liste) {
            val ligne = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            ligne.addView(ImageView(this).apply {
                try { setImageDrawable(packageManager.getApplicationIcon(pkg)) } catch (_: Exception) {}
            }, LinearLayout.LayoutParams((34 * dp).toInt(), (34 * dp).toInt()).apply { rightMargin = (10 * dp).toInt() })
            ligne.addView(CheckBox(this).apply {
                text = Groupes.nomApp(this@GroupesActivity, pkg)
                textSize = 16f
                setTextColor(Color.WHITE)
                isChecked = pkg !in retirees
                setOnCheckedChangeListener { _, coche ->
                    Groupes.definir(this@GroupesActivity, cat.id, pkg, coche)
                    majTitre()
                }
            })
            carte.addView(ligne)
        }

        // Ajouter une app à ce groupe (la liste de toutes les apps s'ouvre)
        carte.addView(Button(this).apply {
            text = "＋  Ajouter une app"
            setOnClickListener {
                startActivity(Intent(this@GroupesActivity, ChoixAppActivity::class.java).putExtra("groupe", cat.id))
            }
        })

        val boutons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }
        // Voir le dossier tel qu'il s'ouvrira (l'étoile part du bas de l'écran).
        boutons.addView(Button(this).apply {
            text = "▶  Voir"
            setOnClickListener {
                startActivity(Intent(this@GroupesActivity, DossierActivity::class.java).putExtra("groupe", cat.id))
            }
        })
        boutons.addView(Button(this).apply {
            text = "✨  Mettre sur l'accueil"
            setOnClickListener {
                if (!WidgetGroupe.proposer(this@GroupesActivity, cat.id)) {
                    Toast.makeText(this@GroupesActivity, "Appui long sur l'accueil → Widgets → " +
                        getString(R.string.app_name), Toast.LENGTH_LONG).show()
                }
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = (8 * dp).toInt() })
        carte.addView(boutons)
        return carte
    }
}
