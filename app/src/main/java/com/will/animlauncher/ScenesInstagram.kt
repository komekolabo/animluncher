package com.will.animlauncher

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import java.util.Random
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// INSTAGRAM, animation « Défilement sans fin » (Reels).
// Des vidéos verticales défilent vers le haut par SWIPES nets (une pause, puis un glissement sec).
// Chaque swipe est un peu plus court que le précédent : le rythme s'accélère jusqu'à devenir
// frénétique. Un compteur « REELS WATCHED » s'emballe. Quand l'app est prête, le swipe SUIVANT
// (au même rythme) amène l'app à la place de la vidéo suivante : la bande part par le haut.
//
// Les vidéos sont 8 petites scènes détaillées (lanternes, rue néon, plage, aurore, lac, feu de
// camp, fond marin, voie lactée). Leur DÉCOR fixe est dessiné une seule fois, en arrière-plan,
// dans des images (Bitmap) : voir FondsReels. À chaque image de l'animation, on ne redessine que
// ce qui bouge (lanternes, pluie, vagues, flammes…).

// =====================================================================================
// Petits outils de dessin partagés par les deux animations Instagram (préfixe « ig »).
// =====================================================================================

internal fun igPinceau(couleur: Int = Color.BLACK, trait: Float? = null): Paint {
    val p = Paint(Paint.ANTI_ALIAS_FLAG); p.color = couleur
    if (trait != null) { p.style = Paint.Style.STROKE; p.strokeWidth = trait; p.strokeCap = Paint.Cap.ROUND }
    return p
}

internal fun igMelange(a: Int, b: Int, t: Float): Int = rgb(
    mix(Color.red(a).toFloat(), Color.red(b).toFloat(), t).toInt().coerceIn(0, 255),
    mix(Color.green(a).toFloat(), Color.green(b).toFloat(), t).toInt().coerceIn(0, 255),
    mix(Color.blue(a).toFloat(), Color.blue(b).toFloat(), t).toInt().coerceIn(0, 255))

/** Remplit un rectangle avec un dégradé vertical (plusieurs couleurs possibles). */
internal fun igDegrade(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, couleurs: IntArray, pos: FloatArray? = null) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(0f, y0, 0f, y1, couleurs, pos, Shader.TileMode.CLAMP)
    c.drawRect(x0, y0, x1, y1, p)
}

/** Un polygone plein, à partir d'une liste de points x, y, x, y… */
internal fun igPoly(c: Canvas, couleur: Int, vararg pts: Float) {
    val p = Path(); p.moveTo(pts[0], pts[1])
    var i = 2
    while (i < pts.size) { p.lineTo(pts[i], pts[i + 1]); i += 2 }
    p.close(); c.drawPath(p, igPinceau(couleur))
}

