package com.will.animlauncher

import android.app.Application
import android.content.Context

// L'« application » elle-même : Android la crée avant tout le reste (écrans et service).
// On en profite pour installer le filet anti-plantage du journal (voir Journal.kt).
class AnimApp : Application() {
    override fun onCreate() {
        super.onCreate()
        contexte = applicationContext
        Journal.installerFilet(this)
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
