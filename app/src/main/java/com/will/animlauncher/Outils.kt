package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// LES CATÉGORIES : durée de l'apparition et de la révélation, en millisecondes.
// L'attente (pendant que l'app charge) s'ajoute entre les deux, 3 s maximum.
enum class Categorie(val intro: Long, val revelation: Long) {
    ESSENTIELLE(450, 520),   // IA, Google principal, apps qui font vivre le téléphone
    COURANTE(300, 380),      // réseaux sociaux, banques, outils du quotidien
    SECONDAIRE(200, 260)     // le reste
}

// Où en est l'animation à cette image : temps écoulé, avancement de l'intro et de la révélation (0 à 1).
class Instant(val secondes: Float, val intro: Float, val revelation: Float)

// UNE SCÈNE = une animation. Chaque animation est une classe qui hérite de Scene
// et sait se dessiner à n'importe quel instant.
abstract class Scene {
    abstract fun dessiner(c: Canvas, o: Outils, i: Instant)
}

// --- Petites fonctions utiles partout ---
fun rgb(r: Int, g: Int, b: Int): Int = Color.rgb(r, g, b)
fun argb(a: Float, r: Int, g: Int, b: Int): Int = Color.argb(a.toInt().coerceIn(0, 255), r, g, b)
fun avecAlpha(couleur: Int, a: Float): Int =
    Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(couleur), Color.green(couleur), Color.blue(couleur))

// Courbes d'accélération : transforment une progression 0→1 en mouvement naturel.
fun doux(x: Float): Float {            // lent, rapide, lent
    val v = x.coerceIn(0f, 1f)
    return if (v < 0.5f) 4f * v * v * v else 1f - (-2f * v + 2f).pow(3) / 2f
}
fun ralentit(x: Float): Float {        // rapide puis ralentit
    val v = x.coerceIn(0f, 1f)
    return 1f - (1f - v).pow(3)
}
fun accelere(x: Float): Float {        // lent puis accélère
    val v = x.coerceIn(0f, 1f)
    return v * v * v
}
fun borne(x: Float, a: Float = 0f, b: Float = 1f): Float = x.coerceIn(a, b)
fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

// Reste d'une division, toujours positif (en Kotlin, -0.3 % 1 donne -0.3 ; ici on veut 0.7).
fun modulo(a: Float, b: Float): Float {
    val r = a % b
    return if (r < 0f) r + b else r
}

// Un nuage de particules tirées au hasard (toujours les mêmes pour une même graine) :
// chaque particule = [x, y, profondeur, phase], x et y entre 0 et 1.
fun nuage(graine: Int, n: Int): List<FloatArray> {
    val r = Random(graine)
    return List(n) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.15f + 0.85f * r.nextFloat(), r.nextFloat() * 6.28f) }
}

// Une grosse lumière floue d'arrière-plan (effet « bokeh » d'un objectif photo).
class Flou(val x: Float, val y: Float, val r: Float, val couleur: Int, val ph: Float)

fun bokehListe(graine: Int, couleurs: IntArray, n: Int = 9): List<Flou> {
    val r = Random(graine)
    return List(n) { k -> Flou(r.nextFloat(), r.nextFloat() * 0.6f, 0.05f + 0.07f * r.nextFloat(), couleurs[k % couleurs.size], r.nextFloat() * 6.28f) }
}

// LA BOÎTE À OUTILS DE DESSIN, partagée par toutes les scènes.
class Outils(val dp: Float) {

