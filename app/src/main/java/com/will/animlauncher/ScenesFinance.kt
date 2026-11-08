package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// LOT 5 : finance et crypto.

val ORANGE_BTC = rgb(247, 147, 26)

private fun radiansDe(degres: Float): Float = degres * PI.toFloat() / 180f

// Un chemin fermé passant par une liste de points [x0, y0, x1, y1, ...].
private fun polygone(vararg p: Float): Path {
    val chemin = Path()
    chemin.moveTo(p[0], p[1])
    var k = 2
    while (k < p.size) { chemin.lineTo(p[k], p[k + 1]); k += 2 }
    chemin.close()
    return chemin
}

// ===================== REVOLUT : « Carte métal » =====================
// Une carte en métal brossé tourne sur elle-même dans le noir ; un reflet glisse sur sa surface.
class CarteMetal : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(10, 10, 14), rgb(4, 4, 6), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val cx = w / 2f; val cy = h / 2f
        val m = min(w, h)
        val k = doux(rev)
        val L = mix(m * 0.62f, w * 1.05f, k); val H = mix(m * 0.39f, h * 1.05f, k)
        val tour = (1f - ralentit(borne(sec / 0.8f))) * 540f + 10f * sin(sec * 3f)   // tourne puis se pose de face
        val sx = cos(radiansDe(tour))
        o.tache(c, cx, cy + H * 0.65f, L * 0.5f * abs(sx) + 1f, 8f * dp, 0.5f * app * a, Color.BLACK)
        c.save(); c.translate(cx, cy); c.scale(max(0.02f, abs(sx)), 1f)
        val r = RectF(-L / 2f, -H / 2f, L / 2f, H / 2f)
        val p = o.pinceau()
        p.shader = LinearGradient(-L / 2f, -H / 2f, L / 2f, H / 2f,
            intArrayOf(argb(255f * app, 70, 72, 80), argb(255f * app, 30, 31, 36), argb(255f * app, 56, 58, 66)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 14f * dp, 14f * dp, p)
        val brosse = o.lumiere(argb(6f * app * a, 255, 255, 255), 1f * dp)       // le métal brossé
        for (s in 0 until 30) { val y = -H / 2f + H * s / 30f; c.drawLine(-L / 2f, y, L / 2f, y, brosse) }
        if (sx >= 0f) {                                                          // face avant : puce et numéro
            c.drawRoundRect(RectF(-L * 0.38f, -H * 0.12f, -L * 0.2f, H * 0.1f), 3f * dp, 3f * dp, o.pinceau(argb(255f * app * a, 200, 180, 120)))
            for (g in 0 until 4) c.drawRect(-L * 0.38f + g * L * 0.13f, H * 0.25f, -L * 0.38f + g * L * 0.13f + L * 0.1f, H * 0.29f, o.pinceau(argb(120f * app * a, 200, 205, 215)))
        } else {                                                                 // dos : la bande noire
            c.drawRect(-L / 2f, -H * 0.3f, L / 2f, -H * 0.12f, o.pinceau(argb(255f * app * a, 12, 12, 14)))
        }
        val px = mix(-L, L, modulo(sec * 0.7f, 1.4f) / 1.4f) * 1.2f              // le reflet qui glisse
        val reflet = o.lumiere()
        reflet.shader = LinearGradient(px - L * 0.15f, 0f, px + L * 0.15f, 0f,
            intArrayOf(Color.TRANSPARENT, argb(45f * app * a, 230, 235, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        val bord = Path().apply { addRoundRect(r, 14f * dp, 14f * dp, Path.Direction.CW) }
        c.save(); c.clipPath(bord); c.rotate(20f); c.drawRect(-L, -L, L, L, reflet); c.restore()
        c.drawRoundRect(r, 14f * dp, 14f * dp, o.lumiere(argb(60f * app * a, 200, 210, 230), 1.2f * dp))
        c.restore()
        o.vignette(c, w, h, 0.55f)
        if (rev > 0f) gommeRect(c, o, RectF(cx - L / 2f, cy - H / 2f, cx + L / 2f, cy + H / 2f), ralentit(rev * 1.3f), 14f * dp)
    }
}

// ===================== LA BANQUE POSTALE : « Timbre » =====================
// Un timbre dentelé posé sous une lampe ; le tampon de la poste vient l'oblitérer d'ondes.
class Timbre : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(12, 12, 16), rgb(5, 5, 7), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val bleu = rgb(60, 110, 220); val jaune = rgb(250, 200, 60)
        val cx = w / 2f; val cy = h / 2f
        val m = min(w, h)
        o.tache(c, cx, cy, m * 0.5f, m * 0.4f, 0.1f * app, rgb(255, 230, 190))
        val k = doux(rev)
        val L = mix(m * 0.36f, w * 1.05f, k); val H = mix(m * 0.46f, h * 1.05f, k)
        val r = RectF(cx - L / 2f, cy - H / 2f, cx + L / 2f, cy + H / 2f)
        c.save(); c.rotate(-4f * (1f - k), cx, cy)
        c.drawRect(r, o.pinceau(argb(255f * app, 40, 40, 46)))
        val dent = 6f * dp                                          // la dentelure
        val trou = o.pinceau(rgb(9, 9, 12))
        for (cote in 0 until 4) {
            val n = ((if (cote % 2 == 0) L else H) / (dent * 2f)).toInt()
            for (q in 0..n) {
                val t = q.toFloat() / max(1, n)
                val x = when (cote) { 0, 2 -> r.left + t * L; 1 -> r.right; else -> r.left }
                val y = when (cote) { 0 -> r.top; 1, 3 -> r.top + t * H; else -> r.bottom }
                c.drawCircle(x, y, dent * 0.55f, trou)
            }
        }
        val dedans = RectF(r.left + L * 0.1f, r.top + H * 0.08f, r.right - L * 0.1f, r.bottom - H * 0.22f)
        val p = o.pinceau()
        p.shader = LinearGradient(0f, dedans.top, 0f, dedans.bottom, argb(255f * app, 26, 40, 80), argb(255f * app, 14, 20, 40), Shader.TileMode.CLAMP)
        c.drawRect(dedans, p)
        o.halo(c, dedans.centerX(), dedans.centerY() + dedans.height() * 0.1f, dedans.width() * 0.22f, 0.4f * app * a, jaune)
        val ox = dedans.centerX(); val oy = dedans.centerY() - dedans.height() * 0.15f; val s = dedans.width() * 0.2f
        val oiseau = Path()                                         // un oiseau stylisé en vol
        oiseau.moveTo(ox - s, oy); oiseau.quadTo(ox - s * 0.5f, oy - s * 0.4f, ox, oy + s * 0.1f); oiseau.quadTo(ox + s * 0.5f, oy - s * 0.4f, ox + s, oy)
        c.drawPath(oiseau, o.pinceau(argb(255f * app * a, 230, 230, 240), 2.5f * dp))
        texte(c, "0,99", r.centerX(), r.bottom - H * 0.11f, H * 0.08f, avecAlpha(rgb(220, 220, 230), app * a))
        c.restore()
        val tampon = borne((sec - 0.3f) / 0.12f)                    // le tampon de la poste
        if (tampon > 0f) {
            val tx = cx + L * 0.25f; val ty = cy - H * 0.05f
            val encre = o.pinceau(avecAlpha(bleu, 0.7f * tampon * a), 2.5f * dp)
            c.drawCircle(tx, ty, m * 0.12f, encre)
            c.drawCircle(tx, ty, m * 0.09f, o.pinceau(avecAlpha(bleu, 0.7f * tampon * a), 1.5f * dp))
            val onde = o.pinceau(avecAlpha(bleu, 0.7f * tampon * a), 2f * dp)
            for (q in 0 until 4) {
                val yy = ty - m * 0.06f + q * m * 0.04f
                val ligne = Path(); ligne.moveTo(tx + m * 0.12f, yy)
                for (t in 1..20) ligne.lineTo(tx + m * 0.12f + t * m * 0.02f, yy + sin(t * 0.9f) * m * 0.01f)
                c.drawPath(ligne, onde)
            }
        }
        o.vignette(c, w, h, 0.55f)
        if (rev > 0f) gommeRect(c, o, r, ralentit(rev * 1.3f))
    }
}

