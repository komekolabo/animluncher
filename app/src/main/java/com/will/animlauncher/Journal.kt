package com.will.animlauncher

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// LE JOURNAL : ce que fait le service (clics, fenêtres, animations), plus les plantages.
// Il est gardé dans le carnet du téléphone (150 lignes au plus : les plus anciennes disparaissent)
// et, si une clé GitHub a été donnée dans l'écran Journal, il est ENVOYÉ AUTOMATIQUEMENT
// sur GitHub, comme commentaire d'un ticket (« issue ») du projet. À chaque envoi, le nouveau
// commentaire remplace l'ancien : sur GitHub, il n'y a toujours que le dernier journal.
object Journal {
    const val CARNET = "detections"
    const val CLE_LISTE = "liste"
    private const val MAX_LIGNES = 150

    // Où envoyer, et ce qu'on retient d'un envoi à l'autre.
    private const val DEPOT = "komekolabo/animluncher"
    private const val CLE_JETON = "github_jeton"
    private const val CLE_TICKET = "github_ticket"          // numéro du ticket « Journal »
    private const val CLE_COMMENTAIRE = "github_commentaire" // le dernier commentaire envoyé
    private const val CLE_ETAT = "github_etat"              // « envoyé à 18:40 », ou l'erreur

    private val principal = Handler(Looper.getMainLooper())
    private var envoiPrevu = false

    private fun carnet(c: Context) = c.applicationContext.getSharedPreferences(CARNET, Context.MODE_PRIVATE)

    // Ajoute une ligne (heure à la milliseconde près) en haut du journal.
    fun noter(c: Context, texte: String, toutDeSuite: Boolean = false) {
        val heure = SimpleDateFormat("HH:mm:ss.SSS", Locale.FRANCE).format(Date())
        val ancienne = carnet(c).getString(CLE_LISTE, "") ?: ""
        val nouvelle = (listOf("$heure $texte") + ancienne.lines().filter { it.isNotBlank() })
            .take(MAX_LIGNES)
            .joinToString("\n")
        val ed = carnet(c).edit().putString(CLE_LISTE, nouvelle)
        // commit() écrit tout de suite sur le disque (utile juste avant un plantage) ; apply() plus tard.
        if (toutDeSuite) ed.commit() else ed.apply()
    }

    // Le texte complet : l'en-tête (version, téléphone) puis le journal.
    fun texte(c: Context): String {
        val version = try { c.packageManager.getPackageInfo(c.packageName, 0).versionName } catch (_: Exception) { "?" }
        val lignes = carnet(c).getString(CLE_LISTE, "") ?: ""
        return "AnimLauncher version $version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n\n" +
            lignes.ifBlank { "(journal vide)" }
    }

    fun effacer(c: Context) = carnet(c).edit().remove(CLE_LISTE).apply()

    // ---------------- LES PLANTAGES ----------------
    // Si une erreur n'est rattrapée nulle part, Android ferme l'app (« fermeture forcée »).
    // Ce filet écrit d'abord l'erreur complète dans le journal, pour qu'on sache ce qui s'est passé.
    // Si l'erreur vient d'un fil secondaire (ceux qui préparent les images des animations),
    // on s'arrête là : ce fil meurt, mais l'app et le service continuent de tourner.
    fun installerFilet(c: Context) {
        val app = c.applicationContext
        val avant = Thread.getDefaultUncaughtExceptionHandler()
        if (avant is Filet) return
        Thread.setDefaultUncaughtExceptionHandler(Filet(app, avant))
    }

    private class Filet(val app: Context, val avant: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(fil: Thread, e: Throwable) {
            val principalPlante = fil === Looper.getMainLooper().thread
            try {
                noter(app, (if (principalPlante) "PLANTAGE (l'app va se fermer) : " else "ERREUR dans le fil « ${fil.name} » (rattrapée) : ") +
                    pile(e), toutDeSuite = true)
            } catch (_: Throwable) {}
            if (principalPlante) {
                avant?.uncaughtException(fil, e)
            } else {
                programmerEnvoi(app)
            }
        }
    }

    // Les 12 premières lignes de la « pile » de l'erreur : le type d'erreur et où elle s'est produite.
    fun pile(e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        return sw.toString().lines().filter { it.isNotBlank() }.take(12).joinToString("\n    ")
    }

