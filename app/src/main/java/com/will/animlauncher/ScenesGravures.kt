package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ===================== CLAUDE : trois courbes en GRAVURE ANCIENNE =====================
// Encre sépia sur fond sombre, hachures pour les ombres, un cadre et une légende en italique,
// comme une planche de vieux livre : la rosace (art), la collision de particules (physique),
// la bibliothèque (littérature). Pendant l'apparition, les traits « s'impriment » depuis le centre.
// Horizons (ScenesHorizons.kt) passe la main à cette classe pour ces trois mondes.

private val ENCRE = rgb(214, 190, 146)
private val FOND = rgb(15, 12, 10)
private val FOND2 = rgb(30, 24, 18)
private val SOL = rgb(21, 17, 13)
private val ITALIQUE: Typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
private val CAPITALES: Typeface = Typeface.SERIF

// Écrit un texte centré en (x, y), dans une police à empattements (comme dans les vieux livres).
private fun ecrireGravure(c: Canvas, s: String, x: Float, y: Float, taille: Float, couleur: Int, police: Typeface = ITALIQUE) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.typeface = police; p.textSize = taille; p.color = couleur; p.textAlign = Paint.Align.CENTER
    c.drawText(s, x, y + taille * 0.35f, p)
}

// Remplit une forme de traits parallèles (angle en degrés, esp = écart entre les traits) : les hachures.
private fun hachures(c: Canvas, chemin: Path, angle: Float, esp: Float, p: Paint) {
    val b = RectF(); chemin.computeBounds(b, true)
    c.save(); c.clipPath(chemin)
    val xc = b.centerX(); val yc = b.centerY(); val l = hypot(b.width(), b.height()) / 2 + 2
    val ca = cos(angle * PI.toFloat() / 180f); val sa = sin(angle * PI.toFloat() / 180f)
    var d = -l
    while (d <= l) {
        val x0 = xc - sa * d; val y0 = yc + ca * d
        c.drawLine(x0 - ca * l, y0 - sa * l, x0 + ca * l, y0 + sa * l, p)
        d += esp
    }
    c.restore()
}

class Gravure(private val nom: String, private val courbure: Float) {
    private val r = Random(23)
    private fun entre(a: Float, b: Float) = a + (b - a) * r.nextFloat()
    private val densites = IntArray(400) { r.nextInt(3) }                  // la « couleur » de chaque verre : 3 densités de hachures
    private val livres = List(500) { floatArrayOf(entre(0.012f, 0.026f), entre(0.72f, 0.95f), r.nextInt(3).toFloat(), if (r.nextFloat() < 0.12f) entre(-0.12f, 0.12f) else 0f) }
    private val alphabet = "abcdefghijklmnopqrstuvwxyzéèàçœ&§¶ABCDEFGHIJ"
    private val lettres = List(30) { floatArrayOf(entre(-0.25f, 0.25f), r.nextFloat(), entre(0.7f, 1.4f), r.nextFloat() * 6.28f) }
    private val caracteres = List(30) { alphabet[r.nextInt(alphabet.length)].toString() }
    private val poussieres = List(30) { floatArrayOf(r.nextFloat(), entre(0.05f, 0.6f), r.nextFloat() * 6.28f) }
    private val reperes = List(6) { floatArrayOf(entre(0.1f, 0.9f), entre(0.12f, 0.5f)) }
    private val etiquettes = listOf("e⁻", "e⁺", "μ⁻", "π⁺", "", "", "", "", "")
    // les traces en spirale des particules : 131 points (x, y) à la suite
    private val traces = List(9) {
        var ang = -PI.toFloat() / 2 + entre(-1.25f, 1.25f); var rr = entre(0.07f, 0.22f)
        val sens = if (r.nextBoolean()) 1f else -1f
        val pts = FloatArray(262)
        var x = 0f; var y = 0f
        for (s in 1..130) {                        // la particule tourne de plus en plus serré : une spirale
            x += cos(ang) * 0.004f; y += sin(ang) * 0.004f
            ang += sens * 0.004f / rr; rr *= 0.984f
            pts[2 * s] = x; pts[2 * s + 1] = y
        }
        pts
    }

    fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val m = min(w, h); val cx = w / 2
        val rayon = courbure * w
        val hautRepos = 0.66f * h
        val hautFin = sqrt(rayon * rayon - cx * cx) - rayon - 0.04f * h
        var haut = h * 1.04f + (hautRepos - h * 1.04f) * doux(intro)
        if (rev > 0f) haut = hautRepos + (hautFin - hautRepos) * doux(rev)
        val cy = haut + rayon
        val app = doux(intro); val vis = app * (1f - rev)
        val tr = doux(borne(sec / 0.6f))                 // l'impression avance du centre vers les bords
        val tmax = asin(min(1f, (w / 2 + 20f * dp) / rayon))
        fun encre(a: Float, ep: Float = 1f) = o.pinceau(avecAlpha(ENCRE, a), ep * dp)
        fun pt(rr: Float, th: Float) = floatArrayOf(cx + rr * sin(th), cy - rr * cos(th))
        fun crete(x: Float) = cy - sqrt(max(0f, rayon * rayon - (x - cx) * (x - cx)))
        fun ligne(a: FloatArray, b: FloatArray, p: Paint) = c.drawLine(a[0], a[1], b[0], b[1], p)

        // ---------- le papier sombre et le cadre de la planche ----------
        c.drawColor(FOND)
        val papier = o.pinceau()
        papier.shader = LinearGradient(0f, 0f, 0f, haut, FOND, avecAlpha(FOND2, 0.3f + 0.7f * app), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, haut + 1f, papier)
        val mg = 10f * dp
        c.drawRect(mg, mg, w - mg, h - mg, encre(0.55f * vis, 1.4f))
        c.drawRect(mg + 4f * dp, mg + 4f * dp, w - mg - 4f * dp, h - mg - 4f * dp, encre(0.35f * vis, 0.7f))
        val titres = when (nom) {
            "Vitrail" -> "PL. XII — LA ROSACE" to "Verrière gothique, d'après nature"
            "Accélérateur" -> "FIG. 2 — COLLISION DE PARTICULES" to "Traces relevées dans une chambre à bulles"
            else -> "PL. VII — LA BIBLIOTHÈQUE" to "Rayonnages et livre ouvert"
        }
        ecrireGravure(c, titres.first, cx, mg + 0.05f * h, 0.034f * m, avecAlpha(ENCRE, 0.85f * vis), CAPITALES)
        ecrireGravure(c, titres.second, cx, mg + 0.05f * h + 0.045f * m, 0.028f * m, avecAlpha(ENCRE, 0.6f * vis))

        // tout le reste s'imprime depuis le centre : on n'affiche qu'une bande qui s'élargit
        c.save()
        val demi = (0.1f + 0.6f * tr) * w
        c.clipRect(cx - demi, 0f, cx + demi, h)

