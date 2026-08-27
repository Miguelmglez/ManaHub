package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ═══════════════════════════════════════════════════════════════════════════════
//  AnalysisEngineSynergyP4Test — Deck Analysis Category Sections rework (W2/W8) +
//  Deck Analysis Engine v3, PHASE 4 (spec §7) rescore.
//
//  W2/W8 established the per-key `fingerprint:<key>`/`tribe:<x>` DISPLAY sections — UNCHANGED by
//  Phase 4 (see [AnalysisEngine.evaluateSynergy]'s own KDoc for why). Phase 4 replaced the
//  SUBSCORE formula wholesale (SynergyGraph-based coverage/axisHealth/connectivity/consistency,
//  band-shaped by macro/theme) and split the old catch-all "offplan" bucket into
//  "interaction"/"standalone"/"offplan" — this file's newer tests cover exactly that split, the
//  anti-synergy conflict -> Finding mapping, and [AnalysisEngine.bandRatioScoreF]'s pure shape.
// ═══════════════════════════════════════════════════════════════════════════════

class AnalysisEngineSynergyP4Test {

    private val scorer = DeckScorer(RoleClassifier())

    private fun profileFor(mainboard: List<DeckEntry>, colorIdentity: Set<ManaColor>): DeckProfile =
        scorer.profile(mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, seedTags = emptyList())

    /** A single dual-tagged card (TOKENS + SACRIFICE, both STRATEGY-category so both feed
     * [DeckScorer]'s fingerprint) plus an untagged filler card. Since the dual card is the ONLY
     * source for both keys, `raw[TOKENS] == raw[SACRIFICE]` — both normalize to 1.0, clearing
     * [AnalysisEngine]'s 0.4f alignment threshold. Neither card carries a RoleKey-vocabulary tag
     * (TOKENS/SACRIFICE are STRATEGY tags, a DIFFERENT key space than the ROLE tags
     * [ArchetypeRoleClassifier.classify] matches — see [DeckScorer.IDENTITY_CATEGORIES]), and
     * neither's default "Instant" type line finds a SPELLS consumer in this 2-card deck, so the
     * [DeckSynergyGraph] this fixture builds has ZERO edges — see
     * [subscoreIsZero_whenNoGraphEdgesExist_onThisDegenerateFixture] for the P4 consequence. */
    private fun mainboard(): List<DeckEntry> {
        val dual = card(id = "dual-card", name = "Dual Card", tags = listOf(CardTag.TOKENS, CardTag.SACRIFICE))
        val offplan = card(id = "offplan-card", name = "Offplan Card", typeLine = "Instant")
        return listOf(entry(dual, quantity = 2), entry(offplan, quantity = 3))
    }

    private fun synergyOf(mainboard: List<DeckEntry>): PillarResult {
        val colorIdentity = setOf(ManaColor.U)
        val profile = profileFor(mainboard, colorIdentity)
        val analysis = AnalysisEngine.evaluate(
            mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, profile = profile,
            archetype = null, themes = emptyList(), isManualOverride = false, confidence = 0f,
        )
        return analysis.pillars.first { it.id == PillarId.SYNERGY }
    }

    // ── Pre-existing, UNCHANGED behavior: per-key fingerprint sections ─────────────────────────

    @Test
    fun cardAlignedOnTwoFingerprintKeys_appearsInBothSections() {
        val synergy = synergyOf(mainboard())

        val tokensSection = synergy.sections.firstOrNull { it.id == "fingerprint:tokens" }
        val sacrificeSection = synergy.sections.firstOrNull { it.id == "fingerprint:sacrifice" }
        assertTrue(tokensSection != null, "expected a fingerprint:tokens section: ${synergy.sections.map { it.id }}")
        assertTrue(sacrificeSection != null, "expected a fingerprint:sacrifice section: ${synergy.sections.map { it.id }}")
        assertTrue(tokensSection.contributions.any { it.scryfallId == "dual-card" }, "dual-card must appear under tokens")
        assertTrue(sacrificeSection.contributions.any { it.scryfallId == "dual-card" }, "dual-card must appear under sacrifice")
    }

