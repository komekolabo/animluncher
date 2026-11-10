package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

// LES MESURES À L'ÉCRAN (PROVISOIRE, 10/11) : une petite ligne de chiffres en bas de l'écran, dessinée par le
// tour lumineux pendant que la musique joue. Elle sert à régler les 5 paliers : Will filme son écran sur un
// morceau, et on lit ce que le VRAI téléphone mesure (la simulation sur ordinateur ne donne pas les mêmes chiffres).
//   P      = le palier choisi (1 à 5)
//   E      = l'énergie (les frontières sont dans EcouteSon.SEUILS, x 100)
//   plein  = à quel point le son est plein (0 à 100)
//   basses = la part des basses dans le son (0 à 100)
//   coups  = les coups de basse par seconde
//   secs   = les sons secs (caisse claire, cloche…) par seconde
// À RETIRER quand les paliers seront réglés : mettre AFFICHER à false (ou supprimer ce fichier et son appel).
object MesuresSon {
    const val AFFICHER = true

    private val chiffres = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
        setShadowLayer(4f, 0f, 0f, Color.BLACK)      // une ombre noire, pour rester lisible sur un fond clair
    }
    private var texte = ""
    private var dernier = 0L

    // Dessine la ligne, centrée en (x, y). « d » = le nombre de pixels dans 1 dp ; force = 0 (invisible) … 1.
    fun dessiner(c: Canvas, x: Float, y: Float, d: Float, force: Float) {
        if (!AFFICHER) return
        // on ne refait le texte que 2 fois par seconde (inutile de le recalculer à chaque image)
        val maintenant = android.os.SystemClock.uptimeMillis()
        if (maintenant - dernier > 500L) {
            dernier = maintenant
            texte = "P" + EcouteSon.palier + "  E" + EcouteSon.energieVue + "  plein " + EcouteSon.pleinVu +
                "  basses " + EcouteSon.bassesVues + "  coups " + (EcouteSon.coupsVus / 10f) + "  secs " + (EcouteSon.secsVus / 10f)
        }
        chiffres.textSize = 11f * d
        chiffres.alpha = (force.coerceIn(0f, 1f) * 230f).toInt()
        c.drawText(texte, x, y, chiffres)
    }
}
