package com.will.animlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.LruCache
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// LES ANIMATIONS DE CATÉGORIE (17/11, demande de Will).
// Une app qui n'a pas sa propre animation, mais que le protocole de classement (Classement.kt) range dans une
// catégorie, reçoit l'animation de sa catégorie. Pour qu'elle reste PERSONNALISÉE, chaque animation utilise :
//  - l'ICÔNE de l'app, au centre ;
//  - la COULEUR principale de son icône (calculée ici), pour tout le motif.
// Le motif dépend de la catégorie : réseau de neurones (IA), bulles de conversation (Social), égaliseur (Musique),
// diaphragme (Photos), pixels (Jeux), courbe et pièces (Finance), itinéraire (Trajets), liste cochée (Travail),
// pouls (Santé), sacs (Shopping), engrenages (Réglages), titres qui défilent (Infos).
// Thème sombre, lumières douces : jamais de flash.

// L'APPARENCE D'UNE APP : son icône (en image) et sa couleur principale. Calculée une fois, gardée pour les
// 24 dernières apps (« LruCache » = une mémoire qui oublie les plus anciennes).
class Apparence(val icone: Bitmap?, val couleur: Int?) {
    companion object {
        private const val TAILLE = 192
        private val memoire = LruCache<String, Apparence>(24)

        fun de(c: Context, pkg: String): Apparence {
            memoire.get(pkg)?.let { return it }
            val a = try { calculer(c, pkg) } catch (_: Throwable) { Apparence(null, null) }
            memoire.put(pkg, a)
            return a
        }

        private fun calculer(c: Context, pkg: String): Apparence {
            val d = c.packageManager.getApplicationIcon(pkg)
            val b = Bitmap.createBitmap(TAILLE, TAILLE, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, TAILLE, TAILLE)
            d.draw(Canvas(b))
            return Apparence(b, couleurDe(b))
        }

        // LA COULEUR PRINCIPALE d'une icône. On la réduit à 24 × 24 points, on range les points colorés par teinte
        // (36 cases de 10°, comme une roue des couleurs), et on prend la teinte la plus présente. Les points gris,
        // noirs, blancs ou transparents ne comptent pas. Icône sans couleur : null (on prendra celle de la catégorie).
        fun couleurDe(b: Bitmap): Int? {
            val n = 24
            val p = Bitmap.createScaledBitmap(b, n, n, true)
            val cases = DoubleArray(36)
            val saturation = DoubleArray(36)
            val hsv = FloatArray(3)
            for (y in 0 until n) for (x in 0 until n) {
                val px = p.getPixel(x, y)
                if (Color.alpha(px) < 128) continue
                Color.colorToHSV(px, hsv)
                val poids = (hsv[1] * hsv[2]).toDouble()      // vif ET lumineux = compte beaucoup
                if (poids < 0.2) continue
                val k = (hsv[0] / 10f).toInt().coerceIn(0, 35)
                cases[k] += poids
                saturation[k] += hsv[1] * poids
            }
            if (p !== b) p.recycle()
            // la case la plus remplie (avec ses deux voisines, pour ne pas couper une couleur en deux)
            var meilleure = -1
            var score = 0.0
            for (k in 0 until 36) {
                val s = cases[(k + 35) % 36] + cases[k] + cases[(k + 1) % 36]
                if (s > score) { score = s; meilleure = k }
            }
            if (meilleure < 0 || score < 4.0) return null
            var somme = 0.0
            var poidsTotal = 0.0
            var sat = 0.0
            for (d in -1..1) {
                val k = (meilleure + d + 36) % 36
                somme += ((meilleure + d) * 10.0 + 5.0) * cases[k]
                poidsTotal += cases[k]
                sat += saturation[k]
            }
            val teinte = (((somme / poidsTotal) % 360.0 + 360.0) % 360.0).toFloat()
            val s = (sat / poidsTotal).toFloat().coerceIn(0.45f, 0.8f)
            return Color.HSVToColor(floatArrayOf(teinte, s, 0.95f))
        }
    }
}

// UNE ANIMATION DE CATÉGORIE. « id » = la catégorie (voir Groupes.kt), « apparence » = l'icône et la couleur de l'app.
class AnimCategorie(private val id: String, apparence: Apparence) : Scene() {

