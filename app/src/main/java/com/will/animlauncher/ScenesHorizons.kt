package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ===================== CLAUDE : « Horizons » =====================
// Même histoire que Horizon : une courbe monte, la lumière respire, puis le sol monte et un liseré
// révèle l'app. Mais 7 courbes, tirées au hasard à chaque ouverture, sur des sujets variés :
// planète (la version de base), forêt (avec le sol vu en coupe), cellule (biologie),
// cercle trigonométrique (maths), et trois planches en gravure ancienne (voir ScenesGravures.kt) :
// la rosace (art), la collision de particules (physique), la bibliothèque (littérature).

// La fiche d'un monde : courbure du sol (rayon / largeur d'écran) et ses couleurs.
class Monde(
    val nom: String, val courbure: Float, val cielHaut: Int, val cielBas: Int, val sol: Int,
    val liser: Int, val eclat: Int, val revelation: Int
)

val MONDES = listOf(
    Monde("Planète", 1.6f, rgb(7, 8, 13), rgb(7, 8, 13), rgb(11, 14, 26), rgb(230, 170, 130), rgb(230, 150, 100), rgb(217, 119, 87)),
    Monde("Forêt", 2.4f, rgb(18, 28, 58), rgb(220, 150, 115), rgb(12, 18, 16), rgb(250, 205, 160), rgb(250, 185, 130), rgb(240, 170, 120)),
    Monde("Cellule", 1.8f, rgb(3, 10, 12), rgb(10, 34, 36), rgb(9, 30, 32), rgb(140, 235, 210), rgb(120, 230, 200), rgb(120, 230, 200)),
    Monde("Vitrail", 2.2f, 0, 0, 0, 0, 0, 0),               // gravure : ses couleurs sont dans ScenesGravures.kt
    Monde("Accélérateur", 1.9f, 0, 0, 0, 0, 0, 0),          // gravure
    Monde("Maths", 1.7f, rgb(6, 10, 22), rgb(16, 26, 50), rgb(9, 16, 32), rgb(190, 215, 255), rgb(190, 215, 255), rgb(190, 215, 255)),
    Monde("Bibliothèque", 2.0f, 0, 0, 0, 0, 0, 0),          // gravure
)

private val ATMO_D = floatArrayOf(0f, 5f, 13f, 24f, 31f, 42f, 75f, 130f)
private val PLANETE_ATMO = intArrayOf(rgb(210, 150, 110), rgb(190, 110, 70), rgb(150, 70, 50), rgb(110, 45, 75), rgb(55, 75, 140), rgb(35, 45, 105), rgb(20, 16, 50), rgb(7, 8, 13))
// Les mondes dessinés en gravure ancienne (par la classe Gravure).
private val GRAVURES = setOf("Cellule", "Vitrail", "Accélérateur", "Maths", "Bibliothèque")

// Un dégradé vertical de y0 (couleur « haut ») à y1 (couleur « bas »), sur toute la largeur.
private fun degrade(c: Canvas, o: Outils, w: Float, y0: Float, y1: Float, haut: Int, bas: Int) {
    val p = o.pinceau()
    p.shader = LinearGradient(0f, y0, 0f, y1, haut, bas, Shader.TileMode.CLAMP)
    c.drawRect(0f, min(y0, y1) - 1f, w, max(y0, y1) + 1f, p)
}

// Un soleil doux : un disque et un grand halo, sans éblouir.
private fun astre(c: Canvas, o: Outils, x: Float, y: Float, r: Float, couleur: Int, alpha: Float) {
    o.halo(c, x, y, r * 3.2f, 0.28f * alpha, couleur)
    o.halo(c, x, y, r * 1.6f, 0.35f * alpha, couleur)
    c.drawCircle(x, y, r, o.pinceau(avecAlpha(couleur, 0.92f * alpha)))
}

private fun sapin(c: Canvas, o: Outils, bx: Float, by: Float, hh: Float, lar: Float, couleur: Int) {
    val p = Path()
    for (etage in 0 until 3) {                     // trois étages de branches
        val y0 = by - hh * etage * 0.28f
        val l = hh * lar * (0.42f - etage * 0.1f)
        p.moveTo(bx - l, y0); p.lineTo(bx, y0 - hh * (0.55f - etage * 0.05f)); p.lineTo(bx + l, y0); p.close()
    }
    p.addRect(bx - hh * 0.03f, by - hh * 0.05f, bx + hh * 0.03f, by + hh * 0.06f, Path.Direction.CW)
    c.drawPath(p, o.pinceau(couleur))
}

