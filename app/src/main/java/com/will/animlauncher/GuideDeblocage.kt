package com.will.animlauncher

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.PI
import kotlin.math.sin

// ============================================================================
// LE GUIDE DE DÉBLOCAGE, en plein écran, pensé pour que TOUT LE MONDE y arrive.
//
// Pourquoi ? Une app installée en dehors du Play Store (un APK) apparaît GRISÉE dans
// l'accessibilité (« Contrôlé par les paramètres restreints »). Pour la débloquer, Android
// impose 3 étapes, dans cet ordre :
//   1. essayer de l'activer (Android refuse, et c'est ce refus qui débloque la suite) ;
//   2. autoriser les « paramètres restreints » dans les infos de l'app (menu ⋮) ;
//   3. l'activer pour de vrai.
//
// Le guide montre UNE étape à la fois, avec des DESSINS de ce qu'on va voir (voir la classe
// Schema plus bas) : l'endroit à toucher est entouré d'un cadre qui pulse doucement, avec un doigt.
// Un gros bouton ouvre le bon écran des Paramètres ; on revient avec la touche retour.
// Dès que l'accessibilité est activée, le guide affiche la confirmation tout seul.
// ============================================================================
class GuideDeblocage : Activity() {

    private var etape = 0                       // 0 = accueil, 1-3 = étapes, 4 = terminé
    private lateinit var contenu: LinearLayout
    private lateinit var boutonPrincipal: Button
    private lateinit var boutonSecond: Button
    private val d by lazy { resources.displayMetrics.density }
    private val nom by lazy { getString(R.string.app_name) }   // « AnimLauncher » ou « AnimLauncher Perso »

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        etape = savedInstanceState?.getInt("etape") ?: 0
        // L'écran : en haut, la zone qui défile (textes + dessins) ; en bas, les deux boutons.
        contenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (40 * d).toInt(), (20 * d).toInt(), (12 * d).toInt())
        }
        boutonPrincipal = Button(this).apply { textSize = 20f; isAllCaps = false }
        boutonSecond = Button(this).apply { textSize = 16f; isAllCaps = false }
        val bas = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (24 * d).toInt())
            setBackgroundColor(Color.parseColor("#141A30"))
            addView(boutonPrincipal, LinearLayout.LayoutParams(-1, (64 * d).toInt()))
            addView(boutonSecond, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (6 * d).toInt() })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B2340"))
            addView(ScrollView(this@GuideDeblocage).apply { addView(contenu) },
                LinearLayout.LayoutParams(-1, 0, 1f))
            addView(bas, LinearLayout.LayoutParams(-1, -2))
        })
        afficher()
    }

    override fun onSaveInstanceState(etat: Bundle) {
        super.onSaveInstanceState(etat)
        etat.putInt("etape", etape)
    }

    // Quand on revient des Paramètres : si l'accessibilité est activée, on affiche la confirmation.
    override fun onResume() {
        super.onResume()
        if (etape in 1..3 && serviceActif(this)) { etape = 4; afficher() }
    }

    // ---------------- LES ÉCRANS DU GUIDE ----------------
    private fun afficher() {
        contenu.removeAllViews()
        when (etape) {
            0 -> accueil()
            1 -> mission(1, "Première tentative",
                "Android refuse d'abord l'activation. Ce refus est attendu : il fait apparaître l'option de l'étape 2.",
                listOf(
                    "Touche « Applications installées »" to Schema.Ecran.ACCESSIBILITE,
                    "Touche « $nom » (grisé)" to Schema.Ecran.APPS_GRISE,
                    "Le message « L'accès a été refusé à cette appli » s'affiche : c'est attendu. Touche « Fermer »." to Schema.Ecran.REFUS
                ),
                "Ouvrir les réglages",
                "Touche « Applications installées », puis « $nom »") { ouvrirAccessibilite(this) }
            2 -> mission(2, "Autoriser les paramètres restreints",
                "Cette option se trouve dans les infos de l'app.",
                listOf(
                    "Touche le menu ⋮ en haut à droite" to Schema.Ecran.INFOS_APP,
                    "Touche « Autoriser les paramètres restreints », puis confirme (code ou empreinte)" to Schema.Ecran.MENU
                ),
                "Ouvrir les infos de l'app",
                "Touche le menu ⋮ en haut à droite") {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName")))
            }
            3 -> mission(3, "Activer le service",
                "$nom n'est plus grisé : il reste à l'activer.",
                listOf(
                    "Touche « Applications installées »" to Schema.Ecran.ACCESSIBILITE,
                    "Touche « $nom » (il n'est plus grisé)" to Schema.Ecran.APPS_OK,
                    "Active l'interrupteur" to Schema.Ecran.INTERRUPTEUR,
                    "Touche « Autoriser »" to Schema.Ecran.AUTORISER
                ),
                "Ouvrir les réglages",
                "Touche « Applications installées », puis « $nom »") { ouvrirAccessibilite(this) }
            else -> victoire()
        }
    }

    private fun texte(t: String, taille: Float, couleur: String = "#E8ECF7", haut: Int = 0, centre: Boolean = false) =
        TextView(this).apply {
            text = t; textSize = taille; setTextColor(Color.parseColor(couleur))
            setPadding(0, (haut * d).toInt(), 0, (8 * d).toInt())
            if (centre) gravity = Gravity.CENTER_HORIZONTAL
        }

    private fun accueil() {
        contenu.addView(LogoAnime(this), LinearLayout.LayoutParams((110 * d).toInt(), (110 * d).toInt()).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        contenu.addView(texte("Activer $nom", 26f, haut = 12, centre = true))
        contenu.addView(texte("Android restreint les apps installées en dehors du Play Store. " +
            "Trois étapes suffisent pour lever cette restriction.", 17f, "#B8C0DA", 8, true))
        contenu.addView(texte("Pour chaque étape :", 18f, haut = 20))
        contenu.addView(texte("1.  Le bouton en bas ouvre le bon écran des Paramètres.", 18f))
        contenu.addView(texte("2.  Touche l'élément encadré sur les illustrations.", 18f))
        contenu.addView(texte("3.  Reviens ici avec la touche retour ◁", 18f))
        contenu.addView(texte("Durée : environ 1 minute.", 16f, "#94A0C4", 12))
        boutonPrincipal.text = "Commencer"
        boutonPrincipal.setOnClickListener { etape = 1; afficher() }
        boutonSecond.visibility = View.GONE
    }

    // Une étape : un titre, une phrase, puis chaque action avec son dessin.
    private fun mission(numero: Int, titre: String, phrase: String, actions: List<Pair<String, Schema.Ecran>>,
                        texteBouton: String, rappel: String, aller: () -> Unit) {
        contenu.addView(texte("Étape $numero sur 3   " + "●".repeat(numero) + "○".repeat(3 - numero),
            15f, "#9A6EF5"))
        contenu.addView(texte(titre, 26f))
        contenu.addView(texte(phrase, 17f, "#B8C0DA"))
        val pastilles = listOf("①", "②", "③", "④")
        actions.forEachIndexed { i, (consigne, ecran) ->
            contenu.addView(texte("${pastilles[i]}  $consigne", 19f, haut = 16))
            contenu.addView(Schema(this, ecran, nom), LinearLayout.LayoutParams(-1, (190 * d).toInt()))
        }
        contenu.addView(texte("Une fois terminé, reviens ici avec la touche retour ◁", 17f, "#FFD27A", 18))
        boutonPrincipal.text = "$texteBouton →"
        boutonPrincipal.setOnClickListener {
            aller()
            Toast.makeText(this, rappel, Toast.LENGTH_LONG).show()   // un rappel par-dessus les Paramètres
        }
        boutonSecond.visibility = View.VISIBLE
        boutonSecond.text = if (numero < 3) "Étape suivante" else "Terminer"
        boutonSecond.setOnClickListener {
            if (numero < 3) { etape = numero + 1; afficher() }
            else if (serviceActif(this)) { etape = 4; afficher() }
            else Toast.makeText(this, "Le service n'est pas encore activé : voir les étapes ③ et ④.", Toast.LENGTH_LONG).show()
        }
    }

    private fun victoire() {
        contenu.addView(texte("✓", 64f, "#9A6EF5", haut = 40, centre = true))
        contenu.addView(texte("$nom est activé", 28f, centre = true))
        contenu.addView(texte("Ouvre une app depuis l'écran d'accueil pour voir l'animation.",
            18f, "#B8C0DA", 8, true))
        boutonPrincipal.text = "Fermer"
        boutonPrincipal.setOnClickListener { finish() }
        boutonSecond.visibility = View.GONE
    }

    companion object {
        // L'accessibilité d'AnimLauncher est-elle allumée ?
        fun serviceActif(c: Context): Boolean {
            val actifs = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return actifs.contains("${c.packageName}/")
        }

        // Ouvre l'accessibilité. Les « extras » demandent de mettre AnimLauncher en surbrillance
        // (ça marche sur certains téléphones, les autres les ignorent).
        fun ouvrirAccessibilite(c: Context) {
            val service = ComponentName(c, AppWatcherService::class.java).flattenToString()
            c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(":settings:fragment_args_key", service)
                .putExtra(":settings:show_fragment_args", Bundle().apply {
                    putString(":settings:fragment_args_key", service)
                }))
        }

        // Faut-il ce guide ? Seulement à partir d'Android 13, pour une app qui ne vient pas du Play Store.
        fun necessaire(c: Context): Boolean {
            if (Build.VERSION.SDK_INT < 33) return false
            val installeur = try {
                c.packageManager.getInstallSourceInfo(c.packageName).installingPackageName
            } catch (_: Exception) { null }
            return installeur != "com.android.vending"
        }
    }
}

