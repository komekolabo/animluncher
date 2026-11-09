package com.will.animlauncher

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
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
//   - 4 styles au choix (validés par Will sur aperçu le 10/11) : Aurore, Cordes, Verre liquide, Prisme.
//     Chacun réagit différemment aux graves, à la mélodie et aux aigus (voir EcouteSon.kt).
//   - Rien en bas de l'écran : les arcs / lueurs du bas sont réservés aux animations de Claude.
//
// Le choix (allumé ou non, quel style) est rangé dans un carnet à part, « son ».
object ReactionSonore {
    private const val CARNET = "son"
    const val STYLES = 4

    private fun carnet(c: Context) = c.applicationContext.getSharedPreferences(CARNET, Context.MODE_PRIVATE)

    fun actif(c: Context): Boolean = carnet(c).getBoolean("actif", false)
    fun definirActif(c: Context, valeur: Boolean) {
        carnet(c).edit().putBoolean("actif", valeur).apply()
        synchroniser(c)
    }

    @Volatile var style = 0          // le style en cours (lu à chaque image par la fenêtre)
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

    private fun poser(s: Context) {
        val gestionnaire = s.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val reglages = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or          // les appuis passent à travers
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        // la fenêtre va jusque sous l'encoche / le poinçon de l'appareil photo
        if (Build.VERSION.SDK_INT >= 30) {
            reglages.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
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

// LA FENÊTRE qui dessine les bords. « View » = un rectangle de l'écran qui sait se dessiner (onDraw).
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
    private var ressort = 0f             // les graves façon « ressort » (gonfle, dépasse un peu, revient)
    private var vitesseRessort = 0f
    private var tClaque = -9f            // l'instant du dernier son sec
    private var claque = 0f

    // la mémoire des dernières images : permet de demander « combien valaient les graves il y a 0,2 s ? »
    // (c'est ce qui fait REMONTER une vague le long du bord)
    private val memoireG = FloatArray(256)
    private val memoireR = FloatArray(256)
    private var rang = 0
    private var imagesParSeconde = 60f

    // ---------- les couleurs (grave = doré … aigu = bleu glacé) ----------
    private val OR = Color.rgb(240, 196, 140)
    private val ROSE = Color.rgb(222, 125, 165)
    private val TURQ = Color.rgb(120, 200, 210)
    private val GLACE = Color.rgb(190, 212, 255)
    private fun transparent(c: Int) = Color.argb(0, Color.red(c), Color.green(c), Color.blue(c))

    // ---------- les pinceaux, créés une seule fois ----------
    private val ajout = PorterDuffXfermode(PorterDuff.Mode.ADD)     // la lumière s'ADDITIONNE (comme de vraies lampes)
    private val flouLarge = BlurMaskFilter(14f * d, BlurMaskFilter.Blur.NORMAL)
    private val flouMoyen = BlurMaskFilter(5f * d, BlurMaskFilter.Blur.NORMAL)
    private val flouFin = BlurMaskFilter(2.5f * d, BlurMaskFilter.Blur.NORMAL)
    private val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; xfermode = ajout
    }
    private val plein = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; xfermode = ajout }
    private val chemin = Path()
    private val chemin2 = Path()

    // ---------- ce qui dépend de la taille de l'écran ----------
    private var w = 0f
    private var ht = 0f
    private var y0 = 0f                  // les bords vivent entre y0 (7 % de la hauteur)…
    private var y1 = 0f                  // …et y1 (83 %) : rien tout en bas
    private var degrade: Shader? = null          // glacé en haut -> doré en bas, effacé aux deux bouts
    private var blancDoux: Shader? = null        // blanc, effacé aux deux bouts (le « cœur » des traits)
    private val voile = arrayOfNulls<Shader>(2)  // un voile coloré qui s'efface en s'éloignant du bord (gauche, droite)
    private val verre = arrayOfNulls<Shader>(2)
    private val N = 160                          // le néon du Prisme, découpé en 160 petits segments
    private val nx = FloatArray(N + 1)
    private val ny = FloatArray(N + 1)
    private val couleurSegment = IntArray(N)
    private val hasard = FloatArray(N) { ((it * 7919) % 101) / 100f }

