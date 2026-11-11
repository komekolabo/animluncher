package com.will.animlauncher

import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

// LES VAGUES DE LUMIÈRE de la réaction sonore (11/11, demandées par Will : « j'ai l'impression que la barre
// clignote plus qu'elle ne suit la musique ; je voudrais voir la lumière PARTIR d'un endroit pour ARRIVER à un
// autre, à plusieurs endroits de l'écran selon le type de percussion »).
//
// Le tour de l'écran est fait de repères numérotés : le numéro 0 est en bas au milieu, on monte par la gauche,
// la moitié des repères est en haut au milieu, puis on redescend par la droite.
// Une VAGUE = une bosse de lumière qui part d'un repère et court le long du tour, DANS LES DEUX SENS à la fois.
// Elle a un front net (devant) et une traîne plus longue (derrière), et elle faiblit en avançant.
// Qui lance quoi :
//   - un COUP DE BASSE (grosse caisse)  -> une grande vague qui part du BAS et monte des deux côtés jusqu'en
//     haut en ~0,45 s (à peu près la durée d'un temps : la vague arrive en haut quand le coup suivant part) ;
//   - un SON SEC (caisse claire, clap)  -> une vague moyenne qui part du HAUT et redescend des deux côtés ;
//   - une ATTAQUE AIGUË (charleston)    -> une petite vague rapide près d'un coin du haut, une fois à gauche,
//     une fois à droite ;
//   - une ATTAQUE DE LA MÉLODIE (voix, synthé) -> une petite vague au milieu d'un côté, une fois à gauche,
//     une fois à droite.
// Ces vagues s'AJOUTENT à la lumière normale du tour (l'égaliseur), elles ne la remplacent pas.
object VaguesSon {
    private const val PLACES = 16                     // au plus 16 vagues en même temps (les places sont réutilisées)
    private val vivante = BooleanArray(PLACES)
    private val depart = FloatArray(PLACES)           // d'où elle part (numéro de repère)
    private val vitesse = FloatArray(PLACES)          // en repères par seconde
    private val largeur = FloatArray(PLACES)          // la largeur du front (en repères)
    private val force = FloatArray(PLACES)            // sa lumière au départ (0 … 1)
    private val portee = FloatArray(PLACES)           // jusqu'où elle va (en repères)
    private val age = FloatArray(PLACES)              // depuis combien de secondes elle court

    private var claqueAvant = 0f
    private var aiguAvant = 0f
    private var mediumAvant = 0f
    private var depuisAigu = 9f
    private var depuisMedium = 9f
    private var aGauche = false                       // (les petites vagues alternent gauche / droite)
    private var melodieAGauche = false

    // À appeler à chaque image, AVANT de dessiner. Fait avancer les vagues et en lance de nouvelles.
    //   coupTombe / forceCoup : un coup de basse vient de tomber, et sa force ; n : le nombre de repères du tour.
    fun suivre(dt: Float, coupTombe: Boolean, forceCoup: Float, n: Int) {
        for (i in 0 until PLACES) {
            if (!vivante[i]) continue
            age[i] += dt
            if (vitesse[i] * age[i] > portee[i]) vivante[i] = false         // elle est arrivée au bout
        }
        depuisAigu += dt
        depuisMedium += dt
        // LE COUP DE BASSE : du bas (repère 0) jusqu'en haut (la moitié du tour), en 0,45 s
        if (coupTombe) lancer(0f, n * 0.5f / 0.45f, n * 0.05f, 0.5f + 0.5f * forceCoup.coerceIn(0f, 1f), n * 0.5f)
        // LE SON SEC : on repère l'instant où « claque » passe au-dessus de 0,5. Du haut vers le milieu des côtés.
        val claque = EcouteSon.claque
        if (claque > 0.5f && claqueAvant <= 0.5f) lancer(n * 0.5f, n * 0.30f / 0.30f, n * 0.03f, 0.65f, n * 0.30f)
        claqueAvant = claque
        // L'ATTAQUE AIGUË : les aigus bondissent d'un coup (et pas plus d'une vague tous les 0,12 s)
        val aigu = EcouteSon.aigu
        if (aigu - aiguAvant > 0.22f && aigu > 0.45f && depuisAigu > 0.12f) {
            lancer(n * (if (aGauche) 0.38f else 0.62f), n * 0.10f / 0.18f, n * 0.018f, 0.45f, n * 0.10f)
            aGauche = !aGauche
            depuisAigu = 0f
        }
        aiguAvant += (aigu - aiguAvant) * min(1f, dt / 0.06f)               // (suit les aigus avec un petit retard)
        // L'ATTAQUE DE LA MÉLODIE : pareil avec les médiums, au milieu d'un côté (pas plus d'une tous les 0,25 s)
        val medium = EcouteSon.medium
        if (medium - mediumAvant > 0.25f && medium > 0.5f && depuisMedium > 0.25f) {
            lancer(n * (if (melodieAGauche) 0.25f else 0.75f), n * 0.13f / 0.30f, n * 0.025f, 0.40f, n * 0.13f)
            melodieAGauche = !melodieAGauche
            depuisMedium = 0f
        }
        mediumAvant += (medium - mediumAvant) * min(1f, dt / 0.08f)
    }

    // Lance une vague sur une place libre (s'il n'y en a plus, tant pis : on ne lance pas celle-ci).
    private fun lancer(ou: Float, vite: Float, large: Float, fort: Float, loin: Float) {
        for (i in 0 until PLACES) {
            if (vivante[i]) continue
            vivante[i] = true
            depart[i] = ou; vitesse[i] = vite; largeur[i] = large.coerceAtLeast(1.5f); force[i] = fort; portee[i] = loin; age[i] = 0f
            return
        }
    }

    // Ajoute la lumière des vagues à celle de chaque repère (« lumiere » : une valeur de 0 à 1 par repère).
    fun eclairer(lumiere: FloatArray) {
        val n = lumiere.size
        for (i in 0 until PLACES) {
            if (!vivante[i]) continue
            val parcouru = vitesse[i] * age[i]
            // elle faiblit en avançant : pleine au départ, éteinte au bout de sa portée
            val reste = (1f - parcouru / portee[i]).coerceIn(0f, 1f)
            val eclat = force[i] * reste.pow(0.6f)
            if (eclat < 0.02f) continue
            val devant = largeur[i]                   // le front : net
            val derriere = largeur[i] * 2.5f          // la traîne : 2,5 fois plus longue
            // les deux fronts : un qui tourne dans un sens (sens = 1), l'autre dans l'autre (sens = -1)
            for (sens in intArrayOf(1, -1)) {
                val front = depart[i] + sens * parcouru
                val debut = (front - sens * derriere * 2f).toInt()
                val fin = (front + sens * devant * 2f).toInt()
                var k = min(debut, fin)
                val dernier = if (debut > fin) debut else fin
                while (k <= dernier) {
                    // la distance au front, comptée dans le sens de la marche (positive = devant le front)
                    val ecart = (k - front) * sens
                    val x = if (ecart >= 0f) ecart / devant else ecart / derriere
                    val place = ((k % n) + n) % n
                    lumiere[place] = min(1.15f, lumiere[place] + eclat * exp(-x * x * 2f))
                    k++
                }
            }
        }
    }
}
