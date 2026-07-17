package com.mmg.manahub.feature.decks.presentation.wizard

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CommunityAggregateRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.SeedStrategy
import com.mmg.manahub.feature.decks.domain.template.BuildDeckFromTemplateUseCase
import com.mmg.manahub.feature.decks.domain.template.CategorySuggestions
import com.mmg.manahub.feature.decks.domain.template.CollectionProfile
import com.mmg.manahub.feature.decks.domain.template.CollectionProfileUseCase
import com.mmg.manahub.feature.decks.domain.template.DeckWizardSpec
import com.mmg.manahub.feature.decks.domain.template.TemplateBuildProgress
import com.mmg.manahub.feature.decks.domain.template.TemplateBuildResult
import com.mmg.manahub.feature.decks.domain.template.TemplateCardSuggestion
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The wizard's internal phases (plan §3.4) — ONE screen, ONE ViewModel, no per-step nav
 * destination (mirrors the Playtest mulligan/battle-phase-in-one-screen precedent). */
enum class WizardPhase { FORMAT, DIRECTION, IDENTITY, REVIEW, GENERATING, RESULT }

/** One-shot side effects, delivered via a buffered [Channel] (never a nullable [MutableStateFlow]
 * — see the project-wide "one-shot events" convention documented on every other feature VM). */
sealed interface DeckWizardEvent {
    data class ShowToast(val message: String) : DeckWizardEvent

    /** The Result screen's "Open in Deck Studio" CTA — the caller navigates + pops the wizard. */
    data class OpenDeckStudio(val deckId: String) : DeckWizardEvent
}

data class DeckWizardUiState(
    val phase: WizardPhase = WizardPhase.FORMAT,

    // ── Step 1 — Format ────────────────────────────────────────────────────────
    val selectedFormat: DeckFormat? = null,

    // ── Step 2 — Direction ────────────────────────────────────────────────────
    val isLoadingProfile: Boolean = true,
    val collectionProfile: CollectionProfile? = null,
    val selectedCommander: Card? = null,
    val commanderQuery: String = "",
    val commanderSearchResults: List<Card> = emptyList(),
    val isSearchingCommander: Boolean = false,
    val selectedStrategyHint: SeedStrategy? = null,
    val selectedTribeLabel: String? = null,
    val showSeedPicker: Boolean = false,
    val seedCards: List<Card> = emptyList(),
    val seedQuery: String = "",
    val seedSearchResults: List<Card> = emptyList(),
    val isSearchingSeeds: Boolean = false,

    // ── Step 3 — Identity ─────────────────────────────────────────────────────
    val colorIdentity: Set<ManaColor> = emptySet(),
    val availableThemeTags: List<String> = emptyList(),
    val isLoadingThemeTags: Boolean = false,
    val selectedThemeHint: String? = null,

    // ── Step 4 — Review ────────────────────────────────────────────────────────
    val fillLands: Boolean = true,

    // ── Generation ─────────────────────────────────────────────────────────────
    val buildStage: com.mmg.manahub.feature.decks.domain.template.BuildStage? = null,
    val completedStages: List<com.mmg.manahub.feature.decks.domain.template.BuildStage> = emptyList(),
    val buildError: String? = null,

    // ── Result ─────────────────────────────────────────────────────────────────
    val buildResult: TemplateBuildResult? = null,
    val createdDeckId: String? = null,
) {
    /** Casual + 3-or-more colors: a non-blocking hint, never a hard gate (D9). */
    val showColorDisciplineHint: Boolean
        get() = selectedFormat?.isSixtyCardConstructed == true && colorIdentity.count { it != ManaColor.C } > 2
}

/**
 * Deck Builder v2 (`docs/plans/deck-builder-v2-plan.md` §3.4) — drives the wizard spec through
 * [BuildDeckFromTemplateUseCase] and writes the result into a FRESH deck this ViewModel creates
 * itself (unlike [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel], which may open
 * an EXISTING deck — the wizard's whole purpose is a single guided build, so it always starts a new
 * draft and hands off to Deck Studio only once the build succeeds).
 *
 * The deck is created lazily, on [onGenerate] — NOT on init — so backing out of the wizard before
 * generating never orphans a draft (no discard-if-empty machinery needed here, unlike Deck Studio).
 */
