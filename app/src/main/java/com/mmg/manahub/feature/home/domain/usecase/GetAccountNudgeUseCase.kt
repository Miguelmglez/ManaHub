package com.mmg.manahub.feature.home.domain.usecase

import com.mmg.manahub.feature.home.presentation.NudgeTrigger
import javax.inject.Inject

/**
 * Resolves the highest-priority account nudge trigger for an unauthenticated user.
 *
 * The 5-priority system (highest → lowest):
 * 1. [NudgeTrigger.ACTION_REQUIRED]      — externally raised by the VM; passed as [actionRequired].
 * 2. [NudgeTrigger.COLLECTION_MILESTONE] — user has ≥ [COLLECTION_MILESTONE] unique cards.
 * 3. [NudgeTrigger.DECK_MILESTONE]       — user has ≥ [DECK_MILESTONE] decks.
 * 4. [NudgeTrigger.GAME_MILESTONE]       — user has played ≥ [GAME_MILESTONE] games.
 * 5. `null`                              — no nudge applies.
 *
 * The 48-hour cooldown check and the authenticated guard are the caller's responsibility
 * (pass `isCoolingDown = true` or `isAuthenticated = true` to suppress all nudges).
 *
 * This use case contains zero Android framework imports and is fully testable as a
 * plain Kotlin unit test.
 *
 * @return The highest-priority [NudgeTrigger] that applies, or `null` if none.
 */
class GetAccountNudgeUseCase @Inject constructor() {

    /**
     * Evaluates nudge eligibility.
     *
     * @param isAuthenticated    When `true` the user is signed in — no nudge is shown.
     * @param isCoolingDown      When `true` the 48-hour dismissal cooldown is active.
     * @param actionRequired     Non-null string means an ACTION_REQUIRED nudge was raised externally.
     * @param uniqueCards        Number of unique cards in the user's collection.
     * @param deckCount          Number of decks the user owns.
     * @param totalGames         Total number of tracked games played.
     * @return The winning [NudgeTrigger], or `null` when no nudge should be shown.
     */
    operator fun invoke(
        isAuthenticated: Boolean,
        isCoolingDown: Boolean,
        actionRequired: String?,
        uniqueCards: Int,
        deckCount: Int,
        totalGames: Int,
    ): NudgeTrigger? {
        if (isAuthenticated) return null

        // ACTION_REQUIRED bypasses the cooldown gate.
        if (actionRequired != null) return NudgeTrigger.ACTION_REQUIRED

        if (isCoolingDown) return null

        if (uniqueCards >= COLLECTION_MILESTONE) return NudgeTrigger.COLLECTION_MILESTONE
        if (deckCount >= DECK_MILESTONE) return NudgeTrigger.DECK_MILESTONE
        if (totalGames >= GAME_MILESTONE) return NudgeTrigger.GAME_MILESTONE

        return null
    }

    companion object {
        /** Minimum unique cards to show the collection milestone nudge. */
        const val COLLECTION_MILESTONE = 10

        /** Minimum deck count to show the deck milestone nudge. */
        const val DECK_MILESTONE = 2

        /** Minimum games played to show the game milestone nudge. */
        const val GAME_MILESTONE = 3
    }
}
