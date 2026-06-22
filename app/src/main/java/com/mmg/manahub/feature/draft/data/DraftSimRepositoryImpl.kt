package com.mmg.manahub.feature.draft.data

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.mmg.manahub.core.data.local.dao.DraftSessionDao
import com.mmg.manahub.core.data.local.entity.DraftSessionEntity
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.data.remote.CloudflareContentClient
import com.mmg.manahub.feature.draft.data.remote.dto.BoosterConfigDto
import com.mmg.manahub.feature.draft.data.remote.dto.EngineConfigDto
import com.mmg.manahub.feature.draft.domain.model.BoosterCardEntry
import com.mmg.manahub.feature.draft.domain.model.BoosterConfig
import com.mmg.manahub.feature.draft.domain.model.BoosterSheet
import com.mmg.manahub.feature.draft.domain.model.BoosterVariant
import com.mmg.manahub.feature.draft.domain.model.DraftError
import com.mmg.manahub.feature.draft.domain.model.DraftResult
import com.mmg.manahub.feature.draft.domain.model.DraftState
import com.mmg.manahub.feature.draft.domain.model.DraftableSet
import com.mmg.manahub.feature.draft.domain.model.EngineArchetype
import com.mmg.manahub.feature.draft.domain.model.EngineCardSignals
import com.mmg.manahub.feature.draft.domain.model.EngineConfig
import com.mmg.manahub.feature.draft.domain.model.EngineParams
import com.mmg.manahub.feature.draft.domain.model.TierCard
import com.mmg.manahub.feature.draft.domain.repository.DraftSimRepository
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSetsUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetCardsPageUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetTierListUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val cloudflareClient: CloudflareContentClient,
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
    }

    /**
     * In-memory cache of parsed engine configs, keyed by lowercase set code. The value is
     * nullable so a set with no engine.json caches its absence (avoids re-fetching a 404 on
     * every bot pick). Guarded by [engineCacheMutex]; `containsKey` distinguishes
     * "not yet loaded" from "loaded, absent".
     */
    private val engineConfigCache = mutableMapOf<String, EngineConfig?>()
    private val engineCacheMutex = Mutex()

    // Image pre-warming was removed: loading 300+ art crops through the shared 50 MB OkHttp
    // disk cache evicts Scryfall search JSON responses, causing subsequent card-search requests
    // to receive bare 304s that Retrofit cannot parse. Cards are loaded on demand by Coil
    // as they appear in the pack grid, which is the correct loading strategy for draft.

    // -------------------------------------------------------------------------
    // getDraftableSimSet
    // -------------------------------------------------------------------------

    override suspend fun getDraftableSimSet(setCode: String): DataResult<DraftableSet> =
        withContext(ioDispatcher) {
            try {
                // 1. Resolve the set from the index and confirm it is draftable.
                // Always force-refresh: the 24 h Room cache may contain rows where
                // boosterVersion is NULL (set before the migration added the column).
                val setsResult = getDraftableSets(forceRefresh = true)
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
                val boosterJson = gson.fromJson(
                    cloudflareClient.getSetBooster(setCode.lowercase()),
                    JsonObject::class.java,
                )
                val boosterConfig = parseBoosterConfig(boosterJson, setCode)

                // 5. Assemble.
                val draftableSet = DraftableSet(
                    set = set,
                    cards = cards,
                    booster = boosterConfig,
                    ratings = ratings,
                )

                DataResult.Success(draftableSet)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                DataResult.Error(DraftError.Unexpected(e.message ?: "Failed to load set").toString())
            }
        }

    // -------------------------------------------------------------------------
    // getEngineConfig
    // -------------------------------------------------------------------------

    override suspend fun getEngineConfig(setCode: String): EngineConfig? =
        withContext(ioDispatcher) {
            val code = setCode.lowercase()

            // Fast path: under the lock, return the cached value (including a cached null/absent)
            // if we already loaded this code. The lock is held only for the map lookup, never
            // across the network call below.
            engineCacheMutex.withLock {
                if (engineConfigCache.containsKey(code)) {
                    return@withContext engineConfigCache[code]
                }
            }

            // Slow path: fetch + parse WITHOUT holding the lock, so concurrent requests for
            // different set codes never block each other. A set without an engine.json (404 / any
            // error) is a normal, expected case → null, cached below so the bot falls back to the
            // heuristic without re-fetching. A duplicate concurrent fetch of the SAME code is
            // acceptable (idempotent); whichever result is stored first wins.
            val config = try {
                val engineJson = gson.fromJson(
                    cloudflareClient.getSetEngine(code),
                    JsonObject::class.java,
                )
                parseEngineConfig(engineJson, code)
            } catch (e: Exception) {
                null
            }

            // Re-acquire the lock to store. If another coroutine populated this code while we were
            // fetching, prefer the existing entry (getOrPut semantics) so the result is stable.
            engineCacheMutex.withLock {
                if (engineConfigCache.containsKey(code)) {
                    engineConfigCache[code]
                } else {
                    engineConfigCache[code] = config
                    config
                }
            }
        }

    /**
     * Maps the Worker's engine.json [JsonObject] to the domain [EngineConfig].
     * Missing/null fields fall back to [EngineParams] defaults and empty collections so a
     * partially-formed config still drives the bot rather than crashing it.
     */
    private fun parseEngineConfig(json: JsonObject, setCode: String): EngineConfig {
        val dto = gson.fromJson(json, EngineConfigDto::class.java)
            ?: throw JsonSyntaxException("Empty engine.json for $setCode")

        val defaults = EngineParams()
        // Sanitise every weight from untrusted/malformed engine.json: a non-finite (NaN/Infinity)
        // value breaks `maxWithOrNull`'s comparator (inconsistent ordering → non-deterministic
        // picks), and a negative weight inverts the scoring term it multiplies. Non-finite → the
        // default; otherwise clamp to the valid floor. Thresholds/counts are clamped, not defaulted.
        val params = dto.params?.let { p ->
            EngineParams(
                ratingWeight = sanitizeWeight(p.ratingWeight, defaults.ratingWeight),
                synergyWeight = sanitizeWeight(p.synergyWeight, defaults.synergyWeight),
                opennessWeight = sanitizeWeight(p.opennessWeight, defaults.opennessWeight),
                fixingBonus = sanitizeWeight(p.fixingBonus, defaults.fixingBonus),
                curveWeight = sanitizeWeight(p.curveWeight, defaults.curveWeight),
                commitmentThreshold = sanitizeWeight(
                    p.commitmentThreshold,
                    defaults.commitmentThreshold,
                ),
                speculationPicks = (p.speculationPicks ?: defaults.speculationPicks)
                    .coerceAtLeast(0),
            )
        } ?: defaults

        val archetypes = dto.archetypes.orEmpty().mapNotNull { a ->
            val id = a.id ?: return@mapNotNull null
            EngineArchetype(
                id = id,
                name = a.name ?: id,
                colors = a.colors.orEmpty(),
                tier = a.tier ?: 99,
                // A non-finite or negative openness would poison the openness term; default to 1.0f.
                opennessBase = (a.opennessBase ?: 1.0f).let {
                    if (it.isFinite() && it >= 0f) it else 1.0f
                },
                keyCardIds = a.keyCardIds.orEmpty(),
            )
        }

        val cards = dto.cards.orEmpty().mapNotNull { (id, signals) ->
            if (id.isBlank()) return@mapNotNull null
            id to EngineCardSignals(
                // Keep only finite, non-negative archetype weights; a negative weight would let a
                // card anti-pick the committed archetype, a NaN/Infinity weight would break scoring.
                archetypeWeights = signals.archetypeWeights.orEmpty()
                    .filterValues { it.isFinite() && it >= 0f },
                // A rating override is only trusted when it is finite and in [0, 1]; otherwise null
                // so the bot falls back to DraftRatingNormalizer.
                rating = signals.rating?.takeIf { it.isFinite() && it in 0f..1f },
                fixing = signals.fixing ?: false,
                removal = signals.removal ?: false,
                evasion = signals.evasion ?: false,
                bomb = signals.bomb ?: false,
            )
        }.toMap()

        return EngineConfig(
            setCode = dto.setCode ?: setCode,
            schemaVersion = dto.schemaVersion ?: 1,
            lastUpdated = dto.lastUpdated.orEmpty(),
            params = params,
            archetypes = archetypes,
            cards = cards,
        )
    }

    /**
     * Sanitises a parsed scoring weight: a null or non-finite ([Float.NaN]/±Infinity) value falls
     * back to [default]; otherwise the value is clamped to `>= 0f` (negative weights invert the
     * scoring term they multiply and must never reach the bot). Centralised so every [EngineParams]
     * weight is sanitised identically.
     */
    private fun sanitizeWeight(value: Float?, default: Float): Float =
        if (value == null || !value.isFinite()) default else value.coerceAtLeast(0f)

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
                cards = sheetDto.cards.orEmpty().mapNotNull { entry ->
                    val id = entry.id ?: return@mapNotNull null
                    BoosterCardEntry(id = id, weight = entry.weight ?: 1)
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
