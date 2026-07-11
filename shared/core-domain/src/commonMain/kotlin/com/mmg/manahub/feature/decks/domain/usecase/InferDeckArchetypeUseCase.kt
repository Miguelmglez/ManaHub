package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.ThemeId

// ═══════════════════════════════════════════════════════════════════════════════
//  InferDeckArchetypeUseCase — Deck Doctor Community/Archetype plan, Phase 1.4 (A.6)
//
//  Zero-regression contract: confidence below [MACRO_CONFIDENCE_THRESHOLD] (~0.55,
//  A.6's own tuning guidance) resolves to [ArchetypeId.GENERIC] with an empty theme
//  list — the caller then skips the whole archetype-aware evaluation path and the
//  deck behaves EXACTLY as it did before Phase 1 (see EvaluateDeckUseCase's
//  archetype wiring / `project_archetype_engine` memory for the "GENERIC path
//  stays bit-stable" exit criterion).
//
//  Resolution order (the CALLER's responsibility, not this use case's): a deck's
//  `Deck.archetypeOverride`/`themesOverride` (a user pin) ALWAYS wins over this
//  inference — this use case is only ever consulted when no override is set, or by
//  the "Auto-detect" UI action which explicitly clears the override and re-runs it.
//
//  These per-macro/per-theme weights and thresholds are exactly the "softest cells"
//  Appendix A.1 calls out — archetype primers rather than hard statistics. They are
//  documented constants, deliberately NOT re-derived from anywhere else, and are the
//  target of the Phase 1.8 classifier confusion-matrix tests (which pin an accuracy
//  bar rather than exact scores, so this file can be re-tuned without touching the
//  resolver/evaluator's locked D15 data).
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * @property macro the inferred macro archetype (or [ArchetypeId.GENERIC] under-threshold).
 * @property themes at most 2 confidently-detected themes, ordered by descending confidence.
 * @property confidence the winning macro's own score (or the best theme's score when [macro]
 *           fell back to [ArchetypeId.MIDRANGE]/[ArchetypeId.GENERIC] via the A.6 "prefer GENERIC
 *           unless a theme is confident" hard-pair rule).
 */
data class ArchetypeInference(
    val macro: ArchetypeId,
    val themes: List<ThemeId>,
    val confidence: Float,
)

class InferDeckArchetypeUseCase {