// ===================== DEGIRO : « Chandeliers » =====================
// Un graphique en chandeliers se construit ; chaque bougie s'allume en montant.
class Chandeliers : Scene() {
    private val bougies: List<FloatArray> = Random(211).let { r ->                 // [ouverture, fermeture, haut, bas]
        var prix = 0.5f
        List(22) {
            val ouv = prix
            prix = borne(prix + (r.nextFloat() - 0.42f) * 0.12f, 0.1f, 0.9f)
            floatArrayOf(ouv, prix, max(ouv, prix) + r.nextFloat() * 0.05f, min(ouv, prix) - r.nextFloat() * 0.05f)
        }
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(6, 10, 16), rgb(4, 5, 8), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val vert = rgb(60, 220, 160); val rouge = rgb(240, 80, 90)
        val grille = o.pinceau(argb(14f * app, 120, 150, 200), 1f * dp)
        for (k in 1 until 6) c.drawLine(0f, h * k / 6f, w, h * k / 6f, grille)
        val n = bougies.size
        val visibles = ralentit(borne(sec / 0.9f)) * n
        val larg = w * 0.84f / n
        val zone = h * 0.5f
        val y0 = h * 0.75f
        for ((k, b) in bougies.withIndex()) {
            val f = borne(visibles - k)
            if (f <= 0f) break
            val ouv = b[0]; val ferm = b[1]; val haut = b[2]; val bas = b[3]
            val x = w * 0.08f + (k + 0.5f) * larg
            val coul = if (ferm >= ouv) vert else rouge
            val ya = y0 - ouv * zone
            val yb = mix(ya, y0 - ferm * zone, f)
            c.drawLine(x, y0 - haut * zone * f - ouv * zone * (1f - f), x, y0 - bas * zone, o.lumiere(avecAlpha(coul, 0.5f * f * app * a), 1.2f * dp))
            val corps = RectF(x - larg * 0.32f, min(ya, yb), x + larg * 0.32f, max(ya, yb) + 1f * dp)
            c.drawRect(corps, o.pinceau(avecAlpha(fonce(coul, 0.6f), f * app * a)))
            c.drawRect(corps, o.lumiere(avecAlpha(coul, 0.6f * f * app * a), 1f * dp))
            if (k == visibles.toInt() - 1 || (k == n - 1 && f >= 1f)) o.halo(c, x, yb, 20f * dp, 0.5f * app * a, coul)
        }
        val dernier = bougies[min(n - 1, max(0, visibles.toInt() - 1))]
        val yl = y0 - dernier[1] * zone
        c.drawLine(0f, yl, w, yl, o.lumiere(avecAlpha(vert, 0.2f * app * a), 1f * dp))
        o.vignette(c, w, h, 0.55f)
        o.revelationBalayageDouce(c, rev, 0f, vert)
    }
}

