package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// LOT 2 : Google. Les couleurs de la marque, mais dans des décors sombres.
val BLEU_G = rgb(66, 133, 244)
val ROUGE_G = rgb(234, 67, 53)
val JAUNE_G = rgb(251, 188, 5)
val VERT_G = rgb(52, 168, 83)
val COULEURS_G = intArrayOf(BLEU_G, ROUGE_G, JAUNE_G, VERT_G)

// --- petits outils partagés par les scènes ---
fun degradeVertical(c: Canvas, o: Outils, w: Float, haut: Int, bas: Int, y0: Float, y1: Float) {
    val p = o.pinceau()
    p.shader = LinearGradient(0f, y0, 0f, y1, haut, bas, Shader.TileMode.CLAMP)
    c.drawRect(0f, y0, w, y1, p)
}

fun texte(c: Canvas, s: String, x: Float, y: Float, taille: Float, couleur: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = couleur
    p.textSize = taille
    p.textAlign = Paint.Align.CENTER
    c.drawText(s, x, y + taille * 0.35f, p)
}

// Efface un rectangle (avec une force de 0 à 1) : l'app apparaît à travers.
fun gommeRect(c: Canvas, o: Outils, rect: RectF, force: Float, rayon: Float = 0f) {
    val q = o.pinceau(argb(255f * borne(force), 0, 0, 0))
    q.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    if (rayon > 0f) c.drawRoundRect(rect, rayon, rayon, q) else c.drawRect(rect, q)
}

fun fonce(couleur: Int, f: Float): Int =
    rgb((Color.red(couleur) * f).toInt(), (Color.green(couleur) * f).toInt(), (Color.blue(couleur) * f).toInt())

// ===================== GOOGLE : « Quatre » =====================
// Quatre sphères lumineuses rebondissent sur un sol brillant (ombres et reflets),
// puis s'envolent en orbite. À la révélation, elles filent vers les coins.
class Quatre : Scene() {
    private val flou = bokehListe(4, COULEURS_G)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val sol = h * 0.62f
        degradeVertical(c, o, w, rgb(10, 11, 18), rgb(20, 22, 32), 0f, sol)
        degradeVertical(c, o, w, rgb(14, 15, 22), rgb(6, 6, 9), sol, h)
        val app = ralentit(intro)
        val a = 1f - rev
        o.bokeh(c, flou, sec, w, sol, 0.25f * app * a)
        val envol = doux(borne((sec - 0.45f) / 0.6f))
        val cx = w / 2f
        val sphere = 13f * dp
        val positions = ArrayList<FloatArray>()           // [profondeur, numéro, x, y]
        for (k in 0 until 4) {
            val xs = cx + (k - 1.5f) * 52f * dp
            val saut = abs(sin(sec * 6f + k * 0.8f)) * 70f * dp * app   // rebond
            val ys = sol - sphere - saut
            val ang = sec * 2.4f + k * PI.toFloat() / 2f                // orbite vue en perspective
            val xo = cx + cos(ang) * 70f * dp
            val yo = sol - 110f * dp + sin(ang) * 22f * dp
            val prof = 1f + 0.25f * sin(ang)
            var x = mix(xs, xo, envol)
            var y = mix(ys, yo, envol)
            val z = mix(1f, prof, envol)
            val fuite = accelere(rev)
            val coinX = if (k == 0 || k == 3) 0f else w
            val coinY = if (k == 0 || k == 1) 0f else h
            x = mix(x, coinX, fuite); y = mix(y, coinY, fuite)
            positions.add(floatArrayOf(z, k.toFloat(), x, y))
        }
        // on dessine d'abord les sphères les plus lointaines
        for (q in positions.sortedWith(compareBy<FloatArray>({ it[0] }, { it[1] }))) {
            val z = q[0]; val k = q[1].toInt(); val x = q[2]; val y = q[3]
            val coul = COULEURS_G[k]
            val hauteur = borne((sol - y) / (180f * dp))
            o.tache(c, x, sol + 2f * dp, sphere * 1.6f * z, sphere * 0.3f, 0.25f * (1f - hauteur) * app * a, coul)  // flaque de lumière
            o.tache(c, x, 2f * sol - y, sphere * 0.9f * z, sphere * 1.3f * z, 0.12f * app * a, coul)              // reflet
            o.halo(c, x, y, sphere * 2.6f * z, 0.30f * app * a, coul)
            val p = o.pinceau()
            p.shader = RadialGradient(
                x - sphere * 0.35f * z, y - sphere * 0.35f * z, sphere * 1.3f * z,
                intArrayOf(avecAlpha(Color.WHITE, 0.85f * app * a), avecAlpha(coul, app * a), avecAlpha(coul, 0.7f * app * a)),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(x, y, sphere * z * app, p)
            if (rev > 0f) c.drawLine(x, y, mix(x, w / 2f, 0.3f), mix(y, h / 2f, 0.3f), o.lumiere(avecAlpha(coul, 0.4f * a), 3f * dp))
        }
        c.drawLine(0f, sol, w, sol, o.lumiere(argb(30f * app, 140, 150, 200), 1f * dp))
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, w / 2f, h / 2f, rev, BLEU_G)
    }
}

