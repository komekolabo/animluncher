package com.will.animlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.File
import kotlin.math.max
import kotlin.math.min

// LA FABRIQUE D'UN FOND 3D (13/11) : à partir d'une photo choisie par l'utilisateur, on fabrique les deux
// calques du fond 3D (voir FondProfondeur.kt). Tout se passe SUR LE TÉLÉPHONE, la photo n'est envoyée nulle part.
//
//  1) LE DÉTOURAGE : l'outil gratuit de Google « ML Kit, Subject Segmentation » trouve le sujet de la photo
//     (personne, animal, objet). Il donne, pour chaque point de l'image, une « confiance » de 0 à 1 :
//     1 = sûrement le sujet, 0 = sûrement le fond. Son modèle est fourni par les services Google du téléphone
//     (téléchargé une fois par Android, il ne grossit pas notre app).
//  2) LE SUJET : la photo, dont chaque point devient plus ou moins transparent selon cette confiance.
//  3) LE FOND REBOUCHÉ : on efface le sujet et on remplit le trou avec les couleurs qui l'entourent
//     (méthode « pousser-tirer », expliquée plus bas), puis on adoucit. Ce n'est pas parfait, mais le sujet
//     cache presque tout le trou : on n'en voit que les bords quand on penche le téléphone.
//  4) On enregistre fond.jpg, sujet.png et apercu.jpg (la vignette de la galerie) dans fonds3d/p_<date>/.
//
// À lancer HORS de l'écran (dans un Thread) : ça prend 1 à 3 secondes.
object FabriqueFond3D {

    // Le dossier de tous les fonds 3D.
    fun dossierTous(c: Context): File = File(c.filesDir, "fonds3d").apply { mkdirs() }

    // Les fonds 3D existants, du plus récent au plus ancien (seulement ceux qui ont leurs 2 calques).
    fun liste(c: Context): List<File> =
        (dossierTous(c).listFiles() ?: emptyArray())
            .filter { it.isDirectory && File(it, "fond.jpg").exists() && File(it, "sujet.png").exists() }
            .sortedByDescending { it.lastModified() }

    // Ce qui peut mal tourner, pour afficher le bon message.
    class Echec(val raison: Raison) : Exception()
    enum class Raison { ILLISIBLE, PAS_DE_SUJET, MODULE_ABSENT }

    // Fabrique le fond 3D de la photo « uri ». Renvoie son dossier, ou lance un Echec.
    fun fabriquer(c: Context, uri: Uri): File {
        // la photo, réduite à 1600 points sur son grand côté (assez pour un écran, et le détourage reste rapide)
        val photo = charger(c, uri, 1600) ?: throw Echec(Raison.ILLISIBLE)
        val w = photo.width
        val h = photo.height

        // 1) LE DÉTOURAGE
        val confiance = FloatArray(w * h)
        val outil = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build())
        try {
            val resultat = try { Tasks.await(outil.process(InputImage.fromBitmap(photo, 0))) } catch (e: Exception) {
                // le cas le plus courant : le modèle n'est pas encore téléchargé par les services Google
                throw Echec(Raison.MODULE_ABSENT)
            }
            val masque = resultat.foregroundConfidenceMask ?: throw Echec(Raison.PAS_DE_SUJET)
            masque.rewind()
            masque.get(confiance, 0, min(confiance.size, masque.remaining()))
        } finally {
            try { outil.close() } catch (_: Exception) {}
        }
        // pas de sujet trouvé (moins de 2 % de l'image) : un fond 3D n'aurait pas de sens
        if (confiance.count { it > 0.5f } < w * h / 50) throw Echec(Raison.PAS_DE_SUJET)

        val pixels = IntArray(w * h)
        photo.getPixels(pixels, 0, w, 0, 0, w, h)

        // 2) LE SUJET : alpha = la confiance, rendue un peu plus nette (sous 0,3 : transparent ; au-dessus de 0,7 : opaque)
        val sujet = IntArray(w * h)
        for (i in pixels.indices) {
            val a = ((confiance[i] - 0.3f) / 0.4f).coerceIn(0f, 1f)
            sujet[i] = (pixels[i] and 0x00FFFFFF) or ((a * 255f).toInt() shl 24)
        }

        // 3) LE FOND REBOUCHÉ
        val fond = reboucher(pixels, confiance, w, h)

