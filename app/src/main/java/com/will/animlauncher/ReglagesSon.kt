package com.will.animlauncher

// LES RÉGLAGES DE LA RÉACTION SONORE : les chiffres qu'on retouche souvent, rangés à part pour pouvoir
// les changer sans toucher au gros fichier EcouteSon.kt.
object ReglagesSon {
    // LES 4 FRONTIÈRES ENTRE LES 5 PALIERS, sur l'échelle d'« énergie » (le « E » de la ligne de mesures, divisé par 100) :
    //   sous 0,13 -> palier 1 (les petits Z)       de 0,13 à 0,30 -> palier 2 (bleu, calme)
    //   de 0,30 à 0,54 -> palier 3 (vert, moyen)   de 0,54 à 0,70 -> palier 4 (rouge, ça envoie)
    //   au-dessus de 0,70 -> palier 5 (rouge + confettis)
    // (ce sont les valeurs AU CASQUE d'aujourd'hui ; l'historique des réglages est juste en dessous)
    // 11/11 au matin, Will : « rendre les confettis et le rouge un peu plus exigeants, j'ai l'impression que
    // maintenant rouge = confettis ». Avant : rouge à 0,52 et confettis à 0,62, donc presque collés : dès que
    // la musique passait au rouge, elle arrivait aux confettis. Maintenant le rouge commence à 0,58 et les
    // confettis à 0,76 : il y a une vraie zone « rouge sans confettis » entre les deux.
    // 11/11, 11h45, Will : « le vert, le rouge et les confettis légèrement moins exigeants : je ne vois les
    // confettis que très rarement, et des fois c'est bleu sans raison ». On descend donc un peu les trois
    // frontières du haut : vert 0,34 -> 0,30 ; rouge 0,58 -> 0,54 ; confettis 0,76 -> 0,70.
    private val AU_CASQUE = floatArrayOf(0.13f, 0.30f, 0.54f, 0.70f)

    // AU HAUT-PARLEUR DU TÉLÉPHONE, les mesures sont gonflées (EcouteSon multiplie le « plein » par 1,35 et les
    // basses par 2,4 pour compenser ce que le haut-parleur retire) : l'énergie lue est donc bien plus haute
    // qu'au casque. Vidéo de Will du 11/11 (une phonk au haut-parleur) : E entre 82 et 164, souvent vers 115,
    // et des confettis sans arrêt (« un peu trop laxiste sur les basses »). On met donc des frontières plus
    // hautes pour le rouge et les confettis dans ce cas : rouge à 0,80, confettis à 1,30 (seulement les pics).
    // (11/11, 11h45 : descendues un peu elles aussi, comme au casque : vert 0,40 -> 0,35 ; rouge 0,80 -> 0,74 ;
    // confettis 1,30 -> 1,15.)
    private val AU_HAUT_PARLEUR = floatArrayOf(0.13f, 0.35f, 0.74f, 1.15f)

    // Les frontières à utiliser en ce moment (EcouteSon les relit à chaque mesure).
    val SEUILS: FloatArray get() = if (EcouteSon.hautParleur) AU_HAUT_PARLEUR else AU_CASQUE
}