// ===================== CHROME : « Tunnel » =====================
// On file dans un tunnel d'anneaux rouge, jaune et vert vers une lumière bleue ; à la révélation, on en sort.
class Tunnel : Scene() {
    private val stries = nuage(41, 50)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        c.drawColor(rgb(6, 7, 12))
        val app = ralentit(intro)
        val a = 1f - rev
        val vitesse = 1.2f + 3f * accelere(rev)
        val r0 = min(w, h) * 0.16f
        o.halo(c, cx, cy, r0 * 1.6f, 0.35f * app, BLEU_G)
        c.drawCircle(cx, cy, r0 * 0.32f * app, o.pinceau(avecAlpha(rgb(150, 190, 255), 0.9f * app)))
        for (s in stries) {                                   // stries de vitesse
            val ang = s[0] * 6.28f
            val d = modulo(s[1] + sec * vitesse * 0.5f * (0.5f + s[2]), 1f)
            val r1 = r0 * 0.6f + d * d * max(w, h) * 0.8f
            val r2 = r1 * (1f + 0.15f * d)
            c.drawLine(cx + cos(ang) * r1, cy + sin(ang) * r1, cx + cos(ang) * r2, cy + sin(ang) * r2,
                o.lumiere(argb(90f * d * app * a, 200, 210, 255), 1.3f * dp))
        }
        val n = 12
        for (k in 0 until n) {                                 // anneaux qui viennent vers nous
            val z = modulo(k.toFloat() / n - sec * vitesse * 0.35f, 1f) * 3f + 0.25f
            val r = r0 / z * 1.2f
            if (r > max(w, h)) continue
            val alpha = borne((3.25f - z) / 3f) * borne(z * 1.5f) * app
            val rot = z * 90f + sec * 60f
            val rect = RectF(cx - r, cy - r, cx + r, cy + r)
            val cols = intArrayOf(ROUGE_G, JAUNE_G, VERT_G)
            for (s in 0 until 3) {
                val p = o.lumiere(avecAlpha(cols[s], 0.55f * alpha * a), max(1.5f * dp, r * 0.07f))
                p.strokeCap = Paint.Cap.BUTT
                c.drawArc(rect, rot + s * 120f + 6f, 108f, false, p)
            }
        }
        o.vignette(c, w, h, 0.65f)
        o.revelationDouce(c, cx, cy, rev, BLEU_G, 0.25f)
    }
}

// ===================== MAPS : « Itinéraire » =====================
// Une ville vue en perspective ; un trajet lumineux file dans les rues jusqu'à l'épingle.
// À la révélation, la caméra plonge sur l'épingle.
class Itineraire : Scene() {
    private val trajet = arrayOf(
        floatArrayOf(-4f, 4f), floatArrayOf(-4f, 1f), floatArrayOf(-1f, 1f), floatArrayOf(-1f, -1f),
        floatArrayOf(2f, -1f), floatArrayOf(2f, -3f), floatArrayOf(3f, -3f)
    )
    private val blocs = Random(8).let { r ->
        val l = ArrayList<FloatArray>()
        for (gx in -8..8) for (gy in -8..8) l.add(floatArrayOf(gx.toFloat(), gy.toFloat(), r.nextFloat()))
        l
    }

    // Plan de la ville (px, py) -> écran : rotation, puis écrasement vertical (perspective).
    private fun projeter(px: Float, py: Float, cx: Float, cy: Float, ang: Float, ech: Float): FloatArray {
        val x = px * cos(ang) - py * sin(ang)
        val y = px * sin(ang) + py * cos(ang)
        return floatArrayOf(cx + x * ech, cy + y * ech * 0.5f)
    }

    private fun pointTrajet(t: Float): FloatArray {
        val n = trajet.size - 1
        val s = borne(t) * n
        val k = min(s.toInt(), n - 1)
        val f = s - k
        return floatArrayOf(mix(trajet[k][0], trajet[k + 1][0], f), mix(trajet[k][1], trajet[k + 1][1], f))
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(8, 10, 16), rgb(16, 20, 28), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val ang = Math.toRadians(32.0 + sec * 5.0).toFloat()
        val ech = min(w, h) * 0.11f * (0.85f + 0.15f * app)
        val m = projeter(-0.5f, 0.5f, 0f, 0f, ang, ech)        // on centre la vue sur le milieu du trajet
        val cx = w / 2f - m[0]; val cy = h * 0.5f - m[1]
        val zoom = 1f + 4f * accelere(rev)
        val pin = trajet[trajet.size - 1]
        val ep = projeter(pin[0], pin[1], cx, cy, ang, ech)
        val epx = ep[0]; val epy = ep[1]
        c.save()
        c.translate(epx, epy); c.scale(zoom, zoom); c.translate(-epx, -epy)
        val bloc = o.pinceau()
        for (b in blocs) {                                     // îlots d'immeubles
            val p0 = projeter(b[0] + 0.12f, b[1] + 0.12f, cx, cy, ang, ech)
            val p1 = projeter(b[0] + 0.88f, b[1] + 0.12f, cx, cy, ang, ech)
            val p2 = projeter(b[0] + 0.88f, b[1] + 0.88f, cx, cy, ang, ech)
            val p3 = projeter(b[0] + 0.12f, b[1] + 0.88f, cx, cy, ang, ech)
            val chemin = Path()
            chemin.moveTo(p0[0], p0[1]); chemin.lineTo(p1[0], p1[1]); chemin.lineTo(p2[0], p2[1]); chemin.lineTo(p3[0], p3[1]); chemin.close()
            val dist = hypot(b[0], b[1]) / 9f
            val ton = (24 + 22 * b[2]).toInt()
            bloc.color = argb(255f * app * borne(1.2f - dist), ton, ton + 3, ton + 10)
            c.drawPath(chemin, bloc)
        }
        val avance = borne(sec / 0.7f)                        // le trajet se dessine
        val chemin = Path()
        for (s in 0..60) {
            val g = pointTrajet(avance * s / 60f)
            val q = projeter(g[0], g[1], cx, cy, ang, ech)
            if (s == 0) chemin.moveTo(q[0], q[1]) else chemin.lineTo(q[0], q[1])
        }
        val couches = arrayOf(floatArrayOf(16f, 0.10f), floatArrayOf(7f, 0.30f), floatArrayOf(3f, 0.95f))
        for (k in couches) c.drawPath(chemin, o.lumiere(avecAlpha(BLEU_G, k[1] * app * a), k[0] * dp))
        val g = pointTrajet(avance)
        val tete = projeter(g[0], g[1], cx, cy, ang, ech)
        o.halo(c, tete[0], tete[1], 18f * dp, 0.5f * app * a, rgb(140, 190, 255))
        val chute = ralentit(borne((sec - 0.55f) / 0.3f))     // l'épingle tombe
        if (chute > 0f) {
            val onde = modulo(sec * 1.3f, 1f)
            c.save(); c.translate(epx, epy); c.scale(1f, 0.5f)
            c.drawCircle(0f, 0f, 40f * dp * onde, o.pinceau(avecAlpha(ROUGE_G, 0.6f * (1f - onde) * a), 2.5f * dp))
            c.restore()
            o.tache(c, epx, epy, 9f * dp, 3f * dp, 0.5f * chute * a, Color.BLACK)
            val yb = epy - (1f - chute) * 90f * dp
            val corps = o.pinceau()
            corps.shader = RadialGradient(
                epx - 3f * dp, yb - 26f * dp, 16f * dp,
                avecAlpha(rgb(255, 140, 120), chute * a), avecAlpha(ROUGE_G, chute * a), Shader.TileMode.CLAMP
            )
            c.drawCircle(epx, yb - 22f * dp, 11f * dp, corps)
            val pied = Path()
            pied.moveTo(epx - 9.5f * dp, yb - 17f * dp); pied.lineTo(epx, yb); pied.lineTo(epx + 9.5f * dp, yb - 17f * dp); pied.close()
            c.drawPath(pied, corps)
            c.drawCircle(epx, yb - 22f * dp, 4f * dp, o.pinceau(avecAlpha(rgb(110, 20, 15), chute * a)))
        }
        c.restore()
        o.vignette(c, w, h, 0.6f)
        o.revelationDouce(c, epx, epy, rev, ROUGE_G)
    }
}

