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
import android.graphics.drawable.ColorDrawable
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
import kotlin.math.min
import kotlin.math.sin

// LE BOUTON ROND de la réaction sonore (en bas à droite de l'accueil) et SA FENÊTRE d'explication.
// (Le moteur — la fenêtre posée par-dessus le téléphone et les 4 styles — est dans ReactionSonore.kt.)

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

    private val NOMS = intArrayOf(R.string.son_style_1, R.string.son_style_2, R.string.son_style_3, R.string.son_style_4)
    private val DESCRIPTIONS = intArrayOf(R.string.son_desc_1, R.string.son_desc_2, R.string.son_desc_3, R.string.son_desc_4)

    // Ouvre la fenêtre : elle monte du bas de l'écran.
    fun ouvrir(a: Activity) {
        ouverte?.dismiss()
        val dialogue = Dialog(a)
        dialogue.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialogue.setOnDismissListener { if (ouverte === dialogue) ouverte = null }   // (remplacé par le diagnostic s'il y en a un)
        dialogue.setContentView(contenu(a, dialogue))
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

    private fun contenu(a: Activity, dialogue: Dialog): View {
        fun px(v: Float) = Style.px(a, v)
        val allume = ReactionSonore.actif(a) && ReactionSonore.permission(a)
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
        if (BuildConfig.STORE) ligne(a.getString(R.string.son_expl_internet), Style.MUET)     // vrai seulement en version Store
        ligne(a.getString(R.string.son_expl_video), Style.MUET, haut = 10f)

        // le choix du style : 4 lignes, un point blanc devant celle qui est choisie
        colonne.addView(Style.titreCarte(a, a.getString(R.string.son_style)).apply { setPadding(0, px(16f), 0, px(6f)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val points = ArrayList<View>()
        fun majPoints() {
            for ((i, point) in points.withIndex()) {
                point.background = if (i == ReactionSonore.style) Style.arrondi(a, Style.TEXTE, 6f)
                                   else Style.arrondi(a, Color.TRANSPARENT, 6f, Color.rgb(90, 90, 98))
            }
        }
        ReactionSonore.lireStyle(a)
        for (i in 0 until ReactionSonore.STYLES) {
            val point = View(a)
            points.add(point)
            colonne.addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Style.arrondi(a, Color.argb(14, 255, 255, 255), 14f, Style.BORD)
                setPadding(px(14f), px(9f), px(14f), px(9f))
                addView(point, LinearLayout.LayoutParams(px(12f), px(12f)).apply { rightMargin = px(12f) })
                addView(LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Style.texte(a, a.getString(NOMS[i]), 15f, Style.TEXTE, gras = true))
                    addView(Style.texte(a, a.getString(DESCRIPTIONS[i]), 12.5f, Style.MUET))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                isClickable = true
                setOnClickListener { ReactionSonore.definirStyle(a, i); majPoints() }     // change tout de suite, même en musique
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(6f) })
        }
        majPoints()

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
            dialogue.setOnDismissListener { minuteur.removeCallbacks(maj); if (ouverte === dialogue) ouverte = null }
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

        // (sur un petit écran, le tout peut défiler)
        return ScrollView(a).apply { addView(colonne) }
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
            a.getString(R.string.son_diag_niveaux, (EcouteSon.grave * 100).toInt(), (EcouteSon.medium * 100).toInt(), (EcouteSon.aigu * 100).toInt())
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
