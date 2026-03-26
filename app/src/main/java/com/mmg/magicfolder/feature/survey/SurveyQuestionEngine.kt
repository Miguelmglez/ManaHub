package com.mmg.magicfolder.feature.survey

import com.mmg.magicfolder.core.data.local.entity.GameSessionWithPlayers

// ── Answer option ─────────────────────────────────────────────────────────────

data class AnswerOption(val id: String, val label: String)

// ── Question types ────────────────────────────────────────────────────────────

sealed class SurveyQuestion {
    abstract val key: String
    abstract val prompt: String

    data class SingleChoice(
        override val key:     String,
        override val prompt:  String,
        val options:          List<AnswerOption>,
    ) : SurveyQuestion()

    data class MultiChoice(
        override val key:     String,
        override val prompt:  String,
        val options:          List<AnswerOption>,
    ) : SurveyQuestion()

    data class StarRating(
        override val key:     String,
        override val prompt:  String,
        val maxStars:         Int = 5,
    ) : SurveyQuestion()

    data class FreeText(
        override val key:     String,
        override val prompt:  String,
        val hint:             String = "",
    ) : SurveyQuestion()
}

// ── Engine ────────────────────────────────────────────────────────────────────

object SurveyQuestionEngine {

    fun buildQuestions(session: GameSessionWithPlayers): List<SurveyQuestion> {
        val q       = mutableListOf<SurveyQuestion>()
        val players = session.players
        val mode    = session.session.mode          // "COMMANDER" / "STANDARD" / …
        val duration = session.session.durationMs

        // 1. Always: overall star rating
        q += SurveyQuestion.StarRating(
            key    = "overall_rating",
            prompt = "How would you rate this game overall?",
        )

        // 2. Game pace — always
        q += SurveyQuestion.SingleChoice(
            key    = "game_pace",
            prompt = "How was the game pace?",
            options = listOf(
                AnswerOption("too_slow",   "Too slow"),
                AnswerOption("just_right", "Just right"),
                AnswerOption("too_fast",   "Too fast"),
            ),
        )

        // 3. Biggest threat — if Commander and 3+ players
        if (mode == "COMMANDER" && players.size >= 3) {
            q += SurveyQuestion.SingleChoice(
                key    = "biggest_threat",
                prompt = "Who was the biggest threat at the table?",
                options = players.map { AnswerOption(it.playerId.toString(), it.playerName) },
            )
        }

        // 4. MVP — if 3+ players
        if (players.size >= 3) {
            q += SurveyQuestion.SingleChoice(
                key    = "mvp",
                prompt = "Who played the best this game?",
                options = players.map { AnswerOption(it.playerId.toString(), it.playerName) },
            )
        }

        // 5. Game felt too long — only if > 45 min
        if (duration > 45 * 60 * 1000L) {
            q += SurveyQuestion.SingleChoice(
                key    = "too_long",
                prompt = "Did the game feel too long?",
                options = listOf(
                    AnswerOption("yes",      "Yes"),
                    AnswerOption("somewhat", "Somewhat"),
                    AnswerOption("no",       "No"),
                ),
            )
        }

        // 6. Highlights — multi-choice, always
        q += SurveyQuestion.MultiChoice(
            key    = "highlights",
            prompt = "What did you enjoy most? (pick all that apply)",
            options = listOf(
                AnswerOption("close_finish",  "Close finish"),
                AnswerOption("big_plays",     "Big plays"),
                AnswerOption("comebacks",     "Comeback moments"),
                AnswerOption("strategy",      "Strategic decisions"),
                AnswerOption("social",        "Playing with friends"),
            ),
        )

        // 7. Rematch? — always
        q += SurveyQuestion.SingleChoice(
            key    = "rematch",
            prompt = "Would you like a rematch?",
            options = listOf(
                AnswerOption("yes",   "Yes, immediately!"),
                AnswerOption("later", "Maybe later"),
                AnswerOption("no",    "No thanks"),
            ),
        )

        // 8. Free-text notes — always last
        q += SurveyQuestion.FreeText(
            key    = "notes",
            prompt = "Any notes about this game?",
            hint   = "Interesting moments, house rules, memorable plays…",
        )

        return q
    }
}