// ===================== GMAIL : « Pli » =====================
// Sous une lampe de bureau, une enveloppe s'ouvre ; la lettre en sort, éclairée,
// puis vient vers nous jusqu'à devenir l'app.
class Pli : Scene() {
    private val poussiere = nuage(13, 40)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f; val cy = h * 0.55f
        degradeVertical(c, o, w, rgb(14, 12, 12), rgb(6, 6, 7), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        o.tache(c, cx, cy, min(w, h) * 0.55f, min(w, h) * 0.35f, 0.16f * app, rgb(255, 210, 160))   // lampe
        o.poussieres(c, poussiere, sec, w, h, rgb(255, 220, 180), 0.4f * app * a, vx = 0.006f, vy = -0.015f)
        val L = min(w, h) * 0.46f; val H = L * 0.64f
        val g = cx - L / 2f; val d = cx + L / 2f
        val t = cy - H / 2f + (1f - app) * 40f * dp; val b = cy + H / 2f + (1f - app) * 40f * dp
        val ouverture = doux(borne((sec - 0.25f) / 0.4f))
        val sortie = doux(borne((sec - 0.5f) / 0.5f))
        o.tache(c, cx, b + 10f * dp, L * 0.55f, 10f * dp, 0.45f * app * a, Color.BLACK)              // ombre
        val lettreH = H * 0.9f
        val ly = t + H * 0.08f - sortie * H * 0.62f
        val envol = accelere(rev)
        val lettre = RectF(mix(g + L * 0.07f, -w * 0.05f, envol), mix(ly, -h * 0.05f, envol),
            mix(d - L * 0.07f, w * 1.05f, envol), mix(ly + lettreH, h * 1.05f, envol))
        val pointeY = mix(t + H * 0.55f, t - H * 0.48f, ouverture)
        val rabat = Path()
        rabat.moveTo(g, t); rabat.lineTo(cx, pointeY); rabat.lineTo(d, t); rabat.close()
        val doublure = o.pinceau()
        doublure.shader = if (ouverture > 0.5f)
            LinearGradient(0f, t, 0f, pointeY, argb(255f * a, 120, 30, 25), argb(255f * a, 200, 60, 45), Shader.TileMode.CLAMP)
        else
            LinearGradient(0f, t, 0f, pointeY, argb(255f * a, 52, 46, 46), argb(255f * a, 40, 36, 36), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(g, t, d, b), 6f * dp, 6f * dp, o.pinceau(argb(255f * a, 26, 23, 24)))    // dos
        if (ouverture > 0.5f) c.drawPath(rabat, doublure)                                             // rabat ouvert
        val papier = o.pinceau()
        papier.shader = LinearGradient(0f, lettre.top, 0f, lettre.bottom, rgb(70, 66, 64), rgb(44, 42, 42), Shader.TileMode.CLAMP)
        c.drawRoundRect(lettre, 5f * dp, 5f * dp, papier)
        for (k in 0 until 4) {
            val yy = lettre.top + lettre.height() * (0.2f + 0.15f * k)
            c.drawLine(lettre.left + lettre.width() * 0.12f, yy, lettre.right - lettre.width() * (0.12f + 0.1f * (k % 2)), yy,
                o.pinceau(argb(200f * a, 140, 136, 132), 3f * dp))
        }
        val devant = Path()                                                                            // devant (cache le bas)
        devant.moveTo(g, t + H * 0.32f); devant.lineTo(cx, t + H * 0.66f); devant.lineTo(d, t + H * 0.32f)
        devant.lineTo(d, b); devant.lineTo(g, b); devant.close()
        val corps = o.pinceau()
        corps.shader = LinearGradient(0f, t, 0f, b, argb(255f * a, 46, 40, 40), argb(255f * a, 30, 27, 28), Shader.TileMode.CLAMP)
        c.drawPath(devant, corps)
        c.drawPath(devant, o.pinceau(argb(160f * a, 200, 60, 45), 2f * dp))
        if (ouverture <= 0.5f) c.drawPath(rabat, doublure)                                            // rabat fermé
        c.drawPath(rabat, o.pinceau(argb(200f * a, 220, 70, 55), 2f * dp))
        o.halo(c, cx, lettre.top, L * 0.4f, 0.12f * sortie * a, rgb(255, 210, 170))
        o.vignette(c, w, h, 0.55f)
        if (rev > 0f) gommeRect(c, o, lettre, ralentit(rev * 1.2f))
    }
}

