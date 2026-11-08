package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// LOT 1 : les IA. Chaque scène construit un décor (plusieurs plans, lumière, particules)
// puis une révélation qui fait partie de l'histoire.

// ===================== CLAUDE : « Horizon » =====================
// Lever de soleil sur une planète vue de l'espace. La planète monte, l'aube respire
// pendant le chargement, puis un liseré orange descend sur la planète et découvre l'app.
class Horizon : Scene() {
    private val etoiles = Random(3).let { r ->
        List(70) { floatArrayOf(r.nextFloat(), r.nextFloat() * 0.8f, r.nextFloat() * 6.28f, 0.6f + r.nextFloat() * 1.4f) }
    }
    private val atmoD = floatArrayOf(0f, 5f, 13f, 24f, 31f, 42f, 75f, 130f)
    private val atmoC = intArrayOf(
        rgb(210, 150, 110), rgb(190, 110, 70), rgb(150, 70, 50), rgb(110, 45, 75),
        rgb(55, 75, 140), rgb(35, 45, 105), rgb(20, 16, 50), rgb(7, 8, 13)
    )

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f
        val rayon = 1.6f * w
        val hautRepos = 0.66f * h
        val hautFin = sqrt(rayon * rayon - cx * cx) - rayon - 0.04f * h
        var haut = h * 1.04f + (hautRepos - h * 1.04f) * doux(intro)
        if (rev > 0f) haut = hautRepos + (hautFin - hautRepos) * doux(rev)
        val cy = haut + rayon
        val aube = doux(intro) * (0.75f + 0.25f * sin(sec * 4f))
        val planete = rgb(11, 14, 26)

        c.drawColor(rgb(7, 8, 13))
        val p = o.pinceau()
        val derive = (haut - hautRepos) * 0.12f
        for (e in etoiles) {
            val a = (0.4f + 0.6f * abs(sin(sec * 3f + e[2]))) * min(1f, intro * 2f) * (1f - rev)
            p.color = argb(a * 255f, 255, 255, 243)
            c.drawCircle(e[0] * w, e[1] * h + derive, e[3] * dp * 0.6f, p)
        }

        val ext = rayon + 130f * dp
        val pos = FloatArray(atmoD.size) { (rayon + atmoD[it] * dp) / ext }
        val cols = IntArray(atmoC.size) { avecAlpha(atmoC[it], 1f - atmoD[it] / 130f) }
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

        c.drawCircle(cx, cy, rayon, o.pinceau(planete))
        val reflet = o.pinceau()
        reflet.shader = RadialGradient(
            cx, cy, rayon,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, argb(aube * 200f, 29, 38, 71)),
            floatArrayOf(0f, (rayon - 60f * dp) / rayon, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, rayon, reflet)

        if (rev > 0f) {
            val front = doux(min(1f, rev / 0.9f)) * h * 1.3f
            val r0 = max(0f, rayon - front)
            c.drawCircle(cx, cy, rayon, o.gomme())
            if (r0 > 0f) c.drawCircle(cx, cy, r0, o.pinceau(planete))
            val halo = 40f * dp
            val rg = r0 + halo
            if (rg > halo) {
                val a = 1f - rev
                val ps = floatArrayOf(
                    borne((r0 - halo) / rg), borne((r0 - 6f * dp) / rg), borne(r0 / rg), borne((r0 + 6f * dp) / rg), 1f
                )
                for (k in 1 until ps.size) if (ps[k] <= ps[k - 1]) ps[k] = min(1f, ps[k - 1] + 0.0001f)
                val l = o.lumiere()
                l.shader = RadialGradient(
                    cx, cy, rg,
                    intArrayOf(
                        Color.TRANSPARENT, argb(90f * a, 120, 50, 30), argb(230f * a, 217, 119, 87),
                        argb(90f * a, 120, 50, 30), Color.TRANSPARENT
                    ),
                    ps, Shader.TileMode.CLAMP
                )
                c.drawCircle(cx, cy, rg, l)
            }
        }

        val al = borne(aube * 0.6f) * 255f
        val liser = o.lumiere(trait = 2.5f * dp)
        liser.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(argb(al * 0.64f, 230, 170, 130), argb(al, 230, 170, 130), argb(al * 0.64f, 230, 170, 130)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, rayon, liser)

