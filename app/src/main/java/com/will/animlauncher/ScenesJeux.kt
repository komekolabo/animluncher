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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// LOT 6 : jeux et utilitaires.

private fun degVersRad(degres: Float): Float = degres * PI.toFloat() / 180f

// ===================== BALATRO : « Cartes » =====================
// Des cartes à jouer tournoient dans un vortex de couleurs sombres, puis se rangent en éventail.
class Cartes : Scene() {
    private val signes = arrayOf("♠", "♥", "♣", "♦", "A")

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(10, 6, 14))
        val app = ralentit(intro)
        val a = 1f - rev
        val cx = w / 2f; val cy = h / 2f
        val m = min(w, h)
        val p = o.lumiere()                                          // le vortex
        p.shader = SweepGradient(cx, cy, intArrayOf(rgb(60, 20, 40), rgb(20, 30, 70), rgb(60, 20, 40)), null)
        p.alpha = (0.35f * app * a * 255).toInt()
        c.save(); c.rotate(sec * 40f, cx, cy); c.drawCircle(cx, cy, max(w, h), p); c.restore()
        val range = doux(borne((sec - 0.2f) / 0.5f))
        val L = m * 0.17f; val H = m * 0.24f
        val n = 5
        for (k in 0 until n) {
            val ang = sec * 3f + k * 2f * PI.toFloat() / n
            val rr = m * 0.25f * (1f - range)
            val x = cx + cos(ang) * rr; val y = cy + sin(ang) * rr * 0.6f
            val rot = ang * 180f / PI.toFloat() * (1f - range) + (k - 2) * 12f * range
            c.save(); c.translate(x, y + H * 0.3f * range); c.rotate(rot); c.translate(0f, -H * 0.3f * range)
            c.drawRoundRect(RectF(-L / 2f, -H / 2f, L / 2f, H / 2f), 6f * dp, 6f * dp, o.pinceau(argb(255f * app * a, 225, 218, 205)))
            val coul = if (k == 1 || k == 3) rgb(190, 50, 60) else rgb(30, 30, 40)
            texte(c, signes[k], 0f, 0f, H * 0.35f, avecAlpha(coul, app * a))
            texte(c, signes[k], -L * 0.3f, -H * 0.36f, H * 0.12f, avecAlpha(coul, app * a))
            c.restore()
        }
        o.vignette(c, w, h, 0.6f)
        o.revelationDouce(c, cx, cy, rev, rgb(220, 80, 100))
    }
}

// ===================== CHESSBAR : « Pendule » =====================
// Une pendule d'échecs : les deux cadrans, l'aiguille qui court, le bouton qu'on frappe.
class Pendule : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(14, 11, 8), rgb(5, 4, 3), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f; val cy = h * 0.55f
        o.tache(c, cx, cy - m * 0.1f, m * 0.5f, m * 0.3f, 0.1f * app, rgb(255, 200, 140))
        val L = m * 0.7f; val H = m * 0.3f
        val corps = RectF(cx - L / 2f, cy - H / 2f, cx + L / 2f, cy + H / 2f)
        val p = o.pinceau()
        p.shader = LinearGradient(0f, corps.top, 0f, corps.bottom, argb(255f * app, 70, 44, 26), argb(255f * app, 34, 22, 14), Shader.TileMode.CLAMP)
        c.drawRoundRect(corps, 10f * dp, 10f * dp, p)
        val frappe = borne((sec - 0.1f) / 0.05f) * (1f - borne((sec - 0.2f) / 0.1f))
        for (k in intArrayOf(-1, 1)) {
            val x = cx + k * L * 0.24f
            val R = H * 0.36f
            c.drawCircle(x, cy, R, o.pinceau(argb(255f * app * a, 220, 212, 196)))
            c.drawCircle(x, cy, R, o.pinceau(argb(255f * app * a, 40, 30, 20), 2f * dp))
            val graduation = o.pinceau(argb(255f * app * a, 60, 50, 40), 1.2f * dp)
            for (t in 0 until 12) {
                val ang = degVersRad(t * 30f)
                c.drawLine(x + cos(ang) * R * 0.82f, cy + sin(ang) * R * 0.82f, x + cos(ang) * R * 0.92f, cy + sin(ang) * R * 0.92f, graduation)
            }
            val vitesse = if ((k < 0) == (sec < 0.12f)) 1f else 0.05f      // le cadran actif tourne vite
            val ang = degVersRad(-90f + sec * 360f * vitesse * 1.5f + k * 40f)
            c.drawLine(x, cy, x + cos(ang) * R * 0.75f, cy + sin(ang) * R * 0.75f, o.pinceau(argb(255f * app * a, 180, 40, 30), 2f * dp))
            val hb = H * 0.12f * (1f - (if (k > 0) frappe else 0f) * 0.7f)
            c.drawRoundRect(RectF(x - L * 0.08f, corps.top - hb, x + L * 0.08f, corps.top + 2f * dp), 3f * dp, 3f * dp, o.pinceau(argb(255f * app * a, 50, 34, 22)))
        }
        o.halo(c, cx + L * 0.24f, corps.top, m * 0.08f, 0.4f * frappe * a, rgb(255, 210, 160))
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, cy, rev, rgb(255, 200, 140))
    }
}

