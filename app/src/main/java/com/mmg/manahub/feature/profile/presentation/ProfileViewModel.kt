package com.mmg.manahub.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.domain.update.AppUpdateState
import com.mmg.manahub.core.domain.update.AppUpdateStatusProvider
import com.mmg.manahub.core.gamification.domain.GamificationAvailability
import com.mmg.manahub.core.gamification.domain.catalog.UnlockableKind
import com.mmg.manahub.core.gamification.domain.model.AchievementUiModel
import com.mmg.manahub.core.gamification.domain.model.ClaimResult
import com.mmg.manahub.core.gamification.domain.model.EquippedCosmetics
import com.mmg.manahub.core.gamification.domain.model.PlayerProgression
import com.mmg.manahub.core.gamification.domain.model.QuestBoard
import com.mmg.manahub.core.gamification.domain.model.RewardUiModel
import com.mmg.manahub.core.gamification.domain.model.RewardsBoard
import com.mmg.manahub.core.gamification.domain.model.StreakUiModel
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.core.gamification.domain.usecase.ClaimQuestRewardUseCase
import com.mmg.manahub.core.model.CollectionColorAffinity
import com.mmg.manahub.core.model.CollectionStats
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.feature.friends.domain.usecase.ShareInviteUseCase
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Backs the Profile screen: identity, game/collection KPIs, friends counts and the gamification tabs.
 *
 * Every gamification read is scoped to [GamificationAvailability.availableFlow]: while it is false no
 * gamification Room flow is collected and every gamification field holds its empty value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val statsRepo: StatsRepository,
    private val gameSessionRepo: GameSessionRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val friendRepository: FriendRepository,
    private val authRepository: AuthRepository,
    private val gamificationRepository: GamificationRepository,
    private val claimQuestRewardUseCase: ClaimQuestRewardUseCase,
    private val shareInviteUseCase: ShareInviteUseCase,
    private val appUpdateStatusProvider: AppUpdateStatusProvider,
    private val gamificationAvailability: GamificationAvailability,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    /**
     * Profile screen state.
     *
     * @property isLoading true until the collection stats first resolve (or fail).
     * @property statsError true when the collection stats flow failed; the Overview shows a retry.
     * @property mostValuableColors colour identity of the most valuable card (`["C"]` when colourless),
     *   or null when there is no priced card.
     * @property totalGames sessions with a local seat; draws are excluded from the win-rate denominator.
     * @property gamificationEnabled false until availability resolves true; hides every gamification surface.
     * @property claimingQuestIds quest instances whose claim is in flight (the Claim button is disabled).
     */
    data class UiState(
        val playerName: String = "Wizard",
        val isLoading: Boolean = true,
        val statsError: Boolean = false,
        val avatarUrl: String? = null,
        val collectionStats: CollectionStats? = null,
        val favouriteColor: String? = null,
        val mostValuableColors: List<String>? = null,
        val totalGames: Int = 0,
        val totalWins: Int = 0,
        val totalDraws: Int = 0,
        val preferredCurrency: PreferredCurrency = PreferredCurrency.USD,
        val friendCount: Int = 0,
        val pendingFriendCount: Int = 0,
        val gamificationEnabled: Boolean = false,
        val progression: PlayerProgression? = null,
        val achievements: List<AchievementUiModel> = emptyList(),
        val questBoard: QuestBoard = QuestBoard.empty,
        val streak: StreakUiModel = EMPTY_STREAK,
        val rewardsBoard: RewardsBoard = RewardsBoard.EMPTY,
        val equipped: EquippedCosmetics = EquippedCosmetics.NONE,
        val claimingQuestIds: Set<String> = emptySet(),
    ) {
        /** Local-seat wins over local-seat games; 0 when no game was played. */
        val winRate: Float get() = (totalGames - totalDraws).takeIf { it > 0 }
            ?.let { totalWins.toFloat() / it } ?: 0f
    }

    /** One-shot side effects for the Profile screen. */
    sealed interface Event {
        /** A quest reward was claimed; [xpAwarded] XP was granted. */
        data class QuestClaimed(val xpAwarded: Int) : Event

        /** A quest claim failed (not completed, not found, or a storage error). */
        data object QuestClaimFailed : Event

        /** The player tried to equip a 4th badge; the cap is [EquippedCosmetics.MAX_EQUIPPED_BADGES]. */
        data class BadgeCapReached(val maxBadges: Int) : Event
    }

    private data class GamificationSlice(
        val progression: PlayerProgression?,
        val achievements: List<AchievementUiModel>,
        val questBoard: QuestBoard,
        val streak: StreakUiModel,
        val rewardsBoard: RewardsBoard,
        val equipped: EquippedCosmetics,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Buffered Channel (not a nullable StateFlow): a StateFlow would equality-collapse repeated
    // identical claim results and drop events while the lifecycle is paused.
    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    /** App-wide update status owned by the single app update controller. */
    val appUpdateState: StateFlow<AppUpdateState> = appUpdateStatusProvider.state

    private val statsRetry = MutableStateFlow(0)

    // Serializes equip/unequip so each read-modify-write sees the previous one's result (P-09).
    private val equipMutex = Mutex()

    init {
        userPreferencesDataStore.avatarUrlFlow
            .onEach { url -> _uiState.update { it.copy(avatarUrl = url) } }
            .catch { reportFlowError("avatar", it) }
            .launchIn(viewModelScope)

        userPreferencesDataStore.playerNameFlow
            .distinctUntilChanged()
            .onEach { name -> _uiState.update { it.copy(playerName = name) } }
            .catch { reportFlowError("player_name", it) }
            .launchIn(viewModelScope)

        val currencyFlow = userPreferencesDataStore.preferencesFlow
            .map { it.preferredCurrency }
            .distinctUntilChanged()

        currencyFlow
            .onEach { currency -> _uiState.update { it.copy(preferredCurrency = currency) } }
            .catch { reportFlowError("preferences", it) }
            .launchIn(viewModelScope)

        combine(currencyFlow, statsRetry) { currency, _ -> currency }
            .flatMapLatest { currency ->
                statsRepo.observeCollectionStats(currency)
                    .onEach { stats ->
                        _uiState.update {
                            it.copy(
                                collectionStats = stats,
                                isLoading = false,
                                statsError = false,
                                favouriteColor = stats.computeFavouriteColor(),
                                mostValuableColors = stats.computeMostValuableColors(),
                            )
                        }
                    }
                    .catch { e ->
                        reportFlowError("collection_stats", e)
                        _uiState.update { it.copy(isLoading = false, statsError = true) }
                    }
            }
            .launchIn(viewModelScope)

        gameSessionRepo.observeTotalGames()
            .onEach { n -> _uiState.update { it.copy(totalGames = n) } }
            .catch { reportFlowError("total_games", it) }
            .launchIn(viewModelScope)

        gameSessionRepo.observeLocalWins()
            .onEach { wins -> _uiState.update { it.copy(totalWins = wins) } }
            .catch { reportFlowError("local_wins", it) }
            .launchIn(viewModelScope)

        gameSessionRepo.observeLocalDraws()
            .onEach { draws -> _uiState.update { it.copy(totalDraws = draws) } }
            .catch { reportFlowError("local_draws", it) }
            .launchIn(viewModelScope)

        friendRepository.observeFriendCount()
            .onEach { count -> _uiState.update { it.copy(friendCount = count) } }
            .catch { reportFlowError("friend_count", it) }
            .launchIn(viewModelScope)

        friendRepository.observePendingCount()
            .onEach { count -> _uiState.update { it.copy(pendingFriendCount = count) } }
            .catch { reportFlowError("pending_friend_count", it) }
            .launchIn(viewModelScope)

        // Refresh once per real account (not on every token refresh); a sign-out cancels it (P-24).
        viewModelScope.launch {
            authRepository.sessionState
                .map { session ->
                    (session as? SessionState.Authenticated)?.user?.takeUnless { it.isAnonymous }?.id
                }
                .distinctUntilChanged()
                .catch { reportFlowError("session", it) }
                .collectLatest { userId -> if (userId != null) refreshFriends(userId) }
        }

        observeGamification()
    }

    /** Starts the update path matching [appUpdateState] (download, restart to install, or store). */
    fun onUpdateClick() = appUpdateStatusProvider.requestUpdate()

    /** Re-subscribes the collection stats after a failure. */
    fun retryStats() {
        _uiState.update { it.copy(isLoading = true, statsError = false) }
        statsRetry.update { it + 1 }
    }

    /**
     * Resolves the current user's invite share link for the share sheet opened from the account card.
     * Fails fast (without hitting the network) when the session isn't authenticated.
     */
    suspend fun fetchShareLink(): Result<String> {
        val userId = (authRepository.sessionState.value as? SessionState.Authenticated)?.user?.id
            ?: return Result.failure(IllegalStateException("Not authenticated"))
        return shareInviteUseCase(userId)
    }

    /**
     * Claims a completed quest's XP reward. A second tap while the first claim is in flight is ignored,
     * and [ClaimResult.AlreadyClaimed] is silent, so a double tap never shows success then failure (P-08).
     */
    fun claimQuest(instanceId: String) {
        var started = false
        _uiState.update { state ->
            if (instanceId in state.claimingQuestIds) {
                state
            } else {
                started = true
                state.copy(claimingQuestIds = state.claimingQuestIds + instanceId)
            }
        }
        if (!started) return

        viewModelScope.launch {
            try {
                when (val result = claimQuestRewardUseCase(instanceId)) {
                    is ClaimResult.Claimed -> _events.send(Event.QuestClaimed(result.xpAwarded))
                    ClaimResult.AlreadyClaimed -> Unit
                    ClaimResult.NotCompleted, ClaimResult.NotFound -> {
                        crashReporter.log("profile_quest_claim_rejected")
                        _events.send(Event.QuestClaimFailed)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordFailure("profile_quest_claim_failed", e)
                _events.send(Event.QuestClaimFailed)
            } finally {
                _uiState.update { it.copy(claimingQuestIds = it.claimingQuestIds - instanceId) }
            }
        }
    }

    /**
     * Equips [reward]. Single-slot kinds replace the selection; BADGE appends to the equipped list
     * (capped at [EquippedCosmetics.MAX_EQUIPPED_BADGES], emitting [Event.BadgeCapReached] when full).
     * Ownership is guarded in the repository.
     */
    fun onEquip(reward: RewardUiModel) = launchEquip {
        when (reward.kind) {
            UnlockableKind.TITLE -> gamificationRepository.equipTitle(reward.id)
            UnlockableKind.AVATAR_FRAME -> gamificationRepository.equipAvatarFrame(reward.id)
            UnlockableKind.LEVEL_RING_STYLE -> gamificationRepository.equipLevelRingStyle(reward.id)
            UnlockableKind.BADGE -> {
                val current = gamificationRepository.observeEquippedCosmetics().first().badgeIds
                when {
                    reward.id in current -> Unit
                    current.size >= EquippedCosmetics.MAX_EQUIPPED_BADGES ->
                        _events.send(Event.BadgeCapReached(EquippedCosmetics.MAX_EQUIPPED_BADGES))
                    else -> gamificationRepository.equipBadges(current + reward.id)
                }
            }
        }
    }

    /** Unequips [reward]: single-slot kinds clear the slot; BADGE removes only [reward]. */
    fun onUnequip(reward: RewardUiModel) = launchEquip {
        when (reward.kind) {
            UnlockableKind.TITLE -> gamificationRepository.equipTitle(null)
            UnlockableKind.AVATAR_FRAME -> gamificationRepository.equipAvatarFrame(null)
            UnlockableKind.LEVEL_RING_STYLE -> gamificationRepository.equipLevelRingStyle(null)
            UnlockableKind.BADGE -> {
                val current = gamificationRepository.observeEquippedCosmetics().first().badgeIds
                if (reward.id in current) gamificationRepository.equipBadges(current - reward.id)
            }
        }
    }

    private fun launchEquip(block: suspend () -> Unit) {
        viewModelScope.launch {
            equipMutex.withLock {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    recordFailure("profile_equip_failed", e)
                }
            }
        }
    }

    private fun observeGamification() {
        gamificationAvailability.availableFlow
            .catch { e ->
                reportFlowError("gamification_availability", e)
                emit(false)
            }
            .distinctUntilChanged()
            .flatMapLatest { available ->
                if (!available) flowOf(null) else gamificationSliceFlow()
            }
            .onEach { slice ->
                _uiState.update { state ->
                    if (slice == null) {
                        state.copy(
                            gamificationEnabled = false,
                            progression = null,
                            achievements = emptyList(),
                            questBoard = QuestBoard.empty,
                            streak = EMPTY_STREAK,
                            rewardsBoard = RewardsBoard.EMPTY,
                            equipped = EquippedCosmetics.NONE,
                            claimingQuestIds = emptySet(),
                        )
                    } else {
                        state.copy(
                            gamificationEnabled = true,
                            progression = slice.progression,
                            achievements = slice.achievements,
                            questBoard = slice.questBoard,
                            streak = slice.streak,
                            rewardsBoard = slice.rewardsBoard,
                            equipped = slice.equipped,
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun gamificationSliceFlow(): Flow<GamificationSlice> {
        val progression = gamificationRepository.observeProgression()
            .map<PlayerProgression, PlayerProgression?> { it }
            .catch { reportFlowError("gamification_progression", it); emit(null) }
        val achievements = gamificationRepository.observeAchievements()
            .catch { reportFlowError("gamification_achievements", it); emit(emptyList()) }
        val quests = gamificationRepository.observeActiveQuests()
            .catch { reportFlowError("gamification_quests", it); emit(QuestBoard.empty) }
        val streak = gamificationRepository.observeDailyActivityStreak()
            .catch { reportFlowError("gamification_streak", it); emit(EMPTY_STREAK) }
        val rewards = gamificationRepository.observeRewards()
            .catch { reportFlowError("gamification_rewards", it); emit(RewardsBoard.EMPTY) }
        val equipped = gamificationRepository.observeEquippedCosmetics()
            .catch { reportFlowError("gamification_equipped", it); emit(EquippedCosmetics.NONE) }

        val progressFlows = combine(progression, achievements, quests, streak) { p, a, q, s ->
            GamificationSlice(p, a, q, s, RewardsBoard.EMPTY, EquippedCosmetics.NONE)
        }
        return combine(progressFlows, rewards, equipped) { slice, r, e ->
            slice.copy(rewardsBoard = r, equipped = e)
        }
    }

    private suspend fun refreshFriends(userId: String) {
        try {
            // Same atomic refresh as the Friends screen, so an accepted id never stays in the outgoing list.
            friendRepository.refreshAll(userId)
                .onFailure { e -> recordFailure("profile_friends_refresh_failed", e) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordFailure("profile_friends_refresh_failed", e)
        }
    }

    private fun reportFlowError(source: String, e: Throwable) {
        if (e is CancellationException) throw e
        crashReporter.setCustomKey("profile_flow_error_source", source)
        recordFailure("profile_flow_failed", e)
    }

    private fun recordFailure(tag: String, e: Throwable) {
        crashReporter.log(tag)
        crashReporter.recordException(RuntimeException("[$tag] ${e::class.simpleName}"))
    }

    // Shared with CollectionStatsSyncWorker so friends see the same colours as the owner.
    private fun CollectionStats.computeFavouriteColor(): String? = CollectionColorAffinity.favouriteColorCode(byColor)

    private fun CollectionStats.computeMostValuableColors(): List<String>? =
        mostValuableCards.firstOrNull()?.colorIdentity?.let(CollectionColorAffinity::identityCodes)

    private companion object {
        val EMPTY_STREAK = StreakUiModel(current = 0, longest = 0, freezeTokens = 0)
    }
}
