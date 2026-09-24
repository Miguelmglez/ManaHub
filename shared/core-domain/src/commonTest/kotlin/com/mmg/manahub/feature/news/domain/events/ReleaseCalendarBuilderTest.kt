package com.mmg.manahub.feature.news.domain.events

import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.SetType
import com.mmg.manahub.core.model.SuggestedTag
import com.mmg.manahub.core.model.news.ReleaseStatus
import com.mmg.manahub.feature.news.domain.usecase.GetUpcomingReleasesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseCalendarBuilderTest {

    private val today = LocalDate(2026, 9, 24)

    private fun set(code: String, releasedAt: String?, type: SetType = SetType.EXPANSION) = MagicSet(
        code = code, name = "Set $code", setType = type, releasedAt = releasedAt, cardCount = 1, iconSvgUri = "",
    )

    @Test
    fun given_futureSets_then_theyAreUpcoming_soonestFirst_withTheirCountdown() {
        val calendar = ReleaseCalendarBuilder.build(listOf(set("b", "2026-10-10"), set("a", "2026-09-25")), today)

        assertEquals(listOf("a", "b"), calendar.releases.map { it.set.code })
        assertEquals(listOf(1, 16), calendar.releases.map { it.daysUntil })
        assertTrue(calendar.releases.all { it.status == ReleaseStatus.UPCOMING })
    }

    @Test
    fun given_setsReleasedWithinFourteenDays_then_theyAreOutNow_andOlderOnesAreDropped() {
        val calendar = ReleaseCalendarBuilder.build(
            listOf(set("today", "2026-09-24"), set("recent", "2026-09-10"), set("old", "2026-09-09")),
            today,
        )

        assertEquals(listOf("recent", "today"), calendar.releases.map { it.set.code })
        assertEquals(listOf(-14, 0), calendar.releases.map { it.daysUntil })
        assertTrue(calendar.releases.all { it.status == ReleaseStatus.OUT_NOW })
    }

    @Test
    fun given_manyOutNowSets_then_onlyTheThreeNewestAreKept_beforeUpcomingOnes() {
        val calendar = ReleaseCalendarBuilder.build(
            listOf(
                set("o1", "2026-09-15"), set("o2", "2026-09-16"), set("o3", "2026-09-17"), set("o4", "2026-09-18"),
                set("u1", "2026-10-01"),
            ),
            today,
        )

        assertEquals(listOf("o2", "o3", "o4", "u1"), calendar.releases.map { it.set.code })
    }

    @Test
    fun given_moreThanEightReleases_then_theListIsCapped() {
        val sets = (1..12).map { set("u$it", "2026-10-${(it + 10).toString().padStart(2, '0')}") }

        assertEquals(ReleaseCalendarBuilder.MAX_RELEASES, ReleaseCalendarBuilder.build(sets, today).releases.size)
    }

    @Test
    fun given_missingOrMalformedDates_then_thoseSetsAreSkipped() {
        val calendar = ReleaseCalendarBuilder.build(listOf(set("x", null), set("y", "soon"), set("z", "2026-09-30")), today)

        assertEquals(listOf("z"), calendar.releases.map { it.set.code })
    }

    @Test
    fun given_releasedExpansionsAndOtherTypes_then_theLatestLimitedSetIsTheNewestReleasedExpansion() {
        val calendar = ReleaseCalendarBuilder.build(
            listOf(
                set("fin", "2026-06-13"),
                set("eoe", "2026-08-01"),
                set("cmd", "2026-09-01", SetType.COMMANDER),
                set("next", "2026-11-14"),
            ),
            today,
        )

        assertEquals("eoe", calendar.latestLimitedSet?.code)
    }

    @Test
    fun given_noReleasedExpansion_then_thereIsNoLimitedSet() {
        assertNull(ReleaseCalendarBuilder.build(listOf(set("next", "2026-11-14")), today).latestLimitedSet)
    }

    @Test
    fun given_theSetListLoads_then_theUseCaseBuildsTheCalendarForToday() = runTest {
        val repository = FakeCardRepository(DataResult.Success(listOf(set("a", "2026-09-25"))))

        val calendar = GetUpcomingReleasesUseCase(repository, today = { today })().getOrThrow()

        assertEquals(1, calendar.releases.single().daysUntil)
    }

    @Test
    fun given_theSetListFails_then_theUseCaseFails() = runTest {
        val repository = FakeCardRepository(DataResult.Error("offline"))

        assertTrue(GetUpcomingReleasesUseCase(repository, today = { today })().isFailure)
    }
}

