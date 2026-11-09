package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ===================== STRIKE : trois animations, tirées au hasard =====================
//  0. « DCA »        : on achète la même somme chaque semaine jusqu'à avoir 1 bitcoin (graphique en escalier).
//  1. « Histoire »   : le prix du bitcoin de 2009 à aujourd'hui (graphique + prix qui défile).
//  2. « Météorite »  : une météorite ₿ fonce sur le quartier des banques (style illustration plate).
val STRIKE_VARIANTES = listOf("DCA", "Histoire du bitcoin", "Météorite")

fun strike(numero: Int): Scene = when (numero) {
    0 -> StrikeDca()
    1 -> StrikeHistoire()
    2 -> StrikeMeteorite()
    else -> listOf<() -> Scene>({ StrikeDca() }, { StrikeHistoire() }, { StrikeMeteorite() }).random()()
}

// --- Petits outils communs à ces trois scènes (privés : invisibles ailleurs) ---

// Un nombre avec des espaces fines entre les milliers, à la française : 100 000 000.
private fun milliers(v: Long): String =
    String.format(Locale.FRANCE, "%,d", v).replace(' ', ' ').replace(' ', ' ')

// Mélange de deux couleurs (t = 0 : la première, t = 1 : la seconde).
private fun melange(a: Int, b: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    return Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
    )
}

// Tirage « en cloche » (loi normale) : la plupart des valeurs près de 0, quelques-unes plus loin.
private fun Random.cloche(): Float {
    val u1 = nextDouble().coerceAtLeast(1e-9)
    val u2 = nextDouble()
    return (sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)).toFloat()
}

private fun enDegres(radians: Float): Float = radians * 180f / PI.toFloat()

// Le symbole ₿ : un B gras avec deux petites barres qui dépassent en haut et en bas.
// (On le dessine nous-mêmes : toutes les polices du téléphone n'ont pas le caractère ₿.)
private class SymboleBitcoin {
    private val lettre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val plein = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bornes = Rect()

    fun dessiner(c: Canvas, cx: Float, cy: Float, taille: Float, couleur: Int) {
        if (Color.alpha(couleur) == 0) return
        lettre.textSize = taille
        lettre.color = couleur
        lettre.getTextBounds("B", 0, 1, bornes)
        val haut = bornes.height().toFloat()
        val larg = bornes.width().toFloat()
        val base = cy + haut / 2f
        c.drawText("B", cx, base, lettre)
        plein.color = couleur
        val e = larg * 0.11f                       // épaisseur d'une barre
        val depasse = haut * 0.16f                 // combien elle dépasse
        val gauche = cx - larg / 2f
        for (bx in floatArrayOf(gauche + larg * 0.22f, gauche + larg * 0.50f)) {
            c.drawRect(bx - e / 2f, base - haut - depasse, bx + e / 2f, base - haut + 1f, plein)
            c.drawRect(bx - e / 2f, base - 1f, bx + e / 2f, base + depasse, plein)
        }
    }
}

// =====================================================================================
// 0. STRIKE « DCA »
// Fond noir, la tuile grise au ₿ (style de l'app Strike). En dessous, un ESCALIER rose :
// chaque marche = un achat de la même somme. Quand le prix (courbe grise discrète) baisse,
// la même somme achète plus de sats : la marche est plus haute. Le compteur suit la tête
// de l'escalier et finit pile sur 100 000 000 sats = 1 bitcoin.
// L'ouverture : la tuile s'agrandit comme une carte jusqu'à remplir l'écran.
// Mesures pensées pour un écran « modèle » de 540 de large, multipliées par k.
// =====================================================================================
class StrikeDca : Scene() {
    private val gris = Color.rgb(30, 30, 30)
    private val rose = Color.rgb(246, 212, 212)

    // 96 achats (≈ 2 ans, une fois par semaine). Le prix fait une marche au hasard
    // qui monte doucement, avec un creux au milieu (un marché baissier).
    private val nAchats = 96
    private val prix = FloatArray(nAchats)
    private val cumul = DoubleArray(nAchats + 1)      // sats accumulés après chaque achat
    private val lnMin: Float
    private val lnMax: Float

    init {
        val r = Random(11)
        var v = 0.0
        for (j in 0 until nAchats) {
            v += (r.nextDouble() + r.nextDouble() + r.nextDouble() - 1.5) * 0.09 + 0.012
            val creux = -0.9 * exp(-((j - 38) / 13.0).pow(2))
            prix[j] = exp(v + creux).toFloat()
        }
        var total = 0.0
        for (j in 0 until nAchats) total += 1.0 / prix[j]          // sats achetés = somme fixe / prix
        for (j in 0 until nAchats) cumul[j + 1] = cumul[j] + (1.0 / prix[j]) / total * 100_000_000.0
        cumul[nAchats] = 100_000_000.0
        lnMin = ln(prix.min())
        lnMax = ln(prix.max())
    }