// ===================== PHOTOS : « Pose longue » =====================
// Comme une photo en pose longue : quatre lumières dessinent une fleur dans le noir.
class PoseLongue : Scene() {
    private val flou = bokehListe(9, COULEURS_G, 7)

    private fun point(k: Int, t: Float, cx: Float, cy: Float, R: Float): FloatArray {
        val th = t * 2.2f + k * PI.toFloat() / 2f
        val r = R * (0.35f + 0.65f * abs(cos(2f * th)))
        return floatArrayOf(cx + cos(th) * r, cy + sin(th) * r)
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        c.drawColor(rgb(8, 8, 11))
        val app = ralentit(intro)
        val a = 1f - rev
        o.bokeh(c, flou, sec, w, h, 0.18f * app * a)
        val R = min(w, h) * 0.26f * (1f + 3f * accelere(rev))
        val cols = intArrayOf(ROUGE_G, JAUNE_G, VERT_G, BLEU_G)
        val couches = arrayOf(floatArrayOf(10f, 0.08f), floatArrayOf(4f, 0.25f), floatArrayOf(1.6f, 0.8f))
        for (k in 0 until 4) {
            val chemin = Path()
            val debut = max(0f, sec - 1.1f)
            for (s in 0..50) {
                val q = point(k, mix(debut, sec, s / 50f), cx, cy, R * app)
                if (s == 0) chemin.moveTo(q[0], q[1]) else chemin.lineTo(q[0], q[1])
            }
            for (cc in couches) c.drawPath(chemin, o.lumiere(avecAlpha(cols[k], cc[1] * app * a), cc[0] * dp))
            val q = point(k, sec, cx, cy, R * app)
            o.halo(c, q[0], q[1], 14f * dp, 0.6f * app * a, cols[k])
        }
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, cy, rev, rgb(220, 220, 230))
    }
}

// ===================== YOUTUBE : « Cinéma » =====================
// Une salle de cinéma dans le noir : le projecteur éclaire l'écran à travers la poussière.
// À la révélation, l'écran grandit jusqu'à nous : le film, c'est l'app.
class Cinema : Scene() {
    private val poussiere = nuage(17, 70)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawColor(rgb(6, 5, 6))
        val app = ralentit(intro)
        val a = 1f - rev
        val sw = min(w * 0.82f, h * 0.9f * 1.6f) * (0.8f + 0.2f * app)
        val sh = sw / 1.85f
        val sx = w / 2f; val sy = h * 0.36f
        val k = accelere(rev)
        val ecranR = RectF(mix(sx - sw / 2f, -2f, k), mix(sy - sh / 2f, -2f, k), mix(sx + sw / 2f, w + 2f, k), mix(sy + sh / 2f, h + 2f, k))
        val scintille = 0.85f + 0.15f * sin(sec * 31f) * sin(sec * 17f)
        val srcX = w / 2f; val srcY = h * 1.02f
        val faisceau = Path()                                 // faisceau du projecteur
        faisceau.moveTo(srcX, srcY); faisceau.lineTo(ecranR.left, ecranR.bottom); faisceau.lineTo(ecranR.left, ecranR.top)
        faisceau.lineTo(ecranR.right, ecranR.top); faisceau.lineTo(ecranR.right, ecranR.bottom); faisceau.close()
        val l = o.lumiere()
        l.shader = LinearGradient(srcX, srcY, sx, sy, argb(60f * app * a * scintille, 255, 230, 220), argb(14f * app * a, 255, 200, 190), Shader.TileMode.CLAMP)
        c.drawPath(faisceau, l)
        c.save(); c.clipPath(faisceau)
        o.poussieres(c, poussiere, sec, w, h, rgb(255, 235, 225), 0.5f * app * a, vx = 0.004f, vy = -0.01f)
        c.restore()
        val ecran = o.pinceau()
        ecran.shader = RadialGradient(sx, sy, sw * 0.6f, rgb(60, 18, 20), rgb(24, 10, 12), Shader.TileMode.CLAMP)
        c.drawRect(ecranR, ecran)
        o.tache(c, sx, sy, sw * 0.5f, sh * 0.5f, 0.12f * app * scintille * a, rgb(255, 80, 70))
        val pulse = 1f + 0.05f * sin(sec * 8f)
        val L = sh * 0.36f * pulse; val H = L * 0.7f
        c.drawRoundRect(RectF(sx - L / 2f, sy - H / 2f, sx + L / 2f, sy + H / 2f), H * 0.28f, H * 0.28f, o.pinceau(argb(255f * app * a, 225, 30, 30)))
        val tri = Path()
        tri.moveTo(sx - L * 0.11f, sy - H * 0.24f); tri.lineTo(sx + L * 0.16f, sy); tri.lineTo(sx - L * 0.11f, sy + H * 0.24f); tri.close()
        c.drawPath(tri, o.pinceau(argb(255f * app * a, 250, 245, 245)))
        for (rang in 0 until 3) {                              // fauteuils en silhouette
            val yr = h * (0.80f + 0.08f * rang)
            val larg = (34f + 8f * rang) * dp
            val n = (w / larg).toInt() + 2
            for (s in 0 until n) {
                val x = (s + 0.5f * (rang % 2)) * larg
                c.drawRoundRect(RectF(x, yr, x + larg * 0.86f, yr + 60f * dp), 10f * dp, 10f * dp, o.pinceau(argb(255f * a, 3 + rang, 3, 4)))
                c.drawLine(x + larg * 0.1f, yr + 1.5f * dp, x + larg * 0.76f, yr + 1.5f * dp, o.pinceau(argb(30f * app * a, 255, 120, 110), 1.2f * dp))
            }
        }
        o.vignette(c, w, h, 0.6f)
        if (rev > 0f) gommeRect(c, o, ecranR, ralentit(rev * 1.3f))
    }
}

