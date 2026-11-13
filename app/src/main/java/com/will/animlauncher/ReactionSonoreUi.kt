package com.will.animlauncher

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

// LE BOUTON ROND de la réaction sonore (en bas à droite de l'accueil) et SA FENÊTRE d'explication.
// (Le moteur — la fenêtre posée par-dessus le téléphone et le tour lumineux — est dans ReactionSonore.kt.)

// Le rond : un logo minimaliste (4 barres, comme une onde sonore) et un halo qui « respire » doucement.
// Quand le mode est allumé et que de la musique joue, les barres dansent avec elle.
class BoutonSon(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val lueur = Color.rgb(120, 200, 210)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flou = BlurMaskFilter(9f * d, BlurMaskFilter.Blur.NORMAL)
    private val repos = floatArrayOf(0.45f, 0.85f, 1f, 0.6f)        // la hauteur des 4 barres quand rien ne joue
    private val niveaux = FloatArray(4)

    init {
        contentDescription = context.getString(R.string.son_titre)
        setLayerType(LAYER_TYPE_SOFTWARE, null)                     // (le halo flou a besoin de ce mode de dessin)
    }

    override fun onDraw(c: Canvas) {
        val t = (SystemClock.uptimeMillis() % 32000L) / 1000f       // (on reboucle toutes les 32 s = 10 respirations pile)
        val cx = width / 2f
        val cy = height / 2f
        val marge = 10f * d                                         // la place du halo autour du rond
        val r = min(cx, cy) - marge
        val allume = ReactionSonore.actif(context) && ReactionSonore.permission(context)
        val enMusique = allume && EcouteSon.enMarche
        val souffle = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 3.2f)      // un va-et-vient toutes les 3,2 s
        val force = if (enMusique) 0.20f + 0.25f * EcouteSon.grave else 0.10f + 0.10f * souffle

        p.style = Paint.Style.FILL; p.color = lueur; p.maskFilter = flou; p.alpha = (force * 255).toInt()
        c.drawCircle(cx, cy, r + 2f * d, p)
        p.maskFilter = null; p.color = Style.CARTE; p.alpha = 255
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f * d; p.color = lueur
        p.alpha = ((if (allume) 0.75f else 0.25f + 0.25f * souffle) * 255).toInt()
        c.drawCircle(cx, cy, r, p)

        // les 4 barres
        val h = r * 0.78f
        p.color = Style.TEXTE; p.alpha = 235; p.strokeWidth = h * 0.15f; p.strokeCap = Paint.Cap.ROUND
        val cibles = floatArrayOf(EcouteSon.grave, EcouteSon.medium, EcouteSon.aigu, abs(sin(t * 3f)) * EcouteSon.medium)
        for (i in 0..3) {
            val cible = if (enMusique) 0.25f + 0.75f * cibles[i] else repos[i]
            niveaux[i] += (cible - niveaux[i]) * 0.25f
            val x = cx + (i - 1.5f) * h * 0.34f
            c.drawLine(x, cy - h * niveaux[i] / 2f, x, cy + h * niveaux[i] / 2f, p)
        }
        postInvalidateOnAnimation()                                 // image suivante (le rond est toujours un peu vivant)
    }
}

object FenetreSon {
    const val DEMANDE = 4107            // le numéro de notre question « micro » (pour reconnaître la réponse)
    private var ouverte: Dialog? = null

    // les 3 couleurs du tour lumineux (dans le même ordre que ReactionSonore : 0 = calme, 1 = moyen, 2 = quand ça envoie)
    private val NOMS = intArrayOf(R.string.son_calme, R.string.son_moyen, R.string.son_fort)
    private val DESCRIPTIONS = intArrayOf(R.string.son_calme_desc, R.string.son_moyen_desc, R.string.son_fort_desc)
    // le nom des 5 paliers (pour le diagnostic), du plus lent au plus fort
    private val PALIERS = intArrayOf(R.string.son_palier1, R.string.son_calme, R.string.son_moyen, R.string.son_fort, R.string.son_palier5)

