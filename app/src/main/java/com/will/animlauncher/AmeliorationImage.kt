package com.will.animlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tflite.java.TfLite
import org.tensorflow.lite.InterpreterApi
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

// AMÉLIORER UNE IMAGE (18/11) : rendre nette une image petite, floue ou pixelisée importée comme fond d'écran.
// Tout se passe SUR LE TÉLÉPHONE : l'image n'est envoyée nulle part.
//
// L'outil est un « modèle » d'intelligence artificielle : « Real-ESRGAN » (version compacte « general-x4v3 », de
// Xintao Wang, licence BSD 3 clauses), converti par Claude au format des téléphones Android. Il agrandit une image
// 4 fois en redessinant des détails plausibles (il ne retrouve pas les vrais détails perdus : il en invente de crédibles).
// Essayé aussi sur des dessins / anime : il s'en sort très bien, donc UN SEUL outil pour tout (pas de choix à faire).
//
// Comme l'outil de détourage des dessins (DetourageDessins.kt) :
//  - il n'est PAS dans l'app (elle reste légère) : 2,5 Mo téléchargés une fois, à la première amélioration,
//    depuis la branche « modeles » du dépôt GitHub d'AnimEngine ;
//  - il tourne avec le moteur d'IA des services Google du téléphone (« LiteRT » / TFLite), pas dans l'app non plus.
//
// LES FICHIERS : l'image d'origine « fond_123.jpg » n'est JAMAIS modifiée. La version améliorée est enregistrée à côté,
// sous le nom « fond_123_net.jpg ». Dans l'écran Fonds d'écran, on ne montre que la version améliorée (voir
// CarnetFond.importes) ; dans l'aperçu, deux pilules « Originale / Améliorée » permettent de comparer et de choisir.
// Supprimer la version améliorée (appui long) fait réapparaître l'originale.
object AmeliorationImage {

    private const val NOM = "amelioration_photos_v1.tflite"
    private const val ADRESSE = "https://raw.githubusercontent.com/komekolabo/animluncher/modeles/$NOM"
    private const val OCTETS = 2_480_168L         // la taille exacte du fichier : sert à vérifier le téléchargement
    const val TAILLE_MO = 3                       // (2,5 Mo, arrondi au-dessus pour l'affichage)

    // Le modèle travaille par CARREAUX : il reçoit un carré de 192 x 192 points et rend un carré 4 fois plus grand.
    // Chaque carreau est pris avec une MARGE de 16 points autour de la partie utile (le « cœur », 160 x 160) : le
    // modèle a besoin de voir un peu autour pour bien dessiner les bords, sinon on verrait les jointures.
    private const val COTE = 192
    private const val MARGE = 16
    private const val COEUR = COTE - 2 * MARGE
    private const val ECHELLE = 4

    private const val SUFFIXE = "_net"

    // ------------------------------ LES DEUX VERSIONS D'UNE IMAGE ------------------------------

    // Ce fichier est-il une version améliorée ?
    fun estNette(f: File): Boolean = f.nameWithoutExtension.endsWith(SUFFIXE)

    // La version améliorée de cette image (qu'elle existe déjà ou pas).
    fun nette(f: File): File = if (estNette(f)) f else File(f.parentFile, f.nameWithoutExtension + SUFFIXE + ".jpg")

    // L'image d'origine (pour « fond_123_net.jpg » : « fond_123.jpg »).
    fun originale(f: File): File =
        if (estNette(f)) File(f.parentFile, f.nameWithoutExtension.removeSuffix(SUFFIXE) + ".jpg") else f

    // Cette image d'origine a-t-elle une version améliorée ? (alors l'écran Fonds d'écran ne montre que l'améliorée)
    fun estRemplacee(f: File): Boolean = !estNette(f) && f.extension.lowercase() == "jpg" && nette(f).exists()

    // ------------------------------ L'OUTIL (téléchargé à la demande) ------------------------------

    private fun fichier(c: Context): File = File(File(c.filesDir, "modeles").apply { mkdirs() }, NOM)

    fun estInstalle(c: Context): Boolean = fichier(c).let { it.exists() && it.length() == OCTETS }

