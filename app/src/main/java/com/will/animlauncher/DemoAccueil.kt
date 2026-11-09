package com.will.animlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.min

// LA DÉMO DE LA CARTE 1 (« Animations d'apps ») — demandée par Will le 09/11.
// Un mini téléphone, au centre de la carte, joue en boucle LE MÊME CIRCUIT pour 5 apps.
// On ne voit qu'UNE SEULE app à la fois (une grande icône au milieu de l'écran) :
//   l'icône de l'app → un doigt la touche → son animation se joue → elle s'efface en fondu
//   transparent, et dessous c'est déjà l'icône de l'app SUIVANTE qui arrive → le doigt la touche…
// Les 5 apps, dans l'ordre : Maps (Paris), Claude (la tour de Babel, monde « Bibliothèque »),
// les échecs (Anderssen – Dufresne), Duolingo « Bulles » (des « Bonjour » dans toutes les langues)
// et ChatGPT (la collision de galaxies). Puis ça recommence.
//
// Ce ne sont PAS des vidéos : ce sont les VRAIES scènes de l'app (celles du catalogue), dessinées
// en petit. Chacune croit qu'elle dessine sur tout l'écran du téléphone (même taille, même
// densité) : on l'enregistre dans une « RenderNode » (une liste de dessins, Android 10+), puis on
// la redessine réduite dans le mini téléphone. Ainsi, la démo est toujours fidèle à l'app.
class DemoAnimations(context: Context) : DessinVivant(context) {

    // Un « plan » de la démo = une app : sa scène, son nom technique (pour retrouver sa vraie
    // icône sur le téléphone), la couleur du carré de secours si l'app n'est pas installée,
    // combien de temps son animation reste à l'écran, et la durée de son apparition
    // (comme dans l'app, selon sa catégorie).
    private class Plan(val fabrique: () -> Scene, val pkg: String, val couleur: Int, val duree: Float, val introSec: Float)

    // Le circuit d'UNE app (en secondes, depuis le début de son tour) :
    private val TAP = 0.7f            // le doigt touche l'icône
    private val DEPART = 1.0f         // l'animation commence
    private val ENTREE = 0.3f         // elle apparaît en 0,3 s
    private val FONDU = 0.8f          // à la fin, elle s'efface en fondu transparent (on revoit l'accueil)
    private val plans = listOf(
        Plan({ Capitales(0) }, "com.google.android.apps.maps", Color.rgb(52, 140, 90), 3.4f, Categorie.ESSENTIELLE.intro / 1000f),      // Maps : Paris
        Plan({ Horizons(6) }, "com.anthropic.claude", Color.rgb(200, 110, 80), 4.0f, Categorie.ESSENTIELLE.intro / 1000f),        // Claude : Babel
        Plan({ CoupsBrillants(0) }, "com.chess", Color.rgb(100, 130, 60), 4.4f, Categorie.ECHECS.intro / 1000f),       // échecs
        Plan({ DuoBulles() }, "com.duolingo", Color.rgb(100, 170, 40), 3.4f, Categorie.COURANTE.intro / 1000f),           // Duolingo
        Plan({ Collision() }, "com.openai.chatgpt", Color.rgb(40, 140, 120), 4.0f, Categorie.ESSENTIELLE.intro / 1000f)        // ChatGPT : galaxies
    )
    // Le tour d'une app dure : DEPART + la durée de son animation. Le tour complet : la somme des 5.
    private val debuts = FloatArray(plans.size).also { d -> var x = 0f; for (k in plans.indices) { d[k] = x; x += DEPART + plans[k].duree } }
    private val BOUCLE = debuts.last() + DEPART + plans.last().duree
    // Les scènes sont fabriquées une seule fois (la première fois qu'on en a besoin), puis gardées.
    private val scenes = arrayOfNulls<Scene>(plans.size)

    // La taille du VRAI écran : c'est à cette taille que les scènes dessinent.
    private val ecranW = resources.displayMetrics.widthPixels
    private val ecranH = resources.displayMetrics.heightPixels
    private val outils = Outils(dp)

    // La « liste de dessins » où l'animation est enregistrée (une seule à la fois).
    // « compositingLayer » : l'animation est dessinée à part, puis posée avec sa transparence
    // (sinon le fondu laisserait voir ses couches internes, et ses « gommes » troueraient la carte).
    private val noeud: RenderNode? =
        if (Build.VERSION.SDK_INT >= 29) RenderNode("demo").apply { setUseCompositingLayer(true, null) } else null

    // La VRAIE icône de chaque app, si elle est installée sur le téléphone (sinon : rien, et on
    // dessinera un carré arrondi de sa couleur à la place).
    private val icones: List<Drawable?> = plans.map { p ->
        try { context.packageManager.getApplicationIcon(p.pkg) } catch (_: Exception) { null }
    }

