package com.will.animlauncher

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// INSTAGRAM, animation « Le mur dans la nuit » (version cosy).
// Une clairière la nuit : ciel étoilé, fine lune, quatre rangées de sapins dans la brume. Au milieu,
// un vieux mur de pierre couvert de 20 polaroïds, tous différents (plage, aurore, ville, chat…).
// Une guirlande d'ampoules chaudes festonne le haut du mur, des bougies et une lanterne sont posées
// au pied. Les photos S'ALLUMENT une à une, en partant du centre : chacune éclaire le mur, la brume
// et l'herbe de sa propre couleur. Quatre restent éteintes.
// Effets « cinéma » : la caméra avance et chaque plan bouge selon sa profondeur (parallaxe), le
// lointain et le premier plan sont flous, de grosses lumières floues en haut (bokeh), un grain de film.
// Révélation : on « entre » dans la photo centrale, qui grandit jusqu'à remplir l'écran.
//
// Ce qui ne bouge pas (les 20 photos, les rangées de sapins floues, les deux grands sapins du premier
// plan, le grain) est dessiné UNE fois, en arrière-plan, au démarrage du service : voir FondsMur.

// =====================================================================================
// La disposition (en fractions de l'écran) et les données tirées au hasard, une fois pour toutes.
// =====================================================================================
internal object Mur {
    const val X0 = 0.06f; const val X1 = 0.94f; const val Y0 = 0.42f; const val Y1 = 0.78f
    const val NC = 5; const val NL = 4

    class Pola(val i: Int, val j: Int, var ang: Float, val dx: Float, val dy: Float, val genre: Int, val alea: Float, var eteint: Boolean)

    private val r = Random(21)
    val etoiles = List(120) { floatArrayOf(r.nextFloat(), r.nextFloat().pow(1.5f) * 0.42f, 0.2f + 0.5f * r.nextFloat(), r.nextFloat() * 6f) }
    // les sapins de la forêt : 4 rangées (loin -> près), chacune une liste de (x, hauteur)
    val foret = intArrayOf(26, 20, 14, 10).map { n -> (-1 until n + 2).map { x -> floatArrayOf((x + 0.6f * r.nextFloat() - 0.3f) / n, 0.6f + 0.45f * r.nextFloat()) } }
    val herbes = List(160) { floatArrayOf(r.nextFloat(), 0.5f + 0.5f * r.nextFloat(), 0.6f * r.nextFloat() - 0.3f, r.nextFloat() * 6f) }
    // les ampoules floues du premier plan (un fil invisible qui pend en haut de l'écran)
    val bokeh = List(9) { i -> val u = i / 8f + 0.03f; floatArrayOf(u, 0.075f + 0.05f * abs(sin(PI.toFloat() * u)).pow(1.2f) + 0.012f * r.nextFloat() - 0.006f, 14f + 8f * r.nextFloat()) }
    val lucioles = List(14) { floatArrayOf(r.nextFloat(), 0.38f + 0.57f * r.nextFloat(), r.nextFloat() * 6f, 0.5f + 0.7f * r.nextFloat()) }

    // les polaroïds : chaque scène n'apparaît qu'UNE fois (20 photos, 20 scènes mélangées au hasard)
    val polas: List<Pola>
    val centre: Pola
    init {
        val ordre = (0 until 20).toMutableList(); java.util.Collections.shuffle(ordre, r)
        val l = ArrayList<Pola>()
        for (j in 0 until NL) for (i in 0 until NC)
            l.add(Pola(i, j, 12f * r.nextFloat() - 6f, 10f * r.nextFloat() - 5f, 10f * r.nextFloat() - 5f, ordre[j * NC + i], r.nextFloat(), false))
        val m = l.toMutableList(); java.util.Collections.shuffle(m, r)
        for (p in m.take(4)) p.eteint = true                      // quatre photos restent éteintes
        polas = l
        // la photo centrale (celle par laquelle on entre dans l'app) s'allume forcément
        centre = l.minByOrNull { abs(it.i - 2f) + abs(it.j - 1.4f) }!!
        centre.eteint = false; centre.ang = -2f
    }

    // la couleur de la lumière de chaque photo (selon sa scène)
    val teintes = intArrayOf(rgb(255, 140, 100), rgb(90, 200, 230), rgb(255, 190, 110), rgb(255, 150, 90),
        rgb(255, 170, 110), rgb(110, 240, 180), rgb(250, 180, 150), rgb(170, 150, 255),
        rgb(255, 190, 120), rgb(255, 130, 80), rgb(140, 220, 120), rgb(255, 220, 150),
        rgb(250, 170, 200), rgb(255, 150, 70), rgb(80, 200, 220), rgb(190, 140, 230),
        rgb(250, 160, 140), rgb(255, 180, 200), rgb(255, 170, 100), rgb(200, 160, 255))

    // les 4 rangées de sapins : couleur, base, hauteur max, largeur (fractions de l'écran)
    val rangees = arrayOf(floatArrayOf(40f, 35f, 62f, 0.47f, 0.22f, 0.05f), floatArrayOf(28f, 24f, 44f, 0.51f, 0.29f, 0.06f),
        floatArrayOf(17f, 15f, 28f, 0.56f, 0.36f, 0.075f), floatArrayOf(9f, 8f, 15f, 0.61f, 0.42f, 0.09f))

    /** Le centre (x, y) et la taille d'un polaroïd sur le mur. */
    fun position(p: Pola, w: Float, h: Float): FloatArray {
        val L = (X1 - X0) * w; val hm = (Y1 - Y0) * h; val k = w / 540f
        return floatArrayOf(X0 * w + L * (p.i + 0.5f) / NC + p.dx * k, Y0 * h + hm * (p.j + 0.5f) / NL + p.dy * k, min(L / NC, hm / NL / 1.25f) * 0.84f)
    }

    /** Un sapin en silhouette, naturel : des étages de branches qui pendent, un bord dentelé, une pointe fine. */
    fun sapin(x: Float, base: Float, haut: Float, larg: Float, graine: Long): Path {
        val rnd = Random(graine)
        val p = Path()
        for (e in 0 until 7) {
            val f = e / 7f
            val yBas = base - haut * f * 0.88f; val yHaut = base - haut * (f + 0.28f) * 0.88f
            val l = larg * (1f - f).pow(0.9f) * (0.85f + 0.25f * rnd.nextFloat())
            p.moveTo(x, yHaut)
            p.quadTo(x - l * 0.45f, yBas - haut * 0.04f, x - l, yBas + haut * 0.015f)
            for (d in 0 until 3) { val u = 1f - (d + 1) / 3.5f
                p.lineTo(x - l * (u + 0.12f), yBas - haut * 0.02f); p.lineTo(x - l * u, yBas + haut * 0.01f * rnd.nextFloat()) }
            p.lineTo(x, yBas - haut * 0.03f)
            for (d in 0 until 3) { val u = (d + 0.5f) / 3.5f
                p.lineTo(x + l * u, yBas + haut * 0.01f * rnd.nextFloat()); p.lineTo(x + l * (u + 0.12f), yBas - haut * 0.02f) }
            p.lineTo(x + l, yBas + haut * 0.015f)
            p.quadTo(x + l * 0.45f, yBas - haut * 0.04f, x, yHaut)
            p.close()
        }
        p.moveTo(x - larg * 0.03f, base - haut * 0.85f); p.lineTo(x, base - haut * 1.02f); p.lineTo(x + larg * 0.03f, base - haut * 0.85f); p.close()
        p.addRect(x - larg * 0.07f, base - haut * 0.06f, x + larg * 0.07f, base + haut * 0.05f, Path.Direction.CW)
        p.fillType = Path.FillType.WINDING
        return p
    }
}

