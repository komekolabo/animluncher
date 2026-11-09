package com.will.animlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
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
// C'est un écran TRANSPARENT posé sur l'accueil : on voit l'accueil derrière.
// L'animation (version 2, validée par Will sur l'aperçu, en palette chaude) :
//  - deux bandes sombres arrivent du haut et du bas du téléphone et se rejoignent au milieu ;
//  - l'étoile du widget s'envole au centre en faisant un tour, le dossier s'ouvre, l'étoile se pose
//    à côté du titre ;
//  - des traînées de poussière partent vers la place de chaque app, qui ÉCLOT d'un point (effet ressort,
//    petite rotation qui se remet droite, onde fine autour), puis son nom glisse en place ;
//  - en dernier, la case « + » (Add) pour ajouter une app au groupe (ChoixAppActivity).
// On touche une app -> son animation d'ouverture habituelle se joue et l'app s'ouvre.
// On touche à côté (ou le bouton retour) -> le dossier se referme (les bandes repartent) et l'étoile
// retourne dans le widget.
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
        val v = DossierVue(this, nom, apps, intent.sourceBounds, { ouvrir(it) }, { ajouter(groupe) }, { fermer() })
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

    // On a touché le « + » : la liste des apps s'ouvre ; le dossier se rouvrira avec l'app choisie.
    private fun ajouter(groupe: String) {
        if (fini) return
        startActivity(Intent(this, ChoixAppActivity::class.java).putExtra("groupe", groupe).putExtra("depuisDossier", true))
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
    private val surAjout: () -> Unit,
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
    // la palette CHAUDE validée par Will : orange -> rose -> vert-jaune
    private val degradeEtoile = LinearGradient(-1f, -1f, 1f, 1f,
        intArrayOf(Color.rgb(255, 140, 91), Color.rgb(255, 107, 155), Color.rgb(196, 209, 63)),
        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)

    private fun lisse(x: Float) = x * x * (3 - 2 * x)               // départ et arrêt en douceur
    private fun seg(t: Float, a: Float, b: Float) = lisse(((t - a) / (b - a)).coerceIn(0f, 1f))
    private fun mix(a: Float, b: Float, u: Float) = a + (b - a) * u
    private fun freine(x: Float) = 1 - (1 - x) * (1 - x) * (1 - x)     // arrive vite, puis ralentit
    // l'effet RESSORT : dépasse un peu le but, puis revient
    private fun ressort(x: Float, tension: Float = 2.6f): Float { val y = x - 1; return y * y * ((tension + 1) * y + tension) + 1 }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        k = w / 540f
        val n = apps.size + 1          // les apps, plus la case « + »
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

        // 1) LES BANDES SOMBRES, qui arrivent du haut et du bas (et repartent à la fermeture)
        bandes(c, seg(t, 0f, 0.8f) * (1 - fer))

        // 2) LE PANNEAU du dossier, qui grandit un peu en apparaissant
        val p = seg(t, 0.85f, 1.45f) * (1 - fer)
        val tw = texte.apply { textSize = 28f * k }.measureText(nom)
        val etoileX = panneau.centerX() - (41f * k + tw) / 2 + 19f * k
        val etoileY = panneau.top + 47f * k
        if (p > 0) {
            val e = 0.88f + 0.12f * p
            c.save()
            c.scale(e, e, panneau.centerX(), panneau.centerY())
            pinceau.shader = LinearGradient(0f, panneau.top, 0f, panneau.bottom,
                Color.argb((235 * p).toInt(), 69, 42, 35), Color.argb((235 * p).toInt(), 41, 24, 20), Shader.TileMode.CLAMP)
            pinceau.style = Paint.Style.FILL
            c.drawRoundRect(panneau, 46f * k, 46f * k, pinceau)
            pinceau.shader = null
            pinceau.style = Paint.Style.STROKE
            pinceau.strokeWidth = 1.5f * k
            pinceau.color = Color.argb((22 * p).toInt(), 255, 255, 255)
            c.drawRoundRect(panneau, 46f * k, 46f * k, pinceau)
            pinceau.style = Paint.Style.FILL
            texte.color = Color.argb((235 * p).toInt(), 247, 239, 236)
            c.drawText(nom, etoileX + 22f * k, panneau.top + 56f * k, texte)
            c.restore()
        }

        // 3) LES TRAÎNÉES DE POUSSIÈRE puis LES APPS QUI ÉCLOSENT (découpées à la zone de la grille, qui peut défiler)
        c.save()
        c.clipRect(panneau.left, haut - 12f * k, panneau.right, haut + hauteurGrille + 6f * k)
        val cases = n + 1                     // les apps, puis la case « + »
        val pas = min(0.15f, 0.9f / cases)
        for (i in 0 until cases) {
            val plus = i == n
            val ax = placeX(i); val ay = placeY(i)
            val d = 1.05f + pas * i
            // la traînée (pas pour la case « + »)
            val tr = ((t - d) / 0.45f).coerceIn(0f, 1f)
            if (!plus && tr > 0 && tr < 1 && fer == 0f) {
                for (q in 0 until 10) {
                    val u = lisse((tr - q * 0.04f).coerceIn(0f, 1f))
                    val x = mix(panneau.centerX(), ax, u)
                    val y = mix(panneau.centerY() - 40f * k, ay, u) - 30f * k * sin(PI.toFloat() * u)
                    pinceau.color = Color.argb(((200 - 18 * q) * (1 - tr * 0.5f)).toInt(), 255, 196, 185)
                    c.drawCircle(x, y, (3.2f - q * 0.25f) * k, pinceau)
                }
            }
            // l'éclosion : un point qui devient l'icône (ressort), avec une petite rotation qui se remet droite
            val v = ((t - (d + 0.38f)) / 0.55f).coerceIn(0f, 1f)
            if (v <= 0) continue
            val alpha = (v / 0.25f).coerceIn(0f, 1f) * (1 - fer)
            val flot = 4f * k * sin(t * 2f + i * 1.6f)
            val appui = if (i == presse) 1 - 0.08f * sin(PI.toFloat() * ((t - presseA) / 0.25f).coerceIn(0f, 1f)) else 1f
            val e = (0.2f + 0.8f * ressort(v)) * appui
            val penche = -14f * (1 - freine(v))
            // l'onde fine qui s'écarte autour de l'app au moment où elle éclot (très discrète)
            if (!plus && v < 1) {
                pinceau.style = Paint.Style.STROKE
                pinceau.strokeWidth = 1.6f * k
                pinceau.color = Color.argb((70 * (1 - v) * alpha).toInt(), 255, 192, 175)
                c.drawCircle(ax, ay + flot, icone * (0.45f + 0.5f * freine(v)), pinceau)
                pinceau.style = Paint.Style.FILL
            }
            c.save()
            c.rotate(penche, ax, ay + flot)
            c.scale(e, e, ax, ay + flot)
            if (plus) casePlus(c, ax, ay + flot, icone * 0.86f, alpha)
            else images.getOrNull(i)?.let { b ->
                pinceau.alpha = (255 * alpha).toInt()
                c.drawBitmap(b, ax - b.width / 2f, ay + flot - b.height / 2f, pinceau)
                pinceau.alpha = 255
            }
            c.restore()
            // le nom glisse doucement en place, un peu après
            val nv = ((t - (d + 0.6f)) / 0.4f).coerceIn(0f, 1f) * (1 - fer)
            if (nv > 0) {
                texte.textSize = 19f * k
                texte.color = Color.argb((200 * nv).toInt(), 255, 255, 255)
                val etiquette = if (plus) "Add" else TextUtils.ellipsize(noms[i], texte, celluleL - 8f * k, TextUtils.TruncateAt.END).toString()
                c.drawText(etiquette, ax - texte.measureText(etiquette) / 2, ay + icone / 2 + 30f * k + 8f * k * (1 - freine(nv)), texte)
            }
        }
        c.restore()

        // 4) L'ÉTOILE : elle monte au centre en faisant un tour, puis se pose en petit à côté du titre ;
        //    à la fermeture, elle retourne dans le widget.
        val u = seg(t, 0f, 1.0f)
        val v = seg(t, 1.0f, 1.9f)        // elle rejoint le titre en douceur (0,9 s)
        var x = mix(mix(sx, panneau.centerX(), u), etoileX, v)
        var y = mix(mix(sy, panneau.centerY() - 40f * k, u), etoileY, v)
        var r = mix(mix(r0, 92f * k, u), 19f * k, v)
        if (fer > 0) { x = mix(x, sx, fer); y = mix(y, sy, fer); r = mix(r, r0, fer) }
        val lueur = 60f * (1 - v * 0.4f) * (1 - fer)
        // IMPORTANT : on remet le pinceau à « opaque ». Sinon il garde la transparence du dernier dessin
        // (une poussière presque invisible, l'onde d'une app…) et l'étoile disparaît ou clignote (vu le 09/11).
        pinceau.color = Color.WHITE
        pinceau.style = Paint.Style.FILL
        if (lueur > 1) {
            pinceau.shader = RadialGradient(x, y, r * 2.6f,
                intArrayOf(Color.argb(lueur.toInt(), 255, 107, 122), Color.argb((lueur * 0.4f).toInt(), 255, 75, 58), Color.TRANSPARENT),
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

    // LES BANDES SOMBRES : celle du haut descend depuis le haut de l'écran, celle du bas monte depuis le bas.
    // Leur bord est doux pendant le trajet, et devient net quand elles se rejoignent au milieu.
    private fun bandes(c: Canvas, ouv: Float) {
        if (ouv <= 0) return
        val w = width.toFloat(); val h = height.toFloat()
        val flou = 110f * k * (1 - ouv)
        val bordHaut = h / 2 * freine(ouv)
        val bordBas = h - h / 2 * freine(ouv)
        val plein = Color.argb(215, 12, 6, 5)
        val vide = Color.argb(0, 12, 6, 5)
        pinceau.color = plein
        c.drawRect(0f, 0f, w, max(0f, bordHaut - flou), pinceau)
        c.drawRect(0f, min(h, bordBas + flou), w, h, pinceau)
        if (flou > 0.5f) {
            pinceau.shader = LinearGradient(0f, bordHaut - flou, 0f, bordHaut, plein, vide, Shader.TileMode.CLAMP)
            c.drawRect(0f, bordHaut - flou, w, bordHaut, pinceau)
            pinceau.shader = LinearGradient(0f, bordBas, 0f, bordBas + flou, vide, plein, Shader.TileMode.CLAMP)
            c.drawRect(0f, bordBas, w, bordBas + flou, pinceau)
            pinceau.shader = null
        }
    }

    // LA CASE « + » : un carré arrondi en pointillés, avec un « + » au milieu.
    private fun casePlus(c: Canvas, x: Float, y: Float, s: Float, a: Float) {
        val r = RectF(x - s / 2, y - s / 2, x + s / 2, y + s / 2)
        pinceau.style = Paint.Style.FILL
        pinceau.color = Color.argb((14 * a).toInt(), 255, 255, 255)
        c.drawRoundRect(r, s * 0.26f, s * 0.26f, pinceau)
        pinceau.style = Paint.Style.STROKE
        pinceau.strokeWidth = 2f * k
        pinceau.color = Color.argb((150 * a).toInt(), 232, 184, 174)
        pinceau.pathEffect = DashPathEffect(floatArrayOf(9f * k, 7f * k), 0f)
        c.drawRoundRect(r, s * 0.26f, s * 0.26f, pinceau)
        pinceau.pathEffect = null
        pinceau.strokeWidth = 4f * k
        pinceau.strokeCap = Paint.Cap.ROUND
        pinceau.color = Color.argb((220 * a).toInt(), 255, 226, 220)
        c.drawLine(x - s * 0.17f, y, x + s * 0.17f, y, pinceau)
        c.drawLine(x, y - s * 0.17f, x, y + s * 0.17f, pinceau)
        pinceau.style = Paint.Style.FILL
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
                if (t < 1.2f) return true          // le dossier n'est pas encore ouvert
                for (i in 0..apps.size) {          // les apps, puis la case « + » (numéro apps.size)
                    val ay = placeY(i)
                    if (ay < haut - 8f * k || ay > haut + hauteurGrille) continue     // case cachée par le défilement
                    if (abs(e.x - placeX(i)) < celluleL / 2 && abs(e.y - ay - 14f * k) < celluleH / 2) {
                        presse = i
                        presseA = t
                        if (i == apps.size) postDelayed({ surAjout() }, 160)
                        else postDelayed({ surApp(apps[i]) }, 160)
                        return true
                    }
                }
            }
        }
        return true
    }
}