    // Télécharge l'outil (dans un Thread !). « progres » reçoit le pourcentage (0 à 100).
    // On écrit d'abord dans un fichier « .part » : si le téléchargement est coupé, on n'a pas d'outil à moitié.
    fun telecharger(c: Context, progres: (Int) -> Unit): Boolean {
        val final = fichier(c)
        val partiel = File(final.path + ".part")
        try {
            val cx = URL(ADRESSE).openConnection() as HttpURLConnection
            cx.connectTimeout = 15_000
            cx.readTimeout = 30_000
            if (cx.responseCode != 200) return false
            cx.inputStream.use { entree ->
                partiel.outputStream().use { sortie ->
                    val tampon = ByteArray(64 * 1024)
                    var lu = 0L
                    while (true) {
                        val n = entree.read(tampon)
                        if (n < 0) break
                        sortie.write(tampon, 0, n)
                        lu += n
                        progres((lu * 100 / OCTETS).toInt().coerceIn(0, 100))
                    }
                }
            }
            if (partiel.length() != OCTETS) { partiel.delete(); return false }
            return partiel.renameTo(final)
        } catch (_: Exception) {
            partiel.delete()
            return false
        }
    }

    // ------------------------------ LE CALCUL ------------------------------

    // L'utilisateur a appuyé sur « Annuler » pendant le calcul.
    class Annule : Exception()

    // Améliore l'image « source » et enregistre le résultat à côté (voir nette()). Renvoie le fichier créé.
    // À lancer dans un Thread : de quelques secondes à plus d'une minute selon l'image et le téléphone.
    //  - « progres » reçoit le pourcentage (0 à 100) ;
    //  - « annule » est interrogé avant chaque carreau : s'il répond vrai, on arrête (exception Annule).
    fun ameliorer(c: Context, source: File, progres: (Int) -> Unit, annule: () -> Boolean): File {
        val origine = originale(source)
        var image = BitmapFactory.decodeFile(origine.path) ?: throw IllegalStateException("image illisible")

        // 1) LA TAILLE DE TRAVAIL. Le résultat sera 4 fois plus grand que ce qu'on donne au modèle, et il ne sert à
        //    rien de dépasser l'écran. On donne donc au modèle au plus 40 % du grand côté de l'écran : le résultat
        //    (160 %) est ensuite ramené à la taille de l'écran, ce qui lisse encore les petits défauts.
        //    Une petite image (le cas visé) passe telle quelle.
        val m = c.resources.displayMetrics
        val ecran = max(m.widthPixels, m.heightPixels)
        val maxEntree = ecran * 2 / 5
        val grand = max(image.width, image.height)
        if (grand > maxEntree) {
            val k = maxEntree.toFloat() / grand
            image = Bitmap.createScaledBitmap(image, max(1, (image.width * k).toInt()), max(1, (image.height * k).toInt()), true)
        }
        val w = image.width
        val h = image.height
        val points = IntArray(w * h)
        image.getPixels(points, 0, w, 0, 0, w, h)

        // 2) le moteur d'IA des services Google, avec notre outil (sur 4 cœurs du processeur au plus)
        Tasks.await(TfLite.initialize(c))
        val options = InterpreterApi.Options()
            .setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_SYSTEM_ONLY)
            .setNumThreads(min(4, Runtime.getRuntime().availableProcessors()))
        val moteur = InterpreterApi.create(fichier(c), options)
        val resultat = Bitmap.createBitmap(w * ECHELLE, h * ECHELLE, Bitmap.Config.ARGB_8888)
        try {
            val grandCote = COTE * ECHELLE                      // 768 : le côté d'un carreau en sortie
            // l'entrée du modèle : pour chaque point, rouge, vert, bleu entre 0 et 1 ; la sortie : pareil, en 4 fois plus grand
            val entree = ByteBuffer.allocateDirect(COTE * COTE * 3 * 4).order(ByteOrder.nativeOrder())
            val sortie = ByteBuffer.allocateDirect(grandCote * grandCote * 3 * 4).order(ByteOrder.nativeOrder())
            val nombres = FloatArray(grandCote * grandCote * 3)
            val morceau = IntArray(COEUR * ECHELLE * COEUR * ECHELLE)
            val total = ((w + COEUR - 1) / COEUR) * ((h + COEUR - 1) / COEUR)
            var faits = 0
            progres(0)

            // 3) carreau par carreau, de gauche à droite et de haut en bas
            for (y0 in 0 until h step COEUR) for (x0 in 0 until w step COEUR) {
                if (annule()) throw Annule()
                // a) on remplit le carreau d'entrée. Au bord de l'image, la marge « sort » de l'image :
                //    coerceIn répète alors le point du bord (mieux que du noir, qui baverait sur le résultat).
                entree.rewind()
                for (ty in 0 until COTE) {
                    val sy = (y0 - MARGE + ty).coerceIn(0, h - 1) * w
                    for (tx in 0 until COTE) {
                        val p = points[sy + (x0 - MARGE + tx).coerceIn(0, w - 1)]
                        entree.putFloat(Color.red(p) / 255f)
                        entree.putFloat(Color.green(p) / 255f)
                        entree.putFloat(Color.blue(p) / 255f)
                    }
                }
                entree.rewind()
                sortie.rewind()
                // b) le calcul
                moteur.run(entree, sortie)
                sortie.rewind()
                sortie.asFloatBuffer().get(nombres)
                // c) on ne garde que le cœur du carreau (sans la marge), posé à sa place dans la grande image
                val lw = min(COEUR, w - x0) * ECHELLE
                val lh = min(COEUR, h - y0) * ECHELLE
                var i = 0
                for (yy in 0 until lh) {
                    var k = ((MARGE * ECHELLE + yy) * grandCote + MARGE * ECHELLE) * 3
                    for (xx in 0 until lw) {
                        morceau[i++] = Color.rgb(
                            (nombres[k] * 255f + 0.5f).toInt().coerceIn(0, 255),
                            (nombres[k + 1] * 255f + 0.5f).toInt().coerceIn(0, 255),
                            (nombres[k + 2] * 255f + 0.5f).toInt().coerceIn(0, 255))
                        k += 3
                    }
                }
                resultat.setPixels(morceau, 0, lw, x0 * ECHELLE, y0 * ECHELLE, lw, lh)
                faits++
                progres(faits * 100 / total)
            }
        } finally {
            moteur.close()
        }

