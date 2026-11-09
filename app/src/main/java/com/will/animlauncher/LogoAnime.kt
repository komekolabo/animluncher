package com.will.animlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

// LE LOGO ANIMÉ, affiché en haut de l'écran principal (et dans le guide de déblocage).
// C'est le même dessin que l'icône de l'app (« Étincelle halo », voir res/drawable/logo_avant.xml),
// mais vivant :
//  - les trois coins du triangle « respirent » : ils s'écartent à peine du centre puis reviennent ;
//  - le halo derrière l'étoile s'éclaire et s'assombrit lentement, au même rythme ;
//  - l'étoile scintille (elle grossit un peu) et se balance de quelques degrés.
// Tout est lent et sans flash : on ne le remarque vraiment que quand on le regarde.
//
// Comme pour l'icône, on dessine sur une grille de 108 x 108, puis on agrandit à la taille de la vue.
class LogoAnime(context: Context) : View(context) {

    private val debut = SystemClock.uptimeMillis()                  // l'heure de départ de l'animation

    // Les « pinceaux » sont préparés une seule fois (en créer à chaque image ralentirait l'animation).
    private val fond = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.logo_fond) }
    private val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        color = Color.rgb(236, 239, 247)                            // blanc légèrement bleuté
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val etoile = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Le dégradé de l'étoile, en diagonale, d'une pointe à l'autre : bleu, vert, jaune, rouge.
        // L'étoile est dessinée autour de (0, 0) (voir formeEtoile), d'où les coordonnées -10,5 / +10,5.
        shader = LinearGradient(-10.5f, -10.5f, 10.5f, 10.5f,
            intArrayOf(Color.rgb(76, 141, 246), Color.rgb(52, 199, 123), Color.rgb(247, 201, 72), Color.rgb(240, 100, 90)),
            floatArrayOf(0f, 0.38f, 0.66f, 1f), Shader.TileMode.CLAMP)
    }

    // Les trois coins du triangle (en coordonnées 108 x 108) : chaque côté est coupé en son milieu.
    private val coinHaut = Path().apply { moveTo(44.5f, 45.2f); lineTo(54f, 29f); lineTo(63.5f, 45.2f) }
    private val coinGauche = Path().apply { moveTo(35.5f, 60.8f); lineTo(26f, 77f); lineTo(45f, 77f) }
    private val coinDroit = Path().apply { moveTo(63f, 77f); lineTo(82f, 77f); lineTo(72.5f, 60.8f) }
    // L'étoile à 4 branches, centrée sur (0, 0) : on la placera au centre du triangle (54, 61).
    private val formeEtoile = Path().apply {
        moveTo(0f, -10.5f); lineTo(2.4f, -2.4f); lineTo(10.5f, 0f); lineTo(2.4f, 2.4f)
        lineTo(0f, 10.5f); lineTo(-2.4f, 2.4f); lineTo(-10.5f, 0f); lineTo(-2.4f, -2.4f); close()
    }

    override fun onDraw(c: Canvas) {
        val t = (SystemClock.uptimeMillis() - debut) / 1000f     // secondes écoulées
        val s = min(width, height) / 108f                         // l'agrandissement 108 -> taille réelle
        c.save()
        c.translate((width - 108f * s) / 2f, (height - 108f * s) / 2f)
        c.scale(s, s)

        // 1) le fond rond (comme l'icône découpée par le téléphone)
        c.drawCircle(54f, 54f, 36f, fond)

        // Comme dans l'icône, le dessin est réduit à 80 % autour du centre pour respirer dans le rond.
        c.scale(0.8f, 0.8f, 54f, 54f)

        // « souffle » va de 0 à 1 puis revient, en 3,5 secondes : il rythme les coins et le halo.
        val souffle = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 3.5f)

        // 2) le halo : un disque dont la couleur jaune s'efface vers le bord (jamais de flash).
        //    Son intensité suit le souffle (entre 50 et 90 sur 255 : toujours très discret).
        halo.shader = RadialGradient(54f, 61f, 16f,
            Color.argb((50 + 40 * souffle).toInt(), 247, 201, 72), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(54f, 61f, 16f, halo)

        // 3) les trois coins, qui s'écartent du centre d'au plus 1,2 (sur 108) puis reviennent.
        //    Chaque coin part dans sa direction : le haut vers le haut, les deux autres en biais vers le bas.
        val e = 1.2f * souffle
        dessinerDecale(c, coinHaut, 0f, -e)
        dessinerDecale(c, coinGauche, -0.87f * e, 0.5f * e)
        dessinerDecale(c, coinDroit, 0.87f * e, 0.5f * e)

        // 4) l'étoile au centre : elle scintille (taille entre 92 % et 104 %, cycle de 2,4 s)
        //    et se balance doucement (± 6 degrés, cycle de 7 s).
        val scintille = 0.92f + 0.12f * (0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 2.4f))
        val balance = 6f * sin(t * 2f * PI.toFloat() / 7f)
        c.save()
        c.translate(54f, 61f)
        c.rotate(balance)
        c.scale(scintille, scintille)
        c.drawPath(formeEtoile, etoile)
        c.restore()

        c.restore()
        // On redemande une image : Android rappellera onDraw au prochain rafraîchissement de l'écran.
        // (Quand l'écran principal n'est plus visible, Android arrête tout seul d'appeler onDraw.)
        postInvalidateOnAnimation()
    }

    // Dessine un coin, décalé de (dx, dy).
    private fun dessinerDecale(c: Canvas, coin: Path, dx: Float, dy: Float) {
        c.save()
        c.translate(dx, dy)
        c.drawPath(coin, trait)
        c.restore()
    }
}
