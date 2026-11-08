package com.will.animlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
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

// L'ÉCRAN « ANIMATIONS PAR APP » : toutes les apps détectées sur le téléphone, avec une barre
// de recherche. Pour chaque app : un interrupteur pour couper son animation. Un appui sur
// la ligne ouvre ses réglages détaillés (durée, variantes, aperçu).
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
    private var dp = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dp = resources.displayMetrics.density
        toutes = chargerApps()
        affichees = toutes

        val titre = TextView(this).apply {
            text = "Animations par app"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        resume = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#94A0C4"))
            setPadding(0, 0, 0, (14 * dp).toInt())
        }
        // La barre de recherche : à chaque lettre tapée, la liste est filtrée.
        val recherche = EditText(this).apply {
            hint = "🔍  Rechercher une app"
            setHintTextColor(Color.parseColor("#7A86AA"))
            setTextColor(Color.WHITE)
            textSize = 17f
            isSingleLine = true
            background = arrondi("#2A3459", 24f)
            setPadding((18 * dp).toInt(), (12 * dp).toInt(), (18 * dp).toInt(), (12 * dp).toInt())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { filtrer(s?.toString() ?: "") }
            })
        }

        adaptateur = Adaptateur()
        val liste = ListView(this).apply {
            adapter = adaptateur
            divider = null
            setPadding(0, (10 * dp).toInt(), 0, 0)
            clipToPadding = false
            // Un appui sur une ligne : on ouvre les réglages détaillés de l'app.
            setOnItemClickListener { _, _, position, _ ->
                val app = affichees[position]
                if (app.aUneAnimation) {
                    startActivity(Intent(this@AppsActivity, ReglageAppActivity::class.java)
                        .putExtra("pkg", app.pkg).putExtra("nom", app.nom))
                }
            }
        }

        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B2340"))
            setPadding((20 * dp).toInt(), (40 * dp).toInt(), (20 * dp).toInt(), 0)
            addView(titre)
            addView(resume)
            addView(recherche)
            addView(liste, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(colonne)
    }

    // En revenant des réglages d'une app, on rafraîchit la liste (l'état a pu changer).
    override fun onResume() {
        super.onResume()
        adaptateur.notifyDataSetChanged()
        majResume()
    }

    // Le petit texte sous le titre : combien d'apps, combien avec une animation, combien coupées.
    private fun majResume() {
        val avec = toutes.count { it.aUneAnimation }
        val coupees = toutes.count { it.aUneAnimation && Catalogue.lancement(this, it.pkg) == null }
        resume.text = "${toutes.size} apps détectées · $avec avec une animation" +
            (if (coupees > 0) " · $coupees coupée(s)" else "")
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

    private fun arrondi(couleur: String, rayonDp: Float) = GradientDrawable().apply {
        setColor(Color.parseColor(couleur))
        cornerRadius = rayonDp * dp
    }

    // Le texte d'une ligne : la durée, les variantes, ou pourquoi il n'y a pas d'animation.
    private fun description(app: AppDetectee): String {
        val e = Catalogue.pour(app.pkg) ?: return "Pas encore d'animation"
        if (!Reglages.appActive(this, app.pkg)) return "Animation coupée"
        val ms = Reglages.duree(this, app.pkg).let { if (it < 0) e.dureeDefaut else it }
        if (ms == 0) return "Durée 0 s : pas d'animation"
        var texte = "Durée " + String.format("%.2f", ms / 1000f).trimEnd('0').trimEnd(',', '.') + " s"
        if (Reglages.duree(this, app.pkg) < 0) texte += " (par défaut)"
        if (e.variantes.isNotEmpty()) {
            val actives = e.variantes.size - Reglages.variantesDesactivees(this, app.pkg).size
            texte += " · $actives/${e.variantes.size} variantes"
        }
        return texte
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
            val nom = TextView(this@AppsActivity).apply { textSize = 17f; setTextColor(Color.WHITE) }
            val detail = TextView(this@AppsActivity).apply { textSize = 13f; setTextColor(Color.parseColor("#94A0C4")) }
            val textes = LinearLayout(this@AppsActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((14 * dp).toInt(), 0, (8 * dp).toInt(), 0)
                addView(nom); addView(detail)
            }
            // L'interrupteur ne prend pas le « focus » : sinon l'appui sur la ligne ne marcherait plus.
            val inter = Switch(this@AppsActivity).apply { isFocusable = false }
            val ligne = LinearLayout(this@AppsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
                addView(icone, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()))
                addView(textes, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(inter)
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
