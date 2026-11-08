package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// LOT 3b : apps système secondaires. Animations courtes (moins d'une demi-seconde),
// donc tout ce qui bouge doit se jouer dans les 0,2 premières secondes.

private fun enRadians(degres: Float): Float = degres * PI.toFloat() / 180f

// Mélange de deux couleurs (t = 0 : la première, t = 1 : la seconde).
fun mixCouleur(c1: Int, c2: Int, t: Float): Int = rgb(
    mix(Color.red(c1).toFloat(), Color.red(c2).toFloat(), t).toInt(),
    mix(Color.green(c1).toFloat(), Color.green(c2).toFloat(), t).toInt(),
    mix(Color.blue(c1).toFloat(), Color.blue(c2).toFloat(), t).toInt()
)

// ===================== GALAXY STORE : « Galaxie » =====================
// Une galaxie spirale dont les étoiles sont de petites tuiles d'apps ; elle tourne lentement.
class Galaxie : Scene() {
    private val points = Random(111).let { r -> List(90) { floatArrayOf(r.nextFloat(), r.nextInt(2).toFloat(), r.nextFloat()) } }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(7, 5, 12))
        val app = ralentit(intro)
        val a = 1f - rev
        val cx = w / 2f; val cy = h / 2f
        val m = min(w, h)
        val rose = rgb(255, 90, 160); val violet = rgb(140, 90, 255)
        o.halo(c, cx, cy, m * 0.25f, 0.35f * app * a, violet)
        o.halo(c, cx, cy, m * 0.08f, 0.5f * app * a, rose)
        val R = m * 0.48f * (0.6f + 0.4f * app) * (1f + 2f * accelere(rev))
        val p = o.lumiere()
        for (q in points) {
            val d = q[0].pow(0.7f); val bras = q[1]; val v = q[2]
            val ang = bras * PI.toFloat() + d * 4.2f + sec * (0.9f - 0.5f * d) + (v - 0.5f) * 0.5f
            val x = cx + cos(ang) * d * R
            val y = cy + sin(ang) * d * R * 0.6f
            val s = (1.5f + 3.5f * v) * dp
            p.color = avecAlpha(if (v > 0.5f) rose else violet, (0.4f + 0.6f * (1f - d)) * app * a)
            c.drawRoundRect(RectF(x - s, y - s, x + s, y + s), s * 0.4f, s * 0.4f, p)
        }
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, rose)
    }
}

// ===================== FIND : « Radar » =====================
// Un radar balaie l'obscurité ; un écho apparaît, pulse, et c'est lui qui s'ouvre sur l'app.
class Radar : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(4, 9, 10))
        val app = ralentit(intro)
        val a = 1f - rev
        val cx = w / 2f; val cy = h / 2f
        val m = min(w, h)
        val teal = rgb(60, 210, 200)
        val R = m * 0.42f
        for (k in 1..4) c.drawCircle(cx, cy, R * k / 4f, o.lumiere(avecAlpha(teal, 0.15f * app * a), 1f * dp))
        c.drawLine(cx - R, cy, cx + R, cy, o.lumiere(avecAlpha(teal, 0.1f * app * a), 1f * dp))
        c.drawLine(cx, cy - R, cx, cy + R, o.lumiere(avecAlpha(teal, 0.1f * app * a), 1f * dp))
        val ang = sec * 300f
        val balai = o.lumiere()
        balai.shader = SweepGradient(cx, cy, intArrayOf(avecAlpha(teal, 0f), avecAlpha(teal, 0f), avecAlpha(teal, 0.35f * app * a)), floatArrayOf(0f, 0.82f, 1f))
        c.save(); c.rotate(ang, cx, cy)
        c.drawCircle(cx, cy, R, balai)
        c.restore()
        val t = enRadians(ang)
        c.drawLine(cx, cy, cx + cos(t) * R, cy + sin(t) * R, o.lumiere(avecAlpha(teal, 0.8f * app * a), 1.5f * dp))
        val ex = cx + R * 0.45f; val ey = cy - R * 0.3f                  // l'écho trouvé
        val trouve = borne((sec - 0.05f) / 0.15f)
        if (trouve > 0f) {
            val onde = modulo(sec * 1.5f, 1f)
            c.drawCircle(ex, ey, 30f * dp * onde, o.lumiere(avecAlpha(teal, 0.6f * (1f - onde) * trouve * a), 2f * dp))
            o.halo(c, ex, ey, 18f * dp, 0.6f * trouve * a, teal)
            c.drawCircle(ex, ey, 4f * dp, o.pinceau(avecAlpha(rgb(220, 255, 250), trouve * a)))
        }
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, ex, ey, rev, teal)
    }
}

