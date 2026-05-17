package com.mmg.manahub.feature.survey.presentation

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.MoodBad
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector
import com.mmg.manahub.R
import java.util.Locale

// ── Choice & Answer models ────────────────────────────────────────────────────

/**
 * A single selectable option within an [AnswerOption].
 *
 * @param id Stable identifier persisted to Room.
 * @param label Human-readable label (already localised by the engine).
 * @param icon Optional Material icon rendered instead of emoji.
 * @param manaToken If non-null, the UI renders a mana symbol image (e.g. "W", "U", "B", "R", "G", "C").
 */
data class SurveyChoice(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    val manaToken: String? = null,
)

/**
 * Exhaustive sum type covering every kind of answer widget the survey screen knows how to render.
 */
sealed class AnswerOption {
    /** Exactly one option must be selected; selection commits immediately. */
    data class SingleChoice(val options: List<SurveyChoice>) : AnswerOption()

    /** Zero or more options; requires explicit confirmation. */
    data class MultiChoice(val options: List<SurveyChoice>) : AnswerOption()

    /** 1-to-[maxStars] star picker. */
    data class StarRating(val maxStars: Int = 5) : AnswerOption()

    /** Plain multiline text field. */
    object FreeText : AnswerOption()

    /**
     * Card-impact carousel. The screen is responsible for rendering the deck cards
     * as Key / Average / Weak chips; the engine only declares the question type.
     */
    object CardImpact : AnswerOption()
}

// ── Question model ────────────────────────────────────────────────────────────

/**
 * A single survey question, fully resolved with localised text.
 */
data class SurveyQuestion(
    val id: String,
    val type: String,
    val text: String,
    val contextBadge: String? = null,
    val answerOption: AnswerOption,
    val cardReference: String? = null,
)

// ── Panel model ───────────────────────────────────────────────────────────────

/** Stable identifier for each thematic group of questions. */
enum class SurveyPanelId { MOOD, FUNDAMENTALS, CARD_IMPACT, SUMMARY }

/**
 * A named group of related [SurveyQuestion]s rendered together as a single screen page.
 *
 * [SUMMARY] is always empty — the screen renders its own recap UI for that panel.
 */
data class SurveyPanel(
    val id: SurveyPanelId,
    val title: String,
    val questions: List<SurveyQuestion>,
)

// ── Engine ────────────────────────────────────────────────────────────────────

/**
 * Produces the ordered list of [SurveyPanel]s for a finished game.
 *
 * All visible strings are resolved from Android resources so localisation is
 * handled by the OS resource system.  Pass [langCode] (BCP-47, e.g. "es-ES") to
 * force a specific locale; null uses the device default.
 */
object SurveyQuestionEngine {

    /**
     * Build the full panel list.
     *
     * @param won Whether the app user won the game.
     * @param context Android context used for string resolution.
     * @param langCode Optional BCP-47 language code override.
     * @param hasDeck Whether the session has an associated deck; controls whether
     *   [SurveyPanelId.CARD_IMPACT] is included.
     */
    fun buildPanels(
        won: Boolean,
        context: Context,
        langCode: String? = null,
        hasDeck: Boolean = false,
    ): List<SurveyPanel> {
        val ctx = if (langCode != null) {
            val locale = Locale.forLanguageTag(langCode)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            context.createConfigurationContext(config)
        } else {
            context
        }

        val panels = mutableListOf<SurveyPanel>()

        // ── MOOD ──────────────────────────────────────────────────────────────
        panels += SurveyPanel(
            id = SurveyPanelId.MOOD,
            title = ctx.getString(R.string.survey_panel_mood),
            questions = listOf(
                buildDecisiveMomentQuestion(ctx),
                buildMatchupDifficultyQuestion(ctx),
            ),
        )

        // ── FUNDAMENTALS ─────────────────────────────────────────────────────
        panels += SurveyPanel(
            id = SurveyPanelId.FUNDAMENTALS,
            title = ctx.getString(R.string.survey_panel_fundamentals),
            questions = listOf(
                buildHandQualityQuestion(ctx),
                buildManaHealthQuestion(ctx),
                buildResultContextQuestion(won, ctx),
            ),
        )

        // ── CARD_IMPACT (only when a deck is linked) ──────────────────────────
        if (hasDeck) {
            panels += SurveyPanel(
                id = SurveyPanelId.CARD_IMPACT,
                title = ctx.getString(R.string.survey_panel_cards),
                questions = listOf(buildCardImpactQuestion(ctx)),
            )
        }

        // ── SUMMARY (empty — screen renders its own UI) ───────────────────────
        panels += SurveyPanel(
            id = SurveyPanelId.SUMMARY,
            title = ctx.getString(R.string.survey_panel_summary),
            questions = emptyList(),
        )

        return panels
    }

    // ── Question builders ─────────────────────────────────────────────────────

