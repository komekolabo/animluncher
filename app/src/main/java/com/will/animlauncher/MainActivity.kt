package com.will.animlauncher

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

// Une "Activity" = un écran de l'app.
class MainActivity : Activity() {

    // onCreate est appelée automatiquement par Android quand l'écran s'ouvre.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // On crée un texte directement dans le code.
        val texte = TextView(this).apply {
            text = "Salut Will"
            textSize = 32f                       // taille du texte
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1B2340"))
            gravity = Gravity.CENTER             // centré à l'écran
        }

        // Ce texte devient tout le contenu de l'écran.
        setContentView(texte)
    }
}