// ===================== ASSISTANT : « Écoute » =====================
// Quatre fils de couleur ondulent comme une voix qu'on écoute, sur toute la largeur.
class Ecoute : Scene() {
    private val poussiere = nuage(23, 40)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cy = h / 2f
        degradeVertical(c, o, w, rgb(10, 11, 18), rgb(6, 6, 10), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        o.poussieres(c, poussiere, sec, w, h, rgb(180, 190, 230), 0.25f * app * a, vx = 0.02f, vy = 0f)
        val couches = arrayOf(floatArrayOf(14f, 0.07f), floatArrayOf(5f, 0.25f), floatArrayOf(2f, 0.9f))
        for (k in 0 until 4) {
            val chemin = Path()
            for (s in 0..90) {
                val t = s / 90f
                val enveloppe = sin(PI.toFloat() * t) * sin(PI.toFloat() * t)
                val amp = h * 0.09f * enveloppe * (0.5f + 0.5f * sin(sec * 3f + k)) * app
                val y = cy + sin(t * (9f + k) + sec * (5f + k * 0.7f) + k * 1.3f) * amp + (k - 1.5f) * 4f * dp
                if (s == 0) chemin.moveTo(t * w, y) else chemin.lineTo(t * w, y)
            }
            for (cc in couches) c.drawPath(chemin, o.lumiere(avecAlpha(COULEURS_G[k], cc[1] * app * a), cc[0] * dp))
        }
        o.vignette(c, w, h, 0.55f)
        o.revelationBalayageDouce(c, rev, 0f, BLEU_G)
    }
}

// ===================== DRIVE : « Prisme » =====================
// Un triangle de trois lumières tourne en 3D au-dessus d'un sol qui le reflète.
class Prisme : Scene() {
    private val flou = bokehListe(14, intArrayOf(VERT_G, JAUNE_G, BLEU_G), 6)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f; val cy = h * 0.46f
        val sol = h * 0.66f
        degradeVertical(c, o, w, rgb(9, 11, 14), rgb(16, 19, 22), 0f, sol)
        degradeVertical(c, o, w, rgb(12, 14, 17), rgb(5, 6, 7), sol, h)
        val app = ralentit(intro)
        val a = 1f - rev
        o.bokeh(c, flou, sec, w, sol, 0.18f * app * a)
        val r = min(w, h) * 0.2f * (1f + 4f * accelere(rev))
        val tour = sec * 1.6f
        val cols = intArrayOf(VERT_G, JAUNE_G, BLEU_G)
        val couches = arrayOf(floatArrayOf(16f, 0.10f), floatArrayOf(6f, 0.35f), floatArrayOf(3f, 0.95f))
        for (miroir in booleanArrayOf(true, false)) {
            val pts = Array(3) { k ->
                val ang = Math.toRadians(-90.0 + 120.0 * k).toFloat()
                val x = cos(ang) * r * cos(tour)
                val y = sin(ang) * r
                floatArrayOf(cx + x, if (miroir) 2f * sol - (cy + y) else cy + y)
            }
            for (k in 0 until 3) {
                val p0 = pts[k]; val p1 = pts[(k + 1) % 3]
                val f = borne(app * 1.3f - k * 0.15f)
                val al = (if (miroir) 0.25f else 1f) * a
                for (cc in couches) {
                    c.drawLine(p0[0], p0[1], mix(p0[0], p1[0], f), mix(p0[1], p1[1], f), o.lumiere(avecAlpha(cols[k], cc[1] * al), cc[0] * dp))
                }
            }
        }
        o.tache(c, cx, sol, r * 1.2f, 6f * dp, 0.2f * app * a, rgb(180, 220, 200))
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, cy, rev, VERT_G)
    }
}

// ===================== DOCS : « Écriture » =====================
// Une page posée sous une lampe s'écrit toute seule, curseur clignotant ; puis elle se soulève vers nous.
class Ecriture : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f; val cy = h * 0.5f
        degradeVertical(c, o, w, rgb(10, 12, 18), rgb(5, 6, 9), 0f, h)
        val app = ralentit(intro)
        o.tache(c, cx, cy, min(w, h) * 0.5f, min(w, h) * 0.45f, 0.12f * app, rgb(160, 190, 255))
        val L = min(w, h) * 0.44f; val H = L * 1.3f
        val k = accelere(rev)
        val page = RectF(mix(cx - L / 2f, -2f, k), mix(cy - H / 2f + (1f - app) * 50f * dp, -2f, k),
            mix(cx + L / 2f, w + 2f, k), mix(cy + H / 2f + (1f - app) * 50f * dp, h + 2f, k))
        o.tache(c, cx + 8f * dp, page.bottom + 6f * dp, L * 0.5f, 10f * dp, 0.5f * app, Color.BLACK)
        val fond = o.pinceau()
        fond.shader = LinearGradient(page.left, page.top, page.right, page.bottom, rgb(44, 60, 100), rgb(28, 38, 66), Shader.TileMode.CLAMP)
        c.drawRoundRect(page, 8f * dp, 8f * dp, fond)
        val progression = borne(sec / 1.0f) * 8f
        val lignes = progression.toInt()
        var curseurX = -1f; var curseurY = 0f
        for (n in 0 until 8) {
            val yy = page.top + page.height() * (0.14f + n * 0.095f)
            val plein = page.width() * (if (n % 3 != 2) 0.66f else 0.42f)
            val f = if (n < lignes) 1f else if (n == lignes) progression - lignes else 0f
            if (f > 0f) {
                c.drawLine(page.left + page.width() * 0.16f, yy, page.left + page.width() * 0.16f + plein * f, yy,
                    o.pinceau(argb(230f * app, 180, 200, 255), 3.5f * dp))
            }
            if (n == min(lignes, 7)) { curseurX = page.left + page.width() * 0.16f + plein * f + 4f * dp; curseurY = yy }
        }
        if (curseurX >= 0f && (sec * 3f).toInt() % 2 == 0 && rev == 0f) {     // curseur qui clignote
            c.drawLine(curseurX, curseurY - 7f * dp, curseurX, curseurY + 7f * dp, o.pinceau(BLEU_G, 2.5f * dp))
            o.halo(c, curseurX, curseurY, 10f * dp, 0.5f, BLEU_G)
        }
        o.vignette(c, w, h, 0.55f)
        if (rev > 0f) gommeRect(c, o, page, ralentit(rev * 1.2f))
    }
}

