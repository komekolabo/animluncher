package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

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

// ===================== CHATGPT : « Souffle » =====================
// Une sphère de brume qui respire, comme une voix qui s'apprête à parler.
class Souffle : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        c.drawColor(rgb(13, 13, 15))
        val respiration = 1f + 0.08f * sin(sec * 3.2f)
        val r0 = min(w, h) * 0.24f * ralentit(intro) * respiration * (1f + 7f * accelere(rev))
        val a = 1f - rev
        if (r0 > 1f) {
            // 6 nappes de brume qui tournent lentement les unes autour des autres
            for (k in 0 until 6) {
                val ang = sec * (0.9f + 0.25f * k) + k * 1.05f
                val dx = cos(ang) * r0 * 0.32f
                val dy = sin(ang * 1.3f) * r0 * 0.28f
                val rk = r0 * (0.75f + 0.12f * sin(sec * 2.1f + k))
                val ton = 200 + 9 * k
                o.halo(c, cx + dx, cy + dy, rk * 0.6f, 0.17f * a, rgb(ton, ton, ton + 6))
            }
            o.halo(c, cx, cy, r0 * 0.35f, 0.26f * a, rgb(60, 170, 140))                 // cœur vert-de-gris
            o.halo(c, cx, cy, r0 * 1.1f, 0.07f * a * (1f + 0.5f * sin(sec * 3.2f)), rgb(150, 160, 170))  // aura
            c.drawCircle(cx, cy, r0 * 1.02f, o.lumiere(argb(50f * a, 220, 225, 230), 1.2f * dp))
        }
        o.revelationCercle(c, cx, cy, rev, rgb(200, 205, 210))
    }
}

// ===================== GEMINI : « Aurore » =====================
// Des rubans d'aurore bleu-violet-rose qui ondulent, constellés de petites étincelles.
class Aurore : Scene() {
    private val eclats = Random(11).let { r ->
        List(16) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat() * 6.28f, 0.6f + r.nextFloat()) }
    }

    private fun etincelle(c: Canvas, o: Outils, x: Float, y: Float, taille: Float, a: Float) {
        if (a <= 0.01f) return
        val p = o.lumiere(argb(a * 255f, 230, 225, 255), taille * 0.12f)
        c.drawLine(x - taille, y, x + taille, y, p)
        c.drawLine(x, y - taille, x, y + taille, p)
        o.halo(c, x, y, taille * 0.5f, a * 0.5f, rgb(170, 150, 255))
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(10, 10, 18))
        val diag = hypot(w, h)
        val apparition = ralentit(intro)
        val bleu = rgb(66, 133, 244); val violet = rgb(150, 110, 220); val rose = rgb(215, 100, 130)
        c.save()
        c.translate(w / 2f, h / 2f)
        c.rotate(-28f)
        for (k in 0 until 3) {
            val chemin = Path()
            val n = 48
            for (s in 0..n) {
                val x = -diag / 2f + diag * s / n
                var y = (k - 1) * 70f * dp + sin(x / (120f * dp) + sec * (1.4f + 0.3f * k) + k * 2f) * 38f * dp
                y += (1f - apparition) * (k - 1) * 160f * dp
                if (s == 0) chemin.moveTo(x, y) else chemin.lineTo(x, y)
            }
            val couches = arrayOf(floatArrayOf(70f, 0.07f), floatArrayOf(34f, 0.13f), floatArrayOf(10f, 0.30f))
            for (couche in couches) {
                val l = o.lumiere(trait = couche[0] * dp)
                l.shader = LinearGradient(
                    -diag / 2f, 0f, diag / 2f, 0f,
                    intArrayOf(avecAlpha(bleu, 0f), bleu, violet, rose, avecAlpha(rose, 0f)),
                    floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f), Shader.TileMode.CLAMP
                )
                l.alpha = (borne(couche[1] * apparition * (1f - rev * 0.6f)) * 255).toInt()
                c.drawPath(chemin, l)
            }
        }
        c.restore()
        for (e in eclats) {
            val s = sin(sec * 2.5f + e[2])
            val a = apparition * (1f - rev) * max(0f, s * s * s) * 0.8f
            etincelle(c, o, e[0] * w, e[1] * h, 7f * dp * e[3], a)
        }
        o.revelationBalayage(c, rev, -28f, violet)
    }
}

// ===================== GROK : « Singularité » =====================
// Un trou noir : disque d'accrétion incliné qui tourne, anneau de photons ;
// à la révélation, l'horizon des événements s'ouvre et l'app apparaît dedans.
class Singularite : Scene() {
    private val poussieres = Random(5).let { r ->
        List(40) { floatArrayOf(r.nextFloat() * 6.28f, 0.5f + r.nextFloat() * 0.8f, 0.4f + r.nextFloat()) }
    }

