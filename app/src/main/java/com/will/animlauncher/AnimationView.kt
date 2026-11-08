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
//   2. ATTENTE    : elle tourne en boucle tant que l'app charge (3 s maximum),
//                   et AU MOINS 1,2 s en tout, même si l'app est prête plus tôt : sinon on ne voit rien
//   3. RÉVÉLATION : l'app est dévoilée (durée selon la catégorie)
// Le service appelle signalerPret() quand le vrai écran de l'app est arrivé.
// « facteur » = la vitesse choisie dans les réglages : 2 = deux fois plus lent (et plus long),
// 0,5 = deux fois plus rapide. 1 = la durée par défaut.
class AnimationView(context: Context, private val scene: Scene, categorie: Categorie, private val facteur: Float = 1f) :
    SurfaceView(context), SurfaceHolder.Callback {

    private val dureeIntro = (categorie.intro * facteur).toLong().coerceAtLeast(1L)
    private val dureeRevelation = (categorie.revelation * facteur).toLong().coerceAtLeast(1L)
    // Durée minimale avant la révélation (1,2 s, ajustée par la vitesse choisie dans les réglages).
    // Les scènes « SansMinimum » (messages, calculatrice…) n'en ont pas : elles s'effacent dès que
    // l'app est prête, et n'attendent jamais plus de 0,9 s.
    private val sansMinimum = scene is SansMinimum
    private val dureeMin = if (sansMinimum) 0L else (1200 * facteur).toLong()
    private val dureeAttenteMax =
        if (sansMinimum) maxOf((900 * facteur).toLong(), dureeIntro + 100L)
        else maxOf(3000L, dureeIntro + 200L, dureeMin + 200L)

    private var quandFini: (() -> Unit)? = null
    private val principal = Handler(Looper.getMainLooper())
    private var fil: Thread? = null
    @Volatile private var arretee = false
    @Volatile private var pret = false
    // EN ATTENTE DE CONFIRMATION : l'animation est prête mais invisible (et son temps ne court pas)
    // tant que le service ne sait pas si l'app démarre vraiment ou si c'est une simple reprise.
    @Volatile var enAttente = false
        private set
    @Volatile private var debut = 0L

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

    // La durée maximale possible (attente comprise) : sert au filet de sécurité du service.
    val dureeMax: Long get() = dureeAttenteMax + dureeRevelation

    fun jouer(fin: () -> Unit) { quandFini = fin }
    fun signalerPret() { pret = true }
    fun attendreConfirmation() { enAttente = true }
    // L'app démarre vraiment : on rend l'animation visible, et son temps part de maintenant.
    fun montrer() {
        if (!enAttente) return
        debut = SystemClock.uptimeMillis()
        enAttente = false
    }
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
        if (debut == 0L) debut = SystemClock.uptimeMillis()
        var debutRevelation = 0L

        while (!arretee) {
            // Invisible en attendant la confirmation : on dessine une image vide, et on recommence.
            if (enAttente) {
                val vide = try { holder.lockHardwareCanvas() } catch (_: Exception) { null } ?: break
                try { vide.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR) } finally { holder.unlockCanvasAndPost(vide) }
                try { Thread.sleep(16) } catch (_: InterruptedException) { break }
                continue
            }
            val maintenant = SystemClock.uptimeMillis()
            val ecoule = maintenant - debut
            // La révélation commence quand l'app est prête ET que l'animation a duré assez longtemps
            // (au moins l'intro, et au moins la durée minimale) ; ou, au pire, après l'attente maximale.
            if (debutRevelation == 0L &&
                ((ecoule >= maxOf(dureeIntro, dureeMin) && pret) || ecoule >= dureeAttenteMax)
            ) debutRevelation = maintenant

            val intro = (ecoule.toFloat() / dureeIntro).coerceIn(0f, 1f)
            val revelation = if (debutRevelation == 0L) 0f
                else ((maintenant - debutRevelation).toFloat() / dureeRevelation).coerceIn(0f, 1f)

            val toile = try { holder.lockHardwareCanvas() } catch (_: Exception) { null } ?: break
            try {
                toile.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                // le temps de la scène avance moins vite si l'animation est rallongée (facteur > 1)
                scene.dessiner(toile, outils, Instant(ecoule / 1000f / facteur, intro, revelation))
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
