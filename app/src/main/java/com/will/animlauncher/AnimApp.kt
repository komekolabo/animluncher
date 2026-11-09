package com.will.animlauncher

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle

// L'« application » elle-même : Android la crée avant tout le reste (écrans et service).
// On en profite pour installer le filet anti-plantage du journal (voir Journal.kt).
class AnimApp : Application() {
    override fun onCreate() {
        super.onCreate()
        contexte = applicationContext
        Journal.installerFilet(this)
        // Android 15 et plus : chaque écran est dessiné « bord à bord » (jusque sous la barre d'état).
        // Plutôt que de corriger chaque écran un par un, on demande à Android de nous prévenir
        // chaque fois qu'un écran de l'app vient d'être créé, et on écarte son contenu des barres
        // (voir Style.sousLesBarres). Les 7 fonctions vides sont obligatoires : on ne s'en sert pas.
        if (Build.VERSION.SDK_INT >= 35) {
            registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
                override fun onActivityPostCreated(a: Activity, etat: Bundle?) = Style.sousLesBarres(a)
                override fun onActivityCreated(a: Activity, etat: Bundle?) {}
                override fun onActivityStarted(a: Activity) {}
                override fun onActivityResumed(a: Activity) {}
                override fun onActivityPaused(a: Activity) {}
                override fun onActivityStopped(a: Activity) {}
                override fun onActivitySaveInstanceState(a: Activity, etat: Bundle) {}
                override fun onActivityDestroyed(a: Activity) {}
            })
        }
    }

    companion object {
        // Le contexte de l'app, pour que les animations puissent écrire dans le journal.
        @Volatile var contexte: Context? = null
        fun noter(texte: String) {
            val c = contexte ?: return
            try { Journal.noter(c, texte); Journal.programmerEnvoi(c) } catch (_: Exception) {}
        }
    }
}
