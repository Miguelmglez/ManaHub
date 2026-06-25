package com.mmg.manahub.feature.playtest.domain.usecase

import com.mmg.manahub.core.model.HandSnapshot
import com.mmg.manahub.core.model.PlaytestSetup
import com.mmg.manahub.core.domain.repository.PlaytestRepository

/**
 * Saves a completed playtest session to the database.
 *
 * This is the ONLY write path for a test session. It is called when the user
 * taps "Guardar test" — never during the redraw/mulligan loop.
 *
 * Save mapping:
 *   - [PlaytestSessionEntity.drawCount] = setup.drawCount (configured count, e.g. 7).
 *     finalHandSize = drawCount - mulligansUsed — derivable from the two fields.
 *   - [PlaytestSessionEntity.mulligansUsed] = snapshot.mulligansUsed.
 *   - [PlaytestSessionEntity.librarySize] = library size at game start
 *     (99 for commander, full mainboard size otherwise).
 *   - Per-card counts: collapse the hand list and bottomedScryfallIds into
 *     per-scryfallId integer counts.
 *
 * @return The auto-generated session id (needed to attach a survey later).
 */
class SavePlaytestUseCase(
    private val repository: PlaytestRepository,
) {

    suspend operator fun invoke(
        setup: PlaytestSetup,
        snapshot: HandSnapshot,
        librarySize: Int,
    ): Long {
        // Collapse hand list into per-scryfallId counts.
        val handCounts = snapshot.hand
            .groupingBy { it.scryfallId }
            .eachCount()

        // Collapse bottomed scryfallId list into per-scryfallId counts.
        val bottomedCounts = snapshot.bottomedScryfallIds
            .groupingBy { it }
            .eachCount()

        return repository.saveTest(
            deckId              = setup.deckId,
            deckFormat          = setup.deckFormat,
            configuredDrawCount = setup.drawCount,
            mulligansUsed       = snapshot.mulligansUsed,
            librarySize         = librarySize,
            onThePlay           = setup.isOnThePlay,
            startedAt           = snapshot.startedAt,
            cardCountsInHand    = handCounts,
            cardCountsBottomed  = bottomedCounts,
        )
    }
}