// ===================== CLASH OF CLANS : « Torches » =====================
// Un rempart de pierre dans la nuit, deux torches crépitent et lâchent des étincelles.
class Torches : Scene() {
    private val etincelles = nuage(251, 50)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(8, 9, 16), rgb(14, 12, 14), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val mur = h * 0.5f
        val pierre = m * 0.09f
        val bloc = o.pinceau()
        for (rang in 0..((h - mur) / (pierre * 0.6f)).toInt()) {      // le rempart, pierre par pierre
            val y = mur + rang * pierre * 0.6f
            val dec = (rang % 2) * pierre * 0.5f
            for (k in 0 until (w / pierre).toInt() + 2) {
                val x = k * pierre - dec
                val ton = 26 + (10 * sin(k * 3.1f + rang * 1.7f)).toInt()
                bloc.color = argb(255f * app, ton, ton - 4, ton - 8)
                c.drawRoundRect(RectF(x + 1f * dp, y + 1f * dp, x + pierre - 1f * dp, y + pierre * 0.6f - 1f * dp), 3f * dp, 3f * dp, bloc)
            }
        }
        val creneau = o.pinceau(argb(255f * app, 30, 26, 22))
        for (k in 0..(w / (pierre * 1.4f)).toInt()) { val x = k * pierre * 1.4f; c.drawRect(x, mur - pierre * 0.5f, x + pierre * 0.8f, mur, creneau) }
        val orange = rgb(255, 140, 40)
        val etincelle = o.lumiere()
        for ((n, tx) in floatArrayOf(w * 0.25f, w * 0.75f).withIndex()) {
            val ty = mur - pierre * 0.9f
            c.drawLine(tx, ty, tx, ty + pierre * 1.4f, o.pinceau(argb(255f * app, 60, 40, 24), 5f * dp))
            val vacille = 0.85f + 0.15f * sin(sec * 23f + tx) * sin(sec * 9f)
            o.tache(c, tx, mur + pierre, m * 0.3f, m * 0.25f, 0.18f * vacille * app * a, orange)
            o.halo(c, tx, ty - pierre * 0.2f, pierre * 1.2f * vacille, 0.45f * app * a, orange)
            val fh = pierre * 0.9f * vacille * app
            val flamme = Path()
            flamme.moveTo(tx - pierre * 0.2f, ty); flamme.quadTo(tx - pierre * 0.15f, ty - fh * 0.6f, tx + sin(sec * 11f) * pierre * 0.08f, ty - fh)
            flamme.quadTo(tx + pierre * 0.15f, ty - fh * 0.6f, tx + pierre * 0.2f, ty); flamme.close()
            c.drawPath(flamme, o.lumiere(avecAlpha(rgb(255, 190, 80), 0.9f * app * a)))
            for (e in etincelles.subList(n * 25, n * 25 + 25)) {
                val t = modulo(e[1] + sec * (0.8f + e[2]), 1f)
                etincelle.color = avecAlpha(rgb(255, 180, 80), (1f - t) * app * a)
                c.drawCircle(tx + (e[0] - 0.5f) * pierre * 1.5f + sin(sec * 4f + e[3]) * 6f * dp, ty - t * m * 0.35f, 1.2f * dp, etincelle)
            }
        }
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, w / 2f, mur, rev, orange)
    }
}

// ===================== DRASTIC : « Double écran » =====================
// Une console à deux écrans s'allume : lignes de balayage, puis une lueur bleutée dans chaque écran.
class DoubleEcran : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(10, 10, 14), rgb(4, 4, 6), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f; val cy = h / 2f
        val L = m * 0.5f; val H = m * 0.36f
        val ligne = o.pinceau(argb(50f, 0, 0, 0), 1f * dp)
        for ((k, sy) in intArrayOf(-1, 1).withIndex()) {
            val r = RectF(cx - L / 2f, cy + sy * H * 0.56f - H / 2f, cx + L / 2f, cy + sy * H * 0.56f + H / 2f)
            c.drawRoundRect(RectF(r.left - 8f * dp, r.top - 8f * dp, r.right + 8f * dp, r.bottom + 8f * dp), 10f * dp, 10f * dp, o.pinceau(argb(255f * app, 30, 32, 40)))
            val allume = borne((sec - 0.02f - k * 0.06f) / 0.08f)
            c.drawRect(r, o.pinceau(rgb(6, 6, 8)))
            val p = o.pinceau()
            p.shader = LinearGradient(0f, r.top, 0f, r.bottom, avecAlpha(rgb(30, 50, 90), allume), avecAlpha(rgb(14, 20, 40), allume), Shader.TileMode.CLAMP)
            c.drawRect(r, p)
            val balai = r.top + modulo(sec * 1.6f + k * 0.5f, 1f) * r.height()
            c.drawLine(r.left, balai, r.right, balai, o.lumiere(avecAlpha(rgb(150, 190, 255), 0.25f * allume * a), 2f * dp))
            var s = 0f
            while (s < r.height()) { c.drawLine(r.left, r.top + s, r.right, r.top + s, ligne); s += 3f * dp }
            o.halo(c, r.centerX(), r.centerY(), L * 0.3f, 0.12f * allume * app * a, rgb(120, 160, 255))
        }
        c.drawCircle(cx, cy, 4f * dp, o.pinceau(argb(255f * app * a, 60, 200, 120)))
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, cy, rev, rgb(120, 160, 255))
    }
}

