package com.will.animlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// LA GOMME DU FOND 3D (13/11, demande de Will) : entre le détourage automatique et l'enregistrement.
// Le détourage n'est jamais parfait (sur le manga de Will, un morceau de « WA » collé aux cheveux restait).
// Ici, on voit le sujet détouré, et on corrige avec le doigt :
//  - « Effacer » : ce qu'on touche disparaît du sujet (il restera dans le fond) ;
//  - « Restaurer » : ce qu'on touche revient dans le sujet ;
//  - quatre tailles de pinceau, « Annuler » (le dernier trait), deux doigts pour zoomer (jusqu'à x 40) et se déplacer ;
//  - le flou derrière le sujet : Doux / Net / Aucun ;
//  - « Essayer l'autre outil » : refait le détourage avec l'autre outil (photo <-> dessins) ;
//  - « Terminé » : fabrique le fond 3D (FabriqueFond3D.terminer) et ouvre son aperçu.
// Ce qui est effacé reste visible en transparence (très atténué), pour savoir où on a gommé.
class GommeActivity : Activity() {

    companion object {
        // Le détourage en cours, posé là par l'écran Fonds d'écran juste avant d'ouvrir celui-ci.
        // (Une photo de 1600 points est trop grosse pour voyager dans un Intent : on la garde en mémoire ici.)
        var enCours: FabriqueFond3D.Detourage? = null
        var photo: Uri? = null
        // (13/11) quand on RETOUCHE un fond 3D déjà créé : son dossier (sinon null = nouveau fond)
        var dossier: java.io.File? = null
    }

    private var remplissage = FabriqueFond3D.DOUX

