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
    val SEUILS = floatArrayOf(0.13f, 0.34f, 0.58f, 0.76f)
}
