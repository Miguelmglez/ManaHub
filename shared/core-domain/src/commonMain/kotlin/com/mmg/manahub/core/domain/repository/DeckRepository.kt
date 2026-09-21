package com.mmg.manahub.core.domain.repository
// COMMENTS_REVIEWED: 2026-09-08

import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.model.DeckWithCards
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** One slot for [DeckRepository.replaceAllCardsWithSource] -- see that function's KDoc. */
data class CardSlotWrite(
    val scryfallId: String,
    val quantity: Int,
    val isSideboard: Boolean = false,
    val source: DeckCardSource = DeckCardSource.USER,
)

data class DeckCardAddition(
    val entryId: String,
    val scryfallId: String,
    val oracleId: String,
    val quantity: Int,
    val isSideboard: Boolean,
)

data class DeckCardAdditionResult(
    val committedEntryIds: Set<String>,
    val blockedCommanderEntryIds: Set<String>,
    val committedCopies: Int,
)

/**
 * Contract for all deck persistence operations.
 *
 * Sync is NOT part of this interface. The [com.mmg.manahub.core.sync.SyncManager]
 * owns the push/pull cycle. This repository is responsible only for local CRUD.
 * All mutations update [Deck.updatedAt] so the sync engine can detect dirty rows.
 */
interface DeckRepository {

    // ── Observables ───────────────────────────────────────────────────────────

    /** Emits all non-deleted decks ordered by most recently updated. */
    fun observeAllDecks(): Flow<List<Deck>>

    /** Emits deck summaries (with card count and color identity) for the list view. */
    fun observeAllDeckSummaries(): Flow<List<DeckSummary>>

    /** Emits decks that contain a specific Scryfall card. */
    fun observeDecksContainingCard(scryfallId: String): Flow<List<Deck>>

    /** Emits a single deck with all its card slots (mainboard + sideboard). */
    fun observeDeckWithCards(deckId: String): Flow<DeckWithCards?>

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Creates a new deck locally. Generates a UUID client-side and sets
     * [Deck.updatedAt] so the next sync push uploads it automatically.
     *
     * @return The UUID of the newly created deck.
     */
    suspend fun createDeck(name: String, description: String, format: String): String

    /**
     * Updates deck metadata. Bumps [Deck.updatedAt] so the sync engine picks up the change.
     */
    suspend fun updateDeck(deck: Deck)

    /**
     * Soft-deletes the deck. Sets `isDeleted = true` and bumps `updatedAt`.
     * Does NOT physically remove the row so the deletion is propagated on the next sync.
     */
    suspend fun deleteDeck(deckId: String)

    /**
     * Adds or updates a card slot in the deck's mainboard or sideboard.
     *
     * @param source Deck Engine Unification plan (D4): per-card provenance, defaults to
     *        [DeckCardSource.USER] so every existing caller keeps compiling unchanged. Callers that
     *        know the card's origin (the wizard build, a Suggestions-tab accept) pass it explicitly.
     */
    suspend fun addCardToDeck(
        deckId: String,
        scryfallId: String,
        quantity: Int = 1,
        isSideboard: Boolean = false,
        source: DeckCardSource = DeckCardSource.USER,
    )

