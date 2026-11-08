package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// LOT 4 : réseaux, musique, services.

private fun versRadians(degres: Float): Float = degres * PI.toFloat() / 180f

// ===================== INSTAGRAM : « Pellicule » =====================
// Une bande de pellicule défile, éclairée par derrière ; chaque image est un coucher de soleil
// aux couleurs violet-orangé. L'image du centre grandit et devient l'app.
class Pellicule : Scene() {
    private val teintes = arrayOf(
        intArrayOf(rgb(120, 40, 160), rgb(250, 120, 60)), intArrayOf(rgb(200, 50, 120), rgb(255, 180, 80)),
        intArrayOf(rgb(80, 40, 170), rgb(230, 70, 120)), intArrayOf(rgb(160, 40, 140), rgb(255, 140, 70))
    )

    private fun image(c: Canvas, o: Outils, r: RectF, k: Int, alpha: Float) {
        val t = teintes[modulo(k.toFloat(), 4f).toInt()]
        val p = o.pinceau()
        p.shader = LinearGradient(0f, r.top, 0f, r.bottom, avecAlpha(fonce(t[0], 0.45f), alpha), avecAlpha(fonce(t[1], 0.55f), alpha), Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        o.halo(c, r.centerX(), r.top + r.height() * 0.62f, r.width() * 0.16f, 0.35f * alpha, t[1])
        val mont = Path(); mont.moveTo(r.left, r.bottom)
        for (s in 0..12) {
            val u = s / 12f
            mont.lineTo(r.left + u * r.width(), r.top + r.height() * (0.7f - 0.08f * sin(u * 7f + k)))
        }
        mont.lineTo(r.right, r.bottom); mont.close()
        c.drawPath(mont, o.pinceau(avecAlpha(rgb(20, 10, 26), alpha)))
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(8, 6, 10))
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f
        val L = m * 0.5f; val H = L * 0.75f
        val pas = H * 1.15f
        o.tache(c, cx, h / 2f, L * 0.9f, h * 0.5f, 0.12f * app, rgb(200, 90, 160))
        c.drawRect(cx - L * 0.62f, -10f, cx + L * 0.62f, h + 10f, o.pinceau(argb(255f * app, 22, 18, 20)))
        val arret = ralentit(borne(sec / 0.9f))
        val defil = arret * pas * 6f + (1f - app) * pas            // la bande défile puis s'arrête
        val decal = modulo(defil, pas)
        val k0 = (defil / pas).toInt()
        for (n in -3..3) {
            if (n == 0) continue
            val y = h / 2f + n * pas + decal
            image(c, o, RectF(cx - L / 2f, y - H / 2f, cx + L / 2f, y + H / 2f), n - k0, app * a)
        }
        val trou = o.pinceau(argb(255f * app * a, 6, 5, 7))          // perforations
        val ecart = 14f * dp
        for (s in 0 until (h / ecart).toInt() + 2) {
            val y = modulo(s * ecart + decal, h + ecart) - 7f * dp
            for (x in floatArrayOf(cx - L * 0.56f, cx + L * 0.56f)) {
                c.drawRoundRect(RectF(x - 3f * dp, y - 4f * dp, x + 3f * dp, y + 4f * dp), 1.5f * dp, 1.5f * dp, trou)
            }
        }
        val k = doux(rev)                                          // l'image du centre grandit
        val yc = h / 2f + decal
        val r = RectF(mix(cx - L / 2f, -2f, k), mix(yc - H / 2f, -2f, k), mix(cx + L / 2f, w + 2f, k), mix(yc + H / 2f, h + 2f, k))
        image(c, o, r, -k0, app)
        o.vignette(c, w, h, 0.6f)
        if (rev > 0f) gommeRect(c, o, r, ralentit(rev * 1.3f))
    }
}

