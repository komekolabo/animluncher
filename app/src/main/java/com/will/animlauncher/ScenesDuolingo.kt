package com.will.animlauncher

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// =====================================================================================
//  DUOLINGO : 5 animations, l'app en tire une au hasard à chaque ouverture.
//  (Elles évoquent l'univers « série de jours / langues » sans jamais montrer
//  de personnage ni de logo : flamme, semaine, bulles, plume, chambre.)
//
//  Une astuce utilisée partout : les animations ont été mises au point sur un écran
//  « modèle » de 540 pixels de large. Pour s'adapter au vrai téléphone, on calcule
//  k = largeur réelle / 540, et on multiplie les tailles du modèle par k.
// =====================================================================================

// La liste des variantes (leur nom apparaît dans les réglages de l'app) et la fabrique.
val DUO_VARIANTES = listOf("Flamme", "Semaine", "Bulles", "Plume", "Chambre")
fun duolingo(numero: Int): Scene = when (numero) {
    0 -> DuoFlamme()
    1 -> DuoSemaine()
    2 -> DuoBulles()
    3 -> DuoPlume()
    4 -> DuoChambre()
    else -> listOf<() -> Scene>({ DuoFlamme() }, { DuoSemaine() }, { DuoBulles() }, { DuoPlume() }, { DuoChambre() }).random()()
}

// ------------------------- petits outils partagés par les 5 scènes -------------------------

private val PIf = PI.toFloat()

// « Rebond » : dépasse un peu sa cible puis revient (pour les apparitions qui « popent »).
private fun duoRebond(x: Float): Float {
    val v = x.coerceIn(0f, 1f); val c = 1.70158f
    return 1f + (c + 1f) * (v - 1f).pow(3) + c * (v - 1f).pow(2)
}

// La forme d'une langue de flamme : une goutte (fond arrondi, pointe qui ondule).
private fun duoGoutte(cx: Float, base: Float, haut: Float, larg: Float, sway: Float): Path {
    val r = larg
    val p = Path()
    p.moveTo(cx - r, base - r)
    p.cubicTo(cx - r * 1.05f, base - r - (haut - r) * 0.45f, cx - r * 0.25f + sway * 0.6f, base - haut * 0.78f, cx + sway, base - haut)
    p.cubicTo(cx + r * 0.3f + sway * 0.6f, base - haut * 0.72f, cx + r * 1.05f, base - r - (haut - r) * 0.4f, cx + r, base - r)
    p.arcTo(RectF(cx - r, base - 2f * r, cx + r, base), 0f, 180f, false)   // le fond arrondi
    p.close()
    return p
}

// Une flamme complète : 3 langues (rouge-orangé, orange, cœur doré) qui vacillent chacune à leur rythme.
private val DUO_COUCHES = arrayOf(
    floatArrayOf(1.00f, 235f, 85f, 25f, 0.80f, 5.3f, 8.1f),
    floatArrayOf(0.74f, 255f, 145f, 35f, 0.75f, 6.7f, 10.3f),
    floatArrayOf(0.46f, 255f, 205f, 110f, 0.60f, 8.9f, 12.7f)
)
private fun duoFlamme(c: Canvas, o: Outils, cx: Float, base: Float, hauteur: Float, largeur: Float, sec: Float, alpha: Float) {
    if (hauteur < 2f || alpha <= 0f) return
    for ((k, d) in DUO_COUCHES.withIndex()) {
        val f = d[0]; val col = rgb(d[1].toInt(), d[2].toInt(), d[3].toInt()); val op = d[4]
        val haut = hauteur * f * (1f + 0.07f * sin(sec * d[5] + k) + 0.04f * sin(sec * d[6] + 2 * k))
        val larg = largeur * f * (1f + 0.04f * sin(sec * d[6] * 0.7f + k))
        val sway = largeur * f * (0.30f * sin(sec * d[5] * 0.9f + k * 1.3f) + 0.15f * sin(sec * d[6] * 1.1f))
        val p = o.lumiere()
        // plus opaque en bas, presque transparente vers la pointe
        p.shader = LinearGradient(0f, base, 0f, base - haut, avecAlpha(col, op * alpha), avecAlpha(col, 0.12f * alpha), Shader.TileMode.CLAMP)
        c.drawPath(duoGoutte(cx, base, max(haut, larg * 1.3f), larg, sway), p)
    }
}

// Un rond flou d'arrière-plan (lumière hors mise au point) : bord doux, liseré un peu plus clair.
private fun duoBokeh(c: Canvas, o: Outils, x: Float, y: Float, r: Float, col: Int, a: Float) {
    if (a <= 0.002f || r <= 1f) return
    val p = o.lumiere()
    p.shader = RadialGradient(x, y, r,
        intArrayOf(avecAlpha(col, a * 0.75f), avecAlpha(col, a * 0.75f), avecAlpha(col, a), avecAlpha(col, 0f)),
        floatArrayOf(0f, 0.6f, 0.88f, 1f), Shader.TileMode.CLAMP)
    c.drawCircle(x, y, r, p)
}

// Lit l'heure : vrai entre 19 h et 7 h (pour la chambre : version nuit ou matin).
private fun duoNuit(): Boolean {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return h >= 19 || h < 7
}

// Taille de l'écran, connue dès la création de la scène (pour préparer les dessins à l'avance).
private fun duoEcran(): Pair<Int, Int> {
    val m = Resources.getSystem().displayMetrics
    return Pair(min(m.widthPixels, m.heightPixels), max(m.widthPixels, m.heightPixels))
}

// Un « bruit » doux (valeurs entre 0 et 1 qui varient en nuages), pour les bords irréguliers.
private fun duoHachage(ix: Int, iy: Int, graine: Int): Float {
    var n = ix * 374761393 + iy * 668265263 + graine * 2147483
    n = (n xor (n ushr 13)) * 1274126177
    return ((n xor (n ushr 16)) and 0xffff) / 65535f
}
private fun duoBruit(x: Float, y: Float, graine: Int): Float {
    val ix = floor(x).toInt(); val iy = floor(y).toInt()
    val fx = x - ix; val fy = y - iy
    val sx = fx * fx * (3 - 2 * fx); val sy = fy * fy * (3 - 2 * fy)    // transition douce entre les points
    val a = duoHachage(ix, iy, graine); val b = duoHachage(ix + 1, iy, graine)
    val c = duoHachage(ix, iy + 1, graine); val d = duoHachage(ix + 1, iy + 1, graine)
    return mix(mix(a, b, sx), mix(c, d, sx), sy)
}


// =====================================================================================
//  1. FLAMME : une étincelle tombe et allume une flamme. Ouverture : l'écran se consume.
// =====================================================================================
class DuoFlamme : Scene() {
    private val braises: List<FloatArray>
    private val bokeh: List<FloatArray>
    @Volatile private var carte: Bitmap? = null      // la « carte de brûlure », préparée à l'avance