    // Les pinceaux, préparés une fois (en créer à chaque image ralentirait l'animation).
    private val fond = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pointilles = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val chiffres = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        fontFeatureSettings = "tnum"      // chiffres de largeur égale : le texte ne tremble pas en comptant
    }
    private val petit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        color = Color.rgb(140, 140, 140)
    }
    private val symbole = SymboleBitcoin()
    private val chemin = Path()
    private val remplissage = Path()
    private val boite = RectF()

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val sec = i.secondes; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val k = w / 540f
        val app = ralentit(i.intro)

        c.drawColor(Color.BLACK)

        // 1) LE GRAPHIQUE : 0 sat en bas, 1 bitcoin en haut (ligne en pointillés).
        val p = ralentit(borne((sec - 0.15f) / 0.85f))
        val gx0 = 40f * k; val gx1 = w - 40f * k
        val gbas = h * 0.86f; val ghaut = h * 0.60f
        fun xA(j: Float) = gx0 + (gx1 - gx0) * j / nAchats
        fun yS(s: Double) = (gbas + (ghaut - gbas) * s / 100_000_000.0).toFloat()

        trait.color = avecAlpha(Color.WHITE, 0.10f * app); trait.strokeWidth = 1f * k
        c.drawLine(gx0, gbas, gx1, gbas, trait)
        pointilles.color = avecAlpha(Color.WHITE, 0.10f * app); pointilles.strokeWidth = 1f * k
        pointilles.pathEffect = DashPathEffect(floatArrayOf(4f * k, 6f * k), 0f)
        c.drawLine(gx0, ghaut, gx1, ghaut, pointilles)

        var sats = 0.0
        if (p > 0f) {
            val pos = p * nAchats
            val n = min(pos.toInt(), nAchats)
            // le prix, en gris très discret, pour comprendre la forme de l'escalier
            chemin.reset()
            for (j in 0 until min(n + 1, nAchats)) {
                val py = gbas - 30f * k - (ln(prix[j]) - lnMin) / (lnMax - lnMin) * (gbas - ghaut) * 0.55f
                if (j == 0) chemin.moveTo(xA(j + 0.5f), py) else chemin.lineTo(xA(j + 0.5f), py)
            }
            trait.color = avecAlpha(Color.WHITE, 0.13f); trait.strokeWidth = 1.2f * k
            c.drawPath(chemin, trait)

            // l'escalier des sats : un palier, puis une marche à chaque achat
            chemin.reset()
            chemin.moveTo(xA(0f), yS(0.0))
            for (j in 0 until n) {
                chemin.lineTo(xA(j.toFloat()), yS(cumul[j + 1]))
                chemin.lineTo(xA(j + 1f), yS(cumul[j + 1]))
            }
            val hx: Float; val hy: Float
            if (n < nAchats) {
                sats = cumul[n + 1]
                hy = yS(sats); hx = xA(pos)
                chemin.lineTo(xA(n.toFloat()), hy); chemin.lineTo(hx, hy)
            } else {
                sats = cumul[nAchats]
                hx = xA(nAchats.toFloat()); hy = yS(sats)
            }
            // léger remplissage rose sous l'escalier
            remplissage.set(chemin)
            remplissage.lineTo(hx, gbas); remplissage.lineTo(xA(0f), gbas); remplissage.close()
            fond.shader = LinearGradient(0f, ghaut, 0f, gbas, avecAlpha(rose, 0.10f), avecAlpha(rose, 0f), Shader.TileMode.CLAMP)
            c.drawPath(remplissage, fond)
            fond.shader = null
            trait.color = avecAlpha(rose, 0.9f); trait.strokeWidth = 1.7f * k
            c.drawPath(chemin, trait)
            if (p < 1f) {
                o.halo(c, hx, hy, 10f * k, 0.12f, rose)
                fond.color = Color.rgb(255, 240, 240)
                c.drawCircle(hx, hy, 2.6f * k, fond)
            }
        }

        // 2) LA TUILE grise et son ₿ : elle apparaît en grandissant un peu.
        val tx = w / 2f; val ty = h * 0.38f
        val t = 0.92f + 0.08f * app
        val demi = 48f * k * t
        boite.set(tx - demi, ty - demi, tx + demi, ty + demi)
        fond.color = avecAlpha(gris, app)
        c.drawRoundRect(boite, 26f * k * t, 26f * k * t, fond)
        symbole.dessiner(c, tx, ty, 50f * k * t, avecAlpha(Color.WHITE, borne((sec - 0.08f) / 0.2f)))

        // 3) LE COMPTEUR suit la tête de l'escalier, jusqu'à 100 000 000 sats (= 1 bitcoin).
        val q = borne((sec - 0.15f) / 0.85f)
        if (q > 0f) {
            chiffres.textSize = 34f * k
            chiffres.alpha = (borne(q * 4f) * 255).toInt()
            c.drawText(milliers(sats.roundToLong()) + " sats", w / 2f, h * 0.50f + 12f * k, chiffres)
        }
        val a1 = borne((sec - 0.95f) / 0.25f)
        if (a1 > 0f) {
            petit.textSize = 16f * k
            petit.alpha = (a1 * 255).toInt()
            c.drawText("= 1 bitcoin", w / 2f, h * 0.50f + 44f * k, petit)
        }

        // 4) L'OUVERTURE : un trou en forme de carte arrondie grandit depuis la tuile.
        if (rev > 0f) {
            val e = ralentit(rev)
            val dl = 48f * k + (w / 2f + 4f - 48f * k) * e
            val dh = 48f * k + (h / 2f + 4f - 48f * k) * e
            val cy = ty + (h / 2f - ty) * e
            val rayon = (26f + (44f - 26f) * e) * k
            boite.set(w / 2f - dl, cy - dh, w / 2f + dl, cy + dh)
            c.drawRoundRect(boite, rayon, rayon, o.gomme())
            trait.color = avecAlpha(Color.rgb(90, 90, 90), 0.8f * (1f - e))
            trait.strokeWidth = 1.2f * k
            c.drawRoundRect(boite, rayon, rayon, trait)
        }
    }
}

// =====================================================================================
// 1. STRIKE « HISTOIRE DU BITCOIN »
// Le prix du bitcoin d'octobre 2009 à aujourd'hui, en échelle normale : plat pendant des années,
// puis la montée. En haut, « 1 bitcoin en <année> » et le prix qui suivent la tête du graphique.
// Les grands repères sont réels ; entre eux, de petites variations sont ajoutées pour le relief.
// Le dernier prix (début novembre 2026) est figé dans l'animation.
// =====================================================================================

