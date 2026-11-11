package com.will.animlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.min

// AJOUTER UNE APP À UN DOSSIER (refonte du 11/11, « A · Feuille », choisie par Will sur maquette).
// Avant : une simple liste bleue plein écran. Maintenant : une FEUILLE sombre, teintée aux couleurs du dossier,
// qui monte du bas de l'écran par-dessus un voile sombre. Dedans, de haut en bas :
//  - une petite poignée (on peut glisser la feuille vers le bas pour la fermer, comme la réaction sonore) ;
//  - l'en-tête : le LOGO DU DOSSIER (celui de Finance pour Finance, etc. — il joue son petit geste à l'ouverture),
//    « Ajouter à Finance », et la place prise (« 5 / 60 » avec une petite jauge) ;
//  - la barre de recherche (une pilule) ;
//  - « Suggestions » : les apps de la même catégorie qui ne sont pas (ou plus) dans le dossier — cachée s'il n'y
//    en a aucune ;
//  - « Toutes les apps » : les autres, en grille de 4.
// Un appui sur une app : son icône se tasse puis rebondit, une copie s'envole jusqu'au logo du dossier (qui refait
// son geste), la feuille redescend, et le dossier se rouvre avec la nouvelle app dedans.
// On arrive ici par le « + » du dossier animé (DossierActivity), ou par le bouton « ＋ Ajouter une app » de l'écran
// des dossiers. L'écran est TRANSPARENT (thème Theme.Feuille, voir res/values/themes.xml) : on voit ce qu'il y a derrière.
class ChoixAppActivity : Activity() {

    private var groupe = ""
    private var depuisDossier = false
    private var fini = false              // vrai dès qu'on a commencé à fermer (pour ne pas fermer deux fois)

    private lateinit var racine: FrameLayout
    private lateinit var voile: View
    private lateinit var feuille: Feuille
    private lateinit var logo: LogoVue

    private fun px(dp: Float) = Style.px(this, dp)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupe = intent.getStringExtra("groupe") ?: run { finish(); return }
        depuisDossier = intent.getBooleanExtra("depuisDossier", false)
        val teinte = Teinte.de(this, groupe)
        val nomGroupe = Rayons.nom(this, groupe)

