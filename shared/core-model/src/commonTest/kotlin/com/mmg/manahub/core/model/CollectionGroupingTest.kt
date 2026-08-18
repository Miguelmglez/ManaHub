package com.mmg.manahub.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Multiplatform test (commonTest) for [groupCollection] — Collection "Cards" tab dynamic
 * grouping. Pure commonMain function, no platform dependency.
 */
class CollectionGroupingTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun buildCard(
        scryfallId: String = "scry-001",
        name: String = "Lightning Bolt",
        oracleId: String = "oracle-bolt",
        setCode: String = "lea",
        setName: String = "Limited Edition Alpha",
        rarity: String = "common",
        cmc: Double = 1.0,
        colors: List<String> = listOf("R"),
        typeLine: String = "Instant",
        priceEur: Double? = null,
        tags: List<CardTag> = emptyList(),
        userTags: List<CardTag> = emptyList(),
    ) = Card(
        scryfallId = scryfallId,
        name = name,
        printedName = null,
        manaCost = null,
        cmc = cmc,
        colors = colors,
        colorIdentity = colors,
        typeLine = typeLine,
        printedTypeLine = null,
        oracleText = null,
        printedText = null,
        keywords = emptyList(),
        power = null,
        toughness = null,
        loyalty = null,
        setCode = setCode,
        setName = setName,
        collectorNumber = "1",
        rarity = rarity,
        releasedAt = "1993-08-05",
        frameEffects = emptyList(),
        promoTypes = emptyList(),
        lang = "en",
        imageNormal = null,
        imageArtCrop = null,
        imageBackNormal = null,
        priceUsd = null,
        priceUsdFoil = null,
        priceEur = priceEur,
        priceEurFoil = null,
        legalityStandard = "legal",
        legalityPioneer = "legal",
        legalityModern = "legal",
        legalityCommander = "legal",
        flavorText = null,
        artist = null,
        scryfallUri = "https://scryfall.com/card/$setCode/1",
        tags = tags,
        userTags = userTags,
        oracleId = oracleId,
    )

    private fun buildGroup(
        card: Card,
        totalQuantity: Int = 1,
        hasFoil: Boolean = false,
        distinctCopies: Int = 1,
        latestAddedAt: Long = 1_000L,
    ) = CollectionCardGroup(
        card = card,
        totalQuantity = totalQuantity,
        hasFoil = hasFoil,
        distinctCopies = distinctCopies,
        latestAddedAt = latestAddedAt,
        groupKey = "${card.setCode}|${card.oracleId.ifBlank { card.name }}",
    )

    // ══════════════════════════════════════════════════════════════════════════
    //  Empty input
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun emptyInput_producesNoSections_forEveryMode() {
        CollectionGroupingMode.entries.forEach { mode ->
            assertTrue(groupCollection(emptyList(), mode).isEmpty(), "mode=$mode should produce no sections")
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TYPE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun type_artifactCreature_bucketsAsCreatures() {
        val group = buildGroup(buildCard(typeLine = "Artifact Creature — Golem"))
        val sections = groupCollection(listOf(group), CollectionGroupingMode.TYPE)

        assertEquals(1, sections.size)
        assertEquals("Creatures", sections[0].labelToken)
    }

    @Test
    fun type_emptyBucketsAreOmitted() {
        val group = buildGroup(buildCard(typeLine = "Instant"))
        val sections = groupCollection(listOf(group), CollectionGroupingMode.TYPE)

        assertEquals(listOf("Instants"), sections.map { it.labelToken })
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  COLOR
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun color_landCreature_bucketsAsLandNotColorless() {
        // A land-typed creature (e.g. Dryad Arbor) has empty `colors` but a Land type line —
        // the land check must run BEFORE the color-count check.
        val group = buildGroup(buildCard(typeLine = "Land Creature — Dryad", colors = emptyList()))
        val sections = groupCollection(listOf(group), CollectionGroupingMode.COLOR)

        assertEquals(listOf("Land"), sections.map { it.labelToken })
    }

    @Test
    fun color_multipleColors_bucketsAsMulticolor() {
        val group = buildGroup(buildCard(colors = listOf("W", "U")))
        val sections = groupCollection(listOf(group), CollectionGroupingMode.COLOR)

        assertEquals(listOf("Multicolor"), sections.map { it.labelToken })
    }

    @Test
    fun color_noColorsNonLand_bucketsAsColorless() {
        val group = buildGroup(buildCard(typeLine = "Artifact", colors = emptyList()))
        val sections = groupCollection(listOf(group), CollectionGroupingMode.COLOR)

        assertEquals(listOf("Colorless"), sections.map { it.labelToken })
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CMC
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun cmc_sevenExactly_bucketsAsSevenPlus() {
        val group = buildGroup(buildCard(cmc = 7.0, typeLine = "Sorcery"))
        val sections = groupCollection(listOf(group), CollectionGroupingMode.CMC)

        assertEquals(listOf("7+"), sections.map { it.labelToken })
    }

    @Test
    fun cmc_belowSeven_bucketsByTruncatedInt() {
        val group = buildGroup(buildCard(cmc = 6.99, typeLine = "Sorcery"))
        val sections = groupCollection(listOf(group), CollectionGroupingMode.CMC)

        assertEquals(listOf("6"), sections.map { it.labelToken })
    }

    @Test
    fun cmc_lands_neverDropped_evenAtHighCmc() {
        // Lands never have a real non-zero CMC, but even if the data were unusual, ALL lands
        // must land in a single trailing "Lands" bucket, never silently disappear.
        val land = buildGroup(buildCard(typeLine = "Land", cmc = 0.0))
        val spell = buildGroup(buildCard(scryfallId = "scry-002", oracleId = "oracle-spell", typeLine = "Instant", cmc = 2.0))
        val sections = groupCollection(listOf(land, spell), CollectionGroupingMode.CMC)

        assertEquals(listOf("2", "Lands"), sections.map { it.labelToken })
        assertEquals(1, sections.single { it.labelToken == "Lands" }.items.size)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SET
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun set_groupsByDistinctSetCode() {
        val alpha = buildGroup(buildCard(scryfallId = "scry-1", setCode = "lea"))
        val revised = buildGroup(buildCard(scryfallId = "scry-2", oracleId = "oracle-bolt-2", setCode = "3ed"))
        val sections = groupCollection(listOf(alpha, revised), CollectionGroupingMode.SET)

        assertEquals(setOf("lea", "3ed"), sections.map { it.labelToken }.toSet())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  RARITY
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun rarity_ordersMythicFirstAndCommonLast() {
        val mythic = buildGroup(buildCard(scryfallId = "s1", oracleId = "o1", rarity = "mythic"))
        val rare = buildGroup(buildCard(scryfallId = "s2", oracleId = "o2", rarity = "rare"))
        val uncommon = buildGroup(buildCard(scryfallId = "s3", oracleId = "o3", rarity = "uncommon"))
        val special = buildGroup(buildCard(scryfallId = "s4", oracleId = "o4", rarity = "special"))
        val common = buildGroup(buildCard(scryfallId = "s5", oracleId = "o5", rarity = "common"))

        val sections = groupCollection(listOf(common, special, uncommon, rare, mythic), CollectionGroupingMode.RARITY)

        assertEquals(listOf("mythic", "rare", "uncommon", "special", "common"), sections.map { it.labelToken })
    }

    @Test
    fun rarity_blankOrUnknownString_bucketsWithSpecialTier_neverCrashes() {
        val unknown = buildGroup(buildCard(rarity = ""))
        val sections = groupCollection(listOf(unknown), CollectionGroupingMode.RARITY)

        assertEquals(listOf(""), sections.map { it.labelToken })
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TAG
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun tag_cardWithMultipleStrategyTags_appearsInEverySectionItCarries() {
        val group = buildGroup(buildCard(tags = listOf(CardTag.TOKENS, CardTag.GRAVEYARD)))
        val sections = groupCollection(listOf(group), CollectionGroupingMode.TAG)

        val tokens = sections.map { it.labelToken }.toSet()
        assertTrue("tokens" in tokens)
        assertTrue("graveyard" in tokens)
        assertEquals(2, sections.size)
    }

    @Test
    fun tag_noTags_bucketsAsUntaggedSentinel() {
        val group = buildGroup(buildCard(tags = emptyList(), userTags = emptyList()))
        val sections = groupCollection(listOf(group), CollectionGroupingMode.TAG)

        assertEquals(listOf("untagged"), sections.map { it.labelToken })
    }

    @Test
    fun tag_sameKeyInBothTagsAndUserTags_countedOnceInThatSection() {
        val group = buildGroup(buildCard(tags = listOf(CardTag.TOKENS), userTags = listOf(CardTag.TOKENS)))
        val sections = groupCollection(listOf(group), CollectionGroupingMode.TAG)

        assertEquals(1, sections.size)
        assertEquals(1, sections[0].items.size)
    }

    @Test
    fun tag_onlyNonIdentityCategoryTags_bucketsAsUntaggedSentinel() {
        // CardTag.REMOVAL and CardTag.TUTOR are both TagCategory.ROLE — not one of the "identity"
        // categories (STRATEGY/ARCHETYPE/TRIBAL) Collection's TAG grouping is restricted to, unlike
        // CardDetail/Deck Studio which show every category — so this card must still be untagged.
        val group = buildGroup(buildCard(tags = listOf(CardTag.REMOVAL, CardTag.TUTOR)))
        val sections = groupCollection(listOf(group), CollectionGroupingMode.TAG)

        assertEquals(listOf("untagged"), sections.map { it.labelToken })
    }

    @Test
    fun tag_mixedIdentityAndRoleCategoryTags_bucketsOnlyUnderIdentityTag() {
        // CardTag.TOKENS is STRATEGY (an identity category); CardTag.REMOVAL (ROLE) is not and
        // must be ignored — the card should land in exactly one section, "tokens".
        val group = buildGroup(buildCard(tags = listOf(CardTag.TOKENS, CardTag.REMOVAL)))
        val sections = groupCollection(listOf(group), CollectionGroupingMode.TAG)

        assertEquals(listOf("tokens"), sections.map { it.labelToken })
    }

    @Test
    fun tag_archetypeOnlyTag_isIncludedAsIdentitySection() {
        // CardTag.RAMP is TagCategory.ARCHETYPE — one of the three "identity" categories
        // (STRATEGY/ARCHETYPE/TRIBAL) widened into scope on 2026-08-17 (was STRATEGY-only, which
        // incorrectly bucketed ARCHETYPE/TRIBAL-only cards as "untagged").
        val group = buildGroup(buildCard(tags = listOf(CardTag.RAMP)))
        val sections = groupCollection(listOf(group), CollectionGroupingMode.TAG)

        assertEquals(listOf("ramp"), sections.map { it.labelToken })
    }

    @Test
    fun tag_tribalCategoryOnlyTag_isIncludedAsIdentitySection() {
        // A TagCategory.TRIBAL tag (e.g. a tribal-synergy "goblin" tag) is an identity category
        // too — must NOT fall into "untagged".
        val group = buildGroup(buildCard(tags = listOf(CardTag("goblin", TagCategory.TRIBAL))))
        val sections = groupCollection(listOf(group), CollectionGroupingMode.TAG)

        assertEquals(listOf("goblin"), sections.map { it.labelToken })
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Order stability
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun withinABucket_incomingOrderIsPreserved_notResorted() {
        val zed = buildGroup(buildCard(scryfallId = "s1", oracleId = "o-zed", name = "Zed", typeLine = "Instant"))
        val alpha = buildGroup(buildCard(scryfallId = "s2", oracleId = "o-alpha", name = "Alpha", typeLine = "Instant"))
        // Caller passes them pre-sorted in a specific (non-alphabetical) order — grouping must
        // preserve it, not silently re-sort.
        val sections = groupCollection(listOf(zed, alpha), CollectionGroupingMode.TYPE)

        assertEquals(1, sections.size)
        assertEquals(listOf("Zed", "Alpha"), sections[0].items.map { it.card.name })
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CollectionSection computed properties
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun section_totalValueEur_sumsOnlyKnownPrices_nullWhenAllUnknown() {
        val withPrice = buildGroup(buildCard(scryfallId = "s1", oracleId = "o1", priceEur = 2.0), totalQuantity = 3)
        val withoutPrice = buildGroup(buildCard(scryfallId = "s2", oracleId = "o2", priceEur = null), totalQuantity = 1)

        val mixedSection = CollectionSection("token", listOf(withPrice, withoutPrice))
        assertEquals(6.0, mixedSection.totalValueEur)

        val allUnknownSection = CollectionSection("token", listOf(withoutPrice))
        assertNull(allUnknownSection.totalValueEur)
    }
}