// (année, prix en dollars) : les grands moments de l'histoire du bitcoin.
private val REPERES_BTC = floatArrayOf(
    2009.79f, 0.00076f, 2010.50f, 0.06f, 2010.90f, 0.30f, 2011.10f, 1.0f, 2011.45f, 30f,
    2011.90f, 2.5f, 2012.50f, 7f, 2012.99f, 13.5f, 2013.27f, 230f, 2013.32f, 80f,
    2013.55f, 75f, 2013.92f, 1100f, 2014.10f, 800f, 2014.50f, 600f, 2015.04f, 180f,
    2015.50f, 250f, 2015.99f, 430f, 2016.50f, 650f, 2016.99f, 960f, 2017.40f, 2500f,
    2017.70f, 4500f, 2017.96f, 19000f, 2018.10f, 7000f, 2018.40f, 7500f, 2018.95f, 3300f,
    2019.50f, 12000f, 2019.95f, 7200f, 2020.20f, 5000f, 2020.50f, 9200f, 2020.99f, 29000f,
    2021.30f, 63000f, 2021.55f, 30000f, 2021.86f, 68000f, 2022.00f, 46000f, 2022.50f, 19000f,
    2022.90f, 16000f, 2023.50f, 30000f, 2023.99f, 42000f, 2024.20f, 72000f, 2024.60f, 58000f,
    2024.96f, 100000f, 2025.05f, 105000f, 2025.30f, 78000f, 2025.55f, 118000f, 2025.77f, 125000f,
    2025.90f, 90000f, 2025.99f, 88000f, 2026.08f, 78000f, 2026.15f, 66000f, 2026.30f, 72000f,
    2026.40f, 73500f, 2026.45f, 61000f, 2026.55f, 60000f, 2026.60f, 62800f, 2026.67f, 77400f,
    2026.75f, 84800f, 2026.85f, 77450f
)

class StrikeHistoire : Scene() {
    private val rose = Color.rgb(246, 212, 212)
    private val nPts = 420
    private val annees = FloatArray(nPts + 1)
    private val prix = FloatArray(nPts + 1)
    private val debut = REPERES_BTC[0]
    private val fin = REPERES_BTC[REPERES_BTC.size - 2]

    init {
        val r = Random(5)
        // un petit frémissement qui varie doucement d'un point à l'autre
        val bruit = FloatArray(nPts + 1)
        for (j in 1..nPts) bruit[j] = bruit[j - 1] * 0.85f + (r.nextFloat() - 0.5f) * 0.10f
        val nRep = REPERES_BTC.size / 2
        var s = 0
        for (j in 0..nPts) {
            val a = debut + (fin - debut) * j / nPts
            while (s < nRep - 2 && REPERES_BTC[(s + 1) * 2] < a) s++
            val a0 = REPERES_BTC[s * 2]; val p0 = REPERES_BTC[s * 2 + 1]
            val a1 = REPERES_BTC[s * 2 + 2]; val p1 = REPERES_BTC[s * 2 + 3]
            var t = ((a - a0) / (a1 - a0)).coerceIn(0f, 1f)
            t = t * t * (3f - 2f * t)                               // passage doux d'un repère à l'autre
            val lp = log10(p0) + (log10(p1) - log10(p0)) * t        // on interpole en échelle log
            val attenue = if (j in 1 until nPts) 1f else 0f         // le premier et le dernier point restent exacts
            annees[j] = a
            prix[j] = 10f.pow(lp + bruit[j] * attenue)
        }
    }