    // 34 petits éclats pour les aigus : (côté, hauteur, distance au bord en dp, décalage, taille)
    private val etoiles = Array(34) { i ->
        val r = java.util.Random(11L + i * 31L)
        floatArrayOf(r.nextInt(2).toFloat(), 0.04f + 0.51f * r.nextFloat(), 6f + 34f * r.nextFloat(), r.nextFloat(), 2f + 2.5f * r.nextFloat())
    }

    override fun onSizeChanged(largeur: Int, hauteur: Int, ancienneL: Int, ancienneH: Int) {
        w = largeur.toFloat(); ht = hauteur.toFloat()
        y0 = ht * 0.07f; y1 = ht * 0.83f
        degrade = LinearGradient(0f, y0, 0f, y1,
            intArrayOf(transparent(GLACE), GLACE, TURQ, ROSE, OR, transparent(OR)),
            floatArrayOf(0f, 0.14f, 0.42f, 0.66f, 0.86f, 1f), Shader.TileMode.CLAMP)
        blancDoux = LinearGradient(0f, y0, 0f, y1,
            intArrayOf(Color.argb(0, 255, 255, 255), Color.WHITE, Color.WHITE, Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 0.14f, 0.86f, 1f), Shader.TileMode.CLAMP)
        val couleurs = LinearGradient(0f, y0, 0f, y1, intArrayOf(GLACE, TURQ, ROSE, OR), null, Shader.TileMode.CLAMP)
        for (cote in 0..1) {
            val bord = if (cote == 0) 0f else w
            val sens = if (cote == 0) 1f else -1f
            // « ComposeShader » combine deux dégradés : les couleurs (de haut en bas) et la transparence (du bord vers l'intérieur)
            voile[cote] = ComposeShader(couleurs,
                LinearGradient(bord, 0f, bord + sens * 96f * d, 0f, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP),
                PorterDuff.Mode.DST_IN)
            verre[cote] = ComposeShader(couleurs,
                LinearGradient(bord, 0f, bord + sens * 40f * d, 0f, Color.argb(120, 0, 0, 0), Color.argb(14, 0, 0, 0), Shader.TileMode.CLAMP),
                PorterDuff.Mode.DST_IN)
        }
        preparerNeon()
    }

    // Le trajet du néon du Prisme : il monte le long du bord gauche, suit l'arrondi de l'écran,
    // traverse le haut, et redescend à droite. (s va de 0 à 1 le long de ce trajet.)
    private fun preparerNeon() {
        val marge = 5f * d
        var coin = 28f * d
        if (Build.VERSION.SDK_INT >= 31) {
            try { display?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.let { coin = it.radius.toFloat() } } catch (_: Throwable) {}
        }
        val r = max(coin - marge, 10f * d)
        val cote = y1 - marge - r
        val haut = w - 2 * marge - 2 * r
        val arc = (PI / 2).toFloat() * r
        val total = 2 * cote + 2 * arc + haut
        for (k in 0..N) {
            var p = k.toFloat() / N * total
            if (p < cote) { nx[k] = marge; ny[k] = y1 - p; continue }
            p -= cote
            if (p < arc) { val an = PI.toFloat() - p / r; nx[k] = marge + r + r * cos(an); ny[k] = marge + r - r * sin(an); continue }
            p -= arc
            if (p < haut) { nx[k] = marge + r + p; ny[k] = marge; continue }
            p -= haut
            if (p < arc) { val an = (PI / 2).toFloat() - p / r; nx[k] = w - marge - r + r * cos(an); ny[k] = marge + r - r * sin(an); continue }
            nx[k] = w - marge; ny[k] = marge + r + (p - arc)
        }
        for (k in 0 until N) couleurSegment[k] = couleurFrequence(frequenceSegment(k))
    }
    // Où est ce segment sur le « piano » ? 0 = bas des côtés (grave) … 1 = milieu du haut (aigu)
    private fun frequenceSegment(k: Int): Float = 1f - abs(2f * (k + 0.5f) / N - 1f)