    /**
     * Merges scanner additions without replacing unrelated deck slots.
     *
     * Android overrides this with one Room transaction. The portable fallback keeps web
     * implementations source-compatible until they provide an equivalent transactional store.
     */
    suspend fun mergeScannerCards(
        deckId: String,
        additions: List<DeckCardAddition>,
    ): DeckCardAdditionResult {
        val deck = observeDeckWithCards(deckId).first() ?: error("Deck not found")
        val commanderId = deck.deck.commanderCardId
        val blockedIds = additions.asSequence()
            .filter { !it.isSideboard && it.scryfallId == commanderId }
            .mapTo(linkedSetOf()) { it.entryId }
        val permitted = additions.filterNot { it.entryId in blockedIds }
        if (permitted.isNotEmpty()) {
            val slots = LinkedHashMap<Pair<String, Boolean>, CardSlotWrite>()
            deck.mainboard.forEach { slot ->
                slots[slot.scryfallId to false] = CardSlotWrite(
                    scryfallId = slot.scryfallId,
                    quantity = slot.quantity,
                    isSideboard = false,
                    source = slot.source,
                )
            }
            deck.sideboard.forEach { slot ->
                slots[slot.scryfallId to true] = CardSlotWrite(
                    scryfallId = slot.scryfallId,
                    quantity = slot.quantity,
                    isSideboard = true,
                    source = slot.source,
                )
            }
            permitted.groupBy { it.scryfallId to it.isSideboard }.forEach { (key, grouped) ->
                val current = slots[key]
                val added = grouped.sumOf { it.quantity.toLong() }
                val quantity = (current?.quantity ?: 0).toLong() + added
                check(quantity <= Int.MAX_VALUE) { "merged quantity exceeds Int range" }
                slots[key] = CardSlotWrite(
                    scryfallId = key.first,
                    quantity = quantity.toInt(),
                    isSideboard = key.second,
                    source = current?.source ?: DeckCardSource.USER,
                )
            }
            replaceAllCardsWithSource(deckId, slots.values.toList())
        }
        val committedCopies = permitted.sumOf { it.quantity.toLong() }
        check(committedCopies <= Int.MAX_VALUE) { "committed copies exceed Int range" }
        return DeckCardAdditionResult(
            committedEntryIds = permitted.mapTo(linkedSetOf()) { it.entryId },
            blockedCommanderEntryIds = blockedIds,
            committedCopies = committedCopies.toInt(),
        )
    }

    /** Removes a card slot from the deck. */
    suspend fun removeCardFromDeck(deckId: String, scryfallId: String, isSideboard: Boolean)

    /**
     * Atomically moves [quantity] copies of a card between mainboard and sideboard.
     *
     * Both the source-board decrement (or removal) and the target-board increment happen
     * inside a single Room transaction, so the deck observer never re-emits an inconsistent
     * intermediate state (copies briefly missing from both boards). Returns silently when
     * the source board holds no copies of the card.
     *
     * @param fromSideboard `true` to move sideboard → mainboard, `false` for mainboard → sideboard.
     */
    suspend fun moveCardQuantity(deckId: String, scryfallId: String, fromSideboard: Boolean, quantity: Int = 1)

    /** Removes all card slots from the deck (does NOT delete the deck itself). */
    suspend fun clearDeck(deckId: String)

    /**
     * Stamps a deck with external community-source attribution (Community Decks).
     *
     * Used after importing a deck from a community service (e.g. Archidekt) to record
     * the original URL, author, service name, and import timestamp. Bumps [Deck.updatedAt]
     * so the change is picked up by the next sync push.
     */
    suspend fun updateDeckAttribution(
        deckId: String,
        sourceUrl: String?,
        sourceAuthor: String?,
        sourceService: String?,
        importedAt: Long?,
    )

    /**
     * Atomically replaces the entire card list for a deck.
     * Used by [saveDeck] in the ViewModel to flush the in-memory draft to Room in one transaction.
     * @param slots List of (scryfallId, quantity, isSideboard) triples.
     */
    suspend fun replaceAllCards(deckId: String, slots: List<Triple<String, Int, Boolean>>)

    /**
     * Deck Wizard Commander v3 plan (Phase 2.6, D12/D13): an ADDITIVE overload of [replaceAllCards]
     * that also carries per-slot [DeckCardSource] provenance (engine-placed + commander = WIZARD,
     * manual adds = USER) -- [replaceAllCards] itself has no source parameter and always writes
     * [DeckCardSource.USER], so a straight delegation would silently lose provenance.
     *
     * DEFAULT implementation (best-effort, not a single Room transaction): [clearDeck] then
     * [addCardToDeck] once per slot with its own [DeckCardSource]. This keeps the interface
     * additive with ZERO changes required to any existing implementer (Android's Room-backed
     * `DeckRepositoryImpl`, `WebDeckRepository`) — both automatically get a working implementation
     * built from primitives they already have. A genuinely single-transaction Room override (the
     * literal "ONE atomic write" the plan's D12 asks for) is deferred to the phase that wires this
     * into the wizard VM (Phase 6): that is also when a cancelled build's "leave the draft
     * untouched" guarantee actually matters end-to-end, and hardening it then avoids touching
     * `androidMain`'s DAO twice. Callers on the Commander build path today (Phase 2's
     * `BuildWizardDeckUseCase`) are not yet wired into any real wizard flow (Phase 3-6), so this
     * default is exercised only by this phase's own tests until then.
     *
     * @param slots (scryfallId, quantity, isSideboard, source) — the sideboard flag exists for API
     *        symmetry with [replaceAllCards]; the Commander build path always writes `false`
     *        (Commander has no sideboard slot in this campaign's scope).
     */
    suspend fun replaceAllCardsWithSource(deckId: String, slots: List<CardSlotWrite>) {
        clearDeck(deckId)
        slots.forEach { slot ->
            addCardToDeck(deckId, slot.scryfallId, slot.quantity, slot.isSideboard, slot.source)
        }
    }

