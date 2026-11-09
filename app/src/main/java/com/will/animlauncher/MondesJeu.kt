package com.will.animlauncher

import android.graphics.BlurMaskFilter
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
import android.graphics.Typeface
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// LE « PEINTRE » (09/11) : une petite boîte à outils pour dessiner les images du widget « top 3 » du dossier
// Games (voir WidgetTop.kt). Ces dessins ont d'abord été mis au point dans les aperçus Python
// (/home/claude/apercu/scenes_jeu.py, widget_intros2.py) : pour les recopier ici sans erreur, tous les
// calculs se font en nombres « Double » (comme en Python), et chaque outil les convertit pour Android.
// g = le « gris » : 1 = image toute grise (l'écran vient de s'allumer), 0 = pleines couleurs.
open class Peintre(val cv: Canvas, val g: Double = 0.0) {

    private val pinceau = Paint()

    private fun prep(couleur: Int, degrade: Shader? = null, flou: Double = 0.0, add: Boolean = false): Paint {
        pinceau.reset()
        pinceau.isAntiAlias = true
        pinceau.color = couleur
        if (degrade != null) { pinceau.color = Color.WHITE; pinceau.shader = degrade }
        // (dans les aperçus, le flou est donné en « sigma » ; Android veut un rayon, environ 1,73 fois plus grand)
        if (flou > 0) pinceau.maskFilter = BlurMaskFilter(max(0.1, flou * 1.73).toFloat(), BlurMaskFilter.Blur.NORMAL)
        if (add) pinceau.xfermode = ADDITION
        return pinceau
    }

    // UNE COULEUR (rouge, vert, bleu, opacité de 0 à 255), passée par le filtre « gris ».
    fun c(r: Int, v: Int, b: Int, a: Double = 255.0): Int {
        val l = 0.3 * r + 0.59 * v + 0.11 * b
        return Color.argb(a.toInt().coerceIn(0, 255), (r + (l - r) * g).toInt().coerceIn(0, 255),
            (v + (l - v) * g).toInt().coerceIn(0, 255), (b + (l - b) * g).toInt().coerceIn(0, 255))
    }
    fun c(r: Int, v: Int, b: Int, a: Int): Int = c(r, v, b, a.toDouble())
    fun c(couleur: Int, a: Double = 255.0): Int = c(Color.red(couleur), Color.green(couleur), Color.blue(couleur), a)
    fun c(couleur: Int, a: Int): Int = c(couleur, a.toDouble())
    // La même couleur, plus sombre (f < 1) ou plus claire (f > 1).
    fun ton(couleur: Int, f: Double): Int = Color.rgb(min(255.0, Color.red(couleur) * f).toInt(),
        min(255.0, Color.green(couleur) * f).toInt(), min(255.0, Color.blue(couleur) * f).toInt())

    // LES DÉGRADÉS : en ligne (d'un point à un autre) ou en rond (autour d'un centre).
    fun pos(vararg v: Double): FloatArray = FloatArray(v.size) { v[it].toFloat() }
    fun lin(x0: Double, y0: Double, x1: Double, y1: Double, couleurs: IntArray, places: FloatArray? = null): Shader =
        LinearGradient(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat(), couleurs, places, Shader.TileMode.CLAMP)
    fun rad(x: Double, y: Double, r: Double, couleurs: IntArray, places: FloatArray? = null): Shader =
        RadialGradient(x.toFloat(), y.toFloat(), max(1.0, r).toFloat(), couleurs, places, Shader.TileMode.CLAMP)

    // LES FORMES
    fun cercle(x: Double, y: Double, r: Double, couleur: Int, flou: Double = 0.0, add: Boolean = false, degrade: Shader? = null) =
        cv.drawCircle(x.toFloat(), y.toFloat(), max(0.0, r).toFloat(), prep(couleur, degrade, flou, add))
    fun rect(l: Double, t: Double, r: Double, b: Double, couleur: Int, degrade: Shader? = null, flou: Double = 0.0, add: Boolean = false) =
        cv.drawRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), prep(couleur, degrade, flou, add))
    fun rrect(l: Double, t: Double, r: Double, b: Double, rayon: Double, couleur: Int, degrade: Shader? = null, flou: Double = 0.0, add: Boolean = false) =
        cv.drawRoundRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), rayon.toFloat(), rayon.toFloat(), prep(couleur, degrade, flou, add))
    fun ovale(l: Double, t: Double, r: Double, b: Double, couleur: Int, flou: Double = 0.0, add: Boolean = false) =
        cv.drawOval(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), prep(couleur, null, flou, add))
    fun chemin(forme: Path, couleur: Int, degrade: Shader? = null, flou: Double = 0.0, add: Boolean = false) =
        cv.drawPath(forme, prep(couleur, degrade, flou, add))
    fun ligne(x0: Double, y0: Double, x1: Double, y1: Double, epaisseur: Double, couleur: Int, degrade: Shader? = null, flou: Double = 0.0, add: Boolean = false) {
        val p = prep(couleur, degrade, flou, add)
        p.strokeWidth = max(0.8, epaisseur).toFloat()
        p.strokeCap = Paint.Cap.ROUND
        cv.drawLine(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat(), p)
    }
    // le contour d'une forme (trait)
    fun trait(forme: Path, epaisseur: Double, couleur: Int) {
        val p = prep(couleur)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(0.8, epaisseur).toFloat()
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        cv.drawPath(forme, p)
    }
    // un arc de cercle (trait) : angles en degrés, 0 = à droite, dans le sens des aiguilles d'une montre
    fun arc(l: Double, t: Double, r: Double, b: Double, debut: Double, balayage: Double, epaisseur: Double, couleur: Int, flou: Double = 0.0, add: Boolean = false) {
        val p = prep(couleur, null, flou, add)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(0.8, epaisseur).toFloat()
        p.strokeCap = Paint.Cap.ROUND
        cv.drawArc(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), debut.toFloat(), balayage.toFloat(), false, p)
    }
    // une « part de tarte » pleine
    fun secteur(l: Double, t: Double, r: Double, b: Double, debut: Double, balayage: Double, couleur: Int) =
        cv.drawArc(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), debut.toFloat(), balayage.toFloat(), true, prep(couleur))
    // un texte en gras, centré sur x
    fun texte(mots: String, x: Double, y: Double, taille: Double, couleur: Int, gras: Boolean = true) {
        val p = prep(couleur)
        p.typeface = if (gras) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        p.textSize = taille.toFloat()
        cv.drawText(mots, x.toFloat() - p.measureText(mots) / 2f, y.toFloat(), p)
    }

    // LES TRACÉS : poly(x0, y0, x1, y1, …) = un polygone fermé ; m = aller à, l = ligne jusqu'à, cu = courbe.
    fun poly(vararg v: Double): Path {
        val p = Path()
        p.moveTo(v[0].toFloat(), v[1].toFloat())
        var i = 2
        while (i + 1 < v.size) { p.lineTo(v[i].toFloat(), v[i + 1].toFloat()); i += 2 }
        p.close()
        return p
    }
    fun Path.m(x: Double, y: Double) = moveTo(x.toFloat(), y.toFloat())
    fun Path.l(x: Double, y: Double) = lineTo(x.toFloat(), y.toFloat())
    fun Path.cu(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double) =
        cubicTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), x3.toFloat(), y3.toFloat())
    fun rrectForme(l: Double, t: Double, r: Double, b: Double, rayon: Double): Path = Path().apply {
        addRoundRect(RectF(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat()), rayon.toFloat(), rayon.toFloat(), Path.Direction.CW)
    }

    // LE CANEVAS : limiter le dessin à une zone, déplacer, tourner, agrandir.
    fun decoupe(l: Double, t: Double, r: Double, b: Double) { cv.clipRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat()) }
    fun deplace(x: Double, y: Double) = cv.translate(x.toFloat(), y.toFloat())
    fun tourne(degres: Double) = cv.rotate(degres.toFloat())
    fun agrandit(e: Double) = cv.scale(e.toFloat(), e.toFloat())

    // LES PETITS CALCULS
    fun lisse(x: Double): Double { val v = x.coerceIn(0.0, 1.0); return v * v * (3 - 2 * v) }
    fun seg(t: Double, a: Double, b: Double): Double = lisse((t - a) / (b - a))
    // le reste d'une division, toujours positif (pour faire défiler un décor en boucle)
    fun mod(a: Double, b: Double): Double = ((a % b) + b) % b
    fun Random.u(a: Double, b: Double): Double = a + nextDouble() * (b - a)

    // LE RELIEF : des « vagues » de plusieurs tailles additionnées donnent une ligne de crête naturelle.
    // crete = pointu (montagnes), ondule = doux (collines). Résultat entre 0 et 1.
    fun crete(x: Double, graine: Int): Double {
        val ph = phases(graine, 4)
        var v = 0.0; var total = 0.0; var a = 1.0; var f = 1.0
        for (o in 0 until 4) {
            v += a * (1 - abs(sin(x * 0.9 * f + ph[o]))).pow(1.6)
            total += a; a *= 0.55; f *= 2.0
        }
        return v / total
    }
    fun ondule(x: Double, graine: Int): Double {
        val ph = phases(graine + 1000, 3)
        return 0.5 + 0.5 * (0.6 * sin(x * 1.1 + ph[0]) + 0.3 * sin(x * 2.3 + ph[1]) + 0.1 * sin(x * 5.1 + ph[2]))
    }
    // la silhouette d'un relief sur toute la largeur (base et amp en fractions de la hauteur h)
    fun profil(gauche: Double, hautR: Double, w: Double, h: Double, base: Double, amp: Double, graine: Int, defile: Double,
               f0: Double = 1.0, douce: Boolean = false): Path {
        val p = Path()
        p.m(gauche, hautR + h + 2)
        val n = 60
        for (i in 0..n) {
            val x = gauche + w * i / n
            val u = (x - gauche + defile) / h * f0
            p.l(x, hautR + h * (base - amp * (if (douce) ondule(u, graine) else crete(u, graine))))
        }
        p.l(gauche + w, hautR + h + 2)
        p.close()
        return p
    }

    companion object {
        private val ADDITION = PorterDuffXfermode(PorterDuff.Mode.ADD)       // les lumières s'additionnent (néons)
        private val PHASES = HashMap<Int, DoubleArray>()
        private fun phases(graine: Int, n: Int): DoubleArray = synchronized(PHASES) {
            PHASES.getOrPut(graine * 10 + n) { val r = Random(graine.toLong()); DoubleArray(n) { r.nextDouble() * 6.283 } }
        }
    }
}

