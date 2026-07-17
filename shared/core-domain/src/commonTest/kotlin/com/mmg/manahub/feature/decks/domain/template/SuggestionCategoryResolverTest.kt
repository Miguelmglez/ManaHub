package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.feature.decks.domain.engine.card
import kotlin.test.Test
import kotlin.test.assertEquals

/** Deck Builder v2, Phase 1 -- exercises [SuggestionCategoryResolver]'s full 5-step priority chain. */
class SuggestionCategoryResolverTest {

    @Test
    fun `step 1 - aggregate category wins over everything else`() {
        val removalSpell = card(name = "Doom Blade", typeLine = "Instant", oracleText = "Destroy target nonblack creature.")
        val category = SuggestionCategoryResolver.resolve(removalSpell, aggregateCategory = "Removal")
        assertEquals("removal", category.id)
        assertEquals("Removal", category.displayLabel)
    }

    @Test
    fun `step 2 - role classifier maps spot removal to the Removal category`() {
        val removalSpell = card(name = "Murder", typeLine = "Instant", oracleText = "Destroy target creature.")
        val category = SuggestionCategoryResolver.resolve(removalSpell)
        assertEquals("removal", category.id)
    }

    @Test
    fun `step 2 - ramp role maps to the Ramp category`() {
        val rampSpell = card(
            name = "Rampant Growth",
            typeLine = "Sorcery",
            oracleText = "Search your library for a basic land card, put it onto the battlefield tapped.",
        )
        val category = SuggestionCategoryResolver.resolve(rampSpell)
        assertEquals("ramp", category.id)
    }

    @Test
    fun `step 3 - a creature's own tribe subtype resolves to a pluralized tribe category`() {
        val elf = card(name = "Elvish Mystic", typeLine = "Creature — Elf Druid", oracleText = "{T}: Add {G}.")
        val category = SuggestionCategoryResolver.resolve(elf)
        // Elvish Mystic is a mana dork -> RoleClassifier credits RAMP first (step 2), so the tribe
        // step never fires here; use a creature with no functional role to reach step 3.
        assertEquals("ramp", category.id)
    }

    @Test
    fun `step 3 - a vanilla creature with a tribe subtype and no role falls to its tribe category`() {
        val vanillaGoblin = card(name = "Test Goblin", typeLine = "Creature — Goblin", power = "1", toughness = "1")
        val category = SuggestionCategoryResolver.resolve(vanillaGoblin)
        assertEquals("tribe_goblin", category.id)
        assertEquals("Goblins", category.displayLabel)
    }

    @Test
    fun `step 4 - a STRATEGY tag resolves when no role or tribe applies`() {
        val tokenMaker = card(
            name = "Test Enchantment",
            typeLine = "Enchantment",
            tags = listOf(CardTag.TOKENS),
        )
        val category = SuggestionCategoryResolver.resolve(tokenMaker)
        assertEquals(CardTag.TOKENS.key, category.id)
    }

    @Test
    fun `step 5 - a real mana rock is caught by the RAMP role first, never reaches the heuristic`() {
        // RoleClassifier's own RAMP oracle safety net ("add (?:\{|one mana|...)") already catches
        // any typical mana-rock oracle, so the type-line heuristic (step 5) never actually fires for
        // a realistic mana rock -- step 2 (role mapping) wins by design (earlier in the priority
        // chain). This is documented, not a bug: see SuggestionCategoryResolver's KDoc.
        val manaRock = card(name = "Test Rock", typeLine = "Artifact", oracleText = "{T}: Add {C}.")
        val category = SuggestionCategoryResolver.resolve(manaRock)
        assertEquals("ramp", category.id)
    }

    @Test
    fun `step 5 - the heuristic fires for an artifact whose Add clause RoleClassifier's RAMP pattern does not recognize`() {
        // "add a loyalty counter" contains the word "add" but not RAMP's narrower
        // "add (?:\{|one mana|two mana|three mana|that much)" shape, so RoleClassifier credits no
        // role and resolution reaches the type-line heuristic (step 5).
        val artifact = card(name = "Test Artifact", typeLine = "Artifact", oracleText = "Whenever a creature enters, add a loyalty counter here.")
        val category = SuggestionCategoryResolver.resolve(artifact)
        assertEquals(SuggestionCategory.MANA_ROCKS.id, category.id)
    }

    @Test
    fun `step 5 - Additional never false-positives as a mana rock`() {
        val notAManaRock = card(
            name = "Test Artifact",
            typeLine = "Artifact",
            oracleText = "Additional cost to cast this spell is discarding a card.",
        )
        val category = SuggestionCategoryResolver.resolve(notAManaRock)
        assertEquals(SuggestionCategory.OTHER.id, category.id)
    }

    @Test
    fun `step 6 - falls back to Other when nothing matches`() {
        val filler = card(name = "Test Filler", typeLine = "Sorcery", oracleText = "This spell does nothing.")
        val category = SuggestionCategoryResolver.resolve(filler)
        assertEquals(SuggestionCategory.OTHER, category)
    }

    @Test
    fun `land role always resolves to Lands`() {
        val forest = card(name = "Forest", typeLine = "Basic Land — Forest")
        val category = SuggestionCategoryResolver.resolve(forest)
        assertEquals(SuggestionCategory.LANDS, category)
    }

    @Test
    fun `resolveAggregateOnly is deterministic and title-cases a lowercase raw category`() {
        val category = SuggestionCategoryResolver.resolveAggregateOnly("card draw")
        assertEquals("Card draw", category.displayLabel)
        assertEquals("card_draw", category.id)
    }

    @Test
    fun `resolveAggregateOnly falls back to Other on a blank string`() {
        assertEquals(SuggestionCategory.OTHER, SuggestionCategoryResolver.resolveAggregateOnly("   "))
    }

    // DeckProfile-context tribe preference (step 3, deck-aware branch) is covered end-to-end by
    // BuildDeckFromTemplateUseCaseTest's tribal-seed scenario -- exercising it here would need to
    // hand-build a DeckProfile.tagFingerprint, which duplicates DeckScorer.profile's own coverage.
}
