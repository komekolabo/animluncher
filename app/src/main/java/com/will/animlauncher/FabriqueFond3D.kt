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
// Deux étapes, avec la GOMME entre les deux (GommeActivity.kt) :
//
//  ÉTAPE 1, detourer() : trouver le sujet. Deux outils possibles, choisis tout seuls :
//   - l'outil PHOTO de Google (« ML Kit, Subject Segmentation »), fourni par les services Google du téléphone ;
//   - l'outil DESSINS (DetourageDessins.kt), spécialisé dans les dessins et l'anime, téléchargé à la demande (44 Mo).
//   Le choix : une image en noir et blanc (manga…) va directement à l'outil dessins. Sinon on essaie l'outil photo, et
//   s'il donne un mauvais résultat (sujet introuvable, sujet qui couvre toute l'image, contour très incertain), on
//   passe à l'outil dessins. Chaque outil donne, pour chaque point, une « confiance » de 0 à 1 (1 = sûrement le sujet).
//   Puis on NETTOIE : on est plus strict (sous 40 % de confiance : transparent), et on ne garde que les gros
//   morceaux (le personnage), pas les petits bouts isolés autour (lettres, traits de vitesse…).
//
//  ÉTAPE 2, terminer() (après la gomme) : le sujet (la photo, transparente hors du sujet), le fond rebouché (le trou du
//   sujet rempli avec les couleurs autour, méthode « pousser-tirer » expliquée plus bas), et la vignette ; le tout
//   enregistré dans fonds3d/p_<date>/.
//
// À lancer HORS de l'écran (dans un Thread) : chaque étape prend 1 à 5 secondes.
object FabriqueFond3D {

    // Le dossier de tous les fonds 3D.
    fun dossierTous(c: Context): File = File(c.filesDir, "fonds3d").apply { mkdirs() }

    // Les fonds 3D existants, du plus récent au plus ancien (seulement ceux qui ont leurs 2 calques).
    fun liste(c: Context): List<File> =
        (dossierTous(c).listFiles() ?: emptyArray())
            .filter { it.isDirectory && File(it, "fond.jpg").exists() && File(it, "sujet.png").exists() }
            .sortedByDescending { it.lastModified() }

    enum class Outil { PHOTO, DESSIN }

    // Ce qui peut mal tourner, pour afficher le bon message.
    class Echec(val raison: Raison) : Exception()
    enum class Raison { ILLISIBLE, PAS_DE_SUJET, SUJET_TROP_GRAND, MODULE_ABSENT, OUTIL_DESSINS_ABSENT }

    // Le résultat de l'étape 1 : la photo, et pour chaque point la part de sujet (0 = fond, 1 = sujet).
    // « masque » est modifié par la gomme.
    class Detourage(val photo: Bitmap, val masque: FloatArray, val outil: Outil) {
        val w get() = photo.width
        val h get() = photo.height
    }

    // ÉTAPE 1. « force » : null = choix automatique, sinon l'outil voulu (bouton « Essayer l'autre outil » de la gomme).
    fun detourer(c: Context, uri: Uri, force: Outil? = null): Detourage {
        // la photo, réduite à 1600 points sur son grand côté (assez pour un écran, et le détourage reste rapide)
        val photo = charger(c, uri, 1600) ?: throw Echec(Raison.ILLISIBLE)
        return detourer(c, photo, force)
    }

