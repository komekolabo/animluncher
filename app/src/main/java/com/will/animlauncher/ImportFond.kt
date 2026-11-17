package com.will.animlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max

// L'IMPORT D'UN FOND (17/11 : sorti de FondsActivity pour servir aussi au « dossier de fonds »).
// Le fichier choisi est COPIÉ dans un dossier privé de l'app (il ne quitte jamais le téléphone) :
//  - vidéo : on garde seulement la piste vidéo (le son est retiré, sans réencoder : c'est rapide) ;
//  - image : réduite à la taille de l'écran (une photo énorme gaspillerait de la mémoire) ;
//  - GIF : copié tel quel.
// « dossier » = où ranger le fichier ; « nom » = son nom sans la fin (.mp4, .jpg…), différent pour chaque fichier.
object ImportFond {

    // Copie le fichier choisi dans « dossier », sous la bonne forme. Renvoie le fichier créé (ou null).
    fun importer(c: Context, uri: Uri, dossier: File, nom: String = "fond_" + System.currentTimeMillis()): File? {
        val type = c.contentResolver.getType(uri) ?: ""
        return when {
            type == "image/gif" -> File(dossier, "$nom.gif").also { copier(c, uri, it) }
            type.startsWith("video/") -> {
                val brut = File(c.cacheDir, "$nom.tmp")
                copier(c, uri, brut)
                var muette = true
                val sortie = try { sansSon(brut, dossier, nom) } catch (_: Exception) {
                    // si le retrait du son échoue (format rare), on garde la vidéo telle quelle (muette à la lecture)
                    muette = false
                    File(dossier, "$nom.mp4").also { brut.copyTo(it, overwrite = true) }
                }
                // (17/11) si la vidéo avait du son, on garde AUSSI l'originale, avec son son, dans le sous-dossier
                // « son/ » : elle est jouée quand l'utilisateur permet le son des fonds (voir SonFond.kt)
                if (muette && aDuSon(brut)) {
                    try {
                        val avecSon = SonFond.avecSon(sortie)
                        avecSon.parentFile?.mkdirs()
                        brut.copyTo(avecSon, overwrite = true)
                    } catch (_: Exception) {}
                }
                brut.delete()
                sortie
            }
            type.startsWith("image/") -> imageReduite(c, uri, File(dossier, "$nom.jpg"))
            else -> null
        }
    }

    // SUPPRIMER un fond importé (17/11) : le fichier (ou le dossier d'un fond 3D) ET sa version avec le son.
    fun supprimer(f: File) {
        SonFond.avecSon(f).delete()
        f.deleteRecursively()
    }

    // La vidéo a-t-elle une piste son ?
    private fun aDuSon(f: File): Boolean {
        val lecteur = MediaExtractor()
        return try {
            lecteur.setDataSource(f.path)
            (0 until lecteur.trackCount).any { lecteur.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        } catch (_: Exception) { false } finally { lecteur.release() }
    }

    private fun copier(c: Context, uri: Uri, vers: File) {
        c.contentResolver.openInputStream(uri).use { entree ->
            requireNotNull(entree)
            vers.outputStream().use { sortie -> entree.copyTo(sortie) }
        }
    }

    // Réduit l'image à la taille de l'écran et l'enregistre en JPEG.
    private fun imageReduite(c: Context, uri: Uri, vers: File): File? {
        val m = c.resources.displayMetrics
        val cible = max(m.widthPixels, m.heightPixels)
        val b: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder tient compte du sens de la photo (portrait / paysage) tout seul
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.contentResolver, uri)) { dec, info, _ ->
                val k = max(info.size.width, info.size.height).toFloat() / cible
                if (k > 1f) dec.setTargetSize((info.size.width / k).toInt(), (info.size.height / k).toInt())
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            c.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) } ?: return null
        }
        vers.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return vers
    }

    // RETIRER LE SON d'une vidéo sans la réencoder : on recopie seulement la piste vidéo dans un
    // nouveau fichier (« démultiplexer » puis « multiplexer »). Pourquoi ? Une vidéo avec du son, même
    // muette, fait croire au téléphone qu'une musique joue (et la réaction sonore s'allumerait).
    private fun sansSon(brut: File, dossier: File, nom: String): File {
        val lecteur = MediaExtractor()
        lecteur.setDataSource(brut.path)
        var piste = -1
        var format: MediaFormat? = null
        for (i in 0 until lecteur.trackCount) {
            val f = lecteur.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) { piste = i; format = f; break }
        }
        if (piste < 0 || format == null) { lecteur.release(); throw IllegalStateException("pas de vidéo") }
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        val webm = mime == "video/x-vnd.on2.vp8" || mime == "video/x-vnd.on2.vp9"
        val sortie = File(dossier, nom + if (webm) ".webm" else ".mp4")
        val ecrivain = MediaMuxer(sortie.path,
            if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            // le sens de la vidéo (filmée en portrait = souvent « tournée de 90° » dans le fichier)
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(brut.path)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?.let { ecrivain.setOrientationHint(it) }
            } catch (_: Exception) {} finally { r.release() }
            val destination = ecrivain.addTrack(format)
            ecrivain.start()
            lecteur.selectTrack(piste)
            val taille = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
            val tampon = ByteBuffer.allocate(max(taille, 4 * 1024 * 1024))
            val info = MediaCodec.BufferInfo()
            while (true) {
                val n = lecteur.readSampleData(tampon, 0)
                if (n < 0) break
                info.offset = 0
                info.size = n
                info.presentationTimeUs = lecteur.sampleTime
                info.flags = if (lecteur.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                ecrivain.writeSampleData(destination, tampon, info)
                lecteur.advance()
            }
            ecrivain.stop()
        } catch (e: Exception) {
            sortie.delete()
            throw e
        } finally {
            try { ecrivain.release() } catch (_: Exception) {}
            lecteur.release()
        }
        return sortie
    }
}
