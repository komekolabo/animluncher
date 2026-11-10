package com.will.animlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

// LE MANÈGE DES CARTES de l'accueil (10/11). Il remplace l'ancien rail qui défilait (HorizontalScrollView).
//
//        carte 3 │      carte 1       │ carte 2
//         (bord) │   (au centre)      │ (bord)
//                │  ━━━━━━──────────  │            ← la barre du temps restant avant la carte suivante
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
//
// LA TRANSITION (v2, 10/11) : en s'éloignant du centre, une carte rétrécit un peu (92 %) et
// s'assombrit (65 %). Celle qui arrive grandit et s'éclaire en même temps : effet de profondeur.
// Le mouvement suit une courbe « rapide au départ, très douce à l'arrivée ».
//
// LE DÉFILEMENT AUTOMATIQUE est géré ICI (pour que la barre et le changement de carte soient
// parfaitement synchronisés) : toutes les DELAI ms, on passe à la carte de droite. Les règles :
//  - le compteur ne tourne pas sous le doigt, pendant un glissement, ni quand « peutDefiler » dit non
//    (menu ou fenêtre ouverts : c'est l'accueil qui décide) ;
//  - tout changement de carte remet le compteur à zéro (5 s pleines sur la nouvelle carte) ;
//  - revenir à la carte PRÉCÉDENTE met « autoArrete » à vrai : plus de défilement ni de barre,
//    jusqu'à ce que l'accueil le remette à faux (quand on quitte l'app).
class Manege(context: Context, private val pas: Int) : FrameLayout(context) {
    // (pas = largeur d'une carte + l'écart entre deux cartes, en pixels)

    companion object {
        const val DELAI = 5000L              // le temps passé sur une carte avant le défilement automatique (ms)
        private const val ECHELLE_LOIN = 0.92f   // la taille d'une carte voisine
        private const val LUMIERE_LOIN = 0.65f   // l'opacité d'une carte voisine
    }

    // L'accueil est prévenu quand la carte du centre change (pour les points sous les cartes).
    var carteAffichee: ((Int) -> Unit)? = null
    // L'accueil dit si le défilement a le droit d'avancer en ce moment (menu fermé, fenêtre du son fermée…).
    var peutDefiler: () -> Boolean = { true }
    // Vrai quand on est revenu en arrière : défilement automatique (et barre) arrêtés.
    var autoArrete = false
    // Vrai quand l'écran est visible (l'accueil le met à faux quand on passe à une autre page).
    var enAvant = false
        set(v) { field = v; dernierInstant = 0L; if (v) { ecoule = 0L; invalidate() } }

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
    private var doigt = false                 // vrai tant que le doigt touche le manège

