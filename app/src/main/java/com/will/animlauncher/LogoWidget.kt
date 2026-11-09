package com.will.animlauncher

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

// LE WIDGET « LOGO ANIMÉ » (09/11) : il remplace l'ancien essai d'« icône vivante » (une petite
// fenêtre posée par-dessus l'icône, qui devait deviner la page affichée, l'écran des apps… et se
// trompait souvent). Un widget fait partie de l'écran d'accueil : Samsung s'occupe de tout.
// Le dessin et l'animation sont dans res/layout/widget_logo.xml et res/drawable/logo_widget_anime.xml.
// Ici, on fait seulement deux choses : brancher l'appui (ouvrir AnimLauncher), et proposer de poser
// le widget sur l'accueil depuis l'écran principal.
class LogoWidget : AppWidgetProvider() {

    // Android nous demande de préparer le widget (quand on le pose, ou au redémarrage du téléphone).
    override fun onUpdate(context: Context, gestionnaire: AppWidgetManager, ids: IntArray) {
        for (id in ids) gestionnaire.updateAppWidget(id, vues(context))
    }

    companion object {
        // Le widget lui-même : le dessin, plus « un appui n'importe où = ouvrir AnimLauncher ».
        // (Un widget ne peut pas lancer de code directement : on lui confie une « intention en
        // attente », que l'écran d'accueil déclenchera au moment de l'appui.)
        fun vues(c: Context): RemoteViews {
            val v = RemoteViews(c.packageName, R.layout.widget_logo)
            val ouvrir = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val appui = PendingIntent.getActivity(c, 0, ouvrir,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.widget_racine, appui)
            return v
        }

        // Demande à l'écran d'accueil d'ajouter le widget : Samsung affiche sa propre fenêtre
        // « Ajouter à l'écran d'accueil ». Renvoie false si l'écran d'accueil ne sait pas le faire
        // (il faudra alors le poser à la main : appui long sur l'accueil -> Widgets -> AnimLauncher).
        fun proposer(a: Activity): Boolean {
            val gestionnaire = AppWidgetManager.getInstance(a)
            if (!gestionnaire.isRequestPinAppWidgetSupported) return false
            return gestionnaire.requestPinAppWidget(ComponentName(a, LogoWidget::class.java), null, null)
        }
    }
}
