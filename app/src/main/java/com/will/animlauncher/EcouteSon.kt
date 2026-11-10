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
import kotlin.math.min
import kotlin.math.pow

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
// LES BASSES QUI TAPENT (10/11, Will : « je veux voir la lumière bien vibrer sur la phonk, ça paraît random ») :
//  3) Le réglage « normalisé » d'Android remet chaque mesure au volume maximum : un passage calme et un
//     passage fort se ressemblaient. On lit donc EN PLUS le vrai volume du moment (« getMeasurementPeakRms »)
//     et on multiplie : la lumière baisse dans les passages calmes et remonte quand ça envoie.
//  4) Musiques très compressées (phonk : la basse ne s'arrête jamais) : on retient aussi le niveau le plus BAS
//     entendu récemment, et on étire ce qui se passe entre ce plancher et le maximum (plus de contraste).
//  5) Nouveau signal « coup » : il saute à 1 à chaque coup de basse puis retombe en ~0,15 s. C'est lui qui
//     fait battre et vibrer tout le tour de l'écran (voir ReactionSonore.kt).
//
// LE SPECTRE (10/11 au soir, Will : « je ne ressens pas la synchronisation, je veux voir la mélodie sur les bords ») :
//  6) Avant, on résumait le son en 3 zones (graves, médiums, aigus) : trop grossier, le dessin semblait au hasard.
//     Maintenant on découpe le son en 40 tranches, de 40 Hz (très grave) à 12 000 Hz (très aigu), comme les
//     40 barres d'un égaliseur : « spectre ». Chaque note qu'on entend fait monter SA tranche.
//  7) Écouteurs Bluetooth : le son met ~0,2 s à arriver aux oreilles, alors que nous le « voyons » tout de suite.
//     « retardMs » permet de RETARDER ce qu'on publie d'autant, pour que l'image et le son tombent ensemble.
//
// LES ATTAQUES ET LE « ÇA ENVOIE » (10/11 au soir, après comparaison d'une vidéo de Will avec sa musique) :
//  8) Le dessin suivait le NIVEAU du son : sur une musique pleine, tout restait allumé et on ne sentait pas le
//     rythme. Maintenant on publie aussi les ATTAQUES (« attaques », 40 tranches) : une tranche ne s'allume que
//     quand elle vient de MONTER nettement plus que d'habitude (une note, un coup qui ARRIVE), puis elle retombe
//     en ~0,2 s. Entre deux coups il fait presque noir : c'est ce contraste qui donne le rythme.
//     « frappe » fait pareil pour le coup de basse (retombe en ~0,25 s, assez long pour être bien vu).
//  9) « fort » : vrai quand la musique S'EMBALLE (refrain, drop). Voir « moment() » plus bas : on compare la
//     puissance du moment (volume + basses, lissée) au plus fort de la dernière minute. Au-dessus de 62 % on
//     passe en « ça envoie », en dessous de 38 % on revient au calme (l'écart évite que ça clignote).
//
// Ce fichier range ces nombres dans 6 valeurs simples, de 0 à 1, que les dessins viennent lire :
//   coup    : un coup de basse vient de tomber (saute à 1, retombe vite)
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
    @Volatile var coup = 0f
        private set

    // LE SPECTRE : 40 tranches de 0 à 1, de la plus grave (0) à la plus aiguë (39). Lu par le dessin à chaque image.
    const val BANDES = 40
    val spectre = FloatArray(BANDES)
    @Volatile var retardMs = 0                     // de combien retarder ce qu'on publie (Bluetooth), en millisecondes
    private val valeur = FloatArray(BANDES)        // le spectre « en direct » (avant le retard)
    private val bruts = FloatArray(BANDES)
    private val refBande = FloatArray(BANDES) { 1.5f }      // le plus fort entendu récemment, tranche par tranche
    private var forces = FloatArray(0)
    private const val MEMOIRE = 24                 // on garde les 24 dernières mesures (~0,4 s) pour pouvoir retarder
    private val memSpectre = Array(MEMOIRE) { FloatArray(BANDES) }
    private val memCoup = FloatArray(MEMOIRE)
    private var rangMem = 0
    private var coupVif = 0f                       // le coup « en direct » (avant le retard)

    // LES ATTAQUES (point 8) : 40 tranches de 0 à 1. Une tranche saute vers 1 quand son son vient d'arriver, puis retombe.
    val attaques = FloatArray(BANDES)
    @Volatile var frappe = 0f                      // le coup de basse, version « attaque » (saute à 1, retombe en ~0,25 s)
        private set
    private val elans = FloatArray(BANDES)         // les attaques « en direct » (avant le retard)
    private val avant = FloatArray(BANDES)         // la force de chaque tranche à la mesure d'avant
    private val moyMontee = FloatArray(BANDES)     // de combien chaque tranche monte « d'habitude » (moyenne sur 1,5 s)
    private val memAttaques = Array(MEMOIRE) { FloatArray(BANDES) }
    private val memFrappe = FloatArray(MEMOIRE)
    private var frappeVive = 0f
    private var avantGrave = 0f
    private var moyFrappe = 0f
    private var ancienne = ByteArray(0)            // la mesure d'avant (Android redonne parfois deux fois la même)

    // LE « ÇA ENVOIE » (point 9)
    @Volatile var fort = false                     // vrai = la musique s'emballe (refrain, drop)
        private set
    @Volatile var elanMoment = 0                   // pour le diagnostic : la puissance du moment, en % du plus fort récent
        private set
    private var puissance = 0f                     // la puissance du moment, lissée
    private var sommet = 0f                        // le plus fort de la dernière minute
    private var creux = -1f                        // le plus calme entendu récemment (-1 = pas encore connu)
    private var duree = 0f                         // depuis combien de secondes le son joue sans silence

    // le vrai volume du moment (voir le point 3 en haut)
    private var mesureOk = false
    private val mesure = Visualizer.MeasurementPeakRms()
    private var refVolume = 0.0001f
    private var basGrave = 0f                      // le plancher des basses (voir le point 4)
    private var depuisCoup = 1f                    // secondes depuis le dernier coup

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
            mesureOk = try { v.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS) == Visualizer.SUCCESS } catch (_: Throwable) { false }
            refVolume = 0.0001f; basGrave = 0f; depuisCoup = 1f; coup = 0f; coupVif = 0f
            valeur.fill(0f); spectre.fill(0f); memCoup.fill(0f); for (m in memSpectre) m.fill(0f)
            elans.fill(0f); attaques.fill(0f); avant.fill(0f); moyMontee.fill(0f); memFrappe.fill(0f); for (m in memAttaques) m.fill(0f)
            frappe = 0f; frappeVive = 0f; avantGrave = 0f; moyFrappe = 0f; ancienne = ByteArray(0)
            fort = false; elanMoment = 0; puissance = 0f; sommet = 0f; creux = -1f; duree = 0f
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
        grave = 0f; medium = 0f; aigu = 0f; claque = 0f; coup = 0f; frappe = 0f; fort = false
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

    // Le vrai volume du moment, de 0 à 1, comparé au plus fort entendu récemment.
    // (Android le donne en « millibels » : -9600 = silence, 0 = le maximum. Si le téléphone ne sait pas, on renvoie 1.)
    private fun volume(dt: Float): Float {
        if (!mesureOk) return 1f
        val v = visu ?: return 1f
        try { if (v.getMeasurementPeakRms(mesure) != Visualizer.SUCCESS) return 1f } catch (_: Throwable) { return 1f }
        if (mesure.mRms <= -9000) return 1f
        val force = 10f.pow(mesure.mRms / 2000f)
        refVolume = max(max(refVolume * exp(-dt / 15f), force), 0.0001f)
        return (force / refVolume).coerceIn(0f, 1f).pow(0.6f)
    }

    // Range la mesure du moment dans la mémoire, et PUBLIE celle d'il y a « retardMs » (ou la toute dernière si 0).
    private fun publier() {
        System.arraycopy(valeur, 0, memSpectre[rangMem], 0, BANDES)
        memCoup[rangMem] = coupVif
        System.arraycopy(elans, 0, memAttaques[rangMem], 0, BANDES)
        memFrappe[rangMem] = frappeVive
        val recul = (retardMs / PAS.toInt()).coerceIn(0, MEMOIRE - 1)
        val j = (rangMem - recul + MEMOIRE) % MEMOIRE
        System.arraycopy(memSpectre[j], 0, spectre, 0, BANDES)
        coup = memCoup[j]
        System.arraycopy(memAttaques[j], 0, attaques, 0, BANDES)
        frappe = memFrappe[j]
        rangMem = (rangMem + 1) % MEMOIRE
    }

    // CALME OU « ÇA ENVOIE » ? (point 9). « p » = la puissance brute de cette mesure (0 pendant un silence).
    //  - « puissance » suit p en douceur : elle monte en ~0,35 s et redescend en ~1 s (un seul coup ne suffit pas).
    //  - « sommet » = le plus fort de la dernière minute : c'est « à fond » pour CE morceau, à CE volume.
    //  - « creux » = le plus calme entendu récemment. Tant qu'on n'a pas entendu un passage nettement plus calme
    //    que le sommet, on reste en « calme » : sinon un morceau qui commence doucement serait pris pour un refrain
    //    (au début, le plus fort entendu… c'est forcément ce qu'on entend).
    private fun moment(p: Float, dt: Float) {
        puissance += (p - puissance) * (1f - exp(-dt / (if (p > puissance) 0.35f else 1f)))
        sommet = max(sommet * exp(-dt / 60f), puissance)
        if (p <= 0f) duree = 0f else duree += dt
        if (sommet < 0.001f) { fort = false; elanMoment = 0; return }
        // le creux : connu après 2 s de son ; il descend tout de suite, et remonte très lentement (en ~1 minute)
        if (duree > 2f) creux = if (creux < 0f) puissance else min(creux + (sommet - creux) * (1f - exp(-dt / 60f)), puissance)
        val r = puissance / sommet
        elanMoment = (r * 100f).toInt()
        if (!fort && r > 0.62f && creux >= 0f && sommet > 1.6f * creux) fort = true
        if (fort && r < 0.38f) fort = false
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
        val vol = volume(dt)
        val g = bande(fft, hzParCase, 30f, 180f) * vol
        val m = bande(fft, hzParCase, 300f, 2000f) * vol
        val a = bande(fft, hzParCase, 4000f, 12000f) * vol
        val sec = bande(fft, hzParCase, 2000f, 5000f) * vol
        depuisCoup += dt
        coupVif *= exp(-dt / 0.15f)                    // le coup retombe tout seul
        frappeVive *= exp(-dt / 0.25f)
        val retombee = exp(-dt / 0.22f)                // les attaques retombent en ~0,2 s
        // Est-ce une NOUVELLE mesure ? (on en demande 60 par seconde, mais Android n'en fabrique parfois que ~40 :
        // il redonne alors la même. Une mesure identique ne doit pas compter comme « rien n'a monté ».)
        val neuve = !java.util.Arrays.equals(fft, ancienne)
        if (neuve) {
            if (ancienne.size != fft.size) ancienne = ByteArray(fft.size)
            System.arraycopy(fft, 0, ancienne, 0, fft.size)
        }

        if (g + m + a < 1.5f) {                        // silence : tout retombe
            grave = 0f; medium = 0f; aigu = 0f; claque = 0f; ancienSec = sec; moyGrave = 0f; basGrave = 0f
            valeur.fill(0f); elans.fill(0f); avant.fill(0f); avantGrave = 0f
            moment(0f, dt)
            publier()
            return
        }
        // LE SPECTRE : la force de chacune des 40 tranches.
        val nb = fft.size / 2
        if (forces.size != nb) forces = FloatArray(nb)
        var total = 0f                                 // la puissance : toutes les fréquences…
        var basses = 0f                                // … et les basses (30 à 180 Hz), qui compteront triple
        val finBasses = (180f / hzParCase).toInt().coerceIn(1, nb - 1)
        for (k in 1 until nb) {
            forces[k] = hypot(fft[2 * k].toFloat(), fft[2 * k + 1].toFloat()) * vol
            total += forces[k]
            if (k <= finBasses) basses += forces[k]
        }
        moment(total + 3f * basses, dt)
        val oubli = exp(-dt / 8f)
        var refMax = 1.5f
        for (b in 0 until BANDES) {
            // la tranche b va de f1 à f2 (chaque tranche est ~15 % plus aiguë que la précédente : 40 Hz -> 12 000 Hz)
            val k1 = 40f * 300f.pow(b / BANDES.toFloat()) / hzParCase
            val k2 = 40f * 300f.pow((b + 1) / BANDES.toFloat()) / hzParCase
            val v = if (k2 - k1 < 1.5f) {
                // tranche plus fine qu'une case de mesure (dans les graves) : on lit ENTRE deux cases
                val kc = ((k1 + k2) / 2f).coerceIn(1f, nb - 2f)
                val k0 = kc.toInt()
                forces[k0] + (forces[k0 + 1] - forces[k0]) * (kc - k0)
            } else {
                var plusFort = 0f
                for (k in k1.toInt().coerceAtLeast(1)..k2.toInt().coerceAtMost(nb - 1)) plusFort = max(plusFort, forces[k])
                plusFort
            }
            bruts[b] = v
            refBande[b] = max(max(refBande[b] * oubli, v), 1.5f)
            refMax = max(refMax, refBande[b])
        }
        for (b in 0 until BANDES) {
            // chaque tranche est comparée à SON maximum récent (sinon on ne verrait que les graves), mais une
            // tranche presque muette n'est pas gonflée artificiellement (au moins 20 % du maximum général)
            val n = bruts[b] / max(refBande[b], 0.2f * refMax)
            val cible = ((n - 0.3f) / 0.7f).coerceIn(0f, 1f).pow(1.3f)
            // monte tout de suite, retombe vite (~0,09 s) : c'est ce qui donne l'impression de « coller » au son
            valeur[b] = if (cible > valeur[b]) cible else valeur[b] + (cible - valeur[b]) * (1f - exp(-dt / 0.09f))
            // L'ATTAQUE de la tranche (point 8) : de combien elle vient de MONTER, comparé à ses montées habituelles.
            elans[b] *= retombee
            if (neuve) {
                val plancher = max(refBande[b], 0.2f * refMax)
                val montee = max(0f, bruts[b] - avant[b])
                avant[b] = bruts[b]
                moyMontee[b] += (montee - moyMontee[b]) * (1f - exp(-dt / 1.5f))
                // une attaque = une montée au moins 2 à 3 fois plus forte que d'habitude, dans une tranche qui s'entend
                // (le « 0.03 x plancher » évite de prendre le petit bruit de fond d'une tranche muette pour une note)
                var attaque = (montee / (moyMontee[b] * 4f + 0.03f * plancher) - 0.6f).coerceIn(0f, 1f)
                if (bruts[b] < 0.25f * plancher) attaque = 0f
                elans[b] = max(elans[b], attaque)
            }
        }
        // la référence descend très lentement, et remonte tout de suite si le son est plus fort
        val baisse = exp(-0.06f * dt)                  // (même vitesse qu'avant : -6 % par seconde)
        refGrave = max(max(refGrave * baisse, g), 4f)
        refMedium = max(max(refMedium * baisse, m), 4f)
        refAigu = max(max(refAigu * baisse, a), 2f)
        // LES BASSES : surtout le COUP (ce qui dépasse la moyenne de la dernière demi-seconde), plus un peu de la force
        // le plancher : il remonte doucement vers le maximum (en ~3 s) et redescend tout de suite si le son baisse
        basGrave = min(basGrave + (refGrave - basGrave) * (1f - exp(-dt / 3f)), g)
        val ecart = max(refGrave - basGrave, refGrave * 0.25f)      // la place entre le plancher et le maximum
        val tenue = ((g - (refGrave - ecart)) / ecart).coerceIn(0f, 1f)             // la basse qui dure
        val elan = ((g - moyGrave * 1.12f) / (ecart * 0.55f)).coerceIn(0f, 1f)      // la basse qui ARRIVE
        moyGrave += (g - moyGrave) * (1f - exp(-dt / 0.4f))
        grave = (0.35f * tenue + 0.85f * elan).coerceIn(0f, 1f)
        // UN COUP : un élan net, et pas deux coups à moins de 0,11 s (sinon ça grésille)
        if (elan > 0.35f && depuisCoup > 0.11f) {
            coupVif = max(coupVif, 0.55f + 0.45f * elan)
            depuisCoup = 0f
        }
        // LA FRAPPE : le même coup de basse, mais mesuré comme une attaque (comparé aux montées habituelles des basses)
        if (neuve) {
            val montee = max(0f, g - avantGrave)
            avantGrave = g
            moyFrappe += (montee - moyFrappe) * (1f - exp(-dt / 1.5f))
            frappeVive = max(frappeVive, (montee / (moyFrappe * 5f + 0.03f * refGrave) - 0.7f).coerceIn(0f, 1f))
        }
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
        publier()
    }
}