    // La même chose à partir d'une photo déjà chargée (« Essayer l'autre outil », retouche d'un fond existant).
    fun detourer(c: Context, photo: Bitmap, force: Outil? = null): Detourage {
        val choix = force ?: if (noirEtBlanc(photo)) Outil.DESSIN else null
        if (choix == Outil.DESSIN) return parDessins(c, photo)
        val confiance = try { parPhoto(photo) } catch (e: Echec) {
            // l'outil photo n'est pas prêt : l'outil dessins s'il est là, sinon le message
            if (e.raison == Raison.MODULE_ABSENT && force == null && DetourageDessins.estInstalle(c)) return parDessins(c, photo)
            throw e
        }
        var probleme = evaluer(confiance)
        // (outil photo choisi à la main : un contour incertain est accepté, la gomme est là pour corriger)
        if (force == Outil.PHOTO && probleme == Raison.ILLISIBLE) probleme = null
        if (probleme == null) return Detourage(photo, nettoyer(confiance, photo.width, photo.height), Outil.PHOTO)
        // mauvais résultat : en automatique, on passe à l'outil dessins (s'il manque, l'écran propose de le télécharger)
        if (force == null) {
            if (!DetourageDessins.estInstalle(c)) throw Echec(Raison.OUTIL_DESSINS_ABSENT)
            return parDessins(c, photo)
        }
        throw Echec(probleme)
    }

    private fun parDessins(c: Context, photo: Bitmap): Detourage {
        if (!DetourageDessins.estInstalle(c)) throw Echec(Raison.OUTIL_DESSINS_ABSENT)
        val confiance = try { DetourageDessins.masque(c, photo) } catch (e: Exception) { throw Echec(Raison.ILLISIBLE) }
        evaluer(confiance)?.let { if (it != Raison.ILLISIBLE) throw Echec(it) }
        return Detourage(photo, nettoyer(confiance, photo.width, photo.height), Outil.DESSIN)
    }

    // L'outil photo de Google.
    private fun parPhoto(photo: Bitmap): FloatArray {
        val w = photo.width
        val h = photo.height
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
        return confiance
    }

    // Le résultat est-il bon ? null = oui ; sinon le problème.
    //  - sujet introuvable : moins de 2 % de l'image ;
    //  - sujet trop grand : plus de 60 % (vu par Will sur une capture de manga : toute la page était « le sujet ») ;
    //  - contour très incertain : beaucoup de points « à moitié sûrs » (entre 20 et 80 %) par rapport au sujet
    //    (marqué ILLISIBLE ici : ça veut juste dire « essayer l'autre outil »).
    private fun evaluer(confiance: FloatArray): Raison? {
        var sujet = 0; var incertain = 0
        for (v in confiance) { if (v > 0.5f) sujet++; if (v > 0.2f && v < 0.8f) incertain++ }
        val part = sujet.toFloat() / confiance.size
        return when {
            part < 0.02f -> Raison.PAS_DE_SUJET
            part > 0.6f -> Raison.SUJET_TROP_GRAND
            incertain > sujet * 0.35f -> Raison.ILLISIBLE
            else -> null
        }
    }

    // Une image presque sans couleur (manga, croquis au crayon) : on mesure l'écart moyen entre le rouge, le vert et
    // le bleu de chaque point (0 = parfaitement gris). Une photo, même terne, a toujours un peu de couleur.
    private fun noirEtBlanc(photo: Bitmap): Boolean {
        val petite = Bitmap.createScaledBitmap(photo, 96, max(1, 96 * photo.height / photo.width), true)
        val p = IntArray(petite.width * petite.height)
        petite.getPixels(p, 0, petite.width, 0, 0, petite.width, petite.height)
        var somme = 0L
        for (q in p) {
            val r = Color.red(q); val g = Color.green(q); val b = Color.blue(q)
            somme += maxOf(r, g, b) - minOf(r, g, b)
        }
        return somme / p.size < 6
    }