        // Les apps déjà dans le dossier. Dossier plein (60 apps) : on ne peut plus rien ajouter.
        val dejaDedans = Groupes.apps(this, groupe).toSet()
        if (dejaDedans.size >= Groupes.MAX) {
            Toast.makeText(this, "Dossier plein : ${Groupes.MAX} apps maximum", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        // Toutes les apps qui ont une icône, sauf AnimLauncher et celles déjà dans le dossier, par ordre alphabétique.
        val intention = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intention, 0)
            .map { it.activityInfo.packageName }.distinct()
            .filter { it != packageName && it !in dejaDedans }
            .map { it to Groupes.nomApp(this, it) }
            .sortedBy { it.second.lowercase() }
        // Les SUGGESTIONS : les apps que le tri range dans cette catégorie, mais qui ne sont pas dans le dossier
        // (en général : celles que l'utilisateur avait décochées).
        val deLaCategorie = Groupes.tous(this, groupe).toSet()
        val suggestions = apps.filter { it.first in deLaCategorie }
        val autres = apps.filter { it.first !in deLaCategorie }

        // ---------- LA FEUILLE ----------
        val colonne = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // la poignée
        colonne.addView(View(this).apply { background = Style.arrondi(this@ChoixAppActivity, Color.argb(56, 255, 255, 255), 2f) },
            LinearLayout.LayoutParams(px(42f), px(4f)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = px(10f); bottomMargin = px(8f) })

        // l'en-tête : logo, titre + phrase d'aide, place prise
        val tete = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(20f), px(4f), px(20f), px(14f))
        }
        logo = LogoVue(this, groupe, teinte.logo)
        tete.addView(logo, LinearLayout.LayoutParams(px(44f), px(44f)).apply { rightMargin = px(12f) })
        val textes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textes.addView(Style.texte(this, getString(R.string.ajout_titre, nomGroupe), 19f, Color.WHITE, gras = true).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        textes.addView(Style.texte(this, getString(R.string.ajout_sous), 12.5f, teinte.info).apply { maxLines = 1 })
        tete.addView(textes, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        // « 5 / 60 » et la petite jauge dessous
        val place = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        place.addView(Style.texte(this, "${dejaDedans.size} / ${Groupes.MAX}", 12f, teinte.info))
        val jauge = FrameLayout(this).apply { background = Style.arrondi(this@ChoixAppActivity, Color.argb(26, 255, 255, 255), 2f) }
        val largeurJauge = px(54f)
        jauge.addView(View(this).apply { background = Style.arrondi(this@ChoixAppActivity, Teinte.avecAlpha(teinte.clair, 180f), 2f) },
            FrameLayout.LayoutParams((largeurJauge * dejaDedans.size / Groupes.MAX).coerceAtLeast(px(3f)), ViewGroup.LayoutParams.MATCH_PARENT))
        place.addView(jauge, LinearLayout.LayoutParams(largeurJauge, px(4f)).apply { topMargin = px(6f) })
        tete.addView(place)
        colonne.addView(tete)

        // la recherche : une pilule avec une petite loupe
        val pilule = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Style.arrondi(this@ChoixAppActivity, Color.argb(18, 255, 255, 255), 16f, Color.argb(20, 255, 255, 255))
            setPadding(px(14f), 0, px(14f), 0)
        }
        pilule.addView(Loupe(this), LinearLayout.LayoutParams(px(16f), px(16f)).apply { rightMargin = px(10f) })
        val recherche = EditText(this).apply {
            hint = getString(R.string.rechercher)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(105, 255, 255, 255))
            textSize = 15f
            background = null                 // pas le trait souligné d'Android : c'est la pilule qui fait le cadre
            setSingleLine()
            setPadding(0, px(11f), 0, px(11f))
        }
        pilule.addView(recherche, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        colonne.addView(pilule, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = px(18f); rightMargin = px(18f); bottomMargin = px(6f)
        })

        // la partie qui défile : suggestions puis toutes les apps
        val defilant = ScrollView(this).apply { isVerticalScrollBarEnabled = false; clipToPadding = false }
        val liste = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(10f), 0, px(10f), px(24f)) }
        defilant.addView(liste)
        colonne.addView(defilant, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val rubriqueSugg = rubrique("✦  " + getString(R.string.ajout_suggestions, nomGroupe), teinte.info)
        val rangeeSugg = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(px(4f), 0, px(4f), px(4f)) }
        val defileSugg = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(rangeeSugg) }
        // (les cartes de suggestion : une case avec un fond légèrement plus clair et un fin contour)
        for ((pkg, nom) in suggestions) {
            val carte = case(pkg, nom)
            carte.background = Style.arrondi(this, Color.argb(13, 255, 255, 255), 18f, Color.argb(15, 255, 255, 255))
            rangeeSugg.addView(carte, LinearLayout.LayoutParams(px(76f), ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = px(6f) })
        }
        if (suggestions.isNotEmpty()) { liste.addView(rubriqueSugg); liste.addView(defileSugg) }
        val rubriqueToutes = rubrique(getString(R.string.ajout_toutes), teinte.info)
        liste.addView(rubriqueToutes)
        val grille = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        liste.addView(grille)
        val vide = Style.texte(this, getString(R.string.ajout_vide), 13f, Color.argb(105, 255, 255, 255)).apply {
            gravity = Gravity.CENTER; setPadding(0, px(30f), 0, px(30f)); visibility = View.GONE
        }
        liste.addView(vide, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Chaque case de la grille est créée UNE fois (avec son icône) ; la recherche ne fait que les re-ranger.
        // (Les suggestions ont donc deux cases : leur carte en haut, et une case pour les résultats de recherche.)
        // On garde le nom « sans accents » de chaque app pour chercher (« ecran » trouve « Écran »).
        val toutesLesCases = (suggestions + autres).map { (pkg, nom) -> Triple(pkg, AppsActivity.sansAccents(nom), case(pkg, nom)) }
        val casesAutres = toutesLesCases.filter { it.first !in deLaCategorie }

        // Ranger une liste de cases dans la grille : des rangées de 4 (la dernière complétée par du vide,
        // pour que les colonnes restent alignées).
        fun ranger(cases: List<View>) {
            for (k in 0 until grille.childCount) (grille.getChildAt(k) as ViewGroup).removeAllViews()
            grille.removeAllViews()
            for (debut in cases.indices step 4) {
                val rangee = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (k in 0 until 4) {
                    val vue = cases.getOrNull(debut + k) ?: Space(this)
                    (vue.parent as? ViewGroup)?.removeView(vue)
                    rangee.addView(vue, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
                grille.addView(rangee)
            }
        }
        ranger(casesAutres.map { it.third })

        // LA RECHERCHE : quand on tape, les suggestions et les titres disparaissent, et la grille ne montre que
        // les apps (suggestions comprises) dont le nom contient le texte. Texte effacé : tout revient comme avant.
        recherche.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val cherche = AppsActivity.sansAccents(s?.toString()?.trim() ?: "")
                if (cherche.isEmpty()) {
                    ranger(casesAutres.map { it.third })
                    val avecSugg = suggestions.isNotEmpty()
                    rubriqueSugg.visibility = if (avecSugg) View.VISIBLE else View.GONE
                    defileSugg.visibility = if (avecSugg) View.VISIBLE else View.GONE
                    rubriqueToutes.visibility = View.VISIBLE
                    vide.visibility = View.GONE
                } else {
                    val trouvees = toutesLesCases.filter { it.second.contains(cherche) }.map { it.third }
                    ranger(trouvees)
                    rubriqueSugg.visibility = View.GONE
                    defileSugg.visibility = View.GONE
                    rubriqueToutes.visibility = View.GONE
                    vide.visibility = if (trouvees.isEmpty()) View.VISIBLE else View.GONE
                }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // Le fond de la feuille : un dégradé du haut vers le bas (la couleur du dossier, puis presque noir),
        // coins arrondis en haut seulement.
        val r = px(30f).toFloat()
        colonne.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(teinte.haut, teinte.bas, Color.rgb(12, 13, 19))).apply {
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
        colonne.clipToOutline = true          // (les icônes qui défilent ne débordent pas des coins arrondis)
        colonne.elevation = px(16f).toFloat()

        // ---------- L'ÉCRAN : le voile sombre, et la feuille posée en bas ----------
        racine = FrameLayout(this)
        voile = View(this).apply {
            setBackgroundColor(Color.argb(150, 4, 5, 8))
            alpha = 0f
            setOnClickListener { fermer() }             // toucher le voile au-dessus de la feuille la ferme
        }
        racine.addView(voile, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        feuille = Feuille(this, defilant, fermer = { fermer(dejaDescendue = true) }, voile = { f -> voile.alpha = f })
        feuille.addView(colonne, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // La feuille prend le bas de l'écran, à partir de 20 % de la hauteur. Quand le clavier s'ouvre, l'écran
        // « rétrécit » par le bas (voir les marges plus bas) : la feuille rétrécit aussi, l'en-tête reste visible.
        val hauteurEcran = resources.displayMetrics.heightPixels
        racine.addView(feuille, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            topMargin = (hauteurEcran * 0.2f).toInt()
        })

        // LES BORDS DE L'ÉCRAN : la barre de navigation (en bas) et le CLAVIER. Leur hauteur est donnée par
        // Android (« insets ») ; on décale le bas de la feuille d'autant, pour que rien ne soit caché dessous.
        racine.setOnApplyWindowInsetsListener { _, insets ->
            val bas = if (Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                      else 0          // (avant Android 11, Android rétrécit lui-même l'écran pour le clavier)
            liste.setPadding(px(10f), 0, px(10f), px(24f) + bas)
            (feuille.layoutParams as FrameLayout.LayoutParams).bottomMargin =
                if (Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.ime()).bottom else 0
            feuille.requestLayout()
            insets
        }
        setContentView(racine)
        // Android 11 et plus : c'est NOUS qui gérons les bords (barre de navigation, clavier), voir juste au-dessus.
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)

        // L'OUVERTURE : le voile apparaît, la feuille monte du bas (en ralentissant), puis le logo fait son geste.
        feuille.visibility = View.INVISIBLE
        racine.post {
            feuille.translationY = feuille.height.toFloat()
            feuille.visibility = View.VISIBLE
            voile.animate().alpha(1f).setDuration(260).start()
            feuille.animate().translationY(0f).setDuration(460).setInterpolator(DecelerateInterpolator(2.2f)).start()
            logo.postDelayed({ logo.jouer() }, 300)
        }
    }

    // un titre de rubrique (« SUGGESTIONS POUR FINANCE », « TOUTES LES APPS »)
    private fun rubrique(t: String, couleur: Int): TextView =
        Style.texte(this, t.uppercase(), 11.5f, couleur, gras = true).apply {
            letterSpacing = 0.08f
            setPadding(px(8f), px(12f), px(8f), px(8f))
        }

    // UNE CASE : l'icône de l'app (arrondie par Android) et son nom sur une ligne. Un appui l'ajoute au dossier.
    private fun case(pkg: String, nom: String): LinearLayout {
        val case = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, px(9f), 0, px(9f))
        }
        val icone = ImageView(this).apply {
            try { setImageDrawable(packageManager.getApplicationIcon(pkg)) } catch (_: Exception) {}
        }
        case.addView(icone, LinearLayout.LayoutParams(px(50f), px(50f)))
        case.addView(Style.texte(this, nom, 11f, Color.rgb(214, 217, 228)).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
            setPadding(px(3f), px(6f), px(3f), 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        case.setOnClickListener { ajouter(pkg, icone) }
        return case
    }

    // ON A CHOISI UNE APP : elle entre dans le dossier, avec une petite animation.
    private fun ajouter(pkg: String, icone: ImageView) {
        if (fini) return
        fini = true
        Groupes.ajouter(this, groupe, pkg)
        // le clavier se range (s'il était ouvert)
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(racine.windowToken, 0)
        // 1) l'icône se tasse…
        icone.animate().scaleX(0.84f).scaleY(0.84f).setDuration(110).withEndAction {
            // 2) … rebondit, et une COPIE de l'icône s'envole jusqu'au logo du dossier
            icone.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(3f)).start()
            icone.alpha = 0.3f
            val ici = IntArray(2); val la = IntArray(2); val base = IntArray(2)
            icone.getLocationInWindow(ici); logo.getLocationInWindow(la); racine.getLocationInWindow(base)
            val vol = ImageView(this).apply { setImageDrawable(icone.drawable?.constantState?.newDrawable()?.mutate() ?: icone.drawable) }
            racine.addView(vol, FrameLayout.LayoutParams(icone.width, icone.height))
            vol.x = (ici[0] - base[0]).toFloat(); vol.y = (ici[1] - base[1]).toFloat()
            vol.elevation = px(24f).toFloat()
            val cibleX = la[0] - base[0] + (logo.width - icone.width) / 2f
            val cibleY = la[1] - base[1] + (logo.height - icone.height) / 2f
            vol.animate().x(cibleX).y(cibleY).scaleX(0.45f).scaleY(0.45f).setDuration(480)
                .setInterpolator(DecelerateInterpolator(1.6f))
                .withEndAction {
                    // 3) l'icône « entre » dans le logo : elle disparaît, le logo rebondit et refait son geste
                    vol.animate().alpha(0f).scaleX(0.2f).scaleY(0.2f).setDuration(140).start()
                    logo.animate().scaleX(1.18f).scaleY(1.18f).setDuration(120).withEndAction {
                        logo.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(OvershootInterpolator(3f)).start()
                    }.start()
                    logo.jouer()
                    // 4) la feuille redescend un instant plus tard, et le dossier se rouvre
                    racine.postDelayed({ fini = false; fermer() }, 520)
                }.start()
        }.start()
    }

    // LA FERMETURE : la feuille descend, le voile s'efface, puis on s'en va (vers le dossier si on en venait).
    private fun fermer(dejaDescendue: Boolean = false) {
        if (fini) return
        fini = true
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(racine.windowToken, 0)
        if (dejaDescendue) { partir(); return }
        voile.animate().alpha(0f).setDuration(240).start()
        feuille.animate().translationY(feuille.height.toFloat()).setDuration(260)
            .setInterpolator(DecelerateInterpolator()).withEndAction { partir() }.start()
    }

    private fun partir() {
        if (depuisDossier) {
            // le dossier se rouvre (avec la nouvelle app qui éclot à sa place, si on en a ajouté une)
            startActivity(Intent(this, DossierActivity::class.java).putExtra("groupe", groupe)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    // Le bouton RETOUR : la feuille redescend (et on revient au dossier si on en venait).
    @Deprecated("Bouton retour")
    override fun onBackPressed() { fermer() }
}

// LE LOGO DU DOSSIER dans l'en-tête de la feuille : le vrai logo de la catégorie (voir LogosDossier.kt), dessiné
// à la taille de la vue. « jouer() » lui fait faire son petit geste une fois (la bulle qui écrit, le vinyle et ses
// notes, la courbe qui monte…) ; le reste du temps, il est au repos.
class LogoVue(c: Context, private val groupe: String, private val couleurs: IntArray) : View(c) {
    private var depart = -1L              // l'heure où le geste a commencé (-1 = au repos)

    fun jouer() { depart = SystemClock.uptimeMillis(); postInvalidateOnAnimation() }

    override fun onDraw(cv: Canvas) {
        // l'horloge du geste : 0,3 (repos) -> 2,9 s ; les logos qui se dessinent (Finance, Maps…) partent de leur
        // dessin « vide », comme sur le widget
        var g = 0.3
        if (depart >= 0) {
            val t = (SystemClock.uptimeMillis() - depart) / 1000.0
            val debut = LogosDossier.debutTrace(groupe) ?: 0.3
            g = debut + t
            if (g >= 2.9) { g = 0.3; depart = -1 }
            else postInvalidateOnAnimation()
        }
        // même cadrage que LogosDossier.dessinerAuCentre : le logo (rayon 26 dans son repère 100 x 100) prend
        // ~80 % de la vue
        val r = min(width, height) * 0.4f
        cv.save()
        cv.translate(width / 2f, height / 2f)
        cv.scale(r / 26f, r / 26f)
        cv.translate(-50f, -48f)
        LogosDossier.dessiner(cv, groupe, g, couleurs)
        cv.restore()
    }
}

// LA PETITE LOUPE de la barre de recherche (un rond et un manche), dessinée à la main.
private class Loupe(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.argb(150, 255, 255, 255)
    }
    override fun onDraw(cv: Canvas) {
        val s = min(width, height).toFloat()
        p.strokeWidth = s * 0.13f
        cv.drawCircle(s * 0.43f, s * 0.43f, s * 0.29f, p)
        cv.drawLine(s * 0.65f, s * 0.65f, s * 0.88f, s * 0.88f, p)
    }
}

// LA FEUILLE QU'ON FAIT GLISSER VERS LE BAS POUR LA FERMER : même principe que FeuilleGlissante (réaction
// sonore, voir ReactionSonoreUi.kt), mais la feuille garde une hauteur FIXE (elle ne rétrécit pas quand la
// recherche cache des apps), et c'est le voile de l'écran qu'on éclaircit.
//  - si la liste est tout en HAUT et que le doigt descend (plus qu'il ne va sur le côté), la feuille suit le doigt ;
//  - au lâcher : descendue de plus d'un quart de sa hauteur, ou lancée vite vers le bas → elle se ferme ;
//    sinon elle remonte à sa place.
private class Feuille(
    context: Context,
    private val defilant: ScrollView,
    private val fermer: () -> Unit,
    private val voile: (Float) -> Unit
) : FrameLayout(context) {
    private val dp = resources.displayMetrics.density
    private val seuil = ViewConfiguration.get(context).scaledTouchSlop
    private var vitesse: VelocityTracker? = null
    private var xDepart = 0f
    private var yDepart = 0f
    private var tire = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        suivre(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { xDepart = ev.rawX; yDepart = ev.rawY; tire = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - xDepart
                val dy = ev.rawY - yDepart
                if (dy > seuil && dy > abs(dx) && !defilant.canScrollVertically(-1)) {
                    tire = true
                    yDepart = ev.rawY
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        suivre(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { xDepart = ev.rawX; yDepart = ev.rawY; tire = true }
            MotionEvent.ACTION_MOVE -> if (tire) {
                val descente = (ev.rawY - yDepart).coerceAtLeast(0f)
                translationY = descente
                voile(1f - (descente / height.coerceAtLeast(1)).coerceIn(0f, 1f))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vitesse?.computeCurrentVelocity(1000)
                val vy = vitesse?.yVelocity ?: 0f
                if (tire && (translationY > height * 0.25f || vy > 900f * dp)) {
                    animate().translationY(height.toFloat()).setDuration(200).setInterpolator(DecelerateInterpolator())
                        .setUpdateListener { voile(1f - (translationY / height.coerceAtLeast(1)).coerceIn(0f, 1f)) }
                        .withEndAction { fermer() }.start()
                } else {
                    animate().translationY(0f).setDuration(220).setInterpolator(DecelerateInterpolator())
                        .setUpdateListener { voile(1f - (translationY / height.coerceAtLeast(1)).coerceIn(0f, 1f)) }
                        .start()
                }
                tire = false
                vitesse?.recycle(); vitesse = null
            }
        }
        return true
    }

    private fun suivre(ev: MotionEvent) {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) { vitesse?.recycle(); vitesse = null }
        if (vitesse == null) vitesse = VelocityTracker.obtain()
        val copie = MotionEvent.obtain(ev)
        copie.setLocation(ev.rawX, ev.rawY)
        vitesse?.addMovement(copie)
        copie.recycle()
    }
}