// ===================== TRADE REPUBLIC : « Courbe » =====================
// Noir et blanc, épuré : une courbe de cours se dessine d'un trait, et un chiffre monte.
class Courbe : Scene() {
    private fun y(t: Float, h: Float) = h * (0.62f - 0.22f * t - 0.05f * sin(t * 13f) - 0.03f * sin(t * 29f))

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(6, 6, 7))
        val app = ralentit(intro)
        val a = 1f - rev
        val blanc = rgb(235, 235, 240)
        val avance = ralentit(borne(sec / 0.8f))
        val chemin = Path(); val aire = Path()
        val x0 = w * 0.06f; val L = w * 0.88f
        for (s in 0..120) {
            val t = avance * s / 120f
            val x = x0 + t * L; val yy = y(t, h)
            if (s == 0) { chemin.moveTo(x, yy); aire.moveTo(x, h) } else chemin.lineTo(x, yy)
            aire.lineTo(x, yy)
        }
        aire.lineTo(x0 + avance * L, h); aire.close()
        val p = o.lumiere()
        p.shader = LinearGradient(0f, h * 0.35f, 0f, h, argb(30f * app * a, 235, 235, 240), argb(0f, 235, 235, 240), Shader.TileMode.CLAMP)
        c.drawPath(aire, p)
        c.drawPath(chemin, o.lumiere(avecAlpha(blanc, 0.06f * app * a), 8f * dp))
        c.drawPath(chemin, o.lumiere(avecAlpha(blanc, 0.9f * app * a), 2f * dp))
        val xt = x0 + avance * L; val yt = y(avance, h)
        o.halo(c, xt, yt, 14f * dp, 0.5f * app * a, blanc)
        c.drawCircle(xt, yt, 3.5f * dp, o.pinceau(avecAlpha(blanc, app * a)))
        val m = min(w, h)
        texte(c, String.format(Locale.FRANCE, "%.2f €", 100f + avance * 23.47f), w / 2f, h * 0.22f, m * 0.09f, avecAlpha(blanc, app * a))
        texte(c, String.format(Locale.FRANCE, "+%.2f %%", avance * 23.47f), w / 2f, h * 0.22f + m * 0.08f, m * 0.04f, avecAlpha(rgb(120, 230, 160), 0.8f * app * a))
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, xt, yt, rev, blanc)
    }
}