// ============================================================================
// LES DESSINS DU GUIDE : un petit écran de téléphone simplifié, qui ressemble à ce qu'on va voir.
// L'endroit à toucher est entouré d'un cadre violet qui « respire » doucement, avec un doigt 👆.
// (Ce sont des dessins faits avec des rectangles et du texte, pas des captures d'écran.)
// ============================================================================
class Schema(context: Context, private val ecran: Ecran, private val nom: String) : View(context) {

    enum class Ecran { ACCESSIBILITE, APPS_GRISE, REFUS, INFOS_APP, MENU, APPS_OK, INTERRUPTEUR, AUTORISER }

    private val d = resources.displayMetrics.density
    private val debut = SystemClock.uptimeMillis()
    private val violet = context.getColor(R.color.logo_couleur)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var respire = 1f      // l'intensité du cadre (entre 0,55 et 1)

    override fun onDraw(c: Canvas) {
        val t = (SystemClock.uptimeMillis() - debut) / 1000f
        respire = 0.55f + 0.45f * (0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 1.6f))
        val w = width.toFloat(); val h = height.toFloat()
        // le cadre du téléphone
        p.style = Paint.Style.FILL; p.color = Color.parseColor("#0B0E18")
        c.drawRoundRect(RectF(0f, 0f, w, h), 16 * d, 16 * d, p)
        when (ecran) {
            Ecran.ACCESSIBILITE -> {
                titre(c, "Accessibilité")
                ligne(c, 0, "TalkBack")
                ligne(c, 1, "Interaction et dextérité")
                ligne(c, 2, "Applications installées", cible = true)
            }
            Ecran.APPS_GRISE -> {
                titre(c, "Applications installées")
                ligne(c, 0, nom, "Contrôlé par les paramètres restreints", gris = true, cible = true)
                ligne(c, 1, "Autre application")
            }
            Ecran.REFUS -> {
                titre(c, "Applications installées")
                ligne(c, 0, nom, gris = true)
                voile(c, w, h)
                val carte = RectF(14 * d, h * 0.30f, w - 14 * d, h - 10 * d)
                fond(c, carte, "#232A35")
                ecrire(c, "🛡  L'accès a été refusé à cette appli", carte.left + 14 * d, carte.top + 26 * d,
                    carte.width() - 28 * d, 14f, Color.WHITE)
                ecrire(c, "(message attendu)", carte.left + 14 * d, carte.top + 46 * d, carte.width(), 12f,
                    Color.parseColor("#FFD27A"))
                bouton(c, RectF(carte.left + 30 * d, carte.bottom - 40 * d, carte.right - 30 * d, carte.bottom - 10 * d),
                    "Fermer", cible = true)
            }
            Ecran.INFOS_APP -> {
                titre(c, nom)
                // les 3 points en haut à droite, entourés
                val cx = w - 26 * d; val cy = 24 * d
                p.style = Paint.Style.FILL; p.color = Color.WHITE
                for (k in -1..1) c.drawCircle(cx, cy + k * 6 * d, 2.2f * d, p)
                cadre(c, RectF(cx - 15 * d, cy - 16 * d, cx + 15 * d, cy + 16 * d), rond = true)
                doigt(c, cx - 34 * d, cy + 30 * d)
                ligne(c, 0, "Notifications")
                ligne(c, 1, "Autorisations")
                ligne(c, 2, "Stockage")
            }
            Ecran.MENU -> {
                titre(c, nom)
                ligne(c, 0, "Notifications")
                ligne(c, 1, "Autorisations")
                val menu = RectF(w * 0.28f, 10 * d, w - 8 * d, 10 * d + 84 * d)
                fond(c, menu, "#2A3142")
                val choix = RectF(menu.left + 4 * d, menu.top + 6 * d, menu.right - 4 * d, menu.top + 40 * d)
                ecrire(c, "Autoriser les paramètres restreints", choix.left + 10 * d, choix.centerY() + 5 * d,
                    choix.width() - 20 * d, 13f, Color.WHITE)
                cadre(c, choix)
                doigt(c, choix.left + 8 * d, choix.bottom + 22 * d)
                ecrire(c, "…", menu.left + 14 * d, menu.top + 66 * d, menu.width(), 13f, Color.parseColor("#7A8399"))
            }
            Ecran.APPS_OK -> {
                titre(c, "Applications installées")
                ligne(c, 0, nom, "Désactivé", cible = true)
                ligne(c, 1, "Autre application")
            }
            Ecran.INTERRUPTEUR -> {
                titre(c, nom)
                val r = ligne(c, 0, "Utiliser $nom")
                // l'interrupteur (éteint), entouré
                val inter = RectF(r.right - 58 * d, r.centerY() - 13 * d, r.right - 12 * d, r.centerY() + 13 * d)
                fond(c, inter, "#3A4256", rayon = 13 * d)
                p.color = Color.WHITE; c.drawCircle(inter.left + 13 * d, inter.centerY(), 9 * d, p)
                cadre(c, RectF(inter.left - 6 * d, inter.top - 6 * d, inter.right + 6 * d, inter.bottom + 6 * d))
                doigt(c, inter.left - 6 * d, inter.bottom + 30 * d)
                ligne(c, 1, "Raccourci")
            }
            Ecran.AUTORISER -> {
                titre(c, nom)
                ligne(c, 0, "Utiliser $nom")
                voile(c, w, h)
                val carte = RectF(14 * d, h * 0.22f, w - 14 * d, h - 10 * d)
                fond(c, carte, "#232A35")
                ecrire(c, "Autoriser $nom à contrôler l'appareil ?", carte.left + 14 * d, carte.top + 28 * d,
                    carte.width() - 28 * d, 14f, Color.WHITE)
                val milieu = carte.centerX()
                bouton(c, RectF(carte.left + 16 * d, carte.bottom - 42 * d, milieu - 8 * d, carte.bottom - 12 * d), "Refuser")
                bouton(c, RectF(milieu + 8 * d, carte.bottom - 42 * d, carte.right - 16 * d, carte.bottom - 12 * d),
                    "Autoriser", cible = true)
            }
        }
        postInvalidateOnAnimation()      // pour que le cadre continue de « respirer »
    }

    // --- petits outils de dessin ---
    private fun titre(c: Canvas, t: String) = ecrire(c, t, 14 * d, 30 * d, width - 60 * d, 17f, Color.parseColor("#A9C7F2"))

    // Une ligne de liste (comme dans les Paramètres). Renvoie son rectangle.
    private fun ligne(c: Canvas, i: Int, t: String, sous: String? = null, gris: Boolean = false, cible: Boolean = false): RectF {
        val haut = 48 * d + i * 46 * d
        val r = RectF(10 * d, haut, width - 10 * d, haut + 40 * d)
        fond(c, r, "#161B26")
        val couleur = if (gris) Color.parseColor("#5C6475") else Color.WHITE
        val place = r.width() - (if (cible) 60 else 24) * d      // on laisse la place du doigt à droite
        if (sous == null) ecrire(c, t, r.left + 12 * d, r.centerY() + 5 * d, place, 14f, couleur)
        else {
            ecrire(c, t, r.left + 12 * d, r.top + 17 * d, place, 13f, couleur)
            ecrire(c, sous, r.left + 12 * d, r.top + 33 * d, place, 10f, Color.parseColor("#5C6475"))
        }
        if (cible) { cadre(c, r); doigt(c, r.right - 40 * d, r.centerY() + 9 * d) }
        return r
    }

    private fun fond(c: Canvas, r: RectF, couleur: String, rayon: Float = 10 * d) {
        p.style = Paint.Style.FILL; p.color = Color.parseColor(couleur)
        c.drawRoundRect(r, rayon, rayon, p)
    }

    // Le cadre qui montre où toucher : violet, épais, qui « respire » (jamais de flash).
    private fun cadre(c: Canvas, r: RectF, rond: Boolean = false) {
        p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d
        p.color = violet; p.alpha = (255 * respire).toInt()
        if (rond) c.drawOval(r, p) else c.drawRoundRect(r, 10 * d, 10 * d, p)
        p.alpha = 255
    }

    private fun doigt(c: Canvas, x: Float, y: Float) = ecrire(c, "👆", x, y, 60 * d, 22f, Color.WHITE)

    private fun voile(c: Canvas, w: Float, h: Float) {
        p.style = Paint.Style.FILL; p.color = Color.argb(150, 0, 0, 0)
        c.drawRoundRect(RectF(0f, 0f, w, h), 16 * d, 16 * d, p)
    }

    private fun bouton(c: Canvas, r: RectF, t: String, cible: Boolean = false) {
        fond(c, r, if (cible) "#8DC8F2" else "#3A4256", rayon = r.height() / 2)
        p.style = Paint.Style.FILL; p.textSize = 14f * d; p.color = if (cible) Color.parseColor("#0B1A2E") else Color.WHITE
        c.drawText(t, r.centerX() - p.measureText(t) / 2, r.centerY() + 5 * d, p)
        if (cible) {
            cadre(c, RectF(r.left - 5 * d, r.top - 5 * d, r.right + 5 * d, r.bottom + 5 * d))
            doigt(c, r.right - 16 * d, r.bottom + 4 * d)
        }
    }

    // Écrit un texte ; s'il est trop long pour la place, on réduit sa taille jusqu'à ce qu'il tienne.
    private fun ecrire(c: Canvas, t: String, x: Float, y: Float, largeurMax: Float, taille: Float, couleur: Int) {
        p.style = Paint.Style.FILL; p.color = couleur
        var s = taille * d
        p.textSize = s
        while (p.measureText(t) > largeurMax && s > 7 * d) { s -= 0.5f * d; p.textSize = s }
        c.drawText(t, x, y, p)
    }
}