// ===================== HEALTH MONITOR : « Jauge » =====================
// Un anneau qui respire et une jauge qui se remplit, comme une mesure en cours.
class Jauge : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(8, 10, 16), rgb(4, 5, 8), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val cx = w / 2f; val cy = h / 2f
        val m = min(w, h)
        val bleu = rgb(80, 160, 255); val rose = rgb(255, 90, 120)
        val R = m * 0.3f
        val rect = RectF(cx - R, cy - R, cx + R, cy + R)
        c.drawArc(rect, 135f, 270f, false, o.pinceau(argb(255f * app * a, 26, 30, 40), 10f * dp))
        val rempli = ralentit(borne(sec / 0.3f)) * 0.72f
        val p = o.lumiere(trait = 10f * dp)
        p.shader = SweepGradient(cx, cy, intArrayOf(bleu, rose, bleu), floatArrayOf(0f, 0.5f, 1f))
        p.alpha = (0.9f * app * a * 255).toInt()
        c.drawArc(rect, 135f, 270f * rempli, false, p)
        val t = enRadians(135f + 270f * rempli)
        o.halo(c, cx + cos(t) * R, cy + sin(t) * R, 14f * dp, 0.6f * app * a, rose)
        val souffle = 0.5f + 0.5f * sin(sec * 4f)
        c.drawCircle(cx, cy, R * (0.55f + 0.08f * souffle), o.lumiere(avecAlpha(bleu, (0.15f + 0.2f * souffle) * app * a), 2f * dp))
        o.halo(c, cx, cy, R * 0.5f, 0.12f * souffle * app * a, bleu)
        texte(c, (60 + 60 * rempli).toInt().toString(), cx, cy, R * 0.32f, avecAlpha(rgb(220, 230, 255), app * a))
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, bleu)
    }
}

// ===================== SMARTTHINGS : « Maison » =====================
// Une maison dans la nuit : ses fenêtres s'allument une à une, la porte s'ouvre sur l'app.
class Maison : Scene() {
    private val etoiles = nuage(121, 60)
    private val fenetres = arrayOf(floatArrayOf(-0.3f, 0.62f), floatArrayOf(0.3f, 0.62f), floatArrayOf(-0.3f, 0.25f), floatArrayOf(0.3f, 0.25f), floatArrayOf(0f, -0.05f))

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(6, 8, 18), rgb(14, 14, 26), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        o.poussieres(c, etoiles, sec * 0.2f, w, h * 0.6f, rgb(220, 225, 255), 0.5f * a, vx = 0f, vy = 0f)
        val m = min(w, h)
        val cx = w / 2f; val sol = h * 0.7f
        c.drawRect(0f, sol, w, h, o.pinceau(rgb(5, 6, 9)))
        val L = m * 0.5f; val H = m * 0.3f
        val maison = Path()
        maison.moveTo(cx - L / 2f, sol); maison.lineTo(cx - L / 2f, sol - H); maison.lineTo(cx, sol - H - m * 0.18f)
        maison.lineTo(cx + L / 2f, sol - H); maison.lineTo(cx + L / 2f, sol); maison.close()
        c.drawPath(maison, o.pinceau(argb(255f * app, 16, 17, 24)))
        c.drawPath(maison, o.lumiere(argb(40f * app * a, 140, 160, 220), 1.2f * dp))
        val jaune = rgb(255, 200, 120)
        for ((k, f) in fenetres.withIndex()) {
            val allume = borne((sec - 0.02f - k * 0.035f) / 0.06f)
            val x = cx + f[0] * L; val y = sol - H + f[1] * H
            val s = m * 0.045f
            c.drawRect(x - s, y - s, x + s, y + s, o.pinceau(avecAlpha(mixCouleur(rgb(20, 22, 30), jaune, allume), app * a)))
            o.halo(c, x, y, s * 3f, 0.25f * allume * app * a, jaune)
            o.tache(c, x, sol + 6f * dp, s * 2f, 3f * dp, 0.2f * allume * app * a, jaune)
        }
        c.drawRect(cx - m * 0.04f, sol - m * 0.11f, cx + m * 0.04f, sol, o.pinceau(argb(255f * app * a, 40, 30, 22)))   // la porte
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, sol - m * 0.05f, rev, jaune)
    }
}

