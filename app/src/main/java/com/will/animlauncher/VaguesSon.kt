package com.will.animlauncher

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// LES VAGUES DE LUMIÈRE de la réaction sonore — VERSION 3 (11/11, validée par Will sur une démo vidéo).
// Will : « j'ai l'impression que la barre clignote plus qu'elle ne suit la musique ; je voudrais voir la lumière
// PARTIR d'un endroit pour ARRIVER à un autre, comme une onde ».
//
// Le tour de l'écran est fait de repères numérotés : le numéro 0 est en bas au milieu, on monte par la gauche,
// la moitié des repères est en haut au milieu, puis on redescend par la droite.
// Une VAGUE = une bosse de lumière qui part d'un repère et court le long du tour, DANS LES DEUX SENS à la fois
// (donc toujours symétrique : Will préfère). Elle a un front net et une traîne derrière, et faiblit en avançant.
//
// Ce que fait ce fichier :
//  1) LE FOND QUI GLISSE (« fond ») : le spectre du son, mais adouci : il monte vite (0,04 s) et redescend
//     lentement (0,22 s). C'est lui qui donnait l'impression de clignoter quand il retombait trop vite.
//  2) LES VAGUES CALÉES SUR LE TEMPO : on mesure l'écart entre deux coups de basse ; la vague met exactement
//     ce temps-là pour monter du bas jusqu'en haut : elle arrive en haut quand le coup suivant part.
//       - un COUP DE BASSE  -> une grande vague du BAS vers le haut, suivie d'une ride plus faible ;
//       - un SON SEC (caisse claire, clap) -> une vague du HAUT vers le milieu des côtés ;
//       - LE DROP (la musique passe d'un coup au rouge) -> une vague plus grosse qui fait tout le tour ;
//       - PAS DE BASSE depuis 2 secondes (piano, voix seule) -> ce sont les attaques de la MÉLODIE qui lancent
//         une vague douce, depuis le milieu des deux côtés à la fois.
//     Les vagues sont plus amples quand la musique envoie (palier 4-5) que quand elle est calme.
//  3) LA RÉMANENCE : la lumière s'allume tout de suite mais s'éteint en douceur (0,12 s) : une traînée fluide.
//  4) LA COULEUR QUI VOYAGE : quand la musique change de palier, la nouvelle couleur part du bas et monte
//     avec la vague, au lieu de changer partout d'un coup.
//  5) UNE RESPIRATION LENTE, très légère, pour que le tour ne soit jamais complètement figé.
object VaguesSon {
    private const val PLACES = 20                     // au plus 20 vagues en même temps (les places sont réutilisées)
    private val vivante = BooleanArray(PLACES)
    private val depart = FloatArray(PLACES)           // d'où elle part (numéro de repère)
    private val vitesse = FloatArray(PLACES)          // en repères par seconde
    private val largeur = FloatArray(PLACES)          // la largeur du front (en repères)
    private val traine = FloatArray(PLACES)           // la traîne est « traine » fois plus longue que le front
    private val force = FloatArray(PLACES)            // sa lumière au départ (0 … 1)
    private val portee = FloatArray(PLACES)           // jusqu'où elle va (en repères)
    private val age = FloatArray(PLACES)              // depuis combien de secondes elle court (négatif = pas encore partie)

    // 1) le spectre adouci (40 tranches, comme EcouteSon.spectre) : lu par le dessin du tour
    val fond = FloatArray(EcouteSon.BANDES)

    private var temps = 0f                            // le temps qui passe, en secondes
    private var dtImage = 0.016f                      // la durée de la dernière image
    private var periode = 0.45f                       // l'écart habituel entre deux coups de basse (le tempo)
    private var dernierCoup = -9f                     // l'instant du dernier coup de basse
    private var claqueAvant = 0f
    private var mediumAvant = 0f
    private var depuisMelodie = 9f
    private var palierAvant = 3
    // l'ampleur des vagues selon le palier (1 à 5)
    private val AMPLEUR = floatArrayOf(0.75f, 0.75f, 0.85f, 1f, 1.1f)

    // 4) la couleur qui voyage : pour chaque repère, la couleur qu'il a (0 calme, 1 moyen, 2 ça envoie)
    //    et la part de chacune des 3 couleurs (elle glisse en 0,18 s vers la couleur du repère)
    private var modeRepere = IntArray(0)
    private var parts = Array(3) { FloatArray(0) }
    private var modeVoulu = 1
    private var attente = -1f                         // depuis quand une nouvelle couleur attend de partir (-1 = aucune)
    private var enRoute = false
    private var modeEnRoute = 1
    private var departCouleur = 0f
    private var dureeCouleur = 0.45f

