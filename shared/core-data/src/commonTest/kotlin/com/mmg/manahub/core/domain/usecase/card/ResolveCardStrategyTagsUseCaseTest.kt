package com.mmg.manahub.core.domain.usecase.card

import com.mmg.manahub.core.data.remote.edhrec.EdhrecCardTagEnrichmentSourceContract
import com.mmg.manahub.core.data.tagging.StrategyAnalyzer
import com.mmg.manahub.core.data.tagging.TagDictionary
import com.mmg.manahub.core.data.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.domain.repository.CardStrategyTagsRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsResult
import com.mmg.manahub.core.domain.repository.CardStrategyTagsSubmission
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DetectionRule
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.model.TagDictionaryEntry
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coverage for [ResolveCardStrategyTagsUseCase] (Deck Engine Unification plan §8a addendum) —
 * STRICT FALLBACK semantics (superseded RUN 6's union): a precomputed [CardStrategyTagsResult
 * .Found] is used EXCLUSIVELY (zero on-device computation, zero suggested tags); a genuine miss
 * runs the on-device engine, attempts a bounded EDHREC theme-page shortlist verification, and
 * pushes the result back via [CardStrategyTagsRepository.submitStrategyTags].
 */
class ResolveCardStrategyTagsUseCaseTest {

    private val computeCardTags = ComputeCardTagsUseCase(
        SuggestTagsUseCase(StrategyAnalyzer(entriesProvider = { TagDictionary.all() })),
    )

    /** A minimal, fully-controlled analyzer whose single rule lands in the "suggested, not
     *  auto-confirmed" band (0.60 <= confidence < 0.90) — deliberately NOT reusing the real
     *  production `TagDictionary` here, since hand-guessing which real oracle text lands a real
     *  rule at exactly the suggested confidence band is fragile (see
     *  `feedback_deck_engine_unification_motor_a_floor_fixtures` memory: never rely on
     *  hand-calibrated score fixtures against the real ruleset). "blink" is chosen because it has
     *  a real entry in [com.mmg.manahub.core.data.remote.edhrec.CARD_TAG_KEY_TO_THEME_ID]
     *  (-> [ThemeId.BLINK]). */
    private val computeCardTagsWithSuggestedBlink = ComputeCardTagsUseCase(
        SuggestTagsUseCase(
            StrategyAnalyzer(
                entriesProvider = {
                    listOf(
                        TagDictionaryEntry(
                            key = "blink",
                            category = TagCategory.STRATEGY,
                            labels = mapOf("en" to "Blink/ETB"),
                            rules = listOf(DetectionRule(allOf = listOf("exile target creature"))),
                            baseConfidence = 0.70f,
                        )
                    )
                },
            ),
        ),
    )

    private class FakeRepository(
        private val result: CardStrategyTagsResult,
    ) : CardStrategyTagsRepository {
        var callCount = 0
        var submitCallCount = 0
        var lastSubmission: CardStrategyTagsSubmission? = null

        override suspend fun getStrategyTags(oracleId: String): CardStrategyTagsResult {
            callCount++
            return result
        }

        override suspend fun getStrategyTagsBatch(oracleIds: Set<String>): Map<String, CardStrategyTagsResult> =
            oracleIds.associateWith { result }

        override suspend fun submitStrategyTags(oracleId: String, submission: CardStrategyTagsSubmission) {
            submitCallCount++
            lastSubmission = submission
        }
    }

    private class ThrowingRepository : CardStrategyTagsRepository {
        var submitCallCount = 0
        override suspend fun getStrategyTags(oracleId: String): CardStrategyTagsResult =
            throw IllegalStateException("offline")
        override suspend fun getStrategyTagsBatch(oracleIds: Set<String>): Map<String, CardStrategyTagsResult> =
            throw IllegalStateException("offline")
        override suspend fun submitStrategyTags(oracleId: String, submission: CardStrategyTagsSubmission) {
            submitCallCount++
        }
    }

