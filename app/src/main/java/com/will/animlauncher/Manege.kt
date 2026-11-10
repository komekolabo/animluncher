package com.will.animlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.roundToInt

// LE MANÈGE DES CARTES de l'accueil (10/11). Il remplace l'ancien rail qui défilait (HorizontalScrollView).
//
//        carte 3 │      carte 1       │ carte 2
//         (bord) │   (au centre)      │ (bord)
//
// Les cartes tournent EN BOUCLE, comme un manège : 1 → 2 → 3 → 1…
//  - Le doigt glisse vers la GAUCHE  → la carte de droite arrive (1 → 2).
//  - Le doigt glisse vers la DROITE  → la carte de gauche arrive (1 → 3).
//  - Quelle que soit la carte au centre, on voit toujours dépasser ses deux voisines.
//
// COMMENT ÇA MARCHE : toutes les cartes sont empilées au même endroit (au centre), et on les
// décale à gauche ou à droite avec « translationX ». Un seul nombre décide de tout : « position ».
//  - position = 0     → la carte 1 est au centre
//  - position = 1     → la carte 2 est au centre
//  - position = 0,5   → on est entre les deux (en plein glissement)
// « position » peut grandir sans fin (4, 5, 6…) : on ramène chaque carte dans la zone
// « de -1,5 à +1,5 cartes du centre », donc elle réapparaît de l'autre côté. Le saut se fait
// toujours HORS de l'écran : on ne le voit jamais.
class Manege(context: Context, private val pas: Int) : FrameLayout(context) {
    // (pas = largeur d'une carte + l'écart entre deux cartes, en pixels)

    // Ce que le manège raconte à l'écran d'accueil.
    interface Ecoute {
        fun carteAffichee(page: Int)          // la carte du centre a changé (pour les points)
        fun doigtPose()                       // le doigt touche le manège (on met le défilement auto en pause)
        fun doigtLeve(sens: Int)              // le doigt est levé : +1 = carte suivante, -1 = précédente, 0 = on reste
    }
    var ecoute: Ecoute? = null

    private val cartes = ArrayList<View>()
    private var position = 0f
    var page = 0                              // la carte au centre : 0, 1 ou 2
        private set

    private val dp = resources.displayMetrics.density
    private val seuil = ViewConfiguration.get(context).scaledTouchSlop     // le petit mouvement à partir duquel on parle de « glisser »
    private var animation: ValueAnimator? = null
    private var vitesse: VelocityTracker? = null
    private var xDepart = 0f
    private var yDepart = 0f
    private var positionDepart = 0f
    private var glisse = false

    // On ajoute une carte (toutes ont la même taille, posées au centre).
    fun ajouter(carte: View, largeur: Int) {
        cartes.add(carte)
        addView(carte, LayoutParams(largeur, LayoutParams.MATCH_PARENT, android.view.Gravity.CENTER_HORIZONTAL))
        placer()
    }

    // Range chaque carte à sa place selon « position ».
    private fun placer() {
        val n = cartes.size
        if (n == 0) return
        for ((i, carte) in cartes.withIndex()) {
            // « rel » = de combien de cartes celle-ci est décalée par rapport au centre (entre -1,5 et +1,5)
            var rel = (i - position + n / 2f) % n
            if (rel < 0) rel += n
            rel -= n / 2f
            carte.translationX = rel * pas
            // une carte très loin (celle qui « fait le tour ») est cachée, pour ne jamais la voir sauter
            carte.visibility = if (abs(rel) < 1.4f) View.VISIBLE else View.INVISIBLE
        }
        val p = Math.floorMod(position.roundToInt(), n)
        if (p != page) { page = p; ecoute?.carteAffichee(p) }
    }

    // Amène le manège en douceur sur une position entière (un numéro de carte « qui peut faire le tour »).
    private fun glisserVers(cible: Float) {
        animation?.cancel()
        animation = ValueAnimator.ofFloat(position, cible).apply {
            duration = 420
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { position = it.animatedValue as Float; placer() }
            start()
        }
    }

    // La carte suivante (celle de droite) : utilisé par le défilement automatique.
    fun suivante() = glisserVers(Math.round(position) + 1f)

    // Aller sur la carte « p » (appui sur un point) par le chemin le plus court.
    // Renvoie le sens : +1 si on a avancé, -1 si on a reculé, 0 si on y était déjà.
    fun montrer(p: Int): Int {
        val n = cartes.size
        var ecart = Math.floorMod(p - page, n)          // 0, 1 ou 2 cartes en avançant
        if (ecart > n / 2) ecart -= n                   // 2 en avançant = 1 en reculant : plus court
        glisserVers(Math.round(position) + ecart.toFloat())
        return Integer.signum(ecart)
    }

    // ---------- LE DOIGT ----------
    // Android envoie d'abord le toucher ici (« intercept »). Tant que le doigt n'a pas glissé à
    // l'horizontale, on le laisse aux cartes : un simple appui ouvre donc toujours la carte.
    // Dès qu'il glisse plus à l'horizontale qu'à la verticale, on le « vole » et c'est le manège qui bouge.
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        suivreVitesse(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animation?.cancel()                        // on peut rattraper une carte en plein mouvement
                xDepart = ev.x; yDepart = ev.y
                positionDepart = Math.round(position).toFloat()
                glisse = false
                ecoute?.doigtPose()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - xDepart
                val dy = ev.y - yDepart
                if (abs(dx) > seuil && abs(dx) > abs(dy)) {
                    glisse = true
                    xDepart = ev.x - dx                     // (on garde le point de départ : pas de saut)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // un simple appui (pas de glissement) : on recentre au cas où on avait arrêté une carte en route
                if (!glisse) { glisserVers(Math.round(position).toFloat()); ecoute?.doigtLeve(0); oublierVitesse() }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        suivreVitesse(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {                   // (doigt posé entre deux cartes)
                animation?.cancel()
                xDepart = ev.x; yDepart = ev.y
                positionDepart = Math.round(position).toFloat()
                glisse = false
                ecoute?.doigtPose()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - xDepart
                if (!glisse && abs(dx) > seuil) glisse = true
                if (glisse) {
                    // le doigt va à gauche (dx négatif) → position augmente → la carte de droite arrive
                    position = positionDepart - dx / pas
                    placer()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vitesse?.computeCurrentVelocity(1000)
                val vx = vitesse?.xVelocity ?: 0f         // en pixels par seconde
                val avance = position - positionDepart    // de combien de cartes on a glissé
                // On change de carte si on a glissé de 15 % d'une carte, OU si on a « lancé » la carte vite.
                val sens = when {
                    abs(vx) > 600f * dp -> if (vx < 0) 1 else -1
                    avance > 0.15f -> 1
                    avance < -0.15f -> -1
                    else -> 0
                }
                glisserVers(positionDepart + sens)
                glisse = false
                oublierVitesse()
                ecoute?.doigtLeve(sens)
            }
        }
        return true
    }

    private fun suivreVitesse(ev: MotionEvent) {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) oublierVitesse()
        if (vitesse == null) vitesse = VelocityTracker.obtain()
        vitesse?.addMovement(ev)
    }

    private fun oublierVitesse() { vitesse?.recycle(); vitesse = null }

    override fun onDetachedFromWindow() {
        animation?.cancel()
        super.onDetachedFromWindow()
    }
}