    // Vrai tant que la fenêtre est affichée (l'accueil ne fait pas défiler ses cartes pendant ce temps).
    fun estOuverte(): Boolean = ouverte?.isShowing == true

    // Ouvre la fenêtre : elle monte du bas de l'écran. On peut la refermer en la faisant glisser vers le bas.
    fun ouvrir(a: Activity) {
        ouverte?.dismiss()
        val dialogue = Dialog(a)
        dialogue.requestWindowFeature(Window.FEATURE_NO_TITLE)
        // à la fermeture : on arrête l'aperçu des couleurs (remplacé plus bas par le diagnostic s'il y en a un)
        dialogue.setOnDismissListener { ReactionSonore.montrer(-1); if (ouverte === dialogue) ouverte = null }
        dialogue.setContentView(FeuilleGlissante(a, contenu(a, dialogue),
            fermer = {
                dialogue.window?.setWindowAnimations(0)     // déjà sortie par le bas : pas de 2e animation de fermeture
                dialogue.dismiss()
            },
            voile = { f -> dialogue.window?.setDimAmount(0.7f * f) }      // le fond s'éclaircit à mesure qu'on descend
        ))
        dialogue.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setWindowAnimations(android.R.style.Animation_InputMethod)      // l'animation « monte du bas » d'Android
            setDimAmount(0.7f)
        }
        ouverte = dialogue
        dialogue.show()
    }

    private fun contenu(a: Activity, dialogue: Dialog): ScrollView {
        fun px(v: Float) = Style.px(a, v)
        val allume = ReactionSonore.actif(a) && ReactionSonore.permission(a)
        val defilant = ScrollView(a)          // (sur un petit écran, le tout peut défiler)
        val colonne = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = Style.arrondi(a, Color.rgb(16, 16, 19), 28f, Style.BORD)
            setPadding(px(22f), px(12f), px(22f), px(18f))
        }
        fun ligne(texte: String, couleur: Int, gras: Boolean = false, haut: Float = 0f) =
            colonne.addView(Style.texte(a, texte, 14f, couleur, gras).apply {
                gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f); setPadding(0, px(haut), 0, 0)
            })

        // la petite poignée, puis le logo en grand
        colonne.addView(View(a).apply { background = Style.arrondi(a, Color.rgb(70, 70, 78), 2f) },
            LinearLayout.LayoutParams(px(40f), px(4f)))
        colonne.addView(BoutonSon(a), LinearLayout.LayoutParams(px(92f), px(92f)).apply { topMargin = px(8f) })
        colonne.addView(Style.texte(a, a.getString(R.string.son_titre), 22f, Style.TEXTE, gras = true).apply { gravity = Gravity.CENTER })

        // l'explication
        ligne(a.getString(R.string.son_expl_mode), Style.DOUX, haut = 10f)
        ligne(a.getString(R.string.son_expl_rien), Style.TEXTE, gras = true, haut = 12f)
        ligne(a.getString(R.string.son_expl_direct), Style.MUET, haut = 2f)
        ligne(a.getString(R.string.son_expl_internet), Style.MUET)     // (13/11 : l'app a Internet pour le fond 3D, mais le son ne sort jamais du téléphone)
        ligne(a.getString(R.string.son_expl_video), Style.MUET, haut = 10f)

        // LES TROIS COULEURS (10/11) : calme, moyen, quand ça envoie. L'app choisit toute seule selon la musique.
        // Chaque ligne montre son dégradé et sa pastille. Un appui sur une ligne ouvre la ROUE des couleurs
        // juste en dessous (un 2e appui la referme). Pendant qu'on tourne la roue, le tour de l'écran prend
        // cette couleur en direct (si le mode est allumé), même sans musique.
        colonne.addView(Style.titreCarte(a, a.getString(R.string.son_couleurs)).apply { setPadding(0, px(16f), 0, px(6f)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        ReactionSonore.lireCouleurs(a)
        var choisie = -1                                  // la ligne ouverte : -1 = aucune, 0 = calme, 1 = moyen, 2 = ça envoie
        val lignes = ArrayList<View>()
        val degrades = ArrayList<View>()
        val pastilles = ArrayList<View>()
        val roue = RoueTeinte(a)
        val parDefaut = Style.texte(a, a.getString(R.string.son_defaut), 14f, Style.DOUX, gras = true).apply {
            gravity = Gravity.CENTER
            setPadding(px(18f), px(10f), px(18f), px(4f))
        }
        // la roue, sa phrase d'aide et « Par défaut » : cachés tant qu'aucune ligne n'est ouverte
        val reglage = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            addView(roue, LinearLayout.LayoutParams(px(210f), px(210f)).apply { topMargin = px(12f) })
            addView(Style.texte(a, a.getString(R.string.son_roue_aide), 12.5f, Style.MUET).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(6f) })
            addView(parDefaut, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // remet à jour les dégradés, les pastilles et le contour de la ligne ouverte
        fun majCouleurs() {
            for (i in 0..2) {
                val pal = ReactionSonore.palette(ReactionSonore.teinte(i), i)
                degrades[i].background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(pal[0], pal[1], pal[2])).apply {
                    cornerRadius = px(7f).toFloat()
                }
                pastilles[i].background = Style.arrondi(a, pal[1], 11f)
                lignes[i].background = Style.arrondi(a, Color.argb(14, 255, 255, 255), 14f,
                    if (i == choisie) Color.rgb(120, 200, 210) else Style.BORD)
            }
        }
        for (i in 0..2) {
            val degrade = View(a)
            val pastille = View(a)
            degrades.add(degrade)
            pastilles.add(pastille)
            val ligneCouleur = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(14f), px(9f), px(14f), px(9f))
                addView(LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Style.texte(a, a.getString(NOMS[i]), 15f, Style.TEXTE, gras = true))
                    addView(Style.texte(a, a.getString(DESCRIPTIONS[i]), 12.5f, Style.MUET))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(degrade, LinearLayout.LayoutParams(px(44f), px(14f)).apply { leftMargin = px(10f) })
                addView(pastille, LinearLayout.LayoutParams(px(22f), px(22f)).apply { leftMargin = px(10f) })
                isClickable = true
                setOnClickListener {
                    choisie = if (choisie == i) -1 else i
                    reglage.visibility = if (choisie >= 0) View.VISIBLE else View.GONE
                    if (choisie >= 0) {
                        roue.placer(ReactionSonore.teinte(choisie), choisie)
                        // on fait défiler la fenêtre pour que la roue soit bien visible
                        defilant.post { defilant.smoothScrollTo(0, lignes[0].top - px(30f)) }
                    }
                    ReactionSonore.montrer(choisie)           // le tour de l'écran montre la couleur qu'on règle
                    majCouleurs()
                }
            }
            lignes.add(ligneCouleur)
            colonne.addView(ligneCouleur, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(6f) })
        }
        colonne.addView(reglage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // les deux paliers extrêmes n'ont pas de couleur à eux : on dit ce qu'ils ajoutent
        ligne(a.getString(R.string.son_extremes), Style.MUET, haut = 8f)
        // LES OPTIONS (11/11) : confettis oui / non, et l'écran noir qui garde le tour allumé (voir OptionsSon.kt)
        OptionsSon.ajouter(a, colonne)
        // la roue tourne : on change la couleur de la ligne ouverte (« fini » = le doigt est levé : on enregistre)
        roue.quandChange = { teinte, fini ->
            if (choisie >= 0) {
                ReactionSonore.definirTeinte(a, choisie, teinte, fini)
                majCouleurs()
            }
        }
        parDefaut.setOnClickListener {
            if (choisie >= 0) {
                val origine = ReactionSonore.TEINTES_DEFAUT[choisie]
                ReactionSonore.definirTeinte(a, choisie, origine, true)
                roue.placer(origine, choisie)
                majCouleurs()
            }
        }
        majCouleurs()

        // le DIAGNOSTIC (quand le mode est allumé) : ce que l'app voit vraiment, mis à jour 2 fois par seconde.
        // Sert à comprendre pourquoi les bords n'apparaissent pas (à envoyer à Claude si besoin).
        if (allume) {
            val diag = Style.texte(a, "", 12.5f, Style.MUET).apply {
                background = Style.arrondi(a, Color.argb(10, 255, 255, 255), 12f, Style.BORD)
                setPadding(px(14f), px(10f), px(14f), px(10f))
                setLineSpacing(0f, 1.25f)
                setTextIsSelectable(true)          // on peut le copier d'un appui long
            }
            colonne.addView(diag, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(14f) })
            val minuteur = Handler(Looper.getMainLooper())
            val maj = object : Runnable {
                override fun run() {
                    diag.text = diagnostic(a)
                    minuteur.postDelayed(this, 500)
                }
            }
            minuteur.post(maj)
            dialogue.setOnDismissListener { minuteur.removeCallbacks(maj); ReactionSonore.montrer(-1); if (ouverte === dialogue) ouverte = null }
        }

        // les avertissements, seulement quand ils servent
        if (!allume) ligne(a.getString(R.string.son_expl_micro), Style.MUET, haut = 14f)
        if (AppWatcherService.actif == null) ligne(a.getString(R.string.son_service), Style.AMBRE, haut = 10f)

        // les boutons
        val principal = if (allume) Style.boutonDiscret(a, a.getString(R.string.son_desactiver)) {
            ReactionSonore.definirActif(a, false)
            dialogue.dismiss()
        } else Style.boutonBlanc(a, a.getString(R.string.son_activer)) { activer(a) }
        colonne.addView(principal, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(16f) })
        colonne.addView(Style.texte(a, a.getString(if (allume) R.string.son_fermer else R.string.son_plus_tard), 14f, Style.MUET).apply {
            gravity = Gravity.CENTER
            setPadding(0, px(14f), 0, px(6f))
            setOnClickListener { dialogue.dismiss() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        defilant.addView(colonne)
        return defilant
    }

    // Le texte du diagnostic : une ligne par vérification, ✓ si c'est bon, ✗ sinon.
    private fun diagnostic(a: Activity): String {
        fun l(ok: Boolean, texte: String) = (if (ok) "✓ " else "✗ ") + texte
        val oui = a.getString(R.string.son_diag_oui)
        val non = a.getString(R.string.son_diag_non)
        val ecoute = when {
            EcouteSon.enMarche -> a.getString(R.string.son_diag_ecoute_ok, EcouteSon.mesures)
            EcouteSon.derniereErreur != null -> a.getString(R.string.son_diag_ecoute_erreur, EcouteSon.derniereErreur)
            else -> a.getString(R.string.son_diag_ecoute_arret)
        }
        return listOf(
            a.getString(R.string.son_diag_titre),
            l(ReactionSonore.servicePret, a.getString(R.string.son_diag_service, if (ReactionSonore.servicePret) oui else non)),
            l(ReactionSonore.fenetrePosee, a.getString(R.string.son_diag_fenetre, if (ReactionSonore.fenetrePosee) oui else non)),
            l(ReactionSonore.musique, a.getString(R.string.son_diag_musique, if (ReactionSonore.musique) oui else non)),
            l(!ReactionSonore.pleinEcran, a.getString(R.string.son_diag_plein, if (ReactionSonore.pleinEcran) oui else non, ReactionSonore.sourcePleinEcran)),
            l(EcouteSon.enMarche, ecoute),
            l(ReactionSonore.dessine, a.getString(R.string.son_diag_dessine, if (ReactionSonore.dessine) oui else non)),
            a.getString(R.string.son_diag_niveaux, (EcouteSon.grave * 100).toInt(), (EcouteSon.medium * 100).toInt(), (EcouteSon.aigu * 100).toInt()),
            // le palier du moment (1 à 5) et l'énergie mesurée (les frontières sont dans ReglagesSon.kt)
            a.getString(R.string.son_diag_moment, a.getString(PALIERS[(EcouteSon.palier - 1).coerceIn(0, 4)]), EcouteSon.energieVue)
        ).joinToString("\n")
    }

    // « Activer » : si Android n'a pas encore donné la permission, on la demande (sa fenêtre officielle
    // s'affiche) ; la réponse arrive dans MainActivity.onRequestPermissionsResult, qui appelle reponse().
    private fun activer(a: Activity) {
        if (ReactionSonore.permission(a)) allumer(a)
        else a.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), DEMANDE)
    }

    fun reponse(a: Activity, demande: Int, resultats: IntArray) {
        if (demande != DEMANDE) return
        if (resultats.isNotEmpty() && resultats[0] == PackageManager.PERMISSION_GRANTED) allumer(a)
        else Toast.makeText(a, a.getString(R.string.son_refuse), Toast.LENGTH_LONG).show()
    }

    private fun allumer(a: Activity) {
        ReactionSonore.definirActif(a, true)
        ouverte?.dismiss()
        Toast.makeText(a, a.getString(R.string.son_active), Toast.LENGTH_SHORT).show()
    }
}

