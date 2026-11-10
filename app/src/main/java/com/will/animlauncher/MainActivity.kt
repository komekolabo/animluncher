package com.will.animlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

// L'ÉCRAN D'ACCUEIL (refonte « piste C », validée par Will le 09/11 sur le canevas Design).
//
//  ┌──────────────────────────────┐
//  │ ≡        AnimLauncher       ⚙ │   en-tête : menu (tiroir à gauche) · titre · réglages
//  │─┐ ┌──────────────────────┐ ┌─│
//  │ │ │ 01                   │ │ │   3 GRANDES CARTES qui tournent EN BOUCLE (voir Manege.kt) :
//  │ │ │     (animation)      │ │ │   1. Animations d'apps  2. Logos animés  3. Fonds d'écran
//  │ │ │ Titre                │ │ │   La carte du centre est bien CENTRÉE ; on voit dépasser la 3 à gauche
//  │ │ │ chiffre           (→)│ │ │   et la 2 à droite. Un appui N'IMPORTE OÙ sur la carte ouvre sa partie.
//  │ │ │                      │ │ │   Toutes les 5 s, le manège passe tout seul à la carte de droite
//  │─┘ └──────────────────────┘ └─│   (règles : voir Manege.kt).
//  │          ━  •  •             │   les points (on peut appuyer dessus) ; le trait de la carte affichée
//  │                              │   se remplit de blanc pendant les 5 s (le minuteur, voir PointManege)
//  │   ( Animations ON  [●] )     │   l'interrupteur général (le kill switch)
//  └──────────────────────────────┘
//
// Tout ce qu'il y avait sur l'ancien écran principal est maintenant dans ReglagesActivity (la roue).
//
// LES TEXTES ne sont plus écrits ici : « getString(R.string.xxx) » va chercher le texte « xxx »
// dans la langue du téléphone (fichiers res/values*/strings.xml : un dossier par langue).
class MainActivity : Activity() {

    private var dp = 1f
    private fun px(v: Float): Int = (v * dp).toInt()

    // le manège des cartes (voir Manege.kt) et les points en dessous
    private lateinit var manege: Manege
    private val points = ArrayList<View>()

    // ce qui change quand on revient sur l'écran
    private lateinit var demo: DemoAnimations      // la démo en boucle de la carte 1 (voir DemoAccueil.kt)
    private lateinit var chiffreApps: TextView
    private lateinit var phraseApps: TextView      // le petit message « N apps compatibles trouvées… »
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

        // La taille des cartes : l'écran moins 40 dp de CHAQUE côté → la carte est bien centrée.
        // Avec 12 dp d'écart entre deux cartes, on voit dépasser 28 dp de la voisine de chaque côté.
        val largeurCarte = resources.displayMetrics.widthPixels - px(80f)
        val ecart = px(12f)
        val pas = largeurCarte + ecart

