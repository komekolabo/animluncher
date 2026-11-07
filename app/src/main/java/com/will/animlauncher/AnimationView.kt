package com.will.animlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Une "View" = une zone qui se dessine à l'écran.
// Celle-ci dessine une animation, image par image, puis disparaît.
class AnimationView(context: Context, private val style: Style) : View(context) {

    enum class Style { ECLAIR, ONDE }

    private val duree = 450L
    private var heureDebut = 0L
    private var progres = 0f
    private var quandFini: (() -> Unit)? = null
    private var arretee = false

    // MOUCHARD : nombre d'images dessinées, nombre de battements du métronome,
    // et heure de création de la vue.
    var images = 0
        private set
    var battements = 0
        private set
    val creeeA = SystemClock.uptimeMillis()

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }

    // LE MÉTRONOME : le "Choreographer" d'Android appelle cette fonction
    // à chaque rafraîchissement de l'écran (environ 60 fois par seconde).
    // À chaque battement, on demande à la vue de se redessiner,
    // puis on se réinscrit pour le battement suivant.
    private val metronome = object : Choreographer.FrameCallback {
        override fun doFrame(heureImageNanos: Long) {
            if (arretee) return
            battements++
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun jouer(fin: () -> Unit) {
        quandFini = fin
        // L'horloge démarre MAINTENANT : si des images sautent,
        // l'animation rattrape son retard au lieu de rester bloquée.
        heureDebut = SystemClock.uptimeMillis()
        Choreographer.getInstance().postFrameCallback(metronome)
    }

    // Arrête le métronome (appelée quand on retire l'animation).
    fun arreter() {
        arretee = true
        Choreographer.getInstance().removeFrameCallback(metronome)
    }

    override fun onDraw(canvas: Canvas) {
        images++
        val t = ((SystemClock.uptimeMillis() - heureDebut).toFloat() / duree).coerceIn(0f, 1f)
        progres = 1f - (1f - t) * (1f - t)   // décélération

        val cx = width / 2f
        val cy = height / 2f
        val rayonMax = hypot(cx, cy)
        when (style) {
            Style.ECLAIR -> dessinerEclair(canvas, cx, cy, rayonMax)
            Style.ONDE -> dessinerOnde(canvas, cx, cy, rayonMax)
        }

        // Fini : on prévient le service (une seule fois).
        if (t >= 1f) {
            val fin = quandFini
            quandFini = null
            if (fin != null) post { fin() }
        }
    }

    // Claude : un éclat orange, 12 rayons qui jaillissent du centre.
    private fun dessinerEclair(canvas: Canvas, cx: Float, cy: Float, rayonMax: Float) {
        val opacite = ((1f - progres) * 255).toInt()

        pinceau.style = Paint.Style.FILL
        pinceau.color = Color.argb((opacite * 0.25f).toInt(), 217, 119, 87)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pinceau)

        pinceau.style = Paint.Style.STROKE
        pinceau.color = Color.argb(opacite, 255, 140, 100)
        pinceau.strokeWidth = 4f + 18f * (1f - progres)
        for (i in 0 until 12) {
            val angle = Math.toRadians(i * 30.0)
            val debut = rayonMax * (0.04f + 0.30f * progres)
            val fin = rayonMax * (0.10f + 0.70f * progres)
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
            Color.rgb(66, 133, 244),
            Color.rgb(234, 67, 53),
            Color.rgb(251, 188, 5),
            Color.rgb(52, 168, 83)
        )
        pinceau.style = Paint.Style.STROKE
        for ((i, couleur) in couleurs.withIndex()) {
            val p = (progres * 1.4f - i * 0.12f).coerceIn(0f, 1f)
            if (p <= 0f) continue
            pinceau.color = couleur
            pinceau.alpha = ((1f - p) * 255).toInt()
            pinceau.strokeWidth = 6f + 30f * (1f - p)
            canvas.drawCircle(cx, cy, rayonMax * (0.04f + 0.96f * p), pinceau)
        }
    }
}
