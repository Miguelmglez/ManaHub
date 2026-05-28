package com.mmg.manahub.feature.playtest.presentation

import android.content.Context
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.feature.survey.presentation.AnswerOption
import com.mmg.manahub.feature.survey.presentation.SurveyChoice

// ── Playtest-specific panel/question models ───────────────────────────────────

/** Stable identifier for each thematic group of playtest questions. */
enum class PlaytestPanelId {
    HAND_QUALITY,
    MANA_ANALYSIS,
    CURVE_FEEL,
    CARD_JUDGMENT,
    FREE_NOTES,
}

/** A single playtest survey question, fully resolved with localised text. */
data class PlaytestSurveyQuestion(
    val id: String,
    val type: String,
    val text: String,
    val answerOption: AnswerOption,
    /** Non-null only for CardImpact questions — the card this question targets. */
    val cardReference: String? = null,
)

/** A named group of [PlaytestSurveyQuestion]s rendered as one page. */
data class PlaytestSurveyPanel(
    val id: PlaytestPanelId,
    val title: String,
    val questions: List<PlaytestSurveyQuestion>,
)

// ── Engine ────────────────────────────────────────────────────────────────────

/**
 * Produces the ordered list of [PlaytestSurveyPanel]s for a deck playtest session.
 *
 * Reuses [AnswerOption] and [SurveyChoice] from [com.mmg.manahub.feature.survey.presentation]
 * without duplicating the sealed class hierarchy. Produces playtest-specific panels and
 * question models ([PlaytestSurveyPanel] / [PlaytestSurveyQuestion]) that are local to
 * this feature.
 *
 * All visible strings are resolved from Android resources for localisation.
 */
object PlaytestSurveyEngine {

