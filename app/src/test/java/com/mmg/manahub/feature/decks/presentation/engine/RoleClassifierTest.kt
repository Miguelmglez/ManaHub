package com.mmg.manahub.feature.decks.presentation.engine

import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.TagCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for RoleClassifier.classify().
 *
 * Strategy:
 *  - Tag-based paths: each role triggered by its canonical tag(s).
 *  - Oracle fallback paths: triggered when no relevant tag is present.
 *  - LAND short-circuit: verified first, prevents oracle scanning.
 *  - SYNERGY: strategy/tribal tag on a non-functional card.
 *  - THREAT: creature with power >= 3, no functional role.
 *  - FILLER: nothing matches.
 *  - Multi-role: a card can carry multiple roles simultaneously.
 */
class RoleClassifierTest {

    private lateinit var classifier: RoleClassifier

    @Before
    fun setUp() {
        classifier = RoleClassifier()
    }

    // ── Group 1: LAND short-circuit ───────────────────────────────────────────

    @Test
    fun `given land typeLine when classify then returns only LAND`() {
        // Arrange
        val c = landCard(id = "land-1")

        // Act
        val roles = classifier.classify(c)

        // Assert
        assertEquals(setOf(DeckRole.LAND), roles)
    }

    @Test
    fun `given land with oracle text and tags when classify then still returns only LAND`() {
        // Arrange — land with ramp oracle text; short-circuit prevents RAMP classification
        val c = card(
            typeLine   = "Basic Land — Forest",
            oracleText = "add {G}",
            tags       = listOf(tagManaRock),
        )

        // Act
        val roles = classifier.classify(c)

        // Assert
        assertEquals(setOf(DeckRole.LAND), roles)
    }

    // ── Group 2: Tag-based RAMP ───────────────────────────────────────────────

    @Test
    fun `given mana_rock tag when classify then contains RAMP`() {
        val c = card(tags = listOf(tagManaRock))
        assertTrue(classifier.classify(c).contains(DeckRole.RAMP))
    }

    @Test
    fun `given mana_dork tag when classify then contains RAMP`() {
        val c = card(tags = listOf(tagManaDork), typeLine = "Creature", power = "1", toughness = "1")
        assertTrue(classifier.classify(c).contains(DeckRole.RAMP))
    }

    @Test
    fun `given ramp archetype tag when classify then contains RAMP`() {
        val c = card(tags = listOf(tagRamp))
        assertTrue(classifier.classify(c).contains(DeckRole.RAMP))
    }

    // ── Group 3: Tag-based CARD_ADVANTAGE ────────────────────────────────────

    @Test
    fun `given card_draw tag when classify then contains CARD_ADVANTAGE`() {
        val c = card(tags = listOf(tagDraw))
        assertTrue(classifier.classify(c).contains(DeckRole.CARD_ADVANTAGE))
    }

    // ── Group 4: Tag-based SPOT_REMOVAL ──────────────────────────────────────

    @Test
    fun `given removal tag when classify then contains SPOT_REMOVAL`() {
        val c = card(tags = listOf(tagRemoval))
        assertTrue(classifier.classify(c).contains(DeckRole.SPOT_REMOVAL))
    }

    // ── Group 5: Tag-based BOARD_WIPE ────────────────────────────────────────

    @Test
    fun `given board_wipe tag when classify then contains BOARD_WIPE`() {
        val c = card(tags = listOf(tagWrath))
        assertTrue(classifier.classify(c).contains(DeckRole.BOARD_WIPE))
    }

    // ── Group 6: Tag-based INTERACTION ───────────────────────────────────────

    @Test
    fun `given counterspell tag when classify then contains INTERACTION`() {
        val c = card(tags = listOf(tagCounterspell))
        assertTrue(classifier.classify(c).contains(DeckRole.INTERACTION))
    }

    @Test
    fun `given protection tag when classify then contains INTERACTION`() {
        val c = card(tags = listOf(tagProtection))
        assertTrue(classifier.classify(c).contains(DeckRole.INTERACTION))
    }

    @Test
    fun `given stax tag when classify then contains INTERACTION`() {
        val c = card(tags = listOf(tagStax))
        assertTrue(classifier.classify(c).contains(DeckRole.INTERACTION))
    }

    // ── Group 7: Tag-based TUTOR ──────────────────────────────────────────────

    @Test
    fun `given tutor tag when classify then contains TUTOR`() {
        val c = card(tags = listOf(tagTutor))
        assertTrue(classifier.classify(c).contains(DeckRole.TUTOR))
    }

    // ── Group 8: Tag-based PAYOFF ─────────────────────────────────────────────

    @Test
    fun `given win_con tag when classify then contains PAYOFF`() {
        val c = card(tags = listOf(tagWinCon))
        assertTrue(classifier.classify(c).contains(DeckRole.PAYOFF))
    }

    // ── Group 9: userTags also trigger roles ──────────────────────────────────

    @Test
    fun `given mana_rock in userTags when classify then contains RAMP`() {
        val c = card(userTags = listOf(tagManaRock))
        assertTrue(classifier.classify(c).contains(DeckRole.RAMP))
    }

