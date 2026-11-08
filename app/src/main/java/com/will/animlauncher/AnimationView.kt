package com.will.animlauncher

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView

// L'ÉCRAN DE CHARGEMENT ANIMÉ, en plein écran, qui joue UNE scène en 3 phases :
//   1. INTRO      : la scène apparaît (durée selon la catégorie)
//   2. ATTENTE    : elle tourne en boucle tant que l'app charge (3 s maximum)
//   3. RÉVÉLATION : l'app est dévoilée (durée selon la catégorie)
// Le service appelle signalerPret() quand le vrai écran de l'app est arrivé.
class AnimationView(context: Context, private val scene: Scene, categorie: Categorie) :
    SurfaceView(context), SurfaceHolder.Callback {

    private val dureeIntro = categorie.intro
    private val dureeRevelation = categorie.revelation
    private val dureeAttenteMax = 3000L

    private var quandFini: (() -> Unit)? = null
    private val principal = Handler(Looper.getMainLooper())
    private var fil: Thread? = null
    @Volatile private var arretee = false
    @Volatile private var pret = false

    // MOUCHARD
    @Volatile var images = 0
        private set
    @Volatile var surfaceCreee = false
        private set
    val creeeA = SystemClock.uptimeMillis()

    private val outils = Outils(resources.displayMetrics.density)

    init {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
    }

    fun jouer(fin: () -> Unit) { quandFini = fin }
    fun signalerPret() { pret = true }
    fun arreter() { arretee = true }

    override fun surfaceCreated(h: SurfaceHolder) {
        surfaceCreee = true
        fil = Thread { boucleDeDessin() }.apply { start() }
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, largeur: Int, hauteur: Int) {}

    override fun surfaceDestroyed(h: SurfaceHolder) {
        arretee = true
        try { fil?.join(200) } catch (_: InterruptedException) {}
    }

    // LA BOUCLE : calcule où on en est, demande à la scène de dessiner, attend 16 ms, recommence.
    private fun boucleDeDessin() {
        val debut = SystemClock.uptimeMillis()
        var debutRevelation = 0L

        while (!arretee) {
            val maintenant = SystemClock.uptimeMillis()
            val ecoule = maintenant - debut
            if (debutRevelation == 0L &&
                ((ecoule >= dureeIntro && pret) || ecoule >= dureeAttenteMax)
            ) debutRevelation = maintenant

            val intro = (ecoule.toFloat() / dureeIntro).coerceIn(0f, 1f)
            val revelation = if (debutRevelation == 0L) 0f
                else ((maintenant - debutRevelation).toFloat() / dureeRevelation).coerceIn(0f, 1f)

            val toile = try { holder.lockHardwareCanvas() } catch (_: Exception) { null } ?: break
            try {
                toile.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                scene.dessiner(toile, outils, Instant(ecoule / 1000f, intro, revelation))
            } catch (_: Exception) {
                // une erreur de dessin ne doit jamais bloquer l'écran : on arrête l'animation
                arretee = true
            } finally {
                holder.unlockCanvasAndPost(toile)
            }
            images++

            if (revelation >= 1f) break
            try { Thread.sleep(16) } catch (_: InterruptedException) { break }
        }

        principal.post {
            val fin = quandFini
            quandFini = null
            fin?.invoke()
        }
    }
}
