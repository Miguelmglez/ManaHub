package com.mmg.manahub.feature.playtest.presentation.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.data.local.mapper.toDomainCardList
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckWithCards
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.model.PlaytestEligibility
import com.mmg.manahub.core.model.PlaytestSetup
import com.mmg.manahub.feature.playtest.domain.usecase.CanPlaytestDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaytestSetupUiState(
    val isLoading: Boolean = true,
    val deckName: String = "",
    val deckFormat: String = "",
    val drawCount: Int = 7,
    /** "Cards to start the game" — target size of the FINAL kept opening hand. */
    val startCount: Int = 7,
    /**
     * Upper bound for [drawCount], derived from the deck's own mainboard size (minus the
     * commander, mirroring [BuildLibraryUseCase]'s own exclusion) so the stepper can never let
     * the user pick more cards than the deck actually has. Defaults to the pre-load hardcoded
     * cap of 10 and is recomputed once the deck loads.
     */
    val maxDrawCount: Int = 10,
    val eligibility: PlaytestEligibility? = null,
    val commanderCard: Card? = null,
    val deckImageUrl: String? = null,
    val colorIdentitySymbols: List<String> = emptyList(),
    val mainboardCount: Int = 0,
    val errorMessage: String? = null,
)

sealed class PlaytestSetupEvent {
    /** Navigate to the hand screen with the resolved setup. */
    data class NavigateToHand(val setup: PlaytestSetup) : PlaytestSetupEvent()
}

