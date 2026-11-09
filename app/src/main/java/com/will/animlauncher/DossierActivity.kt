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
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// LE DOSSIER ANIMÉ (09/11) : l'écran qui s'ouvre quand on touche un widget « dossier » (WidgetGroupe).
// C'est un écran TRANSPARENT posé sur l'accueil : on voit l'accueil derrière.
// L'animation (version 5, validée par Will sur les aperçus, en palette chaude) :
//  - deux bandes sombres arrivent du haut et du bas du téléphone et se rejoignent au milieu ;
//  - l'étoile du widget s'envole au centre en faisant un tour, le dossier s'ouvre, l'étoile se pose
//    à côté du titre ;
//  - des traînées de poussière partent vers la place de chaque app, qui ÉCLOT d'un point (effet ressort,
//    petite rotation qui se remet droite, onde fine autour), puis son nom glisse en place ;
//  - sous chaque nom, une INFO calculée en direct : « Open » (encore ouverte, point vert) ou depuis quand
//    on ne l'a pas ouverte (« 5m ago ») ; sous le titre : « 5 apps · 2 open » ;
//  - la bulle « + » (ajouter une app) : un arc qui épouse le coin en haut à droite, à distance. À partir de
//    55 apps, elle affiche le nombre de places restantes (60 au plus) ;
//  - le cadre grandit vers le bas jusqu'à 4 lignes ; au-delà, des PAGES : on glisse vers la gauche.
// Toucher une app l'ouvre TOUT DE SUITE (même avant la fin de l'animation, dès 0,4 s).
// Toucher à côté (ou le bouton retour) referme le dossier et l'étoile rentre dans le widget ; le geste
// « accueil » aussi : là, c'est le service d'AnimLauncher qui dessine le retour (voir RetourEtoile.kt).
class DossierActivity : Activity() {

