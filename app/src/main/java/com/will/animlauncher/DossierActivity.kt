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
import kotlin.math.floor
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
// Toucher une app l'ouvre TOUT DE SUITE (même avant la fin de l'animation, dès 0,15 s).
// Ouverture RAPIDE (09/11, Will trouvait ça long) : tout est en place en moins d'1 s.
// MODE ÉDITION (09/11) : un appui long sur une app -> les apps se mettent à trembler doucement, une petite
// croix permet d'en retirer une, et on peut les déplacer en les faisant glisser (l'ordre est retenu).
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
        // sourceBounds = la place du widget sur l'écran (donnée par l'écran d'accueil) : l'étoile part de là
        // et y revient. On la retient : si un jour l'écran d'accueil ne la donne pas, on reprend la dernière.
        val v = DossierVue(this, groupe, nom, apps, placeDuWidget(), { ouvrir(it) }, { ajouter() }, { fermer() })
        vue = v
        setContentView(v)
    }

    // La place du widget sur l'écran (gauche, haut, droite, bas, en pixels), donnée par l'écran d'accueil,
    // ou la dernière connue pour ce dossier. On l'écrit aussi dans le journal, pour vérifier.
    private fun placeDuWidget(): Rect? {
        val carnet = getSharedPreferences("dossier_place", MODE_PRIVATE)
        val r = intent.sourceBounds
        if (r != null) {
            carnet.edit().putString(groupe, "${r.left},${r.top},${r.right},${r.bottom}").apply()
            Journal.noter(this, "dossier $groupe : place du widget = $r")
            return r
        }
        Journal.noter(this, "dossier $groupe : place du widget non transmise (on reprend la dernière connue)")
        val n = carnet.getString(groupe, null)?.split(",")?.mapNotNull { it.toIntOrNull() } ?: return null
        return if (n.size == 4) Rect(n[0], n[1], n[2], n[3]) else null
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

    // Le bouton RETOUR : en mode édition, il termine l'édition ; sinon il referme le dossier.
    @Deprecated("Bouton retour")
    override fun onBackPressed() {
        val v = vue ?: return fermer()
        if (v.enEdition()) v.finirEdition() else v.refermer()
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
    private val groupe: String,
    private val nom: String,
    apps: List<String>,
    private val depart: Rect?,
    private val surApp: (String) -> Unit,
    private val surAjout: () -> Unit,
    private val surFin: () -> Unit
) : View(c) {

    // UNE CASE du dossier : l'app, son nom, son icône (dessinée à la bonne taille), et ses infos.
    // dx, dy = l'endroit où elle est DESSINÉE : il rejoint en douceur sa vraie place (quand on déplace
    // une app, les autres glissent vers leur nouvelle place au lieu de sauter).
    private class Case(val pkg: String, val nom: String, val icone: Drawable?, val ouverte: Boolean, val info: String?) {
        var image: Bitmap? = null
        var dx = Float.NaN; var dy = Float.NaN
        var retireeA = -1f          // l'instant où on l'a retirée (elle rétrécit puis disparaît)
    }

    // LES INFOS, calculées une fois à l'ouverture : l'app est-elle encore ouverte ? depuis quand ne l'a-t-on
    // pas ouverte ? (le service d'AnimLauncher le sait : il voit passer les apps à l'écran)
    private val cases: MutableList<Case> = apps.map { pkg ->
        val ouverte = AppWatcherService.actif?.estOuverte(pkg) ?: false
        Case(pkg, Groupes.nomApp(c, pkg),
            try { c.packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null },
            ouverte, if (ouverte) "Open" else depuis(AppWatcherService.derniereOuverture(c, pkg)))
    }.toMutableList()

    private fun resume(): String {
        val n = cases.size
        val o = cases.count { it.ouverte }
        return (if (n == 1) "1 app" else "$n apps") + if (o > 0) "  ·  $o open" else ""
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
    private var derniereImage = SystemClock.uptimeMillis()
    private var fermeA = -1f            // l'instant où on a demandé de refermer (-1 = pas encore)
    private var termine = false
    private var presse: Case? = null    // l'app touchée (elle s'ouvre)
    private var presseA = 0f

    // LE MODE ÉDITION : edition = vrai pendant l'édition ; tiree = l'app qu'on déplace avec le doigt.
    private var edition = false
    private var tiree: Case? = null
    private var doigtX = 0f; private var doigtY = 0f

    // la mise en page (calculée quand on connaît la taille de l'écran, et refaite si on retire une app)
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
    private var pageDepart = 0f
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
    private fun maintenant() = (SystemClock.uptimeMillis() - debut) / 1000f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        k = w / 540f
        miseEnPage()
        // Le départ de l'étoile : le galet du widget (en haut de la case, au-dessus du nom).
        val ici = IntArray(2); getLocationOnScreen(ici)
        val d = resources.displayMetrics.density
        if (depart != null) {
            sx = depart.exactCenterX() - ici[0]
            sy = depart.exactCenterY() - ici[1] - 10.5f * d
        } else { sx = w / 2f; sy = h * 0.75f }
        r0 = 16f * d
        // Les icônes, dessinées une fois pour toutes à la bonne taille.
        val taille = icone.toInt().coerceAtLeast(1)
        for (case in cases) case.image = case.icone?.let {
            val b = Bitmap.createBitmap(taille, taille, Bitmap.Config.ARGB_8888)
            it.setBounds(0, 0, taille, taille)
            it.draw(Canvas(b))
            b
        }
    }

    // LA MISE EN PAGE : 2 colonnes pour 4 apps ou moins, sinon 3 ; au plus 4 lignes par page, puis des pages.
    // (Le nombre de colonnes est choisi à l'ouverture et ne change plus, pour que les icônes gardent leur taille.)
    private var colonnesFixees = false
    private fun miseEnPage() {
        val w = width.toFloat(); val h = height.toFloat()
        val n = cases.size
        if (!colonnesFixees) {
            colonnes = if (n <= 4) 2 else 3
            icone = (if (colonnes == 2) 96f else 84f) * k
            colonnesFixees = true
        }
        parPage = colonnes * 4
        pages = max(1, ceil(n / parPage.toFloat()).toInt())
        val largeur = min(440f * k, w - 100f * k)
        celluleL = (largeur - 40f * k) / colonnes
        celluleH = icone + 80f * k                    // l'icône, son nom, et la ligne d'info
        val lignes = max(1, ceil(min(n, parPage) / colonnes.toFloat()).toInt())
        val hauteur = 110f * k + lignes * celluleH + (if (pages > 1) 34f else 16f) * k
        val cx = w / 2f; val cy = h * 0.47f
        panneau = RectF(cx - largeur / 2, cy - hauteur / 2, cx + largeur / 2, cy + hauteur / 2)
        haut = panneau.top + 110f * k
        if (page > pages - 1) { page = (pages - 1).toFloat(); pageCible = page }
    }

    // La VRAIE place (le centre) de la case numéro i : sa page, puis sa place dans la page.
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
        if (fermeA < 0) fermeA = maintenant()
    }

    fun enEdition() = edition
    fun finirEdition() {
        edition = false
        tiree = null
        Groupes.definirOrdre(context, groupe, cases.map { it.pkg })
    }

    // Où en est l'étoile (en pixels de l'ÉCRAN) : pour que le service dessine son retour dans le widget.
    fun etatPourRetour(): RetourEtoile.Etat? {
        if (width == 0) return null
        val ici = IntArray(2); getLocationOnScreen(ici)
        return RetourEtoile.Etat(etoileX + ici[0], etoileY + ici[1], etoileR, etoileA,
            sx + ici[0], sy + ici[1], r0, voile)
    }

    // La forme du galet du widget (62 dp = 3,875 fois le rayon de l'étoile du widget) : quand l'étoile rentre,
    // on ne la dessine pas là, comme si elle passait SOUS la boîte.
    private fun boite(): Path {
        val demi = r0 * 3.875f / 2
        return Path().apply { addRoundRect(sx - demi, sy - demi, sx + demi, sy + demi, demi * 0.72f, demi * 0.72f, Path.Direction.CW) }
    }

    override fun onDraw(c: Canvas) {
        val t = maintenant()
        val tf = if (fermeA < 0) 0f else t - fermeA
        val fer = if (fermeA < 0) 0f else seg(tf, 0f, 0.55f)
        // le temps écoulé depuis l'image précédente (pour faire glisser les cases en douceur)
        val heure = SystemClock.uptimeMillis()
        val dt = ((heure - derniereImage) / 1000f).coerceIn(0f, 0.05f)
        derniereImage = heure

        // les apps retirées : quand leur petite animation est finie, on les enlève vraiment
        if (cases.removeAll { it.retireeA >= 0 && t - it.retireeA > 0.25f }) {
            miseEnPage()
            Groupes.definirOrdre(context, groupe, cases.map { it.pkg })
        }
        val n = cases.size

        // le glissement de page en cours : on rejoint la page visée en douceur (0,3 s)
        if (glissementA >= 0) {
            val g = ((heure - glissementA) / 300f).coerceIn(0f, 1f)
            page = mix(pageDepart, pageCible, freine(g))
            if (g >= 1f) glissementA = -1
        }

        // 1) LES BANDES SOMBRES, qui arrivent du haut et du bas (et repartent à la fermeture)
        voile = seg(t, 0f, 0.35f) * (1 - fer)
        bandes(c, voile)

        // 2) LE PANNEAU du dossier : il sort du widget en grandissant (il part de la place du widget)
        val p = seg(t, 0.08f, 0.42f) * (1 - fer)
        texte.typeface = gras
        val tw = texte.apply { textSize = 28f * k }.measureText(nom)
        val titreX = panneau.centerX() - (41f * k + tw) / 2 + 19f * k
        val titreY = panneau.top + 44f * k
        if (p > 0) {
            val e = 0.25f + 0.75f * freine(p)
            c.save()
            c.scale(e, e, sx, sy)          // agrandi depuis le widget : il « sort » de la boîte
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
            // la ligne sous le titre : les infos (« 5 apps · 2 open »), ou le mode d'emploi en mode édition
            texte.typeface = Typeface.DEFAULT
            texte.textSize = 17f * k
            val ligne = if (edition) "Drag to move  ·  tap outside when done" else resume()
            texte.color = if (edition) Color.argb((200 * p).toInt(), 255, 205, 180) else Color.argb((150 * p).toInt(), 247, 239, 236)
            c.drawText(ligne, panneau.centerX() - texte.measureText(ligne) / 2, panneau.top + 84f * k, texte)
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
        val pas = min(0.04f, 0.3f / max(1, premiere))      // toutes les apps arrivent en moins d'1 s
        for (i in 0 until n) {
            val case = cases[i]
            if (case === tiree) continue                 // l'app qu'on déplace est dessinée à la fin, sous le doigt
            // la place dessinée rejoint la vraie place en douceur
            val vx = placeX(i); val vy = placeY(i)
            if (case.dx.isNaN() || glissementA >= 0 || tiree == null && !edition) { case.dx = vx; case.dy = vy }
            else { val q = (dt * 14f).coerceIn(0f, 1f); case.dx = mix(case.dx, vx, q); case.dy = mix(case.dy, vy, q) }
            val ax = case.dx; val ay = case.dy
            if (ax < panneau.left - celluleL || ax > panneau.right + celluleL) continue      // page pas visible
            val d = if (i < parPage) 0.2f + pas * i else 0.3f
            // la traînée (seulement pour la page 1, pendant l'ouverture)
            val tr = ((t - d) / 0.25f).coerceIn(0f, 1f)
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
            var v = ((t - (d + 0.15f)) / 0.35f).coerceIn(0f, 1f)
            if (case === presse) v = max(v, ((t - presseA) / 0.15f).coerceIn(0f, 1f))
            if (edition) v = 1f
            if (v <= 0) continue
            dessinerCase(c, case, i, ax, ay, v, fer, t)
        }
        c.restore()
        // l'app qu'on déplace : sous le doigt, un peu plus grande, par-dessus tout
        tiree?.let { dessinerCase(c, it, cases.indexOf(it), doigtX, doigtY - 20f * k, 1f, 0f, t, souleve = true) }

        // 4) LA BULLE « + », en dernier
        val vb = ((t - (0.35f + pas * premiere)) / 0.45f).coerceIn(0f, 1f)
        bullePlus(c, vb, 1 - fer)

        // 5) L'ÉTOILE : elle file directement du widget jusqu'à côté du titre (0,55 s), en décrivant une
        //    petite courbe et un demi-tour ; à la fermeture, elle retourne dans le widget et PASSE DESSOUS.
        val u = seg(t, 0f, 0.55f)
        var x = mix(sx, titreX, u)
        var y = mix(sy, titreY, u) - 60f * k * sin(PI.toFloat() * u)
        var r = mix(r0, 19f * k, u) * (1 + 0.35f * sin(PI.toFloat() * u))      // elle grossit un peu en route
        var angle = t * 18f + 180f * u
        if (fer > 0) { x = mix(x, sx, fer); y = mix(y, sy + 0.6f * r0, fer); r = mix(r, r0 * 0.8f, fer); angle -= 180f * fer }
        etoileX = x; etoileY = y; etoileR = r; etoileA = angle
        val lueur = 55f * (1 - u * 0.35f) * (1 - fer)
        c.save()
        if (fer > 0.3f) c.clipOutPath(boite())     // elle se glisse sous la boîte du widget
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
        c.restore()

        // la fermeture est finie : on s'en va
        if (fermeA >= 0 && tf >= 0.6f) {
            if (!termine) { termine = true; surFin() }
            return
        }
        postInvalidateOnAnimation()
    }

    // UNE CASE : l'onde d'éclosion, l'icône (qui tremble doucement en mode édition), la petite croix pour
    // la retirer (mode édition), puis le nom et l'info en dessous.
    private fun dessinerCase(c: Canvas, case: Case, i: Int, ax: Float, ay: Float, v: Float, fer: Float, t: Float, souleve: Boolean = false) {
        val retrait = if (case.retireeA >= 0) 1 - ((t - case.retireeA) / 0.25f).coerceIn(0f, 1f) else 1f
        val alpha = (v / 0.25f).coerceIn(0f, 1f) * (1 - fer) * retrait
        val flot = if (edition) 0f else 4f * k * sin(t * 2f + i * 1.6f)
        val appui = if (case === presse) 1 - 0.08f * sin(PI.toFloat() * ((t - presseA) / 0.15f).coerceIn(0f, 1f)) else 1f
        val e = (0.2f + 0.8f * ressort(v)) * appui * retrait * (if (souleve) 1.12f else 1f)
        var penche = -14f * (1 - freine(v))
        if (edition && !souleve) penche += 2.5f * sin(t * 18f + i * 2.1f)      // le petit tremblement
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
        case.image?.let { b ->
            pinceau.color = Color.WHITE
            pinceau.alpha = (255 * alpha).toInt()
            c.drawBitmap(b, ax - b.width / 2f, ay + flot - b.height / 2f, pinceau)
            pinceau.alpha = 255
        }
        // la petite croix (mode édition), en haut à gauche de l'icône
        if (edition && !souleve) {
            val bx = ax - icone / 2 + 6f * k; val by = ay - icone / 2 + 6f * k
            pinceau.color = Color.argb((240 * alpha).toInt(), 86, 52, 43)
            c.drawCircle(bx, by, 12f * k, pinceau)
            pinceau.style = Paint.Style.STROKE
            pinceau.strokeWidth = 2.6f * k
            pinceau.strokeCap = Paint.Cap.ROUND
            pinceau.color = Color.argb((235 * alpha).toInt(), 255, 226, 220)
            c.drawLine(bx - 4.5f * k, by - 4.5f * k, bx + 4.5f * k, by + 4.5f * k, pinceau)
            c.drawLine(bx + 4.5f * k, by - 4.5f * k, bx - 4.5f * k, by + 4.5f * k, pinceau)
            pinceau.style = Paint.Style.FILL
        }
        c.restore()
        if (souleve) return
        // le nom, puis l'info en dessous, glissent doucement en place un peu après
        val d = if (i < parPage) 0.2f + min(0.04f, 0.3f / max(1, min(cases.size, parPage))) * i else 0.3f
        val nv = (if (edition) 1f else ((t - (d + 0.3f)) / 0.3f).coerceIn(0f, 1f)) * (1 - fer) * retrait
        if (nv > 0) {
            val glisse = 8f * k * (1 - freine(nv))
            texte.textSize = 19f * k
            texte.color = Color.argb((205 * nv).toInt(), 255, 255, 255)
            val etiquette = TextUtils.ellipsize(case.nom, texte, celluleL - 8f * k, TextUtils.TruncateAt.END).toString()
            c.drawText(etiquette, ax - texte.measureText(etiquette) / 2, ay + icone / 2 + 28f * k + glisse, texte)
            case.info?.let { info ->
                texte.textSize = 15f * k
                val larg = texte.measureText(info)
                val y2 = ay + icone / 2 + 49f * k + glisse
                if (case.ouverte) {
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
        val n = cases.size
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

    // Quelle case est sous le doigt (sur la page affichée) ? -1 = aucune.
    private fun caseSous(x: Float, y: Float): Int {
        val pageVue = page.roundToInt()
        for (i in pageVue * parPage until min(cases.size, (pageVue + 1) * parPage)) {
            if (abs(x - placeX(i)) < celluleL / 2 && abs(y - placeY(i) - 14f * k) < celluleH / 2) return i
        }
        return -1
    }

    // Pendant un déplacement : quelle PLACE est sous le doigt, y compris les places vides du cadre
    // (vu sur la vidéo de Will le 09/11 : poser une app sur une place vide ne faisait rien, elle revenait).
    // Une place vide après la dernière app = « tout à la fin ». -1 = le doigt est hors de la grille.
    private fun placeSous(x: Float, y: Float): Int {
        val col = floor((x - panneau.left - 20f * k) / celluleL).toInt()
        val ligne = floor((y - haut) / celluleH).toInt()
        if (col !in 0 until colonnes || ligne !in 0 until 4) return -1
        val i = page.roundToInt() * parPage + ligne * colonnes + col
        return min(i, cases.size - 1)
    }

    // LES APPUIS : toucher une app l'ouvre (dès 0,15 s, même si elle n'est pas encore apparue) ; toucher la
    // bulle « + » ajoute une app ; toucher à côté referme ; glisser à gauche / à droite change de page.
    // APPUI LONG sur une app : mode édition (retirer avec la croix, déplacer en faisant glisser).
    private var basX = 0f; private var basY = 0f; private var basPage = 0f; private var glisse = false
    private var appuiLong: Runnable? = null

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val t = maintenant()
        if (fermeA >= 0 || presse != null) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                basX = e.x; basY = e.y; basPage = page; glisse = false; glissementA = -1
                doigtX = e.x; doigtY = e.y
                // au bout de 0,4 s sans bouger sur une app : mode édition, et on « soulève » cette app
                val i = caseSous(e.x, e.y)
                if (i >= 0 && t > 0.3f) {
                    val choisie = cases[i]
                    val r = Runnable {
                        if (!glisse && fermeA < 0 && choisie.retireeA < 0 && choisie in cases) {
                            edition = true
                            tiree = choisie
                            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
                    appuiLong = r
                    postDelayed(r, if (edition) 150L else 400L)   // en mode édition, plus besoin d'attendre
                }
            }
            MotionEvent.ACTION_MOVE -> {
                doigtX = e.x; doigtY = e.y
                val tire = tiree
                if (tire != null) {
                    // on déplace une app : elle prend la place de la case sous le doigt, les autres glissent.
                    // Les places VIDES (après la dernière app) comptent aussi : on peut la poser à la fin.
                    val cible = placeSous(e.x, e.y)
                    val actuelle = cases.indexOf(tire)
                    if (cible >= 0 && cible != actuelle) { cases.removeAt(actuelle); cases.add(cible, tire) }
                    return true
                }
                if (abs(e.x - basX) > 12f * k || abs(e.y - basY) > 12f * k) {
                    appuiLong?.let { removeCallbacks(it) }; appuiLong = null
                }
                if (pages > 1 && abs(e.x - basX) > 12f * k) glisse = true
                if (glisse) {
                    // la page suit le doigt (un peu freinée au-delà de la première et de la dernière page)
                    var vp = basPage - (e.x - basX) / panneau.width()
                    if (vp < 0) vp *= 0.3f
                    if (vp > pages - 1) vp = pages - 1 + (vp - (pages - 1)) * 0.3f
                    page = vp
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                appuiLong?.let { removeCallbacks(it) }; appuiLong = null
                if (tiree != null) {
                    // on lâche l'app déplacée : elle rejoint sa place, et l'ordre est retenu
                    tiree?.let { it.dx = doigtX; it.dy = doigtY - 20f * k }
                    tiree = null
                    Groupes.definirOrdre(context, groupe, cases.map { it.pkg })
                    return true
                }
                if (e.actionMasked == MotionEvent.ACTION_CANCEL) return true
                if (glisse) {
                    // on se cale sur la page la plus proche (un petit élan suffit pour passer à la suivante)
                    val elan = (basX - e.x) / panneau.width()
                    var cible = basPage + (if (elan > 0.15f) 1 else if (elan < -0.15f) -1 else 0)
                    cible = cible.coerceIn(0f, (pages - 1).toFloat())
                    pageDepart = page; pageCible = cible.roundToInt().toFloat()
                    glissementA = SystemClock.uptimeMillis()
                    return true
                }
                if (t < 0.15f) return true          // trop tôt : le dossier commence à peine à s'ouvrir
                if (edition) {
                    // en mode édition : la croix retire l'app ; ailleurs, on termine l'édition
                    val pageVue = page.roundToInt()
                    for (i in pageVue * parPage until min(cases.size, (pageVue + 1) * parPage)) {
                        val bx = placeX(i) - icone / 2 + 6f * k; val by = placeY(i) - icone / 2 + 6f * k
                        if (abs(e.x - bx) < 22f * k && abs(e.y - by) < 22f * k && cases[i].retireeA < 0) {
                            cases[i].retireeA = t
                            Groupes.definir(context, groupe, cases[i].pkg, false)
                            return true
                        }
                    }
                    if (caseSous(e.x, e.y) < 0) finirEdition()
                    return true
                }
                // la bulle « + »
                if (abs(e.x - bulleX()) < 40f * k && abs(e.y - bulleY()) < 40f * k) { surAjout(); return true }
                if (!panneau.contains(e.x, e.y)) { refermer(); return true }
                val i = caseSous(e.x, e.y)
                if (i >= 0) {
                    presse = cases[i]
                    presseA = t
                    surApp(cases[i].pkg)            // l'app s'ouvre TOUT DE SUITE
                }
            }
        }
        return true
    }
}
