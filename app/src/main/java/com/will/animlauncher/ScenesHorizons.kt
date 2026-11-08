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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ===================== CLAUDE : « Horizons » =====================
// Même histoire que Horizon : une courbe monte, la lumière respire, puis le sol monte et un liseré
// révèle l'app. Mais 5 mondes, tirés au hasard à chaque ouverture, chacun avec son ciel, son astre
// et ses silhouettes pour qu'on le reconnaisse tout de suite :
// planète (étoiles), océan (pleine lune), colline (aube, village, et le sol vu en coupe), désert (caravane), Provence (lavande).

// La fiche d'un monde : courbure du sol (rayon / largeur d'écran) et ses couleurs.
class Monde(
    val nom: String, val courbure: Float, val cielHaut: Int, val cielBas: Int, val sol: Int,
    val liser: Int, val eclat: Int, val revelation: Int
)

val MONDES = listOf(
    Monde("Planète", 1.6f, rgb(7, 8, 13), rgb(7, 8, 13), rgb(11, 14, 26), rgb(230, 170, 130), rgb(230, 150, 100), rgb(217, 119, 87)),
    Monde("Océan", 6.0f, rgb(5, 9, 20), rgb(38, 58, 92), rgb(6, 14, 28), rgb(190, 205, 230), rgb(215, 222, 235), rgb(190, 210, 235)),
    Monde("Colline", 2.4f, rgb(18, 28, 58), rgb(220, 150, 115), rgb(12, 18, 16), rgb(250, 205, 160), rgb(250, 185, 130), rgb(240, 170, 120)),
    Monde("Désert", 2.0f, rgb(32, 18, 42), rgb(225, 120, 62), rgb(48, 26, 20), rgb(255, 185, 110), rgb(255, 160, 85), rgb(240, 150, 80)),
    Monde("Provence", 3.0f, rgb(40, 30, 72), rgb(230, 150, 96), rgb(30, 20, 42), rgb(255, 195, 140), rgb(255, 170, 105), rgb(245, 160, 110)),
)

private val ATMO_D = floatArrayOf(0f, 5f, 13f, 24f, 31f, 42f, 75f, 130f)
private val PLANETE_ATMO = intArrayOf(rgb(210, 150, 110), rgb(190, 110, 70), rgb(150, 70, 50), rgb(110, 45, 75), rgb(55, 75, 140), rgb(35, 45, 105), rgb(20, 16, 50), rgb(7, 8, 13))

// Un dégradé vertical de y0 (couleur « haut ») à y1 (couleur « bas »), sur toute la largeur.
private fun degrade(c: Canvas, o: Outils, w: Float, y0: Float, y1: Float, haut: Int, bas: Int) {
    val p = o.pinceau()
    p.shader = LinearGradient(0f, y0, 0f, y1, haut, bas, Shader.TileMode.CLAMP)
    c.drawRect(0f, min(y0, y1) - 1f, w, max(y0, y1) + 1f, p)
}

// Un soleil (ou une lune) doux : un disque et un grand halo, sans éblouir.
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

// Un chameau qui marche, de profil, vers la droite. (x, y) = le sol, s = sa taille.
private fun chameau(c: Canvas, o: Outils, x: Float, y: Float, s: Float, couleur: Int, phase: Float) {
    val p = o.pinceau(couleur)
    c.drawOval(RectF(x - 0.5f * s, y - 0.95f * s, x + 0.42f * s, y - 0.5f * s), p)      // le corps
    c.drawOval(RectF(x - 0.28f * s, y - 1.18f * s, x + 0.12f * s, y - 0.7f * s), p)     // la bosse
    val cou = Path()
    cou.moveTo(x + 0.3f * s, y - 0.85f * s); cou.lineTo(x + 0.55f * s, y - 1.25f * s); cou.lineTo(x + 0.66f * s, y - 1.3f * s)
    cou.lineTo(x + 0.74f * s, y - 1.18f * s); cou.lineTo(x + 0.6f * s, y - 1.12f * s); cou.lineTo(x + 0.42f * s, y - 0.7f * s); cou.close()
    c.drawPath(cou, p)
    val jambe = o.pinceau(couleur, 0.07f * s)
    val jambes = arrayOf(floatArrayOf(-0.38f, 0f), floatArrayOf(-0.28f, PI.toFloat()), floatArrayOf(0.22f, PI.toFloat()), floatArrayOf(0.32f, 0f))
    for (j in jambes) {                             // les jambes avancent deux par deux
        val d = 0.12f * s * sin(phase + j[1])
        c.drawLine(x + j[0] * s, y - 0.6f * s, x + j[0] * s + d, y, jambe)
    }
}

