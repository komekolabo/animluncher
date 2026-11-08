package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// LOT 3 : les apps du système Samsung. Même principe : un décor fort, puis une révélation qui raconte quelque chose.

private fun rad(degres: Float): Float = degres * PI.toFloat() / 180f
private fun deg(radians: Float): Float = radians * 180f / PI.toFloat()

// ===================== TÉLÉPHONE : « Appel » =====================
// Un point de lumière verte flotte au-dessus d'une eau noire. Chaque sonnerie
// fait naître une onde à la surface et des arcs sonores dans l'air.
class Appel : Scene() {
    private val poussiere = nuage(51, 40)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val eau = h * 0.6f
        degradeVertical(c, o, w, rgb(6, 10, 10), rgb(12, 20, 18), 0f, eau)
        degradeVertical(c, o, w, rgb(8, 13, 12), rgb(3, 4, 4), eau, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val vert = rgb(60, 205, 120)
        val cx = w / 2f; val cy = eau - 70f * dp
        o.poussieres(c, poussiere, sec, w, eau, rgb(150, 230, 190), 0.3f * app * a, vx = 0.004f, vy = -0.01f)
        for (k in 0 until 5) {                                   // ondes à la surface
            val t = modulo(sec * 0.9f + k / 5f, 1f)
            val r = t * min(w, h) * 0.75f
            c.save(); c.translate(cx, eau); c.scale(1f, 0.22f)
            c.drawCircle(0f, 0f, r, o.lumiere(avecAlpha(vert, 0.45f * (1f - t) * app * a), 2.2f * dp / 0.22f * 0.35f))
            c.restore()
        }
        for (k in 0 until 3) {                                   // arcs sonores dans l'air
            val t = modulo(sec * 1.4f + k / 3f, 1f)
            val r = (24f + 70f * t) * dp
            val rect = RectF(cx - r, cy - r, cx + r, cy + r)
            for (debut in floatArrayOf(-35f, 145f)) {
                c.drawArc(rect, debut, 70f, false, o.lumiere(avecAlpha(vert, 0.6f * (1f - t) * app * a), 2.5f * dp))
            }
        }
        val souffle = 1f + 0.12f * sin(sec * 9f)
        o.halo(c, cx, cy, 60f * dp * souffle, 0.30f * app * a, vert)
        c.drawCircle(cx, cy, 9f * dp * app * souffle, o.pinceau(avecAlpha(rgb(200, 255, 220), app * a)))
        val yr = 2f * eau - cy
        for (k in 0 until 6) {
            o.tache(c, cx + sin(sec * 7f + k) * 4f * dp, yr - k * 6f * dp, 14f * dp, 4f * dp, 0.18f * app * a, vert)
        }
        o.tache(c, cx, eau, w * 0.4f, 3f * dp, 0.2f * app * a, vert)
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, cy, rev, vert)
    }
}

// ===================== MESSAGES : « Bulles » =====================
// Des bulles de conversation montent dans la pénombre, à différentes profondeurs.
// Celle du premier plan « écrit » (trois points), puis grandit jusqu'à devenir l'app.
class Bulles : Scene() {
    private val bulles = Random(61).let { r -> List(14) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.3f + 0.6f * r.nextFloat(), if (r.nextFloat() < 0.5f) 1f else 0f) } }
    private val flou = bokehListe(62, intArrayOf(rgb(70, 120, 255), rgb(90, 200, 255)), 7)

    private fun bulle(c: Canvas, o: Outils, x: Float, y: Float, L: Float, H: Float, coul: Int, alpha: Float, droite: Boolean) {
        val p = o.pinceau()
        p.shader = LinearGradient(x, y - H / 2f, x, y + H / 2f, avecAlpha(coul, alpha), avecAlpha(fonce(coul, 0.55f), alpha), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(x - L / 2f, y - H / 2f, x + L / 2f, y + H / 2f), H * 0.45f, H * 0.45f, p)
        val s = if (droite) 1f else -1f
        val queue = Path()
        queue.moveTo(x + s * L * 0.32f, y + H * 0.3f)
        queue.lineTo(x + s * L * 0.5f, y + H * 0.62f)
        queue.lineTo(x + s * L * 0.12f, y + H * 0.45f)
        queue.close()
        c.drawPath(queue, p)
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(8, 10, 22), rgb(4, 5, 10), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        o.bokeh(c, flou, sec, w, h, 0.2f * app * a)
        val m = min(w, h)
        for (b in bulles.sortedBy { it[2] }) {
            val z = b[2]; val droite = b[3] > 0.5f
            val y = (modulo(b[1] - sec * 0.12f * z, 1.2f) - 0.1f) * h
            val coul = if (droite) rgb(60, 110, 240) else rgb(70, 80, 105)
            bulle(c, o, b[0] * w, y, m * 0.22f * z, m * 0.09f * z, coul, 0.55f * z * app * a, droite)
        }
        val cx = w / 2f; val cy = h * 0.52f + (1f - app) * 60f * dp
        val k = doux(rev)
        val L = mix(m * 0.42f, w * 1.25f, k); val H = mix(m * 0.17f, h * 1.25f, k)
        o.halo(c, cx, cy, L * 0.7f, 0.18f * app * a, rgb(80, 140, 255))
        bulle(c, o, cx, cy, L, H, rgb(64, 118, 250), app, true)
        for (d in 0 until 3) {
            val saut = max(0f, sin(sec * 9f - d * 0.9f))
            c.drawCircle(cx + (d - 1) * H * 0.38f, cy - saut * H * 0.12f, H * 0.09f,
                o.pinceau(avecAlpha(rgb(235, 240, 255), (0.5f + 0.5f * saut) * app * a)))
        }
        o.vignette(c, w, h, 0.5f)
        if (rev > 0f) gommeRect(c, o, RectF(cx - L / 2f, cy - H / 2f, cx + L / 2f, cy + H / 2f), ralentit(rev * 1.3f), H * 0.45f)
    }
}

