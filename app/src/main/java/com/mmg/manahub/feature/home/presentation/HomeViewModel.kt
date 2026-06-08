package com.mmg.manahub.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.CollectionStats
import com.mmg.manahub.core.domain.model.DeckSummary
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.GameSessionRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.domain.repository.TournamentRepository
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.feature.draft.domain.model.DraftState
import com.mmg.manahub.feature.draft.domain.model.DraftStatus
import com.mmg.manahub.feature.draft.domain.repository.DraftSimRepository
import com.mmg.manahub.feature.news.domain.usecase.GetNewsFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.mmg.manahub.feature.news.domain.model.NewsItem as DomainNewsItem

/**
 * Drives the free-first Home dashboard.
 *
 * It composes existing repository flows (stats, decks, games, draft, tournaments,
 * news, auth, preferences) into a single [HomeUiState]. It introduces no new
 * network calls for startup and no new persistence tables.
 *
 * The live in-memory active-game state is NOT injected here (the GameViewModel is
 * activity-scoped); [com.mmg.manahub.app.navigation.AppNavGraph] passes it into the
 * screen instead. This ViewModel therefore derives the hero from draft/summary
 * data, and the screen overrides the hero with an active game when one is running.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userPrefsDataStore: UserPreferencesDataStore,
    private val statsRepository: StatsRepository,
    private val deckRepository: DeckRepository,
    private val gameSessionRepository: GameSessionRepository,
    private val draftSimRepository: DraftSimRepository,
    private val tournamentRepository: TournamentRepository,
    private val authRepository: AuthRepository,
    private val getNewsFeedUseCase: GetNewsFeedUseCase,
) : ViewModel() {

    /**
     * Externally-triggered ACTION_REQUIRED nudge (highest priority). Set when the
     * user attempts an account-gated action (e.g. Friends/Trades) while signed out.
     * Cleared on dismissal or successful authentication.
     */
    private val actionRequiredMessage = MutableStateFlow<String?>(null)

    // ── Derived source flows ────────────────────────────────────────────────────

    private val currencyFlow: StateFlow<PreferredCurrency> =
        userPrefsDataStore.preferredCurrencyFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreferredCurrency.USD)

    private val collectionStatsFlow =
        currencyFlow.flatMapLatest { currency ->
            statsRepository.observeCollectionStats(currency)
        }

    // Shared hot flow: catches any DB/mapping error so a news failure never terminates uiState,
    // and shares the single DAO subscription across reconnects (WhileSubscribed keeps it alive
    // for the same 5-second window as the top-level combine).
    private val recentNewsFlow: StateFlow<List<NewsItem>> =
        getNewsFeedUseCase()
            .map { items -> items.take(MAX_NEWS).map { it.toHomeNewsItem() } }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val uiState: StateFlow<HomeUiState> = run {
        // combine() caps at a handful of args, so group inputs into intermediate flows.
        val libraryFlow = combine(
            collectionStatsFlow,
            deckRepository.observeAllDeckSummaries(),
            currencyFlow,
        ) { stats, decks, currency ->
            LibrarySnapshot(stats = stats, decks = decks, currency = currency)
        }

        val activityFlow = combine(
            gameSessionRepository.observeTotalGames(),
            draftSimRepository.observeActiveSession(),
            tournamentRepository.observeTournaments(),
        ) { totalGames, draft, tournaments ->
            ActivitySnapshot(
                totalGames = totalGames,
                activeDraft = draft,
                activeTournaments = tournaments.count { it.status == "ACTIVE" || it.status == "SETUP" },
            )
        }

        val accountFlow = combine(
            authRepository.sessionState,
            userPrefsDataStore.isNudgeCoolingDown(),
            actionRequiredMessage,
            userPrefsDataStore.avatarUrlFlow,
        ) { session, coolingDown, actionRequired, avatarUrl ->
            AccountSnapshot(
                isAuthenticated = session is SessionState.Authenticated,
                isCoolingDown = coolingDown,
                actionRequiredMessage = actionRequired,
                avatarUrl = avatarUrl,
            )
        }

        // Combine the five non-news inputs (all distinct types → typed 5-arg overload),
        // then fold in the news flow with a 2-arg combine. This avoids the vararg
        // combine, which would erase the element types to Array<Any?>.
        val coreFlow = combine(
            libraryFlow,
            activityFlow,
            accountFlow,
            userPrefsDataStore.observeQuickStartActions(),
            userPrefsDataStore.playerNameFlow,
        ) { library, activity, account, quickStart, playerName ->
            CoreSnapshot(library, activity, account, quickStart, playerName)
        }

        combine(coreFlow, recentNewsFlow) { core, news ->
            buildUiState(
                library = core.library,
                activity = core.activity,
                account = core.account,
                quickStart = core.quickStart,
                playerName = core.playerName,
                news = news,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(isLoading = true, hero = HomeHeroState.Loading),
        )
    }

    /** Public UI state. */
    val state: StateFlow<HomeUiState> get() = uiState

    init {
        // Clear any pending account-required nudge once the user is authenticated.
        authRepository.sessionState
            .onEach { session ->
                if (session is SessionState.Authenticated) actionRequiredMessage.value = null
            }
            .launchIn(viewModelScope)
    }

    // ── Public intents ──────────────────────────────────────────────────────────

    /** Persists a newly chosen set of exactly four Quick Start actions. */
    fun saveQuickStartActions(actions: List<QuickStartAction>) {
        viewModelScope.launch {
            userPrefsDataStore.saveQuickStartActions(actions)
        }
    }

    /** Dismisses the current account nudge, starting its 48-hour cooldown. */
    fun dismissAccountNudge() {
        actionRequiredMessage.value = null
        viewModelScope.launch {
            userPrefsDataStore.dismissAccountNudge()
        }
    }

    /**
     * Raises a high-priority ACTION_REQUIRED nudge. Call when the user taps an
     * account-gated entry point (Friends/Trades) while unauthenticated.
     */
    fun triggerActionRequiredNudge(message: String) {
        actionRequiredMessage.value = message
    }

    // ── Reduction ─────────────────────────────────────────────────────────────

    private fun buildUiState(
        library: LibrarySnapshot,
        activity: ActivitySnapshot,
        account: AccountSnapshot,
        quickStart: List<QuickStartAction>,
        playerName: String,
        news: List<NewsItem>,
    ): HomeUiState {
        val stats = library.stats
        val deckCount = library.decks.size

        val libraryStats = LibraryStats(
            uniqueCards = stats.uniqueCards,
            deckCount = deckCount,
            estimatedValueDisplay = PriceFormatter.formatFromScryfall(
                priceUsd = stats.totalValueUsd,
                priceEur = stats.totalValueEur,
                preferredCurrency = library.currency,
            ),
        )

        val hero = resolveHero(activity, playerName)
        val continueItems = buildContinueItems(activity, library.decks)
        val nudge = resolveNudge(account, stats, deckCount, activity.totalGames)

        return HomeUiState(
            isLoading = false,
            hero = hero,
            quickStartActions = quickStart,
            continueItems = continueItems,
            libraryStats = libraryStats,
            recentNews = news,
            accountNudge = nudge,
            isAuthenticated = account.isAuthenticated,
            avatarUrl = account.avatarUrl,
        )
    }

    /**
     * Hero priority (active game is layered on at the screen level since it lives
     * in the activity-scoped GameViewModel): active draft > returning summary >
     * welcome.
     */
    private fun resolveHero(activity: ActivitySnapshot, playerName: String): HomeHeroState {
        val draft = activity.activeDraft
        if (draft != null && draft.status == DraftStatus.DRAFTING) {
            return HomeHeroState.ActiveDraft(setName = draft.config.setCode.uppercase())
        }
        return if (activity.totalGames > 0 && playerName.isNotBlank()) {
            HomeHeroState.Summary(playerName = playerName, totalGames = activity.totalGames)
        } else if (activity.totalGames > 0) {
            HomeHeroState.Summary(playerName = "Wizard", totalGames = activity.totalGames)
        } else {
            HomeHeroState.Welcome
        }
    }

    private fun buildContinueItems(
        activity: ActivitySnapshot,
        decks: List<DeckSummary>,
    ): List<ContinueItem> = buildList {
        activity.activeDraft?.let { draft ->
            if (draft.status == DraftStatus.DRAFTING) {
                add(
                    ContinueItem(
                        id = draft.config.setCode,
                        label = "Continue draft",
                        subtitle = "Pack ${draft.round}, pick ${draft.pickNumber}",
                        type = ContinueType.DRAFT,
                    )
                )
            }
        }
        if (activity.activeTournaments > 0) {
            add(
                ContinueItem(
                    id = "tournaments",
                    label = "Resume tournament",
                    subtitle = if (activity.activeTournaments == 1) {
                        "1 in progress"
                    } else {
                        "${activity.activeTournaments} in progress"
                    },
                    type = ContinueType.TOURNAMENT,
                )
            )
        }
        decks.firstOrNull()?.let { deck ->
            add(
                ContinueItem(
                    id = deck.id,
                    label = deck.name,
                    subtitle = "${deck.cardCount} cards",
                    type = ContinueType.DECK,
                )
            )
        }
    }

    /**
     * Selects the single active account nudge by priority. Returns null when the
     * user is authenticated, the cooldown is active, or no trigger fires.
     */
    private fun resolveNudge(
        account: AccountSnapshot,
        stats: CollectionStats,
        deckCount: Int,
        totalGames: Int,
    ): AccountNudge? {
        if (account.isAuthenticated) return null

        // 1. ACTION_REQUIRED bypasses the cooldown — the user just hit a hard wall.
        account.actionRequiredMessage?.let {
            return AccountNudge(message = it, trigger = NudgeTrigger.ACTION_REQUIRED)
        }

        if (account.isCoolingDown) return null

        // 2. SYNC_PENDING — no local "unsynced changes" signal is exposed yet.
        // TODO(home): surface a SYNC_PENDING nudge once a pending-changes flow exists.

        // 3. COLLECTION_MILESTONE
        if (stats.uniqueCards >= COLLECTION_MILESTONE) {
            return AccountNudge(
                message = "Back up your collection so it's safe across devices.",
                trigger = NudgeTrigger.COLLECTION_MILESTONE,
            )
        }
        // 4. DECK_MILESTONE
        if (deckCount >= DECK_MILESTONE) {
            return AccountNudge(
                message = "Sync your decks to keep them on every device.",
                trigger = NudgeTrigger.DECK_MILESTONE,
            )
        }
        // 5. GAME_MILESTONE
        if (totalGames >= GAME_MILESTONE) {
            return AccountNudge(
                message = "Keep your match history across devices.",
                trigger = NudgeTrigger.GAME_MILESTONE,
            )
        }
        return null
    }

    // ── Internal snapshots ──────────────────────────────────────────────────────

    private data class CoreSnapshot(
        val library: LibrarySnapshot,
        val activity: ActivitySnapshot,
        val account: AccountSnapshot,
        val quickStart: List<QuickStartAction>,
        val playerName: String,
    )

    private data class LibrarySnapshot(
        val stats: CollectionStats,
        val decks: List<DeckSummary>,
        val currency: PreferredCurrency,
    )

    private data class ActivitySnapshot(
        val totalGames: Int,
        val activeDraft: DraftState?,
        val activeTournaments: Int,
    )

    private data class AccountSnapshot(
        val isAuthenticated: Boolean,
        val isCoolingDown: Boolean,
        val actionRequiredMessage: String?,
        val avatarUrl: String? = null,
    )

    private companion object {
        const val MAX_NEWS = 3
        const val COLLECTION_MILESTONE = 10
        const val DECK_MILESTONE = 2
        const val GAME_MILESTONE = 3
    }
}

/** Maps the rich domain news model into the compact Home wrapper. */
private fun DomainNewsItem.toHomeNewsItem(): NewsItem =
    NewsItem(id = id, title = title, imageUrl = imageUrl)
