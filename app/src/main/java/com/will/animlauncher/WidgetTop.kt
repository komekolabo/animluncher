package com.will.animlauncher

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Shader
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.widget.RemoteViews
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.sqrt

// LE WIDGET « TOP 3 » DU DOSSIER GAMES (09/11, validé par Will sur les aperçus widget_intros2.py).
// Dans le galet 1 x 1 : un petit téléphone s'allume et lance un jeu (voir IntrosJeu.kt et MondesJeu.kt), on
// plonge dans l'écran, puis on voit les 3 jeux LES PLUS JOUÉS des 7 derniers jours (voir Usage.kt), un par
// un : grande icône, son rang, son temps de jeu. Et on recommence avec le monde suivant : monde ouvert, puis
// horreur, puis course de nuit. En regardant 3 tours (environ 45 s), on a donc vu les 3 mondes.
//
// COMMENT C'EST FAIT. Un widget ne peut pas lancer notre code de dessin : il ne sait afficher que des images
// qu'on lui a données. On fabrique donc un « folioscope » :
//  - NOTRE app dessine toutes les images de l'intro (12,5 par seconde) pour les 3 mondes, et les 3 cartes ;
//  - le widget contient une « visionneuse » (top_scene) avec 6 vues : les 3 intros (chacune fait défiler ses
//    images toute seule, 80 ms par image) et les 3 cartes ;
//  - le SERVICE d'AnimLauncher sert de chef d'orchestre : tant qu'on est sur l'accueil, il dit au widget quelle
//    vue montrer (intro du monde 1, carte 1, carte 2, carte 3, intro du monde 2…). Les fondus entre deux vues
//    sont faits par Android (res/anim/top_entre.xml et top_sort.xml).
// Sans le service (accessibilité éteinte), le widget reste sagement sur la carte du n°1.
// Android limite la mémoire d'images d'un widget (environ 1,5 écran) : les images communes aux 3 mondes (le
// téléphone qui s'ouvre) ne sont données qu'une fois, et on réduit un peu la taille des images si besoin.
object WidgetTop {
    const val GROUPE = "jeux"
    private const val PAS = 80L                   // une image d'intro toutes les 80 ms
    private const val CARTE_MS = 3400L            // chaque jeu reste 3,4 s
    private const val PROLONGE = 8                // la dernière image de l'intro est répétée (le temps du fondu)

    private val fil = Executors.newSingleThreadExecutor()        // les dessins se font à part, sans bloquer l'app
    private val minuteur = Handler(Looper.getMainLooper())
    private val JETON = Any()
    private val enAttente = AtomicBoolean(false)

    @Volatile private var signature = ""          // ce que montre le widget (pour ne pas tout refaire pour rien)
    @Volatile private var complet = false         // le folioscope est-il en place ?
    @Volatile private var occupe = false          // un dessin est en cours
    @Volatile private var nCartes = 0
    @Volatile private var dureeIntro = 0L
    @Volatile private var idsPoses = IntArray(0)
    private var enMarche = false                  // le chef d'orchestre tourne-t-il ?
    private var monde = 0                         // le monde de l'intro en cours (0, 1, 2)
    private var etape = 0                         // 0 = l'intro, 1 à 3 = les cartes

    private val LAYOUTS = intArrayOf(R.layout.widget_top_intro1, R.layout.widget_top_intro2, R.layout.widget_top_intro3)
    private val INTROS = intArrayOf(R.id.top_intro1, R.id.top_intro2, R.id.top_intro3)

