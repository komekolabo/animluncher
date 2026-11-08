package com.will.animlauncher

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// LES CATÉGORIES : durée de l'apparition et de la révélation, en millisecondes.
// L'attente (pendant que l'app charge) s'ajoute entre les deux, 3 s maximum.
enum class Categorie(val intro: Long, val revelation: Long) {
    ESSENTIELLE(450, 520),   // IA, Google principal, apps qui font vivre le téléphone
    COURANTE(300, 380),      // réseaux sociaux, banques, outils du quotidien
    SECONDAIRE(200, 260),    // le reste
    JEU(650, 400),           // les jeux : environ 1 seconde, le temps de raconter une petite histoire
    ECHECS(1500, 450)        // les échecs : un peu plus long, pour avoir le temps de voir la combinaison
}

// Où en est l'animation à cette image : temps écoulé, avancement de l'intro et de la révélation (0 à 1).
class Instant(val secondes: Float, val intro: Float, val revelation: Float)

// UNE SCÈNE = une animation. Chaque animation est une classe qui hérite de Scene
// et sait se dessiner à n'importe quel instant.
abstract class Scene {
    abstract fun dessiner(c: Canvas, o: Outils, i: Instant)
}

// --- Petites fonctions utiles partout ---
fun rgb(r: Int, g: Int, b: Int): Int = Color.rgb(r, g, b)
fun argb(a: Float, r: Int, g: Int, b: Int): Int = Color.argb(a.toInt().coerceIn(0, 255), r, g, b)
fun avecAlpha(couleur: Int, a: Float): Int =
    Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(couleur), Color.green(couleur), Color.blue(couleur))

// Courbes d'accélération : transforment une progression 0→1 en mouvement naturel.
fun doux(x: Float): Float {            // lent, rapide, lent
    val v = x.coerceIn(0f, 1f)
    return if (v < 0.5f) 4f * v * v * v else 1f - (-2f * v + 2f).pow(3) / 2f
}
fun ralentit(x: Float): Float {        // rapide puis ralentit
    val v = x.coerceIn(0f, 1f)
    return 1f - (1f - v).pow(3)
}
fun accelere(x: Float): Float {        // lent puis accélère
    val v = x.coerceIn(0f, 1f)
    return v * v * v
}
fun borne(x: Float, a: Float = 0f, b: Float = 1f): Float = x.coerceIn(a, b)
fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

// Reste d'une division, toujours positif (en Kotlin, -0.3 % 1 donne -0.3 ; ici on veut 0.7).
fun modulo(a: Float, b: Float): Float {
    val r = a % b
    return if (r < 0f) r + b else r
}

// Un nuage de particules tirées au hasard (toujours les mêmes pour une même graine) :
// chaque particule = [x, y, profondeur, phase], x et y entre 0 et 1.
fun nuage(graine: Int, n: Int): List<FloatArray> {
    val r = Random(graine)
    return List(n) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.15f + 0.85f * r.nextFloat(), r.nextFloat() * 6.28f) }
}

// Une grosse lumière floue d'arrière-plan (effet « bokeh » d'un objectif photo).
class Flou(val x: Float, val y: Float, val r: Float, val couleur: Int, val ph: Float)

fun bokehListe(graine: Int, couleurs: IntArray, n: Int = 9): List<Flou> {
    val r = Random(graine)
    return List(n) { k -> Flou(r.nextFloat(), r.nextFloat() * 0.6f, 0.05f + 0.07f * r.nextFloat(), couleurs[k % couleurs.size], r.nextFloat() * 6.28f) }
}

// LA BOÎTE À OUTILS DE DESSIN, partagée par toutes les scènes.
class Outils(val dp: Float) {

