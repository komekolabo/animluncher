package com.will.animlauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

// L'ÉCRAN « ANIMATIONS D'APPS » (carte 1 de l'accueil).
// Une liste de 90 apps à la suite n'était pas pratique : depuis le 09/11 (demande de Will), les apps
// sont RANGÉES PAR CATÉGORIE. L'écran a trois états :
//   1. LES CATÉGORIES : de larges barres. À gauche le nom (IA, Jeux…), à droite l'icône d'une app
//      de la catégorie, qui change toutes les 2,4 s (les apps de CE téléphone défilent).
//   2. UNE CATÉGORIE OUVERTE (un appui sur sa barre) : ses apps. L'interrupteur allume ou coupe
//      l'animation ; un appui sur la ligne ouvre les réglages de l'app. « Retour » revient aux catégories.
//   3. LA RECHERCHE : dès qu'on tape une lettre, on cherche dans TOUTES les apps, sans catégories.
//
// D'OÙ VIENT LA CATÉGORIE D'UNE APP ? Du même classement que les dossiers animés (voir Groupes.kt) :
// d'abord notre liste d'apps connues, sinon la catégorie qu'Android connaît pour l'app (le Play Store
// la donne pour les apps qu'il installe : jeu, musique, photo, social, actualités, cartes…).
// Tout le reste va dans « Non répertorié ».
class AppsActivity : Activity() {

    // Une app : son nom affiché, son nom technique, et l'identifiant de sa catégorie.
    class AppDetectee(val nom: String, val pkg: String, val categorie: String) {
        val aUneAnimation = Catalogue.pour(pkg) != null
        val cle = AppsActivity.sansAccents(nom)       // pour la recherche (« ecran » trouve « Écran »)
    }

    // Une catégorie (un « rayon ») : son identifiant, son nom dans la langue de l'app, et ses apps.
    class Rayon(val id: String, val nom: String, val apps: List<AppDetectee>)