    /**
     * Build the full playtest survey panel list.
     *
     * @param context Android context for string resolution.
     * @param handCards The FINAL kept hand cards (used for the CARD_JUDGMENT panel).
     */
    fun buildPanels(
        context: Context,
        handCards: List<Card>,
    ): List<PlaytestSurveyPanel> {
        val panels = mutableListOf<PlaytestSurveyPanel>()

        // ── HAND_QUALITY ──────────────────────────────────────────────────────
        panels += PlaytestSurveyPanel(
            id    = PlaytestPanelId.HAND_QUALITY,
            title = context.getString(R.string.playtest_panel_hand_quality),
            questions = listOf(
                PlaytestSurveyQuestion(
                    id           = "hand_keepable",
                    type         = "HAND_KEEPABLE",
                    text         = context.getString(R.string.playtest_q_hand_keepable),
                    answerOption = AnswerOption.SingleChoice(
                        listOf(
                            SurveyChoice(id = "SNAP_KEEP",        label = context.getString(R.string.playtest_a_snap_keep)),
                            SurveyChoice(id = "KEEP_BORDERLINE",  label = context.getString(R.string.playtest_a_keep_borderline)),
                            SurveyChoice(id = "MULLIGAN_IT",      label = context.getString(R.string.playtest_a_mulligan_it)),
                            SurveyChoice(id = "HARD_MULLIGAN",    label = context.getString(R.string.playtest_a_hard_mulligan)),
                        )
                    ),
                ),
                PlaytestSurveyQuestion(
                    id           = "hand_rating",
                    type         = "STAR_RATING",
                    text         = context.getString(R.string.playtest_q_hand_rating),
                    answerOption = AnswerOption.StarRating(maxStars = 5),
                ),
            ),
        )

        // ── MANA_ANALYSIS ─────────────────────────────────────────────────────
        panels += PlaytestSurveyPanel(
            id    = PlaytestPanelId.MANA_ANALYSIS,
            title = context.getString(R.string.playtest_panel_mana_analysis),
            questions = listOf(
                PlaytestSurveyQuestion(
                    id           = "mana_land_count",
                    type         = "MANA_LAND_COUNT",
                    text         = context.getString(R.string.playtest_q_mana_land_count),
                    answerOption = AnswerOption.SingleChoice(
                        listOf(
                            SurveyChoice(id = "ZERO",     label = context.getString(R.string.playtest_a_lands_zero)),
                            SurveyChoice(id = "ONE",      label = context.getString(R.string.playtest_a_lands_one)),
                            SurveyChoice(id = "TWO",      label = context.getString(R.string.playtest_a_lands_two)),
                            SurveyChoice(id = "THREE",    label = context.getString(R.string.playtest_a_lands_three)),
                            SurveyChoice(id = "FOUR_PLUS", label = context.getString(R.string.playtest_a_lands_four_plus)),
                        )
                    ),
                ),
                PlaytestSurveyQuestion(
                    id           = "mana_health",
                    type         = "MANA_HEALTH",
                    text         = context.getString(R.string.playtest_q_mana_health),
                    answerOption = AnswerOption.MultiChoice(
                        listOf(
                            SurveyChoice(id = "SMOOTH",       label = context.getString(R.string.playtest_a_mana_smooth)),
                            SurveyChoice(id = "FLOODED",      label = context.getString(R.string.playtest_a_mana_flooded)),
                            SurveyChoice(id = "SCREWED",      label = context.getString(R.string.playtest_a_mana_screwed)),
                            SurveyChoice(id = "COLOR_ISSUES", label = context.getString(R.string.playtest_a_mana_color_issues)),
                            SurveyChoice(id = "PERFECT",      label = context.getString(R.string.playtest_a_mana_perfect)),
                        )
                    ),
                ),
            ),
        )

        // ── CURVE_FEEL ────────────────────────────────────────────────────────
        panels += PlaytestSurveyPanel(
            id    = PlaytestPanelId.CURVE_FEEL,
            title = context.getString(R.string.playtest_panel_curve_feel),
            questions = listOf(
                PlaytestSurveyQuestion(
                    id           = "curve_feel",
                    type         = "CURVE_FEEL",
                    text         = context.getString(R.string.playtest_q_curve_feel),
                    answerOption = AnswerOption.SingleChoice(
                        listOf(
                            SurveyChoice(id = "AGGRO_READY", label = context.getString(R.string.playtest_a_curve_aggro)),
                            SurveyChoice(id = "MIDRANGE",    label = context.getString(R.string.playtest_a_curve_midrange)),
                            SurveyChoice(id = "GRINDY",      label = context.getString(R.string.playtest_a_curve_grindy)),
                            SurveyChoice(id = "EXPLOSIVE",   label = context.getString(R.string.playtest_a_curve_explosive)),
                            SurveyChoice(id = "CLUNKY",      label = context.getString(R.string.playtest_a_curve_clunky)),
                        )
                    ),
                ),
                PlaytestSurveyQuestion(
                    id           = "strategy_ready",
                    type         = "STRATEGY_READY",
                    text         = context.getString(R.string.playtest_q_strategy_ready),
                    answerOption = AnswerOption.SingleChoice(
                        listOf(
                            SurveyChoice(id = "FULLY",    label = context.getString(R.string.playtest_a_strategy_fully)),
                            SurveyChoice(id = "PARTIALLY", label = context.getString(R.string.playtest_a_strategy_partially)),
                            SurveyChoice(id = "BARELY",   label = context.getString(R.string.playtest_a_strategy_barely)),
                            SurveyChoice(id = "NO",       label = context.getString(R.string.playtest_a_strategy_no)),
                        )
                    ),
                ),
                PlaytestSurveyQuestion(
                    id           = "missing_piece",
                    type         = "MISSING_PIECE",
                    text         = context.getString(R.string.playtest_q_missing_piece),
                    answerOption = AnswerOption.MultiChoice(
                        listOf(
                            SurveyChoice(id = "LANDS",   label = context.getString(R.string.playtest_a_missing_lands)),
                            SurveyChoice(id = "REMOVAL", label = context.getString(R.string.playtest_a_missing_removal)),
                            SurveyChoice(id = "WINCON",  label = context.getString(R.string.playtest_a_missing_wincon)),
                            SurveyChoice(id = "RAMP",    label = context.getString(R.string.playtest_a_missing_ramp)),
                            SurveyChoice(id = "DRAW",    label = context.getString(R.string.playtest_a_missing_draw)),
                            SurveyChoice(id = "NOTHING", label = context.getString(R.string.playtest_a_missing_nothing)),
                        )
                    ),
                ),
            ),
        )

        // ── CARD_JUDGMENT (one question per card in the kept hand) ────────────
        if (handCards.isNotEmpty()) {
            val cardImpactQuestion = PlaytestSurveyQuestion(
                id           = "card_impact",
                type         = "CARD_IMPACT",
                text         = context.getString(R.string.playtest_q_card_impact),
                answerOption = AnswerOption.CardImpact,
            )
            panels += PlaytestSurveyPanel(
                id    = PlaytestPanelId.CARD_JUDGMENT,
                title = context.getString(R.string.playtest_panel_card_judgment),
                questions = listOf(cardImpactQuestion),
            )
        }

        // ── FREE_NOTES ────────────────────────────────────────────────────────
        panels += PlaytestSurveyPanel(
            id    = PlaytestPanelId.FREE_NOTES,
            title = context.getString(R.string.playtest_panel_free_notes),
            questions = listOf(
                PlaytestSurveyQuestion(
                    id           = "free_notes",
                    type         = "FREE_TEXT",
                    text         = context.getString(R.string.playtest_q_free_notes),
                    answerOption = AnswerOption.FreeText,
                ),
            ),
        )

        return panels
    }
}
