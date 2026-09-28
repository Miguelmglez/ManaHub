package com.mmg.manahub.core.data.repository
// COMMENTS_REVIEWED: 2026-09-08

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.DeckSummaryRow
import com.mmg.manahub.core.data.local.dao.ScannerDeckCardAddition
import com.mmg.manahub.core.data.local.entity.DeckCardEntity
import com.mmg.manahub.core.data.local.entity.DeckEntity
import com.mmg.manahub.core.data.local.mapper.toDomainDeck
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckCreationSource
import com.mmg.manahub.core.model.DeckSlot
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.model.DeckWithCards
import com.mmg.manahub.core.domain.repository.CardSlotWrite
import com.mmg.manahub.core.domain.repository.DeckCardAddition
import com.mmg.manahub.core.domain.repository.DeckCardAdditionResult
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Local-first implementation of [DeckRepository].
 *
 * All mutations write to Room first and bump [DeckEntity.updatedAt].
 * The [com.mmg.manahub.core.sync.SyncManager] detects dirty rows via [updatedAt]
 * and pushes them to Supabase on the next sync cycle.
 *
 * Soft-deletes set [DeckEntity.isDeleted] = true rather than removing the row,
 * so deletions are propagated to Supabase on the next push.
 *
 * RPCs have been removed entirely from this class — they are SyncManager's responsibility.
 *
 * KMP migration — Hilt→Koin cutover batch 3. Plain class (no `@Inject`/`@Singleton`); built as a
 * native Koin `single` in [com.mmg.manahub.app.di.coreBridgeKoinModule] (shared across many islands).
 */