    @Test
    fun fullyUnalignedCard_withNoRoleAndNoEdge_landsInOffplan() {
        val synergy = synergyOf(mainboard())

        val offplanSection = synergy.sections.first { it.id == "offplan" }
        assertTrue(offplanSection.contributions.any { it.scryfallId == "offplan-card" })
        val otherSections = synergy.sections.filterNot { it.id == "offplan" }
        assertFalse(
            otherSections.any { section -> section.contributions.any { it.scryfallId == "offplan-card" } },
            "offplan-card must not appear in any other section",
        )
    }

    @Test
    fun offplanSplit_sectionIdsAreExactlyThreeAndMutuallyExclusive() {
        val synergy = synergyOf(mainboard())

        val splitIds = setOf("interaction", "standalone", "offplan")
        val present = synergy.sections.map { it.id }.filter { it in splitIds }.toSet()
        assertEquals(splitIds, present, "expected exactly the 3 offplan-split sections: ${synergy.sections.map { it.id }}")
    }

    // ── Deck Analysis Engine v3, PHASE 4 (spec §7) — the offplan 3-way split ──────────────────

    /** Swords-to-Plowshares-shaped: an interaction role (removal), no fingerprint-STRATEGY tag,
     * no consumer of anything it structurally produces. Spec §7's own headline example: "A Swords
     * to Plowshares must never read as off-plan." */
    private fun interactionCard() = card(id = "removal-card", name = "Swords to Plowshares", typeLine = "Instant", cmc = 1.0, tags = listOf(CardTag.REMOVAL))

    /** Carries a real ROLE-vocabulary tag ([ArchetypeRoleClassifier.classify] is non-empty) that
     * has no axis membership at all (`stax_piece` is not in [ArchetypeRoleClassifier]'s
     * AXIS_PRODUCES/AXIS_CONSUMES/AXIS_AMPLIFIES tables) — so it can never gain a graph edge, yet
     * is clearly doing something for the plan. Deliberately NOT a `Creature` type line: with only
     * 2 creatures total in this fixture, [TribeDeriver]'s per-subtype share threshold
     * ([DeckScorer]'s own tribal fingerprint mechanism, pre-existing/unrelated to Phase 4) trips on
     * EITHER card's own unique subtype alone, pulling it out of the tag-fingerprint "residual" set
     * entirely before it ever reaches this phase's new split — confirmed live while writing this
     * test (both cards landed under `tribe:<subtype>` instead). `Artifact` sidesteps that. */
    private fun standaloneCard() = card(id = "stax-card", name = "Stax Piece", typeLine = "Artifact", cmc = 4.0, tags = listOf(CardTag("stax_piece", TagCategory.ROLE)))

    /** Zero tags, no oracle text, a type line that trips no structural axis (not Instant/Sorcery/
     * Artifact/Enchantment — plain `Artifact` WOULD trip the ARTIFACTS structural producer, so this
     * one deliberately differs from [standaloneCard]'s type line) and no `Creature` type line
     * (avoids the same tribal-fingerprint interference documented on [standaloneCard]) —
     * [ArchetypeRoleClassifier.classify] is genuinely empty for this card. */
    private fun offplanCard() = card(id = "vanilla-card", name = "Vanilla Filler", typeLine = "Legendary", cmc = 4.0)

    @Test
    fun interactionRoleCard_landsInInteractionSection_neverOffplan() {
        val mainboard = listOf(entry(interactionCard()), entry(standaloneCard()), entry(offplanCard()))
        val synergy = synergyOf(mainboard)

        val interactionSection = synergy.sections.first { it.id == "interaction" }
        assertTrue(interactionSection.contributions.any { it.scryfallId == "removal-card" })
        val offplanSection = synergy.sections.first { it.id == "offplan" }
        assertFalse(offplanSection.contributions.any { it.scryfallId == "removal-card" }, "a removal card must never read as off-plan (spec §7)")
    }

    @Test
    fun classifiedRoleWithNoAxisMembership_landsInStandalone_notOffplan() {
        val mainboard = listOf(entry(interactionCard()), entry(standaloneCard()), entry(offplanCard()))
        val synergy = synergyOf(mainboard)

        val standaloneSection = synergy.sections.first { it.id == "standalone" }
        assertTrue(standaloneSection.contributions.any { it.scryfallId == "stax-card" })
        val offplanSection = synergy.sections.first { it.id == "offplan" }
        assertFalse(offplanSection.contributions.any { it.scryfallId == "stax-card" })
    }