// ===================== X : « Éclipse » =====================
// Une éclipse totale : un disque noir, une couronne blanche qui frémit, quelques étoiles.
class Eclipse : Scene() {
    private val jets = Random(151).let { r -> List(70) { floatArrayOf(r.nextFloat() * 6.28f, 0.3f + 0.7f * r.nextFloat(), r.nextFloat() * 6.28f) } }
    private val etoiles = nuage(152, 80)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(3, 3, 4))
        val app = ralentit(intro)
        val a = 1f - rev
        val cx = w / 2f; val cy = h / 2f
        val m = min(w, h)
        o.poussieres(c, etoiles, sec * 0.1f, w, h, rgb(230, 230, 240), 0.4f * a, vx = 0f, vy = 0f)
        val R = m * 0.2f * (1f + 3f * accelere(rev))
        val couv = ralentit(borne(sec / 0.6f))                      // la lune glisse devant le soleil
        val lx = cx + (1f - couv) * R * 0.6f
        o.halo(c, cx, cy, R * 2.4f, 0.18f * app * a, rgb(200, 210, 230))
        for (j in jets) {                                          // la couronne : des filaments
            val ang = j[0]; val ph = j[2]
            val l = R * (1.05f + 0.6f * j[1] * (0.8f + 0.2f * sin(sec * 3f + ph)))
            val x0 = cx + cos(ang) * R; val y0 = cy + sin(ang) * R
            val ang2 = ang + 0.05f * sin(sec + ph)
            val x1 = cx + cos(ang2) * l; val y1 = cy + sin(ang2) * l
            val p = o.lumiere(trait = 1.5f * dp)
            p.shader = LinearGradient(x0, y0, x1, y1, argb(120f * app * a, 235, 240, 255), argb(0f, 235, 240, 255), Shader.TileMode.CLAMP)
            c.drawLine(x0, y0, x1, y1, p)
        }
        c.drawCircle(cx, cy, R * 1.01f, o.lumiere(argb(200f * app * a, 240, 244, 255), 2f * dp))
        c.drawCircle(lx, cy, R, o.pinceau(rgb(2, 2, 3)))
        val perle = (1f - couv) * 0.7f + 0.3f * borne(1f - abs(sec - 0.6f) / 0.15f)   // le « diamant »
        val t = versRadians(-30f)
        o.halo(c, cx + cos(t) * R, cy + sin(t) * R, R * 0.25f, 0.5f * perle * app * a, Color.WHITE)
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, rgb(220, 225, 240))
    }
}

// ===================== DISCORD : « Néon sous la pluie » =====================
// Une ruelle sous la pluie : une enseigne néon bleu-violet grésille puis s'allume, et se reflète dans les flaques.
class EnseigneNeon : Scene() {
    private val pluie = nuage(161, 90)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val sol = h * 0.7f
        degradeVertical(c, o, w, rgb(8, 8, 14), rgb(12, 12, 20), 0f, sol)
        degradeVertical(c, o, w, rgb(10, 10, 16), rgb(4, 4, 7), sol, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val violet = rgb(110, 120, 255)
        val m = min(w, h)
        val cx = w / 2f; val cy = h * 0.4f
        val s = m * 0.24f * (1f + 2.5f * accelere(rev))
        val gresille = if (sec > 0.35f || sin(sec * 90f) > 0.2f) 1f else 0.25f   // le néon grésille avant de tenir
        val lum = app * gresille * a
        c.drawRect(0f, 0f, w * 0.12f, sol, o.pinceau(rgb(6, 6, 10)))        // murs
        c.drawRect(w * 0.88f, 0f, w, sol, o.pinceau(rgb(6, 6, 10)))
        o.tache(c, cx, cy, s * 2.2f, s * 1.6f, 0.18f * lum, violet)
        val forme = Path().apply { addRoundRect(RectF(cx - s, cy - s * 0.62f, cx + s, cy + s * 0.62f), s * 0.5f, s * 0.5f, Path.Direction.CW) }
        val couches = arrayOf(floatArrayOf(18f, 0.08f), floatArrayOf(8f, 0.25f), floatArrayOf(3f, 0.9f))
        for (cc in couches) c.drawPath(forme, o.lumiere(avecAlpha(violet, cc[1] * lum), cc[0] * dp))
        c.drawPath(forme, o.lumiere(avecAlpha(rgb(230, 235, 255), 0.6f * lum), 1f * dp))
        for (k in intArrayOf(-1, 1)) c.drawCircle(cx + k * s * 0.35f, cy, s * 0.11f, o.lumiere(avecAlpha(violet, 0.8f * lum), 2.5f * dp))
        c.save(); c.clipRect(0f, sol, w, h)                                  // reflet dans la flaque
        c.translate(0f, 2f * sol); c.scale(1f, -1f)
        c.drawPath(forme, o.lumiere(avecAlpha(violet, 0.25f * lum), 6f * dp))
        c.restore()
        o.tache(c, cx, sol + (sol - cy) * 0.8f, s * 1.4f, s * 0.25f, 0.15f * lum, violet)
        for (g in pluie) {                                                   // la pluie
            val z = g[2]
            val x = g[0] * w
            val y = modulo(g[1] + sec * (1.6f + z), 1f) * h
            c.drawLine(x, y, x - 2f * dp, y + (8f + 14f * z) * dp, o.lumiere(argb((30f + 50f * z) * app * a, 170, 180, 230), 1f * dp))
        }
        o.vignette(c, w, h, 0.6f)
        o.revelationDouce(c, cx, cy, rev, violet)
    }
}