class DeckWizardViewModel(
    private val deckRepository: DeckRepository,
    private val userCardRepository: UserCardRepository,
    private val collectionProfileUseCase: CollectionProfileUseCase,
    private val buildDeckFromTemplateUseCase: BuildDeckFromTemplateUseCase,
    private val searchCardsUseCase: SearchCardsUseCase,
    private val communityAggregateRepository: CommunityAggregateRepository,
    private val crashReporter: CrashReporter,
    private val appContext: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeckWizardUiState())
    val uiState: StateFlow<DeckWizardUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeckWizardEvent>(Channel.BUFFERED)
    val events: Flow<DeckWizardEvent> = _events.receiveAsFlow()

    /** Snapshot of the collection, resolved once at init — every downstream step (profile,
     * commander candidates, the actual build) reuses this SAME snapshot (mirrors Motor A/B's
     * "already-snapshotted collection" ownership split; avoids re-querying Room per step). */
    private var collectionSnapshot: List<com.mmg.manahub.core.model.UserCardWithCard> = emptyList()

    private var commanderSearchJob: Job? = null
    private var seedSearchJob: Job? = null
    private var themeTagsJob: Job? = null
    private var generateJob: Job? = null

    /** Set only once [onGenerate] has actually created the deck row -- lets [onCancelGeneration]
     * clean up a partial build instead of orphaning an empty draft. */
    private var pendingDeckId: String? = null

    init {
        // Discoveries v2 "Build this" hand-off (D11) — optional, all blank by default.
        val strategyHintArg = savedStateHandle.get<String?>("strategyHint")?.takeIf { it.isNotEmpty() }
        val themeHintArg = savedStateHandle.get<String?>("themeHint")?.takeIf { it.isNotEmpty() }
        val colorsArg = savedStateHandle.get<String?>("colors")?.takeIf { it.isNotEmpty() }
        if (strategyHintArg != null || themeHintArg != null || colorsArg != null) {
            _uiState.update {
                it.copy(
                    selectedStrategyHint = strategyHintArg?.let { name -> SeedStrategy.entries.firstOrNull { s -> s.name == name } },
                    selectedThemeHint = themeHintArg,
                    colorIdentity = colorsArg?.let(::parseColorString).orEmpty(),
                )
            }
        }

        viewModelScope.launch {
            runCatching {
                val collection = userCardRepository.observeCollection().first()
                collectionSnapshot = collection
                collectionProfileUseCase(collection.map { it.card })
            }.onSuccess { profile ->
                _uiState.update { it.copy(collectionProfile = profile, isLoadingProfile = false) }
            }.onFailure { t ->
                logFailure("deck_wizard_profile_load_failed", t)
                _uiState.update { it.copy(isLoadingProfile = false) }
            }
        }
    }

    private fun parseColorString(raw: String): Set<ManaColor> =
        raw.mapNotNull { ch -> ManaColor.entries.firstOrNull { it.symbol == ch.toString() } }.toSet()

    // ── Step 1 — Format ───────────────────────────────────────────────────────

    fun onSelectFormat(format: DeckFormat) {
        // v1 targets Commander + Casual only (plan D1) — the other 6 restored 60-card formats
        // render "coming soon" and disabled in the UI, but guard here too since this is the actual
        // source of truth (never trust the UI-only disabled state).
        if (format != DeckFormat.COMMANDER && format != DeckFormat.CASUAL) return
        _uiState.update { it.copy(selectedFormat = format) }
    }

    // ── Step 2 — Direction ───────────────────────────────────────────────────

    fun onSelectStrategyDirection(strategy: SeedStrategy) {
        _uiState.update { it.copy(selectedStrategyHint = if (it.selectedStrategyHint == strategy) null else strategy) }
    }

    fun onSelectTribeDirection(label: String) {
        _uiState.update { it.copy(selectedTribeLabel = if (it.selectedTribeLabel == label) null else label) }
    }

    fun onCommanderQueryChange(query: String) {
        _uiState.update { it.copy(commanderQuery = query) }
        commanderSearchJob?.cancel()
        if (query.trim().length < SEARCH_MIN_LENGTH) {
            _uiState.update { it.copy(commanderSearchResults = emptyList(), isSearchingCommander = false) }
            return
        }
        commanderSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(isSearchingCommander = true) }
            val results = when (val res = searchCardsUseCase(query.trim())) {
                is DataResult.Success -> res.data.cards
                is DataResult.Error -> {
                    crashReporter.log("deck_wizard_card_search_failed")
                    emptyList()
                }
            }
            _uiState.update { it.copy(commanderSearchResults = results, isSearchingCommander = false) }
        }
    }

    fun onSelectCommander(card: Card) {
        _uiState.update {
            it.copy(
                selectedCommander = card,
                colorIdentity = card.colorIdentity.toManaColorSet(),
                commanderQuery = "",
                commanderSearchResults = emptyList(),
                availableThemeTags = emptyList(),
                selectedThemeHint = null,
            )
        }
        loadThemeTags(card)
    }

    fun onClearCommander() {
        _uiState.update {
            it.copy(selectedCommander = null, colorIdentity = emptySet(), availableThemeTags = emptyList(), selectedThemeHint = null)
        }
    }

    /** Best-effort commander-aggregate theme-tag fetch (Identity step's theme picker, plan §3.4
     * Step 3). ANY failure (Worker down, obscure commander) leaves [DeckWizardUiState
     * .availableThemeTags] empty — never blocks the wizard, mirrors every other community-data
     * fetch in this codebase ("degraded, never dead"). */
    private fun loadThemeTags(commander: Card) {
        themeTagsJob?.cancel()
        themeTagsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingThemeTags = true) }
            val tags = runCatching {
                val result = communityAggregateRepository.getCommanderAggregate(commander.name)
                (result as? DataResult.Success)?.data?.themeTags?.map { it.name }.orEmpty()
            }.onFailure { crashReporter.log("deck_wizard_theme_tags_fetch_failed") }.getOrDefault(emptyList())
            _uiState.update { it.copy(availableThemeTags = tags, isLoadingThemeTags = false) }
        }
    }

    fun onToggleSeedPicker() = _uiState.update { it.copy(showSeedPicker = !it.showSeedPicker) }

    fun onSeedQueryChange(query: String) {
        _uiState.update { it.copy(seedQuery = query) }
        seedSearchJob?.cancel()
        if (query.trim().length < SEARCH_MIN_LENGTH) {
            _uiState.update { it.copy(seedSearchResults = emptyList(), isSearchingSeeds = false) }
            return
        }
        seedSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(isSearchingSeeds = true) }
            val results = when (val res = searchCardsUseCase(query.trim())) {
                is DataResult.Success -> res.data.cards
                is DataResult.Error -> {
                    crashReporter.log("deck_wizard_card_search_failed")
                    emptyList()
                }
            }
            _uiState.update { it.copy(seedSearchResults = results, isSearchingSeeds = false) }
        }
    }

    fun onAddSeed(card: Card) {
        val current = _uiState.value.seedCards
        if (current.any { it.scryfallId == card.scryfallId } || current.size >= MAX_SEED_CARDS) return
        _uiState.update { it.copy(seedCards = current + card) }
    }

    fun onRemoveSeed(card: Card) {
        _uiState.update { it.copy(seedCards = it.seedCards.filterNot { s -> s.scryfallId == card.scryfallId }) }
    }

    // ── Step 3 — Identity ─────────────────────────────────────────────────────

    /** No-op for Commander (colors are read-only, derived from the commander). */
    fun onToggleColor(color: ManaColor) {
        val state = _uiState.value
        if (state.selectedFormat == DeckFormat.COMMANDER) return
        _uiState.update {
            it.copy(colorIdentity = if (color in it.colorIdentity) it.colorIdentity - color else it.colorIdentity + color)
        }
    }

    fun onSelectThemeHint(theme: String?) =
        _uiState.update { it.copy(selectedThemeHint = if (it.selectedThemeHint == theme) null else theme) }

    // ── Step 4 — Review ───────────────────────────────────────────────────────

    fun onToggleFillLands() = _uiState.update { it.copy(fillLands = !it.fillLands) }

    // ── Navigation between phases ────────────────────────────────────────────

    fun onNextFromFormat() {
        if (_uiState.value.selectedFormat == null) return
        logStep("direction")
        _uiState.update { it.copy(phase = WizardPhase.DIRECTION) }
    }

    fun onNextFromDirection() {
        val state = _uiState.value
        if (state.selectedFormat == DeckFormat.COMMANDER && state.selectedCommander == null) {
            crashReporter.log("deck_wizard_step_direction_blocked_no_commander")
            viewModelScope.launch {
                _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_commander_required)))
            }
            return
        }
        // Casual: pre-fill colorIdentity from the picked seeds' union, same as Commander already
        // does from the commander (DeckWizardSpec KDoc contract) -- only when the user hasn't made
        // an explicit color choice yet, so this never clobbers a Discoveries v2 hand-off or a
        // manual pick on a later back-navigation.
        val prefilled = if (state.selectedFormat != DeckFormat.COMMANDER && state.colorIdentity.isEmpty()) {
            state.seedCards.flatMap { it.colorIdentity }.toManaColorSet()
        } else {
            state.colorIdentity
        }
        logStep("identity")
        _uiState.update { it.copy(phase = WizardPhase.IDENTITY, colorIdentity = prefilled) }
    }

    fun onNextFromIdentity() {
        logStep("review")
        _uiState.update { it.copy(phase = WizardPhase.REVIEW) }
    }

    /** Back navigation between steps 1-4. Returns `true` when the caller should pop the whole
     * wizard screen instead (already at step 1, or Result — nothing left for the VM to unwind). */
    fun onBackPressed(): Boolean {
        val state = _uiState.value
        return when (state.phase) {
            WizardPhase.FORMAT -> true
            WizardPhase.DIRECTION -> { _uiState.update { it.copy(phase = WizardPhase.FORMAT) }; false }
            WizardPhase.IDENTITY -> { _uiState.update { it.copy(phase = WizardPhase.DIRECTION) }; false }
            WizardPhase.REVIEW -> { _uiState.update { it.copy(phase = WizardPhase.IDENTITY) }; false }
            WizardPhase.GENERATING -> { onCancelGeneration(); false }
            WizardPhase.RESULT -> true
        }
    }

    // ── Generation ────────────────────────────────────────────────────────────

    fun onGenerate() {
        val state = _uiState.value
        // Re-entrancy guard: the state write to GENERATING below is synchronous, so a second
        // invocation from a double-tap (before Compose recomposes the Review CTA away) reads the
        // already-updated phase here and returns instead of launching a second build / clobbering
        // `generateJob`. See feedback_deck_wizard_reentrancy_and_orphan_cleanup memory.
        if (state.phase != WizardPhase.REVIEW || state.selectedFormat == null) return
        logStep("generating")
        val format = state.selectedFormat

        _uiState.update {
            it.copy(phase = WizardPhase.GENERATING, buildStage = null, completedStages = emptyList(), buildError = null)
        }
        generateJob = viewModelScope.launch {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log("deck_wizard_generate_started")
            crashlytics.setCustomKey("deck_wizard_format", format.name)
            crashlytics.setCustomKey("deck_wizard_seed_count", state.seedCards.size)

            val spec = DeckWizardSpec(
                format = format,
                commander = state.selectedCommander,
                strategyHint = state.selectedStrategyHint,
                themeHint = state.selectedThemeHint,
                colorIdentity = if (format == DeckFormat.COMMANDER) {
                    state.selectedCommander?.colorIdentity?.toManaColorSet() ?: state.colorIdentity
                } else {
                    state.colorIdentity
                },
                seeds = state.seedCards,
                fillLands = state.fillLands,
            )

            val outcome: Pair<TemplateBuildResult?, String?> = runCatching {
                var finalResult: TemplateBuildResult? = null
                var failure: String? = null
                buildDeckFromTemplateUseCase(spec, collectionSnapshot).collect { progress ->
                    when (progress) {
                        is TemplateBuildProgress.Stage -> _uiState.update { s ->
                            s.copy(
                                completedStages = s.buildStage?.let { s.completedStages + it } ?: s.completedStages,
                                buildStage = progress.stage,
                            )
                        }
                        is TemplateBuildProgress.Complete -> finalResult = progress.result
                        is TemplateBuildProgress.Failed -> failure = progress.message
                    }
                }
                finalResult to failure
            }.getOrElse { t ->
                if (t is kotlinx.coroutines.CancellationException) throw t
                logFailure("deck_wizard_generate_crashed", t)
                null to appContext.getString(R.string.deck_wizard_build_error)
            }

            val (result, failureMessage) = outcome
            if (result == null) {
                crashlytics.log("deck_wizard_generate_failed")
                _uiState.update { it.copy(buildError = failureMessage ?: appContext.getString(R.string.deck_wizard_build_error)) }
                return@launch
            }

            val writeOutcome = runCatching { writeResultIntoNewDeck(spec, result) }
                .getOrElse { t ->
                    logFailure("deck_wizard_write_failed", t)
                    _uiState.update { it.copy(buildError = appContext.getString(R.string.deck_wizard_build_error)) }
                    return@launch
                }

            crashlytics.log("deck_wizard_generate_succeeded")
            crashlytics.setCustomKey("deck_wizard_template_source", result.templateSource.name)
            _uiState.update {
                it.copy(phase = WizardPhase.RESULT, buildResult = result, createdDeckId = writeOutcome)
            }
        }
    }

    /** Creates the fresh deck, writes commander/cover (Commander only), every [TemplateBuildResult
     * .deckCards] entry, and the archetype/theme override — mirrors [com.mmg.manahub.feature.decks
     * .presentation.DeckStudioViewModel.generateFromSeeds]'s write-through convention (one repo
     * call per card copy; a mid-write cancellation leaves a partial but valid deck). */
    private suspend fun writeResultIntoNewDeck(spec: DeckWizardSpec, result: TemplateBuildResult): String {
        val name = wizardDeckName(spec)
        val deckId = deckRepository.createDeck(name = name, description = "Draft", format = spec.format.name)
        pendingDeckId = deckId

        val commander = spec.commander
        if (spec.format == DeckFormat.COMMANDER && commander != null) {
            val created = deckRepository.observeDeckWithCards(deckId).first()?.deck
            if (created != null) {
                deckRepository.updateDeck(
                    created.copy(commanderCardId = commander.scryfallId, coverCardId = commander.scryfallId)
                )
            }
        }

        result.deckCards.forEach { entry ->
            deckRepository.addCardToDeck(deckId, entry.card.scryfallId, entry.quantity, entry.isSideboard)
        }
        deckRepository.updateArchetypeOverride(deckId, result.archetypeOverride, result.themesOverride)
        return deckId
    }

    private fun wizardDeckName(spec: DeckWizardSpec): String {
        val commander = spec.commander
        val strategyHint = spec.strategyHint
        val themeHint = spec.themeHint
        return when {
            commander != null -> commander.name
            strategyHint != null ->
                String.format(appContext.getString(R.string.deck_wizard_name_suffix_deck), strategyHint.displayName)
            themeHint != null ->
                String.format(appContext.getString(R.string.deck_wizard_name_suffix_deck), themeHint)
            else -> String.format(appContext.getString(R.string.deck_wizard_name_default), spec.format.displayName)
        }
    }

    /** Best-effort deletes a dangling partially-created deck (a build that got as far as
     * [writeResultIntoNewDeck]'s `createDeck()` call but failed/was cancelled before finishing).
     * Nulls [pendingDeckId] FIRST so a concurrent second call is a no-op, then fires the delete on
     * [viewModelScope] (fire-and-forget — neither caller needs to await it).
     *
     * MUST be called from both [onCancelGeneration] AND [onRetryGeneration] — a partial-write
     * failure leaves [pendingDeckId] set (see [writeResultIntoNewDeck]'s KDoc), and "Retry" is the
     * more natural CTA on the error screen than "Back"/Cancel. Before this fix, Retry skipped
     * cleanup entirely: each failure-then-retry cycle created a brand-new deck and overwrote
     * [pendingDeckId], permanently orphaning the previous partial draft in the user's deck list. */
    private fun cleanupPendingDeck() {
        val orphanId = pendingDeckId ?: return
        pendingDeckId = null
        viewModelScope.launch {
            runCatching { deckRepository.deleteDeck(orphanId) }
                .onFailure { logFailure("deck_wizard_cancel_cleanup_failed", it) }
        }
    }

    /** Cancels an in-flight build. Best-effort deletes a partially-created deck so backing out of
     * generation never leaves an orphaned draft (the wizard's equivalent of Deck Studio's
     * discard-if-empty contract — simpler here since the wizard ALWAYS starts a fresh deck). */
    fun onCancelGeneration() {
        generateJob?.cancel()
        cleanupPendingDeck()
        _uiState.update { it.copy(phase = WizardPhase.REVIEW, buildStage = null, completedStages = emptyList(), buildError = null) }
    }

    /** Retries generation after a [DeckWizardUiState.buildError] without re-walking the steps.
     * Cleans up any deck orphaned by the failed attempt FIRST (see [cleanupPendingDeck]) so a
     * failure-then-retry cycle never leaves a dangling partial draft behind. */
    fun onRetryGeneration() {
        cleanupPendingDeck()
        _uiState.update { it.copy(buildError = null, phase = WizardPhase.REVIEW) }
    }

    // ── Result ────────────────────────────────────────────────────────────────

    /** In-flight guard for [onAddCommunitySuggestion] -- a rapid double-tap on the same suggestion's
     * "Add" icon (no disabled/in-flight UI state exists yet) would otherwise fire `addCardToDeck`
     * twice before the first call's success removes the row, silently doubling the added quantity.
     * Mirrors this codebase's established atomic double-tap-guard convention (see
     * `feedback_trades_negotiation_atomic_events` memory): a plain mutable set keyed by the card id,
     * `add()` returns `false` when already present. */
    private val pendingSuggestionAdds = mutableSetOf<String>()

    /** Writes one community-picked (unowned, D8) suggestion into the created deck, then removes it
     * from the view-only list so it is never shown as "addable" twice. */
    fun onAddCommunitySuggestion(categoryId: String, suggestion: TemplateCardSuggestion) {
        val deckId = _uiState.value.createdDeckId ?: return
        val cardId = suggestion.card.scryfallId
        if (!pendingSuggestionAdds.add(cardId)) return
        viewModelScope.launch {
            runCatching {
                deckRepository.addCardToDeck(deckId, cardId, suggestion.suggestedCopies, false)
            }.onSuccess {
                _uiState.update { s ->
                    val result = s.buildResult ?: return@update s
                    val updatedCommunity = result.communitySuggestions.mapNotNull { cat ->
                        if (cat.category.id != categoryId) return@mapNotNull cat
                        val remaining = cat.suggestions.filterNot { it.card.scryfallId == cardId }
                        if (remaining.isEmpty()) null else cat.copy(suggestions = remaining)
                    }
                    s.copy(buildResult = result.copy(communitySuggestions = updatedCommunity))
                }
                _events.send(
                    DeckWizardEvent.ShowToast(
                        String.format(appContext.getString(R.string.deck_wizard_suggestion_added), suggestion.card.name)
                    )
                )
            }.onFailure { t -> logFailure("deck_wizard_add_community_suggestion_failed", t) }
            pendingSuggestionAdds.remove(cardId)
        }
    }

    fun onOpenDeckStudio() {
        val deckId = _uiState.value.createdDeckId ?: return
        viewModelScope.launch { _events.send(DeckWizardEvent.OpenDeckStudio(deckId)) }
    }

    private fun logFailure(tag: String, t: Throwable) {
        crashReporter.log(tag)
        crashReporter.recordException(RuntimeException("[DeckWizard] $tag", t))
    }

    /** Breadcrumb for the step being ENTERED (not exited) -- tells us where users progress to, and
     * by omission, where they stop. Plan-mandated `deck_wizard_step_*` events for this multi-step,
     * abandonable flow (see the class KDoc). */
    private fun logStep(step: String) {
        crashReporter.log("deck_wizard_step_$step")
    }

    private companion object {
        const val SEARCH_MIN_LENGTH = 2
        const val SEARCH_DEBOUNCE_MS = 400L
        const val MAX_SEED_CARDS = 8
    }
}

private fun List<String>.toManaColorSet(): Set<ManaColor> =
    mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }.toSet()
