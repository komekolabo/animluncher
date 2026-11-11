package com.will.animlauncher

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BlendMode
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Point
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.Display
import android.widget.RemoteViews
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// LE WIDGET « DOSSIER ANIMÉ » AVEC LES VRAIES ICÔNES (10/11, validé par Will sur l'aperçu dossier_1a9.mp4).
// La boucle de 12 s : le logo respire et fait son geste -> points qui tournent en traçant un anneau ->
// tuiles qui ondulent -> le logo renaît. Mais :
//  - (11/11) CHAQUE DOSSIER A SON LOGO (bulle pour Social, cœur pour Health, engrenages pour Settings…,
//    voir LogosDossier.kt) ; l'étoile reste celle de l'IA ;
//  - (11/11, demande de Will) LE RYTHME : on voit moins le logo et plus longtemps les apps.
//      logo      0 -> 2,3 s, puis il renaît de 10,9 à 11,6 s   (avant : jusqu'à 3,6 s, retour à 10,4 s)
//      points    2,0 -> 5,4 s                                    (avant : 3,2 -> 7,4 s)
//      icônes    4,8 -> 10,8 s                                   (avant : 6,8 -> 10,4 s)
//  - (11/11, v2) LE GALET prend un sombre assorti au logo, et CHAQUE DOSSIER DÉMARRE SA BOUCLE À UN MOMENT
//    DIFFÉRENT (voir LogosDossier.decalage) : plusieurs dossiers posés côte à côte ne « pulsent » plus ensemble.
//  En plus :
//  - il y a AUTANT DE POINTS QUE D'APPS (1 à 9), chacun de la couleur de son app ;
//  - chaque point file vers sa place et devient L'ICÔNE de son app (les tuiles = les vraies icônes) ;
//  - la grille s'adapte : 1 app = une grande icône, 2 à 4 = 2 colonnes, 5 à 9 = 3 colonnes
//    (la dernière rangée, si elle est incomplète, est centrée) ;
//  - plus de 9 apps : on les montre par PAGES de 9 (apps 1 à 9, puis 10 à 18…), une page par boucle.
//
// COMMENT C'EST FAIT (même astuce que le widget Games, voir WidgetTop.kt). Un widget ne peut pas lancer notre
// code de dessin : il ne sait afficher que des images qu'on lui donne. AnimLauncher dessine donc les 300 images
// de la boucle (une toutes les 40 ms = 25 par seconde) et les range dans une « visionneuse » (ViewFlipper) qui
// les fait défiler toute seule, en boucle, même quand AnimLauncher est fermé.
// FLUIDITÉ (10/11, demande de Will : la 1re version à 12,5 images/s saccadait) : Android limite la mémoire
// d'images d'un widget. Les images ne couvrent donc plus tout le galet mais seulement son MILIEU (la « boîte »,
// 42 dp sur 62, voir widget_dossier.xml) : tout ce qui bouge y tient, et chaque image pèse 2 fois moins.
// ICÔNES AGRANDIES (10/11, demande de Will) : boîte passée de 38 à 42 dp, icônes de +25 % (3 colonnes) à +45 %
// (1 app), vague un peu plus douce (x1,10 au lieu de x1,14) pour qu'elles restent dans la boîte.
// La lueur rose, plus grande que la boîte, est devenue une couche à part (dossier_lueur_anime.xml).
// Pour les dossiers de plus de 9 apps, une seule page tient dans la mémoire d'un widget : le SERVICE
// d'AnimLauncher sert de chef d'orchestre. Tant qu'on est sur l'écran d'accueil, il envoie la page suivante
// toutes les 12 s (la visionneuse repart alors de la 1re image : le changement tombe pile sur l'étoile).
// Sans le service (accessibilité éteinte), le widget montre la 1re page en boucle.
//
// POUR NE PAS FAIRE RAMER LE TÉLÉPHONE (10/11, Will voyait des saccades à l'ouverture des apps) :
//  - le dessin des images se fait en PRIORITÉ BASSE : il laisse passer tout le reste (dont l'animation d'ouverture) ;
//  - on réutilise toujours les 300 MÊMES images en mémoire (la « réserve ») au lieu d'en créer 300 nouvelles
//    à chaque page : sinon Android passe son temps à faire le ménage dans la mémoire, et ça se sent ;
//  - en arrivant sur l'accueil on ne fait RIEN pendant 2 s (c'est souvent le moment où l'on ouvre une app),
//    et la page suivante n'arrive qu'après une boucle complète (12 s), plus à l'arrivée ;
//  - dès qu'on quitte l'accueil, tout s'arrête.
object WidgetDossier {
    private const val PAS = 40L                  // une image toutes les 40 ms (25 images par seconde)
    private const val DUREE = 12.0               // la boucle dure 12 s
    private const val N = 300                    // 12 s / 40 ms = 300 images
    // LA BOÎTE : le carré du milieu du galet couvert par les images (42 dp sur 62), dans le repère 100 x 100
    // du dessin. Elle est centrée sur l'étoile (50, 48) : de 16,13 à 83,87 en largeur, de 14,13 à 81,87 en hauteur.
    private const val BOITE = 42f / 62f * 100f   // = 67,74 unités de côté
    private const val BOITE_X = 50f - BOITE / 2
    private const val BOITE_Y = 48f - BOITE / 2
    const val PAR_PAGE = 9                       // 9 apps au plus par boucle

