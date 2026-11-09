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
// Un mini téléphone, au centre de la carte, joue en boucle (environ 20 s) :
//   1. l'écran d'accueil ; un doigt touche l'icône de Maps…
//   2. … l'animation de Maps (Paris) apparaît, puis se ferme en fondu sur
//   3. la tour de Babel de Claude (le monde « Bibliothèque »), puis
//   4. une partie d'échecs (Anderssen – Dufresne), puis
//   5. Duolingo « Bulles » (des « Bonjour » dans toutes les langues), et pour finir
//   6. la collision de galaxies de ChatGPT, qui se fond dans l'écran d'accueil… et ça recommence.
//
// Ce ne sont PAS des vidéos : ce sont les VRAIES scènes de l'app (celles du catalogue), dessinées
// en petit. Chacune croit qu'elle dessine sur tout l'écran du téléphone (même taille, même
// densité) : on l'enregistre dans une « RenderNode » (une liste de dessins, Android 10+), puis on
// la redessine réduite dans le mini téléphone. Ainsi, la démo est toujours fidèle à l'app.
class DemoAnimations(context: Context) : DessinVivant(context) {

    // Un « plan » de la démo : quelle scène, quand elle commence et finit (en secondes dans la
    // boucle), et la durée de son apparition (comme dans l'app, selon sa catégorie).
    private class Plan(val fabrique: () -> Scene, val debut: Float, val fin: Float, val introSec: Float)

    private val FONDU = 0.7f          // durée du fondu entre deux animations
    private val TAP = 1.2f            // le moment où le doigt touche l'icône de Maps
    private val BOUCLE = 20.2f        // la durée d'un tour complet
    private val plans = listOf(
        Plan({ Capitales(0) }, 1.6f, 5.2f, Categorie.ESSENTIELLE.intro / 1000f),        // Maps : Paris
        Plan({ Horizons(6) }, 4.5f, 9.0f, Categorie.ESSENTIELLE.intro / 1000f),         // Claude : Babel
        Plan({ CoupsBrillants(0) }, 8.3f, 13.3f, Categorie.ECHECS.intro / 1000f),       // échecs
        Plan({ DuoBulles() }, 12.6f, 16.4f, Categorie.COURANTE.intro / 1000f),          // Duolingo
        Plan({ Collision() }, 15.7f, BOUCLE, Categorie.ESSENTIELLE.intro / 1000f)       // ChatGPT : galaxies
    )
    // Les scènes sont fabriquées une seule fois (la première fois qu'on en a besoin), puis gardées.
    private val scenes = arrayOfNulls<Scene>(plans.size)

    // La taille du VRAI écran : c'est à cette taille que les scènes dessinent.
    private val ecranW = resources.displayMetrics.widthPixels
    private val ecranH = resources.displayMetrics.heightPixels
    private val outils = Outils(dp)

    // Deux « listes de dessins » : au plus deux animations sont visibles en même temps (pendant un fondu).
    // « compositingLayer » : chaque animation est dessinée à part, puis posée avec sa transparence
    // (sinon un fondu laisserait voir ses couches internes, et ses « gommes » trouerait la carte).
    private val noeuds: Array<RenderNode>? =
        if (Build.VERSION.SDK_INT >= 29) Array(2) { RenderNode("demo$it").apply { setUseCompositingLayer(true, null) } }
        else null

    // L'écran d'accueil du mini téléphone : 4 colonnes x 5 rangées d'icônes. On met les VRAIES
    // icônes de tes apps quand elles sont installées ; sinon, de simples carrés gris.
    private val COLONNES = 4
    private val RANGEES = 5
    private val RANG_MAPS = 13                 // la place de Maps dans la grille (rangée 4, colonne 2)
    private val icones: List<Drawable?> = run {
        val places = arrayOfNulls<String>(COLONNES * RANGEES)
        places[RANG_MAPS] = "com.google.android.apps.maps"
        places[2] = "com.anthropic.claude"; places[5] = "com.chess"
        places[8] = "com.duolingo"; places[10] = "com.openai.chatgpt"
        places.map { pkg -> pkg?.let { try { context.packageManager.getApplicationIcon(it) } catch (_: Exception) { null } } }
    }