// ===================== APPAREIL PHOTO : « Diaphragme » =====================
// On regarde dans un objectif : les lamelles du diaphragme s'ouvrent, cherchent la mise au point,
// des reflets colorés glissent sur le verre. À la révélation, le diaphragme s'ouvre en grand sur l'app.
class Diaphragme : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        c.drawColor(rgb(6, 6, 8))
        val app = ralentit(intro)
        val a = 1f - rev
        val R = min(w, h) * 0.4f
        for (k in 0 until 4) {                                   // le fût de l'objectif
            c.drawCircle(cx, cy, R * (1.25f - 0.07f * k), o.pinceau(rgb(18 + 6 * k, 18 + 6 * k, 22 + 6 * k), R * 0.05f))
        }
        c.drawArc(RectF(cx - R * 1.27f, cy - R * 1.27f, cx + R * 1.27f, cy + R * 1.27f), 200f, 70f, false,
            o.lumiere(argb(70f * app, 200, 210, 230), 2f * dp))
        c.drawCircle(cx, cy, R * 1.02f, o.pinceau(rgb(12, 12, 15)))
        // l'ouverture grandit, « respire » pendant la mise au point, puis s'ouvre en grand
        val miseAuPoint = 0.06f * sin(sec * 5f) * exp(-max(0f, sec - 0.5f) * 1.5f)
        val ouv = mix(R * (0.12f + 0.3f * app + miseAuPoint), hypot(w, h), accelere(rev))
        val n = 7
        val tour = rad(sec * 25f + (1f - app) * 60f)
        val pas = 2f * PI.toFloat() / n
        val px = FloatArray(n) { cx + cos(tour + it * pas) * ouv }
        val py = FloatArray(n) { cy + sin(tour + it * pas) * ouv }
        val fut = Path().apply { addCircle(cx, cy, R * 1.02f, Path.Direction.CW) }
        for (k in 0 until n) {                                   // les lamelles
            val k1 = (k + 1) % n
            val ang = tour + (k + 1) * pas + 0.9f
            val ang0 = tour + k * pas + 0.9f
            val lam = Path()
            lam.moveTo(px[k], py[k]); lam.lineTo(px[k1], py[k1])
            lam.lineTo(cx + cos(ang) * R * 1.05f, cy + sin(ang) * R * 1.05f)
            lam.lineTo(cx + cos(ang0) * R * 1.05f, cy + sin(ang0) * R * 1.05f)
            lam.close()
            val ton = 22 + 6 * (k % 2)
            val p = o.pinceau()
            p.shader = LinearGradient(px[k], py[k], cx + cos(ang) * R, cy + sin(ang) * R,
                rgb(ton + 14, ton + 14, ton + 18), rgb(ton, ton, ton + 4), Shader.TileMode.CLAMP)
            c.save(); c.clipPath(fut)
            c.drawPath(lam, p)
            c.drawLine(px[k], py[k], px[k1], py[k1], o.lumiere(argb(60f * app * a, 180, 190, 210), 1.2f * dp))
            c.restore()
        }
        val trou = Path()                                        // le verre, au fond de l'ouverture
        trou.moveTo(px[0], py[0])
        for (k in 1 until n) trou.lineTo(px[k], py[k])
        trou.close()
        val verre = o.pinceau()
        verre.shader = RadialGradient(cx - ouv * 0.3f, cy - ouv * 0.3f, max(1f, ouv * 1.4f), rgb(30, 34, 60), rgb(8, 9, 18), Shader.TileMode.CLAMP)
        c.drawPath(trou, verre)
        c.save(); c.clipPath(trou)
        val reflets = intArrayOf(rgb(120, 80, 255), rgb(60, 200, 160), rgb(255, 150, 80))
        for (k in 0 until 3) {
            val ang = sec * 0.8f + k * 2.1f
            o.tache(c, cx + cos(ang) * ouv * 0.45f, cy + sin(ang) * ouv * 0.45f, ouv * 0.35f, ouv * 0.12f, 0.25f * app * a, reflets[k])
        }
        c.restore()
        val flare = arrayOf(floatArrayOf(-0.9f, 0.10f), floatArrayOf(-0.4f, 0.05f), floatArrayOf(0.5f, 0.07f), floatArrayOf(1.1f, 0.13f))
        val flareC = intArrayOf(rgb(110, 90, 255), rgb(80, 220, 180), rgb(255, 160, 90), rgb(120, 140, 255))
        for (k in 0 until 4) {                                   // reflets parasites sur une diagonale
            val x = cx + flare[k][0] * R * 0.8f * cos(sec * 0.6f)
            val y = cy + flare[k][0] * R * 0.5f
            c.drawCircle(x, y, R * flare[k][1], o.lumiere(avecAlpha(flareC[k], 0.10f * app * a)))
            c.drawCircle(x, y, R * flare[k][1], o.lumiere(avecAlpha(flareC[k], 0.25f * app * a), 1.2f * dp))
        }
        o.vignette(c, w, h, 0.5f)
        if (rev > 0f) {
            val q = o.pinceau(argb(255f * ralentit(rev * 1.6f), 0, 0, 0))
            q.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            c.drawPath(trou, q)
            o.revelationDouce(c, cx, cy, max(0f, rev * 1.2f - 0.2f))
        }
    }
}

// ===================== GALERIE : « Chambre noire » =====================
// Sous la lumière rouge d'un labo photo, une photo se révèle dans le bac,
// la surface du liquide ondule. À la révélation, la photo sort du bain vers nous.
class ChambreNoire : Scene() {
    private fun paysage(c: Canvas, o: Outils, r: RectF, dev: Float) {
        val L = r.width(); val H = r.height()
        val ciel = o.pinceau()
        ciel.shader = LinearGradient(0f, r.top, 0f, r.bottom,
            rgb(mix(40f, 70f, dev).toInt(), mix(34f, 40f, dev).toInt(), mix(34f, 90f, dev).toInt()),
            rgb(mix(40f, 220f, dev).toInt(), mix(34f, 120f, dev).toInt(), mix(34f, 80f, dev).toInt()), Shader.TileMode.CLAMP)
        c.drawRect(r, ciel)
        o.halo(c, r.left + L * 0.62f, r.top + H * 0.58f, H * 0.25f, 0.55f * dev, rgb(255, 190, 120))
        val plans = arrayOf(floatArrayOf(0.62f, 0.9f, 0.45f), floatArrayOf(0.72f, 1.6f, 0.25f), floatArrayOf(0.84f, 2.3f, 0.1f))
        for (k in 0 until 3) {
            val base = plans[k][0]; val f = plans[k][1]; val ton = plans[k][2]
            val mont = Path(); mont.moveTo(r.left, r.bottom)
            for (s in 0..30) {
                val t = s / 30f
                mont.lineTo(r.left + t * L, r.top + H * (base - 0.08f * sin(t * 6f * f + k) - 0.05f * sin(t * 13f * f)))
            }
            mont.lineTo(r.right, r.bottom); mont.close()
            val v = mix(38f, 30f + 50f * ton, dev).toInt()
            c.drawPath(mont, o.pinceau(rgb(v, (v * 0.75f).toInt(), (v * 0.8f).toInt())))
        }
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(10, 4, 4))
        val app = ralentit(intro)
        val a = 1f - rev
        o.tache(c, w * 0.5f, 0f, w * 0.6f, h * 0.3f, 0.22f * app, rgb(200, 40, 30))   // la lampe rouge
        val cx = w / 2f; val cy = h * 0.56f
        val m = min(w, h)
        val Lh = m * 0.62f; val Lb = m * 0.8f; val H = m * 0.5f
        val bac = Path()
        bac.moveTo(cx - Lh / 2f, cy - H / 2f); bac.lineTo(cx + Lh / 2f, cy - H / 2f)
        bac.lineTo(cx + Lb / 2f, cy + H / 2f); bac.lineTo(cx - Lb / 2f, cy + H / 2f); bac.close()
        c.drawPath(bac, o.pinceau(argb(255f * a, 22, 10, 10)))
        c.drawPath(bac, o.pinceau(argb(200f * a * app, 70, 20, 18), 3f * dp))
        val dev = borne(sec / 1.1f).pow(1.5f)                     // la photo se développe
        val k = doux(rev)
        val pw = m * 0.46f; val ph = m * 0.32f
        val photo = RectF(mix(cx - pw / 2f, -2f, k), mix(cy - ph / 2f + (1f - app) * 30f * dp, -2f, k),
            mix(cx + pw / 2f, w + 2f, k), mix(cy + ph / 2f + (1f - app) * 30f * dp, h + 2f, k))
        c.drawRect(photo.left - 5f * dp, photo.top - 5f * dp, photo.right + 5f * dp, photo.bottom + 5f * dp,
            o.pinceau(rgb(mix(46f, 60f, dev).toInt(), 40, 40)))
        paysage(c, o, photo, dev)
        val filtre = o.pinceau(argb(110f * a, 120, 10, 10))       // filtre rouge de la lampe
        filtre.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        c.drawRect(0f, 0f, w, h, filtre)
        c.save(); c.clipPath(bac)                                 // reflets qui ondulent sur le liquide
        for (s in 0 until 7) {
            val y = cy - H / 2f + modulo(s / 7f + sec * 0.15f, 1f) * H
            val ligne = Path(); ligne.moveTo(cx - Lb / 2f, y)
            for (t in 1..20) ligne.lineTo(cx - Lb / 2f + Lb * t / 20f, y + sin(t * 0.9f + sec * 4f + s) * 3f * dp)
            c.drawPath(ligne, o.lumiere(argb(26f * app * a, 255, 90, 70), 1.4f * dp))
        }
        c.restore()
        o.vignette(c, w, h, 0.6f)
        if (rev > 0f) gommeRect(c, o, photo, ralentit(rev * 1.25f))
    }
}

