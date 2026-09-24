package com.mmg.manahub.feature.decks.domain.inspirations

import com.mmg.manahub.core.domain.repository.CommanderSpellbookRepository
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.model.CardCombo
import com.mmg.manahub.feature.decks.domain.model.CardComboPage
import com.mmg.manahub.feature.decks.domain.model.ComboResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CombosForCardTest {

    private fun combo(id: String, vararg names: String, commandZone: Boolean = false, legalities: Map<String, Boolean> = emptyMap()) =
        CardCombo(id, names.toList(), "desc", listOf("Infinite mana"), commandZone, legalities)

    private class FakeRepository(private val page: CardComboPage) : CommanderSpellbookRepository {
        override suspend fun findCombos(cardNames: List<String>, commanderNames: List<String>): DataResult<ComboResult> =
            DataResult.Success(ComboResult.EMPTY)

        override suspend fun findCombosWithCard(cardName: String, page: Int): DataResult<CardComboPage> =
            DataResult.Success(this.page)
    }

    @Test
    fun `command-zone combos and combos illegal in the format are dropped`() = runTest {
        val page = CardComboPage(
            combos = listOf(
                combo("ok", "A", "B"),
                combo("cz", "A", "C", commandZone = true),
                combo("banned", "A", "D", legalities = mapOf("modern" to false)),
                combo("legal", "A", "E", legalities = mapOf("modern" to true)),
            ),
            totalCount = 4,
            hasMore = false,
        )
        val useCase = FindCombosWithCardUseCase(FakeRepository(page))

        val modern = assertIs<DataResult.Success<CardComboPage>>(useCase("A", DeckFormat.MODERN, 0)).data
        val casual = assertIs<DataResult.Success<CardComboPage>>(useCase("A", DeckFormat.CASUAL, 0)).data

        assertEquals(listOf("ok", "legal"), modern.combos.map { it.id })
        assertEquals(listOf("ok", "banned", "legal"), casual.combos.map { it.id })
    }

    @Test
    fun `ownership matches full names and front faces`() {
        val index = ComboOwnership.ownedNameIndex(listOf(card(name = "Delver of Secrets // Insectile Aberration"), card(name = "Sol Ring")))

        val view = ComboOwnership.view(combo("v", "Delver of Secrets", "sol ring", "Brainstorm"), index)

        assertEquals(listOf("Brainstorm"), view.missingCardNames)
        assertEquals(ComboReadiness.ONE_AWAY, view.readiness)
    }

    @Test
    fun `grouping orders ready, one away, then the rest`() {
        val index = ComboOwnership.ownedNameIndex(listOf(card(name = "A"), card(name = "B")))
        val views = listOf(
            ComboOwnership.view(combo("far", "A", "X", "Y"), index),
            ComboOwnership.view(combo("ready", "A", "B"), index),
            ComboOwnership.view(combo("near", "B", "X"), index),
        )

        val grouped = ComboOwnership.group(views)

        assertEquals(listOf("ready"), grouped.getValue(ComboReadiness.READY).map { it.combo.id })
        assertEquals(listOf("near"), grouped.getValue(ComboReadiness.ONE_AWAY).map { it.combo.id })
        assertEquals(listOf("far"), grouped.getValue(ComboReadiness.MORE_AWAY).map { it.combo.id })
    }
}