        // 4) le résultat est ramené à la taille de l'écran s'il la dépasse, puis enregistré.
        //    On écrit d'abord dans un fichier provisoire : pas de fichier à moitié écrit si l'app est coupée.
        var finale = resultat
        val grandFinal = max(finale.width, finale.height)
        if (grandFinal > ecran) {
            val k = ecran.toFloat() / grandFinal
            finale = Bitmap.createScaledBitmap(finale, max(1, (finale.width * k).toInt()), max(1, (finale.height * k).toInt()), true)
        }
        val cible = nette(origine)
        val provisoire = File(c.cacheDir, cible.name + ".part")
        provisoire.outputStream().use { finale.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        if (!provisoire.renameTo(cible)) { provisoire.copyTo(cible, overwrite = true); provisoire.delete() }
        return cible
    }
}

// ---------------------------------------------------------------------------------------------
// LA LIGNE « NETTETÉ » de l'aperçu d'un fond (FondApercuActivity) : deux pilules « Originale / Améliorée ».
//  - « Améliorée », la première fois : la fenêtre qui prévient du téléchargement de l'outil (une seule fois),
//    puis le calcul avec sa barre de progression (on peut annuler) ;
//  - ensuite, les deux pilules passent d'une version à l'autre pour comparer sur le vrai aperçu.
// « surChangement » reçoit le fichier à afficher : l'aperçu l'affiche, et c'est lui que « Appliquer » mettra en fond.
// ---------------------------------------------------------------------------------------------
object AmeliorationUi {