// ===================== PLAY STORE : « Spirale » =====================
// Des tuiles d'apps montent en spirale autour d'une colonne de lumière.
// À la révélation, la spirale se déroule vers nous.
class Spirale : Scene() {
    private val couleurs = intArrayOf(rgb(0, 160, 255), rgb(0, 210, 140), rgb(255, 200, 0), rgb(255, 70, 80), rgb(150, 100, 255))

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(6, 9, 16), rgb(10, 12, 20), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val cx = w / 2f; val cy = h / 2f
        val m = min(w, h)
        val colonne = o.lumiere()
        colonne.shader = LinearGradient(cx - m * 0.08f, 0f, cx + m * 0.08f, 0f,
            intArrayOf(argb(0f, 80, 180, 255), argb(40f * app * a, 120, 200, 255), argb(0f, 80, 180, 255)), null, Shader.TileMode.CLAMP)
        c.drawRect(cx - m * 0.08f, 0f, cx + m * 0.08f, h, colonne)
        val deroule = accelere(rev)
        val n = 22
        val tuiles = List(n) { k ->                               // [profondeur, x, y, numéro]
            val t = k.toFloat() / n
            val ang = t * 4f * PI.toFloat() + sec * 1.5f
            val hauteur = (modulo(t - sec * 0.08f, 1f) - 0.5f) * h * 1.1f
            val rayon = m * (0.26f + 0.9f * deroule)
            val z = sin(ang)
            floatArrayOf(z, cx + cos(ang) * rayon, cy + hauteur + z * m * 0.05f, k.toFloat())
        }
        for (q in tuiles.sortedBy { it[0] }) {
            val z = q[0]; val x = q[1]; val y = q[2]; val k = q[3].toInt()
            val prof = 0.55f + 0.45f * (z + 1f) / 2f
            val s = m * 0.085f * prof * (1f + 1.5f * deroule)
            val coul = couleurs[k % couleurs.size]
            val alpha = (0.35f + 0.65f * (z + 1f) / 2f) * app * a
            val p = o.pinceau()
            p.shader = LinearGradient(x - s, y - s, x + s, y + s, avecAlpha(coul, alpha), avecAlpha(fonce(coul, 0.35f), alpha), Shader.TileMode.CLAMP)
            val r = RectF(x - s, y - s, x + s, y + s)
            c.drawRoundRect(r, s * 0.45f, s * 0.45f, p)
            c.drawRoundRect(r, s * 0.45f, s * 0.45f, o.lumiere(argb(50f * alpha, 255, 255, 255), 1f * dp))
            if (z > 0.3f) o.halo(c, x, y, s * 1.8f, 0.10f * alpha, coul)
        }
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, cy, rev, rgb(80, 180, 255))
    }
}