    @Test
    fun `given win_con in userTags when classify then contains PAYOFF`() {
        val c = card(userTags = listOf(tagWinCon))
        assertTrue(classifier.classify(c).contains(DeckRole.PAYOFF))
    }

    // ── Group 10: Oracle fallback – RAMP ─────────────────────────────────────

    @Test
    fun `given no ramp tag but oracle add-mana text when classify then contains RAMP`() {
        val c = card(oracleText = "Tap: add {G}{G}.")
        assertTrue(classifier.classify(c).contains(DeckRole.RAMP))
    }

    @Test
    fun `given no ramp tag but oracle search basic land text when classify then contains RAMP`() {
        val c = card(oracleText = "Search your library for a basic land card and put it onto the battlefield.")
        assertTrue(classifier.classify(c).contains(DeckRole.RAMP))
    }

    @Test
    fun `given ramp tag already present oracle fallback does not duplicate RAMP`() {
        val c = card(tags = listOf(tagManaRock), oracleText = "Tap: add {G}.")
        assertEquals(1, classifier.classify(c).count { it == DeckRole.RAMP })
    }

    // ── Group 11: Oracle fallback – CARD_ADVANTAGE ───────────────────────────

    @Test
    fun `given no draw tag but oracle draw a card text when classify then contains CARD_ADVANTAGE`() {
        val c = card(oracleText = "Draw a card.")
        assertTrue(classifier.classify(c).contains(DeckRole.CARD_ADVANTAGE))
    }

    @Test
    fun `given no draw tag but oracle draw two text when classify then contains CARD_ADVANTAGE`() {
        val c = card(oracleText = "Draw two cards.")
        assertTrue(classifier.classify(c).contains(DeckRole.CARD_ADVANTAGE))
    }

    @Test
    fun `given no draw tag but oracle draw three text when classify then contains CARD_ADVANTAGE`() {
        val c = card(oracleText = "Draw three cards, then discard two.")
        assertTrue(classifier.classify(c).contains(DeckRole.CARD_ADVANTAGE))
    }

    // ── Group 12: Oracle fallback – BOARD_WIPE ───────────────────────────────

    @Test
    fun `given no wrath tag but oracle destroy all when classify then contains BOARD_WIPE`() {
        val c = card(oracleText = "Destroy all creatures.")
        assertTrue(classifier.classify(c).contains(DeckRole.BOARD_WIPE))
    }

    @Test
    fun `given no wrath tag but oracle exile all when classify then contains BOARD_WIPE`() {
        val c = card(oracleText = "Exile all permanents target player controls.")
        assertTrue(classifier.classify(c).contains(DeckRole.BOARD_WIPE))
    }

    @Test
    fun `given no wrath tag but oracle each player sacrifices when classify then contains BOARD_WIPE`() {
        val c = card(oracleText = "Each player sacrifices three creatures.")
        assertTrue(classifier.classify(c).contains(DeckRole.BOARD_WIPE))
    }

    // ── Group 13: Oracle fallback – SPOT_REMOVAL ─────────────────────────────

    @Test
    fun `given no removal tag but oracle destroy target when classify then contains SPOT_REMOVAL`() {
        val c = card(oracleText = "Destroy target creature.")
        assertTrue(classifier.classify(c).contains(DeckRole.SPOT_REMOVAL))
    }

    @Test
    fun `given no removal tag but oracle exile target when classify then contains SPOT_REMOVAL`() {
        val c = card(oracleText = "Exile target artifact or enchantment.")
        assertTrue(classifier.classify(c).contains(DeckRole.SPOT_REMOVAL))
    }

    @Test
    fun `given oracle destroy all then SPOT_REMOVAL is NOT also added`() {
        // Board-wipe oracle must not also be classified as spot removal
        val c = card(oracleText = "Destroy all creatures.")
        val roles = classifier.classify(c)
        assertTrue(roles.contains(DeckRole.BOARD_WIPE))
        assertFalse("SPOT_REMOVAL must not be added when BOARD_WIPE is detected", roles.contains(DeckRole.SPOT_REMOVAL))
    }

    // ── Group 14: Oracle fallback – INTERACTION ───────────────────────────────

    @Test
    fun `given no interaction tag but oracle counter target when classify then contains INTERACTION`() {
        val c = card(oracleText = "Counter target spell.")
        assertTrue(classifier.classify(c).contains(DeckRole.INTERACTION))
    }

    // ── Group 15: Oracle fallback – TUTOR ────────────────────────────────────

    @Test
    fun `given no tutor tag but oracle search library for a card when classify then contains TUTOR`() {
        val c = card(oracleText = "Search your library for a card and put it into your hand.")
        assertTrue(classifier.classify(c).contains(DeckRole.TUTOR))
    }

