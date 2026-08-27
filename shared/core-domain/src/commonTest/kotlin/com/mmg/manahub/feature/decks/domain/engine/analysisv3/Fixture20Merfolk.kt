package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

/**
 * Fixture #20 — mono-Blue Merfolk (Tribal Aggro/Tempo), Modern, no commander. 60-card
 * AGGRO-tribal corpus-expansion workstream (2026-08-26): a second 60-card AGGRO witness (alongside
 * fixture #14, Mono-Red Burn), and the first 60-card fixture to exercise `TRIBE:<subtype>` outside
 * Commander (fixture #1, Edgar Markov, is the only other TRIBAL fixture in the corpus). Merfolk has
 * been a stable Legacy/Modern tribal-aggro archetype since Lord of Atlantis/Master of the Pearl
 * Trident were legal together -- no banned pieces, long-established shell. Lord oracle text follows
 * the SAME "Other Merfolk you control get +1/+1"-shaped convention fixture #1's own vampire lords
 * use (real card text, not a fabricated pattern), so [TribeDeriver.payoffTribeKeys] should read it
 * identically regardless of format.
 * Expected: macro AGGRO, no posture, theme TRIBAL:merfolk.
 */
fun fixture20Merfolk(): AnalysisV3Fixture {
    val nonland = listOf(
        entry(
            card(
                id = "mf-master-pearl-trident", name = "Master of the Pearl Trident", typeLine = "Creature — Merfolk",
                cmc = 1.0, colorIdentity = listOf("U"), power = "1", toughness = "1",
                oracleText = "Other Merfolk creatures you control get +1/+1.",
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "mf-cursecatcher", name = "Cursecatcher", typeLine = "Creature — Merfolk Wizard",
                cmc = 1.0, colorIdentity = listOf("U"), power = "1", toughness = "1",
                oracleText = "Whenever Cursecatcher becomes the target of a spell, counter that spell unless its controller pays {1}.",
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "mf-silvergill-adept", name = "Silvergill Adept", typeLine = "Creature — Merfolk Wizard",
                cmc = 3.0, colorIdentity = listOf("U"), power = "2", toughness = "2",
                oracleText = "When Silvergill Adept enters the battlefield, if you cast it, draw a card.",
                tags = listOf(CardTag.DRAW_ENGINE),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "mf-harbinger-of-the-tides", name = "Harbinger of the Tides", typeLine = "Creature — Merfolk Soldier",
                cmc = 3.0, colorIdentity = listOf("U"), power = "3", toughness = "1",
                oracleText = "This spell costs {2} less to cast if it targets a tapped creature. Flash. When Harbinger of the Tides enters the battlefield, you may return target tapped creature an opponent controls to its owner's hand.",
                tags = listOf(CardTag.REMOVAL),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "mf-merrow-reejerey", name = "Merrow Reejerey", typeLine = "Creature — Merfolk Soldier",
                cmc = 3.0, colorIdentity = listOf("U"), power = "2", toughness = "1",
                oracleText = "Other Merfolk creatures you control get +1/+1. Whenever you cast a Merfolk spell, you may tap or untap target permanent.",
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "mf-kopala", name = "Kopala, Warden of Waves", typeLine = "Legendary Creature — Merfolk Wizard",
                cmc = 1.0, colorIdentity = listOf("U"), power = "1", toughness = "1",
                oracleText = "Other Merfolk you control get +1/+0. Nonblue spells cost {1} more to cast for as long as you control a Legendary creature.",
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "mf-master-of-waves", name = "Master of Waves", typeLine = "Creature — Elemental",
                cmc = 3.0, colorIdentity = listOf("U"), power = "3", toughness = "3",
                oracleText = "This spell costs {1} less to cast for each blue creature you control. At the beginning of combat on your turn, create X 1/1 blue Elemental creature tokens with flying, where X is the number of blue creatures you control. As long as it's your turn, Elemental creatures you control get +1/+1.",
                tags = listOf(roleTag("token_generator"), roleTag("anthem")),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "mf-merfolk-mistbinder", name = "Merfolk Mistbinder", typeLine = "Creature — Merfolk Shaman",
                cmc = 2.0, colorIdentity = listOf("U"), power = "2", toughness = "2",
                oracleText = "Other Merfolk creatures you control get +1/+1.",
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "mf-lord-of-atlantis", name = "Lord of Atlantis", typeLine = "Creature — Merfolk",
                cmc = 2.0, colorIdentity = listOf("U"), power = "2", toughness = "2",
                oracleText = "Other Merfolk creatures you control get +1/+1 and have islandwalk.",
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "mf-merfolk-trickster", name = "Merfolk Trickster", typeLine = "Creature — Merfolk Rogue",
                cmc = 2.0, colorIdentity = listOf("U"), power = "2", toughness = "2",
                oracleText = "Flash. When Merfolk Trickster enters the battlefield, target creature loses all abilities and becomes 0/2 until end of turn.",
                tags = listOf(CardTag.REMOVAL),
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "mf-vapor-snag", name = "Vapor Snag", typeLine = "Instant",
                cmc = 1.0, colorIdentity = listOf("U"),
                oracleText = "Return target creature to its owner's hand. Its controller loses 1 life.",
            ),
            quantity = 4,
        ),
        entry(
            card(
                id = "mf-aether-vial", name = "Aether Vial", typeLine = "Artifact",
                cmc = 1.0, colorIdentity = emptyList(),
                oracleText = "(At the beginning of your upkeep, you may put a charge counter on Aether Vial.) {T}: You may put a creature card with mana value equal to the number of charge counters on Aether Vial from your hand onto the battlefield.",
            ),
            quantity = 4,
        ),
    )
    val mainboard = withBasicsSixty(nonland, "Island", "U")
    return AnalysisV3Fixture(
        id = 20, name = "Merfolk (Tribal Aggro, Modern)", anchor = "Merfolk",
        format = DeckFormat.MODERN, mainboard = mainboard,
        colorIdentity = setOf(ManaColor.U),
        expectedMacro = "AGGRO", expectedPosture = NO_POSTURE, expectedThemes = "TRIBAL:merfolk",
    )
}