// Une maison : un rectangle et un toit en pointe. (x, y) = le milieu du bas.
private fun maison(c: Canvas, o: Outils, x: Float, y: Float, lw: Float, lh: Float, couleur: Int, toit: Float = 0.55f) {
    val p = Path()
    p.moveTo(x - lw / 2, y); p.lineTo(x - lw / 2, y - lh); p.lineTo(x, y - lh - lw * toit); p.lineTo(x + lw / 2, y - lh); p.lineTo(x + lw / 2, y); p.close()
    c.drawPath(p, o.pinceau(couleur))
}

class Horizons(numero: Int = -1) : Scene() {
    private val monde = MONDES[if (numero in MONDES.indices) numero else Random.nextInt(MONDES.size)]
    private val etoiles = Random(3).let { r ->
        List(70) { floatArrayOf(r.nextFloat(), r.nextFloat() * 0.8f, r.nextFloat() * 6.28f, 0.6f + r.nextFloat() * 1.4f) }
    }
    private val r2 = Random(11)
    private fun entre(a: Float, b: Float) = a + (b - a) * r2.nextFloat()   // un nombre au hasard entre a et b

    // --- forêt --- sapins : [position (fraction de largeur), hauteur, largeur] ; cailloux : [x, profondeur, taille]
    private val sapins = List(26) { floatArrayOf(entre(-0.48f, 0.12f), entre(0.05f, 0.11f), entre(0.55f, 0.85f)) }
    private val cailloux = List(40) { floatArrayOf(entre(-0.5f, 0.5f), entre(0.03f, 0.24f), entre(0.004f, 0.012f)) }
    private val village = arrayOf(floatArrayOf(0.22f, 0.05f, 0.035f), floatArrayOf(0.27f, 0.04f, 0.03f), floatArrayOf(0.31f, 0.055f, 0.04f), floatArrayOf(0.42f, 0.045f, 0.032f), floatArrayOf(0.46f, 0.04f, 0.03f))
    // --- cellule --- molécules [x, hauteur, taille, phase], anticorps [x, hauteur, phase],
    // mitochondries [x, profondeur, angle, taille], vésicules [x, profondeur, taille]
    private val molecules = List(40) { floatArrayOf(r2.nextFloat(), entre(0.03f, 0.55f), entre(0.004f, 0.012f), r2.nextFloat() * 6.28f) }
    private val anticorps = List(5) { floatArrayOf(entre(0.05f, 0.95f), entre(0.1f, 0.45f), r2.nextFloat() * 6.28f) }
    private val organites = List(9) { floatArrayOf(entre(-0.45f, 0.45f), entre(0.05f, 0.28f), r2.nextFloat() * 3.14f, entre(0.7f, 1.2f)) }
    private val vesicules = List(18) { floatArrayOf(entre(-0.5f, 0.5f), entre(0.03f, 0.3f), entre(0.006f, 0.016f)) }
    // --- maths --- des formules célèbres : [texte, x, hauteur]
    private val formules = listOf(Triple("e^(iπ) + 1 = 0", 0.18f, 0.5f), Triple("x² + y² = r²", 0.72f, 0.38f), Triple("φ = (1 + √5) / 2", 0.3f, 0.2f),
        Triple("Σ 1/n² = π²/6", 0.8f, 0.12f), Triple("sin²θ + cos²θ = 1", 0.5f, 0.62f), Triple("∫ e^(−x²) dx = √π", 0.12f, 0.08f))

    // Pour les trois mondes en gravure, c'est la classe Gravure qui dessine tout.
    private val gravure = if (monde.nom in GRAVURES) Gravure(monde.nom, monde.courbure) else null

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        gravure?.let { it.dessiner(c, o, i); return }
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val mo = monde
        val m = min(w, h)
        val cx = w / 2f
        val rayon = mo.courbure * w
        val hautRepos = 0.66f * h
        val hautFin = sqrt(rayon * rayon - cx * cx) - rayon - 0.04f * h
        var haut = h * 1.04f + (hautRepos - h * 1.04f) * doux(intro)
        if (rev > 0f) haut = hautRepos + (hautFin - hautRepos) * doux(rev)
        val cy = haut + rayon
        val aube = doux(intro) * (0.75f + 0.25f * sin(sec * 4f))
        val app = doux(intro)
        val vis = app * (1f - rev)
        val tmax = asin(min(1f, (w / 2 + 20f * dp) / rayon))      // l'angle jusqu'où la courbe est visible
        fun crete(x: Float) = cy - sqrt(max(0f, rayon * rayon - (x - cx) * (x - cx)))   // la hauteur du sol en x