// ===================== DREDGE : « Lanterne » =====================
// Une petite barque, une lanterne, une mer noire et du brouillard : quelque chose remue sous l'eau.
class Lanterne : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val mer = h * 0.55f
        degradeVertical(c, o, w, rgb(10, 14, 14), rgb(6, 9, 9), 0f, mer)
        degradeVertical(c, o, w, rgb(5, 8, 8), rgb(2, 3, 3), mer, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val jaune = rgb(255, 200, 110)
        val bx = w / 2f; val by = mer + sin(sec * 2f) * 4f * dp
        val vague = o.lumiere(argb(12f * app * a, 140, 180, 170), 1f * dp)
        for (k in 0 until 6) { val y = mer + k * (h - mer) / 6f + sin(sec * 2f + k) * 3f * dp; c.drawLine(0f, y, w, y, vague) }
        val coque = Path()
        coque.moveTo(bx - m * 0.16f, by - m * 0.02f); coque.lineTo(bx + m * 0.16f, by - m * 0.02f); coque.lineTo(bx + m * 0.1f, by + m * 0.04f); coque.lineTo(bx - m * 0.12f, by + m * 0.04f); coque.close()
        c.drawPath(coque, o.pinceau(argb(255f * app, 20, 16, 12)))
        c.drawLine(bx + m * 0.08f, by - m * 0.02f, bx + m * 0.08f, by - m * 0.14f, o.pinceau(argb(255f * app, 26, 20, 14), 2f * dp))
        val lx = bx + m * 0.08f; val ly = by - m * 0.14f
        val vacille = 0.85f + 0.15f * sin(sec * 17f)
        o.halo(c, lx, ly, m * 0.25f * vacille, 0.3f * app * a, jaune)
        o.tache(c, lx, mer + m * 0.05f, m * 0.12f, m * 0.012f, 0.3f * vacille * app * a, jaune)   // reflet sur l'eau
        c.drawCircle(lx, ly, 3.5f * dp, o.pinceau(avecAlpha(rgb(255, 230, 170), app * a)))
        val ombre = borne(sec / 0.4f)                                    // une ombre passe sous la barque
        o.tache(c, mix(-0.2f, 1.2f, ombre) * w, mer + m * 0.15f, m * 0.3f, m * 0.04f, 0.5f * sin(PI.toFloat() * ombre) * app * a, Color.BLACK)
        for (k in 0 until 5) o.tache(c, (modulo(k * 0.27f + sec * 0.04f, 1.2f) - 0.1f) * w, mer - m * 0.02f, w * 0.3f, m * 0.05f, 0.08f * app * a, rgb(150, 170, 165))
        o.vignette(c, w, h, 0.65f)
        o.revelationDouce(c, lx, ly, rev, jaune)
    }
}

