package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.domain.repository.CommanderSpellbookRepository
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.feature.decks.domain.model.ComboResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [FindCombosUseCase] (Deck Engine Unification plan D7, Phase 4.3) -- covers ONLY the input
 * shaping this use case owns (dedupe/cap/commander-exclusion); cache/API/degrade dispatch is
 * [CommanderSpellbookRepositoryImpl]'s job, covered by `CommanderSpellbookRepositoryImplTest`.
 */
class FindCombosUseCaseTest {

    private class FakeRepository(
        val result: DataResult<ComboResult> = DataResult.Success(ComboResult.EMPTY),
    ) : CommanderSpellbookRepository {
        var lastCardNames: List<String>? = null
        var lastCommanderNames: List<String>? = null
        var callCount = 0
        override suspend fun findCombos(cardNames: List<String>, commanderNames: List<String>): DataResult<ComboResult> {
            callCount++
            lastCardNames = cardNames
            lastCommanderNames = commanderNames
            return result
        }
    }

    @Test
    fun `both inputs empty returns EMPTY without calling the repository`() = runTest {
        val repo = FakeRepository()
        val useCase = FindCombosUseCase(repo)

        val result = useCase(cardNames = emptyList())

        assertEquals(DataResult.Success(ComboResult.EMPTY), result)
        assertEquals(0, repo.callCount)
    }

    @Test
    fun `blank and duplicate (case-insensitive) card names are collapsed before calling the repository`() = runTest {
        val repo = FakeRepository()
        val useCase = FindCombosUseCase(repo)

        useCase(cardNames = listOf("Sol Ring", "  ", "sol ring", "Basalt Monolith", ""))

        assertEquals(listOf("Basalt Monolith", "Sol Ring"), repo.lastCardNames)
    }

    @Test
    fun `card list is capped at 600 (Spellbook's documented DeckRequest_main limit)`() = runTest {
        val repo = FakeRepository()
        val useCase = FindCombosUseCase(repo)
        val names = (1..700).map { "Card $it" }

        useCase(cardNames = names)

        assertEquals(600, repo.lastCardNames?.size)
    }

    @Test
    fun `a commander name is excluded from the main card list even if it also appears there`() = runTest {
        val repo = FakeRepository()
        val useCase = FindCombosUseCase(repo)

        useCase(cardNames = listOf("Sol Ring", "Atraxa, Praetors' Voice"), commanderNames = listOf("Atraxa, Praetors' Voice"))

        assertEquals(listOf("Sol Ring"), repo.lastCardNames)
        assertEquals(listOf("Atraxa, Praetors' Voice"), repo.lastCommanderNames)
    }

    @Test
    fun `delegates the repository's result verbatim`() = runTest {
        val expected = DataResult.Success(ComboResult(complete = emptyList(), almostThere = emptyList()), isStale = true)
        val repo = FakeRepository(result = expected)
        val useCase = FindCombosUseCase(repo)

        val result = useCase(cardNames = listOf("Sol Ring"))

        assertTrue(result is DataResult.Success)
        assertEquals(expected, result)
    }
}