    companion object {
        // les catégories qui ont un motif
        val IDS = setOf("ia", "social", "musique", "photo", "jeux", "finance", "trajets", "travail", "sante",
            "shopping", "reglages", "infos")

        // la couleur de la catégorie, quand l'icône de l'app n'en a pas (icône noire et blanche)
        fun couleurDe(id: String): Int = when (id) {
            "ia" -> rgb(167, 139, 250)
            "social" -> rgb(96, 165, 250)
            "musique" -> rgb(244, 114, 182)
            "photo" -> rgb(251, 191, 36)
            "jeux" -> rgb(74, 222, 128)
            "finance" -> rgb(250, 204, 21)
            "trajets" -> rgb(45, 212, 191)
            "travail" -> rgb(125, 211, 252)
            "sante" -> rgb(248, 113, 113)
            "shopping" -> rgb(251, 146, 60)
            "reglages" -> rgb(148, 163, 184)
            else -> rgb(165, 180, 252)
        }
    }

    private val icone = apparence.icone
    private val couleur = apparence.couleur ?: couleurDe(id)
    private val graines = nuage(id.hashCode(), 24)        // des valeurs « au hasard », toujours les mêmes
    private val chemin = Path()
    private val cadre = RectF()
    private val pinceauIcone = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val PI_F = PI.toFloat()

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        val m = min(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val a = ralentit(i.intro)                      // l'apparition (0 → 1)
        val s = i.secondes                             // le temps : le motif bouge tant que l'app charge
        val f = a * (1f - ralentit(i.revelation))      // la force du motif (il s'efface pendant l'ouverture)
        // 1) le voile sombre, puis une lueur douce de la couleur de l'app
        c.drawColor(avecAlpha(rgb(8, 9, 13), a))
        o.halo(c, cx, cy, m * (0.30f + 0.04f * sin(s * 3f)), 0.15f * f, couleur)
        // 2) le motif de la catégorie
        if (f > 0.01f) when (id) {
            "ia" -> neurones(c, o, cx, cy, m, s, f)
            "social" -> bulles(c, o, cx, cy, m, s, f)
            "musique" -> egaliseur(c, o, cx, cy, m, s, f)
            "photo" -> diaphragme(c, o, cx, cy, m, s, f)
            "jeux" -> pixels(c, o, cx, cy, m, s, f)
            "finance" -> courbe(c, o, cx, cy, m, s, f)
            "trajets" -> itineraire(c, o, cx, cy, m, s, f)
            "travail" -> taches(c, o, cx, cy, m, s, f)
            "sante" -> pouls(c, o, cx, cy, m, s, f)
            "shopping" -> sacs(c, o, cx, cy, m, s, f)
            "reglages" -> engrenages(c, o, cx, cy, m, s, f)
            else -> titres(c, o, cx, cy, m, s, f)
        }
        // 3) l'icône de l'app, au centre
        dessinerIcone(c, o, cx, cy, m, a, i.revelation, s)
        // 4) l'ouverture : un fondu rond depuis le centre
        o.revelationDouce(c, cx, cy, i.revelation, couleur)
    }

    // L'icône grandit en arrivant, respire un peu, puis grossit et s'efface pendant l'ouverture.
    private fun dessinerIcone(c: Canvas, o: Outils, cx: Float, cy: Float, m: Float, a: Float, rev: Float, s: Float) {
        val alpha = a * (1f - rev)
        if (alpha <= 0.01f) return
        val taille = m * 0.19f * mix(0.75f, 1f, a) * (1f + 0.35f * doux(rev)) * (1f + 0.015f * sin(s * 4f))
        val b = icone
        if (b == null) {
            c.drawCircle(cx, cy, taille * 0.45f, o.pinceau(avecAlpha(couleur, 0.85f * alpha)))   // pas d'icône : un rond
            return
        }
        cadre.set(cx - taille / 2f, cy - taille / 2f, cx + taille / 2f, cy + taille / 2f)
        pinceauIcone.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        c.drawBitmap(b, null, cadre, pinceauIcone)
    }