    private lateinit var vue: VueGomme
    private val px = { v: Float -> Style.px(this, v) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = enCours ?: run { finish(); return }
        Style.fenetreNoire(this)
        vue = VueGomme(this, d)

        // la barre du haut : le mode d'emploi
        val aide = Style.texte(this, getString(R.string.fd_3d_gomme_aide), 13f, Style.MUET).apply {
            gravity = Gravity.CENTER
            setPadding(px(16f), px(10f), px(16f), px(10f))
        }
        // la barre du bas : effacer / restaurer, les tailles, annuler ; puis l'autre outil et « Terminé »
        val effacer = pilule(getString(R.string.fd_3d_effacer))
        val restaurer = pilule(getString(R.string.fd_3d_restaurer))
        fun modes() { allumer(effacer, vue.effacer); allumer(restaurer, !vue.effacer) }
        effacer.setOnClickListener { vue.effacer = true; modes() }
        restaurer.setOnClickListener { vue.effacer = false; modes() }
        modes()
        val tailles = listOf(5f to "·", 12f to "•", 24f to "●", 44f to "⬤").map { (dp, signe) ->
            pilule(signe).apply { tag = dp }
        }
        fun peindreTailles() { for (t in tailles) allumer(t, vue.rayonDp == t.tag as Float) }
        for (t in tailles) t.setOnClickListener { vue.rayonDp = t.tag as Float; peindreTailles() }
        peindreTailles()
        val annuler = pilule("↶").apply { setOnClickListener { vue.annuler() } }

        // (3 rangées, pour tenir sur un écran étroit comme celui du Flip6)
        val ligne1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(effacer); addView(restaurer)
            addView(View(this@GommeActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(annuler)
        }
        val lignePinceau = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(10f), 0, 0)
            addView(Style.texte(this@GommeActivity, getString(R.string.fd_3d_pinceau), 13f, Style.MUET).apply { setPadding(0, 0, px(10f), 0) })
            for (t in tailles) addView(t)
        }
        // (13/11) le flou derrière le sujet : Doux / Net / Aucun (voir FabriqueFond3D.reboucher)
        dossier?.let { remplissage = FabriqueFond3D.remplissage(it) }
        val choixFond = listOf(FabriqueFond3D.DOUX to R.string.fd_3d_flou_doux, FabriqueFond3D.NET to R.string.fd_3d_flou_net,
            FabriqueFond3D.AUCUN to R.string.fd_3d_flou_aucun).map { (m, t) -> pilule(getString(t)).apply { tag = m } }
        fun peindreFond() { for (p in choixFond) allumer(p, remplissage == p.tag as Int) }
        for (p in choixFond) p.setOnClickListener { remplissage = p.tag as Int; peindreFond() }
        peindreFond()
        val ligneFond = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(10f), 0, 0)
            addView(Style.texte(this@GommeActivity, getString(R.string.fd_3d_flou), 13f, Style.MUET).apply { setPadding(0, 0, px(10f), 0) })
            for (p in choixFond) addView(p)
        }
        val ligne2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(10f), 0, 0)
            addView(Style.boutonDiscret(this@GommeActivity, getString(R.string.fd_3d_autre_outil)) { autreOutil() },
                LinearLayout.LayoutParams(0, px(48f), 1f))
            addView(Style.boutonBlanc(this@GommeActivity, getString(R.string.fd_3d_termine)) { terminer() },
                LinearLayout.LayoutParams(0, px(48f), 1f).apply { leftMargin = px(8f) })
        }
        val bas = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(14f), px(10f), px(14f), px(18f))
            setBackgroundColor(Color.rgb(12, 12, 14))
            addView(ligne1)
            addView(lignePinceau)
            addView(ligneFond)
            addView(ligne2)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Style.NOIR)
            addView(aide)
            addView(vue, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bas)
        })
    }

    // une petite pilule (bouton arrondi) ; allumée = fond clair
    private fun pilule(t: String): TextView = Style.texte(this, t, 14f, Style.TEXTE, gras = true).apply {
        gravity = Gravity.CENTER
        minWidth = px(44f)
        setPadding(px(12f), px(8f), px(12f), px(8f))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(40f)).apply { rightMargin = px(6f) }
    }

    private fun allumer(v: TextView, oui: Boolean) {
        v.background = if (oui) Style.arrondi(this, Color.argb(230, 237, 237, 237), 20f)
        else Style.arrondi(this, Color.argb(20, 255, 255, 255), 20f, Color.argb(40, 255, 255, 255))
        v.setTextColor(if (oui) Color.rgb(16, 16, 18) else Style.TEXTE)
    }

    // « Terminé » : on fabrique le fond 3D (rebouchage + enregistrement), puis on ouvre son aperçu.
    private fun terminer() {
        val d = enCours ?: return
        val attente = AlertDialog.Builder(this).setMessage(getString(R.string.fd_3d_finition)).setCancelable(false).show()
        Thread {
            val dossier = try { FabriqueFond3D.terminer(this, d, remplissage, GommeActivity.dossier) } catch (_: Throwable) { null }
            runOnUiThread {
                attente.dismiss()
                if (dossier == null) { Toast.makeText(this, getString(R.string.fd_import_rate), Toast.LENGTH_LONG).show(); return@runOnUiThread }
                enCours = null
                GommeActivity.dossier = null
                startActivity(Intent(this, FondApercuActivity::class.java)
                    .putExtra(FondApercuActivity.SORTE, CarnetFond.PROFONDEUR)
                    .putExtra(FondApercuActivity.ID, dossier.path))
                finish()
            }
        }.start()
    }

    // « Essayer l'autre outil » : on refait le détourage avec l'outil qu'on n'a pas utilisé.
    private fun autreOutil() {
        val d = enCours ?: return
        val autre = if (d.outil == FabriqueFond3D.Outil.PHOTO) FabriqueFond3D.Outil.DESSIN else FabriqueFond3D.Outil.PHOTO
        val attente = AlertDialog.Builder(this).setMessage(getString(R.string.fd_3d_en_cours)).setCancelable(false).show()
        Thread {
            var nouveau: FabriqueFond3D.Detourage? = null
            var raison: FabriqueFond3D.Raison? = null
            try { nouveau = FabriqueFond3D.detourer(this, d.photo, autre) } catch (e: FabriqueFond3D.Echec) { raison = e.raison } catch (_: Throwable) {}
            runOnUiThread {
                attente.dismiss()
                val n = nouveau
                when {
                    n != null -> { enCours = n; vue.changer(n) }
                    raison == FabriqueFond3D.Raison.OUTIL_DESSINS_ABSENT -> OutilDessinsUi.proposer(this) { autreOutil() }
                    else -> Toast.makeText(this, getString(OutilDessinsUi.message(raison)), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}

// ---------------------------------------------------------------------------------------------
// LA VUE DE LA GOMME : la photo détourée, qu'on corrige au doigt.
// ---------------------------------------------------------------------------------------------
class VueGomme(c: Context, private var d: FabriqueFond3D.Detourage) : View(c) {
    var effacer = true                 // le mode : effacer (vrai) ou restaurer (faux)
    var rayonDp = 22f                  // la taille du pinceau, à l'écran
    private val dp = c.resources.displayMetrics.density

    private var pixels = IntArray(0)   // les couleurs de la photo
    private lateinit var sujet: Bitmap // la photo, transparente là où ce n'est pas le sujet (ce qu'on voit)
    private val historique = ArrayList<FloatArray>()      // les masques d'avant chaque trait (pour « Annuler »)

    // le cadrage : de l'image vers l'écran (zoom et déplacement à deux doigts)
    private val versEcran = Matrix()
    private val versImage = Matrix()
    private var cadre = false

    private val pPhoto = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 55 }        // ce qui est effacé : très atténué
    private val pSujet = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pCurseur = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f * c.resources.displayMetrics.density; color = Color.argb(200, 255, 255, 255)
    }
    private var curseurX = -1f
    private var curseurY = -1f

    init { preparer() }

    // Un nouveau détourage (« Essayer l'autre outil ») : on repart de lui.
    fun changer(n: FabriqueFond3D.Detourage) {
        d = n
        historique.clear()
        preparer()
        invalidate()
    }

    private fun preparer() {
        pixels = IntArray(d.w * d.h)
        d.photo.getPixels(pixels, 0, d.w, 0, 0, d.w, d.h)
        sujet = Bitmap.createBitmap(d.w, d.h, Bitmap.Config.ARGB_8888)
        majSujet(0, 0, d.w - 1, d.h - 1)
        cadre = false
    }

    // Recalcule la transparence du sujet dans un rectangle de l'image (là où le pinceau est passé).
    private fun majSujet(x0: Int, y0: Int, x1: Int, y1: Int) {
        val w = d.w
        val lw = x1 - x0 + 1
        val ligne = IntArray(lw)
        for (y in y0..y1) {
            for (x in x0..x1) {
                val i = y * w + x
                ligne[x - x0] = (pixels[i] and 0x00FFFFFF) or ((d.masque[i] * 255f).toInt().coerceIn(0, 255) shl 24)
            }
            sujet.setPixels(ligne, 0, lw, x0, y, lw, 1)
        }
    }

    fun annuler() {
        if (historique.isEmpty()) return
        val avant = historique.removeAt(historique.size - 1)
        System.arraycopy(avant, 0, d.masque, 0, avant.size)
        majSujet(0, 0, d.w - 1, d.h - 1)
        invalidate()
    }

    // Le cadrage de départ : l'image entière, centrée dans la vue.
    private fun cadrer() {
        val k = min(width.toFloat() / d.w, height.toFloat() / d.h)
        versEcran.reset()
        versEcran.postScale(k, k)
        versEcran.postTranslate((width - d.w * k) / 2f, (height - d.h * k) / 2f)
        versEcran.invert(versImage)
        cadre = true
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { cadre = false }

    override fun onDraw(c: Canvas) {
        if (!cadre) cadrer()
        c.drawColor(Color.rgb(20, 20, 24))
        c.save()
        c.concat(versEcran)
        c.drawBitmap(d.photo, 0f, 0f, pPhoto)       // la photo entière, très atténuée (ce qui est effacé)
        c.drawBitmap(sujet, 0f, 0f, pSujet)         // le sujet, net
        c.restore()
        if (curseurX >= 0) c.drawCircle(curseurX, curseurY, rayonDp * dp, pCurseur)
    }

    // ----- LES DOIGTS : un doigt = le pinceau ; deux doigts = zoomer et se déplacer -----
    private val zoom = ScaleGestureDetector(c, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(g: ScaleGestureDetector): Boolean {
            val valeurs = FloatArray(9); versEcran.getValues(valeurs)
            val actuel = valeurs[Matrix.MSCALE_X]
            val mini = min(width.toFloat() / d.w, height.toFloat() / d.h) * 0.8f
            // jusqu'à 40 fois plus grand qu'au départ (demande de Will : zoomer très fort pour les petits détails)
            val f = (actuel * g.scaleFactor).coerceIn(mini, mini * 50f) / actuel
            versEcran.postScale(f, f, g.focusX, g.focusY)
            versEcran.invert(versImage)
            invalidate()
            return true
        }
    })
    private var deuxDoigts = false
    private var trait = false
    private var dernierX = 0f
    private var dernierY = 0f
    private var milieuX = 0f
    private var milieuY = 0f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        zoom.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                deuxDoigts = false; trait = false
                dernierX = e.x; dernierY = e.y
                curseurX = e.x; curseurY = e.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // un 2e doigt : ce n'était pas un trait de pinceau, on l'annule s'il avait commencé
                if (trait) annuler()
                deuxDoigts = true; trait = false; curseurX = -1f
                milieuX = (e.getX(0) + e.getX(1)) / 2f; milieuY = (e.getY(0) + e.getY(1)) / 2f
            }
            MotionEvent.ACTION_MOVE -> {
                if (deuxDoigts && e.pointerCount >= 2) {
                    val mx = (e.getX(0) + e.getX(1)) / 2f; val my = (e.getY(0) + e.getY(1)) / 2f
                    versEcran.postTranslate(mx - milieuX, my - milieuY)
                    versEcran.invert(versImage)
                    milieuX = mx; milieuY = my
                } else if (!deuxDoigts) {
                    if (!trait) {
                        // le début d'un trait : on garde le masque d'avant (au plus 4, la mémoire est comptée)
                        historique.add(d.masque.copyOf())
                        if (historique.size > 4) historique.removeAt(0)
                        trait = true
                        peindreSegment(dernierX, dernierY, dernierX, dernierY)
                    }
                    peindreSegment(dernierX, dernierY, e.x, e.y)
                    dernierX = e.x; dernierY = e.y
                    curseurX = e.x; curseurY = e.y
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // un simple appui (sans glisser) peint aussi un point
                if (!deuxDoigts && !trait && e.actionMasked == MotionEvent.ACTION_UP) {
                    historique.add(d.masque.copyOf())
                    if (historique.size > 4) historique.removeAt(0)
                    peindreSegment(e.x, e.y, e.x, e.y)
                }
                trait = false; curseurX = -1f
                invalidate()
            }
        }
        return true
    }

    // Peint le long d'un segment (en coordonnées d'écran) : des ronds rapprochés, au bord doux.
    private fun peindreSegment(xa: Float, ya: Float, xb: Float, yb: Float) {
        val pts = floatArrayOf(xa, ya, xb, yb)
        versImage.mapPoints(pts)
        val v = FloatArray(9); versEcran.getValues(v)
        val r = rayonDp * dp / v[Matrix.MSCALE_X]                 // le rayon en points de l'image
        val longueur = hypot(pts[2] - pts[0], pts[3] - pts[1])
        val pas = max(1, (longueur / max(1f, r / 3f)).toInt())
        var gx0 = Int.MAX_VALUE; var gy0 = Int.MAX_VALUE; var gx1 = -1; var gy1 = -1
        for (k in 0..pas) {
            val t = k.toFloat() / pas
            val cx = pts[0] + (pts[2] - pts[0]) * t
            val cy = pts[1] + (pts[3] - pts[1]) * t
            val x0 = max(0, (cx - r).toInt()); val x1 = min(d.w - 1, (cx + r).toInt())
            val y0 = max(0, (cy - r).toInt()); val y1 = min(d.h - 1, (cy + r).toInt())
            if (x0 > x1 || y0 > y1) continue
            for (y in y0..y1) for (x in x0..x1) {
                val dist = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))
                if (dist > r) continue
                // force du pinceau : pleine au centre, adoucie sur le dernier tiers du rayon
                val f = ((r - dist) / (r * 0.3f)).coerceIn(0f, 1f)
                val i = y * d.w + x
                d.masque[i] = if (effacer) min(d.masque[i], 1f - f) else max(d.masque[i], f)
            }
            gx0 = min(gx0, x0); gy0 = min(gy0, y0); gx1 = max(gx1, x1); gy1 = max(gy1, y1)
        }
        if (gx1 >= 0) majSujet(gx0, gy0, gx1, gy1)
    }
}