class PlaytestSetupViewModel(
    savedStateHandle: SavedStateHandle,
    private val deckRepository: DeckRepository,
    private val cardDao: CardDao,
    private val canPlaytestDeckUseCase: CanPlaytestDeckUseCase,
    private val inferDeckIdentityUseCase: InferDeckIdentityUseCase,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val deckId: String = checkNotNull(savedStateHandle["deckId"])

    private val _uiState = MutableStateFlow(PlaytestSetupUiState())
    val uiState: StateFlow<PlaytestSetupUiState> = _uiState.asStateFlow()

    // One-shot navigation events use a buffered Channel (never a nullable StateFlow): a
    // StateFlow equality-collapses repeated equal events and drops them on lifecycle pause,
    // which would silently swallow a second NavigateToHand. Collected via
    // LaunchedEffect(Unit){ vm.events.collect { } } in the screen.
    private val _events = Channel<PlaytestSetupEvent>(Channel.BUFFERED)
    val events: Flow<PlaytestSetupEvent> = _events.receiveAsFlow()

    private var resolvedDeckWithCards: DeckWithCards? = null
    private var resolvedCommanderCard: Card? = null

    /**
     * Guard against double-taps on "Draw Opening Hand". Set when the first tap emits the
     * navigation event and never reset within this ViewModel's lifetime: navigation is a
     * one-way transition, the destination owns a fresh ViewModel, and on return to setup
     * (Back) a new ViewModel instance is created — so a stale `true` cannot wrongly block a
     * legitimate re-entry.
     */
    private var isNavigating = false

    init {
        loadDeck()
    }

    private fun loadDeck() {
        viewModelScope.launch {
            combine(
                deckRepository.observeDeckWithCards(deckId),
                deckRepository.observeAllDeckSummaries()
            ) { deckWithCards, summaries ->
                deckWithCards to summaries.find { it.id == deckId }
            }.collect { (deckWithCards, summary) ->
                if (deckWithCards == null) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Deck not found")
                    }
                    return@collect
                }

                resolvedDeckWithCards = deckWithCards
                val mainboardCount = deckWithCards.mainboard.sumOf { it.quantity }
                val eligibility = canPlaytestDeckUseCase(deckWithCards, mainboardCount)
                FirebaseCrashlytics.getInstance().apply {
                    log("playtest_setup_loaded: deckId=$deckId format=${deckWithCards.deck.format} mainboardCount=$mainboardCount eligibility=${if (eligibility is PlaytestEligibility.Eligible) "eligible" else "ineligible"}")
                    setCustomKey("playtest_deck_id", deckId)
                    setCustomKey("playtest_format", deckWithCards.deck.format)
                }

                // Resolve commander card and identity if needed.
                val identityAndCommander = withContext(ioDispatcher) {
                    val commanderId = deckWithCards.deck.commanderCardId
                    val commander = if (commanderId != null) {
                        cardDao.getById(commanderId)?.toDomainCard()
                    } else null

                    // Resolve all mainboard cards for identity.
                    val mainboardIds = deckWithCards.mainboard.map { it.scryfallId }
                    val mainboardCards = cardDao.getByIds(mainboardIds).toDomainCardList()

                    val identity = inferDeckIdentityUseCase(mainboardCards)

                    identity to commander
                }

                val (identity, commanderCard) = identityAndCommander
                val deckImageUrl = summary?.coverImageUrl

                val commanderId = deckWithCards.deck.commanderCardId
                val commanderInMainboard = commanderId == null ||
                    deckWithCards.mainboard.any { it.scryfallId == commanderId }
                val adjustedEligibility = if (!commanderInMainboard) {
                    FirebaseCrashlytics.getInstance().apply {
                        log("playtest_commander_missing_from_mainboard: deckId=$deckId commanderId=$commanderId")
                        setCustomKey("playtest_deck_id", deckId)
                        setCustomKey("playtest_format", deckWithCards.deck.format)
                        recordException(
                            IllegalStateException(
                                "[PlaytestSetup] Commander id=$commanderId not found in mainboard slots for deckId=$deckId"
                            )
                        )
                    }
                    PlaytestEligibility.Ineligible(
                        reason = "Commander card is not present in the mainboard. " +
                            "Re-add the commander to the deck before playtesting."
                    )
                } else {
                    eligibility
                }

                resolvedCommanderCard = commanderCard
                // Mirrors BuildLibraryUseCase's own commander exclusion: the commander (when
                // present) never sits in the drawable library, so the stepper's ceiling must
                // exclude it too — otherwise "Cards to draw" could exceed the deck's actual
                // drawable card count. Coerced to [1, 10] to preserve the original stepper range.
                val maxDrawCount = (mainboardCount - (if (commanderCard != null) 1 else 0))
                    .coerceIn(1, 10)
                _uiState.update { current ->
                    val clampedDrawCount = current.drawCount.coerceIn(1, maxDrawCount)
                    current.copy(
                        isLoading             = false,
                        deckName              = deckWithCards.deck.name,
                        deckFormat            = deckWithCards.deck.format,
                        drawCount             = clampedDrawCount,
                        startCount            = current.startCount.coerceIn(1, clampedDrawCount),
                        maxDrawCount          = maxDrawCount,
                        eligibility           = adjustedEligibility,
                        commanderCard         = commanderCard,
                        deckImageUrl          = deckImageUrl,
                        colorIdentitySymbols  = identity.colorIdentity.map { c -> c.symbol },
                        mainboardCount        = mainboardCount,
                    )
                }
            }
        }
    }

    fun resetNavigation() {
        isNavigating = false
    }

    fun setDrawCount(count: Int) {
        _uiState.update { current ->
            val newDrawCount = count.coerceIn(1, current.maxDrawCount)
            current.copy(
                drawCount  = newDrawCount,
                // startCount can never exceed the (possibly lowered) drawCount.
                startCount = current.startCount.coerceAtMost(newDrawCount),
            )
        }
    }

    /** "Cards to start the game" — bounded [1, drawCount] (can never exceed cards drawn). */
    fun setStartCount(count: Int) {
        _uiState.update { it.copy(startCount = count.coerceIn(1, it.drawCount)) }
    }

    fun onDrawHand() {
        if (isNavigating) return
        val state = _uiState.value
        val deck = resolvedDeckWithCards?.deck ?: return

        // Setup screen keeps showing the ineligibility warning banner, but no longer blocks the
        // user from running the test anyway (Deck Playtest adjustments plan, Part A). The
        // bypass is surfaced in telemetry so ineligible-but-run sessions stay visible.
        val wasIneligible = state.eligibility !is PlaytestEligibility.Eligible
        isNavigating = true
        FirebaseCrashlytics.getInstance().apply {
            log("playtest_draw_hand_initiated: deckId=$deckId format=${deck.format} drawCount=${state.drawCount} startCount=${state.startCount}")
            setCustomKey("playtest_deck_id", deckId)
            setCustomKey("playtest_format", deck.format)
            setCustomKey("playtest_draw_count", state.drawCount)
            setCustomKey("playtest_bypassed_ineligible", wasIneligible)
        }
        val setup = PlaytestSetup(
            deckId         = deckId,
            deckName       = deck.name,
            deckFormat     = deck.format,
            drawCount      = state.drawCount,
            startCount     = state.startCount,
            isOnThePlay    = true,
            commanderCard  = resolvedCommanderCard,
        )
        viewModelScope.launch { _events.send(PlaytestSetupEvent.NavigateToHand(setup)) }
    }
}