    private fun disque(c: Canvas, o: Outils, cx: Float, cy: Float, rh: Float, sec: Float, a: Float, avant: Boolean) {
        c.save()
        if (avant) c.clipRect(0f, cy, 99999f, 99999f)       // seulement la moitié devant le trou
        c.translate(cx, cy)
        c.scale(1f, 0.26f)                                  // disque vu de biais
        c.rotate(sec * 70f)                                 // qui tourne
        for (k in 0 until 7) {
            val r = rh * (1.5f + k * 0.28f)
            val l = o.lumiere(trait = (7f - k * 0.6f) * o.dp)
            l.shader = SweepGradient(
                0f, 0f,
                intArrayOf(argb(0f, 255, 160, 90), argb(200f * a, 255, 190, 140), argb(120f * a, 230, 110, 60), argb(0f, 255, 160, 90)),
                floatArrayOf(0f, 0.35f, 0.7f, 1f)
            )
            l.alpha = (borne(0.55f - k * 0.06f) * 255).toInt()
            c.drawCircle(0f, 0f, r, l)
        }
        c.restore()
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        c.drawColor(Color.BLACK)
        val rmax = hypot(cx, cy)
        val rh = min(w, h) * 0.12f * doux(intro)
        val a = 1f - rev
        // poussières aspirées en spirale
        val p = o.pinceau()
        for (d in poussieres) {
            val t = (sec * 0.35f * d[2] + d[1]) % 1f
            val dist = rmax * (1f - t) * 0.9f + rh
            val ang = d[0] + t * 5f
            p.color = argb(160f * a * intro * t, 255, 220, 190)
            c.drawCircle(cx + cos(ang) * dist, cy + sin(ang) * dist * 0.6f, 1.3f * dp, p)
        }
        if (rh > 1f) {
            disque(c, o, cx, cy, rh, sec, a * intro, false)
            o.halo(c, cx, cy, rh * 1.6f, 0.25f * a, rgb(255, 150, 80))
            val trou = rh + (rmax - rh) * ralentit(rev)
            c.drawCircle(cx, cy, trou, o.pinceau(Color.BLACK))           // l'horizon des événements
            disque(c, o, cx, cy, rh, sec, a * intro, true)
            if (rev > 0f) c.drawCircle(cx, cy, trou * 0.985f, o.gomme())  // l'app apparaît dedans
            c.drawCircle(cx, cy, trou, o.lumiere(argb(220f * (1f - rev * 0.7f), 255, 210, 170), (2.2f + 3f * rev) * dp))
        }
    }
}

// ===================== CLAUDE DUET : « Duo » =====================
// Deux lumières (orange et crème) qui dansent en orbite, laissent des traînées,
// puis se rejoignent au centre.
class Duo : Scene() {
    private fun position(k: Int, t: Float, cx: Float, cy: Float, rr: Float): FloatArray {
        val ang = t * 4.2f + k * PI.toFloat()
        return floatArrayOf(cx + cos(ang) * rr, cy + sin(ang) * rr * 0.75f)
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        c.drawColor(rgb(20, 16, 19))
        val base = min(w, h) * 0.17f
        val rr = base * (1f + 2.2f * (1f - ralentit(intro))) * (1f + 0.18f * sin(sec * 2.6f)) * (1f - doux(min(1f, rev * 1.6f)))
        val couleurs = intArrayOf(rgb(217, 119, 87), rgb(240, 222, 200))
        for (k in 0 until 2) {
            for (g in 10 downTo 1) {          // traînée : positions passées
                val pt = position(k, sec - g * 0.022f, cx, cy, rr)
                o.halo(c, pt[0], pt[1], (11f - g * 0.6f) * dp, 0.16f * (1f - g / 11f) * intro, couleurs[k])
            }
            val pt = position(k, sec, cx, cy, rr)
            o.halo(c, pt[0], pt[1], 30f * dp, 0.45f * intro * (1f - rev), couleurs[k])
            c.drawCircle(pt[0], pt[1], 8f * dp, o.pinceau(avecAlpha(couleurs[k], intro * (1f - rev))))
        }
        if (rev > 0f) o.halo(c, cx, cy, 30f * dp, 0.35f * sin(PI.toFloat() * min(1f, rev * 1.5f)), rgb(230, 150, 110))
        o.revelationCercle(c, cx, cy, rev, rgb(217, 119, 87))
    }
}