// ===================== KRAKEN : « Abysses » =====================
// Au fond de l'océan, des tentacules violets ondulent dans un rayon de lumière ; des bulles remontent.
class Abysses : Scene() {
    private val bulles = nuage(221, 40)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(10, 8, 24), rgb(3, 2, 8), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val violet = rgb(130, 90, 255)
        val m = min(w, h)
        val l = o.lumiere()                                          // rayon de lumière venu de la surface
        l.shader = LinearGradient(0f, 0f, 0f, h, argb(30f * app * a, 140, 120, 255), argb(0f, 140, 120, 255), Shader.TileMode.CLAMP)
        c.drawPath(polygone(w * 0.4f, 0f, w * 0.6f, 0f, w * 0.75f, h, w * 0.3f, h), l)
        val sortie = ralentit(borne(sec / 0.7f)) * (1f - accelere(rev) * 0.6f)
        val ventouse = o.lumiere(avecAlpha(rgb(200, 180, 255), 0.5f * app * a))
        for (k in 0 until 7) {                                        // tentacules : rubans qui s'effilent
            val baseX = w * (0.1f + 0.8f * k / 6f)
            val long = h * (0.35f + 0.2f * sin(k * 2.3f)) * sortie
            if (long < 1f) continue
            val n = 30
            val gx = FloatArray(n + 1); val dx = FloatArray(n + 1); val yy = FloatArray(n + 1)
            for (s in 0..n) {
                val t = s.toFloat() / n
                val x = baseX + sin(t * 4f + sec * 2.5f + k) * m * 0.06f * t
                val ep = m * 0.035f * (1f - t) + 1f * dp
                gx[s] = x - ep; dx[s] = x + ep; yy[s] = h + 10f * dp - t * long
            }
            val ruban = Path(); ruban.moveTo(gx[0], yy[0])
            for (s in 1..n) ruban.lineTo(gx[s], yy[s])
            for (s in n downTo 0) ruban.lineTo(dx[s], yy[s])
            ruban.close()
            val p = o.pinceau()
            p.shader = LinearGradient(0f, h, 0f, h - long, argb(255f * app, 40, 24, 80), argb(255f * app, 90, 60, 170), Shader.TileMode.CLAMP)
            c.drawPath(ruban, p)
            c.drawPath(ruban, o.lumiere(avecAlpha(violet, 0.25f * app * a), 1f * dp))
            for (v in 1..5) {
                val t = v / 7f
                c.drawCircle(baseX + sin(t * 4f + sec * 2.5f + k) * m * 0.06f * t, h + 10f * dp - t * long, 1.6f * dp, ventouse)
            }
        }
        for (b in bulles) {
            val y = (1f - modulo(b[1] + sec * (0.3f + 0.4f * b[2]), 1f)) * h
            val x = b[0] * w + sin(sec * 3f + b[3]) * 6f * dp
            c.drawCircle(x, y, (1.5f + 2.5f * b[2]) * dp, o.lumiere(argb(60f * b[2] * app * a, 200, 190, 255), 1f * dp))
        }
        o.vignette(c, w, h, 0.6f)
        o.revelationDouce(c, w / 2f, h * 0.4f, rev, violet)
    }
}

// ===================== LEDGER : « Chaîne de blocs » =====================
// Des cubes sombres reliés par des maillons lumineux tournent lentement : une blockchain.
class ChaineBlocs : Scene() {
    private val sommets = arrayOf(floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f), floatArrayOf(1f, 1f, -1f), floatArrayOf(-1f, 1f, -1f),
        floatArrayOf(-1f, -1f, 1f), floatArrayOf(1f, -1f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(-1f, 1f, 1f))
    private val faces = arrayOf(intArrayOf(0, 1, 2, 3), intArrayOf(4, 5, 6, 7), intArrayOf(0, 1, 5, 4), intArrayOf(2, 3, 7, 6), intArrayOf(1, 2, 6, 5), intArrayOf(0, 3, 7, 4))
    private val aretes = arrayOf(intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 0), intArrayOf(4, 5), intArrayOf(5, 6),
        intArrayOf(6, 7), intArrayOf(7, 4), intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7))

    // Un cube vu en légère plongée, qui tourne autour de l'axe vertical.
    private fun cube(c: Canvas, o: Outils, x: Float, y: Float, s: Float, ang: Float, alpha: Float, dp: Float) {
        val px = FloatArray(8); val py = FloatArray(8)
        for ((n, q) in sommets.withIndex()) {
            val xr = q[0] * cos(ang) - q[2] * sin(ang)
            val zr = q[0] * sin(ang) + q[2] * cos(ang)
            px[n] = x + xr * s; py[n] = y + (q[1] * cos(0.5f) - zr * sin(0.5f)) * s
        }
        val face = o.pinceau(argb(30f * alpha, 60, 64, 72))
        for (f in faces) c.drawPath(polygone(px[f[0]], py[f[0]], px[f[1]], py[f[1]], px[f[2]], py[f[2]], px[f[3]], py[f[3]]), face)
        val trait = o.lumiere(argb(160f * alpha, 200, 205, 215), 1.2f * dp)
        for (e in aretes) c.drawLine(px[e[0]], py[e[0]], px[e[1]], py[e[1]], trait)
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(9, 9, 11), rgb(4, 4, 5), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val orange = rgb(255, 120, 60)
        val n = 5
        val ecart = m * 0.38f
        val decal = modulo(sec * 0.25f, 1f)
        for (k in -1..n) {
            val t = k - decal
            val x = w / 2f + (t - (n - 1) / 2f) * ecart
            val y = h / 2f + sin(t * 0.9f) * m * 0.05f
            val s = m * 0.1f * app
            val al = borne(1f - abs(x - w / 2f) / (w * 0.6f)) * app * a
            cube(c, o, x, y, s, sec * 0.8f + k, al, dp)
            val xn = w / 2f + (t + 1f - (n - 1) / 2f) * ecart
            val yn = h / 2f + sin((t + 1f) * 0.9f) * m * 0.05f
            c.drawLine(x + s * 1.3f, y, xn - s * 1.3f, yn, o.lumiere(avecAlpha(orange, 0.5f * al), 2f * dp))
            val puls = modulo(sec * 1.5f + k * 0.3f, 1f)
            o.halo(c, mix(x + s * 1.3f, xn - s * 1.3f, puls), mix(y, yn, puls), 6f * dp, 0.5f * al, orange)
        }
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, w / 2f, h / 2f, rev, orange)
    }
}

