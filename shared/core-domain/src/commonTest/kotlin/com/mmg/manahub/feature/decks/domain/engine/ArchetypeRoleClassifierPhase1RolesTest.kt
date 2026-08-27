package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deck Analysis Engine v3, Phase 1 (spec §5.1-§5.2) — [ArchetypeRoleClassifier] wiring tests:
 * the ONE genuinely new structural matcher ([sacrificeFodderMatcher] via `sacrifice_fodder`),
 * the 4 reused pre-existing tags newly wired into [ArchetypeRoleClassifier.ROLE_SPECS] (`anthem`,
 * `untapper`, `spell_copy`, `equipment`), the `tribe_members` real-classification fix (gap #1),
 * the [RoleSpec.produces]/[RoleSpec.consumes]/[RoleSpec.amplifies] axis table, and a regression
 * proof that `equipment_or_aura`/`mill_engine` (the two derived-union roles) still compute
 * IDENTICAL values to before this phase.
 *
 * Oracle-text-level detection correctness for the 11 brand-new [TagDictionary] entries is covered
 * separately in `:shared:core-data`'s `DeckAnalysisV3RoleTagsTest` (StrategyAnalyzer level) — this
 * file only exercises [ArchetypeRoleClassifier.classify]'s [RoleSpec] wiring (which reads
 * PRE-APPLIED `card.tags`, not live oracle text, for every tag-backed role).
 */
class ArchetypeRoleClassifierPhase1RolesTest {

    private fun roleTag(key: String): CardTag = CardTag(key, TagCategory.ROLE)

    // ── sacrifice_fodder (new structural matcher) ──────────────────────────────────────────

    @Test
    fun `sacrifice_fodder matches a cheap creature with an ETB trigger`() {
        // Elvish Visionary: Creature -- Elf Shaman, MV 2, "When Elvish Visionary enters the
        // battlefield, draw a card."
        val elvishVisionary = card(
            typeLine = "Creature — Elf Shaman",
            cmc = 2.0,
            oracleText = "When this creature enters the battlefield, draw a card.",
        )

        assertEquals(0.7f, ArchetypeRoleClassifier.classify(elvishVisionary)["sacrifice_fodder"])
    }

    @Test
    fun `sacrifice_fodder matches a token generator via the token_generator tag`() {
        val tokenMaker = card(typeLine = "Enchantment", cmc = 3.0, tags = listOf(roleTag("token_generator")))

        assertEquals(0.6f, ArchetypeRoleClassifier.classify(tokenMaker)["sacrifice_fodder"])
    }

    @Test
    fun `sacrifice_fodder does NOT match a vanilla bear`() {
        val bear = card(typeLine = "Creature — Bear", cmc = 2.0, power = "2", toughness = "2")

        assertNull(ArchetypeRoleClassifier.classify(bear)["sacrifice_fodder"])
    }

    @Test
    fun `sacrifice_fodder does NOT match an expensive creature with a death trigger`() {
        // MV too high (> 2) disqualifies the structural branch; no token_generator tag either.
        val expensive = card(
            typeLine = "Creature — Giant",
            cmc = 6.0,
            oracleText = "When this creature dies, draw a card.",
        )

        assertNull(ArchetypeRoleClassifier.classify(expensive)["sacrifice_fodder"])
    }

    // ── Reused pre-existing tags, newly wired (anthem / untapper / spell_copy / equipment) ──

    @Test
    fun `anthem is wired -- matches a tagged card, not an untagged one`() {
        val tagged = card(tags = listOf(roleTag("anthem")))
        val untagged = card()

        assertEquals(1f, ArchetypeRoleClassifier.classify(tagged)["anthem"])
        assertNull(ArchetypeRoleClassifier.classify(untagged)["anthem"])
    }

    @Test
    fun `untapper is wired -- matches a tagged card, not an untagged one`() {
        val tagged = card(tags = listOf(roleTag("untapper")))
        val untagged = card()

        assertEquals(1f, ArchetypeRoleClassifier.classify(tagged)["untapper"])
        assertNull(ArchetypeRoleClassifier.classify(untagged)["untapper"])
    }

    @Test
    fun `spell_copy is wired -- matches a tagged card, not an untagged one`() {
        val tagged = card(tags = listOf(CardTag("spell_copy", TagCategory.STRATEGY)))
        val untagged = card()

        assertEquals(1f, ArchetypeRoleClassifier.classify(tagged)["spell_copy"])
        assertNull(ArchetypeRoleClassifier.classify(untagged)["spell_copy"])
    }

    @Test
    fun `equipment is wired -- matches a tagged card, not an untagged one`() {
        val tagged = card(tags = listOf(roleTag("equipment")))
        val untagged = card()

        assertEquals(1f, ArchetypeRoleClassifier.classify(tagged)["equipment"])
        assertNull(ArchetypeRoleClassifier.classify(untagged)["equipment"])
    }

    // ── tribe_members (fixes gap #1: real classification via deckRoleCounts/Attribution) ────

    @Test
    fun `tribe_members reports the dominant tribe's copy count in deckRoleCounts`() {
        val goblin = card(id = "g1", typeLine = "Creature — Goblin", cmc = 1.0)
        val human = card(id = "h1", typeLine = "Creature — Human Soldier", cmc = 2.0)
        val mainboard = listOf(entry(goblin, quantity = 4), entry(human, quantity = 2))

        val counts = ArchetypeRoleClassifier.deckRoleCounts(mainboard)

        assertEquals(4, counts["tribe_members"])
    }

