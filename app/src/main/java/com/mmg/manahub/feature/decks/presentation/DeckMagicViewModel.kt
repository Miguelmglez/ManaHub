package com.mmg.manahub.feature.decks.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.model.DeckFormat
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.BudgetConstraints
import com.mmg.manahub.feature.decks.domain.usecase.BuildDeckFromSeedsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferredIdentity
import com.mmg.manahub.feature.decks.presentation.engine.DeckMagicEngine
import com.mmg.manahub.feature.decks.presentation.engine.GameFormat
import com.mmg.manahub.feature.decks.presentation.engine.MagicCard
import com.mmg.manahub.feature.decks.presentation.engine.MagicDiscovery
import com.mmg.manahub.feature.decks.presentation.engine.MagicSuggestion
import com.mmg.manahub.feature.decks.presentation.engine.ManaColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Steps of the active Deck Magic flow.
 *
 * SEEDS sits between DASHBOARD and BUILDING: the user picks 1+ seed cards, the deck identity
 * (colors + archetype) is inferred, and "Generate deck" runs [BuildDeckFromSeedsUseCase] then jumps
 * straight to REVIEW with the generated mainboard.
 */
enum class DeckMagicStep { DASHBOARD, SEEDS, SETUP, BUILDING, REVIEW }

data class DeckMagicUiState(
    val step: DeckMagicStep = DeckMagicStep.DASHBOARD,
    val isLoading: Boolean = true,

    // Discovery
    val discoveries: List<MagicDiscovery> = emptyList(),

    // Setup
    val deckName: String = "",
    val format: GameFormat = GameFormat.CASUAL,
    val selectedColors: Set<ManaColor> = emptyState(),
    val targetTags: List<CardTag> = emptyList(),

    // Seeds (Phase 7)
    val seedCards: List<Card> = emptyList(),
    val inferredIdentity: InferredIdentity? = null,
    val budget: BudgetConstraints = BudgetConstraints(),
    val isGenerating: Boolean = false,
    /** Number of land slots the generated deck reserves for the mana base (filled by the basic-land flow). */
    val reservedLandSlots: Int = 0,
    val seedQuery: String = "",
    val seedSearchResults: List<Card> = emptyList(),
    val isSearchingSeeds: Boolean = false,

    // Building
    val currentSuggestions: List<MagicSuggestion> = emptyList(),
    val mainboard: List<MagicCard> = emptyList(),
    val error: String? = null,
) {
    /** "Generate deck" is enabled only once at least one seed is picked. */
    val canGenerate: Boolean get() = seedCards.isNotEmpty() && !isGenerating
}

/** Tiny helper so the default constructor stays readable. */
private fun emptyState(): Set<ManaColor> = emptySet()

/** One-time UI side effects (toasts) — buffered so a paused lifecycle never drops them. */
sealed interface DeckMagicEvent {
    data class Error(val message: String) : DeckMagicEvent
}