// ===================== GAMBONANZA : « Dés » =====================
// Deux dés roulent sur un tapis de jeu sombre et s'arrêtent sur un double six.
class Des : Scene() {
    // Position des points de chaque face, en unités de -1 à 1.
    private val faces = arrayOf(
        floatArrayOf(0f, 0f), floatArrayOf(-1f, -1f, 1f, 1f), floatArrayOf(-1f, -1f, 0f, 0f, 1f, 1f),
        floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f), floatArrayOf(-1f, -1f, 1f, -1f, 0f, 0f, -1f, 1f, 1f, 1f),
        floatArrayOf(-1f, -1f, 1f, -1f, -1f, 0f, 1f, 0f, -1f, 1f, 1f, 1f)
    )

    private fun de(c: Canvas, o: Outils, x: Float, y: Float, s: Float, rot: Float, valeur: Int, alpha: Float, dp: Float) {
        c.save(); c.translate(x, y); c.rotate(rot)
        val r = RectF(-s, -s, s, s)
        c.drawRoundRect(r, s * 0.25f, s * 0.25f, o.pinceau(argb(255f * alpha, 225, 220, 210)))
        c.drawRoundRect(r, s * 0.25f, s * 0.25f, o.lumiere(argb(60f * alpha, 255, 255, 255), 1f * dp))
        val pt = o.pinceau(argb(255f * alpha, 30, 24, 24))
        val f = faces[valeur - 1]
        var k = 0
        while (k < f.size) { c.drawCircle(f[k] * s * 0.5f, f[k + 1] * s * 0.5f, s * 0.14f, pt); k += 2 }
        c.restore()
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(6, 14, 10))
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        o.tache(c, w / 2f, h / 2f, m * 0.6f, m * 0.45f, 0.15f * app, rgb(60, 160, 100))
        val roule = ralentit(borne(sec / 0.18f))
        val s = m * 0.08f
        for (k in 0 until 2) {
            val x = mix(-0.2f * w, w / 2f + (k - 0.5f) * s * 3f, roule)
            val y = h / 2f + (k - 0.5f) * s * 0.8f - sin(roule * PI.toFloat() * 3f) * s * 0.6f * (1f - roule)
            val valeur = if (roule > 0.95f) 6 else 1 + ((sec * 40f).toInt() + k * 3) % 6
            o.tache(c, x + s * 0.3f, y + s * 1.1f, s * 1.1f, s * 0.25f, 0.5f * app * a, Color.BLACK)
            de(c, o, x, y, s, (1f - roule) * 720f + k * 15f, valeur, app * a, dp)
        }
        o.vignette(c, w, h, 0.6f)
        o.revelationDouce(c, w / 2f, h / 2f, rev, rgb(120, 230, 160))
    }
}

// ===================== GEOMETRY DASH : « Cube néon » =====================
// Un cube néon saute par-dessus des pics sur une piste qui défile. Version « lave » pour Meltdown.
class Cube(private val couleur: Int = rgb(80, 220, 255), private val lave: Boolean = false) : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val haut = if (lave) rgb(20, 6, 4) else rgb(4, 8, 22)
        val bas = if (lave) rgb(40, 10, 6) else rgb(10, 16, 40)
        degradeVertical(c, o, w, haut, bas, 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val sol = h * 0.64f
        c.drawRect(0f, sol, w, h, o.pinceau(fonce(bas, 0.6f)))
        c.drawLine(0f, sol, w, sol, o.lumiere(avecAlpha(couleur, 0.7f * app * a), 2f * dp))
        if (lave) for (k in 0 until 8) o.tache(c, modulo(k * 0.13f + sec * 0.3f, 1f) * w, sol + m * 0.08f, m * 0.1f, m * 0.02f, 0.25f * app * a, rgb(255, 90, 30))
        val vitesse = w * 0.6f
        val pas = m * 0.5f
        val dec = modulo(sec * vitesse, pas)
        val s = m * 0.04f
        val noir = o.pinceau(argb(255f * app * a, 10, 10, 14))
        val neon = o.lumiere(avecAlpha(couleur, 0.8f * app * a), 1.5f * dp)
        for (k in 0 until (w / pas).toInt() + 2) {                      // les pics qui défilent
            val x = k * pas - dec + pas * 0.6f
            val pic = Path(); pic.moveTo(x - s, sol); pic.lineTo(x, sol - s * 1.8f); pic.lineTo(x + s, sol); pic.close()
            c.drawPath(pic, noir); c.drawPath(pic, neon)
        }
        val cx = w * 0.32f
        val phase = modulo(sec * vitesse + pas * 0.1f, pas) / pas       // un saut par pic
        val cy = sol - s * 1.1f - max(0f, sin(phase * PI.toFloat())) * m * 0.12f
        for (g in 1..5) o.halo(c, cx - g * s * 0.9f, cy + g * 2f * dp, s * (1f - g / 7f), 0.15f * app * a, couleur)
        c.save(); c.translate(cx, cy); c.rotate(phase * 180f)
        c.drawRect(-s * 1.1f, -s * 1.1f, s * 1.1f, s * 1.1f, o.pinceau(avecAlpha(fonce(couleur, 0.4f), app * a)))
        c.drawRect(-s * 1.1f, -s * 1.1f, s * 1.1f, s * 1.1f, o.lumiere(avecAlpha(couleur, 0.9f * app * a), 2f * dp))
        c.drawRect(-s * 0.45f, -s * 0.45f, s * 0.45f, s * 0.45f, o.lumiere(avecAlpha(couleur, 0.6f * app * a), 1.5f * dp))
        c.restore()
        o.halo(c, cx, cy, s * 3f, 0.2f * app * a, couleur)
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, couleur)
    }
}