        // ---------- 1. l'en-tête ----------
        val entete = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4f), 0, px(4f), 0)
        }
        entete.addView(Icone(this, Icone.MENU).apply {
            contentDescription = getString(R.string.menu_ouvrir)
            setOnClickListener { ouvrirMenu() }
        }, LinearLayout.LayoutParams(px(48f), px(48f)))
        entete.addView(Style.texte(this, "AnimLauncher", 18f, Style.TEXTE, gras = true).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        entete.addView(Icone(this, Icone.ROUE).apply {
            contentDescription = getString(R.string.reglages)
            setOnClickListener { ouvrir(ReglagesActivity::class.java) }
        }, LinearLayout.LayoutParams(px(48f), px(48f)))

        // ---------- 2. les 3 cartes, dans le manège qui tourne en boucle ----------
        manege = Manege(this, pas).apply {
            setPadding(0, px(6f), 0, 0)
            ajouter(carteApps(), largeurCarte)
            ajouter(carteLogos(), largeurCarte)
            ajouter(carteFonds(), largeurCarte)
        }
        manege.carteAffichee = { majPoints() }
        // Le défilement automatique attend tant que le menu ou la fenêtre de la réaction sonore est ouvert.
        manege.peutDefiler = { tiroir.visibility != View.VISIBLE && !FenetreSon.estOuverte() }

        // ---------- 3. le bas : avertissement, points, interrupteur général ----------
        alerte = Style.texte(this, getString(R.string.detection_coupee), 13f, Style.AMBRE).apply {
            gravity = Gravity.CENTER
            setPadding(px(16f), px(10f), px(16f), px(4f))
            setOnClickListener { ouvrir(ReglagesActivity::class.java) }
        }
        val rangeePoints = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val noms = listOf(getString(R.string.carte_apps), getString(R.string.carte_logos), getString(R.string.carte_fonds))
        for (i in 0..2) {
            // chaque point se dessine lui-même au centre d'une case de 32 dp (plus facile à toucher qu'un point de 6 dp)
            val point = PointManege(this, manege, i).apply {
                contentDescription = noms[i]
                isClickable = true
                setOnClickListener { manege.montrer(i) }
            }
            points.add(point)
            rangeePoints.addView(point, LinearLayout.LayoutParams(px(32f), px(32f)))
        }
        etat = Style.texte(this, "", 14f, Style.TEXTE, gras = true)
        interrupteur = Style.interrupteur(this).apply {
            contentDescription = getString(R.string.toutes_animations)
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
            addView(manege, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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

        // ---------- 5. le rond de la « réaction sonore », en bas à droite ----------
        // (72 dp = le rond de 52 dp + la place de son halo ; il est à la hauteur de l'interrupteur général.
        //  Un appui ouvre sa fenêtre d'explication, voir ReactionSonoreUi.kt.)
        val boutonSon = BoutonSon(this).apply { setOnClickListener { FenetreSon.ouvrir(this@MainActivity) } }

        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Style.NOIR)
            addView(contenu)
            addView(boutonSon, FrameLayout.LayoutParams(px(72f), px(72f), Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = px(6f); bottomMargin = px(8f)
            })
            addView(voile)
            addView(tiroir, FrameLayout.LayoutParams(px(300f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
        })
        majPoints()
    }

    // La réponse d'Android à la question « autoriser le micro ? » (posée par la fenêtre de la réaction sonore).
    override fun onRequestPermissionsResult(demande: Int, permissions: Array<out String>, resultats: IntArray) {
        super.onRequestPermissionsResult(demande, permissions, resultats)
        FenetreSon.reponse(this, demande, resultats)
    }

    override fun onResume() {
        super.onResume()
        ReactionSonore.synchroniser(this)      // (la permission a pu être retirée dans les réglages du téléphone)
        // les interrupteurs suivent le réglage (il a pu changer dans le volet rapide ou les réglages)
        majAuto = true
        interrupteur.isChecked = Reglages.actif(this)
        interrupteurMenu.isChecked = Reglages.actif(this)
        majAuto = false
        majEtat()
        alerte.visibility = if (serviceActif()) View.GONE else View.VISIBLE
        // La carte 1 : on regarde quelles apps du téléphone ont une animation (les « compatibles »).
        val jouables = appsJouables()               // celles dont l'animation est allumée
        val avecAnimation = appsAvecAnimation()     // toutes celles qui ont une animation
        chiffreApps.text = getString(R.string.compte_actives, jouables.size, avecAnimation.size - jouables.size)
        // le petit message : « 12 apps compatibles trouvées sur ton téléphone. Voici ce que ça donne. »
        // (« getQuantityString » choisit tout seul le singulier ou le pluriel selon le nombre)
        phraseApps.text = if (avecAnimation.isEmpty()) getString(R.string.phrase_apps_aucune)
            else resources.getQuantityString(R.plurals.apps_trouvees, avecAnimation.size, avecAnimation.size)
        // la démo joue les animations de 5 de CES apps-là (voir DemoAccueil.kt)
        demo.choisir(if (jouables.isNotEmpty()) jouables else avecAnimation)
        manege.enAvant = true                  // le défilement automatique repart (5 s pleines)
    }

    // L'écran passe en arrière-plan (ou une autre page de l'app s'ouvre par-dessus) : le défilement se met en pause.
    override fun onPause() {
        super.onPause()
        manege.enAvant = false
    }

    // Android appelle ceci quand l'utilisateur QUITTE l'app lui-même (bouton accueil, apps récentes),
    // mais PAS quand on ouvre une autre page d'AnimLauncher. C'est exactement le moment où le
    // défilement automatique a le droit de revenir (choix de Will : revenir à l'écran d'accueil suffit).
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        manege.autoArrete = false
    }

    // ================= LES CARTES =================

    // Le cadre commun d'une carte : un rectangle très arrondi, un léger dégradé, un fin contour.
    // « clipToOutline » : ce qui dépasse (les animations) est coupé selon l'arrondi.
    // TOUTE la carte est un bouton : un appui n'importe où lance « action » (plus de bouton blanc).
    // Glisser le doigt, lui, fait toujours défiler les cartes : Android fait la différence tout seul.
    private fun cadre(lueur: Int, nom: String, action: () -> Unit): FrameLayout = FrameLayout(this).apply {
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
        // l'effet d'appui d'Android (une onde discrète), posé PAR-DESSUS le contenu de la carte
        val a = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        foreground = a.getDrawable(0)
        a.recycle()
        contentDescription = nom          // ce que lit un lecteur d'écran
        isClickable = true
        setOnClickListener { action() }
    }

    // Le numéro en haut à gauche (01, 02, 03).
    private fun numero(n: String) = Style.texte(this, n, 12f, Style.MUET, gras = true).apply { letterSpacing = 0.12f }

    // Le bas d'une carte : titre (sur 2 lignes), phrase, puis une ligne avec le chiffre à gauche
    // et une petite flèche ronde à droite (elle rappelle que la carte s'ouvre d'un appui).
    private fun texteBas(titre: String, phrase: TextView, chiffre: TextView): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24f), 0, px(24f), px(22f))
            addView(Style.texte(this@MainActivity, titre, 32f, Style.TEXTE, gras = true).apply { setLineSpacing(0f, 0.95f) })
            addView(phrase)
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(chiffre, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(Style.texte(this@MainActivity, "→", 17f, Style.TEXTE).apply {
                    gravity = Gravity.CENTER
                    background = Style.arrondi(this@MainActivity, Color.argb(40, 255, 255, 255), 20f, Color.rgb(63, 63, 70))
                }, LinearLayout.LayoutParams(px(40f), px(40f)).apply { leftMargin = px(10f) })
            })
        }

    // La phrase grise sous le titre d'une carte.
    private fun phrase(t: String): TextView = Style.texte(this, t, 13.5f, Style.MUET).apply {
        setLineSpacing(0f, 1.15f); setPadding(0, px(8f), 0, px(10f))
    }

    // Une carte « classique » : numéro en haut, animation au milieu (elle prend la place libre), texte en bas.
    private fun carteEmpilee(lueur: Int, num: String, nom: String, animation: View, bas: View, action: () -> Unit): FrameLayout {
        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(animation, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bas)
        }
        return cadre(lueur, nom, action).apply {
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
        phraseApps = phrase(getString(R.string.phrase_apps_aucune))      // le vrai message arrive dans onResume
        return carteEmpilee(Color.rgb(23, 16, 13), "01", getString(R.string.carte_apps), demo,
            texteBas(getString(R.string.titre2_apps), phraseApps, chiffreApps)
        ) { ouvrir(AppsActivity::class.java) }
    }

    // CARTE 2 : logos animés → les dossiers animés (et un lien pour poser le logo sur l'accueil).
    private fun carteLogos(): FrameLayout {
        val taille = px(132f)
        val scene = FrameLayout(this).apply {
            addView(DessinGalets(this@MainActivity))
            addView(LogoAnime(this@MainActivity), FrameLayout.LayoutParams(taille, taille, Gravity.CENTER))
        }
        // (ce petit lien a sa propre action : un appui dessus n'ouvre pas la carte)
        val lien = Style.texte(this, getString(R.string.lien_poser_logo), 12.5f, Style.DOUX).apply {
            setPadding(0, px(10f), 0, px(10f))
            setOnClickListener {
                if (!LogoWidget.proposer(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, getString(R.string.aide_widget, getString(R.string.app_name)), Toast.LENGTH_LONG).show()
                }
            }
        }
        return carteEmpilee(Color.rgb(18, 20, 26), "02", getString(R.string.carte_logos), scene,
            texteBas(getString(R.string.titre2_logos), phrase(getString(R.string.phrase_logos)), lien)
        ) { ouvrir(GroupesActivity::class.java) }
    }

    // CARTE 3 : fonds d'écran. Ici, la carte ENTIÈRE est un fond animé ; le texte est posé
    // par-dessus, sur un dégradé noir pour rester lisible.
    private fun carteFonds(): FrameLayout {
        val chiffre = Style.texte(this, getString(R.string.chiffre_fonds), 12.5f, Style.DOUX)
        val bas = texteBas(getString(R.string.titre2_fonds), phrase(getString(R.string.phrase_fonds)), chiffre).apply {
            setPadding(px(24f), px(60f), px(24f), px(22f))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(230, 0, 0, 0), Color.argb(230, 0, 0, 0)))
        }
        return cadre(Color.rgb(3, 3, 4), getString(R.string.carte_fonds)) { ouvrir(FondsActivity::class.java) }.apply {
            addView(DessinFonds(this@MainActivity))
            addView(numero("03"), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = px(24f); topMargin = px(22f)
            })
            addView(bas, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        }
    }

    // ================= LES POINTS =================
    // (un appui sur un point appelle manege.montrer : le manège y va par le chemin le plus court ;
    //  si c'est un retour en arrière, le défilement automatique s'arrête, comme au doigt)

    // La carte affichée a changé : chaque point se redessine (celui de la nouvelle carte s'allonge
    // en trait, l'ancien redevient un rond ; voir PointManege dans Manege.kt).
    private fun majPoints() {
        for (point in points) point.invalidate()
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
        etat.text = getString(if (actif) R.string.etat_on else R.string.etat_pause)
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
            text = getString(R.string.toutes_animations)
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
        entree(getString(R.string.menu_accueil)) { }
        entree(getString(R.string.carte_apps)) { ouvrir(AppsActivity::class.java) }
        entree(getString(R.string.menu_logos)) { ouvrir(GroupesActivity::class.java) }
        entree(getString(R.string.carte_fonds)) { ouvrir(FondsActivity::class.java) }
        entree(getString(R.string.reglages)) { ouvrir(ReglagesActivity::class.java) }
        entree(getString(R.string.menu_journal)) { ouvrir(JournalActivity::class.java) }
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