// LA ROUE DES COULEURS (10/11) : un anneau arc-en-ciel avec une poignée qu'on fait glisser.
// L'endroit de la poignée sur l'anneau = la « teinte », un angle de 0 à 360 (0 = rouge, à droite ; on tourne
// dans le sens des aiguilles d'une montre : jaune, vert, bleu, violet…). Le disque du milieu montre la couleur choisie.
class RoueTeinte(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var arcEnCiel: SweepGradient? = null
    private var teinte = 0f
    private var mode = 0                           // quelle couleur on règle (pour montrer la même nuance que le tour)
    private var prise = false                      // vrai pendant que le doigt tient la poignée

    // Appelé à chaque mouvement : (la teinte, vrai si le doigt vient de se lever).
    var quandChange: ((Float, Boolean) -> Unit)? = null

    // Met la poignée sur une teinte (quand on ouvre la roue, ou « Par défaut »).
    fun placer(nouvelle: Float, pourMode: Int) {
        teinte = nouvelle; mode = pourMode
        invalidate()
    }

    private fun rayon(): Float = min(width, height) / 2f - 16f * d      // le rayon du milieu de l'anneau

    override fun onSizeChanged(largeur: Int, hauteur: Int, ancienneL: Int, ancienneH: Int) {
        // 13 couleurs, une tous les 30° (la 13e = la 1re : la boucle est bouclée)
        val tour = IntArray(13) { Color.HSVToColor(floatArrayOf((it * 30) % 360f, 0.85f, 1f)) }
        arcEnCiel = SweepGradient(largeur / 2f, hauteur / 2f, tour, null)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = rayon()
        val epaisseur = 22f * d
        val couleur = ReactionSonore.palette(teinte, mode)[1]
        // l'anneau
        p.style = Paint.Style.STROKE; p.strokeWidth = epaisseur; p.shader = arcEnCiel
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        // le disque du milieu : la couleur choisie
        p.style = Paint.Style.FILL; p.color = couleur
        c.drawCircle(cx, cy, r * 0.42f, p)
        // la poignée : un rond de la couleur choisie, cerclé de blanc
        val angle = Math.toRadians(teinte.toDouble()).toFloat()
        val x = cx + r * cos(angle)
        val y = cy + r * sin(angle)
        c.drawCircle(x, y, epaisseur * 0.5f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f * d; p.color = Color.WHITE
        c.drawCircle(x, y, epaisseur * 0.5f, p)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val dx = ev.x - width / 2f
        val dy = ev.y - height / 2f
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // il faut toucher l'anneau (pas le milieu) ; sinon on laisse la fenêtre défiler normalement
                if (hypot(dx, dy) < rayon() * 0.55f) return false
                prise = true
                // « ne m'interrompez pas » : sans ça, glisser vers le bas sur la roue fermerait la fenêtre
                parent?.requestDisallowInterceptTouchEvent(true)
                tourner(dx, dy, false)
            }
            MotionEvent.ACTION_MOVE -> if (prise) tourner(dx, dy, false)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (prise) {
                prise = false
                quandChange?.invoke(teinte, true)
            }
        }
        return true
    }

    // La teinte = l'angle du doigt autour du centre (atan2 donne -180..180 : on le ramène entre 0 et 360).
    private fun tourner(dx: Float, dy: Float, fini: Boolean) {
        teinte = ((Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()) + 360f) % 360f
        quandChange?.invoke(teinte, fini)
        invalidate()
    }
}