        val attente = borne((sec - 0.45f) / 1.2f)
        val force = doux(intro) * (0.22f + 0.18f * attente + 0.06f * sin(sec * 6f)) * (1f - rev)
        if (force > 0.01f) {
            o.tache(c, cx, haut, 0.10f * w, 14f * dp, force * 0.5f, rgb(230, 150, 100))
            o.tache(c, cx, haut, 0.025f * w, 8.4f * dp, force * 0.9f, rgb(230, 150, 100))
            o.tache(c, cx, haut, 0.45f * w, 2.2f * dp, force * 0.4f, rgb(220, 160, 120))
        }
    }
}

// ===================== CHATGPT : « Brume » =====================
// Dans une pièce brumeuse, une sphère faite de 160 points tourne en 3D et respire.
// À la révélation, les points s'envolent vers nous et la brume s'ouvre.
class Brume : Scene() {
    // Points répartis régulièrement sur une sphère (« spirale de Fibonacci ») : [x, y, z].
    private val points = List(160) { k ->
        val y = 1f - 2f * (k + 0.5f) / 160f
        val r = sqrt(1f - y * y)
        val th = k * 2.39996f
        floatArrayOf(cos(th) * r, y, sin(th) * r)
    }
    private val poussiere = nuage(7, 60)
    private val nappes = List(5) { k -> floatArrayOf(Random(k).nextFloat(), 0.2f + 0.15f * k, 0.6f + 0.3f * Random(k + 9).nextFloat()) }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f; val cy = h * 0.47f
        val fond = o.pinceau()
        fond.shader = LinearGradient(0f, 0f, 0f, h, rgb(20, 21, 25), rgb(8, 8, 10), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fond)
        val app = doux(intro)
        val a = 1f - rev
        for (n in nappes) {                      // nappes de brume qui dérivent
            val x = (modulo(n[0] + sec * 0.05f * n[2], 1.4f) - 0.2f) * w
            o.tache(c, x, n[1] * h, w * 0.55f, h * 0.05f, 0.07f * app * a, rgb(120, 130, 145))
        }
        o.poussieres(c, poussiere, sec, w, h, rgb(200, 205, 215), 0.35f * app * a, vx = 0.01f, vy = -0.02f)
        val R = min(w, h) * 0.24f * (1f + 0.07f * sin(sec * 3.2f))
        val rot = sec * 0.9f
        val incl = 0.35f
        val explosion = 1f + 7f * accelere(rev)
        o.halo(c, cx, cy, R * 0.8f, 0.18f * app * a, rgb(70, 160, 140))
        val p = o.pinceau()
        for ((k, pt) in points.withIndex()) {
            // rotation autour de l'axe vertical, puis légère inclinaison
            val x1 = pt[0] * cos(rot) - pt[2] * sin(rot)
            val z1 = pt[0] * sin(rot) + pt[2] * cos(rot)
            val y2 = pt[1] * cos(incl) - z1 * sin(incl)
            val z2 = pt[1] * sin(incl) + z1 * cos(incl)
            val disperse = 1f + 2.2f * (1f - app) * (0.5f + 0.5f * sin(k * 7.1f))
            val prof = 1.6f + z2
            val sx = cx + x1 * R * disperse * explosion * (1f + 0.15f * z2)
            val sy = cy + y2 * R * disperse * explosion * (1f + 0.15f * z2)
            val taille = (0.9f + 1.1f * prof / 2.6f) * dp * (1f + 2f * accelere(rev))
            val coul = if (k % 9 == 0) rgb(90, 200, 170) else rgb(225, 228, 235)
            p.color = avecAlpha(coul, (0.25f + 0.75f * prof / 2.6f) * app * a)
            c.drawCircle(sx, sy, taille, p)
        }
        o.tache(c, cx, cy + R * 1.5f, R * 1.2f, R * 0.12f, 0.12f * app * a, rgb(150, 170, 175))
        o.vignette(c, w, h, 0.6f)
        o.revelationDouce(c, cx, cy, rev, rgb(200, 210, 215))
    }
}