    private val fond = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pointilles = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val legende = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
    private val titre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val chiffres = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        fontFeatureSettings = "tnum"
    }
    private val chemin = Path()
    private val remplissage = Path()
    private val boite = RectF()

    // 0,0008 $ / 3,52 $ / 77 450 $
    private fun formatPrix(p: Float): String = when {
        p < 0.01f -> String.format(Locale.FRANCE, "%.4f", p)
        p < 10f -> String.format(Locale.FRANCE, "%.2f", p)
        else -> milliers(p.roundToLong())
    } + " $"

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val sec = i.secondes; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val k = w / 540f
        val app = ralentit(i.intro)
        c.drawColor(Color.BLACK)

        val gx0 = 28f * k; val gx1 = w - 28f * k
        val ghaut = h * 0.40f; val gbas = h * 0.80f
        fun xP(j: Float) = gx0 + (gx1 - gx0) * j / nPts
        fun yP(v: Float) = gbas - v / 130_000f * (gbas - ghaut)

        // repères de l'échelle, en gris très discret
        legende.textSize = 11f * k
        legende.textAlign = Paint.Align.LEFT
        pointilles.strokeWidth = 1f * k
        pointilles.pathEffect = DashPathEffect(floatArrayOf(3f * k, 7f * k), 0f)
        for ((v, txt) in listOf(25_000f to "25 000 $", 50_000f to "50 000 $", 75_000f to "75 000 $", 100_000f to "100 000 $")) {
            val y = yP(v)
            pointilles.color = avecAlpha(Color.WHITE, 0.07f * app)
            c.drawLine(gx0, y, gx1, y, pointilles)
            legende.color = avecAlpha(Color.WHITE, 0.22f * app)
            c.drawText(txt.replace(' ', ' '), gx0, y - 5f * k, legende)
        }

        // le graphique se dessine de 2009 à aujourd'hui (rapide, puis ralentit un peu sur la fin)
        val x = borne((sec - 0.08f) / 1.0f)
        val p = 1f - (1f - x).pow(1.7f)
        val pos = p * nPts
        val n = min(pos.toInt(), nPts)
        var hx = xP(0f); var hy = yP(prix[0]); var prixT = prix[0]
        if (p > 0f) {
            chemin.reset()
            chemin.moveTo(xP(0f), yP(prix[0]))
            for (j in 1..n) chemin.lineTo(xP(j.toFloat()), yP(prix[j]))
            if (n < nPts) {
                val f = pos - n
                prixT = 10f.pow(log10(prix[n]) * (1f - f) + log10(prix[n + 1]) * f)
                hx = xP(pos); hy = yP(prixT)
                chemin.lineTo(hx, hy)
            } else {
                prixT = prix[nPts]; hx = xP(nPts.toFloat()); hy = yP(prixT)
            }
            remplissage.set(chemin)
            remplissage.lineTo(hx, gbas); remplissage.lineTo(xP(0f), gbas); remplissage.close()
            fond.shader = LinearGradient(0f, ghaut, 0f, gbas, avecAlpha(rose, 0.10f), avecAlpha(rose, 0f), Shader.TileMode.CLAMP)
            c.drawPath(remplissage, fond)
            fond.shader = null
            trait.color = avecAlpha(rose, 0.9f); trait.strokeWidth = 1.7f * k
            c.drawPath(chemin, trait)
            o.halo(c, hx, hy, 10f * k, 0.14f, rose)
            fond.color = Color.rgb(255, 240, 240)
            c.drawCircle(hx, hy, 2.8f * k, fond)
        }

        // ligne de base et années
        trait.color = avecAlpha(Color.WHITE, 0.10f * app); trait.strokeWidth = 1f * k
        c.drawLine(gx0, gbas, gx1, gbas, trait)
        legende.textAlign = Paint.Align.CENTER
        legende.color = avecAlpha(Color.WHITE, 0.22f * app)
        for (an in intArrayOf(2010, 2014, 2018, 2022, 2026)) {
            val xa = gx0 + (gx1 - gx0) * (an - debut) / (fin - debut)
            c.drawText(an.toString(), xa, gbas + 20f * k, legende)
        }

        // l'année et le prix suivent la tête du graphique
        val an = annees[n].toInt()
        titre.textSize = 18f * k
        titre.color = avecAlpha(Color.rgb(140, 140, 140), app)
        c.drawText("1 bitcoin en $an", w / 2f, h * 0.24f, titre)
        chiffres.textSize = 40f * k
        chiffres.color = avecAlpha(Color.WHITE, app)
        c.drawText(formatPrix(prixT), w / 2f, h * 0.24f + 52f * k, chiffres)

        // l'ouverture : une carte arrondie grandit depuis le point final du graphique
        if (rev > 0f) {
            val e = ralentit(rev)
            val cx = hx + (w / 2f - hx) * e; val cy = hy + (h / 2f - hy) * e
            val dl = 6f * k + (w / 2f + 4f - 6f * k) * e
            val dh = 6f * k + (h / 2f + 4f - 6f * k) * e
            val rayon = min(min(dl, dh), (6f + 38f * e) * k)
            boite.set(cx - dl, cy - dh, cx + dl, cy + dh)
            c.drawRoundRect(boite, rayon, rayon, o.gomme())
            trait.color = avecAlpha(Color.rgb(90, 90, 90), 0.8f * (1f - e))
            trait.strokeWidth = 1.2f * k
            c.drawRoundRect(boite, rayon, rayon, trait)
        }
    }
}

// =====================================================================================
// 2. STRIKE « MÉTÉORITE »
// Style illustration plate, lumineux. Une vraie météorite (roche claire, cratères, ₿ gravé)
// reste bien à droite dans le ciel : elle VIBRE dans sa gaine de plasma rose, et sa traînée
// de bulles de feu monte vers le haut. À gauche seulement : le quartier des banques sur un quai.
// La scène arrive en fondu depuis le noir (pour ne pas éblouir), puis l'app s'ouvre
// dans un cercle doux autour de la météorite.
// Toutes les mesures en « u » : u = 1 sur un écran de 1080 pixels de large.
// =====================================================================================
private object PalMeteo {
    fun h(s: String) = Color.parseColor(s)
    val CIEL_H = h("#1f5fd6"); val CIEL_M = h("#2f8ff0"); val CIEL_B = h("#73dcf5")
    val NUAGE_C = h("#ece4ff"); val NUAGE_M = h("#c7b6f4"); val NUAGE_F = h("#9887e0")
    val COLLINE = h("#2d5fb8"); val COLLINE2 = h("#3f78cc"); val VERT = h("#3fae7a")
    val MER_H = h("#31d0cf"); val MER_B = h("#169fb4")
    val SABLE = h("#f6c35a"); val SABLE_F = h("#d99a3a")
    val ROCHE = h("#f7e7b8"); val ROCHE_C = h("#fffbea"); val ROCHE_F = h("#e9c98d")
    val CRATERE = h("#f0a98f"); val CRATERE_F = h("#d9806d")
    val ROSE = h("#ff86b4"); val ROSE_F = h("#f2559a"); val JAUNE = h("#ffe27a"); val BLANC = h("#fffaf0")
    val MAGENTA = h("#ff6fd0")
    val FEN_ON = h("#ffe9a8"); val FEN_OFF = h("#8fd4ff")
}

// une couche de la gaine de plasma : couleur, taille (× rayon), opacité, épaisseur du bord
private class CoucheGaine(val couleur: Int, val taille: Float, val opacite: Float, val epaisseur: Float)
// une tour : position, largeur, hauteur, profondeur du côté, couleur, enseigne ?, toit (0 plat, 1 pointe, 2 antenne)
private class TourBanque(val x: Float, val larg: Float, val haut: Float, val prof: Float, val couleur: Int, val enseigne: Boolean, val toit: Int)

