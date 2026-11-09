package com.will.animlauncher

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// LA RÉACTION SONORE : les bords de l'écran bougent au rythme de la musique du téléphone,
// PARTOUT (écran d'accueil, autres apps), même quand AnimLauncher est fermé.
//
// Comment ? Le service d'accessibilité (toujours en vie) pose une fenêtre transparente par-dessus
// tout, comme le rideau des animations. Elle ne capte aucun appui (on clique « à travers »).
//   - Elle ne dessine QUE quand de la musique joue (sinon : rien, zéro batterie).
//   - Elle s'efface en PLEIN ÉCRAN (vidéo, jeu : la barre d'état est cachée) et en mode paysage.
//   - Un seul dessin, le PRISME (validé par Will sur aperçu le 10/11, à la place des 4 styles d'avant) :
//     une fine ligne lumineuse qui fait le TOUR COMPLET de l'écran, à 5 dp du bord. La bande entre la
//     ligne et le bord se remplit de couleur selon le son, et la lumière déborde un peu vers l'intérieur.
//     Rangé « comme un piano » : graves en bas, mélodie sur les côtés, aigus en haut (voir EcouteSon.kt).
//   - 2 palettes au choix : CHAUDE (braise -> orange -> or) et FROIDE (violet -> bleu -> turquoise).
//
// Le choix (allumé ou non, quelle palette) est rangé dans un carnet à part, « son ».
object ReactionSonore {
    private const val CARNET = "son"
    const val STYLES = 2          // 0 = chaud, 1 = froid

    private fun carnet(c: Context) = c.applicationContext.getSharedPreferences(CARNET, Context.MODE_PRIVATE)

    fun actif(c: Context): Boolean = carnet(c).getBoolean("actif", false)
    fun definirActif(c: Context, valeur: Boolean) {
        carnet(c).edit().putBoolean("actif", valeur).apply()
        synchroniser(c)
    }

    @Volatile var style = 0          // la palette en cours (0 = chaude, 1 = froide), lue à chaque image
        private set
    fun lireStyle(c: Context): Int = carnet(c).getInt("style", 0).coerceIn(0, STYLES - 1).also { style = it }
    fun definirStyle(c: Context, numero: Int) {
        style = numero.coerceIn(0, STYLES - 1)
        carnet(c).edit().putInt("style", style).apply()
    }