// =====================================================================================
// LES ÉLÉMENTS FIXES, dessinés une seule fois dans un fil à part.
// =====================================================================================
object FondsMur {
    @Volatile var photos: Array<Bitmap?> = arrayOfNulls(20); private set
    // une image par rangée de sapins, avec la zone de l'écran qu'elle couvre (fractions : gauche, haut, droite, bas)
    @Volatile var rangees: Array<Bitmap?> = arrayOfNulls(4); private set
    val zonesRangees = Array(4) { FloatArray(4) }
    @Volatile var grandsSapins: Array<Bitmap?> = arrayOfNulls(2); private set
    val zonesGrands = Array(2) { FloatArray(4) }
    @Volatile var grain: Bitmap? = null; private set
    @Volatile private var lance = false

    fun preparer() {
        synchronized(this) { if (lance) return; lance = true }
        thread(name = "fonds-mur", priority = Thread.MIN_PRIORITY) {
            try {
                val (w, h) = igEcranPortrait()
                val k = w / 540f
                // le grain de film : un petit carré de bruit, répété sur tout l'écran
                val g = Random(3); val px = IntArray(128 * 128) { val v = (128 + g.nextGaussian() * 22).toInt().coerceIn(0, 255); Color.rgb(v, v, v) }
                grain = Bitmap.createBitmap(px, 128, 128, Bitmap.Config.ARGB_8888)
                // les 20 photos
                val taille = max(64, (75f * k).toInt())
                for (i in 0 until 20) {
                    val b = Bitmap.createBitmap(taille, taille, Bitmap.Config.ARGB_8888)
                    PhotosMur.dessiner(Canvas(b), taille.toFloat(), i)
                    photos[i] = b
                }
                // les rangées de sapins, en demi-résolution (elles sont floues, ça ne se voit pas)
                for (rang in 0 until 4) {
                    val d = Mur.rangees[rang]
                    val base = d[3]; val hmax = d[4]; val lg = d[5]
                    val z = floatArrayOf(-0.2f, base - hmax * 1.1f - 0.01f, 1.2f, base + 0.03f)
                    val bw = ((z[2] - z[0]) * w / 2).toInt(); val bh = ((z[3] - z[1]) * h / 2).toInt()
                    val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                    val c = Canvas(b)
                    c.scale(0.5f, 0.5f); c.translate(-z[0] * w, -z[1] * h)            // on dessine en pixels d'écran
                    val flou = (3 - rang) * 0.9f * k
                    val p = igPinceau(rgb(d[0].toInt(), d[1].toInt(), d[2].toInt()))
                    if (flou > 0.1f) p.maskFilter = BlurMaskFilter(flou, BlurMaskFilter.Blur.NORMAL)
                    for ((n, a) in Mur.foret[rang].withIndex()) {
                        val path = Mur.sapin(a[0] * w, h * base, h * hmax * a[1], w * lg, (rang * 100 + n).toLong())
                        c.drawPath(path, p)
                        if (rang >= 2) {   // un liseré de lune sur le côté droit des sapins proches
                            c.save(); c.clipPath(path)
                            c.drawRect(a[0] * w + w * lg * 0.25f, h * base - h * hmax * a[1] * 1.05f, a[0] * w + w * lg * 1.1f, h * base, igPinceau(avecAlpha(rgb(120, 120, 170), 0.10f)))
                            c.restore()
                        }
                    }
                    for (q in 0 until 4) zonesRangees[rang][q] = z[q]
                    rangees[rang] = b
                }
                // les deux grands sapins du premier plan (flous)
                val grands = arrayOf(floatArrayOf(-0.09f, 0.82f, 0.70f, 0.26f, 901f), floatArrayOf(1.09f, 0.84f, 0.74f, 0.28f, 902f))
                for ((n, d) in grands.withIndex()) {
                    val z = floatArrayOf(d[0] - d[3] * 1.15f, d[1] - d[2] * 1.06f, d[0] + d[3] * 1.15f, d[1] + d[2] * 0.08f)
                    val bw = ((z[2] - z[0]) * w / 2).toInt(); val bh = ((z[3] - z[1]) * h / 2).toInt()
                    val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                    val c = Canvas(b)
                    c.scale(0.5f, 0.5f); c.translate(-z[0] * w, -z[1] * h)
                    val path = Mur.sapin(d[0] * w, d[1] * h, d[2] * h, d[3] * w, d[4].toLong())
                    c.drawPath(path, igPinceau(rgb(4, 4, 7)).apply { maskFilter = BlurMaskFilter(2.2f * k, BlurMaskFilter.Blur.NORMAL) })
                    c.save(); c.clipPath(path)
                    c.drawRect(d[0] * w + d[3] * w * 0.25f, d[1] * h - d[2] * h * 1.05f, d[0] * w + d[3] * w * 1.1f, d[1] * h, igPinceau(avecAlpha(rgb(140, 120, 160), 0.08f)))
                    c.restore()
                    for (q in 0 until 4) zonesGrands[n][q] = z[q]
                    grandsSapins[n] = b
                }
            } catch (_: Throwable) { /* mémoire insuffisante : la scène se passera des éléments manquants */ }
        }
    }
}

// =====================================================================================
// LES 20 PHOTOS : de petites illustrations (dessinées une fois, dans un carré de côté L).
// =====================================================================================
private object PhotosMur {
    private lateinit var c: Canvas
    private var L = 1f
    private fun X(u: Float) = u * L
    private fun deg(x0: Float, y0: Float, x1: Float, y1: Float, vararg cols: Int, pos: FloatArray? = null) =
        igDegrade(c, X(x0), X(y0), X(x1), X(y1), cols, pos)
    private fun rond(u: Float, v: Float, r: Float, col: Int) = c.drawCircle(X(u), X(v), r * L, igPinceau(col))
    private fun rect(u0: Float, v0: Float, u1: Float, v1: Float, col: Int) = c.drawRect(X(u0), X(v0), X(u1), X(v1), igPinceau(col))
    private fun ovale(u0: Float, v0: Float, u1: Float, v1: Float, col: Int) = c.drawOval(X(u0), X(v0), X(u1), X(v1), igPinceau(col))
    private fun poly(col: Int, vararg uv: Float) = igPoly(c, col, *FloatArray(uv.size) { uv[it] * L })
    private fun trait(u0: Float, v0: Float, u1: Float, v1: Float, col: Int, ep: Float) = c.drawLine(X(u0), X(v0), X(u1), X(v1), igPinceau(col, ep))
    private val un get() = max(1f, L / 120f)          // l'épaisseur « 1 pixel » du modèle

