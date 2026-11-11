package com.will.animlauncher

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch

// POUR QUELLES APPLICATIONS LE TOUR LUMINEUX S'ALLUME (11/11, demandé par Will : « il s'active à plein de
// moments où il n'y a pas trop de raison, par exemple Duolingo ; il faudrait qu'il soit actif seulement pour
// les applications qui sortent de la musique, avec une fenêtre pour en activer d'autres »).
//
// LE PROBLÈME : Android nous dit « un son joue », mais pas QUELLE app le joue (c'est réservé au système).
// L'ASTUCE : notre service sait quelle app est à l'écran. Quand un son DÉMARRE (après au moins 3 secondes de
// silence), on retient l'app qui est à l'écran à ce moment-là : c'est très probablement elle qui joue.
//   - cette app est cochée            -> le tour s'allume, et il reste allumé tant que le son continue,
//                                        même si on change d'app (la musique continue en arrière-plan) ;
//   - cette app n'est pas cochée      -> le tour reste éteint (les petits sons de Duolingo, d'un jeu…) ;
//   - on est sur l'accueil ou l'écran est éteint -> on ne sait pas d'où vient le son (un widget, une
//     notification, le bouton du casque) : on laisse le tour s'allumer ;
//   - le son joue déjà et on ouvre une app cochée -> on considère que c'est elle maintenant.
// LIMITE : si on relance une musique depuis les notifications pendant qu'une app NON cochée est à l'écran,
// on croit que le son vient de cette app, et le tour reste éteint jusqu'au prochain silence.
//
// LES APPS COCHÉES DE BASE : celles du groupe « Music & Video » des dossiers animés (Groupes.kt : notre liste
// d'apps connues, sinon la catégorie « audio » ou « vidéo » que l'app déclare à Android). Tout le reste est
// décoché. Les choix de l'utilisateur sont rangés dans le carnet « son_apps » (une ligne par app changée).
// Un interrupteur « Toutes les applications » remet le comportement d'avant (le tour s'allume pour tout).
object AppsSon {
    private const val CARNET = "son_apps"
    private const val PARTOUT = "*partout"

    private var source: String? = null           // l'app d'où vient (probablement) le son en cours ; null = on ne sait pas
    private var sourceConnue = false             // faux = le prochain son qui démarre devra être attribué
    private var silences = 99                    // depuis combien de secondes aucun son ne joue
    @Volatile var permisMaintenant = true        // la dernière réponse de « permis »
        private set
    private val defauts = HashMap<String, Boolean>()     // (mémoire : cette app est-elle cochée de base ?)

    private fun carnet(c: Context) = c.applicationContext.getSharedPreferences(CARNET, Context.MODE_PRIVATE)

    fun partout(c: Context): Boolean = carnet(c).getBoolean(PARTOUT, false)
    fun definirPartout(c: Context, valeur: Boolean) { carnet(c).edit().putBoolean(PARTOUT, valeur).apply() }

    // Cette app est-elle cochée de base ? (oui pour les apps de musique et de vidéo)
    private fun parDefaut(c: Context, pkg: String): Boolean = defauts.getOrPut(pkg) {
        try {
            val code = try { c.packageManager.getApplicationInfo(pkg, 0).category } catch (_: Exception) { ApplicationInfo.CATEGORY_UNDEFINED }
            Groupes.trouver(pkg, code, Groupes.nomApp(c, pkg))?.id == "musique"
        } catch (_: Throwable) { false }
    }

    // Le tour s'allume-t-il pour cette app ? (le choix de l'utilisateur s'il en a fait un, sinon le réglage de base)
    fun autorisee(c: Context, pkg: String): Boolean {
        if (pkg == c.packageName) return true                    // (AnimLauncher lui-même : toujours)
        val k = carnet(c)
        return if (k.contains(pkg)) k.getBoolean(pkg, false) else parDefaut(c, pkg)
    }

    fun definir(c: Context, pkg: String, valeur: Boolean) { carnet(c).edit().putBoolean(pkg, valeur).apply() }