    /** MOOD Q1: What was the decisive moment of the game? */
    private fun buildDecisiveMomentQuestion(ctx: Context) = SurveyQuestion(
        id = "decisive_moment",
        type = "DECISIVE_MOMENT",
        text = ctx.getString(R.string.survey_q_decisive_moment),
        answerOption = AnswerOption.SingleChoice(
            listOf(
                SurveyChoice(
                    id = "KEY_TURN",
                    label = ctx.getString(R.string.survey_a_key_turn),
                    icon = Icons.Default.EmojiEvents,
                ),
                SurveyChoice(
                    id = "TOP_DECK",
                    label = ctx.getString(R.string.survey_a_top_deck),
                    icon = Icons.Default.Casino,
                ),
                SurveyChoice(
                    id = "RIVAL_ERROR",
                    label = ctx.getString(R.string.survey_a_rival_error),
                    icon = Icons.Default.MoodBad,
                ),
                SurveyChoice(
                    id = "UNCLEAR",
                    label = ctx.getString(R.string.survey_a_unclear),
                    icon = Icons.Default.QuestionMark,
                ),
            )
        ),
    )

    /** MOOD Q2: How hard was the matchup? (1–5 stars) */
    private fun buildMatchupDifficultyQuestion(ctx: Context) = SurveyQuestion(
        id = "matchup_difficulty",
        type = "STAR_RATING",
        text = ctx.getString(R.string.survey_q_matchup_difficulty),
        answerOption = AnswerOption.StarRating(maxStars = 5),
    )

    /** FUNDAMENTALS Q1: Opening hand quality (1–5 stars) */
    private fun buildHandQualityQuestion(ctx: Context) = SurveyQuestion(
        id = "hand_quality",
        type = "HAND",
        text = ctx.getString(R.string.survey_q_hand),
        answerOption = AnswerOption.StarRating(maxStars = 5),
    )

    /** FUNDAMENTALS Q2: Mana health (multi-choice) */
    private fun buildManaHealthQuestion(ctx: Context) = SurveyQuestion(
        id = "mana_health",
        type = "MANA",
        text = ctx.getString(R.string.survey_q_mana),
        answerOption = AnswerOption.MultiChoice(
            listOf(
                SurveyChoice(
                    id = "SMOOTH",
                    label = ctx.getString(R.string.survey_a_mana_smooth),
                    icon = Icons.Default.Check,
                ),
                SurveyChoice(
                    id = "FLOODED",
                    label = ctx.getString(R.string.survey_a_mana_flooded),
                    icon = Icons.Default.Waves,
                ),
                SurveyChoice(
                    id = "SCREWED",
                    label = ctx.getString(R.string.survey_a_mana_screwed),
                    icon = Icons.Default.Block,
                ),
                SurveyChoice(
                    id = "COLORS",
                    label = ctx.getString(R.string.survey_a_mana_colors),
                    icon = Icons.Default.Palette,
                ),
                SurveyChoice(
                    id = "NONE",
                    label = ctx.getString(R.string.survey_a_mana_none),
                    icon = Icons.Default.ThumbUp,
                ),
            )
        ),
    )

    /**
     * FUNDAMENTALS Q3: Game result context — branched by win/loss.
     *
     * Win options: Dominant, Close, Skillful.
     * Loss options: Overwhelmed, No answers, Too slow.
     */
    private fun buildResultContextQuestion(won: Boolean, ctx: Context) = SurveyQuestion(
        id = "result_context",
        type = "RESULT_CONTEXT",
        text = if (won)
            ctx.getString(R.string.survey_q_result_context_win)
        else
            ctx.getString(R.string.survey_q_result_context_loss),
        answerOption = AnswerOption.SingleChoice(
            if (won) listOf(
                SurveyChoice(
                    id = "DOMINANT",
                    label = ctx.getString(R.string.survey_a_dominant),
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                ),
                SurveyChoice(
                    id = "CLOSE",
                    label = ctx.getString(R.string.survey_a_close),
                    icon = Icons.Default.HourglassBottom,
                ),
                SurveyChoice(
                    id = "SKILLFUL",
                    label = ctx.getString(R.string.survey_a_skillful),
                    icon = Icons.Default.Star,
                ),
            ) else listOf(
                SurveyChoice(
                    id = "OVERWHELMED",
                    label = ctx.getString(R.string.survey_a_overwhelmed),
                    icon = Icons.Default.Whatshot,
                ),
                SurveyChoice(
                    id = "NO_ANSWERS",
                    label = ctx.getString(R.string.survey_a_no_answers),
                    icon = Icons.Default.Block,
                ),
                SurveyChoice(
                    id = "TOO_SLOW",
                    label = ctx.getString(R.string.survey_a_too_slow),
                    icon = Icons.Default.HourglassBottom,
                ),
            )
        ),
    )

    /** CARD_IMPACT Q1: Card-impact carousel question. */
    private fun buildCardImpactQuestion(ctx: Context) = SurveyQuestion(
        id = "card_impact",
        type = "CARD_IMPACT",
        text = ctx.getString(R.string.survey_q_card_impact),
        answerOption = AnswerOption.CardImpact,
    )

    // ── Legacy compatibility shim ─────────────────────────────────────────────

    /**
     * Flattens all panel questions into a single list for legacy callers.
     *
     * @deprecated Prefer [buildPanels] and iterate over [SurveyPanel.questions].
     */
    @Deprecated(
        message = "Use buildPanels() instead",
        replaceWith = ReplaceWith("buildPanels(won, context, langCode, hasDeck).flatMap { it.questions }"),
    )
    fun buildQuestions(
        won: Boolean,
        context: Context,
        langCode: String? = null,
        hasDeck: Boolean = false,
    ): List<SurveyQuestion> =
        buildPanels(won, context, langCode, hasDeck).flatMap { it.questions }
}
