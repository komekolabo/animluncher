package com.will.animlauncher

import android.graphics.Canvas
import kotlin.math.min
import kotlin.math.sin

// LES ANIMATIONS « SANS ATTENTE ».
// Pour les apps qu'on ouvre pour aller vite (messages, calculatrice, paiement…), on ne veut pas
// la durée minimale de 1,2 s : l'animation s'efface dès que l'app est prête.
// Une scène qui porte l'étiquette SansMinimum est traitée comme ça par AnimationView.
interface SansMinimum

// ===================== « Transition » =====================
// Juste une belle transition : l'écran s'assombrit doucement, une lueur de la couleur de l'app
// respire au centre, puis l'app apparaît dans un fondu circulaire, avec un fin anneau de sa couleur.
class Transition(private val couleur: Int) : Scene(), SansMinimum {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val m = min(w, h)
        val app = ralentit(i.intro)
        // le voile sombre (il apparaît en même temps que l'intro, pas d'un coup)
        c.drawColor(avecAlpha(rgb(8, 9, 13), app))
        // la lueur au centre, qui grandit puis « respire » légèrement si l'app tarde un peu
        val souffle = 0.03f * sin(i.secondes * 6f)
        o.halo(c, w / 2f, h / 2f, m * (0.25f + 0.35f * app), (0.20f + souffle) * app, couleur)
        // l'ouverture
        o.revelationDouce(c, w / 2f, h / 2f, i.revelation, couleur)
    }
}

// Garde l'animation d'une app telle quelle, mais SANS la durée minimale de 1,2 s :
// elle s'efface dès que l'app est prête (ex. Google Wallet, raccourcie).
class Rapide(private val scene: Scene) : Scene(), SansMinimum {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) = scene.dessiner(c, o, i)
}