// ===================== THE WHIP : « Fouet » =====================
// Un fouet de lumière se déroule et claque : une onde court le long de la lanière jusqu'au bout.
class Fouet : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(10, 7, 6))
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val rouge = rgb(255, 110, 70)
        val deroule = ralentit(borne(sec / 0.15f))
        val x0 = w * 0.15f; val y0 = h * 0.7f
        val chemin = Path()
        for (s in 0..50) {
            val t = s / 50f * deroule
            val x = mix(x0, w * 0.85f, t)
            val y = y0 - sin(t * PI.toFloat() * 0.8f) * h * 0.3f + sin(t * 10f - sec * 25f) * m * 0.06f * t
            if (s == 0) chemin.moveTo(x, y) else chemin.lineTo(x, y)
        }
        val couches = arrayOf(floatArrayOf(12f, 0.06f), floatArrayOf(4f, 0.25f), floatArrayOf(1.8f, 0.9f))
        for (cc in couches) c.drawPath(chemin, o.lumiere(avecAlpha(rouge, cc[1] * app * a), cc[0] * dp))
        c.drawLine(x0 - m * 0.05f, y0 + m * 0.05f, x0, y0, o.pinceau(argb(255f * app * a, 80, 50, 30), 6f * dp))   // le manche
        val xe = w * 0.85f; val ye = y0 - sin(PI.toFloat() * 0.8f) * h * 0.3f
        if (deroule > 0.95f) {                                          // le claquement
            val t = borne((sec - 0.15f) / 0.2f)
            c.drawCircle(xe, ye, t * m * 0.15f, o.lumiere(avecAlpha(rgb(255, 210, 160), 0.6f * (1f - t) * a), 2f * dp))
            o.halo(c, xe, ye, m * 0.08f, 0.5f * (1f - t) * a, rouge)
        }
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, xe, ye, rev, rouge)
    }
}

// ===================== MICROG : « Puzzle » =====================
// Des pièces de puzzle arrivent de loin et s'emboîtent en un carré lumineux.
class Puzzle : Scene() {
    private val couleurs = intArrayOf(BLEU_G, VERT_G, JAUNE_G, ROUGE_G)

    private fun piece(x: Float, y: Float, s: Float, droite: Boolean, bas: Boolean): Path {
        val p = Path()
        p.moveTo(x, y); p.lineTo(x + s, y)
        if (droite) { p.lineTo(x + s, y + s * 0.35f); p.cubicTo(x + s * 1.3f, y + s * 0.2f, x + s * 1.3f, y + s * 0.8f, x + s, y + s * 0.65f) }
        p.lineTo(x + s, y + s)
        if (bas) { p.lineTo(x + s * 0.65f, y + s); p.cubicTo(x + s * 0.8f, y + s * 1.3f, x + s * 0.2f, y + s * 1.3f, x + s * 0.35f, y + s) }
        p.lineTo(x, y + s); p.close()
        return p
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(8, 10, 14), rgb(4, 5, 7), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val s = m * 0.16f
        val cx = w / 2f; val cy = h / 2f
        for (k in 0 until 4) {
            val gx = k % 2; val gy = k / 2
            val arrive = ralentit(borne(sec / 0.15f - k * 0.3f))
            val ang = k * PI.toFloat() / 2f + 0.6f
            val x = cx - s + gx * s + cos(ang) * m * 0.6f * (1f - arrive)
            val y = cy - s + gy * s + sin(ang) * m * 0.6f * (1f - arrive)
            val forme = piece(x, y, s, gx == 0, gy == 0)
            c.drawPath(forme, o.pinceau(avecAlpha(fonce(couleurs[k], 0.4f), arrive * app * a)))
            c.drawPath(forme, o.lumiere(avecAlpha(couleurs[k], 0.8f * arrive * app * a), 1.5f * dp))
        }
        o.halo(c, cx, cy, s * 1.6f, 0.25f * borne((sec - 0.25f) / 0.1f) * app * a, rgb(200, 220, 255))
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, rgb(160, 200, 255))
    }
}

// ===================== REVANCED MANAGER : « Couture » =====================
// Une aiguille de lumière coud un patch : les points de couture apparaissent un à un autour d'un carré.
class Couture : Scene() {
    // Point du périmètre (distance d) : [x, y, direction x, direction y].
    private fun bord(d: Float, s: Float, cx: Float, cy: Float): FloatArray {
        val cote = min(3, (d / (2f * s)).toInt())
        val u = d - cote * 2f * s - s
        return when (cote) {
            0 -> floatArrayOf(cx + u, cy - s * 1.12f, 1f, 0f)
            1 -> floatArrayOf(cx + s * 1.12f, cy + u, 0f, 1f)
            2 -> floatArrayOf(cx - u, cy + s * 1.12f, -1f, 0f)
            else -> floatArrayOf(cx - s * 1.12f, cy - u, 0f, -1f)
        }
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(12, 8, 14), rgb(5, 4, 6), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f; val cy = h / 2f
        val s = m * 0.25f
        val rose = rgb(230, 110, 200)
        c.drawRoundRect(RectF(cx - s, cy - s, cx + s, cy + s), 10f * dp, 10f * dp, o.pinceau(argb(255f * app, 36, 20, 40)))
        val avance = borne(sec / 0.25f)
        val n = 32
        val perim = 8f * s
        val point = o.lumiere(avecAlpha(rose, 0.9f * app * a), 2f * dp)
        for (k in 0 until (n * avance).toInt()) {
            val b = bord(k.toFloat() / n * perim, s, cx, cy)
            c.drawLine(b[0], b[1], b[0] + b[2] * s * 0.12f, b[1] + b[3] * s * 0.12f, point)
        }
        val ai = bord(min(avance * perim, perim - 0.01f), s, cx, cy)        // l'aiguille
        o.halo(c, ai[0], ai[1], 12f * dp, 0.6f * app * a, rose)
        c.drawLine(ai[0], ai[1], ai[0] + m * 0.05f, ai[1] - m * 0.12f, o.pinceau(argb(255f * app * a, 200, 200, 210), 1.5f * dp))
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, rose)
    }
}

