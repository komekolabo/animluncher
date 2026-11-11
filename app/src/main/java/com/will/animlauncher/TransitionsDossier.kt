package com.will.animlauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// LES TRANSITIONS D'OUVERTURE PAR DOSSIER (11/11, validées par Will sur les démos vidéo).
// Chaque dossier qui a la sienne remplace la « poussière » habituelle (les traînées vers chaque app) par
// un effet qui raconte son thème :
//  - MAPS (trajets)   : le dossier se DÉPLIE comme une carte en 3 volets ; dessus, une ville dessinée par une
//                       trame de petits points ; un itinéraire allume les points de la rue qu'il suit, et à
//                       chaque app, un repère se pose (avec une onde fine) et l'app éclot.
//  - HEALTH (sante)   : le cœur, posé à côté du titre, BAT ; chaque battement envoie une onde douce dans le
//                       dossier : la 1re onde fait naître la 1re rangée, la 2e la 2e… et fait rebattre les autres.
//  - PHOTOS (photo)   : un viseur se cale sur le dossier, les lamelles d'un objectif s'ouvrent, et les apps se
//                       « développent » en diagonale (du sombre en noir et blanc vers la couleur).
//  - FINANCE          : une courbe de bourse se trace à travers le dossier avec son aire douce dessous ; les apps
//                       de chaque colonne montent quand la courbe les dépasse.
// Les autres dossiers gardent l'ouverture d'origine.
//
// COMMENT ÇA S'EMBOÎTE avec DossierVue (le dessin du dossier) :
//  1) preparer()   : appelé quand la mise en page est connue (et refait si elle change) : on calcule une fois
//                    pour toutes ce qui est lourd (la carte de points, l'itinéraire, la courbe…) ;
//  2) dansPanneau(): dessiné DANS la fenêtre, sous le titre (la carte de Maps) ;
//  3) avantApps()  : les décors dessinés sous les apps (ondes, lamelles, courbe, itinéraire, repères) ;
//  4) arrivee()    : pour chaque app, OÙ en est son apparition à l'instant t (visibilité, taille, décalage…).
// Les tailles sont en « k » : k = largeur de l'écran / 540 (comme partout dans le dossier).

// Ce que DossierVue donne aux transitions : la fenêtre, la grille des apps de la 1re page, et la place du logo.
class CadreDossier(
    val panneau: RectF, val k: Float, val colonnes: Int, val lignes: Int,
    val celluleL: Float, val celluleH: Float,
    val appX: FloatArray, val appY: FloatArray,      // le centre de chaque app de la 1re page
    val titreX: Float, val titreY: Float,            // l'endroit où le logo se pose, à côté du titre
    val teinte: Teinte
)

// L'apparition d'une app à l'instant t (un seul objet, réutilisé à chaque fois : pas de déchets à ramasser).
class Arrivee {
    var alpha = 0f         // 0 = invisible, 1 = visible
    var echelle = 1f       // la taille (1 = normale)
    var penche = 0f        // la petite rotation (en degrés)
    var decalY = 0f        // décalage vers le bas (en pixels)
    var gris = 0f          // 0 = couleurs normales, 1 = noir et blanc assombri
    var nv = 0f            // l'apparition du nom (0 à 1)
}

abstract class EffetOuverture {
    open val deplie = false           // vrai = la fenêtre se déplie en 3 volets au lieu de sortir du widget
    protected var cadre: CadreDossier? = null
    protected val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val forme = Path()      // la forme arrondie de la fenêtre (pour ne rien dessiner en dehors)
    protected val arr = Arrivee()

    open fun preparer(c: CadreDossier) {
        cadre = c
        forme.reset()
        forme.addRoundRect(c.panneau, 46f * c.k, 46f * c.k, Path.Direction.CW)
    }
    open fun dansPanneau(c: Canvas, t: Float, p: Float) {}
    open fun avantApps(c: Canvas, t: Float, fer: Float, p: Float) {}
    abstract fun arrivee(i: Int, t: Float): Arrivee
    open fun battementLogo(t: Float) = 1f

    // les petites formules de mouvement (les mêmes que dans DossierVue et les démos)
    protected fun lisse(x: Float) = x * x * (3 - 2 * x)
    protected fun seg(t: Float, a: Float, b: Float) = lisse(((t - a) / (b - a)).coerceIn(0f, 1f))
    protected fun freine(x: Float) = 1 - (1 - x) * (1 - x) * (1 - x)
    protected fun mix(a: Float, b: Float, u: Float) = a + (b - a) * u
    protected fun borne(x: Float) = x.coerceIn(0f, 1f)
    protected fun ressort(x: Float, tension: Float = 2.6f): Float { val y = x - 1; return y * y * ((tension + 1) * y + tension) + 1 }
    // une petite bosse douce (0 -> 1 -> 0) autour de « centre », de demi-largeur « demi »
    protected fun bosse(t: Float, centre: Float, demi: Float): Float {
        val u = (t - centre) / demi
        return if (abs(u) >= 1) 0f else 0.5f + 0.5f * cos(u * PI.toFloat())
    }
    protected fun ligne(i: Int) = i / (cadre?.colonnes ?: 3)
    protected fun colonne(i: Int) = i % (cadre?.colonnes ?: 3)
}