// LA FEUILLE QU'ON FAIT GLISSER VERS LE BAS POUR LA FERMER (10/11).
// Elle entoure le contenu de la fenêtre (qui peut défiler). Le principe :
//  - si le contenu est tout en HAUT et que le doigt descend (plus qu'il ne va sur le côté),
//    c'est la feuille entière qui suit le doigt vers le bas (au lieu de faire défiler le contenu) ;
//  - au lâcher : descendue de plus d'un quart de sa hauteur, ou lancée vite vers le bas → elle se ferme ;
//    sinon elle remonte se remettre en place.
// Si le contenu a déjà été défilé vers le bas, glisser vers le bas le fait d'abord remonter, comme d'habitude.
class FeuilleGlissante(
    context: Context,
    private val defilant: ScrollView,
    private val fermer: () -> Unit,
    private val voile: (Float) -> Unit         // 1 = fond sombre normal, 0 = plus de fond sombre
) : FrameLayout(context) {
    private val dp = resources.displayMetrics.density
    private val seuil = ViewConfiguration.get(context).scaledTouchSlop
    private var vitesse: VelocityTracker? = null
    private var xDepart = 0f
    private var yDepart = 0f
    private var tire = false                   // vrai pendant qu'on fait glisser la feuille

    init { addView(defilant, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)) }

    // On utilise « rawY » (la position sur l'ÉCRAN) : la feuille bouge sous le doigt,
    // donc sa position à elle ne servirait pas de repère fiable.
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        suivre(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { xDepart = ev.rawX; yDepart = ev.rawY; tire = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - xDepart
                val dy = ev.rawY - yDepart
                // « canScrollVertically(-1) » = le contenu peut encore remonter (il n'est pas tout en haut)
                if (dy > seuil && dy > abs(dx) && !defilant.canScrollVertically(-1)) {
                    tire = true
                    yDepart = ev.rawY                  // la feuille part d'ici : pas de saut
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
                val descente = (ev.rawY - yDepart).coerceAtLeast(0f)       // (jamais au-dessus de sa place)
                translationY = descente
                voile(1f - (descente / height.coerceAtLeast(1)).coerceIn(0f, 1f))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vitesse?.computeCurrentVelocity(1000)
                val vy = vitesse?.yVelocity ?: 0f                           // en pixels par seconde (positif = vers le bas)
                val lancee = vy > 900f * dp
                if (tire && (translationY > height * 0.25f || lancee)) {
                    // elle finit de descendre jusqu'en bas, puis la fenêtre se ferme
                    animate().translationY(height.toFloat()).setDuration(200)
                        .setInterpolator(DecelerateInterpolator())
                        .setUpdateListener { voile(1f - (translationY / height.coerceAtLeast(1)).coerceIn(0f, 1f)) }
                        .withEndAction { fermer() }.start()
                } else {
                    // pas assez : elle remonte à sa place
                    animate().translationY(0f).setDuration(220)
                        .setInterpolator(DecelerateInterpolator())
                        .setUpdateListener { voile(1f - (translationY / height.coerceAtLeast(1)).coerceIn(0f, 1f)) }
                        .start()
                }
                tire = false
                vitesse?.recycle(); vitesse = null
            }
        }
        return true
    }

    // Pour mesurer la vitesse du doigt, on lui donne aussi les positions sur l'écran.
    private fun suivre(ev: MotionEvent) {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) { vitesse?.recycle(); vitesse = null }
        if (vitesse == null) vitesse = VelocityTracker.obtain()
        val copie = MotionEvent.obtain(ev)
        copie.setLocation(ev.rawX, ev.rawY)
        vitesse?.addMovement(copie)
        copie.recycle()
    }
}