// ===================== WEARABLE : « Lunette » =====================
// Une montre ronde vue de près : la lunette crantée tourne, les anneaux d'activité se remplissent.
class Lunette : Scene() {
    private val couleurs = intArrayOf(rgb(255, 80, 100), rgb(120, 230, 90), rgb(80, 200, 255))

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(10, 10, 14), rgb(4, 4, 6), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val cx = w / 2f; val cy = h / 2f
        val m = min(w, h)
        val R = m * 0.32f * (1f + 2.5f * accelere(rev))
        o.tache(c, cx, cy + R * 1.15f, R * 0.9f, 8f * dp, 0.5f * app, Color.BLACK)
        c.drawCircle(cx, cy, R * 1.12f, o.pinceau(argb(255f * app, 34, 35, 40)))
        val tour = ralentit(borne(sec / 0.35f)) * 90f
        val cran = o.pinceau(argb(255f * app * a, 60, 62, 70), 1.5f * dp)
        for (k in 0 until 60) {                                  // crans de la lunette
            val t = enRadians(k * 6f + tour)
            c.drawLine(cx + cos(t) * R * 1.03f, cy + sin(t) * R * 1.03f, cx + cos(t) * R * 1.11f, cy + sin(t) * R * 1.11f, cran)
        }
        c.drawArc(RectF(cx - R * 1.12f, cy - R * 1.12f, cx + R * 1.12f, cy + R * 1.12f), 200f, 60f, false, o.lumiere(argb(70f * app * a, 200, 210, 230), 1.5f * dp))
        c.drawCircle(cx, cy, R, o.pinceau(rgb(6, 6, 8)))
        for (k in 0 until 3) {                                   // anneaux d'activité
            val rr = R * (0.8f - 0.15f * k)
            val rect = RectF(cx - rr, cy - rr, cx + rr, cy + rr)
            c.drawCircle(cx, cy, rr, o.pinceau(avecAlpha(couleurs[k], 0.15f * app * a), R * 0.1f))
            val f = ralentit(borne((sec - k * 0.04f) / 0.3f)) * (0.6f + 0.15f * k)
            c.drawArc(rect, -90f, 360f * f, false, o.lumiere(avecAlpha(couleurs[k], 0.9f * app * a), R * 0.1f))
        }
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, rgb(120, 200, 255))
    }
}

// ===================== MEMBERS : « Cercle » =====================
// Des membres (petits avatars lumineux) se rassemblent en cercle autour d'un foyer.
class Cercle : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(8, 9, 18), rgb(4, 4, 8), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val cx = w / 2f; val cy = h / 2f
        val m = min(w, h)
        val bleu = rgb(110, 140, 255)
        o.halo(c, cx, cy, m * 0.12f, 0.4f * app * a, rgb(255, 180, 120))
        val n = 10
        for (k in 0 until n) {
            val arrive = ralentit(borne(sec / 0.2f - k * 0.05f))
            val ang = k * 2f * PI.toFloat() / n + sec * 0.6f
            val rr = mix(m * 0.9f, m * 0.3f, arrive) * (1f + 2f * accelere(rev))
            val x = cx + cos(ang) * rr; val y = cy + sin(ang) * rr * 0.9f
            val s = m * 0.035f
            c.drawCircle(x, y - s * 0.6f, s * 0.55f, o.pinceau(avecAlpha(rgb(190, 200, 240), arrive * app * a)))      // la tête
            c.drawArc(RectF(x - s, y, x + s, y + s * 1.6f), 180f, 180f, true, o.pinceau(avecAlpha(rgb(150, 160, 210), arrive * app * a)))  // les épaules
            o.halo(c, x, y, s * 2f, 0.15f * arrive * app * a, bleu)
            c.drawLine(x, y, mix(x, cx, 0.6f), mix(y, cy, 0.6f), o.lumiere(avecAlpha(bleu, 0.12f * arrive * app * a), 1f * dp))
        }
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, bleu)
    }
}