// ===================== SPOTIFY : « Vinyle » =====================
// Un disque vinyle tourne sur la platine ; les sillons accrochent une lumière verte, le bras se pose.
class Vinyle : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(10, 12, 11), rgb(4, 5, 4), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val vert = rgb(40, 210, 100)
        val m = min(w, h)
        val cx = w / 2f; val cy = h / 2f
        val R = m * 0.4f * (1f + 2f * accelere(rev))
        o.tache(c, cx, cy + R * 1.02f, R * 0.9f, 8f * dp, 0.5f * app, Color.BLACK)
        if (app > 0.01f) {
            val disque = o.pinceau()
            disque.shader = RadialGradient(cx, cy, R, rgb(20, 20, 22), rgb(10, 10, 11), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, R * app, disque)
            val sillon = o.pinceau(argb(255f * app * a, 26, 26, 28), 0.8f * dp)
            for (k in 0 until 26) c.drawCircle(cx, cy, R * (0.38f + 0.6f * k / 26f) * app, sillon)
            for (base in floatArrayOf(35f, 215f)) {                // reflets fixes de la lampe sur les sillons
                val p = o.lumiere()
                p.shader = SweepGradient(cx, cy, intArrayOf(avecAlpha(vert, 0f), avecAlpha(vert, 0.22f * app * a), avecAlpha(vert, 0f)), floatArrayOf(0f, 0.04f, 0.08f))
                c.save(); c.rotate(base - 8f, cx, cy)
                c.drawCircle(cx, cy, R * app, p)
                c.restore()
            }
            c.save(); c.rotate(sec * 200f, cx, cy)                 // l'étiquette tourne
            c.drawCircle(cx, cy, R * 0.32f * app, o.pinceau(avecAlpha(fonce(vert, 0.45f), app)))
            c.drawArc(RectF(cx - R * 0.25f, cy - R * 0.25f, cx + R * 0.25f, cy + R * 0.25f), 0f, 90f, false, o.lumiere(avecAlpha(vert, 0.5f * app * a), 2f * dp))
            c.drawArc(RectF(cx - R * 0.18f, cy - R * 0.18f, cx + R * 0.18f, cy + R * 0.18f), 180f, 70f, false, o.lumiere(avecAlpha(vert, 0.4f * app * a), 2f * dp))
            c.restore()
            c.drawCircle(cx, cy, 3f * dp, o.pinceau(argb(255f * app, 180, 180, 180)))
        }
        val pose = ralentit(borne((sec - 0.15f) / 0.5f))           // le bras de lecture se pose
        val px = cx + R * 0.95f; val py = cy - R * 0.95f
        val ang = versRadians(mix(100f, 128f, pose))
        val bx = px + cos(ang) * R * 1.05f; val by = py + sin(ang) * R * 1.05f
        c.drawLine(px, py, bx, by, o.pinceau(argb(255f * app * a, 150, 152, 158), 4f * dp))
        c.drawCircle(px, py, 10f * dp, o.pinceau(argb(255f * app * a, 60, 62, 66)))
        c.drawRect(bx - 6f * dp, by - 4f * dp, bx + 6f * dp, by + 6f * dp, o.pinceau(argb(255f * app * a, 120, 122, 128)))
        o.halo(c, bx, by, 10f * dp, 0.4f * pose * app * a, vert)
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, cy, rev, vert)
    }
}

