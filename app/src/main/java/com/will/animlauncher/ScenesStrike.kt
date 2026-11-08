package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import java.util.Locale
import kotlin.random.Random

// ===================== STRIKE : « Noir et blanc » =====================
// Dans le style de l'app Strike : fond noir, une tuile grise arrondie avec le ₿ blanc,
// un compteur de sats qui monte jusqu'à 1 bitcoin, et une fine courbe rose pâle comme
// le graphique de l'app.
// L'ouverture : la tuile s'agrandit comme une carte jusqu'à remplir l'écran, et l'app apparaît dedans.
//
// Toutes les mesures sont pensées pour un écran « modèle » de 540 de large,
// puis multipliées par k (la vraie largeur / 540) : le dessin garde les mêmes proportions partout.
class StrikeNoirBlanc : Scene() {
    private val gris = Color.rgb(30, 30, 30)
    private val rose = Color.rgb(246, 212, 212)

    // La courbe : une marche au hasard (petits zigzags), avec un palier qui monte puis retombe,
    // comme le graphique de l'app. Calculée une seule fois ; valeurs autour de 0.
    private val nPoints = 160
    private val courbe = FloatArray(nPoints).also { y ->
        val r = Random(7)
        var v = 0f
        for (k in 0 until nPoints) {
            // somme de 3 tirages = une petite variation « en cloche »
            v += (r.nextFloat() + r.nextFloat() + r.nextFloat() - 1.5f) * 2.2f
            y[k] = v
        }
        for (k in 70 until 105) y[k] -= 26f
        val moyenne = y.average().toFloat()
        for (k in 0 until nPoints) y[k] -= moyenne
    }

    // Les pinceaux, préparés une fois (en créer à chaque image ralentirait l'animation).
    private val fond = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val lettreB = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
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
    private val chemin = Path()
    private val boite = RectF()
    private val bornesB = Rect()

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val k = w / 540f
        val app = ralentit(intro)

        c.drawColor(Color.BLACK)

        // 1) LA COURBE se dessine de gauche à droite, avec un petit point à sa tête.
        val p = doux(borne((sec - 0.10f) / 0.95f))
        if (p > 0f) {
            val n = maxOf(2, (nPoints * p).toInt())
            chemin.reset()
            for (j in 0 until n) {
                val x = w * j / (nPoints - 1)
                val y = h * 0.70f + courbe[j] * 1.6f * k
                if (j == 0) chemin.moveTo(x, y) else chemin.lineTo(x, y)
            }
            trait.color = avecAlpha(rose, 0.9f)
            trait.strokeWidth = 1.7f * k
            c.drawPath(chemin, trait)
            if (p < 1f) {
                val hx = w * (n - 1) / (nPoints - 1)
                val hy = h * 0.70f + courbe[n - 1] * 1.6f * k
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
        dessinerBitcoin(c, tx, ty, 50f * k * t, borne((sec - 0.08f) / 0.2f))

        // 3) LE COMPTEUR de sats : il monte vite puis ralentit, jusqu'à 100 000 000 (= 1 bitcoin).
        val q = borne((sec - 0.15f) / 0.85f)
        if (q > 0f) {
            val r = 1f - q
            val valeur = if (q >= 1f) 100_000_000L else ((1f - r * r * r) * 100_000L).toLong() * 1000L
            // « %,d » avec la langue française met des espaces entre les milliers
            val texte = String.format(Locale.FRANCE, "%,d", valeur).replace(' ', ' ').replace(' ', ' ') + " sats"
            chiffres.textSize = 34f * k
            chiffres.alpha = (borne(q * 4f) * 255).toInt()
            c.drawText(texte, w / 2f, h * 0.50f + 12f * k, chiffres)
        }
        val a1 = borne((sec - 0.95f) / 0.25f)
        if (a1 > 0f) {
            petit.textSize = 16f * k
            petit.alpha = (a1 * 255).toInt()
            c.drawText("= 1 bitcoin", w / 2f, h * 0.50f + 44f * k, petit)
        }

        // 4) L'OUVERTURE : un trou en forme de carte arrondie grandit depuis la tuile
        // jusqu'à couvrir tout l'écran ; à l'intérieur, l'app apparaît.
        if (rev > 0f) {
            val e = ralentit(rev)
            val dl = 48f * k + (w / 2f + 4f - 48f * k) * e
            val dh = 48f * k + (h / 2f + 4f - 48f * k) * e
            val cy = ty + (h / 2f - ty) * e
            val rayon = (26f + (44f - 26f) * e) * k
            boite.set(w / 2f - dl, cy - dh, w / 2f + dl, cy + dh)
            c.drawRoundRect(boite, rayon, rayon, o.gomme())
            // un fin liseré gris sur le bord de la carte, qui s'efface en fin d'ouverture
            trait.color = avecAlpha(Color.rgb(90, 90, 90), 0.8f * (1f - e))
            trait.strokeWidth = 1.2f * k
            c.drawRoundRect(boite, rayon, rayon, trait)
        }
    }

    // Le symbole ₿ : un B gras, avec deux petites barres qui dépassent en haut et en bas.
    // (On le dessine nous-mêmes : toutes les polices du téléphone n'ont pas le caractère ₿.)
    private fun dessinerBitcoin(c: Canvas, cx: Float, cy: Float, taille: Float, alpha: Float) {
        if (alpha <= 0f) return
        lettreB.textSize = taille
        lettreB.alpha = (alpha * 255).toInt()
        lettreB.getTextBounds("B", 0, 1, bornesB)
        val haut = bornesB.height().toFloat()
        val larg = bornesB.width().toFloat()
        val base = cy + haut / 2f
        c.drawText("B", cx, base, lettreB)
        fond.color = Color.WHITE
        fond.alpha = lettreB.alpha
        val e = larg * 0.11f                       // épaisseur d'une barre
        val depasse = haut * 0.16f                 // combien elle dépasse
        val gauche = cx - larg / 2f
        for (bx in floatArrayOf(gauche + larg * 0.22f, gauche + larg * 0.50f)) {
            c.drawRect(bx - e / 2f, base - haut - depasse, bx + e / 2f, base - haut + 1f, fond)
            c.drawRect(bx - e / 2f, base - 1f, bx + e / 2f, base + depasse, fond)
        }
    }
}