// ===================== KRAK : « Pièce » =====================
// Une pièce est lancée en l'air ; elle tourne en montant, brille, et retombe face visible.
class Piece : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(12, 10, 20), rgb(5, 4, 8), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val violet = rgb(140, 100, 255)
        val m = min(w, h)
        val cx = w / 2f
        val t = borne(sec / 0.22f)
        val y = h * 0.55f - sin(PI.toFloat() * t) * h * 0.18f
        val R = m * 0.16f * (1f + 2.5f * accelere(rev))
        val tour = t * 1080f + 90f * (1f - app)
        val sy = cos(radiansDe(tour))
        o.tache(c, cx, h * 0.72f, R * (1f - 0.5f * sin(PI.toFloat() * t)), 6f * dp, 0.4f * app * a, Color.BLACK)
        c.save(); c.translate(cx, y); c.scale(1f, max(0.06f, abs(sy)))
        val p = o.pinceau()
        p.shader = RadialGradient(-R * 0.3f, -R * 0.3f, R * 1.6f, argb(255f * app, 190, 170, 255), argb(255f * app, 70, 50, 150), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, R, p)
        c.drawCircle(0f, 0f, R * 0.82f, o.pinceau(argb(150f * app * a, 40, 26, 100), 2f * dp))
        c.drawCircle(0f, 0f, R * 0.55f, o.lumiere(argb(90f * app * a, 230, 220, 255), 1.5f * dp))
        c.restore()
        val brille = max(0f, sin(radiansDe(tour) * 2f)).pow(8)
        o.halo(c, cx + R * 0.4f, y - R * 0.3f * abs(sy), R * 0.5f, 0.5f * brille * app * a, rgb(230, 220, 255))
        o.halo(c, cx, y, R * 2f, 0.15f * app * a, violet)
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, y, rev, violet)
    }
}

// ===================== BANKIN' : « Piles » =====================
// Des piles de pièces poussent comme un graphique en barres, chacune un peu plus haute.
class Piles : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(8, 12, 16), rgb(4, 5, 7), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val teal = rgb(60, 200, 190)
        val n = 5
        val sol = h * 0.68f
        val rx = m * 0.07f; val ry = rx * 0.35f
        val tranche = o.pinceau(argb(255f * app * a, 120, 90, 30))
        for (k in 0 until n) {
            val x = w / 2f + (k - (n - 1) / 2f) * rx * 2.6f
            val nb = ((3 + k * 3) * ralentit(borne(sec / 0.12f - k * 0.25f))).toInt()
            for (s in 0 until nb) {
                val y = sol - s * ry * 0.75f
                c.drawOval(RectF(x - rx, y - ry + ry * 0.4f, x + rx, y + ry + ry * 0.4f), tranche)
                val p = o.pinceau()
                p.shader = LinearGradient(x - rx, 0f, x + rx, 0f, argb(255f * app * a, 230, 190, 90), argb(255f * app * a, 150, 110, 40), Shader.TileMode.CLAMP)
                c.drawOval(RectF(x - rx, y - ry, x + rx, y + ry), p)
            }
            if (nb > 0) o.halo(c, x, sol - (nb - 1) * ry * 0.75f, rx * 1.4f, 0.2f * app * a, rgb(255, 210, 120))
        }
        c.drawLine(w * 0.1f, sol + ry * 2f, w * 0.9f, sol + ry * 2f, o.lumiere(avecAlpha(teal, 0.3f * app * a), 1.5f * dp))
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, w / 2f, sol - m * 0.15f, rev, teal)
    }
}