    private var vue: DossierVue? = null
    private var fini = false
    private var groupe = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupe = intent.getStringExtra("groupe") ?: run { finish(); return }
        val apps = Groupes.apps(this, groupe)
        val nom = Groupes.categorie(groupe)?.nom ?: "?"
        // On dessine aussi sous la barre d'état et la barre de navigation (tout l'écran s'assombrit).
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        // sourceBounds = la place du widget sur l'écran (donnée par l'écran d'accueil) : l'étoile part de là.
        val v = DossierVue(this, nom, apps, intent.sourceBounds, { ouvrir(it) }, { ajouter() }, { fermer() })
        vue = v
        setContentView(v)
    }

    // On a touché une app : l'animation d'AnimLauncher d'abord (comme un appui sur une icône), puis l'app,
    // tout de suite.
    private fun ouvrir(app: String) {
        if (fini) return
        try { AppWatcherService.actif?.depuisDossier(app) } catch (_: Exception) {}
        packageManager.getLaunchIntentForPackage(app)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { startActivity(it) } catch (_: Exception) {}
        }
        fermer()
    }

    // On a touché la bulle « + » : la liste des apps s'ouvre ; le dossier se rouvrira avec l'app choisie.
    private fun ajouter() {
        if (fini) return
        if (Groupes.apps(this, groupe).size >= Groupes.MAX) {
            Toast.makeText(this, "Dossier plein : ${Groupes.MAX} apps maximum", Toast.LENGTH_SHORT).show()
            return
        }
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

    // L'utilisateur quitte autrement (geste « accueil », apps récentes, écran éteint…) : l'écran du dossier
    // disparaît tout de suite. On demande au service de dessiner le retour de l'étoile dans le widget.
    override fun onPause() {
        super.onPause()
        if (!fini) {
            val service = AppWatcherService.actif
            val etat = vue?.etatPourRetour()
            if (service != null && etat != null) {
                try { RetourEtoile.jouer(service, etat) } catch (_: Exception) {}
            }
        }
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

    // LES INFOS, calculées une fois à l'ouverture : l'app est-elle encore ouverte ? depuis quand ne l'a-t-on
    // pas ouverte ? (le service d'AnimLauncher le sait : il voit passer les apps à l'écran)
    private val ouvertes: List<Boolean> = apps.map { AppWatcherService.actif?.estOuverte(it) ?: false }
    private val infos: List<String?> = apps.mapIndexed { i, pkg -> if (ouvertes[i]) "Open" else depuis(AppWatcherService.derniereOuverture(c, pkg)) }
    private val resume = apps.size.let { n ->
        val o = ouvertes.count { it }
        (if (n == 1) "1 app" else "$n apps") + if (o > 0) "  ·  $o open" else ""
    }

    // « 5m ago », « 2h ago », « Yesterday »… (en anglais, comme tous les textes des animations)
    private fun depuis(heure: Long): String? {
        if (heure <= 0) return null
        val min = (System.currentTimeMillis() - heure) / 60_000
        return when {
            min < 1 -> "Just now"
            min < 60 -> "${min}m ago"
            min < 24 * 60 -> "${min / 60}h ago"
            min < 48 * 60 -> "Yesterday"
            else -> "${min / (24 * 60)}d ago"
        }
    }

    private val debut = SystemClock.uptimeMillis()
    private var fermeA = -1f            // l'instant où on a demandé de refermer (-1 = pas encore)
    private var termine = false
    private var presse = -1             // l'app touchée
    private var presseA = 0f

    // la mise en page (calculée quand on connaît la taille de l'écran)
    private var k = 1f
    private var colonnes = 3
    private var parPage = 12            // 3 colonnes x 4 lignes
    private var pages = 1
    private var icone = 84f
    private var panneau = RectF()
    private var celluleL = 0f
    private var celluleH = 0f
    private var haut = 0f              // le haut de la grille des apps
    private var sx = 0f; private var sy = 0f; private var r0 = 0f    // l'étoile du widget, au départ
    // l'étoile, telle qu'elle est dessinée à l'instant (pour le retour par le service)
    private var etoileX = 0f; private var etoileY = 0f; private var etoileR = 0f; private var etoileA = 0f
    private var voile = 0f
    // LES PAGES : page = la page affichée (0, 1, 2… avec des valeurs entre deux pendant le glissement)
    private var page = 0f
    private var pageCible = 0f
    private var glissementA = -1L

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val texte = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val gras = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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
        val n = apps.size
        // 2 colonnes pour 4 apps ou moins, sinon 3 ; au plus 4 lignes par page, puis des pages
        colonnes = if (n <= 4) 2 else 3
        parPage = colonnes * 4
        pages = max(1, ceil(n / parPage.toFloat()).toInt())
        icone = (if (colonnes == 2) 96f else 84f) * k
        val largeur = min(440f * k, w - 100f * k)
        celluleL = (largeur - 40f * k) / colonnes
        celluleH = icone + 80f * k                    // l'icône, son nom, et la ligne d'info
        val lignes = max(1, ceil(min(n, parPage) / colonnes.toFloat()).toInt())
        val hauteur = 110f * k + lignes * celluleH + (if (pages > 1) 34f else 16f) * k
        val cx = w / 2f; val cy = h * 0.47f
        panneau = RectF(cx - largeur / 2, cy - hauteur / 2, cx + largeur / 2, cy + hauteur / 2)
        haut = panneau.top + 110f * k

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

    // La place (le centre) de l'app i : sa page, puis sa case dans la page. Les pages sont côte à côte.
    private fun placeX(i: Int): Float {
        val p = i / parPage; val j = i % parPage
        return panneau.left + 20f * k + celluleL * (j % colonnes + 0.5f) + (p - page) * panneau.width()
    }
    private fun placeY(i: Int): Float = haut + celluleH * ((i % parPage) / colonnes) + icone / 2 + 6f * k

    // La bulle « + » : le centre de l'arrondi du coin en haut à droite, et le rayon de l'arc.
    private fun coinX() = panneau.right - 46f * k
    private fun coinY() = panneau.top + 46f * k
    private fun rayonBulle() = (46f + 14f + 19f) * k
    private fun bulleX() = coinX() + rayonBulle() * cos(-PI.toFloat() / 4)
    private fun bulleY() = coinY() + rayonBulle() * sin(-PI.toFloat() / 4)

    fun refermer() {
        if (fermeA < 0) fermeA = (SystemClock.uptimeMillis() - debut) / 1000f
    }

    // Où en est l'étoile (en pixels de l'ÉCRAN) : pour que le service dessine son retour dans le widget.
    fun etatPourRetour(): RetourEtoile.Etat? {
        if (width == 0) return null
        val ici = IntArray(2); getLocationOnScreen(ici)
        return RetourEtoile.Etat(etoileX + ici[0], etoileY + ici[1], etoileR, etoileA,
            sx + ici[0], sy + ici[1], r0, voile)
    }

    override fun onDraw(c: Canvas) {
        val t = (SystemClock.uptimeMillis() - debut) / 1000f
        val tf = if (fermeA < 0) 0f else t - fermeA
        val fer = if (fermeA < 0) 0f else seg(tf, 0f, 0.55f)
        val n = apps.size

        // le glissement de page en cours : on rejoint la page visée en douceur (0,3 s)
        if (glissementA >= 0) {
            val g = ((SystemClock.uptimeMillis() - glissementA) / 300f).coerceIn(0f, 1f)
            page = mix(pageDepart, pageCible, freine(g))
            if (g >= 1f) glissementA = -1
        }

        // 1) LES BANDES SOMBRES, qui arrivent du haut et du bas (et repartent à la fermeture)
        voile = seg(t, 0f, 0.8f) * (1 - fer)
        bandes(c, voile)

        // 2) LE PANNEAU du dossier, qui grandit un peu en apparaissant
        val p = seg(t, 0.85f, 1.45f) * (1 - fer)
        texte.typeface = gras
        val tw = texte.apply { textSize = 28f * k }.measureText(nom)
        val titreX = panneau.centerX() - (41f * k + tw) / 2 + 19f * k
        val titreY = panneau.top + 44f * k
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
            c.drawText(nom, titreX + 22f * k, panneau.top + 54f * k, texte)
            // la ligne d'infos sous le titre (« 5 apps · 2 open »)
            texte.typeface = Typeface.DEFAULT
            texte.textSize = 17f * k
            texte.color = Color.argb((150 * p).toInt(), 247, 239, 236)
            c.drawText(resume, panneau.centerX() - texte.measureText(resume) / 2, panneau.top + 84f * k, texte)
            // les petits points des pages, en bas du cadre
            if (pages > 1) for (q in 0 until pages) {
                val proche = (1 - abs(page - q)).coerceIn(0f, 1f)
                pinceau.color = Color.argb(((60 + 160 * proche) * p).toInt(), 255, 226, 220)
                c.drawCircle(panneau.centerX() + (q - (pages - 1) / 2f) * 16f * k, panneau.bottom - 18f * k, (3f + 1.2f * proche) * k, pinceau)
            }
            c.restore()
        }
        texte.typeface = Typeface.DEFAULT

        // 3) LES TRAÎNÉES DE POUSSIÈRE puis LES APPS QUI ÉCLOSENT (dans le cadre : les pages voisines sont cachées)
        c.save()
        c.clipRect(panneau.left + 4f * k, haut - 12f * k, panneau.right - 4f * k, panneau.bottom - 4f * k)
        val premiere = min(n, parPage)                 // les apps de la page 1 éclosent l'une après l'autre
        val pas = min(0.15f, 0.9f / max(1, premiere))
        for (i in 0 until n) {
            val ax = placeX(i); val ay = placeY(i)
            if (ax < panneau.left - celluleL || ax > panneau.right + celluleL) continue      // page pas visible
            val d = if (i < parPage) 1.05f + pas * i else 1.2f
            // la traînée (seulement pour la page 1, pendant l'ouverture)
            val tr = ((t - d) / 0.45f).coerceIn(0f, 1f)
            if (i < parPage && tr > 0 && tr < 1 && fer == 0f && page < 0.5f) {
                for (q in 0 until 10) {
                    val u = lisse((tr - q * 0.04f).coerceIn(0f, 1f))
                    val x = mix(panneau.centerX(), ax, u)
                    val y = mix(panneau.centerY() - 40f * k, ay, u) - 30f * k * sin(PI.toFloat() * u)
                    pinceau.color = Color.argb(((200 - 18 * q) * (1 - tr * 0.5f)).toInt(), 255, 196, 185)
                    c.drawCircle(x, y, (3.2f - q * 0.25f) * k, pinceau)
                }
            }
            // l'éclosion : un point qui devient l'icône (ressort), avec une petite rotation qui se remet droite.
            // L'app qu'on vient de toucher apparaît tout de suite (même si son tour n'était pas encore venu).
            var v = ((t - (d + 0.38f)) / 0.55f).coerceIn(0f, 1f)
            if (i == presse) v = max(v, ((t - presseA) / 0.15f).coerceIn(0f, 1f))
            if (v <= 0) continue
            val alpha = (v / 0.25f).coerceIn(0f, 1f) * (1 - fer)
            val flot = 4f * k * sin(t * 2f + i * 1.6f)
            val appui = if (i == presse) 1 - 0.08f * sin(PI.toFloat() * ((t - presseA) / 0.15f).coerceIn(0f, 1f)) else 1f
            val e = (0.2f + 0.8f * ressort(v)) * appui
            val penche = -14f * (1 - freine(v))
            // l'onde fine qui s'écarte autour de l'app au moment où elle éclot (très discrète)
            if (v < 1) {
                pinceau.style = Paint.Style.STROKE
                pinceau.strokeWidth = 1.6f * k
                pinceau.color = Color.argb((70 * (1 - v) * alpha).toInt(), 255, 192, 175)
                c.drawCircle(ax, ay + flot, icone * (0.45f + 0.5f * freine(v)), pinceau)
                pinceau.style = Paint.Style.FILL
            }
            c.save()
            c.rotate(penche, ax, ay + flot)
            c.scale(e, e, ax, ay + flot)
            images.getOrNull(i)?.let { b ->
                pinceau.color = Color.WHITE
                pinceau.alpha = (255 * alpha).toInt()
                c.drawBitmap(b, ax - b.width / 2f, ay + flot - b.height / 2f, pinceau)
                pinceau.alpha = 255
            }
            c.restore()
            // le nom, puis l'info en dessous, glissent doucement en place un peu après
            val nv = ((t - (d + 0.6f)) / 0.4f).coerceIn(0f, 1f) * (1 - fer)
            if (nv > 0) {
                val glisse = 8f * k * (1 - freine(nv))
                texte.textSize = 19f * k
                texte.color = Color.argb((205 * nv).toInt(), 255, 255, 255)
                val etiquette = TextUtils.ellipsize(noms[i], texte, celluleL - 8f * k, TextUtils.TruncateAt.END).toString()
                c.drawText(etiquette, ax - texte.measureText(etiquette) / 2, ay + icone / 2 + 28f * k + glisse, texte)
                infos[i]?.let { info ->
                    texte.textSize = 15f * k
                    val larg = texte.measureText(info)
                    val y2 = ay + icone / 2 + 49f * k + glisse
                    if (ouvertes[i]) {
                        pinceau.color = Color.argb((220 * nv).toInt(), 91, 208, 138)
                        c.drawCircle(ax - larg / 2 - 4f * k, y2 - 5f * k, 3.4f * k, pinceau)
                        texte.color = Color.argb((170 * nv).toInt(), 191, 232, 207)
                        c.drawText(info, ax - larg / 2 + 6f * k, y2, texte)
                    } else {
                        texte.color = Color.argb((150 * nv).toInt(), 201, 167, 155)
                        c.drawText(info, ax - larg / 2, y2, texte)
                    }
                }
            }
        }
        c.restore()

        // 4) LA BULLE « + », en dernier
        val vb = ((t - (1.05f + pas * premiere + 0.35f)) / 0.6f).coerceIn(0f, 1f)
        bullePlus(c, vb, 1 - fer)

        // 5) L'ÉTOILE : elle monte au centre en faisant un tour, puis se pose en petit à côté du titre ;
        //    à la fermeture, elle retourne dans le widget.
        val u = seg(t, 0f, 1.0f)
        val v = seg(t, 1.0f, 1.9f)        // elle rejoint le titre en douceur (0,9 s)
        var x = mix(mix(sx, panneau.centerX(), u), titreX, v)
        var y = mix(mix(sy, panneau.centerY() - 40f * k, u), titreY, v)
        var r = mix(mix(r0, 92f * k, u), 19f * k, v)
        var angle = t * 18f + 360f * u + 90f * v
        if (fer > 0) { x = mix(x, sx, fer); y = mix(y, sy, fer); r = mix(r, r0, fer); angle -= 180f * fer }
        etoileX = x; etoileY = y; etoileR = r; etoileA = angle
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
        c.rotate(angle)
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

    // LA BULLE « + » : un arc épais aux bouts arrondis, qui suit le coin en haut à droite du dossier, à
    // distance. Elle arrive en s'étirant le long du coin, avec un petit ressort. À partir de 55 apps, le
    // « + » laisse la place au nombre de places restantes (5, 4… 0 : dossier plein).
    private fun bullePlus(c: Canvas, v: Float, a: Float) {
        if (v <= 0 || a <= 0) return
        val ray = rayonBulle()
        val epaisseur = 38f * k * (0.35f + 0.65f * ressort(v, 2.4f))
        val balayage = 56f * freine(v)
        val rect = RectF(coinX() - ray, coinY() - ray, coinX() + ray, coinY() + ray)
        pinceau.style = Paint.Style.STROKE
        pinceau.strokeCap = Paint.Cap.ROUND
        pinceau.strokeWidth = epaisseur + 1.5f * k
        pinceau.color = Color.argb((26 * a).toInt(), 255, 255, 255)          // un liseré très fin
        c.drawArc(rect, -45f - balayage / 2, max(0.5f, balayage), false, pinceau)
        pinceau.strokeWidth = epaisseur
        pinceau.color = Color.argb((235 * a).toInt(), 86, 52, 43)
        c.drawArc(rect, -45f - balayage / 2, max(0.5f, balayage), false, pinceau)
        val x = bulleX(); val y = bulleY()
        val n = apps.size
        val montre = (v * 2f).coerceIn(0f, 1f) * a
        if (n >= 55) {
            // le compte à rebours : les places qui restent
            val reste = (Groupes.MAX - n).coerceAtLeast(0).toString()
            texte.typeface = gras
            texte.textSize = 19f * k
            texte.color = Color.argb((230 * montre * (if (n >= Groupes.MAX) 0.5f else 1f)).toInt(), 255, 226, 220)
            c.drawText(reste, x - texte.measureText(reste) / 2, y + 7f * k, texte)
            texte.typeface = Typeface.DEFAULT
        } else {
            pinceau.strokeWidth = 3.6f * k
            pinceau.color = Color.argb((230 * montre).toInt(), 255, 226, 220)
            val s = 9f * k * (v * 1.5f).coerceIn(0f, 1f)
            c.drawLine(x - s, y, x + s, y, pinceau)
            c.drawLine(x, y - s, x, y + s, pinceau)
        }
        pinceau.style = Paint.Style.FILL
    }

    // LES APPUIS : toucher une app l'ouvre (dès 0,4 s, même si elle n'est pas encore apparue) ; toucher la
    // bulle « + » ajoute une app ; toucher à côté referme ; glisser à gauche / à droite change de page.
    private var basX = 0f; private var basY = 0f; private var basPage = 0f; private var glisse = false
    private var pageDepart = 0f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val t = (SystemClock.uptimeMillis() - debut) / 1000f
        if (fermeA >= 0 || presse >= 0) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { basX = e.x; basY = e.y; basPage = page; glisse = false; glissementA = -1 }
            MotionEvent.ACTION_MOVE -> {
                if (pages > 1 && abs(e.x - basX) > 12f * k) glisse = true
                if (glisse) {
                    // la page suit le doigt (un peu freinée au-delà de la première et de la dernière page)
                    var vp = basPage - (e.x - basX) / panneau.width()
                    if (vp < 0) vp *= 0.3f
                    if (vp > pages - 1) vp = pages - 1 + (vp - (pages - 1)) * 0.3f
                    page = vp
                }
            }
            MotionEvent.ACTION_UP -> {
                if (glisse) {
                    // on se cale sur la page la plus proche (un petit élan suffit pour passer à la suivante)
                    val elan = (basX - e.x) / panneau.width()
                    var cible = basPage + (if (elan > 0.15f) 1 else if (elan < -0.15f) -1 else 0)
                    cible = cible.coerceIn(0f, (pages - 1).toFloat())
                    pageDepart = page; pageCible = cible.roundToInt().toFloat()
                    glissementA = SystemClock.uptimeMillis()
                    return true
                }
                if (t < 0.4f) return true          // trop tôt : le dossier commence à peine à s'ouvrir
                // la bulle « + »
                if (abs(e.x - bulleX()) < 40f * k && abs(e.y - bulleY()) < 40f * k) { surAjout(); return true }
                if (!panneau.contains(e.x, e.y)) { refermer(); return true }
                val pageVue = page.roundToInt()
                for (i in pageVue * parPage until min(apps.size, (pageVue + 1) * parPage)) {
                    val ay = placeY(i)
                    if (abs(e.x - placeX(i)) < celluleL / 2 && abs(e.y - ay - 14f * k) < celluleH / 2) {
                        presse = i
                        presseA = t
                        surApp(apps[i])            // l'app s'ouvre TOUT DE SUITE
                        return true
                    }
                }
            }
        }
        return true
    }
}
