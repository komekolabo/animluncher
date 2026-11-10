package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import java.util.Random
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// LES CONFETTIS DU PALIER 5 (réaction sonore), calés sur la musique (10/11, 23h30, Will : « ajuste la sortie des
// confettis avec la musique »). Ce fichier remplace les confettis qui étaient dans ReactionSonore.kt.
//   - À chaque COUP DE BASSE : une poignée de confettis. Plus le coup est fort, plus il y en a (6 à 20) et plus
//     ils partent vite. Ils sortent du BAS de l'écran (là où le tour montre les basses), à l'endroit où le tour
//     brille le plus à cet instant, et du même endroit de l'autre côté (le tour est symétrique).
//   - À chaque SON SEC (caisse claire, cloche) : un petit jet de 5 confettis, depuis le HAUT (là où sont les aigus).
// Ils restent petits et près du bord (le tour est affiché par-dessus toutes les apps) et s'effacent en ~1 s.
// C'est un « object » : il n'y en a qu'un, le tour lumineux lui demande de lancer et de dessiner.
object Confettis {
    private const val PLACES = 96                // au plus 96 confettis en même temps (les places sont réutilisées)
    private val x = FloatArray(PLACES)
    private val y = FloatArray(PLACES)
    private val vx = FloatArray(PLACES)          // la vitesse (pixels par seconde)
    private val vy = FloatArray(PLACES)
    private val vie = FloatArray(PLACES)         // les secondes qu'il lui reste (0 = place libre)
    private val tour = FloatArray(PLACES)        // son angle, et sa vitesse de rotation
    private val vrille = FloatArray(PLACES)
    private val taille = FloatArray(PLACES)
    private val couleur = IntArray(PLACES)
    private val hasard = Random()
    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private var claqueAvant = 0f

    // À appeler à chaque image quand on est au palier 5. Lance des confettis si la musique vient de frapper.
    //   coupTombe / forceCoup : un coup de basse vient de tomber, et sa force (0,5 à 1)
    //   ptX, ptY, lumiere : les repères du tour et leur lumière du moment ; couleurs : les 4 couleurs « ça envoie »
    fun suivre(coupTombe: Boolean, forceCoup: Float, ptX: FloatArray, ptY: FloatArray, lumiere: FloatArray,
               couleurs: IntArray, w: Float, ht: Float, d: Float) {
        val n = ptX.size
        if (n < 12) return
        if (coupTombe) {
            val force = forceCoup.coerceIn(0f, 1f)
            lancer((6f + 14f * force).toInt(), force, plusBrillant(lumiere, 0, n), ptX, ptY, couleurs, w, ht, d)
        }
        // un son sec : on repère l'instant où « claque » passe au-dessus de 0,5
        val claque = EcouteSon.claque
        if (claque > 0.5f && claqueAvant <= 0.5f) lancer(5, 0.4f, plusBrillant(lumiere, n / 2, n), ptX, ptY, couleurs, w, ht, d)
        claqueAvant = claque
    }

    // L'endroit le plus lumineux parmi 12 repères pris au hasard autour de « centre » (0 = en bas, n/2 = en haut).
    private fun plusBrillant(lumiere: FloatArray, centre: Int, n: Int): Int {
        var choisi = centre
        var mieux = -1f
        for (essai in 0 until 12) {
            val k = (centre + hasard.nextInt(n / 3) - n / 6 + n) % n
            if (lumiere[k] > mieux) { mieux = lumiere[k]; choisi = k }
        }
        return choisi
    }

    // Pose « combien » confettis sur des places libres : la moitié près du repère « depart », l'autre moitié près
    // du même endroit de l'autre côté de l'écran. Direction : vers le centre de l'écran, avec un peu de hasard.
    private fun lancer(combien: Int, force: Float, depart: Int, ptX: FloatArray, ptY: FloatArray,
                       couleurs: IntArray, w: Float, ht: Float, d: Float) {
        val n = ptX.size
        var poses = 0
        var i = 0
        while (i < PLACES && poses < combien) {
            if (vie[i] <= 0f) {
                val base = if (poses % 2 == 0) depart else (n - depart) % n
                val k = (base + hasard.nextInt(13) - 6 + n) % n
                x[i] = ptX[k]; y[i] = ptY[k]
                val dx = w / 2f - ptX[k]
                val dy = ht / 2f - ptY[k]
                val longueur = max(hypot(dx, dy), 1f)
                val elan = (90f + 130f * hasard.nextFloat()) * d * (0.7f + 0.6f * force)
                vx[i] = (dx / longueur + (hasard.nextFloat() - 0.5f) * 1.4f) * elan
                vy[i] = (dy / longueur + (hasard.nextFloat() - 0.5f) * 1.4f) * elan
                vie[i] = 0.7f + 0.4f * hasard.nextFloat()
                tour[i] = 360f * hasard.nextFloat()
                vrille[i] = (hasard.nextFloat() - 0.5f) * 900f
                taille[i] = (2f + 1.5f * hasard.nextFloat()) * d
                couleur[i] = couleurs[hasard.nextInt(couleurs.size)]
                poses++
            }
            i++
        }
    }

    // Fait avancer et dessine les confettis encore en vie (à appeler à chaque image). presence : 0 (invisible) … 1.
    fun dessiner(c: Canvas, dt: Float, d: Float, presence: Float) {
        val frein = exp(-dt * 2.2f)                  // l'air les ralentit
        for (i in 0 until PLACES) {
            if (vie[i] <= 0f) continue
            vie[i] -= dt
            vx[i] *= frein
            vy[i] = vy[i] * frein + 60f * d * dt     // … et ils retombent un peu
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            tour[i] += vrille[i] * dt
            if (vie[i] <= 0f) continue
            pinceau.color = couleur[i]
            // il s'efface sur ses 0,3 dernières secondes
            pinceau.alpha = (min(1f, vie[i] / 0.3f) * 0.85f * presence * 255f).toInt().coerceIn(0, 255)
            val t = taille[i]
            c.save()
            c.rotate(tour[i], x[i], y[i])
            c.drawRect(x[i] - t, y[i] - t / 2f, x[i] + t, y[i] + t / 2f, pinceau)
            c.restore()
        }
    }
}
