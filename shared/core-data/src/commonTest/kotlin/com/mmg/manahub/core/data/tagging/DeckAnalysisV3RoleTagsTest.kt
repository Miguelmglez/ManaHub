package com.mmg.manahub.core.data.tagging

import com.mmg.manahub.core.model.Card
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deck Analysis Engine v3, Phase 1 (spec §5.1) — unit tests for the 11 brand-new `TagCategory
 * .ROLE` [DetectionRule]s added to [TagDictionary] (`lifegain_source`, `counters_source`,
 * `treasure_source`, `extra_land_drop`, `discard_outlet`, `cost_reducer`, `haste_source`,
 * `mill_opponent`, `mill_self`, `combat_payoff`, `removal_artifact_enchant`).
 *
 * Mirrors `app/src/test/.../core/tagging/TagDictionaryArchetypeRolesTest.kt`'s own Phase-0
 * pattern (one positive + one negative real-card fixture per key) but lives in `commonTest`
 * since [StrategyAnalyzer] is pure `commonMain` (KMP-first per CLAUDE.md) — this suite runs on
 * every KMP target, not just the JVM `:app` unit test tree. The 4 REUSED tags this phase wires
 * into [ArchetypeRoleClassifier] (`anthem`, `untapper`, `spell_copy`, `equipment`) already had a
 * validated [DetectionRule] before this phase and are NOT re-tested here; see
 * `ArchetypeRoleClassifierPhase1RolesTest` (`:shared:core-domain`) for the classifier-level wiring
 * check instead.
 */
class DeckAnalysisV3RoleTagsTest {

    private val analyzer = StrategyAnalyzer(entriesProvider = { TagDictionary.all() })

    private fun createCard(
        typeLine: String,
        oracleText: String? = null,
        name: String = "Test Card",
    ): Card = Card(
        scryfallId = "test",
        name = name,
        printedName = null,
        manaCost = null,
        cmc = 0.0,
        colors = emptyList(),
        colorIdentity = emptyList(),
        typeLine = typeLine,
        printedTypeLine = null,
        oracleText = oracleText,
        printedText = null,
        keywords = emptyList(),
        power = null,
        toughness = null,
        loyalty = null,
        setCode = "TST",
        setName = "Test Set",
        collectorNumber = "1",
        rarity = "common",
        releasedAt = "2024-01-01",
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
        legalityStandard = "legal",
        legalityPioneer = "legal",
        legalityModern = "legal",
        legalityCommander = "legal",
        flavorText = null,
        artist = null,
        scryfallUri = "https://test.com",
    )

    private fun keysOf(card: Card): Set<String> = analyzer.analyze(card).map { it.tag.key }.toSet()

    private val bear = createCard("Creature — Bear", "", name = "Grizzly Bears")

    @Test
    fun `lifegain_source matches Soul Warden but not a vanilla creature`() {
        val soulWarden = createCard(
            "Creature — Human Cleric",
            "Whenever another creature enters, you gain 1 life.",
            name = "Soul Warden",
        )

        assertTrue("lifegain_source" in keysOf(soulWarden))
        assertFalse("lifegain_source" in keysOf(bear))
    }

    @Test
    fun `counters_source matches Hangarback Walker but not a vanilla creature`() {
        val hangarbackWalker = createCard(
            "Artifact Creature — Construct",
            "~ enters the battlefield with X +1/+1 counters on it. " +
                "When ~ dies, create a number of 1/1 colorless Thopter artifact creature tokens with flying " +
                "equal to the number of +1/+1 counters on ~. {1}{W/B}: Put a +1/+1 counter on ~.",
            name = "Hangarback Walker",
        )

        assertTrue("counters_source" in keysOf(hangarbackWalker))
        assertFalse("counters_source" in keysOf(bear))
    }

    @Test
    fun `treasure_source matches Dockside Extortionist but not a vanilla creature`() {
        val docksideExtortionist = createCard(
            "Creature — Goblin Imp",
            "When ~ enters the battlefield, create X Treasure tokens, " +
                "where X is the number of artifacts and/or enchantments your opponents control.",
            name = "Dockside Extortionist",
        )

        assertTrue("treasure_source" in keysOf(docksideExtortionist))
        assertFalse("treasure_source" in keysOf(bear))
    }

