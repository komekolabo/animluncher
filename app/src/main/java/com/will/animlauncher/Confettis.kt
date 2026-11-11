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

// LES CONFETTIS DU PALIER 5 (réaction sonore) : UN PETIT SPECTACLE calé sur la musique.
// (10/11, 23h40, Will : « ils sortent toujours d'en bas, on s'ennuie ; il faut penser ça comme un show, au bon
//  moment, pas ennuyant, mais sans être envahissant ».)
//
// Le principe : à chaque COUP DE BASSE, des confettis partent du bord de l'écran. Plus le coup est fort, plus
// il y en a (6 à 20) et plus ils partent vite. Mais ils ne partent pas toujours du même endroit : il y a
// 5 FIGURES, et on en change tous les 4 coups (à peu près une mesure de musique), jamais deux fois la même de suite.
//   0  DUO DU BAS   : du bas de l'écran, là où le tour brille le plus, et du même endroit de l'autre côté
//   1  LES COINS    : un coup sur deux des deux coins du bas, l'autre des deux coins du haut
//   2  PING-PONG    : un coup à gauche, un coup à droite, depuis le milieu des côtés
//   3  LA VAGUE     : une vague qui monte le long des deux côtés, du bas vers le haut, en un tiers de seconde
//   4  LA PLUIE     : trois petites pluies qui tombent doucement du haut de l'écran
// Et quand un GROS coup arrive après un petit silence (le moment fort d'un morceau) : LE BOUQUET, des confettis
// qui partent de 8 endroits tout autour de l'écran en même temps.
// En plus, chaque SON SEC (caisse claire, cloche) lance un petit jet de 5 confettis depuis le haut.
//
// POUR NE PAS GÊNER : ils restent petits, ne s'éloignent pas du bord (ils sont vite freinés), s'effacent en
// ~1 seconde, et il n'y en a jamais plus de 120 à la fois.
// C'est un « object » : il n'y en a qu'un, le tour lumineux lui demande de lancer et de dessiner.
object Confettis {
    private const val PLACES = 120               // au plus 120 confettis en même temps (les places sont réutilisées)
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

    // LES TIRS PROGRAMMÉS (pour la vague) : « dans tant de secondes, tant de confettis à tel endroit »
    private const val FILE = 24
    private val fAttente = FloatArray(FILE) { -1f }      // -1 = case libre
    private val fOu = IntArray(FILE)
    private val fCombien = IntArray(FILE)
    private val fForce = FloatArray(FILE)

    // ce que le tour nous a donné la dernière fois (on en a besoin pour les tirs programmés)
    private var reperesX: FloatArray? = null
    private var reperesY: FloatArray? = null
    private var palette: IntArray? = null
    private var largeur = 0f
    private var hauteur = 0f
    private var dp = 1f

    // les endroits remarquables du tour, côté GAUCHE (le même à droite = n - k) : cherchés une fois par taille d'écran
    private var nReperes = 0
    private var coinBas = 0
    private var milieuCote = 0
    private var coinHaut = 0
    private var sommet = 0                       // le milieu du haut

    // le déroulement du spectacle
    private var figure = 0
    private var battement = 0                    // le numéro du coup de basse
    private var depuisCoup = 9f                  // secondes depuis le dernier coup
    private var claqueAvant = 0f

    // À appeler à chaque image quand on est au palier 5. Lance des confettis si la musique vient de frapper.
    //   coupTombe / forceCoup : un coup de basse vient de tomber, et sa force (0,5 à 1)
    //   ptX, ptY, lumiere : les repères du tour et leur lumière du moment ; couleurs : les 4 couleurs « ça envoie »
    fun suivre(coupTombe: Boolean, forceCoup: Float, ptX: FloatArray, ptY: FloatArray, lumiere: FloatArray,
               couleurs: IntArray, w: Float, ht: Float, d: Float) {
        if (!OptionsSon.confettis) return            // l'option « Confettis » est décochée : on n'en lance aucun
        val n = ptX.size
        if (n < 16 || couleurs.isEmpty()) return
        reperesX = ptX; reperesY = ptY; palette = couleurs; dp = d
        reperer(ptX, ptY, w, ht)

        if (coupTombe) {
            val force = forceCoup.coerceIn(0f, 1f)
            val total = (6f + 14f * force).toInt()
            // un gros coup après un petit silence = le moment fort : le bouquet
            val momentFort = force > 0.8f && depuisCoup > 0.7f
            depuisCoup = 0f
            // on change de figure tous les 4 coups, jamais deux fois la même de suite
            if (battement % 4 == 0) {
                var nouvelle = hasard.nextInt(5)
                if (nouvelle == figure) nouvelle = (nouvelle + 1) % 5
                figure = nouvelle
            }
            battement++
            val pair = battement % 2 == 0
            val moitie = total / 2
            if (momentFort) {
                for (j in 0..7) tirer(max(2, total / 6), force, (j * n / 8 + n / 16) % n, 3, 0.9f)
            } else when (figure) {
                0 -> {      // duo du bas
                    val k = plusBrillant(lumiere, 0, n)
                    tirer(moitie, force, k, 6, 1f)
                    tirer(total - moitie, force, (n - k) % n, 6, 1f)
                }
                1 -> {      // les coins : en bas, puis en haut
                    val k = if (pair) coinBas else coinHaut
                    tirer(moitie, force, k, 4, 0.9f)
                    tirer(total - moitie, force, (n - k) % n, 4, 0.9f)
                }
                2 -> tirer(total, force, if (pair) milieuCote else (n - milieuCote) % n, 10, 0.7f)      // ping-pong
                3 -> {      // la vague : 6 petits tirs qui montent de chaque côté, un toutes les 0,06 s
                    for (j in 0..5) {
                        val k = coinBas + (coinHaut - coinBas) * j / 5
                        programmer(j * 0.06f, k, max(1, total / 8), force)
                        programmer(j * 0.06f, (n - k) % n, max(1, total / 8), force)
                    }
                }
                else -> {   // la pluie : 3 endroits du haut, lancés doucement (ils retombent tout seuls)
                    for (j in -1..1) tirer(max(2, total / 3), force * 0.6f, (sommet + j * n / 14 + n) % n, 5, 0.5f)
                }
            }
        }
        // un son sec : on repère l'instant où « claque » passe au-dessus de 0,5 (sauf pendant la pluie, déjà en haut)
        val claque = EcouteSon.claque
        if (claque > 0.5f && claqueAvant <= 0.5f && figure != 4) tirer(5, 0.4f, plusBrillant(lumiere, n / 2, n), 6, 0.8f)
        claqueAvant = claque
    }

