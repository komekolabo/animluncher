package com.will.animlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// NOUVEAU MOTEUR : une "SurfaceView" = une surface de dessin indépendante.
// Une vue classique attend qu'Android lui dise "redessine-toi" (le métronome),
// et ce signal est coupé quand l'app est en arrière-plan.
// Avec une surface, c'est NOTRE code qui envoie chaque image à l'écran,
// depuis un "fil" (thread) séparé, à son propre rythme. Plus besoin du métronome.
class AnimationView(context: Context, private val style: Style) :
    SurfaceView(context), SurfaceHolder.Callback {

    enum class Style { ECLAIR, ONDE }

    private val duree = 450L
    private var quandFini: (() -> Unit)? = null
    private val principal = Handler(Looper.getMainLooper())
    private var fil: Thread? = null

    // "@Volatile" : ces variables sont lues par deux fils en même temps,
    // ce mot garantit que chacun voit la valeur à jour.
    @Volatile private var arretee = false

    // MOUCHARD
    @Volatile var images = 0
        private set
    @Volatile var surfaceCreee = false
        private set
    val creeeA = SystemClock.uptimeMillis()

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }

    init {
        setZOrderOnTop(true)                       // la surface passe au-dessus de tout
        holder.setFormat(PixelFormat.TRANSLUCENT)  // et elle a un fond transparent
        holder.addCallback(this)                   // préviens-nous quand elle est prête
    }

    fun jouer(fin: () -> Unit) {
        quandFini = fin
    }

    fun arreter() {
        arretee = true
    }

    // Android nous prévient : la surface est prête, on peut dessiner.
    override fun surfaceCreated(h: SurfaceHolder) {
        surfaceCreee = true
        fil = Thread { boucleDeDessin() }.apply { start() }
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, largeur: Int, hauteur: Int) {}

    // La surface va disparaître : on arrête le fil et on attend qu'il ait fini.
    override fun surfaceDestroyed(h: SurfaceHolder) {
        arretee = true
        try { fil?.join(200) } catch (_: InterruptedException) {}
    }

    // LA BOUCLE, exécutée sur un fil séparé :
    // calcule l'avancement, dessine une image, attend 16 ms, recommence.
    private fun boucleDeDessin() {
        val debut = SystemClock.uptimeMillis()
        while (!arretee) {
            val t = ((SystemClock.uptimeMillis() - debut).toFloat() / duree).coerceIn(0f, 1f)
            val progres = 1f - (1f - t) * (1f - t)   // décélération

            // "lock" = on emprunte la toile de la surface ; "unlockAndPost" = on l'envoie à l'écran.
            val toile = try { holder.lockHardwareCanvas() } catch (_: Exception) { null } ?: break
            try {
                toile.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)   // efface l'image précédente
                dessiner(toile, progres)
            } finally {
                holder.unlockCanvasAndPost(toile)
            }
            images++

            if (t >= 1f) break
            try { Thread.sleep(16) } catch (_: InterruptedException) { break }   // ~60 images/s
        }

        // Fini : on prévient le service, sur le fil principal.
        principal.post {
            val fin = quandFini
            quandFini = null
            fin?.invoke()
        }
    }

    private fun dessiner(toile: Canvas, progres: Float) {
        val l = toile.width.toFloat()
        val h = toile.height.toFloat()
        val cx = l / 2f
        val cy = h / 2f
        val rayonMax = hypot(cx, cy)
        when (style) {
            Style.ECLAIR -> dessinerEclair(toile, l, h, cx, cy, rayonMax, progres)
            Style.ONDE -> dessinerOnde(toile, cx, cy, rayonMax, progres)
        }
    }

    // Claude : un éclat orange, 12 rayons qui jaillissent du centre.
    private fun dessinerEclair(
        toile: Canvas, l: Float, h: Float, cx: Float, cy: Float, rayonMax: Float, progres: Float
    ) {
        val opacite = ((1f - progres) * 255).toInt()

        pinceau.style = Paint.Style.FILL
        pinceau.color = Color.argb((opacite * 0.25f).toInt(), 217, 119, 87)
        toile.drawRect(0f, 0f, l, h, pinceau)

        pinceau.style = Paint.Style.STROKE
        pinceau.color = Color.argb(opacite, 255, 140, 100)
        pinceau.strokeWidth = 4f + 18f * (1f - progres)
        for (i in 0 until 12) {
            val angle = Math.toRadians(i * 30.0)
            val debut = rayonMax * (0.04f + 0.30f * progres)
            val fin = rayonMax * (0.10f + 0.70f * progres)
            toile.drawLine(
                cx + debut * cos(angle).toFloat(), cy + debut * sin(angle).toFloat(),
                cx + fin * cos(angle).toFloat(), cy + fin * sin(angle).toFloat(),
                pinceau
            )
        }
    }

    // Google : 4 anneaux de couleur qui s'élargissent l'un après l'autre.
    private fun dessinerOnde(toile: Canvas, cx: Float, cy: Float, rayonMax: Float, progres: Float) {
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
            toile.drawCircle(cx, cy, rayonMax * (0.04f + 0.96f * p), pinceau)
        }
    }
}