    // A-t-on le droit d'écouter ? (la permission « micro », exigée par Android pour lire le niveau du son)
    fun permission(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private var service: Context? = null
    private var vue: VueSonore? = null

    // ---------- pour le DIAGNOSTIC affiché dans la fenêtre du rond (voir ReactionSonoreUi.kt) ----------
    val servicePret: Boolean get() = service != null      // le service d'accessibilité nous a bien prévenus
    val fenetrePosee: Boolean get() = vue != null         // la fenêtre des bords est posée sur l'écran
    @Volatile var musique = false                          // Android dit qu'une musique joue
    @Volatile var pleinEcran = false                       // on pense être en plein écran (vidéo, jeu)
    @Volatile var sourcePleinEcran = "?"                   // comment on l'a su (pour comprendre une erreur)
    @Volatile var dessine = false                          // les bords sont en train d'être dessinés

    // Appelé quand le service d'accessibilité est prêt (par le rideau, voir Rideau.kt)…
    fun demarrer(c: Context) {
        if (c !is AccessibilityService) return
        service = c
        synchroniser(c)
    }

    // …et quand il s'arrête. (Le « mode standard » de la version Perso a aussi un rideau, mais ce
    // n'est pas le service d'accessibilité : on l'ignore, dans les deux sens.)
    fun arreter(c: Context) {
        if (c !is AccessibilityService) return
        retirer()
        service = null
    }

    // Met la fenêtre en accord avec les réglages : posée si le mode est allumé (et permis), retirée sinon.
    fun synchroniser(c: Context) {
        lireStyle(c)
        val s = service
        if (s != null && actif(c) && permission(c)) {
            if (vue == null) poser(s)
        } else retirer()
    }

    // La taille RÉELLE de l'écran, barre de navigation comprise (comme le rideau des animations) :
    // le tour lumineux doit aussi passer tout en bas.
    private fun tailleEcran(s: Context): Point {
        val ecran = (s.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        val taille = Point()
        @Suppress("DEPRECATION") ecran.getRealSize(taille)
        return taille
    }

    // L'écran a tourné ou changé de taille : on remet la fenêtre aux bonnes dimensions.
    fun ajuster() {
        val v = vue ?: return
        val reglages = v.layoutParams as? WindowManager.LayoutParams ?: return
        val taille = tailleEcran(v.context)
        if (reglages.width == taille.x && reglages.height == taille.y) return
        reglages.width = taille.x; reglages.height = taille.y
        try { (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(v, reglages) } catch (_: Exception) {}
    }

    private fun poser(s: Context) {
        val gestionnaire = s.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val taille = tailleEcran(s)
        val reglages = WindowManager.LayoutParams(
            taille.x, taille.y,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or          // les appuis passent à travers
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        reglages.gravity = Gravity.TOP or Gravity.START
        // la fenêtre va jusque sous l'encoche / le poinçon de l'appareil photo, et sous les barres
        if (Build.VERSION.SDK_INT >= 30) {
            reglages.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            reglages.setFitInsetsTypes(0)
        } else if (Build.VERSION.SDK_INT >= 28) {
            reglages.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val v = VueSonore(s)
        try {
            gestionnaire.addView(v, reglages)
            vue = v
            AnimApp.noter("son : fenêtre des bords posée")
        } catch (e: Exception) {
            AnimApp.noter("son : fenêtre refusée (${e.javaClass.simpleName})")
        }
    }

    private fun retirer() {
        val v = vue ?: return
        vue = null
        try {
            (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Exception) {}
        EcouteSon.arreter()
    }
}

// LA FENÊTRE qui dessine le tour lumineux. « View » = un rectangle de l'écran qui sait se dessiner (onDraw).
class VueSonore(context: Context) : View(context) {

    private val d = resources.displayMetrics.density          // 1 dp = d pixels
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val minuteur = Handler(Looper.getMainLooper())

    // ---------- l'état ----------
    private var jouer = false            // faut-il dessiner en ce moment ?
    private var enBoucle = false         // la boucle d'images tourne-t-elle ?
    private var presence = 0f            // 0 = invisible … 1 = visible (pour apparaître / disparaître en douceur)
    private var pleinEcranAncien = false // (pour les vieux Android, voir plus bas)
    private var silences = 0
    private var dernier = 0L
    private var t = 0f                   // le temps qui passe, en secondes

    // les niveaux du son, ADOUCIS (ils montent vite et redescendent doucement : pas d'à-coup)
    private var g = 0f                   // graves
    private var m = 0f                   // médiums (mélodie)
    private var a = 0f                   // aigus
    private var h = 0.5f                 // hauteur de la note
    private var tClaque = -9f            // l'instant du dernier son sec
    private var claque = 0f

    // la mémoire des dernières images : permet de demander « combien valaient les graves il y a 0,2 s ? »
    // (c'est ce qui fait REMONTER la vague des basses du bas vers le haut, des deux côtés)
    private val memoireG = FloatArray(256)
    private var rang = 0
    private var imagesParSeconde = 60f

    // ---------- les 2 PALETTES : 4 couleurs, du grave (bas de l'écran) à l'aigu (haut) ----------
    private val PALETTES = arrayOf(
        intArrayOf(Color.rgb(255, 96, 64), Color.rgb(255, 150, 72), Color.rgb(255, 200, 120), Color.rgb(255, 232, 205)),   // chaude : braise -> orange -> or -> blanc chaud
        intArrayOf(Color.rgb(132, 110, 255), Color.rgb(84, 156, 255), Color.rgb(104, 216, 222), Color.rgb(214, 236, 255))  // froide : violet -> bleu -> turquoise -> glace
    )

    // ---------- les pinceaux, créés une seule fois ----------
    private val ajout = PorterDuffXfermode(PorterDuff.Mode.ADD)     // la lumière s'ADDITIONNE (comme de vraies lampes)
    private val flouLarge = BlurMaskFilter(16f * d, BlurMaskFilter.Blur.NORMAL)
    private val flouMoyen = BlurMaskFilter(4f * d, BlurMaskFilter.Blur.NORMAL)
    private val flouFin = BlurMaskFilter(2.5f * d, BlurMaskFilter.Blur.NORMAL)
    private val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; xfermode = ajout
    }
    private val bande = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT; xfermode = ajout     // bouts carrés : les morceaux se touchent sans se chevaucher
    }
    private val plein = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; xfermode = ajout }

    // ---------- LE TOUR, découpé en N petits segments (calculé quand la taille de l'écran est connue) ----------
    private val N = 180
    private val marge = 5f * d                   // la ligne est à 5 dp du bord ; la BANDE colorée est entre les deux
    private val lx = FloatArray(N + 1)           // la ligne
    private val ly = FloatArray(N + 1)
    private val bx = FloatArray(N + 1)           // le milieu de la bande (à mi-chemin entre la ligne et le bord)
    private val by = FloatArray(N + 1)
    private val couleurs = Array(2) { IntArray(N) }      // la couleur de chaque segment, pour chaque palette
    private val freq = FloatArray(N)                      // où est le segment sur le « piano » : 0 = bas (grave) … 1 = haut (aigu)
    private val hasard = FloatArray(N) { ((it * 7919) % 101) / 100f }
    private val intensites = FloatArray(N)
    private var w = 0f
    private var ht = 0f

    override fun onSizeChanged(largeur: Int, hauteur: Int, ancienneL: Int, ancienneH: Int) {
        w = largeur.toFloat(); ht = hauteur.toFloat()
        // l'arrondi des coins de l'écran (Android le connaît depuis la version 12) : la ligne le suit
        var coin = 40f * d
        if (Build.VERSION.SDK_INT >= 31) {
            try { display?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.let { if (it.radius > 0) coin = it.radius.toFloat() } } catch (_: Throwable) {}
        }
        for (k in 0..N) {
            val s = k.toFloat() / N
            pointDuTour(s, marge, coin).let { lx[k] = it[0]; ly[k] = it[1] }
            pointDuTour(s, marge / 2f, coin).let { bx[k] = it[0]; by[k] = it[1] }
        }
        for (k in 0 until N) {
            freq[k] = 1f - abs(2f * (k + 0.5f) / N - 1f)
            for (p in 0..1) couleurs[p][k] = teinte(PALETTES[p], freq[k])
        }
    }

    // Un point du tour, à « m » pixels du bord. s = 0 : en bas au milieu ; on monte par la GAUCHE,
    // s = 0,5 : en haut au milieu ; on redescend par la DROITE ; s = 1 : retour en bas au milieu.
    // Le tour est fait de 9 morceaux : demi-bas, coin, côté, coin, haut, coin, côté, coin, demi-bas.
    private fun pointDuTour(s: Float, m: Float, coin: Float): FloatArray {
        val r = max(coin - m, 4f * d)
        val demiBas = w / 2f - m - r
        val cote = ht - 2f * m - 2f * r
        val arc = (PI / 2).toFloat() * r
        val longueurs = floatArrayOf(demiBas, arc, cote, arc, 2f * demiBas, arc, cote, arc, demiBas)
        var reste = s.coerceIn(0f, 1f) * longueurs.sum()
        var i = 0
        while (i < 8 && reste > longueurs[i]) { reste -= longueurs[i]; i++ }
        val f = if (longueurs[i] > 0f) reste / longueurs[i] else 0f
        val demiPi = (PI / 2).toFloat()
        return when (i) {
            0 -> floatArrayOf(w / 2f - f * demiBas, ht - m)
            1 -> { val an = demiPi + f * demiPi; floatArrayOf(m + r + r * cos(an), ht - m - r + r * sin(an)) }
            2 -> floatArrayOf(m, ht - m - r - f * cote)
            3 -> { val an = PI.toFloat() + f * demiPi; floatArrayOf(m + r + r * cos(an), m + r + r * sin(an)) }
            4 -> floatArrayOf(m + r + f * 2f * demiBas, m)
            5 -> { val an = 3f * demiPi + f * demiPi; floatArrayOf(w - m - r + r * cos(an), m + r + r * sin(an)) }
            6 -> floatArrayOf(w - m, m + r + f * cote)
            7 -> { val an = f * demiPi; floatArrayOf(w - m - r + r * cos(an), ht - m - r + r * sin(an)) }
            else -> floatArrayOf(w - m - r - f * demiBas, ht - m)
        }
    }

    // La couleur d'une palette à une position 0..1 (on glisse doucement d'une couleur à la suivante).
    private fun teinte(pal: IntArray, f: Float): Int {
        val x = f.coerceIn(0f, 0.999f) * 3f
        val i = x.toInt()
        return melange(pal[i], pal[i + 1], x - i)
    }
    private fun melange(c1: Int, c2: Int, k: Float): Int {
        val x = k.coerceIn(0f, 1f)
        return Color.rgb((Color.red(c1) + (Color.red(c2) - Color.red(c1)) * x).toInt(),
            (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * x).toInt(),
            (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * x).toInt())
    }

    // ---------- quand jouer ? (vérifié une fois par seconde) ----------
    private val verification = object : Runnable {
        override fun run() {
            verifier()
            minuteur.postDelayed(this, 1000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Vieux Android (avant la version 11) : on est prévenu quand une app cache la barre d'état.
        if (Build.VERSION.SDK_INT < 30) {
            @Suppress("DEPRECATION")
            setOnSystemUiVisibilityChangeListener { etat ->
                pleinEcranAncien = (etat and SYSTEM_UI_FLAG_FULLSCREEN) != 0
            }
        }
        minuteur.post(verification)
    }

    override fun onDetachedFromWindow() {
        minuteur.removeCallbacks(verification)
        ReactionSonore.dessine = false
        EcouteSon.arreter()
        super.onDetachedFromWindow()
    }

    // Plein écran = la barre d'état (heure, batterie) est cachée : c'est le cas des vidéos et des jeux.
    //
    // PIÈGE (corrigé le 10/11) : notre fenêtre est posée AU-DESSUS de la barre d'état, et Android ne parle
    // de la barre d'état qu'aux fenêtres posées EN DESSOUS. Notre fenêtre ne la « voyait » donc jamais,
    // croyait être en plein écran tout le temps… et ne dessinait jamais rien.
    // Maintenant : 1) on demande à notre fenêtre ; si elle ne connaît pas la barre d'état (hauteur 0),
    // 2) on demande à Android l'état de l'ÉCRAN ; si personne ne sait, 3) on suppose « pas plein écran »
    // (mieux vaut voir l'effet pendant une vidéo que ne jamais le voir).
    private fun pleinEcran(): Boolean {
        if (Build.VERSION.SDK_INT >= 30) {
            val type = WindowInsets.Type.statusBars()
            val marges = rootWindowInsets
            if (marges != null && marges.getInsetsIgnoringVisibility(type).top > 0) {
                ReactionSonore.sourcePleinEcran = "fenêtre"
                return !marges.isVisible(type)
            }
            try {
                val ecran = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics.windowInsets
                if (ecran.getInsetsIgnoringVisibility(type).top > 0) {
                    ReactionSonore.sourcePleinEcran = "écran"
                    return !ecran.isVisible(type)
                }
            } catch (_: Throwable) {}
            ReactionSonore.sourcePleinEcran = "inconnu"
            return false
        }
        ReactionSonore.sourcePleinEcran = "ancien Android"
        return pleinEcranAncien
    }

    private fun verifier() {
        val paysage = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val musique = try { audio.isMusicActive } catch (_: Exception) { false }
        val plein = pleinEcran()
        silences = if (musique) 0 else silences + 1
        val voulu = !paysage && !plein && silences < 3              // 3 s sans musique : on arrête
        // pour le diagnostic, et une ligne dans le journal quand quelque chose change
        if (musique != ReactionSonore.musique || plein != ReactionSonore.pleinEcran) {
            AnimApp.noter("son : musique ${if (musique) "oui" else "non"}, plein écran ${if (plein) "oui" else "non"} (${ReactionSonore.sourcePleinEcran})")
        }
        ReactionSonore.musique = musique
        ReactionSonore.pleinEcran = plein
        if (voulu && !EcouteSon.enMarche) {
            if (!EcouteSon.demarrer()) return                        // le téléphone refuse : on ne dessine rien
        }
        if (!voulu && EcouteSon.enMarche && presence < 0.01f) EcouteSon.arreter()
        jouer = voulu
        ReactionSonore.dessine = voulu
        if (jouer && !enBoucle) {
            enBoucle = true
            dernier = SystemClock.uptimeMillis()
            invalidate()                                             // « invalidate » = redessine-moi
        }
    }

    override fun onConfigurationChanged(nouvelle: Configuration?) {
        super.onConfigurationChanged(nouvelle)
        ReactionSonore.ajuster()                                     // l'écran a peut-être tourné : nouvelle taille
        verifier()
    }

    // ---------- petits outils de calcul ----------
    private fun cloche(x: Float, centre: Float, largeur: Float): Float {
        val e = (x - centre) / largeur
        return exp(-e * e)
    }
    private fun ilYA(secondes: Float): Float {
        val images = (secondes * imagesParSeconde).toInt().coerceIn(0, 255)
        return memoireG[(rang - images) and 255]
    }
    private fun opacite(x: Float): Int = (x * presence * 255f).toInt().coerceIn(0, 255)

    // Un petit ÉCLAT en étoile à 4 branches (sur les aigus).
    private fun eclat(c: Canvas, x: Float, y: Float, taille: Float, force: Float, couleur: Int) {
        if (force * presence < 0.02f) return
        plein.color = couleur
        plein.maskFilter = flouFin; plein.alpha = opacite(force * 0.6f); c.drawCircle(x, y, taille * 0.6f * d, plein)
        plein.maskFilter = null
        trait.maskFilter = null; trait.color = couleur
        trait.strokeWidth = 0.8f * d; trait.alpha = opacite(force)
        c.drawLine(x - taille * d, y, x + taille * d, y, trait)
        c.drawLine(x, y - taille * d, x, y + taille * d, trait)
        plein.color = Color.WHITE; plein.alpha = opacite(force); c.drawCircle(x, y, 0.9f * d, plein)
    }

    // ---------- UNE IMAGE ----------
    override fun onDraw(c: Canvas) {
        if (w <= 0f) return
        val maintenant = SystemClock.uptimeMillis()
        val dt = ((maintenant - dernier) / 1000f).coerceIn(0.001f, 0.05f)       // le temps depuis l'image d'avant
        dernier = maintenant
        t += dt
        imagesParSeconde += (1f / dt - imagesParSeconde) * 0.05f

        // apparaître / disparaître en douceur
        presence += ((if (jouer) 1f else 0f) - presence) * min(1f, dt * 3f)
        if (!jouer && presence < 0.01f) {            // complètement effacé : on arrête la boucle (plus rien n'est dessiné)
            presence = 0f; enBoucle = false
            return
        }

        // on adoucit les mesures : montée rapide, descente lente
        g += (EcouteSon.grave - g) * min(1f, dt * (if (EcouteSon.grave > g) 30f else 6f))
        m += (EcouteSon.medium - m) * min(1f, dt * (if (EcouteSon.medium > m) 25f else 5f))
        a += (EcouteSon.aigu - a) * min(1f, dt * (if (EcouteSon.aigu > a) 40f else 12f))
        h += (EcouteSon.hauteur - h) * min(1f, dt * 8f)
        // un son sec : on retient l'instant (au plus un toutes les 0,25 s)
        if (EcouteSon.claque > 0.5f && t - tClaque > 0.25f) tClaque = t
        claque = exp(-(t - tClaque) / 0.14f)
        rang = (rang + 1) and 255
        memoireG[rang] = g

        prisme(c, couleurs[ReactionSonore.style.coerceIn(0, 1)], PALETTES[ReactionSonore.style.coerceIn(0, 1)][3])
        postInvalidateOnAnimation()                  // … et on demande l'image suivante
    }

    // ================= LE PRISME, TOUR COMPLET =================
    private fun prisme(c: Canvas, coul: IntArray, eclatCouleur: Int) {
        // 1) LA FORCE de chaque segment, selon sa place sur le « piano »
        for (k in 0 until N) {
            val f = freq[k]
            var i = ilYA(f * 0.42f) * max(0f, 1f - f / 0.62f).pow(1.3f)      // basses : partent du bas et montent des deux côtés
            i += m * cloche(f, 0.42f + 0.30f * h, 0.065f)                    // mélodie : une zone qui suit la note
            i += 0.8f * claque * cloche(f, 0.80f, 0.06f)                     // son sec (caisse claire)
            i += a * (0.3f + 0.7f * hasard[k]) * max(0f, (f - 0.87f) / 0.13f)   // aigus : scintillement tout en haut
            intensites[k] = min(1f, i)
        }

        // 2) LA BANDE entre la ligne et le bord : chaque morceau prend la couleur de sa place,
        //    plus ou moins forte (et plus claire, vers le blanc) selon le son à cet endroit
        bande.strokeWidth = marge + 1f
        for (k in 0 until N) {
            val i = intensites[k]
            bande.color = melange(coul[k], Color.WHITE, 0.35f * i * i)
            bande.alpha = opacite(0.10f + 0.80f * i)
            c.drawLine(bx[k], by[k], bx[k + 1], by[k + 1], bande)
        }

        // 3) LE DÉBORDEMENT vers l'intérieur : deux halos flous (par groupes de 4 segments, moins de travail)
        var k = 0
        while (k < N) {
            val i = (intensites[k] + intensites[k + 1] + intensites[k + 2] + intensites[k + 3]) / 4f
            if (i > 0.04f) {
                trait.color = coul[k + 1]
                trait.maskFilter = flouLarge; trait.strokeWidth = (1f + 2.2f * i) * 5f * d; trait.alpha = opacite(0.55f * (0.14f + 0.86f * i))
                c.drawLine(lx[k], ly[k], lx[k + 4], ly[k + 4], trait)
                trait.maskFilter = flouMoyen; trait.strokeWidth = (1f + 2.2f * i) * 2.2f * d; trait.alpha = opacite(0.70f * (0.14f + 0.86f * i))
                c.drawLine(lx[k], ly[k], lx[k + 4], ly[k + 4], trait)
            }
            k += 4
        }

        // 4) LA LIGNE elle-même, puis son cœur blanc là où elle est forte
        trait.maskFilter = null
        for (s in 0 until N) {
            val i = intensites[s]
            trait.color = coul[s]
            trait.strokeWidth = (1f + 2.2f * i) * d; trait.alpha = opacite(0.14f + 0.86f * i)
            c.drawLine(lx[s], ly[s], lx[s + 1], ly[s + 1], trait)
            if (i > 0.45f) {
                trait.color = Color.WHITE; trait.strokeWidth = 0.5f * d; trait.alpha = opacite((i - 0.45f) * 0.8f)
                c.drawLine(lx[s], ly[s], lx[s + 1], ly[s + 1], trait)
            }
        }

        // 5) les aigus : quelques éclats posés sur le haut de la ligne
        if (a > 0.03f) for (j in 0..8) {
            val s = (N * (0.40f + 0.20f * ((j * 0.37f + 0.11f) % 1f))).toInt()
            val onde = max(0f, sin(t * 2.3f + j * 5f))
            eclat(c, lx[s], ly[s], 4.5f, a * onde * onde, eclatCouleur)
        }
    }
}