// ===================== PARSEC : « Hyperespace » =====================
// Les étoiles s'étirent en traits : on saute à travers l'espace, vers l'autre machine.
class Hyperespace : Scene() {
    private val etoiles = nuage(261, 120)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(3, 4, 10))
        val app = ralentit(intro)
        val a = 1f - rev
        val cx = w / 2f; val cy = h / 2f
        val D = hypot(w, h) * 0.6f
        val etire = ralentit(borne(sec / 0.3f))
        for (e in etoiles) {
            val ang = e[0] * 6.28f; val z = e[2]
            val d = modulo(e[1] + sec * (0.5f + z) * (0.3f + 2f * etire), 1f)
            val r1 = d * d * D
            val r2 = r1 * (1.04f + 0.4f * etire * d)
            c.drawLine(cx + cos(ang) * r1, cy + sin(ang) * r1, cx + cos(ang) * r2, cy + sin(ang) * r2,
                o.lumiere(avecAlpha(mixCouleur(rgb(120, 140, 255), rgb(220, 230, 255), z), (0.3f + 0.6f * d) * app * a), (0.8f + 1.2f * z) * dp))
        }
        o.halo(c, cx, cy, min(w, h) * 0.12f, 0.3f * app * a, rgb(120, 140, 255))
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, cy, rev, rgb(160, 180, 255))
    }
}

// ===================== PLANET VPN : « Bulle » =====================
// Une petite planète, et autour d'elle une bulle protectrice hexagonale qui se referme.
class Bulle : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(4, 6, 12))
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f; val cy = h / 2f
        val vert = rgb(60, 220, 160)
        val R = m * 0.15f
        val p = o.pinceau()
        p.shader = RadialGradient(cx - R * 0.4f, cy - R * 0.4f, R * 1.5f, argb(255f * app, 60, 120, 140), argb(255f * app, 10, 26, 36), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, R, p)
        val ferme = ralentit(borne(sec / 0.2f))
        val Rb = R * 1.9f * (1f + 2f * accelere(rev))
        val alveole = o.lumiere(avecAlpha(vert, 0.35f * app * a), 1f * dp)
        for (k in 0 until 36) {                                         // les alvéoles du bouclier
            if (k / 36f > ferme) continue
            val ang = k * PI.toFloat() * 2f / 36f + sec * 0.4f
            for (r in floatArrayOf(0.8f, 1f)) {
                val x = cx + cos(ang) * Rb * r; val y = cy + sin(ang) * Rb * r
                val hexa = Path()
                for (q in 0 until 6) {
                    val t = q * PI.toFloat() / 3f
                    if (q == 0) hexa.moveTo(x + cos(t) * Rb * 0.07f, y + sin(t) * Rb * 0.07f) else hexa.lineTo(x + cos(t) * Rb * 0.07f, y + sin(t) * Rb * 0.07f)
                }
                hexa.close()
                c.drawPath(hexa, alveole)
            }
        }
        o.halo(c, cx, cy, Rb, 0.12f * ferme * app * a, vert)
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, vert)
    }
}

// ===================== LIBRESPRITE : « Pixels » =====================
// Une grille de pixels se remplit case par case et dessine un petit cœur en pixel art.
class Pixels : Scene() {
    private val motif = arrayOf("0110110", "1111111", "1111111", "0111110", "0011100", "0001000")

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(12, 10, 16), rgb(5, 4, 7), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val nCol = 7; val nLig = 6
        val s = m * 0.08f
        val x0 = w / 2f - nCol * s / 2f; val y0 = h / 2f - nLig * s / 2f
        val grille = o.pinceau(argb(30f * app * a, 200, 180, 220), 1f * dp)
        for (gx in 0..nCol) c.drawLine(x0 + gx * s, y0, x0 + gx * s, y0 + nLig * s, grille)
        for (gy in 0..nLig) c.drawLine(x0, y0 + gy * s, x0 + nCol * s, y0 + gy * s, grille)
        val rempli = borne(sec / 0.2f) * nCol * nLig
        var k = 0
        val pixel = o.pinceau()
        for ((gy, ligne) in motif.withIndex()) {
            for ((gx, ch) in ligne.withIndex()) {
                if (ch != '1') continue
                k += 1
                if (k > rempli) continue
                pixel.color = avecAlpha(mixCouleur(rgb(240, 80, 110), rgb(160, 40, 80), gy.toFloat() / nLig), app * a)
                c.drawRect(x0 + gx * s + 1f * dp, y0 + gy * s + 1f * dp, x0 + (gx + 1) * s - 1f * dp, y0 + (gy + 1) * s - 1f * dp, pixel)
            }
        }
        o.halo(c, w / 2f, h / 2f, m * 0.3f, 0.12f * app * a, rgb(240, 80, 110))
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, w / 2f, h / 2f, rev, rgb(240, 100, 140))
    }
}

