package com.will.animlauncher

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// LE STYLE DE LA REFONTE (validée le 09/11) : noir, gris très foncé, accent blanc cassé.
// Un seul endroit pour les couleurs et les petits éléments (textes, boutons, interrupteurs),
// partagé par l'accueil (MainActivity), les réglages (ReglagesActivity) et les fonds (FondsActivity).
// Changer une couleur ici la change partout.
// (Les textes, eux, sont dans res/values*/strings.xml : un fichier par langue.)
object Style {
    val NOIR = Color.rgb(0, 0, 0)
    val CARTE = Color.rgb(14, 14, 16)        // le fond des cartes
    val BORD = Color.rgb(31, 31, 35)         // le fin contour des cartes
    val TEXTE = Color.rgb(237, 237, 237)     // le blanc cassé (textes, boutons principaux)
    val ENCRE = Color.rgb(10, 10, 10)        // le texte posé SUR le blanc cassé
    val MUET = Color.rgb(161, 161, 170)      // les textes secondaires
    val DOUX = Color.rgb(200, 200, 206)      // entre les deux
    val AMBRE = Color.rgb(242, 179, 107)     // les avertissements (détection coupée, pause)

    // Convertit des « dp » (unité indépendante de l'écran) en pixels.
    fun px(c: Context, dp: Float): Int = (dp * c.resources.displayMetrics.density).toInt()

    // Un rectangle arrondi d'une couleur, avec un contour si on veut.
    fun arrondi(c: Context, couleur: Int, rayonDp: Float, bord: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(couleur)
            cornerRadius = rayonDp * c.resources.displayMetrics.density
            if (bord != null) setStroke(px(c, 1f), bord)
        }

    // Un texte : sa taille (en sp), sa couleur, et s'il est en gras.
    fun texte(c: Context, t: String, taille: Float, couleur: Int, gras: Boolean = false): TextView =
        TextView(c).apply {
            text = t
            textSize = taille
            setTextColor(couleur)
            if (gras) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

    // Le bouton principal : blanc cassé, texte noir, bien arrondi, 50 dp de haut.
    fun boutonBlanc(c: Context, t: String, action: () -> Unit): Button = Button(c).apply {
        text = t
        isAllCaps = false                     // Android met les boutons en MAJUSCULES par défaut
        textSize = 15f
        setTextColor(ENCRE)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        background = arrondi(c, TEXTE, 16f)
        stateListAnimator = null              // pas d'ombre qui « saute » à l'appui
        minHeight = px(c, 50f)
        setOnClickListener { action() }
    }

    // Un interrupteur aux couleurs de la refonte : allumé = rond noir sur piste blanche,
    // éteint = rond gris sur piste gris foncé. (« ColorStateList » = une couleur par état.)
    fun interrupteur(c: Context): Switch = Switch(c).apply {
        val etats = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        thumbTintList = ColorStateList(etats, intArrayOf(ENCRE, Color.rgb(142, 142, 150)))
        trackTintList = ColorStateList(etats, intArrayOf(TEXTE, Color.rgb(42, 42, 46)))
    }

    // Barre d'état (en haut) et barre de navigation (en bas) en noir, comme l'écran.
    // (Sur Android 15 et plus, ces deux couleurs ne se règlent plus : c'est le fond de la fenêtre
    // qu'on voit derrière les barres. On le met donc en noir aussi.)
    fun fenetreNoire(a: Activity) {
        @Suppress("DEPRECATION")
        a.window.statusBarColor = NOIR
        @Suppress("DEPRECATION")
        a.window.navigationBarColor = NOIR
        a.window.setBackgroundDrawable(ColorDrawable(NOIR))
    }

    // ANDROID 15 ET PLUS : « BORD À BORD ». Une app qui vise Android 16 est dessinée sur TOUT
    // l'écran, y compris SOUS la barre d'état (l'heure, la batterie) et SOUS la barre de navigation.
    // Sans précaution, le haut de nos écrans passerait sous l'heure. Android nous dit la place
    // que prennent ces barres (les « insets ») : on écarte le contenu d'autant, et aussi de la
    // place du clavier quand il est ouvert (pour les écrans avec une recherche).
    // Appelée automatiquement pour chaque écran (voir AnimApp.kt), sauf le dossier animé, qui
    // dessine exprès sur tout l'écran.
    fun sousLesBarres(a: Activity) {
        if (android.os.Build.VERSION.SDK_INT < 35 || a is DossierActivity) return
        a.window.setBackgroundDrawable(ColorDrawable(NOIR))
        val contenu = a.findViewById<View>(android.R.id.content) ?: return
        contenu.setOnApplyWindowInsetsListener { v, insets ->
            val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        contenu.requestApplyInsets()
    }

    // ---------- les éléments communs des écrans noirs (apps, réglages d'une app, dossiers…) ----------

    // L'en-tête d'un écran : la flèche de retour à gauche, puis le titre.
    fun entete(a: Activity, titre: String): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(a, 4f), 0, px(a, 16f), 0)
        addView(Icone(a, Icone.RETOUR).apply {
            contentDescription = a.getString(R.string.retour)
            setOnClickListener { a.finish() }
        }, LinearLayout.LayoutParams(px(a, 48f), px(a, 48f)))
        addView(texte(a, titre, 20f, TEXTE, gras = true).apply { setPadding(px(a, 6f), 0, 0, 0) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(a, 64f))
    }

    // Une « carte » : un bloc arrondi gris très foncé, avec un fin contour, qui range son contenu
    // en colonne. « espaceDessous » : l'écart avec la carte suivante.
    fun carte(c: Context, espaceDessous: Float = 12f): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = arrondi(c, CARTE, 22f, BORD)
        setPadding(px(c, 18f), px(c, 16f), px(c, 18f), px(c, 16f))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = px(c, espaceDessous) }
    }

    // Le petit titre gris, en majuscules espacées, en haut d'une carte.
    fun titreCarte(c: Context, t: String): TextView = texte(c, t.uppercase(), 12f, MUET, gras = true).apply {
        letterSpacing = 0.08f
        setPadding(0, 0, 0, px(c, 10f))
    }

    // Le bouton secondaire : transparent, avec un contour, texte blanc cassé.
    fun boutonDiscret(c: Context, t: String, action: () -> Unit): Button = Button(c).apply {
        text = t
        isAllCaps = false
        textSize = 14f
        setTextColor(TEXTE)
        background = arrondi(c, Color.argb(20, 255, 255, 255), 14f, Color.rgb(63, 63, 70))
        stateListAnimator = null
        minHeight = px(c, 46f)
        minimumHeight = px(c, 46f)
        setPadding(px(c, 16f), 0, px(c, 16f), 0)
        setOnClickListener { action() }
    }
}

