package com.mmg.magicfolder.feature.survey

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

    fun buildQuestions(result: GameResult): List<SurveyQuestion> {
        val questions   = mutableListOf<SurveyQuestion>()
        val localPlayer = result.playerResults.firstOrNull()
        val isWinner    = localPlayer?.player?.id == result.winner.id

        // Q1 — always: subjective outcome
        questions += SurveyQuestion(
            id   = "result_feel",
            type = "RESULT_FEEL",
            text = if (isWinner) "How did the win feel?" else "What cost you the game?",
            answerOption = AnswerOption.SingleChoice(
                if (isWinner) listOf(
                    SurveyChoice("DOMINANT",  "Dominant",    "\uD83D\uDCAA"),
                    SurveyChoice("CLOSE",     "Close call",  "\uD83D\uDE05"),
                    SurveyChoice("LUCKY",     "Lucky draw",  "\uD83C\uDF40"),
                    SurveyChoice("SKILLFUL",  "Outplayed",   "\uD83E\uDDE0"),
                ) else listOf(
                    SurveyChoice("OVERWHELMED", "Overwhelmed", "\u26A1"),
                    SurveyChoice("MANA",        "Mana issues", "\uD83C\uDFD4"),
                    SurveyChoice("NO_ANSWERS",  "No answers",  "\uD83D\uDEAB"),
                    SurveyChoice("TOO_SLOW",    "Too slow",    "\uD83D\uDC22"),
                )
            ),
        )

        // Q2 — always: mana health
        questions += SurveyQuestion(
            id   = "mana_health",
            type = "MANA",
            text = "How was your mana?",
            answerOption = AnswerOption.MultiChoice(listOf(
                SurveyChoice("SMOOTH",  "Smooth",       "\u2705"),
                SurveyChoice("FLOODED", "Land flooded", "\uD83C\uDF0A"),
                SurveyChoice("SCREWED", "Mana screwed", "\uD83C\uDFDC"),
                SurveyChoice("COLORS",  "Wrong colors", "\uD83C\uDFA8"),
                SurveyChoice("NONE",    "No issues",    "\uD83D\uDC4D"),
            )),
        )

        // Q3 — always: opening hand
        questions += SurveyQuestion(
            id           = "hand_quality",
            type         = "HAND",
            text         = "How was your opening hand?",
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
                text         = "Commander damage was decisive.\nWas that your plan?",
                contextBadge = "\u2694 Commander damage win",
                answerOption = AnswerOption.SingleChoice(listOf(
                    SurveyChoice("PLANNED",   "Planned it",          "\uD83C\uDFAF"),
                    SurveyChoice("DEVELOPED", "Developed naturally", "\uD83C\uDF31"),
                    SurveyChoice("SURPRISE",  "Surprised me too",    "\uD83D\uDE2E"),
                )),
            )
        }

        // Q5 — contextual: loss reason
        if (!isWinner) {
            questions += SurveyQuestion(
                id   = "loss_reason",
                type = "LOSS_REASON",
                text = "What would have changed the outcome?",
                answerOption = AnswerOption.SingleChoice(listOf(
                    SurveyChoice("REMOVAL",     "More removal",        "\u2694"),
                    SurveyChoice("CURVE",       "Better mana curve",   "\uD83D\uDCCA"),
                    SurveyChoice("INTERACTION", "More interaction",    "\uD83D\uDEE1"),
                    SurveyChoice("NOTHING",     "Opponent was better", "\uD83E\uDD1D"),
                )),
            )
        }

        // Q6 — always last: free notes
        questions += SurveyQuestion(
            id           = "free_notes",
            type         = "FREE_TEXT",
            text         = "Anything to note about this game?",
            contextBadge = "Optional",
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
