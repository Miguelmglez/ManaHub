package com.mmg.manahub.feature.stats.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.GameSessionDao
import com.mmg.manahub.core.data.local.entity.SurveyStatus
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.domain.model.MagicSet
import com.mmg.manahub.core.domain.model.MtgColor
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.collection.RefreshCollectionPricesUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionSetCodesUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val getStats:                 GetCollectionStatsUseCase,
    private val getSetCodes:              GetCollectionSetCodesUseCase,
    private val scryfallDataSource:       ScryfallRemoteDataSource,
    private val refreshPricesUseCase:     RefreshCollectionPricesUseCase,
    private val userPreferencesDataStore: UserPreferencesRepository,
    // UserPreferencesDataStore is injected directly (not via the interface) because
    // playerNameFlow is not part of the UserPreferencesRepository contract.
    // This mirrors the pattern used in ProfileViewModel and GameSetupViewModel.
    private val userPrefsStore:           UserPreferencesDataStore,
    private val gameSessionDao:           GameSessionDao,
    private val deckRepository:           DeckRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        observeCollectionStats()
        observeAvailableSets()
        observePreferredCurrency()
        observeLastPriceRefresh()
        observeGameStats()
    }

    // ── Collection tab observers ──────────────────────────────────────────────

    private fun observeCollectionStats() {
        viewModelScope.launch {
            combine(
                userPreferencesDataStore.preferredCurrencyFlow,
                _uiState.map { it.selectedColor }.distinctUntilChanged(),
                _uiState.map { it.selectedSet?.code }.distinctUntilChanged(),
            ) { currency, color, setCode -> Triple(currency, color, setCode) }
            .flatMapLatest { (currency, color, setCode) ->
                getStats(currency, color, setCode)
                    .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
            }
            .collect { stats ->
                _uiState.update { it.copy(stats = stats, isLoading = false) }
            }
        }
    }

    private fun observeAvailableSets() {
        viewModelScope.launch {
            combine(
                getSetCodes(),
                kotlinx.coroutines.flow.flow { emit(scryfallDataSource.getAllSets()) }
            ) { codes, allSets ->
                if (codes.isEmpty()) emptyList()
                else allSets.filter { it.code in codes }
            }.collect { availableSets ->
                _uiState.update { it.copy(availableSets = availableSets) }
            }
        }
    }

    private fun observePreferredCurrency() {
        viewModelScope.launch {
            userPreferencesDataStore.preferredCurrencyFlow.collect { currency ->
                _uiState.update { it.copy(currency = currency) }
            }
        }
    }

    private fun observeLastPriceRefresh() {
        viewModelScope.launch {
            userPreferencesDataStore.lastPriceRefreshFlow.collect { lastRefresh ->
                _uiState.update { it.copy(lastRefreshedAt = lastRefresh) }
            }
        }
    }

    // ── Games tab observer ────────────────────────────────────────────────────

    private fun observeGameStats() {
        viewModelScope.launch {
            // playerNameFlow is the canonical source for the app user's name.
            // It defaults to "Wizard" when no name has been set by the user.
            userPrefsStore.playerNameFlow.flatMapLatest { playerName ->
                combine(
                    gameSessionDao.observeTotalGames(),
                    gameSessionDao.observeWins(playerName),
                    gameSessionDao.observeAvgDurationMs(),
                    gameSessionDao.observeFavoriteMode(),
                    gameSessionDao.observeMostFrequentElimination(),
                    gameSessionDao.observePendingSurveyCount(),
                    gameSessionDao.observeAllSessionSummaries(),
                    gameSessionDao.observeDeckGameStats(playerName),
                    deckRepository.observeAllDecks(),
                ) { args ->
                    // combine with 9 flows uses the array variant
                    @Suppress("UNCHECKED_CAST")
                    val totalGames   = args[0] as Int
                    val wins         = args[1] as Int
                    val avgDuration  = args[2] as Double?
                    val favoriteMode = args[3] as com.mmg.manahub.core.data.local.dao.ModeCount?
                    val mostLoss     = args[4] as com.mmg.manahub.core.data.local.dao.EliminationCount?
                    val pending      = args[5] as Int
                    val summaries    = args[6] as List<com.mmg.manahub.core.data.local.dao.SessionSummary>
                    val deckStats    = args[7] as List<com.mmg.manahub.core.data.local.dao.DeckGameStatsRow>
                    val allDecks     = args[8] as List<com.mmg.manahub.core.domain.model.Deck>

                    val deckNameById = allDecks.associate { it.id to it.name }

                    val gameStats = GameStats(
                        totalGames       = totalGames,
                        wins             = wins,
                        winrate          = if (totalGames > 0) wins.toFloat() / totalGames else 0f,
                        avgDurationMs    = avgDuration?.toLong() ?: 0L,
                        favoriteMode     = favoriteMode?.mode,
                        mostFrequentLoss = mostLoss?.eliminationReason,
                        pendingSurveys   = pending,
                    )

                    val history = summaries.map { row ->
                        GameHistoryItem(
                            sessionId     = row.id,
                            playedAt      = row.playedAt,
                            mode          = row.mode,
                            durationMs    = row.durationMs,
                            winnerName    = row.winnerName,
                            isWin         = row.winnerName == playerName,
                            surveyStatus  = runCatching { SurveyStatus.valueOf(row.surveyStatus) }.getOrDefault(SurveyStatus.PENDING),
                            deckId        = row.deckId,
                            deckName      = row.deckId?.let { deckNameById[it] },
                        )
                    }

                    val deckPerf = deckStats.mapNotNull { row ->
                        val name = deckNameById[row.deckId] ?: return@mapNotNull null
                        if (row.totalGames == 0) return@mapNotNull null
                        DeckPerformance(
                            deckId     = row.deckId,
                            deckName   = name,
                            totalGames = row.totalGames,
                            wins       = row.wins,
                            winrate    = if (row.totalGames > 0) row.wins.toFloat() / row.totalGames else 0f,
                        )
                    }.sortedByDescending { it.totalGames }

                    Triple(gameStats, history, deckPerf)
                }
            }
            .catch { e -> android.util.Log.e("StatsViewModel", "Game stats pipeline failed", e) }
            .collect { (gameStats, history, deckPerf) ->
                _uiState.update {
                    it.copy(
                        hasGameStats     = gameStats.totalGames > 0,
                        gameStats        = gameStats,
                        sessionHistory   = history,
                        deckPerformance  = deckPerf,
                    )
                }
            }
        }
    }

    // ── Public actions ────────────────────────────────────────────────────────

    fun onColorSelected(color: MtgColor?) {
        _uiState.update { it.copy(selectedColor = if (_uiState.value.selectedColor == color) null else color) }
    }

    fun onSetSelected(set: MagicSet?) {
        _uiState.update { it.copy(selectedSet = set) }
    }

    fun onCurrencyToggle() {
        viewModelScope.launch {
            val next = if (_uiState.value.currency == com.mmg.manahub.core.domain.model.PreferredCurrency.USD)
                com.mmg.manahub.core.domain.model.PreferredCurrency.EUR
            else
                com.mmg.manahub.core.domain.model.PreferredCurrency.USD
            userPreferencesDataStore.setPreferredCurrency(next)
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    /** Switches the visible tab. */
    fun onTabSelected(tab: StatsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Permanently removes a game session (and its linked survey via FK cascade).
     * Runs on [Dispatchers.IO] as required by the Room suspend function.
     */
    fun deleteSession(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            gameSessionDao.deleteSession(sessionId)
        }
    }

    fun refreshPrices() {
        if (_uiState.value.isRefreshingPrices) return
        viewModelScope.launch {
            refreshPricesUseCase.invoke().collect { result ->
                when (result) {
                    is RefreshCollectionPricesUseCase.Result.Progress -> {
                        _uiState.update { it.copy(
                            isRefreshingPrices = true,
                            refreshProgress    = result.current to result.total,
                        )}
                    }
                    is RefreshCollectionPricesUseCase.Result.Success -> {
                        val now = System.currentTimeMillis()
                        userPreferencesDataStore.saveLastPriceRefresh(now)
                        val message = buildString {
                            append("Updated ${result.updatedCount} prices")
                            if (result.notFoundCount > 0)
                                append(" (${result.notFoundCount} not found)")
                        }
                        _uiState.update { it.copy(
                            isRefreshingPrices = false,
                            refreshProgress    = null,
                            lastRefreshedAt    = now,
                            refreshResult      = message,
                        )}
                    }
                    is RefreshCollectionPricesUseCase.Result.Error -> {
                        _uiState.update { it.copy(
                            isRefreshingPrices = false,
                            refreshProgress    = null,
                            refreshError       = result.message,
                        )}
                    }
                }
            }
        }
    }

    fun clearRefreshMessage() {
        _uiState.update { it.copy(refreshResult = null, refreshError = null) }
    }
}