class DeckRepositoryImpl(
    private val deckDao: DeckDao,
    private val progressionEventBus: ProgressionEventBus,
    private val ioDispatcher: CoroutineDispatcher,
) : DeckRepository {

    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type

    // Decks created this process whose DeckCreated waits for the first mainboard card; lost on process death by design.
    private val pendingCreations = ConcurrentHashMap<String, DeckCreationSource>()

    // ── Observables ───────────────────────────────────────────────────────────

    override fun observeAllDecks(): Flow<List<Deck>> =
        deckDao.observeAllDecks(null).map { entities ->
            entities.filter { !it.isDeleted }.map { it.toDomainDeck() }
        }

    override fun observeAllDeckSummaries(): Flow<List<DeckSummary>> =
        deckDao.observeDeckSummaryRows().map { rows ->
            rows.groupBy { it.deckId }
                .values
                .sortedByDescending { it.first().updatedAt }
                .map { buildDeckSummary(it) }
        }

    override fun observeDecksContainingCard(scryfallId: String): Flow<List<Deck>> =
        deckDao.observeDecksContainingCard(scryfallId).map { entities ->
            entities.filter { !it.isDeleted }.map { it.toDomainDeck() }
        }

    override fun observeDeckWithCards(deckId: String): Flow<DeckWithCards?> =
        deckDao.observeDeckWithCards(deckId).map { entity ->
            entity?.let {
                DeckWithCards(
                    deck = it.deck.toDomainDeck(),
                    mainboard = it.cards.filter { c -> !c.isSideboard }
                        .map { c -> DeckSlot(c.scryfallId, c.quantity, DeckCardSource.fromRaw(c.source)) },
                    sideboard = it.cards.filter { c -> c.isSideboard }
                        .map { c -> DeckSlot(c.scryfallId, c.quantity, DeckCardSource.fromRaw(c.source)) },
                )
            }
        }

    // ── Mutations ─────────────────────────────────────────────────────────────

    override suspend fun createDeck(
        name: String,
        description: String,
        format: String,
    ): String = createDeck(name, description, format, DeckCreationSource.BUILT)

    override suspend fun createDeck(
        name: String,
        description: String,
        format: String,
        source: DeckCreationSource,
    ): String = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        deckDao.upsertDeck(
            DeckEntity(
                id = id,
                userId = null,          // SyncManager will assign userId on login
                name = name,
                description = description,
                format = format,
                coverCardId = null,
                commanderCardId = null,
                isDeleted = false,
                updatedAt = now,
                createdAt = now,
            )
        )
        pendingCreations[id] = source
        id
    }

    override suspend fun tagDeckCreationSource(deckId: String, source: DeckCreationSource) {
        pendingCreations.computeIfPresent(deckId) { _, _ -> source }
    }

    /**
     * Emits the one-time [ProgressionEvent.DeckCreated] once a deck created this process holds a
     * mainboard card (restore plan D8): an empty draft never earns anything, and the ledger key
     * `deck_created:{id}` dedupes any replay. Call after the card write committed.
     */
    private suspend fun emitDeckCreatedOnFirstMainboardCard(deckId: String) {
        if (!pendingCreations.containsKey(deckId)) return
        if (deckDao.getDeckCards(deckId).none { !it.isSideboard && it.quantity > 0 }) return
        val deck = deckDao.getDeckById(deckId) ?: return
        // remove() is the claim: concurrent writers race here and exactly one emits.
        val source = pendingCreations.remove(deckId) ?: return
        progressionEventBus.emit(
            ProgressionEvent.DeckCreated(
                deckId = deckId,
                format = deck.format,
                source = source,
                occurredAt = Clock.System.now(),
            )
        )
    }

    override suspend fun updateDeck(deck: Deck) = withContext(ioDispatcher) {
        val existing = deckDao.getDeckByIdForSync(deck.id) ?: return@withContext
        deckDao.upsertDeck(existing.copy(
            name = deck.name,
            description = deck.description,
            format = deck.format,
            coverCardId = deck.coverCardId,
            commanderCardId = deck.commanderCardId,
            isDeleted = false,
            updatedAt = System.currentTimeMillis(),
        ))
        // DeckSaved grants no XP in v1 (XpGranter maps it to null); the event is retained
        // for Phase 1/2 quest progress. updateDeck only touches metadata, so the precise
        // card count is not available here without a DAO read — pass 0 (informational only).
        progressionEventBus.emit(
            ProgressionEvent.DeckSaved(
                deckId = deck.id,
                cardCount = 0,
                occurredAt = Clock.System.now(),
            )
        )
    }

    override suspend fun deleteDeck(deckId: String) = withContext(ioDispatcher) {
        pendingCreations.remove(deckId)
        // Soft delete — the row stays so SyncManager can push the deletion to Supabase.
        deckDao.softDeleteDeck(deckId, System.currentTimeMillis())
    }

    override suspend fun addCardToDeck(
        deckId: String,
        scryfallId: String,
        quantity: Int,
        isSideboard: Boolean,
        source: DeckCardSource,
    ) {
        withContext(ioDispatcher) {
            deckDao.upsertDeckCard(
                DeckCardEntity(
                    deckId = deckId,
                    scryfallId = scryfallId,
                    quantity = quantity,
                    isSideboard = isSideboard,
                    source = source.name,
                )
            )
            deckDao.getDeckById(deckId)?.let { deck ->
                deckDao.upsertDeck(deck.copy(updatedAt = System.currentTimeMillis()))
            }
            if (!isSideboard) emitDeckCreatedOnFirstMainboardCard(deckId)
        }
    }

    override suspend fun mergeScannerCards(
        deckId: String,
        additions: List<DeckCardAddition>,
    ): DeckCardAdditionResult = withContext(ioDispatcher) {
        val result = deckDao.mergeScannerCards(
            deckId = deckId,
            additions = additions.map { addition ->
                ScannerDeckCardAddition(
                    entryId = addition.entryId,
                    scryfallId = addition.scryfallId,
                    oracleId = addition.oracleId,
                    quantity = addition.quantity,
                    isSideboard = addition.isSideboard,
                )
            },
        )
        if (result.committedCopies > 0) emitDeckCreatedOnFirstMainboardCard(deckId)
        DeckCardAdditionResult(
            committedEntryIds = result.committedEntryIds,
            blockedCommanderEntryIds = result.blockedCommanderEntryIds,
            committedCopies = result.committedCopies,
        )
    }

    override suspend fun removeCardFromDeck(
        deckId: String,
        scryfallId: String,
        isSideboard: Boolean,
    ) {
        withContext(ioDispatcher) {
            deckDao.removeDeckCard(deckId, scryfallId, isSideboard)
            deckDao.getDeckById(deckId)?.let { deck ->
                deckDao.upsertDeck(deck.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    override suspend fun moveCardQuantity(
        deckId: String,
        scryfallId: String,
        fromSideboard: Boolean,
        quantity: Int,
    ) {
        withContext(ioDispatcher) {
            // Read the current per-board counts so the atomic DAO move only needs the
            // pre-computed target quantities. Both boards are derived from the same
            // single DAO read to avoid a torn view.
            val rows = deckDao.getDeckCards(deckId)
            val sourceRow = rows.firstOrNull { it.scryfallId == scryfallId && it.isSideboard == fromSideboard }
            val sourceQty = sourceRow?.quantity ?: 0
            if (sourceQty <= 0) return@withContext
            val toMove = quantity.coerceIn(1, sourceQty)
            val targetRow = rows.firstOrNull { it.scryfallId == scryfallId && it.isSideboard == !fromSideboard }
            val targetQty = targetRow?.quantity ?: 0

            // Edge-case audit Fix 3 (2026-07-28): the ORIGIN row's remaining stack keeps its OWN
            // provenance unchanged (it's a shrink, not a merge). The TARGET row is a potential MERGE
            // -- when it already holds a stack of the same card with a DIFFERENT (more-protected)
            // source, the merged row must resolve to the MORE-protected of the two, never whichever
            // side happened to initiate the move (a USER sideboard copy moved onto a WIZARD
            // mainboard stack must leave the merged stack WIZARD, preserving the D4 hard no-cut
            // guarantee instead of silently stripping it).
            val originSource = DeckCardSource.fromRaw(sourceRow?.source)
            val targetSource = targetRow?.let { DeckCardSource.fromRaw(it.source) }?.moreProtected(originSource) ?: originSource

            deckDao.moveCardQuantity(
                deckId = deckId,
                scryfallId = scryfallId,
                fromSideboard = fromSideboard,
                newSourceQty = sourceQty - toMove,
                newTargetQty = targetQty + toMove,
                sourceRowSource = originSource.name,
                targetRowSource = targetSource.name,
            )
            deckDao.getDeckById(deckId)?.let { deck ->
                deckDao.upsertDeck(deck.copy(updatedAt = System.currentTimeMillis()))
            }
            if (fromSideboard) emitDeckCreatedOnFirstMainboardCard(deckId)
        }
    }

    override suspend fun clearDeck(deckId: String) {
        withContext(ioDispatcher) {
            deckDao.clearDeckCards(deckId)
            deckDao.getDeckById(deckId)?.let { deck ->
                deckDao.upsertDeck(deck.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    override suspend fun updateDeckAttribution(
        deckId: String,
        sourceUrl: String?,
        sourceAuthor: String?,
        sourceService: String?,
        importedAt: Long?,
    ) {
        withContext(ioDispatcher) {
            deckDao.updateDeckAttribution(
                deckId = deckId,
                sourceUrl = sourceUrl,
                sourceAuthor = sourceAuthor,
                sourceService = sourceService,
                importedAt = importedAt,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun updateArchetypeOverride(
        deckId: String,
        archetypeOverride: String?,
        themesOverride: List<String>,
        posture: String?,
    ) {
        withContext(ioDispatcher) {
            deckDao.updateArchetypeOverride(
                deckId = deckId,
                archetypeOverride = archetypeOverride,
                themesOverrideJson = if (themesOverride.isEmpty()) null else gson.toJson(themesOverride),
                postureOverride = posture,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun updateTribeOverride(deckId: String, tribeOverride: String?) {
        withContext(ioDispatcher) {
            deckDao.updateTribeOverride(
                deckId = deckId,
                tribeOverride = tribeOverride,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun updateStrategyPin(
        deckId: String,
        archetypeOverride: String?,
        themesOverride: List<String>,
        posture: String?,
        tribeOverride: String?,
    ) {
        withContext(ioDispatcher) {
            deckDao.updateStrategyPin(
                deckId = deckId,
                archetypeOverride = archetypeOverride,
                themesOverrideJson = if (themesOverride.isEmpty()) null else gson.toJson(themesOverride),
                postureOverride = posture,
                tribeOverride = tribeOverride,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun updateStrategyLocked(deckId: String, locked: Boolean) {
        withContext(ioDispatcher) {
            deckDao.updateStrategyLocked(
                deckId = deckId,
                locked = locked,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    /**
     * Deck Wizard Commander v3 plan (Phase 6, D12): overrides the commonMain default (non-atomic
     * clearDeck + addCardToDeck loop) with a genuine single-transaction Room write via
     * [DeckDao.replaceAllCardsWithSource] -- a cancelled or failed build leaves the draft exactly as
     * it was, never empty-then-partial.
     */
    override suspend fun replaceAllCardsWithSource(deckId: String, slots: List<CardSlotWrite>) {
        withContext(ioDispatcher) {
            val entities = slots.map { slot ->
                DeckCardEntity(
                    deckId = deckId,
                    scryfallId = slot.scryfallId,
                    quantity = slot.quantity,
                    isSideboard = slot.isSideboard,
                    source = slot.source.name,
                )
            }
            deckDao.replaceAllCardsWithSource(deckId, entities)
            emitDeckCreatedOnFirstMainboardCard(deckId)
        }
    }

    /**
     * Deck Wizard Commander v3 plan (Phase 8, JOB 2): overrides the commonMain default (4 separate
     * suspend calls) with a genuine single-transaction Room write via [DeckDao.persistWizardBuild]
     * -- see that method's KDoc for the data-corruption gap this closes. Deck Wizard 60-card wave
     * (v6, plan §5 Phase 1.3): renamed from `persistCommanderBuild` -- pure rename.
     */
    override suspend fun persistWizardBuild(
        deckId: String,
        slots: List<CardSlotWrite>,
        archetypeOverride: String?,
        themesOverride: List<String>,
        posture: String?,
        tribeOverride: String?,
        strategyLocked: Boolean,
        commanderCardId: String?,
    ) {
        withContext(ioDispatcher) {
            val entities = slots.map { slot ->
                DeckCardEntity(
                    deckId = deckId,
                    scryfallId = slot.scryfallId,
                    quantity = slot.quantity,
                    isSideboard = slot.isSideboard,
                    source = slot.source.name,
                )
            }
            deckDao.persistWizardBuild(
                deckId = deckId,
                cards = entities,
                archetypeOverride = archetypeOverride,
                themesOverrideJson = if (themesOverride.isEmpty()) null else gson.toJson(themesOverride),
                postureOverride = posture,
                tribeOverride = tribeOverride,
                strategyLocked = strategyLocked,
                commanderCardId = commanderCardId,
                updatedAt = System.currentTimeMillis(),
            )
            emitDeckCreatedOnFirstMainboardCard(deckId)
        }
    }

    override suspend fun replaceAllCards(deckId: String, slots: List<Triple<String, Int, Boolean>>) {
        withContext(ioDispatcher) {
            val entities = slots.map { (scryfallId, quantity, isSideboard) ->
                DeckCardEntity(deckId = deckId, scryfallId = scryfallId, quantity = quantity, isSideboard = isSideboard)
            }
            deckDao.replaceAllCards(deckId, entities)
            deckDao.getDeckById(deckId)?.let { deck ->
                deckDao.upsertDeck(deck.copy(updatedAt = System.currentTimeMillis()))
            }
            emitDeckCreatedOnFirstMainboardCard(deckId)
        }
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun buildDeckSummary(deckRows: List<DeckSummaryRow>): DeckSummary {
        val first = deckRows.first()
        val mainboard = deckRows.filter { it.scryfallId != null && !it.isSideboard }
        val cardCount = mainboard.sumOf { it.quantity }
        val colorIdentity = deckRows
            .mapNotNull { it.colorIdentity }
            .flatMap { jsonStr ->
                runCatching { gson.fromJson<List<String>>(jsonStr, listType) ?: emptyList() }
                    .getOrDefault(emptyList())
            }
            .toSet()
        val coverImageUrl = first.coverCardId
            ?.let { coverId -> deckRows.firstOrNull { it.scryfallId == coverId }?.imageArtCrop }
            ?: mainboard.firstOrNull()?.imageArtCrop

        return DeckSummary(
            id = first.deckId,
            name = first.name,
            description = first.description,
            format = first.format,
            coverCardId = first.coverCardId,
            createdAt = first.createdAt,
            updatedAt = first.updatedAt,
            cardCount = cardCount,
            colorIdentity = colorIdentity,
            coverImageUrl = coverImageUrl,
        )
    }
}