    // LE NETTOYAGE (testé sur la capture de manga de Will : les onomatopées « WAAA » restaient en transparence).
    //  1) plus strict : sous 40 % de confiance, transparent ; au-dessus de 80 %, opaque ; entre les deux, un fondu ;
    //  2) on repère les MORCEAUX (les zones de sujet qui se touchent) et on ne garde que le plus gros, plus ceux qui
    //     font au moins 15 % de sa taille (un bras séparé du corps, par exemple). Les petits bouts isolés partent ;
    //  3) on laisse une petite marge autour des morceaux gardés (pour les cheveux fins et les bords doux).
    fun nettoyer(confiance: FloatArray, w: Int, h: Int): FloatArray {
        val a = FloatArray(w * h) { ((confiance[it] - 0.4f) / 0.4f).coerceIn(0f, 1f) }
        // les morceaux : chaque point de sujet reçoit le numéro de son morceau (remplissage de proche en proche)
        val numero = IntArray(w * h)
        val tailles = ArrayList<Int>()
        val file = IntArray(w * h)
        for (depart in 0 until w * h) {
            if (a[depart] <= 0.5f || numero[depart] != 0) continue
            val n = tailles.size + 1
            var debut = 0; var fin = 0
            file[fin++] = depart; numero[depart] = n
            while (debut < fin) {
                val i = file[debut++]
                val x = i % w; val y = i / w
                if (x > 0) { val j = i - 1; if (numero[j] == 0 && a[j] > 0.5f) { numero[j] = n; file[fin++] = j } }
                if (x < w - 1) { val j = i + 1; if (numero[j] == 0 && a[j] > 0.5f) { numero[j] = n; file[fin++] = j } }
                if (y > 0) { val j = i - w; if (numero[j] == 0 && a[j] > 0.5f) { numero[j] = n; file[fin++] = j } }
                if (y < h - 1) { val j = i + w; if (numero[j] == 0 && a[j] > 0.5f) { numero[j] = n; file[fin++] = j } }
            }
            tailles.add(fin)
        }
        if (tailles.isEmpty()) return a
        val plusGros = tailles.max()
        val garde = BooleanArray(w * h) { numero[it] != 0 && tailles[numero[it] - 1] >= plusGros * 0.15f }
        val zone = elargir(garde, w, h, max(2, max(w, h) / 270))
        for (i in a.indices) if (!zone[i]) a[i] = 0f
        return a
    }

    // LE REMPLISSAGE DERRIÈRE LE SUJET (13/11, Will trouvait le flou trop visible sur son manga) :
    //  DOUX = le trou est bien agrandi et très adouci (le plus discret sur une photo) ;
    //  NET = trou à peine agrandi, presque pas adouci (moins de taches floues) ;
    //  AUCUN = pas de rebouchage : l'image d'origine reste derrière le sujet (quand on penche, on voit un léger
    //  « écho » du sujet, mais aucune tache floue).
    const val DOUX = 0
    const val NET = 1
    const val AUCUN = 2

    fun remplissage(dossier: File): Int =
        try { File(dossier, "remplissage.txt").readText().trim().toInt() } catch (_: Exception) { DOUX }

    // RETOUCHER un fond 3D déjà créé : on retrouve la photo d'origine et le détourage (la transparence du sujet).
    // Les fonds créés avant le 13/11 n'ont pas gardé la photo d'origine : on la reconstitue (le fond, avec le sujet
    // par-dessus), ce qui est identique partout sauf dans la marge rebouchée autour du sujet.
    fun rouvrir(c: Context, dossier: File): Detourage? = try {
        val sujet = BitmapFactory.decodeFile(File(dossier, "sujet.png").path)
        val w = sujet.width; val h = sujet.height
        val ps = IntArray(w * h); sujet.getPixels(ps, 0, w, 0, 0, w, h)
        val photo = File(dossier, "photo.jpg").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
            ?.takeIf { it.width == w && it.height == h }
            ?: run {
                val fond = Bitmap.createScaledBitmap(BitmapFactory.decodeFile(File(dossier, "fond.jpg").path), w, h, true)
                val pf = IntArray(w * h); fond.getPixels(pf, 0, w, 0, 0, w, h)
                for (i in pf.indices) if (Color.alpha(ps[i]) > 0) pf[i] = ps[i] or (0xFF shl 24)
                Bitmap.createBitmap(pf, w, h, Bitmap.Config.ARGB_8888)
            }
        val masque = FloatArray(w * h) { Color.alpha(ps[it]) / 255f }
        val outil = try { Outil.valueOf(File(dossier, "outil.txt").readText().trim()) } catch (_: Exception) { Outil.PHOTO }
        Detourage(photo, masque, outil)
    } catch (_: Throwable) { null }

