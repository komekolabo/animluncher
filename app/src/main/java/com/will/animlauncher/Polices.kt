package com.will.animlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.widget.RemoteViews
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.ceil

// LES POLICES D'ÉCRITURE DES DOSSIERS (18/11, demande de Will) : le nom d'un dossier peut s'écrire en gothique,
// en écriture d'horreur, en pixels… Le choix se fait dans la fenêtre « Renommer » (DossiersPerso.kt) ; il est retenu
// pour chaque dossier (Groupes.police) et s'applique au titre du dossier ouvert ET au nom sous son widget.
//
// LES FICHIERS : 7 polices gratuites de Google Fonts (licence SIL Open Font License : on a le droit de les mettre
// dans l'app, même payante), réduites aux lettres latines avec accents pour rester légères (360 Ko en tout).
// Elles sont rangées ensemble dans « assets/polices.zip » (avec le texte de leurs licences). À la première utilisation,
// on les sort du zip dans le dossier privé de l'app (filesDir/polices) : Android sait lire une police depuis un fichier.
// Si le zip manque, seule la police « Classique » (celle du téléphone) est proposée : rien ne plante.
//
// UN WIDGET NE PEUT PAS CHANGER DE POLICE (Android ne le permet pas pour les textes des widgets). L'astuce : on dessine
// le nom nous-mêmes dans une petite image, et le widget affiche cette image à la place du texte (voir poserNom).
object Polices {

    // Une police : sa clé (aussi le nom de son fichier), son nom affiché, et un facteur de taille (certaines polices
    // sont naturellement plus grosses ou plus larges : « Pixel » doit être plus petite pour tenir à la même place).
    class Police(val cle: String, val nom: Int, val echelle: Float)

    const val CLASSIQUE = "classique"

    val LISTE = listOf(
        Police(CLASSIQUE, R.string.police_classique, 1f),
        Police("gothique", R.string.police_gothique, 1.15f),     // Pirata One : gothique lisible
        Police("fraktur", R.string.police_fraktur, 1.05f),       // UnifrakturMaguntia : vieille écriture allemande
        Police("horreur", R.string.police_horreur, 1.05f),       // Creepster : lettres qui dégoulinent
        Police("manuscrite", R.string.police_manuscrite, 0.95f), // Pacifico : écriture au pinceau
        Police("elegante", R.string.police_elegante, 0.95f),     // Cinzel : capitales gravées dans la pierre
        Police("futuriste", R.string.police_futuriste, 0.9f),    // Orbitron : science-fiction
        Police("pixel", R.string.police_pixel, 0.7f),            // Press Start 2P : jeux vidéo des années 80
    )

    fun police(cle: String): Police = LISTE.firstOrNull { it.cle == cle } ?: LISTE[0]

    // ------------------------------ LES FICHIERS ------------------------------

    // On change ce numéro quand le zip change : les polices sont alors ressorties du zip.
    private const val VERSION = 1

    private fun dossier(c: Context): File = File(c.filesDir, "polices")