// ===================== WHATSAPP : « Avion en papier » =====================
// Un avion en papier traverse la nuit en laissant une traînée ; à l'arrivée, deux coches s'allument.
class AvionPapier : Scene() {
    private val etoiles = nuage(171, 70)

    private fun px(t: Float, w: Float) = mix(-0.1f, 0.5f, t) * w
    private fun py(t: Float, h: Float) = h * (0.62f - 0.22f * sin(PI.toFloat() * t * 0.9f)) + sin(t * 9f) * h * 0.015f

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(5, 10, 10), rgb(8, 16, 14), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val vert = rgb(40, 210, 120)
        o.poussieres(c, etoiles, sec * 0.2f, w, h, rgb(210, 240, 230), 0.4f * a, vx = 0f, vy = 0f)
        val vol = ralentit(borne(sec / 0.85f))
        val trainee = Path()
        for (s in 0..40) {
            val t = vol * s / 40f
            if (s == 0) trainee.moveTo(px(t, w), py(t, h)) else trainee.lineTo(px(t, w), py(t, h))
        }
        c.drawPath(trainee, o.lumiere(avecAlpha(vert, 0.06f * app * a), 8f * dp))
        c.drawPath(trainee, o.lumiere(avecAlpha(vert, 0.4f * app * a), 2f * dp))
        val x = px(vol, w); val y = py(vol, h)
        val v2 = max(0f, vol - 0.02f)
        val ang = atan2(y - py(v2, h), x - px(v2, w) + 1e-6f) * 180f / PI.toFloat()
        val s = min(w, h) * 0.1f
        c.save(); c.translate(x, y); c.rotate(ang)
        val aile = Path(); aile.moveTo(s, 0f); aile.lineTo(-s, -s * 0.55f); aile.lineTo(-s * 0.55f, 0f); aile.close()
        c.drawPath(aile, o.pinceau(argb(255f * app * a, 225, 235, 230)))
        val aile2 = Path(); aile2.moveTo(s, 0f); aile2.lineTo(-s * 0.55f, 0f); aile2.lineTo(-s * 0.8f, s * 0.4f); aile2.close()
        c.drawPath(aile2, o.pinceau(argb(255f * app * a, 150, 165, 160)))
        c.restore()
        o.halo(c, x, y, s * 1.5f, 0.15f * app * a, vert)
        val coches = borne((sec - 0.8f) / 0.15f)                   // les deux coches
        for (k in 0 until 2) {
            val f = borne(coches * 2f - k * 0.6f)
            if (f <= 0f) continue
            val bx = x + s * 1.4f + k * s * 0.45f; val by = y - s * 1.1f
            val p = Path(); p.moveTo(bx - s * 0.3f, by); p.lineTo(bx - s * 0.1f, by + s * 0.22f); p.lineTo(bx + s * 0.32f, by - s * 0.25f)
            c.drawPath(p, o.lumiere(avecAlpha(vert, 0.9f * f * a), 3f * dp))
            o.halo(c, bx, by, s * 0.5f, 0.3f * f * a, vert)
        }
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, x, y, rev, vert)
    }
}

