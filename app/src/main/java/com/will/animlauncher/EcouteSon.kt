package com.will.animlauncher

import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max

// L'ÉCOUTE DU SON (pour la « réaction sonore », voir ReactionSonore.kt).
//
// Android fournit un outil, le « Visualizer », qui donne en direct un RÉSUMÉ de ce qui sort du
// haut-parleur : pour chaque tranche de fréquences (graves, médiums, aigus…), sa force du moment.
// Ce ne sont que des nombres. On ne reçoit PAS le son lui-même, on n'enregistre rien, on ne garde
// rien : chaque mesure remplace la précédente.
// (Android exige quand même la permission « micro » pour cet outil : c'est sa règle.)
//
// LE RYTHME (10/11, Will : « pas très en rythme avec la musique »). Deux corrections :
//  1) Avant, Android nous envoyait les mesures 20 fois par seconde au plus : un coup de grosse caisse
//     pouvait être vu jusqu'à 50 ms en retard. Maintenant c'est NOUS qui demandons une mesure toutes
//     les 16 ms (60 fois par seconde), dans un petit fil à part (« son »).
//  2) Avant, « grave » = la force des basses. Une basse qui dure restait donc allumée en continu, et on
//     ne voyait pas les coups. Maintenant « grave » = surtout ce qui DÉPASSE la moyenne de la dernière
//     demi-seconde (le coup qui arrive), plus un peu de la force (pour ne pas s'éteindre entre deux coups).
//
// Ce fichier range ces nombres dans 5 valeurs simples, de 0 à 1, que les dessins viennent lire :
//   grave   : la grosse caisse, la basse            (43 à 170 Hz)
//   medium  : les voix, la mélodie                  (300 à 2 000 Hz)
//   aigu    : les cymbales, les « s »               (4 000 à 12 000 Hz)
//   hauteur : la note dominante de la mélodie (0 = grave … 1 = aiguë)
//   claque  : un son sec qui vient d'arriver (caisse claire, claquement de mains)
object EcouteSon {
    @Volatile var grave = 0f
        private set
    @Volatile var medium = 0f
        private set
    @Volatile var aigu = 0f
        private set
    @Volatile var hauteur = 0.5f
        private set
    @Volatile var claque = 0f
        private set

    @Volatile private var visu: Visualizer? = null
    private var fil: HandlerThread? = null         // le fil à part qui demande les mesures
    private var poste: Handler? = null
    private var tampon = ByteArray(0)              // la mesure en cours (réutilisée : aucune mémoire gaspillée)
    private var derniereMesure = 0L
    private var ratees = 0
    private var moyGrave = 0f                      // la moyenne des basses sur la dernière demi-seconde
    private const val PAS = 16L                    // une mesure toutes les 16 ms

    // Pour le diagnostic (fenêtre du rond) : la dernière erreur, et combien de mesures sont arrivées.
    @Volatile var derniereErreur: String? = null
        private set
    @Volatile var mesures = 0
        private set

    // Le « volume de référence » de chaque tranche : le plus fort entendu récemment. On compare
    // chaque mesure à cette référence, pour réagir aussi bien à une musique douce qu'à une forte.
    private var refGrave = 4f
    private var refMedium = 4f
    private var refAigu = 4f
    private var ancienSec = 0f

    val enMarche: Boolean get() = visu != null

