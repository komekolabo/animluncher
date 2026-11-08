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
        if (a <= 0.003f) return
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
}
