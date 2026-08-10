package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.remote.FriendRemoteDataSource
import com.mmg.manahub.core.data.remote.FriendRequestWithProfile
import com.mmg.manahub.core.data.remote.FriendWithProfile
import com.mmg.manahub.core.data.remote.OutgoingRequestWithProfile
import com.mmg.manahub.core.data.remote.UNKNOWN_DISPLAY_NAME
import com.mmg.manahub.core.data.remote.dto.FriendMatchHistoryDto
import com.mmg.manahub.core.data.remote.dto.FriendStatsDto
import com.mmg.manahub.core.data.remote.orNullIfBlank
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.model.AcceptInviteResult
import com.mmg.manahub.core.model.FolderFilters
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.FriendCard
import com.mmg.manahub.core.model.FriendMatchHistory
import com.mmg.manahub.core.model.FriendRequest
import com.mmg.manahub.core.model.FriendStats
import com.mmg.manahub.core.model.OutgoingFriendRequest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Web [FriendRepository] implementation (web scope expansion, Friends slice, approved 2026-08-04).
 * The fifth web data-layer slice, and the first whose Android counterpart
 * ([com.mmg.manahub.feature.friends.data.repository.FriendRepositoryImpl] under `app/`) is Room-
 * backed with a separate `refresh*(userId)` pull-from-remote step — the SAME "local CRUD, refresh
 * triggers a remote pull" shape [WebDeckRepository]/[WebUserCardRepository] already solved for the
 * web target (no Room on wasmJs): three in-memory [MutableStateFlow] caches stand in for Room,
 * fully REPLACED (never merged) by each `refresh*` call, exactly mirroring the Android
 * `dao.clear*()` + `dao.upsert*()` pair.
 *
 * ## Deliberately NOT ported from [com.mmg.manahub.feature.friends.data.repository.FriendRepositoryImpl]
 * Gamification (`ProgressionEventBus.emit(ProgressionEvent.FriendAdded(...))` on a successful
 * accept) is entirely out of web v1 scope per the master plan — this class never touches it. The
 * Android impl's "retry the post-accept refresh once on failure" nuance is also simplified to a
 * single attempt with a [CrashReporter] breadcrumb on failure — low-frequency user action, not a
 * hot path, and this repo's caches never go stale beyond one manual re-navigation since every
 * screen re-collects `observe*()` on entry anyway.
 *
 * ## Hydration
 * Same pattern as [WebDeckRepository]/[WebUserCardRepository]: driven by
 * [SupabaseClient.auth]'s `sessionStatus`, not a one-shot `init` fetch, since a fresh page load's
 * session restore-from-`localStorage` is itself async (supabase-kt's own default behavior) — a
 * one-shot fetch at construction time could race ahead of auth and silently see no session.
 * Re-fetching every time `sessionStatus` resolves to [SessionStatus.Authenticated] covers both the
 * "already had a session, restoring from storage" case and the "user just tapped guest sign-in"
 * case with the same code path. Unlike [WebDeckRepository]/[WebUserCardRepository], every mutation
 * here (`acceptRequest`/`rejectRequest`/`cancelOutgoingRequest`/`removeFriend`) ALSO needs a
 * `currentUserId` to re-derive the friends list correctly, so callers (the ViewModel) resolve and
 * pass it explicitly per the [FriendRepository] interface contract — this repository never reads
 * `auth.currentUserOrNull()` internally for that purpose (only [sessionScope]'s own hydration
 * trigger does).
 *
 * ## [getFriendCollection] enrichment
 * Reuses the already-registered [CardRepository] singleton ([WebCardRepository] on web) to resolve
 * each row's card metadata — the exact join-through-another-repository pattern
 * `project_w3d_collection_repository.md` documents ("inject that repository, don't duplicate its
 * networking/caching stack"). A row whose card can't be resolved is silently omitted (same
 * degrade-gracefully tolerance the Android impl documents) rather than falling back to a live
 * Scryfall fetch per row.
 */
@OptIn(ExperimentalTime::class)
class WebFriendRepository(
    private val remote: FriendRemoteDataSource,
    private val cardRepository: CardRepository,
    private val supabaseClient: SupabaseClient,
    private val crashReporter: CrashReporter,
) : FriendRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val friendsCache = MutableStateFlow<Map<String, Friend>>(emptyMap())
    private val pendingCache = MutableStateFlow<Map<String, FriendRequest>>(emptyMap())
    private val outgoingCache = MutableStateFlow<Map<String, OutgoingFriendRequest>>(emptyMap())

    init {
        repositoryScope.launch {
            supabaseClient.auth.sessionStatus.collect { status ->
                val userId = (status as? SessionStatus.Authenticated)?.session?.user?.id
                if (!userId.isNullOrBlank()) {
                    refreshFriends(userId)
                    refreshRequests(userId)
                    refreshOutgoingRequests(userId)
                }
            }
        }
    }

    // ── Observables ───────────────────────────────────────────────────────────

    override fun observeFriends(): Flow<List<Friend>> =
        friendsCache.map { it.values.sortedBy { f -> f.nickname.lowercase() } }

    override fun observePendingRequests(): Flow<List<FriendRequest>> =
        pendingCache.map { it.values.toList() }

    override fun observeOutgoingRequests(): Flow<List<OutgoingFriendRequest>> =
        outgoingCache.map { it.values.toList() }

    override fun observePendingCount(): Flow<Int> = pendingCache.map { it.size }

    override fun observeFriendCount(): Flow<Int> = friendsCache.map { it.size }

    // ── Refresh (full replace, mirrors Android's dao.clear()+upsert() pair) ────

    override suspend fun refreshFriends(currentUserId: String): Result<Unit> =
        remote.getFriends(currentUserId).map { friends ->
            friendsCache.update { friends.associate { it.id to it.toDomain() } }
        }

    override suspend fun refreshRequests(currentUserId: String): Result<Unit> =
        remote.getPendingRequests(currentUserId).map { requests ->
            pendingCache.update { requests.associate { it.id to it.toDomain() } }
        }

    override suspend fun refreshOutgoingRequests(currentUserId: String): Result<Unit> =
        remote.getOutgoingRequests(currentUserId).map { requests ->
            outgoingCache.update { requests.associate { it.id to it.toDomain() } }
        }

    // ── Mutations ─────────────────────────────────────────────────────────────

    override suspend fun sendFriendRequest(fromUserId: String, toUserId: String): Result<Unit> =
        remote.sendFriendRequest(fromUserId, toUserId)

    override suspend fun acceptRequest(friendshipId: String, currentUserId: String): Result<Unit> =
        remote.acceptRequest(friendshipId).also { result ->
            if (result.isSuccess) {
                pendingCache.update { it - friendshipId }
                refreshFriends(currentUserId).onFailure { e ->
                    crashReporter.log("acceptRequest: refreshFriends failed after ACCEPT (friendshipId=$friendshipId)")
                    crashReporter.recordException(e)
                }
            }
        }

    override suspend fun rejectRequest(friendshipId: String): Result<Unit> =
        remote.rejectRequest(friendshipId).also { result ->
            if (result.isSuccess) pendingCache.update { it - friendshipId }
        }

    override suspend fun cancelOutgoingRequest(friendshipId: String): Result<Unit> =
        remote.rejectRequest(friendshipId).also { result ->
            if (result.isSuccess) outgoingCache.update { it - friendshipId }
        }

    override suspend fun removeFriend(friendshipId: String): Result<Unit> =
        remote.removeFriend(friendshipId).also { result ->
            if (result.isSuccess) friendsCache.update { it - friendshipId }
        }

    override suspend fun searchByGameTag(gameTag: String): Result<Friend?> =
        remote.searchByGameTag(gameTag).map { dto ->
            dto?.let {
                Friend(
                    // No friendship exists yet -- matches Android's identical "id = ''" convention
                    // for a raw search hit (FriendRepositoryImpl.searchByGameTag).
                    id = "",
                    userId = it.id,
                    nickname = it.nickname.orNullIfBlank()
                        ?: it.gameTag.orNullIfBlank()
                        ?: UNKNOWN_DISPLAY_NAME,
                    gameTag = it.gameTag ?: "",
                    avatarUrl = it.avatarUrl,
                )
            }
        }

    override suspend fun acceptInvite(referralCode: String): Result<AcceptInviteResult> =
        runCatching {
            val dto = remote.acceptInvite(referralCode)
            AcceptInviteResult(inviterId = dto.inviterId, inviterNickname = dto.inviterNickname)
        }.recoverCatching { throwable ->
            // Extract only the known semantic token from the Supabase error body -- never the raw
            // PostgreSQL message -- same mapping as Android's FriendRepositoryImpl.acceptInvite.
            val rawBody = throwable.message ?: ""
            val token = when {
                rawBody.contains("SELF_INVITE", ignoreCase = true) -> "SELF_INVITE"
                rawBody.contains("INVALID_CODE", ignoreCase = true) -> "INVALID_CODE"
                rawBody.contains("NOT_AUTHENTICATED", ignoreCase = true) -> "NOT_AUTHENTICATED"
                else -> "UNKNOWN_ERROR"
            }
            throw Exception(token)
        }

    override suspend fun getMyShareUrl(userId: String): Result<String> {
        val code = remote.getMyReferralCode(userId)
            ?: return Result.failure(Exception("No referral code found for user $userId"))
        return Result.success("https://miguelmglez.github.io/invite/$code")
    }

    override suspend fun getFriendCollection(
        friendUserId: String,
        list: String,
        query: String,
        filters: FolderFilters?,
        limit: Int,
        offset: Int,
    ): Result<List<FriendCard>> = runCatching {
        val dtos = remote.getFriendCollection(
            friendUserId = friendUserId,
            list = list,
            query = query,
            sets = filters?.sets?.takeIf { it.isNotEmpty() }?.toList(),
            rarities = filters?.rarities?.takeIf { it.isNotEmpty() }?.map { it.name },
            colors = filters?.colors?.takeIf { it.isNotEmpty() }?.map { it.name },
            foilOnly = filters?.foilOnly?.takeIf { it },
            conditions = filters?.conditions?.takeIf { it.isNotEmpty() }?.map { it.label },
            languages = filters?.languages?.takeIf { it.isNotEmpty() }?.toList(),
            limit = limit,
            offset = offset,
        )
        val distinctIds = dtos.map { it.scryfallId }.distinct()
        cardRepository.warmCacheForIds(distinctIds)
        val cardsById = cardRepository.getCardsByIds(distinctIds).associateBy { it.scryfallId }

        dtos.mapNotNull { dto ->
            val card = cardsById[dto.scryfallId]
            if (card == null) {
                crashReporter.log("getFriendCollection: card metadata unavailable for ${dto.scryfallId} (list=$list)")
                return@mapNotNull null
            }
            if (query.isNotBlank() && !card.name.contains(query, ignoreCase = true)) {
                return@mapNotNull null
            }
            FriendCard(
                sourceList = dto.sourceList,
                scryfallId = dto.scryfallId,
                name = card.name,
                typeLine = card.typeLine,
                imageNormal = card.imageNormal,
                imageArtCrop = card.imageArtCrop,
                setCode = card.setCode,
                setName = card.setName,
                rarity = card.rarity,
                priceEur = card.priceEur,
                priceUsd = card.priceUsd,
                priceEurFoil = card.priceEurFoil,
                priceUsdFoil = card.priceUsdFoil,
                quantity = dto.quantity,
                isFoil = dto.isFoil,
                isStale = card.isStale,
                condition = dto.condition,
                language = dto.language,
            )
        }
    }

    override suspend fun getFriendStats(friendUserId: String): Result<FriendStats?> =
        runCatching { remote.getFriendStats(friendUserId)?.toDomain() }

    override suspend fun getFriendMatchHistory(friendUserId: String): Result<FriendMatchHistory?> =
        runCatching { remote.getFriendMatchHistory(friendUserId)?.toDomain() }

    override suspend fun upsertMyStats(
        uniqueCards: Int,
        totalCards: Int,
        totalValueEur: Double,
        totalValueUsd: Double,
        favouriteColor: String?,
        mostValuableColor: String?,
    ): Result<Unit> = remote.upsertCollectionStats(
        uniqueCards = uniqueCards,
        totalCards = totalCards,
        totalValueEur = totalValueEur,
        totalValueUsd = totalValueUsd,
        favouriteColor = favouriteColor,
        mostValuableColor = mostValuableColor,
    )

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun FriendWithProfile.toDomain() = Friend(id, friendUserId, nickname, gameTag, avatarUrl)

    private fun FriendRequestWithProfile.toDomain() =
        FriendRequest(id, fromUserId, fromNickname, fromGameTag, fromAvatarUrl)

    private fun OutgoingRequestWithProfile.toDomain() =
        OutgoingFriendRequest(id, toUserId, toNickname, toGameTag, toAvatarUrl)

    private fun FriendStatsDto.toDomain() = FriendStats(
        userId = userId,
        uniqueCards = uniqueCards,
        totalCards = totalCards,
        totalValueEur = totalValueEur,
        totalValueUsd = totalValueUsd,
        favouriteColor = favouriteColor,
        mostValuableColor = mostValuableColor,
        // ISO-8601 string from Supabase -> epoch millis. Falls back to 0L on a parse failure so
        // the UI can still render the other fields (mirrors Android's identical fallback).
        updatedAt = runCatching { Instant.parse(updatedAt).toEpochMilliseconds() }.getOrDefault(0L),
    )

    private fun FriendMatchHistoryDto.toDomain() = FriendMatchHistory(
        myWins = myWins,
        opponentWins = opponentWins,
        totalGames = totalGames,
        lastPlayedAt = lastPlayedAt?.let {
            runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrDefault(0L)
        } ?: 0L,
    )
}