// ===================== ASTUCES : « Ampoule » =====================
// Une ampoule à filament s'allume doucement dans le noir : une idée.
class Ampoule : Scene() {
    private val poussiere = nuage(131, 30)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(8, 7, 6))
        val app = ralentit(intro)
        val a = 1f - rev
        val cx = w / 2f; val cy = h * 0.47f
        val m = min(w, h)
        val chaud = rgb(255, 170, 80)
        val allume = borne((sec - 0.02f) / 0.18f) * (0.9f + 0.1f * sin(sec * 23f) * sin(sec * 7f))
        val R = m * 0.17f
        c.drawLine(cx, 0f, cx, cy - R * 1.6f, o.pinceau(argb(255f * app, 30, 28, 26), 2f * dp))   // le fil
        o.halo(c, cx, cy, R * 3f, 0.2f * allume * app * a, chaud)
        o.poussieres(c, poussiere, sec, w, h, rgb(255, 210, 160), 0.4f * allume * app * a, vx = 0.004f, vy = -0.01f)
        c.drawCircle(cx, cy, R, o.lumiere(avecAlpha(chaud, 0.06f * app * a + 0.08f * allume * a)))
        c.drawCircle(cx, cy, R, o.lumiere(argb(60f * app * a, 255, 230, 200), 1.5f * dp))
        c.drawRect(cx - R * 0.4f, cy - R * 1.6f, cx + R * 0.4f, cy - R * 0.9f, o.pinceau(argb(255f * app * a, 60, 56, 50)))   // le culot
        for (k in 0 until 4) {
            c.drawLine(cx - R * 0.4f, cy - R * (1.5f - 0.15f * k), cx + R * 0.4f, cy - R * (1.45f - 0.15f * k), o.pinceau(argb(255f * app * a, 90, 84, 74), 1f * dp))
        }
        val fil = Path()                                         // le filament en zigzag
        fil.moveTo(cx - R * 0.25f, cy - R * 0.9f)
        fil.lineTo(cx - R * 0.25f, cy)
        for (k in 0 until 7) fil.lineTo(cx - R * 0.25f + (k + 1) * R * 0.5f / 7f, cy + if (k % 2 == 0) R * 0.12f else -R * 0.12f)
        fil.lineTo(cx + R * 0.25f, cy - R * 0.9f)
        c.drawPath(fil, o.pinceau(argb(255f * app * a, 70, 60, 50), 1.2f * dp))
        val couches = arrayOf(floatArrayOf(10f, 0.12f), floatArrayOf(3f, 0.5f), floatArrayOf(1.2f, 1f))
        for (cc in couches) c.drawPath(fil, o.lumiere(avecAlpha(chaud, cc[1] * allume * app * a), cc[0] * dp))
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, cy, rev, chaud)
    }
}

// ===================== SMART SWITCH : « Transfert » =====================
// Deux téléphones face à face : un flot de particules passe de l'un à l'autre en arc.
class Transfert : Scene() {
    private val flux = nuage(141, 70)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(8, 10, 18), rgb(4, 5, 8), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val cx = w / 2f; val cy = h / 2f
        val m = min(w, h)
        val bleu = rgb(80, 150, 255)
        val L = m * 0.17f; val H = m * 0.34f
        val x1 = cx - m * 0.3f; val x2 = cx + m * 0.3f
        for (x in floatArrayOf(x1, x2)) {
            val r = RectF(x - L / 2f, cy - H / 2f, x + L / 2f, cy + H / 2f)
            c.drawRoundRect(r, L * 0.18f, L * 0.18f, o.pinceau(argb(255f * app * a, 22, 24, 32)))
            c.drawRoundRect(r, L * 0.18f, L * 0.18f, o.lumiere(argb(60f * app * a, 150, 180, 255), 1.5f * dp))
        }
        val rempli = borne(sec / 0.4f)
        c.drawRect(x2 - L * 0.4f, cy + H * 0.42f - H * 0.8f * rempli, x2 + L * 0.4f, cy + H * 0.42f, o.lumiere(avecAlpha(bleu, 0.18f * app * a)))
        val p = o.lumiere()
        for (q in flux) {
            val z = q[2]
            val t = modulo(q[1] + sec * (0.7f + 0.6f * z), 1f)
            val x = mix(x1, x2, t)
            val y = cy - sin(PI.toFloat() * t) * m * (0.18f + 0.12f * z) + (q[0] - 0.5f) * 10f * dp
            p.color = avecAlpha(bleu, (0.4f + 0.6f * z) * sin(PI.toFloat() * t) * app * a)
            c.drawCircle(x, y, (1f + 1.5f * z) * dp, p)
        }
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, x2, cy, rev, bleu)
    }
}