// ---------------------------------------------------------------------------------------------
// LA FENÊTRE « OUTIL DE DÉTOURAGE DES DESSINS » (demande de Will) : avant le 1er téléchargement, on explique
// que ça prend 44 Mo, une seule fois, et qu'on peut le supprimer en un bouton. Puis le téléchargement, avec sa barre.
// ---------------------------------------------------------------------------------------------
object OutilDessinsUi {

    fun proposer(a: Activity, apres: () -> Unit) {
        AlertDialog.Builder(a)
            .setTitle(a.getString(R.string.fd_3d_outil_titre))
            .setMessage(a.getString(R.string.fd_3d_outil_texte, DetourageDessins.TAILLE_MO))
            .setPositiveButton(a.getString(R.string.fd_3d_telecharger, DetourageDessins.TAILLE_MO)) { _, _ -> telecharger(a, apres) }
            .setNegativeButton(a.getString(R.string.fd_annuler), null)
            .show()
    }

    private fun telecharger(a: Activity, apres: () -> Unit) {
        val barre = android.widget.ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val boite = FrameLayout(a).apply { setPadding(Style.px(a, 24f), Style.px(a, 8f), Style.px(a, 24f), 0); addView(barre) }
        val fenetre = AlertDialog.Builder(a)
            .setTitle(a.getString(R.string.fd_3d_telechargement))
            .setView(boite)
            .setCancelable(false)
            .show()
        Thread {
            val ok = DetourageDessins.telecharger(a) { p -> a.runOnUiThread { barre.progress = p } }
            a.runOnUiThread {
                fenetre.dismiss()
                if (ok) apres()
                else Toast.makeText(a, a.getString(R.string.fd_3d_telechargement_rate), Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    // Le message à afficher pour un échec du détourage.
    fun message(r: FabriqueFond3D.Raison?): Int = when (r) {
        FabriqueFond3D.Raison.PAS_DE_SUJET -> R.string.fd_3d_pas_de_sujet
        FabriqueFond3D.Raison.SUJET_TROP_GRAND -> R.string.fd_3d_trop_grand
        FabriqueFond3D.Raison.MODULE_ABSENT -> R.string.fd_3d_module
        else -> R.string.fd_import_rate
    }
}
