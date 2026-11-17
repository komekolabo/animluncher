package com.will.animlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import android.widget.TextView

// L'ÉCRAN « APPS DÉTECTÉES » (17/11, demande de Will, POUR LUI : on l'ouvre par un APPUI LONG sur la roue des
// réglages, en haut à droite de l'accueil ; il n'apparaît dans aucun menu).
// Toutes les apps installées qui ont une icône, avec :
//  - un POINT DE COULEUR qui dit ce qu'AnimEngine sait faire avec l'app :
//      vert   = elle a sa propre animation d'ouverture, et elle est allumée ;
//      bleu   = elle a sa propre animation, mais elle est coupée (de base ou par toi) ;
//      ambre  = pas d'animation à elle, mais elle est rangée dans une catégorie (IA, Social…) : elle va dans les
//               dossiers animés et la démo de l'accueil sait la classer ;
//      gris   = rien de spécial (« Non répertorié ») ;
//  - son nom, et une 2e ligne qui détaille (variantes, vitesse, catégorie) ;
//  - une recherche (même façon de chercher que l'écran « Animations d'apps », voir Rayons.rang) ;
//  - la LÉGENDE en haut sert aussi de FILTRE : un appui sur une couleur ne montre que ces apps (un 2e appui : toutes) ;
//  - trois boutons de TRI sous la recherche (17/11) : par couleur (de base), de A à Z, ou les plus récemment
//    installées d'abord (avec la date d'installation sur la 2e ligne). Le choix est gardé pour la prochaine fois.
// Un appui sur une app qui a une animation ouvre ses réglages.
class CompatiblesActivity : Activity() {

    // Ce que l'app peut faire, du mieux au moins bien (c'est aussi l'ordre de la légende).
    private enum class Etat(val couleur: Int, val texte: Int) {
        ANIMEE(Color.rgb(74, 222, 128), R.string.compat_animee),
        COUPEE(Color.rgb(96, 165, 250), R.string.compat_coupee),
        CLASSEE(Color.rgb(251, 191, 36), R.string.compat_classee),
        RIEN(Color.rgb(113, 113, 122), R.string.compat_rien),
    }

    // Une app détectée : son nom, son nom technique, ce qu'elle peut faire, et le détail affiché sous son nom.
    // « installe » = le moment où l'app a été installée la 1re fois (en millisecondes, donné par Android).
    private class Ligne(val nom: String, val pkg: String, val etat: Etat, val detail: String, val installe: Long) {
        val cle = AppsActivity.sansAccents(nom)
    }

