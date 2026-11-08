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
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
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
// Les couleurs « coloriées à la main » (un lavis sous les hachures), toujours sourdes pour rester sombres.
private val VERRES = intArrayOf(rgb(150, 38, 48), rgb(38, 70, 155), rgb(40, 115, 75), rgb(190, 140, 45), rgb(105, 60, 140))   // rubis, saphir, émeraude, ambre, améthyste
private val OR = rgb(220, 178, 92)
private val PARTICULES = intArrayOf(rgb(110, 170, 255), rgb(240, 110, 100), rgb(120, 210, 140), rgb(240, 170, 80), rgb(180, 140, 230), rgb(110, 205, 215), rgb(230, 200, 120), rgb(200, 120, 190), rgb(150, 190, 120))
private val DIPOLE = rgb(45, 80, 165)
private val QUADRIPOLE = rgb(165, 50, 50)
private val COUCHES = intArrayOf(rgb(60, 150, 170), rgb(70, 150, 80), rgb(190, 120, 50))   // trajectographe, calorimètres
private val CUIRS = intArrayOf(rgb(115, 30, 38), rgb(35, 78, 52), rgb(32, 48, 95), rgb(140, 92, 48), rgb(95, 40, 70), rgb(60, 60, 40))   // bordeaux, vert, marine, fauve, prune, olive
private val BOIS = rgb(90, 55, 30)
private val PIERRE = rgb(170, 135, 92)
// Cellule : cytoplasme, noyau, mitochondries, réticulum, Golgi, têtes de la membrane ; les 4 bases de l'ADN
private val CYTO = rgb(40, 95, 90)
private val NOYAU = rgb(95, 70, 140)
private val MITO = rgb(170, 75, 58)
private val RETI = rgb(60, 140, 130)
private val GOLGI = rgb(185, 145, 62)
private val LIPIDE = rgb(205, 165, 95)
private val BASES = intArrayOf(rgb(205, 95, 85), rgb(110, 175, 105), rgb(95, 135, 205), rgb(215, 175, 75))
// Maths : le lavis bleu de Prusse des épures, le sinus en rouge, le cosinus en bleu
private val EPURE = rgb(35, 58, 100)
private val SINUS = rgb(215, 105, 95)
private val COSINUS = rgb(110, 150, 215)
// Babel : des lettres de toutes les langues (latin, grec, cyrillique, hébreu, arabe, chinois/japonais).
// Android va chercher tout seul une police qui sait dessiner chaque écriture.
private val ECRITURES = listOf("abcdefgh&§¶", "αβγδλπΩΣψ", "бжзЯЮфш", "אבגדהשת", "بتجدسعق", "語書言文字本")
private val TEINTES_ECRITURES = intArrayOf(rgb(220, 178, 92), rgb(150, 180, 230), rgb(225, 150, 140), rgb(150, 200, 150), rgb(215, 160, 210), rgb(200, 200, 160))

// Le coloriage à la main : un aplat transparent posé sous les traits.
private fun lavisBrut(c: Canvas, o: Outils, chemin: Path, couleur: Int, a: Float) = c.drawPath(chemin, o.pinceau(avecAlpha(couleur, a)))

// Écrit un texte centré en (x, y), dans une police à empattements (comme dans les vieux livres).
private fun ecrireGravure(c: Canvas, s: String, x: Float, y: Float, taille: Float, couleur: Int, police: Typeface = ITALIQUE) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.typeface = police; p.textSize = taille; p.color = couleur; p.textAlign = Paint.Align.CENTER
    c.drawText(s, x, y + taille * 0.35f, p)
}

