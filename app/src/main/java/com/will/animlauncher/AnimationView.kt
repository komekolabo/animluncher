package com.will.animlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// L'ÉCRAN DE CHARGEMENT ANIMÉ, en plein écran, en 3 phases :
//   1. INTRO      : l'animation apparaît (au moins 0,45 s)
//   2. ATTENTE    : elle tourne en boucle tant que l'app charge (3 s maximum)
//   3. RÉVÉLATION : l'app est dévoilée
// Le service appelle signalerPret() quand le vrai écran de l'app est arrivé.
class AnimationView(context: Context, private val style: Style) :
    SurfaceView(context), SurfaceHolder.Callback {

    enum class Style { HORIZON, CONSTELLATION, POINTS }

    private val dureeIntro = 450L
    private val dureeAttenteMax = 3000L
    private val dureeRevelation = if (style == Style.HORIZON) 520L else 380L

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

    private val dp = resources.displayMetrics.density

    // Les pinceaux. "lumiere" ajoute sa couleur (mode ADD) : comme de la lumière projetée.
    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lumiere = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val gomme = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
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
                    Style.HORIZON -> dessinerHorizon(toile, secondes, intro, revelation)
                    Style.CONSTELLATION -> dessinerConstellation(toile, secondes, intro, revelation)
                    Style.POINTS -> dessinerPoints(toile, secondes, intro, revelation)
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

    // --- COURBES D'ACCÉLÉRATION ---
    private fun douxAuxDeuxBouts(x: Float) =
        if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
    private fun ralentitALaFin(x: Float) = 1f - (1f - x).pow(3)

    // Une tache de lumière ovale (dégradé doux), ajoutée à l'image.
    // sx / sy = largeur / hauteur de la tache ; intensite de 0 à 1.
    private fun tacheDeLumiere(toile: Canvas, x: Float, y: Float, sx: Float, sy: Float,
                               intensite: Float, r: Int, g: Int, b: Int) {
        val a = (intensite * 255).toInt().coerceIn(0, 255)
        if (a == 0) return
        val rayon = sy * 2f
        lumiere.shader = RadialGradient(
            x, y, rayon,
            intArrayOf(Color.argb(a, r, g, b), Color.argb((a * 0.37f).toInt(), r, g, b), Color.argb(0, r, g, b)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        // On étire un cercle horizontalement pour en faire un ovale.
        toile.save()
        toile.scale(sx / sy, 1f, x, y)
        toile.drawCircle(x, y, rayon, lumiere)
        toile.restore()
        lumiere.shader = null
    }

    // ================= CLAUDE : « HORIZON » (version sombre) =================
    // Le lever de soleil sur une planète vue de l'espace. La planète monte, l'aube respire
    // pendant le chargement, puis un liseré orange descend sur la planète et découvre l'app.

    // 70 étoiles : x et y en fraction de l'écran, phase du scintillement, taille.
    private val etoiles = Random(3).let { rnd ->
        List(70) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.8f, rnd.nextFloat() * 6.28f, 0.6f + rnd.nextFloat() * 1.4f) }
    }

    // Les couleurs de l'atmosphère, de la surface vers l'espace (distance en dp, couleur).
    private val atmoDistances = floatArrayOf(0f, 5f, 13f, 24f, 31f, 42f, 75f, 130f)
    private val atmoCouleurs = intArrayOf(
        Color.rgb(210, 150, 110), Color.rgb(190, 110, 70), Color.rgb(150, 70, 50),
        Color.rgb(110, 45, 75), Color.rgb(55, 75, 140), Color.rgb(35, 45, 105),
        Color.rgb(20, 16, 50), Color.rgb(7, 8, 13)
    )

    private fun dessinerHorizon(toile: Canvas, secondes: Float, intro: Float, revelation: Float) {
        val l = toile.width.toFloat()
        val h = toile.height.toFloat()
        val cx = l / 2f
        val rayon = 1.6f * l                                   // la planète est immense
        val hautRepos = 0.66f * h                              // sommet de la courbe pendant l'attente
        // Hauteur finale calculée pour que la planète couvre les 4 coins (portrait ou paysage).
        val hautFin = sqrt(rayon * rayon - cx * cx) - rayon - 0.04f * h

        var haut = h * 1.04f + (hautRepos - h * 1.04f) * douxAuxDeuxBouts(intro)
        if (revelation > 0f) haut = hautRepos + (hautFin - hautRepos) * douxAuxDeuxBouts(revelation)
        val cy = haut + rayon                                  // centre de la planète, sous l'écran
        val aube = douxAuxDeuxBouts(intro) * (0.75f + 0.25f * sin(secondes * 4f))
        val couleurPlanete = Color.rgb(11, 14, 26)

        // 1. Le ciel noir.
        toile.drawColor(Color.rgb(7, 8, 13))

        // 2. Les étoiles, qui scintillent et glissent un peu avec la planète (effet caméra).
        pinceau.style = Paint.Style.FILL
        pinceau.shader = null
        val derive = (haut - hautRepos) * 0.12f
        for (e in etoiles) {
            val a = (0.4f + 0.6f * abs(sin(secondes * 3f + e[2]))) * min(1f, intro * 2f) * (1f - revelation)
            pinceau.color = Color.argb((a * 255).toInt(), 255, 255, 243)
            toile.drawCircle(e[0] * l, e[1] * h + derive, e[3] * dp * 0.6f, pinceau)
        }

        // 3. L'atmosphère : un dégradé en anneau autour de la planète,
        //    plus fort au centre de l'écran qu'aux bords.
        val epaisseur = 130f * dp
        val rayonExt = rayon + epaisseur
        val positions = FloatArray(atmoDistances.size) { (rayon + atmoDistances[it] * dp) / rayonExt }
        val couleurs = IntArray(atmoCouleurs.size) {
            val c = atmoCouleurs[it]
            Color.argb(((1f - atmoDistances[it] / 130f) * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))
        }
        val anneau = RadialGradient(cx, cy, rayonExt, couleurs, positions, Shader.TileMode.CLAMP)
        val centrage = LinearGradient(
            0f, 0f, l, 0f,
            intArrayOf(Color.argb(163, 0, 0, 0), Color.argb(224, 0, 0, 0), Color.BLACK, Color.argb(224, 0, 0, 0), Color.argb(163, 0, 0, 0)),
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f), Shader.TileMode.CLAMP
        )
        // ComposeShader "DST_IN" : garde l'anneau, mais atténué par le dégradé horizontal.
        pinceau.shader = ComposeShader(anneau, centrage, PorterDuff.Mode.DST_IN)
        pinceau.alpha = (aube * 255).toInt().coerceIn(0, 255)
        toile.drawCircle(cx, cy, rayonExt, pinceau)
        pinceau.shader = null
        pinceau.alpha = 255

        // 4. La planète, sombre, avec un léger reflet bleuté près du bord.
        pinceau.color = couleurPlanete
        toile.drawCircle(cx, cy, rayon, pinceau)
        pinceau.shader = RadialGradient(
            cx, cy, rayon,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb((aube * 200).toInt(), 29, 38, 71)),
            floatArrayOf(0f, (rayon - 60f * dp) / rayon, 1f), Shader.TileMode.CLAMP
        )
        toile.drawCircle(cx, cy, rayon, pinceau)
        pinceau.shader = null

        // 5. La révélation : un front descend depuis l'horizon. Entre l'horizon et le front,
        //    on efface tout (la gomme) : l'app apparaît. Un liseré orange marque le front.
        if (revelation > 0f) {
            val front = douxAuxDeuxBouts(min(1f, revelation / 0.9f)) * h * 1.3f
            val r0 = max(0f, rayon - front)
            toile.drawCircle(cx, cy, rayon, gomme)                 // on efface toute la planète...
            pinceau.color = couleurPlanete
            if (r0 > 0f) toile.drawCircle(cx, cy, r0, pinceau)     // ...et on redessine sa partie encore dans la nuit

            val halo = 40f * dp
            val rg = r0 + halo
            if (rg > halo) {
                val a = 1f - revelation
                fun p(v: Float) = (v / rg).coerceIn(0f, 1f)
                val pos = floatArrayOf(p(r0 - halo), p(r0 - 6f * dp), p(r0), p(r0 + 6f * dp), 1f)
                // les positions doivent toujours augmenter : on corrige les égalités éventuelles
                for (i in 1 until pos.size) if (pos[i] <= pos[i - 1]) pos[i] = min(1f, pos[i - 1] + 0.0001f)
                lumiere.shader = RadialGradient(
                    cx, cy, rg,
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.argb((90 * a).toInt(), 120, 50, 30),
                        Color.argb((230 * a).toInt(), 217, 119, 87),
                        Color.argb((90 * a).toInt(), 120, 50, 30),
                        Color.TRANSPARENT
                    ),
                    pos, Shader.TileMode.CLAMP
                )
                toile.drawCircle(cx, cy, rg, lumiere)
                lumiere.shader = null
            }
        }

        // 6. Le fin liseré lumineux sur la courbe de l'horizon.
        val aLiser = (aube * 0.6f * 255).toInt().coerceIn(0, 255)
        lumiere.style = Paint.Style.STROKE
        lumiere.strokeWidth = 2.5f * dp
        lumiere.shader = LinearGradient(
            0f, 0f, l, 0f,
            intArrayOf(Color.argb((aLiser * 0.64f).toInt(), 230, 170, 130), Color.argb(aLiser, 230, 170, 130), Color.argb((aLiser * 0.64f).toInt(), 230, 170, 130)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        toile.drawCircle(cx, cy, rayon, lumiere)
        lumiere.shader = null
        lumiere.style = Paint.Style.FILL

        // 7. Le soleil, discret : une lueur douce au sommet, qui grandit doucement pendant l'attente.
        val attente = ((secondes - 0.45f) / 1.2f).coerceIn(0f, 1f)
        val force = douxAuxDeuxBouts(intro) * (0.22f + 0.18f * attente + 0.06f * sin(secondes * 6f)) * (1f - revelation)
        if (force > 0.01f) {
            tacheDeLumiere(toile, cx, haut, 0.10f * l, 14f * dp, force * 0.5f, 230, 150, 100)
            tacheDeLumiere(toile, cx, haut, 0.025f * l, 8.4f * dp, force * 0.9f, 230, 150, 100)
            tacheDeLumiere(toile, cx, haut, 0.45f * l, 2.2f * dp, force * 0.4f, 220, 160, 120)   // traînée horizontale
        }
    }

    // ================= « CONSTELLATION » (gardée en réserve) =================
    private class Particule(val angle: Float, val distance: Float, val vitesse: Float, val taille: Float, val couleur: Int)
    private val particules = Random(7).let { rnd ->
        val palette = intArrayOf(Color.rgb(217, 119, 87), Color.rgb(240, 180, 138), Color.rgb(255, 241, 230), Color.rgb(201, 100, 66))
        List(110) {
            Particule(rnd.nextFloat() * 6.2832f, 0.55f + rnd.nextFloat() * 0.75f, 0.5f + rnd.nextFloat() * 1.3f,
                1.5f + rnd.nextFloat() * 2.5f, palette[rnd.nextInt(palette.size)])
        }
    }

    private fun dessinerConstellation(toile: Canvas, secondes: Float, intro: Float, revelation: Float) {
        val cx = toile.width / 2f
        val cy = toile.height / 2f
        val rayonMax = hypot(cx, cy)
        val convergence = douxAuxDeuxBouts(intro)
        toile.drawColor(Color.rgb(20, 16, 19))
        pinceau.style = Paint.Style.FILL
        for (p in particules) {
            val depart = p.distance * rayonMax
            val orbite = rayonMax * 0.07f + p.taille * 5f * dp
            val d = (depart + (orbite - depart) * convergence) *
                (1f + 0.10f * sin(secondes * 5f + p.angle * 3f)) * (1f + revelation * revelation * 9f)
            val angle = p.angle + p.vitesse * secondes * 2.2f + (1f - convergence) * 2.5f
            pinceau.color = p.couleur
            pinceau.alpha = ((0.3f + 0.7f * convergence) * (1f - revelation) * 255).toInt()
            toile.drawCircle(cx + d * cos(angle), cy + d * sin(angle), p.taille * dp * (0.6f + 0.6f * convergence), pinceau)
        }
        pinceau.alpha = 255
        dessinerRevelationCercle(toile, cx, cy, rayonMax, revelation, Color.rgb(217, 119, 87))
    }

    // ================= GOOGLE : « POINTS » (version sombre) =================
    private fun dessinerPoints(toile: Canvas, secondes: Float, intro: Float, revelation: Float) {
        val cx = toile.width / 2f
        val cy = toile.height / 2f
        val rayonMax = hypot(cx, cy)
        val couleurs = intArrayOf(
            Color.rgb(66, 133, 244), Color.rgb(234, 67, 53),
            Color.rgb(251, 188, 5), Color.rgb(52, 168, 83)
        )
        toile.drawColor(Color.rgb(16, 18, 24))   // fond nuit

        val apparition = ralentitALaFin(intro)
        val ecart = 34f * dp
        val rayonPoint = 9f * dp * apparition
        pinceau.style = Paint.Style.FILL
        for (i in 0 until 4) {
            val x0 = cx + (i - 1.5f) * ecart
            val y0 = cy - abs(sin(secondes * 7f + i * 0.7f)) * 22f * dp * apparition
            val fuite = revelation * revelation * rayonMax
            pinceau.color = couleurs[i]
            pinceau.alpha = ((1f - revelation) * 230).toInt()
            toile.drawCircle(x0 + (x0 - cx) / ecart * fuite, y0 - fuite * 0.3f, rayonPoint, pinceau)
        }
        pinceau.alpha = 255
        dessinerRevelationCercle(toile, cx, cy, rayonMax, revelation, Color.rgb(66, 133, 244))
    }

    // Révélation en cercle (Constellation, Points) : un trou qui grandit, avec un fin anneau.
    private fun dessinerRevelationCercle(toile: Canvas, cx: Float, cy: Float, rayonMax: Float,
                                         revelation: Float, couleurAnneau: Int) {
        if (revelation <= 0f) return
        val trou = rayonMax * ralentitALaFin(revelation)
        toile.drawCircle(cx, cy, trou, gomme)
        pinceau.style = Paint.Style.STROKE
        pinceau.color = couleurAnneau
        pinceau.alpha = ((1f - revelation) * 200).toInt()
        pinceau.strokeWidth = (2f + 4f * (1f - revelation)) * dp
        toile.drawCircle(cx, cy, trou, pinceau)
        pinceau.style = Paint.Style.FILL
        pinceau.alpha = 255
    }
}
