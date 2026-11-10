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
import kotlin.math.sqrt

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
//  5) Signal « coup » : il saute à 1 à chaque coup de basse puis retombe en ~0,15 s. C'est lui qui
//     fait battre tout le tour de l'écran (voir ReactionSonore.kt).
//
// LE SPECTRE (10/11 au soir, Will : « je veux voir la mélodie sur les bords ») :
//  6) On découpe le son en 40 tranches, de 40 Hz (très grave) à 12 000 Hz (très aigu), comme les
//     40 barres d'un égaliseur : « spectre ». Chaque note qu'on entend fait monter SA tranche.
//  7) Écouteurs Bluetooth : le son met ~0,2 s à arriver aux oreilles, alors que nous le « voyons » tout de suite.
//     « retardMs » permet de RETARDER ce qu'on publie d'autant, pour que l'image et le son tombent ensemble.
//
// LES 5 PALIERS (10/11, 22h, idée de Will) :
//  8) « palier » dit à quel point la musique envoie, de 1 (très lent) à 5 (très fort), sur une échelle FIXE :
//     la même pour tous les morceaux (on ne compare plus un morceau à lui-même). Voir « situer() » plus bas.
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

    // LE PALIER (point 8) : 1 = très lent, 2 = calme, 3 = moyen, 4 = ça envoie, 5 = très fort.
    // Les 4 frontières entre paliers, sur l'échelle d'« énergie » (voir situer()). Calées le 10/11 sur 5 extraits
    // envoyés par Will : son morceau lent mesure ~0,44, ses deux moyens ~0,61 et ~0,64, son rapide ~0,93,
    // et le drop qu'il veut « au moins rouge » ~0,68 (précédé d'une intro à ~0,31).
    // ATTENTION : ces chiffres viennent d'une SIMULATION sur le son des vidéos. Le téléphone ne mesure pas pareil
    // (une phonk simulée « vert » est restée bleue chez Will) : à recaler avec les mesures affichées à l'écran.
    val SEUILS = floatArrayOf(0.27f, 0.52f, 0.655f, 1.08f)
    @Volatile var palier = 3
        private set
    @Volatile var energieVue = 0                   // pour le diagnostic : l'énergie du moment x 100
        private set
    private var energie = 0f
    private var habitude = 0f                      // l'énergie « habituelle » de ce morceau (moyenne lente, ~25 s)
    @Volatile private var creteVive = -1f          // à quel point le son est « plein » en ce moment (-1 = inconnu)
    private var onde = ByteArray(0)                // la forme du son (réutilisée : aucune mémoire gaspillée)
    private var duree = 0f                         // depuis combien de secondes le son joue sans vrai silence
    private var muet = 0f                          // depuis combien de secondes c'est le silence
    private var depuisPalier = 0f                  // depuis combien de secondes on est sur ce palier

    // POUR LE RÉGLAGE (provisoire) : les ingrédients de l'énergie, lissés sur 2-3 s, que le tour affiche en petit
    // en bas de l'écran. Will filme son écran, et on lit ce que le VRAI téléphone mesure sur chaque morceau.
    @Volatile var pleinVu = 0                      // à quel point le son est plein, x 100
        private set
    @Volatile var bassesVues = 0                   // la part des basses dans le son, x 100
        private set
    @Volatile var coupsVus = 0                     // les coups de basse par seconde, x 10
        private set
    @Volatile var secsVus = 0                      // les sons secs (caisse claire, cloche…) par seconde, x 10
        private set
    private var pleinLisse = 0f
    private var bassesLisses = 0f
    private var cadenceCoups = 0f
    private var cadenceSecs = 0f
    private var claqueAvant = 0f

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
            onde = ByteArray(v.captureSize)
            mesureOk = try { v.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS) == Visualizer.SUCCESS } catch (_: Throwable) { false }
            refVolume = 0.0001f; basGrave = 0f; depuisCoup = 1f; coup = 0f; coupVif = 0f
            valeur.fill(0f); spectre.fill(0f); memCoup.fill(0f); for (m in memSpectre) m.fill(0f)
            palier = 3; energieVue = 0; energie = 0f; habitude = 0f; creteVive = -1f; duree = 0f; muet = 0f; depuisPalier = 0f
            pleinLisse = 0f; bassesLisses = 0f; cadenceCoups = 0f; cadenceSecs = 0f; claqueAvant = 0f
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
        grave = 0f; medium = 0f; aigu = 0f; claque = 0f; coup = 0f; palier = 3
    }

    // UNE MESURE (dans le fil « son »), puis rendez-vous dans 16 ms.
    private val tic = object : Runnable {
        override fun run() {
            val v = visu ?: return
            val p = poste ?: return
            try {
                if (v.getFft(tampon) == Visualizer.SUCCESS) {
                    ratees = 0
                    // la forme du son (pour savoir s'il est « plein », voir lireCrete), puis l'analyse
                    if (v.getWaveForm(onde) == Visualizer.SUCCESS) lireCrete(onde)
                    analyser(tampon, v.samplingRate)
                }
                else ratees++
            } catch (_: Throwable) { ratees++ }
            // FILET DE SÉCURITÉ : si le téléphone refuse 30 demandes de suite, on revient à l'ancienne
            // méthode (Android nous envoie lui-même les mesures, 20 fois par seconde).
            if (ratees >= 30) {
                try {
                    v.setEnabled(false)
                    v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(visualizer: Visualizer?, donnees: ByteArray?, frequence: Int) {
                            if (donnees != null) lireCrete(donnees)
                        }
                        override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, frequence: Int) {
                            if (fft != null) analyser(fft, frequence)
                        }
                    }, Visualizer.getMaxCaptureRate(), true, true)
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

    // LE SON EST-IL « PLEIN » ? Android donne aussi la FORME du son : ~1000 points, chacun de 0 à 255
    // (128 = le silence). On compare sa force MOYENNE à son point le PLUS HAUT :
    //   - une note de piano toute seule : un grand pic et presque rien autour -> petit nombre (~0,3) ;
    //   - une grosse basse saturée de dubstep : le son reste collé au maximum tout le temps -> grand nombre (~0,7).
    // C'est un rapport : il ne dépend ni du volume du téléphone, ni du morceau d'avant.
    private fun lireCrete(points: ByteArray) {
        var pic = 0
        var somme = 0L
        for (octet in points) {
            val ecart = (octet.toInt() and 0xFF) - 128
            val hauteurPoint = if (ecart < 0) -ecart else ecart
            if (hauteurPoint > pic) pic = hauteurPoint
            somme += (ecart * ecart).toLong()
        }
        creteVive = if (pic < 6 || points.isEmpty()) -1f else sqrt(somme.toFloat() / points.size) / pic
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
        val recul = (retardMs / PAS.toInt()).coerceIn(0, MEMOIRE - 1)
        val j = (rangMem - recul + MEMOIRE) % MEMOIRE
        System.arraycopy(memSpectre[j], 0, spectre, 0, BANDES)
        coup = memCoup[j]
        rangMem = (rangMem + 1) % MEMOIRE
    }

    // SUR QUEL PALIER EST LA MUSIQUE ? (point 8). Appelé à chaque mesure.
    //   partBasses = la part des basses (sous 180 Hz) dans tout le son, de 0 à 1.
    // L'ÉNERGIE = à quel point le son est plein (voir lireCrete), gonflé quand les basses dominent : dès qu'elles
    // font plus de la moitié du son, elles comptent beaucoup (Will, 10/11 : « trop exigeant », un drop porté par
    // la basse restait vert). Elle est lissée sur ~2 secondes : un seul coup ne change pas le palier.
    // LE CONTRASTE : on retient aussi l'énergie « habituelle » du morceau (moyenne sur ~25 s). Quand la musique
    // monte nettement au-dessus de son habitude (un drop après une intro calme), on ajoute la moitié de l'écart :
    // c'est ce saut qu'on ressent, plus que le niveau lui-même.
    // Ensuite on regarde entre quelles frontières (SEUILS) le résultat tombe. Pour éviter que ça clignote quand
    // il est pile sur une frontière : il faut la dépasser un peu (0,02) et un palier dure au moins 1,5 s.
    // Les 2,5 premières secondes d'un morceau, on reste sur le palier du milieu, le temps d'avoir une mesure sûre.
    private fun situer(partBasses: Float, dt: Float) {
        val plein = creteVive
        if (plein < 0f) return
        muet = 0f
        // (pour le réglage : les deux ingrédients, lissés sur 2 s)
        val doux = 1f - exp(-dt / 2f)
        pleinLisse += (plein - pleinLisse) * doux
        bassesLisses += (partBasses - bassesLisses) * doux
        pleinVu = (pleinLisse * 100f).toInt()
        bassesVues = (bassesLisses * 100f).toInt()
        val brute = plein * (1f + 2.5f * max(0f, partBasses - 0.5f))
        duree += dt; depuisPalier += dt
        energie = if (energie <= 0f) brute
                  else energie + (brute - energie) * (1f - exp(-dt / (if (duree < 2.5f) 0.4f else 2f)))
        if (duree < 2.5f) habitude = energie
        else habitude += (energie - habitude) * (1f - exp(-dt / 25f))
        val ressentie = energie + 0.5f * max(0f, energie - habitude)
        energieVue = (ressentie * 100f).toInt()
        var naturel = 1                                // le palier où tombe l'énergie, sans précaution
        for (seuil in SEUILS) if (ressentie > seuil) naturel++
        if (duree < 2.5f) { palier = 3; depuisPalier = 1.5f; return }
        if (depuisPalier < 1.5f || naturel == palier) return
        if (naturel > palier && ressentie > SEUILS[palier - 1] + 0.02f) { palier = naturel; depuisPalier = 0f }
        else if (naturel < palier && ressentie < SEUILS[palier - 2] - 0.02f) { palier = naturel; depuisPalier = 0f }
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

        if (g + m + a < 1.5f) {                        // silence : tout retombe
            grave = 0f; medium = 0f; aigu = 0f; claque = 0f; ancienSec = sec; moyGrave = 0f; basGrave = 0f
            valeur.fill(0f)
            // plus d'une seconde de silence = un nouveau morceau arrive : on le jugera depuis le début
            muet += dt
            if (muet > 1f) duree = 0f
            publier()
            return
        }
        // LE SPECTRE : la force de chacune des 40 tranches.
        val nb = fft.size / 2
        if (forces.size != nb) forces = FloatArray(nb)
        var tout = 0f                                  // l'énergie de tout le son…
        var basses = 0f                                // … et celle des basses (sous 180 Hz), pour connaître leur part
        val finBasses = (180f / hzParCase).toInt().coerceIn(1, nb - 1)
        for (k in 1 until nb) {
            val brut = hypot(fft[2 * k].toFloat(), fft[2 * k + 1].toFloat())
            forces[k] = brut * vol
            tout += brut * brut
            if (k <= finBasses) basses += brut * brut
        }
        situer(if (tout > 0f) basses / tout else 0f, dt)
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
        var coupTombe = false
        if (elan > 0.35f && depuisCoup > 0.11f) {
            coupVif = max(coupVif, 0.55f + 0.45f * elan)
            depuisCoup = 0f
            coupTombe = true
        }
        medium = niveau(m, refMedium, 0.20f)
        aigu = niveau(a, refAigu, 0.25f)
        // ce qui vient d'augmenter d'un coup : on compare à « ancienSec », qui suit le son avec ~50 ms de retard
        val claqueVive = ((sec - ancienSec) / refMedium * 3f).coerceIn(0f, 1f)
        claque = claqueVive
        ancienSec += (sec - ancienSec) * (1f - exp(-dt / 0.05f))
        // (pour le réglage : combien de coups de basse et de sons secs par seconde, en moyenne sur ~3 s.
        //  Chaque événement ajoute une petite marche qui s'efface doucement : on obtient un nombre par seconde.)
        val secTombe = claqueVive > 0.5f && claqueAvant <= 0.5f
        claqueAvant = claqueVive
        val lent = 1f - exp(-dt / 3f)
        cadenceCoups += ((if (coupTombe) 1f / dt else 0f) - cadenceCoups) * lent
        cadenceSecs += ((if (secTombe) 1f / dt else 0f) - cadenceSecs) * lent
        coupsVus = (cadenceCoups * 10f).toInt()
        secsVus = (cadenceSecs * 10f).toInt()

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