        // ---------- le ciel ----------
        c.drawColor(mo.cielHaut)
        if (mo.nom == "Planète") {
            val p = o.pinceau()
            for (e in etoiles) {
                val a = (0.4f + 0.6f * abs(sin(sec * 3f + e[2]))) * min(1f, intro * 2f) * (1f - rev)
                p.color = argb(a * 255f, 255, 255, 243)
                c.drawCircle(e[0] * w, e[1] * h + (haut - hautRepos) * 0.12f, e[3] * dp * 0.6f, p)
            }
        } else {
            degrade(c, o, w, 0f, haut, mo.cielHaut, avecAlpha(mo.cielBas, 0.25f + 0.75f * app))   // le ciel s'allume
        }

        // ---------- derrière la courbe ----------
        when (mo.nom) {
            "Forêt" -> {
                astre(c, o, cx + 0.16f * w, haut + 0.012f * h - 0.04f * h * app, 0.06f * m, rgb(255, 196, 140), vis)
                val lointaines = arrayOf(floatArrayOf(-0.7f, 1.5f), floatArrayOf(0.6f, 1.3f))
                val couleurs = intArrayOf(rgb(70, 62, 88), rgb(48, 46, 66))
                for (k in 0 until 2) {                // deux collines lointaines, et la brume
                    val r = lointaines[k][1] * w
                    val yb = haut - (0.07f - 0.03f * k) * h
                    c.drawCircle(cx + lointaines[k][0] * w, yb + r, r, o.pinceau(avecAlpha(couleurs[k], app)))
                    degrade(c, o, w, yb, yb + 0.06f * h, avecAlpha(rgb(240, 180, 150), 0f), avecAlpha(rgb(240, 180, 150), 0.12f * app))
                }
                val oiseauP = o.pinceau(argb(200f * vis, 40, 30, 40), 1.3f * dp)
                for (k in 0 until 4) {                // des oiseaux qui battent des ailes
                    val bx = (0.2f + 0.12f * k) * w + sec * 25f * dp
                    val by = haut - (0.25f + 0.04f * sin(k * 2f)) * h
                    val b = 4f * dp * (1f + 0.15f * k); val bat = sin(sec * 9f + k) * 0.6f
                    val ois = Path()
                    ois.moveTo(bx - b, by - b * bat); ois.quadTo(bx - b * 0.4f, by - b * 0.5f, bx, by); ois.quadTo(bx + b * 0.4f, by - b * 0.5f, bx + b, by - b * bat)
                    c.drawPath(ois, oiseauP)
                }
            }
            "Cellule" -> {
                for (mol in molecules) {              // des molécules qui flottent
                    val x = (mol[0] + 0.02f * sin(sec * 0.8f + mol[3])) * w
                    val y = haut - mol[1] * h + 0.01f * h * cos(sec * 0.7f + mol[3])
                    val r = mol[2] * m
                    c.drawCircle(x, y, r, o.pinceau(avecAlpha(rgb(50, 140, 130), 0.4f * vis)))
                    c.drawCircle(x - r * 0.3f, y - r * 0.3f, r * 0.4f, o.pinceau(avecAlpha(rgb(160, 240, 220), 0.35f * vis)))
                }
                val yp = o.pinceau(avecAlpha(rgb(230, 170, 120), 0.5f * vis), 1.6f * dp)
                for (an in anticorps) {               // des anticorps, en forme de Y
                    val s = 0.022f * m
                    c.save(); c.translate(an[0] * w, haut - an[1] * h); c.rotate(Math.toDegrees((an[2] + sec * 0.6f).toDouble()).toFloat())
                    c.drawLine(0f, 0f, 0f, s, yp); c.drawLine(0f, 0f, -s * 0.7f, -s * 0.7f, yp); c.drawLine(0f, 0f, s * 0.7f, -s * 0.7f, yp)
                    c.restore()
                }
            }
            "Maths" -> {
                val pas = 0.05f * m                    // le quadrillage
                val gp = o.pinceau(avecAlpha(rgb(40, 60, 100), 0.35f * app), 1f * dp)
                var x = cx % pas
                while (x < w) { c.drawLine(x, 0f, x, haut, gp); x += pas }
                var y = haut % pas
                while (y < haut) { c.drawLine(0f, y, w, y, gp); y += pas }
                val sinus = Path(); val n = 80
                val fin = (n * borne(sec / 0.9f)).toInt()            // la sinusoïde se dessine de gauche à droite
                for (j in 0..fin) {
                    val sx = w * j / n; val sy = haut - 0.2f * h + 0.05f * h * sin(sx / w * 4f * PI.toFloat() - sec * 2f)
                    if (j == 0) sinus.moveTo(sx, sy) else sinus.lineTo(sx, sy)
                }
                c.drawPath(sinus, o.lumiere(avecAlpha(rgb(120, 200, 255), 0.5f * vis), 1.8f * dp))
                for ((txt, fx, fy) in formules)        // des formules célèbres qui flottent
                    texte(c, txt, fx * w, haut - (0.08f + fy * 0.5f) * h - sec * 4f * dp, 0.032f * m, avecAlpha(rgb(190, 215, 255), 0.45f * vis))
            }
        }

