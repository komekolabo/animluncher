package com.will.animlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// L'ÉCRAN DE CHARGEMENT ANIMÉ, en plein écran.
// Il se déroule en 3 phases :
//   1. INTRO      : l'animation apparaît (au moins 0,45 s)
//   2. ATTENTE    : elle tourne en boucle tant que l'app charge (3 s maximum)
//   3. RÉVÉLATION : un cercle s'ouvre depuis le centre et dévoile l'app (0,38 s)
// Le service appelle signalerPret() quand le vrai écran de l'app est arrivé.
class AnimationView(context: Context, private val style: Style) :
    SurfaceView(context), SurfaceHolder.Callback {

    enum class Style { CLAUDE, GOOGLE }

    private val dureeIntro = 450L
    private val dureeAttenteMax = 3000L
    private val dureeRevelation = 380L

    private var quandFini: (() -> Unit)? = null
    private val principal = Handler(Looper.getMainLooper())
    private var fil: Thread? = null
    @Volatile private var arretee = false
    @Volatile private var pret = false

    // MOUCHARD
    @Volatile var images = 0
        private set
    @Volatile var surfaceCreee = false
        private set
    val creeeA = SystemClock.uptimeMillis()

    // Taille d'un "point d'écran" en pixels (pour que les tailles soient les mêmes sur tout écran).
    private val dp = resources.displayMetrics.density

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lueur = Paint(Paint.ANTI_ALIAS_FLAG)
    // La "gomme" : elle efface les pixels (mode CLEAR) pour faire le trou de la révélation.
    private val gomme = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    // --- LES PARTICULES DE LA CONSTELLATION ---
    // "data class" = une petite fiche qui regroupe les infos d'une particule.
    private class Particule(
        val angle: Float,      // direction de départ (en radians)
        val distance: Float,   // distance de départ, en fraction de l'écran
        val vitesse: Float,    // vitesse de rotation
        val taille: Float,     // rayon en dp
        val couleur: Int
    )

    private val palette = intArrayOf(
        Color.rgb(217, 119, 87),   // orange
        Color.rgb(240, 180, 138),  // pêche
        Color.rgb(255, 241, 230),  // crème
        Color.rgb(201, 100, 66)    // orange brûlé
    )

    // 110 particules tirées au hasard une fois pour toutes.
    private val particules = List(110) {
        Particule(
            angle = Random.nextFloat() * 6.2832f,
            distance = 0.55f + Random.nextFloat() * 0.75f,
            vitesse = 0.5f + Random.nextFloat() * 1.3f,
            taille = 1.5f + Random.nextFloat() * 2.5f,
            couleur = palette[Random.nextInt(palette.size)]
        )
    }

    init {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
    }

    fun jouer(fin: () -> Unit) { quandFini = fin }
    fun signalerPret() { pret = true }
    fun arreter() { arretee = true }

    override fun surfaceCreated(h: SurfaceHolder) {
        surfaceCreee = true
        fil = Thread { boucleDeDessin() }.apply { start() }
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, largeur: Int, hauteur: Int) {}

    override fun surfaceDestroyed(h: SurfaceHolder) {
        arretee = true
        try { fil?.join(200) } catch (_: InterruptedException) {}
    }

    // LA BOUCLE : calcule où on en est, dessine une image, attend 16 ms, recommence.
    private fun boucleDeDessin() {
        val debut = SystemClock.uptimeMillis()
        var debutRevelation = 0L

        while (!arretee) {
            val maintenant = SystemClock.uptimeMillis()
            val ecoule = maintenant - debut

            // On passe à la révélation quand l'intro est finie ET l'app prête,
            // ou quand on a attendu trop longtemps.
            if (debutRevelation == 0L &&
                ((ecoule >= dureeIntro && pret) || ecoule >= dureeAttenteMax)
            ) debutRevelation = maintenant

            val intro = (ecoule.toFloat() / dureeIntro).coerceIn(0f, 1f)
            val revelation = if (debutRevelation == 0L) 0f
                else ((maintenant - debutRevelation).toFloat() / dureeRevelation).coerceIn(0f, 1f)
            val secondes = ecoule / 1000f

            val toile = try { holder.lockHardwareCanvas() } catch (_: Exception) { null } ?: break
            try {
                toile.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                when (style) {
                    Style.CLAUDE -> dessinerConstellation(toile, secondes, intro, revelation)
                    Style.GOOGLE -> dessinerPoints(toile, secondes, intro, revelation)
                }
            } finally {
                holder.unlockCanvasAndPost(toile)
            }
            images++

            if (revelation >= 1f) break
            try { Thread.sleep(16) } catch (_: InterruptedException) { break }
        }

        principal.post {
            val fin = quandFini
            quandFini = null
            fin?.invoke()
        }
    }

    // --- COURBES D'ACCÉLÉRATION ("easing") ---
    // Elles transforment une progression 0→1 linéaire en mouvement plus naturel.
    private fun douxAuxDeuxBouts(x: Float) =      // lent, rapide, lent
        if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
    private fun ralentitALaFin(x: Float) = 1f - (1f - x).pow(3)

    // Le trou circulaire qui dévoile l'app, avec un anneau coloré sur son bord.
    private fun dessinerRevelation(toile: Canvas, cx: Float, cy: Float, rayonMax: Float,
                                   revelation: Float, couleurAnneau: Int) {
        if (revelation <= 0f) return
        val trou = rayonMax * ralentitALaFin(revelation)
        toile.drawCircle(cx, cy, trou, gomme)
        pinceau.style = Paint.Style.STROKE
        pinceau.color = couleurAnneau
        pinceau.alpha = ((1f - revelation) * 255).toInt()
        pinceau.strokeWidth = (2f + 6f * (1f - revelation)) * dp
        toile.drawCircle(cx, cy, trou, pinceau)
    }

    // ================= CLAUDE : « CONSTELLATION » =================
    private fun dessinerConstellation(toile: Canvas, secondes: Float, intro: Float, revelation: Float) {
        val cx = toile.width / 2f
        val cy = toile.height / 2f
        val rayonMax = hypot(cx, cy)
        val convergence = douxAuxDeuxBouts(intro)

        // 1. Fond opaque, sombre et chaud, qui couvre tout l'écran.
        toile.drawColor(Color.rgb(20, 16, 19))

        // 2. Les particules : spirale vers le centre, puis orbite qui "respire".
        pinceau.style = Paint.Style.FILL
        for (p in particules) {
            val depart = p.distance * rayonMax
            val orbite = rayonMax * 0.07f + p.taille * 5f * dp
            val distance = depart + (orbite - depart) * convergence
            val respiration = 1f + 0.10f * sin(secondes * 5f + p.angle * 3f)
            val explosion = 1f + revelation * revelation * 9f     // pendant la révélation
            val angle = p.angle + p.vitesse * secondes * 2.2f + (1f - convergence) * 2.5f
            val d = distance * respiration * explosion
            pinceau.color = p.couleur
            pinceau.alpha = ((0.3f + 0.7f * convergence) * (1f - revelation) * 255).toInt()
            toile.drawCircle(
                cx + d * cos(angle), cy + d * sin(angle),
                p.taille * dp * (0.6f + 0.6f * convergence), pinceau
            )
        }

        // 3. Le noyau lumineux qui pulse au centre.
        val pulsation = 1f + 0.15f * sin(secondes * 8f)
        val rayonNoyau = rayonMax * 0.06f * convergence * pulsation * (1f + revelation)
        if (rayonNoyau > 1f) {
            val a = 1f - revelation
            lueur.shader = RadialGradient(
                cx, cy, rayonNoyau * 3f,
                intArrayOf(
                    Color.argb((235 * a).toInt(), 255, 236, 220),
                    Color.argb((150 * a).toInt(), 217, 119, 87),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.3f, 1f),
                Shader.TileMode.CLAMP
            )
            toile.drawCircle(cx, cy, rayonNoyau * 3f, lueur)
        }

        // 4. La révélation.
        dessinerRevelation(toile, cx, cy, rayonMax, revelation, Color.rgb(240, 180, 138))
    }

    // ================= GOOGLE : 4 POINTS QUI REBONDISSENT =================
    private fun dessinerPoints(toile: Canvas, secondes: Float, intro: Float, revelation: Float) {
        val cx = toile.width / 2f
        val cy = toile.height / 2f
        val rayonMax = hypot(cx, cy)
        val couleurs = intArrayOf(
            Color.rgb(66, 133, 244), Color.rgb(234, 67, 53),
            Color.rgb(251, 188, 5), Color.rgb(52, 168, 83)
        )

        // 1. Fond blanc opaque.
        toile.drawColor(Color.WHITE)

        // 2. Les 4 points : ils grossissent à l'intro, rebondissent en vague,
        //    puis s'envolent vers l'extérieur pendant la révélation.
        val apparition = ralentitALaFin(intro)
        val ecart = 34f * dp
        val rayonPoint = 9f * dp * apparition
        pinceau.style = Paint.Style.FILL
        for (i in 0 until 4) {
            val x0 = cx + (i - 1.5f) * ecart
            val bond = abs(sin(secondes * 7f + i * 0.7f)) * 22f * dp * apparition
            val y0 = cy - bond
            val fuite = revelation * revelation * rayonMax
            val dx = x0 - cx
            val x = x0 + dx / ecart * fuite
            val y = y0 - fuite * 0.3f
            pinceau.color = couleurs[i]
            pinceau.alpha = ((1f - revelation) * 255).toInt()
            toile.drawCircle(x, y, rayonPoint, pinceau)
        }

        // 3. La révélation.
        dessinerRevelation(toile, cx, cy, rayonMax, revelation, Color.rgb(66, 133, 244))
    }
}
