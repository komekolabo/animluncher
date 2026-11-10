package com.will.animlauncher

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import java.nio.ByteBuffer

// LA RÉACTION SONORE : les bords de l'écran bougent au rythme de la musique du téléphone,
// PARTOUT (écran d'accueil, autres apps), même quand AnimLauncher est fermé.
//
// Comment ? Le service d'accessibilité (toujours en vie) pose une fenêtre transparente par-dessus
// tout, comme le rideau des animations. Elle ne capte aucun appui (on clique « à travers »).
//   - Elle ne dessine QUE quand de la musique joue (sinon : rien, zéro batterie).
//   - Elle s'efface en PLEIN ÉCRAN (vidéo, jeu : la barre d'état est cachée) et en mode paysage.
//   - Un seul dessin, le PRISME : une fine ligne lumineuse qui fait le TOUR COMPLET de l'écran, à 5 dp du
//     bord. La bande entre la ligne et le bord se remplit de couleur selon le son, et la lumière déborde un
//     peu vers l'intérieur. C'est un ÉGALISEUR enroulé autour de l'écran : chaque endroit du tour brille selon
//     la force de SA tranche de son (graves en bas, aigus en haut, voir « spectre » dans EcouteSon.kt).
//   - UN RUBAN CONTINU (10/11, 22h). Avant, le tour était dessiné en 180 puis 360 petits morceaux : Will
//     arrivait à les COMPTER (la lumière s'additionne, donc chaque jointure ressortait). Maintenant chaque
//     couche est UN SEUL trait qui fait tout le tour, et c'est sa peinture qui change le long du chemin :
//     un « dégradé tournant » (SweepGradient) centré au milieu de l'écran, comme un phare qui éclaire chaque
//     direction d'une couleur différente. Le téléphone fond lui-même les couleurs entre elles : plus aucun bord.
//   - LA LUEUR FONDUE (10/11, 22h30). Le halo était fait de 7 traits de plus en plus larges posés les uns sur
//     les autres : Will voyait leurs bords, comme de grandes bandes autour de l'écran. Maintenant le halo est
//     peint dans une PETITE image (6 fois plus petite que l'écran), puis cette image est agrandie : en
//     l'agrandissant, le téléphone la floute, et les bords des traits disparaissent. (Et c'est moins de travail :
//     36 fois moins de points à colorier.)
//   - 5 PALIERS (idée de Will, voir « palier » dans EcouteSon.kt) et 3 COULEURS réglables :
//       1 très lent  -> couleur « calme » + des petits Z qui montent
//       2 calme      -> couleur « calme » (bleu)
//       3 moyen      -> couleur « moyen » (vert)
//       4 ça envoie  -> couleur « ça envoie » (rouge-orangé)
//       5 très fort  -> couleur « ça envoie » + des confettis calés sur la musique (Confettis.kt)
//     Dans chaque couleur, le dégradé du bas vers le haut de l'écran est gardé.
//
// Le choix (allumé ou non, les trois couleurs) est rangé dans un carnet à part, « son ».
object ReactionSonore {
    private const val CARNET = "son"
    // LES TROIS COULEURS : 0 = calme, 1 = moyen, 2 = ça envoie.
    // Une couleur = une « teinte » : un angle de 0 à 360 sur la roue des couleurs (0 = rouge, 120 = vert,
    // 240 = bleu). L'app en tire toute seule un dégradé de 4 couleurs (voir palette()).
    val TEINTES_DEFAUT = floatArrayOf(215f, 135f, 6f)         // bleu, vert, rouge-orangé
    // (« teinte_ecoute » : l'ancien nom de la couleur verte, gardé pour ne pas perdre le choix déjà fait)
    private val CLES = arrayOf("teinte_calme", "teinte_ecoute", "teinte_fort")
    private val teintes = floatArrayOf(215f, 135f, 6f)

    private fun carnet(c: Context) = c.applicationContext.getSharedPreferences(CARNET, Context.MODE_PRIVATE)

    fun actif(c: Context): Boolean = carnet(c).getBoolean("actif", false)
    fun definirActif(c: Context, valeur: Boolean) {
        carnet(c).edit().putBoolean("actif", valeur).apply()
        synchroniser(c)
    }

