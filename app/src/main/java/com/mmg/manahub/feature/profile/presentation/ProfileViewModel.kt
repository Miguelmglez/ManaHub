package com.mmg.manahub.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.DeckStatsRow
import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
import com.mmg.manahub.core.data.local.entity.GameSessionWithPlayers
import com.mmg.manahub.core.domain.model.CollectionStats
import com.mmg.manahub.core.domain.model.MtgColor
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.gamification.domain.catalog.UnlockableKind
import com.mmg.manahub.core.gamification.domain.model.AchievementUiModel
import com.mmg.manahub.core.gamification.domain.model.EquippedCosmetics
import com.mmg.manahub.core.gamification.domain.model.PlayerProgression
import com.mmg.manahub.core.gamification.domain.model.QuestBoard
import com.mmg.manahub.core.gamification.domain.model.RewardUiModel
import com.mmg.manahub.core.gamification.domain.model.RewardsBoard
import com.mmg.manahub.core.gamification.domain.model.StreakUiModel
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.core.gamification.domain.usecase.ClaimResult
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import com.mmg.manahub.feature.settings.presentation.PreferencesState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Enums ─────────────────────────────────────────────────────────────────────

enum class PlayStyle(val label: String, val icon: String) {
    AGGRO("Aggressor", "⚔"),
    CONTROL("Strategist", "🛡"),
    MIDRANGE("Midrange", "♟"),
    BALANCED("Balanced", "⚖"),
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val statsRepo: StatsRepository,
    private val gameSessionRepo: GameSessionRepository,
    private val surveyAnswerDao: SurveyAnswerDao,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val friendRepository: FriendRepository,
    private val authRepository: AuthRepository,
    private val gamificationRepository: GamificationRepository,
) : ViewModel() {

    data class UiState(
        val playerName: String = "Wizard",
        val playStyle: PlayStyle = PlayStyle.BALANCED,
        val isLoading: Boolean = true,
        val avatarUrl: String? = null,
        // Collection
        val collectionStats: CollectionStats? = null,
        val favouriteColor: String? = null,
        val mostValuableColor: String? = null,
        // Game stats
        val totalGames: Int = 0,
        val totalWins: Int = 0,
        val avgLifeOnWin: Double = 0.0,
        val avgLifeOnLoss: Double = 0.0,
        val currentStreak: Int = 0,
        val favoriteMode: String = "",
        val avgDurationMs: Double = 0.0,
        val mostFrequentElimination: String = "",
        val avgWinTurn: Double = 0.0,
        // Survey insights
        val surveyCount: Int = 0,
        val manaIssueCount: Int = 0,
        val avgHandRating: Double = 0.0,
        val favoriteWinStyle: String = "",
        // Decks + sessions
        val deckStats: List<DeckStatsRow> = emptyList(),
        val recentSessions: List<GameSessionWithPlayers> = emptyList(),
        // Achievements (gamification Phase 1 — rich model from the catalog + persisted progress)
        val achievements: List<AchievementUiModel> = emptyList(),
        val preferredCurrency: com.mmg.manahub.core.domain.model.PreferredCurrency = com.mmg.manahub.core.domain.model.PreferredCurrency.USD,
        // Friends
        val friendCount: Int = 0,
        val pendingFriendCount: Int = 0,
        // Gamification (ADR-002, Phase 0) — read-only hero ring + level
        /** Null until the progression flow first emits; the migration seeds a level-1 row. */
        val progression: PlayerProgression? = null,
        /** Master gamification switch; when false the hero ring + level chip are hidden. */
        val gamificationEnabled: Boolean = true,
        // Quests (gamification Phase 2) — drive the Quests tab.
        /** Active daily + weekly quest board. Empty until the first emission. */
        val questBoard: QuestBoard = QuestBoard.empty,
        /** Daily-activity streak (count + freeze tokens). */
        val streak: StreakUiModel = StreakUiModel(current = 0, longest = 0, freezeTokens = 0),
        // Rewards / cosmetics (gamification Phase 3) — drive the Rewards tab + hero overlays.
        /** Every cosmetic grouped by kind, flagged owned/equipped. Empty until the first emission. */
        val rewardsBoard: RewardsBoard = RewardsBoard.EMPTY,
        /** The player's currently-equipped cosmetics (title/badges/frame/ring). */
        val equipped: EquippedCosmetics = EquippedCosmetics.NONE,
    ) {
        val winRate: Float get() = if (totalGames > 0) totalWins.toFloat() / totalGames else 0f
    }

    /** One-shot side effects for the Profile screen (e.g. quest-claim toasts). */
    sealed interface Event {
        /** A quest reward was successfully claimed; [xpAwarded] XP was granted. */
        data class QuestClaimed(val xpAwarded: Int) : Event

        /** A quest claim could not be completed (already claimed, not completed, or not found). */
        data object QuestClaimFailed : Event

        /** The player tried to equip a 4th badge; the cap is [EquippedCosmetics.MAX_EQUIPPED_BADGES]. */
        data class BadgeCapReached(val maxBadges: Int) : Event
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Buffered Channel (not a nullable StateFlow): a StateFlow would equality-collapse repeated
    // identical claim results and drop events while the lifecycle is paused.
    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private val _prefsState = MutableStateFlow(PreferencesState())
    val prefsState: StateFlow<PreferencesState> = _prefsState.asStateFlow()

    init {
        // ── Preferences ───────────────────────────────────────────────────────
        userPreferencesDataStore.avatarUrlFlow
            .onEach { url -> _uiState.update { it.copy(avatarUrl = url) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        userPreferencesDataStore.preferencesFlow
            .onEach { prefs ->
                _uiState.update { it.copy(preferredCurrency = prefs.preferredCurrency) }
            }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)


        // ── Gamification (ADR-002, Phase 0) ─────────────────────────────────────
        gamificationRepository.observeProgression()
            .onEach { progression -> _uiState.update { it.copy(progression = progression) } }
            .catch { /* ignore — hero falls back to no ring */ }
            .launchIn(viewModelScope)

        // ── Achievements (ADR-002, Phase 1) ─────────────────────────────────────
        // Single source of truth: the gamification repo joins the catalog with persisted progress.
        // Replaces the old CheckAchievementsUseCase + NOW-merge workaround (unlockedAt is now a real,
        // persisted epoch-millis stamped once by the evaluator/backfill).
        gamificationRepository.observeAchievements()
            .onEach { achievements -> _uiState.update { it.copy(achievements = achievements) } }
            .catch { /* ignore — achievements section stays empty */ }
            .launchIn(viewModelScope)

        userPreferencesDataStore.gamificationEnabledFlow
            .onEach { enabled -> _uiState.update { it.copy(gamificationEnabled = enabled) } }
            .catch { /* ignore — default keeps gamification visible */ }
            .launchIn(viewModelScope)

        // ── Quests (ADR-002, Phase 2) ───────────────────────────────────────────
        gamificationRepository.observeActiveQuests()
            .onEach { board -> _uiState.update { it.copy(questBoard = board) } }
            .catch { /* ignore — Quests tab falls back to its empty state */ }
            .launchIn(viewModelScope)

        gamificationRepository.observeDailyActivityStreak()
            .onEach { streak -> _uiState.update { it.copy(streak = streak) } }
            .catch { /* ignore — streak header falls back to a zeroed value */ }
            .launchIn(viewModelScope)

        // ── Rewards / cosmetics (ADR-002, Phase 3) ──────────────────────────────
        gamificationRepository.observeRewards()
            .onEach { board -> _uiState.update { it.copy(rewardsBoard = board) } }
            .catch { /* ignore — Rewards tab falls back to its empty state */ }
            .launchIn(viewModelScope)

        gamificationRepository.observeEquippedCosmetics()
            .onEach { equipped -> _uiState.update { it.copy(equipped = equipped) } }
            .catch { /* ignore — hero falls back to no equipped cosmetics */ }
            .launchIn(viewModelScope)

        // ── Collection stats ──────────────────────────────────────────────────
        userPreferencesDataStore.preferencesFlow
            .map { it.preferredCurrency }
            .distinctUntilChanged()
            .flatMapLatest { currency ->
                statsRepo.observeCollectionStats(currency)
                    .catch { _uiState.update { it.copy(isLoading = false) } }
            }
            .onEach { stats ->
                _uiState.update {
                    it.copy(
                        collectionStats = stats,
                        isLoading = false,
                        favouriteColor = stats.computeFavouriteColor(),
                        mostValuableColor = stats.computeMostValuableColor(),
                    )
                }
            }
            .launchIn(viewModelScope)

        // ── Game stats ────────────────────────────────────────────────────────
        gameSessionRepo.observeTotalGames()
            .onEach { n -> _uiState.update { it.copy(totalGames = n) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        // ── Resolved Player Name (Auth > Local) ─────────────────────────────
        val resolvedNameFlow = userPreferencesDataStore.playerNameFlow
            .distinctUntilChanged()

        resolvedNameFlow
            .onEach { name -> _uiState.update { it.copy(playerName = name) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        resolvedNameFlow
            .flatMapLatest { name -> gameSessionRepo.observeWins(name) }
            .onEach { wins -> _uiState.update { it.copy(totalWins = wins) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        gameSessionRepo.observeAvgLifeOnWin()
            .onEach { v -> _uiState.update { it.copy(avgLifeOnWin = v ?: 0.0) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        gameSessionRepo.observeAvgLifeOnLoss()
            .onEach { v -> _uiState.update { it.copy(avgLifeOnLoss = v ?: 0.0) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        resolvedNameFlow
            .flatMapLatest { name -> gameSessionRepo.observeCurrentStreak(name) }
            .onEach { streak -> _uiState.update { it.copy(currentStreak = streak) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        gameSessionRepo.observeFavoriteMode()
            .onEach { mc -> _uiState.update { it.copy(favoriteMode = mc?.mode ?: "") } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        gameSessionRepo.observeAvgDurationMs()
            .onEach { v -> _uiState.update { it.copy(avgDurationMs = v ?: 0.0) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        gameSessionRepo.observeMostFrequentElimination()
            .onEach { ec ->
                _uiState.update {
                    it.copy(
                        mostFrequentElimination = ec?.eliminationReason ?: ""
                    )
                }
            }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        resolvedNameFlow
            .flatMapLatest { name -> gameSessionRepo.observeAvgWinTurn(name) }
            .onEach { v -> _uiState.update { it.copy(avgWinTurn = v ?: 0.0) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        gameSessionRepo.observeDeckStats()
            .onEach { ds -> _uiState.update { it.copy(deckStats = ds.sortedByDescending { it.wins }) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        gameSessionRepo.observeRecentSessions(5)
            .onEach { sessions -> _uiState.update { it.copy(recentSessions = sessions) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        // ── Survey insights ───────────────────────────────────────────────────
        surveyAnswerDao.observeSurveyCount()
            .onEach { n -> _uiState.update { it.copy(surveyCount = n) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        surveyAnswerDao.observeManaIssueCount()
            .onEach { n -> _uiState.update { it.copy(manaIssueCount = n) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        surveyAnswerDao.observeAvgHandRating()
            .onEach { v -> _uiState.update { it.copy(avgHandRating = v ?: 0.0) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        surveyAnswerDao.observeFavoriteWinStyle()
            .onEach { ac -> _uiState.update { it.copy(favoriteWinStyle = ac?.answer ?: "") } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        // ── Friends ───────────────────────────────────────────────────────────
        friendRepository.observeFriendCount()
            .onEach { count -> _uiState.update { it.copy(friendCount = count) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        friendRepository.observePendingCount()
            .onEach { count -> _uiState.update { it.copy(pendingFriendCount = count) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        // ── Refresh Friends & Requests ───────────────────────────────────────
        // Load counts whenever the session becomes authenticated.
        authRepository.sessionState
            .onEach { session ->
                if (session is SessionState.Authenticated) {
                    val userId = session.user.id
                    // Refresh friends and requests to ensure the UI shows up-to-date counts
                    // after login or when returning to the profile screen.
                    friendRepository.refreshFriends(userId)
                    friendRepository.refreshRequests(userId)
                }
            }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        // ── Derived: play style ───────────────────────────────────────────────
        _uiState
            .map { s -> Triple(s.avgWinTurn, s.favoriteMode, s.mostFrequentElimination) }
            .distinctUntilChanged()
            .onEach { (avgWinTurn, _, favoriteElim) ->
                _uiState.update { it.copy(playStyle = detectPlayStyle(avgWinTurn, favoriteElim)) }
            }
            .launchIn(viewModelScope)
    }

    // ── Public actions ────────────────────────────────────────────────────────

    fun savePlayerName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val previousName = _uiState.value.playerName
        _uiState.update { it.copy(playerName = trimmed) }
        viewModelScope.launch {
            try {
                userPreferencesDataStore.savePlayerName(trimmed)
            } catch (e: Exception) {
                // DataStore write failed — roll back the optimistic update so
                // the UI stays consistent with what is actually persisted.
                _uiState.update { it.copy(playerName = previousName) }
            }
        }
    }


    /**
     * Claims a completed quest's XP reward (gamification Phase 2). Delegates to the idempotent
     * repository path and emits a one-shot [Event] for the UI to surface as a toast. The reactive
     * [observeActiveQuests] flow re-emits with the CLAIMED status, so no optimistic UI mutation is
     * needed here.
     */
    fun claimQuest(instanceId: String) {
        viewModelScope.launch {
            val result = runCatching { gamificationRepository.claimQuest(instanceId) }
                .getOrElse { ClaimResult.NotFound }
            val event = when (result) {
                is ClaimResult.Claimed -> Event.QuestClaimed(result.xpAwarded)
                ClaimResult.AlreadyClaimed,
                ClaimResult.NotCompleted,
                ClaimResult.NotFound,
                -> Event.QuestClaimFailed
            }
            _events.send(event)
        }
    }

    // ── Rewards / cosmetics actions (gamification Phase 3) ──────────────────────

    /**
     * Equips [reward], routing by its [RewardUiModel.kind]. Single-slot kinds (TITLE / AVATAR_FRAME /
     * LEVEL_RING_STYLE) replace the current selection. BADGE is multi-slot, capped at
     * [EquippedCosmetics.MAX_EQUIPPED_BADGES]: when the cap is already reached the equip is rejected and
     * a [Event.BadgeCapReached] one-shot is emitted (the user must unequip a badge first). Unowned
     * cosmetics are additionally guarded at the repository layer, so a race can never equip an
     * unearned item.
     */
    fun onEquip(reward: RewardUiModel) {
        when (reward.kind) {
            UnlockableKind.TITLE -> viewModelScope.launch {
                runCatching { gamificationRepository.equipTitle(reward.id) }
            }

            UnlockableKind.AVATAR_FRAME -> viewModelScope.launch {
                runCatching { gamificationRepository.equipAvatarFrame(reward.id) }
            }

            UnlockableKind.LEVEL_RING_STYLE -> viewModelScope.launch {
                runCatching { gamificationRepository.equipLevelRingStyle(reward.id) }
            }

            UnlockableKind.BADGE -> {
                val current = _uiState.value.equipped.badgeIds
                if (reward.id in current) return // already equipped — no-op
                if (current.size >= EquippedCosmetics.MAX_EQUIPPED_BADGES) {
                    viewModelScope.launch {
                        _events.send(Event.BadgeCapReached(EquippedCosmetics.MAX_EQUIPPED_BADGES))
                    }
                    return
                }
                val next = current + reward.id
                viewModelScope.launch { runCatching { gamificationRepository.equipBadges(next) } }
            }
        }
    }

    /**
     * Unequips [reward], routing by kind. Single-slot kinds clear the slot (pass null); BADGE removes
     * only [reward] from the equipped list, preserving the others.
     */
    fun onUnequip(reward: RewardUiModel) {
        when (reward.kind) {
            UnlockableKind.TITLE -> viewModelScope.launch {
                runCatching { gamificationRepository.equipTitle(null) }
            }

            UnlockableKind.AVATAR_FRAME -> viewModelScope.launch {
                runCatching { gamificationRepository.equipAvatarFrame(null) }
            }

            UnlockableKind.LEVEL_RING_STYLE -> viewModelScope.launch {
                runCatching { gamificationRepository.equipLevelRingStyle(null) }
            }

            UnlockableKind.BADGE -> {
                val next = _uiState.value.equipped.badgeIds.filterNot { it == reward.id }
                viewModelScope.launch { runCatching { gamificationRepository.equipBadges(next) } }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun detectPlayStyle(avgWinTurn: Double, favoriteElim: String): PlayStyle = when {
        avgWinTurn in 1.0..7.0 -> PlayStyle.AGGRO
        favoriteElim == "COMMANDER_DAMAGE" -> PlayStyle.MIDRANGE
        avgWinTurn > 12.0 -> PlayStyle.CONTROL
        else -> PlayStyle.BALANCED
    }

    private fun CollectionStats.computeFavouriteColor(): String? =
        byColor
            .filterKeys { it != MtgColor.COLORLESS }
            .maxByOrNull { it.value }
            ?.key
            ?.name  // "W","U","B","R","G"

    private fun CollectionStats.computeMostValuableColor(): String? {
        val identity = mostValuableCards.firstOrNull()?.colorIdentity ?: return null
        val parsed = identity
            .removeSurrounding("[", "]").split(",")
            .map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
        return when {
            parsed.isEmpty() -> "C"
            parsed.size > 1 -> "M"
            else -> parsed.first()
        }
    }

}