    // Cherche (une fois par taille d'écran) les repères les plus proches des coins et des milieux, côté gauche.
    private fun reperer(ptX: FloatArray, ptY: FloatArray, w: Float, ht: Float) {
        if (ptX.size == nReperes && w == largeur && ht == hauteur) return
        nReperes = ptX.size; largeur = w; hauteur = ht
        coinBas = proche(ptX, ptY, 0f, ht)
        milieuCote = proche(ptX, ptY, 0f, ht / 2f)
        coinHaut = proche(ptX, ptY, 0f, 0f)
        sommet = proche(ptX, ptY, w / 2f, 0f)
    }

    // Le repère le plus proche du point (cx, cy).
    private fun proche(ptX: FloatArray, ptY: FloatArray, cx: Float, cy: Float): Int {
        var choisi = 0
        var mieux = Float.MAX_VALUE
        for (k in ptX.indices) {
            val distance = hypot(ptX[k] - cx, ptY[k] - cy)
            if (distance < mieux) { mieux = distance; choisi = k }
        }
        return choisi
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

    // Note un tir pour plus tard : dans « attente » secondes, « combien » confettis au repère « ou ».
    private fun programmer(attente: Float, ou: Int, combien: Int, force: Float) {
        for (i in 0 until FILE) {
            if (fAttente[i] < 0f) {
                fAttente[i] = attente; fOu[i] = ou; fCombien[i] = combien; fForce[i] = force
                return
            }
        }
    }

    // Pose « combien » confettis sur des places libres, autour du repère « depart » (à « etale » repères près).
    // Direction : vers le centre de l'écran, avec un peu de hasard. « vitesse » : 1 = normal, 0,5 = tout doux.
    private fun tirer(combien: Int, force: Float, depart: Int, etale: Int, vitesse: Float) {
        val ptX = reperesX ?: return
        val ptY = reperesY ?: return
        val couleurs = palette ?: return
        val n = ptX.size
        var poses = 0
        var i = 0
        while (i < PLACES && poses < combien) {
            if (vie[i] <= 0f) {
                val k = (depart + hasard.nextInt(2 * etale + 1) - etale + 2 * n) % n
                x[i] = ptX[k]; y[i] = ptY[k]
                val dx = largeur / 2f - ptX[k]
                val dy = hauteur / 2f - ptY[k]
                val longueur = max(hypot(dx, dy), 1f)
                // (un élan modéré : freinés par l'air, ils ne parcourent que 30 à 90 dp avant de s'effacer)
                val elan = (70f + 90f * hasard.nextFloat()) * dp * (0.7f + 0.6f * force) * vitesse
                vx[i] = (dx / longueur + (hasard.nextFloat() - 0.5f) * 1.4f) * elan
                vy[i] = (dy / longueur + (hasard.nextFloat() - 0.5f) * 1.4f) * elan
                vie[i] = 0.7f + 0.4f * hasard.nextFloat()
                tour[i] = 360f * hasard.nextFloat()
                vrille[i] = (hasard.nextFloat() - 0.5f) * 900f
                taille[i] = (2f + 1.5f * hasard.nextFloat()) * dp
                couleur[i] = couleurs[hasard.nextInt(couleurs.size)]
                poses++
            }
            i++
        }
    }

    // Fait avancer et dessine les confettis encore en vie (à appeler à chaque image). presence : 0 (invisible) … 1.
    fun dessiner(c: Canvas, dt: Float, d: Float, presence: Float) {
        // (la première fois que le tour dessine, on lit les options et on prépare le mode « écran éteint » :
        //  voir OptionsSon.kt. Les fois suivantes, cette ligne ne fait rien.)
        OptionsSon.preparer()
        depuisCoup += dt
        // les tirs programmés dont l'heure est arrivée
        for (i in 0 until FILE) {
            if (fAttente[i] < 0f) continue
            fAttente[i] -= dt
            if (fAttente[i] <= 0f) {
                fAttente[i] = -1f
                tirer(fCombien[i], fForce[i], fOu[i], 2, 0.8f)
            }
        }
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
