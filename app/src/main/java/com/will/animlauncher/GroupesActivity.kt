package com.will.animlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

// L'ÉCRAN « LOGOS ANIMÉS » (carte 2 de l'accueil) : tout ce qui se pose sur l'écran d'accueil du
// téléphone. Refait le 09/11 (demande de Will) : la longue liste de cartes n'était pas pratique.
//
// L'écran a maintenant 5 états, et « retour » remonte d'un cran à chaque fois :
//
//   ACCUEIL : l'écran coupé en deux, DE BIAIS (voir la classe DeuxZones, en bas du fichier).
//      ├─ en haut : DOSSIERS  → une barre par dossier animé (IA, Jeux…)
//      │               └─ DOSSIER : le dossier choisi (le voir, le poser sur l'accueil, choisir ses apps)
//      └─ en bas : ICONES    → une recherche, puis une barre par catégorie d'apps (et le logo d'AnimLauncher)
//                      └─ APPS : les apps de la catégorie ; un appui pose l'icône animée de l'app
//   Dans ICONES, dès qu'on tape une lettre, les barres laissent la place aux apps trouvées.
//
// Les dossiers viennent de Groupes.kt (widget : WidgetGroupe). Les icônes animées d'une seule app
// sont le widget « galet » (WidgetApp.kt) ; le rangement de toutes les apps vient de Rayons.kt.
class GroupesActivity : Activity() {

    companion object {
        const val ACCUEIL = 0
        const val DOSSIERS = 1
        const val DOSSIER = 2
        const val ICONES = 3
        const val APPS = 4
    }

    private var etat = ACCUEIL
    private var choisi = ""          // l'identifiant du dossier ou de la catégorie ouverte
    private fun px(v: Float) = Style.px(this, v)

    // Ce qu'on sait du téléphone (recalculé à chaque retour sur l'écran) :
    private var dossiers = listOf<Pair<Groupes.Categorie, List<String>>>()   // les dossiers d'au moins 2 apps, et leurs apps
    private var rayons: Map<String, List<String>> = emptyMap()                // toutes les apps, par catégorie

