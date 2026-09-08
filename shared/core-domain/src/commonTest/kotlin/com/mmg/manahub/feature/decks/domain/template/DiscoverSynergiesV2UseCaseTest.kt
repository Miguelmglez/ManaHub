package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
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
        // Deck Engine Unification plan D7 (4.1): threshold raised 6 -> 8 -- "small high-impact
        // clusters, not broad type-based ones."
        val collection = (1..8).map { i ->
            owned("ramp$i") { card(id = "ramp$i", name = "Ramp Card $i", tags = listOf(CardTag.RAMP), colorIdentity = listOf("G")) }
        }
        val discoveries = useCase(collection)
        val rampDiscovery = discoveries.single { it.key is DiscoveryClusterKey.Strategy }
        assertEquals(8, rampDiscovery.memberCount)
        // Deck Analysis Engine v3: RAMP moved from ArchetypeId to PostureId -- CardTag.RAMP no
        // longer resolves to a macro archetype via DeckIdentitySeedTags.archetypeForTag at all.
        assertEquals(null, rampDiscovery.archetype)
    }

    @Test
    fun `a strategy cluster below the copy threshold never surfaces`() = runTest(dispatcher) {
        val collection = (1..7).map { i ->
            owned("ramp$i") { card(id = "ramp$i", name = "Ramp Card $i", tags = listOf(CardTag.RAMP), colorIdentity = listOf("G")) }
        }
        val discoveries = useCase(collection)
        assertTrue(discoveries.none { it.key is DiscoveryClusterKey.Strategy })
    }

    @Test
    fun `a tribe cluster surfaces at its (raised) copy threshold`() = runTest(dispatcher) {
        // Deck Engine Unification plan D7 (4.1): threshold raised 8 -> 10, deliberately past the
        // engine's own TRIBE_ABS_THRESHOLD floor (see MIN_TRIBE_CLUSTER_COPIES's KDoc).
        val collection = (1..10).map { i ->
            owned("elf$i") { card(id = "elf$i", name = "Elf $i", typeLine = "Creature — Elf", colorIdentity = listOf("G")) }
        }
        val discoveries = useCase(collection)
        val tribeDiscovery = discoveries.single { it.key is DiscoveryClusterKey.Tribe }
        assertEquals("Elves", tribeDiscovery.label)
        assertEquals(null, tribeDiscovery.archetype)
        assertEquals(null, tribeDiscovery.theme)
        assertEquals("tribe:elf", tribeDiscovery.tribe)
        assertEquals(10, tribeDiscovery.memberCount)
    }

    @Test
    fun `a tribe cluster below its copy threshold never surfaces`() = runTest(dispatcher) {
        val collection = (1..9).map { i ->
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
        // 7 green (majority) + one each of blue/black/red/white (4 distinct minority colors) --
        // MAX_DOMINANT_COLORS caps at 3, so exactly 1 of the 4 minorities must be excluded, leaving
        // 7 + 3 = 10 survivors, comfortably above the (raised, plan D7 4.1) 8-copy strategy floor.
        val collection = (listOf("G", "G", "G", "G", "G", "G", "G", "U", "B", "R", "W")).mapIndexed { i, color ->
            owned("ramp$i") { card(id = "ramp$i", name = "Ramp Card $i", tags = listOf(CardTag.RAMP), colorIdentity = listOf(color)) }
        }
        val discoveries = useCase(collection)
        val rampDiscovery = discoveries.single { it.key is DiscoveryClusterKey.Strategy }
        // 11 inputs, 1 of the 4 minority colors must be excluded (MAX_DOMINANT_COLORS caps at 3).
        assertTrue(rampDiscovery.memberCount < 11)
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
        // 6 others (qty 1 each) + Ramp Twin (2 printings, deduped to qty 2) = 8 copies, clearing
        // the (raised, plan D7 4.1) 8-copy strategy floor.
        val others = (1..6).map { i ->
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
        // 8 copies per tag -- each cluster genuinely clears the (raised, plan D7 4.1) 8-copy floor,
        // so this test still exercises real capping (4 viable clusters -> limit 2) rather than
        // trivially passing on zero surfaced clusters.
        val collection = strategyTags.flatMap { tag ->
            (1..8).map { i ->
                owned("${tag.key}$i") { card(id = "${tag.key}$i", name = "${tag.key} Card $i", tags = listOf(tag), colorIdentity = listOf("G")) }
            }
        }
        val discoveries = useCase(collection, limit = 2)
        assertEquals(2, discoveries.size)
    }

    @Test
    fun `rarity is a tiebreak only -- higher fit still wins over rarity`() = runTest(dispatcher) {
        // Deck Engine Unification plan D7 (4.1): a common card with equal-or-better fit must NOT
        // be pushed below a mythic by the rarity tiebreak -- it only breaks TIES.
        val collection = (1..8).map { i ->
            owned("ramp$i") {
                card(
                    id = "ramp$i",
                    name = "Ramp Card $i",
                    tags = listOf(CardTag.RAMP),
                    colorIdentity = listOf("G"),
                    rarity = if (i == 1) "mythic" else "common",
                )
            }
        }
        val discoveries = useCase(collection)
        val rampDiscovery = discoveries.single { it.key is DiscoveryClusterKey.Strategy }
        // Every fixture card is otherwise identical (same tags/color/CMC via the shared `card()`
        // builder default) -- fit ties, so the tiebreak decides: the mythic must rank first.
        assertEquals("Ramp Card 1", rampDiscovery.members.first().name)
    }

    @Test
    fun `search filter narrows by label and by member card name`() = runTest(dispatcher) {
        val collection = (1..8).map { i ->
            owned("ramp$i") { card(id = "ramp$i", name = "Ramp Card $i", tags = listOf(CardTag.RAMP), colorIdentity = listOf("G")) }
        } + (1..10).map { i ->
            owned("elf$i") { card(id = "elf$i", name = "Elf $i", typeLine = "Creature — Elf", colorIdentity = listOf("G")) }
        }
        val discoveries = useCase(collection)
        assertEquals(2, discoveries.size)

        val byLabel = DiscoverySearchFilter.apply(discoveries, query = "Elves", selectedCardNames = emptySet())
        assertEquals(1, byLabel.size)
        assertEquals("Elves", byLabel.single().label)

        val byCard = DiscoverySearchFilter.apply(discoveries, query = "", selectedCardNames = setOf("Ramp Card 1"))
        assertEquals(1, byCard.size)
        assertTrue(byCard.single().key is DiscoveryClusterKey.Strategy)

        val noMatch = DiscoverySearchFilter.apply(discoveries, query = "nonexistent strategy", selectedCardNames = emptySet())
        assertTrue(noMatch.isEmpty())

        val unfiltered = DiscoverySearchFilter.apply(discoveries, query = "", selectedCardNames = emptySet())
        assertEquals(discoveries, unfiltered)
    }

    // ── partitionByAxis (Deck Wizard & Engine Rework plan, WS 1.3) ─────────────────────────────

    @Test
    fun `partitionByAxis splits a mixed list into Strategy clusters first and Tribe clusters second`() = runTest(dispatcher) {
        val collection = (1..8).map { i ->
            owned("ramp$i") { card(id = "ramp$i", name = "Ramp Card $i", tags = listOf(CardTag.RAMP), colorIdentity = listOf("G")) }
        } + (1..10).map { i ->
            owned("elf$i") { card(id = "elf$i", name = "Elf $i", typeLine = "Creature — Elf", colorIdentity = listOf("G")) }
        }
        val discoveries = useCase(collection)
        assertEquals(2, discoveries.size)

        val (strategies, tribes) = discoveries.partitionByAxis()

        assertEquals(1, strategies.size)
        assertTrue(strategies.all { it.key is DiscoveryClusterKey.Strategy })
        assertEquals(1, tribes.size)
        assertTrue(tribes.all { it.key is DiscoveryClusterKey.Tribe })
    }

    @Test
    fun `partitionByAxis never drops or duplicates a discovery`() = runTest(dispatcher) {
        val collection = (1..8).map { i ->
            owned("ramp$i") { card(id = "ramp$i", name = "Ramp Card $i", tags = listOf(CardTag.RAMP), colorIdentity = listOf("G")) }
        } + (1..8).map { i ->
            owned("token$i") { card(id = "token$i", name = "Token Card $i", tags = listOf(CardTag.TOKENS), colorIdentity = listOf("W")) }
        } + (1..10).map { i ->
            owned("elf$i") { card(id = "elf$i", name = "Elf $i", typeLine = "Creature — Elf", colorIdentity = listOf("G")) }
        }
        val discoveries = useCase(collection)
        val (strategies, tribes) = discoveries.partitionByAxis()

        assertEquals(discoveries.size, strategies.size + tribes.size)
        assertEquals(discoveries.toSet(), (strategies + tribes).toSet())
    }

    @Test
    fun `partitionByAxis on an all-strategy list yields an empty Tribe half`() = runTest(dispatcher) {
        val collection = (1..8).map { i ->
            owned("ramp$i") { card(id = "ramp$i", name = "Ramp Card $i", tags = listOf(CardTag.RAMP), colorIdentity = listOf("G")) }
        }
        val discoveries = useCase(collection)
        val (strategies, tribes) = discoveries.partitionByAxis()

        assertEquals(discoveries, strategies)
        assertTrue(tribes.isEmpty())
    }
}
