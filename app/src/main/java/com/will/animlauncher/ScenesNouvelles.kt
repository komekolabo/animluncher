package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Étire le temps d'une scène : avec un facteur 2,5, tous ses mouvements vont 2,5 fois moins vite.
// Sert aux jeux, passés de « Secondaire » (0,5 s) à « Jeu » (environ 1 s).
class Etire(private val scene: Scene, private val facteur: Float) : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) =
        scene.dessiner(c, o, Instant(i.secondes / facteur, i.intro, i.revelation))
}