// ===================== MEET : « Visio » =====================
// Une mosaïque de participants en silhouette ; celui qui parle s'illumine,
// puis sa vignette s'agrandit pour devenir l'app.
class Visio : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        degradeVertical(c, o, w, rgb(12, 14, 18), rgb(6, 7, 9), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val s = min(w, h) * 0.3f
        val cols = intArrayOf(VERT_G, BLEU_G, JAUNE_G, ROUGE_G)
        val parleur = (sec / 0.5f).toInt() % 4
        val e = accelere(rev)
        for (k in 0 until 4) {
            var x = cx + (k % 2 - 0.5f) * s * 1.1f
            var y = cy + (k / 2 - 0.5f) * s * 0.78f
            val f = ralentit(borne(sec / 0.12f - k * 0.5f))
            if (f <= 0f) continue
            var ww = s * f; var hh = s * 0.7f * f
            if (k == parleur && rev > 0f) {
                x = mix(x, cx, e); y = mix(y, cy, e); ww = mix(ww, w * 1.05f, e); hh = mix(hh, h * 1.05f, e)
            }
            val rect = RectF(x - ww / 2f, y - hh / 2f, x + ww / 2f, y + hh / 2f)
            val fond = o.pinceau()
            fond.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, rgb(36, 40, 50), rgb(22, 24, 30), Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, 12f * dp, 12f * dp, fond)
            val tete = hh * 0.17f
            val bord = Path()
            bord.addRoundRect(rect, 12f * dp, 12f * dp, Path.Direction.CW)
            c.save(); c.clipPath(bord)
            c.drawCircle(x, y - hh * 0.05f, tete, o.pinceau(rgb(14, 15, 18)))
            c.drawOval(RectF(x - tete * 2f, y + tete * 0.9f, x + tete * 2f, y + tete * 4f), o.pinceau(rgb(14, 15, 18)))
            c.drawCircle(x, y - hh * 0.05f, tete, o.lumiere(avecAlpha(cols[k], 0.35f), 2f * dp))
            c.restore()
            if (k == parleur) {
                val vibre = 0.5f + 0.5f * sin(sec * 14f)
                c.drawRoundRect(rect, 12f * dp, 12f * dp, o.lumiere(avecAlpha(cols[k], (0.5f + 0.4f * vibre) * a), 3f * dp))
                o.tache(c, x, y, ww * 0.6f, hh * 0.6f, 0.10f * a, cols[k])
                if (rev > 0f) gommeRect(c, o, rect, ralentit(rev * 1.3f), 12f * dp)
            }
        }
        o.vignette(c, w, h, 0.5f)
    }
}

// ===================== TRADUCTION : « Passage » =====================
// Une lettre voyage d'une bulle à l'autre en changeant d'écriture, laissant une traînée.
class Passage : Scene() {
    private val signes = arrayOf("A", "Ж", "Ω", "ب", "ש", "Ä")

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(10, 12, 20), rgb(5, 6, 10), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val bx1 = w / 2f - m * 0.28f; val bx2 = w / 2f + m * 0.28f; val by = h / 2f
        val bulles = arrayOf(floatArrayOf(bx1), floatArrayOf(bx2))
        val teintes = intArrayOf(rgb(90, 100, 130), BLEU_G)
        for (k in 0 until 2) {
            val bx = bulles[k][0]
            c.drawRoundRect(RectF(bx - m * 0.13f, by - m * 0.1f, bx + m * 0.13f, by + m * 0.1f), m * 0.05f, m * 0.05f,
                o.pinceau(avecAlpha(teintes[k], 0.8f * app * a), 2.5f * dp))
            o.halo(c, bx, by, m * 0.12f, 0.1f * app * a, teintes[k])
        }
        val phase = modulo(sec / 0.7f, 1f)
        val k = (sec / 0.7f).toInt() % signes.size
        val trajet = doux(phase)
        val x = mix(bx1, bx2, trajet)
        val y = by - sin(PI.toFloat() * trajet) * m * 0.2f
        for (g in 1 until 10) {                                // traînée
            val tt = doux(max(0f, phase - g * 0.03f))
            o.halo(c, mix(bx1, bx2, tt), by - sin(PI.toFloat() * tt) * m * 0.2f, 5f * dp, 0.12f * (1f - g / 10f) * app * a, BLEU_G)
        }
        val taille = m * 0.11f
        texte(c, signes[k], x, y, taille, avecAlpha(rgb(210, 220, 255), app * a * (1f - trajet)))
        texte(c, signes[(k + 1) % signes.size], x, y, taille, avecAlpha(rgb(140, 180, 255), app * a * trajet))
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, bx2, by, rev, BLEU_G)
    }
}

