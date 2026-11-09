package com.will.animlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import java.text.Normalizer

// L'ÉCRAN « ANIMATIONS D'APPS » (carte 1 de l'accueil), refait le 09/11 dans le style noir.
// Le plus simple possible : une recherche, puis la liste des apps.
//   - l'interrupteur d'une ligne allume ou coupe l'animation de cette app ;
//   - un appui sur la ligne ouvre ses réglages (voir l'animation, vitesse, variantes).
// Les apps qui ont une animation sont en haut ; les autres, en gris, tout en bas.
class AppsActivity : Activity() {

    // Une app de la liste : son nom affiché et son nom technique.
    class AppDetectee(val nom: String, val pkg: String) {
        val aUneAnimation = Catalogue.pour(pkg) != null
        val cle = AppsActivity.sansAccents(nom)       // pour la recherche (« ecran » trouve « Écran »)
    }

    private var toutes = listOf<AppDetectee>()
    private var affichees = listOf<AppDetectee>()
    private val icones = HashMap<String, Drawable?>()    // les icônes déjà chargées
    private lateinit var adaptateur: Adaptateur
    private lateinit var resume: TextView
    private fun px(v: Float) = Style.px(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Style.fenetreNoire(this)
        toutes = chargerApps()
        affichees = toutes

        resume = Style.texte(this, "", 13f, Style.MUET).apply { setPadding(px(6f), 0, 0, px(12f)) }

        // La barre de recherche : à chaque lettre tapée, la liste est filtrée.
        val recherche = EditText(this).apply {
            hint = "Rechercher une app"
            setHintTextColor(Color.rgb(142, 142, 150))
            setTextColor(Style.TEXTE)
            textSize = 16f
            isSingleLine = true
            background = Style.arrondi(this@AppsActivity, Style.CARTE, 16f, Style.BORD)
            setPadding(px(18f), px(13f), px(18f), px(13f))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { filtrer(s?.toString() ?: "") }
            })
        }

        adaptateur = Adaptateur()
        val liste = ListView(this).apply {
            adapter = adaptateur
            divider = null                       // pas de trait entre les lignes : chaque ligne est une carte
            dividerHeight = px(8f)               // … séparée de la suivante par 8 dp de vide
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            setPadding(0, px(12f), 0, px(20f))
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            // Un appui sur une ligne : on ouvre les réglages de l'app.
            setOnItemClickListener { _, _, position, _ ->
                val app = affichees[position]
                if (app.aUneAnimation) {
                    startActivity(Intent(this@AppsActivity, ReglageAppActivity::class.java)
                        .putExtra("pkg", app.pkg).putExtra("nom", app.nom))
                }
            }
        }

        val corps = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16f), 0, px(16f), 0)
            addView(resume)
            addView(recherche)
            addView(liste, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Style.NOIR)
            addView(Style.entete(this@AppsActivity, "Animations d'apps"))
            addView(corps, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    // En revenant des réglages d'une app, on rafraîchit la liste (l'état a pu changer).
    override fun onResume() {
        super.onResume()
        adaptateur.notifyDataSetChanged()
        majResume()
    }

    // Le petit texte sous le titre : combien d'animations sont actives, et combien sont coupées.
    private fun majResume() {
        val avec = toutes.count { it.aUneAnimation }
        val coupees = toutes.count { it.aUneAnimation && Catalogue.lancement(this, it.pkg) == null }
        resume.text = "${avec - coupees} actives" + (if (coupees > 0) " · $coupees coupées" else "") +
            " · touche une app pour la régler"
    }

    // Toutes les apps qui ont une icône : celles avec une animation d'abord, puis les autres.
    private fun chargerApps(): List<AppDetectee> {
        val intention = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intention, 0)
            .map { AppDetectee(it.loadLabel(packageManager).toString().trim(), it.activityInfo.packageName) }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedWith(compareBy<AppDetectee>({ !it.aUneAnimation }, { it.cle }))
    }

    private fun filtrer(texte: String) {
        val cherche = sansAccents(texte.trim())
        affichees = if (cherche.isEmpty()) toutes else toutes.filter { it.cle.contains(cherche) || it.pkg.contains(cherche) }
        adaptateur.notifyDataSetChanged()
    }

    // Le texte gris d'une ligne : en quelques mots, où en est l'animation de cette app.
    private fun description(app: AppDetectee): String {
        val e = Catalogue.pour(app.pkg) ?: return "Pas encore d'animation"
        if (!Reglages.appActive(this, app.pkg)) return "Coupée"
        if (Reglages.duree(this, app.pkg) == 0) return "Coupée (durée réglée à 0)"
        if (e.variantes.isEmpty()) return "Activée"
        val actives = e.variantes.size - Reglages.variantesDesactivees(this, app.pkg).size
        return if (actives <= 0) "Aucune variante cochée" else "Activée · $actives variantes au hasard"
    }

    // L'ADAPTATEUR fabrique (et recycle) les lignes de la liste au fur et à mesure du défilement.
    inner class Adaptateur : BaseAdapter() {
        override fun getCount() = affichees.size
        override fun getItem(position: Int) = affichees[position]
        override fun getItemId(position: Int) = position.toLong()

        // Les éléments d'une ligne, gardés pour ne pas les rechercher à chaque fois.
        inner class Ligne(val icone: ImageView, val nom: TextView, val detail: TextView, val inter: Switch)

        override fun getView(position: Int, ancienne: View?, parent: ViewGroup?): View {
            val vue = ancienne ?: creerLigne()
            val l = vue.tag as Ligne
            val app = affichees[position]
            l.icone.setImageDrawable(icones.getOrPut(app.pkg) {
                try { packageManager.getApplicationIcon(app.pkg) } catch (_: Exception) { null }
            })
            l.nom.text = app.nom
            l.detail.text = description(app)
            l.inter.setOnCheckedChangeListener(null)          // évite de déclencher l'ancien écouteur
            l.inter.visibility = if (app.aUneAnimation) View.VISIBLE else View.INVISIBLE
            l.inter.isChecked = Reglages.appActive(this@AppsActivity, app.pkg)
            l.inter.contentDescription = "Animation de ${app.nom}"
            l.inter.setOnCheckedChangeListener { _, coche ->
                Reglages.definirAppActive(this@AppsActivity, app.pkg, coche)
                l.detail.text = description(app)
                majResume()
            }
            vue.alpha = if (app.aUneAnimation) 1f else 0.45f
            return vue
        }

        private fun creerLigne(): View {
            val icone = ImageView(this@AppsActivity)
            val nom = Style.texte(this@AppsActivity, "", 16f, Style.TEXTE)
            val detail = Style.texte(this@AppsActivity, "", 12.5f, Style.MUET)
            val textes = LinearLayout(this@AppsActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(14f), 0, px(8f), 0)
                addView(nom); addView(detail)
            }
            // L'interrupteur ne prend pas le « focus » : sinon l'appui sur la ligne ne marcherait plus.
            val inter = Style.interrupteur(this@AppsActivity).apply { isFocusable = false }
            val ligne = LinearLayout(this@AppsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Style.arrondi(this@AppsActivity, Style.CARTE, 18f, Style.BORD)
                minimumHeight = px(64f)
                setPadding(px(14f), px(10f), px(12f), px(10f))
                addView(icone, LinearLayout.LayoutParams(px(40f), px(40f)))
                addView(textes, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(inter, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, px(44f)))
            }
            ligne.tag = Ligne(icone, nom, detail, inter)
            return ligne
        }
    }

    companion object {
        // « Écran » -> « ecran » : minuscules et sans accents, pour une recherche tolérante.
        fun sansAccents(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    }
}