    operator fun invoke(
        mainboard: List<DeckEntry>,
        format: ArchetypeFormat,
        commanderTags: List<CardTag> = emptyList(),
    ): ArchetypeInference {
        val nonLand = mainboard.filterNot { BasicLandCalculator.isLand(it.card) }
        val nonLandCount = nonLand.sumOf { it.quantity }
        if (nonLandCount == 0) return ArchetypeInference(ArchetypeId.GENERIC, emptyList(), 0f)

        val roleCounts = ArchetypeRoleClassifier.deckRoleCounts(mainboard)
        fun density(key: String): Float = (roleCounts[key] ?: 0) / nonLandCount.toFloat()

        val avgCmc = nonLand.sumOf { it.card.cmc * it.quantity } / nonLandCount
        val creatureCopies = nonLand.filter { it.card.typeLine.contains("Creature", ignoreCase = true) }
            .sumOf { it.quantity }
        val creatureRatio = creatureCopies / nonLandCount.toFloat()

        // ── Macro archetype scores (A.6's "most discriminative signals") ───────────
        val threatEarlyDensity = density("threat_early")
        val curveLowBonus = (1f - (avgCmc / 4.0)).toFloat().coerceIn(0f, 1f)
        val aggroScore = 0.5f * threatEarlyDensity + 0.3f * curveLowBonus + 0.2f * creatureRatio

        val interactionDensity = density("removal_spot") + density("removal_mass") + density("counterspell")
        val curveHighBonus = (avgCmc / 4.5).toFloat().coerceIn(0f, 1f)
        val controlScore = 0.5f * interactionDensity.coerceIn(0f, 1f) +
            0.2f * (1f - creatureRatio).coerceIn(0f, 1f) + 0.3f * curveHighBonus

        val tutorDensity = density("tutor")
        val protectionDensity = density("protection")
        val comboTagCopies = nonLand.filter { entry ->
            (entry.card.tags + entry.card.userTags).any { it.key == CardTag.COMBO.key || it.key == CardTag.INFINITE.key }
        }.sumOf { it.quantity }
        val comboTagDensity = comboTagCopies / nonLandCount.toFloat()
        val comboScore = 0.4f * tutorDensity.coerceIn(0f, 1f) + 0.3f * protectionDensity.coerceIn(0f, 1f) +
            0.3f * comboTagDensity.coerceIn(0f, 1f)

        val rampDensity = density("ramp")
        val curveTopHeavyBonus = (avgCmc / 5.0).toFloat().coerceIn(0f, 1f)
        val rampScore = 0.5f * rampDensity.coerceIn(0f, 1f) + 0.5f * curveTopHeavyBonus

        // TEMPO vs AGGRO hard pair (A.6): counterspell density (raw count, not ratio) >= 6 tips
        // an otherwise-aggro-shaped deck into TEMPO.
        val counterspellCount = roleCounts["counterspell"] ?: 0
        val tempoScore = if (counterspellCount >= TEMPO_COUNTERSPELL_MIN) {
            0.4f * threatEarlyDensity + 0.6f * density("counterspell").coerceIn(0f, 1f)
        } else {
            0f
        }

        val macroScores = mapOf(
            ArchetypeId.AGGRO to aggroScore,
            ArchetypeId.CONTROL to controlScore,
            ArchetypeId.COMBO to comboScore,
            ArchetypeId.RAMP to rampScore,
            ArchetypeId.TEMPO to tempoScore,
        )
        // A.6 "TEMPO vs AGGRO (counterspell density >= 6 => TEMPO)": TEMPO participates in the
        // SAME argmax as every other macro rather than a special-cased override — [tempoScore]
        // is already gated to 0 unless the deck clears [TEMPO_COUNTERSPELL_MIN] counterspell-role
        // copies, so TEMPO can only ever win this argmax once that hard-pair condition holds,
        // which is exactly the annex's rule (an earlier override-based implementation risked
        // TEMPO clobbering a clearly CONTROL-shaped deck whenever counterspell count was high;
        // letting it compete on the same score scale as every other macro fixes that).
        val macroCandidate = macroScores.maxByOrNull { it.value }

        // ── Themes (A.6: dedicated tag clusters / dominant tribe / commander taglinks) ──
        val themeScores = themeScores(mainboard, nonLand, nonLandCount, creatureCopies, format)
        val confidentThemes = themeScores
            .filter { it.value >= THEME_CONFIDENCE_THRESHOLD }
            .entries.sortedByDescending { it.value }
            .take(MAX_THEMES)
            .map { it.key }

        return when {
            macroCandidate != null && macroCandidate.value >= MACRO_CONFIDENCE_THRESHOLD ->
                ArchetypeInference(macroCandidate.key, confidentThemes, macroCandidate.value)
            confidentThemes.isNotEmpty() ->
                // A.6 "MIDRANGE vs GENERIC: prefer GENERIC unless a theme is confident" — a
                // confident theme with no clear macro signal defaults the macro to MIDRANGE
                // (the most common real-world pairing: Meren/Karador-style "theme decks" run a
                // midrange-shaped core with one payoff theme layered on top).
                ArchetypeInference(ArchetypeId.MIDRANGE, confidentThemes, themeScores.getValue(confidentThemes.first()))
            else ->
                ArchetypeInference(ArchetypeId.GENERIC, emptyList(), macroCandidate?.value ?: 0f)
        }
    }