    init {
        val r = Random(7)
        braises = List(46) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat() * 6.28f) }
        bokeh = List(12) { floatArrayOf(r.nextFloat(), 0.15f + 0.6f * r.nextFloat(), 18f + 28f * r.nextFloat(), r.nextFloat(), r.nextFloat() * 6.28f) }
        // La carte de brûlure se calcule dans un fil à part, pour ne pas ralentir le début de l'animation.
        Thread { carte = fabriquerCarte() }.start()
    }

    // Pour chaque point de l'écran : « quand » il brûle (0 = tout de suite, 1 = en dernier).
    // Près de la flamme ça brûle tôt, et un bruit rend le bord irrégulier, comme du papier.
    private fun fabriquerCarte(): Bitmap {
        val (w, h) = duoEcran()
        val k = w / 540f
        val bw = w / 4; val bh = h / 4
        val px = IntArray(bw * bh)
        val cx = w / 2f; val cy = h * 0.6f; val diag = hypot(w.toFloat(), h.toFloat())
        var mini = 9f; var maxi = -9f
        val v = FloatArray(bw * bh)
        for (j in 0 until bh) for (i in 0 until bw) {
            val x = (i + 0.5f) * 4f; val y = (j + 0.5f) * 4f
            val dist = hypot(x - cx, (y - cy) * 0.75f) / diag * 1.9f
            val n = 0.55f * duoBruit(x / (90f * k), y / (90f * k), 1) + 0.30f * duoBruit(x / (40f * k), y / (40f * k), 2) + 0.15f * duoBruit(x / (16f * k), y / (16f * k), 3)
            val valeur = dist * 0.75f + n * 0.40f
            v[j * bw + i] = valeur
            mini = min(mini, valeur); maxi = max(maxi, valeur)
        }
        for (t in v.indices) {
            val g = ((v[t] / 1.4f).coerceIn(0f, 1f) * 255).toInt()
            px[t] = Color.rgb(g, g, g)
        }
        return Bitmap.createBitmap(px, bw, bh, Bitmap.Config.ARGB_8888)
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val k = w / 540f
        val app = ralentit(intro)
        val cx = w / 2f; val base = h * 0.62f; val m = min(w, h)

        // --- LE FOND : dégradé chaud, touches froides dans les coins, lueur qui respire ---
        degradeVertical(c, o, w, rgb(10, 7, 6), rgb(16, 9, 6), 0f, h)
        o.tache(c, w * 0.08f, h * 0.05f, w * 0.55f, h * 0.18f, 0.07f, rgb(40, 60, 120))
        o.tache(c, w * 0.95f, h * 0.12f, w * 0.45f, h * 0.16f, 0.05f, rgb(90, 40, 110))
        val resp = 0.9f + 0.1f * sin(sec * 3.1f)
        o.tache(c, cx, base - h * 0.12f, w * 0.55f, h * 0.26f, 0.11f * app * resp, rgb(200, 80, 25))
        // braises floues au loin, qui montent lentement
        for (b in bokeh) {
            val y = h * b[1] - sec * 14f * k * (0.4f + b[3])
            val x = w * b[0] + sin(sec * 0.5f + b[4]) * 12f * k
            val col = mixCouleur(rgb(255, 120, 40), rgb(200, 50, 30), b[3])
            duoBokeh(c, o, x, y, b[2] * 1.05f * k, col, (0.05f + 0.05f * b[3]) * app * (0.8f + 0.2f * sin(sec * 1.7f + b[4])))
        }
        // le sol : une surface sombre et brillante, avec le reflet de la flamme qui tremble
        val sol = o.pinceau()
        sol.shader = LinearGradient(0f, base, 0f, base + h * 0.02f, Color.TRANSPARENT, avecAlpha(rgb(18, 10, 8), 0.35f), Shader.TileMode.CLAMP)
        c.drawRect(0f, base, w, h, sol)
        val ligne = o.lumiere()
        ligne.shader = LinearGradient(cx - w * 0.45f, 0f, cx + w * 0.45f, 0f,
            intArrayOf(Color.TRANSPARENT, avecAlpha(rgb(255, 140, 60), 0.10f * app), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, base + 1f * k, w, base + 3f * k, ligne)
        val trem = 1f + 0.12f * sin(sec * 9f) + 0.06f * sin(sec * 14f)
        o.tache(c, cx, base + h * 0.075f, w * 0.05f * trem, h * 0.07f, 0.16f * app, rgb(255, 120, 40))
        o.tache(c, cx, base + h * 0.03f, w * 0.16f, h * 0.012f, 0.18f * app, rgb(255, 150, 60))

        // --- LA FLAMME : une étincelle tombe (début de l'intro), puis la flamme prend ---
        val chute = borne(intro / 0.5f)
        val prise = ralentit((intro - 0.45f) / 0.55f)
        val H_ = m * 0.40f * prise
        val L_ = m * 0.105f * (0.4f + 0.6f * prise)
        val resp2 = 0.85f + 0.15f * sin(sec * 2.3f)
        o.tache(c, cx, base + 4f * dp, m * 0.30f, m * 0.035f, 0.45f * prise * resp2, rgb(170, 45, 10))   // lit de braises
        for (n in 0 until 5) {
            o.tache(c, cx + (n - 2) * m * 0.055f, base + 3f * dp, m * 0.04f, m * 0.014f,
                (0.35f + 0.15f * sin(sec * 3f + n * 2)) * prise, rgb(200, 70, 20))
        }
        o.halo(c, cx, base - H_ * 0.4f, max(2f, H_ * 0.9f), 0.16f * prise, rgb(255, 110, 30))
        duoFlamme(c, o, cx, base, H_, L_, sec, 1f)
        if (chute < 1f) {
            val y = base - m * 0.55f * (1f - chute * chute)
            c.drawCircle(cx, y, 2.4f * dp, o.lumiere(avecAlpha(rgb(255, 190, 90), 0.9f)))
            o.halo(c, cx, y, 10f * dp, 0.3f, rgb(255, 140, 40))
        }
        // les braises qui s'envolent : tailles et couleurs variées, elles s'éteignent en montant
        val pb = o.lumiere()
        for (b in braises) {
            val t = modulo(b[1] + sec * (0.35f + 0.5f * b[2]), 1f)
            val x = cx + (b[0] - 0.5f) * L_ * 2.6f + sin(sec * 2.5f + b[3]) * 14f * dp * t
            val y = base - H_ * 0.3f - t * m * 0.55f
            pb.color = avecAlpha(mixCouleur(rgb(255, 170, 60), rgb(190, 50, 20), t), (1f - t).pow(1.5f) * 0.85f * prise)
            c.drawCircle(x, y, (0.7f + 1.3f * b[2]) * dp * (1f - 0.5f * t), pb)
        }
        o.vignette(c, w, h, 0.6f)

        // --- L'OUVERTURE : l'écran se consume depuis la flamme ---
        val carteBrulure = carte
        if (rev > 0f) {
            if (carteBrulure == null) { o.revelationDouce(c, cx, base, rev); return }
            val front = (doux(rev) * 1.30f - 0.05f) / 1.4f
            val dest = RectF(0f, 0f, w, h)
            // 1. le papier noircit juste avant de brûler
            c.drawBitmap(carteBrulure, null, dest, passe(0, 0, 0, 12f, front + 0.10f / 1.4f, 166, null))
            // 2. le liseré de braise orange (ajouté comme de la lumière)
            c.drawBitmap(carteBrulure, null, dest, passe(255, 110, 35, 30f, front + 0.035f / 1.4f, 140, PorterDuff.Mode.ADD))
            // 3. ce qui a brûlé disparaît : l'app apparaît derrière
            c.drawBitmap(carteBrulure, null, dest, passe(0, 0, 0, 100f, front, 255, PorterDuff.Mode.DST_OUT))
        }
    }

    // Un pinceau qui transforme la carte de brûlure en masque : opaque là où la valeur est
    // plus petite que « seuil », transparent ailleurs. « raideur » règle la netteté du bord.
    // (C'est une « matrice de couleur » : elle calcule l'opacité de chaque point à partir de son gris.)
    private fun passe(r: Int, g: Int, b: Int, raideur: Float, seuil: Float, alpha: Int, mode: PorterDuff.Mode?): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            0f, 0f, 0f, 0f, r.toFloat(),
            0f, 0f, 0f, 0f, g.toFloat(),
            0f, 0f, 0f, 0f, b.toFloat(),
            -raideur, 0f, 0f, 0f, raideur * seuil * 255f
        )))
        p.alpha = alpha
        if (mode != null) p.xfermode = PorterDuffXfermode(mode)
        return p
    }
}


// =====================================================================================
//  2. SEMAINE : les 7 jours s'allument, une petite flamme prend sur aujourd'hui.
//     Ouverture : l'écran se découpe en 7 colonnes qui tombent l'une après l'autre.
// =====================================================================================
class DuoSemaine : Scene() {
    private val jours = "LMMJVSD"
    private val bokeh: List<FloatArray>
    private val lettre = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    init {
        val r = Random(11)
        bokeh = List(10) { floatArrayOf(r.nextFloat(), 0.1f + 0.8f * r.nextFloat(), 14f + 20f * r.nextFloat(), r.nextFloat(), r.nextFloat() * 6.28f) }
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val rev = i.revelation
        if (rev <= 0f) { scene(c, o, i, w, h); return }
        // Pendant l'ouverture : on redessine la scène 7 fois, une fois par colonne,
        // chaque colonne étant découpée (clipRect) puis décalée vers le bas (translate).
        // Ce qui est au-dessus d'une colonne qui tombe reste vide : l'app s'y voit.
        val k = w / 540f
        val larg = w / 7f
        for (n in 0 until 7) {
            val u = borne((rev - n * 0.07f) / 0.55f)
            val dec = accelere(u) * h * 1.05f
            if (dec >= h) continue
            val marge = 4.5f * u * k                      // un fin espace apparaît entre les colonnes
            val xa = n * larg + marge; val xb = (n + 1) * larg - marge
            c.save()
            c.clipRect(xa, 0f, xb, h)
            c.translate(0f, dec)
            scene(c, o, i, w, h)
            c.restore()
            if (dec > 0f) {                               // petite ombre en haut de la colonne qui tombe
                val om = o.pinceau()
                om.shader = LinearGradient(0f, dec, 0f, dec + 14f * k, avecAlpha(Color.BLACK, 0.6f * u), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                c.drawRect(xa, dec, xb, dec + 14f * k, om)
            }
        }
    }

    private fun scene(c: Canvas, o: Outils, i: Instant, w: Float, h: Float) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val k = w / 540f
        val app = ralentit(intro)
        val a = 1f - rev
        // --- LE FOND : nuit, une aurore très douce qui ondule (violet → orangé), lumières floues ---
        degradeVertical(c, o, w, rgb(9, 9, 12), rgb(12, 10, 10), 0f, h)
        val tranches = 48
        val pa = o.lumiere()
        for (s in 0 until tranches) {
            val x0 = w * s / tranches; val x1 = w * (s + 1) / tranches
            val xm = (x0 + x1) / 2f
            val yb = h * 0.30f + sin(xm / w * 4f + sec * 0.6f) * h * 0.03f
            val col = mixCouleur(rgb(110, 50, 170), rgb(200, 90, 40), xm / w)
            val e = h * 0.12f
            pa.shader = LinearGradient(0f, yb - e, 0f, yb + e,
                intArrayOf(Color.TRANSPARENT, avecAlpha(col, 0.06f * app), avecAlpha(col, 0.17f * app), avecAlpha(col, 0.06f * app), Color.TRANSPARENT),
                floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(x0, yb - e, x1 + 1f, yb + e, pa)
        }
        o.tache(c, w * 0.5f, h * 0.52f, w * 0.55f, h * 0.10f, 0.07f * app, rgb(220, 100, 40))
        o.tache(c, w * 0.5f, h * 0.85f, w * 0.6f, h * 0.12f, 0.05f, rgb(60, 40, 110))
        for (b in bokeh) {
            val col = mixCouleur(rgb(255, 140, 50), rgb(150, 90, 230), b[3])
            duoBokeh(c, o, w * b[0] + sin(sec * 0.4f + b[4]) * 10f * k, h * b[1], b[2] * 1.05f * k, col, 0.045f * app)
        }

        // --- LES 7 JOURS ---
        val esp = w * 0.125f; val r = w * 0.043f; val y = h * 0.52f
        o.tache(c, w / 2f, y + w * 0.11f, w * 0.45f, 4f * k, 0.10f * app, rgb(255, 130, 40))   // reflet au sol
        val orange = rgb(255, 140, 40)
        lettre.textSize = 26f * k
        for (n in 0 until 7) {
            val x = w / 2f + (n - 3) * esp
            val t = 0.12f + n * 0.11f
            val p = borne((sec - t) / 0.16f)
            if (n > 0) {                                     // le trait qui relie au jour d'avant
                val x0 = x - esp + r; val x1 = x - r
                val l = borne((sec - t + 0.08f) / 0.1f)
                if (l > 0f) c.drawRect(x0, y - 2f * dp, x0 + (x1 - x0) * l, y + 2f * dp, o.lumiere(avecAlpha(rgb(200, 90, 30), 0.7f * a)))
            }
            c.drawCircle(x, y, r, o.pinceau(avecAlpha(rgb(60, 62, 70), 0.9f * app * a), 2f * dp))   // cercle vide
            if (p > 0f) {
                c.drawCircle(x, y, r * duoRebond(p), o.pinceau(avecAlpha(mixCouleur(rgb(200, 75, 20), orange, 0.5f), 0.95f * a)))
                o.halo(c, x, y, r * 1.6f, 0.18f * p * a, orange)
            }
            lettre.color = avecAlpha(if (p >= 1f) rgb(255, 185, 110) else rgb(90, 92, 100), app * a)
            c.drawText(jours[n].toString(), x, y + r + 26f * dp + 9f * k, lettre)
        }
        // aujourd'hui : une petite flamme s'allume sur le dernier cercle
        val pf = ralentit((sec - (0.12f + 6 * 0.11f) - 0.12f) / 0.3f)
        if (pf > 0f) {
            val xs = w / 2f + 3 * esp
            val repli = 1f - ralentit(rev / 0.6f)
            duoFlamme(c, o, xs, y - r * 0.15f, r * 3.6f * pf * repli, r * 0.8f * (0.5f + 0.5f * pf), sec, a)
        }
        o.vignette(c, w, h, 0.55f)
    }
}


// =====================================================================================
//  3. BULLES : « bonjour » dans 9 langues, chaque bulle « pope » avec un halo de sa couleur.
//     Ouverture : chaque bulle éclate et ouvre un trou ; les trous grandissent et se rejoignent.
// =====================================================================================
class DuoBulles : Scene() {
    private class Salut(val texte: String, val couleur: Int, val px: Float, val py: Float, val queueAGauche: Boolean)
    private val saluts = listOf(
        Salut("Bonjour", rgb(110, 200, 120), 0.30f, 0.30f, true), Salut("Hola", rgb(240, 150, 70), 0.72f, 0.24f, false),
        Salut("Hello", rgb(110, 170, 240), 0.62f, 0.42f, true), Salut("Ciao", rgb(220, 110, 120), 0.24f, 0.50f, false),
        Salut("こんにちは", rgb(190, 140, 240), 0.70f, 0.60f, true), Salut("Hallo", rgb(240, 200, 90), 0.33f, 0.68f, false),
        Salut("Olá", rgb(90, 200, 200), 0.76f, 0.78f, true), Salut("안녕", rgb(240, 130, 190), 0.28f, 0.85f, false),
        Salut("Привет", rgb(140, 190, 255), 0.55f, 0.12f, true)
    )
    private val taches = listOf(floatArrayOf(0.2f, 0.25f), floatArrayOf(0.8f, 0.35f), floatArrayOf(0.3f, 0.75f), floatArrayOf(0.75f, 0.85f))
    private val couleursTaches = intArrayOf(rgb(30, 90, 120), rgb(90, 50, 150), rgb(40, 70, 160), rgb(130, 45, 100))
    private val etoiles = nuage(42, 50)
    private val ecriture = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; textSize = 44f }

