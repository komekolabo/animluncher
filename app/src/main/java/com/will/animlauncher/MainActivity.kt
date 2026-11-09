package com.will.animlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.floor

// L'ÉCRAN D'ACCUEIL (refonte « piste C », validée par Will le 09/11 sur le canevas Design).
//
//  ┌──────────────────────────────┐
//  │ ≡        AnimLauncher       ⚙ │   en-tête : menu (tiroir à gauche) · titre · réglages
//  │ ┌──────────────────────┐ ┌─ │
//  │ │ 01                   │ │   │   3 GRANDES CARTES qu'on fait glisser du doigt :
//  │ │     (animation)      │ │   │   1. Animations d'apps  2. Logos animés  3. Fonds d'écran
//  │ │ Titre                │ │   │   Chacune : numéro, animation, titre, phrase, chiffre, bouton.
//  │ │ [   bouton blanc   ] │ │   │   Le bord de la carte suivante dépasse : on comprend qu'on peut glisser.
//  │ └──────────────────────┘ └─ │
//  │          ━  •  •             │   les points (on peut appuyer dessus)
//  │   ( Animations ON  [●] )     │   l'interrupteur général (le kill switch)
//  └──────────────────────────────┘
//
// Tout ce qu'il y avait sur l'ancien écran principal est maintenant dans ReglagesActivity (la roue).
class MainActivity : Activity() {

    private var dp = 1f
    private fun px(v: Float): Int = (v * dp).toInt()

    // le rail des cartes et où on en est
    private lateinit var defileur: HorizontalScrollView
    private var pas = 0                     // largeur d'une carte + l'écart, en pixels
    private var page = 0                    // la carte affichée (0, 1 ou 2)
    private var direction = 0               // le dernier sens du glissement (+1 vers la droite, -1 vers la gauche)
    private val points = ArrayList<View>()

    // ce qui change quand on revient sur l'écran
    private lateinit var demo: DemoAnimations      // la démo en boucle de la carte 1 (voir DemoAccueil.kt)
    private lateinit var chiffreApps: TextView
    private lateinit var etat: TextView
    private lateinit var interrupteur: Switch
    private lateinit var interrupteurMenu: Switch
    private lateinit var alerte: TextView
    private var majAuto = false             // vrai quand c'est le programme (pas le doigt) qui bouge un interrupteur

    // le menu (tiroir de gauche) et le voile sombre derrière
    private lateinit var voile: View
    private lateinit var tiroir: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dp = resources.displayMetrics.density
        Style.fenetreNoire(this)
        noterPositionIcone(intent)

        // La taille des cartes : l'écran moins 72 dp (20 dp de marge à gauche, et 52 dp à droite
        // pour qu'on voie dépasser le bord de la carte suivante).
        val largeurCarte = resources.displayMetrics.widthPixels - px(72f)
        val ecart = px(12f)
        pas = largeurCarte + ecart

