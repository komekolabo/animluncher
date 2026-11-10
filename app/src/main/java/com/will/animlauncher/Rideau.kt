package com.will.animlauncher

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView

// LE RIDEAU : la surface où les animations sont dessinées, installée UNE FOIS pour toutes
// (au démarrage du service), invisible tant qu'elle ne sert pas.
//
// Pourquoi ? Avant, chaque animation créait sa propre fenêtre au moment du clic. Créer une
// fenêtre prend un peu de temps, et une app déjà ouverte en arrière-plan (Maps, Gmail…)
// s'affiche en moins de 20 ms : on la voyait AVANT que l'animation n'apparaisse.
// Avec le rideau, tout est prêt d'avance : au clic, il n'y a plus qu'à dessiner.
//
// Une « séance » = une animation jouée sur le rideau, avec ses 3 phases (voir AnimationView) :
// intro, attente (au moins 1,2 s en tout, sauf scènes SansMinimum), révélation.
class Rideau(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    class Seance(val scene: Scene, categorie: Categorie, val facteur: Float, val fin: (Seance) -> Unit) {
        val dureeIntro = (categorie.intro * facteur).toLong().coerceAtLeast(1L)
        val dureeRevelation = (categorie.revelation * facteur).toLong().coerceAtLeast(1L)
        private val sansMinimum = scene is SansMinimum
        val dureeMin = if (sansMinimum) 0L else (1200 * facteur).toLong()
        val dureeAttenteMax =
            if (sansMinimum) maxOf((900 * facteur).toLong(), dureeIntro + 100L)
            else maxOf(3000L, dureeIntro + 200L, dureeMin + 200L)
        // La durée maximale possible (attente comprise) : sert au filet de sécurité du service.
        val dureeMax: Long get() = dureeAttenteMax + dureeRevelation

        val creeeA = SystemClock.uptimeMillis()
        @Volatile var pret = false           // le vrai écran de l'app est arrivé
        @Volatile var arretee = false
        @Volatile var images = 0
        @Volatile var premiereImageA = 0L    // pour le journal : combien de temps avant la 1re image

        fun signalerPret() { pret = true }
        fun arreter() { arretee = true }
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val verrou = java.lang.Object()    // pour réveiller la boucle (wait / notifyAll)
    @Volatile private var seance: Seance? = null
    @Volatile private var vivant = false
    @Volatile var surfacePrete = false
        private set
    private var fil: Thread? = null
    private val principal = Handler(Looper.getMainLooper())
    private val outils = Outils(resources.displayMetrics.density)

    init {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
    }

    // Le rideau est posé au démarrage du service, et retiré à son arrêt : c'est le bon moment pour
    // poser / retirer aussi la fenêtre de la RÉACTION SONORE (voir ReactionSonore.kt), qui vit avec le service.
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ReactionSonore.demarrer(context)
    }

    override fun onDetachedFromWindow() {
        ReactionSonore.arreter(context)
        super.onDetachedFromWindow()
    }

    // Joue une animation tout de suite (celle qui tournait encore est arrêtée).
    fun jouer(s: Seance) {
        synchronized(verrou) {
            seance?.arretee = true
            seance = s
            verrou.notifyAll()
        }
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        surfacePrete = true
        vivant = true
        fil = Thread({ boucle() }, "rideau").apply { start() }
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, largeur: Int, hauteur: Int) {}

    override fun surfaceDestroyed(h: SurfaceHolder) {
        surfacePrete = false
        synchronized(verrou) {
            vivant = false
            verrou.notifyAll()
        }
        try { fil?.join(200) } catch (_: InterruptedException) {}
    }

    // Une image entièrement transparente : le rideau ne cache plus rien.
    private fun effacer() {
        val toile = try { holder.lockHardwareCanvas() } catch (_: Exception) { null } ?: return
        try { toile.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR) } finally { holder.unlockCanvasAndPost(toile) }
    }

    // LA BOUCLE (dans son propre fil) : elle dort tant qu'il n'y a rien à jouer,
    // et se réveille dès qu'une séance arrive.
    private fun boucle() {
        // PRIORITÉ « affichage » (10/11) : ce fil passe avant les travaux de fond du téléphone (et les nôtres,
        // comme le dessin des widgets), pour que l'animation ne saccade pas quand le téléphone est occupé.
        try { Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY) } catch (_: Throwable) {}
        effacer()
        while (vivant) {
            val s = synchronized(verrou) {
                if (seance == null && vivant) verrou.wait(500)
                seance
            } ?: continue
            jouerSeance(s)
            effacer()
            synchronized(verrou) { if (seance === s) seance = null }
            principal.post { s.fin(s) }
        }
    }

    private fun jouerSeance(s: Seance) {
        val debut = SystemClock.uptimeMillis()
        var debutRevelation = 0L
        // LE RYTHME DES IMAGES (10/11). Avant : on dessinait, puis on attendait 16 ms -> une image toutes les
        // « 16 ms + le temps du dessin », donc un rythme irrégulier (et plus lent quand le dessin est lourd).
        // Maintenant : un rendez-vous fixe toutes les 16,67 ms (60 images par seconde, pile) ; on n'attend que
        // le temps qui RESTE jusqu'au rendez-vous. « nanoTime » = une horloge très précise (milliardièmes de seconde).
        val pas = 16_666_667L
        var rendezVous = System.nanoTime() + pas
        while (vivant && !s.arretee) {
            val maintenant = SystemClock.uptimeMillis()
            val ecoule = maintenant - debut
            // La révélation commence quand l'app est prête ET que l'animation a duré assez longtemps
            // (au moins l'intro, et au moins la durée minimale) ; ou, au pire, après l'attente maximale.
            if (debutRevelation == 0L &&
                ((ecoule >= maxOf(s.dureeIntro, s.dureeMin) && s.pret) || ecoule >= s.dureeAttenteMax)
            ) debutRevelation = maintenant

            val intro = (ecoule.toFloat() / s.dureeIntro).coerceIn(0f, 1f)
            val revelation = if (debutRevelation == 0L) 0f
                else ((maintenant - debutRevelation).toFloat() / s.dureeRevelation).coerceIn(0f, 1f)

            val toile = try { holder.lockHardwareCanvas() } catch (_: Exception) { null } ?: return
            try {
                toile.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                // le temps de la scène avance moins vite si l'animation est rallongée (facteur > 1)
                s.scene.dessiner(toile, outils, Instant(ecoule / 1000f / s.facteur, intro, revelation))
            } catch (_: Exception) {
                // une erreur de dessin ne doit jamais bloquer l'écran : on arrête l'animation
                s.arretee = true
            } finally {
                holder.unlockCanvasAndPost(toile)
            }
            if (s.images == 0) s.premiereImageA = SystemClock.uptimeMillis()
            s.images++

            if (revelation >= 1f) return
            val reste = rendezVous - System.nanoTime()
            if (reste > 0) {
                try { Thread.sleep(reste / 1_000_000L, (reste % 1_000_000L).toInt()) } catch (_: InterruptedException) { return }
                rendezVous += pas
            } else {
                rendezVous = System.nanoTime() + pas       // le dessin a été trop long : on repart d'ici (pas de rattrapage en rafale)
            }
        }
    }
}