    private fun melange(c1: Int, c2: Int, k: Float): Int {
        val x = k.coerceIn(0f, 1f)
        return Color.rgb((Color.red(c1) + (Color.red(c2) - Color.red(c1)) * x).toInt(),
            (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * x).toInt(),
            (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * x).toInt())
    }
    private fun couleurFrequence(f: Float): Int = when {
        f < 0.33f -> melange(OR, ROSE, f / 0.33f)
        f < 0.66f -> melange(ROSE, TURQ, (f - 0.33f) / 0.33f)
        else -> melange(TURQ, GLACE, (f - 0.66f) / 0.34f)
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
        verifier()                                                   // l'écran vient peut-être de tourner
    }

    // ---------- petits outils de calcul ----------
    private fun cloche(x: Float, centre: Float, largeur: Float): Float {
        val e = (x - centre) / largeur
        return exp(-e * e)
    }
    // s'efface en haut et en bas (« max(0…) » : sin(π) peut tomber un cheveu sous 0, et la puissance d'un négatif n'existe pas)
    private fun bordsDoux(u: Float): Float = max(0f, sin(PI.toFloat() * u.coerceIn(0f, 1f))).pow(0.7f)
    private fun ilYA(memoire: FloatArray, secondes: Float): Float {
        val images = (secondes * imagesParSeconde).toInt().coerceIn(0, 255)
        return memoire[(rang - images) and 255]
    }
    private fun opacite(x: Float): Int = (x * presence * 255f).toInt().coerceIn(0, 255)

    // Un TRAIT DE LUMIÈRE : grand halo + halo serré + trait + cœur presque blanc.
    private fun lumiere(c: Canvas, p: Path, shader: Shader?, couleur: Int, epaisseur: Float, force: Float, coeur: Boolean = true) {
        trait.shader = shader
        trait.color = couleur
        trait.strokeWidth = epaisseur * 9f * d; trait.maskFilter = flouLarge; trait.alpha = opacite(0.12f * force); c.drawPath(p, trait)
        trait.strokeWidth = epaisseur * 4f * d; trait.maskFilter = flouMoyen; trait.alpha = opacite(0.26f * force); c.drawPath(p, trait)
        trait.strokeWidth = epaisseur * d; trait.maskFilter = null; trait.alpha = opacite(0.95f * force); c.drawPath(p, trait)
        if (coeur) {
            trait.shader = blancDoux; trait.color = Color.WHITE
            trait.strokeWidth = epaisseur * 0.4f * d; trait.alpha = opacite(0.5f * force); c.drawPath(p, trait)
        }
        trait.shader = null
    }

    // Un petit ÉCLAT en étoile à 4 branches.
    private fun eclat(c: Canvas, x: Float, y: Float, taille: Float, force: Float, couleur: Int) {
        if (force * presence < 0.02f) return
        plein.shader = null; plein.color = couleur
        plein.maskFilter = flouFin; plein.alpha = opacite(force * 0.6f); c.drawCircle(x, y, taille * 0.6f * d, plein)
        plein.maskFilter = null
        trait.shader = null; trait.maskFilter = null; trait.color = couleur
        trait.strokeWidth = 0.8f * d; trait.alpha = opacite(force)
        c.drawLine(x - taille * d, y, x + taille * d, y, trait)
        c.drawLine(x, y - taille * d, x, y + taille * d, trait)
        plein.color = Color.WHITE; plein.alpha = opacite(force); c.drawCircle(x, y, 0.9f * d, plein)
    }