    private var toutes = listOf<Ligne>()
    private var lignes = listOf<Ligne>()
    private var cherche = ""
    private var filtre: Etat? = null                     // null = toutes les couleurs
    private var tri = TRI_COULEUR                       // l'ordre de la liste (voir les 3 boutons de tri)
    private val boutonsTri = ArrayList<TextView>()
    private val formatDate = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT)

    companion object {
        private const val TRI_COULEUR = 0               // vert, bleu, ambre, gris, puis A → Z
        private const val TRI_NOM = 1                   // A → Z
        private const val TRI_DATE = 2                  // la plus récemment installée en premier
        private const val CARNET = "compatibles"         // le petit carnet où on retient le tri choisi
    }
    private val icones = HashMap<String, Drawable?>()
    private val legende = ArrayList<TextView>()
    private lateinit var resume: TextView
    private lateinit var adaptateur: Adaptateur
    private fun px(v: Float) = Style.px(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Style.fenetreNoire(this)

        resume = Style.texte(this, "", 13f, Style.MUET).apply { setPadding(px(6f), 0, 0, px(10f)) }

        // La légende = les 4 couleurs, qui servent aussi de filtre (2 par ligne)
        val blocLegende = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (paire in Etat.values().toList().chunked(2)) {
            val rangee = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((i, e) in paire.withIndex()) {
                val puce = Style.texte(this, "●  " + getString(e.texte), 12.5f, Style.TEXTE).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(px(12f), 0, px(8f), 0)
                    isClickable = true
                    tag = e
                    setOnClickListener {
                        filtre = if (filtre == e) null else e
                        afficher()
                    }
                }
                legende.add(puce)
                rangee.addView(puce, LinearLayout.LayoutParams(0, px(38f), 1f).apply { if (i == 0) rightMargin = px(8f) })
            }
            blocLegende.addView(rangee, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = px(8f) })
        }

        // La recherche
        val recherche = EditText(this).apply {
            hint = getString(R.string.rechercher)
            setHintTextColor(Color.rgb(142, 142, 150))
            setTextColor(Style.TEXTE)
            textSize = 16f
            isSingleLine = true
            background = Style.arrondi(this@CompatiblesActivity, Style.CARTE, 16f, Style.BORD)
            setPadding(px(18f), px(13f), px(18f), px(13f))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    cherche = AppsActivity.sansAccents((s?.toString() ?: "").trim())
                    afficher()
                }
            })
        }

        // Les 3 boutons de tri, côte à côte. Le tri choisi est relu dans le carnet (la dernière fois).
        tri = getSharedPreferences(CARNET, MODE_PRIVATE).getInt("tri", TRI_COULEUR)
        val rangeeTri = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, t) in listOf(TRI_COULEUR to R.string.compat_tri_couleur, TRI_NOM to R.string.compat_tri_nom,
                TRI_DATE to R.string.compat_tri_date).withIndex()) {
            val bouton = Style.texte(this, getString(t.second), 13f, Style.TEXTE).apply {
                gravity = Gravity.CENTER
                isClickable = true
                tag = t.first
                setOnClickListener {
                    tri = t.first
                    getSharedPreferences(CARNET, MODE_PRIVATE).edit().putInt("tri", tri).apply()
                    afficher()
                }
            }
            boutonsTri.add(bouton)
            rangeeTri.addView(bouton, LinearLayout.LayoutParams(0, px(36f), 1f).apply { if (i < 2) rightMargin = px(8f) })
        }

        adaptateur = Adaptateur()
        val liste = ListView(this).apply {
            adapter = adaptateur
            divider = null
            dividerHeight = px(8f)
            selector = ColorDrawable(Color.TRANSPARENT)
            setPadding(0, px(12f), 0, px(20f))
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            // un appui sur une app qui a une animation : ses réglages
            setOnItemClickListener { _, _, position, _ ->
                val l = lignes[position]
                if (l.etat == Etat.ANIMEE || l.etat == Etat.COUPEE) {
                    startActivity(Intent(this@CompatiblesActivity, ReglageAppActivity::class.java)
                        .putExtra("pkg", l.pkg).putExtra("nom", l.nom))
                }
            }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Style.NOIR)
            addView(Style.entete(this@CompatiblesActivity, getString(R.string.compat_titre)))
            addView(LinearLayout(this@CompatiblesActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(16f), 0, px(16f), 0)
                addView(resume)
                addView(blocLegende)
                addView(recherche)
                addView(rangeeTri, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(10f) })
                addView(liste, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    // On recharge à chaque retour (une animation a pu être allumée ou coupée dans ses réglages).
    // La liste est préparée en arrière-plan : lire toutes les apps du téléphone prend un instant.
    override fun onResume() {
        super.onResume()
        Thread {
            val l = charger()
            runOnUiThread { if (!isDestroyed) { toutes = l; afficher() } }
        }.start()
    }

    // Toutes les apps qui ont une icône, avec leur état et leur détail.
    private fun charger(): List<Ligne> {
        val pm = packageManager
        val ajouts = Rayons.ajouts(this)
        val intention = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val vues = HashSet<String>()
        val resultat = ArrayList<Ligne>()
        for (info in pm.queryIntentActivities(intention, 0)) {
            val pkg = info.activityInfo.packageName
            if (pkg == packageName || !vues.add(pkg)) continue        // pas nous-mêmes, et chaque app une seule fois
            val nom = info.loadLabel(pm).toString().trim()
            val categorie = Rayons.categorieDe(this, pkg, nom, ajouts)
            val nomCategorie = Rayons.nom(this, categorie)
            val entree = Catalogue.pour(pkg)
            val etat: Etat
            val morceaux = ArrayList<String>()
            if (entree != null) {
                val allumee = Reglages.appActive(this, pkg) && Reglages.duree(this, pkg) != 0
                etat = if (allumee) Etat.ANIMEE else Etat.COUPEE
                morceaux.add(getString(if (allumee) R.string.compat_animation else R.string.compat_animation_coupee))
                if (entree.variantes.isNotEmpty()) morceaux.add(getString(R.string.compat_variantes, entree.variantes.size))
                if (allumee) morceaux.add(vitesse(pkg, entree))
            } else {
                etat = if (categorie == Rayons.AUTRES) Etat.RIEN else Etat.CLASSEE
                if (etat == Etat.CLASSEE) morceaux.add(getString(R.string.compat_sans_animation))
            }
            morceaux.add(nomCategorie)
            // la date de 1re installation (si Android refuse de la donner : 0, l'app ira en fin de liste)
            val installe = try { pm.getPackageInfo(pkg, 0).firstInstallTime } catch (_: Exception) { 0L }
            resultat.add(Ligne(nom, pkg, etat, morceaux.joinToString(" · "), installe))
        }
        // l'ordre de la liste est choisi plus tard, dans afficher() (selon le bouton de tri)
        return resultat
    }

    // Le nom de la vitesse choisie pour une app (Rapide, Normale, Lente), comme dans ses réglages.
    private fun vitesse(pkg: String, e: Entree): String {
        val choisie = Reglages.duree(this, pkg)
        val rapport = if (choisie < 0) Catalogue.VITESSE_DEFAUT else choisie.toFloat() / e.dureeDefaut
        return getString(if (rapport < 0.8f) R.string.rapide else if (rapport > 1.3f) R.string.lente else R.string.normale)
    }

    // Applique la recherche et le filtre de couleur, puis rafraîchit la liste et les compteurs.
    private fun afficher() {
        var l = toutes
        if (filtre != null) l = l.filter { it.etat == filtre }
        // 1) on range selon le tri choisi
        l = when (tri) {
            TRI_NOM -> l.sortedBy { it.cle }
            TRI_DATE -> l.sortedWith(compareByDescending<Ligne> { it.installe }.thenBy { it.cle })
            else -> l.sortedWith(compareBy<Ligne>({ it.etat.ordinal }, { it.cle }))
        }
        // 2) la recherche ne garde que les apps qui correspondent. En tri « Couleur », les meilleures
        //    correspondances passent devant (comme avant) ; en tri A → Z ou Récentes, on garde l'ordre choisi.
        if (cherche.isNotEmpty()) {
            val trouvees = l.map { it to Rayons.rang(it.cle, cherche) }.filter { it.second >= 0 }
            l = if (tri == TRI_COULEUR) trouvees.sortedWith(compareBy<Pair<Ligne, Int>>({ it.second }, { it.first.cle })).map { it.first }
                else trouvees.map { it.first }
        }
        lignes = l
        adaptateur.notifyDataSetChanged()
        // le résumé : combien d'apps en tout, et combien de chaque couleur (dans la légende)
        resume.text = getString(R.string.compat_resume, toutes.size, toutes.count { it.etat == Etat.ANIMEE || it.etat == Etat.COUPEE })
        // le bouton de tri choisi a un fond clair, les autres le fond des cartes
        for (b in boutonsTri) {
            val actif = b.tag == tri
            b.background = Style.arrondi(this, if (actif) Color.argb(40, 255, 255, 255) else Style.CARTE, 12f,
                if (actif) Style.DOUX else Style.BORD)
            b.setTextColor(if (actif) Style.TEXTE else Style.MUET)
        }
        for (puce in legende) {
            val e = puce.tag as Etat
            val n = toutes.count { it.etat == e }
            val actif = filtre == e
            puce.text = "●  " + getString(e.texte) + "  " + n
            // le point garde sa couleur ; le texte est blanc, la puce choisie a un fond clair
            val s = android.text.SpannableString(puce.text)
            s.setSpan(android.text.style.ForegroundColorSpan(e.couleur), 0, 1, 0)
            puce.text = s
            puce.background = Style.arrondi(this, if (actif) Color.argb(40, 255, 255, 255) else Style.CARTE, 14f,
                if (actif) e.couleur else Style.BORD)
        }
    }

    // ---------------------------------- LA LISTE ----------------------------------
    private inner class Adaptateur : BaseAdapter() {
        override fun getCount() = lignes.size
        override fun getItem(position: Int): Any = lignes[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, recyclee: View?, parent: ViewGroup?): View {
            val l = lignes[position]
            // une ligne : [point] [icône] [nom / détail]
            val vue = (recyclee as? LinearLayout) ?: LinearLayout(this@CompatiblesActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Style.arrondi(this@CompatiblesActivity, Style.CARTE, 16f, Style.BORD)
                setPadding(px(14f), px(10f), px(14f), px(10f))
                addView(View(this@CompatiblesActivity), LinearLayout.LayoutParams(px(12f), px(12f)).apply { rightMargin = px(12f) })
                addView(ImageView(this@CompatiblesActivity), LinearLayout.LayoutParams(px(36f), px(36f)).apply { rightMargin = px(12f) })
                addView(LinearLayout(this@CompatiblesActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Style.texte(this@CompatiblesActivity, "", 15.5f, Style.TEXTE, gras = true).apply { isSingleLine = true })
                    addView(Style.texte(this@CompatiblesActivity, "", 12f, Style.MUET).apply { isSingleLine = true })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            (vue.getChildAt(0)).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(l.etat.couleur)
            }
            (vue.getChildAt(1) as ImageView).setImageDrawable(icone(l.pkg))
            val textes = vue.getChildAt(2) as LinearLayout
            (textes.getChildAt(0) as TextView).text = l.nom
            // en tri « Récentes », la date d'installation passe en tête de la 2e ligne
            (textes.getChildAt(1) as TextView).text =
                if (tri == TRI_DATE && l.installe > 0) formatDate.format(java.util.Date(l.installe)) + " · " + l.detail
                else l.detail
            return vue
        }
    }

    // L'icône d'une app (gardée en mémoire après le premier chargement).
    private fun icone(pkg: String): Drawable? = icones.getOrPut(pkg) {
        try { packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }
    }
}