    // Un pinceau neuf. Avec "trait", il dessine des contours au lieu de remplir.
    fun pinceau(couleur: Int? = null, trait: Float? = null): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (couleur != null) p.color = couleur
        if (trait != null) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = trait
            p.strokeCap = Paint.Cap.ROUND
        }
        return p
    }

    // Un pinceau "lumière" : sa couleur s'AJOUTE à ce qui est dessous (comme un projecteur).
    fun lumiere(couleur: Int? = null, trait: Float? = null): Paint =
        pinceau(couleur, trait).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }

    // La gomme : efface les pixels (ils redeviennent transparents et l'app apparaît).
    fun gomme(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    // Une tache de lumière ovale : dégradé radial étiré horizontalement.
    fun tache(c: Canvas, x: Float, y: Float, sx: Float, sy: Float, intensite: Float, couleur: Int) {
        val a = borne(intensite)
        if (a <= 0.003f || sy <= 0.5f || sx <= 0.5f) return
        val rayon = sy * 2f
        val p = lumiere()
        p.shader = RadialGradient(
            x, y, rayon,
            intArrayOf(avecAlpha(couleur, a), avecAlpha(couleur, a * 0.37f), avecAlpha(couleur, 0f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.save()
        c.scale(sx / sy, 1f, x, y)
        c.drawCircle(x, y, rayon, p)
        c.restore()
    }

    // Un halo rond et doux.
    fun halo(c: Canvas, x: Float, y: Float, r: Float, intensite: Float, couleur: Int) =
        tache(c, x, y, r, r, intensite, couleur)

    // Révélation en cercle : un trou qui grandit depuis (cx, cy) jusqu'aux coins, avec un fin anneau.
    fun revelationCercle(c: Canvas, cx: Float, cy: Float, rev: Float, couleur: Int) {
        if (rev <= 0f) return
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        val rmax = max(max(hypot(cx, cy), hypot(w - cx, cy)), max(hypot(cx, h - cy), hypot(w - cx, h - cy)))
        val trou = rmax * ralentit(rev)
        c.drawCircle(cx, cy, trou, gomme())
        c.drawCircle(cx, cy, trou, pinceau(avecAlpha(couleur, (1f - rev) * 0.8f), (2f + 4f * (1f - rev)) * dp))
    }

    // Révélation en balayage : un bord lumineux traverse l'écran selon un angle (en degrés).
    fun revelationBalayage(c: Canvas, rev: Float, angle: Float, couleur: Int) {
        if (rev <= 0f) return
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        val diag = hypot(w, h)
        c.save()
        c.translate(w / 2f, h / 2f)
        c.rotate(angle)
        val x = -diag / 2f + diag * doux(rev) * 1.08f
        c.drawRect(-diag, -diag, x, diag, gomme())
        val bord = 26f * dp
        val p = lumiere()
        p.shader = LinearGradient(
            x - bord, 0f, x, 0f,
            avecAlpha(couleur, 0f), avecAlpha(couleur, 0.55f * (1f - rev * 0.5f)), Shader.TileMode.CLAMP
        )
        c.drawRect(x - bord, -diag, x, diag, p)
        c.restore()
    }
    // ---------------- OUTILS « CINÉMA » : ce qui donne de la profondeur ----------------

    // Assombrit les bords de l'image, comme un objectif de cinéma.
    fun vignette(c: Canvas, w: Float, h: Float, force: Float = 0.55f) {
        val r = hypot(w, h) / 2f
        val p = pinceau()
        p.shader = RadialGradient(
            w / 2f, h / 2f, r,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, argb(force * 255f, 0, 0, 0)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, p)
    }

    // Un trou à bord flou qui grandit depuis (cx, cy) : l'app apparaît comme dans un fondu.
    // DST_OUT = « efface là où je peins, proportionnellement à mon opacité ».
    fun revelationDouce(c: Canvas, cx: Float, cy: Float, rev: Float, couleur: Int? = null, flou: Float = 0.30f) {
        if (rev <= 0f) return
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        val rmax = max(max(hypot(cx, cy), hypot(w - cx, cy)), max(hypot(cx, h - cy), hypot(w - cx, h - cy)))
        val r = rmax * (1f + flou) * ralentit(rev) + 1f
        val p = pinceau()
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        p.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
            floatArrayOf(0f, 1f - flou, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, p)
        if (couleur != null) {
            c.drawCircle(cx, cy, r * (1f - flou * 0.5f), lumiere(avecAlpha(couleur, 0.35f * (1f - rev)), 3f * dp))
        }
    }

    // Un front à bord flou traverse l'écran selon un angle (en degrés) : derrière lui, l'app.
    fun revelationBalayageDouce(c: Canvas, rev: Float, angle: Float, couleur: Int? = null, flouDp: Float = 90f) {
        if (rev <= 0f) return
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        val diag = hypot(w, h)
        val flou = flouDp * dp
        c.save()
        c.translate(w / 2f, h / 2f)
        c.rotate(angle)
        val x = -diag / 2f - flou + (diag + 2f * flou) * doux(rev)
        val plein = pinceau(Color.BLACK)
        plein.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        c.drawRect(-diag, -diag, x - flou, diag, plein)
        val bord = pinceau()
        bord.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        bord.shader = LinearGradient(x - flou, 0f, x, 0f, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(x - flou, -diag, x, diag, bord)
        if (couleur != null) {
            val l = lumiere()
            l.shader = LinearGradient(
                x - flou, 0f, x, 0f,
                intArrayOf(avecAlpha(couleur, 0f), avecAlpha(couleur, 0.35f * (1f - rev)), avecAlpha(couleur, 0f)),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
            )
            c.drawRect(x - flou, -diag, x, diag, l)
        }
        c.restore()
    }

    // Particules en suspension. Les proches (profondeur forte) sont plus grosses et plus rapides.
    fun poussieres(c: Canvas, liste: List<FloatArray>, sec: Float, w: Float, h: Float,
                   couleur: Int, alpha: Float, vx: Float = 0f, vy: Float = -0.03f) {
        val p = pinceau()
        for (q in liste) {
            val x = modulo(q[0] + vx * sec * q[2], 1f) * w
            val y = modulo(q[1] + vy * sec * q[2], 1f) * h
            val scint = 0.5f + 0.5f * sin(sec * 2f + q[3])
            p.color = avecAlpha(couleur, alpha * (0.3f + 0.7f * scint) * (0.4f + 0.6f * q[2]))
            c.drawCircle(x, y, (0.6f + 1.8f * q[2]) * dp, p)
        }
    }

    // Grosses lumières floues d'arrière-plan, qui bougent à peine.
    fun bokeh(c: Canvas, liste: List<Flou>, sec: Float, w: Float, h: Float, alpha: Float) {
        for (f in liste) {
            val x = (f.x + 0.01f * sin(sec * 0.7f + f.ph)) * w
            val y = (f.y + 0.01f * kotlin.math.cos(sec * 0.6f + f.ph)) * h
            val r = f.r * kotlin.math.min(w, h)
            val a = alpha * (0.6f + 0.4f * sin(sec * 1.3f + f.ph))
            halo(c, x, y, r, a * 0.5f, f.couleur)
            c.drawCircle(x, y, r * 0.9f, lumiere(avecAlpha(f.couleur, a * 0.10f)))
        }
    }

    // ---------------- D'AUTRES RÉVÉLATIONS (pour ne pas toujours ouvrir en rond) ----------------
    // Toutes utilisent DST_OUT : là où on peint, l'animation s'efface et l'app apparaît.
    private fun trou(flouDp: Float = 0f): Paint {
        val p = pinceau(Color.BLACK)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        // bord flou (le flou n'est géré par l'accélération matérielle qu'à partir d'Android 9 ;
        // avant, le bord est simplement net)
        if (flouDp > 0f) p.maskFilter = BlurMaskFilter(flouDp * dp, BlurMaskFilter.Blur.NORMAL)
        return p
    }

    // Des BANDES horizontales qui s'ouvrent chacune depuis leur milieu, décalées en vague
    // (comme une figure d'interférence).
    fun revelationBandes(c: Canvas, rev: Float, couleur: Int? = null, n: Int = 14) {
        if (rev <= 0f) return
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val hb = h / n
        val p = trou(3f)
        for (i in 0 until n) {
            val decal = 0.5f + 0.5f * sin(i * 0.9f)          // l'ordre d'ouverture suit une onde
            val f = doux(borne(rev * 1.6f - decal * 0.6f))
            if (f <= 0f) continue
            val yc = (i + 0.5f) * hb
            val demi = hb * 0.5f * f + (if (f >= 1f) 2f else 0f)
            c.drawRect(-10f, yc - demi, w + 10f, yc + demi, p)
            if (couleur != null && f < 1f) {
                val l = lumiere(avecAlpha(couleur, 0.25f * (1f - f)), 1.2f * dp)
                c.drawLine(0f, yc - demi, w, yc - demi, l); c.drawLine(0f, yc + demi, w, yc + demi, l)
            }
        }
    }

    // Une TRAME de points : des trous ronds qui grossissent jusqu'à se toucher, en partant de (cx, cy).
    fun revelationPoints(c: Canvas, cx: Float, cy: Float, rev: Float, pasDp: Float = 22f) {
        if (rev <= 0f) return
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val pas = pasDp * dp * (w / (411f * dp))           // même trame quelle que soit la densité de l'écran
        val dmax = max(max(hypot(cx, cy), hypot(w - cx, cy)), max(hypot(cx, h - cy), hypot(w - cx, h - cy)))
        val p = trou()
        val ny = (h / (pas * 0.87f)).toInt() + 2; val nx = (w / pas).toInt() + 2
        for (j in 0 until ny) for (i in 0 until nx) {
            val x = (i + (if (j % 2 == 1) 0.5f else 0f)) * pas; val y = j * pas * 0.87f
            val f = borne(rev * 1.7f - hypot(x - cx, y - cy) / dmax * 0.7f)
            if (f <= 0f) continue
            c.drawCircle(x, y, pas * 0.62f * ralentit(f) + 0.5f, p)
        }
    }

    // Des COLONNES qui montent depuis le bas, chacune à son rythme (comme un graphique en barres).
    fun revelationColonnes(c: Canvas, rev: Float, n: Int = 9) {
        if (rev <= 0f) return
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val lc = w / n
        val p = trou(4f)
        for (i in 0 until n) {
            val decal = (0.5f + 0.5f * sin(i * 2.3f + 1f)) * 0.45f
            val f = doux(borne((rev - decal) / 0.55f))
            if (f <= 0f) continue
            c.drawRect(i * lc - 1f, h - (h + 20f) * f, (i + 1) * lc + 1f, h + 10f, p)
        }
    }

    // Des TUILES carrées qui disparaissent une à une, dans un ordre au hasard (chacune rétrécit).
    fun revelationTuiles(c: Canvas, rev: Float, tailleDp: Float = 46f, graine: Int = 4) {
        if (rev <= 0f) return
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val t = tailleDp * dp * (w / (411f * dp))
        val r = Random(graine)
        val p = trou()
        for (j in 0..(h / t).toInt()) for (i in 0..(w / t).toInt()) {
            val f = borne((rev - r.nextFloat() * 0.55f) / 0.45f)
            if (f <= 0f) continue
            val cx = (i + 0.5f) * t; val cy = (j + 0.5f) * t
            val d = t * 0.5f * ralentit(f) + 0.6f
            c.drawRect(cx - d, cy - d, cx + d, cy + d, p)
        }
    }

    // Une FENTE verticale qui s'ouvre depuis x, comme deux rideaux qui s'écartent.
    fun revelationFente(c: Canvas, x: Float, rev: Float) {
        if (rev <= 0f) return
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val e = max(x, w - x) * 1.1f * doux(rev) + 1f
        c.drawRect(x - e, -20f, x + e, h + 20f, trou(10f))
    }

    // Écrire du texte. ancre : 0 = aligné à gauche, 0,5 = centré, 1 = aligné à droite.
    fun texte(c: Canvas, s: String, x: Float, y: Float, taille: Float, couleur: Int,
              ancre: Float = 0f, mono: Boolean = false, gras: Boolean = false) {
        val p = pinceau(couleur)
        p.textSize = taille
        p.typeface = Typeface.create(if (mono) Typeface.MONOSPACE else Typeface.SANS_SERIF,
            if (gras) Typeface.BOLD else Typeface.NORMAL)
        c.drawText(s, x - p.measureText(s) * ancre, y, p)
    }

    // Largeur d'un texte (pour placer un exposant juste après, par exemple).
    fun largeurTexte(s: String, taille: Float, mono: Boolean = false, gras: Boolean = false): Float {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textSize = taille
        p.typeface = Typeface.create(if (mono) Typeface.MONOSPACE else Typeface.SANS_SERIF,
            if (gras) Typeface.BOLD else Typeface.NORMAL)
        return p.measureText(s)
    }
}