    /** Fixed behavior lambda (never a nullable-with-`?:` fallback — see
     *  `project_deck_engine_unification_run6_read_path` memory for why that pattern is a trap). */
    private class FakeEdhrecEnrichment(
        val behavior: (String, List<ThemeId>) -> Map<ThemeId, Float> = { _, _ -> emptyMap() },
    ) : EdhrecCardTagEnrichmentSourceContract {
        var callCount = 0
        var lastCandidates: List<ThemeId> = emptyList()
        override suspend fun confirmThemes(cardName: String, candidates: List<ThemeId>): Map<ThemeId, Float> {
            callCount++
            lastCandidates = candidates
            return behavior(cardName, candidates)
        }
    }

    private class ThrowingEdhrecEnrichment : EdhrecCardTagEnrichmentSourceContract {
        override suspend fun confirmThemes(cardName: String, candidates: List<ThemeId>): Map<ThemeId, Float> =
            throw IllegalStateException("EDHREC down")
    }

    private fun testCard(
        oracleId: String = "",
        oracleText: String? = "",
        name: String = "Test Card",
    ) = Card(
        scryfallId = "id-1",
        name = name,
        printedName = null,
        manaCost = "{1}",
        cmc = 1.0,
        colors = emptyList(),
        colorIdentity = emptyList(),
        typeLine = "Creature — Test",
        printedTypeLine = null,
        oracleText = oracleText,
        printedText = null,
        keywords = emptyList(),
        power = null,
        toughness = null,
        loyalty = null,
        setCode = "tst",
        setName = "Test Set",
        collectorNumber = "1",
        rarity = "common",
        releasedAt = "2026-01-01",
        frameEffects = emptyList(),
        promoTypes = emptyList(),
        lang = "en",
        imageNormal = null,
        imageArtCrop = null,
        imageBackNormal = null,
        priceUsd = null,
        priceUsdFoil = null,
        priceEur = null,
        priceEurFoil = null,
        legalityStandard = "not_legal",
        legalityPioneer = "not_legal",
        legalityModern = "not_legal",
        legalityCommander = "not_legal",
        flavorText = null,
        artist = null,
        scryfallUri = "https://scryfall.com/card/tst/1",
        oracleId = oracleId,
    )

    // ── Blank oracleId ───────────────────────────────────────────────────────

    @Test
    fun `given a blank oracleId when invoke then repository and EDHREC are never called`() = runTest {
        val repo = FakeRepository(CardStrategyTagsResult.Found(tags = listOf(CardTag.RAMP)))
        val edhrec = FakeEdhrecEnrichment()
        val useCase = ResolveCardStrategyTagsUseCase(computeCardTags, repo, edhrec)

        val result = useCase(testCard(oracleId = ""), existingTagsJson = null)

        assertEquals(0, repo.callCount)
        assertEquals(0, repo.submitCallCount)
        assertEquals(0, edhrec.callCount)
        assertTrue(CardTag.RAMP !in result.confirmedTags)
    }

    // ── STRICT FALLBACK: precomputed hit ────────────────────────────────────

    @Test
    fun `given the repository finds a precomputed row then it is used EXCLUSIVELY and no suggestions are produced`() = runTest {
        val repo = FakeRepository(CardStrategyTagsResult.Found(tags = listOf(CardTag.REMOVAL)))
        val edhrec = FakeEdhrecEnrichment()
        val useCase = ResolveCardStrategyTagsUseCase(computeCardTagsWithSuggestedBlink, repo, edhrec)
        val card = testCard(oracleId = "oracle-1", oracleText = "Exile target creature.")

        val result = useCase(card, existingTagsJson = null)

        assertEquals(listOf(CardTag.REMOVAL), result.confirmedTags)
        // Zero on-device computation on a hit: the fixture rule that WOULD have produced a
        // suggested "blink" tag never ran, and EDHREC/write-back are never invoked either.
        assertTrue(result.suggestedTags.isEmpty())
        assertEquals(0, edhrec.callCount)
        assertEquals(0, repo.submitCallCount)
    }

    @Test
    fun `given a precomputed hit and existing user-confirmed tags then both are preserved (never a discard)`() = runTest {
        val repo = FakeRepository(CardStrategyTagsResult.Found(tags = listOf(CardTag.REMOVAL)))
        val edhrec = FakeEdhrecEnrichment()
        val useCase = ResolveCardStrategyTagsUseCase(computeCardTags, repo, edhrec)
        val existingTagsJson = """[{"k":"my_custom_tag","c":"CUSTOM"}]"""

        val result = useCase(testCard(oracleId = "oracle-2"), existingTagsJson = existingTagsJson)

        assertTrue(CardTag.REMOVAL in result.confirmedTags)
        assertTrue(result.confirmedTags.any { it.key == "my_custom_tag" })
    }