        // ---------- le ciel ----------
        when (nom) {
            "Vitrail" -> {
                for (sgn in intArrayOf(-1, 1)) {      // l'arc brisé en pierre, avec ses joints
                    val p0 = FloatArray(4); val p1 = FloatArray(4); val p2 = FloatArray(4); val xs = FloatArray(2)
                    for ((k, ecart) in floatArrayOf(0f, 0.03f * w).withIndex()) {
                        val xb = cx + sgn * (0.46f * w - ecart)
                        xs[k] = xb
                        p0[2 * k] = xb; p0[2 * k + 1] = haut - 0.15f * h
                        p1[2 * k] = xb; p1[2 * k + 1] = haut - 0.42f * h
                        p2[2 * k] = cx; p2[2 * k + 1] = haut - 0.52f * h + ecart
                        val arc = Path(); arc.moveTo(xb, h); arc.lineTo(p0[2 * k], p0[2 * k + 1])
                        arc.quadTo(p1[2 * k], p1[2 * k + 1], p2[2 * k], p2[2 * k + 1])
                        c.drawPath(arc, encre(0.8f * vis, 1.3f))
                    }
                    for (k in 1 until 12) {            // les claveaux de l'arc : un point sur chaque courbe, reliés
                        val t = k / 12f; val u = 1 - t
                        fun q(j: Int) = floatArrayOf(u * u * p0[2 * j] + 2 * u * t * p1[2 * j] + t * t * p2[2 * j], u * u * p0[2 * j + 1] + 2 * u * t * p1[2 * j + 1] + t * t * p2[2 * j + 1])
                        ligne(q(0), q(1), encre(0.55f * vis, 0.9f))
                    }
                    var y = haut - 0.15f * h          // les assises des piédroits
                    while (y < h) { c.drawLine(xs[0], y, xs[1], y, encre(0.45f * vis, 0.8f)); y += 0.045f * h }
                }
                for (k in -2..2) {                     // des rayons de lumière, en hachures légères
                    val th = k * tmax / 2.6f
                    val p0 = pt(rayon, th); val yh = haut - 0.7f * h; val dx = k * 0.09f * w
                    val ray = Path(); ray.moveTo(p0[0] - 0.05f * w, p0[1]); ray.lineTo(p0[0] + 0.05f * w, p0[1])
                    ray.lineTo(p0[0] + dx + 0.12f * w, yh); ray.lineTo(p0[0] + dx - 0.12f * w, yh); ray.close()
                    hachures(c, ray, 90f + k * 4f, 7f * dp, encre(0.16f * vis * (0.8f + 0.2f * sin(sec * 1.5f + k)), 0.7f))
                }
                val grain = o.pinceau(avecAlpha(ENCRE, 0.5f * vis))
                for (pq in poussieres)                 // la poussière qui danse dans la lumière
                    c.drawCircle((pq[0] + 0.01f * sin(sec * 0.6f + pq[2])) * w, haut - pq[1] * h - sec * 5f * dp, 1.1f * dp, grain)
            }
            "Accélérateur" -> {
                for (rp in reperes) {                  // les croix de repère, comme sur les clichés
                    val x = rp[0] * w; val y = haut - rp[1] * h; val s = 0.012f * m
                    c.drawLine(x - s, y, x + s, y, encre(0.5f * vis)); c.drawLine(x, y - s, x, y + s, encre(0.5f * vis))
                }
                val xe = mg + 0.08f * w; val ye = haut - 0.06f * h          // une échelle graduée
                c.drawLine(xe, ye, xe + 0.15f * w, ye, encre(0.6f * vis, 1.2f))
                for (k in 0 until 6) c.drawLine(xe + k * 0.03f * w, ye, xe + k * 0.03f * w, ye - (if (k % 5 == 0) 6f else 3f) * dp, encre(0.6f * vis))
                ecrireGravure(c, "1 cm", xe + 0.075f * w, ye - 0.02f * h, 0.024f * m, avecAlpha(ENCRE, 0.6f * vis))
                val apres = sec - 0.45f                 // la collision a lieu à 0,45 s
                val yc = haut - 0.012f * h
                if (apres > 0f) {
                    for (k in 0 until 12) {            // l'étoile de la collision, en traits gravés
                        val a = k * PI.toFloat() / 6; val l1 = 0.012f * m; val l2 = (0.03f + 0.012f * (k % 2)) * m
                        c.drawLine(cx + l1 * cos(a), yc + l1 * sin(a), cx + l2 * cos(a), yc + l2 * sin(a), encre(0.8f * vis))
                    }
                    val e = 1.8f * m                    // l'échelle des traces
                    for ((k, pts) in traces.withIndex()) {   // les traces en spirale, tracées à l'encre
                        val nb = (pts.size / 2 * borne(apres / 0.55f)).toInt()
                        if (nb < 2) continue
                        val tp = Path(); tp.moveTo(cx + pts[0] * e, yc + pts[1] * e)
                        for (j in 1 until nb) tp.lineTo(cx + pts[2 * j] * e, yc + pts[2 * j + 1] * e)
                        c.drawPath(tp, encre(0.85f * vis, 1.1f))
                        if (etiquettes[k].isNotEmpty() && nb > pts.size / 4) {   // le nom de la particule
                            val j = (pts.size / 2 * 0.45f).toInt()
                            ecrireGravure(c, etiquettes[k], cx + pts[2 * j] * e + 0.03f * m, yc + pts[2 * j + 1] * e - 0.02f * m, 0.034f * m, avecAlpha(ENCRE, 0.85f * vis))
                        }
                    }
                }
            }
            else -> {                                   // la bibliothèque
                val lx = cx; val ly = haut - 0.42f * h; val s = 0.06f * m   // la lampe suspendue
                c.drawLine(lx, mg, lx, ly - 0.04f * m, encre(0.7f * vis))
                val lampe = Path()
                lampe.moveTo(lx - s * 0.2f, ly - s * 0.7f); lampe.lineTo(lx + s * 0.2f, ly - s * 0.7f)
                lampe.quadTo(lx + s * 0.4f, ly - s * 0.1f, lx + s, ly); lampe.lineTo(lx - s, ly)
                lampe.quadTo(lx - s * 0.4f, ly - s * 0.1f, lx - s * 0.2f, ly - s * 0.7f); lampe.close()
                hachures(c, lampe, 70f, 2.5f * dp, encre(0.7f * vis, 0.8f)); c.drawPath(lampe, encre(0.9f * vis, 1.2f))
                val cone = Path(); cone.moveTo(lx - s, ly); cone.lineTo(lx + s, ly); cone.lineTo(lx + 0.45f * w, haut); cone.lineTo(lx - 0.45f * w, haut); cone.close()
                hachures(c, cone, 90f, 8f * dp, encre(0.12f * vis, 0.7f))     // le cône de lumière
                for ((k, l) in lettres.withIndex()) {  // les lettres qui s'envolent du livre
                    val f = modulo(l[1] + sec * 0.35f, 1f)
                    val x = cx + (l[0] + 0.15f * sin(l[3] + f * 4f)) * w * (0.3f + f)
                    val y = haut - 0.06f * h - f * 0.5f * h
                    ecrireGravure(c, caracteres[k], x, y, 0.04f * m * l[2], avecAlpha(ENCRE, vis * sin(PI.toFloat() * f) * 0.9f))
                }
            }
        }

