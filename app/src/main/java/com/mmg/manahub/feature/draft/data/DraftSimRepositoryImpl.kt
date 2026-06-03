package com.mmg.manahub.feature.draft.data

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.request.ImageRequest
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.data.local.dao.DraftSessionDao
import com.mmg.manahub.core.data.local.entity.DraftSessionEntity
import com.mmg.manahub.feature.draft.data.remote.CloudflareContentApi
import com.mmg.manahub.feature.draft.data.remote.dto.BoosterConfigDto
import com.mmg.manahub.feature.draft.domain.model.BoosterCardEntry
import com.mmg.manahub.feature.draft.domain.model.BoosterConfig
import com.mmg.manahub.feature.draft.domain.model.BoosterSheet
import com.mmg.manahub.feature.draft.domain.model.BoosterVariant
import com.mmg.manahub.feature.draft.domain.model.DraftError
import com.mmg.manahub.feature.draft.domain.model.DraftResult
import com.mmg.manahub.feature.draft.domain.model.DraftState
import com.mmg.manahub.feature.draft.domain.model.DraftableSet
import com.mmg.manahub.feature.draft.domain.model.TierCard
import com.mmg.manahub.feature.draft.domain.repository.DraftSimRepository
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSetsUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetCardsPageUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetTierListUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [DraftSimRepository].
 *
 * Assembles a fully-resolved [DraftableSet] for simulation by combining three
 * already-cached sources (sets index, set cards, tier list) with the booster
 * structure fetched from the Worker's `booster.json`. Persists in-progress
 * sessions as a single JSON blob in `draft_sessions` (see [DraftSessionEntity]).
 *
 * @see DraftSimRepository for the contract and per-method behaviour notes.
 */
