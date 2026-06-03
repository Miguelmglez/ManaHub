package com.mmg.manahub.feature.playtest.presentation.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DeckWithCards
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.feature.playtest.domain.model.PlaytestEligibility
import com.mmg.manahub.feature.playtest.domain.model.PlaytestSetup
import com.mmg.manahub.feature.playtest.domain.usecase.CanPlaytestDeckUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PlaytestSetupUiState(
    val isLoading: Boolean = true,
    val deckName: String = "",
    val deckFormat: String = "",
    val drawCount: Int = 7,
    val isOnThePlay: Boolean = true,
    val eligibility: PlaytestEligibility? = null,
    val commanderCard: Card? = null,
    val mainboardCount: Int = 0,
    val errorMessage: String? = null,
)

sealed class PlaytestSetupEvent {
    /** Navigate to the hand screen with the resolved setup. */
    data class NavigateToHand(val setup: PlaytestSetup) : PlaytestSetupEvent()
}

@HiltViewModel
class PlaytestSetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deckRepository: DeckRepository,
    private val cardDao: CardDao,
    private val canPlaytestDeckUseCase: CanPlaytestDeckUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val deckId: String = checkNotNull(savedStateHandle["deckId"])

    private val _uiState = MutableStateFlow(PlaytestSetupUiState())
    val uiState: StateFlow<PlaytestSetupUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<PlaytestSetupEvent?>(null)
    val events: StateFlow<PlaytestSetupEvent?> = _events.asStateFlow()

    private var resolvedDeckWithCards: DeckWithCards? = null
    private var resolvedCommanderCard: Card? = null

    /** Guard against double-taps on "Draw Opening Hand". */
    private var isNavigating = false

    init {
        loadDeck()
    }

    private fun loadDeck() {
        viewModelScope.launch {
            deckRepository.observeDeckWithCards(deckId).collect { deckWithCards ->
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

                // Resolve commander card if needed.
                // Invariant: for commander format, the commanderCardId must be present
                // in the mainboard slots. If it is set but missing from the mainboard,
                // the deck is misconfigured (BuildLibraryUseCase would build a 100-card
                // library while the command zone still shows the commander — double count).
                // Treat this as Ineligible rather than silently producing a corrupt session.
                val commanderCard = withContext(ioDispatcher) {
                    val commanderId = deckWithCards.deck.commanderCardId
                    if (commanderId != null) {
                        cardDao.getById(commanderId)?.toDomainCard()
                    } else null
                }

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
                _uiState.update {
                    it.copy(
                        isLoading      = false,
                        deckName       = deckWithCards.deck.name,
                        deckFormat     = deckWithCards.deck.format,
                        eligibility    = adjustedEligibility,
                        commanderCard  = commanderCard,
                        mainboardCount = mainboardCount,
                    )
                }
            }
        }
    }

    fun setDrawCount(count: Int) {
        _uiState.update { it.copy(drawCount = count.coerceIn(1, 10)) }
    }

    fun setOnThePlay(onThePlay: Boolean) {
        _uiState.update { it.copy(isOnThePlay = onThePlay) }
    }

    fun onDrawHand() {
        if (isNavigating) return
        val state = _uiState.value
        if (state.eligibility !is PlaytestEligibility.Eligible) return

        val deck = resolvedDeckWithCards?.deck ?: return
        isNavigating = true
        FirebaseCrashlytics.getInstance().apply {
            log("playtest_draw_hand_initiated: deckId=$deckId format=${deck.format} drawCount=${state.drawCount} onThePlay=${state.isOnThePlay}")
            setCustomKey("playtest_deck_id", deckId)
            setCustomKey("playtest_format", deck.format)
            setCustomKey("playtest_draw_count", state.drawCount)
            setCustomKey("playtest_on_the_play", state.isOnThePlay)
        }
        val setup = PlaytestSetup(
            deckId         = deckId,
            deckName       = deck.name,
            deckFormat     = deck.format,
            drawCount      = state.drawCount,
            isOnThePlay    = state.isOnThePlay,
            commanderCard  = resolvedCommanderCard,
        )
        _events.value = PlaytestSetupEvent.NavigateToHand(setup)
    }

    fun onEventConsumed() {
        _events.value = null
        isNavigating = false
    }
}