// ===================== BOLT : « Route de nuit » =====================
// La nuit, sur une route : les lignes vertes du marquage filent sous nos roues, des phares passent.
class Route : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val horizon = h * 0.45f
        degradeVertical(c, o, w, rgb(6, 8, 12), rgb(14, 16, 22), 0f, horizon)
        c.drawRect(0f, horizon, w, h, o.pinceau(rgb(7, 8, 9)))
        val app = ralentit(intro)
        val a = 1f - rev
        val vert = rgb(50, 220, 130)
        val cx = w / 2f
        val vitesse = 1.5f + 4f * accelere(rev)
        val route = Path()
        route.moveTo(cx - 6f * dp, horizon); route.lineTo(cx + 6f * dp, horizon); route.lineTo(cx + w * 0.9f, h); route.lineTo(cx - w * 0.9f, h); route.close()
        c.drawPath(route, o.pinceau(argb(255f * app, 16, 17, 19)))
        for (cote in intArrayOf(-1, 1)) c.drawLine(cx + cote * 6f * dp, horizon, cx + cote * w * 0.9f, h, o.lumiere(avecAlpha(vert, 0.35f * app * a), 2f * dp))
        for (k in 0 until 10) {                                    // tirets qui arrivent vers nous
            val z = modulo(k / 10f + sec * vitesse * 0.5f, 1f)
            val z1 = min(1f, z + 0.04f)
            c.drawLine(cx, horizon + (h - horizon) * z * z, cx, horizon + (h - horizon) * z1 * z1,
                o.lumiere(avecAlpha(vert, 0.8f * z * app * a), (1f + 8f * z) * dp))
        }
        val phare = rgb(255, 240, 210)
        for (k in 0 until 3) {                                     // phares en face, au loin
            val t = modulo(sec * 0.6f + k * 0.33f, 1f)
            val y = horizon + (h - horizon) * t * t
            val x = cx - w * 0.3f * t * t - 4f * dp
            o.halo(c, x - 6f * dp * (1f + 6f * t), y, (4f + 30f * t) * dp, 0.35f * app * a, phare)
            o.halo(c, x + 6f * dp * (1f + 6f * t), y, (4f + 30f * t) * dp, 0.35f * app * a, phare)
        }
        o.tache(c, cx, horizon, w * 0.5f, 6f * dp, 0.3f * app * a, vert)
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, horizon, rev, vert)
    }
}

// ===================== DUOLINGO : « Flamme de série » =====================
// La flamme de la série de jours : elle grandit, danse, et lâche des braises.
class Flamme : Scene() {
    private val braises = nuage(181, 40)
    private val langues = arrayOf(floatArrayOf(1f), floatArrayOf(0.72f), floatArrayOf(0.42f))
    private val couleurs = intArrayOf(rgb(255, 110, 20), rgb(255, 170, 40), rgb(255, 230, 140))

    private fun langue(cx: Float, base: Float, haut: Float, larg: Float, ph: Float, sec: Float): Path {
        val p = Path()
        val osc = sin(sec * 9f + ph) * larg * 0.25f
        p.moveTo(cx - larg, base)
        p.cubicTo(cx - larg * 1.1f, base - haut * 0.5f, cx - larg * 0.2f + osc, base - haut * 0.7f, cx + osc * 1.5f, base - haut)
        p.cubicTo(cx + larg * 0.3f + osc, base - haut * 0.6f, cx + larg * 1.1f, base - haut * 0.45f, cx + larg, base)
        p.close()
        return p
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(8, 7, 6))
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f; val base = h * 0.64f
        val H = m * 0.42f * app * (1f + 2f * accelere(rev))
        val L = m * 0.16f * (1f + 2f * accelere(rev))
        o.tache(c, cx, base, m * 0.4f, m * 0.06f, 0.3f * app * a, rgb(255, 140, 40))
        o.halo(c, cx, base - H * 0.45f, max(1f, H * 0.9f), 0.2f * app * a, rgb(255, 120, 30))
        if (H > 1f) {
            for (k in 0 until 3) {
                val f = langues[k][0]
                val p = o.lumiere()
                p.shader = LinearGradient(0f, base, 0f, base - H * f, avecAlpha(couleurs[k], 0.85f * app * a), avecAlpha(couleurs[k], 0.2f * app * a), Shader.TileMode.CLAMP)
                c.drawPath(langue(cx, base, H * f, L * f, k * 1.7f, sec), p)
            }
        }
        val braise = o.lumiere()
        for (b in braises) {
            val t = modulo(b[1] + sec * (0.5f + 0.6f * b[2]), 1f)
            val x = cx + (b[0] - 0.5f) * L * 2.5f + sin(sec * 3f + b[3]) * 10f * dp * t
            braise.color = avecAlpha(rgb(255, 170, 60), (1f - t) * 0.8f * app * a)
            c.drawCircle(x, base - t * m * 0.6f, (1f + b[2]) * dp, braise)
        }
        o.vignette(c, w, h, 0.6f)
        o.revelationDouce(c, cx, base - H * 0.4f, rev, rgb(255, 150, 50))
    }
}

