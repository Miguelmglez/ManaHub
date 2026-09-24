package com.mmg.manahub.feature.decks.domain.inspirations

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.feature.decks.domain.model.CardCombo

/** How far the user's collection is from assembling a combo. */
enum class ComboReadiness { READY, ONE_AWAY, MORE_AWAY }

/** A combo plus the pieces the user does not own, in the combo's own piece order. */
data class OwnedComboView(
    val combo: CardCombo,
    val missingCardNames: List<String>,
) {
    val readiness: ComboReadiness
        get() = when (missingCardNames.size) {
            0 -> ComboReadiness.READY
            1 -> ComboReadiness.ONE_AWAY
            else -> ComboReadiness.MORE_AWAY
        }
}

object ComboOwnership {

    /** Lowercased full names plus front faces, so "Front // Back" in the collection matches a front-face-only piece. */
    fun ownedNameIndex(ownedCards: Collection<Card>): Set<String> =
        ownedCards.flatMap { card ->
            val full = card.name.lowercase()
            listOf(full, full.substringBefore(" // "))
        }.toSet()

    fun isOwned(cardName: String, ownedIndex: Set<String>): Boolean {
        val name = cardName.lowercase()
        return name in ownedIndex || name.substringBefore(" // ") in ownedIndex
    }

    fun view(combo: CardCombo, ownedIndex: Set<String>): OwnedComboView =
        OwnedComboView(combo, combo.cardNames.filterNot { isOwned(it, ownedIndex) })

    /** Ready first, then one piece away, then the rest; Spellbook's own order is kept inside each group. */
    fun group(views: List<OwnedComboView>): Map<ComboReadiness, List<OwnedComboView>> =
        ComboReadiness.entries.associateWith { readiness -> views.filter { it.readiness == readiness } }
}
