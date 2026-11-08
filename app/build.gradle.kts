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

    // LES DEUX VERSIONS DE L'APP, fabriquées à partir du MÊME code (dossier src/main) :
    //  - « perso » : TA version. Même identifiant qu'avant (elle s'installe par-dessus l'actuelle,
    //    tes réglages sont gardés). Accessibilité complète, journal envoyé sur GitHub, mode standard.
    //  - « store » : la version pour le Play Store. Accessibilité allégée, écran d'explication,
    //    rien n'est envoyé sur Internet. Identifiant différent : les deux peuvent être installées ensemble.
    // Ce qui ne concerne qu'une version est rangé dans src/perso ou src/store.
    // Dans le code, BuildConfig.STORE vaut true seulement dans la version Store.
    buildFeatures {
        buildConfig = true
    }
    flavorDimensions += "version"
    productFlavors {
        create("perso") {
            dimension = "version"
            buildConfigField("boolean", "STORE", "false")
            resValue("string", "app_name", "AnimLauncher Perso")
        }
        create("store") {
            dimension = "version"
            // L'identifiant définitif sur le Play Store (modifiable tant que l'app n'est pas publiée).
            applicationIdSuffix = ".store"
            buildConfigField("boolean", "STORE", "true")
            resValue("string", "app_name", "AnimLauncher")
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

// COMPATIBILITÉ avec la compilation GitHub actuelle (.github/workflows/build.yml) :
// elle publie le fichier « outputs/apk/debug/app-debug.apk ». Avec deux versions, Android range
// les APK ailleurs (apk/perso/... et apk/store/...). On copie donc la version Perso à l'ancien
// endroit, pour que ton lien de téléchargement habituel continue de marcher.
val copierApkPerso = tasks.register<Copy>("copierApkPerso") {
    from(layout.buildDirectory.file("outputs/apk/perso/debug/app-perso-debug.apk"))
    into(layout.buildDirectory.dir("outputs/apk/debug"))
    rename { "app-debug.apk" }
}
tasks.configureEach {
    if (name == "assemblePersoDebug") finalizedBy(copierApkPerso)
}