// ===================== SIGTAO : « Boussole » =====================
// Une boussole dans la pénombre : l'aiguille hésite, oscille, puis trouve le nord.
class Boussole : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(10, 12, 14), rgb(4, 5, 6), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f; val cy = h / 2f
        val R = m * 0.28f
        c.drawCircle(cx, cy, R * 1.08f, o.pinceau(argb(255f * app, 40, 36, 30)))
        c.drawCircle(cx, cy, R, o.pinceau(argb(255f * app, 16, 18, 20)))
        for (k in 0 until 36) {
            val ang = degVersRad(k * 10f)
            val grand = k % 9 == 0
            val long = if (grand) 0.12f else 0.05f
            c.drawLine(cx + cos(ang) * R * (0.92f - long), cy + sin(ang) * R * (0.92f - long), cx + cos(ang) * R * 0.92f, cy + sin(ang) * R * 0.92f,
                o.pinceau(argb(200f * app * a, 200, 200, 190), (if (grand) 2f else 1f) * dp))
        }
        texte(c, "N", cx, cy - R * 0.68f, R * 0.14f, avecAlpha(rgb(230, 90, 70), app * a))
        val oscille = exp(-sec * 5f) * sin(sec * 18f) * 70f               // l'aiguille hésite puis se stabilise
        val ang = degVersRad(-90f + oscille + (1f - app) * 120f)
        val perp = ang + PI.toFloat() / 2f
        for ((sens, coul) in listOf(1f to rgb(230, 80, 60), -1f to rgb(200, 200, 205))) {
            val pointe = Path()
            pointe.moveTo(cx + cos(ang) * R * 0.75f * sens, cy + sin(ang) * R * 0.75f * sens)
            pointe.lineTo(cx + cos(perp) * R * 0.07f, cy + sin(perp) * R * 0.07f)
            pointe.lineTo(cx - cos(perp) * R * 0.07f, cy - sin(perp) * R * 0.07f); pointe.close()
            c.drawPath(pointe, o.pinceau(avecAlpha(coul, app * a)))
        }
        c.drawCircle(cx, cy, R * 0.05f, o.pinceau(argb(255f * app * a, 150, 140, 120)))
        c.drawArc(RectF(cx - R * 1.08f, cy - R * 1.08f, cx + R * 1.08f, cy + R * 1.08f), 200f, 70f, false, o.lumiere(argb(70f * app * a, 255, 230, 200), 1.5f * dp))
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, rgb(230, 120, 90))
    }
}

// ===================== TICKET SANS CONTACT : « Sans contact » =====================
// Un ticket s'approche d'une borne ; des ondes passent entre les deux et la borne passe au vert.
class SansContact : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(8, 10, 14), rgb(4, 5, 7), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f; val cy = h / 2f
        val bleu = rgb(80, 160, 255); val vert = rgb(60, 220, 120)
        val valide = borne((sec - 0.16f) / 0.06f)
        val borneR = RectF(cx + m * 0.05f, cy - m * 0.2f, cx + m * 0.3f, cy + m * 0.2f)
        c.drawRoundRect(borneR, 12f * dp, 12f * dp, o.pinceau(argb(255f * app, 30, 32, 38)))
        val vx = borneR.centerX(); val vy = borneR.top + m * 0.06f
        c.drawCircle(vx, vy, m * 0.025f, o.pinceau(avecAlpha(mixCouleur(rgb(40, 60, 90), vert, valide), app * a)))
        o.halo(c, vx, vy, m * 0.06f, 0.5f * valide * a, vert)
        val approche = ralentit(borne(sec / 0.15f))
        c.save(); c.translate(mix(-m * 0.3f, cx - m * 0.12f, approche), cy); c.rotate(-8f)   // le ticket
        c.drawRoundRect(RectF(-m * 0.1f, -m * 0.15f, m * 0.1f, m * 0.15f), 6f * dp, 6f * dp, o.pinceau(argb(255f * app * a, 200, 205, 215)))
        c.drawRect(-m * 0.1f, -m * 0.05f, m * 0.1f, 0f, o.pinceau(argb(255f * app * a, 60, 120, 200)))
        c.restore()
        for (k in 0 until 3) {                                           // les ondes
            val t = modulo(sec * 2.5f + k / 3f, 1f)
            val r = m * (0.05f + 0.1f * t)
            c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -40f, 80f, false,
                o.lumiere(avecAlpha(mixCouleur(bleu, vert, valide), 0.6f * (1f - t) * approche * app * a), 2f * dp))
        }
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, vx, vy, rev, vert)
    }
}