    private var toutes = listOf<AppDetectee>()
    private var rayons = listOf<Rayon>()
    private var ouvert: Rayon? = null                    // la catégorie ouverte (null = on voit les catégories)
    private var cherche = ""                             // ce qui est tapé dans la recherche
    private var lignes = listOf<Any>()                   // ce que la liste montre : des Rayon ou des AppDetectee
    private val icones = HashMap<String, Drawable?>()    // les icônes déjà chargées
    private lateinit var adaptateur: Adaptateur
    private lateinit var resume: TextView
    private lateinit var titre: TextView
    private lateinit var liste: ListView
    private val principal = Handler(Looper.getMainLooper())
    private var tour = 0                                 // augmente de 1 à chaque changement d'icône
    private fun px(v: Float) = Style.px(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Style.fenetreNoire(this)
        toutes = chargerApps()
        rayons = ranger()

        // L'en-tête : la flèche de retour et le titre (le nom de la catégorie quand on en ouvre une).
        titre = Style.texte(this, getString(R.string.carte_apps), 20f, Style.TEXTE, gras = true).apply {
            setPadding(px(6f), 0, 0, 0)
        }
        val entete = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4f), 0, px(16f), 0)
            addView(Icone(this@AppsActivity, Icone.RETOUR).apply {
                contentDescription = getString(R.string.retour)
                setOnClickListener { reculer() }
            }, LinearLayout.LayoutParams(px(48f), px(48f)))
            addView(titre, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        resume = Style.texte(this, "", 13f, Style.MUET).apply { setPadding(px(6f), 0, 0, px(12f)) }

        // La barre de recherche : à chaque lettre tapée, on cherche dans toutes les apps.
        val recherche = EditText(this).apply {
            hint = getString(R.string.rechercher)
            setHintTextColor(Color.rgb(142, 142, 150))
            setTextColor(Style.TEXTE)
            textSize = 16f
            isSingleLine = true
            background = Style.arrondi(this@AppsActivity, Style.CARTE, 16f, Style.BORD)
            setPadding(px(18f), px(13f), px(18f), px(13f))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    cherche = sansAccents((s?.toString() ?: "").trim())
                    afficher()
                }
            })
        }

        adaptateur = Adaptateur()
        liste = ListView(this).apply {
            adapter = adaptateur
            divider = null                       // pas de trait entre les lignes : chaque ligne est une carte
            dividerHeight = px(10f)              // … séparée de la suivante par 10 dp de vide
            selector = ColorDrawable(Color.TRANSPARENT)
            setPadding(0, px(12f), 0, px(20f))
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            // Un appui sur une ligne : on ouvre la catégorie, ou les réglages de l'app.
            setOnItemClickListener { _, _, position, _ ->
                val x = lignes[position]
                if (x is Rayon) {
                    ouvert = x
                    afficher()
                } else if (x is AppDetectee && x.aUneAnimation) {
                    startActivity(Intent(this@AppsActivity, ReglageAppActivity::class.java)
                        .putExtra("pkg", x.pkg).putExtra("nom", x.nom))
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
            addView(entete, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(64f)))
            addView(corps, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        afficher()
    }

    // En revenant des réglages d'une app, on rafraîchit la liste (l'état a pu changer),
    // et on relance le défilement des icônes.
    override fun onResume() {
        super.onResume()
        adaptateur.notifyDataSetChanged()
        majResume()
        principal.removeCallbacks(battement)
        principal.postDelayed(battement, 2400)
    }

    // Quand l'écran n'est plus visible, on arrête le défilement : pas de travail pour rien.
    override fun onPause() {
        super.onPause()
        principal.removeCallbacks(battement)
    }

    // La flèche de l'en-tête et le bouton « retour » du téléphone : si une catégorie est ouverte,
    // on revient aux catégories ; sinon, on quitte l'écran.
    private fun reculer() {
        if (ouvert != null) { ouvert = null; afficher() } else finish()
    }

    @Deprecated("Remplacé par OnBackInvokedCallback sur Android 13+, mais marche toujours ici")
    override fun onBackPressed() {
        if (ouvert != null) reculer() else super.onBackPressed()
    }

    // Décide ce que la liste montre (voir les trois états en haut du fichier), puis la redessine.
    private fun afficher() {
        val r = ouvert
        lignes = when {
            cherche.isNotEmpty() -> toutes.filter { it.cle.contains(cherche) || it.pkg.contains(cherche) }
            r != null -> r.apps
            else -> rayons
        }
        titre.text = if (cherche.isEmpty() && r != null) r.nom else getString(R.string.carte_apps)
        adaptateur.notifyDataSetChanged()
        liste.setSelection(0)                 // on repart du haut de la liste
        majResume()
    }

    // Le petit texte sous le titre : combien d'animations sont actives, et combien sont coupées.
    private fun majResume() {
        val avec = toutes.count { it.aUneAnimation }
        val coupees = toutes.count { it.aUneAnimation && !jouable(it.pkg) }
        resume.text = when {
            cherche.isEmpty() && ouvert == null -> getString(R.string.resume_categories, avec - coupees)
            coupees > 0 -> getString(R.string.resume_apps_coupees, avec - coupees, coupees)
            else -> getString(R.string.resume_apps, avec)
        }
    }

    // Vrai si l'animation de cette app va vraiment se jouer avec les réglages actuels.
    private fun jouable(pkg: String): Boolean {
        val e = Catalogue.pour(pkg) ?: return false
        return Reglages.appActive(this, pkg) && Reglages.duree(this, pkg) != 0 &&
            !(e.variantes.isNotEmpty() && Reglages.variantesDesactivees(this, pkg).size >= e.variantes.size)
    }

    // Toutes les apps qui ont une icône, avec leur catégorie : celles qui ont une animation d'abord,
    // puis les autres, par ordre alphabétique.
    private fun chargerApps(): List<AppDetectee> {
        // les apps que l'utilisateur a ajoutées lui-même à un dossier animé : on respecte son choix
        val ajouts = Groupes.CATEGORIES.associate { it.id to Groupes.ajoutees(this, it.id) }
        val intention = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intention, 0)
            .map { it.loadLabel(packageManager).toString().trim() to it.activityInfo.packageName }
            .filter { it.second != packageName }
            .distinctBy { it.second }
            .map { AppDetectee(it.first, it.second, categorieDe(it.second, ajouts)) }
            .sortedWith(compareBy<AppDetectee>({ !it.aUneAnimation }, { it.cle }))
    }

    // La catégorie d'une app : 1) le choix de l'utilisateur, 2) notre liste d'apps connues,
    // 3) la catégorie qu'Android connaît pour cette app, 4) sinon « autres » (non répertorié).
    private fun categorieDe(pkg: String, ajouts: Map<String, Set<String>>): String {
        val code = try { packageManager.getApplicationInfo(pkg, 0).category }
                   catch (_: Exception) { ApplicationInfo.CATEGORY_UNDEFINED }
        val trouvee = Groupes.CATEGORIES.firstOrNull { pkg in (ajouts[it.id] ?: emptySet()) }
            ?: Groupes.CATEGORIES.firstOrNull { pkg in it.connues }
            ?: Groupes.CATEGORIES.firstOrNull { code in it.android }
        return trouvee?.id ?: "autres"
    }

    // Les catégories, dans l'ordre de Groupes.kt, puis « Non répertorié ». Une catégorie vide n'est pas montrée.
    private fun ranger(): List<Rayon> {
        val parCategorie = toutes.groupBy { it.categorie }
        val ids = Groupes.CATEGORIES.map { it.id } + "autres"
        return ids.mapNotNull { id -> parCategorie[id]?.let { Rayon(id, nomCategorie(id), it) } }
    }

    // Le nom d'une catégorie, dans la langue de l'app (fichiers res/values*/categories.xml).
    private fun nomCategorie(id: String): String = getString(when (id) {
        "ia" -> R.string.cat_ia
        "social" -> R.string.cat_social
        "musique" -> R.string.cat_musique
        "photo" -> R.string.cat_photo
        "jeux" -> R.string.cat_jeux
        "finance" -> R.string.cat_finance
        "trajets" -> R.string.cat_trajets
        "travail" -> R.string.cat_travail
        "infos" -> R.string.cat_infos
        else -> R.string.cat_autres
    })

    private fun icone(pkg: String): Drawable? = icones.getOrPut(pkg) {
        try { packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }
    }

    // LE DÉFILEMENT DES ICÔNES : toutes les 2,4 s, chaque barre de catégorie visible passe à l'app
    // suivante. L'ancienne icône glisse vers la gauche en s'effaçant, la nouvelle arrive par la droite.
    // (Un « Runnable » = un petit travail qu'on demande à Android de refaire plus tard.)
    private val battement = object : Runnable {
        override fun run() {
            tour++
            val glisse = px(14f).toFloat()
            for (i in 0 until liste.childCount) {
                val l = liste.getChildAt(i).tag as? LigneRayon ?: continue
                val r = l.rayon ?: continue
                if (r.apps.size < 2) continue                  // une seule app : rien à faire défiler
                val suivante = icone(r.apps[tour % r.apps.size].pkg)
                // les barres changent l'une après l'autre (90 ms d'écart), pas toutes en même temps
                l.icone.animate().alpha(0f).translationX(-glisse).setStartDelay(i * 90L).setDuration(260).withEndAction {
                    l.icone.setImageDrawable(suivante)
                    l.icone.translationX = glisse
                    l.icone.animate().alpha(1f).translationX(0f).setStartDelay(0L).setDuration(320).start()
                }.start()
            }
            principal.postDelayed(this, 2400)
        }
    }

    // Le texte gris d'une ligne d'app : en quelques mots, où en est son animation.
    private fun description(app: AppDetectee): String {
        val e = Catalogue.pour(app.pkg) ?: return getString(R.string.desc_pas_anim)
        if (!Reglages.appActive(this, app.pkg)) return getString(R.string.desc_coupee)
        if (Reglages.duree(this, app.pkg) == 0) return getString(R.string.desc_duree0)
        if (e.variantes.isEmpty()) return getString(R.string.desc_activee)
        val actives = e.variantes.size - Reglages.variantesDesactivees(this, app.pkg).size
        return if (actives <= 0) getString(R.string.desc_aucune_variante) else getString(R.string.desc_variantes, actives)
    }

    // Les éléments d'une ligne, gardés pour ne pas les rechercher à chaque fois.
    class LigneApp(val icone: ImageView, val nom: TextView, val detail: TextView, val inter: Switch)
    class LigneRayon(val nom: TextView, val detail: TextView, val icone: ImageView) { var rayon: Rayon? = null }

    // L'ADAPTATEUR fabrique (et recycle) les lignes de la liste au fur et à mesure du défilement.
    // Il y a DEUX sortes de lignes (catégorie ou app) : Android garde une réserve pour chaque sorte.
    inner class Adaptateur : BaseAdapter() {
        override fun getCount() = lignes.size
        override fun getItem(position: Int) = lignes[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (lignes[position] is Rayon) 0 else 1

        override fun getView(position: Int, ancienne: View?, parent: ViewGroup?): View {
            val x = lignes[position]
            return if (x is Rayon) vueRayon(x, ancienne) else vueApp(x as AppDetectee, ancienne)
        }

        // ---------- une barre de catégorie ----------
        private fun vueRayon(r: Rayon, ancienne: View?): View {
            val vue = if (ancienne?.tag is LigneRayon) ancienne!! else creerRayon()
            val l = vue.tag as LigneRayon
            l.rayon = r
            l.nom.text = r.nom
            l.detail.text = getString(R.string.n_apps, r.apps.size)
            // on arrête un éventuel glissement en cours, et on montre l'icône du moment
            l.icone.animate().cancel()
            l.icone.alpha = 1f
            l.icone.translationX = 0f
            l.icone.setImageDrawable(icone(r.apps[tour % r.apps.size].pkg))
            return vue
        }

        private fun creerRayon(): View {
            val nom = Style.texte(this@AppsActivity, "", 19f, Style.TEXTE, gras = true)
            val detail = Style.texte(this@AppsActivity, "", 13f, Style.MUET).apply { setPadding(0, px(2f), 0, 0) }
            val icone = ImageView(this@AppsActivity)
            val ligne = LinearLayout(this@AppsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Style.arrondi(this@AppsActivity, Style.CARTE, 22f, Style.BORD)
                minimumHeight = px(84f)
                setPadding(px(20f), px(14f), px(14f), px(14f))
                // à gauche : le nom et le nombre d'apps
                addView(LinearLayout(this@AppsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(nom); addView(detail)
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                // à droite : l'icône qui défile, puis un petit chevron (« ça s'ouvre »)
                addView(icone, LinearLayout.LayoutParams(px(48f), px(48f)))
                addView(Style.texte(this@AppsActivity, "›", 24f, Style.MUET).apply { setPadding(px(12f), 0, px(4f), px(3f)) })
            }
            ligne.tag = LigneRayon(nom, detail, icone)
            return ligne
        }

        // ---------- une ligne d'app ----------
        private fun vueApp(app: AppDetectee, ancienne: View?): View {
            val vue = if (ancienne?.tag is LigneApp) ancienne!! else creerApp()
            val l = vue.tag as LigneApp
            l.icone.setImageDrawable(icone(app.pkg))
            l.nom.text = app.nom
            l.detail.text = description(app)
            l.inter.setOnCheckedChangeListener(null)          // évite de déclencher l'ancien écouteur
            l.inter.visibility = if (app.aUneAnimation) View.VISIBLE else View.INVISIBLE
            l.inter.isChecked = Reglages.appActive(this@AppsActivity, app.pkg)
            l.inter.contentDescription = getString(R.string.anim_de, app.nom)
            l.inter.setOnCheckedChangeListener { _, coche ->
                Reglages.definirAppActive(this@AppsActivity, app.pkg, coche)
                l.detail.text = description(app)
                majResume()
            }
            vue.alpha = if (app.aUneAnimation) 1f else 0.45f
            return vue
        }

        private fun creerApp(): View {
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
            ligne.tag = LigneApp(icone, nom, detail, inter)
            return ligne
        }
    }

    companion object {
        // « Écran » -> « ecran » : minuscules et sans accents, pour une recherche tolérante.
        fun sansAccents(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    }
}
