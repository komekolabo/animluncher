package com.will.animlauncher

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import kotlin.math.min

// LES ÉCRANS DU STYLE AUTO (18/11, maquette validée par Will sur le canevas Design « Personnalisation auto »).
// Le moteur (analyse des icônes, calcul des styles, sauvegarde) est dans StyleAuto.kt. Ici, 4 états :
//
//  ANALYSE : « J'analyse les icônes de tes apps… » (une ou deux secondes, pendant que le moteur travaille)
//  CHOIX   : l'aperçu de l'accueil dans le style choisi, puis les 5 styles (un appui = l'aperçu change)
//  RECAP   : « Ce qui va changer », une ligne par chose, chacune avec son interrupteur, et « Appliquer »
//  FINI    : c'est fait ; si on l'a laissé coché, un bouton « Poser » par dossier pour le mettre sur l'accueil
//            (Android montre sa fenêtre pour chaque widget : on ne peut pas les poser tous d'un coup).
//
// Le style suit celui du reste de l'app (Style.kt) : noir, cartes gris très foncé, bouton blanc cassé.
class StyleAutoActivity : Activity() {

    private companion object {
        const val ANALYSE = 0
        const val CHOIX = 1
        const val RECAP = 2
        const val FINI = 3
    }

    private var etat = ANALYSE
    private var analyse: StyleAuto.Analyse? = null
    private var resultats: List<StyleAuto.Resultat> = emptyList()      // les 5 styles, calculés une seule fois
    private var choisi = StyleAuto.DEGRADE
    // les interrupteurs de l'écran RECAP (gardés si on revient en arrière)
    private var avecCouleurs = true
    private var avecWidgets = true
    private var avecFond = true
    private var avecSon = true
    private var avecAnimations = true