    // Les aigus : les éclats s'allument tour à tour, plutôt en haut des bords.
    private fun scintiller(c: Canvas, portee: Float) {
        if (a < 0.03f) return
        for (e in etoiles) {
            val onde = max(0f, sin(t * 1.7f + e[3] * 40f))
            val x = if (e[0] < 0.5f) e[2] * portee * d else w - e[2] * portee * d
            eclat(c, x, y0 + (y1 - y0) * e[1], e[4], a * onde * onde * 0.9f, GLACE)
        }
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
        // le ressort : il court après les graves avec de l'élan (formule d'un ressort amorti)
        vitesseRessort += (170f * (g - ressort) - 13f * vitesseRessort) * dt
        ressort = (ressort + vitesseRessort * dt).coerceIn(0f, 1.3f)
        // un son sec : on retient l'instant (au plus un toutes les 0,25 s)
        if (EcouteSon.claque > 0.5f && t - tClaque > 0.25f) tClaque = t
        claque = exp(-(t - tClaque) / 0.14f)
        rang = (rang + 1) and 255
        memoireG[rang] = g
        memoireR[rang] = ressort

        when (ReactionSonore.style) {
            1 -> cordes(c)
            2 -> verreLiquide(c)
            3 -> prisme(c)
            else -> aurore(c)
        }
        postInvalidateOnAnimation()                  // … et on demande l'image suivante
    }

    // ================= 1. AURORE =================
    // Un rideau de lumière collé à chaque bord. Graves : il gonfle depuis le bas, et la vague remonte.
    // Mélodie : une avancée de lumière à la hauteur de la note. Aigus : des éclats.
    private fun aurore(c: Canvas) {
        for (cote in 0..1) {
            val bord = if (cote == 0) 0f else w
            val sens = if (cote == 0) 1f else -1f
            chemin.rewind(); chemin.moveTo(bord, y0)
            for (k in 0..80) {
                val u = k / 80f
                val plis = 0.5f + 0.5f * sin(u * 17f + t * 0.7f + 2f * sin(u * 5f - t * 0.4f) + cote * 1.7f)
                val vague = ilYA(memoireG, (1f - u) * 0.30f)                   // la basse part du bas
                val note = m * cloche(u, 0.78f - 0.55f * h, 0.07f)
                val longueur = (14f + 70f * plis * (0.25f + 0.75f * vague) + 34f * note) * d * bordsDoux(u)
                chemin.lineTo(bord + sens * longueur, y0 + (y1 - y0) * u)
            }
            chemin.lineTo(bord, y1); chemin.close()
            plein.shader = voile[cote]
            plein.maskFilter = flouLarge; plein.alpha = opacite(0.30f + 0.30f * g); c.drawPath(chemin, plein)
            plein.maskFilter = flouMoyen; plein.alpha = opacite(0.34f + 0.40f * g); c.drawPath(chemin, plein)
            plein.maskFilter = null; plein.shader = null
            // le liseré au ras du bord
            chemin2.rewind(); chemin2.moveTo(bord + sens * 1.5f * d, y0); chemin2.lineTo(bord + sens * 1.5f * d, y1)
            lumiere(c, chemin2, degrade, Color.WHITE, 1f, 0.35f + 0.45f * g, coeur = false)
        }
        scintiller(c, 1f)
    }