/** Minimal [CardRepository] fake serving only [getPlayableSets]. */
private class FakeCardRepository(
    private val setsResult: DataResult<List<MagicSet>>,
) : CardRepository {
    override suspend fun searchCardByName(query: String): DataResult<Card> = error("unused")
    override suspend fun searchCardPrintedName(name: String, lang: String): DataResult<Card> = error("unused")
    override suspend fun searchCards(query: String, page: Int, bypassCache: Boolean): DataResult<List<Card>> = error("unused")
    override suspend fun searchCardsPaginated(query: String, page: Int, bypassCache: Boolean): DataResult<com.mmg.manahub.core.model.PaginatedCards> = error("unused")
    override suspend fun getCardById(scryfallId: String): DataResult<Card> = error("unused")
    override suspend fun refreshCardById(scryfallId: String): DataResult<Card> = error("unused")
    override suspend fun backfillMissingOracleIds(limit: Int) = error("unused")
    override suspend fun backfillMissingStrategyTags(limit: Int) = error("unused")
    override suspend fun getCardBySetAndNumber(set: String, number: String): DataResult<Card> = error("unused")
    override suspend fun getCachedEnglishSiblings(pairs: Set<Pair<String, String>>): Map<Pair<String, String>, Card> = error("unused")
    override suspend fun getLanguagePrints(setCode: String, collectorNumber: String): DataResult<List<Card>> = error("unused")
    override suspend fun getPlayableSets(): DataResult<List<com.mmg.manahub.core.model.MagicSet>> = setsResult
    override suspend fun getCardPrints(name: String): DataResult<List<Card>> = error("unused")
    override suspend fun getCardArtVariants(name: String): DataResult<List<Card>> = error("unused")
    override suspend fun getCardByExactName(name: String): Result<Card> = error("unused")
    override suspend fun searchWithRawQuery(query: String, order: String?, page: Int): List<Card> = error("unused")
    override suspend fun getCardsByIds(scryfallIds: List<String>): List<Card> = error("unused")
    override fun observeCard(scryfallId: String): Flow<Card?> = flowOf(null)
    override suspend fun updatePrices(scryfallId: String, priceUsd: Double?, priceUsdFoil: Double?, priceEur: Double?, priceEurFoil: Double?, updatedAt: Long) = error("unused")
    override suspend fun updatePricesBatch(updates: List<com.mmg.manahub.core.domain.repository.CardPriceUpdate>) = error("unused")
    override suspend fun evictStaleCache() = error("unused")
    override suspend fun updateCardTags(scryfallId: String, tags: List<CardTag>) = error("unused")
    override suspend fun unionCardTags(scryfallId: String, tags: List<CardTag>) = error("unused")
    override suspend fun updateUserTags(scryfallId: String, userTags: List<CardTag>) = error("unused")
    override suspend fun updateSuggestedTags(scryfallId: String, suggestions: List<SuggestedTag>) = error("unused")
    override suspend fun confirmSuggestedTag(scryfallId: String, tag: CardTag) = error("unused")
    override suspend fun dismissSuggestedTag(scryfallId: String, tag: CardTag) = error("unused")
    override suspend fun warmCacheForIds(scryfallIds: List<String>) = error("unused")
}