    // Position d'une bulle à cet instant (elle flotte doucement en montant).
    private fun position(n: Int, s: Salut, sec: Float, w: Float, h: Float, dp: Float): Pair<Float, Float> =
        Pair(s.px * w + sin(sec * 1.3f + n) * 5f * dp, s.py * h - sec * 6f * dp + sin(sec * 1.7f + n * 2) * 4f * dp)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val k = w / 540f
        val app = ralentit(intro)
        val a = 1f - rev

        // --- LE FOND : 4 grandes taches de couleur qui dérivent lentement + poussière d'étoiles ---
        degradeVertical(c, o, w, rgb(10, 11, 15), rgb(8, 9, 12), 0f, h)
        for ((n, t) in taches.withIndex()) {
            val x = w * t[0] + sin(sec * 0.35f + n * 1.7f) * w * 0.06f
            val y = h * t[1] + cos(sec * 0.3f + n * 2.1f) * h * 0.04f
            o.tache(c, x, y, w * 0.55f, h * 0.22f, 0.11f * app, couleursTaches[n])
        }
        val pe = o.lumiere()
        for (e in etoiles) {
            pe.color = avecAlpha(rgb(180, 190, 230), 0.25f * e[2] * app)
            c.drawCircle(e[0] * w, modulo(e[1] * h - sec * 8f * dp * e[2], h), (0.5f + e[2] * 0.7f) * dp, pe)
        }
        o.tache(c, w / 2f, h * 0.5f, w * 0.5f, h * 0.2f, 0.08f * app * a, rgb(90, 120, 200))

        // --- LES BULLES ---
        for ((n, s) in saluts.withIndex()) {
            val p = (sec - 0.05f - n * 0.09f) / 0.22f
            if (p <= 0f) continue
            val (x, y) = position(n, s, sec, w, h, dp)
            val sortie = 1f - ralentit((rev - n * 0.04f) / 0.5f)     // à l'ouverture, elles se dégonflent
            o.halo(c, x, y, 110f * 1.05f * k / 2f, 0.24f * borne(p * 2f) * sortie, s.couleur)   // halo de sa couleur
            val echelle = duoRebond(p) * 0.82f * sortie
            if (echelle <= 0.01f) continue
            dessinerBulle(c, s, x, y, echelle * k, borne(p * 3f))
        }
        o.vignette(c, w, h, 0.55f)

        // --- L'OUVERTURE : des trous qui s'ouvrent à la place des bulles ---
        if (rev > 0f) {
            for ((n, s) in saluts.withIndex()) {
                val u = borne((rev - n * 0.05f) / 0.6f)
                if (u <= 0f) continue
                val (x, y) = position(n, s, sec, w, h, dp)
                val r = doux(u) * h * 0.6f + 4f * k
                c.drawCircle(x, y, r, o.lumiere(avecAlpha(s.couleur, 0.35f * (1f - u)), 2.5f * k))   // fin anneau coloré
                val g = o.pinceau()
                g.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                g.shader = RadialGradient(x, y, r, intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                    floatArrayOf(0f, borne((r - 22f * k) / r, 0f, 0.99f), 1f), Shader.TileMode.CLAMP)
                c.drawCircle(x, y, r, g)
            }
        }
    }

    // Une bulle de discussion (fond sombre, contour coloré, petite queue) avec le mot au centre.
    // On dessine dans les unités de l'écran modèle, puis on agrandit/rétrécit avec « echelle ».
    private fun dessinerBulle(c: Canvas, s: Salut, x: Float, y: Float, echelle: Float, alpha: Float) {
        val tw = ecriture.measureText(s.texte)
        val bw = tw + 64f; val bh = 92f
        c.save()
        c.translate(x, y)
        c.scale(echelle, echelle)
        val haut = -57f; val bas = haut + bh
        val fond = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = avecAlpha(rgb(26, 29, 36), alpha) }
        val bord = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = avecAlpha(s.couleur, alpha); style = Paint.Style.STROKE; strokeWidth = 3f; strokeJoin = Paint.Join.ROUND }
        val corps = RectF(-bw / 2f, haut, bw / 2f, bas)
        // la petite queue de la bulle, à gauche ou à droite
        val qx = if (s.queueAGauche) -bw / 2f + 24f else bw / 2f - 24f
        val sens = if (s.queueAGauche) 1f else -1f
        val queue = Path().apply {
            moveTo(qx, bas - 3f); lineTo(qx + sens * 22f, bas - 3f); lineTo(qx - sens * 6f, bas + 22f); close()
        }
        c.drawRoundRect(corps, 34f, 34f, fond)
        c.drawRoundRect(corps, 34f, 34f, bord)
        c.drawPath(queue, fond)
        c.drawLine(qx + sens * 22f, bas, qx - sens * 6f, bas + 22f, bord)
        c.drawLine(qx - sens * 6f, bas + 22f, qx, bas, bord)
        ecriture.color = avecAlpha(rgb(232, 232, 238), alpha)
        val fm = ecriture.fontMetrics
        c.drawText(s.texte, 0f, (haut + bas) / 2f - (fm.ascent + fm.descent) / 2f, ecriture)
        c.restore()
    }
}


// =====================================================================================
//  4. PLUME : une plume duveteuse tombe dans des rayons de lumière.
//     Ouverture : une rafale de vent l'emporte et balaie l'écran.
// =====================================================================================
class DuoPlume : Scene() {
    @Volatile private var plume: Bitmap? = null          // la plume, dessinée une seule fois
    @Volatile private var plumeFloue: Bitmap? = null     // sa version floue (pour les plumes lointaines)
    private var echellePlume = 1f              // pixels de la plume par unité de l'écran modèle
    private val poussiere: List<FloatArray>
    private val lucioles: List<FloatArray>
    private val vent: List<FloatArray>
    private val loin = arrayOf(floatArrayOf(0.18f, 0.1f, 0.55f, 1.0f), floatArrayOf(0.80f, 0.35f, 0.45f, 2.4f), floatArrayOf(0.62f, -0.05f, 0.38f, 4.1f))
    private val rayons = arrayOf(floatArrayOf(-0.25f, 0.06f, 1.0f), floatArrayOf(-0.08f, 0.04f, 0.8f), floatArrayOf(0.07f, 0.07f, 1.0f), floatArrayOf(0.22f, 0.035f, 0.7f), floatArrayOf(0.34f, 0.05f, 0.6f))