// LES 3 MONDES DE JEU qu'on voit dans le téléphone de l'intro du widget Games (décors inventés, sans marque) :
//   0 = monde ouvert coloré (un voyageur sur une falaise, une vallée, un château au loin, des îles flottantes)
//   1 = jeu d'horreur (forêt de nuit, maison abandonnée, lampe torche)
//   2 = course de nuit (ville néon sous la pluie)
// Chaque monde se dessine dans un rectangle quelconque : large (Flip, téléphone classique) ou presque carré (Fold).
// t = le temps en secondes (les nuages glissent, la cape bouge…). couleur = celle du jeu n°1 (cape, feux arrière).
class MondesJeu(cv: Canvas, g: Double) : Peintre(cv, g) {

    companion object {
        const val NOMBRE = 3
        fun dessiner(cv: Canvas, monde: Int, zone: RectF, couleur: Int, gris: Double, t: Double) {
            cv.save()
            cv.clipRect(zone)
            val gauche = zone.left.toDouble(); val haut = zone.top.toDouble()
            val w = zone.width().toDouble(); val h = zone.height().toDouble()
            when (monde) {
                0 -> MondesJeu(cv, gris).ouvert(gauche, haut, w, h, couleur, t)
                1 -> MondesJeu(cv, gris * 0.6).horreur(gauche, haut, w, h, t)      // (déjà presque sans couleur)
                else -> MondesJeu(cv, gris).course(gauche, haut, w, h, couleur, t)
            }
            cv.restore()
        }
    }

    // ============================== 1) LE MONDE OUVERT ==============================

    // un nuage en deux tons (dessous bleuté, dessus blanc), à fond plat
    private fun cumulus(x: Double, y: Double, s: Double, graine: Int, a: Double = 255.0) {
        val rnd = Random(graine.toLong())
        val bx = DoubleArray(8); val by = DoubleArray(8); val br = DoubleArray(8)
        for (i in 0 until 8) {
            val dx = rnd.u(-1.3, 1.3)
            br[i] = s * (0.62 - 0.3 * abs(dx) / 1.3) * rnd.u(0.8, 1.15)
            bx[i] = x + dx * s; by[i] = y - br[i] * 0.55
        }
        cv.save()
        decoupe(x - s * 3, y - s * 3, x + s * 3, y + s * 0.12)
        for (i in 0 until 8) cercle(bx[i], by[i], br[i], c(140, 170, 220, a))
        for (i in 0 until 8) cercle(bx[i] + br[i] * 0.1, by[i] - br[i] * 0.16, br[i] * 0.86, c(255, 252, 245, a))
        cv.restore()
    }

    // une chaîne de montagnes : dégradé du sommet vers le pied, neige en haut, versants à l'ombre
    private fun montagne(gauche: Double, haut: Double, w: Double, h: Double, base: Double, amp: Double, graine: Int, defile: Double,
                         sommet: Int, pied: Int, ombre: Int, f0: Double, neige: Boolean) {
        val p = profil(gauche, haut, w, h, base, amp, graine, defile, f0)
        chemin(p, 0, lin(0.0, haut + h * (base - amp), 0.0, haut + h * base, intArrayOf(sommet, pied)))
        cv.save()
        cv.clipPath(p)
        if (neige) rect(gauche, haut, gauche + w, haut + h * (base - amp * 0.4), 0, lin(0.0, haut + h * (base - amp), 0.0,
            haut + h * (base - amp * 0.4), intArrayOf(c(250, 252, 255, 245), c(250, 252, 255, 0))))
        val versant = Path(p)
        versant.offset((-h * 0.035).toFloat(), (h * 0.028).toFloat())
        chemin(versant, ombre)
        cv.restore()
    }