object TransitionsDossier {
    // Le dossier « groupe » a-t-il sa transition ? (null = l'ouverture d'origine)
    fun pour(groupe: String): EffetOuverture? = when (groupe) {
        "trajets" -> EffetCarte()
        "sante" -> EffetBattement()
        "photo" -> EffetObjectif()
        "finance" -> EffetCourbe()
        else -> null
    }
}

// UN TRACÉ : une ligne faite de petits segments, qu'on peut dessiner en partie (de 0 à 1) — pour l'itinéraire
// et la courbe de bourse. cum = la longueur parcourue jusqu'à chaque point.
class Trace(val xs: FloatArray, val ys: FloatArray) {
    val cum = FloatArray(xs.size).also { c ->
        for (i in 1 until xs.size) c[i] = c[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
    }
    val total get() = cum[cum.size - 1]
    var finX = 0f; var finY = 0f            // le bout du dernier morceau dessiné

    // remplit « chemin » avec la partie [0, part] du tracé, et retient son bout dans finX / finY
    fun partie(chemin: Path, part: Float) {
        chemin.reset()
        chemin.moveTo(xs[0], ys[0]); finX = xs[0]; finY = ys[0]
        val cible = part.coerceIn(0f, 1f) * total
        for (i in 1 until xs.size) {
            if (cum[i] <= cible) { chemin.lineTo(xs[i], ys[i]); finX = xs[i]; finY = ys[i]; continue }
            val f = (cible - cum[i - 1]) / max(1e-4f, cum[i] - cum[i - 1])
            finX = xs[i - 1] + (xs[i] - xs[i - 1]) * f; finY = ys[i - 1] + (ys[i] - ys[i - 1]) * f
            chemin.lineTo(finX, finY)
            return
        }
    }

    // l'endroit (0 à 1) du tracé le plus proche du point (x, y)
    fun plusProche(x: Float, y: Float): Float {
        var best = Float.MAX_VALUE; var ib = 0
        for (i in xs.indices) {
            val d = (xs[i] - x) * (xs[i] - x) + (ys[i] - y) * (ys[i] - y)
            if (d < best) { best = d; ib = i }
        }
        return cum[ib] / total
    }

    companion object {
        // Une courbe douce qui passe par tous les points donnés (« Catmull-Rom »), découpée en n morceaux entre deux points.
        fun courbe(px: FloatArray, py: FloatArray, n: Int): Trace {
            val m = px.size
            val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
            for (i in 0 until m - 1) {
                val i0 = max(0, i - 1); val i3 = minOf(m - 1, i + 2)
                for (s in 0 until n) {
                    val u = s / n.toFloat(); val u2 = u * u; val u3 = u2 * u
                    fun cr(p0: Float, p1: Float, p2: Float, p3: Float) =
                        0.5f * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3)
                    xs.add(cr(px[i0], px[i], px[i + 1], px[i3])); ys.add(cr(py[i0], py[i], py[i + 1], py[i3]))
                }
            }
            xs.add(px[m - 1]); ys.add(py[m - 1])
            return Trace(xs.toFloatArray(), ys.toFloatArray())
        }
    }
}

// =====================================================================================================
// MAPS : la carte qui se déplie, en TRAME DE POINTS (le style « B » choisi par Will le 11/11)
// =====================================================================================================
private class EffetCarte : EffetOuverture() {
    override val deplie = true