    // Commence à écouter. Renvoie false si le téléphone refuse (permission absente, outil indisponible).
    fun demarrer(): Boolean {
        if (visu != null) return true
        return try {
            val v = Visualizer(0)                      // 0 = tout ce que le téléphone joue, toutes apps confondues
            v.setEnabled(false)                        // (les réglages se font à l'arrêt)
            val tailles = Visualizer.getCaptureSizeRange()
            v.setCaptureSize(1024.coerceIn(tailles[0], tailles[1]))
            // « normalisé » : la mesure ne dépend pas du volume réglé sur le téléphone
            try { v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED) } catch (_: Throwable) {}
            tampon = ByteArray(v.captureSize)
            v.setEnabled(true)
            visu = v
            ratees = 0; moyGrave = 0f; derniereMesure = 0L
            // le fil à part : il demande une mesure, l'analyse, et recommence 16 ms plus tard
            val t = HandlerThread("son", Process.THREAD_PRIORITY_MORE_FAVORABLE).apply { start() }
            fil = t
            poste = Handler(t.looper).also { it.post(tic) }
            derniereErreur = null
            AnimApp.noter("son : écoute démarrée")
            true
        } catch (e: Throwable) {
            derniereErreur = "${e.javaClass.simpleName} ${e.message ?: ""}".trim()
            AnimApp.noter("son : écoute impossible ($derniereErreur)")
            false
        }
    }

    fun arreter() {
        val v = visu ?: return
        visu = null
        val t = fil; val p = poste
        fil = null; poste = null
        // on éteint l'outil DANS le fil des mesures (jamais pendant qu'une mesure est en cours), puis on ferme le fil
        val eteindre = Runnable {
            try { v.setEnabled(false) } catch (_: Throwable) {}
            try { v.release() } catch (_: Throwable) {}
            t?.quitSafely()
        }
        if (p == null || !p.post(eteindre)) eteindre.run()
        grave = 0f; medium = 0f; aigu = 0f; claque = 0f
    }

    // UNE MESURE (dans le fil « son »), puis rendez-vous dans 16 ms.
    private val tic = object : Runnable {
        override fun run() {
            val v = visu ?: return
            val p = poste ?: return
            try {
                if (v.getFft(tampon) == Visualizer.SUCCESS) { ratees = 0; analyser(tampon, v.samplingRate) }
                else ratees++
            } catch (_: Throwable) { ratees++ }
            // FILET DE SÉCURITÉ : si le téléphone refuse 30 demandes de suite, on revient à l'ancienne
            // méthode (Android nous envoie lui-même les mesures, 20 fois par seconde).
            if (ratees >= 30) {
                try {
                    v.setEnabled(false)
                    v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(visualizer: Visualizer?, donnees: ByteArray?, frequence: Int) {}
                        override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, frequence: Int) {
                            if (fft != null) analyser(fft, frequence)
                        }
                    }, Visualizer.getMaxCaptureRate(), false, true)
                    v.setEnabled(true)
                    AnimApp.noter("son : mesures à la demande refusées, retour à l'ancienne méthode")
                } catch (e: Throwable) {
                    derniereErreur = "${e.javaClass.simpleName} ${e.message ?: ""}".trim()
                }
                return
            }
            p.postDelayed(this, PAS)
        }
    }

    // La force moyenne entre deux fréquences. « fft » range les mesures par paires (partie réelle,
    // partie imaginaire) : la force d'une case = la longueur de ce petit vecteur.
    private fun bande(fft: ByteArray, hzParCase: Float, de: Float, a: Float): Float {
        val cases = fft.size / 2
        val k1 = (de / hzParCase).toInt().coerceIn(1, cases - 1)
        val k2 = (a / hzParCase).toInt().coerceIn(k1, cases - 1)
        var somme = 0f
        for (k in k1..k2) somme += hypot(fft[2 * k].toFloat(), fft[2 * k + 1].toFloat())
        return somme / (k2 - k1 + 1)
    }

    private fun niveau(valeur: Float, reference: Float, seuil: Float): Float =
        ((valeur / reference - seuil) / (1f - seuil)).coerceIn(0f, 1f)

    private fun analyser(fft: ByteArray, frequenceMilliHz: Int) {
        if (fft.size < 16) return
        mesures++
        // le temps depuis la mesure d'avant (pour que les réglages ne dépendent pas du nombre de mesures par seconde)
        val maintenant = SystemClock.uptimeMillis()
        val dt = ((maintenant - derniereMesure) / 1000f).coerceIn(0.005f, 0.1f)
        derniereMesure = maintenant
        val hzParCase = frequenceMilliHz / 1000f / fft.size
        if (hzParCase <= 0f) return
        val g = bande(fft, hzParCase, 43f, 170f)
        val m = bande(fft, hzParCase, 300f, 2000f)
        val a = bande(fft, hzParCase, 4000f, 12000f)
        val sec = bande(fft, hzParCase, 2000f, 5000f)

        if (g + m + a < 1.5f) {                        // silence : tout retombe
            grave = 0f; medium = 0f; aigu = 0f; claque = 0f; ancienSec = sec; moyGrave = 0f
            return
        }
        // la référence descend très lentement, et remonte tout de suite si le son est plus fort
        val baisse = exp(-0.06f * dt)                  // (même vitesse qu'avant : -6 % par seconde)
        refGrave = max(max(refGrave * baisse, g), 4f)
        refMedium = max(max(refMedium * baisse, m), 4f)
        refAigu = max(max(refAigu * baisse, a), 2f)
        // LES BASSES : surtout le COUP (ce qui dépasse la moyenne de la dernière demi-seconde), plus un peu de la force
        val coup = ((g - moyGrave * 1.15f) / (refGrave * 0.45f)).coerceIn(0f, 1f)
        moyGrave += (g - moyGrave) * (1f - exp(-dt / 0.5f))
        grave = (0.40f * niveau(g, refGrave, 0.30f) + 0.85f * coup).coerceIn(0f, 1f)
        medium = niveau(m, refMedium, 0.20f)
        aigu = niveau(a, refAigu, 0.25f)
        // ce qui vient d'augmenter d'un coup : on compare à « ancienSec », qui suit le son avec ~50 ms de retard
        claque = ((sec - ancienSec) / refMedium * 3f).coerceIn(0f, 1f)
        ancienSec += (sec - ancienSec) * (1f - exp(-dt / 0.05f))

        // la note dominante : la case la plus forte entre 200 et 2 000 Hz
        val cases = fft.size / 2
        val k1 = (200f / hzParCase).toInt().coerceIn(1, cases - 1)
        val k2 = (2000f / hzParCase).toInt().coerceIn(k1, cases - 1)
        var meilleure = k1
        var force = 0f
        for (k in k1..k2) {
            val f = hypot(fft[2 * k].toFloat(), fft[2 * k + 1].toFloat())
            if (f > force) { force = f; meilleure = k }
        }
        // échelle « musicale » (logarithmique) : 200 Hz -> 0, 2 000 Hz -> 1
        hauteur = (ln(meilleure * hzParCase / 200f) / ln(10f)).coerceIn(0f, 1f)
    }
}