    // le compteur du défilement automatique et la barre
    private var ecoule = 0L                   // le temps déjà passé sur la carte du centre (ms)
    private var dernierInstant = 0L           // l'heure de l'image précédente (pour savoir combien de temps a passé)
    private var opaciteBarre = 0f             // la barre apparaît / disparaît en fondu (0 → 1)
    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }

    // la courbe du mouvement : démarre franchement, puis se pose tout en douceur
    private val courbe = PathInterpolator(0.2f, 0f, 0f, 1f)

    init { setWillNotDraw(false) }            // (un FrameLayout ne se dessine pas lui-même, sauf si on le demande)

    // On ajoute une carte (toutes ont la même taille, posées au centre).
    fun ajouter(carte: View, largeur: Int) {
        cartes.add(carte)
        addView(carte, LayoutParams(largeur, LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL))
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
            val loin = min(abs(rel), 1f)                       // 0 au centre, 1 dès qu'elle est sur le côté
            val echelle = 1f - (1f - ECHELLE_LOIN) * loin
            carte.scaleX = echelle
            carte.scaleY = echelle
            carte.alpha = 1f - (1f - LUMIERE_LOIN) * loin
            // En rétrécissant, le bord de la carte recule vers son centre. On la rapproche d'autant,
            // pour que le bout qui dépasse sur le côté de l'écran garde la même taille.
            val recul = carte.width * (1f - echelle) / 2f
            carte.translationX = rel * pas - sign(rel) * recul
            // une carte très loin (celle qui « fait le tour ») est cachée, pour ne jamais la voir sauter
            carte.visibility = if (abs(rel) < 1.4f) View.VISIBLE else View.INVISIBLE
        }
        val p = Math.floorMod(position.roundToInt(), n)
        if (p != page) { page = p; carteAffichee?.invoke(p) }
        invalidate()
    }

    // (au premier affichage, les cartes n'avaient pas encore de largeur : on les replace)
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed) placer()
    }

    // Amène le manège en douceur sur une position entière (un numéro de carte « qui peut faire le tour »).
    private fun glisserVers(cible: Float, duree: Long) {
        animation?.cancel()
        ecoule = 0L                                           // nouvelle carte (ou retour en place) : 5 s pleines
        animation = ValueAnimator.ofFloat(position, cible).apply {
            duration = duree
            interpolator = courbe
            addUpdateListener { position = it.animatedValue as Float; placer() }
            start()
        }
    }

    private val enMouvement get() = animation?.isRunning == true

    // La carte suivante (celle de droite) : utilisé par le défilement automatique.
    private fun suivante() = glisserVers(Math.round(position) + 1f, 650)

    // Aller sur la carte « p » (appui sur un point) par le chemin le plus court.
    // Si c'est un retour en arrière, le défilement automatique s'arrête.
    fun montrer(p: Int) {
        val n = cartes.size
        var ecart = Math.floorMod(p - page, n)          // 0, 1 ou 2 cartes en avançant
        if (ecart > n / 2) ecart -= n                   // 2 en avançant = 1 en reculant : plus court
        if (ecart < 0) autoArrete = true
        glisserVers(Math.round(position) + ecart.toFloat(), 550)
    }

    // ---------- LA BARRE DU TEMPS (et le compteur du défilement automatique) ----------
    // Dessinée PAR-DESSUS les cartes, en bas de la carte du centre. Elle se remplit de gauche à
    // droite pendant les 5 s ; quand elle est pleine, la carte suivante arrive.
    override fun dispatchDraw(c: Canvas) {
        super.dispatchDraw(c)                                 // d'abord les cartes
        if (cartes.isEmpty()) return

        // 1. le temps qui passe (seulement si rien ne l'empêche)
        val maintenant = SystemClock.uptimeMillis()
        val compte = enAvant && !autoArrete && !doigt && !enMouvement && peutDefiler()
        if (compte && dernierInstant != 0L) ecoule += min(maintenant - dernierInstant, 100L)   // (100 : pas de grand saut après une pause)
        dernierInstant = if (enAvant) maintenant else 0L
        if (compte && ecoule >= DELAI) suivante()

        // 2. la barre apparaît en fondu, et disparaît pour de bon si le défilement est arrêté
        val cibleOpacite = if (autoArrete) 0f else 1f
        opaciteBarre += (cibleOpacite - opaciteBarre) * 0.12f

        // 3. on la dessine sous la carte du centre, en suivant sa place et sa taille (même pendant un glissement)
        val carte = cartes[page]
        val ecart = abs(position - Math.round(position))     // 0 = carte posée, 0,5 = pile entre deux
        val visibilite = opaciteBarre * (1f - ecart * 2.5f).coerceIn(0f, 1f)   // elle s'efface pendant le mouvement
        if (visibilite > 0.01f && carte.width > 0) {
            val s = carte.scaleX
            val centreX = carte.left + carte.width / 2f + carte.translationX
            val bas = carte.top + carte.height / 2f + carte.height * s / 2f
            val demi = (carte.width / 2f - 26f * dp) * s
            val y = bas - 9f * dp
            pinceau.strokeWidth = 2.5f * dp
            // le rail (très discret)
            pinceau.color = Color.WHITE
            pinceau.alpha = (visibilite * 26).toInt()
            c.drawLine(centreX - demi, y, centreX + demi, y, pinceau)
            // la partie remplie (douce, pas éblouissante)
            val avance = (ecoule.toFloat() / DELAI).coerceIn(0f, 1f)
            if (avance > 0f) {
                pinceau.alpha = (visibilite * 170).toInt()
                c.drawLine(centreX - demi, y, centreX - demi + 2f * demi * avance, y, pinceau)
            }
        }

        // image suivante, tant que l'écran est visible et que la barre peut encore bouger
        // (même en pause : il faut bien remarquer le moment où le menu se referme)
        if (enAvant && (!autoArrete || opaciteBarre > 0.01f)) postInvalidateOnAnimation()
    }

    // ---------- LE DOIGT ----------
    // Android envoie d'abord le toucher ici (« intercept »). Tant que le doigt n'a pas glissé à
    // l'horizontale, on le laisse aux cartes : un simple appui ouvre donc toujours la carte.
    // Dès qu'il glisse plus à l'horizontale qu'à la verticale, on le « vole » et c'est le manège qui bouge.
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        suivreVitesse(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> poser(ev)
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - xDepart
                val dy = ev.y - yDepart
                if (abs(dx) > seuil && abs(dx) > abs(dy)) {
                    glisse = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // un simple appui (pas de glissement) : on recentre au cas où on avait arrêté une carte en route
                if (!glisse) { lever(); glisserVers(Math.round(position).toFloat(), 380) }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        suivreVitesse(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> poser(ev)                // (doigt posé entre deux cartes)
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
                if (sens < 0) autoArrete = true           // retour en arrière : fin du défilement automatique
                lever()
                // (plus il reste de chemin, plus on prend le temps : entre 280 et 450 ms)
                val reste = abs(positionDepart + sens - position)
                glisserVers(positionDepart + sens, (280 + 170 * reste).toLong().coerceAtMost(450))
            }
        }
        return true
    }

    private fun poser(ev: MotionEvent) {
        animation?.cancel()                               // on peut rattraper une carte en plein mouvement
        xDepart = ev.x; yDepart = ev.y
        positionDepart = Math.round(position).toFloat()
        glisse = false
        doigt = true
    }

    private fun lever() {
        glisse = false
        doigt = false
        oublierVitesse()
        invalidate()                                      // (la barre repart)
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