// ===================== PARAMÈTRES : « Engrenages » =====================
// Trois engrenages métalliques s'entraînent dans la pénombre ; leurs dents s'emboîtent vraiment.
class Engrenages : Scene() {
    private fun roue(c: Canvas, o: Outils, x: Float, y: Float, r: Float, dents: Int, ang: Float, app: Float, a: Float, dp: Float) {
        val chemin = Path()
        val pas = 2f * PI.toFloat() / dents
        val profil = arrayOf(floatArrayOf(0f, 0.86f), floatArrayOf(0.18f, 0.86f), floatArrayOf(0.28f, 1f),
            floatArrayOf(0.62f, 1f), floatArrayOf(0.72f, 0.86f), floatArrayOf(1f, 0.86f))
        for (k in 0 until dents) {
            for ((n, q) in profil.withIndex()) {
                val tt = ang + k * pas + q[0] * pas
                val px = x + cos(tt) * r * q[1]; val py = y + sin(tt) * r * q[1]
                if (k == 0 && n == 0) chemin.moveTo(px, py) else chemin.lineTo(px, py)
            }
        }
        chemin.close()
        val p = o.pinceau()
        p.shader = LinearGradient(x - r, y - r, x + r, y + r, argb(255f * app, 70, 76, 90), argb(255f * app, 26, 28, 34), Shader.TileMode.CLAMP)
        c.drawPath(chemin, p)
        c.drawPath(chemin, o.lumiere(argb(50f * app * a, 180, 200, 230), 1.2f * dp))
        c.drawCircle(x, y, r * 0.55f, o.pinceau(argb(255f * app, 20, 22, 27)))
        c.drawCircle(x, y, r * 0.55f, o.lumiere(argb(40f * app * a, 150, 170, 210), 1.2f * dp))
        for (k in 0 until 5) {                                   // rayons
            val t = ang + k * 2f * PI.toFloat() / 5f
            c.drawLine(x + cos(t) * r * 0.18f, y + sin(t) * r * 0.18f, x + cos(t) * r * 0.5f, y + sin(t) * r * 0.5f,
                o.pinceau(argb(255f * app, 44, 48, 58), r * 0.09f))
        }
        c.drawCircle(x, y, r * 0.14f, o.pinceau(argb(255f * app, 12, 12, 15)))
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(12, 14, 20), rgb(5, 6, 8), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f; val cy = h / 2f
        o.tache(c, cx - m * 0.2f, cy - m * 0.25f, m * 0.5f, m * 0.35f, 0.10f * app, rgb(140, 160, 220))
        val v = sec * (1.2f + 3f * accelere(rev))
        // gros (16 dents), moyen (10), petit (7) : même taille de dent, vitesses inverses du nombre de dents
        val r1 = m * 0.22f
        val pas = r1 * 2f * PI.toFloat() / 16f
        val r2 = pas * 10f / (2f * PI.toFloat()); val r3 = pas * 7f / (2f * PI.toFloat())
        val d12 = (r1 + r2) * 0.93f; val d23 = (r2 + r3) * 0.93f
        val x2r = d12 * cos(-0.6f); val y2r = d12 * sin(-0.6f)
        val x3r = x2r + d23 * cos(0.9f); val y3r = y2r + d23 * sin(0.9f)
        val gx = (minOf(-r1, x2r - r2, x3r - r3) + maxOf(r1, x2r + r2, x3r + r3)) / 2f   // on centre l'ensemble
        val gy = (minOf(-r1, y2r - r2, y3r - r3) + maxOf(r1, y2r + r2, y3r + r3)) / 2f
        val x1 = cx - gx; val y1 = cy - gy
        val ecarte = accelere(rev) * m * 0.6f
        roue(c, o, x1 - ecarte, y1 + ecarte * 0.3f, r1, 16, v, app, a, dp)
        roue(c, o, x1 + x2r + ecarte, y1 + y2r - ecarte * 0.4f, r2, 10, -v * 16f / 10f + PI.toFloat() / 10f, app, a, dp)
        roue(c, o, x1 + x3r + ecarte * 0.6f, y1 + y3r + ecarte, r3, 7, v * 16f / 7f + 0.3f, app, a, dp)
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, x1, y1, rev, rgb(140, 170, 230))
    }
}

// ===================== CONTACTS : « Constellation » =====================
// Des personnes deviennent des étoiles ; des fils lumineux les relient une à une depuis le centre.
class Constellation : Scene() {
    private val noeuds: List<FloatArray> = Random(71).let { r ->
        listOf(floatArrayOf(0.5f, 0.5f)) + List(14) { j ->
            val k = j + 1
            floatArrayOf(0.5f + cos(k * 2.4f) * (0.12f + 0.3f * r.nextFloat()), 0.5f + sin(k * 2.4f) * (0.1f + 0.28f * r.nextFloat()))
        }
    }
    // chaque nœud se relie au plus proche de ceux qui sont déjà reliés
    private val liens: List<IntArray> = (1 until noeuds.size).map { k ->
        val j = (0 until k).minByOrNull { q -> hypot(noeuds[q][0] - noeuds[k][0], noeuds[q][1] - noeuds[k][1]) }!!
        intArrayOf(j, k)
    }
    private val etoiles = nuage(72, 90)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(6, 8, 16), rgb(10, 8, 18), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        o.poussieres(c, etoiles, sec * 0.3f, w, h, rgb(200, 210, 255), 0.4f * a, vx = 0f, vy = 0f)
        val m = min(w, h)
        val px = FloatArray(noeuds.size) { k -> w / 2f + (noeuds[k][0] - 0.5f) * m * 1.6f + sin(sec + k) * 3f * dp }
        val py = FloatArray(noeuds.size) { k -> h / 2f + (noeuds[k][1] - 0.5f) * m * 1.6f + cos(sec * 0.8f + k) * 3f * dp }
        val bleu = rgb(90, 160, 255)
        for ((n, l) in liens.withIndex()) {
            val f = borne(sec / 0.06f - n * 0.6f)
            if (f <= 0f) continue
            val j = l[0]; val k = l[1]
            val xe = mix(px[j], px[k], f); val ye = mix(py[j], py[k], f)
            c.drawLine(px[j], py[j], xe, ye, o.lumiere(avecAlpha(bleu, 0.08f * app * a), 6f * dp))
            c.drawLine(px[j], py[j], xe, ye, o.lumiere(avecAlpha(bleu, 0.4f * app * a), 2f * dp))
        }
        for (k in noeuds.indices) {
            val arrive = if (k == 0) 1f else borne(sec / 0.06f - (k - 1) * 0.6f - 1f)
            val taille = (if (k == 0) 7f else 4f) * dp * (0.6f + 0.4f * arrive)
            o.halo(c, px[k], py[k], taille * 5f, 0.3f * arrive * app * a, bleu)
            c.drawCircle(px[k], py[k], taille * app, o.pinceau(avecAlpha(rgb(220, 235, 255), (0.3f + 0.7f * arrive) * app * a)))
        }
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, w / 2f, h / 2f, rev, bleu)
    }
}