    // Appelé UNE FOIS PAR SECONDE par le tour lumineux. « musique » = Android dit qu'un son joue.
    // Répond : le tour a-t-il le droit de s'allumer ?
    fun permis(c: Context, musique: Boolean): Boolean {
        if (!musique) {
            silences++
            if (silences >= 3) sourceConnue = false              // 3 s de silence : le prochain son sera un nouveau son
            return permisMaintenant
        }
        silences = 0
        val devant = Usage.devant                                // l'app à l'écran (null = accueil, écran éteint)
        if (!sourceConnue) {
            source = devant                                      // un son démarre : il vient sûrement de l'app à l'écran
            sourceConnue = true
        } else if (devant != null && devant != source && autorisee(c, devant)) {
            source = devant                                      // le son joue déjà et on ouvre une app cochée
        }
        val s = source
        permisMaintenant = partout(c) || s == null || autorisee(c, s)
        return permisMaintenant
    }

    // LA FENÊTRE « APPLICATIONS » : la liste des apps du téléphone, chacune avec son interrupteur.
    // Les apps cochées sont en haut, puis les autres, par ordre alphabétique.
    fun ouvrir(a: Activity) {
        fun px(v: Float) = Style.px(a, v)
        val dialogue = Dialog(a)
        dialogue.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val colonne = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = Style.arrondi(a, Color.rgb(16, 16, 19), 28f, Style.BORD)
            setPadding(px(20f), px(18f), px(20f), px(14f))
        }
        colonne.addView(Style.texte(a, a.getString(R.string.son_apps), 21f, Style.TEXTE, gras = true))
        colonne.addView(Style.texte(a, a.getString(R.string.son_apps_expl), 13f, Style.MUET).apply {
            setLineSpacing(0f, 1.2f); setPadding(0, px(6f), 0, px(12f))
        })

        // toutes les apps qui ont une icône, avec leur nom ; les cochées d'abord
        val pm = a.packageManager
        val apps = try {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }.distinct().filter { it != a.packageName }
                .map { Triple(it, Groupes.nomApp(a, it), autorisee(a, it)) }
                .sortedWith(compareBy<Triple<String, String, Boolean>>({ !it.third }, { it.second.lowercase() }))
        } catch (_: Throwable) { emptyList() }

        // la liste des apps (dans sa propre colonne, pour pouvoir la griser d'un coup)
        val liste = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val boutons = ArrayList<Switch>()
        for ((pkg, nom, coche) in apps) {
            val bouton = Style.interrupteur(a).apply {
                isChecked = coche
                setOnCheckedChangeListener { _, valeur -> definir(a, pkg, valeur) }
            }
            boutons.add(bouton)
            liste.addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(7f), 0, px(7f))
                addView(ImageView(a).apply {
                    try { setImageDrawable(pm.getApplicationIcon(pkg)) } catch (_: Throwable) {}
                }, LinearLayout.LayoutParams(px(34f), px(34f)))
                addView(Style.texte(a, nom, 15f, Style.TEXTE).apply { setPadding(px(12f), 0, px(8f), 0); maxLines = 1 },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(bouton)
                setOnClickListener { bouton.toggle() }
            })
        }
        // « Toutes les applications » : coché, la liste ne sert plus (on la grise)
        fun griser(tout: Boolean) {
            liste.alpha = if (tout) 0.35f else 1f
            for (b in boutons) b.isEnabled = !tout
        }
        val toutes = Style.interrupteur(a).apply {
            isChecked = partout(a)
            setOnCheckedChangeListener { _, valeur -> definirPartout(a, valeur); griser(valeur) }
        }
        colonne.addView(LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Style.arrondi(a, Color.argb(14, 255, 255, 255), 14f, Style.BORD)
            setPadding(px(14f), px(9f), px(10f), px(9f))
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                addView(Style.texte(a, a.getString(R.string.son_apps_toutes), 15f, Style.TEXTE, gras = true))
                addView(Style.texte(a, a.getString(R.string.son_apps_toutes_desc), 12.5f, Style.MUET))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(toutes)
            setOnClickListener { toutes.toggle() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = px(8f) })
        griser(partout(a))
        colonne.addView(liste)
        colonne.addView(Style.texte(a, a.getString(R.string.son_fermer), 14f, Style.MUET).apply {
            gravity = Gravity.CENTER
            setPadding(0, px(14f), 0, px(6f))
            setOnClickListener { dialogue.dismiss() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        dialogue.setContentView(ScrollView(a).apply { addView(colonne) })
        dialogue.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // presque toute la hauteur de l'écran, collée en bas (la liste défile à l'intérieur)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (a.resources.displayMetrics.heightPixels * 0.82f).toInt())
            setGravity(Gravity.BOTTOM)
            setWindowAnimations(android.R.style.Animation_InputMethod)
            setDimAmount(0.7f)
        }
        dialogue.show()
    }
}
