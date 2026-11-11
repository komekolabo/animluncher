package com.will.animlauncher

import android.content.Context
import android.os.SystemClock
import java.util.TimeZone

// LE TEMPS PASSÉ DANS CHAQUE APP (09/11), pour le widget « top 3 » des dossiers (voir WidgetTop.kt).
// Le service d'AnimLauncher voit déjà quelle app est à l'écran : à chaque changement, il nous prévient
// (auPremierPlan), et on ajoute le temps passé dans l'app qu'on vient de quitter.
// On range ce temps PAR JOUR, dans le carnet « usage » (clé « numéro du jour|nom de l'app » -> millisecondes).
// Le classement porte sur les 7 DERNIERS JOURS GLISSANTS (aujourd'hui + les 6 jours d'avant) : chaque jour, le
// plus ancien sort du calcul, il n'y a donc jamais de remise à zéro le lundi. Rien ne quitte le téléphone.
object Usage {
    private const val CARNET = "usage"
    private const val JOUR = 86_400_000L          // un jour, en millisecondes

    private var app: String? = null               // l'app à l'écran en ce moment (null = accueil, écran éteint…)
    private var debut = 0L                        // depuis quand

    // L'app à l'écran en ce moment (null = accueil, écran éteint…). Lu par la réaction sonore (AppsSon.kt)
    // pour savoir de quelle app vient la musique qui démarre.
    val devant: String?
        @Synchronized get() = app

    // Une nouvelle app passe au premier plan (ou null : on est sur l'accueil, ou l'écran s'éteint).
    @Synchronized
    fun auPremierPlan(c: Context, nouvelle: String?) {
        val maintenant = SystemClock.elapsedRealtime()
        val ancienne = app
        if (ancienne != null) {
            // au plus 4 h d'un coup : au-delà, c'est sûrement une erreur de mesure
            val duree = (maintenant - debut).coerceIn(0L, 4 * 3_600_000L)
            if (duree >= 1000L) ajouter(c, ancienne, duree)
        }
        app = nouvelle
        debut = maintenant
    }

    // Le numéro du jour d'aujourd'hui (à l'heure du téléphone, pas à l'heure de Londres).
    private fun jour(): Long {
        val t = System.currentTimeMillis()
        return (t + TimeZone.getDefault().getOffset(t)) / JOUR
    }

    private fun ajouter(c: Context, pkg: String, ms: Long) {
        val carnet = c.getSharedPreferences(CARNET, Context.MODE_PRIVATE)
        val j = jour()
        val cle = "$j|$pkg"
        val edition = carnet.edit().putLong(cle, carnet.getLong(cle, 0L) + ms)
        // une fois par jour, le ménage : on efface ce qui a plus de 7 jours
        if (carnet.getLong("menage", 0L) != j) {
            for (ancienne in carnet.all.keys) {
                val jourCle = ancienne.substringBefore('|', "").toLongOrNull() ?: continue
                if (jourCle < j - 7) edition.remove(ancienne)
            }
            edition.putLong("menage", j)
        }
        edition.apply()
    }

    // Les minutes passées dans une app sur les 7 derniers jours.
    fun minutes(c: Context, pkg: String): Int {
        val carnet = c.getSharedPreferences(CARNET, Context.MODE_PRIVATE)
        val j = jour()
        var total = 0L
        for (k in 0..6) total += carnet.getLong("${j - k}|$pkg", 0L)
        return (total / 60_000L).toInt()
    }
}