    @Test
    fun `extra_land_drop matches Exploration but not a vanilla creature`() {
        val exploration = createCard(
            "Enchantment",
            "You may play an additional land on each of your turns.",
            name = "Exploration",
        )

        assertTrue("extra_land_drop" in keysOf(exploration))
        assertFalse("extra_land_drop" in keysOf(bear))
    }

    @Test
    fun `discard_outlet matches Wild Mongrel but not a vanilla creature`() {
        val wildMongrel = createCard(
            "Creature — Dog",
            "Discard a card: ~ gets +1/+1 and becomes the color of your choice until end of turn.",
            name = "Wild Mongrel",
        )

        assertTrue("discard_outlet" in keysOf(wildMongrel))
        assertFalse("discard_outlet" in keysOf(bear))
    }

    @Test
    fun `cost_reducer matches Goblin Electromancer but not a vanilla creature`() {
        val goblinElectromancer = createCard(
            "Creature — Goblin Wizard",
            "Instant and sorcery spells you cast cost {1} less to cast.",
            name = "Goblin Electromancer",
        )

        assertTrue("cost_reducer" in keysOf(goblinElectromancer))
        assertFalse("cost_reducer" in keysOf(bear))
    }

    @Test
    fun `haste_source matches Fervor but not a vanilla creature`() {
        val fervor = createCard(
            "Enchantment",
            "Creatures you control have haste.",
            name = "Fervor",
        )

        assertTrue("haste_source" in keysOf(fervor))
        assertFalse("haste_source" in keysOf(bear))
    }

    @Test
    fun `mill_opponent matches Mind Sculpt but not a vanilla creature, and not a self-mill effect`() {
        val mindSculpt = createCard(
            "Sorcery",
            "Target player mills eight cards.",
            name = "Mind Sculpt",
        )
        val hedronCrab = createCard(
            "Creature — Crab",
            "Whenever a land enters the battlefield under your control, mill three cards.",
            name = "Hedron Crab",
        )

        assertTrue("mill_opponent" in keysOf(mindSculpt))
        assertFalse("mill_opponent" in keysOf(bear))
        assertFalse("mill_opponent" in keysOf(hedronCrab))
        // Regression proof: the pre-existing `mill_engine` DetectionRule (untouched by this
        // phase) still fires on the exact same real card, alongside the new `mill_opponent` key.
        assertTrue("mill_engine" in keysOf(mindSculpt))
    }

    @Test
    fun `mill_self matches Hedron Crab but not a vanilla creature, and not an opponent-mill effect`() {
        val hedronCrab = createCard(
            "Creature — Crab",
            "Whenever a land enters the battlefield under your control, mill three cards.",
            name = "Hedron Crab",
        )
        val mindSculpt = createCard(
            "Sorcery",
            "Target player mills eight cards.",
            name = "Mind Sculpt",
        )

        assertTrue("mill_self" in keysOf(hedronCrab))
        assertFalse("mill_self" in keysOf(bear))
        assertFalse("mill_self" in keysOf(mindSculpt))
    }

    @Test
    fun `combat_payoff matches Bloodhall Ooze but not a vanilla creature`() {
        val bloodhallOoze = createCard(
            "Creature — Ooze",
            "Whenever ~ deals combat damage to a player, exile the top card of your library. " +
                "You may play that card this turn.",
            name = "Bloodhall Ooze",
        )

        assertTrue("combat_payoff" in keysOf(bloodhallOoze))
        assertFalse("combat_payoff" in keysOf(bear))
    }

    @Test
    fun `removal_artifact_enchant matches Naturalize but not a vanilla creature spot removal spell`() {
        val naturalize = createCard(
            "Instant",
            "Destroy target artifact or enchantment.",
            name = "Naturalize",
        )
        val doomBlade = createCard(
            "Instant",
            "Destroy target nonblack creature.",
            name = "Doom Blade",
        )

        assertTrue("removal_artifact_enchant" in keysOf(naturalize))
        assertFalse("removal_artifact_enchant" in keysOf(bear))
        assertFalse("removal_artifact_enchant" in keysOf(doomBlade))
    }
}
