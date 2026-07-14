package com.mmg.manahub.feature.decks.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Multiplatform (commonTest) coverage for [TribeDeriver] — KMP migration remediation P1.4.
 *
 * [TribeDeriver] is a pure, stateless object with no tag-dictionary/Room/network dependency
 * (see the class-level KDoc in `RoleClassifier.kt`: derived `tribe:` keys are RUNTIME-ONLY and
 * never persisted), making it a second good first-pass candidate alongside [ManaBaseAnalyzer]
 * for proving the `feature/decks/domain/engine` package behaves identically on the JVM host and
 * wasmJs. These are NEW tests (no direct JVM-side unit test existed for TribeDeriver in isolation
 * — it was previously exercised only indirectly through DeckScorer/RoleClassifier fixtures), so
 * this suite documents its own behavior spec directly against the KDoc contract.
 */
class TribeDeriverTest {

    // ── subtypeKeys ───────────────────────────────────────────────────────────────

    @Test
    fun subtypeKeysDerivesLowercaseTribePrefixedKeysFromTypeLine() {
        val elfWarrior = card(typeLine = "Legendary Creature — Elf Warrior")
        assertEquals(
            setOf("tribe:elf", "tribe:warrior"),
            TribeDeriver.subtypeKeys(elfWarrior),
        )
    }

    @Test
    fun subtypeKeysIsEmptyForNonCreatureNonTribalTypeLine() {
        val instant = card(typeLine = "Instant")
        assertTrue(TribeDeriver.subtypeKeys(instant).isEmpty())
    }

    @Test
    fun subtypeKeysAppliesToTribalAndKindredTypeLinesToo() {
        val tribal = card(typeLine = "Tribal Sorcery — Goblin")
        val kindred = card(typeLine = "Kindred Instant — Zombie")
        assertEquals(setOf("tribe:goblin"), TribeDeriver.subtypeKeys(tribal))
        assertEquals(setOf("tribe:zombie"), TribeDeriver.subtypeKeys(kindred))
    }

    // ── payoffTribeKeys ───────────────────────────────────────────────────────────

    @Test
    fun payoffTribeKeysMatchesTribeYouControlPhrasing() {
        val lord = card(
            typeLine = "Creature — Human Wizard",
            oracleText = "Other Elves you control get +1/+1.",
        )
        assertTrue("tribe:elf" in TribeDeriver.payoffTribeKeys(lord))
    }

    @Test
    fun payoffTribeKeysNormalizesCommonEnglishPlurals() {
        // "elves"/"wolves" -> tribe:elf/tribe:wolf (-ves -> -f).
        val elfPayoff = card(oracleText = "Elves you control get +1/+1.")
        val wolfPayoff = card(oracleText = "Other wolves you control have trample.")
        assertTrue("tribe:elf" in TribeDeriver.payoffTribeKeys(elfPayoff))
        assertTrue("tribe:wolf" in TribeDeriver.payoffTribeKeys(wolfPayoff))
    }

    @Test
    fun payoffTribeKeysIsEmptyWithNoOracleText() {
        val vanilla = card(oracleText = null)
        assertTrue(TribeDeriver.payoffTribeKeys(vanilla).isEmpty())
    }

    @Test
    fun payoffTribeKeysHandlesChooseACreatureTypeAgainstOwnSubtypes() {
        val changeling = card(
            typeLine = "Creature — Shapeshifter",
            oracleText = "As this card enters, choose a creature type.",
        )
        // No named tribe in the oracle text -> credited against the card's OWN subtypes.
        assertEquals(setOf("tribe:shapeshifter"), TribeDeriver.payoffTribeKeys(changeling))
    }

    // ── tribeKeys (union) ─────────────────────────────────────────────────────────

    @Test
    fun tribeKeysIsTheUnionOfSubtypeAndPayoffKeys() {
        val elfLord = card(
            typeLine = "Legendary Creature — Elf Warrior",
            oracleText = "Other Goblins you control get +1/+0.",
        )
        assertEquals(
            setOf("tribe:elf", "tribe:warrior", "tribe:goblin"),
            TribeDeriver.tribeKeys(elfLord),
        )
    }

    @Test
    fun tribeKeysIsEmptyForAVanillaNonCreatureCardWithNoPayoff() {
        val plainInstant = card(typeLine = "Instant", oracleText = "Deal 3 damage to any target.")
        assertTrue(TribeDeriver.tribeKeys(plainInstant).isEmpty())
    }
}