// ===================== IDENTITÉ NUMÉRIQUE : « Empreinte » =====================
// Une empreinte digitale lumineuse est lue par un trait de scan qui la parcourt de haut en bas.
class Empreinte : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(8, 10, 16), rgb(4, 5, 8), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f; val cy = h / 2f
        val bleu = rgb(90, 170, 255)
        val R = m * 0.24f
        val ys = cy - R * 1.2f + borne(sec / 0.22f) * R * 2.4f
        val lu = o.lumiere(avecAlpha(bleu, 0.75f * app * a), 1.8f * dp)
        val pasLu = o.pinceau(argb(60f * app * a, 120, 130, 150), 1.5f * dp)
        for (k in 0 until 10) {                                          // les sillons : ellipses ouvertes et décalées
            val rx = R * (0.15f + 0.09f * k); val ry = R * (0.2f + 0.11f * k)
            val rect = RectF(cx - rx, cy - ry + k * R * 0.02f, cx + rx, cy + ry + k * R * 0.02f)
            val debut = 200f + 15f * sin(k * 1.7f)
            c.save(); c.clipRect(0f, 0f, w, ys); c.drawArc(rect, debut, 300f - k * 6f, false, lu); c.restore()
            c.save(); c.clipRect(0f, ys, w, h); c.drawArc(rect, debut, 300f - k * 6f, false, pasLu); c.restore()
        }
        c.drawLine(cx - R * 1.3f, ys, cx + R * 1.3f, ys, o.lumiere(avecAlpha(bleu, 0.8f * (1f - borne((sec - 0.22f) / 0.1f)) * app * a), 2f * dp))
        o.tache(c, cx, ys, R * 1.3f, 6f * dp, 0.3f * app * a, bleu)
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, bleu)
    }
}

// ===================== AIRBNB : « Clé » =====================
// Une porte dans la nuit ; une clé tourne dans la serrure et un rai de lumière chaude s'ouvre.
class Cle : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(12, 9, 10), rgb(5, 4, 5), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val cx = w / 2f; val cy = h / 2f
        val L = m * 0.42f; val H = m * 0.8f
        val porte = RectF(cx - L / 2f, cy - H / 2f, cx + L / 2f, cy + H / 2f)
        c.drawRect(porte, o.pinceau(argb(255f * app, 30, 22, 20)))
        val panneau = o.pinceau(argb(255f * app, 24, 17, 16), 1.5f * dp)
        for (k in 0 until 3) {                                          // panneaux de la porte
            val y = porte.top + H * (0.08f + 0.3f * k)
            c.drawRect(porte.left + L * 0.12f, y, porte.right - L * 0.12f, y + H * 0.24f, panneau)
        }
        val tourne = ralentit(borne((sec - 0.02f) / 0.15f))
        val ouvre = borne((sec - 0.17f) / 0.15f)
        val rai = porte.right - ouvre * L * 0.25f                      // la porte s'entrouvre : un rai de lumière
        val l = o.lumiere()
        l.shader = LinearGradient(rai, 0f, porte.right + L * 0.3f, 0f, avecAlpha(rgb(255, 200, 140), 0.5f * ouvre * app * a), avecAlpha(rgb(255, 200, 140), 0f), Shader.TileMode.CLAMP)
        c.drawRect(rai, porte.top, porte.right + L * 0.4f, porte.bottom, l)
        c.drawRect(rai, porte.top, porte.right, porte.bottom, o.lumiere(avecAlpha(rgb(255, 210, 160), 0.35f * ouvre * app * a)))
        o.tache(c, porte.right, porte.bottom + 6f * dp, L * 0.5f * ouvre + 1f, 6f * dp, 0.3f * ouvre * app * a, rgb(255, 190, 130))
        val sx = porte.right - L * 0.16f; val sy = cy
        c.drawCircle(sx, sy, m * 0.03f, o.pinceau(argb(255f * app * a, 120, 100, 70)))
        c.save(); c.translate(sx, sy); c.rotate(-90f * tourne)          // la clé tourne
        val laiton = avecAlpha(rgb(220, 190, 130), app * a)
        c.drawLine(0f, 0f, -m * 0.09f, 0f, o.pinceau(laiton, 3f * dp))
        c.drawCircle(-m * 0.11f, 0f, m * 0.025f, o.pinceau(laiton, 2.5f * dp))
        c.restore()
        o.halo(c, sx, sy, m * 0.06f, 0.3f * app * a, rgb(255, 110, 110))
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, porte.right, cy, rev, rgb(255, 190, 140))
    }
}
