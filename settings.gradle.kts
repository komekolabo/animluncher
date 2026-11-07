// Où Gradle (l'outil qui compile l'app) va chercher ses outils et bibliothèques.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AnimLauncher"
include(":app") // le projet contient un seul module : l'app, dans le dossier "app"