// ===================== HORLOGE : « Cadran » =====================
// Derrière une horloge épurée, les étoiles tracent des arcs comme une photo en pose longue.
// Les aiguilles tournent vite puis se posent.
class Cadran : Scene() {
    private val etoiles = Random(81).let { r -> List(120) { floatArrayOf(0.1f + 0.9f * r.nextFloat(), r.nextFloat() * 6.28f, r.nextFloat()) } }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(4, 5, 10))
        val app = ralentit(intro)
        val a = 1f - rev
        val cx = w / 2f; val cy = h / 2f
        val D = hypot(w, h) * 0.55f
        for (e in etoiles) {                                     // filés d'étoiles
            val r = e[0] * D; val t = e[2]
            val long = min(60f, 10f + sec * 40f) * (0.5f + 0.5f * t)
            val coul = rgb(mix(150f, 255f, t).toInt(), mix(180f, 220f, t).toInt(), 255)
            c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), deg(e[1]) + sec * 12f, long, false,
                o.lumiere(avecAlpha(coul, 0.35f * app * a * (0.4f + 0.6f * t)), 1.2f * dp))
        }
        val R = min(w, h) * 0.3f
        c.drawCircle(cx, cy, R * 1.05f, o.pinceau(argb(200f * app * a, 6, 7, 12)))
        c.drawCircle(cx, cy, R, o.lumiere(argb(90f * app * a, 170, 190, 255), 1.5f * dp))
        for (k in 0 until 60) {
            val t = rad(k * 6f)
            val grand = k % 5 == 0
            val long = if (grand) 0.12f else 0.05f
            c.drawLine(cx + cos(t) * R * (0.95f - long), cy + sin(t) * R * (0.95f - long), cx + cos(t) * R * 0.95f, cy + sin(t) * R * 0.95f,
                o.pinceau(argb((if (grand) 200f else 80f) * app * a, 200, 210, 240), (if (grand) 2.2f else 1f) * dp))
        }
        val pose = ralentit(borne(sec / 1.0f))
        val minutes = rad(mix(0f, 1080f, pose) - 90f)
        val heures = rad(mix(0f, 90f, pose) - 30f)
        val blanc = avecAlpha(rgb(220, 225, 240), app * a)
        c.drawLine(cx, cy, cx + cos(heures) * R * 0.5f * app, cy + sin(heures) * R * 0.5f * app, o.pinceau(blanc, 4.5f * dp))
        c.drawLine(cx, cy, cx + cos(minutes) * R * 0.78f * app, cy + sin(minutes) * R * 0.78f * app, o.pinceau(blanc, 3f * dp))
        val trotteuse = rad(sec * 540f - 90f)
        c.drawLine(cx, cy, cx + cos(trotteuse) * R * 0.85f * app, cy + sin(trotteuse) * R * 0.85f * app, o.lumiere(argb(220f * a, 255, 120, 90), 1.5f * dp))
        c.drawCircle(cx, cy, 4f * dp * app, o.pinceau(avecAlpha(rgb(255, 130, 100), a)))
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, rgb(170, 190, 255))
    }
}

// ===================== CALENDRIER : « Éphéméride » =====================
// Un bloc de calendrier à arracher : les feuilles s'envolent une à une, les jours défilent.
// La dernière feuille grandit et devient l'app.
class Ephemeride : Scene() {
    private fun feuille(c: Canvas, o: Outils, x: Float, y: Float, L: Float, H: Float, jour: Int, alpha: Float, dp: Float) {
        val p = o.pinceau()
        p.shader = LinearGradient(0f, y - H / 2f, 0f, y + H / 2f, avecAlpha(rgb(44, 46, 56), alpha), avecAlpha(rgb(30, 31, 38), alpha), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(x - L / 2f, y - H / 2f, x + L / 2f, y + H / 2f), 10f * dp, 10f * dp, p)
        c.drawRoundRect(RectF(x - L / 2f, y - H / 2f, x + L / 2f, y - H / 2f + H * 0.22f), 10f * dp, 10f * dp, o.pinceau(avecAlpha(rgb(180, 60, 60), alpha)))
        texte(c, jour.toString(), x, y + H * 0.1f, H * 0.42f, avecAlpha(rgb(225, 228, 238), alpha))
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(12, 12, 16), rgb(5, 5, 7), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f; val cy = h / 2f + (1f - app) * 40f * dp
        val L = m * 0.42f; val H = m * 0.46f
        o.tache(c, cx, cy + H * 0.62f, L * 0.7f, 10f * dp, 0.5f * app, Color.BLACK)
        val periode = 0.22f
        val n = (sec / periode).toInt()
        val phase = modulo(sec / periode, 1f)
        val jour = 1 + (n % 30)
        val k = doux(rev)
        val Lb = mix(L, w * 1.05f, k); val Hb = mix(H, h * 1.05f, k)
        val yb = mix(cy, h / 2f, k)
        feuille(c, o, cx, yb, Lb, Hb, (jour % 30) + 1, app, dp)
        for (s in 0 until 3) {                                   // épaisseur du bloc
            val y = cy + H / 2f + (s + 1) * 2.5f * dp
            c.drawLine(cx - L / 2f + 4f * dp, y, cx + L / 2f - 4f * dp, y, o.pinceau(argb(255f * app * a, 40 - 6 * s, 41 - 6 * s, 50 - 6 * s), 1.5f * dp))
        }
        if (rev == 0f) {                                         // la feuille arrachée s'envole en tournant
            c.save()
            c.translate(cx + phase * L * 0.6f, cy - phase * H * 0.9f)
            c.rotate(-phase * 35f)
            c.scale(1f, max(0.05f, cos(phase * PI.toFloat() * 0.5f)))
            feuille(c, o, 0f, 0f, L, H, jour, app * (1f - phase), dp)
            c.restore()
        }
        o.vignette(c, w, h, 0.55f)
        if (rev > 0f) gommeRect(c, o, RectF(cx - Lb / 2f, yb - Hb / 2f, cx + Lb / 2f, yb + Hb / 2f), ralentit(rev * 1.3f), 10f * dp)
    }
}

// ===================== CALCULATRICE : « Compteur » =====================
// Des tambours de chiffres, comme un vieux compteur mécanique, roulent puis s'arrêtent un à un.
class Compteur : Scene() {
    private val flou = bokehListe(91, intArrayOf(rgb(255, 150, 60), rgb(120, 160, 255)), 6)
    private val cible = intArrayOf(4, 2, 0, 7, 3, 1)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(12, 11, 14), rgb(5, 5, 6), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        o.bokeh(c, flou, sec, w, h, 0.15f * app * a)
        val m = min(w, h)
        val cx = w / 2f; val cy = h / 2f
        val n = 6
        val larg = m * 0.15f
        val R = m * 0.17f
        val cadre = RectF(cx - larg * n / 2f - 8f * dp, cy - R * 0.8f, cx + larg * n / 2f + 8f * dp, cy + R * 0.8f)
        c.drawRoundRect(cadre, 10f * dp, 10f * dp, o.pinceau(argb(255f * app, 20, 19, 22)))
        for (d in 0 until n) {
            val x = cx + (d - n / 2f + 0.5f) * larg
            val arret = 0.35f + d * 0.12f
            val vitesse = 14f + d * 3f
            var pos = if (sec < arret) sec * vitesse else arret * vitesse + ralentit(borne((sec - arret) / 0.25f)) * 0.6f
            pos = mix(pos, floor(arret * vitesse) + 1f, borne((sec - arret) / 0.25f))
            pos += accelere(rev) * 6f
            c.save()
            c.clipRect(x - larg / 2f + 2f * dp, cadre.top, x + larg / 2f - 2f * dp, cadre.bottom)
            val tambour = o.pinceau()
            tambour.shader = LinearGradient(0f, cy - R, 0f, cy + R,
                intArrayOf(argb(255f * app, 10, 10, 12), argb(255f * app, 52, 50, 56), argb(255f * app, 10, 10, 12)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(x - larg / 2f, cy - R, x + larg / 2f, cy + R, tambour)
            for (chiffre in 0 until 10) {
                val ang = (chiffre - pos) * 36f + cible[d] * 36f
                val t = rad(modulo(ang + 180f, 360f) - 180f)
                if (abs(t) > PI.toFloat() / 2f) continue
                val y = cy + sin(t) * R * 0.95f
                val echelle = cos(t)
                c.save(); c.translate(x, y); c.scale(1f, max(0.05f, echelle))
                texte(c, chiffre.toString(), 0f, 0f, R * 0.75f, avecAlpha(rgb(240, 200, 150), echelle * app * a))
                c.restore()
            }
            c.restore()
        }
        c.drawLine(cadre.left, cy, cadre.right, cy, o.lumiere(argb(20f * app * a, 255, 200, 150), R * 0.5f))
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, cy, rev, rgb(255, 170, 90))
    }
}