    // les dessins se font à part, sans bloquer l'app, et en priorité basse (« BACKGROUND » = travail de fond)
    private val fil = Executors.newSingleThreadExecutor { travail ->
        Thread({
            try { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) } catch (_: Throwable) {}
            travail.run()
        }, "widget-dossier")
    }
    private val minuteur = Handler(Looper.getMainLooper())
    private val JETON = Any()
    @Volatile private var enMarche = false       // le chef d'orchestre tourne-t-il ? (= est-on sur l'écran d'accueil ?)

    // CE QU'ON RETIENT POUR CHAQUE DOSSIER posé sur l'accueil (lu et écrit seulement sur « fil »).
    private class Etat(
        val signature: String,                   // ce qu'il montre (pour ne pas tout redessiner pour rien)
        val pages: List<List<String>>,           // les apps, par paquets de 9
        var page: Int,                           // la page montrée en ce moment
        var px: Int,                             // la taille des images (en pixels)
        var envoyeA: Long = SystemClock.uptimeMillis()   // quand la page en cours a été envoyée au widget
    )
    private val etats = HashMap<String, Etat>()

    // Le widget au complet, avec les images d'une page.
    private fun vues(c: Context, id: Int, groupe: String, images: List<Bitmap>): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_dossier)
        v.setTextViewText(R.id.dossier_nom, Groupes.categorie(groupe)?.nom ?: "?")
        // la couleur choisie avec la roue : sur le galet seulement (l'étoile est dessinée avec la couleur,
        // voir couleursEtoile ; les icônes des apps gardent leurs vraies couleurs)
        val hsv = Groupes.couleur(c, groupe)
        // (11/11) sans couleur choisie, le galet prend la teinte sombre assortie au logo du dossier
        // (l'IA garde son brun d'origine : hsvGalet renvoie null pour elle)
        val hsvGalet = hsv ?: LogosDossier.hsvGalet(groupe)
        if (hsvGalet != null && Build.VERSION.SDK_INT >= 31) {
            val sombre = Teinte.avecAlpha(Teinte.depuis(hsvGalet).haut, 175f)
            v.setColorStateList(R.id.dossier_galet, "setImageTintList", ColorStateList.valueOf(sombre))
            v.setBlendMode(R.id.dossier_galet, "setImageTintBlendMode", BlendMode.SRC_ATOP)
        }
        // LA LUEUR derrière le logo (11/11) : de la couleur du logo (celle du milieu de son dégradé).
        // Mode SRC_IN : on garde la forme et la transparence de la lueur, seule sa couleur change.
        // L'IA sans couleur choisie garde sa lueur rose d'origine.
        if (Build.VERSION.SDK_INT >= 31 && (hsv != null || groupe != "ia")) {
            val lueur = couleursLogo(groupe, hsv)[1]
            v.setColorStateList(R.id.dossier_lueur, "setIndeterminateTintList", ColorStateList.valueOf(lueur))
            v.setBlendMode(R.id.dossier_lueur, "setIndeterminateTintBlendMode", BlendMode.SRC_IN)
        }
        // LE DÉPART DÉCALÉ (11/11) : la visionneuse commence toujours par sa 1re image. On lui donne donc les
        // images dans un ordre qui commence au bon moment de la boucle (par exemple à 0,9 s pour Social),
        // puis on continue jusqu'au bout et on reprend au début.
        val premiere = (LogosDossier.decalage(groupe) * 1000.0 / PAS).roundToInt().mod(images.size.coerceAtLeast(1))
        v.removeAllViews(R.id.dossier_film)
        for (k in images.indices) {
            val img = RemoteViews(c.packageName, R.layout.widget_top_image)
            img.setImageViewBitmap(R.id.top_image, images[(premiere + k) % images.size])
            v.addView(R.id.dossier_film, img)
        }
        v.setOnClickPendingIntent(R.id.dossier_racine, WidgetGroupe.clic(c, id, groupe))
        return v
    }

    // DEMANDE de mise à jour d'un dossier : on regarde (à part) si quelque chose a changé, et on redessine si besoin.
    // « forcer » : on redessine même si rien n'a changé (widget tout juste posé, téléphone redémarré…).
    fun demander(contexte: Context, groupe: String, forcer: Boolean = false) {
        val c = contexte.applicationContext
        fil.execute {
            try { construire(c, groupe, forcer) }
            catch (e: Exception) { Journal.noter(c, "ERREUR widget dossier $groupe : " + Journal.pile(e)) }
        }
    }

    private fun construire(c: Context, groupe: String, forcer: Boolean) {
        val ids = WidgetGroupe.idsDuGroupe(c, groupe)
        if (ids.isEmpty()) { etats.remove(groupe); return }
        val apps = Groupes.apps(c, groupe)
        val hsv = Groupes.couleur(c, groupe)
        val signature = ids.joinToString(",") + "|" + apps.joinToString(",") + "|" + hsv?.joinToString(",")
        val ancien = etats[groupe]
        if (!forcer && ancien != null && ancien.signature == signature) return

        // les apps par paquets de 9 (un dossier vide = une page vide : on ne voit que l'étoile)
        val pages = if (apps.isEmpty()) listOf(emptyList()) else apps.chunked(PAR_PAGE)

        // LA TAILLE DES IMAGES : celle de la boîte (42 dp), réduite si elle dépasse la mémoire permise par Android
        // pour un widget (environ 1,5 écran ; on garde 10 % de marge).
        val ecran = (c.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        val taille = Point()
        @Suppress("DEPRECATION") ecran.getRealSize(taille)
        val permis = 6.0 * taille.x * taille.y * 0.9
        var px = min((42 * c.resources.displayMetrics.density).toInt(), sqrt(permis / (N * 4.0)).toInt()).coerceAtLeast(32)

        for (essai in 0 until 3) {
            try {
                val debut = SystemClock.uptimeMillis()
                val images = dessinerPage(c, px, groupe, pages[0], hsv)
                envoyer(c, groupe, ids, images)
                val etat = Etat(signature, pages, 0, px)
                etats[groupe] = etat
                Journal.noter(c, "widget dossier $groupe : ${apps.size} apps, ${pages.size} page(s), $N images de $px px en ${SystemClock.uptimeMillis() - debut} ms")
                // aucun dossier de plus de 9 apps : la réserve d'images ne resservira pas, on rend la mémoire
                if (etats.values.none { it.pages.size > 1 }) { reserve = null; reserveCle = "" }
                return
            } catch (e: IllegalArgumentException) {
                // trop d'images pour la mémoire d'un widget : on recommence en plus petit
                Journal.noter(c, "widget dossier $groupe : images de $px px refusées (${e.message}), on réduit")
                px = (px * 0.8).toInt().coerceAtLeast(24)
            }
        }
    }

    private fun envoyer(c: Context, groupe: String, ids: IntArray, images: List<Bitmap>) {
        val gestionnaire = AppWidgetManager.getInstance(c)
        for (id in ids) gestionnaire.updateAppWidget(id, vues(c, id, groupe, images))
    }

    // LES 300 IMAGES d'une page, dessinées dans la RÉSERVE : 300 images créées une fois, qu'on efface et qu'on
    // redessine à chaque page. (Une fois envoyées au widget, Android en a sa propre copie : on peut les réutiliser.)
    // Les images où l'on ne voit que le logo (avant 2 s et après 11,3 s) sont les mêmes pour toutes les pages :
    // si la réserve les contient déjà (même taille, même dossier, mêmes couleurs), on ne les redessine pas.
    private var reserve: Array<Bitmap>? = null
    private var reservePx = 0
    private var reserveCle = ""                  // le dossier et les couleurs du logo déjà dessiné dans la réserve
    private fun dessinerPage(c: Context, px: Int, groupe: String, apps: List<String>, hsv: FloatArray?): List<Bitmap> {
        val etoile = couleursLogo(groupe, hsv)
        // la réserve : on garde celle qu'on a si elle est à la bonne taille, sinon on en crée une neuve
        val images: Array<Bitmap> = reserve?.takeIf { reservePx == px }
            ?: Array(N) { Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888) }.also { reserve = it; reservePx = px; reserveCle = "" }
        val cle = groupe + ":" + etoile.joinToString(",")      // (le logo dépend du dossier ET des couleurs)
        val etoileDejaLa = cle == reserveCle
        val dessin = Boucle(apps.map { icone(c, it, px) }, etoile, groupe)
        for (i in 0 until N) {
            val t = i * PAS / 1000.0
            if (etoileDejaLa && (t < 2.0 || t >= 11.3)) continue
            images[i].eraseColor(Color.TRANSPARENT)          // on efface l'ancienne image…
            dessin.dessiner(Canvas(images[i]), px, t)        // …et on dessine la nouvelle par-dessus
        }
        reserveCle = cle
        return images.asList()
    }

    // L'icône d'une app, dessinée en grand une seule fois (on la réduit ensuite à chaque image).
    private fun icone(c: Context, pkg: String, px: Int): Bitmap {
        val s = (px * 0.75).toInt().coerceAtLeast(8)          // la plus grande taille possible (1 app, avec la vague)
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        try {
            val d = c.packageManager.getApplicationIcon(pkg)
            d.setBounds(0, 0, s, s)
            d.draw(Canvas(b))
        } catch (_: Exception) {}
        return b
    }

    // LES 3 COULEURS DU LOGO : celles de sa catégorie (voir LogosDossier), ou des voisines de la couleur choisie avec la roue.
    private fun couleursLogo(groupe: String, hsv: FloatArray?): IntArray {
        if (hsv == null) return LogosDossier.couleurs(groupe)
        val (h, s, v) = Triple(hsv[0], hsv[1], hsv[2].coerceAtLeast(0.6f))
        return intArrayOf(Color.HSVToColor(floatArrayOf((h + 335f) % 360f, s, v)), Color.HSVToColor(floatArrayOf(h, s, v)),
                          Color.HSVToColor(floatArrayOf((h + 40f) % 360f, s * 0.8f, v)))
    }

    // ---------------- LE CHEF D'ORCHESTRE (appelé par le service d'AnimLauncher) ----------------

    // On arrive sur l'écran d'accueil. On ne fait RIEN tout de suite (on est peut-être juste en train d'ouvrir
    // une app). Après 2 s : on vérifie que les widgets sont à jour (apps ajoutées, retirées…). Après 12 s (une
    // boucle complète) : les dossiers de plus de 9 apps passent à la page suivante, puis toutes les 12 s.
    fun surAccueil(contexte: Context) {
        val c = contexte.applicationContext
        minuteur.post {
            if (enMarche) return@post
            enMarche = true
            val maintenant = SystemClock.uptimeMillis()
            minuteur.postAtTime({
                fil.execute {
                    try { for (g in WidgetGroupe.groupesPoses(c)) if (enMarche && g != WidgetTop.GROUPE) construire(c, g, false) }
                    catch (e: Exception) { Journal.noter(c, "ERREUR widget dossier (accueil) : " + Journal.pile(e)) }
                }
            }, JETON, maintenant + 2000)
            minuteur.postAtTime({ tic(c) }, JETON, maintenant + (DUREE * 1000).toLong())
        }
    }

    // On quitte l'accueil (ou l'écran s'éteint) : on arrête, pour ne pas user la batterie.
    fun pause() {
        minuteur.post {
            enMarche = false
            minuteur.removeCallbacksAndMessages(JETON)
        }
    }

    private fun tic(c: Context) {
        if (!enMarche) return
        fil.execute {
            try { pageSuivante(c) } catch (e: Exception) { Journal.noter(c, "ERREUR widget dossier (page) : " + Journal.pile(e)) }
        }
        minuteur.postAtTime({ tic(c) }, JETON, SystemClock.uptimeMillis() + (DUREE * 1000).toLong())
    }

    private fun pageSuivante(c: Context) {
        for ((groupe, etat) in etats) {
            if (!enMarche) return                    // on a quitté l'accueil entre-temps : on arrête là
            if (etat.pages.size < 2) continue
            // une page envoyée il y a moins de 6 s (widget tout juste redessiné) : on la laisse jouer
            if (SystemClock.uptimeMillis() - etat.envoyeA < 6000) continue
            val ids = WidgetGroupe.idsDuGroupe(c, groupe)
            if (ids.isEmpty()) continue
            val hsv = Groupes.couleur(c, groupe)
            etat.page = (etat.page + 1) % etat.pages.size
            val images = dessinerPage(c, etat.px, groupe, etat.pages[etat.page], hsv)
            etat.envoyeA = SystemClock.uptimeMillis()
            try { envoyer(c, groupe, ids, images) }
            catch (e: IllegalArgumentException) { Journal.noter(c, "widget dossier $groupe : page refusée (${e.message})") }
        }
    }

    // ---------------- LE DESSIN DE LA BOUCLE (même chose que l'aperçu Python dossier_n.py) ----------------
    // On dessine dans un repère de 100 x 100 (tout le galet), centre (50, 48), comme l'ancienne animation.
    private class Boucle(val icones: List<Bitmap>, val teintes: IntArray, val groupe: String) {
        val n = icones.size
        val couleurs = icones.map { couleurMoyenne(it) }
        val places: List<FloatArray>
        val cote: Float
        init {
            // LA GRILLE : 1 app = une grande icône ; 2 à 4 = 2 colonnes ; 5 à 9 = 3 colonnes
            val (cols, s, ecart) = when {
                n <= 1 -> Triple(1, 44f, 0f)
                n <= 4 -> Triple(2, 30f, 3f)
                else -> Triple(3, 20f, 2.5f)
            }
            cote = s
            val rangs = ceil(n / cols.toDouble()).toInt()
            val liste = ArrayList<FloatArray>()
            for (r in 0 until rangs) {
                val dans = min(cols, n - r * cols)          // la dernière rangée peut être incomplète : centrée
                for (j in 0 until dans) {
                    liste.add(floatArrayOf(CX + (j - (dans - 1) / 2f) * (s + ecart), CY + (r - (rangs - 1) / 2f) * (s + ecart)))
                }
            }
            places = liste
        }

        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val tours = LogosDossier.tours(groupe)
        val cercle = Path().apply { addCircle(CX, CY, 22f, Path.Direction.CW) }
        val mesure = PathMeasure(cercle, false)

        fun dessiner(cv: Canvas, px: Int, t: Double) {
            cv.save()
            // on ne dessine que la boîte du milieu : on agrandit, puis on décale pour que son coin soit en (0, 0)
            cv.scale(px / BOITE, px / BOITE)
            cv.translate(-BOITE_X, -BOITE_Y)
            logo(cv, t)
            anneau(cv, t)
            points(cv, t)
            tuiles(cv, t)
            cv.restore()
        }

        // LE LOGO : il respire et fait son geste (jusqu'à 1,8 s), rétrécit en tournant (2,3 s),
        // puis renaît avec un ressort (10,9 -> 11,6 s)
        fun logo(cv: Canvas, t: Double) {
            val taille = cle(t, 0.0 to 1.0 by LIN, 0.9 to 1.06 by DOUX, 1.8 to 1.0 by DOUX, 2.3 to 0.0 by ACC,
                             10.9 to 0.0 by LIN, 11.6 to 1.0 by RESSORT)
            if (taille <= 0.001) return
            // l'étoile tourne comme avant ; les autres logos tournent moins (voir LogosDossier.tours)
            val tour = if (tours == null)
                cle(t, 0.0 to 0.0 by LIN, 1.8 to 45.0 by LIN, 2.3 to 225.0 by ACC, 2.31 to -105.0 by LIN,
                    10.9 to -105.0 by LIN, 11.6 to -15.0 by DEC, 12.0 to 0.0 by LIN)
            else {
                val (sortie, recul) = tours
                cle(t, 0.0 to 0.0 by LIN, 1.8 to 0.0 by LIN, 2.3 to sortie by ACC, 2.31 to -recul by LIN,
                    10.9 to -recul by LIN, 11.6 to -recul * 0.14 by DEC, 12.0 to 0.0 by LIN)
            }
            // l'horloge du geste : un peu plus rapide que le temps (1,15 x) pour tenir avant la sortie ;
            // en dehors, le logo est au repos (0,3)
            // Les logos qui se DESSINENT (11/11, voir LogosDossier.debutTrace) : leur geste démarre dès que le logo
            // revient (10,9 s), depuis le dessin vide, et continue après le tour de boucle (0 -> 2,3 s), un peu plus
            // lentement (0,85 x) pour remplir tout ce temps. Comme ça, il ne réapparaît pas plein pour s'effacer ensuite.
            val trace = LogosDossier.debutTrace(groupe)
            val geste = when {
                trace != null && t >= 10.9 -> min(trace + (t - 10.9) * 0.85, 2.9)
                trace != null && t < 2.3 -> min(trace + (t + DUREE - 10.9) * 0.85, 2.9)
                trace != null -> trace
                t < 2.3 -> min(0.3 + t * 1.15, 2.9)
                else -> 0.3
            }
            cv.save()
            cv.rotate(tour.toFloat(), CX, CY)
            cv.scale(taille.toFloat(), taille.toFloat(), CX, CY)
            LogosDossier.dessiner(cv, groupe, geste, teintes)
            cv.restore()
        }

        // l'anneau : il se trace (3,8 s) puis s'efface (5,4 s) derrière les points
        fun anneau(cv: Canvas, t: Double) {
            if (t >= 11.9) return
            val fin = cle(t, 2.4 to 0.0 by LIN, 3.4 to 1.0 by DOUX)
            val debut = cle(t, 3.7 to 0.0 by LIN, 4.5 to 1.0 by DOUX)
            if (fin <= debut) return
            val morceau = Path()
            val L = mesure.length
            mesure.getSegment((debut * L).toFloat(), (fin * L).toFloat(), morceau, true)
            cv.save()
            cv.rotate(cle(t, 2.3 to -90.0 by LIN, 4.5 to 180.0 by DOUX).toFloat(), CX, CY)
            p.reset(); p.isAntiAlias = true
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.4f; p.strokeCap = Paint.Cap.ROUND
            p.color = Color.argb(150, 255, 255, 255)
            cv.drawPath(morceau, p)
            cv.restore()
        }

        // LES POINTS : autant que d'apps, répartis régulièrement sur l'anneau, chacun de la couleur de son app.
        // Ils jaillissent du centre, font trois quarts de tour, puis filent vers la place de leur icône.
        fun points(cv: Canvas, t: Double) {
            if (t <= 2.0 || t >= 5.4 || n == 0) return
            val orbite = cle(t, 2.3 to 0.0 by LIN, 4.5 to 270.0 by DOUX)
            val sortie = cle(t, 2.0 to 0.0 by LIN, 2.5 to 1.0 by RESSORT)
            val vol = cle(t, 4.5 to 0.0 by LIN, 5.1 to 1.0 by DOUX)
            val fondu = 1 - cle(t, 4.9 to 0.0 by LIN, 5.3 to 1.0 by LIN)
            val depart = if (n == 4) -135.0 else -90.0      // 4 apps : sur les diagonales, comme l'ancienne animation
            p.reset(); p.isAntiAlias = true
            for (i in 0 until n) {
                val a = Math.toRadians(orbite + depart + 360.0 * i / n)
                val ox = CX + 22 * sortie * cos(a); val oy = CY + 22 * sortie * sin(a)
                val x = (ox + (places[i][0] - ox) * vol).toFloat(); val y = (oy + (places[i][1] - oy) * vol).toFloat()
                p.color = couleurs[i]; p.alpha = (0.19 * fondu * 255).toInt()
                cv.drawCircle(x, y, 6f, p)
                p.color = couleurs[i]; p.alpha = (fondu * 255).toInt()
                cv.drawCircle(x, y, 3.4f, p)
            }
        }

        // LES ICÔNES : elles éclosent (ressort) à 4,8 s, une vague passe deux fois (espacées : 6,9 et 8,6 s),
        // et elles restent jusqu'à 10,3 s avant de rentrer au centre
        fun tuiles(cv: Canvas, t: Double) {
            if (t >= 11.9) return
            p.reset(); p.isAntiAlias = true; p.isFilterBitmap = true
            val zone = RectF()
            for (i in 0 until n) {
                val d = 0.06 * i
                val vague1 = 6.9 + 0.12 * i; val vague2 = 8.6 + 0.12 * i
                val sc = cle(t, (4.8 + d) to 0.0 by LIN, (5.3 + d) to 1.0 by RESSORT,
                             vague1 to 1.0 by LIN, (vague1 + 0.25) to 1.10 by DOUX, (vague1 + 0.5) to 1.0 by DOUX,
                             vague2 to 1.0 by LIN, (vague2 + 0.25) to 1.10 by DOUX, (vague2 + 0.5) to 1.0 by DOUX,
                             (10.3 + d) to 1.0 by LIN, (10.8 + d) to 0.0 by ACC)
                if (sc <= 0.001) continue
                val rentre = cle(t, (10.3 + d) to 0.0 by LIN, (10.8 + d) to 1.0 by ACC)
                val x = (places[i][0] + (CX - places[i][0]) * rentre).toFloat()
                val y = (places[i][1] + (CY - places[i][1]) * rentre).toFloat()
                val h = (cote * sc / 2).toFloat()
                zone.set(x - h, y - h, x + h, y + h)
                cv.drawBitmap(icones[i], null, zone, p)
            }
        }
    }

    // ---------------- les petits outils de mouvement ----------------
    private const val CX = 50f
    private const val CY = 48f

    // Les façons d'arriver à une valeur (les mêmes que les « interpolateurs » d'Android)
    private val LIN: (Double) -> Double = { it }
    private val ACC: (Double) -> Double = { it * it }                                  // démarre doucement puis accélère
    private val DEC: (Double) -> Double = { 1 - (1 - it) * (1 - it) }                  // arrive vite puis ralentit
    private val DOUX: (Double) -> Double = { cos((it + 1) * Math.PI) / 2 + 0.5 }      // départ et arrivée en douceur
    private val RESSORT: (Double) -> Double = { val u = it - 1; u * u * (3 * u + 2) + 1 }   // dépasse un peu puis revient

    // Une « clé » = (seconde, valeur, façon d'y arriver). On l'écrit « 3.0 to 1.0 by DOUX ».
    private class Cle(val t: Double, val v: Double, val f: (Double) -> Double)
    private infix fun Pair<Double, Double>.by(f: (Double) -> Double) = Cle(first, second, f)

    // La valeur à l'instant t, entre les clés (avant la 1re : sa valeur ; après la dernière : sa valeur).
    private fun cle(t: Double, vararg cles: Cle): Double {
        if (t <= cles[0].t) return cles[0].v
        for (k in 1 until cles.size) {
            val a = cles[k - 1]; val b = cles[k]
            if (t <= b.t) return a.v + (b.v - a.v) * b.f((t - a.t) / (b.t - a.t))
        }
        return cles.last().v
    }

    // La couleur « moyenne » d'une icône (on regarde quelques points au milieu) : c'est la couleur de son point.
    private fun couleurMoyenne(b: Bitmap): Int {
        var r = 0L; var v = 0L; var bl = 0L; var n = 0L
        val pas = maxOf(1, b.width / 12)
        var y = b.height / 5
        while (y < b.height * 4 / 5) {
            var x = b.width / 5
            while (x < b.width * 4 / 5) {
                val c = b.getPixel(x, y)
                if (Color.alpha(c) > 128) { r += Color.red(c); v += Color.green(c); bl += Color.blue(c); n++ }
                x += pas
            }
            y += pas
        }
        if (n == 0L) return Color.rgb(255, 140, 91)
        val couleur = Color.rgb((r / n).toInt(), (v / n).toInt(), (bl / n).toInt())
        // une icône presque noire donnerait un point invisible sur le galet : on l'éclaircit
        val hsv = FloatArray(3); Color.colorToHSV(couleur, hsv)
        hsv[2] = hsv[2].coerceAtLeast(0.7f)
        return Color.HSVToColor(hsv)
    }
}