    // 3) la lumière de l'image d'avant (pour la rémanence), et un brouillon pour lisser
    private var avant = FloatArray(0)
    private var brouillon = FloatArray(0)

    private fun preparer(n: Int) {
        if (modeRepere.size == n) return
        modeRepere = IntArray(n) { 1 }
        parts = Array(3) { p -> FloatArray(n) { if (p == 1) 1f else 0f } }
        avant = FloatArray(n)
        brouillon = FloatArray(n)
    }

    // La part de la couleur p (0, 1 ou 2) au repère k. Les trois parts d'un repère font 1 au total.
    fun part(p: Int, k: Int): Float = if (k < modeRepere.size) parts[p][k] else if (p == 1) 1f else 0f

    // À appeler à chaque image, AVANT de dessiner.
    //   coupTombe / forceCoup : un coup de basse vient de tomber, et sa force ; n : le nombre de repères du tour ;
    //   palier : 1 à 5 ; mode : la couleur voulue (0 calme, 1 moyen, 2 ça envoie).
    fun suivre(dt: Float, coupTombe: Boolean, forceCoup: Float, n: Int, palier: Int, mode: Int) {
        preparer(n)
        temps += dt
        dtImage = dt
        for (i in 0 until PLACES) {
            if (!vivante[i]) continue
            age[i] += dt
            if (vitesse[i] * max(0f, age[i]) > portee[i]) vivante[i] = false        // elle est arrivée au bout
        }
        // 1) LE FOND QUI GLISSE
        val sp = EcouteSon.spectre
        for (b in 0 until EcouteSon.BANDES) {
            val vise = sp[b]
            fond[b] += (vise - fond[b]) * (1f - exp(-dt / (if (vise > fond[b]) 0.04f else 0.22f)))
        }
        // 2) LE TEMPO : l'écart entre deux coups (on ne retient que les écarts plausibles, de 0,28 à 0,9 s)
        if (coupTombe) {
            val ecart = temps - dernierCoup
            dernierCoup = temps
            if (ecart in 0.28f..0.9f) periode += (ecart - periode) * 0.3f
        }
        val temps1 = periode.coerceIn(0.34f, 0.70f)                 // la durée d'UN temps = la durée du voyage du bas au haut
        val ampleur = AMPLEUR[(palier - 1).coerceIn(0, 4)]
        // LE COUP DE BASSE : la grande vague, puis sa ride un quart de temps derrière
        if (coupTombe) {
            val f = (0.55f + 0.45f * forceCoup.coerceIn(0f, 1f)) * ampleur
            lancer(0f, n * 0.5f / temps1, n * 0.075f, f, n * 0.5f, 0f, 3f)
            lancer(0f, n * 0.5f / temps1, n * 0.05f, f * 0.40f, n * 0.45f, 0.26f * temps1, 2f)
        }
        // LE SON SEC : on repère l'instant où « claque » passe au-dessus de 0,5. Du haut vers le milieu des côtés.
        val claque = EcouteSon.claque
        if (claque > 0.5f && claqueAvant <= 0.5f) lancer(n * 0.5f, n * 0.32f / (0.6f * temps1), n * 0.05f, 0.6f * ampleur, n * 0.32f, 0f, 3f)
        claqueAvant = claque
        // LE DROP : on passe d'un coup du vert (ou moins) au rouge -> une vague plus grosse, un peu plus rapide
        val drop = palier >= 4 && palierAvant <= 3
        if (drop) lancer(0f, n * 0.5f / (temps1 * 0.8f), n * 0.11f, 0.8f, n * 0.5f, 0f, 3f)
        palierAvant = palier
        // PAS DE BASSE DEPUIS 2 SECONDES : les attaques de la mélodie prennent le relais (au plus une toutes les 0,3 s).
        // Une vague douce part du milieu de chaque côté en même temps (c'est là que la mélodie éclaire le tour).
        depuisMelodie += dt
        val medium = EcouteSon.medium
        if (temps - dernierCoup > 2f && medium - mediumAvant > 0.25f && medium > 0.5f && depuisMelodie > 0.3f) {
            lancer(n * 0.25f, n * 0.20f / 0.55f, n * 0.06f, 0.45f * ampleur, n * 0.20f, 0f, 3f)
            lancer(n * 0.75f, n * 0.20f / 0.55f, n * 0.06f, 0.45f * ampleur, n * 0.20f, 0f, 3f)
            depuisMelodie = 0f
        }
        mediumAvant += (medium - mediumAvant) * min(1f, dt / 0.08f)             // (suit la mélodie avec un petit retard)

        // 4) LA COULEUR QUI VOYAGE. Une nouvelle couleur attend le prochain coup de basse pour partir avec sa
        //    vague (ou part toute seule après une demi-seconde, ou tout de suite sur un drop).
        if (mode != modeVoulu) { modeVoulu = mode; attente = temps }
        if (attente >= 0f && (coupTombe || drop || temps - attente > 0.5f)) {
            modeEnRoute = modeVoulu
            departCouleur = temps
            dureeCouleur = temps1 * (if (modeVoulu == 2) 0.8f else 1f)
            enRoute = true
            attente = -1f
        }
        val glisse = 1f - exp(-dt / 0.18f)
        var tousArrives = true
        for (k in 0 until n) {
            if (enRoute) {
                val f = 1f - abs(2f * (k + 0.5f) / n - 1f)          // 0 = en bas … 1 = en haut
                // la couleur atteint ce repère quand elle a eu le temps de monter jusqu'à lui
                if (temps >= departCouleur + f * dureeCouleur) modeRepere[k] = modeEnRoute else tousArrives = false
            }
            val m = modeRepere[k]
            for (p in 0..2) parts[p][k] += ((if (m == p) 1f else 0f) - parts[p][k]) * glisse
        }
        if (enRoute && tousArrives) enRoute = false
    }

