package com.will.animlauncher

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

// L'ÉCRAN « LOGOS ANIMÉS » (carte 2 de l'accueil), refait le 09/11 dans le style noir.
// Tout ce qui se pose sur l'écran d'accueil du téléphone, le plus simplement possible :
//   - en haut, le logo animé d'AnimLauncher (voir LogoWidget.kt) ;
//   - puis les DOSSIERS ANIMÉS : AnimLauncher a rangé les apps du téléphone par catégorie
//     (voir Groupes.kt). Une carte par dossier d'au moins 2 apps : ses icônes, « Voir » et
//     « Mettre sur l'accueil » (WidgetGroupe). Le détail des apps est replié : on l'ouvre avec
//     « Choisir les apps » seulement si on veut en retirer ou en ajouter.
class GroupesActivity : Activity() {

    private fun px(v: Float) = Style.px(this, v)
    private val ouverts = HashSet<String>()      // les dossiers dont la liste d'apps est dépliée

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Style.fenetreNoire(this)
    }

    // L'écran est (re)construit à chaque retour ici : on voit tout de suite l'app qu'on vient d'ajouter.
    override fun onResume() {
        super.onResume()
        construire()
    }

    private fun construire() {
        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16f), px(4f), px(16f), px(32f))
        }

        // ---------- le logo animé d'AnimLauncher ----------
        val carteLogo = Style.carte(this, espaceDessous = 22f)
        carteLogo.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(LogoAnime(this@GroupesActivity), LinearLayout.LayoutParams(px(64f), px(64f)).apply { rightMargin = px(14f) })
            addView(LinearLayout(this@GroupesActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(Style.texte(this@GroupesActivity, "Le logo animé", 17f, Style.TEXTE, gras = true))
                addView(Style.texte(this@GroupesActivity, "À poser sur l'accueil, à la place de l'icône.", 13f, Style.MUET))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        carteLogo.addView(Style.boutonDiscret(this, "Mettre sur l'accueil") {
            if (!LogoWidget.proposer(this)) aideWidget()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(46f)).apply { topMargin = px(12f) })
        colonne.addView(carteLogo)

        // ---------- les dossiers animés ----------
        colonne.addView(Style.texte(this, "Dossiers animés", 20f, Style.TEXTE, gras = true).apply { setPadding(px(6f), 0, 0, px(4f)) })
        colonne.addView(Style.texte(this, "Tes apps rangées par thème, derrière une étoile animée.", 13f, Style.MUET)
            .apply { setPadding(px(6f), 0, 0, px(14f)) })

        var aucun = true
        for (cat in Groupes.CATEGORIES) {
            val liste = Groupes.tous(this, cat.id)
            if (liste.size < 2) continue          // un dossier d'une seule app n'a pas d'intérêt
            aucun = false
            colonne.addView(carteGroupe(cat, liste))
        }
        if (aucun) colonne.addView(Style.texte(this, "Aucun groupe d'au moins 2 apps n'a été trouvé.", 15f, Style.DOUX)
            .apply { setPadding(px(6f), 0, 0, 0) })

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Style.NOIR)
            addView(Style.entete(this@GroupesActivity, "Logos animés"))
            addView(ScrollView(this@GroupesActivity).apply {
                isVerticalScrollBarEnabled = false
                addView(colonne)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    // Une carte par dossier : son nom, ses icônes, les deux boutons, et (repliée) la liste des apps.
    private fun carteGroupe(cat: Groupes.Categorie, liste: List<String>): LinearLayout {
        val carte = Style.carte(this)
        val retirees = Groupes.retirees(this, cat.id)
        val dedans = liste.filter { it !in retirees }

        // la première ligne : le nom du dossier à gauche, le nombre d'apps à droite
        val compte = Style.texte(this, "", 13f, Style.MUET)
        fun majCompte() {
            val n = liste.size - Groupes.retirees(this, cat.id).count { it in liste }
            compte.text = "$n apps"
        }
        majCompte()
        carte.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Style.texte(this@GroupesActivity, cat.nom, 18f, Style.TEXTE, gras = true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(compte)
        })

        // un aperçu : les icônes des 7 premières apps du dossier
        carte.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(12f), 0, px(14f))
            for (pkg in dedans.take(7)) {
                addView(ImageView(this@GroupesActivity).apply {
                    try { setImageDrawable(packageManager.getApplicationIcon(pkg)) } catch (_: Exception) {}
                }, LinearLayout.LayoutParams(px(32f), px(32f)).apply { rightMargin = px(8f) })
            }
        })

        // les deux actions : voir le dossier tel qu'il s'ouvrira, et le poser sur l'accueil
        carte.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Style.boutonDiscret(this@GroupesActivity, "Voir") {
                startActivity(Intent(this@GroupesActivity, DossierActivity::class.java).putExtra("groupe", cat.id))
            }, LinearLayout.LayoutParams(0, px(46f), 1f))
            addView(Style.boutonBlanc(this@GroupesActivity, "Mettre sur l'accueil") {
                if (!WidgetGroupe.proposer(this@GroupesActivity, cat.id)) aideWidget()
            }, LinearLayout.LayoutParams(0, px(46f), 2f).apply { leftMargin = px(8f) })
        })

        // la liste des apps, repliée par défaut : une case à cocher par app, et « Ajouter une app »
        val detail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, px(8f), 0, 0)
            visibility = if (cat.id in ouverts) View.VISIBLE else View.GONE
        }
        val teinte = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Style.TEXTE, Color.rgb(113, 113, 122)))
        for (pkg in liste) {
            detail.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(ImageView(this@GroupesActivity).apply {
                    try { setImageDrawable(packageManager.getApplicationIcon(pkg)) } catch (_: Exception) {}
                }, LinearLayout.LayoutParams(px(30f), px(30f)).apply { rightMargin = px(10f) })
                addView(CheckBox(this@GroupesActivity).apply {
                    text = Groupes.nomApp(this@GroupesActivity, pkg)
                    textSize = 15f
                    setTextColor(Style.TEXTE)
                    buttonTintList = teinte
                    isChecked = pkg !in retirees
                    setOnCheckedChangeListener { _, coche ->
                        Groupes.definir(this@GroupesActivity, cat.id, pkg, coche)
                        majCompte()
                    }
                }, LinearLayout.LayoutParams(0, px(46f), 1f))
            })
        }
        detail.addView(Style.boutonDiscret(this, "＋  Ajouter une app") {
            startActivity(Intent(this, ChoixAppActivity::class.java).putExtra("groupe", cat.id))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(46f)).apply { topMargin = px(6f) })

        // le lien qui déplie ou replie cette liste
        val lien = Style.texte(this, "", 13f, Style.DOUX).apply {
            setPadding(0, px(14f), 0, px(2f))
            isClickable = true
        }
        fun majLien() { lien.text = if (cat.id in ouverts) "Masquer les apps  ▴" else "Choisir les apps  ▾" }
        majLien()
        lien.setOnClickListener {
            if (!ouverts.remove(cat.id)) ouverts.add(cat.id)
            detail.visibility = if (cat.id in ouverts) View.VISIBLE else View.GONE
            majLien()
        }
        carte.addView(lien)
        carte.addView(detail)
        return carte
    }

    // Si le téléphone ne sait pas proposer le widget tout seul : on explique où le trouver.
    private fun aideWidget() {
        Toast.makeText(this, "Appui long sur l'accueil → Widgets → " + getString(R.string.app_name), Toast.LENGTH_LONG).show()
    }
}