// UNE PETITE ICÔNE DESSINÉE (pas d'image) : les trois barres du menu, la roue des réglages,
// ou la flèche de retour. Elle se comporte comme un bouton (cercle d'appui à l'appui).
class Icone(context: Context, private val type: Int) : View(context) {
    companion object {
        const val MENU = 0
        const val ROUE = 1
        const val RETOUR = 2
    }

    private val dp = context.resources.displayMetrics.density
    private val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Style.TEXTE
    }

    init {
        isClickable = true
        isFocusable = true
        // l'effet d'appui standard d'Android (un cercle qui s'étend), sans cadre
        val a = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
        background = a.getDrawable(0)
        a.recycle()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val k = min(width, height) / 48f   // 1 unité = 1/48 du bouton (l'icône en occupe environ la moitié)
        when (type) {
            MENU -> for (dy in floatArrayOf(-5f, 0f, 5f)) {
                c.drawLine(cx - 8f * k, cy + dy * k, cx + 8f * k, cy + dy * k, trait)
            }
            ROUE -> {
                trait.strokeWidth = 1.8f * dp
                c.drawCircle(cx, cy, 3f * k, trait)                     // le moyeu
                c.drawCircle(cx, cy, 6.5f * k, trait)                   // la roue
                for (i in 0 until 8) {                                  // les 8 dents
                    val a = i * Math.PI / 4
                    c.drawLine(cx + (6.5f * k * cos(a)).toFloat(), cy + (6.5f * k * sin(a)).toFloat(),
                        cx + (9.5f * k * cos(a)).toFloat(), cy + (9.5f * k * sin(a)).toFloat(), trait)
                }
            }
            RETOUR -> {
                c.drawLine(cx + 3f * k, cy - 7f * k, cx - 4f * k, cy, trait)
                c.drawLine(cx - 4f * k, cy, cx + 3f * k, cy + 7f * k, trait)
            }
        }
    }
}