        // 4) L'ENREGISTREMENT
        val dossier = File(dossierTous(c), "p_" + System.currentTimeMillis()).apply { mkdirs() }
        try {
            Bitmap.createBitmap(fond, w, h, Bitmap.Config.ARGB_8888).let { b ->
                File(dossier, "fond.jpg").outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            }
            Bitmap.createBitmap(sujet, w, h, Bitmap.Config.ARGB_8888).let { b ->
                // PNG : il garde la transparence (le JPEG ne sait pas)
                File(dossier, "sujet.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            // la vignette : le fond 3D dessiné au repos, au format d'un téléphone
            val vignette = Bitmap.createBitmap(360, 780, Bitmap.Config.ARGB_8888)
            val dessin = FondProfondeur(c, dossier.path)
            Canvas(vignette).apply { drawColor(Color.BLACK); dessin.dessiner(this, 360f, 780f, 0f, 1f) }
            File(dossier, "apercu.jpg").outputStream().use { vignette.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } catch (e: Exception) {
            dossier.deleteRecursively()
            throw Echec(Raison.ILLISIBLE)
        }
        return dossier
    }

    // Lit la photo dans le bon sens (portrait / paysage), réduite si elle est plus grande que « cote ».
    private fun charger(c: Context, uri: Uri, cote: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.contentResolver, uri)) { dec, info, _ ->
                val k = max(info.size.width, info.size.height).toFloat() / cote
                if (k > 1f) dec.setTargetSize((info.size.width / k).toInt(), (info.size.height / k).toInt())
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE      // une image en mémoire normale (on lit ses points)
            }.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            val b = c.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            if (b == null) null else {
                val k = max(b.width, b.height).toFloat() / cote
                if (k > 1f) Bitmap.createScaledBitmap(b, (b.width / k).toInt(), (b.height / k).toInt(), true) else b
            }
        }
    } catch (_: Exception) { null }

    // ---------------------------------------------------------------------------------------------
    // LE REBOUCHAGE « POUSSER-TIRER ».
    // On travaille sur une PETITE copie de la photo (256 points de large) : c'est rapide, et le résultat
    // sera adouci de toute façon.
    //  - le trou = le sujet, un peu agrandi (pour ne pas garder son contour) ;
    //  - POUSSER : on fabrique des versions de plus en plus petites de l'image (2 fois plus petite à chaque
    //    fois) en faisant la moyenne de 4 points, SANS compter les points du trou. Au bout de quelques
    //    étapes, il n'y a plus de trou : chaque point est la moyenne de ce qui l'entoure ;
    //  - TIRER : on remonte vers la grande taille ; chaque point du trou prend la couleur de la version plus
    //    petite. Le trou se remplit ainsi avec les couleurs de ses bords, de proche en proche ;
    //  - on floute un peu le résultat, on l'agrandit à la taille de la photo, et on ne le colle que dans le
    //    trou (avec un bord doux), le reste de la photo gardant tous ses détails.
    // ---------------------------------------------------------------------------------------------
    private fun reboucher(pixels: IntArray, confiance: FloatArray, w: Int, h: Int): IntArray {
        val k = max(1f, max(w, h) / 256f)
        val pw = max(1, (w / k).toInt())
        val ph = max(1, (h / k).toInt())
        // la petite copie et son trou (un point est dans le trou si le sujet en couvre une partie)
        val r = FloatArray(pw * ph); val g = FloatArray(pw * ph); val b = FloatArray(pw * ph)
        var trou = BooleanArray(pw * ph)
        for (y in 0 until ph) for (x in 0 until pw) {
            val sx = min(w - 1, (x * k).toInt()); val sy = min(h - 1, (y * k).toInt())
            var rr = 0f; var gg = 0f; var bb = 0f; var n = 0; var sujet = false
            // moyenne des points de la photo couverts par ce petit point
            val pas = max(1, (k / 2f).toInt())
            var yy = sy
            while (yy < min(h, sy + k.toInt() + 1)) {
                var xx = sx
                while (xx < min(w, sx + k.toInt() + 1)) {
                    val p = pixels[yy * w + xx]
                    rr += Color.red(p); gg += Color.green(p); bb += Color.blue(p); n++
                    if (confiance[yy * w + xx] > 0.15f) sujet = true
                    xx += pas
                }
                yy += pas
            }
            val i = y * pw + x
            r[i] = rr / n; g[i] = gg / n; b[i] = bb / n
            trou[i] = sujet
        }
        trou = elargir(trou, pw, ph, 4)              // le trou agrandi de 4 petits points (~1,5 % de l'image)

        // POUSSER : les étages, du plus grand au plus petit. Chaque étage garde des sommes de couleurs
        // et un « poids » (combien de vrais points, hors trou, ont servi).
        class Etage(val w: Int, val h: Int, val r: FloatArray, val g: FloatArray, val b: FloatArray, val p: FloatArray)
        val etages = ArrayList<Etage>()
        val p0 = FloatArray(pw * ph) { if (trou[it]) 0f else 1f }
        etages.add(Etage(pw, ph, FloatArray(pw * ph) { r[it] * p0[it] }, FloatArray(pw * ph) { g[it] * p0[it] },
            FloatArray(pw * ph) { b[it] * p0[it] }, p0))
        while (etages.last().w > 1 || etages.last().h > 1) {
            val e = etages.last()
            val nw = (e.w + 1) / 2; val nh = (e.h + 1) / 2
            val n = Etage(nw, nh, FloatArray(nw * nh), FloatArray(nw * nh), FloatArray(nw * nh), FloatArray(nw * nh))
            for (y in 0 until e.h) for (x in 0 until e.w) {
                val i = y * e.w + x; val j = (y / 2) * nw + x / 2
                n.r[j] += e.r[i]; n.g[j] += e.g[i]; n.b[j] += e.b[i]; n.p[j] += e.p[i]
            }
            etages.add(n)
        }
        // TIRER : du plus petit au plus grand. On transforme les sommes en vraies couleurs ; là où l'étage a
        // peu de vrais points (poids < 1), on complète avec la couleur de l'étage plus petit.
        for (niveau in etages.indices.reversed()) {
            val e = etages[niveau]
            val dessous = if (niveau + 1 < etages.size) etages[niveau + 1] else null
            for (y in 0 until e.h) for (x in 0 until e.w) {
                val i = y * e.w + x
                val poids = e.p[i]
                var cr = 40f; var cg = 40f; var cb = 40f            // (gris foncé si toute l'image était le sujet)
                if (dessous != null) {
                    val j = (y / 2) * dessous.w + x / 2
                    cr = dessous.r[j]; cg = dessous.g[j]; cb = dessous.b[j]
                }
                val part = min(1f, poids)
                if (poids > 0f) {
                    cr = e.r[i] / poids * part + cr * (1f - part)
                    cg = e.g[i] / poids * part + cg * (1f - part)
                    cb = e.b[i] / poids * part + cb * (1f - part)
                }
                e.r[i] = cr; e.g[i] = cg; e.b[i] = cb               // l'étage contient maintenant des couleurs
            }
        }
        val plein = etages[0]
        // on floute deux fois (rayon 3) : le remplissage devient un dégradé doux, sans marches
        repeat(2) { flouter(plein.r, pw, ph, 3); flouter(plein.g, pw, ph, 3); flouter(plein.b, pw, ph, 3) }

        // le masque du collage : 1 dans le trou, 0 ailleurs, avec un bord flouté (rayon 2)
        val masque = FloatArray(pw * ph) { if (trou[it]) 1f else 0f }
        flouter(masque, pw, ph, 2)

        // on agrandit le remplissage et le masque à la taille de la photo (Android les lisse en agrandissant)
        val petit = IntArray(pw * ph) { i ->
            Color.argb((masque[i] * 255f).toInt().coerceIn(0, 255),
                plein.r[i].toInt().coerceIn(0, 255), plein.g[i].toInt().coerceIn(0, 255), plein.b[i].toInt().coerceIn(0, 255))
        }
        val grand = IntArray(w * h)
        Bitmap.createScaledBitmap(Bitmap.createBitmap(petit, pw, ph, Bitmap.Config.ARGB_8888), w, h, true)
            .getPixels(grand, 0, w, 0, 0, w, h)
        // le collage : photo d'origine hors du trou, remplissage dans le trou
        val sortie = IntArray(w * h)
        for (i in pixels.indices) {
            val q = grand[i]
            val m = Color.alpha(q) / 255f
            val p = pixels[i]
            sortie[i] = Color.rgb(
                (Color.red(p) * (1 - m) + Color.red(q) * m).toInt(),
                (Color.green(p) * (1 - m) + Color.green(q) * m).toInt(),
                (Color.blue(p) * (1 - m) + Color.blue(q) * m).toInt())
        }
        return sortie
    }

    // Agrandit une zone de « rayon » points dans toutes les directions.
    private fun elargir(zone: BooleanArray, w: Int, h: Int, rayon: Int): BooleanArray {
        val horiz = BooleanArray(zone.size)
        for (y in 0 until h) for (x in 0 until w) {
            var v = false
            for (d in -rayon..rayon) { val xx = x + d; if (xx in 0 until w && zone[y * w + xx]) { v = true; break } }
            horiz[y * w + x] = v
        }
        val sortie = BooleanArray(zone.size)
        for (y in 0 until h) for (x in 0 until w) {
            var v = false
            for (d in -rayon..rayon) { val yy = y + d; if (yy in 0 until h && horiz[yy * w + x]) { v = true; break } }
            sortie[y * w + x] = v
        }
        return sortie
    }

    // Un flou simple : chaque point devient la moyenne de ses voisins (d'abord en ligne, puis en colonne).
    private fun flouter(v: FloatArray, w: Int, h: Int, rayon: Int) {
        val tmp = FloatArray(v.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f; var n = 0
            for (d in -rayon..rayon) { val xx = x + d; if (xx in 0 until w) { s += v[y * w + xx]; n++ } }
            tmp[y * w + x] = s / n
        }
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f; var n = 0
            for (d in -rayon..rayon) { val yy = y + d; if (yy in 0 until h) { s += tmp[yy * w + x]; n++ } }
            v[y * w + x] = s / n
        }
    }
}