    fun vue(a: Activity, actuel: File, surChangement: (File) -> Unit): View {
        val origine = AmeliorationImage.originale(actuel)
        val nette = AmeliorationImage.nette(actuel)
        var montreNette = AmeliorationImage.estNette(actuel)
        val px = { v: Float -> Style.px(a, v) }

        fun pilule(t: String) = Style.texte(a, t, 13f, Style.TEXTE, gras = true).apply {
            gravity = Gravity.CENTER
            isClickable = true
        }
        val pOrigine = pilule(a.getString(R.string.net_originale))
        val pNette = pilule(a.getString(R.string.net_amelioree))
        // la pilule allumée est blanche, l'autre est sombre avec un contour (comme « Éco / Fluide »)
        fun peindre() {
            for ((v, on) in listOf(pOrigine to !montreNette, pNette to montreNette)) {
                v.background = Style.arrondi(a, if (on) Style.TEXTE else Color.argb(20, 255, 255, 255), 14f,
                    if (on) null else Color.rgb(63, 63, 70))
                v.setTextColor(if (on) Style.ENCRE else Style.TEXTE)
            }
        }
        fun montrer(versionNette: Boolean) {
            montreNette = versionNette
            peindre()
            surChangement(if (versionNette) nette else origine)
        }
        pOrigine.setOnClickListener { if (montreNette && origine.exists()) montrer(false) }
        pNette.setOnClickListener {
            when {
                montreNette -> {}
                nette.exists() -> montrer(true)                                   // déjà calculée : on la montre
                AmeliorationImage.estInstalle(a) -> calculer(a, origine) { montrer(true) }
                else -> proposerOutil(a) { calculer(a, origine) { montrer(true) } }
            }
        }
        peindre()

        return LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(Style.texte(a, a.getString(R.string.net_titre), 13f, Style.MUET).apply { setPadding(0, px(14f), 0, px(8f)) })
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(pOrigine, LinearLayout.LayoutParams(0, px(40f), 1f).apply { rightMargin = px(8f) })
                addView(pNette, LinearLayout.LayoutParams(0, px(40f), 1f))
            })
            addView(Style.texte(a, a.getString(R.string.net_info), 12f, Style.MUET).apply {
                setLineSpacing(0f, 1.2f)
                setPadding(0, px(8f), 0, 0)
            })
        }
    }

    // Avant le 1er téléchargement : on explique (taille, une seule fois, tout reste sur le téléphone).
    private fun proposerOutil(a: Activity, apres: () -> Unit) {
        AlertDialog.Builder(a)
            .setTitle(a.getString(R.string.net_outil_titre))
            .setMessage(a.getString(R.string.net_outil_texte, AmeliorationImage.TAILLE_MO))
            .setPositiveButton(a.getString(R.string.fd_3d_telecharger, AmeliorationImage.TAILLE_MO)) { _, _ -> telecharger(a, apres) }
            .setNegativeButton(a.getString(R.string.fd_annuler), null)
            .show()
    }

    private fun barre(a: Activity): Pair<ProgressBar, FrameLayout> {
        val barre = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val boite = FrameLayout(a).apply {
            setPadding(Style.px(a, 24f), Style.px(a, 12f), Style.px(a, 24f), 0)
            addView(barre, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return barre to boite
    }

    private fun telecharger(a: Activity, apres: () -> Unit) {
        val (barre, boite) = barre(a)
        val fenetre = AlertDialog.Builder(a)
            .setTitle(a.getString(R.string.fd_3d_telechargement))
            .setView(boite)
            .setCancelable(false)
            .show()
        Thread {
            val ok = AmeliorationImage.telecharger(a) { p -> a.runOnUiThread { barre.progress = p } }
            a.runOnUiThread {
                try { fenetre.dismiss() } catch (_: Exception) {}
                if (a.isFinishing || a.isDestroyed) return@runOnUiThread
                if (ok) apres()
                else Toast.makeText(a, a.getString(R.string.fd_3d_telechargement_rate), Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    // Le calcul, dans un Thread (l'écran reste fluide), avec une fenêtre : barre de progression et « Annuler ».
    private fun calculer(a: Activity, origine: File, apres: () -> Unit) {
        val (barre, boite) = barre(a)
        // « Annuler » lève ce drapeau ; le Thread du calcul le regarde avant chaque carreau.
        // (AtomicBoolean = une case vrai / faux faite pour être partagée entre deux fils d'exécution)
        val annule = AtomicBoolean(false)
        val fenetre = AlertDialog.Builder(a)
            .setTitle(a.getString(R.string.net_calcul))
            .setMessage(a.getString(R.string.net_calcul_texte))
            .setView(boite)
            .setCancelable(false)
            .setNegativeButton(a.getString(R.string.fd_annuler)) { _, _ -> annule.set(true) }
            .show()
        // l'écran ne s'éteint pas pendant le calcul (Android mettrait l'app en pause)
        fenetre.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Thread {
            var ok = false
            try {
                AmeliorationImage.ameliorer(a, origine, { p -> a.runOnUiThread { barre.progress = p } }, { annule.get() })
                ok = true
            } catch (_: AmeliorationImage.Annule) {
            } catch (_: Throwable) {
                // (Throwable et pas seulement Exception : pour attraper aussi le manque de mémoire sur une très grande image)
            }
            a.runOnUiThread {
                try { fenetre.dismiss() } catch (_: Exception) {}
                if (a.isFinishing || a.isDestroyed) return@runOnUiThread
                when {
                    ok -> apres()
                    !annule.get() -> Toast.makeText(a, a.getString(R.string.net_rate), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