    fun teinte(mode: Int): Float = teintes[mode.coerceIn(0, 2)]
    // Augmente de 1 à chaque changement de couleur : le dessin sait ainsi qu'il doit refaire ses dégradés.
    @Volatile var versionCouleurs = 0
        private set

    fun lireCouleurs(c: Context) {
        for (m in 0..2) teintes[m] = carnet(c).getFloat(CLES[m], TEINTES_DEFAUT[m])
        versionCouleurs++
    }

    // Change une des trois couleurs (mode : 0 = calme, 1 = moyen, 2 = ça envoie). Le dessin suit tout de suite.
    // « garder » = l'écrire aussi dans le carnet (on ne le fait qu'au lâcher du doigt, pas à chaque millimètre).
    fun definirTeinte(c: Context, mode: Int, nouvelle: Float, garder: Boolean) {
        val m = mode.coerceIn(0, 2)
        val t = ((nouvelle % 360f) + 360f) % 360f
        teintes[m] = t
        versionCouleurs++
        if (garder) carnet(c).edit().putFloat(CLES[m], t).apply()
    }

    // LE DÉGRADÉ tiré d'une teinte : 4 couleurs, du grave (bas de l'écran) à l'aigu (haut).
    // On tourne un peu autour de la teinte choisie (-28°, pile dessus, +30°), puis un presque-blanc pour les aigus.
    // Plus le moment est fort, plus la couleur est vive (saturation 0,75 / 0,78 / 0,82).
    fun palette(angleChoisi: Float, mode: Int): IntArray {
        val vif = when (mode) { 2 -> 0.82f; 1 -> 0.78f; else -> 0.75f }
        fun couleur(angle: Float, saturation: Float) =
            Color.HSVToColor(floatArrayOf(((angle % 360f) + 360f) % 360f, saturation.coerceIn(0f, 1f), 1f))
        return intArrayOf(couleur(angleChoisi - 28f, vif), couleur(angleChoisi, vif * 0.92f), couleur(angleChoisi + 30f, vif * 0.8f), couleur(angleChoisi + 30f, 0.18f))
    }