@Singleton
class DraftSimRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudflareApi: CloudflareContentApi,
    private val getDraftableSets: GetDraftableSetsUseCase,
    private val getSetTierList: GetSetTierListUseCase,
    private val getSetCardsPage: GetSetCardsPageUseCase,
    private val deckRepository: DeckRepository,
    private val draftSessionDao: DraftSessionDao,
    private val gson: Gson,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DraftSimRepository {

    companion object {
        /**
         * Version of the serialised [DraftState] shape stored in `draft_sessions.stateJson`.
         * Bump this whenever DraftState (or any nested model) changes in a way that
         * breaks Gson deserialisation; sessions with a different version are discarded.
         */
        const val CURRENT_SCHEMA_VERSION = 1

        /** Minimum gap between Coil preload enqueues to honour Scryfall's ≤10 req/s guideline. */
        private const val IMAGE_PREWARM_DELAY_MS = 100L
    }

    /** Best-effort, fire-and-forget scope for image pre-warming. Failures are ignored. */
    private val prewarmScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // Use the app-level ImageLoader (already backed by the global OkHttpClient) rather than
    // creating a private instance. A private instance would spawn a second OkHttpClient and
    // connection pool, competing with Scryfall API calls and Supabase token-refresh requests.
    private val imageLoader: ImageLoader get() = Coil.imageLoader(context)

    // -------------------------------------------------------------------------
    // getDraftableSimSet
    // -------------------------------------------------------------------------

    override suspend fun getDraftableSimSet(setCode: String): DataResult<DraftableSet> =
        withContext(ioDispatcher) {
            try {
                // 1. Resolve the set from the index and confirm it is draftable.
                val setsResult = getDraftableSets()
                val set = when (setsResult) {
                    is DataResult.Success ->
                        setsResult.data.firstOrNull { it.code.equals(setCode, ignoreCase = true) }
                    is DataResult.Error ->
                        return@withContext DataResult.Error(setsResult.message)
                }
                    ?: return@withContext DataResult.Error(DraftError.SetNotDraftable.toString())

                if (set.boosterVersion == null) {
                    return@withContext DataResult.Error(DraftError.SetNotDraftable.toString())
                }

                // 2. Fetch the full card pool, paging until Scryfall reports there are no more
                // pages. Using has_more avoids the trailing 422 that occurs when requesting one
                // page past the last.
                val cards = mutableListOf<Card>()
                var page = 1
                while (true) {
                    when (val pageResult = getSetCardsPage(setCode, page)) {
                        is DataResult.Success -> {
                            val (pageCards, hasMore) = pageResult.data
                            cards += pageCards
                            if (!hasMore || pageCards.isEmpty()) break
                            page++
                        }
                        is DataResult.Error ->
                            // Stop on the first page error; if we already collected some
                            // cards keep them, otherwise surface "not downloaded".
                            if (cards.isEmpty()) {
                                return@withContext DataResult.Error(
                                    DraftError.SetNotDownloaded.toString(),
                                )
                            } else {
                                break
                            }
                    }
                }
                if (cards.isEmpty()) {
                    return@withContext DataResult.Error(DraftError.SetNotDownloaded.toString())
                }

                // 3. Fetch the tier list and build the scryfallId → TierCard rating map.
                // The tier list is an optional enhancement: the bots already handle an empty
                // ratings map by falling back to heuristics. A missing or empty tier list must
                // therefore degrade gracefully (empty map) rather than block the whole feature.
                val ratings: Map<String, TierCard> = when (val tier = getSetTierList(setCode)) {
                    is DataResult.Success ->
                        tier.data.tiers
                            .flatMap { it.cards }
                            .associateBy { it.scryfallId }
                    is DataResult.Error -> emptyMap()
                }

                // 4. Fetch and parse booster.json.
                val boosterConfig = parseBoosterConfig(
                    cloudflareApi.getSetBooster(setCode.lowercase()),
                    setCode,
                )

                // 5. Assemble.
                val draftableSet = DraftableSet(
                    set = set,
                    cards = cards,
                    booster = boosterConfig,
                    ratings = ratings,
                )

                // 6. Pre-warm the Coil image cache (best-effort, non-blocking).
                prewarmImageCache(cards)

                DataResult.Success(draftableSet)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                DataResult.Error(DraftError.Unexpected(e.message ?: "Failed to load set").toString())
            }
        }

    /**
     * Maps the Worker's booster.json [JsonObject] to the domain [BoosterConfig].
     * Missing/null fields fall back to safe empty values so a partially-formed
     * config never crashes the generator.
     */
    private fun parseBoosterConfig(json: JsonObject, setCode: String): BoosterConfig {
        val dto = gson.fromJson(json, BoosterConfigDto::class.java)
            ?: throw JsonSyntaxException("Empty booster.json for $setCode")

        val sheets = dto.sheets.orEmpty().mapValues { (_, sheetDto) ->
            BoosterSheet(
                foil = sheetDto.foil ?: false,
                balanceColors = sheetDto.balanceColors ?: false,
                cards = sheetDto.cards.orEmpty().map { (id, weight) ->
                    BoosterCardEntry(id = id, weight = weight)
                },
            )
        }

        val boosters = dto.boosters.orEmpty().map { variantDto ->
            BoosterVariant(
                weight = variantDto.weight ?: 1,
                contents = variantDto.contents.orEmpty(),
            )
        }

        return BoosterConfig(
            setCode = dto.setCode ?: setCode.lowercase(),
            schemaVersion = dto.schemaVersion ?: 1,
            boosters = boosters,
            sheets = sheets,
        )
    }

    /**
     * Pre-warms the Coil disk cache for card art crops at ≤10 req/s.
     *
     * Runs in a detached supervisor scope so it never blocks the caller or propagates
     * failures into the draft flow. Each request is awaited (not just enqueued) before
     * the next delay fires — this is the only way to enforce the rate limit, because
     * `enqueue` is non-blocking and Coil would otherwise dispatch all requests immediately
     * from its own dispatcher pool, saturating the network and interfering with background
     * Supabase token-refresh requests.
     */
    private fun prewarmImageCache(cards: List<Card>) {
        prewarmScope.launch {
            for (card in cards) {
                val url = card.imageArtCrop ?: continue
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()
                runCatching { imageLoader.execute(request) }  // await — enforces rate limit
                delay(IMAGE_PREWARM_DELAY_MS)
            }
        }
    }

    // -------------------------------------------------------------------------
    // observeActiveSession
    // -------------------------------------------------------------------------

    override fun observeActiveSession(): Flow<DraftState?> =
        draftSessionDao.observeActiveSession()
            .map { entity -> entity?.let { deserializeState(it) } }
            .flowOn(ioDispatcher)

    /**
     * Deserialises a stored session to [DraftState], or null when the stored
     * schema version is incompatible or the JSON is malformed. Incompatible
     * sessions are discarded rather than crash-deserialised.
     */
    private fun deserializeState(entity: DraftSessionEntity): DraftState? {
        if (entity.stateSchemaVersion != CURRENT_SCHEMA_VERSION) return null
        return try {
            gson.fromJson(entity.stateJson, DraftState::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    // -------------------------------------------------------------------------
    // saveSession
    // -------------------------------------------------------------------------

    override suspend fun saveSession(state: DraftState) {
        withContext(ioDispatcher) {
            try {
                val id = deriveSessionId(state)
                val now = System.currentTimeMillis()
                // Preserve the original createdAt across saves of the same session.
                val createdAt = draftSessionDao.getById(id)?.createdAt ?: now
                val entity = DraftSessionEntity(
                    id = id,
                    setCode = state.config.setCode,
                    mode = state.config.mode.name,
                    status = state.status.name,
                    createdAt = createdAt,
                    updatedAt = now,
                    stateSchemaVersion = CURRENT_SCHEMA_VERSION,
                    stateJson = gson.toJson(state),
                )
                draftSessionDao.upsert(entity)
            } catch (e: Exception) {
                // Persisting the session is the only durable record of draft progress;
                // record the failure as a non-fatal, then rethrow so the caller's
                // error path still fires (the draft state is not silently lost).
                FirebaseCrashlytics.getInstance().recordException(e)
                throw e
            }
        }
    }

    /**
     * One active session per (set, mode) pair. The set code is normalised to lowercase so a
     * session saved with an uppercase config code is still found when looked up with the
     * Scryfall-canonical lowercase code (and vice versa).
     */
    private fun deriveSessionId(state: DraftState): String =
        "${state.config.setCode.lowercase()}-${state.config.mode.name}"

    // -------------------------------------------------------------------------
    // completeAndSaveDeck
    // -------------------------------------------------------------------------

    override suspend fun completeAndSaveDeck(result: DraftResult): DataResult<String> =
        withContext(ioDispatcher) {
            try {
                val setCode = result.seat.pool.firstOrNull()?.card?.setCode ?: "Set"
                val deckId = deckRepository.createDeck(
                    name = "Draft — ${setCode.uppercase()}",
                    description = "Auto-generated from Draft Simulator",
                    format = "DRAFT",
                )

                val slots = buildList {
                    result.deck.mainboard.forEach { draftCard ->
                        add(Triple(draftCard.card.scryfallId, 1, false))
                    }
                    result.deck.basics.forEach { basic ->
                        add(Triple(basic.scryfallId, basic.count, false))
                    }
                }
                // If populating the deck fails, compensate by deleting the freshly-created empty
                // deck so we never leave an orphaned, card-less deck behind. Rethrow so the outer
                // catch surfaces the error and the session is NOT marked complete.
                runCatching {
                    deckRepository.replaceAllCards(deckId, slots)
                }.onFailure { e ->
                    runCatching { deckRepository.deleteDeck(deckId) }
                    FirebaseCrashlytics.getInstance().recordException(e)
                    throw e
                }

                // Mark the matching active session complete, if one exists.
                // Completion must never fail the deck save, so it is best-effort.
                runCatching { markCompleteForSet(setCode, deckId) }

                DataResult.Success(deckId)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                DataResult.Error(
                    DraftError.Unexpected(e.message ?: "Failed to save deck").toString(),
                )
            }
        }

    /**
     * Marks any active session for [setCode] complete. The session id is derived
     * from set+mode; we try both modes so completion is robust whether the result
     * came from a DRAFT or SEALED run.
     */
    private suspend fun markCompleteForSet(setCode: String, deckId: String) {
        // Match the lowercase normalisation used by deriveSessionId so the session id resolves
        // regardless of the casing the deck's setCode happened to carry.
        val normalizedCode = setCode.lowercase()
        listOf("DRAFT", "SEALED").forEach { mode ->
            val id = "$normalizedCode-$mode"
            if (draftSessionDao.getById(id) != null) {
                draftSessionDao.markComplete(id, deckId)
            }
        }
    }
}