// ===================== GEMINI : « Aurore » =====================
// Une aurore boréale ondule au-dessus d'une chaîne de montagnes et se reflète dans un lac.
class Aurore : Scene() {
    private val couleurs = arrayOf(intArrayOf(66, 133, 244), intArrayOf(120, 110, 230), intArrayOf(170, 100, 210), intArrayOf(215, 100, 140))
    private val etoiles = nuage(11, 90)

    private fun couleur(tt: Float): Int {
        val t = borne(tt) * (couleurs.size - 1)
        val k = min(t.toInt(), couleurs.size - 2)
        val f = t - k
        val a = couleurs[k]; val b = couleurs[k + 1]
        return rgb(mix(a[0].toFloat(), b[0].toFloat(), f).toInt(), mix(a[1].toFloat(), b[1].toFloat(), f).toInt(), mix(a[2].toFloat(), b[2].toFloat(), f).toInt())
    }

    private fun montagnes(w: Float, h: Float, base: Float): Path {
        val chemin = Path()
        chemin.moveTo(0f, h)
        for (s in 0..40) {
            val x = w * s / 40f
            val y = base - (0.06f * sin(s * 0.55f + 1f) + 0.035f * sin(s * 1.7f) + 0.02f * sin(s * 3.9f)) * h - 0.03f * h
            chemin.lineTo(x, y)
        }
        chemin.lineTo(w, h)
        chemin.close()
        return chemin
    }

    // Les rideaux de l'aurore : des traits verticaux en dégradé, côte à côte.
    // miroirY : si donné, on dessine le reflet (retourné) dans le lac.
    private fun rideaux(c: Canvas, o: Outils, w: Float, h: Float, sec: Float, force: Float, miroirY: Float?) {
        val n = 64
        val largeur = w / n * 1.7f
        for (k in 0 until 3) {
            for (s in 0 until n) {
                val t = s / (n - 1f)
                val x = t * w
                val yBas = h * (0.42f + 0.08f * k) + sin(t * 5f + sec * (1.1f + 0.3f * k) + k * 2f) * h * 0.05f - t * h * 0.08f
                val hauteur = h * (0.16f + 0.10f * sin(t * 9f + sec * 2f + k)) * (1f - 0.25f * k)
                val coul = couleur(t + 0.1f * sin(sec + k))
                val intensite = force * (0.22f - 0.05f * k) * (0.6f + 0.4f * sin(t * 13f + sec * 3f + k * 4f))
                val y0: Float; val y1: Float
                if (miroirY == null) { y0 = yBas; y1 = yBas - hauteur }
                else { y0 = 2f * miroirY - yBas; y1 = 2f * miroirY - yBas + hauteur * 0.6f }
                val l = o.lumiere(trait = largeur)
                l.strokeCap = Paint.Cap.BUTT
                l.shader = LinearGradient(
                    0f, y0, 0f, y1,
                    intArrayOf(avecAlpha(coul, borne(intensite * 1.4f)), avecAlpha(coul, borne(intensite)), avecAlpha(coul, 0f)),
                    floatArrayOf(0f, 0.15f, 1f), Shader.TileMode.CLAMP
                )
                c.drawLine(x, y0, x, y1, l)
            }
        }
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val app = ralentit(intro)
        val ciel = o.pinceau()
        ciel.shader = LinearGradient(0f, 0f, 0f, h * 0.8f, rgb(5, 6, 14), rgb(16, 13, 34), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, ciel)
        o.poussieres(c, etoiles, sec * 0.2f, w, h * 0.75f, rgb(230, 230, 255), 0.6f * app, vx = 0f, vy = 0f)
        val force = app * (1f + 0.6f * rev)
        rideaux(c, o, w, h, sec, force, null)
        val lac = h * 0.84f
        val eau = o.pinceau()
        eau.shader = LinearGradient(0f, lac, 0f, h, rgb(10, 10, 24), rgb(4, 4, 10), Shader.TileMode.CLAMP)
        c.drawRect(0f, lac, w, h, eau)
        c.save()
        c.clipRect(0f, lac, w, h)
        rideaux(c, o, w, h, sec, force * 0.45f, lac)
        for (k in 0 until 6) {
            val yy = lac + (k + 1) * (h - lac) / 7f + sin(sec * 2f + k) * dp
            c.drawLine(0f, yy, w, yy, o.pinceau(argb(25f, 0, 0, 0), 1.5f * dp))
        }
        c.restore()
        c.drawPath(montagnes(w, h, lac), o.pinceau(rgb(7, 7, 13)))
        c.drawRect(0f, lac, w, lac + 1.5f * dp, o.lumiere(argb(40f * app, 150, 130, 230)))
        o.vignette(c, w, h, 0.5f)
        o.revelationBalayageDouce(c, rev, -20f, rgb(150, 110, 220))
    }
}