// ===================== NOTES : « Plume » =====================
// Sur une page sombre, une écriture lumineuse se trace toute seule, la pointe brille comme une braise.
class Plume : Scene() {
    private fun px(t: Float, w: Float) = w * (0.12f + 0.76f * t) + sin(t * 38f) * w * 0.025f
    private fun py(t: Float, h: Float) = h * 0.5f + cos(t * 38f) * h * 0.035f - sin(t * 6f) * h * 0.04f

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(16, 15, 13), rgb(7, 7, 6), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        for (k in 0 until 9) {                                   // lignes du cahier
            val y = h * (0.2f + k * 0.08f)
            c.drawLine(w * 0.06f, y, w * 0.94f, y, o.pinceau(argb(14f * app, 200, 190, 160), 1f * dp))
        }
        val avance = borne(sec / 1.1f)
        val chemin = Path()
        val n = 140
        for (s in 0..n) {
            val t = avance * s / n
            if (s == 0) chemin.moveTo(px(t, w), py(t, h)) else chemin.lineTo(px(t, w), py(t, h))
        }
        val encre = rgb(255, 190, 110)
        val couches = arrayOf(floatArrayOf(12f, 0.06f), floatArrayOf(5f, 0.18f), floatArrayOf(2f, 0.9f))
        for (cc in couches) c.drawPath(chemin, o.lumiere(avecAlpha(encre, cc[1] * app * a), cc[0] * dp))
        val x = px(avance, w); val y = py(avance, h)
        o.halo(c, x, y, 20f * dp, 0.5f * app * a, encre)
        c.drawLine(x, y, x + 34f * dp, y - 60f * dp, o.pinceau(argb(255f * app * a, 60, 56, 52), 4f * dp))     // le stylo
        c.drawLine(x, y, x + 34f * dp, y - 60f * dp, o.lumiere(argb(40f * app * a, 255, 220, 180), 1f * dp))
        o.vignette(c, w, h, 0.55f)
        o.revelationBalayageDouce(c, rev, 0f, encre)
    }
}

// ===================== MES FICHIERS : « Fichier rotatif » =====================
// Un fichier rotatif de bureau : des fiches tournent autour de leur axe ; l'une s'arrête face à nous.
class Rotatif : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(14, 12, 8), rgb(6, 5, 4), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f; val cy = h * 0.52f
        val L = m * 0.5f; val H = m * 0.32f
        val R = H * 0.95f
        o.tache(c, cx, cy, m * 0.5f, m * 0.3f, 0.10f * app, rgb(255, 190, 90))
        val tour = ralentit(borne(sec / 1.1f)) * 720f + 8f * sin(sec * 3f) * borne(sec - 1.1f)
        val n = 16
        val fiches = List(n) { k -> val ang = rad(k * 360f / n - tour); floatArrayOf(cos(ang), sin(ang)) }
        for (f in fiches.sortedBy { it[0] }) {
            val z = f[0]; val s = f[1]
            if (z < -0.2f) continue
            if (z > 0.98f && rev > 0f) continue
            val y = cy - s * R
            val hauteur = H * max(0.04f, z)
            val alpha = (0.4f + 0.6f * z) * app * a
            val p = o.pinceau()
            p.shader = LinearGradient(0f, y - hauteur / 2f, 0f, y + hauteur / 2f, avecAlpha(rgb(80, 66, 40), alpha), avecAlpha(rgb(46, 38, 26), alpha), Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(cx - L / 2f, y - hauteur / 2f, cx + L / 2f, y + hauteur / 2f), 5f * dp, 5f * dp, p)
            c.drawRoundRect(RectF(cx - L * 0.4f, y - hauteur / 2f - 8f * dp * z, cx - L * 0.15f, y - hauteur / 2f + 2f * dp), 3f * dp, 3f * dp,
                o.pinceau(avecAlpha(rgb(255, 196, 70), alpha)))                   // l'onglet jaune
            for (t in 0 until 3) {
                val yy = y - hauteur * 0.15f + t * hauteur * 0.2f
                c.drawLine(cx - L * 0.38f, yy, cx + L * (0.3f - 0.12f * t), yy, o.pinceau(avecAlpha(rgb(150, 130, 100), alpha * 0.7f), 2f * dp))
            }
        }
        c.drawLine(cx - L * 0.6f, cy, cx + L * 0.6f, cy, o.pinceau(argb(255f * app * a, 30, 28, 24), 6f * dp))   // l'axe
        for (sx in intArrayOf(-1, 1)) {
            c.drawLine(cx + sx * L * 0.6f, cy, cx + sx * L * 0.7f, cy + R * 1.2f, o.pinceau(argb(255f * app * a, 26, 24, 20), 8f * dp))
        }
        if (rev > 0f) {                                          // la fiche de face grandit
            val k = doux(rev)
            val Lf = mix(L, w * 1.05f, k); val Hf = mix(H, h * 1.05f, k)
            val yf = mix(cy, h / 2f, k)
            val r = RectF(cx - Lf / 2f, yf - Hf / 2f, cx + Lf / 2f, yf + Hf / 2f)
            c.drawRoundRect(r, 5f * dp, 5f * dp, o.pinceau(rgb(70, 58, 36)))
            gommeRect(c, o, r, ralentit(rev * 1.3f), 5f * dp)
        }
        o.vignette(c, w, h, 0.55f)
    }
}