    private val chemin = Path()
    private val cadre = RectF()
    private val visible = Rect()
    private val pFond = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pCase = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(26, 26, 30) }
    private val pDoigt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBord = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.rgb(39, 39, 42) }

    init {
        FilmCollision.preparer()      // le film des galaxies se calcule tout de suite, en arrière-plan
    }

    override fun dessiner(c: Canvas, w: Float, h: Float, t: Float) {
        // 1) la place du mini téléphone : toute la hauteur libre, et la largeur qui va avec
        //    (mêmes proportions que ton vrai écran), centré dans la carte
        val ph = h - 30f * dp
        val pw = ph * ecranW / ecranH
        val gauche = (w - pw) / 2f
        val haut = 20f * dp
        if (ph <= 0f || pw <= 0f) return
        cadre.set(gauche, haut, gauche + pw, haut + ph)
        val rayon = 18f * dp
        chemin.reset()
        chemin.addRoundRect(cadre, rayon, rayon, Path.Direction.CW)

        // Où en est-on ? D'abord dans le tour complet, puis : quelle app, et où dans SON circuit.
        val tb = t % BOUCLE
        var k = plans.size - 1
        while (k > 0 && tb < debuts[k]) k--
        val p = plans[k]
        val u = tb - debuts[k]           // le temps depuis le début du circuit de cette app

        c.save()
        c.clipPath(chemin)               // rien ne dépasse de l'écran arrondi du mini téléphone

        // 2) l'écran d'accueil, toujours dessous, avec UNE seule icône.
        //    Avant le départ de l'animation : l'icône de l'app du moment, que le doigt touche.
        //    Dès que l'animation couvre l'écran : on met dessous l'icône de l'app SUIVANTE.
        //    Quand l'animation s'efface en fondu, c'est donc elle qu'on découvre : elle grossit
        //    un peu en arrivant (de 85 % à 100 %), au même rythme que le fondu.
        val local = u - DEPART           // le temps depuis le début de l'animation (négatif avant)
        val alpha = when {
            local < 0f -> 0f
            local < ENTREE -> local / ENTREE                       // elle apparaît juste après le doigt
            local > p.duree - FONDU -> (p.duree - local) / FONDU   // elle s'efface en fondu transparent
            else -> 1f
        }
        if (local < ENTREE) dessinerAccueil(c, pw, ph, k, 1f, u)
        else dessinerAccueil(c, pw, ph, (k + 1) % plans.size, 0.85f + 0.15f * (1f - borne(alpha)), -1f)

        // 3) l'animation de l'app. Si la carte n'est pas à l'écran (on a glissé vers une autre
        //    carte), on ne la dessine pas : inutile de faire travailler le téléphone pour rien.
        val n = noeud
        if (u >= DEPART && n != null && c.isHardwareAccelerated && getGlobalVisibleRect(visible)) {
            val scene = scenes[k] ?: p.fabrique().also { scenes[k] = it }
            n.setPosition(0, 0, ecranW, ecranH)
            val toile = n.beginRecording()
            try {
                // « revelation » reste à 0 : dans la démo, c'est le fondu qui ramène l'accueil
                scene.dessiner(toile, outils, Instant(local, borne(local / p.introSec), 0f))
            } catch (_: Exception) {
                // une scène qui a un souci ne doit pas faire planter l'accueil
            } finally {
                n.endRecording()
            }
            n.setAlpha(borne(alpha))
            c.save()
            c.translate(gauche, haut)
            c.scale(pw / ecranW, ph / ecranH)     // on réduit l'écran entier à la taille du mini téléphone
            c.drawRenderNode(n)
            c.restore()
        }
        c.restore()

        // 4) le contour du mini téléphone
        pBord.strokeWidth = 1.5f * dp
        c.drawRoundRect(cadre, rayon, rayon, pBord)
    }

    // L'écran d'accueil du mini téléphone : un fond sombre et UNE grande icône au milieu.
    // « numero » = quelle app ; « echelle » = sa taille (1 = normale) ; « u » = le temps depuis le
    // début du circuit, pour dessiner le doigt (un nombre négatif = pas de doigt).
    private fun dessinerAccueil(c: Canvas, pw: Float, ph: Float, numero: Int, echelle: Float, u: Float) {
        val x0 = cadre.left
        val y0 = cadre.top
        pFond.shader = LinearGradient(0f, y0, 0f, y0 + ph, Color.rgb(12, 14, 22), Color.rgb(5, 5, 6), Shader.TileMode.CLAMP)
        c.drawRect(cadre, pFond)
        val cx = x0 + pw / 2f
        val cy = y0 + ph * 0.46f
        val touche = u > TAP && u < TAP + 0.35f               // le doigt est posé : l'icône s'enfonce un peu
        val m = pw * 0.19f * echelle * (if (touche) 0.9f else 1f)   // la demi-taille de l'icône
        val d = icones[numero]
        if (d != null) {
            d.setBounds((cx - m).toInt(), (cy - m).toInt(), (cx + m).toInt(), (cy + m).toInt())
            d.draw(c)
        } else {
            pCase.color = plans[numero].couleur
            c.drawRoundRect(cx - m, cy - m, cx + m, cy + m, m * 0.5f, m * 0.5f, pCase)
        }
        // le doigt : un rond clair et doux qui s'élargit et s'efface (aucun flash)
        if (u > TAP && u < TAP + 0.7f) {
            val p = (u - TAP) / 0.7f
            pDoigt.color = Color.argb((90 * (1f - p)).toInt(), 237, 237, 237)
            c.drawCircle(cx, cy, pw * 0.19f * (0.9f + 0.9f * p), pDoigt)
        }
        // la barre en bas de l'écran d'accueil, pour qu'on reconnaisse un téléphone
        val lb = min(pw * 0.3f, 60f * dp)
        pCase.color = Color.rgb(26, 26, 30)
        c.drawRoundRect(x0 + (pw - lb) / 2f, y0 + ph - 10f * dp, x0 + (pw + lb) / 2f, y0 + ph - 7f * dp, 2f * dp, 2f * dp, pCase)
    }
}