        // ---------- 1. l'en-tête ----------
        val entete = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4f), 0, px(4f), 0)
        }
        entete.addView(Icone(this, Icone.MENU).apply {
            contentDescription = "Ouvrir le menu"
            setOnClickListener { ouvrirMenu() }
        }, LinearLayout.LayoutParams(px(48f), px(48f)))
        entete.addView(Style.texte(this, "AnimLauncher", 18f, Style.TEXTE, gras = true).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        entete.addView(Icone(this, Icone.ROUE).apply {
            contentDescription = "Réglages"
            setOnClickListener { ouvrir(ReglagesActivity::class.java) }
        }, LinearLayout.LayoutParams(px(48f), px(48f)))

        // ---------- 2. les 3 cartes, dans un rail qui défile à l'horizontale ----------
        val rangee = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(px(20f), px(6f), px(20f), 0)
        }
        for ((i, carte) in listOf(carteApps(), carteLogos(), carteFonds()).withIndex()) {
            rangee.addView(carte, LinearLayout.LayoutParams(largeurCarte, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                if (i > 0) leftMargin = ecart
            })
        }
        defileur = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(rangee, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        // Pendant le glissement : on retient le sens, et on met les points à jour.
        defileur.setOnScrollChangeListener { _, x, _, ancienX, _ ->
            if (x != ancienX) direction = if (x > ancienX) 1 else -1
            val p = Math.round(x.toFloat() / pas).coerceIn(0, 2)
            if (p != page) { page = p; majPoints() }
        }
        // Quand le doigt se lève : la carte « s'aimante » (comme les pages d'un téléphone).
        // Il suffit d'avoir glissé de 15 % d'une carte dans un sens pour passer à la suivante.
        defileur.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                val position = defileur.scrollX.toFloat() / pas
                val avant = floor(position).toInt()
                val reste = position - avant
                val cible = if (direction > 0) (if (reste > 0.15f) avant + 1 else avant)
                            else (if (reste < 0.85f) avant else avant + 1)
                defileur.post { allerA(cible) }
            }
            false        // false = on laisse le rail faire son travail normal en plus
        }

        // ---------- 3. le bas : avertissement, points, interrupteur général ----------
        alerte = Style.texte(this, "Détection désactivée : touche ici pour l'activer", 13f, Style.AMBRE).apply {
            gravity = Gravity.CENTER
            setPadding(px(16f), px(10f), px(16f), px(4f))
            setOnClickListener { ouvrir(ReglagesActivity::class.java) }
        }
        val rangeePoints = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val noms = listOf("Animations d'apps", "Logos animés", "Fonds d'écran")
        for (i in 0..2) {
            val point = View(this)
            points.add(point)
            // chaque point est dans une zone de 32 dp, plus facile à toucher qu'un point de 6 dp
            rangeePoints.addView(FrameLayout(this).apply {
                contentDescription = noms[i]
                isClickable = true
                setOnClickListener { allerA(i) }
                addView(point, FrameLayout.LayoutParams(px(6f), px(6f), Gravity.CENTER))
            }, LinearLayout.LayoutParams(px(32f), px(32f)))
        }
        etat = Style.texte(this, "", 14f, Style.TEXTE, gras = true)
        interrupteur = Style.interrupteur(this).apply {
            contentDescription = "Toutes les animations"
            setOnCheckedChangeListener { _, coche -> if (!majAuto) changerActif(coche) }
        }
        val pastille = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Style.arrondi(this@MainActivity, Style.CARTE, 28f, Style.BORD)
            setPadding(px(18f), px(4f), px(8f), px(4f))
            addView(etat)
            addView(interrupteur, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(44f)).apply { leftMargin = px(10f) })
        }
        val bas = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, px(6f), 0, px(18f))
            addView(alerte)
            addView(rangeePoints)
            addView(pastille, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(8f) })
        }

        val contenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Style.NOIR)
            addView(entete, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(64f)))
            addView(defileur, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bas)
        }

        // ---------- 4. le menu (caché au départ) ----------
        voile = View(this).apply {
            setBackgroundColor(Color.argb(178, 0, 0, 0))
            alpha = 0f
            visibility = View.GONE
            setOnClickListener { fermerMenu() }
        }
        tiroir = construireMenu()

        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Style.NOIR)
            addView(contenu)
            addView(voile)
            addView(tiroir, FrameLayout.LayoutParams(px(300f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
        })
        majPoints()
    }

    override fun onResume() {
        super.onResume()
        // les interrupteurs suivent le réglage (il a pu changer dans le volet rapide ou les réglages)
        majAuto = true
        interrupteur.isChecked = Reglages.actif(this)
        interrupteurMenu.isChecked = Reglages.actif(this)
        majAuto = false
        majEtat()
        alerte.visibility = if (serviceActif()) View.GONE else View.VISIBLE
        // les chiffres de la carte 1
        val jouables = appsJouables()
        val avecAnimation = appsAvecAnimation()
        chiffreApps.text = "${jouables.size} actives · ${avecAnimation.size - jouables.size} coupées"
    }

    // ================= LES CARTES =================

    // Le cadre commun d'une carte : un rectangle très arrondi, un léger dégradé, un fin contour.
    // « clipToOutline » : ce qui dépasse (les animations) est coupé selon l'arrondi.
    private fun cadre(lueur: Int): FrameLayout = FrameLayout(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(lueur, Color.rgb(11, 11, 13))).apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = px(340f).toFloat()
            setGradientCenter(0.5f, 0.3f)
            cornerRadius = px(34f).toFloat()
            setStroke(px(1f), Style.BORD)
        }
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
    }

    // Le numéro en haut à gauche (01, 02, 03).
    private fun numero(n: String) = Style.texte(this, n, 12f, Style.MUET, gras = true).apply { letterSpacing = 0.12f }

    // Le bas d'une carte : titre (sur 2 lignes), phrase, chiffre, bouton blanc.
    private fun texteBas(titre: String, phrase: String, chiffre: TextView, bouton: String, action: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24f), 0, px(24f), px(24f))
            addView(Style.texte(this@MainActivity, titre, 32f, Style.TEXTE, gras = true).apply { setLineSpacing(0f, 0.95f) })
            addView(Style.texte(this@MainActivity, phrase, 13.5f, Style.MUET).apply {
                setLineSpacing(0f, 1.15f); setPadding(0, px(8f), 0, px(6f))
            })
            addView(chiffre)
            addView(Style.boutonBlanc(this@MainActivity, bouton, action),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(50f)).apply { topMargin = px(14f) })
        }

    // Une carte « classique » : numéro en haut, animation au milieu (elle prend la place libre), texte en bas.
    private fun carteEmpilee(lueur: Int, num: String, animation: View, bas: View): FrameLayout {
        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(animation, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bas)
        }
        return cadre(lueur).apply {
            addView(colonne)
            addView(numero(num), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = px(24f); topMargin = px(22f)
            })
        }
    }

    // CARTE 1 : animations d'apps → la liste des apps.
    // Au centre, la démo : un mini téléphone qui joue en boucle les vraies animations
    // de Maps, Claude, des échecs, de Duolingo et de ChatGPT (voir DemoAccueil.kt).
    private fun carteApps(): FrameLayout {
        demo = DemoAnimations(this)
        chiffreApps = Style.texte(this, "", 12.5f, Style.DOUX)
        return carteEmpilee(Color.rgb(23, 16, 13), "01", demo,
            texteBas("Animations\nd'apps", "Une animation plein écran à chaque app que tu ouvres.",
                chiffreApps, "Gérer les animations") { ouvrir(AppsActivity::class.java) })
    }

    // CARTE 2 : logos animés → les dossiers animés (et un lien pour poser le logo sur l'accueil).
    private fun carteLogos(): FrameLayout {
        val taille = px(132f)
        val scene = FrameLayout(this).apply {
            addView(DessinGalets(this@MainActivity))
            addView(LogoAnime(this@MainActivity), FrameLayout.LayoutParams(taille, taille, Gravity.CENTER))
        }
        val lien = Style.texte(this, "Poser le logo animé sur l'accueil ›", 12.5f, Style.DOUX).apply {
            setPadding(0, px(4f), 0, px(4f))
            setOnClickListener {
                if (!LogoWidget.proposer(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Appuie longuement sur un endroit vide de l'accueil, " +
                        "puis « Widgets », et cherche « ${getString(R.string.app_name)} ».", Toast.LENGTH_LONG).show()
                }
            }
        }
        return carteEmpilee(Color.rgb(18, 20, 26), "02", scene,
            texteBas("Logos\nanimés", "Des icônes vivantes sur ton écran d'accueil : logo, galets d'apps et dossiers.",
                lien, "Voir les dossiers animés") { ouvrir(GroupesActivity::class.java) })
    }

    // CARTE 3 : fonds d'écran. Ici, la carte ENTIÈRE est un fond animé ; le texte est posé
    // par-dessus, sur un dégradé noir pour rester lisible.
    private fun carteFonds(): FrameLayout {
        val chiffre = Style.texte(this, "Bientôt dans l'app, puis la communauté", 12.5f, Style.DOUX)
        val bas = texteBas("Fonds\nd'écran", "Animés, sombres et légers pour la batterie. Celui-ci en est un.",
            chiffre, "Voir les fonds d'écran") { ouvrir(FondsActivity::class.java) }.apply {
            setPadding(px(24f), px(60f), px(24f), px(24f))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(230, 0, 0, 0), Color.argb(230, 0, 0, 0)))
        }
        return cadre(Color.rgb(3, 3, 4)).apply {
            addView(DessinFonds(this@MainActivity))
            addView(numero("03"), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = px(24f); topMargin = px(22f)
            })
            addView(bas, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        }
    }

    // ================= LE RAIL ET LES POINTS =================

    private fun allerA(p: Int) {
        page = p.coerceIn(0, 2)
        defileur.smoothScrollTo(page * pas, 0)
        majPoints()
    }

    // Le point de la carte affichée est un petit trait blanc ; les autres, des points gris.
    private fun majPoints() {
        for ((i, point) in points.withIndex()) {
            val actif = i == page
            point.layoutParams = point.layoutParams.apply { width = px(if (actif) 22f else 6f) }
            point.background = Style.arrondi(this, if (actif) Style.TEXTE else Color.rgb(63, 63, 70), 3f)
        }
    }

    // ================= L'INTERRUPTEUR GÉNÉRAL =================

    private fun changerActif(actif: Boolean) {
        Reglages.definir(this, actif)
        majAuto = true
        interrupteur.isChecked = actif          // les deux interrupteurs (accueil et menu) restent d'accord
        interrupteurMenu.isChecked = actif
        majAuto = false
        majEtat()
    }

    private fun majEtat() {
        val actif = Reglages.actif(this)
        etat.text = if (actif) "Animations ON" else "En pause"
        etat.setTextColor(if (actif) Style.TEXTE else Style.AMBRE)
    }

    // ================= LE MENU =================

    private fun construireMenu(): LinearLayout {
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 11, 13))
            setPadding(px(14f), px(24f), px(14f), px(20f))
            visibility = View.GONE
            isClickable = true          // un appui dans le menu ne doit pas le fermer
        }
        // le logo et le nom
        menu.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4f), 0, 0, px(16f))
            addView(LogoAnime(this@MainActivity), LinearLayout.LayoutParams(px(40f), px(40f)))
            addView(Style.texte(this@MainActivity, getString(R.string.app_name), 18f, Style.TEXTE, gras = true).apply {
                setPadding(px(10f), 0, 0, 0)
            })
        })
        // l'interrupteur général, aussi ici
        interrupteurMenu = Style.interrupteur(this).apply {
            text = "Toutes les animations"
            textSize = 14.5f
            setTextColor(Style.TEXTE)
            setOnCheckedChangeListener { _, coche -> if (!majAuto) changerActif(coche) }
        }
        menu.addView(LinearLayout(this).apply {
            background = Style.arrondi(this@MainActivity, Color.rgb(18, 18, 20), 14f, Style.BORD)
            setPadding(px(14f), px(4f), px(8f), px(4f))
            addView(interrupteurMenu, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(48f)))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = px(10f)
        })
        // les destinations
        fun entree(titre: String, action: () -> Unit) {
            menu.addView(Style.texte(this, titre, 15.5f, Style.TEXTE).apply {
                gravity = Gravity.CENTER_VERTICAL
                minHeight = px(52f)
                setPadding(px(14f), 0, px(14f), 0)
                val a = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                background = a.getDrawable(0)
                a.recycle()
                isClickable = true
                setOnClickListener { fermerMenu(); action() }
            })
        }
        entree("Accueil") { }
        entree("Animations d'apps") { ouvrir(AppsActivity::class.java) }
        entree("Logos & dossiers animés") { ouvrir(GroupesActivity::class.java) }
        entree("Fonds d'écran") { ouvrir(FondsActivity::class.java) }
        entree("Réglages") { ouvrir(ReglagesActivity::class.java) }
        entree("Journal (diagnostic)") { ouvrir(JournalActivity::class.java) }
        return menu
    }

    private fun ouvrirMenu() {
        val largeur = px(300f).toFloat()
        voile.visibility = View.VISIBLE
        tiroir.visibility = View.VISIBLE
        tiroir.translationX = -largeur          // il part de la gauche, hors de l'écran…
        tiroir.animate().translationX(0f).setDuration(280).setInterpolator(DecelerateInterpolator()).start()   // …et glisse
        voile.animate().alpha(1f).setDuration(280).start()
    }

    private fun fermerMenu() {
        tiroir.animate().translationX(-px(300f).toFloat()).setDuration(220).withEndAction {
            tiroir.visibility = View.GONE
        }.start()
        voile.animate().alpha(0f).setDuration(220).withEndAction { voile.visibility = View.GONE }.start()
    }

    // Le bouton « retour » du téléphone ferme d'abord le menu s'il est ouvert.
    @Deprecated("Remplacé par OnBackInvokedCallback sur Android 13+, mais marche toujours ici")
    override fun onBackPressed() {
        if (tiroir.visibility == View.VISIBLE) fermerMenu() else super.onBackPressed()
    }

    // ================= OUTILS =================

    private fun ouvrir(ecran: Class<*>) = startActivity(Intent(this, ecran))

    // Les apps installées qui ont une animation dans le catalogue.
    private fun appsAvecAnimation(): List<String> {
        val intention = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intention, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != packageName && Catalogue.pour(it) != null }
    }

    // Parmi elles, celles dont l'animation va vraiment se jouer avec les réglages actuels
    // (même règle que Catalogue.lancement, mais sans fabriquer la scène : plus rapide).
    private fun appsJouables(): List<String> = appsAvecAnimation().filter { app ->
        val e = Catalogue.pour(app)
        e != null && Reglages.appActive(this, app) && Reglages.duree(this, app) != 0 &&
            !(e.variantes.isNotEmpty() && Reglages.variantesDesactivees(this, app).size >= e.variantes.size)
    }

    private fun serviceActif(): Boolean {
        val actifs = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return actifs.contains("$packageName/")
    }

    // TEST : OÙ EST NOTRE ICÔNE ? Quand on touche une icône, l'écran d'accueil glisse en général,
    // dans la demande d'ouverture (l'« intent »), le rectangle exact de l'icône touchée à l'écran
    // (« sourceBounds », en pixels : gauche, haut, droite, bas). C'est ce qui lui sert à faire
    // grossir l'app depuis l'icône. On l'écrit simplement dans le journal pour vérifier que le
    // lanceur Samsung le donne bien, et que les chiffres tombent au bon endroit.
    // Attention : ça ne marche qu'au vrai démarrage de l'écran principal. Si AnimLauncher était
    // encore ouverte en arrière-plan, Android la ramène sans nouvelle demande, donc sans rectangle.
    private fun noterPositionIcone(demande: Intent?) {
        val r = demande?.sourceBounds
        val ecran = resources.displayMetrics
        if (r == null) {
            Journal.noter(this, "position de l'icône : non transmise par l'écran d'accueil")
        } else {
            Journal.noter(this, "position de l'icône : gauche ${r.left}, haut ${r.top}, " +
                "droite ${r.right}, bas ${r.bottom} (taille ${r.width()} x ${r.height()}) " +
                "sur un écran de ${ecran.widthPixels} x ${ecran.heightPixels}")
        }
        Journal.programmerEnvoi(this)
    }
}
