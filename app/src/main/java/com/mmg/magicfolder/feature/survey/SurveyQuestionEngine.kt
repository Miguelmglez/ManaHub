package com.mmg.magicfolder.feature.survey

import android.content.Context
import com.mmg.magicfolder.R
import com.mmg.magicfolder.feature.game.model.EliminationReason
import com.mmg.magicfolder.feature.game.model.GameMode
import com.mmg.magicfolder.feature.game.model.GameResult

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

    fun buildQuestions(result: GameResult, context: Context): List<SurveyQuestion> {
        val questions   = mutableListOf<SurveyQuestion>()
        val localPlayer = result.playerResults.firstOrNull()
        val isWinner    = localPlayer?.player?.id == result.winner.id

        // Q1 — always: subjective outcome
        questions += SurveyQuestion(
            id   = "result_feel",
            type = "RESULT_FEEL",
            text = if (isWinner) context.getString(R.string.survey_q_result_win) else context.getString(R.string.survey_q_result_loss),
            answerOption = AnswerOption.SingleChoice(
                if (isWinner) listOf(
                    SurveyChoice("DOMINANT",  context.getString(R.string.survey_a_dominant),    "\uD83D\uDCAA"),
                    SurveyChoice("CLOSE",     context.getString(R.string.survey_a_close),       "\uD83D\uDE05"),
                    SurveyChoice("LUCKY",     context.getString(R.string.survey_a_lucky),       "\uD83C\uDF40"),
                    SurveyChoice("SKILLFUL",  context.getString(R.string.survey_a_skillful),    "\uD83E\uDDE0"),
                ) else listOf(
                    SurveyChoice("OVERWHELMED", context.getString(R.string.survey_a_overwhelmed), "\u26A1"),
                    SurveyChoice("MANA",        context.getString(R.string.survey_a_mana_issues), "\uD83C\uDFD4"),
                    SurveyChoice("NO_ANSWERS",  context.getString(R.string.survey_a_no_answers),  "\uD83D\uDEAB"),
                    SurveyChoice("TOO_SLOW",    context.getString(R.string.survey_a_too_slow),    "\uD83D\uDC22"),
                )
            ),
        )

        // Q2 — always: mana health
        questions += SurveyQuestion(
            id   = "mana_health",
            type = "MANA",
            text = context.getString(R.string.survey_q_mana),
            answerOption = AnswerOption.MultiChoice(listOf(
                SurveyChoice("SMOOTH",  context.getString(R.string.survey_a_mana_smooth),   "\u2705"),
                SurveyChoice("FLOODED", context.getString(R.string.survey_a_mana_flooded),  "\uD83C\uDF0A"),
                SurveyChoice("SCREWED", context.getString(R.string.survey_a_mana_screwed),  "\uD83C\uDFDC"),
                SurveyChoice("COLORS",  context.getString(R.string.survey_a_mana_colors),   "\uD83C\uDFA8"),
                SurveyChoice("NONE",    context.getString(R.string.survey_a_mana_none),     "\uD83D\uDC4D"),
            )),
        )

        // Q3 — always: opening hand
        questions += SurveyQuestion(
            id           = "hand_quality",
            type         = "HAND",
            text         = context.getString(R.string.survey_q_hand),
            answerOption = AnswerOption.StarRating(maxStars = 5),
        )

        // Q4 — contextual: commander damage decisive
        val commanderWin = result.playerResults.any {
            it.eliminationReason == EliminationReason.COMMANDER_DAMAGE
        }
        if (commanderWin && result.gameMode == GameMode.COMMANDER) {
            questions += SurveyQuestion(
                id           = "commander_plan",
                type         = "COMMANDER_DAMAGE",
                text         = context.getString(R.string.survey_q_commander_plan),
                contextBadge = context.getString(R.string.survey_q_commander_badge),
                answerOption = AnswerOption.SingleChoice(listOf(
                    SurveyChoice("PLANNED",   context.getString(R.string.survey_a_planned),    "\uD83C\uDFAF"),
                    SurveyChoice("DEVELOPED", context.getString(R.string.survey_a_developed),  "\uD83C\uDF31"),
                    SurveyChoice("SURPRISE",  context.getString(R.string.survey_a_surprise),   "\uD83D\uDE2E"),
                )),
            )
        }

        // Q5 — contextual: loss reason
        if (!isWinner) {
            questions += SurveyQuestion(
                id   = "loss_reason",
                type = "LOSS_REASON",
                text = context.getString(R.string.survey_q_loss_reason),
                answerOption = AnswerOption.SingleChoice(listOf(
                    SurveyChoice("REMOVAL",     context.getString(R.string.survey_a_more_removal),    "\u2694"),
                    SurveyChoice("CURVE",       context.getString(R.string.survey_a_better_curve),    "\uD83D\uDCCA"),
                    SurveyChoice("INTERACTION", context.getString(R.string.survey_a_more_interaction), "\uD83D\uDEE1"),
                    SurveyChoice("NOTHING",     context.getString(R.string.survey_a_nothing),          "\uD83E\uDD1D"),
                )),
            )
        }

        // Q6 — always last: free notes
        questions += SurveyQuestion(
            id           = "free_notes",
            type         = "FREE_TEXT",
            text         = context.getString(R.string.survey_q_free_text),
            contextBadge = context.getString(R.string.survey_q_free_text_badge),
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