    private fun px(v: Float) = Style.px(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Style.fenetreNoire(this)
        construire()
        // L'ANALYSE tourne dans un fil à part (sinon l'écran serait figé pendant qu'on dessine les icônes).
        Thread {
            val a = StyleAuto.analyser(applicationContext)
            val r = StyleAuto.STYLES.map { StyleAuto.calculer(it, a) }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                analyse = a; resultats = r
                // on repart du dernier style appliqué, s'il y en a un
                StyleAuto.dernierStyle(this).takeIf { it in StyleAuto.STYLES }?.let { choisi = it }
                aller(CHOIX)
            }
        }.start()
    }

    // Au retour sur l'écran FINI (après la fenêtre d'Android « Ajouter à l'écran d'accueil »), les boutons
    // « Poser » des dossiers qui viennent d'être posés deviennent « Posé ».
    override fun onResume() {
        super.onResume()
        if (etat == FINI) construire()
    }

    private fun aller(nouveau: Int) { etat = nouveau; construire() }

    @Deprecated("Remplacé par OnBackInvokedCallback sur Android 13+, mais marche toujours ici")
    override fun onBackPressed() {
        if (etat == RECAP) aller(CHOIX) else super.onBackPressed()
    }

    // ================= LA CONSTRUCTION DE L'ÉCRAN =================

    private fun construire() {
        val titre = getString(if (etat == RECAP) R.string.sa_recap_titre else R.string.sa_titre)
        val entete = Style.entete(this, titre)
        // la flèche de l'en-tête : sur RECAP, on revient au choix ; ailleurs, on quitte
        (entete.getChildAt(0) as? View)?.setOnClickListener { if (etat == RECAP) aller(CHOIX) else finish() }
        val corps: View = when (etat) {
            ANALYSE -> ecranAnalyse()
            CHOIX -> ecranChoix()
            RECAP -> ecranRecap()
            else -> ecranFini()
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Style.NOIR)
            addView(entete)
            addView(corps, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    private fun resultat(): StyleAuto.Resultat? = resultats.getOrNull(choisi)
    private fun nomStyle(style: Int) = getString(StyleAuto.NOMS[style])
    private fun nomFond(r: StyleAuto.Resultat) = getString(if (r.fond == "galets") R.string.fd_nom_galets else R.string.fd_nom_nuit)
    private fun nomDossier(id: String) = Groupes.categorie(id)?.nom ?: "?"

    // Une colonne qui défile, avec les marges de l'app.
    private fun defilement(contenu: View): ScrollView = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        addView(contenu)
    }
    private fun colonne(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(18f), px(2f), px(18f), px(24f))
    }

    // Le bas d'écran : le bouton blanc (toujours visible, il ne défile pas).
    private fun avecBouton(haut: View, bouton: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(haut, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(bouton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = px(18f); rightMargin = px(18f); bottomMargin = px(16f); topMargin = px(6f)
        })
    }

    // ================= ANALYSE =================

    private fun ecranAnalyse(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(AnneauStyle(this@StyleAutoActivity, null), LinearLayout.LayoutParams(px(64f), px(64f)))
        addView(Style.texte(this@StyleAutoActivity, getString(R.string.sa_analyse), 14f, Style.MUET).apply {
            gravity = Gravity.CENTER; setPadding(px(24f), px(18f), px(24f), 0)
        })
    }

    // ================= CHOIX =================

    private fun ecranChoix(): View {
        val a = analyse ?: return ecranAnalyse()
        val r = resultat() ?: return ecranAnalyse()
        val col = colonne()
        col.addView(Style.texte(this, getString(R.string.sa_trouve, a.nbApps, a.dossiers.size), 13f, Style.MUET).apply {
            setPadding(px(4f), 0, 0, px(12f))
        })
        if (a.dossiers.isEmpty()) {
            col.addView(Style.texte(this, getString(R.string.sa_aucun_dossier), 14f, Style.DOUX))
        }
        // l'aperçu de l'accueil : un mini téléphone centré
        col.addView(ApercuStyle(this).apply { montrer(r, a.dossiers.map { nomDossier(it) }) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(352f)))
        col.addView(Style.texte(this, getString(R.string.sa_legende, nomFond(r)), 11.5f, Style.MUET).apply {
            gravity = Gravity.CENTER; setPadding(0, px(8f), 0, px(16f))
        })
        // les 5 styles
        for (s in StyleAuto.STYLES) col.addView(ligneStyle(s))
        // annuler un style appliqué avant (discret, en bas)
        if (StyleAuto.aSauvegarde(this)) {
            col.addView(Style.boutonDiscret(this, getString(R.string.sa_annuler_dernier)) { annuler() },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(8f) })
        }
        val bouton = Style.boutonBlanc(this, getString(R.string.sa_continuer)) { aller(RECAP) }
        bouton.isEnabled = a.dossiers.isNotEmpty() || a.compatibles.isNotEmpty()
        return avecBouton(defilement(col), bouton)
    }

    // Une ligne de style : 4 petits ronds (les couleurs de ses premiers dossiers), le nom, la phrase.
    // Le style choisi a un contour blanc (comme le rond choisi sous la roue des couleurs).
    private fun ligneStyle(s: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val choisiIci = s == choisi
        background = Style.arrondi(this@StyleAutoActivity, Style.CARTE, 18f, if (choisiIci) Style.TEXTE else Style.BORD)
            .apply { if (choisiIci) setStroke(px(2f), Style.TEXTE) }
        setPadding(px(14f), px(10f), px(14f), px(10f))
        minimumHeight = px(58f)
        val couleurs = resultats.getOrNull(s)?.couleurs?.values?.take(4)?.map { Teinte.vitrine(it) } ?: emptyList()
        addView(PointsStyle(this@StyleAutoActivity, couleurs), LinearLayout.LayoutParams(px(46f), px(18f)))
        addView(LinearLayout(this@StyleAutoActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(10f), 0, 0, 0)
            addView(Style.texte(this@StyleAutoActivity, nomStyle(s), 14.5f, Style.TEXTE, gras = true))
            addView(Style.texte(this@StyleAutoActivity, getString(StyleAuto.DESCRIPTIONS[s]), 12f, Style.MUET).apply { setPadding(0, px(2f), 0, 0) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = px(8f) }
        contentDescription = nomStyle(s)
        isClickable = true
        setOnClickListener { choisi = s; construire() }
    }

    // ================= RECAP =================

    private fun ecranRecap(): View {
        val a = analyse ?: return ecranAnalyse()
        val r = resultat() ?: return ecranAnalyse()
        val col = colonne()
        // la pastille du style choisi
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Style.arrondi(this@StyleAutoActivity, Style.CARTE, 18f, Color.rgb(42, 42, 48))
            setPadding(px(10f), px(8f), px(14f), px(8f))
            addView(PointsStyle(this@StyleAutoActivity, r.couleurs.values.take(4).map { Teinte.vitrine(it) }),
                LinearLayout.LayoutParams(px(46f), px(18f)))
            addView(Style.texte(this@StyleAutoActivity, nomStyle(r.style), 13.5f, Style.TEXTE, gras = true).apply { setPadding(px(6f), 0, 0, 0) })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = px(14f) })

        val ourFond = try { WallpaperManager.getInstance(this).wallpaperInfo?.component == CarnetFond.service(this, CarnetFond.APP) } catch (_: Exception) { false }
        val texteFond = getString(R.string.sa_l_fond_desc, nomFond(r)) + if (ourFond) "" else "\n" + getString(R.string.sa_l_fond_android)
        if (a.dossiers.isNotEmpty()) {
            col.addView(ligneChoix(getString(R.string.sa_l_couleurs), getString(R.string.sa_l_couleurs_desc), avecCouleurs) { avecCouleurs = it })
            col.addView(ligneChoix(getString(R.string.sa_l_widgets), getString(R.string.sa_l_widgets_desc, a.dossiers.size), avecWidgets) { avecWidgets = it })
        }
        col.addView(ligneChoix(getString(R.string.sa_l_fond), texteFond, avecFond) { avecFond = it })
        col.addView(ligneChoix(getString(R.string.sa_l_son), getString(R.string.sa_l_son_desc), avecSon) { avecSon = it })
        if (a.compatibles.isNotEmpty()) {
            col.addView(ligneChoix(getString(R.string.sa_l_anim), getString(R.string.sa_l_anim_desc, a.compatibles.size), avecAnimations) { avecAnimations = it })
        }
        col.addView(Style.texte(this, getString(R.string.sa_garde), 12.5f, Style.MUET).apply {
            gravity = Gravity.CENTER; setPadding(px(8f), px(10f), px(8f), 0)
        })
        return avecBouton(defilement(col), Style.boutonBlanc(this, getString(R.string.sa_appliquer)) { appliquer() })
    }

    // Une ligne : titre + phrase grise à gauche, interrupteur à droite. Toute la ligne bascule l'interrupteur.
    private fun ligneChoix(titre: String, detail: String, coche: Boolean, change: (Boolean) -> Unit): View {
        val inter: Switch = Style.interrupteur(this).apply {
            isChecked = coche
            setOnCheckedChangeListener { _, v -> change(v) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Style.arrondi(this@StyleAutoActivity, Style.CARTE, 18f, Style.BORD)
            setPadding(px(14f), px(12f), px(8f), px(12f))
            addView(LinearLayout(this@StyleAutoActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(Style.texte(this@StyleAutoActivity, titre, 14.5f, Style.TEXTE, gras = true))
                addView(Style.texte(this@StyleAutoActivity, detail, 12f, Style.MUET).apply { setPadding(0, px(3f), 0, 0); setLineSpacing(0f, 1.15f) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(inter, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(44f)).apply { leftMargin = px(8f) })
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = px(8f) }
            isClickable = true
            setOnClickListener { inter.toggle() }
        }
    }

    // APPLIQUER : le moteur fait le travail (après une sauvegarde) ; si notre fond animé n'est pas encore le
    // fond du téléphone, on ouvre la page d'Android pour le confirmer (comme l'écran des fonds d'écran).
    private fun appliquer() {
        val a = analyse ?: return
        val r = resultat() ?: return
        val confirmer = StyleAuto.appliquer(this, r, a, StyleAuto.Choix(avecCouleurs && a.dossiers.isNotEmpty(), avecFond, avecSon, avecAnimations))
        aller(FINI)
        if (confirmer) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, CarnetFond.service(this, CarnetFond.APP)))
                Toast.makeText(this, getString(R.string.fd_choisir_ecran), Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                try { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
                catch (_: Exception) { Toast.makeText(this, getString(R.string.fd_pas_possible), Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun annuler() {
        StyleAuto.annuler(this)
        Toast.makeText(this, getString(R.string.sa_annule), Toast.LENGTH_SHORT).show()
        if (etat == FINI) finish() else construire()
    }

    // ================= FINI =================

    private fun ecranFini(): View {
        val a = analyse ?: return ecranAnalyse()
        val r = resultat() ?: return ecranAnalyse()
        val col = colonne()
        col.addView(Style.texte(this, getString(R.string.sa_fini_titre), 24f, Style.TEXTE, gras = true).apply { setPadding(px(4f), px(6f), 0, px(4f)) })
        col.addView(Style.texte(this, getString(R.string.sa_fini_texte, nomStyle(r.style)), 14f, Style.DOUX).apply { setPadding(px(4f), 0, 0, px(18f)) })
        if (avecWidgets && a.dossiers.isNotEmpty()) {
            col.addView(Style.titreCarte(this, getString(R.string.sa_poser_titre)).apply { setPadding(px(4f), 0, 0, px(10f)) })
            val poses = try { WidgetGroupe.groupesPoses(this) } catch (_: Exception) { emptySet() }
            for (id in a.dossiers) col.addView(lignePoser(id, id in poses, r.couleurs[id]))
        }
        col.addView(Style.boutonDiscret(this, getString(R.string.sa_annuler)) { annuler() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(10f) })
        return avecBouton(defilement(col), Style.boutonBlanc(this, getString(R.string.sa_terminer)) { finish() })
    }

    // Un dossier à poser : son petit galet aux couleurs du style, son nom, et « Poser » (ou « Posé » une fois fait).
    private fun lignePoser(id: String, pose: Boolean, hsv: FloatArray?): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = Style.arrondi(this@StyleAutoActivity, Style.CARTE, 18f, Style.BORD)
        setPadding(px(12f), px(8f), px(10f), px(8f))
        val teinte = if (hsv != null && avecCouleurs) Teinte.depuis(hsv) else Teinte.de(this@StyleAutoActivity, id)
        addView(MiniGalet(this@StyleAutoActivity, teinte), LinearLayout.LayoutParams(px(40f), px(40f)))
        addView(Style.texte(this@StyleAutoActivity, nomDossier(id), 14.5f, Style.TEXTE, gras = true).apply { setPadding(px(12f), 0, 0, 0) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (pose) {
            addView(Style.texte(this@StyleAutoActivity, "✓ " + getString(R.string.sa_pose), 13.5f, Style.MUET).apply { setPadding(0, 0, px(8f), 0) })
        } else {
            addView(Style.boutonDiscret(this@StyleAutoActivity, getString(R.string.sa_poser)) {
                if (!WidgetGroupe.proposer(this@StyleAutoActivity, id)) {
                    Toast.makeText(this@StyleAutoActivity, getString(R.string.aide_widget, getString(R.string.app_name)), Toast.LENGTH_LONG).show()
                }
            })
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = px(8f) }
    }
}

// ======================= LES PETITS DESSINS DE CES ÉCRANS =======================

// 4 petits ronds qui se chevauchent : les couleurs d'un style (comme dans la maquette).
class PointsStyle(context: Context, private val couleurs: List<Int>) : View(context) {
    private val dp = context.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val r = 7f * dp
        val y = height / 2f
        for ((i, couleur) in couleurs.withIndex()) {
            val x = r + 2f * dp + i * 8f * dp
            p.color = Style.CARTE                 // un liseré de la couleur de la carte entre deux ronds
            c.drawCircle(x, y, r + 2f * dp, p)
            p.color = couleur
            c.drawCircle(x, y, r, p)
        }
    }
}

// Un petit galet de dossier : le dégradé sombre de la fenêtre, et au centre le rond du logo qui brille doucement.
class MiniGalet(context: Context, private val teinte: Teinte) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val zone = RectF()
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        dessinerGalet(c, p, zone, 0f, 0f, min(w, h), teinte)
    }
    companion object {
        // Dessine un galet (carré arrondi) de côté « cote » à partir de (x, y). Utilisé aussi par l'aperçu.
        fun dessinerGalet(c: Canvas, p: Paint, zone: RectF, x: Float, y: Float, cote: Float, t: Teinte) {
            zone.set(x, y, x + cote, y + cote)
            p.shader = LinearGradient(x, y, x + cote * 0.4f, y + cote, t.haut, t.bas, Shader.TileMode.CLAMP)
            c.drawRoundRect(zone, cote * 0.3f, cote * 0.3f, p)
            val cx = x + cote / 2f; val cy = y + cote / 2f
            // la lueur, puis le rond du logo en dégradé (ses 3 couleurs)
            p.shader = RadialGradient(cx, cy, cote * 0.42f, intArrayOf(Teinte.avecAlpha(t.lueur, 110f), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, cote * 0.42f, p)
            val r = cote * 0.2f
            p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, t.logo, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
            p.shader = null
        }
    }
}

// L'ANNEAU DU STYLE AUTO : un anneau aux couleurs des dossiers (ou d'une palette douce par défaut) qui tourne
// lentement (un tour en 24 s), avec l'étoile à 4 branches au centre. Sert pour le bouton de l'accueil et
// pendant l'analyse. Jamais de flash : les couleurs sont celles des dossiers, sur fond sombre.
class AnneauStyle(context: Context, couleurs: List<Int>?) : View(context) {
    private val dp = context.resources.displayMetrics.density
    private val debut = SystemClock.uptimeMillis()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val etoile = android.graphics.Path()
    // par défaut : 5 teintes réparties, aussi claires les unes que les autres (OKLCH)
    private val teintes: IntArray = (couleurs?.takeIf { it.size >= 2 } ?: List(5) { Teinte.oklch(0.74f, 0.13f, 30f + 72f * it) }).let {
        (it + it[0]).toIntArray()          // on referme la boucle : la dernière couleur rejoint la première
    }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val r = min(w, h) / 2f - 1f * dp
        val t = (SystemClock.uptimeMillis() - debut) / 1000f
        c.save()
        c.rotate(t * 15f, cx, cy)                 // 15° par seconde = un tour en 24 s
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f * dp
        p.shader = SweepGradient(cx, cy, teintes, null)
        c.drawCircle(cx, cy, r - 1.5f * dp, p)
        c.restore()
        p.shader = null
        p.style = Paint.Style.FILL
        // l'étoile à 4 branches : 4 pointes reliées par des courbes qui passent près du centre
        val e = r * 0.42f
        etoile.reset()
        etoile.moveTo(cx, cy - e)
        etoile.quadTo(cx, cy, cx + e, cy)
        etoile.quadTo(cx, cy, cx, cy + e)
        etoile.quadTo(cx, cy, cx - e, cy)
        etoile.quadTo(cx, cy, cx, cy - e)
        p.color = Style.TEXTE
        c.drawPath(etoile, p)
        postInvalidateOnAnimation()
    }
}

// L'APERÇU DU STYLE : un mini téléphone. Dedans, le VRAI dessin du fond d'écran (Nuit ou Galets) avec la
// palette du style, l'heure, et jusqu'à 6 dossiers (galet + nom) aux couleurs du style. Autour, un contour
// aux 3 couleurs de la réaction sonore (c'est là qu'elle s'allume sur le vrai téléphone).
class ApercuStyle(context: Context) : View(context) {
    private val dp = context.resources.displayMetrics.density
    private val debut = SystemClock.uptimeMillis()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val texte = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Style.TEXTE; textAlign = Paint.Align.CENTER }
    private val zone = RectF()
    private var fond: DessinFond = FondNuit()
    private var teintes: List<Teinte> = emptyList()
    private var noms: List<String> = emptyList()
    private var contour = intArrayOf(Color.GRAY, Color.GRAY)

    fun montrer(r: StyleAuto.Resultat, nomsDossiers: List<String>) {
        fond = CarnetFond.dessin(r.fond, r.palette)
        teintes = r.couleurs.values.take(6).map { Teinte.depuis(it) }
        noms = nomsDossiers.take(6)
        val son = r.son.map { ReactionSonore.palette(it, 1)[1] }
        contour = intArrayOf(son[0], son[1], son[2], son[1], son[0])
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        val ph = h - 8f * dp                          // le téléphone, un peu moins haut que la vue (place du contour)
        val pw = ph * 0.68f
        val x = (width - pw) / 2f; val y = 4f * dp
        // le contour de la réaction sonore
        zone.set(x - 4f * dp, y - 4f * dp, x + pw + 4f * dp, y + ph + 4f * dp)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f * dp
        p.shader = SweepGradient(zone.centerX(), zone.centerY(), contour, null)
        c.drawRoundRect(zone, 34f * dp, 34f * dp, p)
        p.shader = null
        p.style = Paint.Style.FILL
        // l'écran : le vrai fond d'écran, coupé selon l'arrondi
        zone.set(x, y, x + pw, y + ph)
        val chemin = android.graphics.Path().apply { addRoundRect(zone, 30f * dp, 30f * dp, android.graphics.Path.Direction.CW) }
        c.save()
        c.clipPath(chemin)
        c.translate(x, y)
        val t = (SystemClock.uptimeMillis() - debut) / 1000f
        fond.dessiner(c, pw, ph, t, dp * 0.62f)       // un peu réduit : c'est un mini téléphone
        // l'heure et la date
        texte.typeface = Typeface.DEFAULT
        texte.textSize = 34f * dp
        c.drawText(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()), pw / 2f, 64f * dp, texte)
        texte.textSize = 10.5f * dp
        texte.color = Style.DOUX
        c.drawText(DateFormat.getDateInstance(DateFormat.FULL).format(Date()), pw / 2f, 82f * dp, texte)
        // les dossiers : 3 colonnes, en bas de l'écran
        val cote = min(48f * dp, pw / 3f - 22f * dp)
        val case = (pw - 24f * dp) / 3f
        val lignes = (teintes.size + 2) / 3
        texte.textSize = 9.5f * dp
        texte.color = Style.TEXTE
        for ((i, t2) in teintes.withIndex()) {
            val col = i % 3; val ligne = i / 3
            val gx = 12f * dp + case * col + (case - cote) / 2f
            val gy = ph - 22f * dp - (lignes - ligne) * (cote + 24f * dp)
            MiniGalet.dessinerGalet(c, p, zone, gx, gy, cote, t2)
            c.drawText(noms.getOrElse(i) { "" }, gx + cote / 2f, gy + cote + 13f * dp, texte)
        }
        c.restore()
        postInvalidateOnAnimation()
    }
}
