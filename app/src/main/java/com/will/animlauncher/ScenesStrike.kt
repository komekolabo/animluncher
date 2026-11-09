package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

// ===================== STRIKE : deux animations, tirées au hasard =====================
//  0. « DCA »        : on achète la même somme chaque semaine jusqu'à avoir 1 bitcoin (graphique en escalier).
//  1. « Histoire »   : le prix du bitcoin de 2009 à aujourd'hui (graphique + prix qui défile).
val STRIKE_VARIANTES = listOf("DCA", "Histoire du bitcoin")

fun strike(numero: Int): Scene = when (numero) {
    0 -> StrikeDca()
    1 -> StrikeHistoire()
    else -> listOf<() -> Scene>({ StrikeDca() }, { StrikeHistoire() }).random()()
}

// --- Petits outils communs à ces deux scènes (privés : invisibles ailleurs) ---

// Un nombre avec des espaces fines entre les milliers, à la française : 100 000 000.
private fun milliers(v: Long): String =
    String.format(Locale.FRANCE, "%,d", v).replace(' ', ' ').replace(' ', ' ')

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

    // 0,0008 $ / 3,52 $ / 77 450 $
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