    // ── STRICT FALLBACK: genuine miss (NotFound / Error) ────────────────────

    @Test
    fun `given the repository returns NotFound then the on-device engine runs and the result is written back`() = runTest {
        val repo = FakeRepository(CardStrategyTagsResult.NotFound)
        val edhrec = FakeEdhrecEnrichment()
        val useCase = ResolveCardStrategyTagsUseCase(computeCardTags, repo, edhrec)

        val onDeviceOnly = computeCardTags(testCard(oracleId = "oracle-3"), existingTagsJson = null)
        val result = useCase(testCard(oracleId = "oracle-3"), existingTagsJson = null)

        assertEquals(onDeviceOnly.confirmedTags, result.confirmedTags)
        assertEquals(1, repo.submitCallCount)
        assertEquals(result.confirmedTags.map { it.key }, repo.lastSubmission?.tags)
    }

    @Test
    fun `given a throwing repository lookup then the on-device result is returned and a write-back is still attempted`() = runTest {
        val repo = ThrowingRepository()
        val edhrec = FakeEdhrecEnrichment()
        val useCase = ResolveCardStrategyTagsUseCase(computeCardTags, repo, edhrec)

        val onDeviceOnly = computeCardTags(testCard(oracleId = "oracle-4"), existingTagsJson = null)
        val result = useCase(testCard(oracleId = "oracle-4"), existingTagsJson = null)

        assertEquals(onDeviceOnly.confirmedTags, result.confirmedTags)
        assertEquals(1, repo.submitCallCount)
    }

    // ── EDHREC shortlist promotion ───────────────────────────────────────────

    @Test
    fun `given EDHREC confirms a suggested tag's theme then it is promoted to confirmed and removed from suggestions`() = runTest {
        val repo = FakeRepository(CardStrategyTagsResult.NotFound)
        val edhrec = FakeEdhrecEnrichment(behavior = { _, _ -> mapOf(ThemeId.BLINK to 0.42f) })
        val useCase = ResolveCardStrategyTagsUseCase(computeCardTagsWithSuggestedBlink, repo, edhrec)
        val card = testCard(oracleId = "oracle-5", oracleText = "Exile target creature.")

        // Sanity: without EDHREC, "blink" would only ever be a suggestion, never confirmed.
        val withoutEdhrec = computeCardTagsWithSuggestedBlink(card, existingTagsJson = null)
        assertTrue(withoutEdhrec.suggestedTags.any { it.tag.key == "blink" })
        assertTrue(withoutEdhrec.confirmedTags.none { it.key == "blink" })

        val result = useCase(card, existingTagsJson = null)

        assertTrue(result.confirmedTags.any { it.key == "blink" }, "EDHREC-confirmed theme must be promoted")
        assertTrue(result.suggestedTags.none { it.tag.key == "blink" }, "promoted tag must leave the suggested bucket")
        assertEquals(1, edhrec.callCount)
        assertEquals(listOf(ThemeId.BLINK), edhrec.lastCandidates)
        assertEquals(mapOf("BLINK" to 0.42f), repo.lastSubmission?.themes)
    }

    @Test
    fun `given EDHREC finds nothing then the suggested tag is left untouched and themes write-back is empty`() = runTest {
        val repo = FakeRepository(CardStrategyTagsResult.NotFound)
        val edhrec = FakeEdhrecEnrichment(behavior = { _, _ -> emptyMap() })
        val useCase = ResolveCardStrategyTagsUseCase(computeCardTagsWithSuggestedBlink, repo, edhrec)
        val card = testCard(oracleId = "oracle-6", oracleText = "Exile target creature.")

        val result = useCase(card, existingTagsJson = null)

        assertTrue(result.suggestedTags.any { it.tag.key == "blink" })
        assertTrue(result.confirmedTags.none { it.key == "blink" })
        assertEquals(emptyMap(), repo.lastSubmission?.themes)
    }

