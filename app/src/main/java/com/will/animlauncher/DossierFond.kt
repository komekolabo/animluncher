package com.will.animlauncher

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import java.io.File

// LE DOSSIER DE FONDS (17/11, demande de Will) : une collection de photos et de vidéos qui servent de fond
// d'écran CHACUNE À SON TOUR. Le fond change à chaque fois qu'on revient sur l'écran d'accueil : en quittant
// une app, ou en rallumant le téléphone. (Le fond lui-même est dessiné par FondDossierService.kt.)
//
// Ce fichier s'occupe de la collection :
//  - les FICHIERS : copiés dans le dossier privé de l'app « fonds_dossier/ » (comme les fonds importés : les
//    vidéos sans leur son, les photos réduites à la taille de l'écran ; voir ImportFond.kt) ;
//  - L'ORDRE : « au hasard » (par défaut) ou « dans l'ordre d'ajout ».
//    Au hasard, on fait comme avec un paquet de cartes : on mélange tout, on tire les cartes une par une, et
//    on ne remélange que quand le paquet est vide. Comme ça, on voit TOUS les fonds avant de revoir le même,
//    et le dernier montré n'est jamais tiré deux fois de suite.
//  - LE FOND EN COURS : retenu dans le carnet « dossier_fond » (case « courant »). Quand il change, le service
//    du fond d'écran le voit tout de suite (il écoute le carnet) et affiche le nouveau.
object DossierFond {
    const val MAX = 100                          // au plus 100 photos et vidéos dans le dossier
    const val COURANT = "courant"                // la case du carnet qui dit quel fond est affiché

    fun carnet(c: Context): SharedPreferences = c.getSharedPreferences("dossier_fond", Context.MODE_PRIVATE)

    fun dossier(c: Context): File = File(c.filesDir, "fonds_dossier").apply { mkdirs() }

    // Les fichiers du dossier, dans l'ordre d'ajout (leur nom commence par l'heure de l'ajout).
    fun fichiers(c: Context): List<File> =
        (dossier(c).listFiles() ?: emptyArray()).filter { it.isFile && it.length() > 0 }.sortedBy { it.name }

    fun estVideo(f: File) = CarnetFond.sorteDe(f) == CarnetFond.VIDEO

    // L'ordre : au hasard (true) ou dans l'ordre d'ajout (false). Changer d'ordre jette le paquet en cours.
    fun auHasard(c: Context) = carnet(c).getBoolean("hasard", true)
    fun mettreAuHasard(c: Context, oui: Boolean) {
        carnet(c).edit().putBoolean("hasard", oui).remove("pioche").apply()
    }

    // Le fond à afficher : celui retenu, ou le premier du dossier (null si le dossier est vide).
    fun courant(c: Context): File? {
        val nom = carnet(c).getString(COURANT, null)
        val liste = fichiers(c)
        return liste.firstOrNull { it.name == nom } ?: liste.firstOrNull()
    }

    // PASSER AU FOND SUIVANT. Appelé par le service quand le fond a été caché un instant (on est dans une app,
    // ou l'écran est éteint) : le suivant est donc déjà prêt quand on revient sur l'accueil.
    // Sécurité : jamais deux changements en moins de 1,5 s (si l'accueil ET l'écran de verrouillage
    // affichent chacun le fond, les deux pourraient demander un changement en même temps).
    private var dernierChangement = 0L
    @Synchronized
    fun avancer(c: Context) {
        val maintenant = SystemClock.uptimeMillis()
        if (dernierChangement != 0L && maintenant - dernierChangement < 1500L) return
        dernierChangement = maintenant
        val liste = fichiers(c).map { it.name }
        if (liste.size < 2) return                          // 0 ou 1 fond : rien à changer
        val p = carnet(c)
        val actuel = p.getString(COURANT, null)
        val e = p.edit()
        val suivant: String
        if (auHasard(c)) {
            // le paquet : les noms restants, séparés par « / » (on retire ceux qui ont été supprimés entre-temps)
            var paquet = (p.getString("pioche", "") ?: "").split("/").filter { it in liste }.toMutableList()
            if (paquet.isEmpty()) {
                paquet = liste.shuffled().toMutableList()
                // le fond qu'on vient de voir ne doit pas revenir tout de suite : on le met au fond du paquet
                if (paquet.first() == actuel) paquet.add(paquet.removeAt(0))
            }
            suivant = paquet.removeAt(0)
            e.putString("pioche", paquet.joinToString("/"))
        } else {
            val i = liste.indexOf(actuel)                   // -1 si inconnu : on repart du premier
            suivant = liste[(i + 1) % liste.size]
        }
        e.putString(COURANT, suivant).apply()
    }

    // AJOUTER des photos et des vidéos choisies dans la galerie. Renvoie combien ont été ajoutées.
    // (Peut prendre quelques secondes par vidéo : à appeler en arrière-plan.)
    fun ajouter(c: Context, uris: List<Uri>): Int {
        var ajoutes = 0
        val place = MAX - fichiers(c).size
        for ((i, uri) in uris.take(place.coerceAtLeast(0)).withIndex()) {
            // un nom différent pour chaque fichier : l'heure, puis le numéro dans la sélection (sur 3 chiffres,
            // pour que l'ordre alphabétique reste l'ordre d'ajout)
            val nom = "d_" + System.currentTimeMillis() + "_" + i.toString().padStart(3, '0')
            val f = try { ImportFond.importer(c, uri, dossier(c), nom) } catch (_: Exception) { null }
            if (f != null) ajoutes++
        }
        // le dossier était vide : le premier fond ajouté devient le fond en cours
        if (carnet(c).getString(COURANT, null) == null) fichiers(c).firstOrNull()?.let {
            carnet(c).edit().putString(COURANT, it.name).apply()
        }
        signalerChangement(c)
        return ajoutes
    }

    // RETIRER un fond du dossier.
    fun retirer(c: Context, f: File) {
        ImportFond.supprimer(f)                          // (et sa version avec le son, s'il y en a une)
        if (carnet(c).getString(COURANT, null) == f.name) {
            carnet(c).edit().putString(COURANT, fichiers(c).firstOrNull()?.name).apply()
        } else signalerChangement(c)
    }

    // Prévient le service que la collection a changé (il relit « courant » ; s'il était vide, il affiche le 1er fond).
    private fun signalerChangement(c: Context) {
        carnet(c).edit().putLong("change", System.currentTimeMillis()).apply()
    }
}