class StrikeMeteorite : Scene() {
    private val P = PalMeteo
    private val couchesGaine = arrayOf(
        CoucheGaine(P.MAGENTA, 1.55f, 0.55f, 0.30f), CoucheGaine(P.ROSE, 1.40f, 0.85f, 0.22f), CoucheGaine(P.BLANC, 1.27f, 0.95f, 0.12f)
    )

    // la traînée part vers le haut (un peu à droite) : la météorite fonce vers le bas-gauche
    private val dx: Float; private val dy: Float
    init {
        val n = hypot(0.22f, -0.975f)
        dx = 0.22f / n; dy = -0.975f / n
    }
    private val px = -dy
    private val py = dx
    private val angMouvement = atan2(-dy, -dx)

    private val rnd = Random(21)
    // le contour irrégulier de la roche (64 points)
    private val contour = FloatArray(64) { i ->
        val a = i / 64f * 2f * PI.toFloat()
        1f + 0.07f * sin(3f * a + 0.4f) + 0.05f * sin(5f * a + 1.3f) + 0.03f * sin(9f * a)
    }
    // les cratères (x, y, rayon), sans toucher le ₿ au centre
    private val crateres: List<FloatArray> = List(60) {
        floatArrayOf(rnd.nextFloat() * 1.2f - 0.6f, rnd.nextFloat() * 1.2f - 0.6f, 0.05f + rnd.nextFloat() * 0.08f)
    }.filter { hypot(it[0], it[1]) < 0.72f && !(kotlin.math.abs(it[0]) < 0.48f && kotlin.math.abs(it[1]) < 0.58f) }
    // les bulles de feu : phase, décalage sur le côté, taille, teinte
    private val nBulles = 150
    private val bPh = FloatArray(nBulles) { rnd.nextFloat() }
    private val bLat = FloatArray(nBulles) { rnd.cloche() * 0.40f }
    private val bTaille = FloatArray(nBulles) { 0.6f + rnd.nextFloat() * 0.5f }
    private val bTeinte = FloatArray(nBulles) { rnd.nextFloat() }
    private val bAge = FloatArray(nBulles)
    // les traits de vitesse blancs
    private val traits = List(14) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 2.2f - 1.1f, 0.8f + rnd.nextFloat() * 0.8f) }
    // les nuages : x, y (fractions de l'écran), taille
    private val nuages = listOf(
        floatArrayOf(0.12f, 0.08f, 1.1f), floatArrayOf(0.55f, 0.15f, 0.8f), floatArrayOf(0.30f, 0.30f, 0.9f),
        floatArrayOf(0.92f, 0.24f, 1.0f), floatArrayOf(0.18f, 0.50f, 0.7f), floatArrayOf(0.95f, 0.55f, 0.75f),
        floatArrayOf(0.60f, 0.60f, 0.55f)
    )
    private val oiseaux = List(6) { floatArrayOf(0.35f + rnd.nextFloat() * 0.6f, 0.50f + rnd.nextFloat() * 0.14f, 0.6f + rnd.nextFloat() * 0.5f, rnd.nextFloat()) }
    // reflets de la météorite sur l'eau (26 rangées × 3 tirets) : décalage, longueur, couleur
    private val reflets = List(78) { floatArrayOf(rnd.cloche(), 30f + rnd.nextFloat() * 80f, rnd.nextFloat()) }

    // pinceaux et objets réutilisés à chaque image
    private val pl = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tr = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val texte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD); textAlign = Paint.Align.CENTER
    }
    private val texteSerif = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER
    }
    private val effaceur = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val symbole = SymboleBitcoin()
    private val chemin = Path()
    private val ov = RectF()

    private fun ovale(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, couleur: Int) {
        ov.set(x - rx, y - ry, x + rx, y + ry); pl.color = couleur; c.drawOval(ov, pl)
    }

    private fun poly(c: Canvas, couleur: Int, vararg pts: Float) {
        chemin.reset(); chemin.moveTo(pts[0], pts[1])
        var k = 2
        while (k < pts.size) { chemin.lineTo(pts[k], pts[k + 1]); k += 2 }
        chemin.close()
        pl.color = couleur; c.drawPath(chemin, pl)
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val sec = i.secondes; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val u = w / 1080f
        val horizon = h * 0.69f
        val rm = 192f * u                                   // rayon de la météorite

        // la météorite vibre sur place
        val x = w * 0.70f + (sin(sec * 83f) + sin(sec * 131f + 1f)) * 0.5f * 10f * u
        val y = h * 0.37f + (sin(sec * 97f + 2f) + sin(sec * 149f)) * 0.5f * 10f * u

        // ---- le ciel
        pl.shader = LinearGradient(0f, 0f, 0f, horizon, intArrayOf(P.CIEL_H, P.CIEL_M, P.CIEL_B),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, pl)
        pl.shader = null

        // ---- les nuages : couches d'ellipses (dessous foncé, corps, dessus clair) qui dérivent
        for (n in nuages) {
            val s = n[2] * 2f * u * 0.9f
            val nx = modulo(n[0] * w + sec * 25f * n[2] * u, w * 1.4f) - w * 0.2f
            val ny = n[1] * h
            for ((col, ddy, kk) in listOf(Triple(P.NUAGE_F, 0.20f, 1.0f), Triple(P.NUAGE_M, 0.0f, 0.92f), Triple(P.NUAGE_C, -0.20f, 0.62f))) {
                val yy = ny + ddy * 60f * s
                ovale(c, nx, yy, 170f * s * kk, 46f * s * kk, col)
                ovale(c, nx - 70f * s * kk, yy + 10f * s, 120f * s * kk, 36f * s * kk, col)
                ovale(c, nx + 40f * s * kk, yy - 34f * s, 110f * s * kk, 40f * s * kk, col)
            }
        }

        // ---- collines au loin, mer, reflets, vagues
        poly(c, P.COLLINE, w * 0.30f, horizon, w * 0.42f, horizon - 70f * u, w * 0.55f, horizon - 40f * u, w * 0.62f, horizon - 95f * u,
            w * 0.78f, horizon - 55f * u, w * 0.90f, horizon - 85f * u, w * 1.05f, horizon - 30f * u, w * 1.05f, horizon)
        poly(c, P.VERT, w * 0.58f, horizon - 80f * u, w * 0.62f, horizon - 95f * u, w * 0.67f, horizon - 83f * u, w * 0.63f, horizon - 76f * u)
        poly(c, P.COLLINE2, w * 0.45f, horizon, w * 0.70f, horizon - 35f * u, w * 0.82f, horizon - 20f * u, w * 1.05f, horizon - 45f * u, w * 1.05f, horizon)
        pl.shader = LinearGradient(0f, horizon, 0f, h, P.MER_H, P.MER_B, Shader.TileMode.CLAMP)
        c.drawRect(0f, horizon, w, h, pl)
        pl.shader = null
        for (r in 0 until 26) {
            val yy = horizon + 14f * u + r * r * 2.2f * u
            if (yy > h) break
            for (j in 0 until 3) {
                val f = reflets[r * 3 + j]
                val xx = x + f[0] * (60f + r * 9f) * u + sin(sec * 3f + r + j) * 8f * u
                val l = f[1] * (1f + r * 0.05f) * u
                pl.color = avecAlpha(if (f[2] < 0.5f) P.BLANC else P.ROSE, 0.85f - r * 0.025f)
                c.drawRect(xx, yy, xx + l, yy + (5f + r * 0.25f) * u, pl)
            }
        }
        tr.color = avecAlpha(Color.WHITE, 0.9f); tr.strokeWidth = 7f * u
        tr.pathEffect = DashPathEffect(floatArrayOf(60f * u, 34f * u), modulo(sec * 120f * u, 94f * u))
        for ((y0, amp, ph) in listOf(Triple(horizon + 330f * u, 40f * u, 0f), Triple(horizon + 520f * u, 55f * u, 2f))) {
            chemin.reset()
            for (kk in 0 until 120) {
                val xx = w * 0.35f + kk * w * 0.7f / 119f
                val yy = y0 + sin(kk * 0.35f / 3f + ph + sec * 2f) * amp
                if (kk == 0) chemin.moveTo(xx, yy) else chemin.lineTo(xx, yy)
            }
            c.drawPath(chemin, tr)
        }
        tr.pathEffect = null

        // ---- la traînée : lueurs, bulles de feu qui montent, traits de vitesse
        o.halo(c, x, y, rm * 2.1f, 0.33f, P.MAGENTA)
        o.halo(c, x - dx * rm * 0.6f, y - dy * rm * 0.6f, rm * 1.2f, 0.35f, P.BLANC)
        o.halo(c, x + dx * rm * 2.5f, y + dy * rm * 2.5f, rm * 1.75f, 0.18f, P.ROSE)
        for (b in 0 until nBulles) bAge[b] = modulo(bPh[b] + sec * 0.9f, 1f)
        for (b in (0 until nBulles).sortedByDescending { bAge[it] }) {      // les plus vieilles derrière
            val f = bAge[b]
            val d = (0.4f + f * 6f) * rm
            val off = bLat[b] * rm * (1f + 0.6f * f)
            val bx = x + dx * d + px * off; val by = y + dy * d + py * off
            val rr = rm * bTaille[b] * (0.32f + 0.35f * sin(PI.toFloat() * min(1f, f * 1.3f))) * (1f - 0.5f * f)
            val col = when {
                bTeinte[b] < 0.35f -> melange(P.BLANC, P.JAUNE, f * 1.5f)
                bTeinte[b] < 0.7f -> melange(P.JAUNE, P.ROSE, f)
                else -> melange(P.ROSE, P.ROSE_F, f)
            }
            val a = if (f < 0.75f) 1f else (1f - f) / 0.25f
            pl.color = avecAlpha(col, a); c.drawCircle(bx, by, rr, pl)
            if (rr > 10f * u) { pl.color = avecAlpha(Color.WHITE, 0.35f * a); c.drawCircle(bx - rr * 0.3f, by - rr * 0.3f, rr * 0.35f, pl) }
        }
        for (t in traits) {
            val f = modulo(t[0] + sec * 1.6f, 1f)
            val d0 = (1.3f + f * 4.5f) * rm; val d1 = d0 + rm * 0.9f * t[2]
            val off = t[1] * rm * 0.9f
            tr.color = avecAlpha(P.BLANC, 0.9f * (1f - f)); tr.strokeWidth = 9f * u * (1f - 0.5f * f)
            c.drawLine(x + dx * d0 + px * off, y + dy * d0 + py * off, x + dx * d1 + px * off, y + dy * d1 + py * off, tr)
        }

        // ---- la gaine de plasma en U, ouverte vers l'arrière (comme un bouclier thermique)
        val pul = 1f + 0.03f * sin(sec * 37f)
        for (couche in couchesGaine) {
            val col = couche.couleur; val kk = couche.taille; val a = couche.opacite; val ep = couche.epaisseur
            c.save(); c.translate(x, y); c.rotate(enDegres(angMouvement - PI.toFloat() / 2f))
            val r = rm * kk * pul
            chemin.reset()
            ov.set(-r, -r, r, r); chemin.arcTo(ov, 0f, 180f, true)
            chemin.lineTo(-r * 0.92f, -r * 1.25f); chemin.lineTo(-r * 0.92f + r * ep, -r * 1.25f)
            chemin.lineTo(-r + r * ep, 0f)
            val ri = r * (1f - ep)
            ov.set(-ri, -ri, ri, ri); chemin.arcTo(ov, 180f, -180f, false)
            chemin.lineTo(r * 0.92f - r * ep, -r * 1.25f); chemin.lineTo(r * 0.92f, -r * 1.25f)
            chemin.close()
            pl.shader = LinearGradient(0f, r, 0f, -r * 1.25f, intArrayOf(avecAlpha(col, a), avecAlpha(col, a * 0.8f), avecAlpha(col, 0f)),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(chemin, pl)
            pl.shader = null
            c.restore()
        }

        // ---- la roche, ses cratères et le ₿ gravé
        val rot = 0.08f * sin(sec * 29f)
        fun tracerContour(kk: Float, ox: Float, oy: Float) {
            chemin.reset()
            for (j in 0 until 64) {
                val aa = j / 64f * 2f * PI.toFloat()
                val qx = ox + cos(aa) * rm * contour[j] * kk
                val qy = oy + sin(aa) * rm * contour[j] * kk * 1.08f
                if (j == 0) chemin.moveTo(qx, qy) else chemin.lineTo(qx, qy)
            }
            chemin.close()
        }
        c.save(); c.translate(x, y); c.rotate(enDegres(rot))
        tracerContour(1f, 0f, 0f); pl.color = P.ROCHE_F; c.drawPath(chemin, pl)                        // ombre
        tracerContour(0.93f, -rm * 0.05f, -rm * 0.06f); pl.color = P.ROCHE; c.drawPath(chemin, pl)      // corps
        tracerContour(0.55f, -rm * 0.28f, -rm * 0.30f); pl.color = avecAlpha(P.ROCHE_C, 0.8f); c.drawPath(chemin, pl)   // reflet
        for (cr in crateres) {
            ovale(c, cr[0] * rm, cr[1] * rm, cr[2] * rm, cr[2] * rm * 0.85f, P.CRATERE)
            ovale(c, cr[0] * rm + cr[2] * rm * 0.15f, cr[1] * rm + cr[2] * rm * 0.15f, cr[2] * rm * 0.6f, cr[2] * rm * 0.5f, P.CRATERE_F)
        }
        c.rotate(enDegres(-0.18f))
        symbole.dessiner(c, -rm * 0.03f, -rm * 0.03f, rm * 1.05f, P.ROCHE_C)                 // arête claire
        symbole.dessiner(c, rm * 0.025f, rm * 0.025f, rm * 1.05f, Color.parseColor("#c97a5c"))   // ombre du creux
        symbole.dessiner(c, 0f, 0f, rm * 1.05f, Color.parseColor("#e39a7b"))                  // fond du creux
        c.restore()
        // l'avant (bas-gauche) chauffé à blanc
        c.save(); c.translate(x, y); c.rotate(enDegres(rot))
        tracerContour(1f, 0f, 0f); c.clipPath(chemin)
        val fx = cos(angMouvement); val fy = sin(angMouvement)
        pl.shader = LinearGradient(fx * rm, fy * rm, -fx * rm * 0.2f, -fy * rm * 0.2f, avecAlpha(P.BLANC, 0.85f), avecAlpha(P.BLANC, 0f), Shader.TileMode.CLAMP)
        c.drawRect(-rm * 1.3f, -rm * 1.3f, rm * 1.3f, rm * 1.3f, pl)
        pl.shader = null
        c.restore()

        // ---- quelques oiseaux
        tr.color = Color.WHITE; tr.strokeWidth = 5f * u
        for (b in oiseaux) {
            val s = b[2]
            val xx = modulo(b[0] * w + sec * 60f * s * u, w * 0.7f) + w * 0.3f
            val by = b[1] * h
            val battement = sin(sec * 12f + b[3] * 6f) * 10f * u * s
            chemin.reset()
            chemin.moveTo(xx - 22f * u * s, by - battement); chemin.lineTo(xx, by + 6f * u * s); chemin.lineTo(xx + 22f * u * s, by - battement)
            c.drawPath(chemin, tr)
        }

        // ---- le quartier des banques, à gauche
        tours(c, sec, w, h, u, horizon)

        // ---- l'ouverture : un cercle aux bords doux s'ouvre autour de la météorite
        if (rev > 0f) {
            val e = ralentit(rev)
            val rmax = hypot(max(x, w - x), max(y, h - y)) * 1.25f
            val rr = 40f * u + rmax * e
            effaceur.shader = RadialGradient(x, y, rr, intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                floatArrayOf(0f, 0.82f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, rr, effaceur)
        }
        // ---- l'arrivée en fondu depuis le noir, pour ne pas éblouir
        if (i.intro < 1f) c.drawColor(avecAlpha(Color.BLACK, 1f - ralentit(i.intro)))
    }

    private fun tours(c: Canvas, sec: Float, w: Float, h: Float, u: Float, horizon: Float) {
        val base = horizon + 210f * u
        // le quai
        poly(c, P.SABLE, 0f, horizon + 120f * u, w * 0.52f, horizon + 150f * u, w * 0.60f, base + 40f * u, 0f, base + 40f * u)
        poly(c, P.SABLE_F, 0f, base + 40f * u, w * 0.60f, base + 40f * u, w * 0.62f, base + 80f * u, 0f, base + 80f * u)
        val lum = 0.55f + 0.1f * sin(sec * 30f)
        // (x, largeur, hauteur, profondeur du côté, couleur, enseigne ?, toit : 0 plat, 1 pointe, 2 antenne)
        val liste = listOf(
            TourBanque(w * 0.02f, 150f * u, 760f * u, 40f * u, Color.parseColor("#3b3fa8"), false, 0),
            TourBanque(w * 0.20f, 130f * u, 980f * u, 36f * u, Color.parseColor("#5146c0"), true, 1),
            TourBanque(w * 0.33f, 120f * u, 640f * u, 34f * u, Color.parseColor("#2f58b8"), false, 2),
            TourBanque(-30f * u, 140f * u, 560f * u, 38f * u, Color.parseColor("#4b2f9e"), false, 0)
        )
        for (t in liste) {
            val x = t.x; val lw = t.larg; val hh = t.haut; val dep = t.prof
            val cc = t.couleur
            val top = base - hh
            val clair = melange(cc, Color.WHITE, 0.18f)
            val ombre = melange(cc, Color.rgb(0, 0, 38), 0.35f)
            pl.color = clair; c.drawRect(x, top, x + lw, base, pl)                               // face avant
            poly(c, ombre, x + lw, top, x + lw + dep, top + dep * 0.5f, x + lw + dep, base, x + lw, base)   // côté droit
            pl.shader = LinearGradient(0f, top, 0f, base, avecAlpha(P.ROSE, 0.75f * lum), avecAlpha(P.ROSE, 0.15f * lum), Shader.TileMode.CLAMP)
            c.drawPath(chemin, pl)                                                               // éclairé en rose par la météorite
            pl.shader = null
            when (t.toit) {
                1 -> {
                    poly(c, clair, x, top, x + lw / 2f, top - 90f * u, x + lw, top)
                    poly(c, melange(ombre, P.ROSE, 0.5f), x + lw / 2f, top - 90f * u, x + lw + dep, top + dep * 0.5f, x + lw, top)
                }
                2 -> {
                    pl.color = ombre; c.drawRect(x + lw * 0.45f, top - 80f * u, x + lw * 0.45f + 8f * u, top, pl)
                    pl.color = P.ROSE; c.drawCircle(x + lw * 0.45f + 4f * u, top - 84f * u, 7f * u, pl)
                }
            }
            // fenêtres
            var rang = 0
            var yy = top + 40f * u
            while (yy < base - 30f * u) {
                var col = 0
                var xx = x + 16f * u
                while (xx < x + lw - 26f * u) {
                    pl.color = if ((rang * 5 + col * 3) % 7 < 4) P.FEN_ON else P.FEN_OFF
                    c.drawRect(xx, yy, xx + 18f * u, yy + 22f * u, pl)
                    xx += 32f * u; col++
                }
                yy += 46f * u; rang++
            }
            if (t.enseigne) {
                pl.color = P.JAUNE; c.drawRect(x + 14f * u, top + 40f * u, x + lw - 14f * u, top + 90f * u, pl)
                texte.color = Color.parseColor("#2a2170"); texte.textSize = 34f * u
                c.drawText("BANK", x + lw / 2f, top + 78f * u, texte)
            }
        }
        // la grande banque à colonnes, au premier plan
        val bx = w * 0.06f; val bl = w * 0.46f; val bh = 240f * u
        val y0 = base - bh
        pl.color = Color.parseColor("#f1e6ff"); c.drawRect(bx - 20f * u, base - 24f * u, bx + bl + 20f * u, base, pl)
        pl.color = Color.parseColor("#d9c8ff"); c.drawRect(bx - 10f * u, base - 44f * u, bx + bl + 10f * u, base - 24f * u, pl)
        pl.color = Color.parseColor("#7a62d6"); c.drawRect(bx, y0, bx + bl, base - 44f * u, pl)
        for (k in 0 until 6) {
            val xc = bx + 24f * u + k * (bl - 48f * u) / 5f
            pl.color = Color.parseColor("#efe6ff"); c.drawRect(xc - 16f * u, y0 + 10f * u, xc + 16f * u, base - 44f * u, pl)
            pl.color = Color.parseColor("#c3b0f5"); c.drawRect(xc + 6f * u, y0 + 10f * u, xc + 16f * u, base - 44f * u, pl)
            pl.color = avecAlpha(P.ROSE, 0.8f * lum); c.drawRect(xc + 12f * u, y0 + 10f * u, xc + 16f * u, base - 44f * u, pl)
        }
        pl.color = Color.parseColor("#e6dbff"); c.drawRect(bx - 14f * u, y0 - 46f * u, bx + bl + 14f * u, y0, pl)
        poly(c, Color.parseColor("#f6f0ff"), bx - 26f * u, y0 - 46f * u, bx + bl / 2f, y0 - 150f * u, bx + bl + 26f * u, y0 - 46f * u)
        poly(c, melange(Color.parseColor("#c9b6ff"), P.ROSE, 0.4f * lum), bx + bl / 2f, y0 - 150f * u, bx + bl + 26f * u, y0 - 46f * u, bx + bl / 2f, y0 - 46f * u)
        texteSerif.color = Color.parseColor("#4a3aa8"); texteSerif.textSize = 36f * u
        c.drawText("BANK", bx + bl / 2f, y0 - 10f * u, texteSerif)
        // deux arbres au bord du quai
        for ((tx, th) in listOf(w * 0.55f to 150f * u, w * 0.50f to 110f * u)) {
            pl.color = Color.parseColor("#2a7a5a"); c.drawRect(tx - 5f * u, base - th + 20f * u, tx + 5f * u, base, pl)
            ovale(c, tx, base - th + 10f * u, 46f * u, 30f * u, P.VERT)
            ovale(c, tx - 10f * u, base - th, 26f * u, 16f * u, Color.parseColor("#6fd39a"))
        }
    }
}