// ===================== INTERNET (Samsung) : « Toile » =====================
// Une toile d'araignée couverte de rosée. Des impulsions lumineuses courent le long des fils.
class Toile : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(10, 7, 18), rgb(4, 3, 8), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val violet = rgb(150, 110, 255)
        val fil = rgb(170, 160, 210)
        val cx = w / 2f; val cy = h * 0.48f
        val R = hypot(w, h) * 0.5f * (0.7f + 0.3f * app) * (1f + 1.5f * accelere(rev))
        val n = 14
        val angles = FloatArray(n) { k -> k * 2f * PI.toFloat() / n + 0.15f * sin(k * 3.7f) }
        for (t in angles) c.drawLine(cx, cy, cx + cos(t) * R, cy + sin(t) * R, o.lumiere(avecAlpha(fil, 0.22f * app * a), 1f * dp))
        val spirale = Path()
        val tours = 9
        for (s in 0..n * tours) {
            val rr = R * (0.06f + 0.9f * s / (n * tours).toFloat())
            val x = cx + cos(angles[s % n]) * rr; val y = cy + sin(angles[s % n]) * rr
            if (s == 0) spirale.moveTo(x, y) else spirale.lineTo(x, y)
        }
        c.drawPath(spirale, o.lumiere(avecAlpha(fil, 0.18f * app * a), 1f * dp))
        val goutte = o.lumiere()
        for (k in 0 until n) {                                   // rosée qui scintille
            for (g in 1..5) {
                val rr = R * (0.1f + 0.17f * g + 0.03f * sin((k * g).toFloat()))
                val sc = 0.5f + 0.5f * sin(sec * 4f + k * 1.3f + g)
                goutte.color = argb(160f * sc * app * a, 220, 210, 255)
                c.drawCircle(cx + cos(angles[k]) * rr, cy + sin(angles[k]) * rr, 1.6f * dp, goutte)
            }
        }
        for (k in 0 until n step 2) {                            // impulsions depuis le centre
            val d = modulo(sec * 0.8f + k * 0.13f, 1f)
            o.halo(c, cx + cos(angles[k]) * d * R, cy + sin(angles[k]) * d * R, 9f * dp, 0.5f * (1f - d) * app * a, violet)
        }
        o.halo(c, cx, cy, 40f * dp, 0.25f * app * a, violet)
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, cy, rev, violet)
    }
}

// ===================== ENREGISTREUR VOCAL : « Bande » =====================
// Un magnétophone à bandes : les bobines tournent, la bande défile, le voyant rouge palpite.
class Bande : Scene() {
    private fun bobine(c: Canvas, o: Outils, x: Float, y: Float, r: Float, ang: Float, plein: Float, app: Float, a: Float, dp: Float) {
        val bande = o.pinceau()
        bande.shader = RadialGradient(x, y, r * plein,
            intArrayOf(argb(255f * app * a, 60, 42, 30), argb(255f * app * a, 92, 64, 44), argb(255f * app * a, 52, 36, 26)),
            floatArrayOf(0f, 0.85f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * plein, bande)                     // la bande enroulée
        c.drawCircle(x, y, r, o.pinceau(argb(255f * app * a, 110, 112, 122), 2.5f * dp))
        for (k in 0 until 3) {                                   // les trois branches de la bobine
            val t = ang + k * 2f * PI.toFloat() / 3f
            val p = Path()
            p.moveTo(x + cos(t - 0.45f) * r * 0.32f, y + sin(t - 0.45f) * r * 0.32f)
            p.lineTo(x + cos(t - 0.3f) * r * 0.85f, y + sin(t - 0.3f) * r * 0.85f)
            p.lineTo(x + cos(t + 0.3f) * r * 0.85f, y + sin(t + 0.3f) * r * 0.85f)
            p.lineTo(x + cos(t + 0.45f) * r * 0.32f, y + sin(t + 0.45f) * r * 0.32f)
            p.close()
            c.drawPath(p, o.pinceau(argb(255f * app * a, 58, 60, 68)))
            c.drawPath(p, o.lumiere(argb(50f * app * a, 200, 205, 220), 1f * dp))
        }
        c.drawCircle(x, y, r * 0.12f, o.pinceau(argb(255f * app * a, 90, 92, 100)))
        c.drawArc(RectF(x - r, y - r, x + r, y + r), 200f, 60f, false, o.lumiere(argb(60f * app * a, 200, 210, 230), 1.5f * dp))
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(14, 13, 14), rgb(5, 5, 6), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f; val cy = h * 0.45f
        val r = m * 0.2f
        val x1 = cx - r * 1.25f; val x2 = cx + r * 1.25f
        o.tache(c, cx, cy - r * 0.3f, m * 0.6f, m * 0.4f, 0.10f * app * a, rgb(255, 200, 160))
        val ang = sec * 5f * (1f + 2f * accelere(rev))
        val avance = borne(sec / 3f)
        val tx = cx; val ty = cy + r * 1.25f
        val bandeP = o.pinceau(argb(255f * app * a, 60, 44, 34), 2.5f * dp)
        c.drawLine(x1 - r * 0.55f, cy + r * 0.2f, tx, ty, bandeP)
        c.drawLine(x2 + r * 0.55f, cy + r * 0.2f, tx, ty, bandeP)
        bobine(c, o, x1, cy, r, -ang, 0.85f - 0.15f * avance, app, a, dp)
        bobine(c, o, x2, cy, r, -ang * 1.1f, 0.55f + 0.15f * avance, app, a, dp)
        c.drawRoundRect(RectF(tx - 16f * dp, ty - 4f * dp, tx + 16f * dp, ty + 12f * dp), 3f * dp, 3f * dp, o.pinceau(argb(255f * app * a, 80, 82, 90)))
        val yo = cy + r * 2.1f
        val onde = Path()                                        // le signal enregistré
        for (s in 0..60) {
            val t = s / 60f
            val amp = (0.4f + 0.6f * abs(sin(sec * 3f + t * 4f))) * m * 0.035f * sin(PI.toFloat() * t)
            val x = cx - m * 0.35f + t * m * 0.7f
            val y = yo + sin(t * 40f - sec * 12f) * amp
            if (s == 0) onde.moveTo(x, y) else onde.lineTo(x, y)
        }
        val rouge = rgb(240, 70, 60)
        c.drawPath(onde, o.lumiere(avecAlpha(rouge, 0.08f * app * a), 8f * dp))
        c.drawPath(onde, o.lumiere(avecAlpha(rouge, 0.8f * app * a), 2f * dp))
        val pulse = 0.5f + 0.5f * sin(sec * 6f)
        val ry = cy - r * 1.35f
        o.halo(c, cx, ry, 14f * dp, 0.4f * pulse * app * a, rouge)
        c.drawCircle(cx, ry, 5f * dp, o.pinceau(avecAlpha(rouge, (0.5f + 0.5f * pulse) * app * a)))
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, yo, rev, rouge)
    }
}

