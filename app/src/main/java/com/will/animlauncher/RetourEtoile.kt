package com.will.animlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min

// LE RETOUR DE L'ÉTOILE (09/11). Quand on quitte le dossier animé par le geste « accueil », Android retire
// l'écran du dossier tout de suite : il ne peut plus rien dessiner. Pour que l'étoile rentre quand même dans
// son widget (et que les bandes sombres repartent), on la fait dessiner par le SERVICE d'AnimLauncher, dans
// une petite fenêtre transparente posée par-dessus l'accueil (comme le rideau des animations), pendant
// 0,6 s. Puis la fenêtre s'enlève toute seule. Elle ne capte jamais les appuis.
object RetourEtoile {

    // Ce qu'il faut savoir pour dessiner le retour : où est l'étoile (x, y, taille, angle) au moment où on
    // quitte, où elle doit aller (le widget), et à quel point les bandes sombres étaient fermées (0 à 1).
    // Toutes les positions sont en pixels de l'ÉCRAN. teinte = les couleurs du dossier (roue chromatique).
    class Etat(val x: Float, val y: Float, val r: Float, val angle: Float,
               val versX: Float, val versY: Float, val versR: Float, val voile: Float,
               val teinte: Teinte = Teinte.ORIGINE)

    private const val DUREE = 600L

    // La fenêtre en cours (pour pouvoir l'effacer plus tôt, voir annuler), et l'heure à laquelle on a vu
    // l'écran des apps récentes s'ouvrir pour la dernière fois.
    private var enCours: View? = null
    private var recentsA = 0L

    // TROP TARD = PAS DU TOUT (09/11, vidéo de Will) : quand on part vers les apps récentes, Android
    // affiche notre fenêtre avec une demi-seconde de retard ; l'animation, déjà presque finie, passait
    // alors « giga vite » par-dessus les cartes. Si la première image arrive plus de 250 ms après le
    // départ, on n'affiche rien du tout.
    private const val RETARD_MAX = 250L

    // ON EST PARTI VERS LES APPS RÉCENTES (et pas vers l'accueil) : Android y montre déjà sa propre
    // animation (les cartes qui se rangent). Nos bandes sombres et l'étoile par-dessus faisaient une
    // transition moche (vu par Will le 09/11) : on efface tout en douceur, en 0,15 s.
    // Appelé par le service dès qu'il voit l'écran des apps récentes s'ouvrir.
    fun annuler() {
        recentsA = SystemClock.uptimeMillis()
        val vue = enCours ?: return
        enCours = null
        vue.animate().alpha(0f).setDuration(150).start()
    }

