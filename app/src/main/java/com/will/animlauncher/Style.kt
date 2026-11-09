package com.will.animlauncher

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// LE STYLE DE LA REFONTE (validée le 09/11) : noir, gris très foncé, accent blanc cassé.
// Un seul endroit pour les couleurs et les petits éléments (textes, boutons, interrupteurs),
// partagé par l'accueil (MainActivity), les réglages (ReglagesActivity) et les fonds (FondsActivity).
// Changer une couleur ici la change partout.
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
    fun fenetreNoire(a: Activity) {
        a.window.statusBarColor = NOIR
        a.window.navigationBarColor = NOIR
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