// ===================== SAMSUNG TV PLUS : « Mire » =====================
// Une vieille télé cathodique dans le noir : de la neige, puis la mire apparaît (couleurs atténuées).
class Mire : Scene() {
    private val barres = intArrayOf(rgb(150, 150, 150), rgb(150, 150, 40), rgb(40, 150, 150), rgb(40, 150, 40), rgb(150, 40, 150), rgb(150, 40, 40), rgb(40, 40, 150))

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(5, 5, 6))
        val app = ralentit(intro)
        val a = 1f - rev
        val cx = w / 2f; val cy = h / 2f
        val m = min(w, h)
        val k = doux(rev)
        val L = mix(m * 0.6f, w * 1.05f, k); val H = mix(m * 0.45f, h * 1.05f, k)
        val ecran = RectF(cx - L / 2f, cy - H / 2f, cx + L / 2f, cy + H / 2f)
        c.drawRoundRect(RectF(cx - m * 0.36f, cy - m * 0.28f, cx + m * 0.36f, cy + m * 0.28f), 16f * dp, 16f * dp, o.pinceau(argb(255f * app * a, 26, 22, 20)))
        c.drawRoundRect(ecran, 18f * dp, 18f * dp, o.pinceau(rgb(14, 15, 16)))
        val reglage = borne((sec - 0.03f) / 0.2f)
        val bord = Path().apply { addRoundRect(ecran, 18f * dp, 18f * dp, Path.Direction.CW) }
        c.save(); c.clipPath(bord)
        for ((n, coul) in barres.withIndex()) {                   // la mire
            val bx = ecran.left + ecran.width() * n / 7f
            c.drawRect(bx, ecran.top, bx + ecran.width() / 7f + 1f, ecran.bottom, o.pinceau(avecAlpha(coul, 0.5f * reglage * app)))
        }
        val r = Random((sec * 30f).toInt())                       // la neige (change 30 fois par seconde)
        val p = o.pinceau()
        for (q in 0 until (260 * (1f - reglage) + 20).toInt()) {
            p.color = argb(r.nextFloat() * 120f * app, 200, 200, 200)
            val x = ecran.left + r.nextFloat() * ecran.width(); val y = ecran.top + r.nextFloat() * ecran.height()
            c.drawRect(x, y, x + 2f * dp, y + 2f * dp, p)
        }
        val ligne = o.pinceau(argb(60f, 0, 0, 0), 1f * dp)        // lignes de balayage
        var s = 0f
        while (s < ecran.height()) { c.drawLine(ecran.left, ecran.top + s, ecran.right, ecran.top + s, ligne); s += 3f * dp }
        c.restore()
        o.vignette(c, w, h, 0.55f)
        if (rev > 0f) gommeRect(c, o, ecran, ralentit(rev * 1.3f), 18f * dp)
    }
}

// ===================== SAMSUNG SHOP : « Paquet » =====================
// Un paquet posé dans l'ombre ; le couvercle se soulève et la lumière s'échappe de l'intérieur.
class Paquet : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(10, 10, 14), rgb(4, 4, 6), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val cx = w / 2f; val cy = h * 0.55f
        val m = min(w, h)
        val bleu = rgb(70, 130, 255)
        val L = m * 0.4f; val H = m * 0.28f
        val ouvre = ralentit(borne((sec - 0.02f) / 0.25f))
        o.tache(c, cx, cy + H / 2f + 6f * dp, L * 0.7f, 8f * dp, 0.5f * app, Color.BLACK)
        val faisceau = Path()                                    // la lumière qui sort
        faisceau.moveTo(cx - L * 0.45f, cy - H / 2f); faisceau.lineTo(cx + L * 0.45f, cy - H / 2f)
        faisceau.lineTo(cx + L * 0.9f, cy - H / 2f - m * 0.6f); faisceau.lineTo(cx - L * 0.9f, cy - H / 2f - m * 0.6f); faisceau.close()
        val l = o.lumiere()
        l.shader = LinearGradient(0f, cy - H / 2f, 0f, cy - H / 2f - m * 0.6f, avecAlpha(bleu, 0.35f * ouvre * app * a), avecAlpha(bleu, 0f), Shader.TileMode.CLAMP)
        c.drawPath(faisceau, l)
        val corps = o.pinceau()
        corps.shader = LinearGradient(cx - L / 2f, 0f, cx + L / 2f, 0f, argb(255f * app * a, 30, 34, 48), argb(255f * app * a, 18, 20, 28), Shader.TileMode.CLAMP)
        c.drawRect(cx - L / 2f, cy - H / 2f, cx + L / 2f, cy + H / 2f, corps)
        c.drawRect(cx - L * 0.06f, cy - H / 2f, cx + L * 0.06f, cy + H / 2f, o.pinceau(avecAlpha(bleu, 0.7f * app * a)))   // ruban
        c.save()                                                 // le couvercle se soulève et bascule
        c.translate(cx - L * 0.55f, cy - H / 2f - ouvre * m * 0.08f)
        c.rotate(-ouvre * 25f)
        c.drawRect(0f, -H * 0.18f, L * 1.1f, 0f, o.pinceau(argb(255f * app * a, 36, 40, 56)))
        c.drawRect(L * 0.49f, -H * 0.18f, L * 0.61f, 0f, o.pinceau(avecAlpha(bleu, 0.8f * app * a)))
        c.restore()
        o.halo(c, cx, cy - H / 2f, L * 0.5f, 0.3f * ouvre * app * a, bleu)
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy - H / 2f, rev, bleu)
    }
}