// ===================== GROK : « Singularité » =====================
// Un trou noir déforme les étoiles autour de lui ; son disque de matière tourne, plus brillant
// du côté qui vient vers nous. À la révélation, on tombe dedans.
class Singularite : Scene() {
    private val etoiles = nuage(5, 160)

    private fun disque(c: Canvas, o: Outils, cx: Float, cy: Float, rh: Float, sec: Float, a: Float, avant: Boolean) {
        c.save()
        if (avant) c.clipRect(0f, cy, 99999f, 99999f)
        c.translate(cx, cy)
        c.scale(1f, 0.22f)
        c.rotate(sec * 40f)
        for (k in 0 until 18) {
            val r = rh * (1.35f + k * 0.12f)
            val l = o.lumiere(trait = rh * 0.13f)
            l.shader = SweepGradient(
                0f, 0f,
                intArrayOf(argb(255f * a, 255, 220, 180), argb(140f * a, 255, 140, 70), argb(40f * a, 200, 70, 40),
                    argb(140f * a, 255, 140, 70), argb(255f * a, 255, 220, 180)),
                floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
            )
            l.alpha = (borne(0.32f * (1f - k / 18f).pow(1.5f)) * 255).toInt()
            c.drawCircle(0f, 0f, r, l)
        }
        c.restore()
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        c.drawColor(Color.BLACK)
        val app = doux(intro)
        val zoom = 1f + 3.5f * accelere(rev)
        val rh = min(w, h) * 0.11f * app * zoom
        val a = 1f - rev
        // étoiles déviées par la gravité : celles qui passent près du trou sont repoussées en anneau
        val p = o.pinceau()
        for (e in etoiles) {
            val dx = e[0] * w - cx; val dy = e[1] * h - cy
            val d = hypot(dx, dy) + 0.001f
            val dev = d + (rh * 1.6f) * (rh * 1.6f) / d
            p.color = argb(200f * a * (0.3f + 0.7f * e[2]) * (0.6f + 0.4f * sin(sec * 3f + e[3])), 235, 235, 255)
            c.drawCircle(cx + dx / d * dev * zoom, cy + dy / d * dev * zoom, (0.5f + e[2]) * dp, p)
        }
        if (rh > 1f) {
            disque(c, o, cx, cy, rh, sec, a, false)
            // l'arrière du disque, courbé par la gravité au-dessus du trou (anneau d'Einstein)
            val l = o.lumiere(trait = rh * 0.12f)
            l.shader = LinearGradient(
                cx - rh * 1.5f, 0f, cx + rh * 1.5f, 0f,
                intArrayOf(argb(60f * a, 255, 150, 80), argb(180f * a, 255, 210, 160), argb(60f * a, 255, 150, 80)),
                null, Shader.TileMode.CLAMP
            )
            c.drawArc(RectF(cx - rh * 1.3f, cy - rh * 1.3f, cx + rh * 1.3f, cy + rh * 1.3f), 180f, 180f, false, l)
            c.drawCircle(cx, cy, rh, o.pinceau(Color.BLACK))
            disque(c, o, cx, cy, rh, sec, a, true)
            c.drawCircle(cx, cy, rh * 1.03f, o.lumiere(argb(150f * a, 255, 230, 200), 1.6f * dp))
            o.halo(c, cx, cy, rh * 2.2f, 0.10f * a, rgb(255, 140, 70))
        }
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, rgb(255, 170, 110), 0.2f)
    }
}