    // une île flottante : rocher en pointe, herbe dessus, arbres ronds ; en option une arche en ruine et une cascade
    private fun ile(x: Double, y: Double, s: Double, graine: Int, t: Double, cascade: Boolean = false, ruine: Boolean = false, a: Double = 255.0) {
        val rnd = Random(graine.toLong())
        val roche = Path()
        roche.m(x - s, y)
        for (i in 0..9) {
            val u = i / 9.0
            val prof = s * 1.5 * max(0.0, sin(PI * u)).pow(0.8) * rnd.u(0.75, 1.1) * (if (u > 0.3 && u < 0.6) 1.25 else 1.0)
            roche.l(x - s + 2 * s * u, y + (if (i == 0 || i == 9) 0.0 else prof))
        }
        roche.close()
        chemin(roche, 0, lin(x - s, y, x + s, y + s * 1.6, intArrayOf(c(205, 160, 130, a), c(120, 85, 110, a), c(70, 55, 95, a)), pos(0.0, 0.5, 1.0)))
        rrect(x - s * 1.06, y - s * 0.2, x + s * 1.06, y + s * 0.1, s * 0.14, 0,
            lin(0.0, y - s * 0.2, 0.0, y + s * 0.1, intArrayOf(c(185, 240, 120, a), c(70, 165, 95, a))))
        for (arbre in arrayOf(doubleArrayOf(-0.55, 1.0), doubleArrayOf(-0.15, 0.7), doubleArrayOf(0.6, 0.55))) {
            val tx = x + arbre[0] * s; val ty = y - s * 0.2; val k = arbre[1]
            rect(tx - s * 0.03, ty - s * 0.3 * k, tx + s * 0.03, ty, c(110, 75, 70, a))
            cercle(tx, ty - s * 0.5 * k, s * 0.36 * k, c(45, 135, 95, a))
            cercle(tx + s * 0.08 * k, ty - s * 0.6 * k, s * 0.24 * k, c(120, 210, 120, a))
        }
        if (ruine) {
            val ax = x + s * 0.15; val ay = y - s * 0.2
            rect(ax, ay - s * 0.75, ax + s * 0.12, ay, c(235, 225, 215, a))
            rect(ax + s * 0.42, ay - s * 0.75, ax + s * 0.54, ay, c(235, 225, 215, a))
            arc(ax + s * 0.02, ay - s * 1.0, ax + s * 0.52, ay - s * 0.5, 180.0, 150.0, s * 0.11, c(235, 225, 215, a))
        }
        if (cascade) {
            val cx = x + s * 0.72
            rect(cx - s * 0.07, y, cx + s * 0.07, y + s * 3.4, 0, lin(0.0, y, 0.0, y + s * 3.4, intArrayOf(c(235, 250, 255, a * 0.95), c(235, 250, 255, 0))))
            for (k in 0 until 4) cercle(cx, y + mod(t * s * 2.2 + k * s * 0.85, s * 3.2), s * 0.06, c(255, 255, 255, a * 0.85))
        }
    }