    /**
     * Pins (or clears) the deck's archetype/theme override (Deck Doctor Phase 1.5, D2).
     *
     * @param archetypeOverride a raw `ArchetypeId.name` string, or null to clear the macro pin
     *        (the engine goes back to inferring it every analysis).
     * @param themesOverride raw `ThemeId.name` strings (at most 2); empty clears the theme pin.
     * @param posture Deck Wizard Commander v3 plan (E3, D5) -- a raw `PostureId.name` string, or
     *        null to clear the posture pin. Appended LAST and defaulted so every existing 3-arg
     *        call site keeps compiling unchanged; `null` ALSO clears any previously-set posture on
     *        this write (same "null clears" convention as [archetypeOverride]/[themesOverride]
     *        and the sibling `tribe` param on [com.mmg.manahub.feature.decks.domain.orchestrator
     *        .DeckDoctorOrchestrator.setArchetypeOverride] -- correct for a non-postured strategy
     *        pick or "Auto-detect", same as that precedent documents for tribe).
     */
    suspend fun updateArchetypeOverride(
        deckId: String,
        archetypeOverride: String?,
        themesOverride: List<String>,
        posture: String? = null,
    )

    /**
     * Pins (or, when null, clears) the deck's tribe override (Deck Engine Unification plan, D2/D4)
     * -- a raw `tribe:<subtype>` key. Kept as its OWN write path, separate from
     * [updateArchetypeOverride], so a manual archetype/theme edit (which has no tribe UI) never
     * silently clobbers a wizard-set tribe pin by omission. Bumps [Deck.updatedAt].
     */
    suspend fun updateTribeOverride(deckId: String, tribeOverride: String?)

    /**
     * Sets (or clears) the deck's [Deck.strategyLocked] flag (D4 hard no-cut guarantee). Bumps
     * [Deck.updatedAt].
     */
    suspend fun updateStrategyLocked(deckId: String, locked: Boolean)

    /**
     * Deck Wizard Commander v3 plan (Phase 8, JOB 2): the ONE atomic write for a wizard build's
     * whole persist step -- card replacement + archetype/theme/posture pin + tribe pin + the
     * strategy-locked flag. Before this, [com.mmg.manahub.feature.decks.domain.template
     * .BuildWizardDeckUseCase.persist] issued 4 SEPARATE suspend calls
     * ([replaceAllCardsWithSource] + [updateArchetypeOverride] + [updateTribeOverride] +
     * [updateStrategyLocked]); a cancellation or failure between any two of them could leave a
     * deck with NEW cards but a STALE pin (mitigated, not fixed, by a cancel-blocking guard in
     * `DeckWizardViewModel` -- see that guard's own KDoc for why it stays as defense-in-depth).
     *
     * Deck Wizard 60-card wave (v6, plan §5 Phase 1.3): renamed from `persistCommanderBuild` --
     * pure rename, every format now writes through this same entry point.
     *
     * DEFAULT implementation (best-effort, NOT one transaction): the same 4 calls this replaces,
     * in the same order -- kept additive so `WebDeckRepository` compiles unchanged until it gets
     * a real transactional web store. Android's `DeckRepositoryImpl` overrides this with a genuine
     * single Room `@Transaction` ([com.mmg.manahub.core.data.local.dao.DeckDao.persistWizardBuild]).
     */
    suspend fun persistWizardBuild(
        deckId: String,
        slots: List<CardSlotWrite>,
        archetypeOverride: String?,
        themesOverride: List<String>,
        posture: String?,
        tribeOverride: String?,
        strategyLocked: Boolean,
    ) {
        replaceAllCardsWithSource(deckId, slots)
        updateArchetypeOverride(deckId, archetypeOverride, themesOverride, posture)
        updateTribeOverride(deckId, tribeOverride)
        updateStrategyLocked(deckId, strategyLocked)
    }
}