    // La base du widget : le nom du dossier, et « un appui = ouvrir le dossier ». (Les images arrivent ensuite.)
    fun base(c: Context, id: Int, groupe: String): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_top)
        v.setTextViewText(R.id.top_nom, Groupes.categorie(groupe)?.nom ?: "?")
        v.setOnClickPendingIntent(R.id.top_racine, WidgetGroupe.clic(c, id, groupe))
        return v
    }

    // Les 3 jeux les plus joués des 7 derniers jours : (nom technique, minutes). Les ex æquo gardent l'ordre du dossier.
    private fun top(c: Context): List<Pair<String, Int>> =
        Groupes.apps(c, GROUPE).map { it to Usage.minutes(c, it) }.sortedByDescending { it.second }.take(3)

    // DEMANDE de mise à jour : on vérifie (à part) si quelque chose a changé, et on redessine si besoin.
    fun demander(contexte: Context, forcer: Boolean = false) {
        val c = contexte.applicationContext
        if (forcer) signature = ""
        occupe = true
        if (!enAttente.compareAndSet(false, true)) return          // une demande attend déjà : inutile d'en empiler
        fil.execute {
            enAttente.set(false)
            occupe = true
            try { construire(c) } catch (e: Exception) { Journal.noter(c, "ERREUR widget top : " + Journal.pile(e)) }
            occupe = false
        }
    }

    private fun construire(c: Context) {
        val gestionnaire = AppWidgetManager.getInstance(c)
        val ids = WidgetGroupe.idsDuGroupe(c, GROUPE)
        idsPoses = ids
        if (ids.isEmpty()) { complet = false; return }
        val apps = top(c)
        val type = Appareil.type(c)
        val nouvelle = ids.joinToString(",") + "|" + type + "|" + apps.joinToString(",") { it.first + ":" + it.second }
        if (nouvelle == signature && complet) return

        // LA TAILLE DES IMAGES : celle du galet (62 dp), réduite si elle dépasse la mémoire permise par Android.
        val n = (IntrosJeu.duree(type) * 1000 / PAS).toInt()
        val communes = (IntrosJeu.debutJeu(type) * 1000 / PAS).toInt() + 1
        val distinctes = communes + MondesJeu.NOMBRE * (n - communes) + 3
        val ecran = (c.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        val taille = Point()
        @Suppress("DEPRECATION") ecran.getRealSize(taille)
        val permis = 6.0 * taille.x * taille.y * 0.8
        var px = min((62 * c.resources.displayMetrics.density).toInt(), sqrt(permis / (distinctes * 4.0)).toInt()).coerceAtLeast(48)

        for (essai in 0 until 3) {
            try {
                val debut = SystemClock.uptimeMillis()
                // les cartes des jeux (ou une carte « No games yet » si le dossier est vide)
                val cartes = if (apps.isEmpty()) listOf(carteVide(px))
                             else apps.mapIndexed { i, a -> carte(c, px, a.first, a.second, i + 1, apps.size) }
                val couleur = if (apps.isEmpty()) Color.rgb(150, 110, 230) else couleurIcone(c, apps[0].first)
                // les images des 3 intros : celles d'avant le jeu sont les mêmes pour les 3 mondes
                val partagees = arrayOfNulls<Bitmap>(n)
                val intros = List(MondesJeu.NOMBRE) { m ->
                    List(n) { i ->
                        val t = i * PAS / 1000.0
                        if (i < communes) partagees[i] ?: image(px) { IntrosJeu(it).dessiner(type, 0, px.toDouble(), t, couleur) }.also { b -> partagees[i] = b }
                        else image(px) { IntrosJeu(it).dessiner(type, m, px.toDouble(), t, couleur) }
                    }
                }
                for (id in ids) {
                    val v = base(c, id, GROUPE)
                    v.removeAllViews(R.id.top_scene)
                    for (m in 0 until MondesJeu.NOMBRE) {
                        val intro = RemoteViews(c.packageName, LAYOUTS[m])
                        for (b in intros[m]) intro.addView(INTROS[m], vueImage(c, b))
                        for (k in 0 until PROLONGE) intro.addView(INTROS[m], vueImage(c, intros[m].last()))
                        v.addView(R.id.top_scene, intro)
                    }
                    for (b in cartes) v.addView(R.id.top_scene, vueImage(c, b))
                    v.setDisplayedChild(R.id.top_scene, MondesJeu.NOMBRE)          // en attendant le chef d'orchestre : la carte du n°1
                    gestionnaire.updateAppWidget(id, v)
                }
                nCartes = cartes.size
                dureeIntro = n * PAS
                signature = nouvelle
                complet = true
                Journal.noter(c, "widget top : ${distinctes} images de $px px dessinées en ${SystemClock.uptimeMillis() - debut} ms")
                minuteur.post { if (enMarche) { etape = 0; minuteur.removeCallbacksAndMessages(JETON); avancer(c) } }
                return
            } catch (e: IllegalArgumentException) {
                // trop d'images pour la mémoire d'un widget : on recommence en plus petit
                Journal.noter(c, "widget top : images de $px px refusées (${e.message}), on réduit")
                px = (px * 0.75).toInt().coerceAtLeast(40)
            }
        }
    }

    private fun vueImage(c: Context, b: Bitmap): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_top_image)
        v.setImageViewBitmap(R.id.top_image, b)
        return v
    }

    // UNE IMAGE du widget : un carré aux coins très arrondis (comme le galet), avec un fin liseré de verre.
    private fun image(px: Int, dessin: (Canvas) -> Unit): Bitmap {
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val d = px.toFloat()
        val forme = Path().apply { addRoundRect(RectF(0f, 0f, d, d), d * 0.28f, d * 0.28f, Path.Direction.CW) }
        cv.save()
        cv.clipPath(forme)
        dessin(cv)
        cv.restore()
        val lisere = Paint(Paint.ANTI_ALIAS_FLAG)
        lisere.style = Paint.Style.STROKE
        lisere.strokeWidth = maxOf(1.2f, d * 0.008f)
        lisere.shader = LinearGradient(0f, 0f, 0f, d, Color.argb(90, 255, 255, 255), Color.argb(10, 255, 255, 255), Shader.TileMode.CLAMP)
        val bord = lisere.strokeWidth / 2
        cv.drawRoundRect(bord, bord, d - bord, d - bord, d * 0.28f, d * 0.28f, lisere)
        return b
    }

    // L'icône d'une app, dessinée dans une image carrée de côté s.
    private fun icone(c: Context, pkg: String, s: Int): Bitmap {
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        try {
            val dessin = c.packageManager.getApplicationIcon(pkg)
            dessin.setBounds(0, 0, s, s)
            dessin.draw(Canvas(b))
        } catch (_: Exception) {}
        return b
    }

    // La couleur « moyenne » d'une icône (on regarde quelques points au milieu) : elle teinte le fond de la carte.
    private fun couleurMoyenne(b: Bitmap): Int {
        var r = 0L; var v = 0L; var bl = 0L; var n = 0L
        val pas = maxOf(1, b.width / 12)
        var y = b.height / 5
        while (y < b.height * 4 / 5) {
            var x = b.width / 5
            while (x < b.width * 4 / 5) {
                val p = b.getPixel(x, y)
                if (Color.alpha(p) > 128) { r += Color.red(p); v += Color.green(p); bl += Color.blue(p); n++ }
                x += pas
            }
            y += pas
        }
        if (n == 0L) return Color.rgb(110, 110, 140)
        val couleur = Color.rgb((r / n).toInt(), (v / n).toInt(), (bl / n).toInt())
        // une icône presque noire ou presque grise donnerait un fond terne : on prend un gris-bleu neutre
        val clair = maxOf(Color.red(couleur), Color.green(couleur), Color.blue(couleur))
        return if (clair < 60) Color.rgb(110, 110, 140) else couleur
    }
    private fun couleurIcone(c: Context, pkg: String): Int = couleurMoyenne(icone(c, pkg, 48))

    private fun duree(minutes: Int): String =
        if (minutes >= 60) "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m" else "${minutes}m"

    // LA CARTE D'UN JEU : grande icône au centre, son rang dans une pastille, son temps de jeu fixe dessous,
    // « this week », et en haut 3 petits traits qui disent où on en est (n°1, 2 ou 3).
    private fun carte(c: Context, px: Int, pkg: String, minutes: Int, rang: Int, n: Int): Bitmap = image(px) { cv ->
        val p = Peintre(cv)
        val d = px.toDouble()
        val s = d * 0.54
        val logo = icone(c, pkg, s.toInt().coerceAtLeast(8))
        val col = couleurMoyenne(logo)
        val x = d / 2; val y = d * 0.45
        p.rect(0.0, 0.0, d, d, 0, p.lin(0.0, 0.0, 0.0, d, intArrayOf(p.ton(col, 0.22), p.ton(col, 0.1))))
        p.cercle(x, y, d * 0.62, 0, degrade = p.rad(x, y, d * 0.62, intArrayOf(p.c(p.ton(col, 0.9), 120), p.c(p.ton(col, 0.4), 40), p.c(col, 0)), p.pos(0.0, 0.45, 1.0)))
        p.rrect(x - s * 0.46, y - s * 0.38, x + s * 0.46, y + s * 0.56, s * 0.25, Color.argb(110, 0, 0, 0), flou = s * 0.08)
        cv.drawBitmap(logo, (x - s / 2).toFloat(), (y - s / 2).toFloat(), null)
        if (n > 1) {
            // la pastille du rang, posée sur le coin de l'icône
            val bx = x - s * 0.44; val by = y - s * 0.44; val br = d * 0.075
            p.cercle(bx, by, br * 1.15, p.ton(col, 0.12))
            p.cercle(bx, by, br, Color.argb(240, 255, 255, 255))
            p.texte(rang.toString(), bx, by + br * 0.4, br * 1.15, p.ton(col, 0.25))
            // les 3 petits traits du haut
            val ecart = d * 0.02; val lw = (d * 0.44 - ecart * (n - 1)) / n
            for (q in 0 until n) {
                val tx = d * 0.28 + q * (lw + ecart)
                p.rrect(tx, d * 0.065, tx + lw, d * 0.078, d * 0.0065, Color.argb(if (q < rang) 220 else 55, 255, 255, 255))
            }
        }
        p.texte(duree(minutes), d / 2, d * 0.855, d * 0.115, Color.argb(245, 255, 255, 255))
        p.texte("this week", d / 2, d * 0.925, d * 0.052, Color.argb(125, 255, 255, 255), gras = false)
    }

    private fun carteVide(px: Int): Bitmap = image(px) { cv ->
        val p = Peintre(cv)
        val d = px.toDouble()
        p.rect(0.0, 0.0, d, d, 0, p.lin(0.0, 0.0, 0.0, d, intArrayOf(Color.rgb(34, 26, 52), Color.rgb(14, 12, 24))))
        p.texte("No games", d / 2, d * 0.5, d * 0.12, Color.argb(220, 255, 255, 255))
        p.texte("yet", d / 2, d * 0.64, d * 0.1, Color.argb(150, 255, 255, 255), gras = false)
    }

    // ---------------- LE CHEF D'ORCHESTRE (appelé par le service d'AnimLauncher) ----------------

    // On arrive sur l'écran d'accueil : on met le widget à jour si besoin, et l'animation commence.
    fun surAccueil(contexte: Context) {
        val c = contexte.applicationContext
        minuteur.post {
            if (enMarche) return@post
            enMarche = true
            etape = 0
            demander(c)
            avancer(c)
        }
    }

    // On quitte l'accueil (ou l'écran s'éteint) : on arrête tout, pour ne pas user la batterie.
    fun pause() {
        minuteur.post {
            enMarche = false
            minuteur.removeCallbacksAndMessages(JETON)
        }
    }

    private fun plusTard(c: Context, attente: Long) {
        minuteur.postAtTime({ avancer(c) }, JETON, SystemClock.uptimeMillis() + attente)
    }

    private fun avancer(c: Context) {
        if (!enMarche) return
        if (occupe) { plusTard(c, 300); return }  // les images sont en train d'être dessinées : on repasse dans un instant
        val ids = idsPoses
        if (!complet || ids.isEmpty()) { enMarche = false; return }      // aucun widget « top » posé (ou dessin raté) : rien à faire
        val v = RemoteViews(c.packageName, R.layout.widget_top)
        val attente: Long
        if (etape == 0) {
            v.setDisplayedChild(INTROS[monde], 0)                  // l'intro repart de sa première image…
            v.setDisplayedChild(R.id.top_scene, monde)             // …et on la montre
            attente = dureeIntro + 250
        } else {
            v.setDisplayedChild(R.id.top_scene, MondesJeu.NOMBRE + etape - 1)
            attente = CARTE_MS
        }
        try { AppWidgetManager.getInstance(c).partiallyUpdateAppWidget(ids, v) } catch (_: Exception) {}
        etape++
        if (etape > nCartes) { etape = 0; monde = (monde + 1) % MondesJeu.NOMBRE }
        plusTard(c, attente)
    }
}
