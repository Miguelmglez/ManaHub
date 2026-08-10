package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.StrategyCatalog
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.engine.card
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deck Engine Unification plan (`docs/plans/deck-engine-unification-plan.md` §5 Phase 3.2, Flow A) —
 * [SuggestStrategiesForSeedsUseCase]'s seed-coherence check and strategy ranking.
 */
class SuggestStrategiesForSeedsUseCaseTest {

    private val useCase = SuggestStrategiesForSeedsUseCase()

    @Test
    fun `empty seeds yield a trivially coherent, empty suggestion`() {
        val result = useCase(emptyList())
        assertTrue(result.isCoherent)
        assertEquals(1f, result.coherenceScore)
        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun `a single seed is trivially coherent -- nothing to disagree with`() {
        val seed = card(id = "s1", name = "Ramp Spell", tags = listOf(CardTag.RAMP))
        val result = useCase(listOf(seed))
        assertTrue(result.isCoherent)
        assertEquals(1f, result.coherenceScore)
    }

    @Test
    fun `a RAMP-tagged seed ranks the RAMP archetype as a viable candidate`() {
        val seed = card(id = "s1", name = "Ramp Spell", tags = listOf(CardTag.RAMP))
        val result = useCase(listOf(seed))
        assertTrue(result.candidates.any { it.profile.archetype == ArchetypeId.RAMP })
    }

    @Test
    fun `a candidate with zero tag overlap is never returned`() {
        val seed = card(id = "s1", name = "Ramp Spell", tags = listOf(CardTag.RAMP))
        val result = useCase(listOf(seed))
        // Nothing in the RAMP seed's tag set overlaps STAX's seed tags -- STAX must not appear.
        assertTrue(result.candidates.none { it.profile.themes.contains(ThemeId.STAX) })
    }

    @Test
    fun `two seeds sharing an identity tag are coherent`() {
        val seedA = card(id = "s1", name = "Ramp Spell A", tags = listOf(CardTag.RAMP))
        val seedB = card(id = "s2", name = "Ramp Spell B", tags = listOf(CardTag.RAMP))
        val result = useCase(listOf(seedA, seedB))
        assertTrue(result.isCoherent)
        assertEquals(1f, result.coherenceScore)
    }

    @Test
    fun `two seeds with completely disjoint identity tags are flagged incoherent, but still ranked`() {
        val seedA = card(id = "s1", name = "Ramp Piece", tags = listOf(CardTag.RAMP))
        val seedB = card(id = "s2", name = "Mill Piece", tags = listOf(CardTag.GRAVEYARD))
        val result = useCase(listOf(seedA, seedB))
        assertTrue(!result.isCoherent)
        assertEquals(0f, result.coherenceScore)
        // Incoherent does NOT mean empty -- the caller still gets a ranked list to choose from.
        assertTrue(result.candidates.isNotEmpty())
    }

    @Test
    fun `two fully-untagged seeds are trivially coherent -- no data is not conflicting data`() {
        // Edge-case audit follow-up (RUN 3b QA fix): two seeds with zero identity tags and no
        // derivable tribe (non-creature type line, so TribeDeriver.subtypeKeys is also empty) aren't
        // in TENSION with each other, they just carry no signal -- pairwiseCoherence must not
        // conflate "no data" with "conflicting data" the way it would flag a genuinely disjoint pair
        // (RAMP vs GRAVEYARD) as incoherent. Uses the default "Instant" typeLine + no tags/oracleText
        // so BOTH seeds' identity-key sets are genuinely empty, not just tag-empty.
        val untaggedA = card(id = "s1", name = "Plain Bolt")
        val untaggedB = card(id = "s2", name = "Plain Zap")
        val result = useCase(listOf(untaggedA, untaggedB))
        assertTrue(result.isCoherent)
        assertEquals(1f, result.coherenceScore)
    }

    @Test
    fun `a shared creature subtype yields a tribe candidate`() {
        val elfA = card(id = "s1", name = "Elf Warrior", typeLine = "Creature — Elf Warrior")
        val elfB = card(id = "s2", name = "Elf Druid", typeLine = "Creature — Elf Druid")
        val result = useCase(listOf(elfA, elfB))
        assertTrue(result.candidates.any { it.profile.tribe == "tribe:elf" })
    }

    @Test
    fun `candidates are sorted best-fit first`() {
        val seed = card(id = "s1", name = "Ramp Spell", tags = listOf(CardTag.RAMP))
        val result = useCase(listOf(seed))
        val scores = result.candidates.map { it.fitScore }
        assertEquals(scores.sortedDescending(), scores)
    }

    // ── Deck Wizard & Engine Rework plan, Workstream 3.1 -- coherence hints ──────

    @Test
    fun `a seed sharing nothing with a candidate surfaces as that candidate's misfitSeeds`() {
        val rampSeed = card(id = "s1", name = "Ramp Piece", tags = listOf(CardTag.RAMP))
        val offSeed = card(id = "s2", name = "Graveyard Piece", tags = listOf(CardTag.GRAVEYARD))
        val result = useCase(listOf(rampSeed, offSeed))

        val rampCandidate = result.candidates.first { it.profile.archetype == ArchetypeId.RAMP }
        assertTrue(rampCandidate.misfitSeeds.any { it.scryfallId == "s2" })
        assertTrue(rampCandidate.misfitSeeds.none { it.scryfallId == "s1" })
    }

    @Test
    fun `a candidate every seed supports has no misfitSeeds`() {
        val seedA = card(id = "s1", name = "Ramp Piece A", tags = listOf(CardTag.RAMP))
        val seedB = card(id = "s2", name = "Ramp Piece B", tags = listOf(CardTag.RAMP))
        val result = useCase(listOf(seedA, seedB))

        val rampCandidate = result.candidates.first { it.profile.archetype == ArchetypeId.RAMP }
        assertTrue(rampCandidate.misfitSeeds.isEmpty())
    }

    @Test
    fun `misfitColors is empty when no colors are selected yet`() {
        val seed = card(id = "s1", name = "Aggro Beater", tags = listOf(CardTag.AGGRO))
        val result = useCase(listOf(seed))
        val aggroCandidate = result.candidates.first { it.profile.archetype == ArchetypeId.AGGRO }
        assertTrue(aggroCandidate.misfitColors.isEmpty())
    }

    @Test
    fun `a selected color absent from every curated combo for a candidate is flagged as a misfit`() {
        // AGGRO's curated ColorStrategyAffinity combos (mono-W, mono-R, Rakdos, Gruul, Boros,
        // Naya, Mardu...) never include Blue -- picking Blue alongside an AGGRO candidate must
        // surface it as a misfit, regardless of which specific combo the tie-break lands on.
        val seed = card(id = "s1", name = "Aggro Beater", tags = listOf(CardTag.AGGRO))
        val result = useCase(listOf(seed), selectedColors = setOf(ManaColor.U))
        val aggroCandidate = result.candidates.first { it.profile.archetype == ArchetypeId.AGGRO }
        assertTrue(ManaColor.U in aggroCandidate.misfitColors)
    }

    @Test
    fun `a selected color present in the best curated combo is not flagged`() {
        val seed = card(id = "s1", name = "Aggro Beater", tags = listOf(CardTag.AGGRO))
        val result = useCase(listOf(seed), selectedColors = setOf(ManaColor.R))
        val aggroCandidate = result.candidates.first { it.profile.archetype == ArchetypeId.AGGRO }
        assertTrue(ManaColor.R !in aggroCandidate.misfitColors)
    }

    @Test
    fun `a tribe candidate carries ThemeId-TRIBAL so it passes StrategyCatalog-isValidCombination`() {
        // WS3.1 bug fix -- before this, a tribe candidate's profile carried no ThemeId.TRIBAL, so
        // StrategyCatalog.isValidCombination (a WS1 addition made AFTER this use case's tribe
        // candidates were first written) rejected it as an orphaned tribe pick.
        val elfA = card(id = "s1", name = "Elf Warrior", typeLine = "Creature — Elf Warrior")
        val elfB = card(id = "s2", name = "Elf Druid", typeLine = "Creature — Elf Druid")
        val result = useCase(listOf(elfA, elfB))

        val tribeCandidate = result.candidates.first { it.profile.tribe == "tribe:elf" }
        assertEquals(listOf(ThemeId.TRIBAL), tribeCandidate.profile.themes)
        assertTrue(StrategyCatalog.isValidCombination(tribeCandidate.profile.archetype, tribeCandidate.profile.themes, tribeCandidate.profile.tribe))
    }

    @Test
    fun `every returned candidate passes StrategyCatalog-isValidCombination`() {
        val rampSeed = card(id = "s1", name = "Ramp Spell", tags = listOf(CardTag.RAMP))
        val elf = card(id = "s2", name = "Elf Warrior", typeLine = "Creature — Elf Warrior")
        val result = useCase(listOf(rampSeed, elf))
        assertTrue(result.candidates.isNotEmpty())
        result.candidates.forEach { candidate ->
            assertTrue(
                StrategyCatalog.isValidCombination(candidate.profile.archetype, candidate.profile.themes, candidate.profile.tribe),
                "candidate ${candidate.label} must pass isValidCombination",
            )
        }
    }
}
