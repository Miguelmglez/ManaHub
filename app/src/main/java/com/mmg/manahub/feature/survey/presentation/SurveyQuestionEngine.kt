package com.mmg.manahub.feature.survey.presentation

import android.content.Context
import android.content.res.Configuration
import com.mmg.manahub.R
import com.mmg.manahub.feature.game.domain.model.EliminationReason
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.game.domain.model.GameResult
import java.util.Locale

// ── Answer options ────────────────────────────────────────────────────────────

data class SurveyChoice(
    val id:    String,
    val label: String,
    val emoji: String = "",
)

sealed class AnswerOption {
    data class SingleChoice(val options: List<SurveyChoice>) : AnswerOption()
    data class MultiChoice(val options: List<SurveyChoice>)  : AnswerOption()
    data class StarRating(val maxStars: Int = 5)             : AnswerOption()
    object FreeText                                          : AnswerOption()
}

// ── Question model ────────────────────────────────────────────────────────────

data class SurveyQuestion(
    val id:            String,
    val type:          String,
    val text:          String,
    val contextBadge:  String?      = null,
    val answerOption:  AnswerOption,
    val cardReference: String?      = null,
)

// ── Engine ────────────────────────────────────────────────────────────────────

object SurveyQuestionEngine {

    fun buildQuestions(
        result:   GameResult,
        context:  Context,
        langCode: String? = null
    ): List<SurveyQuestion> {
        val localizedContext = if (langCode != null) {
            val locale = Locale.forLanguageTag(langCode)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            context.createConfigurationContext(config)
        } else {
            context
        }

        val questions   = mutableListOf<SurveyQuestion>()
        val appUserWon  = result.appUserWon

        // Q1 — always: subjective outcome, branched by win/loss
        questions += SurveyQuestion(
            id           = "result_feel",
            type         = "RESULT_FEEL",
            contextBadge = if (appUserWon)
                localizedContext.getString(R.string.survey_q_win_badge)
            else
                localizedContext.getString(R.string.survey_q_loss_badge),
            text         = if (appUserWon)
                localizedContext.getString(R.string.survey_q_result_win)
            else
                localizedContext.getString(R.string.survey_q_result_loss),
            answerOption = AnswerOption.SingleChoice(
                if (appUserWon) listOf(
                    SurveyChoice("DOMINANT",  localizedContext.getString(R.string.survey_a_dominant),    "\uD83D\uDCAA"),
                    SurveyChoice("CLOSE",     localizedContext.getString(R.string.survey_a_close),       "\uD83D\uDE05"),
                    SurveyChoice("LUCKY",     localizedContext.getString(R.string.survey_a_lucky),       "\uD83C\uDF40"),
                    SurveyChoice("SKILLFUL",  localizedContext.getString(R.string.survey_a_skillful),    "\uD83E\uDDE0"),
                ) else listOf(
                    SurveyChoice("OVERWHELMED", localizedContext.getString(R.string.survey_a_overwhelmed), "\u26A1"),
                    SurveyChoice("MANA",        localizedContext.getString(R.string.survey_a_mana_issues), "\uD83C\uDFD4"),
                    SurveyChoice("NO_ANSWERS",  localizedContext.getString(R.string.survey_a_no_answers),  "\uD83D\uDEAB"),
                    SurveyChoice("TOO_SLOW",    localizedContext.getString(R.string.survey_a_too_slow),    "\uD83D\uDC22"),
                )
            ),
        )

        // Q2 — always: mana health
        questions += SurveyQuestion(
            id   = "mana_health",
            type = "MANA",
            text = localizedContext.getString(R.string.survey_q_mana),
            answerOption = AnswerOption.MultiChoice(listOf(
                SurveyChoice("SMOOTH",  localizedContext.getString(R.string.survey_a_mana_smooth),   "\u2705"),
                SurveyChoice("FLOODED", localizedContext.getString(R.string.survey_a_mana_flooded),  "\uD83C\uDF0A"),
                SurveyChoice("SCREWED", localizedContext.getString(R.string.survey_a_mana_screwed),  "\uD83C\uDFDC"),
                SurveyChoice("COLORS",  localizedContext.getString(R.string.survey_a_mana_colors),   "\uD83C\uDFA8"),
                SurveyChoice("NONE",    localizedContext.getString(R.string.survey_a_mana_none),     "\uD83D\uDC4D"),
            )),
        )

        // Q3 — always: opening hand
        questions += SurveyQuestion(
            id           = "hand_quality",
            type         = "HAND",
            text         = localizedContext.getString(R.string.survey_q_hand),
            answerOption = AnswerOption.StarRating(maxStars = 5),
        )

        if (appUserWon) {
            // ── WIN branch ────────────────────────────────────────────────────

            // Q4 win — contextual: commander damage decisive
            val commanderWin = result.playerResults.any {
                it.eliminationReason == EliminationReason.COMMANDER_DAMAGE
            }
            if (commanderWin && result.gameMode == GameMode.COMMANDER) {
                questions += SurveyQuestion(
                    id           = "commander_plan",
                    type         = "COMMANDER_DAMAGE",
                    text         = localizedContext.getString(R.string.survey_q_commander_plan),
                    contextBadge = localizedContext.getString(R.string.survey_q_commander_badge),
                    answerOption = AnswerOption.SingleChoice(listOf(
                        SurveyChoice("PLANNED",   localizedContext.getString(R.string.survey_a_planned),    "\uD83C\uDFAF"),
                        SurveyChoice("DEVELOPED", localizedContext.getString(R.string.survey_a_developed),  "\uD83C\uDF31"),
                        SurveyChoice("SURPRISE",  localizedContext.getString(R.string.survey_a_surprise),   "\uD83D\uDE2E"),
                    )),
                )
            }

            // Q5 win — optional: anything to do differently?
            questions += SurveyQuestion(
                id           = "win_improvement",
                type         = "FREE_TEXT",
                text         = localizedContext.getString(R.string.survey_q_win_improvement),
                contextBadge = localizedContext.getString(R.string.survey_q_optional_badge),
                answerOption = AnswerOption.FreeText,
            )

        } else {
            // ── LOSS branch ───────────────────────────────────────────────────

            // Q4 loss — what would you change?
            questions += SurveyQuestion(
                id           = "loss_reason",
                type         = "LOSS_REASON",
                text         = localizedContext.getString(R.string.survey_q_loss_reason),
                contextBadge = localizedContext.getString(R.string.survey_q_loss_reason_badge),
                answerOption = AnswerOption.SingleChoice(listOf(
                    SurveyChoice("REMOVAL",     localizedContext.getString(R.string.survey_a_more_removal),     "\u2694"),
                    SurveyChoice("CURVE",       localizedContext.getString(R.string.survey_a_better_curve),     "\uD83D\uDCCA"),
                    SurveyChoice("INTERACTION", localizedContext.getString(R.string.survey_a_more_interaction), "\uD83D\uDEE1"),
                    SurveyChoice("NOTHING",     localizedContext.getString(R.string.survey_a_nothing),           "\uD83E\uDD1D"),
                )),
            )

            // Q5 loss — contextual: sideboard if app user has a deck
            val appUser = result.allPlayers.firstOrNull { it.isAppUser }
            if (appUser?.deckId != null) {
                questions += SurveyQuestion(
                    id           = "sideboard",
                    type         = "SIDEBOARD",
                    text         = localizedContext.getString(R.string.survey_q_sideboard),
                    contextBadge = localizedContext.getString(R.string.survey_q_sideboard_badge),
                    answerOption = AnswerOption.SingleChoice(listOf(
                        SurveyChoice("SIDE_HELPED",  localizedContext.getString(R.string.survey_a_side_helped),  "\uD83D\uDCAA"),
                        SurveyChoice("SIDE_NO_HELP", localizedContext.getString(R.string.survey_a_side_no_help), "\uD83E\uDD37"),
                        SurveyChoice("NO_SWAPS",     localizedContext.getString(R.string.survey_a_side_no_swaps),"\uD83D\uDEAB"),
                        SurveyChoice("FORGOT",       localizedContext.getString(R.string.survey_a_side_forgot),  "\uD83E\uDD26"),
                    )),
                )
            }
        }

        // Last — always: free notes
        questions += SurveyQuestion(
            id           = "free_notes",
            type         = "FREE_TEXT",
            text         = localizedContext.getString(R.string.survey_q_free_text),
            contextBadge = localizedContext.getString(R.string.survey_q_free_text_badge),
            answerOption = AnswerOption.FreeText,
        )

        return questions.take(8)
    }

    // For future use when deck cards are available
    fun buildCardImpactQuestion(
        cardName:     String,
        scryfallId:   String,
        contextBadge: String? = null,
    ) = SurveyQuestion(
        id            = "card_impact_$scryfallId",
        type          = "CARD_IMPACT",
        text          = "How did $cardName perform?",
        contextBadge  = contextBadge,
        cardReference = scryfallId,
        answerOption  = AnswerOption.SingleChoice(listOf(
            SurveyChoice("KEY_CARD", "Key card", "\uD83D\uDD25"),
            SurveyChoice("AVERAGE",  "Average",  "\uD83D\uDE10"),
            SurveyChoice("WEAK",     "Weak",     "\uD83D\uDE34"),
        )),
    )
}