class Horizons(numero: Int = -1) : Scene() {
    private val monde = MONDES[if (numero in MONDES.indices) numero else Random.nextInt(MONDES.size)]
    private val etoiles = Random(3).let { r ->
        List(70) { floatArrayOf(r.nextFloat(), r.nextFloat() * 0.8f, r.nextFloat() * 6.28f, 0.6f + r.nextFloat() * 1.4f) }
    }
    private val r2 = Random(11)
    // sapins : [position (fraction de largeur), hauteur (fraction de h), largeur]
    private val sapins = List(26) { floatArrayOf(-0.48f + 0.6f * r2.nextFloat(), 0.05f + r2.nextFloat() * 0.06f, 0.55f + r2.nextFloat() * 0.3f) }
    // cailloux dans le sol : [position x, profondeur (fraction de h), taille]
    private val cailloux = List(40) { floatArrayOf(-0.5f + r2.nextFloat(), 0.03f + 0.21f * r2.nextFloat(), 0.004f + 0.008f * r2.nextFloat()) }
    // nuages : [position x, hauteur au-dessus de l'horizon, longueur, épaisseur]
    private val nuages = List(5) { floatArrayOf(-0.45f + 0.9f * r2.nextFloat(), 0.08f + 0.22f * r2.nextFloat(), 0.18f + 0.22f * r2.nextFloat(), 0.008f + 0.008f * r2.nextFloat()) }
    // le village de la colline : [position x, largeur, hauteur]
    private val village = arrayOf(floatArrayOf(0.22f, 0.05f, 0.035f), floatArrayOf(0.27f, 0.04f, 0.03f), floatArrayOf(0.31f, 0.055f, 0.04f), floatArrayOf(0.42f, 0.045f, 0.032f), floatArrayOf(0.46f, 0.04f, 0.03f))

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
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

