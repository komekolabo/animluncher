package com.will.animlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tflite.java.TfLite
import org.tensorflow.lite.InterpreterApi
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

// L'OUTIL DE DÉTOURAGE DES DESSINS (13/11) : un « modèle » d'intelligence artificielle spécialisé dans les dessins et
// l'anime (là où l'outil de Google, fait pour les photos, échoue). C'est « anime-segmentation » de SkyTNT
// (licence Apache 2.0), converti par Claude au format des téléphones Android et compressé : 44 Mo.
//
// Il n'est PAS dans l'app (elle reste légère) : il est téléchargé UNE fois, la première fois qu'on détoure un dessin,
// après une fenêtre qui prévient (voir FondsActivity). On peut le supprimer en un bouton (en bas de l'écran Fonds
// d'écran) ; il serait retéléchargé seulement si on détoure un nouveau dessin.
// Il est hébergé dans la branche « modeles » du dépôt GitHub d'AnimEngine.
//
// Pour le faire tourner, on utilise le moteur d'IA fourni par les services Google du téléphone (« LiteRT » / TFLite) :
// lui non plus n'est pas dans l'app.
object DetourageDessins {

    private const val ADRESSE = "https://raw.githubusercontent.com/komekolabo/animluncher/modeles/detourage_dessins_v1.tflite"
    const val TAILLE_MO = 44
    private const val COTE = 1024                 // le modèle travaille sur une image carrée de 1024 x 1024

    fun fichier(c: Context): File = File(File(c.filesDir, "modeles").apply { mkdirs() }, "detourage_dessins_v1.tflite")

    fun estInstalle(c: Context): Boolean = fichier(c).let { it.exists() && it.length() > 40_000_000L }

    fun supprimer(c: Context) { fichier(c).delete() }

    // Télécharge le modèle (dans un Thread !). « progres » reçoit le pourcentage (0 à 100).
    // On écrit d'abord dans un fichier « .part » : si le téléchargement est coupé, on n'a pas de modèle à moitié.
    fun telecharger(c: Context, progres: (Int) -> Unit): Boolean {
        val final = fichier(c)
        val partiel = File(final.path + ".part")
        try {
            val cx = URL(ADRESSE).openConnection() as HttpURLConnection
            cx.connectTimeout = 15_000
            cx.readTimeout = 30_000
            if (cx.responseCode != 200) return false
            val total = cx.contentLengthLong
            cx.inputStream.use { entree ->
                partiel.outputStream().use { sortie ->
                    val tampon = ByteArray(64 * 1024)
                    var lu = 0L
                    var dernier = -1
                    while (true) {
                        val n = entree.read(tampon)
                        if (n < 0) break
                        sortie.write(tampon, 0, n)
                        lu += n
                        if (total > 0) {
                            val p = (lu * 100 / total).toInt()
                            if (p != dernier) { dernier = p; progres(p) }
                        }
                    }
                }
            }
            if (total > 0 && partiel.length() != total) { partiel.delete(); return false }
            return partiel.renameTo(final)
        } catch (_: Exception) {
            partiel.delete()
            return false
        }
    }

    // Détoure « photo » : renvoie, pour chaque point, la confiance (0 à 1) qu'il appartient au sujet.
    // (dans un Thread : 1 à 5 secondes selon le téléphone)
    fun masque(c: Context, photo: Bitmap): FloatArray {
        val w = photo.width
        val h = photo.height
        // 1) on prépare le moteur d'IA des services Google
        Tasks.await(TfLite.initialize(c))
        val options = InterpreterApi.Options().setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_SYSTEM_ONLY)
        val moteur = InterpreterApi.create(fichier(c), options)
        try {
            // 2) la photo est réduite pour tenir dans un carré de 1024 (sans déformer), centrée sur du noir
            val k = COTE.toFloat() / max(w, h)
            val pw = (w * k).toInt().coerceIn(1, COTE)
            val ph = (h * k).toInt().coerceIn(1, COTE)
            val ox = (COTE - pw) / 2
            val oy = (COTE - ph) / 2
            val carre = Bitmap.createBitmap(COTE, COTE, Bitmap.Config.ARGB_8888)
            Canvas(carre).apply {
                drawColor(Color.BLACK)
                drawBitmap(photo, null, RectF(ox.toFloat(), oy.toFloat(), (ox + pw).toFloat(), (oy + ph).toFloat()),
                    Paint(Paint.FILTER_BITMAP_FLAG))
            }
            // 3) l'entrée du modèle : pour chaque point, rouge, vert, bleu entre 0 et 1 (nombres à virgule)
            val points = IntArray(COTE * COTE)
            carre.getPixels(points, 0, COTE, 0, 0, COTE, COTE)
            val entree = ByteBuffer.allocateDirect(COTE * COTE * 3 * 4).order(ByteOrder.nativeOrder())
            for (p in points) {
                entree.putFloat(Color.red(p) / 255f)
                entree.putFloat(Color.green(p) / 255f)
                entree.putFloat(Color.blue(p) / 255f)
            }
            entree.rewind()
            val sortie = ByteBuffer.allocateDirect(COTE * COTE * 4).order(ByteOrder.nativeOrder())
            // 4) le calcul
            moteur.run(entree, sortie)
            sortie.rewind()
            val brut = FloatArray(COTE * COTE)
            sortie.asFloatBuffer().get(brut)
            // 5) on ne garde que la zone de la photo, ramenée entre 0 et 1, puis agrandie à la taille de la photo
            var mini = Float.MAX_VALUE; var maxi = -Float.MAX_VALUE
            for (y in oy until oy + ph) for (x in ox until ox + pw) {
                val v = brut[y * COTE + x]; if (v < mini) mini = v; if (v > maxi) maxi = v
            }
            val ecart = max(1e-6f, maxi - mini)
            val petit = IntArray(pw * ph) { i ->
                val v = ((brut[(oy + i / pw) * COTE + ox + i % pw] - mini) / ecart * 255f).toInt().coerceIn(0, 255)
                Color.rgb(v, v, v)
            }
            val grand = IntArray(w * h)
            Bitmap.createScaledBitmap(Bitmap.createBitmap(petit, pw, ph, Bitmap.Config.ARGB_8888), w, h, true)
                .getPixels(grand, 0, w, 0, 0, w, h)
            return FloatArray(w * h) { Color.red(grand[it]) / 255f }
        } finally {
            moteur.close()
        }
    }

    // (pour les réglages) la place prise, en Mo
    fun placeMo(c: Context): Int = (fichier(c).length() / 1_000_000L).toInt()
}