// ===================== EARTH : « Planète bleue » =====================
// La Terre tourne dans l'espace : océans éclairés d'un côté, lumières des villes de l'autre.
class PlaneteBleue : Scene() {
    private val etoiles = nuage(3, 110)
    private val villes = Random(12).let { r ->
        val l = ArrayList<FloatArray>()
        for (k in 0 until 220) {
            val y = 1f - 2f * r.nextFloat(); val th = r.nextFloat() * 6.28f
            if (abs(y) < 0.75f) l.add(floatArrayOf(y, th))
        }
        l
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        c.drawColor(rgb(2, 3, 7))
        val app = ralentit(intro)
        val a = 1f - rev
        o.poussieres(c, etoiles, 0f, w, h, rgb(220, 225, 255), 0.7f * a, vx = 0f, vy = 0f)
        val R = min(w, h) * 0.3f * (0.85f + 0.15f * app) * (1f + 4f * accelere(rev))
        if (app > 0.01f) {
            o.halo(c, cx, cy, R * 1.12f, 0.3f * app, rgb(80, 140, 255))          // atmosphère
            val corps = o.pinceau()
            corps.shader = RadialGradient(cx - R * 0.45f, cy - R * 0.35f, R * 1.6f,
                intArrayOf(rgb(40, 90, 170), rgb(14, 40, 90), rgb(3, 8, 20)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            corps.alpha = (app * 255).toInt()
            c.drawCircle(cx, cy, R, corps)
            val rot = sec * 0.5f
            val p = o.pinceau()
            for (v in villes) {                                                     // villes du côté nuit
                val rr = sqrt(1f - v[0] * v[0])
                val x = cos(v[1] + rot) * rr
                val z = sin(v[1] + rot) * rr
                if (z < 0f) continue
                val nuit = borne(x * 1.5f + 0.2f)
                if (nuit <= 0f) continue
                p.color = argb(220f * nuit * app * a, 255, 200, 120)
                c.drawCircle(cx + x * R, cy + v[0] * R, 1.2f * dp, p)
            }
            c.drawCircle(cx, cy, R, o.lumiere(argb(120f * app * a, 120, 180, 255), 2f * dp))
        }
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, rgb(110, 170, 255))
    }
}

// ===================== CARTES EN ÉVENTAIL (Wallet, banques...) =====================
// Des cartes en verre sombre s'étalent en éventail ; un reflet lumineux glisse dessus.
class Eventail(private val couleurs: IntArray = intArrayOf(BLEU_G, VERT_G, JAUNE_G, ROUGE_G)) : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f; val cy = h * 0.5f
        degradeVertical(c, o, w, rgb(12, 13, 18), rgb(5, 5, 8), 0f, h)
        val app = ralentit(intro)
        val L = min(w, h) * 0.46f; val H = L * 0.62f
        val ev = doux(borne(sec / 0.6f)) * (1f - doux(rev))
        o.tache(c, cx, cy + H * 1.2f, L * 0.8f, 12f * dp, 0.5f * app, Color.BLACK)
        val n = couleurs.size
        for (k in 0 until n) {
            val coul = couleurs[k]
            c.save()
            c.translate(cx, cy + H * 0.7f + (1f - app) * 60f * dp)
            c.rotate((k - (n - 1) / 2f) * 13f * ev)
            c.translate(0f, -H * 0.7f - (n - k) * 4f * dp)
            val rect = RectF(-L / 2f, -H / 2f, L / 2f, H / 2f)
            val carte = o.pinceau()
            carte.shader = LinearGradient(-L / 2f, -H / 2f, L / 2f, H / 2f, coul, fonce(coul, 0.35f), Shader.TileMode.CLAMP)
            carte.alpha = (0.95f * app * 255).toInt()
            c.drawRoundRect(rect, 12f * dp, 12f * dp, carte)
            val px = mix(-L, L, modulo(sec * 0.8f + k * 0.15f, 1.4f))           // reflet qui glisse
            val reflet = o.lumiere()
            reflet.shader = LinearGradient(px - 40f * dp, 0f, px + 40f * dp, 0f,
                intArrayOf(Color.TRANSPARENT, argb(40f * app, 255, 255, 255), Color.TRANSPARENT), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            val bord = Path()
            bord.addRoundRect(rect, 12f * dp, 12f * dp, Path.Direction.CW)
            c.save(); c.clipPath(bord); c.rotate(20f); c.drawRect(-L, -L, L, L, reflet); c.restore()
            c.drawRoundRect(RectF(-L / 2f + 16f * dp, -H / 4f, -L / 2f + 40f * dp, -H / 4f + 18f * dp), 3f * dp, 3f * dp,
                o.pinceau(argb(200f * app, 230, 200, 120)))                         // puce
            c.restore()
        }
        o.vignette(c, w, h, 0.55f)
        o.revelationDouce(c, cx, cy, rev, couleurs[0])
    }
}

