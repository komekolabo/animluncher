package com.will.animlauncher

// Une entrée du catalogue : la catégorie de durée, et de quoi fabriquer la scène.
// "() -> Scene" = une petite fonction qui crée une scène neuve à chaque ouverture.
class Entree(val categorie: Categorie, val fabrique: () -> Scene)

// LE CATALOGUE : quelle animation pour quelle app (nom technique -> entrée).
// Pour ajouter une app : une ligne de plus dans la liste.
object Catalogue {
    private val E = Categorie.ESSENTIELLE
    private val C = Categorie.COURANTE
    private val S = Categorie.SECONDAIRE

    private fun e(categorie: Categorie, fabrique: () -> Scene) = Entree(categorie, fabrique)

    private val entrees: Map<String, Entree> = mapOf(
        // --- Lot 1 : les IA ---
        "com.anthropic.claude" to e(E) { Horizon() },
        "com.anthropic.aibo" to e(E) { Lucioles() },
        "com.openai.chatgpt" to e(E) { Brume() },
        "com.google.android.apps.bard" to e(E) { Aurore() },
        "ai.x.grok" to e(E) { Singularite() },
        // --- Lot 2 : Google ---
        "com.google.android.googlequicksearchbox" to e(E) { Quatre() },
        "com.android.chrome" to e(E) { Tunnel() },
        "com.google.android.apps.maps" to e(E) { Itineraire() },
        "com.google.android.gm" to e(E) { Pli() },
        "com.google.android.apps.photos" to e(E) { PoseLongue() },
        "app.revanced.android.youtube" to e(E) { Cinema() },
        "com.google.android.apps.googleassistant" to e(C) { Ecoute() },
        "com.google.android.apps.docs" to e(C) { Prisme() },
        "com.google.android.apps.docs.editors.docs" to e(C) { Ecriture() },
        "com.google.android.apps.tachyon" to e(C) { Visio() },
        "com.google.android.apps.translate" to e(C) { Passage() },
        "com.google.earth" to e(C) { PlaneteBleue() },
        "com.google.android.apps.walletnfcrel" to e(C) { Eventail() },
        "com.google.android.play.games" to e(S) { Neon() },
        "com.google.android.videos" to e(S) { Salon() },
        "com.google.android.apps.authenticator2" to e(S) { Coffre() },
    )

    fun pour(app: String): Entree? = entrees[app]
}
