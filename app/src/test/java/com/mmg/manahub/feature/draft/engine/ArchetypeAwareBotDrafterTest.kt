package com.mmg.manahub.feature.draft.engine

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.feature.draft.data.engine.ArchetypeAwareBotDrafter
import com.mmg.manahub.feature.draft.data.engine.DraftRatingNormalizer
import com.mmg.manahub.feature.draft.data.engine.HeuristicBotDrafter
import com.mmg.manahub.feature.draft.domain.engine.BotDrafter
import com.mmg.manahub.feature.draft.domain.model.BoosterPack
import com.mmg.manahub.feature.draft.domain.model.DraftCard
import com.mmg.manahub.feature.draft.domain.model.DraftSeat
import com.mmg.manahub.feature.draft.domain.model.EngineArchetype
import com.mmg.manahub.feature.draft.domain.model.EngineCardSignals
import com.mmg.manahub.feature.draft.domain.model.EngineConfig
import com.mmg.manahub.feature.draft.domain.model.EngineParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ArchetypeAwareBotDrafter].
 *
 * Covers: engine-driven archetype picks, the heuristic fallback when no engine is present,
 * per-bot lane diversity, the neutral human suggestion, and the mana-fixing tie-breaker.
 */
class ArchetypeAwareBotDrafterTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun card(
        id: String,
        cmc: Double = 3.0,
        colors: List<String> = listOf("W"),
        rarity: String = "common",
    ): Card = Card(
        scryfallId = id, name = id, printedName = null,
        manaCost = null, cmc = cmc,
        colors = colors, colorIdentity = colors,
        typeLine = "Creature", printedTypeLine = null, oracleText = null,
        printedText = null, keywords = emptyList(), power = "1", toughness = "1",
        loyalty = null, setCode = "TDM", setName = "Tarkir",
        collectorNumber = "1", rarity = rarity,
        releasedAt = "2025-01-01", frameEffects = emptyList(), promoTypes = emptyList(),
        lang = "en", imageNormal = null, imageArtCrop = null, imageBackNormal = null,
        priceUsd = null, priceUsdFoil = null, priceEur = null, priceEurFoil = null,
        legalityStandard = "legal", legalityPioneer = "legal",
        legalityModern = "legal", legalityCommander = "legal",
        flavorText = null, artist = null, scryfallUri = "https://scryfall.com/$id",
    )

    private fun draftCard(id: String, cmc: Double = 3.0, rank: Int? = null): DraftCard =
        DraftCard(card(id, cmc = cmc), pickOrderRank = rank)

    /** Two 3-colour wedge archetypes so colour-pair logic can't be exercised. */
    private val archetypes = listOf(
        EngineArchetype("abzan", "Abzan", listOf("W", "B", "G"), tier = 1, opennessBase = 1.0f, keyCardIds = emptyList()),
        EngineArchetype("jeskai", "Jeskai", listOf("U", "R", "W"), tier = 2, opennessBase = 1.0f, keyCardIds = emptyList()),
    )

    private fun engine(
        cards: Map<String, EngineCardSignals>,
        params: EngineParams = EngineParams(),
    ): EngineConfig = EngineConfig(
        setCode = "tdm",
        schemaVersion = 1,
        lastUpdated = "v1",
        params = params,
        archetypes = archetypes,
        cards = cards,
    )

    private val drafter: BotDrafter = ArchetypeAwareBotDrafter(fallback = HeuristicBotDrafter())

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `engine present drives an archetype-led pick once committed`() {
        // Seat already committed to Abzan via its pool (several Abzan cards).
        val poolCards = (1..6).map { draftCard("abzan-pool-$it", rank = 50) }
        val poolSignals = poolCards.associate {
            it.card.scryfallId to EngineCardSignals(archetypeWeights = mapOf("abzan" to 1.0f))
        }

        // Pack: a strong Jeskai card vs a weaker Abzan card. Once committed, synergy should pull
        // the seat toward the on-archetype (Abzan) card despite its lower raw rating.
        val jeskaiCard = draftCard("jeskai-strong", rank = 1)
        val abzanCard = draftCard("abzan-weak", rank = 120)
        val packSignals = mapOf(
            jeskaiCard.card.scryfallId to EngineCardSignals(archetypeWeights = mapOf("jeskai" to 1.0f)),
            abzanCard.card.scryfallId to EngineCardSignals(archetypeWeights = mapOf("abzan" to 1.0f)),
        )

        val seat = DraftSeat(index = 0, isHuman = true, pool = poolCards)
        val engineConfig = engine(poolSignals + packSignals)
        val pack = BoosterPack("p", listOf(jeskaiCard, abzanCard))

        val pick = drafter.pick(seat, pack, round = 1, pickNumber = 7, engine = engineConfig)

        assertEquals("abzan-weak", pick.card.scryfallId)
    }

    @Test
    fun `early picks are rating-led not synergy-led`() {
        // Empty pool → no commitment → synergy ramp ≈ 0, so the highest-rated card wins regardless
        // of archetype membership.
        val strong = draftCard("strong", rank = 1)
        val weak = draftCard("weak", rank = 150)
        val signals = mapOf(
            strong.card.scryfallId to EngineCardSignals(archetypeWeights = mapOf("jeskai" to 1.0f)),
            weak.card.scryfallId to EngineCardSignals(archetypeWeights = mapOf("abzan" to 1.0f)),
        )
        val seat = DraftSeat(index = 0, isHuman = true, pool = emptyList())
        val pack = BoosterPack("p", listOf(strong, weak))

        val pick = drafter.pick(seat, pack, round = 1, pickNumber = 1, engine = engine(signals))

        assertEquals("strong", pick.card.scryfallId)
    }

    @Test
    fun `engine null delegates to the heuristic fallback`() {
        // A recording fallback proves delegation happens and the same args are forwarded.
        var fallbackCalled = false
        val recordingFallback = object : BotDrafter {
            override fun pick(
                seat: DraftSeat,
                pack: BoosterPack,
                round: Int,
                pickNumber: Int,
                engine: EngineConfig?,
            ): DraftCard {
                fallbackCalled = true
                return pack.cards.last()
            }
        }
        val drafterWithFallback = ArchetypeAwareBotDrafter(fallback = recordingFallback)
        val pack = BoosterPack("p", listOf(draftCard("a", rank = 1), draftCard("b", rank = 2)))
        val seat = DraftSeat(index = 0, isHuman = false)

        val pick = drafterWithFallback.pick(seat, pack, round = 1, pickNumber = 1, engine = null)

        assertTrue("fallback should be invoked when engine is null", fallbackCalled)
        assertEquals("b", pick.card.scryfallId)
    }

    @Test
    fun `bots with different seat indices spread across lanes`() {
        // Two equally-rated cards, each pure to one archetype. The deterministic per-seat prior should
        // tip different bot seats toward different lanes on the very first pick (empty pools).
        val abzanCard = draftCard("abzan-card", rank = 40)
        val jeskaiCard = draftCard("jeskai-card", rank = 40)
        val signals = mapOf(
            abzanCard.card.scryfallId to EngineCardSignals(archetypeWeights = mapOf("abzan" to 1.0f)),
            jeskaiCard.card.scryfallId to EngineCardSignals(archetypeWeights = mapOf("jeskai" to 1.0f)),
        )
        val engineConfig = engine(signals)
        val pack = BoosterPack("p", listOf(abzanCard, jeskaiCard))

        // Seat 0 → primary archetype index 0 (abzan); seat 1 → primary archetype index 1 (jeskai).
        val seat0 = DraftSeat(index = 0, isHuman = false)
        val seat1 = DraftSeat(index = 1, isHuman = false)

        val pick0 = drafter.pick(seat0, pack, round = 1, pickNumber = 1, engine = engineConfig)
        val pick1 = drafter.pick(seat1, pack, round = 1, pickNumber = 1, engine = engineConfig)

        assertEquals("abzan-card", pick0.card.scryfallId)
        assertEquals("jeskai-card", pick1.card.scryfallId)
        assertNotEquals(pick0.card.scryfallId, pick1.card.scryfallId)
    }

    @Test
    fun `human seat gets no diversity prior`() {
        // With identical ratings and no pool, a human seat must not be tipped by any prior — it falls
        // back to the deterministic tie-break, NOT a seat-index lane bias.
        val abzanCard = draftCard("abzan-card", rank = 40)
        val jeskaiCard = draftCard("jeskai-card", rank = 40)
        val signals = mapOf(
            abzanCard.card.scryfallId to EngineCardSignals(archetypeWeights = mapOf("abzan" to 1.0f)),
            jeskaiCard.card.scryfallId to EngineCardSignals(archetypeWeights = mapOf("jeskai" to 1.0f)),
        )
        val engineConfig = engine(signals)
        val pack = BoosterPack("p", listOf(abzanCard, jeskaiCard))

        // Human seat at index 1 — if a prior were applied it would tip toward jeskai; it must not.
        val humanSeat1 = DraftSeat(index = 1, isHuman = true)
        val humanSeat3 = DraftSeat(index = 3, isHuman = true)

        val pickA = drafter.pick(humanSeat1, pack, round = 1, pickNumber = 1, engine = engineConfig)
        val pickB = drafter.pick(humanSeat3, pack, round = 1, pickNumber = 1, engine = engineConfig)

        // Both human suggestions are identical regardless of seat index → no diversity prior.
        assertEquals(pickA.card.scryfallId, pickB.card.scryfallId)
    }

    @Test
    fun `fixing bonus tips a close pick`() {
        // Two cards with the same rating and no archetype commitment. The fixing flag should be the
        // tie-breaker that pushes the fixer ahead.
        val plainCard = draftCard("plain", rank = 40)
        val fixerCard = draftCard("fixer", rank = 40)
        val signals = mapOf(
            plainCard.card.scryfallId to EngineCardSignals(archetypeWeights = emptyMap()),
            fixerCard.card.scryfallId to EngineCardSignals(archetypeWeights = emptyMap(), fixing = true),
        )
        // Generous fixing bonus so it dominates the (equal) ratings.
        val params = EngineParams(fixingBonus = 1.0f)
        val seat = DraftSeat(index = 0, isHuman = true)
        val pack = BoosterPack("p", listOf(plainCard, fixerCard))

        val pick = drafter.pick(seat, pack, round = 1, pickNumber = 1, engine = engine(signals, params))

        assertEquals("fixer", pick.card.scryfallId)
    }

    @Test
    fun `works for five-colour archetype membership`() {
        // A card belonging to a 5-colour lane must score fine — no off-colour penalty exists.
        val fiveColor = EngineArchetype(
            "globe", "5C Globe", listOf("W", "U", "B", "R", "G"),
            tier = 3, opennessBase = 1.0f, keyCardIds = emptyList(),
        )
        val cfg = EngineConfig(
            setCode = "tdm", schemaVersion = 1, lastUpdated = "v1",
            params = EngineParams(),
            archetypes = listOf(fiveColor),
            cards = mapOf(
                "globe-card" to EngineCardSignals(archetypeWeights = mapOf("globe" to 1.0f)),
                "off" to EngineCardSignals(archetypeWeights = emptyMap()),
            ),
        )
        // Commit the seat to the globe lane with a deep pool, then offer an off-lane high-rated card.
        val pool = (1..8).map {
            DraftCard(card("globe-pool-$it"), pickOrderRank = 30)
        }
        val cfgWithPool = cfg.copy(
            cards = cfg.cards + pool.associate {
                it.card.scryfallId to EngineCardSignals(archetypeWeights = mapOf("globe" to 1.0f))
            },
        )
        val seat = DraftSeat(index = 0, isHuman = true, pool = pool)
        val globeCard = draftCard("globe-card", rank = 100)
        val offCard = draftCard("off", rank = 5)
        val pack = BoosterPack("p", listOf(globeCard, offCard))

        val pick = drafter.pick(seat, pack, round = 1, pickNumber = 9, engine = cfgWithPool)

        // Strong commitment should pull the on-lane card even though "off" has a better raw rating.
        assertEquals("globe-card", pick.card.scryfallId)
    }

    // ── Edge-case audit regression tests ────────────────────────────────────────

    @Test
    fun `empty pack throws a clear IllegalArgumentException`() {
        // FIX 1: an empty pack must fail fast with a require() precondition rather than crashing
        // later with a confusing NoSuchElementException / error("Empty pack").
        val emptyPack = BoosterPack("p", emptyList())
        val seat = DraftSeat(index = 0, isHuman = true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            drafter.pick(seat, emptyPack, round = 1, pickNumber = 1, engine = engine(emptyMap()))
        }
        assertTrue(
            "message should name the drafter + empty pack",
            ex.message?.contains("empty pack", ignoreCase = true) == true,
        )
    }

    @Test
    fun `empty pack throws even on the heuristic fallback path`() {
        // The same require() must fire when engine == null (delegation to HeuristicBotDrafter).
        val emptyPack = BoosterPack("p", emptyList())
        val seat = DraftSeat(index = 0, isHuman = false)

        assertThrows(IllegalArgumentException::class.java) {
            drafter.pick(seat, emptyPack, round = 1, pickNumber = 1, engine = null)
        }
    }

    @Test
    fun `sanitised null rating still yields a deterministic best-card pick`() {
        // FIX 2: a Float.NaN rating in engine.json is sanitised to null by the repository parser, so
        // the drafter receives null and falls back to DraftRatingNormalizer (pickOrderRank). With a
        // clearly stronger rank the best card must win deterministically — no comparator inconsistency.
        val strong = draftCard("strong", rank = 1)
        val weak = draftCard("weak", rank = 150)
        // rating = null mirrors the post-sanitize shape of a NaN/out-of-range rating.
        val signals = mapOf(
            strong.card.scryfallId to EngineCardSignals(
                archetypeWeights = mapOf("jeskai" to 1.0f),
                rating = null,
            ),
            weak.card.scryfallId to EngineCardSignals(
                archetypeWeights = mapOf("abzan" to 1.0f),
                rating = null,
            ),
        )
        val seat = DraftSeat(index = 0, isHuman = true, pool = emptyList())
        val pack = BoosterPack("p", listOf(strong, weak))

        // Repeated calls must be identical (deterministic), and pick the higher-rated card.
        val first = drafter.pick(seat, pack, round = 1, pickNumber = 1, engine = engine(signals))
        val second = drafter.pick(seat, pack, round = 1, pickNumber = 1, engine = engine(signals))
        assertEquals("strong", first.card.scryfallId)
        assertEquals(first.card.scryfallId, second.card.scryfallId)
    }

    @Test
    fun `sanitised archetype weights do not anti-pick the committed archetype`() {
        // FIX 2: a negative archetypeWeight would invert synergy and anti-pick the seat's committed
        // lane. The repository parser strips negative/non-finite weights. Post-sanitize the on-lane
        // card keeps its POSITIVE weight (the negative one removed) while a neutral off-lane card has
        // no weights — so once the seat is committed to Abzan the on-lane card must WIN on synergy,
        // not be pushed below the neutral card. (If a negative weight had leaked, the abzan card would
        // score below the neutral one and lose — the exact bug this guards against.)
        val poolCards = (1..6).map { draftCard("abzan-pool-$it", rank = 50) }
        val poolSignals = poolCards.associate {
            it.card.scryfallId to EngineCardSignals(archetypeWeights = mapOf("abzan" to 1.0f))
        }

        // Give the on-lane card a lower raw rating than the neutral card so synergy alone — not
        // rating — is what could pull it ahead; only a correctly-signed (positive) weight can.
        val abzanCard = draftCard("abzan-card", rank = 90)
        val neutralCard = draftCard("neutral", rank = 40)
        val packSignals = mapOf(
            // Post-sanitize: the negative weight was filtered out, leaving the genuine positive one.
            abzanCard.card.scryfallId to EngineCardSignals(archetypeWeights = mapOf("abzan" to 1.0f)),
            neutralCard.card.scryfallId to EngineCardSignals(archetypeWeights = emptyMap()),
        )

        val seat = DraftSeat(index = 0, isHuman = true, pool = poolCards)
        val engineConfig = engine(poolSignals + packSignals)
        val pack = BoosterPack("p", listOf(abzanCard, neutralCard))

        val pick = drafter.pick(seat, pack, round = 1, pickNumber = 7, engine = engineConfig)

        // Committed synergy pulls the on-lane card ahead despite its weaker raw rating; a leaked
        // negative weight would have anti-picked it and the neutral card would win instead.
        assertEquals("abzan-card", pick.card.scryfallId)
    }

    @Test
    fun `ratingScore with rank zero does not outscore a real rank`() {
        // FIX 5: pickOrderRank == 0 is invalid/sentinel data; it must NOT compute 1.005f → 1.0f and
        // thereby outscore a genuinely strong rank. It falls through to the neutral 0.30f default.
        val zeroRank = draftCard("zero", rank = 0)
        val realRank = draftCard("ten", rank = 10)

        val zeroScore = DraftRatingNormalizer.ratingScore(zeroRank)
        val realScore = DraftRatingNormalizer.ratingScore(realRank)

        assertTrue(
            "rank=0 ($zeroScore) must not outscore rank=10 ($realScore)",
            zeroScore < realScore,
        )
        // Confirm the fall-through neutral default rather than the bogus 1.0f.
        assertEquals(0.30f, zeroScore, 1e-4f)
    }
}