// ===================== CLAUDE DUET : « Lucioles » =====================
// Deux lucioles dansent au-dessus d'un lac la nuit ; leurs lumières se reflètent dans l'eau.
// Elles finissent par se rejoindre, et l'onde qui en naît révèle l'app.
class Lucioles : Scene() {
    private val couleurs = intArrayOf(rgb(217, 119, 87), rgb(240, 222, 200))
    private val autres = nuage(31, 26)

    // Position d'une luciole à l'instant t : [x, y, profondeur].
    private fun position(k: Int, t: Float, cx: Float, cy: Float, rr: Float): FloatArray {
        val ang = t * 3.4f + k * PI.toFloat()
        return floatArrayOf(cx + cos(ang) * rr, cy + sin(ang * 2f) * rr * 0.22f - sin(ang) * rr * 0.1f, sin(ang))
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val app = ralentit(intro)
        val lac = h * 0.62f
        val ciel = o.pinceau()
        ciel.shader = LinearGradient(0f, 0f, 0f, lac, rgb(8, 8, 14), rgb(22, 16, 20), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, lac, ciel)
        val eau = o.pinceau()
        eau.shader = LinearGradient(0f, lac, 0f, h, rgb(14, 11, 14), rgb(5, 5, 8), Shader.TileMode.CLAMP)
        c.drawRect(0f, lac, w, h, eau)
        for (k in 0 until 9) {                         // roseaux en silhouette
            val x = if (k < 5) (0.04f + 0.025f * k) * w else (0.82f + 0.03f * (k - 5)) * w
            val haut = lac - (0.05f + 0.04f * sin(k * 3.1f)) * h
            val pli = sin(sec * 1.2f + k) * 6f * dp
            val chemin = Path()
            chemin.moveTo(x, lac + 4f * dp)
            chemin.quadTo(x + pli * 0.5f, (lac + haut) / 2f, x + pli, haut)
            c.drawPath(chemin, o.pinceau(rgb(4, 4, 6), 2.2f * dp))
        }
        o.poussieres(c, autres, sec, w, lac, rgb(230, 190, 140), 0.35f * app * (1f - rev), vx = 0.015f, vy = -0.01f)
        val cx = w / 2f; val cy = lac - h * 0.13f
        val rr = min(w, h) * 0.24f * (1f + 1.5f * (1f - app)) * (1f - doux(min(1f, rev * 1.4f)))
        for (k in 0 until 2) {
            val coul = couleurs[k]
            for (g in 14 downTo 1) {                   // traînée lumineuse
                val q = position(k, sec - g * 0.02f, cx, cy, rr)
                o.halo(c, q[0], q[1], (6f - g * 0.3f) * dp, 0.10f * (1f - g / 15f) * app, coul)
            }
            val q = position(k, sec, cx, cy, rr)
            val taille = 1f + 0.25f * q[2]
            o.halo(c, q[0], q[1], 26f * dp * taille, 0.42f * app * (1f - rev), coul)
            c.drawCircle(q[0], q[1], 4.5f * dp * taille, o.pinceau(avecAlpha(coul, app * (1f - rev))))
            val yr = 2f * lac - q[1]                    // reflet dans l'eau
            o.tache(c, q[0] + sin(sec * 9f + k) * 2f * dp, yr, 18f * dp, 30f * dp, 0.16f * app * (1f - rev), coul)
            o.tache(c, q[0], lac + 3f * dp, 40f * dp, 3f * dp, 0.12f * app * (1f - rev), coul)
        }
        if (rev > 0f) {
            o.halo(c, cx, cy, 34f * dp, 0.3f * sin(PI.toFloat() * min(1f, rev * 1.4f)), rgb(235, 170, 130))
            val onde = ralentit(rev) * w * 0.8f
            c.save()
            c.translate(cx, lac)
            c.scale(1f, 0.18f)
            c.drawCircle(0f, 0f, onde, o.lumiere(avecAlpha(rgb(230, 170, 130), 0.5f * (1f - rev)), 2f * dp / 0.18f))
            c.restore()
        }
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, cy, max(0f, rev * 1.15f - 0.15f), rgb(217, 119, 87))
    }
}
