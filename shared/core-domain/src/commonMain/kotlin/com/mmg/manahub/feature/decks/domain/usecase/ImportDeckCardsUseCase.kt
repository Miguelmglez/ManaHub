package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.model.CommunityDeck
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.feature.decks.domain.engine.DeckImportExportHelper
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * One already-resolved card entry from a deckstats.net deck fetch. Deliberately a PLAIN domain
 * type — see [DeckstatsFetcher]'s KDoc for why core-domain never sees the raw JSON/DTO shape.
 */
data class DeckstatsCard(val name: String, val quantity: Int, val isSideboard: Boolean, val isCommander: Boolean)

/** An already-parsed deckstats.net deck (name optional — deckstats may omit it; empty [cards] is
 * treated as a fetch failure by [ImportDeckCardsUseCase], never a valid empty import). */
data class DeckstatsDeck(val name: String?, val cards: List<DeckstatsCard>)

/**
 * Testing seam / provider abstraction (D17: "the Worker and client define a `CommunityDeckSource`
 * provider interface so future providers plug in without rewrites") for the deckstats.net
 * import-by-URL adapter (Phase 6). The concrete Ktor + JSON-parsing implementation lives in
 * `core-data` (`com.mmg.manahub.core.data.remote.DeckstatsFetcherImpl`) — mirroring the exact
 * `CommunityAggregateApiContract`/`SixtyFallbackFetcher` seam Phase 3 established (`core-domain`
 * consumes an interface, never a concrete Ktor client, so it needs no Ktor/kotlinx.serialization
 * dependency and is trivially fakeable in commonTest).
 *
 * [fetchDeck] returns `null` on ANY failure — a malformed/unrecognized URL, a network error, a
 * non-2xx response, or (per `docs/adr/ADR-004-community-api-contracts.md` §4) an UNPARSEABLE body,
 * since deckstats' success-path JSON shape was never verified live in the Phase 3 spike. The
 * implementation is responsible for logging the specific failure category via [CrashReporter];
 * [ImportDeckCardsUseCase] only needs to know "it worked" vs. "it didn't."
 */
fun interface DeckstatsFetcher {
    suspend fun fetchDeck(url: String): DeckstatsDeck?
}

/**
 * Where the cards being imported come from (Deck Doctor Community/Archetype plan, Phase 6). The
 * plan's D17 `CommunityDeckSource` provider-interface intent is satisfied by this sealed hierarchy
 * being the single extension point for a future provider (Moxfield, a future deckstats SEARCH
 * integration if one is ever exposed) — adding a case here is the whole integration surface.
 */
sealed interface ImportSource {
    /** A pasted Moxfield / MTG Arena text deck list. */
    data class PastedText(val text: String) : ImportSource

    /** An already-fetched Archidekt community deck (Community Decks feature). */
    data class FromCommunityDeck(val deck: CommunityDeck) : ImportSource

    /** A pasted deckstats.net deck URL (D17 import-by-URL; NO deckstats aggregate/search — see
     * [com.mmg.manahub.core.data.remote.DeckstatsClient]'s KDoc for the unverified-shape caveat). */
    data class DeckstatsUrl(val url: String) : ImportSource
}

/** Outcome of [ImportDeckCardsUseCase]. */
sealed class ImportOutcome {
    /** The deck was created/updated. [resolvedCount] cards were added; [failedCount] could not be
     * resolved against Scryfall and were skipped (never aborts the batch). */
    data class Success(val deckId: String, val resolvedCount: Int, val failedCount: Int) : ImportOutcome()

    /** The import failed before completing (deck creation, a repository write, or — for
     * [ImportSource.DeckstatsUrl] — URL parsing / the network call / a shape mismatch). */
    data class Error(val message: String) : ImportOutcome()
}

