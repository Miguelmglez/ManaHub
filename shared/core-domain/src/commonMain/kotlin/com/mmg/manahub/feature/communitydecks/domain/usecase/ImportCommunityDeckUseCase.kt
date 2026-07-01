package com.mmg.manahub.feature.communitydecks.domain.usecase

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.model.CommunityDeck
import com.mmg.manahub.core.model.DataResult
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Imports a fetched [CommunityDeck] (Archidekt) into a new local ManaHub deck.
 *
 * The import is deliberately RESILIENT: each card is resolved against Scryfall by name
 * (via [CardRepository.searchCardByName], which is itself rate-limited through the
 * `ScryfallRequestQueue`), and a single unresolvable card is SKIPPED — it never aborts
 * the whole import. The resolved/failed counts are reported back so the UI can surface a
 * partial-import summary.
 *
 * On completion the new deck is stamped with community-source attribution
 * (URL / author / service / timestamp) via [DeckRepository.updateDeckAttribution].
 *
 * KMP migration note: reports through the platform-neutral [CrashReporter] (Crashlytics on
 * Android, no-op on web) instead of `FirebaseCrashlytics` directly, and uses
 * `Clock.System.now().toEpochMilliseconds()` instead of `System.currentTimeMillis()` — both
 * required for this use case to live in `commonMain`. Millisecond semantics are unchanged.
 *
 * @param crashReporter platform-neutral crash/log reporter, injected so Android keeps recording
 *   to Crashlytics with zero behavior change.
 */
@OptIn(ExperimentalTime::class)
class ImportCommunityDeckUseCase(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val crashReporter: CrashReporter,
) {

    /** Outcome of an import attempt. */
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
    ): ImportResult {
        return try {
            // 1. Create the local deck.
            val deckId = deckRepository.createDeck(
                name = deck.name,
                description = deck.description,
                format = deck.format,
            )

            // 2. Resolve + add cards (skip unresolvable ones, never abort).
            val allCards = deck.cards
            var resolvedCount = 0
            var failedCount = 0
            var commanderScryfallId: String? = null

            allCards.forEachIndexed { index, card ->
                val result = cardRepository.searchCardByName(card.name)
                if (result is DataResult.Success) {
                    deckRepository.addCardToDeck(
                        deckId = deckId,
                        scryfallId = result.data.scryfallId,
                        quantity = card.quantity,
                        isSideboard = card.isSideboard,
                    )
                    if (card.isCommander && commanderScryfallId == null) {
                        commanderScryfallId = result.data.scryfallId
                    }
                    resolvedCount++
                } else {
                    // Card could not be resolved against Scryfall — skip it (never abort).
                    // Log only the index + name length (never the name itself — PII-adjacent).
                    crashReporter.log(
                        "community_deck_import_card_unresolved: index=$index, name_length=${card.name.length}",
                    )
                    failedCount++
                }
                onProgress(resolvedCount + failedCount, allCards.size)
            }

            // If more than half the cards failed to resolve, surface a non-fatal so we can
            // diagnose systemic resolution problems (e.g. a broken Scryfall name mapping).
            if (failedCount > allCards.size / 2) {
                crashReporter.log("community_deck_import_high_failure_rate")
                crashReporter.recordException(
                    IllegalStateException("$failedCount/${allCards.size} cards unresolved"),
                )
            }

            // 3. Set the commander, if one was resolved. The repository exposes no dedicated
            //    setter, so we read the freshly-created deck and write back a copy — matching the
            //    established DeckStudio / DeckBuilder pattern.
            commanderScryfallId?.let { commanderId ->
                deckRepository.observeDeckWithCards(deckId).first()?.deck?.let { createdDeck ->
                    deckRepository.updateDeck(
                        createdDeck.copy(
                            commanderCardId = commanderId,
                            coverCardId = createdDeck.coverCardId ?: commanderId,
                            updatedAt = Clock.System.now().toEpochMilliseconds(),
                        )
                    )
                }
            }

            // 4. Stamp community-source attribution.
            deckRepository.updateDeckAttribution(
                deckId = deckId,
                sourceUrl = deck.sourceUrl,
                sourceAuthor = deck.owner.username,
                sourceService = "archidekt",
                importedAt = Clock.System.now().toEpochMilliseconds(),
            )

            ImportResult.Success(
                deckId = deckId,
                resolvedCount = resolvedCount,
                failedCount = failedCount,
            )
        } catch (e: Exception) {
            crashReporter.log("community_deck_import_failed")
            crashReporter.recordException(e)
            crashReporter.setCustomKey("community_deck_import_card_count", deck.cards.size.toString())
            ImportResult.Error(e.message ?: "Import failed")
        }
    }
}