    @Test
    fun `tribe_members attributes to exactly the dominant tribe's own cards`() {
        val goblin = card(id = "g1", typeLine = "Creature — Goblin", cmc = 1.0)
        val human = card(id = "h1", typeLine = "Creature — Human Soldier", cmc = 2.0)
        val mainboard = listOf(entry(goblin, quantity = 4), entry(human, quantity = 2))

        val contributions = ArchetypeRoleClassifier.deckRoleAttribution(mainboard)["tribe_members"].orEmpty()

        assertEquals(1, contributions.size)
        assertEquals("g1", contributions.single().scryfallId)
        assertEquals(4, contributions.single().quantity)
    }

    @Test
    fun `tribe_members is absent for a creatureless mainboard`() {
        val land = card(id = "l1", typeLine = "Land", cmc = 0.0)
        val mainboard = listOf(entry(land, quantity = 10))

        assertFalse("tribe_members" in ArchetypeRoleClassifier.deckRoleCounts(mainboard))
        assertFalse("tribe_members" in ArchetypeRoleClassifier.deckRoleAttribution(mainboard))
    }

    // ── Axis table (spec §5.2) spot checks ───────────────────────────────────────────────────

    private val specsByKey = ArchetypeRoleClassifier.ROLE_SPECS.associateBy { it.key }

    @Test
    fun `anthem amplifies TOKENS, COUNTERS, TRIBE and ATTACK`() {
        assertEquals(setOf("TOKENS", "COUNTERS", "TRIBE", "ATTACK"), specsByKey.getValue("anthem").amplifies)
    }

    @Test
    fun `token_generator produces TOKENS, ETB and DEATH`() {
        assertEquals(setOf("TOKENS", "ETB", "DEATH"), specsByKey.getValue("token_generator").produces)
    }

    @Test
    fun `recursion consumes GRAVEYARD and amplifies DEATH`() {
        val recursion = specsByKey.getValue("recursion")
        assertEquals(setOf("GRAVEYARD"), recursion.consumes)
        assertEquals(setOf("DEATH"), recursion.amplifies)
    }

    @Test
    fun `evasion produces ATTACK and consumes ATTACHED`() {
        val evasion = specsByKey.getValue("evasion")
        assertEquals(setOf("ATTACK"), evasion.produces)
        assertEquals(setOf("ATTACHED"), evasion.consumes)
    }

    @Test
    fun `counters_source produces COUNTERS and consumes PLANESWALKERS`() {
        val countersSource = specsByKey.getValue("counters_source")
        assertEquals(setOf("COUNTERS"), countersSource.produces)
        assertEquals(setOf("PLANESWALKERS"), countersSource.consumes)
    }

    @Test
    fun `combat_payoff consumes ATTACHED, ATTACK and TOKENS`() {
        assertEquals(setOf("ATTACHED", "ATTACK", "TOKENS"), specsByKey.getValue("combat_payoff").consumes)
    }

    @Test
    fun `equipment_or_aura and mill_engine carry no axis metadata -- axis fields live on their split children`() {
        assertEquals(emptySet<AxisKey>(), specsByKey.getValue("equipment_or_aura").produces)
        assertEquals(emptySet<AxisKey>(), specsByKey.getValue("equipment_or_aura").consumes)
        assertEquals(emptySet<AxisKey>(), specsByKey.getValue("mill_engine").produces)
        assertEquals(emptySet<AxisKey>(), specsByKey.getValue("mill_engine").consumes)
    }

    // ── Regression proof: equipment_or_aura / mill_engine derived unions are UNCHANGED ──────

    @Test
    fun `equipment_or_aura still returns full confidence for an Equipment card, unchanged by the new equipment role`() {
        val bonesplitter = card(
            typeLine = "Artifact — Equipment",
            oracleText = "Equipped creature gets +2/+0.\nEquip {1}",
            tags = listOf(roleTag("equipment")),
        )

        val classified = ArchetypeRoleClassifier.classify(bonesplitter)

        assertEquals(1f, classified["equipment_or_aura"], "equipment_or_aura's own matcher logic must be byte-identical to before Phase 1")
        assertEquals(1f, classified["equipment"], "the NEW 'equipment' key is additive, not a replacement")
    }

    @Test
    fun `equipment_or_aura still returns the aura_buff tag confidence for a buffing Aura, unchanged`() {
        val rancor = card(
            typeLine = "Enchantment — Aura",
            oracleText = "Enchant creature\nEnchanted creature gets +2/+0 and has trample.",
            tags = listOf(roleTag("aura_buff")),
        )

        assertEquals(1f, ArchetypeRoleClassifier.classify(rancor)["equipment_or_aura"])
    }

    @Test
    fun `mill_engine still fires from its own tag, unaffected by the new mill_self and mill_opponent keys`() {
        val opponentMillCard = card(tags = listOf(roleTag("mill_engine"), roleTag("mill_opponent")))
        val selfMillCard = card(tags = listOf(roleTag("mill_engine"), roleTag("mill_self")))

        assertEquals(1f, ArchetypeRoleClassifier.classify(opponentMillCard)["mill_engine"])
        assertEquals(1f, ArchetypeRoleClassifier.classify(opponentMillCard)["mill_opponent"])
        assertEquals(1f, ArchetypeRoleClassifier.classify(selfMillCard)["mill_engine"])
        assertEquals(1f, ArchetypeRoleClassifier.classify(selfMillCard)["mill_self"])
    }
}
