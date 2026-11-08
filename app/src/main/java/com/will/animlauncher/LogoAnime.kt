package com.will.animlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// LE LOGO ANIMÉ, affiché en haut de l'écran principal.
// C'est le même dessin que l'icône de l'app (voir res/drawable/logo_avant.xml), mais vivant :
//  - la comète tourne lentement autour de l'icône (un tour en 12 secondes) ;
//  - l'étincelle au bout scintille doucement (elle grossit et rapetisse un peu) ;
//  - le point au centre « respire » (il s'éclaire et s'assombrit lentement).
// Tout est lent et sans flash : on ne le remarque vraiment que quand on le regarde.
//
// Comme pour l'icône, on dessine sur une grille de 108 x 108, puis on agrandit à la taille de la vue.
class LogoAnime(context: Context) : View(context) {

    private val couleur = context.getColor(R.color.logo_couleur)   // violet (Store) ou ambre (Perso)
    private val debut = SystemClock.uptimeMillis()                  // l'heure de départ de l'animation

    // Les « pinceaux » sont préparés une seule fois (en créer à chaque image ralentirait l'animation).
    private val fond = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.logo_fond) }
    private val traitIcone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3.6f; color = Color.rgb(232, 236, 247)
    }
    private val point = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = couleur }
    private val comete = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4.8f; strokeCap = Paint.Cap.ROUND; color = couleur
    }
    private val traine = Paint(comete).apply { strokeWidth = 3f; alpha = 100 }   // plus fine et transparente
    private val etincelle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)

    // Les formes fixes (en coordonnées 108 x 108)
    private val icone = RectF(36f, 36f, 72f, 72f)
    private val cercleComete = RectF(54f - 28.8f, 54f - 28.8f, 54f + 28.8f, 54f + 28.8f)
    private val etoile = Path().apply {
        // une étoile à 4 branches, centrée sur (0, 0) : on la déplacera au bout de la comète
        moveTo(0f, -7.2f); lineTo(1.8f, -1.8f); lineTo(7.4f, 0f); lineTo(1.8f, 1.8f)
        lineTo(0f, 7.2f); lineTo(-1.8f, 1.8f); lineTo(-7.4f, 0f); lineTo(-1.8f, -1.8f); close()
    }

    override fun onDraw(c: Canvas) {
        val t = (SystemClock.uptimeMillis() - debut) / 1000f     // secondes écoulées
        val s = min(width, height) / 108f                         // l'agrandissement 108 -> taille réelle
        c.save()
        c.translate((width - 108f * s) / 2f, (height - 108f * s) / 2f)
        c.scale(s, s)

        // 1) le fond rond (comme l'icône découpée par le téléphone)
        c.drawCircle(54f, 54f, 36f, fond)

        // 2) l'icône d'app au centre, et le point qui respire (cycle de 3 s)
        c.drawRoundRect(icone, 8.4f, 8.4f, traitIcone)
        val souffle = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 3f)
        point.alpha = (150 + 105 * souffle).toInt()
        c.drawCircle(54f, 54f, 3.6f, point)

        // 3) la comète, qui tourne autour du centre (30 degrés par seconde = un tour en 12 s).
        //    drawArc compte les angles en degrés, à partir de « 3 heures », dans le sens des aiguilles.
        val rotation = (t * 30f) % 360f
        c.drawArc(cercleComete, 140f + rotation, 10f, false, traine)    // la traîne
        c.drawArc(cercleComete, 150f + rotation, 170f, false, comete)   // le corps de la comète

        // 4) l'étincelle au bout de la comète (angle 150 + 170 = 320 degrés, plus la rotation)
        val angle = Math.toRadians((320f + rotation).toDouble())
        val ex = 54f + 28.8f * cos(angle).toFloat()
        val ey = 54f + 28.8f * sin(angle).toFloat()
        val scintille = 0.85f + 0.2f * (0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 2.4f))   // cycle de 2,4 s
        // un halo doux derrière l'étincelle (dégradé transparent : jamais de flash)
        halo.shader = RadialGradient(ex, ey, 9f,
            Color.argb((70 * scintille).toInt(), Color.red(couleur), Color.green(couleur), Color.blue(couleur)),
            Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(ex, ey, 9f, halo)
        c.save()
        c.translate(ex, ey)
        c.scale(scintille, scintille)
        c.drawPath(etoile, etincelle)
        c.restore()

        c.restore()
        // On redemande une image : Android rappellera onDraw au prochain rafraîchissement de l'écran.
        // (Quand l'écran principal n'est plus visible, Android arrête tout seul d'appeler onDraw.)
        postInvalidateOnAnimation()
    }
}
