package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository

/**
 * Imports a Moxfield / MTG Arena text deck list INTO an already-existing deck.
 *
 * Extracted from `DeckViewModel.importDeck` so the unified Deck Studio can import
 * straight into its live draft deck (rather than always creating a brand-new deck).
 *
 * ## Deck Doctor Community/Archetype plan, Phase 6 — now a THIN ADAPTER
 * The parsing/resolution/write pipeline moved to [ImportDeckCardsUseCase] (shared with
 * [com.mmg.manahub.feature.communitydecks.domain.usecase.ImportCommunityDeckUseCase] and the new
 * deckstats.net URL adapter). This class's PUBLIC `invoke(deckId, text)` signature is UNCHANGED —
 * `DeckStudioViewModel.importDeck` needed zero edits. A failed INDIVIDUAL card resolution was never
 * surfaced as a `Result.failure` before, and still isn't (skip-on-unresolvable is preserved by
 * [ImportDeckCardsUseCase]); only a hard exception during a repository write now maps to
 * [Result.failure], via [ImportOutcome.Error].
 *
 * Note: a pasted deck list carries no deck name, so this path still does NOT rename the target deck
 * — the caller keeps the live deck's existing name (unchanged behavior).
 */
class ImportDeckUseCase(
    private val importDeckCardsUseCase: ImportDeckCardsUseCase,
) {

    /**
     * Convenience constructor kept for source compatibility with the original 2-dependency shape
     * (this codebase's own tests still construct it this way — see
     * `app/src/test/.../ImportDeckUseCaseTest.kt`) — internally wraps a fresh
     * [ImportDeckCardsUseCase]. `crashReporter` defaults to a no-op so existing 2-arg call sites
     * need no change; Koin wiring passes a real one explicitly (see `DecksKoinModule`).
     */
    constructor(
        cardRepository: CardRepository,
        deckRepository: DeckRepository,
        crashReporter: CrashReporter = NoOpCrashReporter,
    ) : this(ImportDeckCardsUseCase(deckRepository = deckRepository, cardRepository = cardRepository, crashReporter = crashReporter))

    /**
     * Parses [text] and writes the resolved cards into the deck identified by [deckId].
     *
     * @param deckId the live deck to import into (must already exist).
     * @param text the raw pasted deck list (Moxfield / Arena format).
     * @return [Result.success] once parsing + all resolvable writes complete (even when
     *         some individual lines failed to resolve); [Result.failure] only when the underlying
     *         [ImportDeckCardsUseCase] returns [ImportOutcome.Error] (a repository write threw).
     */
    suspend operator fun invoke(deckId: String, text: String): Result<Unit> =
        when (val outcome = importDeckCardsUseCase(source = ImportSource.PastedText(text), targetDeckId = deckId)) {
            is ImportOutcome.Success -> Result.success(Unit)
            is ImportOutcome.Error -> Result.failure(IllegalStateException(outcome.message))
        }
}

/** A no-op [CrashReporter] used ONLY by [ImportDeckUseCase]'s legacy 2-arg convenience
 * constructor, so a caller/test that never cared about telemetry doesn't have to wire one. */
private object NoOpCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) = Unit
    override fun log(message: String) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}
