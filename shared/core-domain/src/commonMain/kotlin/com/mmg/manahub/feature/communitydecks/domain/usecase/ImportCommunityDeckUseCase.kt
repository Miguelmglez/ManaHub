package com.mmg.manahub.feature.communitydecks.domain.usecase

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.model.CommunityDeck
import com.mmg.manahub.feature.decks.domain.usecase.ImportDeckCardsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.ImportOutcome
import com.mmg.manahub.feature.decks.domain.usecase.ImportSource

/**
 * Imports a fetched [CommunityDeck] (Archidekt) into a new local ManaHub deck.
 *
 * ## Deck Doctor Community/Archetype plan, Phase 6 — now a THIN ADAPTER
 * The parsing/resolution/write pipeline moved to
 * [com.mmg.manahub.feature.decks.domain.usecase.ImportDeckCardsUseCase] (shared with
 * [com.mmg.manahub.feature.decks.domain.usecase.ImportDeckUseCase] and the new deckstats.net URL
 * adapter). This class's PUBLIC `invoke(deck, onProgress)` signature AND its nested [ImportResult]
 * type are UNCHANGED — `CommunityDeckDetailViewModel` needed zero edits.
 *
 * The import is still deliberately RESILIENT (skip-on-unresolvable, never abort), still stamps
 * community-source attribution, and still sets the commander (Archidekt "Commander" category) +
 * cover card — all preserved verbatim inside [ImportDeckCardsUseCase]'s unified write path.
 */
class ImportCommunityDeckUseCase(
    private val importDeckCardsUseCase: ImportDeckCardsUseCase,
) {

    /** Convenience constructor kept for source compatibility with the original 3-dependency shape
     * (this codebase's own tests still construct it this way — see
     * `app/src/test/.../ImportCommunityDeckUseCaseTest.kt`) — internally wraps a fresh
     * [ImportDeckCardsUseCase]. */
    constructor(
        deckRepository: DeckRepository,
        cardRepository: CardRepository,
        crashReporter: CrashReporter,
    ) : this(ImportDeckCardsUseCase(deckRepository = deckRepository, cardRepository = cardRepository, crashReporter = crashReporter))

    /** Outcome of an import attempt — kept as this class's OWN nested type (not
     * [ImportOutcome] directly) so `CommunityDeckDetailViewModel`'s existing `when` branches over
     * `ImportCommunityDeckUseCase.ImportResult.Success`/`.Error` need zero edits. */
    sealed class ImportResult {
        /**
         * The deck was created. [resolvedCount] cards were added; [failedCount] could not be
         * resolved against Scryfall and were skipped.
         */
        data class Success(
            val deckId: String,
            val resolvedCount: Int,
            val failedCount: Int,
        ) : ImportResult()

        /** The import failed before completing (deck creation or a repository write threw). */
        data class Error(val message: String) : ImportResult()
    }

    /**
     * @param deck the community deck to import.
     * @param onProgress invoked after each card with (processed, total) so the UI can show progress.
     */
    suspend operator fun invoke(
        deck: CommunityDeck,
        onProgress: (resolved: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportResult = when (
        val outcome = importDeckCardsUseCase(
            source = ImportSource.FromCommunityDeck(deck),
            targetDeckId = null, // always creates a new deck (original contract).
            onProgress = onProgress,
        )
    ) {
        is ImportOutcome.Success -> ImportResult.Success(outcome.deckId, outcome.resolvedCount, outcome.failedCount)
        is ImportOutcome.Error -> ImportResult.Error(outcome.message)
    }
}
