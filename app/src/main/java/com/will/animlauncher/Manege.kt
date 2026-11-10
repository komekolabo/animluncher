package com.will.animlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
//
//                     ━━━━──   •   •                ← sous les cartes, le trait de la carte affichée
//                                                     se remplit de blanc : c'est le temps avant la suivante
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
// LE DÉFILEMENT AUTOMATIQUE est géré ICI (pour que le trait qui se remplit et le changement de carte
// soient parfaitement synchronisés) : toutes les DELAI ms, on passe à la carte de droite. Les règles :
//  - le compteur ne tourne pas sous le doigt, pendant un glissement, ni quand « peutDefiler » dit non
//    (menu ou fenêtre ouverts : c'est l'accueil qui décide) ;
//  - tout changement de carte remet le compteur à zéro (5 s pleines sur la nouvelle carte) ;
//  - revenir à la carte PRÉCÉDENTE met « autoArrete » à vrai : plus de défilement (le trait redevient
//    tout blanc), jusqu'à ce que l'accueil le remette à faux (quand on quitte l'app).
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
    // Vrai quand on est revenu en arrière : défilement automatique arrêté.
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

    // le compteur du défilement automatique
    private var ecoule = 0L                   // le temps déjà passé sur la carte du centre (ms)
    private var dernierInstant = 0L           // l'heure de l'image précédente (pour savoir combien de temps a passé)

    // Où en est le compteur, de 0 (la carte vient d'arriver) à 1 (la suivante arrive).
    // Défilement arrêté → 1 : le trait des points reste tout blanc, comme avant.
    val avancement: Float get() = if (autoArrete) 1f else (ecoule.toFloat() / DELAI).coerceIn(0f, 1f)

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

    // ---------- LE COMPTEUR DU DÉFILEMENT AUTOMATIQUE ----------
    // On le fait avancer à chaque image dessinée (« dispatchDraw » = le moment où le manège dessine
    // ses cartes) : comme ça il est réglé sur l'écran, et le trait des points avance au même rythme.
    override fun dispatchDraw(c: Canvas) {
        super.dispatchDraw(c)                                 // les cartes
        if (cartes.isEmpty()) return
        val maintenant = SystemClock.uptimeMillis()
        // le temps ne compte que si rien ne l'empêche
        val compte = enAvant && !autoArrete && !doigt && !enMouvement && peutDefiler()
        if (compte && dernierInstant != 0L) ecoule += min(maintenant - dernierInstant, 100L)   // (100 : pas de grand saut après une pause)
        dernierInstant = if (enAvant) maintenant else 0L
        if (compte && ecoule >= DELAI) suivante()
        // image suivante tant que l'écran est visible (même en pause : il faut remarquer le moment
        // où le menu se referme pour repartir)
        if (enAvant && !autoArrete) postInvalidateOnAnimation()
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
        invalidate()                                      // (le compteur repart)
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

// UN POINT SOUS LES CARTES (10/11). Les points d'avant étaient de simples ronds ; celui de la carte
// affichée devient un petit trait (pilule) qui sert aussi de MINUTEUR :
//
//      ━━━━━━──────   la partie blanche avance de gauche à droite pendant les 5 s,
//                     puis la carte suivante arrive et c'est son point qui devient le trait.
//
// Si le défilement automatique est arrêté (retour en arrière), le trait reste tout blanc.
// Le point se dessine lui-même au centre de sa case ; il s'allonge / se raccourcit en douceur.
class PointManege(context: Context, private val manege: Manege, private val numero: Int) : View(context) {
    private val dp = resources.displayMetrics.density
    private val gris = Color.rgb(63, 63, 70)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val forme = RectF()
    private val contour = Path()
    private var largeur = 6f * dp              // la largeur actuelle (6 dp = rond, 22 dp = trait)
    private var blanc = 0f                     // la part blanche affichée (0 → 1), lissée pour ne pas sauter

    override fun onDraw(c: Canvas) {
        val actif = manege.page == numero
        val cible = (if (actif) 22f else 6f) * dp
        largeur += (cible - largeur) * 0.22f                       // il s'allonge / se raccourcit en douceur
        if (abs(cible - largeur) < 0.3f) largeur = cible
        val h = 6f * dp
        forme.set(width / 2f - largeur / 2f, height / 2f - h / 2f, width / 2f + largeur / 2f, height / 2f + h / 2f)

        // le fond gris (la forme entière)
        p.color = gris
        c.drawRoundRect(forme, h / 2f, h / 2f, p)

        // la partie blanche : seulement sur le point de la carte affichée
        val cibleBlanc = if (actif) manege.avancement else 0f
        // (en remplissage, elle suit exactement le compteur ; quand elle doit se vider ou devenir
        //  toute blanche d'un coup, elle glisse en douceur)
        blanc = if (actif && !manege.autoArrete && cibleBlanc >= blanc) cibleBlanc else blanc + (cibleBlanc - blanc) * 0.18f
        if (blanc > 0.005f) {
            contour.reset()
            contour.addRoundRect(forme, h / 2f, h / 2f, Path.Direction.CW)
            c.save()
            c.clipPath(contour)                                      // on ne déborde pas de la pilule
            p.color = Style.TEXTE
            c.drawRect(forme.left, forme.top, forme.left + forme.width() * blanc, forme.bottom, p)
            c.restore()
        }

        // image suivante, tant qu'il y a quelque chose qui bouge
        val bouge = abs(cible - largeur) > 0f || abs(cibleBlanc - blanc) > 0.005f || (actif && !manege.autoArrete)
        if (bouge && isShown) postInvalidateOnAnimation()
    }
}