    // Lance une vague sur une place libre (s'il n'y en a plus, tant pis : on ne lance pas celle-ci).
    //   retard : elle ne part que dans « retard » secondes ; trainee : la longueur de sa traîne (x la largeur du front).
    private fun lancer(ou: Float, vite: Float, large: Float, fort: Float, loin: Float, retard: Float, trainee: Float) {
        for (i in 0 until PLACES) {
            if (vivante[i]) continue
            vivante[i] = true
            depart[i] = ou; vitesse[i] = vite; largeur[i] = large.coerceAtLeast(1.5f); force[i] = fort
            portee[i] = loin; age[i] = -retard; traine[i] = trainee
            return
        }
    }

    // Reçoit la lumière de fond de chaque repère (une valeur de 0 à 1) et la transforme :
    // lissage, respiration, vagues, rémanence.
    fun eclairer(lumiere: FloatArray) {
        val n = lumiere.size
        preparer(n)
        // le fond est lissé 3 fois entre voisins (un quart, la moitié, un quart) : plus de petites dents
        for (passe in 0 until 3) {
            for (k in 0 until n) brouillon[k] = (lumiere[(k + n - 1) % n] + 2f * lumiere[k] + lumiere[(k + 1) % n]) / 4f
            System.arraycopy(brouillon, 0, lumiere, 0, n)
        }
        // 5) la respiration lente : une ondulation très légère qui descend doucement le long du tour
        val deuxPi = (2.0 * PI).toFloat()
        for (k in 0 until n) {
            val f = 1f - abs(2f * (k + 0.5f) / n - 1f)
            lumiere[k] = min(1f, lumiere[k] + 0.03f * (1f + sin(deuxPi * (f * 1.5f - temps * 0.25f))))
        }
        // 2) les vagues
        for (i in 0 until PLACES) {
            if (!vivante[i] || age[i] < 0f) continue
            val parcouru = vitesse[i] * age[i]
            // elle apparaît en 0,05 s (pas d'un coup), puis faiblit en avançant jusqu'au bout de sa portée
            val reste = (1f - parcouru / portee[i]).coerceIn(0f, 1f)
            val eclat = force[i] * min(1f, age[i] / 0.05f) * reste.pow(0.5f)
            if (eclat < 0.02f) continue
            val devant = largeur[i]
            val derriere = largeur[i] * traine[i]
            // les deux fronts : un qui tourne dans un sens (sens = 1), l'autre dans l'autre (sens = -1)
            var sens = 1
            while (sens >= -1) {
                val front = depart[i] + sens * parcouru
                val a = (front - sens * derriere * 2f).toInt()
                val b = (front + sens * devant * 2f).toInt()
                var k = min(a, b)
                val dernier = max(a, b)
                while (k <= dernier) {
                    // la distance au front, comptée dans le sens de la marche (positive = devant le front)
                    val ecart = (k - front) * sens
                    val x = if (ecart >= 0f) ecart / devant else ecart / derriere
                    val place = ((k % n) + n) % n
                    lumiere[place] = min(1f, lumiere[place] + eclat * exp(-x * x * 2f))
                    k++
                }
                sens -= 2
            }
        }
        // 3) la rémanence : chaque repère garde un peu de sa lumière d'avant, qui s'éteint en 0,12 s
        val garde = exp(-dtImage / 0.12f)
        for (k in 0 until n) {
            val v = max(lumiere[k], avant[k] * garde)
            lumiere[k] = v
            avant[k] = v
        }
    }
}