// ===================== SUNO : « Spectre » =====================
// Un paysage fait de son : les vagues de la musique avancent vers nous comme des collines.
class Spectre : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(8, 6, 14), rgb(4, 3, 8), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val horizon = h * 0.42f
        val cx = w / 2f
        val lignes = 16
        val fond = o.pinceau(rgb(6, 5, 10))
        for (r in lignes downTo 1) {                                // du fond vers l'avant
            val z = r.toFloat() / lignes
            val yBase = horizon + (h - horizon) * (1f - z).pow(1.6f)
            val larg = w * (0.35f + 1.1f * (1f - z))
            val n = 28
            val coul = mixCouleur(rgb(255, 120, 60), rgb(150, 80, 255), z)
            val chemin = Path()
            for (s in 0..n) {
                val t = s.toFloat() / n
                val x = cx + (t - 0.5f) * larg
                val amp = (0.5f + 0.5f * sin(t * 11f + sec * 6f + r * 0.7f)) * (0.5f + 0.5f * sin(t * 3f - sec * 2f + r))
                val y = yBase - amp * h * 0.12f * (1f - 0.6f * z) * app * sin(PI.toFloat() * t)
                if (s == 0) chemin.moveTo(x, y) else chemin.lineTo(x, y)
            }
            val plein = Path(chemin)
            plein.lineTo(cx + larg / 2f, yBase + 4f * dp); plein.lineTo(cx - larg / 2f, yBase + 4f * dp); plein.close()
            c.drawPath(plein, fond)                                 // cache les collines de derrière
            c.drawPath(chemin, o.lumiere(avecAlpha(coul, (0.25f + 0.6f * (1f - z)) * app * a), (1f + 1.5f * (1f - z)) * dp))
        }
        o.tache(c, cx, horizon, w * 0.4f, h * 0.05f, 0.25f * app * a, rgb(200, 100, 255))
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, horizon, rev, rgb(220, 120, 255))
    }
}

// ===================== GITHUB : « Branches » =====================
// L'historique d'un projet : des branches lumineuses se séparent et se rejoignent, les commits s'allument.
class Branches : Scene() {
    private val couleurs = intArrayOf(rgb(120, 200, 255), rgb(130, 230, 140), rgb(230, 150, 255))

    // Décalage horizontal de la branche k à la hauteur t : elle sort de la principale puis y revient.
    private fun voie(k: Int, t: Float, ecart: Float): Float {
        if (k == 0) return 0f
        val debut = if (k == 1) 0.15f else 0.4f
        val fin = if (k == 1) 0.7f else 0.95f
        val u = (t - debut) / (fin - debut)
        if (u <= 0f || u >= 1f) return 0f
        return (k * 2 - 3) * ecart * sin(PI.toFloat() * min(1f, u * 1.3f)).pow(0.6f)
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(8, 10, 14), rgb(4, 5, 7), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f
        val ecart = m * 0.2f
        val avance = sec * 0.9f
        val visible = ralentit(borne(sec / 0.4f))
        val couches = arrayOf(floatArrayOf(12f, 0.08f), floatArrayOf(4f, 0.25f), floatArrayOf(2.5f, 0.9f))
        for (k in 0 until 3) {
            val chemin = Path()
            var premier = true
            for (s in 0..60) {
                val t = s / 60f
                if (t > visible) break
                val x = cx + voie(k, modulo(t + avance * 0.1f, 1f), ecart)
                if (premier) { chemin.moveTo(x, h * (1f - t)); premier = false } else chemin.lineTo(x, h * (1f - t))
            }
            for (cc in couches) c.drawPath(chemin, o.lumiere(avecAlpha(couleurs[k], cc[1] * app * a), cc[0] * dp))
        }
        val fond = o.pinceau(rgb(8, 10, 14))
        for (n in 0 until 12) {                                    // les commits
            val t = n / 11f
            if (t > visible) continue
            val k = n % 3
            val y = h * (1f - t)
            val x = cx + voie(k, modulo(t + avance * 0.1f, 1f), ecart)
            val pulse = 0.5f + 0.5f * sin(sec * 6f - n)
            c.drawCircle(x, y, 5f * dp, fond)
            c.drawCircle(x, y, 5f * dp, o.lumiere(avecAlpha(couleurs[k], 0.9f * app * a), 2f * dp))
            o.halo(c, x, y, 12f * dp, 0.2f * pulse * app * a, couleurs[k])
        }
        o.vignette(c, w, h, 0.55f)
        o.revelationBalayageDouce(c, rev, -90f, couleurs[0])
    }
}