    @Test
    fun `given a throwing EDHREC enrichment then the on-device result is returned and write-back still fires`() = runTest {
        val repo = FakeRepository(CardStrategyTagsResult.NotFound)
        val useCase = ResolveCardStrategyTagsUseCase(computeCardTagsWithSuggestedBlink, repo, ThrowingEdhrecEnrichment())
        val card = testCard(oracleId = "oracle-7", oracleText = "Exile target creature.")

        val result = useCase(card, existingTagsJson = null)

        assertTrue(result.suggestedTags.any { it.tag.key == "blink" }, "EDHREC failure must not swallow the on-device suggestion")
        assertEquals(1, repo.submitCallCount)
    }

    @Test
    fun `given a suggested tag with no CARD_TAG_KEY_TO_THEME_ID mapping then EDHREC is called with an empty candidate list`() = runTest {
        val repo = FakeRepository(CardStrategyTagsResult.NotFound)
        val edhrec = FakeEdhrecEnrichment()
        // The real production dictionary/analyzer — its suggested tags (if any, on empty oracle
        // text there are none) never include a key absent from the curated bridge table.
        val useCase = ResolveCardStrategyTagsUseCase(computeCardTags, repo, edhrec)

        useCase(testCard(oracleId = "oracle-8", oracleText = ""), existingTagsJson = null)

        assertTrue(edhrec.lastCandidates.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  resolveWithPrefetched — bulk hydration entry point (2026-09-07)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a prefetched Found when resolveWithPrefetched then the repository is never re-asked`() = runTest {
        val repo = FakeRepository(CardStrategyTagsResult.NotFound)
        val edhrec = FakeEdhrecEnrichment()
        val useCase = ResolveCardStrategyTagsUseCase(computeCardTagsWithSuggestedBlink, repo, edhrec)
        val card = testCard(oracleId = "oracle-1", oracleText = "Exile target creature.")

        val result = useCase.resolveWithPrefetched(
            card = card,
            existingTagsJson = null,
            prefetched = CardStrategyTagsResult.Found(tags = listOf(CardTag.REMOVAL)),
        )

        assertEquals(listOf(CardTag.REMOVAL), result.confirmedTags)
        assertTrue(result.suggestedTags.isEmpty())
        assertEquals(0, repo.callCount)
        assertEquals(0, repo.submitCallCount)
        assertEquals(0, edhrec.callCount)
    }

    @Test
    fun `given a prefetched Found and existing user tags when resolveWithPrefetched then both survive`() = runTest {
        val repo = FakeRepository(CardStrategyTagsResult.NotFound)
        val useCase = ResolveCardStrategyTagsUseCase(computeCardTags, repo, FakeEdhrecEnrichment())

        val result = useCase.resolveWithPrefetched(
            card = testCard(oracleId = "oracle-2"),
            existingTagsJson = """[{"k":"my_custom_tag","c":"CUSTOM"}]""",
            prefetched = CardStrategyTagsResult.Found(tags = listOf(CardTag.REMOVAL)),
        )

        assertTrue(CardTag.REMOVAL in result.confirmedTags)
        assertTrue(result.confirmedTags.any { it.key == "my_custom_tag" })
    }

    @Test
    fun `given a prefetched NotFound when resolveWithPrefetched then the on-device fallback runs and writes back`() = runTest {
        val repo = FakeRepository(CardStrategyTagsResult.Found(tags = listOf(CardTag.RAMP)))
        val edhrec = FakeEdhrecEnrichment()
        val useCase = ResolveCardStrategyTagsUseCase(computeCardTagsWithSuggestedBlink, repo, edhrec)

        val result = useCase.resolveWithPrefetched(
            card = testCard(oracleId = "oracle-3", oracleText = "Exile target creature, then return it."),
            existingTagsJson = null,
            prefetched = CardStrategyTagsResult.NotFound,
        )

        // The prefetched miss is authoritative: the repository is never read again, only written to.
        assertEquals(0, repo.callCount)
        assertEquals(1, repo.submitCallCount)
        assertTrue(CardTag.RAMP !in result.confirmedTags)
    }
}
