package com.will.animlauncher

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BlendMode
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Shader
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.Display
import android.view.View
import android.widget.RemoteViews
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// LE WIDGET « DOSSIER ANIMÉ » AVEC LES VRAIES ICÔNES (10/11, validé par Will sur l'aperçu dossier_1a9.mp4).
// La boucle de 12 s : le logo respire et fait son geste -> les icônes des apps tournent en traçant un anneau
// (des points de couleur avant le 17/11) ->
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
//
// (18/11, choisis par Will sur l'aperçu widgets_3_propositions.mp4) DEUX NOUVELLES BOUCLES remplacent l'ancienne :
//  - CARROUSEL : le logo reste au centre, en petit ; les apps tournent autour sur un anneau INCLINÉ (en 3D) :
//    devant = grandes et nettes, derrière = petites et estompées (elles passent derrière le logo) ;
//  - MOSAÏQUE : le logo se fend en tuiles (une par app), une vague en diagonale les retourne côté app, un reflet
//    doux traverse la grille, puis la vague repasse dans l'autre sens et le logo se ressoude.
// Un widget ne garde qu'UNE boucle de 12 s en mémoire. (18/11 au soir, demande de Will) TOUS les dossiers jouent le
// MÊME style en même temps : un seul tirage au sort pour tout l'accueil, puis tous ALTERNENT ensemble
// (Carrousel <-> Mosaïque) au plus une fois toutes les 5 min, en arrivant sur l'accueil (voir styleDuMoment).
// L'ancienne boucle (apps qui tournent puis grille) reste dans le code (style ANCIENNE), mais n'est plus tirée.
object WidgetDossier {
    const val CARROUSEL = 0
    const val MOSAIQUE = 1
    const val ANCIENNE = 2
    private const val ALTERNANCE = 5 * 60 * 1000L      // (18/11 au soir, choix de Will) changer de style au plus toutes les 5 min