    @Test
    fun `given search basic land oracle and no ramp tag then RAMP added but TUTOR not added by tutor oracle`() {
        // "search your library for a basic land" matches RAMP oracle; the tutor fallback
        // checks for "search your library for a card" which is NOT a substring of the basic land variant.
        val c = card(oracleText = "Search your library for a basic land card and put it onto the battlefield.")
        val roles = classifier.classify(c)
        assertTrue(roles.contains(DeckRole.RAMP))
        // The tutor oracle check requires "search your library for a card" literally;
        // "basic land card" does NOT contain that exact phrase.
        assertFalse("TUTOR must not fire on 'search for a basic land' oracle", roles.contains(DeckRole.TUTOR))
    }

    // ── Group 16: SYNERGY path ────────────────────────────────────────────────

    @Test
    fun `given strategy tag and no functional role when classify then contains SYNERGY`() {
        val c = card(tags = listOf(tagTokens))   // STRATEGY category
        assertTrue(classifier.classify(c).contains(DeckRole.SYNERGY))
    }

    @Test
    fun `given tribal category tag and no functional role when classify then contains SYNERGY`() {
        val tribalTag = CardTag("goblin", TagCategory.TRIBAL)
        val c = card(tags = listOf(tribalTag))
        assertTrue(classifier.classify(c).contains(DeckRole.SYNERGY))
    }

    @Test
    fun `given strategy tag AND functional role when classify then no SYNERGY`() {
        // If a card has ramp + tokens it should be RAMP, not also SYNERGY
        val c = card(tags = listOf(tagRamp, tagTokens))
        assertFalse(classifier.classify(c).contains(DeckRole.SYNERGY))
    }

    // ── Group 17: THREAT path ─────────────────────────────────────────────────

    @Test
    fun `given creature with power 3 and no functional role when classify then contains THREAT`() {
        val c = card(typeLine = "Creature — Warrior", power = "3", toughness = "3")
        assertTrue(classifier.classify(c).contains(DeckRole.THREAT))
    }

    @Test
    fun `given creature with power 5 and no functional role when classify then contains THREAT`() {
        val c = card(typeLine = "Creature — Dragon", power = "5", toughness = "4")
        assertTrue(classifier.classify(c).contains(DeckRole.THREAT))
    }

    @Test
    fun `given creature with power 2 and no functional role when classify then NOT THREAT`() {
        val c = card(typeLine = "Creature — Elf", power = "2", toughness = "2")
        assertFalse(classifier.classify(c).contains(DeckRole.THREAT))
    }

    @Test
    fun `given creature with power null and no functional role when classify then NOT THREAT`() {
        val c = card(typeLine = "Creature — Ooze", power = null, toughness = "*")
        assertFalse(classifier.classify(c).contains(DeckRole.THREAT))
    }

    @Test
    fun `given creature with power 3 but also has win_con tag when classify then NOT THREAT`() {
        // win_con gives PAYOFF (functional) so threat check is skipped
        val c = card(typeLine = "Creature — Dragon", power = "5", tags = listOf(tagWinCon))
        val roles = classifier.classify(c)
        assertTrue(roles.contains(DeckRole.PAYOFF))
        assertFalse("THREAT must not be added when card already has a functional role", roles.contains(DeckRole.THREAT))
    }

    // ── Group 18: FILLER fallback ─────────────────────────────────────────────

    @Test
    fun `given card with no tags no oracle no creature body when classify then returns FILLER`() {
        val c = card(typeLine = "Enchantment", oracleText = "You gain 1 life.", power = null)
        assertEquals(setOf(DeckRole.FILLER), classifier.classify(c))
    }

    @Test
    fun `given creature with power 1 and no tags no matching oracle when classify then returns FILLER`() {
        val c = card(typeLine = "Creature — Elf", power = "1", toughness = "1", oracleText = null)
        assertEquals(setOf(DeckRole.FILLER), classifier.classify(c))
    }

    // ── Group 19: Multi-role cards ─────────────────────────────────────────────

    @Test
    fun `given mana_rock and card_draw tags when classify then contains both RAMP and CARD_ADVANTAGE`() {
        val c = card(tags = listOf(tagManaRock, tagDraw))
        val roles = classifier.classify(c)
        assertTrue(roles.contains(DeckRole.RAMP))
        assertTrue(roles.contains(DeckRole.CARD_ADVANTAGE))
    }

    @Test
    fun `given win_con and counterspell tags when classify then contains PAYOFF and INTERACTION`() {
        val c = card(tags = listOf(tagWinCon, tagCounterspell))
        val roles = classifier.classify(c)
        assertTrue(roles.contains(DeckRole.PAYOFF))
        assertTrue(roles.contains(DeckRole.INTERACTION))
    }

    // ── Group 20: Case insensitivity of oracle text ───────────────────────────

    @Test
    fun `given uppercase oracle DESTROY ALL when classify then contains BOARD_WIPE`() {
        val c = card(oracleText = "DESTROY ALL CREATURES.")
        assertTrue(classifier.classify(c).contains(DeckRole.BOARD_WIPE))
    }

    @Test
    fun `given mixed-case oracle Draw a Card when classify then contains CARD_ADVANTAGE`() {
        val c = card(oracleText = "Draw a Card.")
        assertTrue(classifier.classify(c).contains(DeckRole.CARD_ADVANTAGE))
    }
}