        // ---------- derrière le sol : l'astre, les lointains, les silhouettes ----------
        when (mo.nom) {
            "Océan" -> {
                val lx = cx + 0.12f * w; val ly = haut - 0.2f * h + 0.05f * h * (1f - app); val lr = 0.075f * m
                astre(c, o, lx, ly, lr, rgb(232, 230, 220), vis)
                for (t in arrayOf(floatArrayOf(-0.3f, -0.2f, 0.2f), floatArrayOf(0.25f, 0.3f, 0.16f), floatArrayOf(0.1f, -0.35f, 0.12f)))   // les mers de la lune
                    c.drawCircle(lx + t[0] * lr, ly + t[1] * lr, t[2] * lr, o.pinceau(argb(40f * vis, 120, 120, 130)))
                val bouffee = o.pinceau(argb(120f * vis, 18, 25, 44))
                for (n in nuages) {                  // des nuages éclairés par la lune (5 bouffées chacun)
                    val x = cx + n[0] * w + sec * 6f * dp
                    val y = haut - n[1] * h
                    for (b in 0 until 5) {
                        val bx = x + (b - 2) * n[2] * w * 0.38f
                        val bl = n[2] * w * (0.45f - 0.06f * abs(b - 2)); val bh = n[3] * h * (1.6f - 0.25f * abs(b - 2))
                        c.drawOval(RectF(bx - bl, y - bh, bx + bl, y + bh * 0.7f), bouffee)
                    }
                    c.drawLine(x - n[2] * w * 0.7f, y + n[3] * h * 0.7f, x + n[2] * w * 0.7f, y + n[3] * h * 0.7f,
                        o.lumiere(avecAlpha(rgb(150, 165, 195), 0.18f * vis), 1.2f * dp))
                }
            }
            "Colline" -> {
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
            "Désert" -> {
                astre(c, o, cx - 0.14f * w, haut - 0.02f * h - 0.04f * h * app, 0.1f * m, rgb(255, 170, 95), vis)
                val dunes = arrayOf(floatArrayOf(0.55f, 1.4f, 0.09f), floatArrayOf(-0.5f, 1.6f, 0.05f))
                val couleurs = intArrayOf(rgb(120, 58, 46), rgb(88, 40, 34))
                for (k in 0 until 2) {                // deux dunes lointaines
                    val r = dunes[k][1] * w
                    val yb = haut - dunes[k][2] * h
                    c.drawCircle(cx + dunes[k][0] * w, yb + r, r, o.pinceau(avecAlpha(couleurs[k], app)))
                    c.drawCircle(cx + dunes[k][0] * w, yb + r, r, o.lumiere(avecAlpha(mo.liser, 0.18f * vis), 1.4f * dp))
                }
                val r = 1.4f * w; val dcx = cx + 0.55f * w; val dcy = haut - 0.09f * h + r
                for (k in 0 until 3) {                // la caravane marche sur la dune de droite
                    val x = cx + (0.12f + 0.11f * k) * w + sec * 8f * dp
                    val y = dcy - sqrt(max(0f, r * r - (x - dcx) * (x - dcx))) + 1f * dp
                    chameau(c, o, x, y, 0.045f * m, avecAlpha(rgb(40, 18, 20), vis), sec * 6f + k * 1.3f)
                }
            }
            "Provence" -> astre(c, o, cx + 0.2f * w, haut - 0.01f * h - 0.03f * h * app, 0.06f * m, rgb(255, 185, 120), vis)
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

        // ---------- le sol ----------
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
            surface(c, o, cx, cy, rayon, haut, w, h, m, dp, sec, app)
            c.restore()
        }

        // ---------- les silhouettes posées sur la crête ----------
        if (rev < 0.25f) {
            val fondu = 1f - rev * 4f
            val col = avecAlpha(mo.sol, fondu)
            if (mo.nom == "Colline") {
                for (s in sapins) { val x = cx + s[0] * w; sapin(c, o, x, crete(x) + 3f * dp, s[1] * m * 1.4f, s[2], col) }
                for (v in village) { val x = cx + v[0] * w; maison(c, o, x, crete(x) + 3f * dp, v[1] * m, v[2] * m, col) }
                val ex = cx + 0.365f * w; val ey = crete(ex) + 3f * dp          // l'église et son clocher
                maison(c, o, ex, ey, 0.06f * m, 0.05f * m, col, 0.4f)
                maison(c, o, ex + 0.012f * m, ey - 0.03f * m, 0.022f * m, 0.07f * m, col, 1.6f)
                for (v in village) {                  // les fenêtres allumées
                    val x = cx + v[0] * w; val y = crete(x) + 3f * dp - v[2] * m * 0.55f
                    val a = borne(aube * 1.4f) * fondu * (0.8f + 0.2f * sin(sec * 3f + v[0] * 40f))
                    o.halo(c, x, y, 6f * dp, 0.35f * a, rgb(255, 180, 90))
                    c.drawRect(x - 1.6f * dp, y - 1.6f * dp, x + 1.6f * dp, y + 1.6f * dp, o.pinceau(avecAlpha(rgb(255, 205, 130), 0.95f * a)))
                }
            }
            if (mo.nom == "Provence") {
                val ax = cx - 0.3f * w; val ay = crete(ax) + 2f * dp           // l'arbre seul
                c.drawRect(ax - 0.006f * m, ay - 0.06f * m, ax + 0.006f * m, ay, o.pinceau(col))
                for (t in arrayOf(floatArrayOf(0f, -0.085f, 0.045f), floatArrayOf(-0.035f, -0.07f, 0.032f), floatArrayOf(0.035f, -0.07f, 0.032f), floatArrayOf(0f, -0.115f, 0.03f)))
                    c.drawCircle(ax + t[0] * m, ay + t[1] * m, t[2] * m, o.pinceau(col))
                val mx = cx + 0.33f * w; val my = crete(mx) + 3f * dp          // le mas provençal et son cyprès
                maison(c, o, mx, my, 0.11f * m, 0.04f * m, col, 0.18f)
                maison(c, o, mx + 0.07f * m, my, 0.05f * m, 0.03f * m, col, 0.25f)
                c.drawOval(RectF(mx - 0.05f * m, my - 0.13f * m, mx - 0.02f * m, my - 0.01f * m), o.pinceau(col))
                val y = my - 0.022f * m; val a = borne(aube * 1.4f) * fondu
                o.halo(c, mx + 0.02f * m, y, 6f * dp, 0.3f * a, rgb(255, 180, 90))
                c.drawRect(mx + 0.015f * m, y - 1.6f * dp, mx + 0.025f * m, y + 1.6f * dp, o.pinceau(avecAlpha(rgb(255, 205, 130), 0.9f * a)))
            }
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
        if (rev > 0f) {                               // la révélation : un liseré lumineux descend sur le sol
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

    // Ce qu'on voit sur le sol (dessiné à l'intérieur de la courbe).
    private fun surface(c: Canvas, o: Outils, cx: Float, cy: Float, rayon: Float, haut: Float, w: Float, h: Float, m: Float, dp: Float, sec: Float, app: Float) {
        fun creteD(x: Float, d: Float) = cy - sqrt(max(0f, (rayon - d) * (rayon - d) - (x - cx) * (x - cx)))
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
            "Océan" -> {
                val houle = o.lumiere(avecAlpha(rgb(70, 100, 140), 0.12f * app), 1.2f * dp)
                for (k in 1 until 14)
                    c.drawPath(vague(k.toFloat().pow(1.5f) * 0.008f * h, (1f + k * 0.5f) * dp, 16f + k * 2, k * 2.1f + sec * (1.5f + k * 0.1f)), houle)
                val lx = cx + 0.12f * w
                for (k in 0 until 90) {               // le chemin de lune qui scintille
                    val f = k / 90f
                    val d = f.pow(1.4f) * 0.34f * h
                    val lar = (0.015f + 0.12f * f) * w
                    val x = lx + lar * (sin(k * 12.9898f) * 0.6f + sin(k * 4.1414f + sec * 2f) * 0.4f)
                    val y = creteD(x, d)
                    val sc = 0.5f + 0.5f * sin(sec * 7f + k * 1.7f)
                    val a = 0.75f * app * sc * (1f - f * 0.5f)
                    val demi = (0.008f + 0.035f * f) * w * (0.5f + sc)
                    c.drawLine(x - demi, y, x + demi, y, o.lumiere(avecAlpha(rgb(220, 225, 235), a), (1.2f + 1.8f * f) * dp))
                }
            }
            "Colline" -> {
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
            "Désert" -> {
                val p = o.pinceau()                  // le flanc éclairé par le soleil couchant
                p.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(avecAlpha(rgb(150, 75, 40), 0.55f * app), avecAlpha(rgb(48, 26, 20), 0f), avecAlpha(rgb(48, 26, 20), 0f)),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, haut - 2f, w, cy, p)
                for (k in 1 until 12) {               // les rides du sable
                    val ride = vague(k.toFloat().pow(1.35f) * 0.012f * h, (2f + k * 0.6f) * dp, 9f + k, k * 1.3f)
                    c.drawPath(ride, o.pinceau(argb(100f, 20, 9, 8), (1.2f + 0.2f * k) * dp))
                    c.save(); c.translate(0f, -1.5f * dp)
                    c.drawPath(ride, o.lumiere(avecAlpha(monde.liser, 0.06f * app * (1f - k / 13f)), 1f * dp))
                    c.restore()
                }
            }
            "Provence" -> {
                val vx = cx; val vy = haut               // les rangs de lavande filent vers l'horizon
                val rangP = o.pinceau(avecAlpha(rgb(110, 70, 165), 0.75f * app))
                val fleurP = o.pinceau(avecAlpha(rgb(175, 125, 225), 0.7f * app))
                for (k in -18..18) {
                    val rang = Path()
                    rang.moveTo(vx + k * 0.006f * w, vy); rang.lineTo(vx + (k + 0.55f) * 0.006f * w, vy)
                    rang.lineTo(cx + (k + 0.55f) * 0.2f * w, h * 1.4f); rang.lineTo(cx + k * 0.2f * w, h * 1.4f); rang.close()
                    c.drawPath(rang, rangP)
                    for (j in 1 until 16) {           // les épis en fleur, plus gros devant
                        val f = (j / 16f) * (j / 16f)
                        val y = vy + f * (h * 1.4f - vy)
                        val x = mix(vx + (k + 0.27f) * 0.006f * w, cx + (k + 0.27f) * 0.2f * w, f)
                        c.drawCircle(x, y, (0.6f + 9f * f) * dp, fleurP)
                    }
                }
                degrade(c, o, w, vy - 2f * dp, vy + 0.07f * h, avecAlpha(rgb(200, 130, 120), 0.8f * app), avecAlpha(rgb(200, 130, 120), 0f))   // la brume dorée au loin
            }
        }
    }
}