@HiltViewModel
class DeckMagicViewModel @Inject constructor(
    private val userCardRepo: UserCardRepository,
    private val engine: DeckMagicEngine,
    private val inferDeckIdentity: InferDeckIdentityUseCase,
    private val buildDeckFromSeeds: BuildDeckFromSeedsUseCase,
    private val searchCards: SearchCardsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeckMagicUiState())
    val uiState: StateFlow<DeckMagicUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeckMagicEvent>(Channel.BUFFERED)
    val events: Flow<DeckMagicEvent> = _events.receiveAsFlow()

    private var seedSearchJob: Job? = null

    init {
        loadDiscoveries()
    }

    private fun loadDiscoveries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                val collection = userCardRepo.observeCollection().first()
                engine.discoverSynergies(collection)
            }.onSuccess { discoveries ->
                _uiState.update { it.copy(discoveries = discoveries, isLoading = false) }
            }.onFailure { t ->
                FirebaseCrashlytics.getInstance().recordException(t)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun startFromDiscovery(discovery: MagicDiscovery) {
        _uiState.update {
            it.copy(
                step = DeckMagicStep.BUILDING,
                targetTags = listOf(discovery.primaryTag),
                mainboard = discovery.cards.take(1) // Seed with the first card
            )
        }
        updateSuggestions()
    }

    fun setStep(step: DeckMagicStep) {
        _uiState.update { it.copy(step = step) }
        if (step == DeckMagicStep.SETUP || step == DeckMagicStep.REVIEW) {
            FirebaseCrashlytics.getInstance().apply {
                log("deck_magic_stub_screen_reached: step=${step.name}")
                setCustomKey("deck_magic_step", step.name)
                recordException(IllegalStateException("[DeckMagicScreen] User reached unimplemented step: ${step.name}"))
            }
        }
    }

    fun toggleColor(color: ManaColor) {
        _uiState.update { s ->
            val colors = if (color in s.selectedColors) s.selectedColors - color
                         else s.selectedColors + color
            s.copy(selectedColors = colors)
        }
        updateSuggestions()
    }

    fun addCard(card: MagicCard) {
        _uiState.update { it.copy(mainboard = it.mainboard + card) }
        updateSuggestions()
    }

    // ── Seeds flow (Phase 7) ────────────────────────────────────────────────────

    /** Enters the SEEDS step from the dashboard ("Build from a seed card"). */
    fun startFromSeeds() {
        _uiState.update {
            it.copy(
                step = DeckMagicStep.SEEDS,
                seedCards = emptyList(),
                inferredIdentity = null,
            )
        }
    }

    /** Debounced Scryfall seed search. Updates [DeckMagicUiState.seedSearchResults]. */
    fun onSeedQueryChange(query: String) {
        _uiState.update { it.copy(seedQuery = query) }
        seedSearchJob?.cancel()
        if (query.trim().length < SEED_QUERY_MIN_LENGTH) {
            _uiState.update { it.copy(seedSearchResults = emptyList(), isSearchingSeeds = false) }
            return
        }
        seedSearchJob = viewModelScope.launch {
            delay(SEED_SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(isSearchingSeeds = true) }
            val results = when (val res = searchCards(query.trim())) {
                is DataResult.Success -> res.data
                is DataResult.Error -> emptyList()
            }
            _uiState.update { it.copy(seedSearchResults = results, isSearchingSeeds = false) }
        }
    }

    /** Adds a seed card (de-duplicated by scryfallId) and re-infers the deck identity. */
    fun addSeed(card: Card) {
        _uiState.update { s ->
            if (s.seedCards.any { it.scryfallId == card.scryfallId }) return@update s
            val seeds = s.seedCards + card
            s.copy(seedCards = seeds, inferredIdentity = inferDeckIdentity(seeds))
        }
    }

    /** Removes a seed card and re-infers the deck identity (null identity when no seeds remain). */
    fun removeSeed(card: Card) {
        _uiState.update { s ->
            val seeds = s.seedCards.filterNot { it.scryfallId == card.scryfallId }
            s.copy(
                seedCards = seeds,
                inferredIdentity = if (seeds.isEmpty()) null else inferDeckIdentity(seeds),
            )
        }
    }

    /** Updates the active budget filter for the generated deck. */
    fun onBudgetChanged(budget: BudgetConstraints) {
        _uiState.update { it.copy(budget = budget) }
    }

    /**
     * Runs [BuildDeckFromSeedsUseCase] off the seeds + inferred identity, then transitions to REVIEW
     * with the generated mainboard. Errors are surfaced via a [DeckMagicEvent.Error] toast and the
     * step is left unchanged so the user can retry.
     */
    fun generateFromSeeds() {
        var captured: DeckMagicUiState? = null
        _uiState.update { s ->
            if (s.seedCards.isEmpty() || s.isGenerating) return@update s
            captured = s
            s.copy(isGenerating = true, error = null)
        }
        val snapshot = captured ?: return

        viewModelScope.launch {
            val result = runCatching {
                val collection = userCardRepo.observeCollection().first().map { it.card }
                val identity = snapshot.inferredIdentity ?: inferDeckIdentity(snapshot.seedCards)
                buildDeckFromSeeds(
                    seeds = snapshot.seedCards,
                    identity = identity,
                    format = snapshot.format.toDeckFormat(),
                    constraints = snapshot.budget,
                    collection = collection,
                )
            }

            result
                .onSuccess { deck ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            step = DeckMagicStep.REVIEW,
                            mainboard = deck.mainboard,
                            reservedLandSlots = deck.reservedLandSlots,
                        )
                    }
                }
                .onFailure { t ->
                    FirebaseCrashlytics.getInstance().recordException(t)
                    _uiState.update { it.copy(isGenerating = false) }
                    _events.send(DeckMagicEvent.Error(t.message ?: "Could not generate the deck"))
                }
        }
    }

    private fun updateSuggestions() {
        viewModelScope.launch {
            val collection = userCardRepo.observeCollection().first()
            // Read state after the suspend boundary so colors/mainboard/tags are fresh.
            val s = _uiState.value
            val suggestions = engine.getSuggestions(
                collection = collection,
                targetTags = s.targetTags,
                selectedColors = s.selectedColors,
                mainboard = s.mainboard,
                format = s.format
            )
            _uiState.update { it.copy(currentSuggestions = suggestions) }
        }
    }

    private companion object {
        const val SEED_QUERY_MIN_LENGTH = 2
        const val SEED_SEARCH_DEBOUNCE_MS = 400L
    }
}

/**
 * Maps the active builder's [GameFormat] to the engine's [DeckFormat]. The engine only models three
 * skeletons (STANDARD / COMMANDER / DRAFT); the 60-card constructed formats all collapse to STANDARD
 * (closest skeleton), Commander stays Commander, and there is no Draft entry in [GameFormat].
 */
internal fun GameFormat.toDeckFormat(): DeckFormat = when (this) {
    GameFormat.COMMANDER -> DeckFormat.COMMANDER
    GameFormat.STANDARD,
    GameFormat.PIONEER,
    GameFormat.MODERN,
    GameFormat.LEGACY,
    GameFormat.VINTAGE,
    GameFormat.PAUPER,
    GameFormat.CASUAL -> DeckFormat.STANDARD
}
