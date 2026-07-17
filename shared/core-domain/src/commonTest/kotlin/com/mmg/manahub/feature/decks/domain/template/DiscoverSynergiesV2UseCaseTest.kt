package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.SeedStrategy
import com.mmg.manahub.feature.decks.domain.engine.card
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Deck Builder v2, Phase 5 (plan §3.5) -- [DiscoverSynergiesV2UseCase] clustering, thresholds,
 * and color coherence. Mirrors [BuildDeckFromTemplateUseCaseTest]'s fixture conventions. */
class DiscoverSynergiesV2UseCaseTest {

    private val dispatcher = StandardTestDispatcher()
    private val scorer = DeckScorer(RoleClassifier(), NeutralPowerResolver)
    private val useCase = DiscoverSynergiesV2UseCase(deckScorer = scorer, ioDispatcher = dispatcher)

    private fun owned(
        name: String,
        quantity: Int = 1,
        builder: () -> com.mmg.manahub.core.model.Card,
    ) = UserCardWithCard(
        userCard = UserCard(id = "uc-$name", scryfallId = "scry-$name", quantity = quantity),
        card = builder(),
    )

    @Test
    fun `a strategy cluster surfaces once it reaches the copy threshold`() = runTest(dispatcher) {
        val collection = (1..6).map { i ->
            owned("ramp$i") { card(id = "ramp$i", name = "Ramp Card $i", tags = listOf(CardTag.RAMP), colorIdentity = listOf("G")) }
        }
        val discoveries = useCase(collection)
        val rampDiscovery = discoveries.single { it.key is DiscoveryClusterKey.Strategy }
        assertEquals(6, rampDiscovery.memberCount)
        assertEquals(SeedStrategy.RAMP, rampDiscovery.strategyHint)
    }

    @Test
    fun `a strategy cluster below the copy threshold never surfaces`() = runTest(dispatcher) {
        val collection = (1..5).map { i ->
            owned("ramp$i") { card(id = "ramp$i", name = "Ramp Card $i", tags = listOf(CardTag.RAMP), colorIdentity = listOf("G")) }
        }
        val discoveries = useCase(collection)
        assertTrue(discoveries.none { it.key is DiscoveryClusterKey.Strategy })
    }

    @Test
    fun `a tribe cluster surfaces at the TRIBE_ABS_THRESHOLD copy count`() = runTest(dispatcher) {
        val collection = (1..8).map { i ->
            owned("elf$i") { card(id = "elf$i", name = "Elf $i", typeLine = "Creature — Elf", colorIdentity = listOf("G")) }
        }
        val discoveries = useCase(collection)
        val tribeDiscovery = discoveries.single { it.key is DiscoveryClusterKey.Tribe }
        assertEquals("Elves", tribeDiscovery.label)
        assertEquals(SeedStrategy.TRIBAL, tribeDiscovery.strategyHint)
        assertEquals("Elves", tribeDiscovery.themeHint)
        assertEquals(8, tribeDiscovery.memberCount)
    }

    @Test
    fun `a tribe cluster below 8 copies never surfaces`() = runTest(dispatcher) {
        val collection = (1..7).map { i ->
            owned("elf$i") { card(id = "elf$i", name = "Elf $i", typeLine = "Creature — Elf", colorIdentity = listOf("G")) }
        }
        val discoveries = useCase(collection)
        assertTrue(discoveries.none { it.key is DiscoveryClusterKey.Tribe })
    }

    @Test
    fun `TYPE and KEYWORD tags never form a cluster -- identity signals only`() = runTest(dispatcher) {
        val flying = CardTag("flying", TagCategory.KEYWORD)
        val instantType = CardTag("instant", TagCategory.TYPE)
        val collection = (1..10).map { i ->
            owned("card$i") {
                card(id = "card$i", name = "Card $i", typeLine = "Instant", tags = listOf(flying, instantType), colorIdentity = listOf("U"))
            }
        }
        val discoveries = useCase(collection)
        assertTrue(discoveries.isEmpty())
    }

    @Test
    fun `color coherence drops off-color members and can push a cluster below threshold`() = runTest(dispatcher) {
        // 3 green (majority) + one each of blue/black/red (4 distinct colors total). The color
        // -coherence filter keeps green + only 2 of the 3 tied minorities (MAX_DOMINANT_COLORS = 3),
        // so exactly 5 of 6 members survive -- below the 6-copy strategy threshold, so the whole
        // cluster is honestly dropped rather than surfaced half-filtered.
        val collection = listOf("G", "G", "G", "U", "B", "R").mapIndexed { i, color ->
            owned("ramp$i") { card(id = "ramp$i", name = "Ramp Card $i", tags = listOf(CardTag.RAMP), colorIdentity = listOf(color)) }
        }
        val discoveries = useCase(collection)
        assertTrue(discoveries.none { it.key is DiscoveryClusterKey.Strategy })
    }