// ===================== BONJOUR RATP : « Quai » =====================
// Sur le quai, une rame de métro entre en station : ses fenêtres éclairées défilent puis ralentissent.
class Quai : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(7, 8, 10))
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cy = h * 0.48f
        val H = m * 0.36f
        val teinte = rgb(60, 200, 170)
        c.drawRect(0f, cy + H * 0.6f, w, h, o.pinceau(rgb(12, 12, 14)))      // le quai et sa bande jaune
        c.drawLine(0f, cy + H * 0.6f, w, cy + H * 0.6f, o.lumiere(avecAlpha(rgb(230, 200, 80), 0.4f * app * a), 3f * dp))
        val freinage = ralentit(borne(sec / 1.0f))
        val x0 = -w * 3f * (1f - freinage) - (1f - app) * w
        val voiture = m * 0.9f
        for (k in -1..4) {
            val vx = x0 + k * voiture
            if (vx > w || vx + voiture < 0f) continue
            val caisse = RectF(vx + 4f * dp, cy - H / 2f, vx + voiture - 4f * dp, cy + H / 2f)
            c.drawRoundRect(caisse, 10f * dp, 10f * dp, o.pinceau(argb(255f * app, 28, 32, 36)))
            c.drawLine(caisse.left, cy + H * 0.15f, caisse.right, cy + H * 0.15f, o.pinceau(avecAlpha(teinte, 0.6f * app * a), 4f * dp))
            for (f in 0 until 3) {                                   // fenêtres éclairées
                val fx = vx + voiture * (0.12f + 0.3f * f)
                val fen = RectF(fx, cy - H * 0.35f, fx + voiture * 0.2f, cy + H * 0.02f)
                val p = o.pinceau()
                p.shader = LinearGradient(0f, fen.top, 0f, fen.bottom, argb(255f * app * a, 230, 220, 180), argb(255f * app * a, 150, 140, 110), Shader.TileMode.CLAMP)
                c.drawRoundRect(fen, 4f * dp, 4f * dp, p)
                o.tache(c, fen.centerX(), cy + H * 0.75f, voiture * 0.12f, 4f * dp, 0.15f * app * a, rgb(255, 230, 180))
            }
        }
        val flou = (1f - freinage) * 0.8f                            // filé de vitesse
        if (flou > 0.05f) {
            val trait = o.lumiere(argb(20f * flou * app * a, 230, 220, 180), 2f * dp)
            for (k in 0 until 12) { val y = cy - H / 2f + H * k / 12f; c.drawLine(0f, y, w, y, trait) }
        }
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, w / 2f, cy, rev, teinte)
    }
}

// ===================== RADIO GARDEN : « Antenne » =====================
// Une antenne au sommet d'une colline émet des ondes vertes dans la nuit.
class Antenne : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(5, 9, 8), rgb(9, 14, 12), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val vert = rgb(70, 230, 140)
        val m = min(w, h)
        val cx = w / 2f; val sol = h * 0.68f
        val colline = Path(); colline.moveTo(0f, h); colline.lineTo(0f, sol + m * 0.1f)
        colline.quadTo(cx, sol - m * 0.08f, w, sol + m * 0.1f); colline.lineTo(w, h); colline.close()
        c.drawPath(colline, o.pinceau(rgb(4, 6, 5)))
        val haut = sol - m * 0.3f
        val metal = o.pinceau(argb(255f * app, 40, 46, 44), 2.5f * dp)
        for (k in intArrayOf(-1, 1)) c.drawLine(cx + k * m * 0.06f, sol, cx, haut, metal)
        val barreau = o.pinceau(argb(255f * app, 40, 46, 44), 1.5f * dp)
        for (y in 0 until 4) {
            val yy = mix(sol, haut, y / 4f)
            c.drawLine(cx - m * 0.06f * (1f - y / 4f), yy, cx + m * 0.06f * (1f - y / 4f), yy, barreau)
        }
        for (k in 0 until 4) {                                     // les ondes
            val t = modulo(sec * 2.2f + k / 4f, 1f)
            val r = t * m * 0.7f
            c.drawArc(RectF(cx - r, haut - r, cx + r, haut + r), -150f, 120f, false, o.lumiere(avecAlpha(vert, 0.6f * (1f - t) * app * a), 2f * dp))
        }
        val clign = 0.5f + 0.5f * sin(sec * 12f)
        o.halo(c, cx, haut, 10f * dp, 0.6f * clign * app * a, rgb(255, 80, 70))
        c.drawCircle(cx, haut, 2.5f * dp, o.pinceau(avecAlpha(rgb(255, 120, 100), app * a)))
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, haut, rev, vert)
    }
}

