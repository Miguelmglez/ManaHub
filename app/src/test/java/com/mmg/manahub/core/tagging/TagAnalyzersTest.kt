package com.mmg.manahub.core.tagging

import com.mmg.manahub.core.model.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagAnalyzersTest {

    private fun createCard(
        typeLine: String,
        oracleText: String? = null,
        name: String = "Test Card",
    ): Card {
        return Card(
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
            scryfallUri = "https://test.com"
        )
    }

    private fun keysOf(card: Card): Set<String> =
        StrategyAnalyzer.analyze(card).map { it.tag.key }.toSet()

    // ── TypeLineAnalyzer ─────────────────────────────────────────────────────

    @Test
    fun `TypeLineAnalyzer combines Basic and Land into basic_land`() {
        val forest = createCard("Basic Land — Forest")
        val tags = TypeLineAnalyzer.analyze(forest)

        assertTrue(tags.any { it.tag.key == "basic_land" })
        assertTrue(tags.any { it.tag.key == "forest" })
        assertFalse(tags.any { it.tag.key == "basic" })
        assertFalse(tags.any { it.tag.key == "land" })
    }

    // ── Ramp / basic-land exclusion via typeLineNoneOf ───────────────────────

    @Test
    fun `StrategyAnalyzer excludes ramp tag for Basic Lands`() {
        // A basic Forest taps for mana but must NOT be ramp (typeLineNoneOf basic).
        val forest = createCard("Basic Land — Forest", "({T}: Add {G}.)")
        assertFalse("Basic Lands should not be tagged as ramp", "ramp" in keysOf(forest))
    }

    @Test
    fun `StrategyAnalyzer tags ramp for fetch-style non-basic lands and not tutor`() {
        val rampantGrowth = createCard(
            "Sorcery",
            "Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.",
        )
        val keys = keysOf(rampantGrowth)
        assertTrue("Fetching a land to the battlefield is ramp", "ramp" in keys)
        assertFalse("Land-fetch ramp must not also be a tutor", "tutor" in keys)
    }

    @Test
    fun `StrategyAnalyzer tags tutor for generic library search`() {
        val demonicTutor = createCard(
            "Sorcery",
            "Search your library for a card, put that card into your hand, then shuffle.",
        )
        assertTrue("tutor" in keysOf(demonicTutor))
    }

    // ── OR-semantics regression (lifegain / burn / tokens) ───────────────────

    @Test
    fun `gain control of target creature is NOT lifegain`() {
        val control = createCard(
            "Sorcery",
            "Gain control of target creature for as long as you control this enchantment.",
        )
        assertFalse("'gain control' must not match lifegain", "lifegain" in keysOf(control))
    }

    @Test
    fun `Lightning Bolt is burn and removal`() {
        val bolt = createCard("Instant", "~ deals 3 damage to any target.", name = "Lightning Bolt")
        val keys = keysOf(bolt)
        assertTrue("Bolt is burn", "burn" in keys)
        assertTrue("Damage to any target is removal", "removal" in keys)
    }

    @Test
    fun `vanilla combat-damage text is NOT burn`() {
        val bear = createCard(
            "Creature — Bear",
            "Whenever this creature deals combat damage to a player, draw a card.",
        )
        assertFalse("'deals combat damage' must not match burn", "burn" in keysOf(bear))
    }

    // ── Reminder text stripping ──────────────────────────────────────────────

    @Test
    fun `deathtouch reminder text does not produce removal`() {
        val card = createCard(
            "Creature — Snake",
            "Deathtouch (Any amount of damage this deals to a creature is enough to destroy it.)",
        )
        assertFalse("reminder-text 'destroy' must not match removal", "removal" in keysOf(card))
    }

    // ── Mana producers (type-line gated) ─────────────────────────────────────

    @Test
    fun `basic Forest is neither mana_rock nor mana_dork nor ramp`() {
        val forest = createCard("Basic Land — Forest", "({T}: Add {G}.)")
        val keys = keysOf(forest)
        assertFalse("ramp" in keys)
        assertFalse("mana_rock" in keys)
        assertFalse("mana_dork" in keys)
    }

    @Test
    fun `Sol Ring is a mana_rock`() {
        val solRing = createCard("Artifact", "{T}: Add {C}{C}.", name = "Sol Ring")
        assertTrue("mana_rock" in keysOf(solRing))
    }

    @Test
    fun `Llanowar Elves is a mana_dork`() {
        val elves = createCard("Creature — Elf Druid", "{T}: Add {G}.", name = "Llanowar Elves")
        val keys = keysOf(elves)
        assertTrue("mana_dork" in keys)
        assertFalse("creature mana producer is not a rock", "mana_rock" in keys)
    }

    // ── New strategy detection ───────────────────────────────────────────────

    @Test
    fun `reanimation spell is reanimator`() {
        val reanimate = createCard(
            "Sorcery",
            "Return target creature card from your graveyard to the battlefield.",
        )
        assertTrue("reanimator" in keysOf(reanimate))
    }

    @Test
    fun `wheel effect is detected`() {
        val windfall = createCard(
            "Sorcery",
            "Each player discards their hand, then draws seven cards.",
        )
        assertTrue("wheel" in keysOf(windfall))
    }

    @Test
    fun `extra turn spell is extra_turns`() {
        val timeWalk = createCard("Sorcery", "Take an extra turn after this one.")
        assertTrue("extra_turns" in keysOf(timeWalk))
    }

    @Test
    fun `mill effect is detected`() {
        val card = createCard("Instant", "Target player mills three cards.")
        assertTrue("mill" in keysOf(card))
    }

    // ── Self-name normalization (card name → ~) ──────────────────────────────

    @Test
    fun `self-referential ETB trigger matches after name normalization`() {
        val mulldrifter = createCard(
            "Creature — Elemental",
            "When Mulldrifter enters the battlefield, draw two cards.",
            name = "Mulldrifter",
        )
        val keys = keysOf(mulldrifter)
        assertTrue("etb should match 'when ~ enters'", "etb" in keys)
        assertTrue("card draw should still be detected", "card_draw" in keys)
    }

    // ── Override rule-line parsing round-trip ─────────────────────────────────

    @Test
    fun `override rule line gain plus life plus negation round-trips through dictionary`() {
        // Parse the user syntax into a DetectionRule.
        val rule = TagDictionary.parseRuleLine("gain + life + !gain control")
        requireNotNull(rule)
        assertEquals(listOf("gain", "life"), rule.allOf)
        assertEquals(listOf("gain control"), rule.noneOf)

        // Apply as an override that REPLACES the lifegain rules, then verify behavior.
        TagDictionary.applyOverrides(
            listOf(
                TagOverride(
                    key      = "lifegain",
                    labels   = mapOf("en" to "Lifegain"),
                    patterns = listOf("gain + life + !gain control"),
                )
            )
        )
        try {
            val gainsLife = createCard("Instant", "You gain 5 life.")
            assertTrue("lifegain" in keysOf(gainsLife))

            val gainControl = createCard("Sorcery", "Gain control of target creature. You gain its life.")
            // "gain control" present → excluded by the override's noneOf.
            assertFalse("lifegain" in keysOf(gainControl))
        } finally {
            TagDictionary.applyOverrides(emptyList())
        }
    }
}