// ===================== RAPPELS : « Sablier » =====================
// Un sablier en verre, éclairé de côté : le sable s'écoule en filet de grains.
class Sablier : Scene() {
    private val grains = nuage(101, 60)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(10, 12, 18), rgb(5, 5, 8), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f; val cy = h / 2f + (1f - app) * 40f * dp
        val L = m * 0.26f; val H = m * 0.36f
        val sable = rgb(225, 175, 105)
        val verre = Path()
        verre.moveTo(cx - L, cy - H)
        verre.cubicTo(cx - L, cy - H * 0.3f, cx - L * 0.08f, cy - H * 0.15f, cx - L * 0.06f, cy)
        verre.cubicTo(cx - L * 0.08f, cy + H * 0.15f, cx - L, cy + H * 0.3f, cx - L, cy + H)
        verre.lineTo(cx + L, cy + H)
        verre.cubicTo(cx + L, cy + H * 0.3f, cx + L * 0.08f, cy + H * 0.15f, cx + L * 0.06f, cy)
        verre.cubicTo(cx + L * 0.08f, cy - H * 0.15f, cx + L, cy - H * 0.3f, cx + L, cy - H)
        verre.close()
        c.drawPath(verre, o.pinceau(argb(255f * app, 16, 18, 26)))
        val ecoule = borne(sec / 3f) * 0.6f + 0.2f + accelere(rev) * 0.2f
        val hh = H * 0.85f * ecoule
        c.save(); c.clipPath(verre)
        val haut = cy - H * 0.05f - (1f - ecoule) * H * 0.8f                    // le sable du haut
        c.drawRect(cx - L, haut, cx + L, cy, o.pinceau(avecAlpha(sable, 0.9f * app * a)))
        val tas = Path()                                                        // le tas du bas
        tas.moveTo(cx - L, cy + H); tas.lineTo(cx - L, cy + H - hh * 0.4f); tas.lineTo(cx, cy + H - hh)
        tas.lineTo(cx + L, cy + H - hh * 0.4f); tas.lineTo(cx + L, cy + H); tas.close()
        val p = o.pinceau()
        p.shader = LinearGradient(cx - L, 0f, cx + L, 0f, avecAlpha(fonce(sable, 0.6f), app * a), avecAlpha(sable, app * a), Shader.TileMode.CLAMP)
        c.drawPath(tas, p)
        c.restore()
        val grain = o.pinceau(avecAlpha(sable, app * a))
        for (g in grains) {                                                     // le filet de grains
            val y = cy + modulo(g[1] + sec * 1.6f * (0.6f + 0.4f * g[2]), 1f) * (H - hh * 0.98f)
            c.drawCircle(cx + (g[0] - 0.5f) * 3f * dp, y, 1.2f * dp, grain)
        }
        c.drawLine(cx, cy, cx, cy + H - hh, o.lumiere(avecAlpha(sable, 0.3f * app * a), 1.5f * dp))
        c.drawPath(verre, o.lumiere(argb(70f * app * a, 170, 190, 230), 1.5f * dp))
        for (sy in intArrayOf(-1, 1)) {                                         // les deux socles en bois
            c.drawRoundRect(RectF(cx - L * 1.2f, cy + sy * H - 7f * dp, cx + L * 1.2f, cy + sy * H + 7f * dp), 4f * dp, 4f * dp,
                o.pinceau(argb(255f * app * a, 60, 40, 28)))
        }
        o.tache(c, cx + L * 0.6f, cy - H * 0.5f, L * 0.12f, H * 0.4f, 0.18f * app * a, rgb(200, 220, 255))
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, cy, rev, sable)
    }
}

// ===================== SAMSUNG HEALTH : « Pouls » =====================
// Un tracé de pouls court sur l'écran ; derrière, une lueur en forme de cœur bat au même rythme.
class Pouls : Scene() {
    private fun coeur(cx: Float, cy: Float, s: Float): Path {
        val p = Path()
        p.moveTo(cx, cy + s * 0.9f)
        p.cubicTo(cx - s * 1.6f, cy - s * 0.1f, cx - s * 0.8f, cy - s * 1.2f, cx, cy - s * 0.45f)
        p.cubicTo(cx + s * 0.8f, cy - s * 1.2f, cx + s * 1.6f, cy - s * 0.1f, cx, cy + s * 0.9f)
        p.close()
        return p
    }

    // La forme d'un battement de cœur (onde P, complexe QRS, onde T), pour t entre 0 et 1.
    private fun battement(tt: Float): Float {
        val t = modulo(tt, 1f)
        val pi = PI.toFloat()
        return when {
            t < 0.08f -> sin(t / 0.08f * pi) * 0.25f
            t > 0.14f && t < 0.18f -> -0.3f * sin((t - 0.14f) / 0.04f * pi)
            t >= 0.18f && t < 0.24f -> sin((t - 0.18f) / 0.06f * pi)
            t >= 0.24f && t < 0.29f -> -0.45f * sin((t - 0.24f) / 0.05f * pi)
            t > 0.4f && t < 0.55f -> sin((t - 0.4f) / 0.15f * pi) * 0.3f
            else -> 0f
        }
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(6, 12, 10), rgb(3, 5, 4), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val vert = rgb(50, 220, 130)
        val cx = w / 2f; val cy = h / 2f
        val m = min(w, h)
        val b = max(0f, sin(modulo(sec * 1.4f, 1f) * PI.toFloat() * 2f)).pow(3)
        val s = m * 0.2f * (1f + 0.08f * b)
        val forme = coeur(cx, cy, s)
        val p = o.lumiere()
        p.shader = RadialGradient(cx, cy - s * 0.2f, s * 1.4f, avecAlpha(vert, (0.25f + 0.3f * b) * app * a), avecAlpha(vert, 0.05f * app * a), Shader.TileMode.CLAMP)
        c.drawPath(forme, p)
        c.drawPath(forme, o.lumiere(avecAlpha(vert, (0.3f + 0.4f * b) * app * a), 2f * dp))
        o.halo(c, cx, cy - s * 0.1f, s * 1.6f, (0.08f + 0.15f * b) * app * a, vert)
        val grille = o.pinceau(argb(10f * app, 80, 200, 140), 1f * dp)           // la grille du moniteur
        for (k in 1 until 12) c.drawLine(w * k / 12f, 0f, w * k / 12f, h, grille)
        for (k in 1 until 8) c.drawLine(0f, h * k / 8f, w, h * k / 8f, grille)
        val tete = modulo(sec * 1.1f, 1f)
        val trace = Path()
        val n = 140
        for (q in 0..n) {
            val t = q.toFloat() / n
            if (t > tete) break
            val y = cy - battement(t * 2.4f) * m * 0.22f * app
            if (q == 0) trace.moveTo(t * w, y) else trace.lineTo(t * w, y)
        }
        val couches = arrayOf(floatArrayOf(10f, 0.08f), floatArrayOf(4f, 0.22f), floatArrayOf(2f, 0.9f))
        for (cc in couches) c.drawPath(trace, o.lumiere(avecAlpha(vert, cc[1] * app * a), cc[0] * dp))
        o.halo(c, tete * w, cy - battement(tete * 2.4f) * m * 0.22f * app, 16f * dp, 0.6f * app * a, vert)
        o.vignette(c, w, h, 0.55f)
        o.revelationBalayageDouce(c, rev, 0f, vert)
    }
}