    // ------------------------------ IA : un réseau de neurones ------------------------------
    // 8 nœuds tournent autour de l'icône, reliés entre eux et vers l'icône ; des impulsions filent vers le centre.
    private fun neurones(c: Canvas, o: Outils, cx: Float, cy: Float, m: Float, s: Float, f: Float) {
        val n = 8
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        for (k in 0 until n) {
            val an = s * 0.45f + k * 2f * PI_F / n
            val r = m * (0.25f + 0.04f * sin(s * 1.8f + k * 1.7f))
            xs[k] = cx + cos(an) * r
            ys[k] = cy + sin(an) * r
        }
        val trait = o.lumiere(avecAlpha(couleur, 0.28f * f), 1.2f * o.dp)
        val bord = m * 0.12f
        for (k in 0 until n) {
            val j = (k + 1) % n
            c.drawLine(xs[k], ys[k], xs[j], ys[j], trait)
            val d = hypot(xs[k] - cx, ys[k] - cy)
            val ex = cx + (xs[k] - cx) / d * bord
            val ey = cy + (ys[k] - cy) / d * bord
            c.drawLine(xs[k], ys[k], ex, ey, trait)
            val t = modulo(s * 1.1f + k * 0.37f, 1f)
            c.drawCircle(mix(xs[k], ex, t), mix(ys[k], ey, t), 2.2f * o.dp,
                o.lumiere(avecAlpha(couleur, 0.7f * f * sin(PI_F * t))))
        }
        val noeud = o.lumiere(avecAlpha(couleur, 0.75f * f))
        for (k in 0 until n) {
            o.halo(c, xs[k], ys[k], 9f * o.dp, 0.25f * f, couleur)
            c.drawCircle(xs[k], ys[k], 2.8f * o.dp, noeud)
        }
    }

    // ------------------------------ SOCIAL : des bulles de conversation ------------------------------
    private fun bulles(c: Canvas, o: Outils, cx: Float, cy: Float, m: Float, s: Float, f: Float) {
        // deux ondes qui s'éloignent de l'icône
        for (k in 0..1) {
            val t = modulo(s * 0.8f + k * 0.5f, 1f)
            c.drawCircle(cx, cy, m * (0.13f + 0.24f * t), o.lumiere(avecAlpha(couleur, 0.35f * f * (1f - t)), 1.5f * o.dp))
        }
        // quatre bulles qui montent en arc au-dessus de l'icône, avec les trois points « en train d'écrire »
        for (k in 0 until 4) {
            val t = modulo(s * 0.45f + k * 0.25f, 1f)
            val al = sin(PI_F * t) * 0.6f * f
            if (al < 0.01f) continue
            val an = -PI_F / 2f + (k - 1.5f) * 0.75f
            val r = m * (0.2f + 0.12f * t)
            val x = cx + cos(an) * r
            val y = cy + sin(an) * r - m * 0.06f * t
            val bw = m * 0.075f
            val bh = m * 0.05f
            val gauche = k % 2 == 0
            val p = o.lumiere(avecAlpha(couleur, al), 1.6f * o.dp)
            cadre.set(x - bw / 2f, y - bh / 2f, x + bw / 2f, y + bh / 2f)
            c.drawRoundRect(cadre, bh * 0.45f, bh * 0.45f, p)
            // la petite pointe de la bulle
            val qx = if (gauche) cadre.left + bw * 0.25f else cadre.right - bw * 0.25f
            chemin.reset()
            chemin.moveTo(qx - bw * 0.08f, cadre.bottom)
            chemin.lineTo(qx + (if (gauche) -bw * 0.12f else bw * 0.12f), cadre.bottom + bh * 0.35f)
            chemin.lineTo(qx + bw * 0.08f, cadre.bottom)
            c.drawPath(chemin, p)
            val pt = o.lumiere(avecAlpha(couleur, al))
            for (d in -1..1) c.drawCircle(x + d * bw * 0.22f, y, bh * 0.09f * (1f + 0.4f * sin(s * 8f + d)), pt)
        }
    }