    init {
        val r = Random(23)
        poussiere = List(55) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat() * 6.28f) }
        lucioles = List(14) { floatArrayOf(r.nextFloat(), 0.2f + 0.75f * r.nextFloat(), 10f + 20f * r.nextFloat(), r.nextFloat(), r.nextFloat() * 6.28f) }
        vent = List(10) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
        // la plume se dessine dans un fil à part, pour que l'animation démarre tout de suite
        Thread { fabriquerPlume(duoEcran().first / 540f) }.start()
    }

    // LA PLUME : dessinée barbe par barbe (150 de chaque côté), avec des mèches,
    // une pointe arrondie, des brins fous, et un duvet ébouriffé en bas.
    private fun fabriquerPlume(k: Float) {
        val s = k * 1.15f
        echellePlume = s
        val bw = (150 * s).toInt().coerceAtLeast(2); val bh = (280 * s).toInt().coerceAtLeast(2)
        val nette = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val d = Canvas(nette)
        d.scale(s, s)
        val r = java.util.Random(9)
        val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        val haut = 12f; val bas = 225f; val cx = 75f
        fun tigeX(t: Float) = cx + 7f * sin(t * 2.2f) - 4f
        fun tigeY(t: Float) = haut + t * (bas - haut)
        val n = 150
        var paquet = 0f
        for (k2 in 0 until n) {
            val t = k2.toFloat() / n
            val x = tigeX(t); val y = tigeY(t)
            if (k2 % 9 == 0) paquet = r.nextFloat() * 12f - 6f
            for ((sg, ampl) in listOf(Pair(-1f, 36f), Pair(1f, 28f))) {
                val prof = sqrt(min(1f, (t + 0.01f) / 0.32f)) * (1f - 0.2f * t)
                val duvet = borne((t - 0.55f) / 0.3f)
                val lg = ampl * prof * (0.85f + 0.25f * r.nextFloat()) * (1f + 0.35f * duvet)
                val mont = (14f * prof + 10f * (1f - min(1f, t / 0.2f))) * (1f - 0.7f * duvet)
                val bx = x + sg * lg
                val by = y - mont + paquet * (1f - duvet) + r.nextGaussian().toFloat() * 7f * duvet
                val ctx = x + sg * lg * 0.55f
                val cty = y - mont * 0.2f + r.nextGaussian().toFloat() * 5f * duvet
                // couleur : plus claire vers la pointe, un côté à l'ombre, le duvet plus pâle
                val clair = 1f - t
                var cr = 70f + (145f - 70f) * clair; var cg = 155f + (225f - 155f) * clair; var cb = 48f + (105f - 48f) * clair
                if (sg > 0f) { cr *= 0.88f; cg *= 0.88f; cb *= 0.88f }
                cr += (175f - cr) * duvet * 0.6f; cg += (225f - cg) * duvet * 0.6f; cb += (150f - cb) * duvet * 0.6f
                trait.color = Color.argb((230f - 120f * duvet - r.nextFloat() * 40f).toInt().coerceIn(0, 255), cr.toInt(), cg.toInt(), cb.toInt())
                trait.strokeWidth = max(1f / s, 1.7f - 0.8f * duvet)
                d.drawPath(Path().apply { moveTo(x, y); quadTo(ctx, cty, bx, by) }, trait)
                if (r.nextFloat() < 0.22f) {                 // un brin fou qui dépasse du bord
                    trait.color = Color.argb(110, 175, 230, 145); trait.strokeWidth = 1f
                    val b2x = bx + sg * (4f + 6f * r.nextFloat()); val b2y = by + r.nextGaussian().toFloat() * 4f - 2f
                    d.drawPath(Path().apply { moveTo(bx, by); quadTo(bx + sg * 3f, by + r.nextGaussian().toFloat() * 3f, b2x, b2y) }, trait)
                }
            }
        }
        // le petit duvet au pied de la tige
        val xb = tigeX(1f); val yb = tigeY(1f)
        trait.color = Color.argb(90, 185, 230, 160); trait.strokeWidth = 1f
        for (k2 in 0 until 40) {
            val ang = -2.6f + 2.1f * r.nextFloat()
            val lg = 12f + 18f * r.nextFloat()
            val ex = xb + cos(ang) * lg; val ey = yb + 8f + sin(ang) * lg * 0.6f + r.nextGaussian().toFloat() * 4f
            d.drawPath(Path().apply { moveTo(xb, yb + 8f); quadTo(xb + cos(ang) * lg * 0.4f + r.nextGaussian().toFloat() * 5f, yb + 8f + r.nextGaussian().toFloat() * 5f, ex, ey) }, trait)
        }
        // la tige
        trait.color = rgb(225, 240, 205); trait.strokeWidth = 2f
        val tige = Path().apply {
            moveTo(tigeX(0f), tigeY(0f))
            for (j in 1..30) { val t = j / 30f; lineTo(tigeX(t), tigeY(t)) }
            lineTo(tigeX(1f) - 1f, bas + 32f)
        }
        d.drawPath(tige, trait)

        // Version finale : un halo vert flou dessous, puis la plume un peu transparente par-dessus (douceur).
        val finale = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val f = Canvas(finale)
        val ombre = nette.extractAlpha()
        f.drawBitmap(ombre, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(128, 120, 200, 90); maskFilter = BlurMaskFilter(8f * s, BlurMaskFilter.Blur.NORMAL) })
        f.drawBitmap(nette, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 210 })
        // Version floue et sombre pour les plumes au loin.
        val floue = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        Canvas(floue).drawBitmap(ombre, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rgb(80, 150, 60); maskFilter = BlurMaskFilter(4f * s, BlurMaskFilter.Blur.NORMAL) })
        plumeFloue = floue
        plume = finale
    }

    // Pose une plume : elle tourne sur sa tige (on la rétrécit en largeur) et elle est inclinée.
    private fun poser(c: Canvas, img: Bitmap, x: Float, y: Float, angle: Float, retourne: Float, taille: Float, alpha: Float) {
        val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = (alpha * 255).toInt().coerceIn(0, 255) }
        c.save()
        c.translate(x, y)
        c.rotate(-angle)                               // (sur Android, un angle positif tourne dans le sens horaire)
        c.scale(max(0.12f, abs(retourne)) * taille, taille)
        c.drawBitmap(img, -img.width / 2f, -img.height / 2f, p)
        c.restore()
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val k = w / 540f
        val app = ralentit(intro)

        // --- LE FOND : vert très sombre, rayons de lumière en éventail depuis le haut ---
        degradeVertical(c, o, w, rgb(7, 11, 8), rgb(4, 6, 5), 0f, h)
        val sx = w * 0.42f; val sy = -h * 0.15f
        val pr = o.lumiere()
        pr.shader = RadialGradient(sx, sy, h * 1.15f, intArrayOf(rgb(170, 225, 160), rgb(170, 225, 160), Color.BLACK),
            floatArrayOf(0f, 0.15f, 1f), Shader.TileMode.CLAMP)
        for ((n, rr) in rayons.withIndex()) {
            val ang = rr[0] + 0.02f * sin(sec * 0.7f + n * 1.9f)
            val force = rr[2] * (0.75f + 0.25f * sin(sec * 1.1f + n)) * 0.07f * app
            // chaque rayon = 3 coins de lumière emboîtés, de plus en plus larges et faibles (bord doux)
            for ((f, al) in listOf(Pair(0.5f, 0.45f), Pair(1.0f, 0.35f), Pair(1.7f, 0.2f))) {
                val l = rr[1] * f
                val L = h * 1.3f
                val coin = Path().apply {
                    moveTo(sx, sy)
                    lineTo(sx + sin(ang - l) * L, sy + cos(ang - l) * L)
                    lineTo(sx + sin(ang + l) * L, sy + cos(ang + l) * L)
                    close()
                }
                pr.alpha = (255 * force * al * 3f).toInt().coerceIn(0, 255)
                c.drawPath(coin, pr)
            }
        }
        o.tache(c, sx, 0f, w * 0.6f, h * 0.12f, 0.10f * app, rgb(150, 210, 140))       // la source, en haut
        o.tache(c, w * 0.5f, h, w * 0.8f, h * 0.15f, 0.07f * app, rgb(40, 110, 100))    // brume en bas
        for (b in lucioles) {
            val col = mixCouleur(rgb(170, 230, 120), rgb(240, 210, 120), b[3])
            duoBokeh(c, o, w * b[0] + sin(sec * 0.6f + b[4]) * 14f * k, h * b[1] + cos(sec * 0.5f + b[4]) * 8f * k,
                b[2] * 1.05f * k, col, (0.035f + 0.04f * b[3]) * app * (0.7f + 0.3f * sin(sec * 2f + b[4])))
        }
        // un rai de lumière principal, en biais, et la poussière qui brille dedans
        val rai = Path().apply {
            moveTo(w * 0.46f - 40f * dp, 0f); lineTo(w * 0.46f + 40f * dp, 0f)
            lineTo(w * 0.46f + h * 0.08f + 40f * dp + h * 0.22f, h); lineTo(w * 0.46f + h * 0.08f - 40f * dp - h * 0.22f, h); close()
        }
        val prai = o.lumiere()
        prai.shader = LinearGradient(0f, 0f, 0f, h, avecAlpha(rgb(110, 190, 90), 0.06f * app), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawPath(rai, prai)
        val pp = o.lumiere()
        for (q in poussiere) {
            val xx = q[0] * w + sin(sec * 0.8f + q[3]) * 10f * dp
            val yy = modulo(q[1] * h - sec * 10f * dp * q[2], h)
            val dans = max(0f, 1f - abs(xx - (w * 0.46f + yy * 0.08f)) / (40f * dp + yy * 0.22f))
            pp.color = avecAlpha(rgb(170, 215, 150), (0.12f + 0.5f * dans) * q[2] * app)
            c.drawCircle(xx, yy, (0.5f + q[2]) * dp, pp)
        }

        // --- LES PLUMES ---
        val rafale = accelere(borne(rev / 0.8f))           // à l'ouverture, une rafale emporte tout
        val ech = 1f / echellePlume * k                    // passe des pixels de la plume aux pixels de l'écran
        plumeFloue?.let { pf ->
            for (l in loin) {
                val x = l[0] * w + sin(sec * 1.6f + l[3]) * w * 0.06f + rafale * w * 0.9f
                val y = l[1] * h + sec * h * 0.06f
                poser(c, pf, x, y, -cos(sec * 1.6f + l[3]) * 25f + 15f, cos(sec * 1.1f + l[3]), l[2] * ech, 0.22f * app)
            }
        }
        val phase = sec * 2.4f
        var x = w / 2f + sin(phase) * w * 0.17f
        var y = h * 0.20f + sec * h * 0.12f + abs(cos(phase)) * 16f * dp
        var angle = -cos(phase) * 30f + 12f
        x += rafale * w * 0.9f; y -= rafale * h * 0.12f; angle -= rafale * 120f
        o.halo(c, x, y, 75f * dp, 0.10f * app, rgb(90, 180, 60))
        plume?.let { poser(c, it, x, y, angle, cos(sec * 1.5f), 1.15f * ech, app) }
        // les fines lignes du vent
        if (rev > 0f) {
            val pv = o.lumiere()
            for (v in vent) {
                val t = borne(rev * 1.6f - v[2] * 0.5f)
                if (t <= 0f || t >= 1f) continue
                val xx = -w * 0.3f + t * w * 1.6f
                val yy = h * (0.15f + 0.7f * v[1])
                val lg = (60f + 120f * v[0]) * dp
                pv.shader = LinearGradient(xx - lg, 0f, xx, 0f, Color.TRANSPARENT, avecAlpha(rgb(150, 210, 140), 0.35f * sin(PIf * t)), Shader.TileMode.CLAMP)
                c.drawRect(xx - lg, yy - 1f * dp, xx, yy + 1f * dp, pv)
            }
        }
        o.vignette(c, w, h, 0.6f)
        o.revelationBalayageDouce(c, rev, 10f, null, 110f)    // le vent balaie l'écran : l'app apparaît
    }
}


// =====================================================================================
//  5. CHAMBRE : vue du dessus, caméra tournée. Lit défait, chat calico endormi sur un plaid,
//     table de chevet, téléphone qui vibre, une flamme s'allume sur son écran.
//     La nuit : lune et lampe. Le jour : aube dorée par la fenêtre.
//     Ouverture : la caméra plonge dans l'écran du téléphone.
// =====================================================================================
class DuoChambre : Scene() {
    // --- Le « monde » (la chambre) est plus grand que l'écran : la caméra n'en filme qu'une partie.
    //     Toutes les mesures sont en unités de l'écran modèle (540 de large). ---
    private val WW = 900f; private val HH = 1500f
    private fun px(f: Float) = 180f + 540f * f
    private fun py(f: Float) = 270f + 960f * f
    private val litD = px(0.60f); private val litG = px(-0.14f)
    private val tab = RectF(px(0.645f), py(0.035f), px(1.04f), py(0.335f))
    private val lampeX = px(0.905f); private val lampeY = py(0.105f)
    private val telX = px(0.775f); private val telY = py(0.215f)
    private val tasseX = px(0.915f); private val tasseY = py(0.275f)
    private val chatX = px(0.29f); private val chatY = py(0.635f)
    private val TW = 56f; private val TH = 116f
    private val ANGLE_CAM = 16f