    private fun ouvert(gauche: Double, haut: Double, w: Double, h: Double, couleur: Int, t: Double) {
        val cam = t * h * 0.028                        // la caméra glisse doucement vers la droite
        val droite = gauche + w; val bas = haut + h
        // --- le ciel : azur profond, turquoise, puis or pâle à l'horizon ; le soleil et ses rayons très doux
        rect(gauche, haut, droite, bas, 0, lin(0.0, haut, 0.0, haut + h * 0.72,
            intArrayOf(c(22, 78, 190), c(52, 148, 232), c(140, 215, 240), c(255, 232, 190)), pos(0.0, 0.42, 0.78, 1.0)))
        val sx = gauche + w * 0.76; val sy = haut + h * 0.3
        cercle(sx, sy, h * 0.75, 0, degrade = rad(sx, sy, h * 0.75, intArrayOf(c(255, 244, 210, 150), c(255, 225, 180, 50), c(255, 225, 180, 0)), pos(0.0, 0.4, 1.0)))
        for (k in 0 until 5) {
            val ang = Math.toRadians(150 + k * 13 + sin(t * 0.4 + k) * 2)
            chemin(poly(sx, sy, sx + cos(ang) * w * 1.5, sy + sin(ang) * w * 1.5, sx + cos(ang + 0.06) * w * 1.5, sy + sin(ang + 0.06) * w * 1.5), c(255, 246, 220, 16))
        }
        cercle(sx, sy, h * 0.055, c(255, 252, 238, 235), flou = h * 0.008)
        // --- une mer de nuages à l'horizon
        for (i in 0 until 6) {
            val x = gauche + mod(i * w * 0.22 - cam * 0.12, w * 1.33) - w * 0.16
            cumulus(x, haut + h * (0.6 + 0.02 * (i % 3)), h * (0.06 + 0.015 * (i % 2)), 20 + i, 200.0)
        }
        // --- les montagnes enneigées, puis le piton rocheux et son château
        montagne(gauche, haut, w, h, 0.7, 0.62, 1, cam * 0.1, c(120, 150, 225), c(200, 215, 245), c(80, 105, 200, 120), 0.7, true)
        val px = gauche + w * 0.57 - cam * 0.16; val py = haut + h * 0.68
        chemin(poly(px - h * 0.1, py, px - h * 0.045, py - h * 0.22, px - h * 0.02, py - h * 0.3, px + h * 0.03, py - h * 0.29, px + h * 0.06, py - h * 0.2, px + h * 0.12, py),
            0, lin(px - h * 0.1, 0.0, px + h * 0.12, 0.0, intArrayOf(c(120, 140, 205), c(175, 190, 235))))
        for (tour in arrayOf(doubleArrayOf(-0.02, 0.13, 0.022), doubleArrayOf(0.012, 0.2, 0.028), doubleArrayOf(0.04, 0.11, 0.02))) {
            val bx = px + tour[0] * h; val by = py - h * 0.29; val th = tour[1] * h; val tw = tour[2] * h
            rect(bx - tw / 2, by - th, bx + tw / 2, by + h * 0.02, 0, lin(bx - tw, 0.0, bx + tw, 0.0, intArrayOf(c(150, 165, 220), c(235, 235, 250))))
            chemin(poly(bx - tw * 0.8, by - th, bx, by - th - tw * 2.6, bx + tw * 0.8, by - th), c(90, 110, 200))
        }
        rect(gauche, haut + h * 0.45, droite, haut + h * 0.72, 0, lin(0.0, haut + h * 0.45, 0.0, haut + h * 0.72,
            intArrayOf(c(235, 242, 255, 0), c(255, 236, 215, 120), c(235, 242, 255, 30)), pos(0.0, 0.7, 1.0)))       // la brume
        // --- les grands nuages et les îles flottantes
        cumulus(gauche + mod(w * 0.16 - cam * 0.2, w * 1.4) - w * 0.1, haut + h * 0.2, h * 0.1, 3)
        cumulus(gauche + mod(w * 0.95 - cam * 0.16, w * 1.4) - w * 0.1, haut + h * 0.13, h * 0.075, 5)
        ile(gauche + w * 0.6 - cam * 0.3 + h * 0.1, haut + h * 0.2 + sin(t * 0.7) * h * 0.006, h * 0.06, 2, t, a = 215.0)
        ile(gauche + w * 0.3 - cam * 0.42 + h * 0.2, haut + h * 0.33 + sin(t * 0.6 + 1) * h * 0.008, h * 0.115, 1, t, cascade = true, ruine = true)
        ile(gauche + w * 0.93 - cam * 0.36, haut + h * 0.46 + sin(t * 0.8 + 2) * h * 0.006, h * 0.045, 4, t, a = 230.0)
        // --- les montagnes vertes et les collines boisées
        montagne(gauche, haut, w, h, 0.74, 0.22, 2, cam * 0.3, c(60, 150, 150), c(130, 205, 175), c(35, 95, 135, 120), 1.5, false)
        chemin(profil(gauche, haut, w, h, 0.8, 0.09, 3, cam * 0.5, 1.3, true), 0, lin(0.0, haut + h * 0.7, 0.0, haut + h * 0.84, intArrayOf(c(150, 225, 115), c(70, 170, 100))))
        var rnd = Random(8)
        for (i in 0 until 22) {
            val x = gauche + mod(rnd.nextDouble() * 1.5 * w - cam * 0.5, w * 1.5) - w * 0.25
            val y = haut + h * (0.8 - 0.09 * ondule((x - gauche + cam * 0.5) / h * 1.3, 3)) + h * rnd.u(0.0, 0.03)
            val k = h * rnd.u(0.012, 0.022)
            cercle(x, y, k, c(40, 130, 90))
            cercle(x + k * 0.25, y - k * 0.3, k * 0.6, c(105, 200, 115))
        }
        // --- le lac : il reflète le ciel, avec la colonne de lumière du soleil et des éclats qui scintillent
        val lacH = haut + h * 0.83; val lacB = haut + h * 0.93
        rect(gauche, lacH, droite, lacB, 0, lin(0.0, lacH, 0.0, lacB, intArrayOf(c(170, 225, 240), c(60, 150, 220))))
        rect(sx - h * 0.09, lacH, sx + h * 0.09, lacB, 0, lin(sx - h * 0.09, 0.0, sx + h * 0.09, 0.0, intArrayOf(c(255, 245, 215, 0), c(255, 245, 215, 150), c(255, 245, 215, 0))))
        rnd = Random(3)
        for (k in 0 until 12) {
            val x = gauche + rnd.nextDouble() * w; val y = lacH + rnd.nextDouble() * (lacB - lacH)
            rect(x, y, x + h * rnd.u(0.03, 0.09), y + max(1.0, h * 0.005), c(255, 255, 255, 110 + 130 * sin(t * 3.5 + k * 1.7)))
        }
        // --- la prairie de l'autre rive et ses fleurs
        chemin(profil(gauche, haut, w, h, 0.95, 0.07, 5, cam * 0.8, 0.9, true), 0, lin(0.0, haut + h * 0.87, 0.0, bas, intArrayOf(c(165, 232, 105), c(60, 150, 80))))
        rnd = Random(9)
        val fleurs = intArrayOf(c(255, 232, 100), c(255, 150, 190), c(255, 255, 255), c(170, 210, 255))
        for (i in 0 until 28) {
            val x = gauche + mod(rnd.nextDouble() * w * 1.4 - cam * 0.8, w * 1.4) - w * 0.2
            cercle(x, haut + h * rnd.u(0.93, 1.0), h * 0.008, fleurs[rnd.nextInt(4)])
        }
        // --- LA FALAISE du premier plan (à gauche) et le voyageur qui regarde la vallée
        val cw = min(h * 0.85, w * 0.42); val fy = haut + h * 0.7
        val falaise = Path()
        falaise.m(gauche - 2, fy - h * 0.03)
        falaise.cu(gauche + cw * 0.4, fy - h * 0.05, gauche + cw * 0.8, fy - h * 0.01, gauche + cw, fy + h * 0.02)
        falaise.cu(gauche + cw * 0.93, fy + h * 0.1, gauche + cw * 0.82, fy + h * 0.16, gauche + cw * 0.86, bas + 2)
        falaise.l(gauche - 2, bas + 2)
        falaise.close()
        chemin(falaise, 0, lin(gauche, fy, gauche + cw, bas, intArrayOf(c(95, 80, 120), c(60, 50, 95), c(35, 30, 70)), pos(0.0, 0.5, 1.0)))
        cv.save()
        cv.clipPath(falaise)
        for (i in 0 until 6) {                         // les strates de la roche
            val y = fy + h * (0.07 + i * 0.045)
            ligne(gauche, y + i * h * 0.004, gauche + cw, y - h * 0.015, h * 0.006, c(130, 110, 150, 70))
        }
        val dessus = Path()
        dessus.m(gauche - 2, fy - h * 0.03)
        dessus.cu(gauche + cw * 0.4, fy - h * 0.05, gauche + cw * 0.8, fy - h * 0.01, gauche + cw, fy + h * 0.02)
        dessus.l(gauche + cw, fy + h * 0.06)
        dessus.cu(gauche + cw * 0.7, fy + h * 0.03, gauche + cw * 0.3, fy + h * 0.04, gauche - 2, fy + h * 0.035)
        dessus.close()
        chemin(dessus, 0, lin(0.0, fy - h * 0.05, 0.0, fy + h * 0.06, intArrayOf(c(190, 240, 115), c(55, 140, 80))))
        cv.restore()
        rnd = Random(12)
        val herbes = intArrayOf(c(70, 160, 85), c(140, 215, 105), c(45, 125, 80))
        for (i in 0 until 26) {                        // les herbes hautes du bord, qui ondulent
            val u = rnd.nextDouble(); val x = gauche + cw * u
            val y = fy - h * 0.035 + h * 0.05 * u.pow(2.2) + h * 0.004
            val k = h * rnd.u(0.02, 0.045)
            ligne(x, y + h * 0.01, x + sin(t * 2.2 + i) * k * 0.25 + k * 0.15, y - k, h * 0.006, herbes[rnd.nextInt(3)])
        }
        // le voyageur : de dos, cape au vent (de la couleur du jeu n°1), bâton à la pointe lumineuse
        val vx = gauche + cw * 0.66; val vy = fy - h * 0.012
        val o = sin(t * 3) * h * 0.012
        val cape = Path()
        cape.m(vx - h * 0.018, vy - h * 0.135)
        cape.cu(vx - h * 0.08, vy - h * 0.12 + o, vx - h * 0.1, vy - h * 0.05 - o, vx - h * 0.15 + o, vy - h * 0.03)
        cape.cu(vx - h * 0.09, vy - h * 0.02 + o, vx - h * 0.04, vy - h * 0.04, vx + h * 0.012, vy - h * 0.03)
        cape.l(vx + h * 0.018, vy - h * 0.135)
        cape.close()
        chemin(cape, 0, lin(vx, vy - h * 0.14, vx - h * 0.12, vy, intArrayOf(c(couleur), c(ton(couleur, 0.55)))))
        ligne(vx - h * 0.01, vy - h * 0.05, vx - h * 0.01, vy, h * 0.012, c(45, 40, 70))
        ligne(vx + h * 0.008, vy - h * 0.05, vx + h * 0.008, vy, h * 0.012, c(45, 40, 70))
        rrect(vx - h * 0.02, vy - h * 0.135, vx + h * 0.02, vy - h * 0.045, h * 0.01, c(60, 55, 95))
        cercle(vx, vy - h * 0.158, h * 0.024, c(250, 215, 185))
        secteur(vx - h * 0.027, vy - h * 0.19, vx + h * 0.027, vy - h * 0.135, 170.0, 200.0, c(250, 235, 170))
        val bx = vx + h * 0.045
        ligne(bx, vy, bx + h * 0.012, vy - h * 0.2, h * 0.007, c(110, 80, 70))
        cercle(bx + h * 0.012, vy - h * 0.2, h * 0.05, c(170, 255, 240, 120), flou = h * 0.02)
        cercle(bx + h * 0.012, vy - h * 0.2, h * 0.012, c(235, 255, 250))
        // --- le feuillage qui encadre l'image, en haut à gauche
        rnd = Random(14)
        for (i in 0 until 12) {
            val x = gauche + rnd.u(-0.05, 0.5) * cw
            val y = haut + rnd.u(-0.04, 0.13) * h * (1 - (x - gauche) / cw * 0.6)
            cercle(x + sin(t * 1.2 + i) * h * 0.004, y, h * rnd.u(0.05, 0.09), c(22, 85, 75))
        }
        for (i in 0 until 9) {
            val x = gauche + rnd.u(-0.02, 0.45) * cw
            val y = haut + rnd.u(0.0, 0.13) * h * (1 - (x - gauche) / cw * 0.6)
            cercle(x + sin(t * 1.2 + i) * h * 0.005, y, h * rnd.u(0.02, 0.04), c(75, 165, 95))
        }
        // --- la vie : un planeur au loin, des oiseaux, des pétales emportés par le vent
        val gx = gauche + mod(w * 0.55 + t * h * 0.07, w * 1.2) - w * 0.1; val gy = haut + h * (0.4 + 0.03 * sin(t * 0.9))
        chemin(poly(gx - h * 0.04, gy, gx, gy - h * 0.022, gx + h * 0.04, gy, gx, gy - h * 0.008), c(couleur))
        ligne(gx, gy - h * 0.008, gx, gy + h * 0.02, h * 0.006, c(50, 45, 80))
        for (k in 0 until 3) {
            val ox = gauche + mod(w * (0.5 + k * 0.05) + t * h * 0.09, w * 1.2) - w * 0.1; val oy = haut + h * (0.24 + k * 0.025)
            val aile = sin(t * 8 + k) * h * 0.012
            ligne(ox, oy, ox - h * 0.018, oy - aile, h * 0.006, c(50, 65, 110))
            ligne(ox, oy, ox + h * 0.018, oy - aile, h * 0.006, c(50, 65, 110))
        }
        rnd = Random(4)
        for (k in 0 until 12) {
            val x = gauche + mod(rnd.nextDouble() * w + t * h * 0.22 * (1 + (k % 3) * 0.4), w * 1.1) - w * 0.05
            val y = haut + h * (0.2 + 0.7 * rnd.nextDouble()) + sin(t * 2 + k) * h * 0.04
            cv.save(); deplace(x, y); tourne(t * 120 + k * 40)
            ovale(-h * 0.012, -h * 0.006, h * 0.012, h * 0.006, c(255, 185, 210, 230))
            cv.restore()
        }
        // --- un léger assombrissement des bords (profondeur, sans éblouir)
        rect(gauche, haut, droite, bas, 0, rad(gauche + w * 0.55, haut + h * 0.45, max(w, h) * 0.75,
            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb((95 * (1 - g)).toInt(), 10, 5, 40)), pos(0.55, 1.0)))
    }

    // ============================== 2) LE JEU D'HORREUR ==============================

    // un arbre mort : une branche, qui se divise en 2 ou 3 branches plus petites, et ainsi de suite
    private fun arbreMort(x: Double, y: Double, ang: Double, longueur: Double, ep: Double, niveau: Int, rnd: Random, couleur: Int, vent: Double) {
        val x2 = x + cos(ang) * longueur; val y2 = y - sin(ang) * longueur
        ligne(x, y, x2, y2, ep, couleur)
        if (niveau <= 0) return
        val trois = rnd.nextDouble() < 0.25
        arbreMort(x2, y2, ang - rnd.u(0.3, 0.75) + vent, longueur * rnd.u(0.58, 0.76), ep * 0.62, niveau - 1, rnd, couleur, vent)
        arbreMort(x2, y2, ang + rnd.u(0.3, 0.75) + vent, longueur * rnd.u(0.58, 0.76), ep * 0.62, niveau - 1, rnd, couleur, vent)
        if (trois) arbreMort(x2, y2, ang + rnd.u(-0.3, 0.3) + vent, longueur * rnd.u(0.58, 0.76), ep * 0.62, niveau - 1, rnd, couleur, vent)
    }

    private fun horreur(gauche: Double, haut: Double, w: Double, h: Double, t: Double) {
        val droite = gauche + w; val bas = haut + h
        rect(gauche, haut, droite, bas, 0, lin(0.0, haut, 0.0, haut + h * 0.66, intArrayOf(c(5, 8, 14), c(12, 24, 32), c(34, 54, 58)), pos(0.0, 0.55, 1.0)))
        // la lune voilée et les nuages qui passent devant
        val mx = gauche + w * 0.72; val my = haut + h * 0.2
        cercle(mx, my, h * 0.5, 0, degrade = rad(mx, my, h * 0.5, intArrayOf(c(150, 185, 180, 75), c(90, 130, 130, 25), c(90, 130, 130, 0)), pos(0.0, 0.45, 1.0)))
        cercle(mx, my, h * 0.075, c(196, 210, 198, 215), flou = h * 0.006)
        cercle(mx - h * 0.02, my - h * 0.015, h * 0.018, c(150, 165, 158, 120))
        for (k in 0 until 4) {
            val x = gauche + mod(k * w * 0.3 + t * h * 0.05 * (1 + (k % 2) * 0.5), w * 1.5) - w * 0.25; val y = haut + h * (0.1 + 0.07 * (k % 3))
            ovale(x - h * 0.4, y - h * 0.035, x + h * 0.4, y + h * 0.035, c(8, 14, 20, 215), flou = h * 0.03)
        }
        // la forêt de sapins au loin, puis la brume
        var rnd = Random(21)
        var x = gauche - h * 0.05
        while (x < droite + h * 0.05) {
            val ht = h * rnd.u(0.12, 0.26); val lw = ht * rnd.u(0.22, 0.32)
            chemin(poly(x - lw, haut + h * 0.66, x, haut + h * 0.66 - ht, x + lw, haut + h * 0.66), c(13, 26, 31))
            x += lw * rnd.u(0.9, 1.5)
        }
        rect(gauche, haut + h * 0.44, droite, haut + h * 0.7, 0, lin(0.0, haut + h * 0.44, 0.0, haut + h * 0.7,
            intArrayOf(c(120, 155, 150, 0), c(120, 155, 150, 70), c(120, 155, 150, 20)), pos(0.0, 0.75, 1.0)))
        // la colline et LA MAISON abandonnée (une seule fenêtre éclairée, qui vacille)
        val colline = Path()
        colline.m(gauche - 2, haut + h * 0.72)
        colline.cu(gauche + w * 0.3, haut + h * 0.7, gauche + w * 0.5, haut + h * 0.56, gauche + w * 0.7, haut + h * 0.57)
        colline.cu(gauche + w * 0.85, haut + h * 0.58, gauche + w * 0.95, haut + h * 0.66, droite + 2, haut + h * 0.68)
        colline.l(droite + 2, bas + 2); colline.l(gauche - 2, bas + 2); colline.close()
        chemin(colline, 0, lin(0.0, haut + h * 0.56, 0.0, bas, intArrayOf(c(12, 22, 24), c(4, 8, 10))))
        val hx = gauche + w * 0.68; val hy = haut + h * 0.575; val s = h * 0.2
        val noir = c(6, 10, 13)
        cv.save(); deplace(hx, hy); tourne(-2.5)
        rect(-s * 0.6, -s * 0.7, s * 0.5, 0.0, noir)
        chemin(poly(-s * 0.72, -s * 0.7, -s * 0.05, -s * 1.25, s * 0.62, -s * 0.7), noir)
        rect(s * 0.22, -s * 1.35, s * 0.56, 0.0, noir)                                    // la tourelle
        chemin(poly(s * 0.14, -s * 1.35, s * 0.39, -s * 1.85, s * 0.64, -s * 1.35), noir)
        rect(-s * 0.45, -s * 1.2, -s * 0.33, -s * 0.9, noir)                              // la cheminée
        ligne(-s * 0.72, -s * 0.7, -s * 0.05, -s * 1.25, s * 0.02, c(70, 100, 100, 150))  // un filet de lune sur le toit
        ligne(s * 0.14, -s * 1.35, s * 0.39, -s * 1.85, s * 0.02, c(70, 100, 100, 150))
        rect(-s * 0.42, -s * 0.52, -s * 0.26, -s * 0.32, c(16, 26, 30))
        rect(-s * 0.1, -s * 0.52, s * 0.06, -s * 0.32, c(16, 26, 30))
        rect(-s * 0.42, -s * 0.22, -s * 0.26, -s * 0.02, c(16, 26, 30))
        val vacille = 0.78 + 0.22 * sin(t * 9) * sin(t * 2.3)
        val fx = s * 0.31; val fy = -s * 1.15
        cercle(fx + s * 0.08, fy + s * 0.11, s * 0.5, c(255, 170, 70, 70 * vacille), flou = s * 0.2)
        rect(fx, fy, fx + s * 0.16, fy + s * 0.22, c(255, 196, 100, 235 * vacille))
        ligne(fx + s * 0.08, fy, fx + s * 0.08, fy + s * 0.22, s * 0.02, noir)
        val passe = 0.5 + 0.5 * sin(t * 0.9)                                              // une silhouette passe derrière la vitre
        rect(fx + s * 0.1 * passe, fy + s * 0.06, fx + s * 0.1 * passe + s * 0.05, fy + s * 0.22, c(20, 12, 8, 200))
        rect(-s * 0.02, -s * 0.32, s * 0.14, 0.0, c(2, 4, 6))                             // la porte entrouverte
        cv.restore()
        // des arbres morts (un peu de vent), puis des tombes
        val vent = sin(t * 0.8) * 0.02
        arbreMort(gauche + w * 0.2, haut + h * 0.7, PI / 2 + 0.08, h * 0.2, h * 0.02, 3, Random(3), c(16, 28, 32), vent)
        arbreMort(gauche + w * 0.9, haut + h * 0.67, PI / 2 + 0.08, h * 0.17, h * 0.016, 3, Random(5), c(14, 25, 29), vent)
        arbreMort(gauche + w * 0.46, haut + h * 0.66, PI / 2 + 0.08, h * 0.13, h * 0.012, 3, Random(8), c(20, 34, 38), vent)
        rnd = Random(31)
        for (i in 0 until 6) {
            val tx = gauche + w * (0.06 + i * 0.075) + h * rnd.u(-0.02, 0.02); val ty = haut + h * (0.76 + 0.03 * (i % 2)); val k = h * rnd.u(0.035, 0.05)
            cv.save(); deplace(tx, ty); tourne(rnd.u(-9.0, 9.0))
            if (i % 3 == 2) {
                rect(-k * 0.12, -k * 1.5, k * 0.12, 0.0, c(14, 22, 26)); rect(-k * 0.45, -k * 1.15, k * 0.45, -k * 0.9, c(14, 22, 26))
            } else {
                rrect(-k * 0.45, -k * 1.2, k * 0.45, k * 0.2, k * 0.4, c(15, 24, 28))
                arc(-k * 0.45, -k * 1.2, k * 0.45, -k * 0.3, 190.0, 110.0, k * 0.07, c(60, 90, 90, 170))
            }
            cv.restore()
        }
        // des YEUX rouges dans le noir de la forêt : ils apparaissent, clignent, puis disparaissent
        val cycle = (t * 0.35) % 1.0
        if (cycle > 0.1 && cycle < 0.75 && (t * 1.7) % 1.0 <= 0.93) {
            val ex = gauche + w * 0.12; val ey = haut + h * 0.6
            for (dx in doubleArrayOf(-h * 0.016, h * 0.016)) {
                cercle(ex + dx, ey, h * 0.03, c(200, 20, 20, 70), flou = h * 0.014)
                ovale(ex + dx - h * 0.009, ey - h * 0.005, ex + dx + h * 0.009, ey + h * 0.005, c(235, 60, 50))
            }
        }
        // la brume qui glisse au sol
        for (k in 0 until 4) {
            val bx = gauche + mod(k * w * 0.33 + t * h * 0.06 * (if (k % 2 == 1) 1 else -1), w * 1.6) - w * 0.3; val by = haut + h * (0.68 + 0.06 * (k % 3))
            ovale(bx - h * 0.6, by - h * 0.05, bx + h * 0.6, by + h * 0.05, c(135, 170, 165, 40), flou = h * 0.04)
        }
        // le joueur de dos (tête et épaules) et le faisceau de sa lampe torche, qui tremble
        val jx = gauche + w * 0.36; val jy = bas
        val tremble = sin(t * 2.1) * h * 0.012 + sin(t * 5.3) * h * 0.004
        val cibleX = hx - h * 0.04 + tremble * 2; val cibleY = hy - h * 0.08 + tremble
        val ox = jx + h * 0.1; val oy = jy - h * 0.14
        val dxc = cibleX - ox; val dyc = cibleY - oy
        val n = max(1.0, hypot(dxc, dyc)); val nx = -dyc / n; val ny = dxc / n
        chemin(poly(ox, oy, cibleX + dxc * 0.5 + nx * h * 0.2, cibleY + dyc * 0.5 + ny * h * 0.2, cibleX + dxc * 0.5 - nx * h * 0.2, cibleY + dyc * 0.5 - ny * h * 0.2),
            0, lin(ox, oy, cibleX + dxc * 0.4, cibleY + dyc * 0.4, intArrayOf(c(255, 238, 200, 95), c(255, 238, 200, 26), c(255, 238, 200, 0)), pos(0.0, 0.6, 1.0)), flou = h * 0.012)
        rnd = Random(40)
        for (k in 0 until 8) {                                                            // des poussières dans le faisceau
            val u = (rnd.nextDouble() + t * 0.03 * (1 + k % 3)) % 1.0; val cote = rnd.u(-0.8, 0.8) * u * h * 0.12
            cercle(ox + dxc * u + nx * cote, oy + dyc * u + ny * cote + sin(t + k) * h * 0.01, h * 0.005, c(255, 240, 210, 130 * (1 - u)))
        }
        val corps = Path()
        corps.m(jx - h * 0.17, jy + 2)
        corps.cu(jx - h * 0.16, jy - h * 0.12, jx - h * 0.08, jy - h * 0.15, jx, jy - h * 0.15)
        corps.cu(jx + h * 0.08, jy - h * 0.15, jx + h * 0.16, jy - h * 0.12, jx + h * 0.17, jy + 2)
        corps.close()
        chemin(corps, c(3, 5, 7))
        cercle(jx, jy - h * 0.2, h * 0.065, c(3, 5, 7))
        arc(jx - h * 0.065, jy - h * 0.265, jx + h * 0.065, jy - h * 0.135, -70.0, 100.0, h * 0.008, c(120, 150, 145, 150))
        ligne(jx + h * 0.1, jy - h * 0.06, ox, oy, h * 0.035, c(3, 5, 7))
        // la clôture au tout premier plan, des corbeaux, et les bords très sombres
        for (i in 0 until 9) {
            val px = gauche + w * (0.52 + i * 0.07); val k = h * (0.1 + 0.02 * (i % 2))
            cv.save(); deplace(px, bas); tourne(((i * 37) % 11) - 5.0)
            rect(-h * 0.008, -k, h * 0.008, 2.0, c(2, 4, 5))
            cv.restore()
        }
        ligne(gauche + w * 0.5, haut + h * 0.93, droite + 2, haut + h * 0.92, h * 0.005, c(2, 4, 5))
        for (k in 0 until 2) {
            val bx = gauche + mod(w * (0.2 + k * 0.3) + t * h * 0.12, w * 1.2) - w * 0.1; val by = haut + h * (0.3 + k * 0.08)
            val aile = sin(t * 7 + k) * h * 0.015
            ligne(bx, by, bx - h * 0.022, by - aile, h * 0.008, c(4, 7, 9))
            ligne(bx, by, bx + h * 0.022, by - aile, h * 0.008, c(4, 7, 9))
        }
        rect(gauche, haut, droite, bas, 0, rad(gauche + w * 0.52, haut + h * 0.5, max(w, h) * 0.7, intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(215, 0, 0, 0)), pos(0.4, 1.0)))
    }

    // ============================== 3) LA COURSE DE NUIT ==============================

    private fun course(gauche: Double, haut: Double, w: Double, h: Double, couleur: Int, t: Double) {
        val droite = gauche + w; val bas = haut + h
        val hy = haut + h * 0.52                                   // la ligne d'horizon
        val vx = gauche + w * 0.5 + sin(t * 0.5) * h * 0.03        // le point de fuite (la route tourne un peu)
        val neons = arrayOf(intArrayOf(60, 220, 255), intArrayOf(255, 70, 170), intArrayOf(255, 190, 70), intArrayOf(150, 110, 255))
        fun neon(i: Int, a: Double = 255.0): Int { val k = neons[((i % 4) + 4) % 4]; return c(k[0], k[1], k[2], a) }
        rect(gauche, haut, droite, hy, 0, lin(0.0, haut, 0.0, hy, intArrayOf(c(6, 5, 22), c(26, 12, 58), c(92, 30, 110)), pos(0.0, 0.6, 1.0)))
        cercle(vx, hy, w * 0.5, 0, degrade = rad(vx, hy, w * 0.5, intArrayOf(c(255, 90, 170, 110), c(60, 190, 255, 30), c(0, 0, 0, 0)), pos(0.0, 0.5, 1.0)))
        // les tours lointaines et leurs fenêtres allumées
        var rnd = Random(51)
        var x = gauche - h * 0.05
        while (x < droite) {
            val bw = h * rnd.u(0.05, 0.11); val bh = h * rnd.u(0.1, 0.34) * (0.55 + abs((x - vx) / w) * 1.5)
            rect(x, hy - bh, x + bw, hy, c(20, 14, 46))
            val fenetres = (bh / h * 26).toInt()
            for (i in 0 until fenetres) {
                val fx = x + rnd.nextDouble() * bw * 0.85 + h * 0.005; val fy = hy - rnd.nextDouble() * bh * 0.95
                rect(fx - h * 0.004, fy - h * 0.004, fx + h * 0.004, fy + h * 0.004, neon(rnd.nextInt(4), rnd.u(90.0, 210.0)))
            }
            x += bw * rnd.u(0.85, 1.3)
        }
        // la route mouillée, en perspective : z = 0 à l'horizon, z = 1 tout près de nous
        fun demi(z: Double): Double = h * 0.03 + (w * 0.62 + h * 0.25) * z       // la demi-largeur de la route
        fun yz(z: Double): Double = hy + (bas - hy) * z
        rect(gauche, hy, droite, bas, c(8, 7, 18))
        val route = poly(vx - demi(0.0), hy, vx + demi(0.0), hy, vx + demi(1.0), bas + 2, vx - demi(1.0), bas + 2)
        chemin(route, 0, lin(0.0, hy, 0.0, bas, intArrayOf(c(34, 22, 60), c(12, 11, 24))))
        cv.save(); cv.clipPath(route)                                          // les reflets des néons sur la route
        for (k in 0 until 7) {
            val rx = vx + (k - 3) * w * 0.13 + sin(k * 2.3) * h * 0.05
            rect(rx - h * 0.03, hy, rx + h * 0.03, bas, 0, lin(0.0, hy, 0.0, bas, intArrayOf(neon(k, 120.0), neon(k, 0.0))), flou = h * 0.02, add = true)
        }
        cv.restore()
        // les immeubles proches qui défilent des deux côtés, avec leurs enseignes lumineuses (abstraites)
        for (cote in intArrayOf(-1, 1)) {
            for (k in 0 until 6) {
                val z = ((k / 6.0 + t * 0.22) % 1.0).pow(2.2)
                if (z < 0.004) continue
                val x0 = vx + cote * demi(z) * 1.12; val largeur = h * 1.2 * z + h * 0.03; val hauteur = h * (1.6 * z + 0.16)
                val x1 = x0 + cote * largeur; val a = min(1.0, z * 9)
                rect(min(x0, x1), yz(z) - hauteur, max(x0, x1), yz(z), c(10, 8, 24, 255 * a))
                val ex = x0 + cote * largeur * 0.12; val ew = largeur * 0.16
                val el = min(ex, ex + cote * ew); val er = max(ex, ex + cote * ew)
                val i = k + (if (cote < 0) 0 else 2)
                rect(el, yz(z) - hauteur * 0.8, er, yz(z) - hauteur * 0.35, neon(i, 150 * a), flou = largeur * 0.12, add = true)
                rect(el, yz(z) - hauteur * 0.8, er, yz(z) - hauteur * 0.35, c(ton(neon(i), 1.25), 235 * a))
            }
        }
        // les rails lumineux, les bandes blanches qui foncent vers nous, les portiques au-dessus
        ligne(vx - demi(0.0), hy, vx - demi(1.0), bas, h * 0.03, c(60, 220, 255, 120), flou = h * 0.02, add = true)
        ligne(vx - demi(0.0), hy, vx - demi(1.0), bas, h * 0.008, c(60, 220, 255), add = true)
        ligne(vx + demi(0.0), hy, vx + demi(1.0), bas, h * 0.03, c(255, 70, 170, 120), flou = h * 0.02, add = true)
        ligne(vx + demi(0.0), hy, vx + demi(1.0), bas, h * 0.008, c(255, 70, 170), add = true)
        for (decale in doubleArrayOf(-0.34, 0.34)) {
            for (k in 0 until 7) {
                val z0 = ((k / 7.0 + t * 1.1) % 1.0).pow(2.4); val z1 = min(1.0, z0 * 1.35 + 0.004)
                val p = prepBande(h * 0.02 * z1, c(235, 235, 255, 220 * min(1.0, z0 * 6)))
                cv.drawLine((vx + decale * demi(z0)).toFloat(), yz(z0).toFloat(), (vx + decale * demi(z1)).toFloat(), yz(z1).toFloat(), p)
            }
        }
        for (k in 0 until 3) {
            val z = ((k / 3.0 + t * 0.5) % 1.0).pow(2.2)
            if (z < 0.01) continue
            val y = yz(z) - h * 1.5 * z - h * 0.05; val a = min(1.0, z * 6) * (1 - max(0.0, z - 0.75) * 4)
            ligne(vx - demi(z) * 1.05, y, vx + demi(z) * 1.05, y, h * 0.03 * z, c(120, 235, 255, 210 * a), flou = h * 0.012 * z, add = true)
        }
        // une voiture rivale devant nous
        val zr = 0.16 + 0.03 * sin(t * 0.7); val rx = vx - 0.34 * demi(zr) + sin(t * 1.3) * h * 0.01; val ry = yz(zr); val kr = h * 0.09
        rrect(rx - kr * 0.5, ry - kr * 0.42, rx + kr * 0.5, ry, kr * 0.1, c(16, 14, 30))
        rect(rx - kr * 0.46, ry - kr * 0.26, rx + kr * 0.46, ry - kr * 0.18, c(255, 60, 60), flou = kr * 0.03)
        // NOTRE VOITURE, vue de derrière (ses feux arrière prennent la couleur du jeu n°1)
        val cx = gauche + w * 0.5 + sin(t * 1.1) * h * 0.035; val cw = min(h * 0.7, w * 0.5)
        val vif = ton(couleur, 1.3)
        cv.save(); deplace(cx, haut + h * 0.99); tourne(sin(t * 1.1 + 0.6) * 1.6)
        ovale(-cw * 0.6, -cw * 0.08, cw * 0.6, cw * 0.08, c(vif, 120), flou = cw * 0.06, add = true)
        rrect(-cw * 0.59, -cw * 0.16, -cw * 0.41, cw * 0.02, cw * 0.03, c(4, 4, 8))        // les roues
        rrect(cw * 0.41, -cw * 0.16, cw * 0.59, cw * 0.02, cw * 0.03, c(4, 4, 8))
        val caisse = Path()
        caisse.m(-cw * 0.52, 0.0); caisse.l(-cw * 0.52, -cw * 0.2)
        caisse.cu(-cw * 0.5, -cw * 0.3, -cw * 0.44, -cw * 0.33, -cw * 0.38, -cw * 0.34)
        caisse.l(-cw * 0.27, -cw * 0.5)
        caisse.cu(-cw * 0.1, -cw * 0.54, cw * 0.1, -cw * 0.54, cw * 0.27, -cw * 0.5)
        caisse.l(cw * 0.38, -cw * 0.34)
        caisse.cu(cw * 0.44, -cw * 0.33, cw * 0.5, -cw * 0.3, cw * 0.52, -cw * 0.2)
        caisse.l(cw * 0.52, 0.0); caisse.close()
        chemin(caisse, 0, lin(0.0, -cw * 0.54, 0.0, 0.0, intArrayOf(c(52, 44, 86), c(20, 18, 38), c(8, 8, 16)), pos(0.0, 0.45, 1.0)))
        chemin(poly(-cw * 0.25, -cw * 0.47, cw * 0.25, -cw * 0.47, cw * 0.33, -cw * 0.35, -cw * 0.33, -cw * 0.35), 0,
            lin(-cw * 0.3, 0.0, cw * 0.3, 0.0, intArrayOf(c(255, 70, 170, 150), c(30, 26, 60), c(60, 220, 255, 150))))          // la vitre reflète les néons
        rrect(-cw * 0.46, -cw * 0.27, cw * 0.46, -cw * 0.21, cw * 0.03, c(vif, 190), flou = cw * 0.05, add = true)
        rrect(-cw * 0.46, -cw * 0.27, cw * 0.46, -cw * 0.21, cw * 0.03, c(vif))
        rect(-cw * 0.4, -cw * 0.245, cw * 0.4, -cw * 0.235, c(255, 255, 255, 200))
        rect(-cw * 0.3, -cw * 0.12, cw * 0.3, -cw * 0.03, c(6, 6, 12))
        cv.restore()
        // la pluie, et les bords assombris
        rnd = Random(60)
        for (k in 0 until 30) {
            val px = gauche + mod(rnd.nextDouble() * w * 1.2 - t * h * 0.5, w * 1.2) - w * 0.1
            val py = haut + (rnd.nextDouble() * h + t * h * 2.4 * (1 + (k % 3) * 0.3)) % h
            ligne(px, py, px - h * 0.012, py + h * 0.05, h * 0.004, c(190, 210, 255, 75))
        }
        rect(gauche, haut, droite, bas, 0, rad(gauche + w * 0.5, haut + h * 0.5, max(w, h) * 0.72, intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(170, 0, 0, 0)), pos(0.5, 1.0)))
    }

    // un pinceau pour les bandes blanches de la route (bouts droits, pas arrondis)
    private val bande = Paint()
    private fun prepBande(epaisseur: Double, couleur: Int): Paint {
        bande.reset(); bande.isAntiAlias = true; bande.color = couleur
        bande.strokeWidth = max(1.0, epaisseur).toFloat()
        return bande
    }
}