    // Sort les polices du zip, UNE seule fois (« synchronized » : si deux écrans le demandent en même temps,
    // le second attend que le premier ait fini au lieu de tout faire en double).
    @Synchronized
    private fun preparer(c: Context) {
        val d = dossier(c)
        val marque = File(d, "version_$VERSION")
        if (marque.exists()) return
        try {
            d.deleteRecursively()
            d.mkdirs()
            c.assets.open("polices.zip").use { brut ->
                ZipInputStream(brut).use { zip ->
                    while (true) {
                        val entree = zip.nextEntry ?: break
                        val nom = File(entree.name).name            // (on ne garde que le nom : pas de sous-dossier)
                        if (!entree.isDirectory && nom.endsWith(".ttf")) {
                            File(d, nom).outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
            }
            marque.createNewFile()
        } catch (_: Exception) {
            // pas de zip dans l'app (ou zip abîmé) : on réessaiera à la prochaine version
        }
    }

    // Cette police peut-elle être utilisée ? (Classique : toujours ; les autres : si leur fichier est là.)
    fun disponible(c: Context, cle: String): Boolean {
        if (cle == CLASSIQUE) return true
        preparer(c)
        return File(dossier(c), "$cle.ttf").exists()
    }

    // Les polices proposées dans la fenêtre « Renommer » (seulement celles qu'on peut vraiment utiliser).
    fun proposees(c: Context): List<Police> = LISTE.filter { disponible(c, it.cle) }

    // Les polices déjà chargées (en lire une depuis son fichier prend un peu de temps : on la garde).
    private val memoire = HashMap<String, Typeface>()

    // La police à utiliser pour écrire. « gras » : la version grasse de la police du téléphone, pour Classique
    // (les autres polices n'ont qu'une épaisseur, déjà choisie pour être bien visible).
    @Synchronized
    fun typeface(c: Context, cle: String, gras: Boolean = true): Typeface {
        if (cle == CLASSIQUE || !disponible(c, cle))
            return if (gras) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
        return memoire.getOrPut(cle) {
            try { Typeface.createFromFile(File(dossier(c), "$cle.ttf")) } catch (_: Exception) { Typeface.DEFAULT_BOLD }
        }
    }

    // La police choisie pour un dossier, et son facteur de taille (CLASSIQUE si elle n'est plus disponible).
    fun cleDe(c: Context, groupe: String): String = Groupes.police(c, groupe).takeIf { disponible(c, it) } ?: CLASSIQUE
    fun echelle(c: Context, groupe: String): Float = police(cleDe(c, groupe)).echelle

    // ------------------------------ LE NOM SOUS LE WIDGET ------------------------------

    // Pose le nom du dossier sous un widget : en texte normal (police Classique), ou en IMAGE dessinée avec la
    // police choisie. Les layouts des widgets ont les deux (un TextView et un ImageView) : on montre l'un ou l'autre.
    fun poserNom(c: Context, v: RemoteViews, idTexte: Int, idImage: Int, groupe: String) {
        val nom = Groupes.categorie(groupe)?.nom ?: "?"
        val cle = cleDe(c, groupe)
        val image = if (cle == CLASSIQUE) null else try { imageNom(c, nom, cle) } catch (_: Exception) { null }
        if (image == null) {
            v.setTextViewText(idTexte, nom)
            v.setViewVisibility(idTexte, View.VISIBLE)
            v.setViewVisibility(idImage, View.GONE)
        } else {
            v.setImageViewBitmap(idImage, image)
            v.setContentDescription(idImage, nom)                 // (lu à voix haute par TalkBack)
            v.setViewVisibility(idImage, View.VISIBLE)
            v.setViewVisibility(idTexte, View.GONE)
        }
    }

    // Dessine « nom » dans une petite image transparente, comme le texte d'origine du widget : blanc, 13 sp,
    // avec la même ombre douce (pour rester lisible sur n'importe quel fond d'écran), coupé par « … » s'il est trop long.
    // L'image est dessinée en vrais pixels de l'écran : elle est aussi nette que du texte.
    private fun imageNom(c: Context, nom: String, cle: String): Bitmap {
        val m = c.resources.displayMetrics
        val ombre = 3f * m.density
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typeface(c, cle)
            textSize = 13f * m.scaledDensity * police(cle).echelle
            color = Color.WHITE
            setShadowLayer(ombre, 0f, 1f * m.density, Color.argb(0x99, 0, 0, 0))
        }
        val largeurMax = 96f * m.density                           // (à peu près la largeur d'une case de l'accueil)
        val texte = TextUtils.ellipsize(nom, p, largeurMax, TextUtils.TruncateAt.END).toString()
        // la hauteur vient de la police elle-même (Pacifico descend très bas, Fraktur monte haut…)
        val f = p.fontMetrics
        val marge = ombre + 1f * m.density
        val w = ceil(p.measureText(texte) + 2 * marge).toInt().coerceAtLeast(1)
        val h = ceil(f.descent - f.ascent + 2 * marge).toInt().coerceAtLeast(1)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(b).drawText(texte, marge, marge - f.ascent, p)
        return b
    }
}
