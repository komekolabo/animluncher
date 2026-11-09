package com.will.animlauncher

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.widget.RemoteViews
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

// LE WIDGET « UNE APP DANS UN GALET » (essai du 09/11). On le pose depuis AnimLauncher :
// Animations par app -> l'app -> « Mettre le widget sur l'accueil ».
// Le galet reste immobile, avec le FOND de l'icône de l'app (le orange de Claude, par exemple) et des
// touches de verre (reflet en haut, liseré) ; le LOGO de l'app flotte dedans, avec son ombre en bas du
// galet ; une bande de lumière traverse le verre de temps en temps.
//
// LE PROBLÈME : le logo de l'app est une image qu'on ne connaît qu'au moment de poser le widget (on la
// demande à Android). Or un widget ne sait animer que des dessins préparés à l'avance dans notre app.
// L'ASTUCE (« folioscope ») : au moment de poser le widget, NOTRE app dessine elle-même 60 images de la
// flottaison (3 s), et les donne au widget, qui les fait défiler en boucle tout seul (ViewFlipper,
// 20 images par seconde). Le galet (fond de l'app) est dessiné une seule fois, lui aussi par notre app.
// La bande de lumière est un dessin préparé (voir res/drawable/widget_galet_reflet*.xml).
class WidgetApp : AppWidgetProvider() {

    // Android nous demande de préparer le widget (quand on le pose, ou au redémarrage du téléphone).
    override fun onUpdate(context: Context, gestionnaire: AppWidgetManager, ids: IntArray) {
        val carnet = context.getSharedPreferences(CARNET, Context.MODE_PRIVATE)
        for (id in ids) {
            // Un widget tout neuf : il prend l'app qu'on vient de demander (voir proposer)
            if (!carnet.contains("app_$id")) {
                carnet.getString("en_attente", null)?.let { carnet.edit().putString("app_$id", it).apply() }
            }
            try {
                gestionnaire.updateAppWidget(id, vues(context, id))
            } catch (e: Exception) {
                Journal.noter(context, "ERREUR widget galet : ${e.message}")
            }
        }
    }

