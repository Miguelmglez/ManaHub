package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.remote.decks.DeckCardSyncDto
import com.mmg.manahub.core.data.remote.decks.DeckRemoteDataSource
import com.mmg.manahub.core.data.remote.decks.DeckSyncDto
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckSlot
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.model.DeckWithCards
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Web [DeckRepository] implementation (KMP web roadmap W3c) — the third web data-layer slice,
 * and the first one that is genuinely remote-first with NO local staging at all.
 *
 * ## Why this repository looks different from [WebCardRepository]/`WebUserPreferencesRepository`
 * [DeckRepository]'s own KDoc documents it as **local-CRUD-only by design on Android**: Room is
 * the source of truth there, and a separate `com.mmg.manahub.core.sync.SyncManager` periodically
 * pushes/pulls decks to/from Supabase. The web target has no Room and no separate sync engine
 * (master plan §2.1: web is online-first, no local database) — so every mutation here talks to
 * Supabase via [remote] ([DeckRemoteDataSource], the SAME `commonMain` class the Android
 * `SyncManager` uses since W3c moved it out of `:app`) DIRECTLY and IMMEDIATELY. There is no
 * staging/dirty-row/watermark concept on web; a `createDeck`/`updateDeck`/`addCardToDeck` call
 * either lands in Supabase or the suspend function throws.
 *
 * ## Reactivity pattern
 * [DeckRemoteDataSource] has no native `Flow` support (it's a plain RPC-calling contract), so every
 * exposed [Flow] here is backed by an in-memory [MutableStateFlow] cache — the same general shape
 * [WebUserPreferencesRepository]/[WebCardRepository] already established (see
 * `project_kmp_spike_findings.md`'s W3a addendum), with ONE documented divergence: this cache's
 * initial hydration is NOT synchronous. Unlike `LocalStorageKeyValueStore` (zero real suspension
 * points, so `Dispatchers.Unconfined` finishes hydration inline with construction),
 * [DeckRemoteDataSource.getDeckChangesSince] is a genuine network round-trip through Ktor/Postgrest
 * — hydration is eventually-consistent (empty/stale cache first, a real emission once the fetch
 * resolves), exactly the degraded-gracefully case that addendum called out in advance.
 *
 * Hydration is driven by [SupabaseClient.auth]'s `sessionStatus`, not a one-shot `init` fetch: a
 * fresh page load's session restore-from-`localStorage` is ALSO async (supabase-kt's own default
 * behavior), so a one-shot fetch at construction time could race ahead of auth and silently see
 * no session. Re-fetching every time `sessionStatus` resolves to [SessionStatus.Authenticated]
 * covers both the "already had a session, restoring from storage" case and the "user just tapped
 * guest sign-in" case with the same code path.
 *
 * ## Known Web v1 gaps (documented, not bugs)
 * - [DeckSummary.colorIdentity] and [DeckSummary.coverImageUrl] are always empty/`null` on web.
 *   Android derives both by joining `deck_cards` against cached `Card` rows in Room; web has no
 *   local card cache to join against, and pulling every mainboard card through
 *   `CardRepository`/`ScryfallRemoteDataSource` just to build a deck-list summary is out of scope
 *   for this slice (revisit once a web Deck Studio needs it).
 * - [updateDeckAttribution]/[updateArchetypeOverride]/[updateTribeOverride] throw a loud, documented
 *   [UnsupportedOperationException] — same "real vs. stub" split [WebCardRepository] established.
 *   These are NOT stubbed because of a web limitation: [DeckSyncDto] itself has no
 *   `archetype_override`/`themes_override`/`tribe_override`/attribution columns — Android's own
 *   `SyncManager` never pushes these fields to Supabase either (see the DTO's KDoc: "NOT synced,
 *   local-only convention"). There is no remote column for this repository to write to on ANY
 *   platform, and no web consumer (Community Deck import, Deck Doctor archetype pinning) exists
 *   yet either. [updateStrategyLocked] is the one exception in this group — `strategy_locked` IS a
 *   real synced column, so it has a full implementation.
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class WebDeckRepository(
    private val remote: DeckRemoteDataSource,
    private val supabaseClient: SupabaseClient,
) : DeckRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** All non-deleted decks, keyed by id. Never persisted — session-scoped, like [WebCardRepository]. */
    private val decksCache = MutableStateFlow<Map<String, Deck>>(emptyMap())

    /** Card slots per deck, keyed by deck id. Populated lazily per-deck, eagerly on a full refresh. */
    private val cardsCache = MutableStateFlow<Map<String, List<DeckCardSyncDto>>>(emptyMap())

    init {
        repositoryScope.launch {
            supabaseClient.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) refreshAllDecks()
            }
        }
    }

    // ── Observables ───────────────────────────────────────────────────────────

    override fun observeAllDecks(): Flow<List<Deck>> =
        decksCache.map { it.values.sortedByDescending(Deck::updatedAt) }

    override fun observeAllDeckSummaries(): Flow<List<DeckSummary>> =
        combine(decksCache, cardsCache) { decks, cardsByDeck ->
            decks.values
                .sortedByDescending(Deck::updatedAt)
                .map { deck -> buildDeckSummary(deck, cardsByDeck[deck.id].orEmpty()) }
        }

    override fun observeDecksContainingCard(scryfallId: String): Flow<List<Deck>> =
        combine(decksCache, cardsCache) { decks, cardsByDeck ->
            decks.values
                .filter { deck -> cardsByDeck[deck.id]?.any { it.scryfallId == scryfallId } == true }
                .sortedByDescending(Deck::updatedAt)
        }

    override fun observeDeckWithCards(deckId: String): Flow<DeckWithCards?> {
        ensureDeckCardsLoaded(deckId)
        return combine(decksCache, cardsCache) { decks, cardsByDeck ->
            val deck = decks[deckId] ?: return@combine null
            val cards = cardsByDeck[deckId].orEmpty()
            DeckWithCards(
                deck = deck,
                mainboard = cards.filterNot { it.isSideboard }.map { it.toSlot() },
                sideboard = cards.filter { it.isSideboard }.map { it.toSlot() },
            )
        }
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    override suspend fun createDeck(name: String, description: String, format: String): String {
        val uid = requireUserId()
        val now = nowMillis()
        val id = Uuid.random().toString()
        val dto = DeckSyncDto(
            id = id,
            userId = uid,
            name = name,
            description = description,
            format = format,
            coverCardId = null,
            commanderCardId = null,
            isDeleted = false,
            updatedAt = now,
            createdAt = now,
            strategyLocked = false,
        )
        remote.batchUpsertDecks(listOf(dto)).getOrThrow()
        decksCache.update { it + (id to dto.toDeck()) }
        cardsCache.update { it + (id to emptyList()) }
        return id
    }

    override suspend fun updateDeck(deck: Deck) {
        val base = decksCache.value[deck.id] ?: deck
        val uid = base.userId ?: requireUserId()
        val updated = base.copy(
            name = deck.name,
            description = deck.description,
            format = deck.format,
            coverCardId = deck.coverCardId,
            commanderCardId = deck.commanderCardId,
            userId = uid,
            isDeleted = false,
            updatedAt = nowMillis(),
        )
        remote.batchUpsertDecks(listOf(updated.toSyncDto(uid))).getOrThrow()
        decksCache.update { it + (deck.id to updated) }
    }

    override suspend fun deleteDeck(deckId: String) {
        val existing = decksCache.value[deckId] ?: return
        val uid = existing.userId ?: requireUserId()
        val updated = existing.copy(isDeleted = true, updatedAt = nowMillis())
        // Soft-delete on the server too (mirrors Android's tombstone convention exactly, even
        // though web has no second device to reconcile with yet) rather than physically removing
        // the row -- keeps behavior identical if/when a second web session or Android device pulls
        // the same account's decks.
        remote.batchUpsertDecks(listOf(updated.toSyncDto(uid))).getOrThrow()
        decksCache.update { it - deckId }
        cardsCache.update { it - deckId }
    }

    override suspend fun addCardToDeck(
        deckId: String,
        scryfallId: String,
        quantity: Int,
        isSideboard: Boolean,
        source: DeckCardSource,
    ) {
        val cards = currentCards(deckId).toMutableList()
        val idx = cards.indexOfFirst { it.scryfallId == scryfallId && it.isSideboard == isSideboard }
        if (idx >= 0) {
            cards[idx] = cards[idx].copy(quantity = quantity, source = source.name)
        } else {
            cards.add(DeckCardSyncDto(scryfallId, quantity, isSideboard, source.name))
        }
        writeCards(deckId, cards)
    }

    override suspend fun removeCardFromDeck(deckId: String, scryfallId: String, isSideboard: Boolean) {
        val cards = currentCards(deckId).filterNot { it.scryfallId == scryfallId && it.isSideboard == isSideboard }
        writeCards(deckId, cards)
    }

    override suspend fun moveCardQuantity(
        deckId: String,
        scryfallId: String,
        fromSideboard: Boolean,
        quantity: Int,
    ) {
        val cards = currentCards(deckId).toMutableList()
        val sourceIdx = cards.indexOfFirst { it.scryfallId == scryfallId && it.isSideboard == fromSideboard }
        if (sourceIdx < 0) return
        val sourceRow = cards[sourceIdx]
        if (sourceRow.quantity <= 0) return
        val toMove = quantity.coerceIn(1, sourceRow.quantity)
        val remaining = sourceRow.quantity - toMove
        val originSource = DeckCardSource.fromRaw(sourceRow.source)

        val targetIdx = cards.indexOfFirst { it.scryfallId == scryfallId && it.isSideboard == !fromSideboard }
        if (targetIdx >= 0) {
            val targetRow = cards[targetIdx]
            // Edge-case audit Fix 3 (mirrors DeckRepositoryImpl.moveCardQuantity on Android): a
            // board-move MERGE can only ever gain cut-protection, never lose it.
            val mergedSource = DeckCardSource.fromRaw(targetRow.source).moreProtected(originSource)
            cards[targetIdx] = targetRow.copy(quantity = targetRow.quantity + toMove, source = mergedSource.name)
        } else {
            cards.add(DeckCardSyncDto(scryfallId, toMove, !fromSideboard, originSource.name))
        }

        if (remaining > 0) {
            cards[sourceIdx] = sourceRow.copy(quantity = remaining)
        } else {
            cards.removeAt(sourceIdx)
        }
        writeCards(deckId, cards)
    }

    override suspend fun clearDeck(deckId: String) {
        writeCards(deckId, emptyList())
    }

    override suspend fun replaceAllCards(deckId: String, slots: List<Triple<String, Int, Boolean>>) {
        val cards = slots.map { (scryfallId, quantity, isSideboard) ->
            DeckCardSyncDto(scryfallId = scryfallId, quantity = quantity, isSideboard = isSideboard)
        }
        writeCards(deckId, cards)
    }

    override suspend fun updateStrategyLocked(deckId: String, locked: Boolean) {
        val existing = decksCache.value[deckId] ?: return
        val uid = existing.userId ?: requireUserId()
        val updated = existing.copy(strategyLocked = locked, updatedAt = nowMillis())
        remote.batchUpsertDecks(listOf(updated.toSyncDto(uid))).getOrThrow()
        decksCache.update { it + (deckId to updated) }
    }

    // ── Stubbed: no Supabase-synced column on ANY platform, no web consumer today ─────────────
    // See the class KDoc "Known Web v1 gaps" section above.

    override suspend fun updateDeckAttribution(
        deckId: String,
        sourceUrl: String?,
        sourceAuthor: String?,
        sourceService: String?,
        importedAt: Long?,
    ): Unit = notSynced("updateDeckAttribution")

    override suspend fun updateArchetypeOverride(
        deckId: String,
        archetypeOverride: String?,
        themesOverride: List<String>,
    ): Unit = notSynced("updateArchetypeOverride")

    override suspend fun updateTribeOverride(deckId: String, tribeOverride: String?): Unit =
        notSynced("updateTribeOverride")

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Full push+pull of every deck AND its card slots for the current account (0L watermark = full pull). */
    private suspend fun refreshAllDecks() {
        val dtos = remote.getDeckChangesSince(0L).getOrElse { return }
        val liveDeckIds = dtos.filterNot { it.isDeleted }.map { it.id }
        decksCache.value = dtos.filterNot { it.isDeleted }.associate { it.id to it.toDeck() }
        // Eagerly warm every deck's card slots so observeAllDeckSummaries() has an accurate
        // cardCount immediately, not just once each deck happens to be individually opened.
        coroutineScope {
            liveDeckIds.map { deckId ->
                async { deckId to remote.getDeckCardsForDeck(deckId).getOrElse { emptyList() } }
            }.awaitAll().forEach { (deckId, cards) ->
                cardsCache.update { it + (deckId to cards) }
            }
        }
    }

    private suspend fun currentCards(deckId: String): List<DeckCardSyncDto> {
        cardsCache.value[deckId]?.let { return it }
        val fetched = remote.getDeckCardsForDeck(deckId).getOrElse { emptyList() }
        cardsCache.update { it + (deckId to fetched) }
        return fetched
    }

    private fun ensureDeckCardsLoaded(deckId: String) {
        if (cardsCache.value.containsKey(deckId)) return
        repositoryScope.launch { currentCards(deckId) }
    }

    private suspend fun writeCards(deckId: String, cards: List<DeckCardSyncDto>) {
        remote.upsertDeckCards(deckId, cards).getOrThrow()
        cardsCache.update { it + (deckId to cards) }
        touchDeckUpdatedAt(deckId)
    }

    private suspend fun touchDeckUpdatedAt(deckId: String) {
        val existing = decksCache.value[deckId] ?: return
        val uid = existing.userId ?: requireUserId()
        val updated = existing.copy(updatedAt = nowMillis())
        remote.batchUpsertDecks(listOf(updated.toSyncDto(uid))).getOrThrow()
        decksCache.update { it + (deckId to updated) }
    }

    private fun requireUserId(): String =
        supabaseClient.auth.currentUserOrNull()?.id
            ?: error("WebDeckRepository requires a signed-in session (guest sign-in included) to mutate decks")

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private fun buildDeckSummary(deck: Deck, cards: List<DeckCardSyncDto>): DeckSummary {
        val cardCount = cards.filterNot { it.isSideboard }.sumOf { it.quantity }
        return DeckSummary(
            id = deck.id,
            name = deck.name,
            description = deck.description,
            format = deck.format,
            coverCardId = deck.coverCardId,
            createdAt = deck.createdAt,
            updatedAt = deck.updatedAt,
            cardCount = cardCount,
            // Web v1 gap -- see class KDoc "Known Web v1 gaps".
            colorIdentity = emptySet(),
            coverImageUrl = null,
        )
    }

    /**
     * Throws a loud, self-explanatory [UnsupportedOperationException] for a field with no
     * Supabase-synced column on ANY platform. Returns [Nothing] so it type-checks as the body of
     * any override regardless of declared return type -- same helper shape as
     * [WebCardRepository]'s `roomOnly`.
     */
    private fun notSynced(methodName: String): Nothing = throw UnsupportedOperationException(
        "$methodName has no Supabase-synced column on any platform -- DeckSyncDto never carries " +
            "archetype/theme/tribe overrides or community-deck attribution (see DeckSyncDto.kt's " +
            "own KDoc: local-only convention, unrelated to the web target). No web consumer " +
            "(Community Deck import, Deck Doctor archetype pinning) exists yet either.",
    )

    private fun DeckSyncDto.toDeck(): Deck = Deck(
        id = id,
        userId = userId,
        name = name,
        description = description,
        format = format,
        coverCardId = coverCardId,
        commanderCardId = commanderCardId,
        isDeleted = isDeleted,
        createdAt = createdAt,
        updatedAt = updatedAt,
        strategyLocked = strategyLocked,
    )

    private fun Deck.toSyncDto(uid: String): DeckSyncDto = DeckSyncDto(
        id = id,
        userId = uid,
        name = name,
        description = description,
        format = format,
        coverCardId = coverCardId,
        commanderCardId = commanderCardId,
        isDeleted = isDeleted,
        updatedAt = updatedAt,
        createdAt = createdAt,
        strategyLocked = strategyLocked,
    )

    private fun DeckCardSyncDto.toSlot(): DeckSlot =
        DeckSlot(scryfallId = scryfallId, quantity = quantity, source = DeckCardSource.fromRaw(source))
}