/**
 * Unified import pipeline (Deck Doctor Community/Archetype plan, Phase 6): a single parametrized
 * entry point over every [ImportSource]. [ImportDeckUseCase] (paste-into-existing-deck) and
 * [com.mmg.manahub.feature.communitydecks.domain.usecase.ImportCommunityDeckUseCase]
 * (create-new-deck-from-Archidekt) are now THIN ADAPTERS over this class — their PUBLIC `invoke`
 * signatures are unchanged, so `DeckStudioViewModel`/`CommunityDeckDetailViewModel` call sites needed
 * zero edits.
 *
 * ## What every source shares (preserved from the two originals, never reinvented)
 * - Skip-on-unresolvable: a card that fails Scryfall resolution ([CardRepository.searchCardByName])
 *   is SKIPPED, never aborts the batch.
 * - The `>50%`-failure-rate telemetry breadcrumb (`deck_import_high_failure_rate`), fired once per
 *   import when more than half the cards failed to resolve.
 * - Every card resolution routes through [CardRepository.searchCardByName], which is itself
 *   rate-limited through `ScryfallRequestQueue` and writes via the safe INSERT-OR-IGNORE + `@Update`
 *   upsert (never `OnConflictStrategy.REPLACE` on `CardEntity`) — this use case never touches Room
 *   directly, it only calls the repository.
 * - `onProgress(resolved+failed, total)` after each card.
 * - Commander assignment (first resolved commander-flagged card wins) + community-source attribution
 *   stamping ([DeckRepository.updateDeckAttribution]) when the source carries either.
 *
 * ## Two write modes
 * - [targetDeckId] non-null: writes INTO that existing live deck (mirrors [ImportDeckUseCase]'s
 *   original contract — no rename, no new deck).
 * - [targetDeckId] null: CREATES a new deck first (mirrors [com.mmg.manahub.feature.communitydecks
 *   .domain.usecase.ImportCommunityDeckUseCase]'s original contract), named/formatted from the
 *   source when available.
 */