    private var carte: Bitmap? = null                 // la ville en points, dessinée une seule fois
    private var route: Trace? = null                  // l'itinéraire (il suit les avenues)
    private var arriveeA = FloatArray(0)              // l'instant où l'itinéraire passe par chaque app
    // les points de la trame qui sont SUR l'itinéraire (ils s'allument à son passage) : x, y, et l'endroit (0 à 1)
    private var allumeX = FloatArray(0); private var allumeY = FloatArray(0); private var allumeF = FloatArray(0)
    private val peinture = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun preparer(c: CadreDossier) {
        super.preparer(c)
        val k = c.k; val pan = c.panneau
        val L = pan.left; val R = pan.right; val T = pan.top; val B = pan.bottom; val W = pan.width()
        val haut = T + 95f * k                         // la carte commence sous le titre
        val hc = B - haut
        fun rx(u: Float) = L + u * W
        fun ry(u: Float) = haut + u * hc
        val lignesY = FloatArray(c.lignes) { c.appY[minOf(c.appY.size - 1, it * c.colonnes)] }
        val colsX = FloatArray(c.colonnes) { c.appX[minOf(c.appX.size - 1, it)] }

        // ---- LES RUES (des listes de points), avec leur largeur et leur importance (0 à 1) ----
        class Rue(val xs: FloatArray, val ys: FloatArray, val larg: Float, val force: Float)
        val rues = ArrayList<Rue>()
        fun droite(x0: Float, y0: Float, x1: Float, y1: Float, larg: Float, force: Float) =
            rues.add(Rue(floatArrayOf(x0, x1), floatArrayOf(y0, y1), larg, force))
        // les grandes avenues : une par rangée d'apps, et une sur la 1re et la dernière colonne (l'itinéraire les suit)
        for (y in lignesY) droite(L - 20 * k, y, R + 20 * k, y, 6 * k, 1f)
        for (x in floatArrayOf(colsX.first(), colsX.last())) droite(x, haut - 20 * k, x, B + 20 * k, 6 * k, 1f)
        // les rues secondaires, un peu de travers pour casser la grille : entre les colonnes, les colonnes du milieu,
        // et entre les rangées
        for (j in 1 until c.colonnes) {
            val xb = L + 20 * k + c.celluleL * j; val d = if (j % 2 == 1) -7 * k else 6 * k
            droite(xb - d, haut - 20 * k, xb + d, B + 20 * k, 4 * k, 0.6f)
        }
        for (j in 1 until c.colonnes - 1) droite(colsX[j] + 3 * k, haut - 20 * k, colsX[j] - 4 * k, B + 20 * k, 4 * k, 0.6f)
        val penteH = floatArrayOf(7f, -9f, 6f, -8f, 7f)
        for (j in 0..c.lignes) {
            val yb = lignesY[0] - c.celluleH / 2 + 7 * k + c.celluleH * j; val d = penteH[j % penteH.size] * k
            droite(L - 20 * k, yb - d, R + 20 * k, yb + d, 4 * k, 0.6f)
        }
        // le canal (une courbe en S) et ses deux quais
        val canal = Trace.courbe(
            floatArrayOf(rx(-0.07f), rx(0.18f), rx(0.39f), rx(0.57f), rx(0.68f), rx(0.83f), rx(1.07f)),
            floatArrayOf(ry(0.62f), ry(0.61f), ry(0.58f), ry(0.49f), ry(0.38f), ry(0.30f), ry(0.28f)), 18)
        for (dec in floatArrayOf(-29 * k, 29 * k))
            rues.add(Rue(canal.xs, FloatArray(canal.ys.size) { canal.ys[it] + dec }, 3 * k, 0.45f))
        // la voie rapide, en diagonale
        val bd = Trace.courbe(
            floatArrayOf(rx(-0.07f), rx(0.26f), rx(0.52f), rx(0.61f), rx(0.75f), rx(1.07f)),
            floatArrayOf(ry(0.87f), ry(0.85f), ry(0.675f), ry(0.62f), ry(0.53f), ry(0.50f)), 18)
        rues.add(Rue(bd.xs, bd.ys, 7 * k, 1f))
        // un petit quartier aux rues courbes, en bas à droite
        for (j in 0 until 3) {
            val r = 35 * k + 21 * k * j
            rues.add(Rue(FloatArray(13) { R - 37.5f * k + r * cos(PI.toFloat() * (1 + it / 24f)) },
                FloatArray(13) { B - 30 * k + r * sin(PI.toFloat() * (1 + it / 24f)) }, 3 * k, 0.5f))
        }

        // ---- 1) UN « MASQUE » INVISIBLE : les rues en blanc avec un halo, et l'eau à part ----
        val lw = W.roundToInt().coerceAtLeast(1); val lh = pan.height().roundToInt().coerceAtLeast(1)
        val masque = Bitmap.createBitmap(lw, lh, Bitmap.Config.ALPHA_8)
        val cm = Canvas(masque); cm.translate(-L, -T)
        val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val ch = Path()
        fun chemin(xs: FloatArray, ys: FloatArray): Path {
            ch.reset(); ch.moveTo(xs[0], ys[0]); for (i in 1 until xs.size) ch.lineTo(xs[i], ys[i]); return ch
        }
        for (rue in rues) for ((f, al) in listOf(3.2f to 0.18f, 1f to 1f)) {
            trait.strokeWidth = rue.larg * f
            trait.color = Color.argb((255 * rue.force * al).toInt(), 0, 0, 0)
            cm.drawPath(chemin(rue.xs, rue.ys), trait)
        }
        val eau = Bitmap.createBitmap(lw, lh, Bitmap.Config.ALPHA_8)
        val ce = Canvas(eau); ce.translate(-L, -T)
        trait.strokeWidth = 42 * k; trait.color = Color.BLACK
        ce.drawPath(chemin(canal.xs, canal.ys), trait)
        val dm = lire(masque); val de = lire(eau)
        val pas = masque.rowBytes; val pasE = eau.rowBytes
        masque.recycle(); eau.recycle()

        // ---- 2) LA CARTE : un fond très sombre avec vignettage, puis la trame de points ----
        val te = c.teinte
        val img = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
        val ci = Canvas(img)
        ci.clipPath(Path().apply { addRoundRect(0f, 0f, lw.toFloat(), lh.toFloat(), 46 * k, 46 * k, Path.Direction.CW) })
        ci.drawColor(Color.rgb((Color.red(te.bas) * 0.5f).toInt(), (Color.green(te.bas) * 0.5f).toInt(), (Color.blue(te.bas) * 0.5f).toInt()))
        val rv = 0.75f * lh
        pinceau.shader = RadialGradient(lw / 2f, lh * 0.55f, rv,
            intArrayOf(Teinte.avecAlpha(te.haut, 0.35f * 255), Teinte.avecAlpha(te.haut, 0.35f * 255), Color.argb(115, 0, 0, 0)),
            floatArrayOf(0f, (40 * k / rv).coerceIn(0f, 0.99f), 1f), Shader.TileMode.CLAMP)
        ci.drawRect(0f, 0f, lw.toFloat(), lh.toFloat(), pinceau)
        pinceau.shader = null
        val ecart = 7 * k                            // la distance entre deux points de la trame
        val y0 = haut - 15 * k - T; val x0 = 3.5f * k
        val nx = ((lw - x0) / ecart).toInt() + 1; val ny = ((lh - y0) / ecart).toInt() + 1
        val existe = BooleanArray(max(0, nx * ny))
        val o = (2.5f * k).roundToInt()
        for (gy in 0 until ny) for (gx in 0 until nx) {
            val x = x0 + gx * ecart; val y = y0 + gy * ecart
            val xi = x.roundToInt(); val yi = y.roundToInt()
            if (xi !in 0 until lw || yi !in 0 until lh) continue
            if ((de[yi * pasE + xi].toInt() and 0xFF) > 128) continue    // pas de points sur l'eau : le canal se lit « en creux »
            // la rue la plus lumineuse autour du point (sinon les rues en biais tombent entre deux rangées de points)
            var v = 0
            for (dy in intArrayOf(-o, 0, o)) for (dx in intArrayOf(-o, 0, o)) {
                val xx = (xi + dx).coerceIn(0, lw - 1); val yy = (yi + dy).coerceIn(0, lh - 1)
                v = max(v, dm[yy * pas + xx].toInt() and 0xFF)
            }
            val f = v / 255f
            pinceau.color = Teinte.avecAlpha(te.clair, (0.17f + 0.32f * f) * 255)
            ci.drawCircle(x, y, (0.75f + 0.7f * f) * k, pinceau)
            existe[gy * nx + gx] = true
        }
        // le haut de la carte se fond dans la fenêtre (le titre reste net)
        pinceau.shader = LinearGradient(0f, 80 * k, 0f, 130 * k, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        pinceau.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        ci.drawRect(0f, 0f, lw.toFloat(), lh.toFloat(), pinceau)
        pinceau.xfermode = null; pinceau.shader = null
        carte?.recycle()
        carte = img

        // ---- 3) L'ITINÉRAIRE : il entre à gauche, suit chaque rangée en serpentin (virages arrondis), ressort ----
        val coinsX = ArrayList<Float>(); val coinsY = ArrayList<Float>()
        coinsX.add(L - 15 * k); coinsY.add(lignesY[0])
        for (r in 0 until c.lignes) {
            val bout = if (r % 2 == 0) colsX.last() else colsX.first()
            coinsX.add(bout); coinsY.add(lignesY[r])
            if (r < c.lignes - 1) { coinsX.add(bout); coinsY.add(lignesY[r + 1]) }
        }
        coinsX[coinsX.size - 1] = if ((c.lignes - 1) % 2 == 0) R + 15 * k else L - 15 * k
        val rxs = ArrayList<Float>(); val rys = ArrayList<Float>()
        rxs.add(coinsX[0]); rys.add(coinsY[0])
        fun jusqua(x: Float, y: Float) {
            val ax = rxs.last(); val ay = rys.last()
            val n = max(1, (hypot(x - ax, y - ay) / (4 * k)).toInt())
            for (s in 1..n) { rxs.add(mix(ax, x, s / n.toFloat())); rys.add(mix(ay, y, s / n.toFloat())) }
        }
        val rayon = 23 * k
        for (i in 1 until coinsX.size - 1) {
            val ax = coinsX[i - 1]; val ay = coinsY[i - 1]; val bx = coinsX[i]; val by = coinsY[i]
            val dx = coinsX[i + 1]; val dy = coinsY[i + 1]
            val l1 = max(1e-3f, hypot(bx - ax, by - ay)); val l2 = max(1e-3f, hypot(dx - bx, dy - by))
            val rr = minOf(rayon, l1 / 2, l2 / 2)
            val p1x = bx - (bx - ax) / l1 * rr; val p1y = by - (by - ay) / l1 * rr
            val p2x = bx + (dx - bx) / l2 * rr; val p2y = by + (dy - by) / l2 * rr
            jusqua(p1x, p1y)
            for (s in 1..10) {          // le virage : une petite courbe de Bézier
                val u = s / 10f
                rxs.add((1 - u) * (1 - u) * p1x + 2 * u * (1 - u) * bx + u * u * p2x)
                rys.add((1 - u) * (1 - u) * p1y + 2 * u * (1 - u) * by + u * u * p2y)
            }
        }
        jusqua(coinsX.last(), coinsY.last())
        val tr = Trace(rxs.toFloatArray(), rys.toFloatArray())
        route = tr
        // l'instant où la tête de l'itinéraire passe au plus près de chaque app
        arriveeA = FloatArray(c.appX.size) { i ->
            val f = tr.plusProche(c.appX[i], c.appY[i])
            var t = 0.3f
            while (t < 1.2f && seg(t, 0.3f, 1.2f) < f) t += 0.004f
            t
        }
        // les points de la trame que l'itinéraire recouvre (dans l'ordre où il les rencontre)
        val vus = BooleanArray(existe.size)
        val ax2 = ArrayList<Float>(); val ay2 = ArrayList<Float>(); val af = ArrayList<Float>()
        for (i in tr.xs.indices) {
            val gx = ((tr.xs[i] - L - x0) / ecart).roundToInt(); val gy = ((tr.ys[i] - T - y0) / ecart).roundToInt()
            if (gx !in 0 until nx || gy !in 0 until ny) continue
            val id = gy * nx + gx
            if (!existe[id] || vus[id]) continue
            val x = L + x0 + gx * ecart; val y = T + y0 + gy * ecart
            if (hypot(x - tr.xs[i], y - tr.ys[i]) > 4.5f * k) continue
            vus[id] = true; ax2.add(x); ay2.add(y); af.add(tr.cum[i] / tr.total)
        }
        allumeX = ax2.toFloatArray(); allumeY = ay2.toFloatArray(); allumeF = af.toFloatArray()
    }

    // les octets d'une image « alpha » (un octet par pixel, ligne après ligne)
    private fun lire(b: Bitmap): ByteArray {
        val tampon = ByteBuffer.allocate(b.rowBytes * b.height)
        b.copyPixelsToBuffer(tampon)
        return tampon.array()
    }

    // la carte, dans la fenêtre (elle recule un peu une fois les apps arrivées, pour qu'elles restent lisibles)
    override fun dansPanneau(c: Canvas, t: Float, p: Float) {
        val img = carte ?: return
        val cd = cadre ?: return
        peinture.alpha = (255 * p * (1 - 0.3f * seg(t, 1.4f, 2.1f))).toInt().coerceIn(0, 255)
        c.drawBitmap(img, cd.panneau.left, cd.panneau.top, peinture)
    }

    override fun avantApps(c: Canvas, t: Float, fer: Float, p: Float) {
        val cd = cadre ?: return
        val k = cd.k; val te = cd.teinte; val pan = cd.panneau
        val vif = te.logo[0]
        c.save()
        c.clipPath(forme)
        // un reflet très doux qui balaie la carte une fois (comme la lumière sur un papier glacé) — pas de flash
        val rf = seg(t, 0.35f, 1.25f)
        if (rf > 0f && rf < 1f && fer == 0f) {
            val xc = mix(pan.left - 200 * k, pan.right + 200 * k, rf)
            pinceau.shader = LinearGradient(xc - 130 * k, pan.top, xc + 130 * k, pan.top + 250 * k,
                intArrayOf(Color.TRANSPARENT, Teinte.avecAlpha(te.clair, 0.045f * 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            c.drawRect(pan, pinceau)
            pinceau.shader = null
        }
        // l'itinéraire : les points de la rue s'allument à son passage ; ensuite il reste en filigrane
        val tr = route
        val prog = seg(t, 0.3f, 1.2f)
        val aR = (0.4f + 0.6f * (1 - seg(t, 1.5f, 2.1f))) * (1 - fer)
        if (tr != null && prog > 0f && aR > 0f) {
            pinceau.color = Teinte.avecAlpha(vif, 0.95f * 255 * aR)
            for (i in allumeF.indices) if (allumeF[i] <= prog) c.drawCircle(allumeX[i], allumeY[i], 1.7f * k, pinceau)
            if (prog < 1f) {
                // la tête : un point blanc avec un halo doux
                tr.partie(chemin, prog)
                val hx = tr.finX; val hy = tr.finY
                pinceau.shader = RadialGradient(hx, hy, 17 * k, Teinte.avecAlpha(vif, 0.45f * 255 * aR), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                c.drawCircle(hx, hy, 17 * k, pinceau)
                pinceau.shader = null
                pinceau.color = Color.argb((255 * aR).toInt(), 255, 255, 255)
                c.drawCircle(hx, hy, 2.75f * k, pinceau)
            }
        }
        // à chaque app : un repère se pose (anneau fin + point, et une onde qui s'élargit en s'effaçant)
        if (fer == 0f) for (i in arriveeA.indices) {
            val ta = arriveeA[i]
            val pin = borne((t - ta) / 0.22f)
            val v = borne((t - (ta + 0.16f)) / 0.4f)
            if (pin <= 0f || v >= 1f) continue
            val a = 1 - v
            val x = cd.appX[i]; val y = cd.appY[i]
            val onde = freine(pin)
            pinceau.style = Paint.Style.STROKE
            pinceau.strokeWidth = 0.75f * k
            pinceau.color = Teinte.avecAlpha(vif, 0.5f * (1 - onde) * a * 255)
            c.drawCircle(x, y, 5 * k + 23 * k * onde, pinceau)
            val e = ressort(pin, 2f).coerceAtLeast(0f)
            pinceau.strokeWidth = 1f * k
            pinceau.color = Teinte.avecAlpha(vif, 0.9f * a * 255)
            c.drawCircle(x, y, 6.5f * k * e, pinceau)
            pinceau.style = Paint.Style.FILL
            pinceau.color = Color.argb((255 * a).toInt(), 255, 255, 255)
            c.drawCircle(x, y, 2.5f * k * e, pinceau)
        }
        c.restore()
    }
    private val chemin = Path()

    // l'app éclot juste après que son repère s'est posé
    override fun arrivee(i: Int, t: Float): Arrivee {
        val ta = if (i < arriveeA.size) arriveeA[i] else 1.2f
        val v = borne((t - (ta + 0.16f)) / 0.4f)
        arr.alpha = borne(v / 0.2f)
        arr.echelle = 0.2f + 0.8f * ressort(v)
        arr.penche = -10f * (1 - freine(v))
        arr.decalY = 0f; arr.gris = 0f
        arr.nv = borne((t - (ta + 0.45f)) / 0.3f)
        return arr
    }
}

// =====================================================================================================
// HEALTH : le battement
// =====================================================================================================
private class EffetBattement : EffetOuverture() {
    private var bats = floatArrayOf(0.55f, 0.92f, 1.29f)   // les battements (en secondes), un par rangée (3 au moins)
    private var vitesse = 750f                             // la vitesse des ondes (pixels par seconde, x k)
    private var distance = FloatArray(0)                   // la distance du cœur à chaque app

    override fun preparer(c: CadreDossier) {
        super.preparer(c)
        bats = FloatArray(max(3, c.lignes)) { 0.55f + 0.37f * it }
        vitesse = 750f * c.k
        distance = FloatArray(c.appX.size) { hypot(c.appX[it] - c.titreX, c.appY[it] - c.titreY) }
    }

    // le cœur bat à côté du titre : un « boum » puis un plus petit « boum » juste après
    override fun battementLogo(t: Float): Float {
        var e = 1f
        for (b in bats) e += 0.16f * bosse(t, b, 0.07f) + 0.08f * bosse(t, b + 0.16f, 0.07f)
        return e
    }

    // chaque battement envoie une onde de lumière douce, qui part du cœur et s'efface en s'éloignant
    override fun avantApps(c: Canvas, t: Float, fer: Float, p: Float) {
        val cd = cadre ?: return
        val k = cd.k
        c.save(); c.clipPath(forme)
        for (b in bats) {
            if (t < b) continue
            val r = (t - b) * vitesse
            val a = (1 - borne(r / (750f * k))) * (1 - fer)
            if (a <= 0.01f) continue
            val r1 = r + 15 * k; val r0 = max(0f, r - 45 * k)
            val p0 = r0 / r1; val p7 = (r0 + 0.7f * (r1 - r0)) / r1
            pinceau.shader = RadialGradient(cd.titreX, cd.titreY, r1,
                intArrayOf(Color.TRANSPARENT, Teinte.avecAlpha(cd.teinte.lueur, 0f), Teinte.avecAlpha(cd.teinte.lueur, 0.22f * a * 255), Teinte.avecAlpha(cd.teinte.lueur, 0f)),
                floatArrayOf(0f, p0, p7, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(cd.titreX, cd.titreY, r1, pinceau)
        }
        pinceau.shader = null
        c.restore()
    }

    // la 1re onde fait naître la 1re rangée, la 2e la 2e… et chaque onde qui repasse fait rebattre l'app
    override fun arrivee(i: Int, t: Float): Arrivee {
        val rang = ligne(i).coerceAtMost(bats.size - 1)
        val d = if (i < distance.size) distance[i] else 0f
        val ti = bats[rang] + d / vitesse
        val v = borne((t - ti) / 0.32f)
        var bat = 0f
        for (j in rang + 1 until bats.size) bat += 0.1f * bosse(t, bats[j] + d / vitesse + 0.05f, 0.08f)
        arr.alpha = borne(v / 0.2f)
        arr.echelle = (0.2f + 0.8f * ressort(v)) * (1 + bat)
        arr.penche = 0f; arr.decalY = 0f; arr.gris = 0f
        arr.nv = borne((t - (ti + 0.35f)) / 0.3f)
        return arr
    }
}

// =====================================================================================================
// PHOTOS : l'objectif
// =====================================================================================================
private class EffetObjectif : EffetOuverture() {
    private val lamelles = Path().apply { fillType = Path.FillType.EVEN_ODD }

    override fun avantApps(c: Canvas, t: Float, fer: Float, p: Float) {
        val cd = cadre ?: return
        val k = cd.k; val pan = cd.panneau; val te = cd.teinte
        val cx = pan.centerX(); val cy = pan.centerY()
        // les lamelles de l'objectif : un hexagone qui s'ouvre en tournant (dans la fenêtre seulement)
        val rMax = 450 * k
        val R = seg(t, 0.28f, 0.72f) * rMax
        val a = (1 - fer) * p
        if (R < rMax - 0.5f && a > 0f) {
            val rot = (70f * (1 - R / rMax)) * PI.toFloat() / 180f
            val sx = FloatArray(6) { cx + R * cos(rot + it * PI.toFloat() / 3) }
            val sy = FloatArray(6) { cy + R * sin(rot + it * PI.toFloat() / 3) }
            c.save(); c.clipPath(forme)
            lamelles.reset()
            lamelles.addRect(pan.left - 10 * k, pan.top - 10 * k, pan.right + 10 * k, pan.bottom + 10 * k, Path.Direction.CW)
            lamelles.moveTo(sx[0], sy[0]); for (i in 1 until 6) lamelles.lineTo(sx[i], sy[i]); lamelles.close()
            val fonce = Color.rgb((Color.red(te.bas) * 0.55f).toInt(), (Color.green(te.bas) * 0.55f).toInt(), (Color.blue(te.bas) * 0.55f).toInt())
            pinceau.color = Teinte.avecAlpha(fonce, 0.97f * a * 255)
            c.drawPath(lamelles, pinceau)
            // le bord de chaque lamelle : il prolonge un côté de l'ouverture jusqu'au bord de la fenêtre
            pinceau.style = Paint.Style.STROKE
            pinceau.strokeWidth = 1.5f * k
            pinceau.color = Teinte.avecAlpha(te.clair, 0.22f * a * 255)
            for (i in 0 until 6) {
                val x1 = sx[i]; val y1 = sy[i]; val x2 = sx[(i + 1) % 6]; val y2 = sy[(i + 1) % 6]
                val l = max(1e-3f, hypot(x2 - x1, y2 - y1))
                c.drawLine(x2, y2, x2 + (x2 - x1) / l * 800 * k, y2 + (y2 - y1) / l * 800 * k, pinceau)
            }
            pinceau.style = Paint.Style.FILL
            c.restore()
        }
        // le viseur : 4 coins qui se resserrent sur le dossier, puis s'effacent
        val cv = seg(t, 0.05f, 0.35f)
        val av = cv * (1 - seg(t, 1.1f, 1.5f)) * (1 - fer)
        if (av > 0.01f) {
            val m = 24 * k + 30 * k * (1 - ressort(cv, 2f))
            val L = 34 * k
            pinceau.style = Paint.Style.STROKE
            pinceau.strokeWidth = 2.5f * k
            pinceau.strokeCap = Paint.Cap.ROUND
            pinceau.strokeJoin = Paint.Join.ROUND
            pinceau.color = Teinte.avecAlpha(te.clair, 0.85f * av * 255)
            val coin = Path()
            for ((x, y, sx, sy) in listOf(
                floatArrayOf(pan.left + m, pan.top + m, 1f, 1f), floatArrayOf(pan.right - m, pan.top + m, -1f, 1f),
                floatArrayOf(pan.left + m, pan.bottom - m, 1f, -1f), floatArrayOf(pan.right - m, pan.bottom - m, -1f, -1f))) {
                coin.moveTo(x, y + sy * L); coin.lineTo(x, y); coin.lineTo(x + sx * L, y)
            }
            c.drawPath(coin, pinceau)
            pinceau.style = Paint.Style.FILL
        }
    }

    // les apps se « développent » en diagonale : sombres et en noir et blanc d'abord, puis en couleur
    override fun arrivee(i: Int, t: Float): Arrivee {
        val d = 0.6f + 0.09f * (ligne(i) + colonne(i))
        val v = borne((t - d) / 0.5f)
        arr.alpha = borne(v * 2.5f)
        arr.echelle = 0.92f + 0.08f * freine(v)
        arr.penche = 0f; arr.decalY = 0f
        arr.gris = 1 - lisse(v)
        arr.nv = borne((t - (d + 0.4f)) / 0.3f)
        return arr
    }
}

// =====================================================================================================
// FINANCE : la courbe
// =====================================================================================================
private class EffetCourbe : EffetOuverture() {
    private var courbe: Trace? = null
    private var departCol = FloatArray(0)      // l'instant où la tête de la courbe dépasse chaque colonne
    private val chemin = Path()

    override fun preparer(c: CadreDossier) {
        super.preparer(c)
        val k = c.k; val pan = c.panneau
        val ys = floatArrayOf(0.93f, 0.80f, 0.85f, 0.63f, 0.70f, 0.44f, 0.50f, 0.22f)
        val px = FloatArray(ys.size) { pan.left + 26 * k + (pan.width() - 52 * k) * it / (ys.size - 1) }
        val py = FloatArray(ys.size) { pan.top + pan.height() * ys[it] }
        val tr = Trace.courbe(px, py, 14)
        courbe = tr
        departCol = FloatArray(c.colonnes) { col ->
            val x = c.appX[minOf(c.appX.size - 1, col)]
            var t = 0.25f
            while (t < 1.1f) {
                tr.partie(chemin, seg(t, 0.25f, 1.1f))
                if (tr.finX >= x - 15 * k) break
                t += 0.005f
            }
            t
        }
    }

    override fun avantApps(c: Canvas, t: Float, fer: Float, p: Float) {
        val cd = cadre ?: return
        val tr = courbe ?: return
        val k = cd.k; val pan = cd.panneau; val te = cd.teinte
        val prog = seg(t, 0.25f, 1.1f)
        val a = (0.4f + 0.6f * (1 - seg(t, 1.4f, 2.0f))) * (1 - fer) * p
        if (prog <= 0f || a <= 0f) return
        c.save(); c.clipPath(forme)
        // l'aire sous la courbe : un dégradé doux qui s'efface vers le bas
        tr.partie(chemin, prog)
        val fx = tr.finX; val fy = tr.finY
        chemin.lineTo(fx, pan.bottom); chemin.lineTo(tr.xs[0], pan.bottom); chemin.close()
        pinceau.shader = LinearGradient(0f, pan.top + pan.height() * 0.2f, 0f, pan.bottom,
            Teinte.avecAlpha(te.lueur, 0.22f * a * 255), Teinte.avecAlpha(te.lueur, 0f), Shader.TileMode.CLAMP)
        c.drawPath(chemin, pinceau)
        pinceau.shader = null
        // la courbe : un halo large et léger, puis le trait
        tr.partie(chemin, prog)
        pinceau.style = Paint.Style.STROKE
        pinceau.strokeCap = Paint.Cap.ROUND
        pinceau.strokeJoin = Paint.Join.ROUND
        for ((larg, al) in listOf(11f to 0.14f, 3.5f to 0.9f)) {
            pinceau.strokeWidth = larg * k
            pinceau.color = Teinte.avecAlpha(te.lueur, al * a * 255)
            c.drawPath(chemin, pinceau)
        }
        pinceau.style = Paint.Style.FILL
        if (prog < 1f) {
            pinceau.color = Teinte.avecAlpha(te.clair, a * 255)
            c.drawCircle(fx, fy, 5.5f * k, pinceau)
        }
        c.restore()
    }

    // les apps d'une colonne montent quand la courbe la dépasse (de bas en haut, avec un petit décalage par rangée)
    override fun arrivee(i: Int, t: Float): Arrivee {
        val cd = cadre
        val col = colonne(i)
        val lignes = cd?.lignes ?: 3
        val ti = (if (col < departCol.size) departCol[col] else 1.1f) + 0.07f * (lignes - 1 - ligne(i))
        val v = borne((t - ti) / 0.45f)
        arr.alpha = borne(v / 0.3f)
        arr.echelle = 0.9f + 0.1f * ressort(v, 2f)
        arr.penche = 0f
        arr.decalY = 45f * (cd?.k ?: 1f) * (1 - freine(v))
        arr.gris = 0f
        arr.nv = borne((t - (ti + 0.4f)) / 0.3f)
        return arr
    }
}