    private val chemin = Path()
    private val cadre = RectF()
    private val visible = Rect()
    private val pFond = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pCase = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(26, 26, 30) }
    private val pMaps = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(52, 140, 90) }
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

        val tb = t % BOUCLE              // où on en est dans le tour
        c.save()
        c.clipPath(chemin)               // rien ne dépasse de l'écran arrondi du mini téléphone

        // 2) l'écran d'accueil (toujours dessous : on le revoit à la fin, quand les galaxies s'effacent)
        dessinerAccueil(c, pw, ph, tb)

        // 3) les animations. Si la carte n'est pas à l'écran (on a glissé vers une autre carte),
        //    on ne les dessine pas : inutile de faire travailler le téléphone pour rien.
        val n = noeuds
        if (n != null && c.isHardwareAccelerated && getGlobalVisibleRect(visible)) {
            var place = 0
            // De la DERNIÈRE à la première : pendant un fondu, l'animation qui s'en va est posée
            // par-dessus la suivante, et devient transparente petit à petit.
            for (k in plans.indices.reversed()) {
                val p = plans[k]
                if (tb < p.debut || tb > p.fin) continue
                val scene = scenes[k] ?: p.fabrique().also { scenes[k] = it }
                val local = tb - p.debut
                val alpha = when {
                    tb > p.fin - FONDU -> (p.fin - tb) / FONDU      // elle se ferme en fondu
                    k == 0 && local < 0.3f -> local / 0.3f          // Maps arrive vite, juste après le doigt
                    else -> 1f
                }
                val noeud = n[place++ % 2]
                noeud.setPosition(0, 0, ecranW, ecranH)
                val toile = noeud.beginRecording()
                try {
                    // « revelation » reste à 0 : dans la démo, on ne dévoile jamais d'app derrière
                    scene.dessiner(toile, outils, Instant(local, borne(local / p.introSec), 0f))
                } catch (_: Exception) {
                    // une scène qui a un souci ne doit pas faire planter l'accueil
                } finally {
                    noeud.endRecording()
                }
                noeud.setAlpha(borne(alpha))
                c.save()
                c.translate(gauche, haut)
                c.scale(pw / ecranW, ph / ecranH)     // on réduit l'écran entier à la taille du mini téléphone
                c.drawRenderNode(noeud)
                c.restore()
            }
        }
        c.restore()

        // 4) le contour du mini téléphone
        pBord.strokeWidth = 1.5f * dp
        c.drawRoundRect(cadre, rayon, rayon, pBord)
    }

    // L'écran d'accueil : un fond sombre, la grille d'icônes, et le doigt qui touche Maps.
    private fun dessinerAccueil(c: Canvas, pw: Float, ph: Float, tb: Float) {
        val x0 = cadre.left
        val y0 = cadre.top
        pFond.shader = LinearGradient(0f, y0, 0f, y0 + ph, Color.rgb(12, 14, 22), Color.rgb(5, 5, 6), Shader.TileMode.CLAMP)
        c.drawRect(cadre, pFond)
        val taille = pw * 0.15f
        val pasX = pw / COLONNES
        val pasY = ph * 0.13f
        val debutY = y0 + ph * 0.22f
        for (i in 0 until COLONNES * RANGEES) {
            val cx = x0 + pasX * (i % COLONNES + 0.5f)
            val cy = debutY + pasY * (i / COLONNES)
            // l'icône de Maps s'enfonce un peu quand le doigt la touche
            val enfonce = if (i == RANG_MAPS && tb > TAP && tb < TAP + 0.35f) 0.88f else 1f
            val m = taille / 2f * enfonce
            val d = icones[i]
            if (d != null) {
                d.setBounds((cx - m).toInt(), (cy - m).toInt(), (cx + m).toInt(), (cy + m).toInt())
                d.draw(c)
            } else {
                c.drawRoundRect(cx - m, cy - m, cx + m, cy + m, m * 0.5f, m * 0.5f, if (i == RANG_MAPS) pMaps else pCase)
            }
            // le doigt : un rond clair et doux qui s'élargit et s'efface (aucun flash)
            if (i == RANG_MAPS && tb > TAP && tb < TAP + 0.7f) {
                val p = (tb - TAP) / 0.7f
                pDoigt.color = Color.argb((90 * (1f - p)).toInt(), 237, 237, 237)
                c.drawCircle(cx, cy, taille * (0.45f + 0.6f * p), pDoigt)
            }
        }
        // la barre en bas de l'écran d'accueil, pour qu'on reconnaisse un téléphone
        val lb = min(pw * 0.3f, 60f * dp)
        pCase.alpha = 255
        c.drawRoundRect(x0 + (pw - lb) / 2f, y0 + ph - 10f * dp, x0 + (pw + lb) / 2f, y0 + ph - 7f * dp, 2f * dp, 2f * dp, pCase)
    }
}
