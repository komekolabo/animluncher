package com.will.animlauncher

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BlendMode
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews

// LE WIDGET « DOSSIER ANIMÉ » (09/11) : un galet de verre avec une étoile qui tourne doucement, et le
// nom d'un groupe d'apps dessous (« AI », « Games »…, voir Groupes.kt). Un appui ouvre NOTRE dossier
// (DossierActivity) : l'écran s'assombrit, l'étoile s'envole et le dossier s'ouvre avec les apps.
// Le dessin et l'animation de l'étoile sont dans res/layout/widget_groupe.xml et
// res/drawable/etoile_ia_anime.xml (Android les joue tout seul, sans notre code).
// On le pose depuis AnimLauncher : écran principal -> « Dossiers animés » -> un groupe -> le bouton.
class WidgetGroupe : AppWidgetProvider() {

    // Android nous demande de préparer le widget (quand on le pose, ou au redémarrage du téléphone).
    override fun onUpdate(context: Context, gestionnaire: AppWidgetManager, ids: IntArray) {
        val carnet = context.getSharedPreferences(CARNET, Context.MODE_PRIVATE)
        for (id in ids) {
            // Un widget tout neuf : il prend le groupe qu'on vient de demander (voir proposer)
            if (!carnet.contains("groupe_$id")) {
                carnet.getString("en_attente", null)?.let { carnet.edit().putString("groupe_$id", it).apply() }
            }
            gestionnaire.updateAppWidget(id, vues(context, id))
        }
    }

    // Samsung nous confirme que le widget a été posé, avec son numéro : on retient son groupe.
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_POSE) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val groupe = intent.getStringExtra("groupe")
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID && groupe != null) {
                context.getSharedPreferences(CARNET, Context.MODE_PRIVATE).edit().putString("groupe_$id", groupe).apply()
                AppWidgetManager.getInstance(context).updateAppWidget(id, vues(context, id))
            }
            return
        }
        super.onReceive(context, intent)
    }

    // Le widget est retiré de l'accueil : on oublie son groupe.
    override fun onDeleted(context: Context, ids: IntArray) {
        val carnet = context.getSharedPreferences(CARNET, Context.MODE_PRIVATE).edit()
        for (id in ids) carnet.remove("groupe_$id")
        carnet.apply()
    }

    companion object {
        private const val CARNET = "widgets_groupe"
        private const val ACTION_POSE = "com.will.animlauncher.WIDGET_GROUPE_POSE"

        // Le widget : le nom du groupe, et « un appui = ouvrir le dossier de ce groupe ».
        fun vues(c: Context, id: Int): RemoteViews {
            val groupe = c.getSharedPreferences(CARNET, Context.MODE_PRIVATE).getString("groupe_$id", "ia") ?: "ia"
            val v = RemoteViews(c.packageName, R.layout.widget_groupe)
            v.setTextViewText(R.id.widget_groupe_nom, Groupes.categorie(groupe)?.nom ?: "?")
            // LA COULEUR CHOISIE (roue du dossier) : on « teinte » le galet et l'étoile animée. Le mode COLOR
            // garde les ombres et les reflets du dessin d'origine (sa luminosité) et ne remplace que la teinte
            // et l'intensité : l'étoile garde son relief, le galet reste sombre.
            // Android 12 et plus seulement (avant, un widget ne peut pas changer de teinte).
            val hsv = Groupes.couleur(c, groupe)
            if (hsv != null && Build.VERSION.SDK_INT >= 31) {
                val vive = Color.HSVToColor(hsv)
                val sombre = Color.HSVToColor(floatArrayOf(hsv[0], 0.35f + 0.3f * hsv[1], 0.5f))
                v.setColorStateList(R.id.widget_groupe_etoile, "setIndeterminateTintList", ColorStateList.valueOf(vive))
                v.setBlendMode(R.id.widget_groupe_etoile, "setIndeterminateTintBlendMode", BlendMode.COLOR)
                v.setColorStateList(R.id.widget_groupe_galet, "setImageTintList", ColorStateList.valueOf(sombre))
                v.setBlendMode(R.id.widget_groupe_galet, "setImageTintBlendMode", BlendMode.COLOR)
            }
            // NEW_TASK + CLEAR_TASK : le dossier s'ouvre toujours seul, dans sa propre tâche
            // (jamais par-dessus l'écran principal d'AnimLauncher).
            val ouvrir = Intent(c, DossierActivity::class.java).putExtra("groupe", groupe)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            // MUTABLE (et pas IMMUTABLE) : sinon Android ne peut pas y ajouter la PLACE du widget sur l'écran
            // (« sourceBounds »), et l'étoile du dossier ne sait pas d'où partir ni où revenir (vu le 09/11).
            v.setOnClickPendingIntent(R.id.widget_groupe_racine, PendingIntent.getActivity(c, id, ouvrir,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            return v
        }

        // Repeint tous les widgets posés pour ce groupe (après un changement de couleur dans le dossier).
        fun rafraichir(c: Context, groupe: String) {
            val gestionnaire = AppWidgetManager.getInstance(c)
            val carnet = c.getSharedPreferences(CARNET, Context.MODE_PRIVATE)
            for (id in gestionnaire.getAppWidgetIds(ComponentName(c, WidgetGroupe::class.java))) {
                if ((carnet.getString("groupe_$id", "ia") ?: "ia") == groupe) gestionnaire.updateAppWidget(id, vues(c, id))
            }
        }

        // Demande à l'écran d'accueil d'ajouter le widget du groupe (Samsung affiche sa fenêtre
        // « Ajouter à l'écran d'accueil »), et de nous prévenir avec le numéro du widget une fois posé.
        fun proposer(a: Activity, groupe: String): Boolean {
            val gestionnaire = AppWidgetManager.getInstance(a)
            if (!gestionnaire.isRequestPinAppWidgetSupported) return false
            a.getSharedPreferences(CARNET, Context.MODE_PRIVATE).edit().putString("en_attente", groupe).apply()
            val pose = Intent(a, WidgetGroupe::class.java).setAction(ACTION_POSE).putExtra("groupe", groupe)
            val rappel = PendingIntent.getBroadcast(a, groupe.hashCode(), pose,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return gestionnaire.requestPinAppWidget(ComponentName(a, WidgetGroupe::class.java), null, rappel)
        }
    }
}