    // ---------------- L'ENVOI SUR GITHUB ----------------
    fun jeton(c: Context): String = carnet(c).getString(CLE_JETON, "") ?: ""
    fun definirJeton(c: Context, jeton: String) = carnet(c).edit().putString(CLE_JETON, jeton.trim()).apply()
    fun etat(c: Context): String = carnet(c).getString(CLE_ETAT, "") ?: ""
    fun lienTicket(c: Context): String? {
        val n = carnet(c).getInt(CLE_TICKET, 0)
        return if (n > 0) "https://github.com/$DEPOT/issues/$n" else null
    }

    // On n'envoie pas à chaque ligne : on attend 15 s de calme, puis on envoie tout d'un coup.
    fun programmerEnvoi(c: Context) {
        val app = c.applicationContext
        principal.post {
            if (envoiPrevu || jeton(app).isEmpty()) return@post
            envoiPrevu = true
            principal.postDelayed({
                envoiPrevu = false
                envoyer(app) {}
            }, 15_000)
        }
    }

    // Envoie maintenant (dans un fil à part : Internet ne doit jamais bloquer l'écran).
    // « fini » est appelé sur le fil principal avec un petit message pour l'utilisateur.
    fun envoyer(c: Context, fini: (String) -> Unit) {
        val app = c.applicationContext
        val jeton = jeton(app)
        if (jeton.isEmpty()) { fini("Pas de clé GitHub"); return }
        val corps = "```\n" + texte(app).take(60_000) + "\n```"
        Thread {
            val message = try {
                envoyerSurGithub(app, jeton, corps)
            } catch (e: Exception) {
                "échec : ${e.javaClass.simpleName} ${e.message ?: ""}"
            }
            val heure = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date())
            carnet(app).edit().putString(CLE_ETAT, "$heure — $message").apply()
            principal.post { fini(message) }
        }.start()
    }

    private fun envoyerSurGithub(c: Context, jeton: String, corps: String): String {
        val api = "https://api.github.com/repos/$DEPOT"
        // 1) le ticket « Journal » : on le crée la première fois
        var ticket = carnet(c).getInt(CLE_TICKET, 0)
        if (ticket == 0) {
            val (code, reponse) = requete("POST", "$api/issues", jeton, JSONObject()
                .put("title", "Journal AnimLauncher (envoyé automatiquement)")
                .put("body", "L'app met ici son dernier journal, en commentaire. Chaque envoi remplace le précédent.")
                .toString())
            if (code !in 200..299) return "échec création du ticket ($code) ${erreur(reponse)}"
            ticket = JSONObject(reponse).getInt("number")
            carnet(c).edit().putInt(CLE_TICKET, ticket).putLong(CLE_COMMENTAIRE, 0L).apply()
        }
        // 2) le nouveau journal, en commentaire
        val (code, reponse) = requete("POST", "$api/issues/$ticket/comments", jeton,
            JSONObject().put("body", corps).toString())
        if (code == 404 || code == 410) {
            // le ticket a été supprimé : on en recréera un au prochain envoi
            carnet(c).edit().putInt(CLE_TICKET, 0).apply()
            return "ticket introuvable, il sera recréé"
        }
        if (code !in 200..299) return "échec ($code) ${erreur(reponse)}"
        val nouveau = JSONObject(reponse).getLong("id")
        // 3) on efface l'ancien commentaire : il ne reste que le journal le plus récent
        val ancien = carnet(c).getLong(CLE_COMMENTAIRE, 0L)
        carnet(c).edit().putLong(CLE_COMMENTAIRE, nouveau).apply()
        if (ancien != 0L) requete("DELETE", "$api/issues/comments/$ancien", jeton, null)
        return "envoyé (ticket n° $ticket)"
    }

    private fun erreur(reponse: String): String =
        try { JSONObject(reponse).optString("message") } catch (_: Exception) { reponse.take(80) }

    // Une requête à l'API GitHub. Renvoie le code (200, 201, 404…) et la réponse.
    private fun requete(methode: String, url: String, jeton: String, json: String?): Pair<Int, String> {
        val cx = URL(url).openConnection() as HttpURLConnection
        try {
            cx.requestMethod = methode
            cx.connectTimeout = 10_000
            cx.readTimeout = 15_000
            cx.setRequestProperty("Authorization", "Bearer $jeton")
            cx.setRequestProperty("Accept", "application/vnd.github+json")
            cx.setRequestProperty("User-Agent", "AnimLauncher")
            if (json != null) {
                cx.doOutput = true
                cx.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                cx.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            val code = cx.responseCode
            val flux = if (code in 200..299) cx.inputStream else cx.errorStream
            val texte = flux?.bufferedReader()?.use { it.readText() } ?: ""
            return code to texte
        } finally {
            cx.disconnect()
        }
    }
}