    fun jouer(service: AccessibilityService, etat: Etat) {
        // les apps récentes viennent de s'ouvrir (le signal est arrivé avant nous) : rien à dessiner
        if (SystemClock.uptimeMillis() - recentsA < 1000) return
        val gestionnaire = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val ecran = (service.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        val taille = Point()
        @Suppress("DEPRECATION") ecran.getRealSize(taille)
        val reglages = WindowManager.LayoutParams(
            taille.x, taille.y,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 0
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val vue = Vue(service, etat)
        try {
            gestionnaire.addView(vue, reglages)
        } catch (e: Exception) {
            Journal.noter(service, "ERREUR retour de l'étoile : ${e.message}")
            return
        }
        enCours = vue
        // on enlève la fenêtre une fois l'animation finie (avec une petite marge)
        vue.postDelayed({
            if (enCours === vue) enCours = null
            try { gestionnaire.removeView(vue) } catch (_: Exception) {}
        }, DUREE + 80)
    }

    // Le dessin : les bandes qui repartent vers le haut et le bas, et l'étoile qui rentre en tournant.
    private class Vue(c: Context, val e: Etat) : View(c) {
        private val debut = SystemClock.uptimeMillis()
        private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
        private val etoile = Path().apply {
            moveTo(0f, -1f)
            quadTo(0.085f, -0.085f, 1f, 0f)
            quadTo(0.085f, 0.085f, 0f, 1f)
            quadTo(-0.085f, 0.085f, -1f, 0f)
            quadTo(-0.085f, -0.085f, 0f, -1f)
            close()
        }
        private val degrade = LinearGradient(-1f, -1f, 1f, 1f, e.teinte.logo,
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)

        private fun lisse(x: Float) = x * x * (3 - 2 * x)
        private fun freine(x: Float) = 1 - (1 - x) * (1 - x) * (1 - x)
        private fun mix(a: Float, b: Float, u: Float) = a + (b - a) * u

        private var premiere = true
        private var abandon = false

        override fun onDraw(c: Canvas) {
            // la première image arrive trop tard, ou on est parti vers les apps récentes : on ne dessine rien
            if (premiere) {
                premiere = false
                if (SystemClock.uptimeMillis() - debut > RETARD_MAX) abandon = true
            }
            if (recentsA > debut) abandon = true
            if (abandon) return
            val f = ((SystemClock.uptimeMillis() - debut) / DUREE.toFloat()).coerceIn(0f, 1f)
            val k = lisse(f)
            // 1) les bandes repartent (même dessin que dans le dossier, en sens inverse)
            val ouv = e.voile * (1 - k)
            if (ouv > 0.01f) {
                val w = width.toFloat(); val h = height.toFloat()
                val flou = 110f * (w / 540f) * (1 - ouv)
                val bh = h / 2 * freine(ouv); val bb = h - h / 2 * freine(ouv)
                val plein = Color.argb(215, 12, 6, 5); val vide = Color.argb(0, 12, 6, 5)
                pinceau.color = plein
                c.drawRect(0f, 0f, w, max(0f, bh - flou), pinceau)
                c.drawRect(0f, min(h, bb + flou), w, h, pinceau)
                if (flou > 0.5f) {
                    pinceau.shader = LinearGradient(0f, bh - flou, 0f, bh, plein, vide, Shader.TileMode.CLAMP)
                    c.drawRect(0f, bh - flou, w, bh, pinceau)
                    pinceau.shader = LinearGradient(0f, bb, 0f, bb + flou, vide, plein, Shader.TileMode.CLAMP)
                    c.drawRect(0f, bb, w, bb + flou, pinceau)
                    pinceau.shader = null
                }
            }
            // 2) l'étoile rentre dans le widget en tournant (un demi-tour en arrière). Elle PASSE SOUS LA BOÎTE :
            //    on ne la dessine pas là où est le galet du widget (« clipOutPath » = tout sauf cette zone), et
            //    elle vise un peu plus bas que le centre, comme si elle se glissait dessous.
            val x = mix(e.x, e.versX, k); val y = mix(e.y, e.versY + 0.6f * e.versR, k); val r = mix(e.r, e.versR * 0.8f, k)
            c.save()
            if (k > 0.3f) c.clipOutPath(boite(e.versX, e.versY, e.versR))
            pinceau.color = Color.WHITE
            val lueur = 50f * (1 - k)
            if (lueur > 1) {
                pinceau.shader = RadialGradient(x, y, r * 2.6f,
                    intArrayOf(Teinte.avecAlpha(e.teinte.lueur, lueur), Teinte.avecAlpha(e.teinte.lueur2, lueur * 0.4f), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(x, y, r * 2.6f, pinceau)
                pinceau.shader = null
            }
            // à la toute fin, l'étoile se fond dans celle du widget
            pinceau.alpha = (255 * (1 - lisse(((f - 0.85f) / 0.15f).coerceIn(0f, 1f)))).toInt()
            c.save()
            c.translate(x, y)
            c.rotate(e.angle - 180f * k)
            c.scale(r, r)
            pinceau.shader = degrade
            c.drawPath(etoile, pinceau)
            pinceau.shader = null
            c.restore()
            c.restore()
            if (f < 1f) postInvalidateOnAnimation()
        }

        // La forme du galet du widget (62 dp de côté = 3,875 fois le rayon de l'étoile du widget), en carré
        // très arrondi : c'est la zone où l'étoile est « sous la boîte ».
        private fun boite(cx: Float, cy: Float, rEtoile: Float): Path {
            val demi = rEtoile * 3.875f / 2
            return Path().apply { addRoundRect(cx - demi, cy - demi, cx + demi, cy + demi, demi * 0.72f, demi * 0.72f, Path.Direction.CW) }
        }
    }
}