// ===================== WERO : « Pont » =====================
// Un pont de lumière s'élance au-dessus de la nuit et relie deux villes.
class Pont : Scene() {
    private val fenetres = Random(231).let { r -> List(60) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(6, 8, 20), rgb(12, 12, 28), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val jaune = rgb(255, 210, 90)
        val m = min(w, h)
        val sol = h * 0.66f
        val immeuble = o.pinceau(rgb(10, 11, 18))
        for (cote in intArrayOf(-1, 1)) {                           // deux villes en silhouette
            for (k in 0 until 6) {
                val bx = w / 2f + cote * (w * 0.28f + k * w * 0.035f)
                val bh = m * (0.1f + 0.15f * abs(sin(k * 2.7f + cote)))
                c.drawRect(bx - w * 0.015f, sol - bh, bx + w * 0.015f, sol, immeuble)
            }
        }
        val fen = o.pinceau()
        for (f in fenetres) {
            val cote = if (f[0] < 0.5f) -1 else 1
            val x = w / 2f + cote * (w * 0.28f + modulo(f[0] * 2f, 1f) * w * 0.2f)
            val y = sol - f[1] * m * 0.18f
            fen.color = argb(160f * f[2] * app * a, 255, 220, 150)
            c.drawRect(x, y, x + 2f * dp, y + 2f * dp, fen)
        }
        c.drawRect(0f, sol, w, h, o.pinceau(rgb(5, 6, 12)))
        val lance = ralentit(borne(sec / 0.22f))
        val xa = w / 2f - w * 0.28f; val xb = w / 2f + w * 0.28f
        val arche = Path()
        for (s in 0..40) {
            val t = s / 40f * lance
            val x = mix(xa, xb, t); val y = sol - sin(PI.toFloat() * t) * m * 0.22f
            if (s == 0) arche.moveTo(x, y) else arche.lineTo(x, y)
        }
        val couches = arrayOf(floatArrayOf(10f, 0.08f), floatArrayOf(3f, 0.35f), floatArrayOf(1.5f, 0.9f))
        for (cc in couches) c.drawPath(arche, o.lumiere(avecAlpha(jaune, cc[1] * app * a), cc[0] * dp))
        c.save(); c.translate(0f, 2f * sol); c.scale(1f, -1f)        // reflet dans l'eau
        c.drawPath(arche, o.lumiere(avecAlpha(jaune, 0.15f * app * a), 3f * dp))
        c.restore()
        o.halo(c, mix(xa, xb, lance), sol - sin(PI.toFloat() * lance) * m * 0.22f, 14f * dp, 0.5f * app * a, jaune)
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, w / 2f, sol - m * 0.22f, rev, jaune)
    }
}

// ===================== STRIKE : « Foudre » =====================
// Un éclair se ramifie dans un ciel d'orage (lumière contenue, pas de flash), puis la lueur reste.
class Foudre : Scene() {
    private class Branche(val x: FloatArray, val y: FloatArray, val prof: Int)
    private val branches = ArrayList<Branche>()

    init {
        val r = Random(241)
        // Chaque branche avance en zigzag et peut faire naître des branches plus petites.
        fun pousse(x0: Float, y0: Float, a0: Float, long: Float, prof: Int) {
            if (prof > 3 || long < 0.02f) return
            var x = x0; var y = y0; var ang = a0
            val xs = FloatArray(7); val ys = FloatArray(7)
            xs[0] = x; ys[0] = y
            for (s in 1..6) {
                ang += (r.nextFloat() - 0.5f) * 0.9f
                x += sin(ang) * long / 6f; y += cos(ang) * long / 6f
                xs[s] = x; ys[s] = y
                if (r.nextFloat() < 0.3f) pousse(x, y, ang + (r.nextFloat() - 0.5f) * 1.6f, long * 0.45f, prof + 1)
            }
            branches.add(Branche(xs, ys, prof))
        }
        pousse(0.5f, 0f, 0f, 0.9f, 0)
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(10, 10, 16), rgb(4, 4, 6), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val jaune = rgb(255, 220, 90)
        val m = min(w, h)
        for (k in 0 until 5) o.tache(c, w * (0.1f + 0.2f * k), h * 0.08f, m * 0.25f, m * 0.08f, 0.08f * app, rgb(120, 120, 160))
        val descente = ralentit(borne(sec / 0.12f))
        val lueur = exp(-max(0f, sec - 0.12f) * 3f) * 0.6f + 0.4f
        for (b in branches) {
            val chemin = Path()
            var premier = true
            for (s in b.x.indices) {
                if (b.y[s] > descente) break
                val px = w / 2f + (b.x[s] - 0.5f) * m * 1.2f; val py = b.y[s] * h
                if (premier) { chemin.moveTo(px, py); premier = false } else chemin.lineTo(px, py)
            }
            val f = 1f / (1 + b.prof)
            c.drawPath(chemin, o.lumiere(avecAlpha(jaune, 0.08f * f * lueur * app * a), 10f * dp * f))
            c.drawPath(chemin, o.lumiere(avecAlpha(jaune, 0.3f * f * lueur * app * a), 3f * dp * f))
            c.drawPath(chemin, o.lumiere(avecAlpha(jaune, 0.9f * f * lueur * app * a), 1.2f * dp * f))
        }
        o.tache(c, w / 2f, h * 0.92f, m * 0.3f, m * 0.03f, 0.3f * lueur * descente * app * a, jaune)
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, w / 2f, h * 0.9f, rev, jaune)
    }
}

