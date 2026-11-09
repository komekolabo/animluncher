package com.will.animlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// LE DOSSIER ANIMÉ (09/11) : l'écran qui s'ouvre quand on touche un widget « dossier » (WidgetGroupe).
// C'est un écran TRANSPARENT posé sur l'accueil : on voit l'accueil derrière, qui s'assombrit.
// L'animation, validée par Will sur l'aperçu : l'étoile du widget s'envole au centre en faisant un
// tour, le dossier s'ouvre, l'étoile se pose à côté du titre, et 4 traînées de poussière partent vers
// les places des apps, qui apparaissent l'une après l'autre en flottant doucement.
// On touche une app -> son animation d'ouverture habituelle se joue et l'app s'ouvre.
// On touche à côté (ou le bouton retour) -> le dossier se referme et l'étoile retourne dans le widget.
class DossierActivity : Activity() {

    private var vue: DossierVue? = null
    private var fini = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val groupe = intent.getStringExtra("groupe") ?: run { finish(); return }
        val apps = Groupes.apps(this, groupe)
        val nom = Groupes.categorie(groupe)?.nom ?: "?"
        // On dessine aussi sous la barre d'état et la barre de navigation (tout l'écran s'assombrit).
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        // sourceBounds = la place du widget sur l'écran (donnée par l'écran d'accueil) : l'étoile part de là.
        val v = DossierVue(this, nom, apps, intent.sourceBounds, { ouvrir(it) }, { fermer() })
        vue = v
        setContentView(v)
    }

    // On a touché une app : l'animation d'AnimLauncher d'abord (comme un appui sur une icône), puis l'app.
    private fun ouvrir(app: String) {
        if (fini) return
        try { AppWatcherService.actif?.depuisDossier(app) } catch (_: Exception) {}
        packageManager.getLaunchIntentForPackage(app)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { startActivity(it) } catch (_: Exception) {}
        }
        fermer()
    }

    // On s'en va sans l'animation de fermeture d'Android (c'est nous qui animons).
    private fun fermer() {
        if (fini) return
        fini = true
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    @Deprecated("Bouton retour")
    override fun onBackPressed() {
        vue?.refermer() ?: fermer()
    }

    // L'utilisateur quitte l'écran autrement (bouton accueil, écran éteint…) : le dossier disparaît.
    override fun onPause() {
        super.onPause()
        fermer()
    }
}

