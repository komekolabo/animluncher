package com.will.animlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// LES PETITES ANIMATIONS DES CARTES DE L'ACCUEIL (refonte du 09/11).
// Elles sont volontairement lentes et sombres : aucun flash.
// Chacune est une « vue » qui se redessine à chaque image de l'écran (environ 60 fois par seconde)
// tant qu'elle est affichée. Android arrête tout seul quand l'écran n'est plus visible.

// La base commune : compte le temps écoulé et redemande une image après chaque dessin.
abstract class DessinVivant(context: Context) : View(context) {
    private val debut = SystemClock.uptimeMillis()
    protected val dp = context.resources.displayMetrics.density

    override fun onDraw(c: Canvas) {
        val t = (SystemClock.uptimeMillis() - debut) / 1000f        // les secondes écoulées
        dessiner(c, width.toFloat(), height.toFloat(), t)
        postInvalidateOnAnimation()                                 // « redessine-moi à la prochaine image »
    }

    abstract fun dessiner(c: Canvas, w: Float, h: Float, t: Float)

    // Un cycle de 0 à 1 qui se répète toutes les « periode » secondes.
    // (le reste d'une division peut être négatif en Kotlin : on le ramène entre 0 et la période)
    protected fun cycle(t: Float, periode: Float): Float {
        val r = t % periode
        return (if (r < 0f) r + periode else r) / periode
    }
}

// ===================== CARTE 2 : « Logos animés » =====================
// Le fond de la carte 2 : un halo doré très léger au centre (derrière le logo animé, qui est posé
// par-dessus, voir MainActivity) et 4 galets de couleur qui flottent autour.
class DessinGalets(context: Context) : DessinVivant(context) {
    // Chaque galet : décalage depuis le centre (en dp), rayon (en dp), couleur, retard (en s).
    private class Galet(val dx: Float, val dy: Float, val r: Float, val couleur: Int, val retard: Float)
    private val galets = listOf(
        Galet(-92f, -63f, 19f, Color.rgb(217, 119, 87), 0f),
        Galet(94f, -13f, 15f, Color.rgb(60, 196, 106), 0.9f),
        Galet(-74f, 97f, 13f, Color.rgb(91, 147, 240), 1.7f),
        Galet(66f, 103f, 11f, Color.rgb(123, 203, 58), 2.4f)
    )
    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ombre = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 0, 0, 0) }

    override fun dessiner(c: Canvas, w: Float, h: Float, t: Float) {
        val cx = w / 2f
        val cy = h / 2f
        // le halo doré, qui respire lentement
        val souffle = 0.92f + 0.14f * (0.5f - 0.5f * cos(2f * PI.toFloat() * cycle(t, 6f)))
        val rh = 100f * dp * souffle
        pinceau.shader = RadialGradient(cx, cy, rh,
            intArrayOf(Color.argb(31, 246, 210, 122), Color.TRANSPARENT, Color.TRANSPARENT),
            floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, rh, pinceau)
        // les galets : chacun monte et descend de quelques dp, sur 3 s, avec son propre retard
        val echelle = min(1f, w / (318f * dp))      // sur une carte étroite, on resserre un peu
        for (g in galets) {
            val flotte = -3.5f * dp * sin(2f * PI.toFloat() * cycle(t + g.retard, 3f))
            val x = cx + g.dx * dp * echelle
            val y = cy + g.dy * dp * echelle + flotte
            val r = g.r * dp
            c.drawCircle(x, y + r * 0.45f, r * 0.9f, ombre)          // une ombre douce dessous
            // la lumière vient d'en haut à gauche : couleur vive là, presque noir de l'autre côté
            pinceau.shader = RadialGradient(x - r * 0.3f, y - r * 0.4f, r * 1.9f,
                intArrayOf(g.couleur, Color.rgb(10, 10, 10)), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, pinceau)
        }
    }
}

// ===================== CARTE 3 : « Fonds d'écran » =====================
// Un vrai petit fond d'écran animé : trois lueurs sombres (bleu nuit, braise, vert profond) qui
// dérivent très lentement (22 s dans un sens, 22 s dans l'autre), et quelques étoiles qui scintillent.
// Servira aussi d'aperçu dans l'écran Fonds d'écran (FondsActivity).
class DessinFonds(context: Context) : DessinVivant(context) {
    private class Lueur(val fx: Float, val fy: Float, val couleur: Int)
    private val lueurs = listOf(
        Lueur(0.33f, 0.30f, Color.rgb(30, 42, 58)),
        Lueur(0.70f, 0.50f, Color.rgb(58, 36, 24)),
        Lueur(0.25f, 0.72f, Color.rgb(16, 48, 42))
    )
    // les étoiles : position (en fraction de l'écran) et rayon (en dp)
    private val etoiles = listOf(
        floatArrayOf(0.18f, 0.22f, 1f), floatArrayOf(0.72f, 0.16f, 1.5f), floatArrayOf(0.58f, 0.38f, 1f),
        floatArrayOf(0.84f, 0.44f, 1f), floatArrayOf(0.30f, 0.48f, 1.5f), floatArrayOf(0.12f, 0.60f, 1f),
        floatArrayOf(0.64f, 0.66f, 1f), floatArrayOf(0.46f, 0.12f, 1f)
    )
    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pEtoile = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun dessiner(c: Canvas, w: Float, h: Float, t: Float) {
        c.drawColor(Color.rgb(3, 3, 4))
        // k va de 0 à 1 puis revient à 0, en douceur, sur 44 s
        val k = 0.5f - 0.5f * cos(2f * PI.toFloat() * cycle(t, 44f))
        c.save()
        c.rotate(14f * k, w / 2f, h / 2f)
        c.translate(0.08f * w * k, -0.06f * h * k)
        val r = 0.55f * w
        for (l in lueurs) {
            val x = l.fx * w
            val y = l.fy * h
            pinceau.shader = RadialGradient(x, y, r, intArrayOf(l.couleur, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, pinceau)
        }
        c.restore()
        for ((i, e) in etoiles.withIndex()) {
            val a = 0.5f + 0.5f * sin(t * 1.3f + i * 1.7f)
            pEtoile.color = Color.argb((70 + 110 * a).toInt(), 200, 200, 206)
            c.drawCircle(e[0] * w, e[1] * h, e[2] * dp, pEtoile)
        }
    }
}
