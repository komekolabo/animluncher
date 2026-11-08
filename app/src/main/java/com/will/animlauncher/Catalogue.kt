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
        "com.anthropic.aibo" to e(E) { Duo() },
        "com.openai.chatgpt" to e(E) { Souffle() },
        "com.google.android.apps.bard" to e(E) { Aurore() },
        "ai.x.grok" to e(E) { Singularite() },
    )

    fun pour(app: String): Entree? = entrees[app]
}