    // Samsung nous confirme que le widget a bien été posé (voir proposer) : il nous donne son numéro.
    // On retient quelle app il doit montrer, et on le redessine avec elle.
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_POSE) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val app = intent.getStringExtra("app")
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID && app != null) {
                context.getSharedPreferences(CARNET, Context.MODE_PRIVATE).edit().putString("app_$id", app).apply()
                try {
                    AppWidgetManager.getInstance(context).updateAppWidget(id, vues(context, id))
                } catch (e: Exception) {
                    Journal.noter(context, "ERREUR widget galet : ${e.message}")
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    // Le widget est retiré de l'accueil : on oublie quelle app il montrait.
    override fun onDeleted(context: Context, ids: IntArray) {
        val carnet = context.getSharedPreferences(CARNET, Context.MODE_PRIVATE).edit()
        for (id in ids) carnet.remove("app_$id")
        carnet.apply()
    }

    companion object {
        private const val CARNET = "widgets_app"
        private const val ACTION_POSE = "com.will.animlauncher.WIDGET_GALET_POSE"
        // Si on ne sait pas quelle app montrer (widget posé depuis la liste des widgets) : Claude.
        const val APP_PAR_DEFAUT = "com.anthropic.claude"
        // Taille du dessin d'une icône moderne (108 unités, dont les 72 du milieu forment l'icône
        // visible) par rapport au galet : 1,5 fois le galet, comme une vraie icône (un poil plus petit,
        // pour laisser au logo la place de flotter).
        private const val ECHELLE = 1.5f
        // 60 images = 3 s à 20 images par seconde (avec 36 images à 12/s, Will trouvait que ça saccadait)
        private const val IMAGES = 60

        // Prépare tout le widget : le nom de l'app, le galet, les 60 images du folioscope, et l'appui.
        fun vues(c: Context, id: Int): RemoteViews {
            val app = c.getSharedPreferences(CARNET, Context.MODE_PRIVATE)
                .getString("app_$id", APP_PAR_DEFAUT) ?: APP_PAR_DEFAUT
            val pm = c.packageManager
            val v = RemoteViews(c.packageName, R.layout.widget_app)
            val icone = try { pm.getApplicationIcon(app) } catch (_: Exception) { null }
            val nom = try { pm.getApplicationLabel(pm.getApplicationInfo(app, 0)).toString() } catch (_: Exception) { "?" }
            v.setTextViewText(R.id.widget_app_nom, nom)

            // LE FOLIOSCOPE : on vide, puis on ajoute les images une par une.
            v.removeAllViews(R.id.widget_app_folioscope)
            if (icone != null) {
                // la taille exacte du galet à l'écran, en pixels (62 dp)
                val taille = (62 * c.resources.displayMetrics.density).toInt()
                // le galet, avec le fond de l'icône de l'app (dessiné une seule fois)
                v.setImageViewBitmap(R.id.widget_app_galet, dessinerGalet(icone, taille))
                for (i in 0 until IMAGES) {
                    val image = RemoteViews(c.packageName, R.layout.widget_app_image)
                    image.setImageViewBitmap(R.id.widget_app_image, dessiner(icone, taille, i.toFloat() / IMAGES))
                    v.addView(R.id.widget_app_folioscope, image)
                }
            }

            // L'APPUI : ouvre l'app (et l'animation d'AnimLauncher se joue, comme depuis une icône).
            pm.getLaunchIntentForPackage(app)?.let { ouvrir ->
                ouvrir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                v.setOnClickPendingIntent(R.id.widget_app_racine, PendingIntent.getActivity(c, id, ouvrir,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            return v
        }

        // LE GALET : le fond de l'icône de l'app (sa couleur ou son dégradé), découpé à la forme d'icône
        // Samsung, avec des touches de verre par-dessus (reflet en haut, liseré lumineux en haut).
        private fun dessinerGalet(icone: Drawable, taille: Int): Bitmap {
            val b = Bitmap.createBitmap(taille, taille, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val s = taille.toFloat()
            val forme = formeGalet(s)
            c.save()
            c.clipPath(forme)
            if (icone is AdaptiveIconDrawable && icone.background != null) {
                // Icône moderne : on dessine sa couche de FOND, à la même échelle que le logo.
                val d = icone.background
                val demi = (s * ECHELLE / 2).toInt()
                d.setBounds(taille / 2 - demi, taille / 2 - demi, taille / 2 + demi, taille / 2 + demi)
                d.draw(c)
            } else {
                // Ancienne icône : on remplit avec la couleur qu'on trouve sur son bord.
                c.drawColor(couleurDuBord(icone))
            }
            // le reflet en haut du verre (très léger)
            val reflet = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, 0f, s * 0.36f, Color.argb(34, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            }
            c.drawOval(s * 0.08f, s * 0.14f, s * 0.92f, s * 0.50f, reflet)
            c.restore()
            // le liseré : un trait fin tout autour, plus lumineux en haut
            val lisere = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = s * 0.014f
                shader = LinearGradient(0f, 0f, 0f, s, intArrayOf(Color.argb(80, 255, 255, 255),
                    Color.argb(26, 255, 255, 255), Color.argb(12, 255, 255, 255)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            }
            c.drawPath(forme, lisere)
            return b
        }

        // La couleur moyenne du bord d'une icône (pour les anciennes icônes, sans couche de fond).
        private fun couleurDuBord(icone: Drawable): Int {
            val n = 24
            val petit = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            icone.setBounds(0, 0, n, n)
            icone.draw(Canvas(petit))
            var r = 0L; var g = 0L; var bl = 0L; var nb = 0L
            for (x in 0 until n) for (y in 0 until n) {
                if (x in 3 until n - 3 && y in 3 until n - 3) continue        // seulement le bord
                val p = petit.getPixel(x, y)
                if (Color.alpha(p) < 200) continue                             // on ignore le transparent
                r += Color.red(p); g += Color.green(p); bl += Color.blue(p); nb++
            }
            return if (nb == 0L) Color.rgb(21, 26, 46) else Color.rgb((r / nb).toInt(), (g / nb).toInt(), (bl / nb).toInt())
        }

        // UNE IMAGE DU FOLIOSCOPE, à l'instant « u » de la boucle (0 = début, 1 = fin).
        // Fond transparent : on ne dessine que l'ombre et le logo (le galet est dessous, la lumière dessus).
        private fun dessiner(icone: Drawable, taille: Int, u: Float): Bitmap {
            val b = Bitmap.createBitmap(taille, taille, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val s = taille.toFloat()
            val v = 0.5f - 0.5f * cos(2f * PI.toFloat() * u)      // hauteur du logo : 0 = en bas, 1 = en haut

            // on ne dessine qu'à l'intérieur du galet (même forme que widget_galet.xml)
            c.clipPath(formeGalet(s))

            // 1) L'OMBRE, posée en bas du galet : plus petite, plus claire et plus floue quand le logo monte
            val ombre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((120 - 55 * v).toInt(), 0, 0, 0)
                maskFilter = BlurMaskFilter(s * (0.03f + 0.035f * v), BlurMaskFilter.Blur.NORMAL)
            }
            val largeur = s * (0.27f - 0.05f * v)
            val yOmbre = s / 2 + s * 0.33f
            c.drawOval(s / 2 - largeur, yOmbre - s * 0.035f, s / 2 + largeur, yOmbre + s * 0.035f, ombre)

            // 2) LE LOGO : il monte de 7 % de la taille du galet, et grossit à peine (3 %) en montant
            val yLogo = s / 2 - s * 0.03f - s * 0.07f * v
            val e = 1f + 0.03f * v
            c.save()
            c.translate(s / 2, yLogo)
            c.scale(e, e)
            if (icone is AdaptiveIconDrawable && icone.foreground != null) {
                // Icône moderne en 2 couches : on ne prend que la couche du LOGO (son fond est déjà
                // dessiné dans le galet). Même échelle que le fond, pour que le logo ait sa vraie taille.
                val d = icone.foreground
                val demi = (s * ECHELLE / 2).toInt()
                d.setBounds(-demi, -demi, demi, demi)
                d.draw(c)
            } else {
                // Ancienne icône en une seule couche : on la pose entière, un peu plus petite.
                val demi = (s * 0.31f).toInt()
                icone.setBounds(-demi, -demi, demi, demi)
                icone.draw(c)
            }
            c.restore()
            return b
        }

        // La forme du galet (la « super-ellipse » de Samsung), à la taille s.
        private fun formeGalet(s: Float): Path {
            val p = Path()
            val n = 2.6
            val r = s * 0.493f
            for (i in 0 until 96) {
                val a = 2.0 * PI * i / 96
                val cx = cos(a); val sy = sin(a)
                val x = (s / 2 + r * (sign(cx) * abs(cx).pow(2.0 / n))).toFloat()
                val y = (s / 2 + r * (sign(sy) * abs(sy).pow(2.0 / n))).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            return p
        }

        // Demande à l'écran d'accueil d'ajouter le widget de l'app « app » (Samsung affiche sa fenêtre
        // « Ajouter à l'écran d'accueil »). On note l'app demandée, et on demande à Samsung de nous
        // prévenir quand le widget est posé (« intention en attente » : MUTABLE pour que Samsung puisse
        // y ajouter le numéro du nouveau widget).
        fun proposer(a: Activity, app: String): Boolean {
            val gestionnaire = AppWidgetManager.getInstance(a)
            if (!gestionnaire.isRequestPinAppWidgetSupported) return false
            a.getSharedPreferences(CARNET, Context.MODE_PRIVATE).edit().putString("en_attente", app).apply()
            val pose = Intent(a, WidgetApp::class.java).setAction(ACTION_POSE).putExtra("app", app)
            val rappel = PendingIntent.getBroadcast(a, app.hashCode(), pose,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return gestionnaire.requestPinAppWidget(ComponentName(a, WidgetApp::class.java), null, rappel)
        }
    }
}