    // ------------------------------ MUSIQUE : un égaliseur en cercle ------------------------------
    private fun egaliseur(c: Canvas, o: Outils, cx: Float, cy: Float, m: Float, s: Float, f: Float) {
        val n = 40
        val r0 = m * 0.15f
        val p = o.lumiere(avecAlpha(couleur, 0.5f * f), 3f * o.dp)
        for (k in 0 until n) {
            val an = k * 2f * PI_F / n + s * 0.25f
            val v = abs(sin(s * 6.5f + k * 0.9f)) * (0.55f + 0.45f * sin(s * 2.3f + k * 0.41f))
            val l = m * (0.015f + 0.09f * v)
            val ca = cos(an)
            val sa = sin(an)
            c.drawLine(cx + ca * r0, cy + sa * r0, cx + ca * (r0 + l), cy + sa * (r0 + l), p)
        }
    }

    // ------------------------------ PHOTOS : un diaphragme et le viseur ------------------------------
    private fun diaphragme(c: Canvas, o: Outils, cx: Float, cy: Float, m: Float, s: Float, f: Float) {
        val grand = m * 0.25f
        val ri = grand * (0.5f + 0.2f * sin(s * 2.2f))       // l'ouverture qui s'ouvre et se ferme
        val p = o.lumiere(avecAlpha(couleur, 0.45f * f), 1.6f * o.dp)
        c.drawCircle(cx, cy, grand, p)
        // 6 lamelles : chacune est une ligne qui touche le cercle de l'ouverture et va jusqu'au bord
        val longueur = sqrt(grand * grand - ri * ri)
        for (k in 0 until 6) {
            val phi = s * 0.35f + k * PI_F / 3f
            val tx = cx + ri * cos(phi)
            val ty = cy + ri * sin(phi)
            c.drawLine(tx, ty, tx - sin(phi) * longueur, ty + cos(phi) * longueur, p)
        }
        // les quatre coins du viseur
        val b = m * 0.36f
        val l = m * 0.05f
        val pc = o.lumiere(avecAlpha(couleur, 0.4f * f), 2f * o.dp)
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
            val x = cx + sx * b
            val y = cy + sy * b
            c.drawLine(x, y, x - sx * l, y, pc)
            c.drawLine(x, y, x, y - sy * l, pc)
        }
    }

    // ------------------------------ JEUX : un anneau de pixels ------------------------------
    // Une vague tourne autour de l'icône et allume les pixels, qui clignotent chacun à leur rythme.
    private fun pixels(c: Canvas, o: Outils, cx: Float, cy: Float, m: Float, s: Float, f: Float) {
        val g = m * 0.034f
        val p = o.lumiere()
        for (gx in -10..10) for (gy in -10..10) {
            val d = hypot(gx.toFloat(), gy.toFloat()) * g
            if (d < m * 0.16f || d > m * 0.34f) continue
            val ph = ((gx * 73 + gy * 151) and 0xff) / 255f * 6.28f
            val an = atan2(gy.toFloat(), gx.toFloat())
            val vague = 0.5f + 0.5f * cos(an - s * 2.5f)
            val clin = 0.5f + 0.5f * sin(s * 5f + ph)
            val loin = 1f - (d - m * 0.16f) / (m * 0.18f) * 0.5f
            val al = f * 0.4f * vague * vague * clin * loin
            if (al < 0.02f) continue
            p.color = avecAlpha(couleur, al)
            val x = cx + gx * g
            val y = cy + gy * g
            c.drawRect(x - g * 0.4f, y - g * 0.4f, x + g * 0.4f, y + g * 0.4f, p)
        }
    }

    // ------------------------------ FINANCE : une courbe qui monte, et des pièces ------------------------------
    private fun courbe(c: Canvas, o: Outils, cx: Float, cy: Float, m: Float, s: Float, f: Float) {
        val x0 = cx - m * 0.45f
        val x1 = cx + m * 0.45f
        // le quadrillage discret
        val grille = o.pinceau(avecAlpha(couleur, 0.08f * f), 1f * o.dp)
        for (k in -2..2) c.drawLine(x0, cy + k * m * 0.09f, x1, cy + k * m * 0.09f, grille)
        // la courbe se trace en un peu plus d'une seconde, de gauche à droite
        val n = 48
        val fin = (n * borne(s * 0.9f)).toInt()
        chemin.reset()
        var hx = x0
        var hy = cy
        for (k in 0..fin) {
            val u = k / n.toFloat()
            val x = mix(x0, x1, u)
            val y = cy + m * 0.16f - u * m * 0.3f + m * 0.04f * sin(u * 19f) + m * 0.025f * sin(u * 41f)
            if (k == 0) chemin.moveTo(x, y) else chemin.lineTo(x, y)
            hx = x
            hy = y
        }
        if (fin > 0) {
            c.drawPath(chemin, o.lumiere(avecAlpha(couleur, 0.55f * f), 2.4f * o.dp))
            o.halo(c, hx, hy, 14f * o.dp, 0.45f * f, couleur)
        }
        // cinq pièces qui montent
        for (k in 0 until 5) {
            val t = modulo(s * 0.55f + k * 0.2f, 1f)
            val al = sin(PI_F * t) * 0.45f * f
            if (al < 0.01f) continue
            val x = cx + (k - 2) * m * 0.13f
            val y = cy + m * 0.32f - t * m * 0.55f
            val r = m * 0.022f
            val pp = o.lumiere(avecAlpha(couleur, al), 1.4f * o.dp)
            c.drawCircle(x, y, r, pp)
            c.drawCircle(x, y, r * 0.55f, pp)
        }
    }

    // ------------------------------ TRAJETS : deux itinéraires en pointillés ------------------------------
    private fun itineraire(c: Canvas, o: Outils, cx: Float, cy: Float, m: Float, s: Float, f: Float) {
        val q = borne(s * 1.1f)                       // les routes se tracent jusqu'à l'icône
        val tirets = DashPathEffect(floatArrayOf(9f * o.dp, 7f * o.dp), -s * 40f * o.dp)
        for (cote in 0..1) {
            chemin.reset()
            if (cote == 0) {
                chemin.moveTo(cx - m * 0.48f, cy + m * 0.55f)
                chemin.cubicTo(cx - m * 0.1f, cy + m * 0.5f, cx - m * 0.4f, cy + m * 0.15f, cx, cy + m * 0.13f)
            } else {
                chemin.moveTo(cx + m * 0.48f, cy - m * 0.55f)
                chemin.cubicTo(cx + m * 0.1f, cy - m * 0.5f, cx + m * 0.4f, cy - m * 0.15f, cx, cy - m * 0.13f)
            }
            val mesure = PathMeasure(chemin, false)
            val morceau = Path()
            mesure.getSegment(0f, mesure.length * q, morceau, true)
            val p = o.lumiere(avecAlpha(couleur, 0.5f * f), 2.6f * o.dp)
            p.pathEffect = tirets
            c.drawPath(morceau, p)
        }
        // le repère qui pulse sous l'icône (des ellipses à plat, comme posées au sol)
        for (k in 0..1) {
            val t = modulo(s * 0.9f + k * 0.5f, 1f)
            val r = m * (0.08f + 0.16f * t)
            cadre.set(cx - r, cy + m * 0.13f - r * 0.32f, cx + r, cy + m * 0.13f + r * 0.32f)
            c.drawOval(cadre, o.lumiere(avecAlpha(couleur, 0.4f * f * (1f - t)), 1.5f * o.dp))
        }
    }

    // ------------------------------ TRAVAIL : graduations et liste de tâches ------------------------------
    private fun taches(c: Canvas, o: Outils, cx: Float, cy: Float, m: Float, s: Float, f: Float) {
        // un anneau de graduations, comme un agenda ou une horloge
        val pg = o.lumiere(avecAlpha(couleur, 0.22f * f), 1.5f * o.dp)
        for (k in 0 until 24) {
            val an = k * PI_F / 12f + s * 0.15f
            val r0 = m * 0.22f
            val r1 = m * (if (k % 6 == 0) 0.26f else 0.24f)
            c.drawLine(cx + cos(an) * r0, cy + sin(an) * r0, cx + cos(an) * r1, cy + sin(an) * r1, pg)
        }
        // trois tâches sous l'icône, qui se cochent l'une après l'autre
        val cycle = modulo(s * 1.6f, 4.5f)
        val boite = m * 0.034f
        val x0 = cx - m * 0.15f
        for (k in 0 until 3) {
            val y = cy + m * (0.33f + 0.065f * k)
            val fait = borne(cycle - k - 0.2f)
            val pb = o.lumiere(avecAlpha(couleur, (0.3f + 0.3f * fait) * f), 1.5f * o.dp)
            cadre.set(x0, y - boite / 2f, x0 + boite, y + boite / 2f)
            c.drawRoundRect(cadre, boite * 0.2f, boite * 0.2f, pb)
            val lx = x0 + boite * 1.8f
            val longueur = m * (0.22f - 0.04f * (k % 2))
            c.drawLine(lx, y, lx + longueur, y, o.lumiere(avecAlpha(couleur, (0.18f + 0.22f * fait) * f), 2.4f * o.dp))
            if (fait > 0f) {
                // la coche, en deux traits
                val e1 = borne(fait * 2f)
                val e2 = borne(fait * 2f - 1f)
                val ax = x0 + boite * 0.2f
                val ay = y
                val bx = x0 + boite * 0.42f
                val by = y + boite * 0.25f
                val dx = x0 + boite * 0.85f
                val dy = y - boite * 0.35f
                val pk = o.lumiere(avecAlpha(couleur, 0.75f * f), 2f * o.dp)
                c.drawLine(ax, ay, mix(ax, bx, e1), mix(ay, by, e1), pk)
                if (e2 > 0f) c.drawLine(bx, by, mix(bx, dx, e2), mix(by, dy, e2), pk)
            }
        }
    }

    // ------------------------------ SANTÉ : un électrocardiogramme ------------------------------
    // La hauteur du tracé pendant une pulsation (u de 0 à 1) : petite bosse, grand pic, puis une bosse plus large.
    private fun ecg(u: Float): Float = when {
        u < 0.12f -> 0f
        u < 0.22f -> 0.12f * sin((u - 0.12f) / 0.10f * PI_F)
        u < 0.30f -> 0f
        u < 0.33f -> -0.15f * (u - 0.30f) / 0.03f
        u < 0.37f -> mix(-0.15f, 1f, (u - 0.33f) / 0.04f)
        u < 0.41f -> mix(1f, -0.35f, (u - 0.37f) / 0.04f)
        u < 0.45f -> mix(-0.35f, 0f, (u - 0.41f) / 0.04f)
        u < 0.60f -> 0f
        u < 0.78f -> 0.22f * sin((u - 0.60f) / 0.18f * PI_F)
        else -> 0f
    }

    private fun pouls(c: Canvas, o: Outils, cx: Float, cy: Float, m: Float, s: Float, f: Float) {
        val periode = m * 0.7f
        val decalage = s * m * 0.55f                 // le tracé défile vers la gauche
        val amp = m * 0.13f
        val pas = 3f * o.dp
        val w = c.width.toFloat()
        chemin.reset()
        var x = 0f
        while (x <= w) {
            val y = cy - amp * ecg(modulo((x + decalage) / periode, 1f))
            if (x == 0f) chemin.moveTo(x, y) else chemin.lineTo(x, y)
            x += pas
        }
        c.drawPath(chemin, o.lumiere(avecAlpha(couleur, 0.5f * f), 2.2f * o.dp))
        // un anneau qui bat autour de l'icône, au moment où le pic passe derrière elle
        val t = modulo((cx + decalage) / periode - 0.37f, 1f)
        c.drawCircle(cx, cy, m * (0.11f + 0.12f * t), o.lumiere(avecAlpha(couleur, 0.4f * f * (1f - t)), 2f * o.dp))
    }

    // ------------------------------ SHOPPING : des sacs qui tournent, et des étincelles ------------------------------
    private fun sacs(c: Canvas, o: Outils, cx: Float, cy: Float, m: Float, s: Float, f: Float) {
        for (k in 0 until 3) {
            val an = s * 0.9f + k * 2f * PI_F / 3f
            val devant = 0.5f + 0.5f * sin(an)           // 1 = le sac passe devant, 0 = derrière
            val x = cx + cos(an) * m * 0.27f
            val y = cy + sin(an) * m * 0.12f
            val sc = 0.75f + 0.3f * devant
            val bw = m * 0.065f * sc
            val bh = m * 0.07f * sc
            val p = o.lumiere(avecAlpha(couleur, (0.25f + 0.35f * devant) * f), 1.7f * o.dp)
            cadre.set(x - bw / 2f, y - bh * 0.35f, x + bw / 2f, y + bh * 0.65f)
            c.drawRoundRect(cadre, bw * 0.12f, bw * 0.12f, p)
            cadre.set(x - bw * 0.25f, y - bh * 0.75f, x + bw * 0.25f, y + bh * 0.05f)
            c.drawArc(cadre, 180f, 180f, false, p)       // l'anse
        }
        val pe = o.lumiere(couleur, 1.4f * o.dp)
        for (k in 0 until 7) {
            val g = graines[k]
            val al = f * 0.5f * borne(sin(s * 4f + g[3]))
            if (al < 0.02f) continue
            val an = g[0] * 6.28f
            val r = m * (0.2f + 0.18f * g[1])
            val x = cx + cos(an) * r
            val y = cy + sin(an) * r
            val l = m * 0.018f * (0.6f + g[2])
            pe.color = avecAlpha(couleur, al)
            c.drawLine(x - l, y, x + l, y, pe)
            c.drawLine(x, y - l, x, y + l, pe)
        }
    }

    // ------------------------------ RÉGLAGES : deux engrenages ------------------------------
    private fun roue(c: Canvas, x: Float, y: Float, r1: Float, r0: Float, dents: Int, angle: Float, p: Paint) {
        chemin.reset()
        val pas = 2f * PI_F / dents
        for (k in 0 until dents) {
            val a0 = angle + k * pas
            val px = x + cos(a0) * r0
            val py = y + sin(a0) * r0
            if (k == 0) chemin.moveTo(px, py) else chemin.lineTo(px, py)
            val a1 = a0 + pas * 0.2f
            chemin.lineTo(x + cos(a1) * r1, y + sin(a1) * r1)
            val a2 = a0 + pas * 0.5f
            chemin.lineTo(x + cos(a2) * r1, y + sin(a2) * r1)
            val a3 = a0 + pas * 0.7f
            chemin.lineTo(x + cos(a3) * r0, y + sin(a3) * r0)
        }
        chemin.close()
        c.drawPath(chemin, p)
        c.drawCircle(x, y, r0 * 0.55f, p)
    }

    private fun engrenages(c: Canvas, o: Outils, cx: Float, cy: Float, m: Float, s: Float, f: Float) {
        val p = o.lumiere(avecAlpha(couleur, 0.42f * f), 1.8f * o.dp)
        roue(c, cx, cy, m * 0.28f, m * 0.25f, 16, s * 0.35f, p)
        // le petit engrenage tourne dans l'autre sens, plus vite (il a moins de dents)
        roue(c, cx + m * 0.26f, cy - m * 0.26f, m * 0.1f, m * 0.085f, 9, -s * 0.35f * 16f / 9f + 0.2f, p)
    }

    // ------------------------------ INFOS : des titres qui défilent ------------------------------
    private fun titres(c: Canvas, o: Outils, cx: Float, cy: Float, m: Float, s: Float, f: Float) {
        val ecart = m * 0.055f
        val avance = s * m * 0.1f
        val decal = modulo(avance, ecart)
        val haut = m * 0.42f
        val tour = (avance / ecart).toInt()          // pour que chaque ligne garde sa longueur en défilant
        val p = o.lumiere(couleur, 2.2f * o.dp)
        val x0 = cx - m * 0.36f
        for (j in 0..((2f * haut / ecart).toInt() + 1)) {
            val y = cy - haut + j * ecart - decal
            val dist = abs(y - cy) / haut
            if (dist > 1f) continue
            val rang = j + tour
            val g = graines[rang % graines.size]
            val titre = rang % 5 == 0
            p.strokeWidth = (if (titre) 4f else 2.2f) * o.dp
            p.color = avecAlpha(couleur, f * (if (titre) 0.4f else 0.22f) * (1f - dist * dist))
            c.drawLine(x0, y, x0 + m * (0.35f + 0.35f * g[0]), y, p)
        }
        // le point « en direct », en haut à droite de l'icône
        val al = f * (0.45f + 0.3f * sin(s * 5f))
        o.halo(c, cx + m * 0.12f, cy - m * 0.12f, 10f * o.dp, al * 0.6f, couleur)
        c.drawCircle(cx + m * 0.12f, cy - m * 0.12f, 3.2f * o.dp, o.lumiere(avecAlpha(couleur, al)))
    }
}