    fun dessiner(canvas: Canvas, cote: Float, genre: Int) {
        c = canvas; L = cote
        val rnd = Random(genre * 131L + 7)
        val sec = 0.5f
        when (genre) {
            0 -> {      // coucher de soleil sur un lac de montagne, avec son reflet
                deg(0f, 0f, 1f, 1f, rgb(60, 40, 110), rgb(230, 100, 90), rgb(255, 180, 100), pos = floatArrayOf(0f, 0.45f, 0.6f))
                rond(0.5f, 0.56f, 0.11f, rgb(255, 225, 160))
                poly(rgb(50, 30, 70), 0f, 0.6f, 0.2f, 0.32f, 0.38f, 0.5f, 0.6f, 0.28f, 0.85f, 0.48f, 1f, 0.38f, 1f, 0.6f)
                poly(rgb(230, 210, 230), 0.6f, 0.28f, 0.66f, 0.35f, 0.62f, 0.34f, 0.56f, 0.33f)
                deg(0f, 0.6f, 1f, 1f, rgb(150, 80, 110), rgb(40, 30, 70))
                for (i in 0 until 5) { val yy = 0.64f + i * 0.07f; val ww = 0.25f - i * 0.03f
                    trait(0.5f - ww / 2, yy, 0.5f + ww / 2, yy, avecAlpha(rgb(255, 210, 150), 0.6f), max(1f, L * 0.015f)) }
            }
            1 -> {      // plage : mer turquoise, sable, soleil, palmier
                deg(0f, 0f, 1f, 1f, rgb(70, 160, 220), rgb(170, 220, 240))
                rond(0.78f, 0.2f, 0.08f, rgb(255, 245, 200))
                deg(0f, 0.5f, 1f, 0.72f, rgb(20, 120, 170), rgb(60, 200, 200))
                val p = Path(); p.moveTo(0f, X(0.72f)); p.quadTo(X(0.3f), X(0.66f), X(0.6f), X(0.72f)); p.quadTo(X(0.8f), X(0.76f), L, X(0.7f))
                p.lineTo(L, L); p.lineTo(0f, L); p.close()
                c.drawPath(p, igPinceau(rgb(240, 215, 160))); c.drawPath(p, igPinceau(avecAlpha(Color.WHITE, 0.7f), max(1f, L * 0.02f)))
                val tr = Path(); tr.moveTo(X(0.18f), L); tr.quadTo(X(0.2f), X(0.6f), X(0.32f), X(0.32f))
                c.drawPath(tr, igPinceau(rgb(90, 60, 40), L * 0.035f))
                for (a in intArrayOf(-150, -110, -60, -20, 20)) {
                    val ar = a * PI.toFloat() / 180f; val cx = X(0.32f); val cy = X(0.32f)
                    val ex = cx + cos(ar) * L * 0.25f; val ey = cy + sin(ar) * L * 0.25f + L * 0.08f
                    val f = Path(); f.moveTo(cx, cy); f.quadTo((cx + ex) / 2, (cy + ey) / 2 - L * 0.08f, ex, ey)
                    c.drawPath(f, igPinceau(rgb(30, 110, 60), L * 0.04f))
                }
            }
            2 -> {      // ville la nuit : immeubles aux fenêtres allumées, lune
                deg(0f, 0f, 1f, 1f, rgb(10, 15, 50), rgb(60, 40, 100))
                rond(0.8f, 0.18f, 0.07f, rgb(240, 235, 210))
                var x = 0f
                while (x < 1f) {
                    val bw = 0.1f + 0.1f * rnd.nextFloat(); val bh = 0.3f + 0.45f * rnd.nextFloat()
                    c.drawRect(X(x), X(1 - bh), X(x + bw) - 1, L, igPinceau(rgb(20, 18, 35)))
                    for (fy in 0 until (bh * 14).toInt()) for (fx in 0 until 3) if (rnd.nextFloat() < 0.45f) {
                        val wx = X(x + bw * (0.15f + fx * 0.28f)); val wy = X(1 - bh + 0.04f + fy * 0.07f)
                        c.drawRect(wx, wy, wx + L * bw * 0.16f, wy + L * 0.035f, igPinceau(rgb(255, 200, 110)))
                    }
                    x += bw
                }
            }
            3 -> {      // deux amis de dos devant un coucher de soleil
                deg(0f, 0f, 1f, 1f, rgb(250, 140, 90), rgb(250, 200, 120), rgb(120, 70, 100), pos = floatArrayOf(0f, 0.55f, 1f))
                rond(0.5f, 0.62f, 0.2f, rgb(255, 230, 170))
                rect(0f, 0.72f, 1f, 1f, rgb(60, 30, 50))
                val sil = rgb(40, 20, 35)
                for ((u, t) in listOf(Pair(0.38f, 1f), Pair(0.6f, 0.92f))) {
                    rond(u, 0.47f + (1 - t) * 0.1f, 0.07f * t, sil)
                    c.drawRoundRect(X(u - 0.1f * t), X(0.55f + (1 - t) * 0.1f), X(u + 0.1f * t), X(1.05f), L * 0.06f, L * 0.06f, igPinceau(sil))
                }
                trait(0.46f, 0.62f, 0.53f, 0.63f, sil, L * 0.04f)
            }
            4 -> {      // une tasse de café fumante sur une table en bois
                deg(0f, 0f, 1f, 1f, rgb(120, 80, 50), rgb(70, 45, 30))
                for (i in 0 until 4) trait(0f, 0.25f * i + 0.1f, 1f, 0.25f * i + 0.13f, avecAlpha(rgb(40, 25, 15), 0.5f), un)
                ovale(0.2f, 0.62f, 0.8f, 0.8f, rgb(235, 230, 225))
                c.drawRoundRect(X(0.3f), X(0.4f), X(0.7f), X(0.72f), L * 0.06f, L * 0.06f, igPinceau(rgb(245, 240, 235)))
                ovale(0.3f, 0.36f, 0.7f, 0.46f, rgb(90, 50, 25)); ovale(0.4f, 0.38f, 0.6f, 0.43f, rgb(200, 150, 100))
                c.drawCircle(X(0.75f), X(0.55f), L * 0.07f, igPinceau(rgb(245, 240, 235), L * 0.035f))
                for (i in 0 until 2) {
                    val v = Path(); val xx = X(0.43f + i * 0.13f); val ph = sec * 2 + i
                    v.moveTo(xx, X(0.34f)); v.cubicTo(xx + L * 0.06f * sin(ph), X(0.24f), xx - L * 0.06f * sin(ph), X(0.16f), xx + L * 0.03f, X(0.06f))
                    c.drawPath(v, igPinceau(avecAlpha(Color.WHITE, 0.5f), L * 0.025f))
                }
            }
            5 -> {      // aurore boréale au-dessus de sapins enneigés
                deg(0f, 0f, 1f, 1f, rgb(10, 15, 40), rgb(20, 40, 70))
                val cols = intArrayOf(rgb(80, 240, 160), rgb(120, 200, 255), rgb(200, 120, 255))
                for (i in 0 until 3) {
                    val p = Path(); p.moveTo(X(-0.1f), X(0.45f - i * 0.08f))
                    p.cubicTo(X(0.3f), X(0.15f - i * 0.05f + 0.05f * sin(sec + i)), X(0.6f), X(0.55f - i * 0.1f), X(1.1f), X(0.2f - i * 0.05f))
                    c.drawPath(p, igPinceau(avecAlpha(cols[i], 0.55f), L * 0.08f).apply { strokeCap = Paint.Cap.BUTT })
                }
                for (i in 0 until 12) c.drawCircle(X(rnd.nextFloat()), X(rnd.nextFloat() * 0.5f), 0.8f * un, igPinceau(Color.WHITE))
                rect(0f, 0.85f, 1f, 1f, rgb(200, 215, 235))
                for (i in 0 until 6) { val u = i / 5f + 0.1f * rnd.nextFloat() - 0.05f; val hh = 0.25f + 0.15f * rnd.nextFloat()
                    poly(rgb(15, 30, 35), u - 0.08f, 0.88f, u, 0.88f - hh, u + 0.08f, 0.88f) }
            }
            6 -> {      // montgolfières au-dessus d'une vallée au lever du jour
                deg(0f, 0f, 1f, 1f, rgb(130, 170, 230), rgb(250, 200, 170))
                poly(rgb(90, 120, 110), 0f, 0.8f, 0.3f, 0.62f, 0.6f, 0.75f, 1f, 0.6f, 1f, 1f, 0f, 1f)
                poly(rgb(60, 90, 80), 0f, 0.9f, 0.5f, 0.78f, 1f, 0.88f, 1f, 1f, 0f, 1f)
                val b = arrayOf(floatArrayOf(0.3f, 0.3f, 0.13f), floatArrayOf(0.68f, 0.22f, 0.09f), floatArrayOf(0.85f, 0.5f, 0.06f))
                val bc = intArrayOf(rgb(230, 70, 60), rgb(250, 190, 60), rgb(80, 120, 220))
                for (n in 0 until 3) { val u = b[n][0]; val v = b[n][1]; val s = b[n][2]
                    c.drawOval(X(u - s), X(v) - L * s * 1.2f, X(u + s), X(v) + L * s * 0.9f, igPinceau(bc[n]))
                    c.drawRect(X(u - s * 0.25f), X(v) + L * s * 1.2f, X(u + s * 0.25f), X(v) + L * s * 1.5f, igPinceau(rgb(110, 70, 40)))
                    c.drawLine(X(u), X(v) - L * s * 1.2f, X(u), X(v) + L * s * 0.9f, igPinceau(avecAlpha(Color.WHITE, 0.4f), un)) }
            }
            7 -> {      // un chat sur le rebord d'une fenêtre, devant la lune
                deg(0f, 0f, 1f, 1f, rgb(30, 40, 90), rgb(90, 80, 150))
                rond(0.6f, 0.35f, 0.22f, rgb(250, 235, 190))
                rect(0f, 0.8f, 1f, 1f, rgb(25, 20, 40))
                val noir = rgb(20, 15, 30)
                ovale(0.38f, 0.55f, 0.72f, 0.82f, noir); rond(0.47f, 0.5f, 0.1f, noir)
                poly(noir, 0.39f, 0.46f, 0.4f, 0.34f, 0.46f, 0.42f); poly(noir, 0.49f, 0.42f, 0.55f, 0.34f, 0.56f, 0.46f)
                val q = Path(); q.moveTo(X(0.7f), X(0.78f)); q.cubicTo(X(0.85f), X(0.8f), X(0.9f), X(0.65f), X(0.82f), X(0.58f))
                c.drawPath(q, igPinceau(noir, L * 0.045f))
                rect(0f, 0f, 0.06f, 1f, rgb(40, 30, 50))
            }
            8 -> {      // un chalet dans la neige, fenêtre allumée et fumée de cheminée
                deg(0f, 0f, 1f, 1f, rgb(20, 25, 60), rgb(60, 70, 120))
                for (i in 0 until 15) c.drawCircle(X(rnd.nextFloat()), X(rnd.nextFloat() * 0.9f), un, igPinceau(avecAlpha(Color.WHITE, 0.8f)))
                val p = Path(); p.moveTo(0f, X(0.75f)); p.quadTo(X(0.5f), X(0.68f), L, X(0.78f)); p.lineTo(L, L); p.lineTo(0f, L); p.close()
                c.drawPath(p, igPinceau(rgb(215, 225, 240)))
                rect(0.28f, 0.5f, 0.72f, 0.76f, rgb(90, 55, 35))
                poly(rgb(230, 235, 245), 0.2f, 0.52f, 0.5f, 0.3f, 0.8f, 0.52f)
                rect(0.6f, 0.3f, 0.67f, 0.42f, rgb(70, 45, 30)); rect(0.36f, 0.57f, 0.5f, 0.68f, rgb(255, 200, 110))
                for (i in 0 until 3) c.drawCircle(X(0.64f + i * 0.05f + 0.02f * sin(sec * 2 + i)), X(0.25f - i * 0.07f), L * (0.03f + i * 0.012f), igPinceau(avecAlpha(rgb(200, 200, 220), 0.45f)))
            }
            9 -> {      // une pizza vue de dessus, sur une planche
                deg(0f, 0f, 1f, 1f, rgb(60, 35, 25), rgb(40, 22, 16))
                rond(0.5f, 0.5f, 0.42f, rgb(150, 100, 60)); rond(0.5f, 0.5f, 0.4f, rgb(220, 160, 80)); rond(0.5f, 0.5f, 0.34f, rgb(200, 60, 40))
                for (i in 0 until 9) { val a = rnd.nextFloat() * 6.28f; val d = rnd.nextFloat() * 0.26f
                    rond(0.5f + cos(a) * d, 0.5f + sin(a) * d, 0.06f, rgb(245, 235, 200)) }
                for (i in 0 until 5) { val a = rnd.nextFloat() * 6.28f; val d = rnd.nextFloat() * 0.25f
                    val x = X(0.5f + cos(a) * d); val y = X(0.5f + sin(a) * d)
                    c.drawOval(x - L * 0.03f, y - L * 0.015f, x + L * 0.03f, y + L * 0.015f, igPinceau(rgb(40, 130, 50))) }
            }
            10 -> {     // un chien qui court dans un parc, frisbee en l'air
                deg(0f, 0f, 1f, 1f, rgb(150, 200, 240), rgb(220, 235, 245))
                rect(0f, 0.7f, 1f, 1f, rgb(90, 170, 70))
                for (u in floatArrayOf(0.12f, 0.85f)) { rond(u, 0.45f, 0.14f, rgb(50, 120, 60))
                    c.drawRect(X(u) - L * 0.02f, X(0.5f), X(u) + L * 0.02f, X(0.72f), igPinceau(rgb(90, 60, 40))) }
                val brun = rgb(170, 110, 50)
                ovale(0.35f, 0.55f, 0.62f, 0.67f, brun); rond(0.65f, 0.52f, 0.07f, brun); ovale(0.6f, 0.45f, 0.64f, 0.55f, rgb(120, 70, 30))
                for ((u, dv) in listOf(Pair(0.38f, 0.1f), Pair(0.44f, 0.06f), Pair(0.55f, 0.1f), Pair(0.6f, 0.05f)))
                    trait(u, 0.64f, u + dv * 0.5f, 0.76f, brun, L * 0.03f)
                trait(0.35f, 0.58f, 0.26f, 0.5f, brun, L * 0.025f)
                ovale(0.72f, 0.3f, 0.88f, 0.35f, rgb(240, 70, 60))
            }
            11 -> {     // un phare au bord de la mer, la nuit
                deg(0f, 0f, 1f, 1f, rgb(15, 20, 45), rgb(50, 50, 90))
                val a = sec * 1.5f
                val p = Path(); p.moveTo(X(0.6f), X(0.28f))
                p.lineTo(X(0.6f) + cos(a) * L, X(0.28f) - L * 0.12f); p.lineTo(X(0.6f) + cos(a) * L, X(0.28f) + L * 0.12f); p.close()
                c.drawPath(p, igPinceau(avecAlpha(rgb(255, 230, 160), 0.25f)))
                deg(0f, 0.7f, 1f, 1f, rgb(20, 40, 70), rgb(10, 20, 40))
                poly(rgb(25, 22, 30), 0.35f, 0.85f, 0.45f, 0.68f, 0.8f, 0.66f, 0.95f, 0.85f)
                poly(rgb(230, 230, 235), 0.54f, 0.68f, 0.57f, 0.3f, 0.63f, 0.3f, 0.66f, 0.68f)
                for (v in floatArrayOf(0.4f, 0.55f)) rect(0.555f, v, 0.645f, v + 0.06f, rgb(200, 50, 50))
                rect(0.56f, 0.22f, 0.64f, 0.3f, rgb(255, 225, 150))
                poly(rgb(40, 35, 45), 0.54f, 0.22f, 0.6f, 0.15f, 0.66f, 0.22f)
            }
            12 -> {     // un cerisier en fleurs
                deg(0f, 0f, 1f, 1f, rgb(170, 200, 240), rgb(240, 220, 235))
                rect(0f, 0.82f, 1f, 1f, rgb(120, 170, 100))
                val t = Path(); t.moveTo(X(0.45f), X(0.85f)); t.quadTo(X(0.5f), X(0.6f), X(0.42f), X(0.45f))
                c.drawPath(t, igPinceau(rgb(80, 50, 45), L * 0.06f))
                val fl = intArrayOf(rgb(250, 180, 200), rgb(240, 150, 180), rgb(255, 210, 225))
                for (i in 0 until 26) { val a = rnd.nextFloat() * 6.28f; val d = sqrt(rnd.nextFloat()) * 0.3f
                    c.drawCircle(X(0.47f + cos(a) * d), X(0.38f + sin(a) * d * 0.8f), L * (0.05f + 0.04f * rnd.nextFloat()), igPinceau(avecAlpha(fl[i % 3], 0.9f))) }
                for (i in 0 until 5) c.drawCircle(X(rnd.nextFloat()), X(rnd.nextFloat()), L * 0.015f, igPinceau(rgb(250, 190, 210)))
            }
            13 -> {     // une tente et un feu de camp sous les étoiles
                deg(0f, 0f, 1f, 1f, rgb(8, 10, 30), rgb(30, 30, 70))
                for (i in 0 until 25) c.drawCircle(X(rnd.nextFloat()), X(rnd.nextFloat() * 0.6f), 0.9f * un, igPinceau(avecAlpha(Color.WHITE, 0.4f + 0.6f * rnd.nextFloat())))
                poly(rgb(15, 20, 30), 0f, 0.7f, 0.3f, 0.55f, 0.6f, 0.65f, 1f, 0.5f, 1f, 1f, 0f, 1f)
                poly(rgb(230, 130, 60), 0.12f, 0.85f, 0.33f, 0.55f, 0.54f, 0.85f)
                poly(rgb(60, 30, 20), 0.28f, 0.85f, 0.33f, 0.66f, 0.38f, 0.85f)
                c.drawCircle(X(0.75f), X(0.84f), L * 0.15f, igPinceau(avecAlpha(rgb(255, 140, 50), 0.25f)))
                poly(rgb(255, 170, 60), 0.68f, 0.88f, 0.75f, 0.71f, 0.82f, 0.88f)
                poly(rgb(255, 235, 150), 0.72f, 0.88f, 0.75f, 0.79f, 0.78f, 0.88f)
                trait(0.66f, 0.9f, 0.84f, 0.88f, rgb(70, 40, 25), L * 0.03f)
            }
            14 -> {     // un surfeur sur une vague
                deg(0f, 0f, 1f, 1f, rgb(110, 190, 235), rgb(200, 235, 245))
                val p = Path(); p.moveTo(0f, L); p.lineTo(0f, X(0.55f))
                p.cubicTo(X(0.35f), X(0.2f), X(0.8f), X(0.25f), X(0.85f), X(0.5f))
                p.quadTo(X(0.7f), X(0.38f), X(0.62f), X(0.52f)); p.lineTo(L, X(0.7f)); p.lineTo(L, L); p.close()
                val pp = Paint(Paint.ANTI_ALIAS_FLAG); pp.shader = LinearGradient(0f, X(0.2f), 0f, L, rgb(40, 170, 190), rgb(10, 70, 120), Shader.TileMode.CLAMP)
                c.drawPath(p, pp)
                val ec = Path(); ec.moveTo(X(0.3f), X(0.33f)); ec.cubicTo(X(0.5f), X(0.2f), X(0.8f), X(0.25f), X(0.85f), X(0.5f))
                c.drawPath(ec, igPinceau(rgb(240, 250, 255), L * 0.04f))
                c.save(); c.translate(X(0.42f), X(0.6f)); c.rotate(-20f)
                val sil = rgb(30, 25, 30)
                c.drawOval(-L * 0.14f, -L * 0.025f, L * 0.14f, L * 0.025f, igPinceau(rgb(250, 200, 60)))
                c.drawLine(0f, -L * 0.02f, 0f, -L * 0.14f, igPinceau(sil, L * 0.035f))
                c.drawLine(0f, -L * 0.1f, L * 0.08f, -L * 0.13f, igPinceau(sil, L * 0.025f))
                c.drawLine(0f, -L * 0.1f, -L * 0.08f, -L * 0.08f, igPinceau(sil, L * 0.025f))
                c.drawCircle(0f, -L * 0.17f, L * 0.03f, igPinceau(sil))
                c.restore()
            }
            15 -> {     // un champ de lavande au coucher du soleil
                deg(0f, 0f, 1f, 1f, rgb(250, 170, 120), rgb(255, 210, 160))
                rond(0.3f, 0.42f, 0.1f, rgb(255, 240, 200))
                rect(0f, 0.5f, 1f, 1f, rgb(110, 80, 150))
                for (i in 0 until 9) { val u = i / 8f
                    poly(rgb(160, 110, 200), 0.5f, 0.5f, u * 2.4f - 0.7f, 1.05f, u * 2.4f - 0.62f, 1.05f) }
                rond(0.82f, 0.47f, 0.05f, rgb(60, 50, 60)); rect(0.81f, 0.47f, 0.83f, 0.53f, rgb(60, 50, 60))
            }
            16 -> {     // un voilier au large, mer calme
                deg(0f, 0f, 1f, 1f, rgb(60, 90, 160), rgb(240, 160, 140))
                deg(0f, 0.62f, 1f, 1f, rgb(70, 80, 140), rgb(20, 30, 70))
                poly(rgb(40, 30, 40), 0.32f, 0.6f, 0.68f, 0.6f, 0.62f, 0.67f, 0.38f, 0.67f)
                poly(rgb(250, 245, 235), 0.5f, 0.22f, 0.5f, 0.58f, 0.66f, 0.58f)
                poly(rgb(230, 225, 215), 0.48f, 0.28f, 0.48f, 0.58f, 0.36f, 0.58f)
                for (i in 0 until 4) trait(0.2f + i * 0.15f, 0.75f + i * 0.05f, 0.32f + i * 0.15f, 0.75f + i * 0.05f, avecAlpha(rgb(255, 200, 170), 0.5f), un)
            }
            17 -> {     // une glace, trois boules
                deg(0f, 0f, 1f, 1f, rgb(255, 210, 220), rgb(250, 235, 200))
                poly(rgb(210, 160, 90), 0.36f, 0.55f, 0.64f, 0.55f, 0.5f, 0.95f)
                for (i in 0 until 4) trait(0.38f + i * 0.07f, 0.56f, 0.5f, 0.93f, avecAlpha(rgb(150, 100, 50), 0.6f), un)
                rond(0.42f, 0.5f, 0.13f, rgb(250, 240, 220)); rond(0.58f, 0.5f, 0.13f, rgb(120, 70, 50)); rond(0.5f, 0.36f, 0.13f, rgb(250, 150, 170))
                rond(0.5f, 0.22f, 0.04f, rgb(220, 30, 50))
            }
            18 -> {     // désert : dunes et cactus
                deg(0f, 0f, 1f, 1f, rgb(240, 150, 90), rgb(255, 210, 150))
                rond(0.7f, 0.3f, 0.09f, rgb(255, 245, 210))
                val p = Path(); p.moveTo(0f, X(0.7f)); p.quadTo(X(0.4f), X(0.5f), X(0.75f), X(0.68f)); p.quadTo(X(0.9f), X(0.74f), L, X(0.66f))
                p.lineTo(L, L); p.lineTo(0f, L); p.close(); c.drawPath(p, igPinceau(rgb(210, 130, 70)))
                val q = Path(); q.moveTo(0f, X(0.88f)); q.quadTo(X(0.5f), X(0.72f), L, X(0.9f)); q.lineTo(L, L); q.lineTo(0f, L); q.close()
                c.drawPath(q, igPinceau(rgb(180, 100, 55)))
                val v = rgb(40, 90, 50); val e = L * 0.07f
                trait(0.25f, 0.85f, 0.25f, 0.48f, v, e); trait(0.25f, 0.65f, 0.15f, 0.65f, v, e); trait(0.15f, 0.65f, 0.15f, 0.55f, v, e)
                trait(0.25f, 0.6f, 0.34f, 0.6f, v, e); trait(0.34f, 0.6f, 0.34f, 0.52f, v, e)
            }
            else -> {   // feux d'artifice au-dessus d'un fleuve (doux, pas éblouissants)
                deg(0f, 0f, 1f, 1f, rgb(10, 10, 30), rgb(30, 25, 60))
                val f = arrayOf(floatArrayOf(0.3f, 0.3f, 0f), floatArrayOf(0.68f, 0.22f, 1.3f), floatArrayOf(0.55f, 0.45f, 2.1f))
                val fc = intArrayOf(rgb(255, 120, 150), rgb(120, 200, 255), rgb(255, 210, 120))
                for (n in 0 until 3) { val u = f[n][0]; val vv = f[n][1]; val e = 0.5f + 0.5f * sin(sec * 2 + f[n][2])
                    for (i in 0 until 14) { val a = i / 14f * 6.28f
                        c.drawLine(X(u) + cos(a) * L * 0.04f, X(vv) + sin(a) * L * 0.04f, X(u) + cos(a) * L * (0.08f + 0.06f * e), X(vv) + sin(a) * L * (0.08f + 0.06f * e), igPinceau(avecAlpha(fc[n], 0.75f), 1.2f * un)) } }
                rect(0f, 0.72f, 1f, 0.8f, rgb(15, 15, 25))
                for (i in 0 until 8) rect(i / 8f, 0.62f - ((i * 37) % 5) * 0.03f, i / 8f + 0.1f, 0.73f, rgb(15, 15, 25))
                deg(0f, 0.8f, 1f, 1f, rgb(40, 30, 60), rgb(15, 12, 30))
            }
        }
    }
}

