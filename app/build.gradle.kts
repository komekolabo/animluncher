// Recette de fabrication de l'app.
plugins {
    id("com.android.application") version "8.5.2"       // c'est une app Android
    id("org.jetbrains.kotlin.android") version "2.0.20"  // écrite en Kotlin
}

// Numéro de compilation fourni par GitHub (1, 2, 3...). 0 si on compile ailleurs.
// Il devient le numéro de version de l'app : on sait ainsi quelle version est installée.
val numeroCompilation = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toInt()

android {
    namespace = "com.will.animlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.will.animlauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = numeroCompilation.coerceAtLeast(1)
        versionName = "0.$numeroCompilation"
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