    // Le défilement des icônes à droite des barres : chaque icône, et la liste des apps qu'elle fait défiler.
    private val defilants = ArrayList<Pair<ImageView, List<String>>>()
    // Les mini dossiers à droite des barres de dossiers : 4 icônes à la fois (une page de 4, puis la suivante).
    private val defilants4 = ArrayList<Pair<MiniDossier, List<String>>>()
    private var cherche = ""         // ce qui est tapé dans la recherche des icônes d'apps (en minuscules, sans accents)
    private var chercheTape = ""     // le même texte, tel qu'il a été tapé (pour le remettre dans la case)
    private val icones = HashMap<String, Drawable?>()
    private val principal = Handler(Looper.getMainLooper())
    private var tour = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Style.fenetreNoire(this)
    }

    // L'écran est (re)construit à chaque retour ici : on voit tout de suite l'app qu'on vient d'ajouter.
    override fun onResume() {
        super.onResume()
        rayons = Rayons.ranger(this)
        dossiers = Groupes.CATEGORIES.mapNotNull { cat ->
            val apps = Groupes.tous(this, cat.id)
            if (apps.size < 2) null else cat to apps      // un dossier d'une seule app n'a pas d'intérêt
        }
        construire()
        principal.removeCallbacks(battement)
        principal.postDelayed(battement, 2400)
    }

    override fun onPause() {
        super.onPause()
        principal.removeCallbacks(battement)
    }

    // ================= LA NAVIGATION ENTRE LES 5 ÉTATS =================

    private fun aller(nouvelEtat: Int, id: String = "") {
        if (nouvelEtat == ACCUEIL) { cherche = ""; chercheTape = "" }      // en revenant à l'écran coupé en deux, on oublie la recherche
        etat = nouvelEtat
        choisi = id
        construire()
    }

    // La flèche de l'en-tête et le bouton « retour » du téléphone : on remonte d'un cran.
    private fun reculer() {
        when (etat) {
            DOSSIER -> aller(DOSSIERS)
            APPS -> aller(ICONES)
            DOSSIERS, ICONES -> aller(ACCUEIL)
            else -> finish()
        }
    }

    @Deprecated("Remplacé par OnBackInvokedCallback sur Android 13+, mais marche toujours ici")
    override fun onBackPressed() {
        if (etat != ACCUEIL) reculer() else super.onBackPressed()
    }

    // Fabrique l'écran de l'état en cours : l'en-tête (flèche + titre), puis le contenu.
    private fun construire() {
        defilants.clear()
        defilants4.clear()
        val corps: View
        val titre: String
        when (etat) {
            DOSSIERS -> { titre = getString(R.string.dossiers_animes); corps = ecranDossiers() }
            DOSSIER -> { titre = Rayons.nom(this, choisi); corps = ecranDossier() }
            ICONES -> { titre = getString(R.string.icones_animees); corps = ecranIcones() }
            APPS -> { titre = Rayons.nom(this, choisi); corps = ecranApps() }
            else -> { titre = getString(R.string.carte_logos); corps = ecranAccueil() }
        }
        val entete = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4f), 0, px(16f), 0)
            addView(Icone(this@GroupesActivity, Icone.RETOUR).apply {
                contentDescription = getString(R.string.retour)
                setOnClickListener { reculer() }
            }, LinearLayout.LayoutParams(px(48f), px(48f)))
            addView(Style.texte(this@GroupesActivity, titre, 20f, Style.TEXTE, gras = true).apply { setPadding(px(6f), 0, 0, 0) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Style.NOIR)
            addView(entete, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(64f)))
            addView(corps, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    // ================= ÉTAT 0 : L'ÉCRAN COUPÉ EN DEUX =================

    private fun ecranAccueil(): View {
        // les icônes que le dessin fait vivre : 4 apps par dossier (en haut), quelques apps (en bas)
        val parDossier = dossiers.map { (cat, _) -> Groupes.apps(this, cat.id).take(4).mapNotNull { charger(it) } }
            .filter { it.isNotEmpty() }
        val quelquesApps = rayons.values.flatMap { it.take(2) }.take(14).mapNotNull { charger(it) }
        val dessin = DeuxZones(this, parDossier, quelquesApps, { aller(DOSSIERS) }, { aller(ICONES) }).apply {
            contentDescription = getString(R.string.dossiers_animes) + ", " + getString(R.string.icones_animees)
        }
        // Les textes sont posés PAR-DESSUS le dessin (ils ne prennent pas les appuis : c'est le dessin
        // qui reçoit le doigt et qui regarde de quel côté de la coupe il est tombé).
        fun bloc(titre: String, note: String) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24f), px(22f), 0, px(22f))
            addView(Style.texte(this@GroupesActivity, titre, 28f, Style.TEXTE, gras = true).apply { setLineSpacing(0f, 0.95f) })
            addView(Style.texte(this@GroupesActivity, note, 13.5f, Style.MUET).apply { setLineSpacing(0f, 1.15f); setPadding(0, px(8f), 0, 0) })
        }
        // les textes n'occupent que 60 % de la largeur : la droite est laissée aux icônes du dessin
        val largeurTexte = ((resources.displayMetrics.widthPixels - px(32f)) * 0.6f).toInt()
        return FrameLayout(this).apply {
            setPadding(px(16f), px(4f), px(16f), px(20f))
            addView(dessin)
            addView(bloc(getString(R.string.dossiers_animes), getString(R.string.dossiers_note)),
                FrameLayout.LayoutParams(largeurTexte, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
            addView(bloc(getString(R.string.icones_animees), getString(R.string.icones_zone_note)),
                FrameLayout.LayoutParams(largeurTexte, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START))
        }
    }

    // ================= ÉTAT 1 : LES DOSSIERS, EN BARRES =================

    private fun ecranDossiers(): View {
        val colonne = colonne()
        colonne.addView(note(getString(R.string.dossiers_choix)))
        for ((cat, _) in dossiers) {
            val dedans = Groupes.apps(this, cat.id)        // les apps du dossier, sans celles qu'on a décochées
            colonne.addView(barre(Rayons.nom(this, cat.id), getString(R.string.n_apps, dedans.size), miniDossier(dedans)) {
                aller(DOSSIER, cat.id)
            })
        }
        if (dossiers.isEmpty()) colonne.addView(Style.texte(this, getString(R.string.aucun_groupe), 15f, Style.DOUX)
            .apply { setPadding(px(6f), 0, 0, 0) })
        return defilement(colonne)
    }

    // ================= ÉTAT 2 : UN DOSSIER =================

    private fun ecranDossier(): View {
        val cat = Groupes.categorie(choisi) ?: return ecranDossiers()
        val liste = Groupes.tous(this, cat.id)
        val retirees = Groupes.retirees(this, cat.id)
        val colonne = colonne()

        // la carte du haut : un aperçu des icônes, puis les deux actions
        val carte = Style.carte(this)
        carte.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, px(14f))
            for (pkg in liste.filter { it !in retirees }.take(7)) {
                addView(ImageView(this@GroupesActivity).apply { setImageDrawable(charger(pkg)) },
                    LinearLayout.LayoutParams(px(32f), px(32f)).apply { rightMargin = px(8f) })
            }
        })
        carte.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            // « Voir » : le dossier tel qu'il s'ouvrira depuis l'accueil
            addView(Style.boutonDiscret(this@GroupesActivity, getString(R.string.voir)) {
                startActivity(Intent(this@GroupesActivity, DossierActivity::class.java).putExtra("groupe", cat.id))
            }, LinearLayout.LayoutParams(0, px(46f), 1f))
            addView(Style.boutonBlanc(this@GroupesActivity, getString(R.string.mettre_accueil)) {
                if (!WidgetGroupe.proposer(this@GroupesActivity, cat.id)) aideWidget()
            }, LinearLayout.LayoutParams(0, px(46f), 2f).apply { leftMargin = px(8f) })
        })
        colonne.addView(carte)

        // la carte du bas : une case à cocher par app, et « Ajouter une app »
        val carteApps = Style.carte(this)
        carteApps.addView(Style.titreCarte(this, getString(R.string.apps_du_dossier)))
        val teinte = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Style.TEXTE, Color.rgb(113, 113, 122)))
        for (pkg in liste) {
            carteApps.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(ImageView(this@GroupesActivity).apply { setImageDrawable(charger(pkg)) },
                    LinearLayout.LayoutParams(px(30f), px(30f)).apply { rightMargin = px(10f) })
                addView(CheckBox(this@GroupesActivity).apply {
                    text = Groupes.nomApp(this@GroupesActivity, pkg)
                    textSize = 15f
                    setTextColor(Style.TEXTE)
                    buttonTintList = teinte
                    isChecked = pkg !in retirees
                    setOnCheckedChangeListener { _, coche -> Groupes.definir(this@GroupesActivity, cat.id, pkg, coche) }
                }, LinearLayout.LayoutParams(0, px(46f), 1f))
            })
        }
        carteApps.addView(Style.boutonDiscret(this, getString(R.string.ajouter_app)) {
            startActivity(Intent(this, ChoixAppActivity::class.java).putExtra("groupe", cat.id))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(46f)).apply { topMargin = px(6f) })
        colonne.addView(carteApps)
        return defilement(colonne)
    }

    // ================= ÉTAT 3 : LES ICÔNES ANIMÉES, PAR CATÉGORIE =================

    // En haut, une recherche (elle reste en place) ; dessous, la liste, refaite à chaque lettre tapée.
    private fun ecranIcones(): View {
        val colonne = colonne()
        val recherche = EditText(this).apply {
            hint = getString(R.string.rechercher)
            setHintTextColor(Color.rgb(142, 142, 150))
            setTextColor(Style.TEXTE)
            textSize = 16f
            isSingleLine = true
            background = Style.arrondi(this@GroupesActivity, Style.CARTE, 16f, Style.BORD)
            setPadding(px(18f), px(13f), px(18f), px(13f))
            setText(chercheTape)                 // en revenant ici, on retrouve ce qu'on avait tapé
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    chercheTape = s?.toString() ?: ""
                    cherche = AppsActivity.sansAccents(chercheTape.trim())
                    remplirIcones(colonne)
                }
            })
        }
        remplirIcones(colonne)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(recherche, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { leftMargin = px(16f); rightMargin = px(16f); bottomMargin = px(12f) })
            addView(defilement(colonne), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    // Remplit la liste sous la recherche : les barres de catégories, ou les apps trouvées si on cherche.
    private fun remplirIcones(colonne: LinearLayout) {
        colonne.removeAllViews()
        defilants.clear()
        colonne.addView(note(getString(R.string.icones_note)))
        if (cherche.isNotEmpty()) {
            // toutes les apps, avec leur nom ; on garde celles qui correspondent, les meilleures d'abord
            val trouvees = rayons.values.flatten().distinct()
                .map { it to AppsActivity.sansAccents(Groupes.nomApp(this, it)) }
                .map { Triple(it.first, it.second, Rayons.rang(it.second, cherche)) }
                .filter { it.third >= 0 }
                .sortedWith(compareBy({ it.third }, { it.second }))
            for (t in trouvees) colonne.addView(ligneApp(t.first))
            return
        }
        // en premier : le logo animé d'AnimLauncher lui-même (voir LogoWidget.kt)
        colonne.addView(barre(getString(R.string.app_name), getString(R.string.logo_anime_note), LogoAnime(this)) {
            if (!LogoWidget.proposer(this)) aideWidget()
        })
        for ((id, apps) in rayons) {
            colonne.addView(barre(Rayons.nom(this, id), getString(R.string.n_apps, apps.size), iconeDefilante(apps)) {
                aller(APPS, id)
            })
        }
    }

    // ================= ÉTAT 4 : LES APPS D'UNE CATÉGORIE =================

    private fun ecranApps(): View {
        val colonne = colonne()
        colonne.addView(note(getString(R.string.icones_note)))
        for (pkg in rayons[choisi] ?: emptyList()) colonne.addView(ligneApp(pkg))
        return defilement(colonne)
    }

    // ================= LES PETITES PIÈCES COMMUNES =================

    // UNE LIGNE D'APP : son icône, son nom, et un « + ». Un appui propose de poser son icône animée.
    private fun ligneApp(pkg: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = Style.arrondi(this@GroupesActivity, Style.CARTE, 18f, Style.BORD)
        minimumHeight = px(64f)
        setPadding(px(14f), px(10f), px(12f), px(10f))
        appuyable(this)
        addView(ImageView(this@GroupesActivity).apply { setImageDrawable(charger(pkg)) },
            LinearLayout.LayoutParams(px(40f), px(40f)))
        addView(Style.texte(this@GroupesActivity, Groupes.nomApp(this@GroupesActivity, pkg), 16f, Style.TEXTE)
            .apply { setPadding(px(14f), 0, px(8f), 0) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        // le petit « + » rond : il rappelle qu'un appui pose l'icône sur l'accueil
        addView(Style.texte(this@GroupesActivity, "+", 20f, Style.TEXTE).apply {
            gravity = Gravity.CENTER
            background = Style.arrondi(this@GroupesActivity, Color.argb(20, 255, 255, 255), 18f, Color.rgb(63, 63, 70))
        }, LinearLayout.LayoutParams(px(36f), px(36f)))
        setOnClickListener { if (!WidgetApp.proposer(this@GroupesActivity, pkg)) aideWidget() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = px(8f) }
    }

    private fun colonne() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(16f), px(4f), px(16f), px(32f))
    }

    private fun defilement(contenu: View) = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        addView(contenu)
    }

    // La phrase grise en haut d'une liste.
    private fun note(t: String) = Style.texte(this, t, 13f, Style.MUET).apply { setPadding(px(6f), 0, px(6f), px(14f)) }

    // Rend une ligne « appuyable » : l'onde d'appui d'Android, coupée selon l'arrondi de la ligne.
    private fun appuyable(v: View) {
        val a = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        v.foreground = a.getDrawable(0)
        a.recycle()
        v.outlineProvider = ViewOutlineProvider.BACKGROUND
        v.clipToOutline = true
        v.isClickable = true
    }

    // UNE BARRE : à gauche un nom et un détail, à droite une image (icône qui défile, ou logo), puis un chevron.
    private fun barre(nom: String, detail: String, droite: View, action: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = Style.arrondi(this@GroupesActivity, Style.CARTE, 22f, Style.BORD)
        minimumHeight = px(84f)
        setPadding(px(20f), px(14f), px(14f), px(14f))
        appuyable(this)
        addView(LinearLayout(this@GroupesActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, px(10f), 0)
            addView(Style.texte(this@GroupesActivity, nom, 19f, Style.TEXTE, gras = true))
            addView(Style.texte(this@GroupesActivity, detail, 13f, Style.MUET).apply { setPadding(0, px(2f), 0, 0) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val cote = if (droite is MiniDossier) 56f else 48f       // le mini dossier est un peu plus grand
        addView(droite, LinearLayout.LayoutParams(px(cote), px(cote)))
        addView(Style.texte(this@GroupesActivity, "›", 24f, Style.MUET).apply { setPadding(px(12f), 0, px(4f), px(3f)) })
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = px(10f) }
    }

    // L'icône à droite d'une barre : elle montre une app de la liste, puis la suivante, etc.
    // (on l'inscrit dans « defilants » : c'est le « battement », plus bas, qui la fait changer)
    private fun iconeDefilante(apps: List<String>): ImageView = ImageView(this).apply {
        if (apps.isNotEmpty()) setImageDrawable(icone(apps[tour % apps.size]))
        defilants.add(this to apps)
    }

    // Le mini dossier à droite d'une barre de dossier : les 4 premières apps, puis les 4 suivantes…
    private fun miniDossier(apps: List<String>): MiniDossier = MiniDossier(this).apply {
        montrer(page(apps, tour))
        defilants4.add(this to apps)
    }

    // Les 4 apps de la « page » n° tour (on recommence au début quand on a tout vu).
    private fun page(apps: List<String>, tour: Int): List<Drawable?> {
        if (apps.isEmpty()) return emptyList()
        val pages = (apps.size + 3) / 4                    // le nombre de pages de 4, arrondi au-dessus
        val p = tour % pages
        return apps.drop(p * 4).take(4).map { icone(it) }
    }

    // LE DÉFILEMENT : toutes les 2,4 s, l'ancienne icône glisse vers la gauche en s'effaçant,
    // la nouvelle arrive par la droite. Les barres changent l'une après l'autre (90 ms d'écart).
    private val battement = object : Runnable {
        override fun run() {
            tour++
            val glisse = px(14f).toFloat()
            for ((i, d) in defilants.withIndex()) {
                val vue = d.first
                val apps = d.second
                if (apps.size < 2) continue
                val suivante = icone(apps[tour % apps.size])
                vue.animate().alpha(0f).translationX(-glisse).setStartDelay(i * 90L).setDuration(260).withEndAction {
                    vue.setImageDrawable(suivante)
                    vue.translationX = glisse
                    vue.animate().alpha(1f).translationX(0f).setStartDelay(0L).setDuration(320).start()
                }.start()
            }
            // les mini dossiers : tout le carré s'efface, prend les 4 apps suivantes, et revient
            for ((i, d) in defilants4.withIndex()) {
                val mini = d.first
                val apps = d.second
                if (apps.size <= 4) continue                   // une seule page : rien à faire défiler
                val suivantes = page(apps, tour)
                mini.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setStartDelay(i * 90L).setDuration(260).withEndAction {
                    mini.montrer(suivantes)
                    mini.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0L).setDuration(320).start()
                }.start()
            }
            principal.postDelayed(this, 2400)
        }
    }

    // L'icône d'une app, gardée en réserve (pour les icônes qui défilent, redemandées sans arrêt).
    private fun icone(pkg: String): Drawable? = icones.getOrPut(pkg) { charger(pkg) }

    // L'icône d'une app, chargée à neuf (chaque image de l'écran a ainsi la sienne).
    private fun charger(pkg: String): Drawable? =
        try { packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }

    // Si le téléphone ne sait pas proposer le widget tout seul : on explique où le trouver.
    private fun aideWidget() {
        Toast.makeText(this, getString(R.string.aide_widget, getString(R.string.app_name)), Toast.LENGTH_LONG).show()
    }
}