/** Une lueur ronde et douce, en mode « écran » (elle éclaircit sans jamais saturer en blanc). */
internal fun igLueur(c: Canvas, x: Float, y: Float, r: Float, couleur: Int, a: Float) {
    if (r <= 0.5f || a <= 0.003f) return
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    p.shader = RadialGradient(x, y, r, intArrayOf(avecAlpha(couleur, a), avecAlpha(couleur, a * 0.35f), Color.TRANSPARENT),
        floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
    c.drawCircle(x, y, r, p)
}

/** Un pinceau dont les bords sont flous (rayon en pixels). */
internal fun igFlou(rayon: Float, couleur: Int): Paint =
    igPinceau(couleur).apply { maskFilter = BlurMaskFilter(max(0.5f, rayon), BlurMaskFilter.Blur.NORMAL) }

/** Une ligne de crête (montagnes, dunes) : une hauteur pour chacun des n points. */
internal fun igCrete(n: Int, graine: Long, base: Float, ampl: Float, rugo: Float = 0.5f, octaves: Int = 6): FloatArray {
    val g = Random(graine)
    val ys = FloatArray(n); var amp = 1f; var tot = 0f
    for (o in 0 until octaves) {
        val m = 2 * (1 shl o) + 1
        val pts = FloatArray(m) { g.nextFloat() }
        for (x in 0 until n) {
            val f = x.toFloat() / (n - 1) * (m - 1)
            val i0 = min(m - 2, floor(f).toInt()); val t = f - i0
            ys[x] += (pts[i0] + (pts[i0 + 1] - pts[i0]) * t) * amp
        }
        tot += amp; amp *= rugo
    }
    for (x in 0 until n) ys[x] = base - (ys[x] / tot - 0.5f) * 2f * ampl
    return ys
}

/** Remplit sous une ligne de crête (largeur = nombre de points), avec un dégradé vertical. */
internal fun igRemplirCrete(c: Canvas, ys: FloatArray, largeur: Float, haut: Int, bas: Int, yBas: Float): Path {
    val p = Path(); p.moveTo(0f, yBas)
    val dx = largeur / (ys.size - 1)
    var ymin = Float.MAX_VALUE
    for (x in ys.indices) { p.lineTo(x * dx, ys[x]); ymin = min(ymin, ys[x]) }
    p.lineTo(largeur, yBas); p.close()
    val q = Paint(Paint.ANTI_ALIAS_FLAG)
    q.shader = LinearGradient(0f, ymin, 0f, yBas, haut, bas, Shader.TileMode.CLAMP)
    c.drawPath(p, q)
    return p
}

/** Des étoiles fixes (toujours les mêmes pour une même graine). */
internal fun igEtoiles(c: Canvas, n: Int, graine: Long, largeur: Float, hmax: Float, sec: Float, taille: Float = 1f) {
    val r = Random(graine)
    val tailles = floatArrayOf(0.6f, 0.7f, 0.8f, 1.1f, 1.5f)
    for (i in 0 until n) {
        val x = r.nextFloat() * largeur; val y = r.nextFloat().pow(1.4f) * hmax
        val vit = 1f + 3f * r.nextFloat()
        val a = (0.25f + 0.65f * r.nextFloat()) * (0.75f + 0.25f * sin(sec * vit + i))
        val s = tailles[r.nextInt(5)] * taille
        c.drawCircle(x, y, s, igPinceau(avecAlpha(rgb(230, 230, 255), a)))
        if (s > 1.2f) igLueur(c, x, y, 5f, rgb(200, 210, 255), 0.25f * a)
    }
}

/** Un sapin simple (des triangles empilés). */
internal fun igSapinSimple(c: Canvas, x: Float, base: Float, haut: Float, larg: Float, couleur: Int) {
    val p = Path()
    for (e in 0 until 6) {
        val f = e / 6f; val yb = base - haut * f * 0.9f; val l = larg * (1f - f)
        p.moveTo(x - l, yb); p.lineTo(x, yb - haut * 0.3f); p.lineTo(x + l, yb); p.close()
    }
    p.addRect(x - larg * 0.06f, base - haut * 0.05f, x + larg * 0.06f, base + 2f, Path.Direction.CW)
    p.fillType = Path.FillType.WINDING
    c.drawPath(p, igPinceau(couleur))
}

/** Un bruit « nuageux » (fractal) : w x h valeurs entre 0 et 1. */
internal fun igFbm(w: Int, h: Int, graine: Long, base: Int = 3, octaves: Int = 6, persist: Float = 0.55f): FloatArray {
    val g = Random(graine)
    val out = FloatArray(w * h); var amp = 1f
    for (o in 0 until octaves) {
        val gw = base * (1 shl o) + 1
        val gh = max(2, (base * (1 shl o) * h / w)) + 1
        val grille = FloatArray(gw * gh) { g.nextFloat() }
        for (y in 0 until h) {
            val fy = y.toFloat() / h * (gh - 1); val iy = min(gh - 2, fy.toInt()); var ty = fy - iy; ty = ty * ty * (3 - 2 * ty)
            for (x in 0 until w) {
                val fx = x.toFloat() / w * (gw - 1); val ix = min(gw - 2, fx.toInt()); var tx = fx - ix; tx = tx * tx * (3 - 2 * tx)
                val a = grille[iy * gw + ix]; val b = grille[iy * gw + ix + 1]
                val cc = grille[(iy + 1) * gw + ix]; val d = grille[(iy + 1) * gw + ix + 1]
                out[y * w + x] += (mix(mix(a, b, tx), mix(cc, d, tx), ty)) * amp
            }
        }
        amp *= persist
    }
    var mn = Float.MAX_VALUE; var mx = -Float.MAX_VALUE
    for (v in out) { mn = min(mn, v); mx = max(mx, v) }
    for (i in out.indices) out[i] = (out[i] - mn) / max(1e-6f, mx - mn)
    return out
}

/** Couleur d'un dégradé à plusieurs étapes, à la position t (0 à 1). */
internal fun igCouleurDegrade(couleurs: IntArray, pos: FloatArray, t: Float): Int {
    if (t <= pos[0]) return couleurs[0]
    for (i in 1 until pos.size) if (t <= pos[i]) return igMelange(couleurs[i - 1], couleurs[i], (t - pos[i - 1]) / (pos[i] - pos[i - 1]))
    return couleurs.last()
}

/** Un cœur (pour les petits cœurs qui montent). */
internal fun igCoeur(c: Canvas, x: Float, y: Float, s: Float, couleur: Int) {
    val p = Path()
    p.moveTo(x, y + s * 0.9f)
    p.cubicTo(x - s * 1.6f, y - s * 0.2f, x - s * 0.7f, y - s * 1.3f, x, y - s * 0.45f)
    p.cubicTo(x + s * 0.7f, y - s * 1.3f, x + s * 1.6f, y - s * 0.2f, x, y + s * 0.9f)
    c.drawPath(p, igPinceau(couleur))
}

internal val IG_VIOLET = rgb(110, 50, 170)
internal val IG_ROSE = rgb(220, 60, 130)
internal val IG_ORANGE = rgb(250, 120, 60)

/** La taille de l'écran en mode portrait (largeur, hauteur), lisible même sans activité. */
internal fun igEcranPortrait(): Pair<Int, Int> {
    val m = Resources.getSystem().displayMetrics
    return Pair(min(m.widthPixels, m.heightPixels), max(m.widthPixels, m.heightPixels))
}

// =====================================================================================
// LES DÉCORS DES 8 VIDÉOS, dessinés une seule fois (dans un fil à part, au démarrage du service).
// On dessine dans un espace « modèle » de 432 x 768 (format 9:16) puis on agrandit.
// =====================================================================================
object FondsReels {
    const val L0 = 432f
    const val H0 = 768f
    private val fonds = arrayOfNulls<Bitmap>(8)
    @Volatile private var lance = false

    fun preparer() {
        synchronized(this) { if (lance) return; lance = true }
        thread(name = "fonds-reels", priority = Thread.MIN_PRIORITY) {
            try {
                val (w, _) = igEcranPortrait()
                // la vidéo fait 80 % de la largeur de l'écran ; on la dessine aux 3/4 de sa taille
                // réelle (moins de mémoire, et l'agrandissement reste net : les décors sont doux)
                val e = max(1f, w * 0.8f / L0 * 0.75f)
                val bw = (L0 * e).toInt(); val bh = (H0 * e).toInt()
                for (i in 0 until 8) {
                    val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                    val c = Canvas(b); c.scale(e, e)
                    when (i) {
                        0 -> fondLanternes(c); 1 -> fondNeon(c); 2 -> fondPlage(c); 3 -> fondAurore(c)
                        4 -> fondLac(c, e); 5 -> fondFeu(c); 6 -> fondMer(c); else -> fondDesert(c)
                    }
                    fonds[i] = b
                }
            } catch (_: Throwable) { /* pas assez de mémoire : les vidéos garderont un fond simple */ }
        }
    }

    fun fond(i: Int): Bitmap? = fonds[((i % 8) + 8) % 8]

    /** Une petite image de bruit colorée, agrandie en douceur (pour nuages, Voie lactée…). */
    private fun imagePixels(w: Int, h: Int, couleur: (Int, Int) -> Int): Bitmap {
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) px[y * w + x] = couleur(x, y)
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
    private val lisse = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    // ---------- 1. Lanternes sur la rivière ----------
    private fun fondLanternes(c: Canvas) {
        igDegrade(c, 0f, 0f, L0, H0, intArrayOf(rgb(8, 10, 30), rgb(30, 20, 60), rgb(110, 50, 70), rgb(40, 25, 50)), floatArrayOf(0f, 0.35f, 0.56f, 1f))
        val n = igFbm(54, 96, 3, base = 2)
        c.drawBitmap(imagePixels(54, 96) { x, y -> Color.argb((n[y * 54 + x] * 70 * (1f - y / 96f * 0.8f)).toInt(), 70, 45, 80) }, null, RectF(0f, 0f, L0, H0), lisse)
        igEtoiles(c, 90, 2, L0, H0 * 0.4f, 0f)
        igRemplirCrete(c, igCrete(L0.toInt(), 5, H0 * 0.55f, 18f), L0, rgb(40, 28, 55), rgb(30, 20, 40), H0 * 0.6f)
        val r = Random(4); var x = 0f
        while (x < L0) {                         // la ville au loin, quelques fenêtres allumées
            val bw = 10f + 16f * r.nextFloat(); val bh = 8f + 32f * r.nextFloat()
            c.drawRect(x, H0 * 0.585f - bh, x + bw, H0 * 0.6f, igPinceau(rgb(22, 15, 32)))
            for (i in 0 until (bh / 6).toInt()) if (r.nextFloat() < 0.3f) {
                val fx = x + 2f + (bw - 6f) * r.nextFloat()
                c.drawRect(fx, H0 * 0.585f - bh + 3 + i * 6, fx + 2f, H0 * 0.585f - bh + 5 + i * 6, igPinceau(rgb(255, 190, 120)))
            }
            x += bw + 6f * r.nextFloat()
        }
        igDegrade(c, 0f, H0 * 0.6f, L0, H0, intArrayOf(rgb(70, 35, 60), rgb(20, 14, 35), rgb(8, 8, 18)), floatArrayOf(0f, 0.4f, 1f))
    }

    // ---------- 2. Rue néon sous la pluie ----------
    private fun fondNeon(c: Canvas) {
        igDegrade(c, 0f, 0f, L0, H0, intArrayOf(rgb(10, 8, 25), rgb(35, 20, 55), rgb(20, 12, 30), rgb(8, 6, 14)), floatArrayOf(0f, 0.45f, 0.62f, 1f))
        val vx = L0 * 0.5f; val vy = H0 * 0.5f                       // le point de fuite
        val fen = intArrayOf(rgb(255, 200, 130), rgb(130, 200, 255), rgb(255, 140, 180))
        for (cote in intArrayOf(-1, 1)) for (i in 0 until 7) {     // les façades, en perspective
            val x0 = vx + cote * (L0 * 0.08f + i * L0 * 0.07f); val x1 = vx + cote * (L0 * 0.08f + (i + 1) * L0 * 0.07f)
            val haut = vy - (H0 * 0.08f + i * H0 * 0.05f) * (1f + (i % 3) * 0.3f)
            val bas = vy + (H0 * 0.02f + i * H0 * 0.035f)
            val g = 14 + i * 3
            val p = Path(); p.moveTo(x0, vy - H0 * 0.06f * (1 + i * 0.6f)); p.lineTo(x1, haut - H0 * 0.1f * (i % 2))
            p.lineTo(x1, bas + H0 * 0.03f); p.lineTo(x0, bas); p.close()
            c.drawPath(p, igPinceau(rgb(g, g - 3, g + 8)))
            val r = Random((i * 7 + cote).toLong())
            for (f in 0 until 10) {
                val fy = mix(haut, bas, r.nextFloat()); val fx = mix(x0, x1, r.nextFloat())
                if (r.nextFloat() < 0.45f) c.drawRect(fx, fy, fx + 3 + i, fy + 3 + i * 0.7f, igPinceau(avecAlpha(fen[r.nextInt(3)], 0.7f)))
            }
        }
        val route = Path(); route.moveTo(vx - 4, vy + 6); route.lineTo(vx + 4, vy + 6); route.lineTo(L0 * 1.1f, H0); route.lineTo(-L0 * 0.1f, H0); route.close()
        c.drawPath(route, igPinceau(rgb(12, 10, 20)))
        for (i in 0 until 10) {                                     // le marquage au sol
            val t1 = (i / 10f).pow(2); val t2 = ((i + 0.5f) / 10f).pow(2)
            c.drawLine(vx, vy + 6 + t1 * H0 * 0.5f, vx, vy + 6 + t2 * H0 * 0.5f, igPinceau(avecAlpha(rgb(200, 200, 210), 0.25f), 1f + t1 * 6f))
        }
        igLueur(c, vx, vy, L0 * 0.4f, rgb(120, 60, 140), 0.35f)    // la brume au loin
    }

    // ---------- 3. Plage à l'heure dorée ----------
    private fun fondPlage(c: Canvas) {
        igDegrade(c, 0f, 0f, L0, H0, intArrayOf(rgb(40, 50, 110), rgb(190, 110, 120), rgb(255, 170, 100), rgb(255, 200, 130)), floatArrayOf(0f, 0.3f, 0.47f, 0.52f))
        // les nuages : du bruit, gardé seulement dans une bande du ciel, éclairés par en dessous
        val nw = 108; val nh = 192
        val n = igFbm(nw, nh, 11, base = 3)
        val cn = intArrayOf(rgb(90, 60, 110), rgb(255, 150, 120), rgb(255, 200, 150)); val pn = floatArrayOf(0f, 0.3f, 0.5f)
        c.drawBitmap(imagePixels(nw, nh) { x, y ->
            val v = y.toFloat() / nh
            val a = borne((n[y * nw + x] - 0.52f) * 3.5f) * borne(1f - abs(v - 0.27f) / 0.22f) * 0.85f
            val col = igCouleurDegrade(cn, pn, v)
            Color.argb((a * 255).toInt(), Color.red(col), Color.green(col), Color.blue(col))
        }, null, RectF(0f, 0f, L0, H0), lisse)
        igLueur(c, L0 * 0.5f, H0 * 0.47f, L0 * 0.6f, rgb(255, 180, 110), 0.5f)
        c.drawCircle(L0 * 0.5f, H0 * 0.465f, 26f, igPinceau(rgb(255, 225, 170)))
        igDegrade(c, 0f, H0 * 0.48f, L0, H0 * 0.68f, intArrayOf(rgb(200, 110, 110), rgb(60, 60, 110), rgb(30, 40, 80)), floatArrayOf(0f, 0.4f, 1f))
        igDegrade(c, 0f, H0 * 0.68f, L0, H0, intArrayOf(rgb(150, 100, 110), rgb(200, 140, 110), rgb(120, 80, 70)), floatArrayOf(0f, 0.3f, 1f))
        val ombre = rgb(30, 18, 25)                                 // un palmier en silhouette
        val tr = Path(); tr.moveTo(L0 * 0.08f, H0); tr.cubicTo(L0 * 0.12f, H0 * 0.8f, L0 * 0.1f, H0 * 0.6f, L0 * 0.22f, H0 * 0.42f)
        c.drawPath(tr, igPinceau(ombre, 9f))
        for (a in intArrayOf(-170, -140, -110, -60, -25, 10, 40)) {
            val x0 = L0 * 0.22f; val y0 = H0 * 0.42f
            val ar = a * PI.toFloat() / 180f
            val ex = x0 + cos(ar) * 110f; val ey = y0 + sin(ar) * 60f + 50f
            val f = Path(); f.moveTo(x0, y0); f.quadTo((x0 + ex) / 2, (y0 + ey) / 2 - 40, ex, ey); f.quadTo((x0 + ex) / 2, (y0 + ey) / 2 - 25, x0, y0)
            c.drawPath(f, igPinceau(ombre))
        }
    }

    // ---------- 4. Aurore boréale ----------
    private fun fondAurore(c: Canvas) {
        igDegrade(c, 0f, 0f, L0, H0, intArrayOf(rgb(4, 8, 22), rgb(10, 25, 45), rgb(20, 40, 60)), floatArrayOf(0f, 0.6f, 1f))
        igEtoiles(c, 160, 7, L0, H0 * 0.7f, 0f)
        igRemplirCrete(c, igCrete(L0.toInt(), 21, H0 * 0.66f, 30f), L0, rgb(60, 80, 110), rgb(30, 45, 70), H0)
        igRemplirCrete(c, igCrete(L0.toInt(), 22, H0 * 0.76f, 14f, 0.4f), L0, rgb(170, 190, 220), rgb(60, 75, 110), H0)
        val r = Random(3)
        for (i in 0 until 22) {
            val x = r.nextFloat() * L0; val b = H0 * (0.74f + r.nextFloat() * 0.1f)
            igSapinSimple(c, x, b, 50f + 60f * r.nextFloat(), 12f + 8f * r.nextFloat(), rgb(8, 14, 22))
        }
    }

    // ---------- 5. Lac de montagne à l'aube (avec son reflet) ----------
    private fun fondLac(c: Canvas, e: Float) {
        val hh = H0 * 0.55f
        val haut = Bitmap.createBitmap((L0 * e).toInt(), (hh * e).toInt(), Bitmap.Config.ARGB_8888)
        val ch = Canvas(haut); ch.scale(e, e)
        igDegrade(ch, 0f, 0f, L0, hh, intArrayOf(rgb(40, 50, 100), rgb(160, 120, 160), rgb(250, 170, 140)), floatArrayOf(0f, 0.6f, 1f))
        igLueur(ch, L0 * 0.6f, H0 * 0.52f, L0 * 0.5f, rgb(255, 190, 150), 0.4f)
        val couches = arrayOf(intArrayOf(150, 120, 160), intArrayOf(110, 85, 130), intArrayOf(70, 55, 95), intArrayOf(35, 30, 55))
        val bases = floatArrayOf(0.36f, 0.42f, 0.48f, 0.53f); val amps = floatArrayOf(60f, 50f, 40f, 25f)
        for (n in 0 until 4) {
            val col = couches[n]
            val ys = igCrete(L0.toInt(), (30 + n).toLong(), H0 * bases[n], amps[n], 0.55f)
            igRemplirCrete(ch, ys, L0, rgb(col[0], col[1], col[2]), rgb((col[0] * 0.85f).toInt(), (col[1] * 0.85f).toInt(), (col[2] * 0.85f).toInt()), hh)
            if (n == 0) {   // de la neige sur les sommets les plus hauts
                val p = Path(); p.moveTo(0f, ys[0])
                for (x in ys.indices) p.lineTo(x.toFloat(), ys[x])
                for (x in ys.indices.reversed()) p.lineTo(x.toFloat(), max(ys[x], H0 * bases[n] - amps[n] * 0.4f))
                p.close(); ch.drawPath(p, igPinceau(rgb(235, 210, 225)))
            }
        }
        val r = Random(8)
        for (i in 0 until 30) igSapinSimple(ch, r.nextFloat() * L0, hh, 25f + 35f * r.nextFloat(), 7f + 5f * r.nextFloat(), rgb(18, 16, 30))
        c.drawBitmap(haut, null, RectF(0f, 0f, L0, hh), lisse)
        // le reflet : la même image, retournée, rendue floue (on la rétrécit puis on la ré-agrandit)
        val petit = Bitmap.createScaledBitmap(haut, max(2, haut.width / 6), max(2, haut.height / 10), true)
        c.save(); c.translate(0f, H0 * 1.1f); c.scale(1f, -1f)
        c.drawBitmap(petit, null, RectF(0f, 0f, L0, hh), lisse)
        c.restore()
        c.drawRect(0f, hh, L0, H0, igPinceau(avecAlpha(rgb(15, 15, 35), 0.45f)))
    }

    // ---------- 6. Feu de camp ----------
    private fun fondFeu(c: Canvas) {
        igDegrade(c, 0f, 0f, L0, H0, intArrayOf(rgb(4, 5, 16), rgb(14, 14, 34), rgb(25, 18, 30)), floatArrayOf(0f, 0.55f, 1f))
        igEtoiles(c, 140, 4, L0, H0 * 0.55f, 0f)
        val r = Random(6)
        for (i in 0 until 26) igSapinSimple(c, r.nextFloat() * L0, H0 * (0.6f + r.nextFloat() * 0.04f), 120f + 110f * r.nextFloat(), 25f + 15f * r.nextFloat(), rgb(6, 6, 12))
        c.drawRect(0f, H0 * 0.62f, L0, H0, igPinceau(rgb(14, 10, 12)))
        val tente = Path(); tente.moveTo(L0 * 0.06f, H0 * 0.78f); tente.lineTo(L0 * 0.3f, H0 * 0.58f); tente.lineTo(L0 * 0.52f, H0 * 0.78f); tente.close()
        val q = Paint(Paint.ANTI_ALIAS_FLAG)
        q.shader = RadialGradient(L0 * 0.3f, H0 * 0.74f, 110f, rgb(255, 160, 70), rgb(130, 60, 30), Shader.TileMode.CLAMP)
        c.drawPath(tente, q)
        igPoly(c, rgb(255, 210, 130), L0 * 0.26f, H0 * 0.78f, L0 * 0.3f, H0 * 0.66f, L0 * 0.34f, H0 * 0.78f)
        c.drawLine(L0 * 0.56f, H0 * 0.86f, L0 * 0.84f, H0 * 0.83f, igPinceau(rgb(50, 28, 20), 12f))    // les bûches
        c.drawLine(L0 * 0.58f, H0 * 0.83f, L0 * 0.82f, H0 * 0.87f, igPinceau(rgb(50, 28, 20), 12f))
    }

    // ---------- 7. Fond marin ----------
    private fun fondMer(c: Canvas) {
        igDegrade(c, 0f, 0f, L0, H0, intArrayOf(rgb(30, 140, 170), rgb(10, 70, 120), rgb(4, 20, 50), rgb(2, 8, 22)), floatArrayOf(0f, 0.3f, 0.7f, 1f))
        igRemplirCrete(c, igCrete(L0.toInt(), 41, H0 * 0.86f, 30f, 0.6f), L0, rgb(10, 30, 45), rgb(4, 10, 20), H0)
        val r = Random(4)
        for (i in 0 until 14) {                                       // algues en silhouette
            val x = r.nextFloat() * L0; val b = H0 * (0.86f + r.nextFloat() * 0.08f); val hh = 40f + 70f * r.nextFloat()
            val p = Path(); p.moveTo(x, b); p.cubicTo(x - 15, b - hh * 0.4f, x + 15, b - hh * 0.7f, x + 20f * r.nextFloat() - 10f, b - hh)
            c.drawPath(p, igPinceau(rgb(8, 25, 35), 3f + 4f * r.nextFloat()))
        }
    }

    // ---------- 8. Voie lactée sur le désert ----------
    private fun fondDesert(c: Canvas) {
        val nw = 216; val nh = 384
        val n = igFbm(nw, nh, 51, base = 4, octaves = 7)
        val cc = intArrayOf(rgb(3, 4, 14), rgb(12, 12, 35), rgb(40, 30, 60)); val pc = floatArrayOf(0f, 0.6f, 0.75f)
        c.drawBitmap(imagePixels(nw, nh) { x, y ->
            val xm = x * L0 / nw; val ym = y * H0 / nh
            val d = abs((xm - L0 * 0.15f) * 0.8f - (ym - H0 * 0.05f) * 0.45f) / (L0 * 0.25f)
            val nv = n[y * nw + x]
            val bande = borne(1f - d).pow(1.5f) * borne((nv - 0.35f) * 2f)       // la bande d'étoiles
            val poussiere = borne((0.5f - nv) * 3f) * borne(1f - d * 2.5f)        // ses nuages sombres
            val base = igCouleurDegrade(cc, pc, ym / H0)
            val f = 1f - poussiere * 0.5f
            rgb(((Color.red(base) + bande * 120f) * f).toInt().coerceIn(0, 255), ((Color.green(base) + bande * 100f) * f).toInt().coerceIn(0, 255),
                ((Color.blue(base) + bande * 150f) * f).toInt().coerceIn(0, 255))
        }, null, RectF(0f, 0f, L0, H0), lisse)
        igEtoiles(c, 300, 9, L0, H0 * 0.72f, 0f, 0.9f)
        igRemplirCrete(c, igCrete(L0.toInt(), 61, H0 * 0.72f, 25f, 0.35f, 4), L0, rgb(70, 55, 80), rgb(40, 30, 50), H0)
        igRemplirCrete(c, igCrete(L0.toInt(), 62, H0 * 0.82f, 30f, 0.3f, 4), L0, rgb(50, 38, 58), rgb(20, 15, 25), H0)
        val noir = rgb(10, 8, 14)                                     // un arbre solitaire
        c.drawLine(L0 * 0.72f, H0 * 0.8f, L0 * 0.73f, H0 * 0.7f, igPinceau(noir, 5f))
        for ((dx, dy) in listOf(Pair(-30f, -40f), Pair(25f, -45f), Pair(-10f, -55f), Pair(35f, -25f))) {
            c.drawLine(L0 * 0.73f, H0 * 0.71f, L0 * 0.73f + dx, H0 * 0.71f + dy, igPinceau(noir, 3f))
            c.drawOval(L0 * 0.73f + dx - 22, H0 * 0.71f + dy - 10, L0 * 0.73f + dx + 22, H0 * 0.71f + dy + 4, igPinceau(noir))
        }
    }
}

// =====================================================================================
// CE QUI BOUGE dans chaque vidéo (dessiné à chaque image, dans l'espace modèle 432 x 768).
// =====================================================================================
private object AnimsReels {
    const val L0 = FondsReels.L0
    const val H0 = FondsReels.H0

    private val lanternes = Random(1).let { r -> List(34) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.5f + 0.5f * r.nextFloat(), r.nextFloat() * 6f) } }

    fun dessiner(c: Canvas, idx: Int, sec: Float) {
        when (((idx % 8) + 8) % 8) {
            0 -> lanternes(c, sec); 1 -> neon(c, sec); 2 -> plage(c, sec); 3 -> aurore(c, sec)
            4 -> lac(c, sec); 5 -> feu(c, sec); 6 -> mer(c, sec); else -> desert(c, sec)
        }
    }

    private fun lanternes(c: Canvas, sec: Float) {
        for (l in lanternes) {
            val u = l[0]; val v = l[1]; val t = l[2]; val ph = l[3]
            val z = 0.35f + 0.65f * t                                // les proches sont plus grandes
            val y = H0 * (0.62f - modulo(v + sec * 0.05f * z, 1f) * 0.6f)
            val x = L0 * u + sin(sec * 0.8f + ph) * 6f * z
            val s = 7f * z
            val fl = 0.85f + 0.15f * sin(sec * 6f + ph * 3f)
            igLueur(c, x, y, s * 5f, rgb(255, 150, 60), 0.35f * fl)
            val p = Path(); p.moveTo(x - s * 0.75f, y - s); p.lineTo(x + s * 0.75f, y - s)
            p.lineTo(x + s * 0.55f, y + s * 0.9f); p.lineTo(x - s * 0.55f, y + s * 0.9f); p.close()
            val q = Paint(Paint.ANTI_ALIAS_FLAG)
            q.shader = LinearGradient(0f, y - s, 0f, y + s, rgb(255, 120, 50), rgb(255, 225, 150), Shader.TileMode.CLAMP)
            c.drawPath(p, q)
            c.drawRect(x - s * 0.55f, y + s * 0.8f, x + s * 0.55f, y + s * 0.95f, igPinceau(rgb(120, 50, 30)))
            val ry = H0 * 0.6f + (H0 * 0.6f - y) * 0.35f             // son reflet dans l'eau, qui ondule
            if (ry < H0) for (i in 0 until 4) {
                val o = sin(sec * 3f + i + ph) * 2f
                c.drawLine(x - s * 0.5f + o, ry + i * 5f * z, x + s * 0.5f + o, ry + i * 5f * z,
                    igPinceau(avecAlpha(rgb(255, 170, 90), 0.35f * (1f - i / 4f) * fl), 1.6f * z))
            }
        }
        val r = Random(9)
        for (i in 0 until 40) {                                      // vaguelettes sur la rivière
            val y = H0 * (0.62f + r.nextFloat() * 0.38f); val x = r.nextFloat() * L0 + sin(sec + i) * 5f
            c.drawLine(x, y, x + 6f + 12f * r.nextFloat(), y, igPinceau(avecAlpha(rgb(200, 120, 140), 0.18f), 1f))
        }
    }

    private val neons = arrayOf(
        floatArrayOf(255f, 60f, 150f, 0.1f, 0.30f, 0.05f, 0.14f), floatArrayOf(60f, 220f, 255f, 0.82f, 0.25f, 0.07f, 0.09f),
        floatArrayOf(255f, 170f, 60f, 0.12f, 0.47f, 0.06f, 0.05f), floatArrayOf(150f, 90f, 255f, 0.86f, 0.43f, 0.05f, 0.1f),
        floatArrayOf(60f, 255f, 180f, 0.2f, 0.58f, 0.04f, 0.035f), floatArrayOf(255f, 80f, 90f, 0.74f, 0.56f, 0.05f, 0.04f))

    private fun neon(c: Canvas, sec: Float) {
        for ((i, n) in neons.withIndex()) {                         // les enseignes (sans texte) et leurs reflets
            val col = rgb(n[0].toInt(), n[1].toInt(), n[2].toInt())
            val fl = if (i != 2 || sin(sec * 13f) > -0.6f) 1f else 0.4f      // une enseigne grésille
            val x = n[3] * L0; val y = n[4] * H0; val lw = n[5] * L0; val lh = n[6] * H0
            igLueur(c, x, y, max(lw, lh) * 2.2f, col, 0.45f * fl)
            val r = RectF(x - lw / 2, y - lh / 2, x + lw / 2, y + lh / 2)
            c.drawRoundRect(r, 3f, 3f, igPinceau(avecAlpha(col, 0.9f * fl), 2.5f))
            c.drawRoundRect(r, 3f, 3f, igPinceau(avecAlpha(Color.WHITE, 0.6f * fl), 0.8f))
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, H0 * 0.6f, 0f, H0, avecAlpha(col, 0f), avecAlpha(col, 0.35f * fl), Shader.TileMode.CLAMP)
            p.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
            val xr = L0 * 0.5f + (x - L0 * 0.5f) * 1.1f
            c.drawRect(xr - lw * 0.4f + sin(sec * 4f + i) * 2f, H0 * 0.6f, xr + lw * 0.4f, H0, p)
        }
        val r = Random(3)
        for (i in 0 until 110) {                                     // la pluie, en biais
            val vit = 0.8f + 0.4f * r.nextFloat()
            val x = modulo(r.nextFloat() * L0 + sec * 60f, L0); val y = modulo(r.nextFloat() * H0 + sec * 900f * vit, H0)
            val lg = 10f + 12f * r.nextFloat()
            c.drawLine(x, y, x - lg * 0.15f, y + lg, igPinceau(avecAlpha(rgb(190, 200, 255), 0.12f + 0.18f * r.nextFloat()), 0.8f))
        }
        val bok = arrayOf(floatArrayOf(0.15f, 0.82f), floatArrayOf(0.8f, 0.9f), floatArrayOf(0.55f, 0.95f))
        val cols = intArrayOf(rgb(255, 60, 150), rgb(60, 220, 255), rgb(255, 170, 60))
        for (i in 0 until 3) c.drawCircle(bok[i][0] * L0, bok[i][1] * H0, 24f, igFlou(7f, avecAlpha(cols[i], 0.22f + 0.05f * sin(sec * 2f + i))))
    }

    private fun plage(c: Canvas, sec: Float) {
        val r = Random(5)
        for (i in 0 until 70) {                                      // les paillettes du soleil sur l'eau
            val y = H0 * (0.49f + r.nextFloat().pow(1.5f) * 0.18f)
            val larg = (y - H0 * 0.48f) * 0.9f + 8f
            val x = L0 * 0.5f + (r.nextFloat() - 0.5f) * larg * 2f
            val a = 0.5f + 0.5f * sin(sec * 5f + i * 1.7f)
            c.drawLine(x, y, x + 3f + 6f * r.nextFloat(), y, igPinceau(avecAlpha(rgb(255, 220, 160), 0.7f * a), 1.3f))
        }
        for (j in 0 until 3) {                                       // les vagues qui s'étalent sur le sable
            val t = modulo(sec * 0.35f + j / 3f, 1f)
            val y = H0 * (0.68f + t * 0.2f)
            val p = Path(); p.moveTo(-10f, y)
            var x = 0f
            while (x <= L0 + 20f) { p.lineTo(x, y + sin(x * 0.03f + j * 2f + sec) * 5f); x += 20f }
            c.drawPath(p, igPinceau(avecAlpha(rgb(255, 235, 220), 0.55f * (1f - t)), 2.5f * (1f - t) + 0.5f))
        }
    }

    private fun aurore(c: Canvas, sec: Float) {
        // le rideau : de fins rayons verticaux le long d'une courbe qui ondule ; chaque rayon a sa propre
        // intensité, ce qui dessine les « plis » du rideau
        val p = Paint(); p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        val bases = floatArrayOf(0.36f, 0.28f); val amps = floatArrayOf(50f, 35f)
        val c1 = intArrayOf(rgb(60, 255, 150), rgb(40, 200, 255)); val c2 = intArrayOf(rgb(160, 80, 255), rgb(255, 80, 200))
        for (rideau in 0 until 2) {
            var i = 0
            while (i < L0.toInt()) {
                val u = i / L0
                val y = H0 * bases[rideau] + sin(u * 5f + sec * 0.8f + rideau * 2f) * amps[rideau] + sin(u * 13f - sec * 1.3f) * 10f
                val hgt = 70f + 80f * (0.5f + 0.5f * sin(u * 9f + sec * 1.1f + rideau))
                val a = 0.05f + 0.10f * (0.5f + 0.5f * sin(u * 37f + sec * 1.7f)) * (0.5f + 0.5f * sin(u * 11f - sec + rideau))
                p.shader = LinearGradient(0f, y + 8f, 0f, y - hgt,
                    intArrayOf(avecAlpha(c1[rideau], 0f), avecAlpha(c1[rideau], a), avecAlpha(c2[rideau], a * 0.4f), avecAlpha(c2[rideau], 0f)),
                    floatArrayOf(0f, 0.12f, 0.6f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(i.toFloat(), y - hgt, i + 3f, y + 8f, p)
                i += 3
            }
        }
        igLueur(c, L0 * 0.5f, H0 * 0.38f, L0 * 0.7f, rgb(60, 220, 160), 0.12f)
    }

    private fun lac(c: Canvas, sec: Float) {
        for (j in 0 until 3) {                                       // la brume qui glisse sur l'eau
            val x = modulo(sec * 20f * (j + 1) + j * 140f, L0 + 300f) - 150f
            c.drawOval(x - 120f, H0 * (0.53f + j * 0.03f), x + 120f, H0 * (0.57f + j * 0.03f), igFlou(18f, avecAlpha(rgb(230, 200, 220), 0.12f)))
        }
        val r = Random(2)
        for (i in 0 until 50) {                                      // des rides claires sur l'eau
            val y = H0 * (0.56f + r.nextFloat() * 0.44f); val x = r.nextFloat() * L0
            val a = 0.5f + 0.5f * sin(sec * 2f + i)
            c.drawLine(x, y, x + 10f + 30f * r.nextFloat(), y, igPinceau(avecAlpha(rgb(250, 200, 190), 0.12f * a), 1f))
        }
    }

    private fun feu(c: Canvas, sec: Float) {
        val fx = L0 * 0.7f; val fy = H0 * 0.85f
        val fl = 0.85f + 0.15f * sin(sec * 9f) * sin(sec * 5.3f)
        igLueur(c, fx, fy - 30f, 230f * fl, rgb(255, 120, 40), 0.35f)          // la lumière chaude sur la clairière
        val cols = intArrayOf(rgb(255, 90, 30), rgb(255, 160, 50), rgb(255, 230, 150))
        val lgs = floatArrayOf(34f, 24f, 12f); val hts = floatArrayOf(120f, 90f, 50f)
        for (j in 0 until 3) for (f in 0 until 3) {                    // les flammes : 3 couches de 3 langues
            val ph = sec * (7f + f) + f * 2f + j
            val hh = hts[j] * (0.8f + 0.2f * sin(ph)) * fl
            val lg = lgs[j]
            val x = fx + (f - 1) * lg * 0.5f
            val p = Path(); p.moveTo(x - lg / 2, fy)
            p.cubicTo(x - lg / 2, fy - hh * 0.5f, x + sin(ph * 1.3f) * 10f, fy - hh * 0.7f, x + sin(ph) * 8f, fy - hh)
            p.cubicTo(x + lg / 2 * 0.3f, fy - hh * 0.6f, x + lg / 2, fy - hh * 0.4f, x + lg / 2, fy); p.close()
            c.drawPath(p, igFlou(2f + j, avecAlpha(cols[j], 0.75f)))
        }
        val r = Random(1)
        for (i in 0 until 40) {                                      // des étincelles qui montent en tourbillonnant
            val dx = r.nextFloat(); val vit = 0.3f + 0.3f * r.nextFloat()
            val t = modulo(r.nextFloat() + sec * vit, 1f)
            val x = fx + (dx - 0.5f) * 40f + sin(t * 9f + i) * 20f * t
            val y = fy - 40f - t * H0 * 0.45f
            c.drawCircle(x, y, 1.3f, igPinceau(avecAlpha(rgb(255, 200, 110), 0.9f * (1f - t))))
            igLueur(c, x, y, 5f, rgb(255, 140, 50), 0.4f * (1f - t))
        }
    }

    private fun mer(c: Canvas, sec: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        p.shader = LinearGradient(0f, 0f, 0f, H0 * 0.8f, avecAlpha(rgb(180, 240, 255), 0.18f), avecAlpha(rgb(180, 240, 255), 0f), Shader.TileMode.CLAMP)
        for (i in 0 until 6) {                                       // rayons de lumière qui se balancent
            val x = L0 * (0.1f + i * 0.17f) + sin(sec * 0.6f + i) * 20f
            val ra = Path(); ra.moveTo(x - 14, 0f); ra.lineTo(x + 14, 0f); ra.lineTo(x + 70, H0 * 0.8f); ra.lineTo(x - 10, H0 * 0.8f); ra.close()
            c.drawPath(ra, p)
        }
        val meduses = arrayOf(floatArrayOf(0.32f, 0.38f, 1.0f, 0f), floatArrayOf(0.7f, 0.55f, 0.7f, 2f), floatArrayOf(0.55f, 0.22f, 0.5f, 4f))
        for (m in meduses) {                                          // méduses lumineuses qui pulsent
            val js = m[2]; val ph = m[3]
            val x = L0 * m[0] + sin(sec * 0.7f + ph) * 10f; val y = H0 * m[1] - modulo(sec * 12f + ph * 5f, 30f)
            val rr = 30f * js * (1f + 0.08f * sin(sec * 3f + ph))
            igLueur(c, x, y, rr * 3f, rgb(255, 120, 200), 0.3f)
            val cl = Paint(Paint.ANTI_ALIAS_FLAG)
            cl.shader = RadialGradient(x, y, rr, avecAlpha(rgb(255, 200, 240), 0.8f), avecAlpha(rgb(220, 100, 200), 0.35f), Shader.TileMode.CLAMP)
            c.drawArc(x - rr, y - rr * 0.8f, x + rr, y + rr * 0.8f, 180f, 180f, true, cl)
            for (t in 0 until 6) {
                val tx = x - rr * 0.7f + t * rr * 0.28f
                val q = Path(); q.moveTo(tx, y)
                q.cubicTo(tx + sin(sec * 2f + t) * 8f, y + rr, tx - sin(sec * 2f + t) * 8f, y + rr * 1.8f, tx + sin(sec * 1.5f + t) * 5f, y + rr * 2.6f * js + 20f)
                c.drawPath(q, igPinceau(avecAlpha(rgb(255, 170, 230), 0.4f), 1.2f).apply { strokeCap = Paint.Cap.BUTT })
            }
        }
        val r = Random(7)
        for (i in 0 until 30) {                                      // les bulles qui montent
            val vit = 0.08f + 0.12f * r.nextFloat()
            val t = modulo(r.nextFloat() + sec * vit, 1f)
            val x = r.nextFloat() * L0 + sin(t * 12f + i) * 6f; val y = H0 * (1f - t)
            c.drawCircle(x, y, 1.5f + 2.5f * r.nextFloat(), igPinceau(avecAlpha(rgb(200, 240, 255), 0.45f), 1f))
        }
    }

    private fun desert(c: Canvas, sec: Float) {
        val t = modulo(sec * 0.6f, 3f)                               // une étoile filante de temps en temps
        if (t < 0.5f) {
            val x = L0 * (0.2f + t * 0.9f); val y = H0 * (0.1f + t * 0.25f)
            val p = igPinceau(Color.WHITE, 1.5f)
            p.shader = LinearGradient(x, y, x - 60f, y - 30f, avecAlpha(Color.WHITE, 0.8f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawLine(x, y, x - 60f, y - 30f, p)
        }
        igEtoiles(c, 40, 99, L0, H0 * 0.6f, sec, 1.2f)               // quelques étoiles qui scintillent
    }
}

// =====================================================================================
// L'ANIMATION « Défilement sans fin »
// =====================================================================================
class Reels : Scene() {
    private var tRev = -1f                      // l'instant où l'app a été prête (début de la révélation)
    private var noeud: Any? = null              // pour le flou de mouvement (Android 12 et plus)
    private val coeurs = Random(4).let { r -> List(9) { floatArrayOf(r.nextFloat() * 0.6f - 0.3f, r.nextFloat(), 0.6f + 0.4f * r.nextFloat()) } }

    // LE RYTHME DES SWIPES : chaque swipe = une pause, puis un glissement. Chaque swipe dure 18 % de
    // moins que le précédent (x 0,82), et la part de pause diminue : ça s'accélère. On ne descend
    // pas sous 0,07 s par swipe (au-delà, l'œil ne suit plus).
    private fun duree(n: Int): Float = max(0.07f, 0.36f * 0.82f.pow(n))
    private fun glisse(n: Int): Float = min(0.92f, 0.45f + 0.05f * n) * duree(n)

    /** Combien de vidéos ont défilé à l'instant sec (avec des décimales pendant un glissement). */
    private fun defilement(sec: Float): Float {
        var debut = 0f; var n = 0
        while (n < 10000) {
            val d = duree(n); val g = glisse(n)
            if (sec < debut + d) return n + doux(borne((sec - debut - (d - g)) / g))
            debut += d; n++
        }
        return n.toFloat()
    }

    /** Le swipe qui amène l'app : le premier qui commence une fois l'app prête. (numéro, début du glissement, durée du glissement) */
    private fun swipeApp(t: Float): Triple<Int, Float, Float> {
        var debut = 0f; var n = 0
        while (debut < t - 0.02f && n < 10000) { debut += duree(n); n++ }
        return Triple(n, debut + duree(n) - glisse(n), glisse(n))
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val w = c.width.toFloat(); val h = c.height.toFloat(); val k = w / 540f
        val sec = i.secondes
        if (i.revelation > 0f && tRev < 0f) tRev = sec
        if (i.revelation >= 1f) return                       // tout est parti : l'app est entièrement visible

        val cl = w * 0.80f; val ch = cl * 16f / 9f; val ecart = 18f * k; val pas = ch + ecart
        val pousse = h / 2f + ch / 2f + ecart                // de combien l'app pousse la bande vers le haut

        // Où en est-on ? Avant le swipe de l'app : le rythme normal. Pendant : la bande est poussée.
        var u = 0f; var pos: Float; var v: Float
        val app = if (tRev >= 0f) swipeApp(tRev) else null
        if (app != null && sec >= app.second) {
            pos = app.first.toFloat()
            u = doux(borne((sec - app.second) / app.third))
            v = pousse / pas * (doux(borne((sec + 0.01f - app.second) / app.third)) - doux(borne((sec - 0.01f - app.second) / app.third))) / 0.02f
        } else {
            pos = defilement(sec)
            v = (defilement(sec + 0.01f) - defilement(max(0f, sec - 0.01f))) / 0.02f
        }

        // le fond sombre, avec une lueur aux couleurs d'Instagram qui monte avec l'emballement
        c.drawRect(0f, 0f, w, h, igPinceau(rgb(7, 6, 10)))
        val elan = borne(pos / 11f)
        o.tache(c, w / 2f, h * 0.5f, w * 0.8f, h * 0.45f, 0.18f * elan, IG_VIOLET)
        o.tache(c, w / 2f, h * 0.75f, w * 0.6f, h * 0.25f, 0.12f * elan, IG_ROSE)

        // LA BANDE DE VIDÉOS, inclinée en perspective (de plus en plus), floutée pendant les glissements rapides
        val pente = (0.03f + 0.12f * doux(elan)) * (1f - u)
        val m = Matrix()
        m.setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0,
            floatArrayOf(w * pente, h * 0.02f * elan, w * (1f - pente), h * 0.02f * elan, w * (1f + pente * 0.5f), h, -w * pente * 0.5f, h), 0, 4)
        val flou = min(55f * k, max(0f, v - 4f) * pas / 30f * 0.16f)
        val dessinerBande: (Canvas) -> Unit = { cc ->
            cc.save()
            cc.translate(0f, -u * pousse)
            cc.concat(m)
            val premiere = floor(pos).toInt() - 2
            for (n in premiere until premiere + 7) {
                val yc = h * 0.5f + (n - pos) * pas
                val r = RectF(w / 2f - cl / 2f, yc - ch / 2f, w / 2f + cl / 2f, yc + ch / 2f)
                if (r.bottom < -h * 0.1f || r.top > h * 1.1f) continue
                val actuelle = borne(1f - abs(n - pos) * 3f) * borne(1f - pos / 8f)
                carte(cc, r, n, sec, actuelle, k)
            }
            cc.restore()
        }
        if (flou > 0.5f && Build.VERSION.SDK_INT >= 31 && c.isHardwareAccelerated) {
            // Android 12+ : on dessine la bande dans un « nœud » auquel on applique un flou VERTICAL
            val nd = (noeud as? RenderNode) ?: RenderNode("reels").also { noeud = it }
            nd.setPosition(0, 0, w.toInt(), h.toInt())
            nd.setRenderEffect(RenderEffect.createBlurEffect(0.5f, flou, Shader.TileMode.DECAL))
            val rc = nd.beginRecording()
            try { dessinerBande(rc) } finally { nd.endRecording() }
            c.drawRenderNode(nd)
        } else dessinerBande(c)

        // des traits de vitesse, fins et discrets, sur les côtés
        if (elan > 0.05f) {
            val rr = Random((sec * 30f).toLong())
            for (j in 0 until 14) {
                val x = if (rr.nextBoolean()) rr.nextFloat() * w * 0.1f else w * 0.9f + rr.nextFloat() * w * 0.1f
                val y = rr.nextFloat() * h; val lg = (0.1f + 0.2f * rr.nextFloat()) * h * elan
                c.drawLine(x, y, x, y + lg, igPinceau(avecAlpha(igMelange(IG_ROSE, IG_VIOLET, rr.nextFloat()), 0.25f * elan), 1.2f * k))
            }
        }

        // le compteur qui s'emballe (en anglais), sur un léger voile sombre pour rester lisible
        val voile = Paint(); voile.shader = LinearGradient(0f, 0f, 0f, h * 0.14f, avecAlpha(Color.BLACK, 0.75f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h * 0.14f, voile)
        val vues = floor(pos).toInt() + 1
        o.texte(c, "REELS WATCHED", w / 2f, h * 0.055f - u * pousse, 11f * k, avecAlpha(Color.WHITE, 0.45f), ancre = 0.5f, mono = true)
        o.texte(c, "$vues", w / 2f, h * 0.055f + 34f * k - u * pousse, 30f * k, avecAlpha(Color.WHITE, 0.85f), ancre = 0.5f, mono = true, gras = true)
        val une = borne((sec - 0.85f) / 0.3f) * (1f - u)
        if (une > 0f) o.texte(c, "just one more...", w / 2f, h * 0.955f, 14f * k, avecAlpha(Color.WHITE, 0.55f * une), ancre = 0.5f)
        o.vignette(c, w, h, 0.6f)

        // RÉVÉLATION : l'app arrive comme la vidéo SUIVANTE, avec le même swipe sec. Sous la dernière
        // vidéo, à la place de la suivante, un « trou » monte avec la bande et montre l'app.
        if (u > 0f) {
            val haut = h / 2f + ch / 2f + ecart - u * pousse
            val bord = 10f * k
            val q = Paint(); q.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            c.drawRect(0f, haut + bord, w, h, q)
            val d = Paint(); d.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            d.shader = LinearGradient(0f, haut, 0f, haut + bord, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
            c.drawRect(0f, haut, w, haut + bord, d)
            if (u >= 1f) c.drawRect(0f, 0f, w, h, q)
        }
    }

    /** Une vidéo verticale : le décor, ce qui bouge, la légende, la barre de progression, les cœurs. */
    private fun carte(c: Canvas, r: RectF, n: Int, sec: Float, actuelle: Float, k: Float) {
        val ry = 14f * k
        val forme = Path(); forme.addRoundRect(r, ry, ry, Path.Direction.CW)
        c.save(); c.clipPath(forme)
        val fond = FondsReels.fond(n)
        if (fond != null) c.drawBitmap(fond, null, r, Paint(Paint.FILTER_BITMAP_FLAG))
        else igDegrade(c, r.left, r.top, r.right, r.bottom, intArrayOf(rgb(20, 16, 40), rgb(60, 30, 70)))
        c.save(); c.translate(r.left, r.top); c.scale(r.width() / FondsReels.L0, r.height() / FondsReels.H0)
        AnimsReels.dessiner(c, n, sec)
        c.restore()
        // un léger vignettage dans la vidéo, et le bas assombri pour la légende
        val vg = Paint(); vg.shader = RadialGradient(r.centerX(), r.centerY(), r.height() * 0.65f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, avecAlpha(Color.BLACK, 0.45f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(r, vg)
        val bas = Paint(); bas.shader = LinearGradient(0f, r.bottom - r.height() * 0.35f, 0f, r.bottom, Color.TRANSPARENT, avecAlpha(Color.BLACK, 0.65f), Shader.TileMode.CLAMP)
        c.drawRect(r, bas)
        val L = r.width()
        // la légende : un avatar rond et des barres (pas de vrai texte)
        c.drawCircle(r.left + L * 0.09f, r.bottom - L * 0.30f, L * 0.04f, igPinceau(avecAlpha(Color.WHITE, 0.7f)))
        c.drawRoundRect(r.left + L * 0.16f, r.bottom - L * 0.315f, r.left + L * 0.45f, r.bottom - L * 0.285f, 3f * k, 3f * k, igPinceau(avecAlpha(Color.WHITE, 0.65f)))
        c.drawRoundRect(r.left + L * 0.05f, r.bottom - L * 0.22f, r.left + L * (0.55f + 0.2f * modulo(n * 0.61f, 1f)), r.bottom - L * 0.195f, 3f * k, 3f * k, igPinceau(avecAlpha(Color.WHITE, 0.4f)))
        c.drawRoundRect(r.left + L * 0.05f, r.bottom - L * 0.17f, r.left + L * 0.4f, r.bottom - L * 0.145f, 3f * k, 3f * k, igPinceau(avecAlpha(Color.WHITE, 0.3f)))
        // la barre de progression de la vidéo
        c.drawRect(r.left, r.bottom - 3f * k, r.right, r.bottom, igPinceau(avecAlpha(Color.WHITE, 0.2f)))
        val prog = if (actuelle > 0f) modulo(sec * 0.9f + n * 0.23f, 1f) else modulo(n * 0.41f, 1f)
        val pg = Paint(); pg.shader = LinearGradient(r.left, 0f, r.right, 0f, intArrayOf(IG_ORANGE, IG_ROSE, IG_VIOLET), null, Shader.TileMode.CLAMP)
        c.drawRect(r.left, r.bottom - 3f * k, r.left + r.width() * prog, r.bottom, pg)
        // les petits cœurs qui montent, sur la vidéo qu'on regarde
        if (actuelle > 0f) for (q in coeurs) {
            val dx = q[0]; val ph = q[1]; val tl = q[2]
            val uu = modulo(sec * 0.9f + ph, 1f)
            val x = r.right - L * 0.13f + dx * L * 0.15f * sin(uu * 5f + ph * 6f)
            val y = r.bottom - L * 0.25f - uu * r.height() * 0.45f
            igCoeur(c, x, y, L * 0.035f * tl, avecAlpha(igMelange(IG_ROSE, IG_ORANGE, ph), 0.8f * actuelle * sin(PI.toFloat() * uu)))
        }
        c.restore()
        c.drawRoundRect(r, ry, ry, igPinceau(avecAlpha(Color.WHITE, 0.08f), 1f * k))
    }
}