    private fun themeScores(
        mainboard: List<DeckEntry>,
        nonLand: List<DeckEntry>,
        nonLandCount: Int,
        creatureCopies: Int,
        format: ArchetypeFormat,
    ): Map<ThemeId, Float> {
        val roleCounts = ArchetypeRoleClassifier.deckRoleCounts(mainboard)
        fun density(key: String): Float = if (nonLandCount == 0) 0f else (roleCounts[key] ?: 0) / nonLandCount.toFloat()
        fun typeLineRatio(typeWord: String): Float {
            if (nonLandCount == 0) return 0f
            val copies = nonLand.filter { it.card.typeLine.contains(typeWord, ignoreCase = true) }.sumOf { it.quantity }
            return copies / nonLandCount.toFloat()
        }

        val dominantTribeShare = if (creatureCopies == 0) 0f else {
            ArchetypeRoleClassifier.tribeMemberCount(mainboard) / creatureCopies.toFloat()
        }
        val instantSorceryRatio = typeLineRatio("Instant") + typeLineRatio("Sorcery")

        val scores = mutableMapOf<ThemeId, Float>()
        scores[ThemeId.REANIMATOR] = ((density("graveyard_enabler") + density("reanimation")) / 2f).themeNorm(0.12f)
        scores[ThemeId.SELF_MILL] = density("self_mill_payoff").themeNorm(0.1f)
        scores[ThemeId.ARISTOCRATS] = ((density("sac_outlet") + density("death_payoff")) / 2f).themeNorm(0.1f)
        scores[ThemeId.TOKENS] = density("token_generator").themeNorm(0.14f)
        scores[ThemeId.SPELLSLINGER] = instantSorceryRatio.themeNorm(0.25f)
        scores[ThemeId.VOLTRON] = density("equipment_or_aura").themeNorm(0.15f)
        scores[ThemeId.STAX] = density("stax_piece").themeNorm(0.1f)
        scores[ThemeId.LANDFALL] = density("landfall_payoff").themeNorm(0.09f)
        scores[ThemeId.LIFEGAIN] = density("lifegain_payoff").themeNorm(0.09f)
        scores[ThemeId.PLUS1_COUNTERS] = density("counters_payoff").themeNorm(0.12f)
        // A.6: a dominant tribe covering >= 35% of creature copies -> TRIBAL.
        scores[ThemeId.TRIBAL] = if (dominantTribeShare >= TRIBAL_SHARE_THRESHOLD) 1f else dominantTribeShare / TRIBAL_SHARE_THRESHOLD
        scores[ThemeId.ARTIFACTS] = maxOf(typeLineRatio("Artifact"), density("artifact_payoff")).themeNorm(0.22f)
        scores[ThemeId.ENCHANTRESS] = maxOf(typeLineRatio("Enchantment"), density("enchantment_payoff")).themeNorm(0.2f)
        scores[ThemeId.WHEELS] = density("wheel").themeNorm(0.07f)
        scores[ThemeId.MILL] = density("mill_engine").themeNorm(0.12f)
        scores[ThemeId.BLINK] = density("blink_effect").themeNorm(0.1f)
        scores[ThemeId.SUPERFRIENDS] = typeLineRatio("Planeswalker").themeNorm(0.1f)
        scores[ThemeId.VEHICLES] = typeLineRatio("Vehicle").themeNorm(0.09f)
        scores[ThemeId.TOOLBOX] = density("tutor").themeNorm(0.1f)
        if (format == ArchetypeFormat.COMMANDER) {
            scores[ThemeId.GROUP_HUG] = density("group_effect").themeNorm(0.1f)
            scores[ThemeId.GROUP_SLUG] = density("group_effect").themeNorm(0.1f)
            scores[ThemeId.CLONES_THEFT] = density("clone_theft_effect").themeNorm(0.09f)
        }
        return scores
    }

    /** Normalizes a raw density against a "clearly present" anchor, capped at 1.0. */
    private fun Float.themeNorm(anchor: Float): Float = (this / anchor).coerceIn(0f, 1f)

    private companion object {
        /** A.6: "Fall back to GENERIC below confidence ~0.55 (tune via confusion tests)." */
        const val MACRO_CONFIDENCE_THRESHOLD = 0.55f

        /** Secondary signal — a theme needs less certainty than a whole-deck macro archetype. */
        const val THEME_CONFIDENCE_THRESHOLD = 0.45f

        /** Max simultaneously-detected themes (D2 / Studio UI multi-select cap). */
        const val MAX_THEMES = 2

        /** A.6 hard pair: TEMPO needs >= 6 counterspell-role copies to even qualify. */
        const val TEMPO_COUNTERSPELL_MIN = 6

        /** A.6: "dominant tribe (>=35% creatures share a type) => TRIBAL." */
        const val TRIBAL_SHARE_THRESHOLD = 0.35f
    }
}