        // ---------- l'atmosphère (planète) ou la lueur de l'horizon ----------
        if (mo.nom == "Planète") {
            val ext = rayon + 130f * dp
            val pos = FloatArray(ATMO_D.size) { (rayon + ATMO_D[it] * dp) / ext }
            val cols = IntArray(ATMO_D.size) { avecAlpha(PLANETE_ATMO[it], 1f - ATMO_D[it] / 130f) }
            val anneau = RadialGradient(cx, cy, ext, cols, pos, Shader.TileMode.CLAMP)
            val centrage = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(argb(163f, 0, 0, 0), argb(224f, 0, 0, 0), Color.BLACK, argb(224f, 0, 0, 0), argb(163f, 0, 0, 0)),
                floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f), Shader.TileMode.CLAMP
            )
            val q = o.pinceau()
            q.shader = ComposeShader(anneau, centrage, PorterDuff.Mode.DST_IN)
            q.alpha = (borne(aube) * 255).toInt()
            c.drawCircle(cx, cy, ext, q)
        } else {
            o.tache(c, cx, haut, 0.7f * w, 0.06f * h, 0.22f * borne(aube) * (1f - rev), mo.eclat)
        }

        // ---------- la courbe et ce qu'il y a dedans ----------
        var rCorps = rayon
        if (rev > 0f) {
            val front = doux(min(1f, rev / 0.9f)) * h * 1.3f
            rCorps = max(0f, rayon - front)
            c.drawCircle(cx, cy, rayon, o.gomme())
        }
        if (rCorps > 0f) {
            c.drawCircle(cx, cy, rCorps, o.pinceau(mo.sol))
            val chemin = Path(); chemin.addCircle(cx, cy, rCorps, Path.Direction.CW)
            c.save(); c.clipPath(chemin)
            surface(c, o, cx, cy, rayon, haut, w, h, m, dp, sec, app, tmax)
            c.restore()
        }

        // ---------- les silhouettes de la forêt, posées sur la crête ----------
        if (rev < 0.25f && mo.nom == "Forêt") {
            val fondu = 1f - rev * 4f
            val col = avecAlpha(mo.sol, fondu)
            for (s in sapins) { val x = cx + s[0] * w; sapin(c, o, x, crete(x) + 3f * dp, s[1] * m * 1.4f, s[2], col) }
            for (v in village) { val x = cx + v[0] * w; maison(c, o, x, crete(x) + 3f * dp, v[1] * m, v[2] * m, col) }
            val ex = cx + 0.365f * w; val ey = crete(ex) + 3f * dp          // l'église et son clocher
            maison(c, o, ex, ey, 0.06f * m, 0.05f * m, col, 0.4f)
            maison(c, o, ex + 0.012f * m, ey - 0.03f * m, 0.022f * m, 0.07f * m, col, 1.6f)
            for (v in village) {                      // les fenêtres allumées
                val x = cx + v[0] * w; val y = crete(x) + 3f * dp - v[2] * m * 0.55f
                val a = borne(aube * 1.4f) * fondu * (0.8f + 0.2f * sin(sec * 3f + v[0] * 40f))
                o.halo(c, x, y, 6f * dp, 0.35f * a, rgb(255, 180, 90))
                c.drawRect(x - 1.6f * dp, y - 1.6f * dp, x + 1.6f * dp, y + 1.6f * dp, o.pinceau(avecAlpha(rgb(255, 205, 130), 0.95f * a)))
            }
        }

        // ---------- ce qui se dessine sur la courbe elle-même ----------
        fun pt(r: Float, th: Float) = floatArrayOf(cx + r * sin(th), cy - r * cos(th))   // un point du cercle, repéré par son angle
        if (rCorps > 0f && mo.nom == "Cellule") {     // la membrane : une double couche de lipides
            val tete = o.pinceau(avecAlpha(rgb(110, 210, 190), 0.85f * vis))
            for (dr in floatArrayOf(0f, 0.014f * h)) {
                val rr = rCorps - dr
                val n = (2 * tmax * rr / (0.011f * m)).toInt()
                for (j in 0..n) {
                    val th = -tmax + 2 * tmax * j / max(1, n) + 0.02f * sin(sec * 1.5f + j)
                    val p = pt(rr, th)
                    c.drawCircle(p[0], p[1], 0.0045f * m, tete)
                }
            }
        }
        if (mo.nom == "Maths" && rCorps > 0f) {
            val grad = o.pinceau(avecAlpha(rgb(190, 215, 255), 0.7f * vis), 1.2f * dp)
            for (j in -30..30) {                       // les graduations du cercle, tous les 2°
                val th = j * 2f * PI.toFloat() / 180f
                if (abs(th) > tmax) continue
                val lg = (if (j % 5 == 0) 0.02f else 0.01f) * h
                val a = pt(rCorps, th); val b = pt(rCorps - lg, th)
                c.drawLine(a[0], a[1], b[0], b[1], grad)
            }
            val tg = o.pinceau(avecAlpha(rgb(255, 200, 120), 0.7f * vis), 1.4f * dp)    // la tangente au sommet, et l'angle droit
            val ys = cy - rCorps
            c.drawLine(cx - 0.42f * w, ys, cx + 0.42f * w, ys, tg)
            val q = 0.025f * m
            c.drawLine(cx + q, ys, cx + q, ys + q, tg); c.drawLine(cx + q, ys + q, cx, ys + q, tg)
            c.drawLine(cx, ys, cx, h, o.pinceau(avecAlpha(rgb(255, 200, 120), 0.35f * vis), 1.2f * dp))   // le rayon
            texte(c, "r", cx + 0.02f * m, ys + 0.12f * h, 0.035f * m, avecAlpha(rgb(255, 200, 120), 0.7f * vis))
            val th = 0.8f * tmax * sin(sec * 1.2f)     // un point qui glisse sur le cercle, et sa projection
            val p = pt(rCorps, th)
            c.drawLine(p[0], p[1], p[0], ys, o.pinceau(avecAlpha(rgb(120, 200, 255), 0.5f * vis), 1f * dp))
            o.halo(c, p[0], p[1], 10f * dp, 0.4f * vis, rgb(120, 200, 255))
            c.drawCircle(p[0], p[1], 3.5f * dp, o.pinceau(avecAlpha(rgb(220, 240, 255), vis)))
        }

        if (rev <= 0f && mo.nom == "Planète") {
            val reflet = o.pinceau()
            reflet.shader = RadialGradient(
                cx, cy, rayon,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, argb(aube * 200f, 29, 38, 71)),
                floatArrayOf(0f, (rayon - 60f * dp) / rayon, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, cy, rayon, reflet)
        }
        if (rev > 0f) {                               // la révélation : un liseré lumineux descend sur la courbe
            val r0 = rCorps
            val halo = 40f * dp
            val rg = r0 + halo
            if (rg > halo) {
                val a = 1f - rev
                val ps = floatArrayOf(borne((r0 - halo) / rg), borne((r0 - 6f * dp) / rg), borne(r0 / rg), borne((r0 + 6f * dp) / rg), 1f)
                for (k in 1 until ps.size) if (ps[k] <= ps[k - 1]) ps[k] = min(1f, ps[k - 1] + 0.0001f)
                val rc = mo.revelation
                val sombre = Color.rgb(Color.red(rc) / 2, Color.green(rc) / 2, Color.blue(rc) / 2)
                val l = o.lumiere()
                l.shader = RadialGradient(
                    cx, cy, rg,
                    intArrayOf(Color.TRANSPARENT, avecAlpha(sombre, 90f * a / 255f), avecAlpha(rc, 230f * a / 255f), avecAlpha(sombre, 90f * a / 255f), Color.TRANSPARENT),
                    ps, Shader.TileMode.CLAMP
                )
                c.drawCircle(cx, cy, rg, l)
            }
        }

        // le fin liseré de lumière sur la crête
        val al = borne(aube * 0.6f)
        val liser = o.lumiere(trait = 2.5f * dp)
        liser.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(avecAlpha(mo.liser, al * 0.64f), avecAlpha(mo.liser, al), avecAlpha(mo.liser, al * 0.64f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, rayon, liser)
        if (mo.nom == "Planète") {                    // l'éclat du soleil qui pointe derrière la planète
            val attente = borne((sec - 0.45f) / 1.2f)
            val force = doux(intro) * (0.22f + 0.18f * attente + 0.06f * sin(sec * 6f)) * (1f - rev)
            if (force > 0.01f) {
                o.tache(c, cx, haut, 0.10f * w, 14f * dp, force * 0.5f, mo.eclat)
                o.tache(c, cx, haut, 0.025f * w, 8.4f * dp, force * 0.9f, mo.eclat)
                o.tache(c, cx, haut, 0.45f * w, 2.2f * dp, force * 0.4f, mo.eclat)
            }
        }
    }

    // Ce qu'on voit à l'intérieur de la courbe.
    private fun surface(c: Canvas, o: Outils, cx: Float, cy: Float, rayon: Float, haut: Float, w: Float, h: Float, m: Float, dp: Float, sec: Float, app: Float, tmax: Float) {
        fun creteD(x: Float, d: Float) = cy - sqrt(max(0f, (rayon - d) * (rayon - d) - (x - cx) * (x - cx)))
        fun pt(r: Float, th: Float) = floatArrayOf(cx + r * sin(th), cy - r * cos(th))
        fun vague(d: Float, amp: Float, freq: Float, ph: Float, n: Int = 48): Path {   // une ligne qui suit la courbe en ondulant
            val p = Path()
            for (j in 0..n) {
                val x = w * j / n
                val y = creteD(x, d) + amp * sin(x / w * freq + ph)
                if (j == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            return p
        }
        when (monde.nom) {
            "Forêt" -> {
                // la colline vue en coupe : herbe, terre, argile, roches, et la chaleur du magma tout en bas
                val profondeurs = floatArrayOf(0f, 0.016f, 0.07f, 0.13f, 0.2f, 0.27f)
                val couleurs = intArrayOf(rgb(26, 44, 28), rgb(50, 35, 26), rgb(64, 44, 32), rgb(46, 44, 50), rgb(32, 30, 37), rgb(70, 26, 16))
                for (k in profondeurs.indices) c.drawCircle(cx, cy, rayon - profondeurs[k] * h, o.pinceau(avecAlpha(couleurs[k], app)))
                c.drawCircle(cx, cy, rayon - 0.27f * h, o.lumiere(avecAlpha(rgb(255, 110, 50), 0.35f * app * (0.85f + 0.15f * sin(sec * 3f))), 3f * dp))   // le magma rougeoie
                o.tache(c, cx, cy - rayon + 0.33f * h, 0.6f * w, 0.05f * h, 0.25f * app, rgb(255, 90, 40))
                val limite = o.pinceau(argb(90f * app, 10, 8, 8), 1.5f * dp)
                for (k in 1 until profondeurs.size)   // les limites entre couches, un peu ondulées
                    c.drawPath(vague(profondeurs[k] * h, 2f * dp, 11f, profondeurs[k] * 50f, 30), limite)
                for (ca in cailloux) {                // des cailloux dans la terre et la roche
                    val x = cx + ca[0] * w; val y = creteD(x, ca[1] * h)
                    val col = if (ca[1] > 0.13f) argb(150f * app, 95, 88, 80) else argb(140f * app, 92, 70, 52)
                    c.drawOval(RectF(x - ca[2] * m, y - ca[2] * m * 0.6f, x + ca[2] * m, y + ca[2] * m * 0.6f), o.pinceau(col))
                }
                val racine = o.pinceau(argb(150f * app, 70, 52, 36), 1.2f * dp)
                for (k in sapins.indices step 2) {    // les racines des sapins
                    val x = cx + sapins[k][0] * w; val y = creteD(x, 0f)
                    for (r in -1..1) {
                        val q = Path(); q.moveTo(x, y)
                        q.quadTo(x + r * 0.01f * w, y + 0.025f * h, x + r * 0.025f * w, y + (0.04f + 0.01f * abs(r)) * h)
                        c.drawPath(q, racine)
                    }
                }
            }
            "Cellule" -> {
                degrade(c, o, w, haut, haut + 0.35f * h, avecAlpha(rgb(20, 60, 60), app), avecAlpha(rgb(6, 20, 24), app))   // le cytoplasme
                c.drawRect(0f, haut + 0.35f * h, w, h * 1.2f, o.pinceau(avecAlpha(rgb(6, 20, 24), app)))
                val nx = cx + 0.12f * w; val ny = haut + 0.3f * h       // le noyau et son nucléole
                val noyau = RectF(nx - 0.34f * w, ny - 0.15f * h, nx + 0.34f * w, ny + 0.15f * h)
                c.drawOval(noyau, o.pinceau(avecAlpha(rgb(24, 46, 70), app)))
                c.drawOval(noyau, o.pinceau(avecAlpha(rgb(80, 150, 190), 0.7f * app), 2f * dp))
                o.halo(c, nx + 0.04f * w, ny - 0.04f * h, 0.06f * m, 0.4f * app, rgb(140, 120, 230))
                c.drawCircle(nx + 0.04f * w, ny - 0.04f * h, 0.035f * m, o.pinceau(avecAlpha(rgb(90, 80, 170), app)))
                val reti = o.pinceau(avecAlpha(rgb(70, 160, 150), 0.45f * app), 1.6f * dp)
                for (k in 0 until 3) {                // le réticulum : des replis autour du noyau
                    val q = Path()
                    for (j in 0..30) {
                        val x = nx - 0.4f * w + 0.8f * w * j / 30
                        val y = ny - 0.19f * h - k * 0.018f * h + 0.008f * h * sin(j * 0.9f + k + sec)
                        if (j == 0) q.moveTo(x, y) else q.lineTo(x, y)
                    }
                    c.drawPath(q, reti)
                }
                for (org in organites) {              // les mitochondries, avec leurs replis en zigzag
                    val x = cx + org[0] * w; val y = creteD(x, org[1] * h + 0.03f * h)
                    val lg = 0.05f * m * org[3]; val ep = 0.022f * m * org[3]
                    c.save(); c.translate(x, y); c.rotate(Math.toDegrees((org[2] + 0.2f * sin(sec + org[0] * 9f)).toDouble()).toFloat())
                    c.drawRoundRect(RectF(-lg, -ep, lg, ep), ep, ep, o.pinceau(avecAlpha(rgb(150, 70, 60), 0.9f * app)))
                    val cr = Path(); cr.moveTo(-lg * 0.8f, 0f)
                    for (j in 1..8) cr.lineTo(-lg * 0.8f + j * lg * 0.2f, ep * 0.6f * (if (j % 2 == 1) 1f else -1f))
                    c.drawPath(cr, o.pinceau(avecAlpha(rgb(230, 150, 120), 0.8f * app), 1.2f * dp))
                    c.restore()
                }
                val ves = o.pinceau(avecAlpha(rgb(120, 220, 200), 0.5f * app), 1.2f * dp)
                for (v in vesicules) {                // les vésicules
                    val x = cx + v[0] * w
                    c.drawCircle(x, creteD(x, v[1] * h + 0.02f * h), v[2] * m, ves)
                }
            }
            "Maths" -> {
                val trait = o.pinceau(avecAlpha(rgb(60, 90, 150), 0.35f * app), 1f * dp)
                for (k in 1..11) c.drawCircle(cx, cy, rayon - k * 0.03f * h, trait)   // des cercles concentriques
                val rayonP = o.pinceau(avecAlpha(rgb(60, 90, 150), 0.25f * app), 1f * dp)
                for (j in -40..40) {                  // et des rayons, tous les 1,5°
                    val th = j * 1.5f * PI.toFloat() / 180f
                    if (abs(th) > tmax) continue
                    val a = pt(rayon, th); val b = pt(rayon - 0.4f * h, th)
                    c.drawLine(a[0], a[1], b[0], b[1], rayonP)
                }
            }
        }
    }
}