    private val nuit = duoNuit()
    @Volatile private var pret = false
    private var decor: Bitmap? = null
    private var chat: Bitmap? = null
    private var ombreChat: Bitmap? = null
    private var ombreTel: Bitmap? = null
    private var echDecor = 1f; private var echChat = 1f

    private val ampoules: List<FloatArray>
    private val vapeur: List<FloatArray>
    private val salves = floatArrayOf(0.30f, 0.52f, 1.70f, 1.92f)
    private val zPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    // les couleurs du chat (déclarées avant « init », car le fil de préparation s'en sert)
    private val creme = rgb(232, 224, 214)
    private val orangeChat = rgb(214, 128, 66)
    private val noirChat = rgb(46, 38, 44)
    private val rose = rgb(220, 150, 160)

    init {
        val r = Random(17)
        ampoules = List(16) { n ->
            val u = (n + 0.5f) / 16f
            floatArrayOf(litD + 42f + 9f * sin(u * 8f), py(0.35f) + (HH + 20f - py(0.35f)) * u, (n % 3).toFloat(), r.nextFloat() * 6.28f)
        }
        vapeur = List(7) { floatArrayOf(r.nextFloat(), r.nextFloat() * 1.2f - 0.6f, 0.7f + 0.6f * r.nextFloat()) }
        // Le décor fixe et le chat sont dessinés une seule fois, dans un fil à part
        // (pendant ce temps, l'écran reste noir : c'est le début du fondu d'entrée).
        Thread {
            val (w, _) = duoEcran()
            val k = w / 540f
            construireDecor(k * 0.6f)
            construireChat(k)
            construireOmbreTel(k)
            pret = true
        }.start()
    }