// ===================== GRILLE NÉON (Play Jeux, jeux...) =====================
// Une grille néon défile vers l'horizon ; des symboles de manette flottent au-dessus.
class Neon(private val couleur: Int = VERT_G, private val symboles: Boolean = true) : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f
        val horizon = h * 0.55f
        degradeVertical(c, o, w, rgb(8, 6, 16), rgb(22, 10, 30), 0f, horizon)
        c.drawRect(0f, horizon, w, h, o.pinceau(rgb(6, 5, 10)))
        val app = ralentit(intro)
        val a = 1f - rev
        o.tache(c, cx, horizon, w * 0.6f, 14f * dp, 0.35f * app * a, couleur)
        for (k in -12..12) {                                         // lignes qui fuient vers l'horizon
            c.drawLine(cx + k * 6f * dp, horizon, cx + k * w * 0.14f, h, o.lumiere(avecAlpha(couleur, 0.35f * app * a), 1.4f * dp))
        }
        for (k in 0 until 14) {                                      // lignes qui défilent vers nous
            val z = modulo(k / 14f + sec * 0.7f, 1f)
            val y = horizon + (h - horizon) * z * z
            c.drawLine(0f, y, w, y, o.lumiere(avecAlpha(couleur, 0.45f * z * app * a), 1.4f * dp))
        }
        if (symboles) {
            val m = min(w, h)
            for (k in 0 until 4) {
                val x = cx + (k - 1.5f) * m * 0.16f
                val y = horizon - m * 0.2f + sin(sec * 3f + k) * 8f * dp
                val s = m * 0.04f * app
                val p = o.lumiere(avecAlpha(COULEURS_G[k], 0.9f * a), 3f * dp)
                when (k) {
                    0 -> { c.drawLine(x - s, y - s, x + s, y + s, p); c.drawLine(x - s, y + s, x + s, y - s, p) }
                    1 -> c.drawCircle(x, y, s, p)
                    2 -> {
                        val tri = Path()
                        tri.moveTo(x, y - s); tri.lineTo(x + s, y + s * 0.8f); tri.lineTo(x - s, y + s * 0.8f); tri.close()
                        c.drawPath(tri, p)
                    }
                    else -> c.drawRect(x - s, y - s, x + s, y + s, p)
                }
                o.halo(c, x, y, s * 2.5f, 0.2f * a, COULEURS_G[k])
            }
        }
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, horizon, rev, couleur)
    }
}

// ===================== GOOGLE TV : « Salon » =====================
// Un salon dans le noir, éclairé seulement par l'écran de la télé dont les couleurs changent.
class Salon : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f; val cy = h * 0.42f
        c.drawColor(rgb(5, 5, 7))
        val app = ralentit(intro)
        val a = 1f - rev
        val tw = min(w * 0.7f, h * 0.9f) * (0.85f + 0.15f * app); val th = tw * 0.56f
        val k = accelere(rev)
        val ecran = RectF(mix(cx - tw / 2f, -2f, k), mix(cy - th / 2f, -2f, k), mix(cx + tw / 2f, w + 2f, k), mix(cy + th / 2f, h + 2f, k))
        val teinte = COULEURS_G[(sec / 0.35f).toInt() % 4]
        o.tache(c, cx, cy, tw * 0.9f, th, 0.16f * app * a, teinte)                 // lumière sur le mur
        o.tache(c, cx, h * 0.9f, tw * 0.9f, 18f * dp, 0.12f * app * a, teinte)      // et sur le sol
        c.drawRect(cx - tw * 0.55f, cy + th / 2f + 14f * dp, cx + tw * 0.55f, cy + th / 2f + 40f * dp, o.pinceau(argb(255f * a, 12, 12, 15)))
        c.drawRoundRect(RectF(cx - tw * 0.6f, h * 0.86f, cx + tw * 0.6f, h * 1.1f), 30f * dp, 30f * dp, o.pinceau(argb(255f * a, 3, 3, 4)))
        c.drawRect(ecran, o.pinceau(rgb(14, 15, 20)))
        val image = o.pinceau()
        image.shader = LinearGradient(ecran.left, ecran.top, ecran.right, ecran.bottom, avecAlpha(teinte, 0.55f), rgb(10, 12, 20), Shader.TileMode.CLAMP)
        c.drawRect(ecran, image)
        c.drawRect(ecran, o.pinceau(argb(200f * a, 30, 30, 36), 4f * dp))
        o.vignette(c, w, h, 0.6f)
        if (rev > 0f) gommeRect(c, o, ecran, ralentit(rev * 1.3f))
    }
}

// ===================== COFFRE (Authenticator, sécurité, crypto...) =====================
// Les anneaux d'un coffre-fort tournent chacun à son rythme ; le code apparaît au centre,
// puis les anneaux s'écartent et le coffre s'ouvre.
class Coffre(private val couleur: Int = BLEU_G) : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        degradeVertical(c, o, w, rgb(10, 12, 18), rgb(5, 6, 9), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        for (k in 0 until 4) {
            val r = m * (0.14f + 0.07f * k) * (1f + 2.5f * accelere(rev) * (k + 1) / 4f)
            val rot = sec * (if (k % 2 == 0) 60f else -45f) * (1f + k * 0.3f)
            c.drawCircle(cx, cy, r, o.pinceau(argb(255f * app * a, 40 + 8 * k, 44 + 8 * k, 56 + 8 * k), m * 0.045f))
            for (t in 0 until 36) {                                         // graduations
                val ang = Math.toRadians((rot + t * 10f).toDouble()).toFloat()
                val long = if (t % 3 != 0) 0.4f else 0.8f
                val r1 = r - m * 0.018f * long; val r2 = r + m * 0.018f * long
                c.drawLine(cx + cos(ang) * r1, cy + sin(ang) * r1, cx + cos(ang) * r2, cy + sin(ang) * r2,
                    o.pinceau(argb(150f * app * a, 120, 130, 160), 1.2f * dp))
            }
            val repere = Math.toRadians(rot.toDouble()).toFloat()
            o.halo(c, cx + cos(repere) * r, cy + sin(repere) * r, 6f * dp, 0.6f * app * a, couleur)
        }
        val rnd = Random(if (sec < 0.9f) (sec * 10f).toInt() else 99)      // le code défile puis se fige
        val code = "${rnd.nextInt(10)}${rnd.nextInt(10)}${rnd.nextInt(10)} ${rnd.nextInt(10)}${rnd.nextInt(10)}${rnd.nextInt(10)}"
        texte(c, code, cx, cy, m * 0.07f, avecAlpha(rgb(170, 200, 255), app * a))
        o.halo(c, cx, cy, m * 0.12f, 0.12f * app * a, couleur)
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, cx, cy, rev, couleur)
    }
}