// Remplit une forme de traits parallèles (angle en degrés, esp = écart entre les traits) : les hachures.
private fun hachuresBrutes(c: Canvas, chemin: Path, angle: Float, esp: Float, p: Paint) {
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
    private val densites = IntArray(400) { r.nextInt(3) }                  // 3 densités de hachures pour les verres
    private val teintes = IntArray(400) { r.nextInt(VERRES.size) }          // et la couleur de chaque verre
    private val cuirs = IntArray(500) { r.nextInt(CUIRS.size) }             // la couleur de chaque reliure
    private val livres = List(500) { floatArrayOf(entre(0.012f, 0.026f), entre(0.72f, 0.95f), r.nextInt(3).toFloat(), if (r.nextFloat() < 0.12f) entre(-0.12f, 0.12f) else 0f) }
    private val leger = nom != "Vitrail"          // la collision et Babel : moins de hachures, plus de couleur
    // les lettres de Babel : [écriture, écart, phase, taille, oscillation] et le caractère lui-même
    private val lettres = List(42) { floatArrayOf(r.nextInt(ECRITURES.size).toFloat(), entre(-1f, 1f), r.nextFloat(), entre(0.8f, 1.5f), r.nextFloat() * 6.28f) }
    private val caracteres = lettres.map { l -> val e = ECRITURES[l[0].toInt()]; e[r.nextInt(e.length)].toString() }
    private val nuages = List(4) { floatArrayOf(r.nextFloat(), entre(0.22f, 0.4f), entre(0.12f, 0.2f), entre(-1f, 1f)) }
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
    // la cellule : molécules [x, hauteur, taille, phase, base], anticorps [x, hauteur, phase],
    // mitochondries [x, profondeur, angle, taille], vésicules [x, profondeur, taille], chromatine [x, y, angle]
    private val molecules = List(22) { floatArrayOf(r.nextFloat(), entre(0.08f, 0.6f), entre(0.006f, 0.013f), r.nextFloat() * 6.28f, r.nextInt(4).toFloat()) }
    private val anticorps = List(4) { floatArrayOf(entre(0.08f, 0.92f), entre(0.1f, 0.5f), r.nextFloat() * 6.28f) }
    private val mitos = List(9) { floatArrayOf(entre(-0.45f, 0.45f), entre(0.06f, 0.4f), r.nextFloat() * 3.14f, entre(0.8f, 1.2f)) }
    private val vesicules = List(18) { floatArrayOf(entre(-0.5f, 0.5f), entre(0.04f, 0.42f), entre(0.006f, 0.014f)) }
    private val chromatine = List(14) { floatArrayOf(entre(-0.8f, 0.8f), entre(-0.7f, 0.7f), r.nextFloat() * 6.28f) }

    // Moins gravé pour la collision et Babel : des hachures plus espacées et plus discrètes…
    private fun hachures(c: Canvas, chemin: Path, angle: Float, esp: Float, p: Paint) {
        if (!leger) { hachuresBrutes(c, chemin, angle, esp, p); return }
        val q = Paint(p); q.alpha = (p.alpha * 0.4f).toInt()
        hachuresBrutes(c, chemin, angle, esp * 1.7f, q)
    }
    // … et des couleurs un peu plus franches.
    private fun lavis(c: Canvas, o: Outils, chemin: Path, couleur: Int, a: Float) = lavisBrut(c, o, chemin, couleur, min(0.95f, a * (if (leger) 1.3f else 1f)))

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
        val kE = if (leger) 0.8f else 1f                // l'encre aussi est un peu plus légère
        fun encre(a: Float, ep: Float = 1f, col: Int = ENCRE) = o.pinceau(avecAlpha(col, a * kE), ep * dp)
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
            "Cellule" -> "PL. III — LA CELLULE VIVANTE" to "Coupe vue au microscope"
            "Maths" -> "FIG. 1 — LE NOMBRE D'OR" to "Spirale, sinus et cercle gradué"
            else -> "PL. VII — LA BIBLIOTHÈQUE DE BABEL" to "Tous les livres, dans toutes les langues"
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
                    val teinte = VERRES[(k + 2 + VERRES.size) % VERRES.size]       // chaque rayon prend la couleur d'un verre
                    val lav = o.pinceau()
                    lav.shader = LinearGradient(0f, p0[1], 0f, yh, avecAlpha(teinte, 0.22f * vis), avecAlpha(teinte, 0f), Shader.TileMode.CLAMP)
                    c.drawPath(ray, lav)
                    hachures(c, ray, 90f + k * 4f, 7f * dp, encre(0.2f * vis * (0.8f + 0.2f * sin(sec * 1.5f + k)), 0.7f, teinte))
                }
                val grain = o.pinceau(avecAlpha(OR, 0.55f * vis))
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
                        c.drawLine(cx + l1 * cos(a), yc + l1 * sin(a), cx + l2 * cos(a), yc + l2 * sin(a), encre(0.85f * vis, 1f, OR))
                    }
                    val e = 1.8f * m                    // l'échelle des traces
                    for ((k, pts) in traces.withIndex()) {   // les traces en spirale, une couleur par particule
                        val col = PARTICULES[k]
                        val nb = (pts.size / 2 * borne(apres / 0.55f)).toInt()
                        if (nb < 2) continue
                        val tp = Path(); tp.moveTo(cx + pts[0] * e, yc + pts[1] * e)
                        for (j in 1 until nb) tp.lineTo(cx + pts[2 * j] * e, yc + pts[2 * j + 1] * e)
                        c.drawPath(tp, encre(0.85f * vis, 1.2f, col))
                        if (etiquettes[k].isNotEmpty() && nb > pts.size / 4) {   // le nom de la particule
                            val j = (pts.size / 2 * 0.45f).toInt()
                            ecrireGravure(c, etiquettes[k], cx + pts[2 * j] * e + 0.03f * m, yc + pts[2 * j + 1] * e - 0.02f * m, 0.034f * m, avecAlpha(col, 0.9f * vis))
                        }
                    }
                }
            }
            "Cellule" -> cielCellule(c, o, haut, w, h, m, sec, vis) { a, ep, col -> encre(a, ep, col) }
            "Maths" -> cielMaths(c, o, cx, haut, w, h, m, dp, sec, vis) { a, ep, col -> encre(a, ep, col) }
            else -> babel(c, o, cx, crete(cx) + 2f * dp, w, h, m, dp, sec, vis) { a, ep, col -> encre(a, ep, col) }   // la tour de Babel
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
            interieur(c, o, cx, rayon, haut, w, h, m, dp, sec, app, tmax, { a, ep, col -> encre(a, ep, col) }, { rr, th -> pt(rr, th) })
            c.restore()
            c.drawCircle(cx, cy, rCorps, encre(0.9f * vis, 1.6f))                // le bord, en double trait
            c.drawCircle(cx, cy, rCorps - 4f * dp, encre(0.5f * vis, 0.8f))
        }

        // ---------- au-dessus de la courbe ----------
        if (nom == "Cellule" && rCorps > 0f)
            membrane(c, o, rCorps, h, m, dp, sec, vis, tmax, { a, ep, col -> encre(a, ep, col) }, { rr, th -> pt(rr, th) })
        if (nom == "Maths" && rCorps > 0f)
            cercleGradue(c, o, cx, cy, rCorps, w, h, m, dp, sec, vis, tmax, { a, ep, col -> encre(a, ep, col) }, { rr, th -> pt(rr, th) })
        if (nom == "Bibliothèque" && rev < 0.3f)                  // la ville au pied de la tour
            ville(c, o, cx, { x -> crete(x) }, w, m, dp, sec, vis * (1f - rev / 0.3f)) { a, ep, col -> encre(a, ep, col) }
        if (nom == "Accélérateur" && rCorps > 0f && sec < 0.45f) {   // les deux paquets de particules qui foncent
            val rt = rCorps - 0.012f * h
            val f = doux(borne(sec / 0.45f))
            for (sens in floatArrayOf(1f, -1f)) {
                val th = sens * -tmax * (1f - f)
                val p = pt(rt, th)
                val col = if (sens > 0f) PARTICULES[0] else PARTICULES[1]     // un paquet bleu, un paquet rouge
                c.drawCircle(p[0], p[1], 3.2f * dp, o.pinceau(avecAlpha(col, vis)))
                for (q in 1..3) ligne(pt(rt + (q - 2) * 3f * dp, th - sens * 0.05f), pt(rt + (q - 2) * 3f * dp, th - sens * 0.015f), encre(0.6f * vis, 0.8f, col))
            }
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
                          encreC: (Float, Float, Int) -> Paint, pt: (Float, Float) -> FloatArray) {
        fun encre(a: Float, ep: Float, col: Int = ENCRE) = encreC(a, ep, col)
        fun ligne(a: FloatArray, b: FloatArray, p: Paint) = c.drawLine(a[0], a[1], b[0], b[1], p)
        val cy = haut + rayon
        when (nom) {
            "Vitrail" -> {
                // la rosace gravée : la « couleur » de chaque verre est rendue par la densité des hachures
                fun hach(path: Path, i: Int, k: Int) {
                    val n400 = ((i % 400) + 400) % 400
                    val d = densites[n400]
                    val esp = floatArrayOf(3.2f, 4.6f, 6.5f)[d] * dp
                    lavis(c, o, path, VERRES[teintes[n400]], floatArrayOf(0.75f, 0.62f, 0.5f)[d] * app)   // la couleur du verre
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
                    c.drawCircle(p[0], p[1], rm * 0.62f, o.pinceau(avecAlpha(OR, 0.28f * app)))   // le cœur doré du médaillon
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
                    intArrayOf(avecAlpha(OR, 0f), avecAlpha(OR, 0.09f * app), avecAlpha(OR, 0f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
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
                    lavis(c, o, aimant, if (j % 2 != 0) QUADRIPOLE else DIPOLE, 0.7f * app)   // dipôles bleus, quadripôles rouges
                    hachures(c, aimant, 45f, 3f * dp, encre(0.6f * app, 0.7f))
                    if (j % 2 != 0) hachures(c, aimant, -45f, 3f * dp, encre(0.5f * app, 0.7f))
                    c.drawPath(aimant, encre(0.9f * app, 1.1f))
                    c.restore()
                }
                val couches = arrayOf(floatArrayOf(0.14f, 0.2f, 3f), floatArrayOf(0.22f, 0.3f, 4.5f), floatArrayOf(0.32f, 0.42f, 6f))
                for ((k, cou) in couches.withIndex()) {   // les couches du détecteur, découpées en cellules
                    val ra = rayon - cou[0] * h; val rb = rayon - cou[1] * h
                    val anneau = Path(); anneau.addCircle(cx, cy, ra, Path.Direction.CW); anneau.addCircle(cx, cy, rb, Path.Direction.CW)
                    anneau.fillType = Path.FillType.EVEN_ODD
                    lavis(c, o, anneau, COUCHES[k], 0.22f * app)          // chaque couche du détecteur a sa teinte
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
                            lavis(c, o, cel, COUCHES[k], 0.3f * app)
                            hachures(c, cel, 30f + 60f * k, 3.5f * dp, encre(0.4f * app, 0.6f, COUCHES[k]))
                        }
                    }
                }
                for (k in -3..3) {                    // des numéros d'aimants, comme sur un plan
                    val p = pt(rayon - 0.11f * h, k * tmax / 3.6f)
                    ecrireGravure(c, (if (k % 2 == 0) "D" else "Q") + (k + 4), p[0], p[1], 0.026f * m, avecAlpha(ENCRE, 0.6f * app))
                }
            }
            "Cellule" -> intCellule(c, o, cx, cy, rayon, haut, w, h, m, dp, sec, app, encreC)
            "Maths" -> intMaths(c, o, cx, cy, rayon, haut, w, h, app, tmax, encreC, pt)
            else -> {
                // sous la courbe : les galeries hexagonales de la Bibliothèque de Babel (Borges),
                // chacune tapissée de livres, avec son puits central bordé d'une rambarde
                val R = 0.11f * m; val hx = sqrt(3f) * R
                val fond = o.pinceau(SOL); val vide = o.pinceau(FOND)
                fun hexagone(x: Float, y: Float, r: Float): Path {
                    val p = Path()
                    for (k in 0 until 6) {
                        val a = k * PI.toFloat() / 3
                        if (k == 0) p.moveTo(x + r * cos(a), y + r * sin(a)) else p.lineTo(x + r * cos(a), y + r * sin(a))
                    }
                    p.close(); return p
                }
                var colonne = 0; var xc = -R
                while (xc < w + R) {
                    var yc = haut - 0.01f * h + (if (colonne % 2 == 1) hx / 2 else 0f)
                    while (yc < h + hx) {
                        val hexa = hexagone(xc, yc, R); val dedans = hexagone(xc, yc, 0.84f * R)
                        lavis(c, o, hexa, BOIS, 0.55f * app)              // le cadre en bois
                        c.drawPath(dedans, fond)
                        c.save(); c.clipPath(dedans)
                        var idx = modulo((colonne * 37 + yc.toInt()).toFloat(), 500f).toInt()
                        for (rang in 0 until 4) {                     // quatre rayonnages de livres
                            val yb = yc - 0.84f * hx / 2 + (rang + 1) * 0.84f * hx / 4
                            val hl = 0.84f * hx / 4 * 0.85f
                            var x = xc - 0.84f * R
                            while (x < xc + 0.84f * R) {
                                val lv = livres[idx % 500]; idx++
                                val lb = lv[0] * w * 0.55f; val hb = hl * lv[1]
                                val dos = Path(); dos.addRect(x, yb - hb, x + lb * 0.92f, yb, Path.Direction.CW)
                                lavis(c, o, dos, CUIRS[cuirs[idx % 500]], 0.8f * app)
                                c.drawLine(x, yb - hb * 0.85f, x + lb * 0.92f, yb - hb * 0.85f, encre(0.6f * app, 0.6f, OR))
                                x += lb
                            }
                            c.drawLine(xc - R, yb, xc + R, yb, encre(0.7f * app, 1f))
                        }
                        c.restore()
                        val puits = 0.26f * R                          // le puits central et sa rambarde
                        c.drawCircle(xc, yc, puits, vide)
                        c.drawCircle(xc, yc, puits, encre(0.85f * app, 1.2f))
                        for (k in 0 until 12) {
                            val a = k * PI.toFloat() / 6
                            c.drawLine(xc + puits * cos(a), yc + puits * sin(a), xc + puits * 1.25f * cos(a), yc + puits * 1.25f * sin(a), encre(0.6f * app, 0.8f))
                        }
                        c.drawCircle(xc, yc, puits * 1.25f, encre(0.7f * app, 0.9f))
                        c.drawPath(hexa, encre(0.9f * app, 1.4f)); c.drawPath(dedans, encre(0.6f * app, 0.8f))
                        yc += hx
                    }
                    xc += 1.5f * R; colonne++
                }
            }
        }
    }

    // La tour de Babel, à la manière de Bruegel : une tour ronde en volume, éclairée par le couchant,
    // qui se construit étage par étage et transperce les nuages. Des lettres de toutes les langues
    // s'en échappent : la confusion des langues.
    private fun babel(c: Canvas, o: Outils, cx: Float, base: Float, w: Float, h: Float, m: Float, dp: Float, sec: Float, vis: Float,
                      encreC: (Float, Float, Int) -> Paint) {
        fun encre(a: Float, ep: Float, col: Int = ENCRE) = encreC(a, ep, col)
        // --- le ciel du couchant, à l'intérieur du cadre et sous la légende
        val mg = 16f * dp
        val pmil = min(0.9f, max(0.05f, 0.18f * h / max(1f, base - 0.12f * h)))
        val ciel = o.pinceau()
        ciel.shader = LinearGradient(0f, 0.12f * h, 0f, base,
            intArrayOf(avecAlpha(rgb(22, 20, 34), 0f), avecAlpha(rgb(26, 22, 34), vis), avecAlpha(rgb(130, 82, 45), vis)), floatArrayOf(0f, pmil, 1f), Shader.TileMode.CLAMP)
        c.drawRect(mg, mg, w - mg, base + 2f, ciel)
        val sx = cx - 0.3f * w; val sy = base - 0.12f * h            // le soleil bas, derrière la tour
        o.halo(c, sx, sy, 0.35f * m, 0.25f * vis, rgb(230, 150, 80))
        c.drawCircle(sx, sy, 0.045f * m, o.pinceau(avecAlpha(rgb(235, 170, 100), 0.55f * vis)))
        val collines = arrayOf(floatArrayOf(-0.55f, 0.05f), floatArrayOf(0.6f, 0.065f), floatArrayOf(-0.15f, 0.03f))
        val teintesCollines = intArrayOf(rgb(60, 45, 50), rgb(52, 40, 46), rgb(75, 52, 50))
        for ((k, col) in collines.withIndex())                       // des collines au loin
            c.drawOval(RectF(cx + col[0] * w - 0.5f * w, base - col[1] * h, cx + col[0] * w + 0.5f * w, base + col[1] * h), o.pinceau(avecAlpha(teintesCollines[k], vis)))
        for (k in 0 until 5) {                                       // des oiseaux
            val bx = modulo(0.15f + 0.17f * k + sec * 0.03f, 1f) * w; val by = base - (0.3f + 0.05f * sin(k * 2.3f)) * h
            val b = 3.5f * dp; val bat = sin(sec * 8f + k) * 0.6f
            val ois = Path(); ois.moveTo(bx - b, by - b * bat); ois.quadTo(bx - b * 0.4f, by - b * 0.5f, bx, by); ois.quadTo(bx + b * 0.4f, by - b * 0.5f, bx + b, by - b * bat)
            c.drawPath(ois, encre(0.6f * vis, 1.1f))
        }
        nuagesBabel(c, o, base, w, h, sec, vis, encreC, true)

        // --- la tour : des étages ronds (des cylindres vus de face), de plus en plus étroits
        val etages = 9; val hE = 0.045f * h; val lb0 = 0.42f * m
        val croissance = borne(sec / 0.9f) * etages
        val bombe = 0.14f * hE                                        // le bombé des étages ronds
        val socle = Path(); socle.addRect(cx - lb0 * 1.08f, base - 0.012f * h, cx + lb0 * 1.08f, base + 2f * dp, Path.Direction.CW)
        lavis(c, o, socle, PIERRE, 0.5f * vis); c.drawPath(socle, encre(0.8f * vis, 1f))
        val vide = o.pinceau(avecAlpha(FOND, 0.9f * vis))
        var sommet = base
        for (k in 0 until etages) {
            val f = borne(croissance - k)
            if (f <= 0f) break
            val y1 = base - 0.012f * h - k * hE; val y0 = y1 - hE * f; sommet = y0
            val lb = lb0 * 0.86f.pow(k); val lt = mix(lb, lb * 0.965f, f)
            val etage = Path(); etage.moveTo(cx - lb, y1); etage.quadTo(cx, y1 + 2 * bombe, cx + lb, y1)
            etage.lineTo(cx + lt, y0); etage.quadTo(cx, y0 + 2 * bombe, cx - lt, y0); etage.close()
            val volume = o.pinceau()                                  // éclairé à gauche par le couchant, dans l'ombre à droite
            volume.shader = LinearGradient(cx - lb, 0f, cx + lb, 0f,
                intArrayOf(avecAlpha(rgb(205, 165, 110), 0.85f * vis), avecAlpha(rgb(165, 125, 82), 0.8f * vis), avecAlpha(rgb(70, 50, 38), 0.85f * vis)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(etage, volume)
            hachures(c, etage, 0f, 5f * dp, encre(0.4f * vis, 0.6f))
            if (f > 0.75f) {                                          // les arcades, qui tournent avec le cylindre
                val na = 9 + (etages - k) / 2
                for (j in 0 until na) {
                    val th = -PI.toFloat() / 2 + (j + 0.5f) * PI.toFloat() / na
                    val ax = cx + lb * 0.93f * sin(th); val aw = (lb * PI.toFloat() / na) * 0.5f * cos(th)
                    if (aw < 1.2f * dp) continue
                    val yb = y1 - 0.1f * hE + bombe * cos(th); val yh = yb - 0.62f * hE
                    val arche = Path(); arche.moveTo(ax - aw / 2, yb); arche.lineTo(ax - aw / 2, yh + aw / 2)
                    arche.arcTo(RectF(ax - aw / 2, yh, ax + aw / 2, yh + aw), 180f, 180f, false); arche.lineTo(ax + aw / 2, yb); arche.close()
                    c.drawPath(arche, vide)
                    val lg = aw / 3
                    for (q in 0 until 3) {                            // quelques livres au fond de chaque arcade
                        val dos = Path()
                        dos.addRect(ax - aw / 2 + q * lg + 0.4f, yb - (yb - yh) * (0.4f + 0.1f * ((j + q + k) % 3)), ax - aw / 2 + (q + 0.8f) * lg, yb, Path.Direction.CW)
                        lavis(c, o, dos, CUIRS[(k * 13 + j * 5 + q) % CUIRS.size], 0.7f * vis)
                    }
                    c.drawPath(arche, encre(0.7f * vis, 0.8f))
                }
            }
            if (f >= 1f) {                                            // la rampe en spirale, qui monte de gauche à droite
                val n = 24
                val hx = FloatArray(n + 1); val hy = FloatArray(n + 1)
                for (q in 0..n) {
                    val t = q.toFloat() / n; val th = -PI.toFloat() / 2 + t * PI.toFloat()
                    hx[q] = cx + mix(lb, lt, t) * sin(th)
                    hy[q] = mix(y1, y0, t) + bombe * cos(th)
                }
                val rampe = Path(); rampe.moveTo(hx[0], hy[0] - 0.04f * hE)
                for (q in 1..n) rampe.lineTo(hx[q], hy[q] - 0.04f * hE)
                for (q in n downTo 0) rampe.lineTo(hx[q], hy[q] + 0.12f * hE)
                rampe.close()
                lavis(c, o, rampe, rgb(215, 185, 135), 0.6f * vis)
                c.drawPath(rampe, encre(0.7f * vis, 0.8f))
            }
            c.drawPath(etage, encre(0.9f * vis, 1.2f))
        }
        if (croissance > 1f) {                                        // le chantier au sommet : échafaudage et grue à roue
            val lt = lb0 * 0.86f.pow(min(etages, croissance.toInt()) - 1) * 0.75f
            for (j in -3..3) { val x = cx + j * lt / 3; c.drawLine(x, sommet, x, sommet - 0.03f * h, encre(0.7f * vis, 0.9f)) }
            c.drawLine(cx - lt, sommet - 0.026f * h, cx + lt, sommet - 0.026f * h, encre(0.6f * vis, 0.8f))
            val mx = cx + lt * 0.5f
            c.drawLine(mx, sommet, mx, sommet - 0.07f * h, encre(0.8f * vis, 1.2f))                       // le mât de la grue
            c.drawLine(mx, sommet - 0.07f * h, mx - 0.12f * m, sommet - 0.05f * h, encre(0.8f * vis, 1.1f))  // sa flèche
            c.drawLine(mx - 0.12f * m, sommet - 0.05f * h, mx - 0.12f * m, sommet - 0.05f * h + 0.03f * h * (0.6f + 0.4f * sin(sec * 2f)), encre(0.6f * vis, 0.7f))
            c.drawCircle(mx, sommet - 0.012f * h, 0.012f * h, encre(0.7f * vis, 0.9f))                       // la roue
        }
        nuagesBabel(c, o, base, w, h, sec, vis, encreC, false)       // des nuages passent devant : la tour les transperce
        for ((k, l) in lettres.withIndex()) {                         // la confusion des langues
            val f = modulo(l[2] + sec * 0.3f, 1f)
            val x = cx + l[1] * w * (0.04f + 0.45f * f) + 0.02f * w * sin(l[4] + f * 6f)
            val y = sommet - 0.03f * h - f * (sommet - 0.16f * h)
            ecrireGravure(c, caracteres[k], x, y, 0.038f * m * l[3], avecAlpha(TEINTES_ECRITURES[l[0].toInt()], vis * sin(PI.toFloat() * f) * 0.9f))
        }
    }

    // Des nuages en lavis : les uns derrière la tour, les autres devant.
    private fun nuagesBabel(c: Canvas, o: Outils, base: Float, w: Float, h: Float, sec: Float, vis: Float, encreC: (Float, Float, Int) -> Paint, derriere: Boolean) {
        for ((k, n) in nuages.withIndex()) {
            if ((k % 2 == 0) != derriere) continue
            val x = (modulo(n[0] + n[3] * sec * 0.04f, 1.4f) - 0.2f) * w; val y = base - (n[1] + 0.05f) * h
            for (q in 0 until 5) {
                val bx = x + (q - 2) * n[2] * w * 0.28f; val rr = n[2] * w * (if (q in 1..3) 0.24f else 0.16f)
                val by = y - (if (q == 2) 0.014f * h else if (q == 1 || q == 3) 0.006f * h else 0f)
                val ov = Path(); ov.addOval(RectF(bx - rr, by - rr * 0.42f, bx + rr, by + rr * 0.42f), Path.Direction.CW)
                lavis(c, o, ov, if (derriere) rgb(150, 130, 130) else rgb(175, 150, 140), (if (derriere) 0.3f else 0.45f) * vis)
                c.drawPath(ov, encreC(0.3f * vis, 0.7f, ENCRE))
            }
        }
    }

    // La ville au pied de la tour : des maisons et leurs fenêtres allumées, de part et d'autre.
    private fun ville(c: Canvas, o: Outils, cx: Float, crete: (Float) -> Float, w: Float, m: Float, dp: Float, sec: Float, vis: Float, encreC: (Float, Float, Int) -> Paint) {
        for (k in 0 until 16) {
            val sgn = if (k % 2 == 1) -1f else 1f
            val x = cx + sgn * (0.44f * m + (k / 2) * 0.055f * w)
            if (x < -0.05f * w || x > 1.05f * w) continue
            val y = crete(x) + 2f * dp
            val lw = 0.04f * m * (1 + 0.3f * ((k * 7) % 3)); val lh = 0.03f * m * (1 + 0.4f * ((k * 5) % 3))
            val mai = Path(); mai.moveTo(x - lw / 2, y); mai.lineTo(x - lw / 2, y - lh); mai.lineTo(x, y - lh - lw * 0.5f); mai.lineTo(x + lw / 2, y - lh); mai.lineTo(x + lw / 2, y); mai.close()
            c.drawPath(mai, o.pinceau(avecAlpha(rgb(40, 30, 24), vis)))
            c.drawPath(mai, encreC(0.6f * vis, 0.8f, ENCRE))
            val a = vis * (0.75f + 0.25f * sin(sec * 3f + k))
            c.drawRect(x - 1.5f * dp, y - lh * 0.6f - 1.5f * dp, x + 1.5f * dp, y - lh * 0.6f + 1.5f * dp, o.pinceau(avecAlpha(rgb(240, 190, 110), 0.85f * a)))
        }
    }

    // ======================= LA CELLULE (biologie) =======================
    // Au-dessus de la cellule : une double hélice d'ADN qui se dessine, des molécules et des anticorps.
    private fun cielCellule(c: Canvas, o: Outils, haut: Float, w: Float, h: Float, m: Float, sec: Float, vis: Float, encreC: (Float, Float, Int) -> Paint) {
        fun encre(a: Float, ep: Float, col: Int = ENCRE) = encreC(a, ep, col)
        val dp = o.dp; val mg = 16f * dp
        for (mo in molecules) {                                          // des molécules qui flottent
            val x = (mo[0] + 0.015f * sin(sec * 0.8f + mo[3])) * w; val y = haut - mo[1] * h + 0.01f * h * cos(sec * 0.7f + mo[3])
            val rr = mo[2] * m
            val boule = Path(); boule.addCircle(x, y, rr, Path.Direction.CW)
            lavis(c, o, boule, BASES[mo[4].toInt()], 0.55f * vis)
            c.drawCircle(x - rr * 0.3f, y - rr * 0.3f, rr * 0.35f, o.pinceau(avecAlpha(rgb(240, 225, 200), 0.3f * vis)))
            c.drawPath(boule, encre(0.6f * vis, 0.8f))
        }
        for (an in anticorps) {                                          // des anticorps, en forme de Y
            val s = 0.024f * m
            c.save(); c.translate(an[0] * w, haut - an[1] * h); c.rotate((an[2] + sec * 0.6f) * 180f / PI.toFloat())
            for (b in arrayOf(floatArrayOf(0f, s), floatArrayOf(-s * 0.7f, -s * 0.7f), floatArrayOf(s * 0.7f, -s * 0.7f))) {
                c.drawLine(0f, 0f, b[0], b[1], encre(0.85f * vis, 3.2f, LIPIDE)); c.drawLine(0f, 0f, b[0], b[1], encre(0.6f * vis, 1f))
            }
            c.restore()
        }
        // la double hélice : deux brins qui s'enroulent, reliés par des paires de bases colorées
        val x0 = mg + 0.05f * w; val x1 = w - mg - 0.05f * w; val yH = haut - 0.36f * h; val amp = 0.055f * h; val lam = 0.45f * w
        val fin = borne(sec / 0.8f); val ph = sec * 1.6f
        fun brin(x: Float, k: Int): Float { val a = 2 * PI.toFloat() * (x - x0) / lam + ph; return yH + amp * sin(a + k * PI.toFloat()) }
        val xf = x0 + (x1 - x0) * fin
        val n = ((x1 - x0) / (0.028f * w)).toInt()
        for (k in 0..n) {                                                // les barreaux : A-T et G-C
            val x = x0 + k * (x1 - x0) / n
            if (x > xf) break
            val ya = brin(x, 0); val yb = brin(x, 1); val ym = (ya + yb) / 2
            val i = (k * 7) % 4
            c.drawLine(x, ya, x, ym, encre(0.8f * vis, 2.6f, BASES[i])); c.drawLine(x, ym, x, yb, encre(0.8f * vis, 2.6f, BASES[i xor 1]))
        }
        val couleursBrins = intArrayOf(rgb(110, 150, 210), rgb(210, 120, 100))
        for (b in 0..1) {                                                // les deux brins (ruban coloré + trait d'encre)
            val p = Path(); val n2 = 160
            for (q in 0..n2) {
                val x = x0 + (xf - x0) * q / n2
                if (q == 0) p.moveTo(x, brin(x, b)) else p.lineTo(x, brin(x, b))
            }
            c.drawPath(p, encre(0.75f * vis, 4.5f, couleursBrins[b])); c.drawPath(p, encre(0.7f * vis, 1f))
        }
        if (fin > 0.3f) ecrireGravure(c, "a. A.D.N.", x0 + 0.06f * w, yH - amp - 0.03f * h, 0.028f * m, avecAlpha(ENCRE, 0.75f * vis))
    }

    // La cellule vue en coupe : cytoplasme, réticulum, noyau, mitochondries, Golgi, vésicules, et leurs légendes.
    private fun intCellule(c: Canvas, o: Outils, cx: Float, cy: Float, rayon: Float, haut: Float, w: Float, h: Float, m: Float, dp: Float, sec: Float, app: Float,
                           encreC: (Float, Float, Int) -> Paint) {
        fun encre(a: Float, ep: Float, col: Int = ENCRE) = encreC(a, ep, col)
        val fond = Path(); fond.addRect(0f, haut - 2f, w, h * 1.2f, Path.Direction.CW)
        lavis(c, o, fond, CYTO, 0.4f * app)                              // le cytoplasme
        hachures(c, fond, 30f, 9f * dp, encre(0.25f * app, 0.6f))
        val nx = cx + 0.12f * w; val ny = haut + 0.29f * h; val rx = 0.33f * w; val ry = 0.13f * h
        for (k in 0 until 3) {                                           // le réticulum : des replis autour du noyau
            val p = Path(); val n = 90
            for (q in 0..n) {
                val t = 2 * PI.toFloat() * q / n; val e = 1.18f + 0.1f * k + 0.025f * sin(14 * t + k + sec)
                if (q == 0) p.moveTo(nx + rx * e * cos(t), ny + ry * e * sin(t)) else p.lineTo(nx + rx * e * cos(t), ny + ry * e * sin(t))
            }
            c.drawPath(p, encre(0.6f * app, 3f, RETI)); c.drawPath(p, encre(0.5f * app, 0.7f))
        }
        val noyau = Path(); noyau.addOval(RectF(nx - rx, ny - ry, nx + rx, ny + ry), Path.Direction.CW)   // le noyau
        lavis(c, o, noyau, NOYAU, 0.7f * app)
        hachures(c, noyau, 45f, 4f * dp, encre(0.5f * app, 0.6f))
        for (ch in chromatine) {                                         // la chromatine, en petits filaments
            val x = nx + ch[0] * rx * 0.85f; val y = ny + ch[1] * ry * 0.85f
            val f = Path(); f.moveTo(x, y)
            f.quadTo(x + 0.02f * w * cos(ch[2]), y + 0.02f * h * sin(ch[2]), x + 0.035f * w * cos(ch[2] + 1), y + 0.01f * h * sin(ch[2] + 1))
            c.drawPath(f, encre(0.6f * app, 1.6f, rgb(150, 120, 200)))
        }
        val nucl = Path(); nucl.addCircle(nx + 0.05f * w, ny - 0.03f * h, 0.04f * m, Path.Direction.CW)   // le nucléole
        lavis(c, o, nucl, rgb(70, 45, 110), 0.9f * app)
        hachures(c, nucl, -45f, 3f * dp, encre(0.6f * app, 0.6f))
        c.drawPath(nucl, encre(0.8f * app, 1f))
        c.drawPath(noyau, encre(0.9f * app, 1.5f))
        c.drawOval(RectF(nx - rx - 4f * dp, ny - ry - 4f * dp, nx + rx + 4f * dp, ny + ry + 4f * dp), encre(0.7f * app, 0.9f))   // la double membrane
        val pore = o.pinceau(avecAlpha(CYTO, app))
        for (k in 0 until 16) {                                          // ses pores
            val t = 2 * PI.toFloat() * k / 16
            c.drawCircle(nx + (rx + 2f * dp) * cos(t), ny + (ry + 2f * dp) * sin(t), 1.8f * dp, pore)
        }
        val gx = cx - 0.3f * w; val gy = haut + 0.2f * h                 // l'appareil de Golgi : des saccules empilés
        for (k in 0 until 4) {
            val p = Path(); p.addArc(RectF(gx - 0.09f * w + k * 4f * dp, gy - 0.05f * h + k * 0.011f * h, gx + 0.09f * w - k * 4f * dp, gy + 0.05f * h + k * 0.011f * h), 200f, 140f)
            c.drawPath(p, encre(0.8f * app, 5f, GOLGI)); c.drawPath(p, encre(0.6f * app, 0.8f))
        }
        for (mi in mitos) {                                              // les mitochondries et leurs crêtes
            val x = cx + mi[0] * w; val y = haut + mi[1] * h + 0.04f * h
            val ex = (x - nx) / (rx * 1.45f); val ey = (y - ny) / (ry * 1.6f)
            if (ex * ex + ey * ey < 1f) continue
            if (abs(x - gx) < 0.12f * w && abs(y - gy) < 0.08f * h) continue
            val lg = 0.055f * m * mi[3]; val ep = 0.024f * m * mi[3]
            c.save(); c.translate(x, y); c.rotate((mi[2] + 0.15f * sin(sec + mi[0] * 9f)) * 180f / PI.toFloat())
            val forme = Path(); forme.addRoundRect(RectF(-lg, -ep, lg, ep), ep, ep, Path.Direction.CW)
            lavis(c, o, forme, MITO, 0.8f * app)
            val cr = Path(); cr.moveTo(-lg * 0.8f, 0f)
            for (j in 1..8) cr.lineTo(-lg * 0.8f + j * lg * 0.2f, ep * 0.6f * (if (j % 2 == 1) 1f else -1f))
            c.drawPath(cr, encre(0.7f * app, 1f, rgb(240, 180, 150)))
            c.drawPath(forme, encre(0.85f * app, 1.1f))
            c.restore()
        }
        for (v in vesicules) {                                           // les vésicules
            val x = cx + v[0] * w; val y = haut + v[1] * h + 0.03f * h
            val ex = (x - nx) / (rx * 1.3f); val ey = (y - ny) / (ry * 1.4f)
            if (ex * ex + ey * ey < 1f) continue
            val ve = Path(); ve.addCircle(x, y, v[2] * m, Path.Direction.CW)
            lavis(c, o, ve, GOLGI, 0.5f * app); c.drawPath(ve, encre(0.7f * app, 0.8f))
        }
        val legende = avecAlpha(ENCRE, 0.8f * app)                       // les légendes, comme sur une planche
        c.drawLine(nx - rx * 0.2f, ny + ry * 0.4f, nx - rx * 0.55f, ny + ry + 0.038f * h, encre(0.6f * app, 0.7f))
        ecrireGravure(c, "b. Noyau", nx - rx * 0.55f, ny + ry + 0.05f * h, 0.028f * m, legende)
        c.drawLine(gx, gy - 0.03f * h, gx - 0.02f * w, gy - 0.092f * h, encre(0.6f * app, 0.7f))
        ecrireGravure(c, "c. Golgi", gx - 0.02f * w, gy - 0.08f * h, 0.028f * m, legende)
    }

    // La membrane : une double couche de lipides (une tête ronde, une queue) tout le long de la courbe.
    private fun membrane(c: Canvas, o: Outils, rCorps: Float, h: Float, m: Float, dp: Float, sec: Float, vis: Float, tmax: Float,
                         encreC: (Float, Float, Int) -> Paint, pt: (Float, Float) -> FloatArray) {
        val ep = 0.016f * h
        val tete = o.pinceau(avecAlpha(LIPIDE, 0.85f * vis))
        for (couche in 0..1) {
            val rr = rCorps - couche * ep; val sens = if (couche == 0) 1f else -1f
            val n = (2 * tmax * rr / (0.012f * m)).toInt()
            for (j in 0..n) {
                val th = -tmax + 2 * tmax * j / max(1, n) + 0.006f * sin(sec * 1.5f + j)
                val p = pt(rr, th); val q = pt(rr - sens * ep * 0.45f, th)
                c.drawLine(p[0], p[1], q[0], q[1], encreC(0.55f * vis, 0.8f, ENCRE))
                c.drawCircle(p[0], p[1], 0.0048f * m, tete)
                c.drawCircle(p[0], p[1], 0.0048f * m, encreC(0.6f * vis, 0.6f, ENCRE))
            }
        }
    }

    // ======================= LES MATHS =======================
    // Au-dessus : le papier quadrillé, le rectangle d'or et sa spirale qui se construisent, une sinusoïde.
    private fun cielMaths(c: Canvas, o: Outils, cx: Float, haut: Float, w: Float, h: Float, m: Float, dp: Float, sec: Float, vis: Float,
                          encreC: (Float, Float, Int) -> Paint) {
        fun encre(a: Float, ep: Float, col: Int = ENCRE) = encreC(a, ep, col)
        val mg = 16f * dp
        val pas = 0.05f * m                                              // le quadrillage
        var y = modulo(haut, pas)
        while (y < haut) { c.drawLine(mg, y, w - mg, y, encre(0.12f * vis, 0.6f)); y += pas }
        var x = modulo(cx, pas)
        while (x < w - mg) { if (x > mg) c.drawLine(x, 0.13f * h, x, haut, encre(0.12f * vis, 0.6f)); x += pas }
        val phi = (1f + sqrt(5f)) / 2                                    // le rectangle d'or, découpé en carrés
        val L = min(0.7f * w, 0.42f * (haut - 0.16f * h) * phi); val H = L / phi
        var rx = cx - L / 2; var ry = haut - 0.2f * h - H; var rw = L; var rh = H
        val couleurs = intArrayOf(rgb(215, 175, 75), rgb(110, 150, 215), rgb(215, 105, 95), rgb(110, 175, 105))
        val spirale = Path(); var premier = true
        for (k in 0 until 9) {
            val visible = borne((sec - k * 0.07f) / 0.12f)
            if (visible <= 0f) break
            val sx: Float; val sy: Float; val cote: Float; val ccx: Float; val ccy: Float; val a0: Float
            when (k % 4) {                                               // on coupe un carré à gauche, en haut, à droite, en bas…
                0 -> { sx = rx; sy = ry; cote = rh; ccx = rx + rh; ccy = ry + rh; a0 = 180f; rx += rh; rw -= rh }
                1 -> { sx = rx; sy = ry; cote = rw; ccx = rx; ccy = ry + rw; a0 = 270f; ry += rw; rh -= rw }
                2 -> { sx = rx + rw - rh; sy = ry; cote = rh; ccx = rx + rw - rh; ccy = ry; a0 = 0f; rw -= rh }
                else -> { sx = rx; sy = ry + rh - rw; cote = rw; ccx = rx + rw; ccy = ry + rh - rw; a0 = 90f; rh -= rw }
            }
            val carre = Path(); carre.addRect(sx, sy, sx + cote, sy + cote, Path.Direction.CW)
            lavis(c, o, carre, couleurs[k % 4], 0.28f * vis * visible)
            c.drawPath(carre, encre(0.7f * vis * visible, 1f))
            spirale.arcTo(RectF(ccx - cote, ccy - cote, ccx + cote, ccy + cote), a0, 90f * visible, premier); premier = false
        }
        c.drawPath(spirale, encre(0.9f * vis, 2.2f, OR))                 // … et un quart de cercle dans chacun : la spirale d'or
        ecrireGravure(c, "φ = 1,618…", cx - L / 2 + 0.06f * w, haut - 0.2f * h - H - 0.025f * h, 0.03f * m, avecAlpha(OR, 0.85f * vis))
        val ys = haut - 0.09f * h; val amp = 0.035f * h                  // la sinusoïde, sur son axe
        val xa = mg + 0.04f * w; val lx = w - 2 * mg - 0.08f * w
        c.drawLine(xa, ys, xa + lx, ys, encre(0.5f * vis, 0.8f))
        val p = Path(); val n = 120; val fin = borne(sec / 0.9f)
        for (q in 0..(n * fin).toInt()) {
            val px = xa + lx * q / n; val py = ys - amp * sin(2 * PI.toFloat() * 2 * q / n - sec * 2f)
            if (q == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        c.drawPath(p, encre(0.85f * vis, 2f, SINUS))
        for ((k, lab) in listOf("0", "π", "2π", "3π", "4π").withIndex()) {
            val px = xa + lx * k / 4
            c.drawLine(px, ys - 3f * dp, px, ys + 3f * dp, encre(0.6f * vis, 0.8f))
            ecrireGravure(c, lab, px, ys + 0.018f * h, 0.024f * m, avecAlpha(ENCRE, 0.6f * vis))
        }
    }

    // Sous la courbe : l'épure au lavis bleu, des cercles concentriques et des rayons.
    private fun intMaths(c: Canvas, o: Outils, cx: Float, cy: Float, rayon: Float, haut: Float, w: Float, h: Float, app: Float, tmax: Float,
                         encreC: (Float, Float, Int) -> Paint, pt: (Float, Float) -> FloatArray) {
        val fond = Path(); fond.addRect(0f, haut - 2f, w, h * 1.2f, Path.Direction.CW)
        lavis(c, o, fond, EPURE, 0.55f * app)
        for (k in 1..11) c.drawCircle(cx, cy, rayon - k * 0.03f * h, encreC((if (k % 5 == 0) 0.45f else 0.22f) * app, 0.7f, ENCRE))
        for (j in -40..40) {
            val th = j * 1.5f * PI.toFloat() / 180f
            if (abs(th) > tmax) continue
            val a = pt(rayon - 0.02f * h, th); val b = pt(rayon - 0.4f * h, th)
            c.drawLine(a[0], a[1], b[0], b[1], encreC((if (j % 10 == 0) 0.35f else 0.15f) * app, 0.6f, ENCRE))
        }
    }

    // Le bord gradué en degrés, la tangente, et un point qui glisse avec son sinus (rouge) et son cosinus (bleu).
    private fun cercleGradue(c: Canvas, o: Outils, cx: Float, cy: Float, rCorps: Float, w: Float, h: Float, m: Float, dp: Float, sec: Float, vis: Float, tmax: Float,
                             encreC: (Float, Float, Int) -> Paint, pt: (Float, Float) -> FloatArray) {
        fun encre(a: Float, ep: Float, col: Int = ENCRE) = encreC(a, ep, col)
        for (j in -40..40) {                                             // les graduations, tous les degrés
            val th = j * PI.toFloat() / 180f
            if (abs(th) > tmax) continue
            val lg = (if (j % 5 == 0) 0.022f else 0.01f) * h
            val a = pt(rCorps, th); val b = pt(rCorps - lg, th)
            c.drawLine(a[0], a[1], b[0], b[1], encre(0.8f * vis, 1f))
            if (j % 5 == 0 && j != 0) { val l = pt(rCorps - 0.04f * h, th); ecrireGravure(c, "$j°", l[0], l[1], 0.024f * m, avecAlpha(ENCRE, 0.7f * vis)) }
        }
        val ys = cy - rCorps
        c.drawLine(cx - 0.42f * w, ys, cx + 0.42f * w, ys, encre(0.8f * vis, 1.4f, OR))      // la tangente au sommet, et l'angle droit
        val q = 0.025f * m
        c.drawLine(cx + q, ys, cx + q, ys + q, encre(0.8f * vis, 1f, OR)); c.drawLine(cx + q, ys + q, cx, ys + q, encre(0.8f * vis, 1f, OR))
        c.drawLine(cx, ys, cx, ys + 0.3f * h, encre(0.8f * vis, 1.6f, COSINUS))              // l'axe (cosinus)
        ecrireGravure(c, "cos θ", cx - 0.06f * w, ys + 0.2f * h, 0.03f * m, avecAlpha(COSINUS, 0.9f * vis))
        val th = 0.8f * tmax * sin(sec * 1.2f)                           // le point qui glisse sur le cercle
        val p = pt(rCorps, th)
        c.drawLine(p[0], p[1], cx, p[1], encre(0.9f * vis, 2f, SINUS))                        // son sinus
        ecrireGravure(c, "sin θ", (p[0] + cx) / 2, p[1] + 0.022f * h, 0.03f * m, avecAlpha(SINUS, 0.9f * vis))
        val bas = pt(rCorps - 0.3f * h, th)
        c.drawLine(p[0], p[1], bas[0], bas[1], encre(0.6f * vis, 1f))                         // son rayon
        val lt = pt(rCorps - 0.12f * h, th / 2)
        ecrireGravure(c, "θ", lt[0], lt[1], 0.032f * m, avecAlpha(OR, 0.9f * vis))
        o.halo(c, p[0], p[1], 12f * dp, 0.35f * vis, OR)
        c.drawCircle(p[0], p[1], 4f * dp, o.pinceau(avecAlpha(OR, vis))); c.drawCircle(p[0], p[1], 4f * dp, encre(0.9f * vis, 1f))
    }
}