// LE DESSIN DU DOSSIER. Comme les scènes des animations, on redessine toute l'image à chaque instant,
// à partir du temps écoulé (t, en secondes). Tailles calibrées sur un écran « modèle » de 540 de large.
class DossierVue(
    c: Context,
    private val nom: String,
    private val apps: List<String>,
    private val depart: Rect?,
    private val surApp: (String) -> Unit,
    private val surFin: () -> Unit
) : View(c) {

    private val noms = apps.map { Groupes.nomApp(c, it) }
    private val icones: List<Drawable?> = apps.map { try { c.packageManager.getApplicationIcon(it) } catch (_: Exception) { null } }
    private var images: List<Bitmap?> = emptyList()      // les icônes, dessinées une fois à la bonne taille

    private val debut = SystemClock.uptimeMillis()
    private var fermeA = -1f            // l'instant où on a demandé de refermer (-1 = pas encore)
    private var termine = false
    private var presse = -1             // l'app touchée
    private var presseA = 0f

    // la mise en page (calculée quand on connaît la taille de l'écran)
    private var k = 1f
    private var colonnes = 2
    private var icone = 96f
    private var panneau = RectF()
    private var celluleL = 0f
    private var celluleH = 0f
    private var haut = 0f              // le haut de la grille des apps
    private var hauteurGrille = 0f
    private var defilementMax = 0f
    private var defilement = 0f
    private var sx = 0f; private var sy = 0f; private var r0 = 0f    // l'étoile du widget, au départ

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val texte = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val etoile = Path().apply {      // l'étoile à 4 branches, de rayon 1 (on l'agrandit au dessin)
        moveTo(0f, -1f)
        quadTo(0.085f, -0.085f, 1f, 0f)
        quadTo(0.085f, 0.085f, 0f, 1f)
        quadTo(-0.085f, 0.085f, -1f, 0f)
        quadTo(-0.085f, -0.085f, 0f, -1f)
        close()
    }
    private val degradeEtoile = LinearGradient(-1f, -1f, 1f, 1f,
        intArrayOf(Color.rgb(91, 140, 255), Color.rgb(155, 107, 255), Color.rgb(63, 209, 196)),
        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)

    private fun lisse(x: Float) = x * x * (3 - 2 * x)               // départ et arrêt en douceur
    private fun seg(t: Float, a: Float, b: Float) = lisse(((t - a) / (b - a)).coerceIn(0f, 1f))
    private fun mix(a: Float, b: Float, u: Float) = a + (b - a) * u

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        k = w / 540f
        val n = apps.size
        colonnes = when { n <= 4 -> 2; n <= 9 -> 3; else -> 4 }
        icone = when (colonnes) { 2 -> 96f; 3 -> 84f; else -> 70f } * k
        val largeur = min(440f * k, w - 40f * k)
        celluleL = (largeur - 40f * k) / colonnes
        celluleH = icone + 62f * k
        val lignes = max(1, ceil(n / colonnes.toFloat()).toInt())
        hauteurGrille = min(lignes * celluleH, h * 0.62f)
        defilementMax = max(0f, lignes * celluleH - hauteurGrille)
        val hauteur = 96f * k + hauteurGrille + 26f * k
        val cx = w / 2f; val cy = h * 0.47f
        panneau = RectF(cx - largeur / 2, cy - hauteur / 2, cx + largeur / 2, cy + hauteur / 2)
        haut = panneau.top + 96f * k

        // Le départ de l'étoile : le galet du widget (en haut de la case, au-dessus du nom).
        val ici = IntArray(2); getLocationOnScreen(ici)
        val d = resources.displayMetrics.density
        if (depart != null) {
            sx = depart.exactCenterX() - ici[0]
            sy = depart.exactCenterY() - ici[1] - 10.5f * d
        } else { sx = cx; sy = h * 0.75f }
        r0 = 16f * d

        // Les icônes, dessinées une fois pour toutes à la bonne taille.
        val taille = icone.toInt().coerceAtLeast(1)
        images = icones.map { dessin ->
            dessin?.let {
                val b = Bitmap.createBitmap(taille, taille, Bitmap.Config.ARGB_8888)
                it.setBounds(0, 0, taille, taille)
                it.draw(Canvas(b))
                b
            }
        }
    }

    // La place (le centre) de l'app i dans le dossier.
    private fun placeX(i: Int) = panneau.left + 20f * k + celluleL * (i % colonnes + 0.5f)
    private fun placeY(i: Int) = haut + celluleH * (i / colonnes) + icone / 2 + 8f * k - defilement

    fun refermer() {
        if (fermeA < 0) fermeA = (SystemClock.uptimeMillis() - debut) / 1000f
    }

    override fun onDraw(c: Canvas) {
        val t = (SystemClock.uptimeMillis() - debut) / 1000f
        val tf = if (fermeA < 0) 0f else t - fermeA
        val fer = if (fermeA < 0) 0f else seg(tf, 0f, 0.55f)
        val n = apps.size

        // 1) LE VOILE SOMBRE sur l'accueil
        val sombre = seg(t, 0f, 0.95f) * (1 - fer)
        c.drawColor(Color.argb((215 * sombre).toInt(), 5, 6, 12))

        // 2) LE PANNEAU du dossier, qui grandit un peu en apparaissant
        val p = seg(t, 1.0f, 1.65f) * (1 - fer)
        val tw = texte.apply { textSize = 28f * k }.measureText(nom)
        val etoileX = panneau.centerX() - (41f * k + tw) / 2 + 19f * k
        val etoileY = panneau.top + 47f * k
        if (p > 0) {
            val e = 0.88f + 0.12f * p
            c.save()
            c.scale(e, e, panneau.centerX(), panneau.centerY())
            pinceau.shader = LinearGradient(0f, panneau.top, 0f, panneau.bottom,
                Color.argb((235 * p).toInt(), 35, 42, 69), Color.argb((235 * p).toInt(), 20, 24, 41), Shader.TileMode.CLAMP)
            pinceau.style = Paint.Style.FILL
            c.drawRoundRect(panneau, 46f * k, 46f * k, pinceau)
            pinceau.shader = null
            pinceau.style = Paint.Style.STROKE
            pinceau.strokeWidth = 1.5f * k
            pinceau.color = Color.argb((22 * p).toInt(), 255, 255, 255)
            c.drawRoundRect(panneau, 46f * k, 46f * k, pinceau)
            pinceau.style = Paint.Style.FILL
            texte.color = Color.argb((235 * p).toInt(), 236, 239, 247)
            c.drawText(nom, etoileX + 22f * k, panneau.top + 56f * k, texte)
            c.restore()
            if (n == 0) {
                texte.textSize = 19f * k
                texte.color = Color.argb((180 * p).toInt(), 255, 255, 255)
                val m = "No apps in this group"
                c.drawText(m, panneau.centerX() - texte.measureText(m) / 2, haut + 40f * k, texte)
            }
        }

        // 3) LES TRAÎNÉES DE POUSSIÈRE puis LES APPS (découpées à la zone de la grille, qui peut défiler)
        c.save()
        c.clipRect(panneau.left, haut - 12f * k, panneau.right, haut + hauteurGrille + 6f * k)
        val pas = min(0.15f, 0.9f / max(1, n))
        for (i in 0 until n) {
            val ax = placeX(i); val ay = placeY(i)
            val d = 1.2f + pas * i
            val tr = seg(t, d, d + 0.45f)
            if (tr > 0 && tr < 1 && fer == 0f) {
                for (q in 0 until 10) {
                    val u = (tr - q * 0.04f).coerceIn(0f, 1f)
                    val x = mix(panneau.centerX(), ax, u)
                    val y = mix(panneau.centerY() - 40f * k, ay, u) - 30f * k * sin(PI.toFloat() * u)
                    pinceau.color = Color.argb(((200 - 18 * q) * (1 - tr * 0.5f)).toInt(), 185, 196, 255)
                    c.drawCircle(x, y, (3.2f - q * 0.25f) * k, pinceau)
                }
            }
            val ap = seg(t, d + 0.3f, d + 0.75f) * (1 - fer)
            if (ap <= 0) continue
            val flot = 4f * k * sin(t * 2f + i * 1.6f)
            val appui = if (i == presse) 1 - 0.08f * sin(PI.toFloat() * ((t - presseA) / 0.25f).coerceIn(0f, 1f)) else 1f
            val e = (0.6f + 0.4f * ap) * appui
            images.getOrNull(i)?.let { b ->
                c.save()
                c.scale(e, e, ax, ay + flot)
                pinceau.alpha = (255 * ap).toInt()
                c.drawBitmap(b, ax - b.width / 2f, ay + flot - b.height / 2f, pinceau)
                pinceau.alpha = 255
                c.restore()
            }
            texte.textSize = 19f * k
            texte.color = Color.argb((200 * ap).toInt(), 255, 255, 255)
            val etiquette = TextUtils.ellipsize(noms[i], texte, celluleL - 8f * k, TextUtils.TruncateAt.END).toString()
            c.drawText(etiquette, ax - texte.measureText(etiquette) / 2, ay + icone / 2 + 30f * k, texte)
        }
        c.restore()

        // 4) L'ÉTOILE : elle monte au centre en faisant un tour, puis se pose en petit à côté du titre ;
        //    à la fermeture, elle retourne dans le widget.
        val u = seg(t, 0f, 1.05f)
        val v = seg(t, 1.05f, 1.75f)
        var x = mix(mix(sx, panneau.centerX(), u), etoileX, v)
        var y = mix(mix(sy, panneau.centerY() - 40f * k, u), etoileY, v)
        var r = mix(mix(r0, 92f * k, u), 19f * k, v)
        if (fer > 0) { x = mix(x, sx, fer); y = mix(y, sy, fer); r = mix(r, r0, fer) }
        val lueur = 60f * (1 - v * 0.4f) * (1 - fer)
        if (lueur > 1) {
            pinceau.shader = RadialGradient(x, y, r * 2.6f,
                intArrayOf(Color.argb(lueur.toInt(), 122, 107, 255), Color.argb((lueur * 0.4f).toInt(), 58, 75, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r * 2.6f, pinceau)
            pinceau.shader = null
        }
        c.save()
        c.translate(x, y)
        c.rotate(t * 18f + 360f * u + 90f * v)
        c.scale(r, r)
        pinceau.shader = degradeEtoile
        c.drawPath(etoile, pinceau)
        pinceau.shader = null
        c.restore()

        // la fermeture est finie : on s'en va
        if (fermeA >= 0 && tf >= 0.6f) {
            if (!termine) { termine = true; surFin() }
            return
        }
        postInvalidateOnAnimation()
    }

    // LES APPUIS : toucher une app l'ouvre ; toucher à côté du dossier le referme ;
    // glisser le doigt fait défiler les apps (s'il y en a trop pour la hauteur du dossier).
    private var basX = 0f; private var basY = 0f; private var basDefilement = 0f; private var glisse = false

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val t = (SystemClock.uptimeMillis() - debut) / 1000f
        if (fermeA >= 0 || presse >= 0) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { basX = e.x; basY = e.y; basDefilement = defilement; glisse = false }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.y - basY) > 12f * k) glisse = true
                if (glisse) defilement = (basDefilement - (e.y - basY)).coerceIn(0f, defilementMax)
            }
            MotionEvent.ACTION_UP -> {
                if (glisse) return true
                if (!panneau.contains(e.x, e.y)) { refermer(); return true }
                if (t < 1.3f) return true          // le dossier n'est pas encore ouvert
                for (i in apps.indices) {
                    val ay = placeY(i)
                    if (ay < haut - 8f * k || ay > haut + hauteurGrille) continue     // app cachée par le défilement
                    if (abs(e.x - placeX(i)) < celluleL / 2 && abs(e.y - ay - 14f * k) < celluleH / 2) {
                        presse = i
                        presseA = t
                        postDelayed({ surApp(apps[i]) }, 160)
                        return true
                    }
                }
            }
        }
        return true
    }
}