    // ================= 2. CORDES =================
    // Trois cordes tendues le long de chaque bord, comme une harpe. La grosse (dorée) vibre large sur
    // les graves ; la moyenne (turquoise) sur la mélodie, avec plus de « ventres » quand la note monte ;
    // la fine (glacée) frémit sur les aigus. Un son sec fait filer un éclat rose.
    private fun cordes(c: Canvas) {
        val ya = y0 + 10f * d
        val yb = y1 - 10f * d
        for (cote in 0..1) {
            for (n in 0..2) {
                val distance = floatArrayOf(10f, 20f, 28f)[n]
                val epaisseur = floatArrayOf(1.7f, 1.2f, 0.8f)[n]
                val couleur = intArrayOf(OR, TURQ, GLACE)[n]
                val force = min(1f, floatArrayOf(ressort, m, a)[n])
                val ventres = floatArrayOf(1f, 2f + h * 5f, 13f)[n]
                val vitesse = floatArrayOf(2.6f, 4.3f, 8f)[n]
                val amplitude = floatArrayOf(11f, 6.5f, 2.6f)[n] * d * force
                val phase = cos(t * vitesse * 2f * PI.toFloat() + cote * 1.1f)
                // le « fuseau » : toute la zone balayée par la corde (le flou d'une vraie corde qui vibre)
                chemin.rewind(); chemin2.rewind()
                for (k in 0..60) {
                    val u = k / 60f
                    val ecart = amplitude * sin(PI.toFloat() * u * ventres + n) * max(0f, sin(PI.toFloat() * u)).pow(0.6f)
                    val y = ya + (yb - ya) * u
                    val xGauche = distance * d + ecart
                    val xCorde = distance * d + ecart * phase
                    if (k == 0) { chemin.moveTo(if (cote == 0) xGauche else w - xGauche, y); chemin2.moveTo(if (cote == 0) xCorde else w - xCorde, y) }
                    else { chemin.lineTo(if (cote == 0) xGauche else w - xGauche, y); chemin2.lineTo(if (cote == 0) xCorde else w - xCorde, y) }
                }
                for (k in 60 downTo 0) {
                    val u = k / 60f
                    val ecart = amplitude * sin(PI.toFloat() * u * ventres + n) * max(0f, sin(PI.toFloat() * u)).pow(0.6f)
                    val x = distance * d - ecart
                    chemin.lineTo(if (cote == 0) x else w - x, ya + (yb - ya) * u)
                }
                chemin.close()
                plein.shader = null; plein.color = couleur; plein.maskFilter = null
                plein.alpha = opacite(0.13f * force); c.drawPath(chemin, plein)
                lumiere(c, chemin2, null, couleur, epaisseur, 0.30f + 0.75f * force)
                // les deux attaches de la corde
                val x = if (cote == 0) distance * d else w - distance * d
                plein.color = melange(couleur, Color.WHITE, 0.4f); plein.alpha = opacite(0.8f)
                c.drawCircle(x, ya, 2.2f * d, plein); c.drawCircle(x, yb, 2.2f * d, plein)
            }
            val age = t - tClaque
            if (age < 0.55f) {
                val u = age / 0.55f
                val x = if (cote == 0) 20f * d else w - 20f * d
                eclat(c, x, yb - (yb - ya) * u * u * (3f - 2f * u), 5f, max(0f, sin(PI.toFloat() * u)).pow(0.7f) * 0.9f, ROSE)
            }
        }
    }

    // ================= 3. VERRE LIQUIDE =================
    // Le bord devient une matière souple. Graves : de grosses bosses qui gonflent comme un ressort.
    // Mélodie : une bosse à la hauteur de la note. Aigus : de fines rides qui courent dessus.
    private val centresBosses = floatArrayOf(0.24f, 0.52f, 0.80f)
    private val forcesBosses = floatArrayOf(0.85f, 1f, 0.8f)
    private fun tracerVerre(p: Path, cote: Int, echelle: Float, retard: Float, fermer: Boolean) {
        val bord = if (cote == 0) 0f else w
        p.rewind()
        if (fermer) p.moveTo(bord, y0)
        for (k in 0..120) {
            val u = k / 120f
            var x = 0f
            for (i in 0..2) {
                val centre = centresBosses[i] + 0.05f * sin(t * 0.5f + i * 2f + cote * 1.3f)
                x += 26f * forcesBosses[i] * ilYA(memoireR, i * 0.07f + cote * 0.035f + retard) * cloche(u, centre, 0.115f)
            }
            x += 14f * m * cloche(u, 0.85f - 0.7f * h, 0.05f)
            x += 1.8f * a * sin(u * 150f - t * 24f) * (0.3f + 0.7f * (1f - u))
            x += 6f * claque * cloche(u, 0.12f, 0.07f)
            x = (3f + x * echelle) * bordsDoux(u) * d
            val y = y0 + (y1 - y0) * u
            if (k == 0 && !fermer) p.moveTo(if (cote == 0) x else w - x, y) else p.lineTo(if (cote == 0) x else w - x, y)
        }
        if (fermer) { p.lineTo(bord, y1); p.close() }
    }
    private fun verreLiquide(c: Canvas) {
        for (cote in 0..1) {
            tracerVerre(chemin, cote, 1f, 0f, fermer = true)                  // l'intérieur : du verre teinté
            plein.shader = verre[cote]; plein.maskFilter = null; plein.alpha = opacite(1f); c.drawPath(chemin, plein)
            plein.shader = null
            tracerVerre(chemin2, cote, 0.55f, 0.05f, fermer = false)          // un second fil, en retard : l'épaisseur du verre
            trait.shader = degrade; trait.color = Color.WHITE; trait.maskFilter = null
            trait.strokeWidth = 0.8f * d; trait.alpha = opacite(0.30f); c.drawPath(chemin2, trait)
            tracerVerre(chemin2, cote, 1f, 0f, fermer = false)                // le liseré lumineux
            lumiere(c, chemin2, degrade, Color.WHITE, 1.2f, 0.55f + 0.45f * min(1f, ressort))
        }
        scintiller(c, 0.7f)
    }

