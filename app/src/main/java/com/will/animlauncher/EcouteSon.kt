package com.will.animlauncher

import android.media.audiofx.Visualizer
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max

// L'ÉCOUTE DU SON (pour la « réaction sonore », voir ReactionSonore.kt).
//
// Android fournit un outil, le « Visualizer », qui donne en direct un RÉSUMÉ de ce qui sort du
// haut-parleur : pour chaque tranche de fréquences (graves, médiums, aigus…), sa force du moment.
// Ce ne sont que des nombres, environ 20 fois par seconde. On ne reçoit PAS le son lui-même,
// on n'enregistre rien, on ne garde rien : chaque mesure remplace la précédente.
// (Android exige quand même la permission « micro » pour cet outil : c'est sa règle.)
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

    private var visu: Visualizer? = null

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
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, donnees: ByteArray?, frequence: Int) {}
                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, frequence: Int) {
                    if (fft != null) analyser(fft, frequence)
                }
            }, Visualizer.getMaxCaptureRate(), false, true)
            v.setEnabled(true)
            visu = v
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
        try { v.setEnabled(false) } catch (_: Throwable) {}
        try { v.release() } catch (_: Throwable) {}
        grave = 0f; medium = 0f; aigu = 0f; claque = 0f
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
        val hzParCase = frequenceMilliHz / 1000f / fft.size
        if (hzParCase <= 0f) return
        val g = bande(fft, hzParCase, 43f, 170f)
        val m = bande(fft, hzParCase, 300f, 2000f)
        val a = bande(fft, hzParCase, 4000f, 12000f)
        val sec = bande(fft, hzParCase, 2000f, 5000f)

        if (g + m + a < 1.5f) {                        // silence : tout retombe
            grave = 0f; medium = 0f; aigu = 0f; claque = 0f; ancienSec = sec
            return
        }
        // la référence descend très lentement, et remonte tout de suite si le son est plus fort
        refGrave = max(max(refGrave * 0.997f, g), 4f)
        refMedium = max(max(refMedium * 0.997f, m), 4f)
        refAigu = max(max(refAigu * 0.997f, a), 2f)
        grave = niveau(g, refGrave, 0.30f)
        medium = niveau(m, refMedium, 0.20f)
        aigu = niveau(a, refAigu, 0.25f)
        claque = ((sec - ancienSec) / refMedium * 3f).coerceIn(0f, 1f)     // ce qui vient d'augmenter d'un coup
        ancienSec = sec

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