    // Un pinceau neuf. Avec "trait", il dessine des contours au lieu de remplir.
    fun pinceau(couleur: Int? = null, trait: Float? = null): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (couleur != null) p.color = couleur
        if (trait != null) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = trait
            p.strokeCap = Paint.Cap.ROUND
        }
        return p
    }

    // Un pinceau "lumière" : sa couleur s'AJOUTE à ce qui est dessous (comme un projecteur).
    fun lumiere(couleur: Int? = null, trait: Float? = null): Paint =
        pinceau(couleur, trait).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }

    // La gomme : efface les pixels (ils redeviennent transparents et l'app apparaît).
    fun gomme(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    // Une tache de lumière ovale : dégradé radial étiré horizontalement.
    fun tache(c: Canvas, x: Float, y: Float, sx: Float, sy: Float, intensite: Float, couleur: Int) {
        val a = borne(intensite)
        if (a <= 0.003f || sy <= 0.5f || sx <= 0.5f) return
        val rayon = sy * 2f
        val p = lumiere()
        p.shader = RadialGradient(
            x, y, rayon,
            intArrayOf(avecAlpha(couleur, a), avecAlpha(couleur, a * 0.37f), avecAlpha(couleur, 0f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.save()
        c.scale(sx / sy, 1f, x, y)
        c.drawCircle(x, y, rayon, p)
        c.restore()
    }

    // Un halo rond et doux.
    fun halo(c: Canvas, x: Float, y: Float, r: Float, intensite: Float, couleur: Int) =
        tache(c, x, y, r, r, intensite, couleur)

    // Révélation en cercle : un trou qui grandit depuis (cx, cy) jusqu'aux coins, avec un fin anneau.
    fun revelationCercle(c: Canvas, cx: Float, cy: Float, rev: Float, couleur: Int) {
        if (rev <= 0f) return
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        val rmax = max(max(hypot(cx, cy), hypot(w - cx, cy)), max(hypot(cx, h - cy), hypot(w - cx, h - cy)))
        val trou = rmax * ralentit(rev)
        c.drawCircle(cx, cy, trou, gomme())
        c.drawCircle(cx, cy, trou, pinceau(avecAlpha(couleur, (1f - rev) * 0.8f), (2f + 4f * (1f - rev)) * dp))
    }

    // Révélation en balayage : un bord lumineux traverse l'écran selon un angle (en degrés).
    fun revelationBalayage(c: Canvas, rev: Float, angle: Float, couleur: Int) {
        if (rev <= 0f) return
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        val diag = hypot(w, h)
        c.save()
        c.translate(w / 2f, h / 2f)
        c.rotate(angle)
        val x = -diag / 2f + diag * doux(rev) * 1.08f
        c.drawRect(-diag, -diag, x, diag, gomme())
        val bord = 26f * dp
        val p = lumiere()
        p.shader = LinearGradient(
            x - bord, 0f, x, 0f,
            avecAlpha(couleur, 0f), avecAlpha(couleur, 0.55f * (1f - rev * 0.5f)), Shader.TileMode.CLAMP
        )
        c.drawRect(x - bord, -diag, x, diag, p)
        c.restore()
    }
    // ---------------- OUTILS « CINÉMA » : ce qui donne de la profondeur ----------------

    // Assombrit les bords de l'image, comme un objectif de cinéma.
    fun vignette(c: Canvas, w: Float, h: Float, force: Float = 0.55f) {
        val r = hypot(w, h) / 2f
        val p = pinceau()
        p.shader = RadialGradient(
            w / 2f, h / 2f, r,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, argb(force * 255f, 0, 0, 0)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, p)
    }

    // Un trou à bord flou qui grandit depuis (cx, cy) : l'app apparaît comme dans un fondu.
    // DST_OUT = « efface là où je peins, proportionnellement à mon opacité ».
    fun revelationDouce(c: Canvas, cx: Float, cy: Float, rev: Float, couleur: Int? = null, flou: Float = 0.30f) {
        if (rev <= 0f) return
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        val rmax = max(max(hypot(cx, cy), hypot(w - cx, cy)), max(hypot(cx, h - cy), hypot(w - cx, h - cy)))
        val r = rmax * (1f + flou) * ralentit(rev) + 1f
        val p = pinceau()
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        p.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
            floatArrayOf(0f, 1f - flou, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, p)
        if (couleur != null) {
            c.drawCircle(cx, cy, r * (1f - flou * 0.5f), lumiere(avecAlpha(couleur, 0.35f * (1f - rev)), 3f * dp))
        }
    }

    // Un front à bord flou traverse l'écran selon un angle (en degrés) : derrière lui, l'app.
    fun revelationBalayageDouce(c: Canvas, rev: Float, angle: Float, couleur: Int? = null, flouDp: Float = 90f) {
        if (rev <= 0f) return
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        val diag = hypot(w, h)
        val flou = flouDp * dp
        c.save()
        c.translate(w / 2f, h / 2f)
        c.rotate(angle)
        val x = -diag / 2f - flou + (diag + 2f * flou) * doux(rev)
        val plein = pinceau(Color.BLACK)
        plein.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        c.drawRect(-diag, -diag, x - flou, diag, plein)
        val bord = pinceau()
        bord.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        bord.shader = LinearGradient(x - flou, 0f, x, 0f, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(x - flou, -diag, x, diag, bord)
        if (couleur != null) {
            val l = lumiere()
            l.shader = LinearGradient(
                x - flou, 0f, x, 0f,
                intArrayOf(avecAlpha(couleur, 0f), avecAlpha(couleur, 0.35f * (1f - rev)), avecAlpha(couleur, 0f)),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
            )
            c.drawRect(x - flou, -diag, x, diag, l)
        }
        c.restore()
    }

    // Particules en suspension. Les proches (profondeur forte) sont plus grosses et plus rapides.
    fun poussieres(c: Canvas, liste: List<FloatArray>, sec: Float, w: Float, h: Float,
                   couleur: Int, alpha: Float, vx: Float = 0f, vy: Float = -0.03f) {
        val p = pinceau()
        for (q in liste) {
            val x = modulo(q[0] + vx * sec * q[2], 1f) * w
            val y = modulo(q[1] + vy * sec * q[2], 1f) * h
            val scint = 0.5f + 0.5f * sin(sec * 2f + q[3])
            p.color = avecAlpha(couleur, alpha * (0.3f + 0.7f * scint) * (0.4f + 0.6f * q[2]))
            c.drawCircle(x, y, (0.6f + 1.8f * q[2]) * dp, p)
        }
    }

    // Grosses lumières floues d'arrière-plan, qui bougent à peine.
    fun bokeh(c: Canvas, liste: List<Flou>, sec: Float, w: Float, h: Float, alpha: Float) {
        for (f in liste) {
            val x = (f.x + 0.01f * sin(sec * 0.7f + f.ph)) * w
            val y = (f.y + 0.01f * kotlin.math.cos(sec * 0.6f + f.ph)) * h
            val r = f.r * kotlin.math.min(w, h)
            val a = alpha * (0.6f + 0.4f * sin(sec * 1.3f + f.ph))
            halo(c, x, y, r, a * 0.5f, f.couleur)
            c.drawCircle(x, y, r * 0.9f, lumiere(avecAlpha(f.couleur, a * 0.10f)))
        }
    }
}
