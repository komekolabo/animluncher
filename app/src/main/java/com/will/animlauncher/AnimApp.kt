package com.will.animlauncher

import android.app.Application

// L'« application » elle-même : Android la crée avant tout le reste (écrans et service).
// On en profite pour installer le filet anti-plantage du journal (voir Journal.kt).
class AnimApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Journal.installerFilet(this)
    }
}