        // ---------- la courbe ----------
        var rCorps = rayon
        if (rev > 0f) {
            val front = doux(min(1f, rev / 0.9f)) * h * 1.3f
            rCorps = max(0f, rayon - front)
            c.drawCircle(cx, cy, rayon, o.gomme())
        }
        if (rCorps > 0f) {
            c.drawCircle(cx, cy, rCorps, o.pinceau(SOL))
            val chemin = Path(); chemin.addCircle(cx, cy, rCorps, Path.Direction.CW)
            c.save(); c.clipPath(chemin)
            interieur(c, o, cx, rayon, haut, w, h, m, dp, sec, app, tmax, { a, ep -> encre(a, ep) }, { rr, th -> pt(rr, th) })
            c.restore()
            c.drawCircle(cx, cy, rCorps, encre(0.9f * vis, 1.6f))                // le bord, en double trait
            c.drawCircle(cx, cy, rCorps - 4f * dp, encre(0.5f * vis, 0.8f))
        }

        // ---------- au-dessus de la courbe ----------
        if (nom == "Accélérateur" && rCorps > 0f && sec < 0.45f) {   // les deux paquets de particules qui foncent
            val rt = rCorps - 0.012f * h
            val f = doux(borne(sec / 0.45f))
            for (sens in floatArrayOf(1f, -1f)) {
                val th = sens * -tmax * (1f - f)
                val p = pt(rt, th)
                c.drawCircle(p[0], p[1], 3.2f * dp, o.pinceau(avecAlpha(ENCRE, vis)))
                for (q in 1..3) ligne(pt(rt + (q - 2) * 3f * dp, th - sens * 0.05f), pt(rt + (q - 2) * 3f * dp, th - sens * 0.015f), encre(0.6f * vis, 0.8f))
            }
        }
        if (nom == "Bibliothèque" && rev < 0.25f) {    // le livre ouvert, gravé, qui tourne ses pages
            val fondu = (1f - rev * 4f) * app
            val bx = cx; val by = crete(cx) + 2f * dp
            val L = 0.3f * m; val H = 0.11f * m
            val fond = o.pinceau(SOL)
            for (sgn in floatArrayOf(-1f, 1f)) {
                val page = Path()
                page.moveTo(bx, by); page.quadTo(bx + sgn * L * 0.5f, by - H * 0.35f, bx + sgn * L, by - H * 0.15f)
                page.lineTo(bx + sgn * L, by - H * 1.15f); page.quadTo(bx + sgn * L * 0.5f, by - H * 1.35f, bx, by - H); page.close()
                c.drawPath(page, fond)
                c.drawPath(page, encre(fondu, 1.3f))
                for (li in 0 until 7) {               // les lignes de texte
                    val fr = 0.22f + li * 0.1f
                    val y0 = by - H * fr; val yb = by - H * 0.15f - H * fr
                    val q = Path(); q.moveTo(bx + sgn * L * 0.12f, y0 - H * 0.06f)
                    q.quadTo(bx + sgn * L * 0.5f, y0 - H * 0.3f, bx + sgn * L * 0.86f, yb + H * 0.02f)
                    c.drawPath(q, encre(0.55f * fondu, 0.8f))
                }
            }
            val tour = modulo(sec * 0.8f, 1f)         // une page qui se tourne
            val px = bx + L * cos(PI.toFloat() * tour); val bosse = H * 0.3f * sin(PI.toFloat() * tour)
            val page = Path()
            page.moveTo(bx, by); page.quadTo((bx + px) / 2, by - H * 0.35f - bosse, px, by - H * 0.15f)
            page.lineTo(px, by - H * 1.15f); page.quadTo((bx + px) / 2, by - H * 1.35f - bosse, bx, by - H); page.close()
            c.drawPath(page, fond)
            hachures(c, page, 20f, 4f * dp, encre(0.3f * fondu, 0.7f))
            c.drawPath(page, encre(fondu, 1.2f))
        }
        c.restore()     // fin de l'impression

