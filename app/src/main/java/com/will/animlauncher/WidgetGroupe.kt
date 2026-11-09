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
import android.os.Bundle
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
            // LE DOSSIER GAMES a son propre widget : l'intro du téléphone puis le top 3 des jeux (voir WidgetTop.kt).
            // On pose tout de suite sa base (nom + appui), et ses images arrivent une seconde après.
            if (groupe == WidgetTop.GROUPE) {
                WidgetTop.demander(c, forcer = true)
                return WidgetTop.base(c, id, groupe)
            }
            val v = RemoteViews(c.packageName, R.layout.widget_groupe)
            v.setTextViewText(R.id.widget_groupe_nom, Groupes.categorie(groupe)?.nom ?: "?")
            // LA COULEUR CHOISIE (roue du dossier) : on « teinte » le galet et l'étoile animée.
            // Mode SRC_ATOP : la couleur ne se pose QUE là où il y a déjà du dessin (la forme arrondie du galet,
            // la forme de l'étoile). Le 09/11, le mode COLOR remplissait aussi les coins transparents : le
            // galet devenait un CARRÉ rouge (vu sur l'accueil de Will). La couleur est un peu transparente
            // (alpha 175 et 190 sur 255) : on voit encore un peu le dessin d'origine dessous (reflets, dégradé).
            // Android 12 et plus seulement (avant, un widget ne peut pas changer de teinte).
            val hsv = Groupes.couleur(c, groupe)
            if (hsv != null && Build.VERSION.SDK_INT >= 31) {
                val vive = Teinte.avecAlpha(Color.HSVToColor(hsv), 190f)
                val sombre = Teinte.avecAlpha(Teinte.depuis(hsv).haut, 175f)
                v.setColorStateList(R.id.widget_groupe_etoile, "setIndeterminateTintList", ColorStateList.valueOf(vive))
                v.setBlendMode(R.id.widget_groupe_etoile, "setIndeterminateTintBlendMode", BlendMode.SRC_ATOP)
                v.setColorStateList(R.id.widget_groupe_galet, "setImageTintList", ColorStateList.valueOf(sombre))
                v.setBlendMode(R.id.widget_groupe_galet, "setImageTintBlendMode", BlendMode.SRC_ATOP)
            }
            v.setOnClickPendingIntent(R.id.widget_groupe_racine, clic(c, id, groupe))
            return v
        }

        // « Un appui sur le widget = ouvrir le dossier de ce groupe ».
        fun clic(c: Context, id: Int, groupe: String): PendingIntent {
            // NEW_TASK + CLEAR_TASK : le dossier s'ouvre toujours seul, dans sa propre tâche
            // (jamais par-dessus l'écran principal d'AnimLauncher).
            val ouvrir = Intent(c, DossierActivity::class.java).putExtra("groupe", groupe)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            // MUTABLE (et pas IMMUTABLE) : sinon Android ne peut pas y ajouter la PLACE du widget sur l'écran
            // (« sourceBounds »), et l'étoile du dossier ne sait pas d'où partir ni où revenir (vu le 09/11).
            return PendingIntent.getActivity(c, id, ouvrir, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        // Les numéros des widgets posés pour un groupe.
        fun idsDuGroupe(c: Context, groupe: String): IntArray {
            val carnet = c.getSharedPreferences(CARNET, Context.MODE_PRIVATE)
            return AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, WidgetGroupe::class.java))
                .filter { (carnet.getString("groupe_$it", "ia") ?: "ia") == groupe }.toIntArray()
        }

        // Repeint tous les widgets posés pour ce groupe (après un changement de couleur dans le dossier).
        fun rafraichir(c: Context, groupe: String) {
            if (groupe == WidgetTop.GROUPE) return      // le widget « top 3 » des jeux ne suit pas (encore) la roue des couleurs
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
            // L'APERÇU de la fenêtre « Ajouter à l'écran d'accueil » : sans rien préciser, Android montre l'aperçu
            // écrit dans res/xml/widget_groupe_info.xml, c'est-à-dire l'étoile de l'IA, même pour le dossier Games
            // (vu par Will le 09/11). Pour Games, on lui donne donc notre propre aperçu : une image du jeu.
            var options: Bundle? = null
            if (groupe == WidgetTop.GROUPE) {
                try {
                    options = Bundle().apply { putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, WidgetTop.apercu(a)) }
                } catch (e: Exception) {
                    Journal.noter(a, "widget top : aperçu impossible (${e.message})")
                }
            }
            return gestionnaire.requestPinAppWidget(ComponentName(a, WidgetGroupe::class.java), options, rappel)
        }
    }
}
