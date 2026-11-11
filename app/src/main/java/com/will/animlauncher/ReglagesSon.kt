package com.will.animlauncher

// LES RÉGLAGES DE LA RÉACTION SONORE : les chiffres qu'on retouche souvent, rangés à part pour pouvoir
// les changer sans toucher au gros fichier EcouteSon.kt.
object ReglagesSon {
    // LES 4 FRONTIÈRES ENTRE LES 5 PALIERS, sur l'échelle d'« énergie » (le « E » de la ligne de mesures, divisé par 100) :
    //   sous 0,13 -> palier 1 (les petits Z)       de 0,13 à 0,34 -> palier 2 (bleu, calme)
    //   de 0,34 à 0,58 -> palier 3 (vert, moyen)   de 0,58 à 0,76 -> palier 4 (rouge, ça envoie)
    //   au-dessus de 0,76 -> palier 5 (rouge + confettis)
    // 11/11 au matin, Will : « rendre les confettis et le rouge un peu plus exigeants, j'ai l'impression que
    // maintenant rouge = confettis ». Avant : rouge à 0,52 et confettis à 0,62, donc presque collés : dès que
    // la musique passait au rouge, elle arrivait aux confettis. Maintenant le rouge commence à 0,58 et les
    // confettis à 0,76 : il y a une vraie zone « rouge sans confettis » entre les deux.
    private val AU_CASQUE = floatArrayOf(0.13f, 0.34f, 0.58f, 0.76f)

    // AU HAUT-PARLEUR DU TÉLÉPHONE, les mesures sont gonflées (EcouteSon multiplie le « plein » par 1,35 et les
    // basses par 2,4 pour compenser ce que le haut-parleur retire) : l'énergie lue est donc bien plus haute
    // qu'au casque. Vidéo de Will du 11/11 (une phonk au haut-parleur) : E entre 82 et 164, souvent vers 115,
    // et des confettis sans arrêt (« un peu trop laxiste sur les basses »). On met donc des frontières plus
    // hautes pour le rouge et les confettis dans ce cas : rouge à 0,80, confettis à 1,30 (seulement les pics).
    private val AU_HAUT_PARLEUR = floatArrayOf(0.13f, 0.40f, 0.80f, 1.30f)

    // Les frontières à utiliser en ce moment (EcouteSon les relit à chaque mesure).
    val SEUILS: FloatArray get() = if (EcouteSon.hautParleur) AU_HAUT_PARLEUR else AU_CASQUE
}