// ===================== SOUND CONNECT : « Silence » =====================
// Un casque dans le noir : les bruits du dehors (particules) s'arrêtent net contre une bulle de calme.
class Silence : Scene() {
    private val bruit = nuage(191, 80)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(7, 7, 9))
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f; val cy = h / 2f
        val bulle = m * 0.3f * (0.6f + 0.4f * app) * (1f + 2.5f * accelere(rev))
        val grain = o.pinceau(argb(110f * app * a, 170, 170, 180))
        for (b in bruit) {                                         // le bruit arrive de partout
            val ang = b[0] * 6.28f
            val d = (1f - modulo(b[1] + sec * (0.8f + b[2]), 1f)) * max(w, h) * 0.7f + bulle
            c.drawCircle(cx + cos(ang) * d, cy + sin(ang) * d, (1f + b[2]) * dp, grain)
        }
        c.drawCircle(cx, cy, bulle, o.lumiere(argb(50f * app * a, 200, 210, 255), 2f * dp))
        o.halo(c, cx, cy, bulle, 0.1f * app * a, rgb(180, 190, 230))
        val s = m * 0.16f                                          // le casque
        c.drawArc(RectF(cx - s, cy - s, cx + s, cy + s), 200f, 140f, false, o.pinceau(argb(255f * app * a, 60, 62, 70), 6f * dp))
        for (k in intArrayOf(-1, 1)) {
            val r = RectF(cx + k * s - s * 0.22f, cy - s * 0.1f, cx + k * s + s * 0.22f, cy + s * 0.55f)
            c.drawRoundRect(r, s * 0.18f, s * 0.18f, o.pinceau(argb(255f * app * a, 40, 42, 48)))
            c.drawRoundRect(r, s * 0.18f, s * 0.18f, o.lumiere(argb(60f * app * a, 200, 210, 230), 1f * dp))
        }
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, rgb(180, 190, 230))
    }
}

// ===================== ONEDRIVE : « Nuées » =====================
// Des nuages sombres dérivent ; des petits fichiers lumineux montent et s'y rangent.
class Nuees : Scene() {
    private val nuages = Random(201).let { r -> List(7) { floatArrayOf(r.nextFloat(), 0.15f + 0.5f * r.nextFloat(), 0.5f + 0.5f * r.nextFloat()) } }
    private val fichiers = nuage(202, 14)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(6, 9, 18), rgb(10, 14, 26), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val bleu = rgb(70, 150, 255)
        val m = min(w, h)
        for (n in nuages) {
            val z = n[2]
            val x = (modulo(n[0] + sec * 0.05f * z, 1.3f) - 0.15f) * w
            val y = n[1] * h
            for (k in 0 until 4) {
                o.tache(c, x + (k - 1.5f) * m * 0.08f * z, y + sin(k * 2f) * m * 0.02f, m * 0.12f * z, m * 0.06f * z, 0.12f * app * a, rgb(110, 130, 180))
            }
        }
        for (f in fichiers) {
            val t = modulo(f[1] + sec * 1.2f, 1f)
            val x = f[0] * w
            val y = h * (1f - t * 0.7f)
            val s = (5f + 4f * f[2]) * dp
            val al = sin(PI.toFloat() * t) * app * a
            c.drawRoundRect(RectF(x - s * 0.75f, y - s, x + s * 0.75f, y + s), 2f * dp, 2f * dp, o.lumiere(avecAlpha(bleu, 0.5f * al), 1.2f * dp))
            o.halo(c, x, y, s * 1.5f, 0.15f * al, bleu)
        }
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, w / 2f, h * 0.35f, rev, bleu)
    }
}
