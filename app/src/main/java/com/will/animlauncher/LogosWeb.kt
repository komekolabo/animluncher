package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

// LE LOGO DU DOSSIER « INTERNET » (18/11, demande de Will) : un globe dont les méridiens tournent.
// Même repère et mêmes règles que LogosDossier.kt et LogosQuotidien.kt : carré 100 x 100, centre (50, 48),
// « g » = l'horloge du geste (0,3 = la pose au repos), les lignes du globe sont DÉCOUPÉES avec la gomme
// (on voit le galet au travers), et une étincelle scintille.
// Le globe fait tourner ses 3 méridiens d'un sixième de tour : comme ils sont espacés d'un sixième de tour,
// le dessin final est exactement celui du départ, et la boucle du widget ne « saute » pas.
internal object LogosWeb {
    private val L = LogosDossier
    private val rond = Path()
    private val ovale = RectF()

    private val LIN = L.LIN
    private val DOUX = L.DOUX
    private val RESSORT = L.RESSORT
    private infix fun Pair<Double, Double>.by(fn: (Double) -> Double) = LogosDossier.CleLogo(first, second, fn)

    // Dessine le logo si c'est le nôtre (« internet ») et renvoie true ; sinon renvoie false.
    fun dessiner(cv: Canvas, cle: String, g: Double, src: Shader, lum: Int): Boolean {
        if (cle != "internet") return false
        globe(cv, g, src, lum)
        return true
    }

    private fun globe(cv: Canvas, g: Double, src: Shader, lum: Int) {
        val cx = 50f
        val cy = 48f
        val r = 24f
        // un petit saut au début du geste, puis le globe se repose
        val saut = L.cle(g, 0.35 to 0.0 by LIN, 0.6 to -3.0 by DOUX, 1.0 to 0.0 by RESSORT).toFloat()
        // la rotation : un sixième de tour (60°), en douceur
        val tour = L.cle(g, 0.5 to 0.0 by LIN, 2.2 to 1.0 by DOUX) * PI / 3
        cv.save()
        cv.translate(0f, saut)
        // la boule pleine (le dégradé du dossier)
        rond.reset()
        rond.addCircle(cx, cy, r, Path.Direction.CW)
        L.remplir(cv, rond, src)
        // les 3 méridiens : des ellipses de plus en plus étroites selon l'angle (un trait vertical si très étroites)
        val gomme = L.gomme(2.2f)
        for (k in 0 until 3) {
            val rx = (r * abs(cos(tour + k * PI / 3))).toFloat()
            if (rx > 1.2f) {
                ovale.set(cx - rx, cy - r, cx + rx, cy + r)
                cv.drawOval(ovale, gomme)
            } else {
                cv.drawLine(cx, cy - r, cx, cy + r, gomme)
            }
        }
        // les parallèles : l'équateur et deux cercles de latitude (vus de face, ce sont des lignes droites)
        for (h in floatArrayOf(-0.52f, 0f, 0.52f)) {
            val y = cy + h * r
            val demi = r * sqrt(1f - h * h)
            cv.drawLine(cx - demi, y, cx + demi, y, gomme)
        }
        cv.restore()
        L.etincelle(cv, 75f, 25f, 4.4f, lum, L.bosse(g, 1.25, 0.22))
    }
}
