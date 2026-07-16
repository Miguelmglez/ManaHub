package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.domain.repository.CommunityDecksRepository
import com.mmg.manahub.core.model.CommunityDeckOwner
import com.mmg.manahub.core.model.CommunityDeckSearchFilters
import com.mmg.manahub.core.model.CommunityDeckSearchResult
import com.mmg.manahub.core.model.CommunityDeckSummary
import com.mmg.manahub.core.model.DataResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun deckSummary(id: Int, name: String, colors: List<String>, viewCount: Int) = CommunityDeckSummary(
    archidektId = id,
    name = name,
    size = 100,
    format = "commander",
    owner = CommunityDeckOwner(id = 1, username = "owner-$id", avatarUrl = ""),
    viewCount = viewCount,
    createdAt = "",
    updatedAt = "",
    colorIdentity = colors,
)

private class FakeCommunityDecksRepository(
    private val result: DataResult<CommunityDeckSearchResult>,
) : CommunityDecksRepository {
    var lastQuery: String? = null
    override suspend fun getDeckById(archidektId: Int): DataResult<com.mmg.manahub.core.model.CommunityDeck> = error("unused")
    override suspend fun searchDecks(filters: CommunityDeckSearchFilters): DataResult<CommunityDeckSearchResult> {
        lastQuery = filters.cardName
        return result
    }
}

/**
 * Deck Doctor Community/Archetype plan, Phase 4 (Motor B) — commonTest coverage for
 * [FindSimilarDecksUseCase]. See the class KDoc for the documented scope deviation (color-identity
 * Jaccard is the ONLY similarity signal — the Worker's `/v1/similar` returns similar COMMANDER
 * NAMES, not decks with mainboards, and no per-deck card-list data exists anywhere else in the
 * contract to compute a true mainboard Jaccard against).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FindSimilarDecksUseCaseTest {

    @Test
    fun blankSeedQueryShortCircuitsToAnEmptySuccessWithoutCallingTheRepository() = runTest {
        val repo = FakeCommunityDecksRepository(DataResult.Success(CommunityDeckSearchResult(0, false, emptyList())))
        val useCase = FindSimilarDecksUseCase(communityDecksRepository = repo)

        val result = useCase(seedQuery = "  ", deckFormat = null, userColorIdentity = emptySet())

        assertEquals(DataResult.Success(emptyList()), result)
        assertEquals(null, repo.lastQuery)
    }

    @Test
    fun rankedByColorIdentityJaccardThenViewCount() = runTest {
        val decks = listOf(
            deckSummary(id = 1, name = "Exact Match", colors = listOf("U", "R"), viewCount = 10),
            deckSummary(id = 2, name = "Partial Match", colors = listOf("U"), viewCount = 999),
            deckSummary(id = 3, name = "No Match", colors = listOf("B", "G"), viewCount = 500),
        )
        val repo = FakeCommunityDecksRepository(DataResult.Success(CommunityDeckSearchResult(3, false, decks)))
        val useCase = FindSimilarDecksUseCase(communityDecksRepository = repo)

        val result = useCase(seedQuery = "Some Commander", deckFormat = 3, userColorIdentity = setOf("U", "R"))

        val ranked = (result as DataResult.Success).data
        assertEquals(listOf(1, 2, 3), ranked.map { it.archidektId })
        assertEquals(1f, ranked.first { it.archidektId == 1 }.colorSimilarity)
        assertTrue(ranked.first { it.archidektId == 3 }.colorSimilarity < ranked.first { it.archidektId == 2 }.colorSimilarity)
    }

    @Test
    fun viewCountBreaksATieInColorSimilarity() = runTest {
        val decks = listOf(
            deckSummary(id = 10, name = "Low Views", colors = listOf("G"), viewCount = 5),
            deckSummary(id = 11, name = "High Views", colors = listOf("G"), viewCount = 500),
        )
        val repo = FakeCommunityDecksRepository(DataResult.Success(CommunityDeckSearchResult(2, false, decks)))
        val useCase = FindSimilarDecksUseCase(communityDecksRepository = repo)

        val result = useCase(seedQuery = "Some Card", deckFormat = null, userColorIdentity = setOf("G"))

        val ranked = (result as DataResult.Success).data
        assertEquals(listOf(11, 10), ranked.map { it.archidektId })
    }

    @Test
    fun limitCapsTheResultCount() = runTest {
        val decks = (1..10).map { deckSummary(id = it, name = "Deck $it", colors = listOf("W"), viewCount = it) }
        val repo = FakeCommunityDecksRepository(DataResult.Success(CommunityDeckSearchResult(10, false, decks)))
        val useCase = FindSimilarDecksUseCase(communityDecksRepository = repo)

        val result = useCase(seedQuery = "Some Card", deckFormat = null, userColorIdentity = setOf("W"), limit = 3)

        assertEquals(3, (result as DataResult.Success).data.size)
    }

    @Test
    fun propagatesARepositoryError() = runTest {
        val repo = FakeCommunityDecksRepository(DataResult.Error("boom"))
        val useCase = FindSimilarDecksUseCase(communityDecksRepository = repo)

        val result = useCase(seedQuery = "Some Card", deckFormat = null, userColorIdentity = emptySet())

        assertTrue(result is DataResult.Error)
    }
}