    // L'APERÇU pendant qu'on choisit une couleur dans la fenêtre du rond : -1 = non, sinon la couleur qu'on
    // montre (0 = calme, 1 = moyen, 2 = ça envoie). Le tour s'allume alors même sans musique, pour voir ce qu'on règle.
    @Volatile var apercu = -1
        private set
    fun montrer(mode: Int) {
        apercu = mode
        vue?.reveiller()
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
        lireCouleurs(c)
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

    private var a = 0f                   // les aigus (pour les petits éclats du haut)
    private var coup = 0f                // le coup de basse du moment
    private var coupAvant = 0f           // celui de l'image d'avant (pour repérer l'instant où un coup tombe)
    private var sommeil = 0f             // 0 … 1 : les petits Z sont-ils là ? (palier 1)

    // ---------- les 3 COULEURS (calme / moyen / ça envoie) et le fondu entre elles ----------
    private var versionVue = -1          // la version des couleurs pour laquelle nos dégradés sont faits
    private val poids = floatArrayOf(0f, 1f, 0f)     // la part de chaque couleur dans l'image (glisse en ~0,25 s)
    private val clairs = IntArray(3)     // le presque-blanc de chaque couleur (éclats des aigus, petits Z)
    private val vives = Array(3) { IntArray(4) }     // les 4 couleurs de chaque palette (pour les confettis, voir Confettis.kt)

    // ---------- les pinceaux, créés une seule fois ----------
    private val ajout = PorterDuffXfermode(PorterDuff.Mode.ADD)     // la lumière s'ADDITIONNE (comme de vraies lampes)
    // LA TACHE DE LUMIÈRE (pour les petits éclats) : une petite image ronde de 64 x 64, faite une seule fois.
    // Elle ne contient que de la transparence (« ALPHA_8 ») : c'est le pinceau qui lui donne sa couleur.
    private val tache: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ALPHA_8).also { image ->
        val octets = ByteArray(64 * 64)
        for (y in 0 until 64) for (x in 0 until 64) {
            val r2 = ((x - 31.5f) * (x - 31.5f) + (y - 31.5f) * (y - 31.5f)) / (32f * 32f)     // 0 au centre, 1 au bord
            val v = (exp(-r2 * 3.125f) - 0.044f) / 0.956f                                        // une cloche qui finit à 0 au bord
            octets[y * 64 + x] = (v.coerceIn(0f, 1f) * 255f).toInt().toByte()
        }
        image.copyPixelsFromBuffer(ByteBuffer.wrap(octets))
    }
    private val tampon = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { xfermode = ajout }
    private val zone = RectF()
    // Colle la tache en (x, y), de rayon r, avec une couleur et une force (0..1).
    private fun tamponner(c: Canvas, x: Float, y: Float, r: Float, couleur: Int, force: Float) {
        val al = opacite(force)
        if (al <= 0) return
        tampon.color = couleur; tampon.alpha = al
        zone.set(x - r, y - r, x + r, y + r)
        c.drawBitmap(tache, null, zone, tampon)
    }
    // Écran rapide (plus de 90 images/s) : on ne dessine qu'une image sur deux.
    private var ecranRapide = false
    private val redessiner = Runnable { invalidate() }
    private val sauterUneImage = Runnable { postOnAnimation(redessiner) }
    // LE RUBAN : le pinceau qui trace un trait tout autour de l'écran, d'un seul geste (coins arrondis)
    private val ruban = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; xfermode = ajout
    }
    private val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; xfermode = ajout
    }
    private val plein = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; xfermode = ajout }
    private val lettre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; xfermode = ajout
    }
    // LA LUEUR : la petite image où l'on peint le halo, sa « toile » (pour dessiner dedans), et le pinceau qui la
    // recolle sur l'écran en l'agrandissant (FILTER_BITMAP = agrandir en floutant, pas en gros carrés).
    private val REDUCTION = 6f
    private var lueur: Bitmap? = null
    private var toileLueur: Canvas? = null
    private val colleLueur = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = ajout }

    // ---------- LE TOUR (calculé quand la taille de l'écran est connue) ----------
    // M REPÈRES répartis sur le tour. Ce ne sont PAS des morceaux qu'on dessine : seulement les endroits où
    // l'on dit au dégradé « ici, telle couleur ». Entre deux repères, le téléphone fond les couleurs tout seul.
    private val M = 240
    private val marge = 5f * d                   // la ligne est à 5 dp du bord ; la BANDE colorée est entre les deux
    private val ptX = FloatArray(M)              // où est chaque repère sur la ligne
    private val ptY = FloatArray(M)
    private val freq = FloatArray(M)             // sa place sur le « piano » : 0 = bas (grave) … 1 = haut (aigu)
    private val ordre = IntArray(M)              // les repères rangés dans le sens du dégradé tournant
    private val positions = FloatArray(M + 2)    // l'angle de chacun, de 0 à 1 (1 = un tour complet)
    private val couleurs = Array(3) { IntArray(M) }      // la couleur de chaque repère : [0] = calme, [1] = moyen, [2] = ça envoie
    private val brutes = FloatArray(M)                    // la lumière de chaque repère, avant puis après lissage
    private val intensites = FloatArray(M)
    // les 4 dégradés d'une image : la bande, le halo, la ligne, le cœur blanc
    private val cBande = IntArray(M + 2)
    private val cHalo = IntArray(M + 2)
    private val cLigne = IntArray(M + 2)
    private val cCoeur = IntArray(M + 2)
    private val cheminLigne = Path()             // le trajet de la ligne (un rectangle aux coins arrondis)
    private val cheminBande = Path()             // celui du milieu de la bande
    private var w = 0f
    private var ht = 0f

    override fun onSizeChanged(largeur: Int, hauteur: Int, ancienneL: Int, ancienneH: Int) {
        w = largeur.toFloat(); ht = hauteur.toFloat()
        // la petite image de la lueur : 6 fois plus petite que l'écran dans chaque sens
        try {
            lueur?.recycle()
            val image = Bitmap.createBitmap((largeur / REDUCTION).toInt() + 1, (hauteur / REDUCTION).toInt() + 1, Bitmap.Config.ARGB_8888)
            lueur = image
            toileLueur = Canvas(image)
        } catch (_: Throwable) { lueur = null; toileLueur = null }
        // l'arrondi des coins de l'écran (Android le connaît depuis la version 12) : la ligne le suit
        var coin = 40f * d
        if (Build.VERSION.SDK_INT >= 31) {
            try { display?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.let { if (it.radius > 0) coin = it.radius.toFloat() } } catch (_: Throwable) {}
        }
        // les deux trajets : des rectangles aux coins arrondis, un peu en retrait du bord
        val r = max(coin - marge, 4f * d)
        cheminLigne.rewind()
        cheminLigne.addRoundRect(marge, marge, w - marge, ht - marge, r, r, Path.Direction.CW)
        val demi = marge / 2f
        val rBande = max(coin - demi, 4f * d)
        cheminBande.rewind()
        cheminBande.addRoundRect(demi, demi, w - demi, ht - demi, rBande, rBande, Path.Direction.CW)
        // les repères : où ils sont, leur place sur le piano, et leur ANGLE vu du centre de l'écran
        // (0° = vers la droite, puis on tourne comme les aiguilles d'une montre : bas, gauche, haut)
        var premier = 0
        var plusPetit = 400f
        val angles = FloatArray(M)
        for (k in 0 until M) {
            val p = pointDuTour(k.toFloat() / M, marge, coin)
            ptX[k] = p[0]; ptY[k] = p[1]
            freq[k] = 1f - abs(2f * k / M - 1f)
            var an = Math.toDegrees(atan2((p[1] - ht / 2f).toDouble(), (p[0] - w / 2f).toDouble())).toFloat()
            if (an < 0f) an += 360f
            angles[k] = an
            if (an < plusPetit) { plusPetit = an; premier = k }
        }
        // le dégradé tournant veut ses repères rangés par angle croissant : on part du plus petit angle
        for (j in 0 until M) {
            ordre[j] = (premier + j) % M
            positions[j + 1] = (angles[ordre[j]] / 360f).coerceIn(0f, 1f)
        }
        // (sécurité : les angles ne doivent jamais reculer, sinon le dégradé serait refusé)
        for (j in 2..M) if (positions[j] < positions[j - 1]) positions[j] = positions[j - 1]
        positions[0] = 0f
        positions[M + 1] = 1f
        versionVue = -1                              // (les couleurs seront refaites à la prochaine image)
    }

    // Refait les couleurs des repères, seulement si Will vient de changer une des trois couleurs.
    private fun recolorer() {
        val version = ReactionSonore.versionCouleurs
        if (version == versionVue) return
        versionVue = version
        for (p in 0..2) {
            val pal = ReactionSonore.palette(ReactionSonore.teinte(p), p)
            for (k in 0 until M) couleurs[p][k] = teinte(pal, freq[k])
            for (q in 0..3) vives[p][q] = pal[q]
            clairs[p] = pal[3]
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

    // Le mélange de 3 couleurs, chacune avec sa part (les 3 parts font 1 au total).
    private fun trio(c0: Int, c1: Int, c2: Int, p0: Float, p1: Float, p2: Float): Int =
        Color.rgb((Color.red(c0) * p0 + Color.red(c1) * p1 + Color.red(c2) * p2).toInt().coerceIn(0, 255),
            (Color.green(c0) * p0 + Color.green(c1) * p1 + Color.green(c2) * p2).toInt().coerceIn(0, 255),
            (Color.blue(c0) * p0 + Color.blue(c1) * p1 + Color.blue(c2) * p2).toInt().coerceIn(0, 255))

    // Une couleur plus ou moins transparente (force : 0 = invisible … 1 = pleine).
    private fun voile(couleur: Int, force: Float): Int =
        Color.argb((force.coerceIn(0f, 1f) * 255f).toInt(), Color.red(couleur), Color.green(couleur), Color.blue(couleur))

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

    // Appelé par la fenêtre du rond quand on commence / finit de choisir une couleur : on réagit tout de suite.
    fun reveiller() = verifier()

    private fun verifier() {
        val paysage = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val musique = try { audio.isMusicActive } catch (_: Exception) { false }
        ecranRapide = try { (display?.refreshRate ?: 60f) > 90f } catch (_: Throwable) { false }
        // Écouteurs / enceinte Bluetooth : le son arrive ~0,18 s plus tard aux oreilles -> on retarde le dessin d'autant.
        // (26 et 27 = les nouveaux appareils Bluetooth « basse consommation ».)
        val bluetooth = try {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == 26 || it.type == 27 }
        } catch (_: Throwable) { false }
        EcouteSon.retardMs = if (bluetooth) 180 else 0
        val plein = pleinEcran()
        silences = if (musique) 0 else silences + 1
        // 3 s sans musique : on arrête (sauf pendant l'aperçu d'une couleur : là on montre le tour même en silence)
        val voulu = !paysage && !plein && (silences < 3 || ReactionSonore.apercu >= 0)
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
    private fun opacite(x: Float): Int = (x * presence * 255f).toInt().coerceIn(0, 255)

    // Un petit ÉCLAT en étoile à 4 branches (sur les aigus).
    private fun eclat(c: Canvas, x: Float, y: Float, taille: Float, force: Float, couleur: Int) {
        if (force * presence < 0.02f) return
        tamponner(c, x, y, taille * 1.5f * d, couleur, force * 0.6f)
        trait.color = couleur
        trait.strokeWidth = 0.8f * d; trait.alpha = opacite(force)
        c.drawLine(x - taille * d, y, x + taille * d, y, trait)
        c.drawLine(x, y - taille * d, x, y + taille * d, trait)
        plein.color = Color.WHITE; plein.alpha = opacite(force); c.drawCircle(x, y, 0.9f * d, plein)
    }

    // UNE COUCHE DU RUBAN : un seul trait qui fait tout le tour, peint avec un dégradé tournant.
    private fun couche(c: Canvas, chemin: Path, largeur: Float, peinture: SweepGradient, force: Float) {
        val al = opacite(force)
        if (al <= 0) return
        ruban.shader = peinture
        ruban.strokeWidth = largeur
        ruban.alpha = al
        c.drawPath(chemin, ruban)
    }

    // ---------- UNE IMAGE ----------
    override fun onDraw(c: Canvas) {
        if (w <= 0f) return
        val maintenant = SystemClock.uptimeMillis()
        val dt = ((maintenant - dernier) / 1000f).coerceIn(0.001f, 0.05f)       // le temps depuis l'image d'avant
        dernier = maintenant
        t += dt

        // apparaître / disparaître en douceur
        presence += ((if (jouer) 1f else 0f) - presence) * min(1f, dt * 3f)
        if (!jouer && presence < 0.01f) {            // complètement effacé : on arrête la boucle (plus rien n'est dessiné)
            presence = 0f; enBoucle = false
            return
        }

        // LA COULEUR DU MOMENT, selon le palier : 1 et 2 -> calme, 3 -> moyen, 4 et 5 -> ça envoie.
        // (Pendant l'aperçu d'une couleur, c'est celle qu'on règle.)
        // On ne saute pas d'une couleur à l'autre : la part de chacune (« poids ») glisse en un quart de seconde.
        recolorer()
        val montre = ReactionSonore.apercu
        val palier = if (montre >= 0) 3 else EcouteSon.palier
        val mode = if (montre >= 0) montre else if (palier <= 2) 0 else if (palier == 3) 1 else 2
        val glisse = 1f - exp(-dt / 0.25f)
        var somme = 0f
        for (p in 0..2) {
            poids[p] += ((if (p == mode) 1f else 0f) - poids[p]) * glisse
            somme += poids[p]
        }
        if (somme < 0.001f) { poids[1] = 1f; somme = 1f }
        val p0 = poids[0] / somme
        val p1 = poids[1] / somme
        val p2 = poids[2] / somme

        // les aigus (pour les petits éclats du haut) : la moyenne des 6 tranches les plus aiguës
        val sp = EcouteSon.spectre
        var haut = 0f
        for (b in EcouteSon.BANDES - 6 until EcouteSon.BANDES) haut += sp[b]
        a = haut / 6f
        // le coup de basse : on prend le plus fort entre celui qui arrive et le nôtre qui retombe
        val arrive = EcouteSon.coup
        val tombe = arrive > 0.5f && coupAvant <= 0.5f              // vrai à l'instant précis où un coup tombe
        coupAvant = arrive
        coup = max(arrive, coup * exp(-dt / 0.15f))

        prisme(c, p0, p1, p2)
        val clair = trio(clairs[0], clairs[1], clairs[2], p0, p1, p2)

        // les aigus : quelques éclats posés sur le haut de la ligne
        if (a > 0.03f) for (j in 0..8) {
            val s = (M * (0.40f + 0.20f * ((j * 0.37f + 0.11f) % 1f))).toInt()
            val onde = max(0f, sin(t * 2.3f + j * 5f))
            eclat(c, ptX[s], ptY[s], 4.5f, a * onde * onde, clair)
        }

        // PALIER 5 : des confettis calés sur la musique (coups de basse et sons secs), voir Confettis.kt
        if (palier == 5) Confettis.suivre(tombe, arrive, ptX, ptY, intensites, vives[2], w, ht, d)
        Confettis.dessiner(c, dt, d, presence)
        // PALIER 1 : des petits Z
        sommeil += ((if (palier == 1) 1f else 0f) - sommeil) * min(1f, dt * 1.5f)
        if (sommeil > 0.01f) petitsZ(c, clairs[0])
        // (PROVISOIRE) les mesures en chiffres, en bas de l'écran, pour régler les paliers : voir MesuresSon.kt
        MesuresSon.dessiner(c, w / 2f, ht - 30f * d, d, presence)

        // … et on demande l'image suivante (sur un écran rapide : celle d'après, donc 60 images/s)
        if (ecranRapide) postOnAnimation(sauterUneImage) else postInvalidateOnAnimation()
    }

    // ================= LE PRISME : UN RUBAN CONTINU =================
    private fun prisme(c: Canvas, p0: Float, p1: Float, p2: Float) {
        // 1) LA LUMIÈRE de chaque repère = la force de SA tranche de son (le spectre).
        //    Très graves en bas, de plus en plus aigu en montant, pareil à gauche et à droite.
        //    Une note qu'on entend allume TOUJOURS le même endroit. Et sur un coup de basse, tout le tour bat.
        val sp = EcouteSon.spectre
        val fin = EcouteSon.BANDES - 1
        // (pendant l'aperçu d'une couleur : une lumière moyenne qui respire, pour bien voir la couleur sans musique)
        val demo = if (ReactionSonore.apercu >= 0) 0.42f + 0.10f * sin(t * 2.2f) else 0f
        for (k in 0 until M) {
            val f = freq[k]                                      // 0 = en bas (grave) … 1 = en haut (aigu)
            val pos = f * fin
            val b = pos.toInt().coerceIn(0, fin - 1)
            val q = (pos - b).coerceIn(0f, 1f)
            // entre deux tranches on glisse de l'une à l'autre en courbe douce (pas en ligne brisée)
            val v = sp[b] + (sp[b + 1] - sp[b]) * (q * q * (3f - 2f * q))
            brutes[k] = max(demo, min(1f, 0.85f * v + 0.30f * coup * (1f - 0.5f * f)))
        }
        // un léger lissage : chaque repère se mélange un peu avec ses deux voisins (un quart, la moitié, un quart)
        for (k in 0 until M) intensites[k] = (brutes[(k + M - 1) % M] + 2f * brutes[k] + brutes[(k + 1) % M]) / 4f

        // 2) LES 4 PEINTURES de cette image : pour chaque repère, sa couleur du moment, plus ou moins transparente.
        var haloMax = 0f
        for (j in 0 until M) {
            val k = ordre[j]
            val i = intensites[k]
            val couleur = trio(couleurs[0][k], couleurs[1][k], couleurs[2][k], p0, p1, p2)
            // la bande : de plus en plus forte, et de plus en plus claire (vers le blanc), avec le son
            cBande[j + 1] = voile(melange(couleur, Color.WHITE, 0.35f * i * i), 0.10f + 0.80f * i)
            // le halo : presque rien quand c'est calme, il monte vite quand ça joue (et un peu plus sur un coup de basse)
            val halo = ((0.14f + 0.86f * i) * (0.2f + 0.5f * i + 0.12f * coup) - 0.03f).coerceIn(0f, 1f)
            if (halo > haloMax) haloMax = halo
            cHalo[j + 1] = voile(couleur, halo)
            cLigne[j + 1] = voile(couleur, 0.14f + 0.86f * i)
            // le cœur blanc de la ligne : seulement là où elle est forte
            cCoeur[j + 1] = voile(Color.WHITE, (i - 0.45f) * 0.8f)
        }
        // (le dégradé commence à l'angle 0 et finit à un tour complet : les deux bouts ont la même couleur)
        cBande[0] = cBande[1]; cBande[M + 1] = cBande[1]
        cHalo[0] = cHalo[1]; cHalo[M + 1] = cHalo[1]
        cLigne[0] = cLigne[1]; cLigne[M + 1] = cLigne[1]
        cCoeur[0] = cCoeur[1]; cCoeur[M + 1] = cCoeur[1]
        val cx = w / 2f
        val cy = ht / 2f

        // 3) LA BANDE entre la ligne et le bord
        couche(c, cheminBande, marge + 1f, SweepGradient(cx, cy, cBande, positions), 1f)

        // 4) LE DÉBORDEMENT vers l'intérieur (la lueur fondue, voir tout en haut du fichier).
        //    Dans la petite image : 17 traits, du plus large (70 dp) au plus étroit (6 dp), de plus en plus forts.
        //    Chacun dépasse à peine du suivant, et l'agrandissement floute le tout : on ne voit plus qu'une
        //    lueur qui s'éteint doucement en s'éloignant de la ligne.
        //    (Rien à dessiner quand tout est éteint : on économise le téléphone.)
        if (haloMax > 0.01f) {
            val halo = SweepGradient(cx, cy, cHalo, positions)
            val image = lueur
            val toile = toileLueur
            if (image != null && toile != null) {
                image.eraseColor(Color.TRANSPARENT)              // on efface la petite image de l'image d'avant
                toile.save()
                toile.scale(1f / REDUCTION, 1f / REDUCTION)      // tout ce qu'on y dessine est 6 fois plus petit
                for (n in 0..16) {
                    // (les traits larges s'élargissent un peu sur un coup de basse : la lueur « bat »)
                    val largeur = 70f - 4f * n + 10f * coup * (1f - n / 16f)
                    couche(toile, cheminLigne, largeur * d, halo, 0.033f + 0.0085f * n)
                }
                toile.restore()
                zone.set(0f, 0f, image.width * REDUCTION, image.height * REDUCTION)
                c.drawBitmap(image, null, zone, colleLueur)      // … et on la recolle, agrandie, sur l'écran
            }
            couche(c, cheminLigne, 3.2f * d, halo, 1f)           // la ligne s'épaissit là où le son est fort
        }

        // 5) LA LIGNE elle-même, puis son cœur blanc
        couche(c, cheminLigne, 1.2f * d, SweepGradient(cx, cy, cLigne, positions), 1f)
        couche(c, cheminLigne, 0.6f * d, SweepGradient(cx, cy, cCoeur, positions), 1f)
        ruban.shader = null
    }

    // ================= LES PETITS Z (palier 1) =================
    // Trois petits « Z », comme quelqu'un qui dort : ils partent du bord gauche, en bas, montent doucement
    // en grandissant un peu, puis s'effacent. Chacun met 3,6 s, et ils se suivent à 1,2 s d'écart.
    private fun petitsZ(c: Canvas, couleur: Int) {
        lettre.color = couleur
        for (j in 0..2) {
            val avance = ((t / 3.6f) + j / 3f) % 1f              // 0 = il naît … 1 = il a disparu
            lettre.textSize = (10f + 7f * avance) * d
            lettre.alpha = opacite(sin(PI.toFloat() * avance) * 0.6f * sommeil)
            c.drawText("Z", (22f + 18f * avance + 4f * j) * d, ht * 0.78f - 70f * d * avance, lettre)
        }
    }
}
