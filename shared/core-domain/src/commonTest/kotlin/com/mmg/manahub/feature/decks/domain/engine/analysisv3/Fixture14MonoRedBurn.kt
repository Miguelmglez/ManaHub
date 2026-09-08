package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #14 (spec §9) — Mono-Red Burn, Modern, no commander. Anchored on Modern rather than the
 * current Standard metagame per the plan's own "harness must not rot" guidance.
 * Expected: macro AGGRO, no posture, no themes.
 */
fun fixture14MonoRedBurn(): AnalysisV3Fixture {
    val nonland = listOf(
        entry(card(id = "mrb-swiftspear", name = "Monastery Swiftspear", typeLine = "Creature — Human Monk", cmc = 1.0, colorIdentity = listOf("R"), power = "1", toughness = "2"), quantity = 4),
        entry(card(id = "mrb-goblin-guide", name = "Goblin Guide", typeLine = "Creature — Goblin Scout", cmc = 1.0, colorIdentity = listOf("R"), power = "2", toughness = "2"), quantity = 4),
        entry(card(id = "mrb-eidolon", name = "Eidolon of the Great Revel", typeLine = "Enchantment Creature — Nymph", cmc = 2.0, colorIdentity = listOf("R"), power = "2", toughness = "2"), quantity = 4),
        entry(card(id = "mrb-vexing-devil", name = "Vexing Devil", typeLine = "Creature — Devil", cmc = 1.0, colorIdentity = listOf("R"), power = "4", toughness = "3"), quantity = 4),
        // Final engine-correction run, DEFECT 3: every one of these 7 burn spells is real-Magic
        // dual-purpose ("deals X damage to any target/target player") -- CardTag.BURN added
        // alongside the pre-existing CardTag.REMOVAL tags (both are honest: they genuinely ARE
        // removal AND genuinely CAN close the game) so the new `direct_damage` role can see them.
        // Lava Spike ("deals 3 damage to target player or planeswalker") can NEVER target a
        // creature -- CardTag.REMOVAL would misrepresent it, so it gets CardTag.BURN only, an
        // authoring correction (it previously carried no role tag at all).
        entry(card(id = "mrb-bolt", name = "Lightning Bolt", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL, CardTag.BURN)), quantity = 4),
        entry(card(id = "mrb-lava-spike", name = "Lava Spike", typeLine = "Sorcery", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.BURN)), quantity = 4),
        entry(card(id = "mrb-rift-bolt", name = "Rift Bolt", typeLine = "Sorcery", cmc = 3.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL, CardTag.BURN)), quantity = 4),
        entry(card(id = "mrb-wild-slash", name = "Wild Slash", typeLine = "Instant", cmc = 1.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL, CardTag.BURN)), quantity = 4),
        entry(card(id = "mrb-searing-blaze", name = "Searing Blaze", typeLine = "Instant", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL, CardTag.BURN)), quantity = 4),
        entry(card(id = "mrb-fireblast", name = "Fireblast", typeLine = "Instant", cmc = 4.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL, CardTag.BURN)), quantity = 4),
        entry(card(id = "mrb-skewer", name = "Skewer the Critics", typeLine = "Sorcery", cmc = 2.0, colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL, CardTag.BURN)), quantity = 4),
        entry(card(id = "mrb-grim-lavamancer", name = "Grim Lavamancer", typeLine = "Creature — Human Wizard", cmc = 1.0, colorIdentity = listOf("R"), power = "1", toughness = "1"), quantity = 4),
    )
    val mainboard = withBasicsSixty(nonland, "Mountain", "R")
    return AnalysisV3Fixture(
        id = 14, name = "Mono-Red Burn (Modern)", anchor = "Mono-Red Burn",
        format = DeckFormat.MODERN, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.R),
        expectedMacro = "AGGRO", expectedPosture = NO_POSTURE, expectedThemes = NO_THEMES,
    )
}
