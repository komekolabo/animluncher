package com.will.animlauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Une "View" = une zone qui se dessine à l'écran.
// Celle-ci dessine une animation, image par image, puis disparaît.
class AnimationView(context: Context, private val style: Style) : View(context) {

    // Les animations disponibles. On en ajoutera d'autres plus tard.
    enum class Style { ECLAIR, ONDE }

    // Avancement de l'animation : 0 = début, 1 = fin.
    private var progres = 0f

    // Le "pinceau" : couleur, épaisseur, lissage des bords (ANTI_ALIAS).
    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND   // bouts de traits arrondis
    }

    // Lance l'animation. "fin" est une fonction appelée quand c'est terminé.
    fun jouer(fin: () -> Unit) {
        // ValueAnimator fait passer un nombre de 0 à 1 en 450 millisecondes.
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = DecelerateInterpolator()   // rapide au début, ralentit à la fin
            addUpdateListener {
                progres = it.animatedValue as Float
                invalidate()   // "redessine-toi" -> Android rappelle onDraw
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = fin()
            })
            start()
        }
    }

    // Appelée par Android à chaque image (environ 60 fois par seconde).
    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f             // centre de l'écran
        val cy = height / 2f
        val rayonMax = hypot(cx, cy)    // distance du centre à un coin
        when (style) {
            Style.ECLAIR -> dessinerEclair(canvas, cx, cy, rayonMax)
            Style.ONDE -> dessinerOnde(canvas, cx, cy, rayonMax)
        }
    }

    // Claude : un éclat orange, 12 rayons qui jaillissent du centre.
    private fun dessinerEclair(canvas: Canvas, cx: Float, cy: Float, rayonMax: Float) {
        val opacite = ((1f - progres) * 255).toInt()   // 255 = opaque, 0 = invisible

        // 1. Un léger voile orange sur tout l'écran, qui s'efface.
        pinceau.style = Paint.Style.FILL
        pinceau.color = Color.argb((opacite * 0.25f).toInt(), 217, 119, 87)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pinceau)

        // 2. Les rayons : chaque rayon part d'un angle différent (360° / 12 = 30°).
        pinceau.style = Paint.Style.STROKE
        pinceau.color = Color.argb(opacite, 255, 140, 100)
        pinceau.strokeWidth = 4f + 22f * (1f - progres)   // les rayons s'affinent
        for (i in 0 until 12) {
            val angle = Math.toRadians(i * 30.0)
            val debut = rayonMax * progres * 0.25f
            val fin = rayonMax * progres * 0.8f
            canvas.drawLine(
                cx + debut * cos(angle).toFloat(), cy + debut * sin(angle).toFloat(),
                cx + fin * cos(angle).toFloat(), cy + fin * sin(angle).toFloat(),
                pinceau
            )
        }
    }

    // Google : 4 anneaux de couleur qui s'élargissent l'un après l'autre.
    private fun dessinerOnde(canvas: Canvas, cx: Float, cy: Float, rayonMax: Float) {
        val couleurs = listOf(
            Color.rgb(66, 133, 244),   // bleu
            Color.rgb(234, 67, 53),    // rouge
            Color.rgb(251, 188, 5),    // jaune
            Color.rgb(52, 168, 83)     // vert
        )
        pinceau.style = Paint.Style.STROKE
        for ((i, couleur) in couleurs.withIndex()) {
            // Chaque anneau démarre un peu après le précédent (décalage de 0,1).
            val p = (progres * 1.4f - i * 0.12f).coerceIn(0f, 1f)
            if (p <= 0f) continue
            pinceau.color = couleur
            pinceau.alpha = ((1f - p) * 255).toInt()
            pinceau.strokeWidth = 4f + 30f * (1f - p)
            canvas.drawCircle(cx, cy, rayonMax * p, pinceau)
        }
    }
}
