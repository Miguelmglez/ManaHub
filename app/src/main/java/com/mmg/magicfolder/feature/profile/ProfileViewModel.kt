package com.mmg.magicfolder.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.data.local.LanguagePreference
import com.mmg.magicfolder.core.data.local.dao.DeckStatsRow
import com.mmg.magicfolder.core.data.local.dao.SurveyAnswerDao
import com.mmg.magicfolder.core.data.local.entity.GameSessionWithPlayers
import com.mmg.magicfolder.core.domain.model.Achievement
import com.mmg.magicfolder.core.domain.model.CollectionStats
import com.mmg.magicfolder.core.domain.model.MtgColor
import com.mmg.magicfolder.core.domain.model.Rarity
import com.mmg.magicfolder.core.domain.repository.GameSessionRepository
import com.mmg.magicfolder.core.domain.repository.StatsRepository
import com.mmg.magicfolder.core.domain.usecase.achievements.AchievementStats
import com.mmg.magicfolder.core.domain.usecase.achievements.CheckAchievementsUseCase
import com.mmg.magicfolder.core.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

// ── Enums ─────────────────────────────────────────────────────────────────────

enum class PlayStyle(val label: String, val icon: String) {
    AGGRO("Aggressor",  "⚔"),
    CONTROL("Strategist", "🛡"),
    MIDRANGE("Midrange",  "♟"),
    BALANCED("Balanced",  "⚖"),
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val statsRepo:             StatsRepository,
    private val gameSessionRepo:       GameSessionRepository,
    private val surveyAnswerDao:       SurveyAnswerDao,
    private val langPref:              LanguagePreference,
    private val checkAchievementsUseCase: CheckAchievementsUseCase,
) : ViewModel() {

    data class UiState(
        val playerName:              String     = "Player 1",
        val currentTheme:            AppTheme   = AppTheme.NeonVoid,
        val playStyle:               PlayStyle  = PlayStyle.BALANCED,
        val isLoading:               Boolean    = true,
        // Collection
        val collectionStats:         CollectionStats?           = null,
        val favouriteColor:          String?                    = null,
        val mostValuableColor:       String?                    = null,
        // Game stats
        val totalGames:              Int    = 0,
        val totalWins:               Int    = 0,
        val avgLifeOnWin:            Double = 0.0,
        val avgLifeOnLoss:           Double = 0.0,
        val currentStreak:           Int    = 0,
        val favoriteMode:            String = "",
        val avgDurationMs:           Double = 0.0,
        val mostFrequentElimination: String = "",
        val avgWinTurn:              Double = 0.0,
        // Survey insights
        val surveyCount:             Int    = 0,
        val manaIssueCount:          Int    = 0,
        val avgHandRating:           Double = 0.0,
        val favoriteWinStyle:        String = "",
        // Decks + sessions
        val deckStats:               List<DeckStatsRow>          = emptyList(),
        val recentSessions:          List<GameSessionWithPlayers> = emptyList(),
        // Achievements
        val achievements:            List<Achievement>            = emptyList(),
    ) {
        val winRate: Float get() = if (totalGames > 0) totalWins.toFloat() / totalGames else 0f
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // ── Preferences ───────────────────────────────────────────────────────
        langPref.playerNameFlow
            .onEach { name -> _uiState.update { it.copy(playerName = name) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        langPref.themeFlow
            .onEach { theme -> _uiState.update { it.copy(currentTheme = theme) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        // ── Collection stats ──────────────────────────────────────────────────
        statsRepo.observeCollectionStats()
            .onEach { stats ->
                _uiState.update {
                    it.copy(
                        collectionStats    = stats,
                        isLoading          = false,
                        favouriteColor     = stats.computeFavouriteColor(),
                        mostValuableColor  = stats.computeMostValuableColor(),
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

        langPref.playerNameFlow
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

        langPref.playerNameFlow
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
            .onEach { ec -> _uiState.update { it.copy(mostFrequentElimination = ec?.eliminationReason ?: "") } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        langPref.playerNameFlow
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

        // ── Derived: play style ───────────────────────────────────────────────
        _uiState
            .map { s -> Triple(s.avgWinTurn, s.favoriteMode, s.mostFrequentElimination) }
            .distinctUntilChanged()
            .onEach { (avgWinTurn, _, favoriteElim) ->
                _uiState.update { it.copy(playStyle = detectPlayStyle(avgWinTurn, favoriteElim)) }
            }
            .launchIn(viewModelScope)

        // ── Derived: achievements ─────────────────────────────────────────────
        _uiState
            .map { s -> buildAchievementStats(s) }
            .distinctUntilChanged()
            .onEach { stats ->
                _uiState.update { it.copy(achievements = checkAchievementsUseCase(stats)) }
            }
            .launchIn(viewModelScope)
    }

    // ── Public actions ────────────────────────────────────────────────────────

    fun savePlayerName(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { langPref.savePlayerName(name.trim()) }
    }

    fun selectTheme(theme: AppTheme) {
        viewModelScope.launch {
            langPref.saveTheme(theme)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun detectPlayStyle(avgWinTurn: Double, favoriteElim: String): PlayStyle = when {
        avgWinTurn in 1.0..7.0             -> PlayStyle.AGGRO
        favoriteElim == "COMMANDER_DAMAGE" -> PlayStyle.MIDRANGE
        avgWinTurn > 12.0                  -> PlayStyle.CONTROL
        else                               -> PlayStyle.BALANCED
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
            parsed.size > 1  -> "M"
            else             -> parsed.first()
        }
    }

    private fun buildAchievementStats(s: UiState): AchievementStats {
        val byRarity = s.collectionStats?.byRarity ?: emptyMap()
        val byColor  = s.collectionStats?.byColor  ?: emptyMap()
        return AchievementStats(
            totalGames          = s.totalGames,
            totalWins           = s.totalWins,
            winStreak           = s.currentStreak,
            totalCards          = s.collectionStats?.totalCards ?: 0,
            hasMythic           = (byRarity[Rarity.MYTHIC] ?: 0) > 0,
            deckCount           = s.collectionStats?.totalDecks ?: 0,
            surveyCount         = s.surveyCount,
            maxCardValue        = s.collectionStats?.mostValuableCards?.firstOrNull()?.priceUsd ?: 0.0,
            avgWinTurn          = s.avgWinTurn,
            favoriteElimination = s.mostFrequentElimination,
            distinctColorCount  = byColor.entries.count { (color) ->
                color != MtgColor.COLORLESS
            },
        )
    }
}