    // LE STYLE COMMUN À TOUS LES DOSSIERS (18/11 au soir). Retenu dans un petit carnet (« widgets_dossier »), avec
    // l'heure où il a été choisi : il survit si Android ferme AnimLauncher ou si on redémarre le téléphone.
    // - la toute première fois : tirage au sort entre Carrousel et Mosaïque ;
    // - ensuite : au bout de 5 min, on passe à l'autre style (pour TOUS les dossiers à la fois).
    private fun styleDuMoment(c: Context): Int {
        val carnet = c.getSharedPreferences("widgets_dossier", Context.MODE_PRIVATE)
        val maintenant = System.currentTimeMillis()      // l'heure « réelle » (continue même après un redémarrage)
        var style = carnet.getInt("style", -1)
        val depuis = carnet.getLong("style_depuis", 0L)
        if (style !in listOf(CARROUSEL, MOSAIQUE)) style = if (Math.random() < 0.5) CARROUSEL else MOSAIQUE
        else if (maintenant - depuis < ALTERNANCE && maintenant >= depuis) return style
        else style = 1 - style
        carnet.edit().putInt("style", style).putLong("style_depuis", maintenant).apply()
        return style
    }
    private const val PAS = 40L                  // une image toutes les 40 ms (25 images par seconde)
    private const val DUREE = 12.0               // la boucle dure 12 s
    private const val N = 300                    // 12 s / 40 ms = 300 images
    // (18/11, demande de Will : « les icônes sont trop petites, illisibles ») la boîte passe de 42 à 54 dp : les icônes
    // y sont bien plus grandes. Revers : Android limite la mémoire d'images d'un widget, on était déjà au maximum ;
    // les images sont donc un peu moins fines (environ 113 px pour 54 dp au lieu de 110 px pour 42 dp sur le Flip6).
    // (18/11 au soir, « prends la place maximale ») les images couvrent maintenant TOUT le galet (62 dp sur 62) ;
    // ce qui dépasse de sa forme arrondie est coupé (voir FORME_GALET). Même revers mémoire : environ 113 px pour 62 dp.
    private const val COTE_DP = 62
    // LA BOÎTE : la partie du galet couverte par les images, dans le repère 100 x 100 du dessin (= tout le galet)
    private const val BOITE = COTE_DP / 62f * 100f   // = 100 unités de côté
    private const val BOITE_X = 50f - BOITE / 2
    private const val BOITE_Y = 50f - BOITE / 2
    // le logo des nouvelles boucles, plus grand qu'avant (rayon 34 au lieu de 26, sur 100)
    private const val LOGO_ECH = 1.3f
    // LA FORME DU GALET (celle de widget_galet*.xml : un « super-cercle » d'exposant 2,6, de rayon 49,3, centré en
    // 50, 50) : on coupe chaque image avec, pour que rien ne dépasse dans les coins.
    private val FORME_GALET: Path = Path().apply {
        for (i in 0..120) {
            val a = 2 * Math.PI * i / 120
            val c = cos(a); val s = sin(a)
            val x = 50 + 49.3 * Math.signum(c) * Math.pow(kotlin.math.abs(c), 2 / 2.6)
            val y = 50 + 49.3 * Math.signum(s) * Math.pow(kotlin.math.abs(s), 2 / 2.6)
            if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
        }
        close()
    }
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
        val style: Int,                          // (18/11) CARROUSEL ou MOSAIQUE
        val dessineA: Long,                      // (18/11) quand ce style a été dessiné (pour alterner)
        var envoyeA: Long = SystemClock.uptimeMillis()   // quand la page en cours a été envoyée au widget
    )
    private val etats = HashMap<String, Etat>()

    // Le widget au complet, avec les images d'une page.
    private fun vues(c: Context, id: Int, groupe: String, images: List<Bitmap>): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_dossier)
        // le nom du dossier, dans sa police (en image si ce n'est pas la police du téléphone, voir Polices.poserNom)
        Polices.poserNom(c, v, R.id.dossier_nom, R.id.dossier_nom_image, groupe)
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
        // (18/11) les infos utiles (apps ouvertes, dernière app utilisée)
        infosMontrees[groupe] = poserInfos(c, groupe, v)
        return v
    }

    // ---------------- (18/11) LES INFOS UTILES ----------------
    // Deux petites vues par-dessus la boucle (voir widget_dossier.xml) :
    //  - le nombre d'apps du dossier encore OUVERTES (le service d'AnimLauncher le sait : il voit passer les apps) ;
    //  - l'icône de la DERNIÈRE app du dossier qu'on a ouverte (carnet « ouvertures » du service).
    // Elles ne font PAS partie des 300 images : quand elles changent, on envoie seulement ces deux vues au widget
    // (« mise à jour partielle »), ce qui est instantané et ne coûte presque rien.
    private val infosMontrees = HashMap<String, String>()     // ce qui est affiché sur chaque dossier (pour ne rien renvoyer pour rien)

    // Remplit les deux vues dans « v », et renvoie un petit résumé (« 2|com.exemple.app ») pour savoir si ça a changé.
    private fun poserInfos(c: Context, groupe: String, v: RemoteViews): String {
        val apps = Groupes.apps(c, groupe)
        val service = AppWatcherService.actif
        val ouvertes = if (service == null) 0 else apps.count { try { service.estOuverte(it) } catch (_: Exception) { false } }
        if (ouvertes > 0) {
            v.setViewVisibility(R.id.dossier_ouvertes, View.VISIBLE)
            v.setTextViewText(R.id.dossier_ouvertes_nb, ouvertes.toString())
        } else v.setViewVisibility(R.id.dossier_ouvertes, View.GONE)
        // la dernière app utilisée : celle dont la dernière ouverture est la plus récente (rien pour un dossier d'1 app)
        val derniere = if (apps.size < 2) null
            else apps.maxByOrNull { AppWatcherService.derniereOuverture(c, it) }?.takeIf { AppWatcherService.derniereOuverture(c, it) > 0 }
        if (derniere != null) {
            v.setViewVisibility(R.id.dossier_derniere, View.VISIBLE)
            v.setImageViewBitmap(R.id.dossier_derniere_icone, petiteIcone(c, derniere))
        } else v.setViewVisibility(R.id.dossier_derniere, View.GONE)
        return "$ouvertes|$derniere"
    }

    // l'icône d'une app, à la taille du petit rond (11 dp)
    private fun petiteIcone(c: Context, pkg: String): Bitmap {
        val s = (11 * c.resources.displayMetrics.density).roundToInt().coerceAtLeast(8)
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        try {
            val d = c.packageManager.getApplicationIcon(pkg)
            d.setBounds(0, 0, s, s)
            d.draw(Canvas(b))
        } catch (_: Exception) {}
        return b
    }

    // Met à jour les infos de tous les dossiers posés, seulement là où elles ont changé (appelé en arrivant sur l'accueil).
    private fun mettreAJourInfos(c: Context) {
        val gestionnaire = AppWidgetManager.getInstance(c)
        for (g in WidgetGroupe.groupesPoses(c)) {
            if (g == WidgetTop.GROUPE || !etats.containsKey(g)) continue      // dossier pas encore dessiné : il le sera avec ses infos
            val v = RemoteViews(c.packageName, R.layout.widget_dossier)
            val resume = poserInfos(c, g, v)
            if (resume == infosMontrees[g]) continue
            for (id in WidgetGroupe.idsDuGroupe(c, g)) {
                try { gestionnaire.partiallyUpdateAppWidget(id, v) } catch (_: Exception) {}
            }
            infosMontrees[g] = resume
        }
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
        // LE STYLE : le même pour tous les dossiers (il change pour tous à la fois, toutes les 5 min au plus)
        val style = styleDuMoment(c)
        // rien n'a changé (mêmes apps, mêmes couleurs, même style) : on ne redessine pas
        if (!forcer && ancien != null && ancien.signature == signature && ancien.style == style) return

        // les apps par paquets de 9 (un dossier vide = une page vide : on ne voit que l'étoile)
        val pages = if (apps.isEmpty()) listOf(emptyList()) else apps.chunked(PAR_PAGE)

        // LA TAILLE DES IMAGES : celle de la boîte (42 dp), réduite si elle dépasse la mémoire permise par Android
        // pour un widget (environ 1,5 écran ; on garde 10 % de marge).
        val ecran = (c.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        val taille = Point()
        @Suppress("DEPRECATION") ecran.getRealSize(taille)
        val permis = 6.0 * taille.x * taille.y * 0.9
        var px = min((COTE_DP * c.resources.displayMetrics.density).toInt(), sqrt(permis / (N * 4.0)).toInt()).coerceAtLeast(32)

        for (essai in 0 until 3) {
            try {
                val debut = SystemClock.uptimeMillis()
                val images = dessinerPage(c, px, groupe, pages[0], hsv, style)
                envoyer(c, groupe, ids, images)
                val etat = Etat(signature, pages, 0, px, style, SystemClock.uptimeMillis())
                etats[groupe] = etat
                Journal.noter(c, "widget dossier $groupe (${if (style == CARROUSEL) "carrousel" else "mosaïque"}) : ${apps.size} apps, ${pages.size} page(s), $N images de $px px en ${SystemClock.uptimeMillis() - debut} ms")
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
    private fun dessinerPage(c: Context, px: Int, groupe: String, apps: List<String>, hsv: FloatArray?, style: Int): List<Bitmap> {
        val etoile = couleursLogo(groupe, hsv)
        // la réserve : on garde celle qu'on a si elle est à la bonne taille, sinon on en crée une neuve
        val images: Array<Bitmap> = reserve?.takeIf { reservePx == px }
            ?: Array(N) { Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888) }.also { reserve = it; reservePx = px; reserveCle = "" }
        // (le logo dépend du dossier, des couleurs ET du style : chaque style a sa façon de le faire partir et revenir)
        val cle = groupe + ":" + etoile.joinToString(",") + ":" + style
        val etoileDejaLa = cle == reserveCle
        val dessin = Boucle(apps.map { icone(c, it, px) }, etoile, groupe, style)
        // les images où l'on ne voit QUE le logo, identiques pour toutes les pages (selon le style)
        val (logoAvant, logoApres) = when (style) {
            CARROUSEL -> 1.8 to 11.2
            MOSAIQUE -> 2.0 to 11.75
            else -> 2.0 to 11.3
        }
        for (i in 0 until N) {
            val t = i * PAS / 1000.0
            if (etoileDejaLa && (t < logoAvant || t >= logoApres)) continue
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
        // (18/11) exactement le dégradé du logo dans le dossier ouvert (calculé en OKLCH, voir Teinte.depuis)
        return Teinte.depuis(hsv).logo
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
                    // (18/11) d'abord les infos utiles (instantané), puis les dossiers à redessiner s'il y en a
                    try { if (enMarche) mettreAJourInfos(c) }
                    catch (e: Exception) { Journal.noter(c, "ERREUR widget dossier (infos) : " + Journal.pile(e)) }
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
            val images = dessinerPage(c, etat.px, groupe, etat.pages[etat.page], hsv, etat.style)
            etat.envoyeA = SystemClock.uptimeMillis()
            try { envoyer(c, groupe, ids, images) }
            catch (e: IllegalArgumentException) { Journal.noter(c, "widget dossier $groupe : page refusée (${e.message})") }
        }
    }

    // ---------------- LE DESSIN DE LA BOUCLE (même chose que l'aperçu Python dossier_n.py) ----------------
    // On dessine dans un repère de 100 x 100 (tout le galet), centre (50, 48), comme l'ancienne animation.
    private class Boucle(val icones: List<Bitmap>, val teintes: IntArray, val groupe: String, val style: Int) {
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
            cv.clipPath(FORME_GALET)            // rien ne dépasse de la forme du galet
            when (style) {
                CARROUSEL -> carrousel(cv, t)
                MOSAIQUE -> mosaique(cv, t)
                else -> { logo(cv, t); anneau(cv, t); points(cv, t); tuiles(cv, t) }
            }
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
            cv.save()
            cv.rotate(tour.toFloat(), CX, CY)
            cv.scale(taille.toFloat(), taille.toFloat(), CX, CY)
            LogosDossier.dessiner(cv, groupe, geste(t), teintes)
            cv.restore()
        }

        fun geste(t: Double): Double {
            // l'horloge du geste : un peu plus rapide que le temps (1,15 x) pour tenir avant la sortie ;
            // en dehors, le logo est au repos (0,3)
            // Les logos qui se DESSINENT (11/11, voir LogosDossier.debutTrace) : leur geste démarre dès que le logo
            // revient (10,9 s), depuis le dessin vide, et continue après le tour de boucle (0 -> 2,3 s), un peu plus
            // lentement (0,85 x) pour remplir tout ce temps. Comme ça, il ne réapparaît pas plein pour s'effacer ensuite.
            val trace = LogosDossier.debutTrace(groupe)
            return when {
                trace != null && t >= 10.9 -> min(trace + (t - 10.9) * 0.85, 2.9)
                trace != null && t < 2.3 -> min(trace + (t + DUREE - 10.9) * 0.85, 2.9)
                trace != null -> trace
                t < 2.3 -> min(0.3 + t * 1.15, 2.9)
                else -> 0.3
            }
        }


        // (18/11, correction) LE GESTE DU LOGO dans Carrousel et Mosaïque : le logo y reste VISIBLE pendant toute la
        // boucle (en petit, ou en morceaux). Il joue donc son geste au début (0 -> 2,3 s), puis reste au repos avec
        // son dessin COMPLET (0,3). Avant, les logos qui se tracent (Finance, Maps, Health, News) étaient remis à
        // « vide » dès 2,3 s (pratique quand le logo disparaissait, mais là on le voyait se vider : le bug de Finance).
        fun gesteVisible(t: Double): Double = if (t < 2.3) min(0.3 + t * 1.15, 2.9) else 0.3

        // le logo, droit, à la taille demandée, avec son geste (alpha : 0 à 255, pour le faire disparaître en fondu)
        fun logoSimple(cv: Canvas, t: Double, taille: Double, alpha: Int = 255) {
            if (taille <= 0.001 || alpha <= 0) return
            cv.save()
            cv.scale(taille.toFloat(), taille.toFloat(), CX, CY)
            LogosDossier.dessiner(cv, groupe, gesteVisible(t), teintes, alpha)
            cv.restore()
        }

        private val zone = RectF()
        private val tuile = Path()

        // ============ (18/11) A · CARROUSEL (v3, « toute la place ») ============
        // Le logo (plus grand) fait son geste, puis devient tout petit au centre (un point de lumière). Les apps tournent
        // autour sur un anneau INCLINÉ (une ellipse, vue en 3D) qui tourne doucement (36° par seconde : à 25 images par
        // seconde, chaque image avance peu, donc ça reste coulé). Devant (bas de l'ellipse) : grandes, nettes, avec
        // une petite ombre ; derrière (haut) : plus petites, un peu estompées, elles passent derrière le logo.
        // AU PLUS 4 APPS sur l'anneau, pour qu'elles soient GRANDES. S'il y en a plus, chaque fois qu'une app passe
        // tout au fond, elle s'efface et la suivante du dossier prend sa place : on voit défiler tout le dossier.
        private val m = min(n, 4)
        private val ordre = Array(m) { it }
        private val profondeur = FloatArray(m)
        private val posX = FloatArray(m)
        private val posY = FloatArray(m)
        private val quelle = IntArray(m)          // l'app montrée à chaque place de l'anneau
        private val presence = FloatArray(m)      // 0 = effacée (le temps d'être remplacée), 1 = visible
        fun carrousel(cv: Canvas, t: Double) {
            val taille = LOGO_ECH * cle(t, 0.0 to 1.0 by LIN, 0.9 to 1.06 by DOUX, 1.8 to 1.0 by DOUX, 2.6 to 0.28 by DOUX,
                                        10.1 to 0.28 by LIN, 11.2 to 1.0 by RESSORT)
            val ouv = cle(t, 1.9 to 0.0 by LIN, 2.9 to 1.0 by DOUX, 9.9 to 1.0 by LIN, 10.8 to 0.0 by DOUX)
            if (m == 0 || ouv <= 0.001) { logoSimple(cv, t, taille); return }
            val avance = Math.toRadians(36.0) * maxOf(0.0, t - 1.9) + Math.toRadians(30.0) * ouv
            val rx = (if (m <= 1) 12.0 else 28.0) * ouv
            val ry = (if (m <= 1) 6.0 else 17.0 + 1.5 * sin(t * 0.8)) * ouv
            val base = when (m) { 1 -> 60.0; 2 -> 40.0; else -> min(38.0, 2 * Math.PI * 28 / m * 0.8) }
            for (k in 0 until m) {
                val tourne = avance + 2 * Math.PI * k / m
                val a = -Math.PI / 2 + tourne
                profondeur[k] = ((sin(a) + 1) / 2).toFloat()           // 0 = tout au fond, 1 = tout devant
                posX[k] = (50.0 + rx * cos(a)).toFloat()
                posY[k] = (50.0 + ry * sin(a)).toFloat()
                // le nombre de tours faits par cette place = combien de fois elle est passée au fond
                val tours = kotlin.math.floor(tourne / (2 * Math.PI)).toInt()
                quelle[k] = if (n > m) (k + tours * m) % n else k
                presence[k] = if (n > m) min(1f, profondeur[k] * 6f) else 1f
                ordre[k] = k
            }
            ordre.sortBy { profondeur[it] }                            // on dessine du fond vers l'avant
            fun app(k: Int) {
                val pr = profondeur[k]
                val h = (base * (0.66 + 0.34 * pr) / 2 * (0.35 + 0.65 * ouv)).toFloat()
                val alpha = ((0.62 + 0.38 * pr) * min(1.0, ouv * 3) * presence[k]).toFloat()
                if (alpha <= 0.004f) return
                p.reset(); p.isAntiAlias = true; p.isFilterBitmap = true
                if (pr > 0.5f) {                                       // une ombre douce sous les apps de devant
                    p.color = Color.argb((0.22f * (pr - 0.5f) * 2 * ouv.toFloat() * 255).toInt(), 0, 0, 0)
                    zone.set(posX[k] - h * 0.75f, posY[k] + h - 0.4f, posX[k] + h * 0.75f, posY[k] + h + 2.6f)
                    cv.drawOval(zone, p)
                }
                p.alpha = (255 * alpha).toInt().coerceIn(0, 255)
                zone.set(posX[k] - h, posY[k] - h, posX[k] + h, posY[k] + h)
                cv.drawBitmap(icones[quelle[k]], null, zone, p)
            }
            for (k in ordre) if (profondeur[k] < 0.5f) app(k)          // derrière le logo
            logoSimple(cv, t, taille)
            for (k in ordre) if (profondeur[k] >= 0.5f) app(k)         // devant le logo
        }

        // ============ (18/11) B · MOSAÏQUE (v3, « toute la place », une tuile par app) ============
        // Le logo (plus grand) se FEND en tuiles, UNE PAR APP, rangées selon leur nombre :
        //   1 = une grande tuile ; 2, 3, 4 = tuiles moyennes sur 2 colonnes (2 / 2+1 / 2+2) ;
        //   5 à 9 = 3 colonnes, rangées centrées : 5 = 3+2, 6 = 3+3, 7 = 2+3+2, 8 = 3+2+3, 9 = 3+3+3.
        // Pendant la fente, une PLAQUE DE VERRE teintée (dégradé aux couleurs du logo) apparaît derrière lui : elle
        // s'étend sur toute la grille, donc chaque tuile a un vrai fond, même là où le logo ne passe presque pas
        // (sans elle, les logos qui ne remplissent pas leur carré — la courbe de Finance, l'appareil photo… —
        // laissaient des tuiles vides). Une vague en diagonale RETOURNE les tuiles : la tuile rétrécit en largeur
        // jusqu'à n'être qu'un trait (on la voit de profil), s'assombrit, puis ré-élargit côté app. Puis la vague
        // repasse dans l'autre sens, les tuiles redeviennent le logo, qui se ressoude avec un petit ressort.
        // (corrections) Au départ, toute la grille est ramenée à la TAILLE DU LOGO : les morceaux tombent pile sur lui,
        // collés entre eux, puis la grille grandit et les espaces s'ouvrent. Le logo entier, dessous, grandit EN MÊME
        // TEMPS que les morceaux (sinon on voyait 2 logos décalés pendant le fondu : le bug de l'appareil photo).
        private val nb = min(n, PAR_PAGE).coerceAtLeast(1)
        private val coteT = when { nb == 1 -> 70f; nb <= 4 -> 38f; else -> 25f }   // le côté d'une tuile
        private val placesT: List<FloatArray> = run {
            val rangs = when (nb) { 1 -> intArrayOf(1); 2 -> intArrayOf(2); 3 -> intArrayOf(2, 1); 4 -> intArrayOf(2, 2)
                5 -> intArrayOf(3, 2); 6 -> intArrayOf(3, 3); 7 -> intArrayOf(2, 3, 2); 8 -> intArrayOf(3, 2, 3)
                else -> intArrayOf(3, 3, 3) }
            val pas = coteT + if (nb <= 4) 3f else 2.5f
            val liste = ArrayList<FloatArray>()
            rangs.forEachIndexed { r, combien ->
                for (j in 0 until combien)
                    liste.add(floatArrayOf(CX + (j - (combien - 1) / 2f) * pas, CY + (r - (rangs.size - 1) / 2f) * pas))
            }
            liste
        }
        // le carré qui contient la grille : gauche, haut, droite, bas, et son côté
        private val bord: FloatArray = run {
            var x0 = 1e9f; var y0 = 1e9f; var x1 = -1e9f; var y1 = -1e9f
            for (pl in placesT) {
                x0 = min(x0, pl[0] - coteT / 2); y0 = min(y0, pl[1] - coteT / 2)
                x1 = maxOf(x1, pl[0] + coteT / 2); y1 = maxOf(y1, pl[1] + coteT / 2)
            }
            floatArrayOf(x0, y0, x1, y1, maxOf(x1 - x0, y1 - y0))
        }
        // les deux couleurs de la plaque de verre : un brun très sombre mélangé aux couleurs du logo
        private fun melange(a: Int, b: Int, u: Float) = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * u).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * u).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).toInt())
        private val plaque1 = melange(Color.rgb(34, 24, 22), teintes[0], 0.42f)
        private val plaque2 = melange(Color.rgb(34, 24, 22), teintes[2], 0.30f)
        private val trait = Paint(Paint.ANTI_ALIAS_FLAG).also { it.style = Paint.Style.STROKE; it.strokeWidth = 0.7f }
        fun mosaique(cv: Canvas, t: Double) {
            val taille = cle(t, 0.0 to 1.0 by LIN, 0.9 to 1.05 by DOUX, 1.8 to 1.0 by DOUX,
                             11.1 to 1.0 by LIN, 11.35 to 1.06 by DEC, 11.7 to 1.0 by DOUX)
            val fendu = cle(t, 2.1 to 0.0 by LIN, 2.7 to 1.0 by DOUX, 10.5 to 1.0 by LIN, 11.1 to 0.0 by DOUX).toFloat()
            if (n == 0 || fendu <= 0.001f) { logoSimple(cv, t, LOGO_ECH * taille); return }
            val lisse = fendu * fendu * (3 - 2 * fendu)
            val g = gesteVisible(t)
            val x0 = bord[0]; val y0 = bord[1]; val x1 = bord[2]; val y1 = bord[3]; val s = bord[4]
            // le logo (52 unités utiles, de 24 à 76) étalé sur la grille, avec une marge sur la plaque (x 1,14)
            val k2 = s / 52f / (1f + 0.14f * lisse)
            // la grille : à la taille du logo affiché au départ, puis à sa vraie taille
            val depart = 52f * LOGO_ECH / s
            val zoomG = depart + (1 - depart) * lisse
            // le logo entier, dessous : il grandit comme les morceaux et s'efface en douceur
            logoSimple(cv, t, (k2 * zoomG).toDouble(), (255 * (1 - lisse)).toInt())
            for (j in 0 until nb) {
                val px = placesT[j][0]; val py = placesT[j][1]
                val diag = ((px - x0) / (x1 - x0 + 0.01f) + (py - y0) / (y1 - y0 + 0.01f)) * 0.3
                // aller : logo -> app (vers 2,9 s) ; retour : app -> logo (vers 9,4 s), en vague diagonale
                val aller = cle(t, (2.9 + diag) to 0.0 by LIN, (3.45 + diag) to 1.0 by DOUX)
                val retour = cle(t, (9.4 + 0.6 - diag) to 0.0 by LIN, (9.95 + 0.6 - diag) to 1.0 by DOUX)
                val ang = Math.PI * maxOf(0.0, aller - retour)          // 0 = côté logo, PI = côté app
                val sn = sin(ang).toFloat()
                val largeur = maxOf(0.02f, kotlin.math.abs(cos(ang)).toFloat())
                val h = (coteT / 2 + 1.5f * (1 - fendu)) * (1 + 0.06f * sn)   // collées au départ, puis l'espace s'ouvre
                cv.save()
                cv.translate(CX + (px - CX) * zoomG, CY + (py - CY) * zoomG)
                cv.scale(largeur * zoomG, zoomG)
                tuile.reset()
                p.reset(); p.isAntiAlias = true; p.isFilterBitmap = true
                val arrondi = h * 0.45f * fendu + 0.01f
                if (ang < Math.PI / 2 || j >= n) {
                    tuile.addRoundRect(-h, -h, h, h, arrondi, arrondi, Path.Direction.CW)
                    cv.save()
                    cv.clipPath(tuile)
                    // la plaque de verre : son dégradé couvre toute la grille (il continue d'une tuile à l'autre)
                    p.shader = LinearGradient(x0 - px, y0 - py, x1 - px, y1 - py,
                        Teinte.avecAlpha(plaque1, 255 * fendu), Teinte.avecAlpha(plaque2, 255 * fendu), Shader.TileMode.CLAMP)
                    cv.drawRect(-h, -h, h, h, p)
                    p.shader = LinearGradient(0f, -h, 0f, 0f, Color.argb((0.14f * fendu * 255).toInt(), 255, 255, 255),
                        Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    cv.drawRect(-h, -h, h, 0f, p)                         // le reflet en haut de la tuile
                    p.shader = null
                    // le morceau de logo : le logo étalé sur toute la grille, vu à travers la tuile
                    cv.translate(-px, -py)
                    cv.translate(CX, CY); cv.scale(k2, k2); cv.translate(-CX, -CY)
                    LogosDossier.dessiner(cv, groupe, g, teintes)
                    cv.restore()
                    trait.color = Color.argb((0.16f * fendu * 255).toInt(), 255, 255, 255)   // le liseré de verre
                    cv.drawPath(tuile, trait)
                    p.color = Color.argb((0.35f * sn * 255).toInt(), 0, 0, 0)               // elle s'assombrit en tournant
                    cv.drawPath(tuile, p)
                } else {
                    zone.set(-h, -h, h, h)
                    cv.drawBitmap(icones[j], null, zone, p)
                    if (sn > 0.01f) {
                        tuile.addRoundRect(-h * 0.88f, -h * 0.88f, h * 0.88f, h * 0.88f, h * 0.52f, h * 0.52f, Path.Direction.CW)
                        p.color = Color.argb((0.3f * sn * 255).toInt(), 0, 0, 0)
                        cv.drawPath(tuile, p)
                    }
                }
                cv.restore()
            }
        }

        // ============ L'ANCIENNE BOUCLE (style ANCIENNE, plus tirée depuis le 18/11) ============
        // l'anneau : il se trace (3,8 s) puis s'efface (5,4 s) derrière les points
        fun anneau(cv: Canvas, t: Double) {
            if (t >= 11.9) return
            // (17/11, v2) il s'efface un peu plus tard (jusqu'à 4,8 s), comme la rotation des apps, plus longue
            val fin = cle(t, 2.4 to 0.0 by LIN, 3.5 to 1.0 by DOUX)
            val debut = cle(t, 3.9 to 0.0 by LIN, 4.8 to 1.0 by DOUX)
            if (fin <= debut) return
            val morceau = Path()
            val L = mesure.length
            mesure.getSegment((debut * L).toFloat(), (fin * L).toFloat(), morceau, true)
            cv.save()
            cv.rotate(cle(t, 2.0 to -90.0 by LIN, 4.8 to 120.0 by DOUX).toFloat(), CX, CY)   // tourne avec les apps
            p.reset(); p.isAntiAlias = true
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.4f; p.strokeCap = Paint.Cap.ROUND
            p.color = Color.argb(150, 255, 255, 255)
            cv.drawPath(morceau, p)
            cv.restore()
        }

        // LES APPS QUI TOURNENT (17/11, demande de Will : avant, c'étaient des points de couleur).
        // Les vraies icônes jaillissent du centre l'une après l'autre (petit ressort), font trois quarts de tour sur
        // l'anneau en restant DROITES (pour qu'on les reconnaisse), avec un léger halo de leur couleur. Puis elles
        // filent l'une après l'autre vers leur place en grandissant : arrivées, la tuile (fonction suivante) prend
        // le relais, à la même taille et au même endroit, donc on ne voit aucun changement.
        // Sur l'anneau, leur taille dépend de leur nombre, pour ne pas se chevaucher (au plus 16 unités).
        val tailleAnneau = min(16.0, 0.78 * 2 * Math.PI * 22 / n.coerceAtLeast(1))
        fun points(cv: Canvas, t: Double) {
            if (t <= 2.0 || n == 0) return
            // (17/11, v2, demande de Will : « plus lent et plus fluide ») la rotation démarre dès que les icônes
            // sortent (2,0 s au lieu de 2,3), dure plus longtemps (jusqu'à 4,8 s) et fait un peu moins de chemin
            // (210° au lieu de 270°) : au plus vite, elles vont environ 1,6 fois moins vite, donc chaque image avance moins et
            // le mouvement paraît plus coulé (le widget n'affiche que 25 images par seconde).
            val orbite = cle(t, 2.0 to 0.0 by LIN, 4.8 to 210.0 by DOUX)
            val depart = if (n == 4) -135.0 else -90.0      // 4 apps : sur les diagonales, comme l'ancienne animation
            p.reset(); p.isAntiAlias = true; p.isFilterBitmap = true
            val zone = RectF()
            for (i in 0 until n) {
                val fin = arrivee(i)
                if (t >= fin) continue                        // arrivée à sa place : c'est la tuile qui la dessine
                val sortie = cle(t, (2.0 + 0.04 * i) to 0.0 by LIN, (2.55 + 0.04 * i) to 1.0 by RESSORT)
                if (sortie <= 0.001) continue
                val a = Math.toRadians(orbite + depart + 360.0 * i / n)
                val ox = CX + 22 * sortie * cos(a); val oy = CY + 22 * sortie * sin(a)
                // le trajet vers sa place : il commence un peu AVANT la fin de la rotation (4,45 s), pour que l'icône
                // passe du cercle à la grille sans s'arrêter, et il dure plus longtemps (0,9 s au lieu de 0,7)
                val vol = cle(t, debutVol(i) to 0.0 by LIN, fin to 1.0 by DOUX)
                val x = (ox + (places[i][0] - ox) * vol).toFloat(); val y = (oy + (places[i][1] - oy) * vol).toFloat()
                val h = (sortie * cle(t, debutVol(i) to tailleAnneau by LIN, fin to cote.toDouble() by RESSORT) / 2).toFloat()
                // le halo, qui s'efface pendant le trajet
                p.color = couleurs[i]; p.alpha = (0.22 * (1 - vol) * 255).toInt()
                cv.drawCircle(x, y, h * 1.35f, p)
                p.alpha = 255
                zone.set(x - h, y - h, x + h, y + h)
                cv.drawBitmap(icones[i], null, zone, p)
            }
        }
        // le moment où l'app « i » arrive à sa place (elles arrivent l'une après l'autre)
        fun debutVol(i: Int) = 4.45 + 0.05 * i
        fun arrivee(i: Int) = 5.35 + 0.05 * i

        // LES ICÔNES à leur place : elles prennent le relais des apps qui tournaient (dès leur arrivée), une vague
        // passe deux fois (espacées : 6,9 et 8,6 s), et elles restent jusqu'à 10,3 s avant de rentrer au centre
        fun tuiles(cv: Canvas, t: Double) {
            if (t >= 11.9) return
            p.reset(); p.isAntiAlias = true; p.isFilterBitmap = true
            val zone = RectF()
            for (i in 0 until n) {
                val d = 0.06 * i
                val vague1 = 6.9 + 0.12 * i; val vague2 = 8.6 + 0.12 * i
                val sc = cle(t, (arrivee(i) - 0.001) to 0.0 by LIN, arrivee(i) to 1.0 by LIN,
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