// ===================== CAKE WALLET : « Strates » =====================
// Des couches de couleur glissent et s'empilent comme les strates d'un gâteau vu en coupe.
class Strates : Scene() {
    private val couleurs = intArrayOf(rgb(60, 90, 200), rgb(90, 140, 240), rgb(40, 60, 150), rgb(120, 170, 255), rgb(50, 80, 180))

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(8, 8, 12), rgb(4, 4, 6), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val L = m * 0.6f; val H = m * 0.08f
        val bas = h * 0.66f
        for ((k, coul) in couleurs.withIndex()) {
            val f = ralentit(borne(sec / 0.06f - k * 0.6f))
            if (f <= 0f) continue
            val cote = if (k % 2 == 0) -1 else 1
            val x = w / 2f + cote * (1f - f) * w
            val y = bas - k * H * 1.02f
            val p = o.pinceau()
            p.shader = LinearGradient(0f, y - H, 0f, y, avecAlpha(coul, 0.9f * app * a), avecAlpha(fonce(coul, 0.5f), 0.9f * app * a), Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(x - L / 2f, y - H, x + L / 2f, y), 4f * dp, 4f * dp, p)
            c.drawLine(x - L / 2f, y - H, x + L / 2f, y - H, o.lumiere(argb(50f * app * a, 255, 255, 255), 1f * dp))
        }
        o.tache(c, w / 2f, bas + 8f * dp, L * 0.6f, 6f * dp, 0.5f * app, Color.BLACK)
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, w / 2f, bas - H * 2.5f, rev, rgb(110, 160, 255))
    }
}

// ===================== BISQ : « Échange » =====================
// Deux lumières tournent l'une autour de l'autre, de plus en plus près, et échangent leurs couleurs.
class Echange : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(6, 8, 7))
        val app = ralentit(intro)
        val a = 1f - rev
        val vert = rgb(60, 220, 120)
        val m = min(w, h)
        val cx = w / 2f; val cy = h / 2f
        val echange = doux(borne(sec / 0.2f))
        val r = m * 0.22f * (1f - 0.5f * echange)
        for (k in 0 until 2) {
            val coul = if (k == 0) mixCouleur(vert, ORANGE_BTC, echange) else mixCouleur(ORANGE_BTC, vert, echange)
            for (g in 10 downTo 1) {                               // traînée
                val ang = (sec - g * 0.012f) * 9f + k * PI.toFloat()
                o.halo(c, cx + cos(ang) * r, cy + sin(ang) * r * 0.6f, (8f - g * 0.5f) * dp, 0.1f * (1f - g / 11f) * app * a, coul)
            }
            val ang = sec * 9f + k * PI.toFloat()
            val x = cx + cos(ang) * r; val y = cy + sin(ang) * r * 0.6f
            o.halo(c, x, y, 24f * dp, 0.4f * app * a, coul)
            c.drawCircle(x, y, 6f * dp * app, o.pinceau(avecAlpha(coul, app * a)))
        }
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, vert)
    }
}

