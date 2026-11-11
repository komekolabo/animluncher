package com.will.animlauncher

import android.os.SystemClock
import kotlin.math.exp

// LE DROP (11/11, idée de Will) : repérer le moment où la musique MONTE BRUTALEMENT, pour lancer les confettis
// à ce moment-là, et savoir combien de temps les garder.
//
// Comment on le repère : on suit l'énergie de la musique (le « E » de la ligne de mesures) de deux façons :
//   - « court » : l'énergie de maintenant (à peine lissée, sur 0,3 s) ;
//   - « lent »  : l'énergie des secondes d'avant (une moyenne lente, sur ~6 s).
// Quand « court » dépasse « lent » d'un grand saut, ET que la musique est au moins au rouge : c'est un drop.
//
// Combien de temps il dure : on note le niveau d'AVANT le drop (« base ») et le niveau ATTEINT (« sommet »).
//   - il continue tant que l'énergie reste plus près du sommet que de la base ;
//   - il s'arrête quand elle est retombée à mi-chemin (pendant plus de 0,6 s : un petit creux ne compte pas) ;
//   - et dans tous les cas après 16 TEMPS de musique (on mesure le tempo avec les coups de basse) : même si le
//     morceau reste à fond, les confettis se calment, et reviendront au prochain vrai saut.
// À la fin d'un drop, « lent » repart du niveau actuel : il faudra un NOUVEAU saut pour un nouveau drop.
//
// Qui s'en sert : ReglagesSon.SEUILS (la frontière des confettis descend pendant un drop). C'est lui qui appelle
// « suivre », plusieurs fois par mesure du son : on ne refait donc le calcul que toutes les 10 millisecondes.
object DropSon {
    @Volatile var actif = false              // un drop est en cours
        private set
    private var dernier = 0L                 // l'heure du dernier calcul
    private var court = -1f                  // (-1 = pas encore de mesure)
    private var lent = 0f
    private var base = 0f
    private var sommet = 0f
    private var duree = 0f                   // depuis combien de secondes le drop dure
    private var retombe = 0f                 // depuis combien de secondes l'énergie est retombée à mi-chemin
    // le tempo : l'écart habituel entre deux coups de basse
    private var coupAvant = 0f
    private var depuisCoup = 9f
    private var periode = 0.45f

    // Met à jour et répond : un drop est-il en cours ?
    //   seuilRouge : la frontière du rouge ; saut : de combien l'énergie doit bondir pour que ça compte.
    fun suivre(seuilRouge: Float, saut: Float): Boolean {
        val maintenant = SystemClock.uptimeMillis()
        val ecoule = maintenant - dernier
        if (ecoule < 10L) return actif
        dernier = maintenant
        // l'écoute est arrêtée, ou on revient après une longue pause : on repart de zéro
        if (!EcouteSon.enMarche || ecoule > 1500L) {
            court = -1f
            actif = false
            if (!EcouteSon.enMarche) return false
        }
        val dt = (ecoule / 1000f).coerceIn(0.005f, 0.1f)
        val energie = EcouteSon.energieVue / 100f
        if (court < 0f) { court = energie; lent = energie }
        court += (energie - court) * (1f - exp(-dt / 0.3f))
        lent += (court - lent) * (1f - exp(-dt / 6f))
        // le tempo : on repère l'instant où un coup de basse tombe, et on retient l'écart avec le précédent
        depuisCoup += dt
        val coup = EcouteSon.coup
        if (coup > 0.5f && coupAvant <= 0.5f) {
            if (depuisCoup in 0.28f..0.9f) periode += (depuisCoup - periode) * 0.3f
            depuisCoup = 0f
        }
        coupAvant = coup

        if (!actif) {
            // UN DROP COMMENCE : un grand saut au-dessus des secondes d'avant, et on est au moins au rouge
            if (court - lent > saut && court > seuilRouge) {
                actif = true
                base = lent
                sommet = court
                duree = 0f
                retombe = 0f
            }
        } else {
            duree += dt
            if (duree < 1.5f && court > sommet) sommet = court          // (le drop finit de monter pendant 1,5 s)
            if (court < base + 0.5f * (sommet - base)) retombe += dt else retombe = 0f
            val seizeTemps = 16f * periode.coerceIn(0.34f, 0.70f)       // entre 5,4 et 11 secondes
            if (retombe > 0.6f || duree > seizeTemps) {
                actif = false
                lent = court                                             // il faudra un nouveau saut
            }
        }
        return actif
    }
}