    @Test
    fun `a color-coherent cluster with a genuine minority color still filters at least one member`() = runTest(dispatcher) {
        val collection = listOf("G", "G", "G", "G", "G", "U", "B", "R").mapIndexed { i, color ->
            owned("ramp$i") { card(id = "ramp$i", name = "Ramp Card $i", tags = listOf(CardTag.RAMP), colorIdentity = listOf(color)) }
        }
        val discoveries = useCase(collection)
        val rampDiscovery = discoveries.single { it.key is DiscoveryClusterKey.Strategy }
        // 8 inputs, 1 of the 3 minority colors must be excluded (MAX_DOMINANT_COLORS caps at 3).
        assertTrue(rampDiscovery.memberCount < 8)
        assertTrue(rampDiscovery.dominantColors.size <= 3)
    }

    @Test
    fun `a duplicate-printing card does not inflate a cluster's copies threshold via double-counting`() = runTest(dispatcher) {
        val others = (1..3).map { i ->
            owned("ramp$i") { card(id = "ramp$i", name = "Ramp Card $i", tags = listOf(CardTag.RAMP), colorIdentity = listOf("G")) }
        }
        // Two DIFFERENT printings (different scryfallId) of the SAME named card -- real total owned
        // quantity is 1+1=2. Pre-fix this card was treated as TWO distinct cluster members (deduped
        // by scryfallId, not name), each contributing the FULL per-name total (quantityByName["Ramp
        // Twin"] = 2) to `copies`, double-counting it to 4 instead of 2.
        val twinPrintingA = owned("twin-a") { card(id = "twin-a", name = "Ramp Twin", tags = listOf(CardTag.RAMP), colorIdentity = listOf("G")) }
        val twinPrintingB = owned("twin-b") { card(id = "twin-b", name = "Ramp Twin", tags = listOf(CardTag.RAMP), colorIdentity = listOf("G")) }
        val collection = others + listOf(twinPrintingA, twinPrintingB)

        val discoveries = useCase(collection)

        // Real distinct-name copy count is 3 (others) + 2 (Ramp Twin, counted once) = 5, below the
        // 6-copy strategy threshold -- the cluster must NOT surface. Pre-fix, double-counting Ramp
        // Twin inflated this to 3 + 4 = 7, incorrectly clearing the threshold.
        assertTrue(discoveries.none { it.key is DiscoveryClusterKey.Strategy })
    }

    @Test
    fun `duplicate printings never produce two entries of the same card name in cluster members`() = runTest(dispatcher) {
        val others = (1..5).map { i ->
            owned("ramp$i") { card(id = "ramp$i", name = "Ramp Card $i", tags = listOf(CardTag.RAMP), colorIdentity = listOf("G")) }
        }
        val twinPrintingA = owned("twin-a") { card(id = "twin-a", name = "Ramp Twin", tags = listOf(CardTag.RAMP), colorIdentity = listOf("G")) }
        val twinPrintingB = owned("twin-b") { card(id = "twin-b", name = "Ramp Twin", tags = listOf(CardTag.RAMP), colorIdentity = listOf("G")) }
        val collection = others + listOf(twinPrintingA, twinPrintingB)

        val discoveries = useCase(collection)
        val rampDiscovery = discoveries.single { it.key is DiscoveryClusterKey.Strategy }

        val twinOccurrences = rampDiscovery.members.count { it.name == "Ramp Twin" }
        assertEquals(1, twinOccurrences, "a duplicate-printing card must appear at most once in cluster members, never once per printing")
    }

    @Test
    fun `results are deterministic across repeated runs on the same input`() = runTest(dispatcher) {
        val collection = (1..6).map { i ->
            owned("ramp$i") { card(id = "ramp$i", name = "Ramp Card $i", tags = listOf(CardTag.RAMP), colorIdentity = listOf("G")) }
        } + (1..8).map { i ->
            owned("elf$i") { card(id = "elf$i", name = "Elf $i", typeLine = "Creature — Elf", colorIdentity = listOf("G")) }
        }
        val first = useCase(collection)
        val second = useCase(collection)
        assertEquals(first, second)
    }

    @Test
    fun `an empty collection yields no discoveries, never a crash`() = runTest(dispatcher) {
        assertTrue(useCase(emptyList()).isEmpty())
    }

    @Test
    fun `discoveries are capped at the requested limit`() = runTest(dispatcher) {
        val strategyTags = listOf(CardTag.RAMP, CardTag.TOKENS, CardTag.LIFEGAIN, CardTag.SACRIFICE)
        val collection = strategyTags.flatMap { tag ->
            (1..6).map { i ->
                owned("${tag.key}$i") { card(id = "${tag.key}$i", name = "${tag.key} Card $i", tags = listOf(tag), colorIdentity = listOf("G")) }
            }
        }
        val discoveries = useCase(collection, limit = 2)
        assertTrue(discoveries.size <= 2)
    }
}