    // ÉTAPE 2 : enregistre le fond 3D (après la gomme). Renvoie son dossier, ou lance un Echec.
    // « dossierExistant » : on retouche un fond déjà créé (il garde sa place, et le fond d'écran se met à jour).
    fun terminer(c: Context, d: Detourage, mode: Int = DOUX, dossierExistant: File? = null): File {
        val w = d.w
        val h = d.h
        val pixels = IntArray(w * h)
        d.photo.getPixels(pixels, 0, w, 0, 0, w, h)
        // LE SUJET : la photo, avec pour transparence la part de sujet de chaque point
        val sujet = IntArray(w * h) { (pixels[it] and 0x00FFFFFF) or ((d.masque[it] * 255f).toInt().coerceIn(0, 255) shl 24) }
        // LE FOND REBOUCHÉ
        val fond = if (mode == AUCUN) pixels else reboucher(pixels, d.masque, w, h, mode == NET)
        // L'ENREGISTREMENT
        val dossier = dossierExistant ?: File(dossierTous(c), "p_" + System.currentTimeMillis()).apply { mkdirs() }
        try {
            Bitmap.createBitmap(fond, w, h, Bitmap.Config.ARGB_8888).let { b ->
                File(dossier, "fond.jpg").outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            }
            Bitmap.createBitmap(sujet, w, h, Bitmap.Config.ARGB_8888).let { b ->
                // PNG : il garde la transparence (le JPEG ne sait pas)
                File(dossier, "sujet.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            // la photo d'origine et les choix faits (pour pouvoir RETOUCHER ce fond plus tard)
            if (dossierExistant == null || !File(dossier, "photo.jpg").exists())
                File(dossier, "photo.jpg").outputStream().use { d.photo.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            File(dossier, "remplissage.txt").writeText(mode.toString())
            File(dossier, "outil.txt").writeText(d.outil.name)
            // la vignette : le fond 3D dessiné au repos, au format d'un téléphone
            val vignette = Bitmap.createBitmap(360, 780, Bitmap.Config.ARGB_8888)
            val dessin = FondProfondeur(c, dossier.path)
            Canvas(vignette).apply { drawColor(Color.BLACK); dessin.dessiner(this, 360f, 780f, 0f, 1f) }
            File(dossier, "apercu.jpg").outputStream().use { vignette.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } catch (e: Exception) {
            if (dossierExistant == null) dossier.deleteRecursively()
            throw Echec(Raison.ILLISIBLE)
        }
        // si ce fond est le fond d'écran actuel, le service le recharge (il écoute les changements du carnet « fond »)
        CarnetFond.carnet(c).edit().putLong(CarnetFond.PREFIXE_DESSIN + "maj", System.currentTimeMillis()).apply()
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
    private fun reboucher(pixels: IntArray, confiance: FloatArray, w: Int, h: Int, net: Boolean = false): IntArray {
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
        // le trou agrandi de 4 petits points (~1,5 % de l'image) ; en mode NET, d'un seul (moins de taches floues)
        trou = elargir(trou, pw, ph, if (net) 1 else 4)

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
        if (net) { flouter(plein.r, pw, ph, 1); flouter(plein.g, pw, ph, 1); flouter(plein.b, pw, ph, 1) }
        else repeat(2) { flouter(plein.r, pw, ph, 3); flouter(plein.g, pw, ph, 3); flouter(plein.b, pw, ph, 3) }

        // le masque du collage : 1 dans le trou, 0 ailleurs, avec un bord flouté (rayon 2)
        val masque = FloatArray(pw * ph) { if (trou[it]) 1f else 0f }
        flouter(masque, pw, ph, if (net) 1 else 2)

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