// ===================== BTC FRAME : « Facettes » =====================
// Une pièce orange faite de facettes tourne lentement ; chaque facette accroche la lumière à son tour.
class Facettes : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(12, 9, 6), rgb(5, 4, 3), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f; val cy = h / 2f
        val R = m * 0.26f * (0.7f + 0.3f * app) * (1f + 2.5f * accelere(rev))
        val n = 12
        val rot = sec * 40f
        o.halo(c, cx, cy, R * 1.8f, 0.2f * app * a, ORANGE_BTC)
        val arete = o.lumiere(argb(30f * app * a, 255, 230, 190), 1f * dp)
        for (k in 0 until n) {
            val a0 = radiansDe(rot + k * 360f / n); val a1 = radiansDe(rot + (k + 1) * 360f / n)
            val facette = polygone(cx, cy, cx + cos(a0) * R, cy + sin(a0) * R, cx + cos(a1) * R, cy + sin(a1) * R)
            val lum = 0.5f + 0.5f * cos(a0 + 0.26f - radiansDe(sec * 160f))
            c.drawPath(facette, o.pinceau(avecAlpha(mixCouleur(fonce(ORANGE_BTC, 0.35f), ORANGE_BTC, lum), app * a)))
            c.drawPath(facette, arete)
        }
        c.drawCircle(cx, cy, R * 0.55f, o.pinceau(avecAlpha(fonce(ORANGE_BTC, 0.25f), app * a)))
        c.drawCircle(cx, cy, R * 0.55f, o.lumiere(avecAlpha(ORANGE_BTC, 0.5f * app * a), 1.5f * dp))
        val clair = avecAlpha(rgb(255, 220, 170), app * a)              // le symbole : un B barré de deux traits
        texte(c, "B", cx, cy, R * 0.6f, clair)
        val trait = o.pinceau(clair, R * 0.05f)
        for (dx in floatArrayOf(-0.06f, 0.06f)) {
            c.drawLine(cx + dx * R - R * 0.03f, cy - R * 0.33f, cx + dx * R - R * 0.03f, cy - R * 0.24f, trait)
            c.drawLine(cx + dx * R - R * 0.03f, cy + R * 0.24f, cx + dx * R - R * 0.03f, cy + R * 0.33f, trait)
        }
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, ORANGE_BTC)
    }
}

// ===================== STRATEGY : « Isométrie » =====================
// Des barres orange s'élèvent sur un plateau en vue isométrique, comme une réserve qui grandit.
class Isometrie : Scene() {
    private val barres = arrayOf(floatArrayOf(-2f, 1f, 0.3f), floatArrayOf(-1f, 0f, 0.45f), floatArrayOf(0f, -1f, 0.6f), floatArrayOf(1f, -2f, 0.8f),
        floatArrayOf(0f, 1f, 0.35f), floatArrayOf(1f, 0f, 0.55f), floatArrayOf(2f, -1f, 0.75f)).sortedBy { it[0] + it[1] }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(10, 9, 12), rgb(4, 4, 5), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f; val cy = h * 0.6f
        val u = m * 0.06f
        fun isoX(gx: Float, gy: Float) = cx + (gx - gy) * u
        fun isoY(gx: Float, gy: Float, z: Float) = cy + (gx + gy) * u * 0.5f - z
        val grille = o.lumiere(argb(20f * app * a, 255, 160, 80), 1f * dp)
        for (g in -3..3) {                                            // le plateau
            val gf = g.toFloat()
            c.drawLine(isoX(gf, -3f), isoY(gf, -3f, 0f), isoX(gf, 3f), isoY(gf, 3f, 0f), grille)
            c.drawLine(isoX(-3f, gf), isoY(-3f, gf, 0f), isoX(3f, gf), isoY(3f, gf, 0f), grille)
        }
        for ((k, b) in barres.withIndex()) {
            val gx = b[0]; val gy = b[1]
            val z = m * 0.5f * b[2] * ralentit(borne(sec / 0.15f - k * 0.15f))
            val bx = floatArrayOf(gx - 0.4f, gx + 0.4f, gx + 0.4f, gx - 0.4f)
            val by = floatArrayOf(gy - 0.4f, gy - 0.4f, gy + 0.4f, gy + 0.4f)
            val px = FloatArray(4) { isoX(bx[it], by[it]) }
            val pb = FloatArray(4) { isoY(bx[it], by[it], 0f) }
            val ph = FloatArray(4) { isoY(bx[it], by[it], z) }
            c.drawPath(polygone(px[1], pb[1], px[2], pb[2], px[2], ph[2], px[1], ph[1]), o.pinceau(avecAlpha(fonce(ORANGE_BTC, 0.55f), app * a)))
            c.drawPath(polygone(px[2], pb[2], px[3], pb[3], px[3], ph[3], px[2], ph[2]), o.pinceau(avecAlpha(fonce(ORANGE_BTC, 0.35f), app * a)))
            c.drawPath(polygone(px[0], ph[0], px[1], ph[1], px[2], ph[2], px[3], ph[3]), o.pinceau(avecAlpha(ORANGE_BTC, app * a)))
            o.halo(c, isoX(gx, gy), isoY(gx, gy, z), u * 0.8f, 0.2f * app * a, ORANGE_BTC)
        }
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy - m * 0.2f, rev, ORANGE_BTC)
    }
}