    @Test
    fun cardWithNoRoleAndNoEdge_landsInOffplan_notStandalone() {
        val mainboard = listOf(entry(interactionCard()), entry(standaloneCard()), entry(offplanCard()))
        val synergy = synergyOf(mainboard)

        val offplanSection = synergy.sections.first { it.id == "offplan" }
        assertTrue(offplanSection.contributions.any { it.scryfallId == "vanilla-card" })
        val standaloneSection = synergy.sections.first { it.id == "standalone" }
        assertFalse(standaloneSection.contributions.any { it.scryfallId == "vanilla-card" })
    }

    // ── Deck Analysis Engine v3, PHASE 4 — subscore/coverage math on a fully-degenerate fixture ─

    @Test
    fun subscoreIsZero_whenNoGraphEdgesExist_onThisDegenerateFixture() {
        // See mainboard()'s own KDoc: both cards structurally produce SPELLS (default "Instant"
        // type line) but neither consumes it, so the graph has ZERO edges -- coverage/axisHealth/
        // connectivity/consistency are all 0, so `raw` is 0 regardless of the anti-synergy penalty,
        // and subscore = 100 * bandRatioScoreF(0, ideal, max) = 0 for every macro/theme band.
        val synergy = synergyOf(mainboard())

        assertEquals(0, synergy.alignedNonLandCopies, "no card participates in any edge")
        assertEquals(5, synergy.totalNonLandCopies)
        assertEquals(0, synergy.subscore)
    }

    @Test
    fun tokenGeneratorsWithNoPayoffs_emitOrphanProducersFinding() {
        // 3 unique token_generator-tagged creatures (avoiding a Commander SingletonViolation),
        // no death_payoff/counters_payoff/combat_payoff anywhere -- TOKENS axis producerCopies=3
        // clears its own tiny density-scaled producerIdeal (round(12 * 3 / 65) = 1) while
        // payoffCopies stays 0, tripping SynergyConflict.OrphanProducers (spec §5.4).
        fun tokenGenerator(id: String) = card(id = id, name = id, typeLine = "Creature — Human", cmc = 4.0, power = "1", toughness = "1", tags = listOf(CardTag("token_generator", TagCategory.ROLE)))
        val mainboard = listOf(entry(tokenGenerator("gen-1")), entry(tokenGenerator("gen-2")), entry(tokenGenerator("gen-3")))
        val synergy = synergyOf(mainboard)

        val finding = synergy.findings.filterIsInstance<Finding.OrphanProducers>().firstOrNull { it.axis == "TOKENS" }
        assertNotNull(finding, "expected an OrphanProducers(TOKENS) finding: ${synergy.findings}")
        assertEquals(3, finding.producerCopies)
        assertEquals(1, finding.producerIdeal)
        assertEquals("Tokens", finding.axisLabel)
    }

    // ── bandRatioScoreF (spec §7 task 2) — pure shape test, independent of the full pipeline ────

    @Test
    fun bandRatioScoreF_ramps_thenPlateaus_thenDecays() {
        // ramp: current < ideal -> linear current/ideal.
        assertEquals(0.5f, AnalysisEngine.bandRatioScoreF(0.125f, 0.25f, 0.50f))
        assertEquals(0f, AnalysisEngine.bandRatioScoreF(0f, 0.25f, 0.50f))
        // plateau: [ideal, max] -> always 1.
        assertEquals(1f, AnalysisEngine.bandRatioScoreF(0.25f, 0.25f, 0.50f))
        assertEquals(1f, AnalysisEngine.bandRatioScoreF(0.375f, 0.25f, 0.50f))
        assertEquals(1f, AnalysisEngine.bandRatioScoreF(0.50f, 0.25f, 0.50f))
        // decay: current > max -> hyperbolic max/current, strictly < 1.
        assertEquals(0.5f, AnalysisEngine.bandRatioScoreF(1.0f, 0.25f, 0.50f))
        assertEquals(0.25f, AnalysisEngine.bandRatioScoreF(2.0f, 0.25f, 0.50f))
        // degenerate ideal <= 0 -> always full credit (mirrors bandRatioScore's own convention).
        assertEquals(1f, AnalysisEngine.bandRatioScoreF(0.9f, 0f, 0.5f))
    }
}
