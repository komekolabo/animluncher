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
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.widget.RemoteViews
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

// LE WIDGET « UNE APP DANS UN GALET » (essai du 09/11, d'abord pour Claude).
// Le galet (fond générique en verre sombre) reste immobile ; le LOGO de l'app flotte dedans, avec son
// ombre sur le fond du galet ; une bande de lumière traverse le verre de temps en temps.
//
// LE PROBLÈME : le logo de l'app est une image qu'on ne connaît qu'au moment de poser le widget (on la
// demande à Android). Or un widget ne sait animer que des dessins préparés à l'avance dans notre app.
// L'ASTUCE (« folioscope ») : au moment de poser le widget, NOTRE app dessine elle-même 36 images de la
// flottaison (3 s), et les donne au widget, qui les fait défiler en boucle tout seul (ViewFlipper,
// 12 images par seconde). Le galet et la bande de lumière, eux, sont des dessins préparés (voir
// res/drawable/widget_galet*.xml).
class WidgetApp : AppWidgetProvider() {

    // Android nous demande de préparer le widget (quand on le pose, ou au redémarrage du téléphone).
    override fun onUpdate(context: Context, gestionnaire: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            try {
                gestionnaire.updateAppWidget(id, vues(context, id))
            } catch (e: Exception) {
                Journal.noter(context, "ERREUR widget galet : ${e.message}")
            }
        }
    }

    // Le widget est retiré de l'accueil : on oublie quelle app il montrait.
    override fun onDeleted(context: Context, ids: IntArray) {
        val carnet = context.getSharedPreferences(CARNET, Context.MODE_PRIVATE).edit()
        for (id in ids) carnet.remove("app_$id")
        carnet.apply()
    }

    companion object {
        private const val CARNET = "widgets_app"
        // Pour l'essai : Claude. (Plus tard : on choisira l'app au moment de poser le widget.)
        const val APP_PAR_DEFAUT = "com.anthropic.claude"
        private const val IMAGES = 36          // 36 images = 3 s à 12 images par seconde

        // Prépare tout le widget : le nom de l'app, les 36 images du folioscope, et l'appui.
        fun vues(c: Context, id: Int): RemoteViews {
            val app = c.getSharedPreferences(CARNET, Context.MODE_PRIVATE)
                .getString("app_$id", APP_PAR_DEFAUT) ?: APP_PAR_DEFAUT
            val pm = c.packageManager
            val v = RemoteViews(c.packageName, R.layout.widget_app)
            val icone = try { pm.getApplicationIcon(app) } catch (_: Exception) { null }
            val nom = try { pm.getApplicationLabel(pm.getApplicationInfo(app, 0)).toString() } catch (_: Exception) { "?" }
            v.setTextViewText(R.id.widget_app_nom, nom)

            // LE FOLIOSCOPE : on vide, puis on ajoute les 36 images une par une.
            v.removeAllViews(R.id.widget_app_folioscope)
            if (icone != null) {
                // la taille exacte du galet à l'écran, en pixels (62 dp)
                val taille = (62 * c.resources.displayMetrics.density).toInt()
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
                color = Color.argb((150 - 70 * v).toInt(), 0, 0, 0)
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
                // Icône moderne en 2 couches : on ne prend que la couche du LOGO (sans son fond).
                // Cette couche fait 108 unités de large, dont le logo occupe le centre : on la dessine à
                // la taille du galet, le logo tombe alors au bon endroit.
                val d = icone.foreground
                val demi = (s * 0.5f).toInt()
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

        // Demande à l'écran d'accueil d'ajouter le widget (Samsung affiche sa fenêtre « Ajouter »).
        fun proposer(a: Activity): Boolean {
            val gestionnaire = AppWidgetManager.getInstance(a)
            if (!gestionnaire.isRequestPinAppWidgetSupported) return false
            return gestionnaire.requestPinAppWidget(ComponentName(a, WidgetApp::class.java), null, null)
        }
    }
}