// =====================================================================================
// L'ANIMATION
// =====================================================================================
class MurNuit : Scene() {
    // la photo éteinte : en gris sombre (comme un filtre « noir et blanc » un peu bleuté)
    private val gris = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
        0.17f, 0.33f, 0.06f, 0f, 5f, 0.17f, 0.33f, 0.06f, 0f, 5f, 0.18f, 0.35f, 0.07f, 0f, 6.4f, 0f, 0f, 0f, 1f, 0f)))

    /** De 0 (éteint) à 1 (allumé) : l'allumage part du centre du mur, avec un petit grésillement doux. */
    private fun allumage(p: Mur.Pola, sec: Float): Float {
        if (p.eteint) return 0f
        val d = hypot(p.i - 2f, (p.j - 1.5f) * 0.9f)
        val t0 = 0.15f + d * 0.22f + p.alea * 0.18f
        var a = doux(borne((sec - t0) / 0.35f))
        if (a > 0f && a < 1f) a *= 0.85f + 0.15f * sin(sec * 47f + p.alea * 9f)
        return a
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val w = c.width.toFloat(); val h = c.height.toFloat(); val k = w / 540f
        val sec = i.secondes; val rev = i.revelation
        val lisse = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        // LA CAMÉRA (parallaxe) : elle avance et glisse un peu sur le côté. Chaque plan bouge selon
        // sa « profondeur » : le ciel presque pas, la forêt un peu, le mur normalement, l'herbe beaucoup.
        val avance = doux(borne(sec / 1.8f))
        val pc = Mur.position(Mur.centre, w, h); val cx0 = pc[0]; val cy0 = pc[1]
        c.save()
        fun cam(prof: Float) {
            val z = 1f + 0.09f * avance * prof
            c.restore(); c.save()
            c.translate(cx0 + (1f - avance) * 14f * k * prof, cy0); c.scale(z, z); c.translate(-cx0, -cy0)
        }
        val zoom = 1f + 0.09f * avance
        cam(0.1f)

        // --- le ciel : bleu nuit vers violet, des étoiles qui scintillent, une lune fine
        val ciel = Paint(); ciel.shader = LinearGradient(0f, 0f, 0f, h * 0.5f, intArrayOf(rgb(5, 6, 14), rgb(16, 14, 34), rgb(46, 34, 66)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(-w, -h, 2 * w, h * 0.6f, ciel)
        for (e in Mur.etoiles)
            c.drawCircle(e[0] * w, e[1] * h, 0.9f * k, igPinceau(avecAlpha(rgb(225, 220, 245), e[2] * (0.65f + 0.35f * sin(sec * 2.5f + e[3])))))
        val lx = w * 0.78f; val ly = h * 0.09f
        o.halo(c, lx, ly, w * 0.18f, 0.10f, rgb(170, 170, 210))
        val lune = Path(); lune.addCircle(lx, ly, 15f * k, Path.Direction.CW)
        val creux = Path(); creux.addCircle(lx + 7f * k, ly - 4f * k, 14f * k, Path.Direction.CW)
        lune.op(creux, Path.Op.DIFFERENCE)
        c.drawPath(lune, igPinceau(rgb(205, 205, 225)))

        // --- la forêt : quatre rangées de sapins (préparées à l'avance, floues au loin), avec leur brume
        for (rang in 0 until 4) {
            cam(0.25f + rang * 0.15f)
            val b = FondsMur.rangees[rang]; val z = FondsMur.zonesRangees[rang]
            if (b != null) c.drawBitmap(b, null, RectF(z[0] * w, z[1] * h, z[2] * w, z[3] * h), lisse)
            o.tache(c, w / 2f + sin(sec * 0.4f + rang) * 15f * k, h * (Mur.rangees[rang][3] - 0.005f), w * 0.95f, h * 0.025f, 0.30f - rang * 0.05f, rgb(70, 60, 95))
        }
        val allumes = Mur.polas.map { allumage(it, sec) }
        val total = allumes.sum() / Mur.polas.size

        cam(1f)
        // --- le sol de la clairière
        igDegrade(c, -w, h * 0.62f, 2 * w, 2 * h, intArrayOf(rgb(10, 10, 14), rgb(5, 5, 8)))

        // --- le mur de pierre : ombre au pied, dégradé sombre, joints des pierres, chaperon en haut
        val mur = RectF(Mur.X0 * w, Mur.Y0 * h, Mur.X1 * w, Mur.Y1 * h)
        c.drawRect(mur.left, mur.top + 6f * k, mur.right, mur.bottom + 6f * k, igPinceau(avecAlpha(Color.BLACK, 0.6f)))
        igDegrade(c, mur.left, mur.top, mur.right, mur.bottom, intArrayOf(rgb(30, 27, 32), rgb(20, 18, 22)))
        c.save(); c.clipRect(mur)
        val bl = 46f * k; val bh = 26f * k
        val joint = igPinceau(avecAlpha(rgb(8, 7, 10), 0.8f), 1.4f * k)
        var y = mur.top; var rangee = 0
        while (y < mur.bottom) {
            c.drawLine(mur.left, y, mur.right, y, joint)
            var x = mur.left - (if (rangee % 2 == 1) bl / 2f else 0f)
            while (x < mur.right) { c.drawLine(x, y, x, y + bh, joint); x += bl }
            y += bh; rangee++
        }
        val gr = FondsMur.grain
        if (gr != null) {               // le grain de la pierre
            val g = Paint(); g.shader = BitmapShader(gr, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            g.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY); g.alpha = 70
            c.drawRect(mur, g)
        }
        c.restore()
        c.drawRect(mur.left - 6f * k, mur.top - 10f * k, mur.right + 6f * k, mur.top, igPinceau(rgb(36, 32, 38)))

        // --- la lumière des photos sur le mur, et sur l'herbe au pied du mur
        for ((n, p) in Mur.polas.withIndex()) {
            val a = allumes[n]; if (a <= 0f) continue
            val ps = Mur.position(p, w, h); val col = Mur.teintes[p.genre]
            o.halo(c, ps[0], ps[1], ps[2] * 1.4f, 0.085f * a, col)
            o.tache(c, ps[0], h * 0.79f, ps[2] * 1.3f, h * 0.03f, 0.10f * a * (1f - p.j / Mur.NL.toFloat() * 0.5f), col)
        }

        // --- les polaroïds
        for ((n, p) in Mur.polas.withIndex()) {
            val a = allumes[n]
            val ps = Mur.position(p, w, h); val s = ps[2]
            c.save(); c.translate(ps[0], ps[1]); c.rotate(p.ang)
            val cadre = RectF(-s / 2f, -s * 0.58f, s / 2f, s * 0.62f)
            val photo = RectF(-s * 0.43f, -s * 0.51f, s * 0.43f, s * 0.35f)
            c.drawRect(cadre.left + 2.5f * k, cadre.top + 3.5f * k, cadre.right + 2.5f * k, cadre.bottom + 3.5f * k, igPinceau(avecAlpha(Color.BLACK, 0.55f)))
            // le cadre blanc : gris terne éteint, blanc chaud (mais pas éclatant) allumé
            c.drawRect(cadre, igPinceau(igMelange(rgb(44, 42, 46), rgb(185, 176, 166), a)))
            val img = FondsMur.photos[p.genre]
            // la photo éteinte (grise et sombre)…
            if (img != null) c.drawBitmap(img, null, photo, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = gris })
            else c.drawRect(photo, igPinceau(rgb(40, 40, 46)))
            c.drawRect(photo, igPinceau(avecAlpha(Color.BLACK, 0.62f)))
            // …puis allumée : couleurs vives, comme éclairée de l'intérieur
            if (a > 0f && img != null) {
                c.drawBitmap(img, null, photo, Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (255 * a).toInt().coerceIn(0, 255) })
                val lp = Paint(); lp.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                lp.shader = RadialGradient(0f, -s * 0.08f, s * 0.6f, avecAlpha(rgb(255, 235, 215), 0.30f * a), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                c.drawRect(photo, lp)
            }
            // un liseré lumineux autour du polaroïd allumé (la lumière qui « bave » un peu)
            if (a > 0f) c.drawRect(cadre, o.lumiere(avecAlpha(Mur.teintes[p.genre], 0.18f * a), 3f * k))
            c.restore()
        }

        // --- deux grands sapins au premier plan, de chaque côté (préparés à l'avance, flous)
        cam(1.35f)
        for (n in 0 until 2) {
            val b = FondsMur.grandsSapins[n]; val z = FondsMur.zonesGrands[n]
            if (b != null) c.drawBitmap(b, null, RectF(z[0] * w, z[1] * h, z[2] * w, z[3] * h), lisse)
        }
        cam(1f)

        // --- LA GUIRLANDE : un fil qui part du grand sapin de gauche, festonne le long du haut du mur,
        // et repart vers celui de droite. Les ampoules s'allument de gauche à droite, puis scintillent.
        val mt = Mur.Y0 * h
        val ancres = arrayOf(floatArrayOf(0f, h * 0.38f), floatArrayOf(Mur.X0 * w - 4f * k, mt - 6f * k), floatArrayOf(w * 0.30f, mt - 4f * k),
            floatArrayOf(w * 0.50f, mt - 6f * k), floatArrayOf(w * 0.70f, mt - 4f * k), floatArrayOf(Mur.X1 * w + 4f * k, mt - 6f * k), floatArrayOf(w, h * 0.37f))
        val fil = igPinceau(rgb(14, 12, 16), 1.3f * k)
        val ampoules = ArrayList<FloatArray>()
        for (q in 0 until ancres.size - 1) {
            val ax = ancres[q][0]; val ay = ancres[q][1]; val bx = ancres[q + 1][0]; val by = ancres[q + 1][1]
            val creux = 0.05f * hypot(bx - ax, by - ay) + 10f * k              // le fil pend entre deux attaches
            val chemin = Path(); chemin.moveTo(ax, ay)
            for (s in 1..24) { val t = s / 24f; chemin.lineTo(mix(ax, bx, t), mix(ay, by, t) + creux * 4f * t * (1f - t)) }
            c.drawPath(chemin, fil)
            val nb = max(3, (hypot(bx - ax, by - ay) / (19f * k)).toInt())
            for (s in 1 until nb) { val t = s.toFloat() / nb; ampoules.add(floatArrayOf(mix(ax, bx, t), mix(ay, by, t) + creux * 4f * t * (1f - t) + 3f * k)) }
        }
        for ((n, b) in ampoules.withIndex()) {
            val on = doux(borne((sec - 0.02f - b[0] / w * 0.25f) / 0.2f))
            val a = on * (0.82f + 0.18f * sin(sec * 2.2f + n * 1.7f))
            c.drawLine(b[0], b[1] - 3f * k, b[0], b[1] - 1f * k, igPinceau(rgb(14, 12, 16), 1.5f * k))      // la douille
            o.halo(c, b[0], b[1], 13f * k, 0.28f * a, rgb(255, 170, 80))
            c.drawCircle(b[0], b[1], 2.3f * k, igPinceau(igMelange(rgb(50, 40, 30), rgb(255, 215, 150), a)))
        }

        // --- des bougies dans des bocaux, et une lanterne, posées au pied du mur
        val bougies = arrayOf(floatArrayOf(0.16f, 1f), floatArrayOf(0.22f, 0.75f), floatArrayOf(0.80f, 0.9f), floatArrayOf(0.86f, 0.7f), floatArrayOf(0.47f, 0.6f))
        for ((n, bg) in bougies.withIndex()) {
            val t = bg[1]; val bx = bg[0] * w; val by = Mur.Y1 * h + 40f * k + (n % 2) * 8f * k
            val fl = doux(borne((sec - 0.1f - n * 0.06f) / 0.3f)) * (0.85f + 0.15f * sin(sec * 9f + n * 3f))
            o.halo(c, bx, by - 8f * k * t, 34f * k * t, 0.30f * fl, rgb(255, 160, 70))
            val bocal = RectF(bx - 7f * k * t, by - 18f * k * t, bx + 7f * k * t, by)
            c.drawRoundRect(bocal, 2.5f * k, 2.5f * k, igPinceau(avecAlpha(igMelange(rgb(40, 34, 30), rgb(255, 170, 90), fl * 0.6f), 0.85f)))
            c.drawRect(bocal.left - k, bocal.top - 2f * k, bocal.right + k, bocal.top + k, igPinceau(rgb(30, 26, 22)))
            c.drawOval(bx - 1.6f * k, by - 12f * k * t, bx + 1.6f * k, by - 6f * k * t, igPinceau(avecAlpha(rgb(255, 225, 160), fl)))
        }
        val lxl = w * 0.64f; val lyl = Mur.Y1 * h + 52f * k
        val fll = doux(borne((sec - 0.2f) / 0.3f)) * (0.9f + 0.1f * sin(sec * 7f))
        o.halo(c, lxl, lyl - 16f * k, 50f * k, 0.32f * fll, rgb(255, 170, 80))
        val corps = RectF(lxl - 10f * k, lyl - 30f * k, lxl + 10f * k, lyl)
        val metal = rgb(20, 16, 14)
        c.drawRect(corps, igPinceau(igMelange(rgb(30, 26, 22), rgb(255, 190, 110), fll * 0.7f)))
        for (xx in floatArrayOf(corps.left, lxl, corps.right)) c.drawLine(xx, corps.top, xx, corps.bottom, igPinceau(metal, 2.2f * k))
        igPoly(c, metal, lxl - 13f * k, corps.top, lxl, corps.top - 9f * k, lxl + 13f * k, corps.top)
        c.drawCircle(lxl, corps.top - 12f * k, 3.5f * k, igPinceau(metal, 1.5f * k))
        c.drawRect(corps.left - 2f * k, corps.bottom - 3f * k, corps.right + 2f * k, corps.bottom, igPinceau(metal))

        // --- la brume au pied du mur, éclairée par les photos
        o.tache(c, w / 2f + sin(sec * 0.6f) * 20f * k, h * 0.78f, w * 0.8f, h * 0.04f, 0.25f + 0.2f * total, igMelange(rgb(50, 44, 70), rgb(150, 90, 130), total))

        // --- l'herbe du premier plan : des brins noirs qui ondulent, un peu flous
        cam(1.6f)
        val herbe = igPinceau(rgb(4, 4, 7)).apply { maskFilter = BlurMaskFilter(1.6f * k, BlurMaskFilter.Blur.NORMAL) }
        for (hb in Mur.herbes) {
            val bx = hb[0] * w * 1.1f - w * 0.05f
            val by = h * (0.83f + 0.17f * modulo(hb[0] * 13f, 1f))
            val hh = h * 0.09f * hb[1]
            val sway = hb[2] + 0.12f * sin(sec * 1.6f + hb[3])
            val brin = Path(); brin.moveTo(bx - 2.5f * k, by)
            brin.quadTo(bx + sway * hh * 0.5f, by - hh * 0.6f, bx + sway * hh, by - hh)
            brin.quadTo(bx + sway * hh * 0.4f, by - hh * 0.55f, bx + 2.5f * k, by); brin.close()
            c.drawPath(brin, herbe)
        }
        c.drawRect(-w, h * 0.93f, 2 * w, 2 * h, herbe)

        // --- des lucioles qui flottent
        cam(1.2f)
        for (l in Mur.lucioles) {
            val fx = w * (l[0] + 0.03f * sin(sec * l[3] + l[2])); val fy = h * (l[1] + 0.01f * cos(sec * l[3] * 1.3f + l[2]))
            val a = borne(0.4f + 0.6f * sin(sec * 2f * l[3] + l[2])) * borne(sec / 0.4f)
            o.halo(c, fx, fy, 9f * k, 0.35f * a, rgb(255, 210, 120))
            c.drawCircle(fx, fy, 1.3f * k, igPinceau(avecAlpha(rgb(255, 230, 170), 0.8f * a)))
        }
        // --- des ampoules TOUT PRÈS de la caméra, complètement floues (bokeh) : de gros ronds chauds en haut
        cam(2.2f)
        for ((n, b) in Mur.bokeh.withIndex()) {
            val a = doux(borne((sec - 0.05f - b[0] * 0.2f) / 0.3f)) * (0.75f + 0.25f * sin(sec * 1.8f + n))
            c.drawCircle(w * b[0], h * b[1], b[2] * k, igFlou(3f * k, avecAlpha(rgb(255, 175, 90), 0.15f * a)))
            c.drawCircle(w * b[0], h * b[1], b[2] * k, o.lumiere(avecAlpha(rgb(255, 200, 130), 0.06f * a), 1.5f * k))
        }
        c.restore()
        o.vignette(c, w, h, 0.65f)
        // le grain de film, très léger, qui bouge à chaque image
        if (gr != null) {
            val g = Paint(); val sh = BitmapShader(gr, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            val m = Matrix(); m.setTranslate(modulo(sec * 997f, 128f), modulo(sec * 613f, 128f)); m.postScale(k, k); sh.setLocalMatrix(m)
            g.shader = sh; g.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY); g.alpha = 26
            c.drawRect(0f, 0f, w, h, g)
        }

        // --- RÉVÉLATION : on entre dans la photo centrale. Un trou en forme de photo (bords doux)
        // part de sa position et grandit jusqu'à couvrir l'écran ; l'app apparaît dedans.
        if (rev > 0f) {
            val ps = Mur.position(Mur.centre, w, h)
            val x = cx0 + (ps[0] - cx0) * zoom; val yy = cy0 + (ps[1] - cy0) * zoom; val s = ps[2] * zoom
            val v = accelere(rev) * 0.8f + rev * 0.2f
            val cxr = mix(x, w / 2f, v); val cyr = mix(yy - s * 0.08f, h / 2f, v)
            val dl = mix(s * 0.43f, w * 0.75f, v); val dh = mix(s * 0.43f, h * 0.75f, v)
            val trou = Paint(Paint.ANTI_ALIAS_FLAG); trou.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            trou.maskFilter = BlurMaskFilter(4f * k + 30f * k * v, BlurMaskFilter.Blur.NORMAL)
            c.save(); c.translate(cxr, cyr); c.rotate(Mur.centre.ang * (1f - v))
            c.drawRoundRect(-dl, -dh, dl, dh, 4f * k, 4f * k, trou)
            c.restore()
            if (rev >= 0.999f) c.drawRect(0f, 0f, w, h, o.gomme())
        }
    }
}