@OptIn(ExperimentalTime::class)
class ImportDeckCardsUseCase(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val crashReporter: CrashReporter,
    /** Only required for [ImportSource.DeckstatsUrl]; every other source never touches it. */
    private val deckstatsFetcher: DeckstatsFetcher? = null,
) {

    /**
     * @param source what to import.
     * @param targetDeckId writes into this EXISTING deck when non-null; creates a new deck when null.
     * @param onProgress invoked after each card with (processed, total).
     */
    suspend operator fun invoke(
        source: ImportSource,
        targetDeckId: String? = null,
        onProgress: (resolved: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportOutcome {
        val parsed = when (source) {
            is ImportSource.PastedText -> parsePastedText(source.text)
            is ImportSource.FromCommunityDeck -> parseCommunityDeck(source.deck)
            is ImportSource.DeckstatsUrl -> parseDeckstatsUrlSource(source.url)
                ?: return ImportOutcome.Error("Couldn't read this deckstats.net deck — check the URL or try again later.")
        }
        return writeImport(parsed, targetDeckId, onProgress)
    }

    // ── Per-source parsing → a common internal representation ──────────────────────

    private data class ParsedCard(val name: String, val quantity: Int, val isSideboard: Boolean, val isCommander: Boolean)
    private data class Attribution(val sourceUrl: String?, val sourceAuthor: String?, val sourceService: String)
    private data class ParsedImport(
        val cards: List<ParsedCard>,
        val suggestedName: String? = null,
        val suggestedDescription: String? = null,
        val suggestedFormat: String? = null,
        val attribution: Attribution? = null,
    )

    private fun parsePastedText(text: String): ParsedImport {
        val parsed = DeckImportExportHelper.parse(text)
        val cards = buildList {
            parsed.mainboard.forEach { add(ParsedCard(it.name, it.quantity, isSideboard = false, isCommander = false)) }
            parsed.sideboard.forEach { add(ParsedCard(it.name, it.quantity, isSideboard = true, isCommander = false)) }
            parsed.commander?.let { add(ParsedCard(it.name, it.quantity, isSideboard = false, isCommander = true)) }
        }
        // ImportDeckUseCase's original contract: no rename (ParsedDeckList carries no deck name).
        return ParsedImport(cards = cards)
    }

    private fun parseCommunityDeck(deck: CommunityDeck): ParsedImport = ParsedImport(
        cards = deck.cards.map { ParsedCard(it.name, it.quantity, isSideboard = it.isSideboard, isCommander = it.isCommander) },
        suggestedName = deck.name,
        suggestedDescription = deck.description,
        suggestedFormat = deck.format,
        attribution = Attribution(sourceUrl = deck.sourceUrl, sourceAuthor = deck.owner.username, sourceService = "archidekt"),
    )

    /** Delegates to the injected [DeckstatsFetcher] seam — see its KDoc for the unverified-shape
     * caveat. `null` (no fetcher wired, or the fetch/parse failed) surfaces one clear error to the
     * caller rather than propagating a raw exception. */
    private suspend fun parseDeckstatsUrlSource(url: String): ParsedImport? {
        val fetcher = deckstatsFetcher ?: return null
        val deck = fetcher.fetchDeck(url) ?: return null
        if (deck.cards.isEmpty()) return null
        return ParsedImport(
            cards = deck.cards.map { ParsedCard(it.name, it.quantity, isSideboard = it.isSideboard, isCommander = it.isCommander) },
            suggestedName = deck.name?.ifBlank { null },
            attribution = Attribution(sourceUrl = url, sourceAuthor = null, sourceService = "deckstats"),
        )
    }

    // ── Unified write path ──────────────────────────────────────────────────────────

    private suspend fun writeImport(
        parsed: ParsedImport,
        targetDeckId: String?,
        onProgress: (resolved: Int, total: Int) -> Unit,
    ): ImportOutcome {
        val deckId = targetDeckId ?: run {
            try {
                deckRepository.createDeck(
                    name = parsed.suggestedName ?: DEFAULT_DECK_NAME,
                    description = parsed.suggestedDescription ?: DEFAULT_DECK_DESCRIPTION,
                    format = parsed.suggestedFormat ?: "casual",
                )
            } catch (t: Throwable) {
                crashReporter.log("deck_import_create_failed")
                crashReporter.recordException(t)
                return ImportOutcome.Error(t.message ?: "Import failed")
            }
        }

        return try {
            var resolvedCount = 0
            var failedCount = 0
            var commanderScryfallId: String? = null
            val totalPhysicalCards = parsed.cards.sumOf { it.quantity }
            var physicalProcessedCount = 0

            parsed.cards.forEachIndexed { index, card ->
                val result = cardRepository.searchCardByName(card.name)
                if (result is DataResult.Success) {
                    deckRepository.addCardToDeck(
                        deckId = deckId,
                        scryfallId = result.data.scryfallId,
                        quantity = card.quantity,
                        isSideboard = card.isSideboard,
                    )
                    // Commander auto-assignment is scoped to NEW-deck creation only (targetDeckId
                    // == null) — see the class KDoc's "Two write modes". Importing pasted TEXT
                    // into an EXISTING live deck (ImportDeckUseCase's original contract) must NEVER
                    // silently overwrite that deck's already-chosen commander; the original
                    // ImportDeckUseCase never touched `commanderCardId` at all.
                    if (targetDeckId == null && card.isCommander && commanderScryfallId == null) {
                        commanderScryfallId = result.data.scryfallId
                    }
                    resolvedCount++
                } else {
                    // Never the card name itself (PII-adjacent) — index + name length only.
                    crashReporter.log("deck_import_card_unresolved: index=$index, name_length=${card.name.length}")
                    failedCount++
                }
                physicalProcessedCount += card.quantity
                onProgress(physicalProcessedCount, totalPhysicalCards)
            }

            if (parsed.cards.isNotEmpty() && failedCount > parsed.cards.size / 2) {
                crashReporter.log("deck_import_high_failure_rate")
                crashReporter.recordException(IllegalStateException("$failedCount/${parsed.cards.size} cards unresolved"))
            }

            commanderScryfallId?.let { commanderId ->
                deckRepository.observeDeckWithCards(deckId).let { flow ->
                    val current = flow.first()?.deck
                    if (current != null) {
                        deckRepository.updateDeck(
                            current.copy(
                                commanderCardId = commanderId,
                                coverCardId = current.coverCardId ?: commanderId,
                                updatedAt = Clock.System.now().toEpochMilliseconds(),
                            )
                        )
                    }
                }
            }

            parsed.attribution?.let { a ->
                deckRepository.updateDeckAttribution(
                    deckId = deckId,
                    sourceUrl = a.sourceUrl,
                    sourceAuthor = a.sourceAuthor,
                    sourceService = a.sourceService,
                    importedAt = Clock.System.now().toEpochMilliseconds(),
                )
            }

            ImportOutcome.Success(deckId = deckId, resolvedCount = resolvedCount, failedCount = failedCount)
        } catch (t: Throwable) {
            crashReporter.log("deck_import_failed")
            crashReporter.recordException(t)
            crashReporter.setCustomKey("deck_import_card_count", parsed.cards.size.toString())
            ImportOutcome.Error(t.message ?: "Import failed")
        }
    }

    private companion object {
        const val DEFAULT_DECK_NAME = "Imported Deck"
        const val DEFAULT_DECK_DESCRIPTION = "Imported deck list"
    }
}