    // ---------------------------------------------------------------- outils de dessin
    private fun pinceau(col: Int, a: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = avecAlpha(col, a) }
    private fun flou(col: Int, a: Float, r: Float) = pinceau(col, a).apply { maskFilter = BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL) }
    private fun lumiere(col: Int, a: Float) = pinceau(col, a).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private fun trait(col: Int, a: Float, l: Float) = pinceau(col, a).apply { style = Paint.Style.STROKE; strokeWidth = l; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private fun fois(col: Int, f: Float) = rgb((Color.red(col) * f).toInt().coerceIn(0, 255), (Color.green(col) * f).toInt().coerceIn(0, 255), (Color.blue(col) * f).toInt().coerceIn(0, 255))
    private fun poly(vararg p: Float) = Path().apply { moveTo(p[0], p[1]); var j = 2; while (j < p.size) { lineTo(p[j], p[j + 1]); j += 2 }; close() }

    // ---------------------------------------------------------------- LE DÉCOR FIXE
    private fun construireDecor(e: Float) {
        echDecor = e
        val bmp = Bitmap.createBitmap((WW * e).toInt(), (HH * e).toInt(), Bitmap.Config.ARGB_8888)
        val d = Canvas(bmp)
        d.scale(e, e)
        val r = Random(5)

        // le parquet sombre, lames verticales
        d.drawColor(rgb(15, 10, 20))
        var n = 0
        var x = 0f
        while (x < WW) {
            d.drawRect(x, 0f, x + 46f, HH, pinceau(fois(rgb(15, 10, 20), 1f + 0.08f * sin(n * 7.7f))))
            d.drawRect(x, 0f, x + 1.2f, HH, pinceau(rgb(6, 4, 9), 0.8f))
            var y = (n * 230f) % 600f
            while (y < HH) { d.drawRect(x, y, x + 46f, y + 1.5f, pinceau(rgb(6, 4, 9), 0.8f)); y += 600f }
            x += 46f; n++
        }
        // le tapis moelleux, avec de petites fibres
        val tapis = RectF(px(0.70f), py(0.48f), px(1.35f), py(0.88f))
        d.drawOval(tapis, flou(rgb(48, 30, 62), 1f, 4f))
        val fibre = pinceau(Color.WHITE)
        for (j in 0 until 2500) {
            val ang = r.nextFloat() * 6.28f; val rr = sqrt(r.nextFloat())
            val fx = tapis.centerX() + cos(ang) * rr * tapis.width() / 2f; val fy = tapis.centerY() + sin(ang) * rr * tapis.height() / 2f
            fibre.color = if (r.nextBoolean()) avecAlpha(Color.WHITE, 0.04f) else avecAlpha(Color.BLACK, 0.12f)
            d.drawCircle(fx, fy, 1.2f, fibre)
        }
        // ombres du lit et de la table sur le sol
        d.drawRect(litD - 10f, py(0.30f), litD + 30f, HH, flou(Color.BLACK, 0.75f, 10f))
        d.drawRect(litG - 50f, 0f, litG + 10f, HH, flou(Color.BLACK, 0.75f, 10f))
        d.drawRoundRect(RectF(tab.left - 4f, tab.top + 12f, tab.right + 10f, tab.bottom + 22f), 10f, 10f, flou(Color.BLACK, 0.8f, 12f))
        // le mur, tout en haut (vu de dessus : une bande sombre)
        d.drawRect(0f, 0f, WW, py(-0.03f), pinceau(rgb(9, 6, 12)))
        d.drawRect(0f, py(-0.03f) - 1f, WW, py(-0.03f) + 1f, lumiere(rgb(140, 90, 170), 0.10f))

        // --- le lit : cadre, tête de lit, drap ---
        d.drawRect(litG - 18f, py(-0.03f), litD + 18f, HH, pinceau(rgb(30, 19, 36)))
        d.drawRect(litD + 16f, py(0.055f), litD + 18f, HH, lumiere(rgb(140, 90, 170), 0.12f))
        d.drawRect(litG - 18f, py(-0.03f), litD + 18f, py(0.055f), pinceau(rgb(36, 23, 44)))
        d.drawRect(litG - 18f, py(0.055f) - 1f, litD + 18f, py(0.055f) + 1f, lumiere(rgb(140, 90, 170), 0.15f))
        d.drawRoundRect(RectF(litG, py(0.062f), litD, HH + 40f), 18f, 18f, pinceau(rgb(40, 32, 52)))

        // --- deux gros oreillers : coins qui pointent, côtés rentrés, creux de la tête ---
        for ((ox0, ox1) in listOf(Pair(litG + 14f, px(0.255f)), Pair(px(0.27f), litD - 14f))) {
            val oy0 = py(0.078f); val oy1 = py(0.245f)
            val ox = (ox0 + ox1) / 2f; val oy = (oy0 + oy1) / 2f
            val ax = (ox1 - ox0) / 2f; val ay = (oy1 - oy0) / 2f
            val forme = Path()
            for (j in 0 until 120) {
                val th = j / 120f * 2f * PIf
                val c_ = cos(th); val s_ = sin(th)
                val f = 1f - 0.13f * cos(4f * th)
                val xx = ox + ax * f * Math.copySign(abs(c_).pow(0.55f), c_)
                val yy = oy + ay * f * Math.copySign(abs(s_).pow(0.55f), s_)
                if (j == 0) forme.moveTo(xx, yy) else forme.lineTo(xx, yy)
            }
            forme.close()
            d.drawPath(forme, flou(Color.BLACK, 0.55f, 9f))
            val base = rgb(108, 90, 136)
            val p = pinceau(Color.WHITE)
            p.shader = RadialGradient(ox + ax * 0.15f, oy - ay * 0.2f, max(ax, ay) * 1.15f,
                intArrayOf(fois(base, 1.04f), fois(base, 0.90f), fois(base, 0.70f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            d.drawPath(forme, p)
            d.save(); d.clipPath(forme)
            val creux = pinceau(Color.BLACK)
            creux.shader = RadialGradient(ox + 10f, oy - 4f, 42f, avecAlpha(Color.BLACK, 0.20f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            d.drawCircle(ox + 10f, oy - 4f, 42f, creux)
            // quelques plis qui partent des coins
            for ((cx_, cy_) in listOf(Pair(ox0, oy0), Pair(ox1, oy0), Pair(ox0, oy1), Pair(ox1, oy1))) {
                for (j in 0 until 4) {
                    val ang = atan2(oy - cy_, ox - cx_) + (j - 1.5f) * 0.25f
                    val l = 20f + 12f * r.nextFloat()
                    d.drawLine(cx_ + cos(ang) * 6f, cy_ + sin(ang) * 6f, cx_ + cos(ang) * l, cy_ + sin(ang) * l, trait(Color.BLACK, 0.12f, 2f))
                }
            }
            d.restore()
        }

        // --- la couette matelassée ---
        val couette = poly(litG - 4f, py(0.295f), litD - 4f, py(0.28f), litD + 8f, py(0.42f), litD + 2f, py(0.75f),
            litD + 10f, HH + 20f, litG - 10f, HH + 20f, litG - 14f, py(0.6f))
        d.drawPath(couette, flou(Color.BLACK, 0.6f, 9f))
        d.drawPath(couette, pinceau(rgb(78, 36, 106)))
        d.save(); d.clipPath(couette)
        // grands plis doux : bandes claires et sombres, floues
        for (j in 0 until 9) {
            val y0 = py(0.32f) + j * 90f
            val pli = Path().apply {
                moveTo(litG - 30f, y0)
                cubicTo(px(0.1f), y0 + 40f * sin(j * 1.7f), px(0.35f), y0 - 30f * cos(j * 2.3f), litD + 30f, y0 + 50f)
            }
            d.drawPath(pli, trait(if (j % 2 == 0) rgb(130, 70, 160) else rgb(30, 10, 50), if (j % 2 == 0) 0.18f else 0.28f, 26f).apply { maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL) })
        }
        // coutures du matelassage : un creux sombre + un reflet clair à côté (effet gonflé)
        var cy = py(0.36f)
        while (cy < HH) {
            d.drawLine(litG, cy, litD, cy, trait(rgb(30, 10, 45), 0.35f, 2f))
            d.drawLine(litG, cy + 4f, litD, cy + 4f, trait(rgb(150, 90, 180), 0.12f, 3f).apply { maskFilter = BlurMaskFilter(2f, BlurMaskFilter.Blur.NORMAL) })
            cy += 70f
        }
        var cx = litG - 100f
        while (cx < litD + 100f) {
            d.drawLine(cx, py(0.33f), cx - 0.08f * (HH - py(0.33f)), HH, trait(rgb(30, 10, 45), 0.35f, 2f))
            d.drawLine(cx + 4f, py(0.33f), cx + 4f - 0.08f * (HH - py(0.33f)), HH, trait(rgb(150, 90, 180), 0.12f, 3f).apply { maskFilter = BlurMaskFilter(2f, BlurMaskFilter.Blur.NORMAL) })
            cx += 70f
        }
        // ombre sous le rabat
        val ombreRabat = pinceau(Color.BLACK)
        ombreRabat.shader = LinearGradient(0f, py(0.335f), 0f, py(0.375f), avecAlpha(rgb(20, 10, 28), 0.45f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        d.drawRect(litG - 20f, py(0.30f), litD + 20f, py(0.38f), ombreRabat)
        d.restore()
        // le rabat (le haut replié) avec deux fins passepoils
        val rabat = poly(litG - 4f, py(0.295f), litD - 4f, py(0.28f), litD - 2f, py(0.335f), litG - 6f, py(0.355f))
        d.drawPath(rabat, pinceau(rgb(98, 50, 128)))
        for (kk in floatArrayOf(0.30f, 0.322f)) {
            d.drawLine(litG, py(kk) + 0.018f * 960f, litD - 4f, py(kk), trait(rgb(220, 190, 240), 0.14f, 2.2f).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) })
        }

        // --- le plaid en tricot, posé en travers : le chat dort dessus ---
        d.save()
        d.translate(chatX - 8f, chatY + 14f)
        d.rotate(12f)
        d.drawRect(-119f + 6f, -95f + 8f, 119f + 6f, 95f + 8f, flou(Color.BLACK, 0.6f, 7f))
        val laine = rgb(112, 68, 96)
        d.drawRect(-119f, -95f, 119f, 95f, pinceau(fois(laine, 0.78f)))
        for (col in 0 until 11) {
            val xc = -119f + col * 22f + 11f
            if (col % 2 == 0) {
                d.drawLine(xc, -95f, xc, 95f, trait(fois(laine, 1.0f), 0.9f, 9f))     // côte droite
            } else {
                val torsade = Path().apply {                                        // torsade
                    moveTo(xc, -95f)
                    var yy = -95f
                    while (yy <= 95f) { lineTo(xc + 4f * sin(yy / 6f), yy); yy += 2f }
                }
                d.drawPath(torsade, trait(fois(laine, 1.0f), 0.9f, 8f))
            }
            d.drawLine(xc + 11f, -95f, xc + 11f, 95f, trait(fois(laine, 0.6f), 0.6f, 1.5f))
        }
        var yy = -93f
        while (yy < 95f) {                                                          // les franges
            d.drawLine(-125f, yy, -119f, yy, trait(rgb(120, 74, 98), 0.9f, 1.2f))
            d.drawLine(119f, yy, 125f, yy, trait(rgb(120, 74, 98), 0.9f, 1.2f))
            yy += 3f
        }
        d.restore()

        // --- le fil de la guirlande, posé au sol le long du lit ---
        val fil = Path()
        for (j in 0..90) {
            val u = j / 90f
            val fx = litD + 42f + 9f * sin(u * 8f); val fy = py(0.35f) + (HH + 20f - py(0.35f)) * u
            if (j == 0) fil.moveTo(fx, fy) else fil.lineTo(fx, fy)
        }
        d.drawPath(fil, trait(rgb(10, 8, 12), 1f, 2f))

        // --- la table de chevet ---
        d.drawRoundRect(tab, 10f, 10f, pinceau(rgb(44, 30, 54)))
        d.save(); d.clipRect(tab)
        var gy = tab.top
        while (gy < tab.bottom) {                                                   // veines du bois
            val v = Path().apply { moveTo(tab.left, gy); var xx2 = tab.left; while (xx2 <= tab.right) { lineTo(xx2, gy + 3f * sin(xx2 * 0.04f + gy)); xx2 += 6f } }
            d.drawPath(v, trait(if (((gy / 6f).toInt()) % 2 == 0) Color.WHITE else Color.BLACK, 0.05f, 1.5f))
            gy += 6f
        }
        d.restore()
        d.drawRoundRect(RectF(tab.left + 1.5f, tab.top + 1.5f, tab.right - 1.5f, tab.bottom - 1.5f), 9f, 9f, trait(rgb(170, 120, 200), 0.16f, 3f).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) })

        // --- le livre sous le téléphone (ombre à l'opposé de la lampe) ---
        val (odx, ody) = direction(telX, telY, 10f)
        d.save(); d.translate(telX - 6f + odx, telY + 4f + ody); d.rotate(7f)
        d.drawRoundRect(RectF(-58f, -84f, 58f, 84f), 4f, 4f, flou(Color.BLACK, 0.6f, 6f))
        d.restore()
        d.save(); d.translate(telX - 6f, telY + 4f); d.rotate(7f)
        d.drawRoundRect(RectF(-58f, -84f, 58f, 84f), 4f, 4f, pinceau(rgb(56, 30, 66)))
        d.drawRect(-58f, -84f, -48f, 84f, pinceau(rgb(42, 22, 50)))
        d.drawRect(54f, -80f, 58f, 80f, pinceau(rgb(78, 70, 70)))
        d.restore()
        // --- le câble de charge : descend du téléphone et tombe de la table ---
        val cable = Path()
        for (j in 0..50) {
            val u = j / 50f
            val fx = telX - 4f - 22f * sin(u * 2.6f) * u; val fy = telY + TH / 2f + u * (py(0.47f) - telY - TH / 2f)
            if (j == 0) cable.moveTo(fx, fy) else cable.lineTo(fx, fy)
        }
        d.save(); d.translate(4f, 0f); d.drawPath(cable, trait(Color.BLACK, 0.5f, 3f).apply { maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL) }); d.restore()
        d.drawPath(cable, trait(rgb(52, 48, 60), 1f, 3f))
        // --- la tasse ---
        val (tdx, tdy) = direction(tasseX, tasseY, 9f)
        d.drawCircle(tasseX + tdx, tasseY + tdy, 23f, flou(Color.BLACK, 0.6f, 5f))
        d.drawRoundRect(RectF(tasseX + 16f, tasseY - 6f, tasseX + 33f, tasseY + 6f), 5f, 5f, pinceau(rgb(78, 66, 96)))
        d.drawCircle(tasseX, tasseY, 23f, pinceau(rgb(84, 72, 104)))
        d.drawCircle(tasseX, tasseY, 22f, trait(rgb(220, 200, 240), 0.25f, 2f).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) })
        // --- l'abat-jour, vu de dessus ---
        d.drawCircle(lampeX + 6f, lampeY + 8f, 46f, flou(Color.BLACK, 0.5f, 7f))
        val abat = pinceau(Color.WHITE)
        abat.shader = RadialGradient(lampeX, lampeY, 46f, intArrayOf(fois(rgb(76, 40, 98), 1.05f), fois(rgb(76, 40, 98), 1.05f), fois(rgb(76, 40, 98), 0.70f)),
            floatArrayOf(0f, 0.56f, 1f), Shader.TileMode.CLAMP)
        d.drawCircle(lampeX, lampeY, 46f, abat)
        for (j in 0 until 22) {                                                     // les plis du tissu
            val ang = j / 22f * 2f * PIf
            d.drawLine(lampeX + cos(ang) * 26f, lampeY + sin(ang) * 26f, lampeX + cos(ang) * 46f, lampeY + sin(ang) * 46f,
                trait(if (j % 2 == 0) Color.WHITE else Color.BLACK, 0.07f, 3f))
        }
        d.drawCircle(lampeX, lampeY, 44.5f, trait(rgb(30, 16, 40), 1f, 3f))

        // --- la lumière de la fenêtre (hors champ, à droite) : une bande en biais, avec les croisillons ---
        fun haut(xx: Float) = py(0.44f) + (px(1.1f) - xx) * 0.18f
        fun bas(xx: Float) = py(0.70f) + (px(1.1f) - xx) * 0.24f
        val bande = poly(WW + 20f, haut(WW + 20f), WW + 20f, bas(WW + 20f), -20f, bas(-20f), -20f, haut(-20f))
        fun milieu(xx: Float, dec: Float) = (haut(xx) + bas(xx)) / 2f + dec * 0.025f * (bas(xx) - haut(xx))
        val croix = poly(-20f, milieu(-20f, -1f), WW + 20f, milieu(WW + 20f, -1f), WW + 20f, milieu(WW + 20f, 1f), -20f, milieu(-20f, 1f))
        for (xc in floatArrayOf(px(0.62f), px(0.12f))) {
            val y0 = 0f; val y1 = HH
            croix.op(poly(xc + 0.35f * py(0.6f) - 5f, y0, xc + 0.35f * py(0.6f) + 5f, y0,
                xc - 0.35f * (y1 - py(0.6f)) + 5f, y1, xc - 0.35f * (y1 - py(0.6f)) - 5f, y1), Path.Op.UNION)
        }
        bande.op(croix, Path.Op.DIFFERENCE)
        if (nuit) {
            d.drawPath(bande, lumiere(rgb(110, 125, 210), 0.10f).apply { maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL) })
        } else {
            d.drawRect(0f, 0f, WW, HH, lumiere(rgb(14, 9, 4), 1f))                  // tout un peu plus chaud et clair
            d.drawPath(bande, lumiere(rgb(255, 170, 110), 0.16f).apply { maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL) })
            d.drawPath(bande, lumiere(rgb(255, 150, 90), 0.05f).apply { maskFilter = BlurMaskFilter(25f, BlurMaskFilter.Blur.NORMAL) })
        }
        decor = bmp
    }

    // Décalage d'une ombre : à l'opposé de la lampe.
    private fun direction(x: Float, y: Float, longueur: Float): Pair<Float, Float> {
        val dx = x - lampeX; val dy = y - lampeY
        val n = hypot(dx, dy).coerceAtLeast(1f)
        return Pair(dx / n * longueur, dy / n * longueur)
    }

    // ---------------------------------------------------------------- LE CHAT
    // Un chat calico (blanc crème, taches orange rayées et noires) roulé en boule, qui dort.
    // On le dessine une fois dans une image (230 × 230 unités), son oreille droite est dessinée à part
    // à chaque image car elle bouge quand le téléphone vibre.

    private fun queuePoint(u: Float): FloatArray {
        val a = (20f + 155f * u) / 180f * PIf
        val r = 64f - 6f * u
        return floatArrayOf(104f + cos(a) * r * 1.05f, 128f + sin(a) * r * 0.88f, 13f + (9f - 13f) * u)
    }

    private fun construireChat(k: Float) {
        echChat = k
        val taille = (230 * k).toInt()
        val bmp = Bitmap.createBitmap(taille, taille, Bitmap.Config.ARGB_8888)
        val d = Canvas(bmp)
        d.scale(k, k)
        val surDessus = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)     // « ne peins que là où il y a déjà du chat »
        val corps = RectF(36f, 72f, 172f, 182f)
        val tete = RectF(118f, 46f, 196f, 118f)

        // 1. le corps, crème
        d.drawOval(corps, pinceau(creme))
        // 2. les taches orange rayées, puis la tache noire du dos (seulement sur le corps)
        val taches = Bitmap.createBitmap(taille, taille, Bitmap.Config.ARGB_8888)
        val t = Canvas(taches); t.scale(k, k)
        t.drawOval(RectF(30f, 70f, 110f, 150f), pinceau(orangeChat).apply { maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL) })
        t.drawOval(RectF(70f, 140f, 130f, 190f), pinceau(orangeChat).apply { maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL) })
        for (j in 0 until 14) {                                       // les rayures, en éventail depuis le centre
            val a = (PIf / 2f + 2f * PIf * j) / 14f
            t.drawLine(104f, 128f, 104f + cos(a) * 130f, 128f + sin(a) * 130f, trait(Color.BLACK, 0.18f, 5f).apply { xfermode = surDessus })
        }
        d.drawBitmap(taches, 0f, 0f, Paint().apply { xfermode = surDessus })
        d.drawOval(RectF(60f, 82f, 104f, 120f), pinceau(noirChat).apply { xfermode = surDessus; maskFilter = BlurMaskFilter(2f, BlurMaskFilter.Blur.NORMAL) })
        // 3. le volume du corps : clair en haut à droite, plus sombre sur les bords
        val vol = pinceau(Color.WHITE).apply { xfermode = surDessus }
        vol.shader = RadialGradient(124f, 108f, 90f, intArrayOf(avecAlpha(Color.WHITE, 0.12f), Color.TRANSPARENT, avecAlpha(Color.BLACK, 0.30f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        d.drawOval(corps, vol)
        // 4. la queue enroulée autour du corps (orange, bout blanc), avec son ombre sur le corps
        d.save(); d.translate(-3f, 5f)
        for (j in 0 until 60) { val q = queuePoint(j / 59f); d.drawCircle(q[0], q[1], q[2], pinceau(Color.BLACK, 0.12f).apply { xfermode = surDessus; maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL) }) }
        d.restore()
        for (j in 0 until 60) {
            val u = j / 59f; val q = queuePoint(u)
            d.drawCircle(q[0], q[1], q[2], pinceau(if (u > 0.84f) creme else orangeChat))
        }
        for (j in 1 until 9) {                                         // rayures sur la queue
            val u = j / 10f; val q = queuePoint(u); val q2 = queuePoint(u + 0.01f)
            val nx = -(q2[1] - q[1]); val ny = q2[0] - q[0]; val nn = hypot(nx, ny).coerceAtLeast(0.01f)
            d.drawLine(q[0] - nx / nn * q[2] * 0.8f, q[1] - ny / nn * q[2] * 0.8f, q[0] + nx / nn * q[2] * 0.8f, q[1] + ny / nn * q[2] * 0.8f,
                trait(fois(orangeChat, 0.7f), 0.6f, 2.5f))
        }
        // 5. l'ombre de la tête sur le corps, puis la tête (calotte noire), son volume
        d.drawOval(RectF(tete.left - 5f, tete.top + 6f, tete.right - 5f, tete.bottom + 6f), pinceau(Color.BLACK, 0.30f).apply { xfermode = surDessus; maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL) })
        // oreille gauche (fixe) : noire, intérieur rose
        d.drawPath(poly(128f, 62f, 124f, 30f, 152f, 52f), pinceau(noirChat))
        d.drawPath(poly(131f, 58f, 128f, 38f, 146f, 52f), pinceau(rose))
        d.drawOval(tete, pinceau(creme))
        d.save()
        d.clipPath(Path().apply { addOval(tete, Path.Direction.CW) })
        d.drawOval(RectF(120f, 34f, 172f, 80f), pinceau(noirChat).apply { maskFilter = BlurMaskFilter(2f, BlurMaskFilter.Blur.NORMAL) })
        val volT = pinceau(Color.WHITE)
        volT.shader = RadialGradient(166f, 70f, 46f, intArrayOf(avecAlpha(Color.WHITE, 0.10f), Color.TRANSPARENT, avecAlpha(Color.BLACK, 0.25f)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        d.drawOval(tete, volT)
        d.restore()
        // 6. les pattes avant repliées sous le menton, avec leurs coussinets roses
        d.drawOval(RectF(112f, 106f, 136f, 124f), pinceau(creme))
        d.drawOval(RectF(134f, 112f, 158f, 130f), pinceau(creme))
        d.drawOval(RectF(121f, 118f, 127f, 122f), pinceau(rose))
        d.drawOval(RectF(143f, 124f, 149f, 128f), pinceau(rose))
        // 7. le visage : yeux fermés, joues roses, petit nez, bouche en « w », moustaches
        val oeil = trait(rgb(50, 38, 46), 1f, 2.2f)
        d.drawArc(RectF(134f, 74f, 150f, 86f), 20f, 140f, false, oeil)
        d.drawArc(RectF(162f, 70f, 178f, 82f), 20f, 140f, false, oeil)
        for ((jx, jy) in listOf(Pair(136f, 92f), Pair(178f, 88f))) {
            val joue = pinceau(Color.WHITE)
            joue.shader = RadialGradient(jx, jy, 10f, avecAlpha(rgb(240, 140, 150), 0.45f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            d.drawOval(RectF(jx - 10f, jy - 7f, jx + 10f, jy + 7f), joue)
        }
        d.drawPath(poly(152f, 90f, 160f, 89f, 156f, 95f), pinceau(rgb(226, 130, 140)))
        val bouche = trait(rgb(70, 50, 60), 1f, 1.4f)
        d.drawArc(RectF(150f, 92f, 157f, 99f), 10f, 160f, false, bouche)
        d.drawArc(RectF(156f, 92f, 163f, 99f), 10f, 160f, false, bouche)
        for ((sg, bx, by) in listOf(Triple(-1f, 140f, 94f), Triple(1f, 172f, 91f))) {
            for (kk in -1..1) d.drawLine(bx, by, bx + sg * 18f, by + kk * 4f - 2f, trait(rgb(235, 228, 220), 0.6f, 0.8f))
        }
        chat = bmp
        // l'ombre douce du chat sur le plaid
        val ombre = Bitmap.createBitmap(taille, taille, Bitmap.Config.ARGB_8888)
        Canvas(ombre).drawBitmap(bmp.extractAlpha(), 0f, 0f, Paint().apply { color = Color.argb(140, 4, 2, 6); maskFilter = BlurMaskFilter(6f * k, BlurMaskFilter.Blur.NORMAL) })
        ombreChat = ombre
    }

    private fun construireOmbreTel(k: Float) {
        val b = Bitmap.createBitmap(((TW + 40f) * k).toInt(), ((TH + 40f) * k).toInt(), Bitmap.Config.ARGB_8888)
        val d = Canvas(b); d.scale(k, k)
        d.drawRoundRect(RectF(20f, 20f, 20f + TW, 20f + TH), 10f, 10f, flou(rgb(4, 2, 6), 0.7f, 6f))
        ombreTel = b
    }

    // ---------------------------------------------------------------- CHAQUE IMAGE
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val k = w / 540f
        val decorImg = decor
        if (!pret || decorImg == null) {                     // le décor se prépare encore : écran noir
            c.drawColor(Color.BLACK)
            if (rev > 0f) c.drawRect(0f, 0f, w, h, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT); alpha = (rev * 255).toInt() })
            return
        }
        val app = ralentit(intro)
        val resp = 0.92f + 0.08f * sin(sec * 1.2f)

        // --- la vibration du téléphone (deux salves : bzz bzz) ---
        var vib = 0f; var deja = 0; var dernier = -1f
        for (t0 in salves) {
            if (sec >= t0 && sec < t0 + 0.15f) vib = sin((sec - t0) / 0.15f * PIf)
            if (sec >= t0) dernier = t0
            if (sec >= t0 + 0.15f) deja++
        }
        val tx = telX + sin(sec * 170f) * 1.6f * vib + deja * 0.6f
        val ty = telY + cos(sec * 150f) * 1.0f * vib - deja * 0.4f

        // --- LA CAMÉRA : tournée de 16°, elle glisse un peu vers le téléphone pendant l'attente ;
        //     à l'ouverture, elle se redresse et plonge dans l'écran (le téléphone ne sort jamais du cadre).
        //     Tout est calculé en unités du modèle ; l'écran fait 540 × (h / k) unités. ---
        val ow = 540f; val oh = h / k
        val plonge = doux(rev)
        val pousse = doux(sec / 2.6f)
        val cx0 = px(0.50f) + (tx - px(0.50f)) * 0.12f * pousse
        val cy0 = py(0.42f) + (ty - py(0.42f)) * 0.12f * pousse
        val z0 = 1f + 0.06f * pousse
        val a0 = ANGLE_CAM / 180f * PIf
        val rx = (tx - cx0) * z0; val ry = (ty - cy0) * z0
        val ex0 = ow / 2f + cos(a0) * rx + sin(a0) * ry           // où est le téléphone à l'écran
        val ey0 = oh / 2f - sin(a0) * rx + cos(a0) * ry
        val z = z0 * (1f + 11.5f * plonge)
        val ang = ANGLE_CAM * (1f - plonge)
        val a = ang / 180f * PIf
        val ex = ex0 + (ow / 2f - ex0) * plonge; val ey = ey0 + (oh / 2f - ey0) * plonge
        val ux = (ow / 2f - ex) / z; val uy = (oh / 2f - ey) / z
        val camX = tx + cos(a) * ux - sin(a) * uy
        val camY = ty + sin(a) * ux + cos(a) * uy
        fun camera() {                                         // applique la caméra au pinceau du Canvas
            c.scale(k, k)
            c.translate(ow / 2f, oh / 2f)
            c.scale(z, z)
            c.rotate(-ang)
            c.translate(-camX, -camY)
        }

        c.drawColor(rgb(9, 6, 12))
        c.save()
        camera()
        // le décor fixe (préparé à l'avance)
        c.drawBitmap(decorImg, null, RectF(0f, 0f, WW, HH), Paint(Paint.FILTER_BITMAP_FLAG))

        // --- la lampe : allumée la nuit, éteinte le jour ---
        if (nuit) {
            o.tache(c, lampeX, lampeY, 200f, 200f, 0.16f * resp, rgb(190, 100, 220))
            o.tache(c, lampeX - 150f, lampeY + 60f, 240f, 170f, 0.05f * resp, rgb(150, 70, 200))
            o.halo(c, lampeX, lampeY, 16f, 0.55f * resp, rgb(240, 180, 230))
            c.drawCircle(lampeX, lampeY, 11f, pinceau(rgb(250, 215, 235), 0.85f))
        } else {
            c.drawCircle(lampeX, lampeY, 26f, pinceau(rgb(40, 24, 50), 0.9f))
            c.drawCircle(lampeX, lampeY, 10f, pinceau(rgb(150, 135, 140), 0.8f))
        }
        for (j in 0 until 3) {                                // le petit croisillon de l'abat-jour
            val an = 0.3f + j * 2.094f
            c.drawLine(lampeX, lampeY, lampeX + cos(an) * 26f, lampeY + sin(an) * 26f, trait(rgb(40, 30, 50), 0.8f, 1.8f))
        }
        // --- la guirlande au sol (éteinte le jour) ---
        val couleursAmp = intArrayOf(rgb(255, 150, 200), rgb(255, 185, 120), rgb(200, 140, 255))
        for (b in ampoules) {
            val col = couleursAmp[b[2].toInt()]
            val sc = 0.75f + 0.25f * sin(sec * 2.0f + b[3])
            if (nuit) {
                o.halo(c, b[0], b[1], 12f, 0.17f * sc, col)
                c.drawCircle(b[0], b[1], 2.8f, pinceau(col, 0.85f * sc))
            } else {
                c.drawCircle(b[0], b[1], 2.6f, pinceau(fois(col, 0.4f), 0.8f))
            }
        }

        // --- le chat : il respire, son oreille tressaille quand le téléphone vibre, des « z » s'envolent ---
        val chatImg = chat
        if (chatImg != null) {
            val souffle = 1f + 0.025f * sin(sec * 2.1f)
            val teinte = if (nuit) LightingColorFilter(rgb(168, 163, 204), 0) else LightingColorFilter(rgb(235, 199, 179), 0)
            ombreChat?.let { om ->
                c.save(); c.translate(chatX - 6f, chatY + 7f); c.scale(1f / echChat, souffle / echChat)
                c.drawBitmap(om, -om.width / 2f, -om.height / 2f, Paint(Paint.FILTER_BITMAP_FLAG)); c.restore()
            }
            c.save()
            c.translate(chatX, chatY)
            c.scale(1f, souffle)
            c.translate(-115f, -115f)                          // le chat est dessiné dans un carré de 230 unités
            c.save(); c.scale(1f / echChat, 1f / echChat)
            c.drawBitmap(chatImg, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = teinte })
            c.restore()
            // l'oreille droite, qui bouge
            var oreille = 0f
            for (t0 in floatArrayOf(0.40f, 1.80f)) {
                val u = (sec - t0) / 0.25f
                if (u > 0f && u < 1f) oreille = max(oreille, sin(u * PIf) * (if (u < 0.5f) 1f else 0.6f))
            }
            c.save()
            c.rotate(-22f * oreille, 180f, 58f)
            c.drawPath(poly(170f, 52f, 196f, 34f, 190f, 70f), pinceau(creme).apply { colorFilter = teinte })
            c.drawPath(poly(173f, 52f, 191f, 40f, 186f, 64f), pinceau(rose).apply { colorFilter = teinte })
            c.restore()
            c.restore()
            for (j in 0 until 2) {
                val u = modulo(sec * 0.45f + j * 0.5f, 1f)
                zPaint.textSize = 9f + 7f * u
                zPaint.color = avecAlpha(rgb(215, 200, 245), sin(PIf * u) * 0.55f)
                c.drawText("z", chatX + 56f + 22f * u + 6f * sin(u * 6f), chatY - 60f - 46f * u, zPaint)
            }
        }

        // --- les ondes de la vibration sur la table ---
        if (dernier >= 0f) {
            c.save(); c.clipRect(tab)
            val age = sec - dernier
            for (j in 0 until 2) {
                val ag = age - j * 0.07f
                if (ag > 0f && ag < 0.45f) {
                    val r = 68f + ag * 150f
                    c.drawOval(RectF(tx - r, ty - r / 0.6f, tx + r, ty + r / 0.6f), trait(rgb(200, 170, 235), (1f - ag / 0.45f) * 0.16f, 2.8f).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) })
                }
            }
            c.restore()
        }
        // --- le thé : il ride quand ça vibre, et de la vapeur monte ---
        c.drawCircle(tasseX, tasseY, 17f, pinceau(rgb(34, 18, 16)))
        val agite = if (dernier < 0f) 0f else max(0f, 1f - (sec - dernier) / 0.8f)
        if (agite > 0f) {
            for (j in 0 until 5) {
                val rr = modulo((2f * PIf * j + sec * 30f) / 1.3f, 17f)
                c.drawCircle(tasseX, tasseY, rr, trait(rgb(60, 34, 30), agite * 0.8f, 1.5f))
            }
        }
        o.halo(c, tasseX - 5f, tasseY - 6f, 3f, 0.25f, rgb(200, 150, 220))
        for (v in vapeur) {
            val u = modulo(sec * 0.55f * v[2] + v[0], 1f)
            val vx = tasseX + v[1] * 26f * u + sin(sec * 2f + v[0] * 6f) * 5f * u - 12f * u
            val vy = tasseY - 10f - 30f * u
            o.halo(c, vx, vy, (6f + 22f * u) * 0.8f, sin(PIf * u) * (if (nuit) 0.07f else 0.09f), rgb(210, 200, 230))
        }

        // --- le téléphone : ombre, lueur de la flamme, puis le téléphone lui-même ---
        val lum = doux((sec - 0.34f) / 0.25f)
        val flamme = borne((sec - 0.42f) / 0.35f)
        val envahit = doux(rev / 0.55f)
        val vive = flamme * (0.85f + 0.15f * sin(sec * 7f))
        val (sdx, sdy) = if (nuit) direction(telX, telY, 8f) else Pair(-6f, 5f)
        ombreTel?.let { om ->
            c.save(); c.translate(tx + sdx, ty + sdy); c.scale(1f / k, 1f / k)
            c.drawBitmap(om, -om.width / 2f, -om.height / 2f, Paint(Paint.FILTER_BITMAP_FLAG)); c.restore()
        }
        o.tache(c, tx, ty, 95f, 95f, 0.12f * vive, rgb(255, 130, 50))
        o.halo(c, tasseX - 20f, tasseY - 6f, 13f, 0.10f * vive, rgb(255, 140, 60))
        dessinerTelephone(c, o, tx, ty, lum, flamme, envahit, sec)
        c.restore()

        // --- finitions : vignette, fondu d'entrée, et l'ouverture ---
        o.vignette(c, w, h, 0.6f)
        if (app < 1f) c.drawRect(0f, 0f, w, h, pinceau(Color.BLACK, 1f - app))
        if (rev > 0f) {
            val gomme = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
            // l'app apparaît d'abord DANS l'écran du téléphone (à travers la flamme)…
            gomme.alpha = (doux((rev - 0.3f) / 0.35f) * 255).toInt()
            c.save(); camera()
            c.drawRoundRect(RectF(tx - TW / 2f + 3f, ty - TH / 2f + 3f, tx + TW / 2f - 3f, ty + TH / 2f - 3f), 8f, 8f, gomme)
            c.restore()
            // …puis, à la fin, plus rien ne reste
            gomme.alpha = (doux((rev - 0.65f) / 0.3f) * 255).toInt()
            c.drawRect(0f, 0f, w, h, gomme)
        }
    }

    private fun dessinerTelephone(c: Canvas, o: Outils, tx: Float, ty: Float, lum: Float, flamme: Float, envahit: Float, sec: Float) {
        val corps = RectF(tx - TW / 2f, ty - TH / 2f, tx + TW / 2f, ty + TH / 2f)
        c.drawRoundRect(corps, 10f, 10f, pinceau(rgb(16, 14, 22)))
        c.drawRoundRect(corps, 10f, 10f, trait(rgb(82, 66, 108), 1f, 1.2f))
        val ecran = RectF(corps.left + 3f, corps.top + 3f, corps.right - 3f, corps.bottom - 3f)
        val fond = pinceau(Color.WHITE)
        val off = rgb(5, 4, 8)
        fond.shader = LinearGradient(0f, ecran.top, 0f, ecran.bottom,
            mixCouleur(off, rgb(24, 12, 28), lum), mixCouleur(off, rgb(10, 6, 14), lum), Shader.TileMode.CLAMP)
        c.drawRoundRect(ecran, 8f, 8f, fond)
        c.save()
        c.clipPath(Path().apply { addRoundRect(ecran, 8f, 8f, Path.Direction.CW) })
        if (flamme > 0f) {
            // la flamme sur l'écran ; à l'ouverture, elle grandit jusqu'à remplir l'écran (sans éblouir)
            val taille = ralentit(flamme) * (1f + 3.2f * envahit)
            val base = ecran.top + ecran.height() * (0.58f + 0.35f * envahit)
            val attenue = 1f - 0.35f * envahit
            o.tache(c, tx, base - 16f * taille, 16f * taille, 15f * taille, 0.31f * min(1f, taille), rgb(255, 120, 40))
            val couches = arrayOf(floatArrayOf(1.0f, 235f, 95f, 30f, 210f), floatArrayOf(0.70f, 255f, 150f, 45f, 215f), floatArrayOf(0.42f, 255f, 205f, 120f, 190f))
            for ((n, cc) in couches.withIndex()) {
                val haut = 30f * cc[0] * taille * (1f + 0.06f * sin(sec * (6f + 2f * n) + n))
                val larg = 9.5f * cc[0] * (0.6f + 0.4f * min(1f, taille)) * (1f + 1.6f * envahit)
                val sway = larg * 0.3f * sin(sec * (5f + 1.5f * n) + n * 1.3f)
                c.drawPath(duoGoutte(tx, base, max(haut, larg * 1.3f), larg, sway),
                    pinceau(rgb(cc[1].toInt(), cc[2].toInt(), cc[3].toInt()), cc[4] / 255f * attenue))
            }
        }
        if (nuit) o.tache(c, ecran.right - 8f, ecran.top + 4f, 14f, 10f, 0.12f * (1f - 0.6f * lum), rgb(230, 170, 240))   // reflet de la lampe
        c.restore()
        c.drawCircle(tx, ecran.top + 3.5f, 1.7f, pinceau(rgb(2, 2, 4)))       // la caméra du téléphone
    }
}