        // ---------- la révélation : un liseré sépia descend sur la courbe ----------
        if (rev > 0f) {
            val r0 = rCorps; val halo = 40f * dp; val rg = r0 + halo
            if (rg > halo) {
                val a = 1f - rev
                val ps = floatArrayOf(borne((r0 - halo) / rg), borne((r0 - 6f * dp) / rg), borne(r0 / rg), borne((r0 + 6f * dp) / rg), 1f)
                for (k in 1 until ps.size) if (ps[k] <= ps[k - 1]) ps[k] = min(1f, ps[k - 1] + 0.0001f)
                val l = o.lumiere()
                l.shader = RadialGradient(cx, cy, rg,
                    intArrayOf(0, argb(70f * a, 110, 95, 70), avecAlpha(ENCRE, 220f * a / 255f), argb(70f * a, 110, 95, 70), 0), ps, Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, rg, l)
            }
        }
    }

    // Ce qui est gravé à l'intérieur de la courbe.
    private fun interieur(c: Canvas, o: Outils, cx: Float, rayon: Float, haut: Float, w: Float, h: Float, m: Float, dp: Float, sec: Float, app: Float, tmax: Float,
                          encre: (Float, Float) -> Paint, pt: (Float, Float) -> FloatArray) {
        fun ligne(a: FloatArray, b: FloatArray, p: Paint) = c.drawLine(a[0], a[1], b[0], b[1], p)
        val cy = haut + rayon
        when (nom) {
            "Vitrail" -> {
                // la rosace gravée : la « couleur » de chaque verre est rendue par la densité des hachures
                fun hach(path: Path, i: Int, k: Int) {
                    val d = densites[((i % 400) + 400) % 400]
                    val esp = floatArrayOf(3.2f, 4.6f, 6.5f)[d] * dp
                    val sens = if ((i + k) % 2 == 0) 45f else -45f
                    hachures(c, path, sens, esp, encre(0.5f * app, 0.7f))
                    if (d == 0) hachures(c, path, -sens, esp * 1.3f, encre(0.35f * app, 0.6f))   // les verres sombres : hachures croisées
                }
                val rHaut = rayon - 0.022f * h; val rBas = rayon - 0.17f * h
                val dth = 0.36f * (rHaut - rBas) / rayon
                val n = (tmax / dth).toInt() + 1
                val fond = o.pinceau(SOL)
                for (j in -n..n) {                    // les lancettes en ogive, coiffées d'un trèfle
                    val th = j * dth; val g0 = th - dth * 0.38f; val g1 = th + dth * 0.38f
                    val rEp = rHaut - 0.045f * h
                    val lan = Path()
                    var p = pt(rBas, g0); lan.moveTo(p[0], p[1])
                    p = pt(rEp, g0); lan.lineTo(p[0], p[1])
                    var cp = pt(rHaut - 0.008f * h, g0); p = pt(rHaut, th); lan.quadTo(cp[0], cp[1], p[0], p[1])
                    cp = pt(rHaut - 0.008f * h, g1); p = pt(rEp, g1); lan.quadTo(cp[0], cp[1], p[0], p[1])
                    p = pt(rBas, g1); lan.lineTo(p[0], p[1]); lan.close()
                    hach(lan, j + 50, 0)
                    c.drawPath(lan, encre(0.9f * app, 1.4f))
                    ligne(pt(rBas, th), pt(rEp - 0.01f * h, th), encre(0.8f * app, 1f))
                    for (f in floatArrayOf(0.33f, 0.66f)) ligne(pt(mix(rBas, rEp, f), g0), pt(mix(rBas, rEp, f), g1), encre(0.8f * app, 1f))
                    val rl = 0.24f * dth * rEp
                    for (lobe in arrayOf(floatArrayOf(-1f, 0f), floatArrayOf(1f, 0f), floatArrayOf(0f, 1.3f))) {
                        val lp = pt(rEp - 0.004f * h + lobe[1] * rl, th + lobe[0] * rl * 0.95f / rEp)
                        c.drawCircle(lp[0], lp[1], rl, fond); c.drawCircle(lp[0], lp[1], rl, encre(0.85f * app, 1f))
                    }
                    val qd = pt(rHaut - 0.012f * h, th + dth / 2); val rq = 0.0065f * m      // un quadrilobe entre deux lancettes
                    for (d in arrayOf(floatArrayOf(-1f, 0f), floatArrayOf(1f, 0f), floatArrayOf(0f, -1f), floatArrayOf(0f, 1f)))
                        c.drawCircle(qd[0] + d[0] * rq, qd[1] + d[1] * rq, rq, encre(0.8f * app, 0.9f))
                }
                val rMed = rayon - 0.215f * h; val rm = 0.03f * m      // les médaillons et leur fleur
                val dth2 = 2.5f * rm / rMed
                val n2 = (tmax / dth2).toInt() + 1
                val ovale = RectF(-rm * 0.17f, -rm * 0.6f, rm * 0.17f, -rm * 0.1f)
                for (j in -n2..n2) {
                    val p = pt(rMed, j * dth2 + dth2 / 2)
                    val cercle = Path(); cercle.addCircle(p[0], p[1], rm, Path.Direction.CW)
                    hach(cercle, j + 120, 1)
                    c.drawCircle(p[0], p[1], rm * 0.62f, fond)
                    for (pe in 0 until 6) {
                        c.save(); c.translate(p[0], p[1]); c.rotate(pe * 60f + j * 7f)
                        c.drawOval(ovale, encre(0.85f * app, 0.9f))
                        c.restore()
                    }
                    c.drawCircle(p[0], p[1], rm, encre(0.9f * app, 1.3f)); c.drawCircle(p[0], p[1], rm * 0.62f, encre(0.6f * app, 0.8f))
                }
                for (rr in floatArrayOf(rBas, rBas - 4f * dp, rMed + rm + 4f * dp, rMed - rm - 4f * dp)) c.drawCircle(cx, cy, rr, encre(0.75f * app, 1.1f))
                for (k in 0 until 4) {                // la mosaïque de losanges
                    val rOut = rMed - rm - 0.012f * h - k * 0.045f * h; val rIn = rOut - 0.045f * h
                    val d3 = 0.06f * w / rOut
                    val n3 = (tmax / d3).toInt() + 1
                    for (j in -n3..n3) {
                        val a0 = j * d3 + (if (k % 2 == 1) d3 / 2 else 0f)
                        val cell = Path()
                        var p = pt(rOut, a0); cell.moveTo(p[0], p[1])
                        p = pt((rOut + rIn) / 2, a0 + d3 / 2); cell.lineTo(p[0], p[1])
                        p = pt(rIn, a0); cell.lineTo(p[0], p[1])
                        p = pt((rOut + rIn) / 2, a0 - d3 / 2); cell.lineTo(p[0], p[1]); cell.close()
                        if ((j + k) % 2 == 0) hach(cell, j * 7 + k * 31 + 200, k)
                        c.drawPath(cell, encre(0.75f * app, 1f))
                    }
                }
                val xs = -0.4f * w + modulo(sec * 0.45f, 1f) * 1.8f * w      // le reflet du soleil qui glisse
                val lum = o.lumiere()
                lum.shader = LinearGradient(xs - 0.25f * w, haut, xs + 0.25f * w, haut + 0.3f * h,
                    intArrayOf(avecAlpha(ENCRE, 0f), avecAlpha(ENCRE, 0.07f * app), avecAlpha(ENCRE, 0f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, haut - 2f, w, h * 1.2f, lum)
            }
            "Accélérateur" -> {
                // l'anneau vu en coupe, comme sur une planche technique : le tube, les aimants, le détecteur
                for (rk in arrayOf(floatArrayOf(1f, 0.8f), floatArrayOf(2f, 0.5f))) c.drawCircle(cx, cy, rayon - rk[0] * 0.03f * h, encre(rk[1] * app, 0.8f))
                val n = 18
                for (j in -n..n) {                    // les aimants : dipôles hachurés, quadripôles croisés
                    val th = j * tmax / n * 1.05f
                    val p = pt(rayon - 0.05f * h, th)
                    c.save(); c.translate(p[0], p[1]); c.rotate(th * 180f / PI.toFloat())
                    val aimant = Path(); aimant.addRect(-0.02f * m, -0.018f * h, 0.02f * m, 0.018f * h, Path.Direction.CW)
                    hachures(c, aimant, 45f, 3f * dp, encre(0.6f * app, 0.7f))
                    if (j % 2 != 0) hachures(c, aimant, -45f, 3f * dp, encre(0.5f * app, 0.7f))
                    c.drawPath(aimant, encre(0.9f * app, 1.1f))
                    c.restore()
                }
                val couches = arrayOf(floatArrayOf(0.14f, 0.2f, 3f), floatArrayOf(0.22f, 0.3f, 4.5f), floatArrayOf(0.32f, 0.42f, 6f))
                for ((k, cou) in couches.withIndex()) {   // les couches du détecteur, découpées en cellules
                    val ra = rayon - cou[0] * h; val rb = rayon - cou[1] * h
                    c.drawCircle(cx, cy, ra, encre(0.7f * app, 1.1f)); c.drawCircle(cx, cy, rb, encre(0.7f * app, 1.1f))
                    val d = cou[2] * PI.toFloat() / 180f
                    val nn = (tmax / d).toInt() + 1
                    for (j in -nn..nn + 1) {
                        val a0 = j * d + (if (k % 2 == 1) d / 2 else 0f)
                        ligne(pt(ra, a0), pt(rb, a0), encre(0.6f * app, 0.8f))
                        if ((j + k) % 2 == 0) {        // une cellule sur deux, hachurée
                            val cel = Path()
                            var p = pt(ra, a0); cel.moveTo(p[0], p[1])
                            p = pt(ra, a0 + d); cel.lineTo(p[0], p[1])
                            p = pt(rb, a0 + d); cel.lineTo(p[0], p[1])
                            p = pt(rb, a0); cel.lineTo(p[0], p[1]); cel.close()
                            hachures(c, cel, 30f + 60f * k, 3.5f * dp, encre(0.4f * app, 0.6f))
                        }
                    }
                }
                for (k in -3..3) {                    // des numéros d'aimants, comme sur un plan
                    val p = pt(rayon - 0.11f * h, k * tmax / 3.6f)
                    ecrireGravure(c, (if (k % 2 == 0) "D" else "Q") + (k + 4), p[0], p[1], 0.026f * m, avecAlpha(ENCRE, 0.6f * app))
                }
            }
            else -> {
                // des étagères gravées : chaque livre est un dos hachuré, avec ses filets
                val hauteurEtagere = 0.075f * h
                var idx = 0
                val fond = o.pinceau(SOL)
                for (k in 0 until 6) {
                    val rBas = rayon - (k + 1) * hauteurEtagere - 0.01f * h
                    c.drawCircle(cx, cy, rBas, encre(0.85f * app, 1.4f))
                    c.drawCircle(cx, cy, rBas - 0.008f * h, encre(0.5f * app, 0.8f))
                    var th = -tmax
                    while (th < tmax) {
                        val lv = livres[idx % 500]; idx++
                        val dth = lv[0] * w / rBas
                        val hauteur = hauteurEtagere * 0.9f * lv[1]
                        val p = pt(rBas + 0.002f * h, th + dth / 2)
                        c.save(); c.translate(p[0], p[1]); c.rotate((th + dth / 2 + lv[3]) * 180f / PI.toFloat())
                        val larg = lv[0] * w * 0.9f
                        val dos = Path(); dos.addRect(-larg / 2, -hauteur, larg / 2, 0f, Path.Direction.CW)
                        hachures(c, dos, 90f, floatArrayOf(2.6f, 3.8f, 5.5f)[lv[2].toInt()] * dp, encre(0.45f * app, 0.6f))
                        c.drawPath(dos, encre(0.85f * app, 1f))
                        for (f in floatArrayOf(0.12f, 0.17f, 0.83f, 0.88f)) c.drawLine(-larg / 2, -hauteur * f, larg / 2, -hauteur * f, encre(0.8f * app, 0.8f))
                        if (idx % 3 == 0) {            // une étiquette sur certains dos
                            c.drawRect(-larg * 0.28f, -hauteur * 0.6f, larg * 0.28f, -hauteur * 0.4f, fond)
                            c.drawRect(-larg * 0.28f, -hauteur * 0.6f, larg * 0.28f, -hauteur * 0.4f, encre(0.8f * app, 0.8f))
                        }
                        c.restore()
                        th += dth
                    }
                }
            }
        }
    }
}