// L'ÉCRAN COUPÉ EN DEUX, DE BIAIS. Un seul dessin, avec deux zones séparées par une coupe qui
// monte de la gauche vers la droite :
//   - EN HAUT, les dossiers : un mini dossier (4 icônes dans un carré) qui change toutes les 2,6 s ;
//   - EN BAS, les icônes d'apps : une grande icône qui flotte et laisse sa place à la suivante.
// Un appui : on regarde de quel côté de la coupe le doigt est tombé, et on lance la bonne action.
// (Les titres ne sont pas dessinés ici : ce sont des textes posés par-dessus, voir ecranAccueil.)
class DeuxZones(context: Context, private val dossiers: List<List<Drawable>>, private val apps: List<Drawable>,
                private val enHaut: () -> Unit, private val enBas: () -> Unit) : DessinVivant(context) {

    // « CornerPathEffect » : arrondit tous les coins d'une forme (ici de 26 dp).
    private val arrondi = CornerPathEffect(26f * dp)
    private val pZone = Paint(Paint.ANTI_ALIAS_FLAG).apply { pathEffect = arrondi }
    private val pBord = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp; color = Style.BORD; pathEffect = arrondi
    }
    private val pAppui = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(16, 255, 255, 255); pathEffect = arrondi }
    private val pCase = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(26, 26, 30) }
    private val haut = Path()
    private val bas = Path()
    private var appui = 0            // la zone sous le doigt : 0 = aucune, 1 = le haut, 2 = le bas

    // La hauteur de la coupe à la position x : 57 % de la hauteur à gauche, 43 % à droite.
    private fun coupe(x: Float, w: Float, h: Float): Float = h * 0.57f - h * 0.14f * (x / w)

    override fun dessiner(c: Canvas, w: Float, h: Float, t: Float) {
        if (w <= 0f || h <= 0f) return
        val e = 5f * dp                       // la moitié du vide entre les deux zones
        val yg = coupe(0f, w, h)              // la coupe à gauche…
        val yd = coupe(w, w, h)               // … et à droite

        // 1) les deux formes à 4 côtés, de part et d'autre de la coupe
        haut.reset(); haut.moveTo(0f, 0f); haut.lineTo(w, 0f); haut.lineTo(w, yd - e); haut.lineTo(0f, yg - e); haut.close()
        bas.reset(); bas.moveTo(0f, yg + e); bas.lineTo(w, yd + e); bas.lineTo(w, h); bas.lineTo(0f, h); bas.close()
        pZone.shader = LinearGradient(0f, 0f, 0f, yg, Color.rgb(18, 20, 26), Color.rgb(11, 11, 13), Shader.TileMode.CLAMP)
        c.drawPath(haut, pZone)
        pZone.shader = LinearGradient(0f, yd, 0f, h, Color.rgb(11, 11, 13), Color.rgb(23, 16, 13), Shader.TileMode.CLAMP)
        c.drawPath(bas, pZone)
        if (appui == 1) c.drawPath(haut, pAppui)      // un léger voile clair sous le doigt (pas de flash)
        if (appui == 2) c.drawPath(bas, pAppui)
        c.drawPath(haut, pBord)
        c.drawPath(bas, pBord)

        // 2) en haut à droite : le mini dossier. Un carré arrondi et, dedans, 4 icônes (2 × 2).
        val cote = min(96f * dp, w * 0.27f)
        val dx = w - 28f * dp - cote / 2f
        val dy = (yd - e) * 0.56f
        c.drawRoundRect(dx - cote / 2f, dy - cote / 2f, dx + cote / 2f, dy + cote / 2f, cote * 0.28f, cote * 0.28f, pCase)
        if (dossiers.isNotEmpty()) {
            val duree = 2.6f
            val quatre = dossiers[floor(t / duree).toInt() % dossiers.size]
            val p = (t % duree) / duree                                   // l'avancement du tour, de 0 à 1
            val alpha = (min(p, 1f - p) / 0.12f).coerceIn(0f, 1f)         // apparaît au début, s'efface à la fin
            c.saveLayerAlpha(dx - cote / 2f, dy - cote / 2f, dx + cote / 2f, dy + cote / 2f, (255 * alpha).toInt())
            val m = cote * 0.16f                                          // la demi-taille d'une petite icône
            for ((i, d) in quatre.withIndex()) {
                val ix = dx + (if (i % 2 == 0) -1f else 1f) * cote * 0.21f
                val iy = dy + (if (i < 2) -1f else 1f) * cote * 0.21f
                d.setBounds((ix - m).toInt(), (iy - m).toInt(), (ix + m).toInt(), (iy + m).toInt())
                d.draw(c)
            }
            c.restore()
        }

        // 3) en bas à droite : une grande icône qui flotte. Elle arrive par la droite, repart par la gauche.
        if (apps.isNotEmpty()) {
            val duree = 2.4f
            val d = apps[floor(t / duree).toInt() % apps.size]
            val p = (t % duree) / duree
            val alpha = (min(p, 1f - p) / 0.12f).coerceIn(0f, 1f)
            val glisse = (if (p < 0.5f) 1f - alpha else alpha - 1f) * 14f * dp
            val m = min(42f * dp, w * 0.115f)
            val ax = w - 28f * dp - cote / 2f + glisse
            val ay = yd + e + (h - yd - e) * 0.42f + sin(t * 1.3f) * 3f * dp
            c.saveLayerAlpha(ax - m, ay - m, ax + m, ay + m, (255 * alpha).toInt())
            d.setBounds((ax - m).toInt(), (ay - m).toInt(), (ax + m).toInt(), (ay + m).toInt())
            d.draw(c)
            c.restore()
        }
    }

    // LE DOIGT : on retient la zone touchée ; si le doigt se lève dans la même zone, on lance l'action.
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val zone = if (ev.y < coupe(ev.x, width.toFloat(), height.toFloat())) 1 else 2
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> appui = zone
            MotionEvent.ACTION_MOVE -> if (zone != appui) appui = 0       // le doigt a quitté la zone : on annule
            MotionEvent.ACTION_CANCEL -> appui = 0
            MotionEvent.ACTION_UP -> {
                val touchee = appui
                appui = 0
                if (touchee == zone) { if (touchee == 1) enHaut() else enBas() }
            }
        }
        return true
    }
}

// UN MINI DOSSIER : un carré arrondi gris foncé avec 4 petites icônes rangées 2 par 2,
// comme un dossier sur l'écran d'accueil. « montrer » change les 4 icônes.
class MiniDossier(context: Context) : LinearLayout(context) {
    private val cases = List(4) { ImageView(context) }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val p = Style.px(context, 5f)
        setPadding(p, p, p, p)
        background = Style.arrondi(context, Color.rgb(26, 26, 30), 14f, Style.BORD)
        for (rangee in 0..1) {
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                for (k in 0..1) {
                    addView(cases[rangee * 2 + k], LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                        .apply { val m = Style.px(context, 2f); setMargins(m, m, m, m) })
                }
            }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    fun montrer(icones: List<Drawable?>) {
        for ((i, case) in cases.withIndex()) case.setImageDrawable(icones.getOrNull(i))
    }
}
