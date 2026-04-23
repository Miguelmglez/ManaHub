package com.mmg.manahub.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.DeckStatsRow
import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
import com.mmg.manahub.core.data.local.entity.GameSessionWithPlayers
import com.mmg.manahub.core.domain.model.Achievement
import com.mmg.manahub.core.domain.model.CollectionStats
import com.mmg.manahub.core.domain.model.MtgColor
import com.mmg.manahub.core.domain.model.Rarity
import com.mmg.manahub.core.domain.repository.GameSessionRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.domain.usecase.achievements.AchievementStats
import com.mmg.manahub.core.domain.usecase.achievements.CheckAchievementsUseCase
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import com.mmg.manahub.feature.settings.PreferencesState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
    private val checkAchievementsUseCase: CheckAchievementsUseCase,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val friendRepository: FriendRepository,
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
        // Achievements
        val achievements: List<Achievement> = emptyList(),
        val preferredCurrency: com.mmg.manahub.core.domain.model.PreferredCurrency = com.mmg.manahub.core.domain.model.PreferredCurrency.USD,
        // Friends
        val friendCount: Int = 0,
        val pendingFriendCount: Int = 0,
    ) {
        val winRate: Float get() = if (totalGames > 0) totalWins.toFloat() / totalGames else 0f
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

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


        // ── Collection stats ──────────────────────────────────────────────────
        userPreferencesDataStore.preferencesFlow
            .map { it.preferredCurrency }
            .distinctUntilChanged()
            .flatMapLatest { currency ->
                statsRepo.observeCollectionStats(currency)
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
            .catch { _uiState.update { it.copy(isLoading = false) } }
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

        // ── Derived: play style ───────────────────────────────────────────────
        _uiState
            .map { s -> Triple(s.avgWinTurn, s.favoriteMode, s.mostFrequentElimination) }
            .distinctUntilChanged()
            .onEach { (avgWinTurn, _, favoriteElim) ->
                _uiState.update { it.copy(playStyle = detectPlayStyle(avgWinTurn, favoriteElim)) }
            }
            .launchIn(viewModelScope)

        // ── Derived: achievements ─────────────────────────────────────────────
        // Note: CheckAchievementsUseCase stamps unlocked achievements with NOW,
        // so we suppress re-invocation by comparing only the inputs (currency +
        // stats), not the output list which changes every millisecond.
        _uiState
            .map { s -> s.preferredCurrency to buildAchievementStats(s) }
            .distinctUntilChanged()
            .onEach { (currency, stats) ->
                val newAchievements = checkAchievementsUseCase(stats, currency)
                _uiState.update { current ->
                    // Preserve existing unlockedAt timestamps for achievements that
                    // were already unlocked — only stamp NOW for newly-unlocked ones.
                    val existingById = current.achievements.associateBy { it.id }
                    val merged = newAchievements.map { achievement ->
                        val existing = existingById[achievement.id]
                        if (achievement.isUnlocked && existing?.isUnlocked == true) {
                            // Already unlocked — keep the original timestamp.
                            achievement.copy(unlockedAt = existing.unlockedAt)
                        } else {
                            achievement
                        }
                    }
                    current.copy(achievements = merged)
                }
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

    private fun buildAchievementStats(s: UiState): AchievementStats {
        val byRarity = s.collectionStats?.byRarity ?: emptyMap()
        val byColor = s.collectionStats?.byColor ?: emptyMap()
        return AchievementStats(
            totalGames = s.totalGames,
            totalWins = s.totalWins,
            winStreak = s.currentStreak,
            totalCards = s.collectionStats?.totalCards ?: 0,
            hasMythic = (byRarity[Rarity.MYTHIC] ?: 0) > 0,
            deckCount = s.collectionStats?.totalDecks ?: 0,
            surveyCount = s.surveyCount,
            maxCardValue = s.collectionStats?.mostValuableCards?.firstOrNull()?.priceUsd ?: 0.0,
            avgWinTurn = s.avgWinTurn,
            favoriteElimination = s.mostFrequentElimination,
            distinctColorCount = byColor.entries.count { (color) ->
                color != MtgColor.COLORLESS
            },
        )
    }
}
