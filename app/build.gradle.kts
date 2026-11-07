// Recette de fabrication de l'app.
plugins {
    id("com.android.application") version "8.5.2"       // c'est une app Android
    id("org.jetbrains.kotlin.android") version "2.0.20"  // écrite en Kotlin
}

android {
    namespace = "com.will.animlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.will.animlauncher" // identifiant unique sur le téléphone
        minSdk = 26        // marche à partir d'Android 8
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    // Toujours la même clé de signature, pour installer
    // chaque nouvelle version par-dessus l'ancienne.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