    // ================= 4. PRISME =================
    // Un fin néon fait le tour par les côtés et le haut, rangé comme un piano : graves en bas des côtés
    // (doré), mélodie au milieu (rose, turquoise), aigus tout en haut (glacé). Chaque son allume SA zone,
    // et la basse remonte les côtés comme une vague.
    private val intensites = FloatArray(160)
    private fun prisme(c: Canvas) {
        for (k in 0 until N) {
            val f = frequenceSegment(k)
            var i = ilYA(memoireG, f * 0.38f) * max(0f, 1f - f / 0.6f).pow(1.4f)
            i += m * cloche(f, 0.40f + 0.30f * h, 0.065f)
            i += 0.8f * claque * cloche(f, 0.80f, 0.06f)
            i += a * (0.3f + 0.7f * hasard[k]) * max(0f, (f - 0.87f) / 0.13f)
            intensites[k] = min(1f, i)
        }
        trait.shader = null
        // les halos : par groupes de 4 segments (moins de travail pour le téléphone)
        var k = 0
        while (k < N) {
            val i = (intensites[k] + intensites[k + 1] + intensites[k + 2] + intensites[k + 3]) / 4f
            if (i > 0.06f) {
                val fondu = min(1f, min((k + 2f) / N / 0.07f, (1f - (k + 2f) / N) / 0.07f))
                trait.color = couleurSegment[k + 1]
                trait.maskFilter = flouLarge; trait.strokeWidth = (1f + 2.2f * i) * 5f * d; trait.alpha = opacite(0.5f * i * fondu)
                c.drawLine(nx[k], ny[k], nx[k + 4], ny[k + 4], trait)
                trait.maskFilter = flouMoyen; trait.strokeWidth = (1f + 2.2f * i) * 2.2f * d; trait.alpha = opacite(0.6f * i * fondu)
                c.drawLine(nx[k], ny[k], nx[k + 4], ny[k + 4], trait)
            }
            k += 4
        }
        // le néon lui-même, puis son cœur blanc là où il est fort
        trait.maskFilter = null
        for (s in 0 until N) {
            val i = intensites[s]
            val fondu = min(1f, min((s + 0.5f) / N / 0.07f, (1f - (s + 0.5f) / N) / 0.07f))
            trait.color = couleurSegment[s]
            trait.strokeWidth = (1f + 2.2f * i) * d; trait.alpha = opacite((0.10f + 0.90f * i) * fondu)
            c.drawLine(nx[s], ny[s], nx[s + 1], ny[s + 1], trait)
            if (i > 0.45f) {
                trait.color = Color.WHITE; trait.strokeWidth = 0.5f * d; trait.alpha = opacite((i - 0.45f) * 0.8f * fondu)
                c.drawLine(nx[s], ny[s], nx[s + 1], ny[s + 1], trait)
            }
        }
        // aigus : quelques éclats posés sur le haut du néon
        if (a > 0.03f) for (j in 0..8) {
            val s = (N * (0.40f + 0.20f * ((j * 0.37f + 0.11f) % 1f))).toInt()
            val onde = max(0f, sin(t * 2.3f + j * 5f))
            eclat(c, nx[s], ny[s], 4.5f, a * onde * onde, GLACE)
        }
    }
}
