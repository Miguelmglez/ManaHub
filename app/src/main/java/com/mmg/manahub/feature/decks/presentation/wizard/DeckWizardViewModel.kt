package com.mmg.manahub.feature.decks.presentation.wizard

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.repository.CommunityAggregateRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ColorStrategyAffinity
import com.mmg.manahub.feature.decks.domain.engine.ColorStrategyEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckIdentitySeedTags
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.StrategyProfile
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.template.BuildDeckFromTemplateUseCase
import com.mmg.manahub.feature.decks.domain.template.CategorySuggestions
import com.mmg.manahub.feature.decks.domain.template.CollectionProfile
import com.mmg.manahub.feature.decks.domain.template.CollectionProfileUseCase
import com.mmg.manahub.feature.decks.domain.template.CollectionTribeSignal
import com.mmg.manahub.feature.decks.domain.template.DeckWizardSpec
import com.mmg.manahub.feature.decks.domain.template.TemplateBuildProgress
import com.mmg.manahub.feature.decks.domain.template.TemplateBuildResult
import com.mmg.manahub.feature.decks.domain.template.TemplateCardSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.RankOwnedCardsForProfileUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SeedStrategyCandidate
import com.mmg.manahub.feature.decks.domain.usecase.SeedStrategySuggestion
import com.mmg.manahub.feature.decks.domain.usecase.SuggestStrategiesForSeedsUseCase
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

/** The wizard's internal phases (plan §3.4; [ENTRY] added by the Deck Engine Unification plan §5
 * Phase 3.1) — ONE screen, ONE ViewModel, no per-step nav destination (mirrors the Playtest
 * mulligan/battle-phase-in-one-screen precedent). */
enum class WizardPhase { FORMAT, ENTRY, DIRECTION, IDENTITY, REVIEW, GENERATING, RESULT }

/**
 * Deck Engine Unification plan (§5 Phase 3.1) — the three ways a build can start. [CARDS] is the
 * ORIGINAL wizard flow (commander/seed picker, unchanged) and is the ONLY flow [DeckFormat.COMMANDER]
 * ever uses (the commander itself is the mandatory first seed — plan: "Commander = cards-flow variant
 * with a mandatory commander slot as seed #1"), so [WizardPhase.ENTRY] is skipped entirely for
 * Commander builds. [COLORS] and [STRATEGY] are Casual-only and both reuse [WizardPhase.DIRECTION] —
 * that phase's content composable dispatches on this field rather than adding two more phases.
 */
enum class WizardEntryFlow { CARDS, COLORS, STRATEGY }

/** A rankable color-combination pick for Flow C (strategy-first) — the curated
 * [ColorStrategyAffinity.combosFor] weight blended with how strong the user's OWN collection already
 * is in those colors ([CollectionProfile.colorShares]), so a taxonomy pick favors combos the user can
 * actually build today over a purely abstract ranking. */
data class ColorComboSuggestion(val colors: Set<ManaColor>, val score: Float)

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

    // ── Entry chooser (Deck Engine Unification plan §5 Phase 3.1) ────────────────
    /** Casual only — Commander always forces [WizardEntryFlow.CARDS] and skips [WizardPhase.ENTRY]
     * entirely (see that enum's KDoc), so this default is only ever OBSERVED for Casual. */
    val entryFlow: WizardEntryFlow = WizardEntryFlow.CARDS,

    // ── Step 2 — Direction ────────────────────────────────────────────────────
    val isLoadingProfile: Boolean = true,
    val collectionProfile: CollectionProfile? = null,
    val selectedCommander: Card? = null,
    val commanderQuery: String = "",
    val commanderSearchResults: List<Card> = emptyList(),
    val isSearchingCommander: Boolean = false,
    /** Flow A (cards-first, plan §5 3.2) — recomputed from [seedCards] (+ [selectedCommander]) on
     * every seed add/remove. `null` while no seed is picked yet (nothing to rank). */
    val seedStrategySuggestion: SeedStrategySuggestion? = null,
    /** Flow B (colors-first, plan §5 3.3) — [ColorStrategyAffinity.forColors] for the CURRENT
     * [colorIdentity] pick; empty until at least one color is toggled on. */
    val colorAffinityEntries: List<ColorStrategyEntry> = emptyList(),
    val selectedColorAffinityEntry: ColorStrategyEntry? = null,
    /** Flow C (strategy-first, plan §5 3.4) — the taxonomy browser's free-text filter and the color
     * combos ranked once an archetype/theme is picked. */
    val taxonomyQuery: String = "",
    val colorComboSuggestions: List<ColorComboSuggestion> = emptyList(),
    /** Flow B/C shared (plan §5 3.3/3.4) — owned cards ranked against the just-picked profile,
     * rendered as a check/uncheck list that writes into [seedCards] via the SAME add/remove handlers
     * Flow A's seed picker uses (RC5 — the user always controls which exact cards seed the build). */
    val suggestedSeedCards: List<Card> = emptyList(),
    /** Deck Engine Unification plan (D2): the Direction step's picked [ArchetypeId], resolved from
     * a tapped collection-lean [CardTag] chip via [DeckIdentitySeedTags.archetypeForTag]. Mutually
     * exclusive with [selectedDirectionTheme] and the tribe pick -- selecting one clears the
     * others, since all three represent the SAME "one Direction pick" slot in
     * [com.mmg.manahub.feature.decks.domain.template.DeckWizardSpec.strategyProfile]. */
    val selectedArchetype: ArchetypeId? = null,
    /** The Direction step's picked [ThemeId] -- resolved from a tapped chip whose [CardTag] has no
     * [ArchetypeId] equivalent but does resolve to a theme via [DeckIdentitySeedTags.themeForTag]
     * (e.g. `plus_counters` -> PLUS1_COUNTERS, `spellslinger` -> SPELLSLINGER). Same "one Direction
     * pick" slot as [selectedArchetype]. */
    val selectedDirectionTheme: ThemeId? = null,
    /** Edge-case audit (Wizard Quality Campaign final gate, 2026-07-19): the raw
     * [com.mmg.manahub.feature.decks.domain.template.CollectionTribeSignal.tribeKey] (e.g.
     * `"tribe:elf"`) of the picked tribe Direction chip -- threaded into
     * [StrategyProfile.tribe]. [selectedTribeLabel] stays the pluralized DISPLAY string (e.g.
     * "Elves") for the Review-screen echo; this is the stable machine key the build actually
     * consumes. Same "one Direction pick" slot as [selectedArchetype]/[selectedDirectionTheme] --
     * selecting a tribe clears both of those, and vice versa. */
    val selectedTribeKey: String? = null,
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
    /** Deck Engine Unification plan (§5 Phase 3.5) — the shared source step. Motor B only ever
     * re-ranks OWNED cards by community popularity (never pulls in unowned cards), so this is a
     * plain on/off "also use community trends" toggle rather than a 3-way collection/community/both
     * selector (see [DeckWizardSpec.useCommunityData]'s KDoc). Hidden entirely in the UI when
     * [communityEngineAvailable] is false (the GLOBAL `communityEngineEnabledFlow` feature flag is
     * off) — the per-build toggle can never turn on a capability the global flag has disabled.
     */
    val communityEngineAvailable: Boolean = false,
    val useCommunityData: Boolean = false,

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
 * QA fix (Deck Engine Unification plan RUN 3b, edge-case audit 2026-07-20): both
 * [WizardPhase.FORMAT] and [WizardPhase.ENTRY] are reachable via normal back-navigation at ANY
 * point after the user has already populated state in a flow ([DeckWizardViewModel.onBackPressed]
 * routes DIRECTION→ENTRY/FORMAT and ENTRY→FORMAT) — so a user can pick a Direction (commander,
 * archetype, colors, seeds...), back out, and pick a DIFFERENT format or entry flow without that
 * state ever being cleared. Without this reset, stale scratch state from an ABANDONED flow rode
 * into the build: a stale [DeckWizardUiState.selectedArchetype] survived a Casual→Commander format
 * switch straight into [StrategyProfile] via `onGenerate`, and a stale [DeckWizardUiState
 * .selectedCommander] survived a Commander→Casual switch into [DeckWizardViewModel.wizardDeckName]
 * and (pre-fix) [BuildDeckFromTemplateUseCase]'s seed-tag inference.
 *
 * Every field here represents "one Direction/Identity/Review-step attempt" scratch state, NOT a
 * session-level preference — [DeckWizardUiState.useCommunityData]/[DeckWizardUiState.fillLands]
 * are deliberately excluded (legitimate across a format/flow switch). [DeckWizardUiState.entryFlow]
 * is also excluded: both call sites ([DeckWizardViewModel.onSelectFormat]/[DeckWizardViewModel
 * .onSelectEntryFlow]) set it themselves right after calling this, to the value that call is
 * actually selecting.
 */
private fun DeckWizardUiState.resetDirectionScratchState(): DeckWizardUiState = copy(
    selectedCommander = null,
    commanderQuery = "",
    commanderSearchResults = emptyList(),
    isSearchingCommander = false,
    seedStrategySuggestion = null,
    colorAffinityEntries = emptyList(),
    selectedColorAffinityEntry = null,
    taxonomyQuery = "",
    colorComboSuggestions = emptyList(),
    suggestedSeedCards = emptyList(),
    selectedArchetype = null,
    selectedDirectionTheme = null,
    selectedTribeKey = null,
    selectedTribeLabel = null,
    showSeedPicker = false,
    seedCards = emptyList(),
    seedQuery = "",
    seedSearchResults = emptyList(),
    isSearchingSeeds = false,
    colorIdentity = emptySet(),
    availableThemeTags = emptyList(),
    isLoadingThemeTags = false,
    selectedThemeHint = null,
)

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
    // Deck Engine Unification plan (§5 Phase 3) — appended last so no existing positional-arg-free
    // call site needs to change. Pure, dependency-free use cases (no defaults needed in production;
    // Koin always supplies real instances) plus the ONE stateful dependency (the global community
    // feature flag) the Review step's source toggle needs to know whether to render at all.
    private val suggestStrategiesForSeedsUseCase: SuggestStrategiesForSeedsUseCase = SuggestStrategiesForSeedsUseCase(),
    private val rankOwnedCardsForProfileUseCase: RankOwnedCardsForProfileUseCase = RankOwnedCardsForProfileUseCase(),
    private val userPreferences: UserPreferencesDataStore? = null,
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
        // Discoveries v2 "Build this" hand-off (D11) — optional, all blank by default. Deck Engine
        // Unification (D2): nav args carry the unified taxonomy directly (raw ArchetypeId/ThemeId
        // enum names + a tribe key) instead of the old SeedStrategy-name/free-text-theme pair.
        val archetypeArg = savedStateHandle.get<String?>("archetype")?.takeIf { it.isNotEmpty() }
        val themeArg = savedStateHandle.get<String?>("theme")?.takeIf { it.isNotEmpty() }
        val tribeArg = savedStateHandle.get<String?>("tribe")?.takeIf { it.isNotEmpty() }
        val colorsArg = savedStateHandle.get<String?>("colors")?.takeIf { it.isNotEmpty() }
        // Deck Engine Unification plan D7 (4.3): a Commander Spellbook combo's card names --
        // percent-decoded by Navigation before this reads it, `|`-joined (NOT `,` -- many real
        // MTG card names contain a literal comma, e.g. "Urza, Lord High Artificer"; see
        // Screen.DeckWizard.createRoute's KDoc). Forces Flow A (cards-first): a combo hand-off IS
        // a card-first pick by construction.
        val seedsArg = savedStateHandle.get<String?>("seeds")?.takeIf { it.isNotEmpty() }
            ?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (archetypeArg != null || themeArg != null || tribeArg != null || colorsArg != null) {
            _uiState.update {
                it.copy(
                    selectedArchetype = archetypeArg?.let { name -> ArchetypeId.entries.firstOrNull { a -> a.name == name } },
                    selectedDirectionTheme = themeArg?.let { name -> ThemeId.entries.firstOrNull { t -> t.name == name } },
                    selectedTribeKey = tribeArg,
                    selectedTribeLabel = tribeArg?.let { key -> key.removePrefix("tribe:").replaceFirstChar(Char::uppercase) },
                    colorIdentity = colorsArg?.let(::parseColorString).orEmpty(),
                )
            }
        }
        if (seedsArg.isNotEmpty()) {
            _uiState.update { it.copy(entryFlow = WizardEntryFlow.CARDS) }
        }

        viewModelScope.launch {
            runCatching {
                val collection = userCardRepository.observeCollection().first()
                collectionSnapshot = collection
                collectionProfileUseCase(collection.map { it.card })
            }.onSuccess { profile ->
                _uiState.update { it.copy(collectionProfile = profile, isLoadingProfile = false) }
                // QA fix (RUN 3b follow-up): collectionSnapshot only lands here, asynchronously --
                // if the user already picked a Flow B color-affinity entry or a Flow C color combo
                // WHILE this load was still in flight, onSelectColorAffinityEntry/onSelectColorCombo
                // ranked against a still-empty snapshot and silently produced an empty
                // suggestedSeedCards list (no crash, but the user's own owned cards never showed up
                // as suggestions). Re-rank against whichever pick is CURRENTLY active now that the
                // real snapshot is in, so a fast tap during the loading window still ends up correct.
                recomputeActiveSuggestedSeeds()
                if (seedsArg.isNotEmpty()) resolveComboSeeds(seedsArg)
            }.onFailure { t ->
                logFailure("deck_wizard_profile_load_failed", t)
                _uiState.update { it.copy(isLoadingProfile = false) }
            }
        }

        // Deck Engine Unification plan (§5 Phase 3.5) — the Review step's community-source toggle
        // only ever RENDERS when the global flag is on; a fetch failure degrades to "unavailable"
        // (never blocks the wizard, mirrors every other best-effort community-data read in this VM).
        viewModelScope.launch {
            val available = runCatching { userPreferences?.communityEngineEnabledFlow?.first() ?: false }.getOrDefault(false)
            _uiState.update { it.copy(communityEngineAvailable = available) }
        }
    }

    private fun parseColorString(raw: String): Set<ManaColor> =
        raw.mapNotNull { ch -> ManaColor.entries.firstOrNull { it.symbol == ch.toString() } }.toSet()

    /** QA fix (RUN 3b): cancels any in-flight commander/seed search or theme-tag fetch BEFORE a
     * format/entry-flow switch wipes the state those coroutines write into -- without this, a
     * debounced search from the ABANDONED flow could land after the reset and silently repopulate
     * `commanderSearchResults`/`seedSearchResults`/`availableThemeTags` for the NEW flow. */
    private fun cancelDirectionSearchJobs() {
        commanderSearchJob?.cancel()
        seedSearchJob?.cancel()
        themeTagsJob?.cancel()
    }

    // ── Step 1 — Format ───────────────────────────────────────────────────────

    fun onSelectFormat(format: DeckFormat) {
        // v1 targets Commander + Casual only (plan D1) — the other 6 restored 60-card formats
        // render "coming soon" and disabled in the UI, but guard here too since this is the actual
        // source of truth (never trust the UI-only disabled state).
        if (format != DeckFormat.COMMANDER && format != DeckFormat.CASUAL) return
        // QA fix (RUN 3b): FORMAT is reachable via back-navigation at any point after Direction-step
        // state has already been populated (see resetDirectionScratchState's KDoc) -- an ACTUAL
        // format change wipes every per-flow scratch field and resets the entry chooser to CARDS, so
        // a stale commander/archetype/colors pick from the abandoned format can never leak into a
        // build under the new one. A re-tap of the CURRENTLY selected format is a pure no-op-ish
        // write (never wipes state the user hasn't actually left).
        if (_uiState.value.selectedFormat == format) {
            _uiState.update { it.copy(selectedFormat = format) }
            return
        }
        cancelDirectionSearchJobs()
        _uiState.update { it.resetDirectionScratchState().copy(selectedFormat = format, entryFlow = WizardEntryFlow.CARDS) }
    }

    // ── Step 2 — Direction ───────────────────────────────────────────────────

    /**
     * Deck Engine Unification plan (D2): resolves a collection-lean STRATEGY [CardTag] chip tap
     * onto the unified taxonomy via [DeckIdentitySeedTags.archetypeForTag]/`.themeForTag` --
     * replaces the old `SeedStrategy.forTag` + raw-tagHint-fallback split
     * (`onSelectStrategyDirection`/`onSelectTagHintDirection`, Wizard Quality Campaign B1) with ONE
     * handler. [CollectionLeanSection] only renders a chip whose tag resolves to one or the other
     * (never a dead tap -- see that composable's KDoc), so the `else` branch below is defensive
     * only and never observed in practice.
     */
    fun onSelectDirectionTag(tag: CardTag) {
        val archetype = DeckIdentitySeedTags.archetypeForTag(tag)
        val theme = DeckIdentitySeedTags.themeForTag(tag)
        _uiState.update { state ->
            when {
                archetype != null -> state.copy(
                    selectedArchetype = if (state.selectedArchetype == archetype) null else archetype,
                    selectedDirectionTheme = null,
                    selectedTribeKey = null,
                    selectedTribeLabel = null,
                )
                theme != null -> state.copy(
                    selectedDirectionTheme = if (state.selectedDirectionTheme == theme) null else theme,
                    selectedArchetype = null,
                    selectedTribeKey = null,
                    selectedTribeLabel = null,
                )
                else -> state
            }
        }
    }

    /** Edge-case audit (Wizard Quality Campaign final gate, 2026-07-19): the tribe Direction chip's
     * fix for the same "dead chip" bug class B1 fixed for the strategy/tag chips -- previously this
     * only toggled [DeckWizardUiState.selectedTribeLabel] for Review-screen display with no effect
     * on the build. Carries [CollectionTribeSignal.tribeKey] (the stable `tribe:<subtype>` key, not
     * the pluralized display label) into [StrategyProfile.tribe] via [onGenerate]. Same "one
     * Direction pick" slot as archetype/theme -- clears both. */
    fun onSelectTribeDirection(tribe: CollectionTribeSignal) {
        _uiState.update {
            it.copy(
                selectedTribeKey = if (it.selectedTribeKey == tribe.tribeKey) null else tribe.tribeKey,
                selectedTribeLabel = if (it.selectedTribeKey == tribe.tribeKey) null else tribe.displayLabel,
                selectedArchetype = null,
                selectedDirectionTheme = null,
            )
        }
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
        recomputeSeedStrategySuggestion()
    }

    fun onClearCommander() {
        _uiState.update {
            it.copy(selectedCommander = null, colorIdentity = emptySet(), availableThemeTags = emptyList(), selectedThemeHint = null)
        }
        recomputeSeedStrategySuggestion()
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
        recomputeSeedStrategySuggestion()
    }

    fun onRemoveSeed(card: Card) {
        _uiState.update { it.copy(seedCards = it.seedCards.filterNot { s -> s.scryfallId == card.scryfallId }) }
        recomputeSeedStrategySuggestion()
    }

    /** Flow A (cards-first, plan §5 3.2) — re-ranks [SuggestStrategiesForSeedsUseCase] over the
     * current seeds PLUS the commander when one is picked (plan: "Commander = cards-flow variant
     * with a mandatory commander slot as seed #1" — its identity tags count toward strategy fit and
     * seed coherence exactly like any other seed). `null` while nothing is picked yet. */
    private fun recomputeSeedStrategySuggestion() {
        val state = _uiState.value
        val seedsForRanking = listOfNotNull(state.selectedCommander) + state.seedCards
        val suggestion = if (seedsForRanking.isEmpty()) null else suggestStrategiesForSeedsUseCase(seedsForRanking)
        if (suggestion != null && !suggestion.isCoherent) {
            crashReporter.log("deck_wizard_seed_coherence_low")
            crashReporter.setCustomKey("deck_wizard_seed_coherence_score", suggestion.coherenceScore.toString())
            crashReporter.setCustomKey("deck_wizard_seed_count", seedsForRanking.size.toString())
        }
        _uiState.update { it.copy(seedStrategySuggestion = suggestion) }
    }

    /**
     * Deck Engine Unification plan D7 (4.3) — resolves a combo's component card NAMES (from the
     * synergy browser's "Use as seed" hand-off) into actual [Card]s and pre-populates
     * [DeckWizardUiState.seedCards], mirroring [onAddSeed]'s exact dedupe/[MAX_SEED_CARDS] cap so
     * a combo hand-off can never behave differently from a manual pick.
     *
     * Resolution order per name: (1) [collectionSnapshot] first (owned, no network -- most combo
     * pieces the user already has), (2) a [searchCardsUseCase] lookup for names not owned (a combo
     * missing exactly one card is the WHOLE POINT of "almost there" -- that piece is by definition
     * not owned, so skipping network resolution would silently drop it). Best-effort: a name that
     * resolves to nothing (typo-adjacent Spellbook naming drift, or the search itself failing) is
     * silently skipped rather than blocking the rest -- the user still gets whatever DID resolve
     * and can search-add the rest manually.
     */
    private suspend fun resolveComboSeeds(names: List<String>) {
        val ownedByLowerName = collectionSnapshot.associateBy { it.card.name.lowercase() }
        val resolved = mutableListOf<Card>()
        for (name in names) {
            if (resolved.size >= MAX_SEED_CARDS) break
            val owned = ownedByLowerName[name.lowercase()]?.card
            if (owned != null) {
                resolved += owned
                continue
            }
            val found = runCatching {
                when (val res = searchCardsUseCase(name)) {
                    is DataResult.Success -> res.data.cards.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    is DataResult.Error -> null
                }
            }.getOrNull()
            if (found != null) resolved += found
        }
        if (resolved.isEmpty()) return
        val current = _uiState.value.seedCards
        val merged = (current + resolved).distinctBy { it.scryfallId }.take(MAX_SEED_CARDS)
        _uiState.update { it.copy(seedCards = merged) }
        recomputeSeedStrategySuggestion()
    }

    /**
     * Flow A's ranked "Suggested strategies" list tap handler — resolves a [SeedStrategyCandidate]
     * onto the SAME one-slot Direction pick [onSelectDirectionTag]/[onSelectTribeDirection] already
     * write (archetype/theme/tribe are mutually exclusive), so [onGenerate] needs no awareness this
     * pick came from ranking rather than a quick-pick chip. Tapping the CURRENTLY selected candidate
     * again clears the pick (toggle, same UX as every other Direction chip in this screen).
     */
    fun onSelectSeedStrategyCandidate(candidate: SeedStrategyCandidate) {
        val profile = candidate.profile
        val state = _uiState.value
        val hasPick = profile.archetype != null || profile.themes.isNotEmpty() || profile.tribe != null
        val alreadySelected = hasPick &&
            state.selectedArchetype == profile.archetype &&
            state.selectedDirectionTheme == profile.themes.firstOrNull() &&
            state.selectedTribeKey == profile.tribe
        _uiState.update {
            if (alreadySelected) {
                it.copy(selectedArchetype = null, selectedDirectionTheme = null, selectedTribeKey = null, selectedTribeLabel = null)
            } else {
                it.copy(
                    selectedArchetype = profile.archetype,
                    selectedDirectionTheme = profile.themes.firstOrNull(),
                    selectedTribeKey = profile.tribe,
                    selectedTribeLabel = profile.tribe?.let { key -> key.removePrefix("tribe:").replaceFirstChar(Char::uppercase) },
                )
            }
        }
    }

    // ── Entry chooser (Deck Engine Unification plan §5 Phase 3.1) ────────────────

    /** Casual only — Commander never reaches [WizardPhase.ENTRY] (see [WizardEntryFlow]'s KDoc).
     *
     * QA fix (RUN 3b): ENTRY is reachable via back-navigation from DIRECTION after the previously
     * active flow has already populated its own scratch state (e.g. Strategy flow's archetype +
     * colors) -- switching to a DIFFERENT flow resets that scratch state (see
     * [resetDirectionScratchState]'s KDoc) so it can never render as a stale pre-filled pick in the
     * newly chosen flow, or ride silently into the build. Re-tapping the flow the user is ALREADY
     * in is a no-op-ish transition (never wipes state on the screen they haven't left). */
    fun onSelectEntryFlow(flow: WizardEntryFlow) {
        logStep("direction_${flow.name.lowercase()}")
        if (_uiState.value.entryFlow == flow) {
            _uiState.update { it.copy(entryFlow = flow, phase = WizardPhase.DIRECTION) }
            return
        }
        cancelDirectionSearchJobs()
        _uiState.update { it.resetDirectionScratchState().copy(entryFlow = flow, phase = WizardPhase.DIRECTION) }
    }

    // ── Flow B — colors-first (plan §5 3.3) ───────────────────────────────────────

    fun onToggleColorFlowColor(color: ManaColor) {
        _uiState.update { state ->
            val updated = if (color in state.colorIdentity) state.colorIdentity - color else state.colorIdentity + color
            state.copy(
                colorIdentity = updated,
                colorAffinityEntries = if (updated.isEmpty()) emptyList() else ColorStrategyAffinity.forColors(updated),
                selectedColorAffinityEntry = null,
                selectedArchetype = null,
                selectedDirectionTheme = null,
                suggestedSeedCards = emptyList(),
            )
        }
    }

    /** Picks one [ColorStrategyEntry] from the current [DeckWizardUiState.colorAffinityEntries] list
     * (writes the SAME archetype/theme Direction-pick fields as every other flow), then ranks the
     * user's owned collection against the resulting [StrategyProfile] as a suggested-seed
     * check/uncheck list (RC5). Tapping the same entry again clears the pick. */
    fun onSelectColorAffinityEntry(entry: ColorStrategyEntry) {
        val state = _uiState.value
        val alreadySelected = state.selectedColorAffinityEntry == entry
        if (alreadySelected) {
            _uiState.update {
                it.copy(selectedColorAffinityEntry = null, selectedArchetype = null, selectedDirectionTheme = null, suggestedSeedCards = emptyList())
            }
            return
        }
        _uiState.update {
            it.copy(
                selectedColorAffinityEntry = entry,
                selectedArchetype = entry.archetype,
                selectedDirectionTheme = entry.themes.firstOrNull(),
                selectedTribeKey = null,
                selectedTribeLabel = null,
            )
        }
        val suggested = rankOwnedCardsForProfileUseCase(entry.toStrategyProfile(state.colorIdentity), collectionSnapshot.map { it.card })
        _uiState.update { it.copy(suggestedSeedCards = suggested) }
    }

    // ── Flow C — strategy-first (plan §5 3.4) ─────────────────────────────────────

    fun onTaxonomyQueryChange(query: String) = _uiState.update { it.copy(taxonomyQuery = query) }

    fun onSelectTaxonomyArchetype(archetype: ArchetypeId) {
        val alreadySelected = _uiState.value.selectedArchetype == archetype
        _uiState.update {
            it.copy(
                selectedArchetype = if (alreadySelected) null else archetype,
                selectedDirectionTheme = null,
                selectedTribeKey = null,
                selectedTribeLabel = null,
                colorComboSuggestions = emptyList(),
                suggestedSeedCards = emptyList(),
                // QA fix (edge-case audit follow-up, RUN 3b): a color-combo pick is DOWNSTREAM of
                // the archetype/theme pick -- it stops being valid the instant the upstream pick
                // changes (select-different or deselect), so it must be invalidated here too.
                // Without this, a combo picked for a since-abandoned archetype/theme rode into the
                // build alongside the newly picked one (e.g. Boros {R,W} colors surviving a switch
                // to Ramp, an archetype whose curated combos are green-leaning).
                colorIdentity = emptySet(),
            )
        }
        if (!alreadySelected) recomputeColorComboSuggestions(archetype = archetype, theme = null)
    }

    fun onSelectTaxonomyTheme(theme: ThemeId) {
        val alreadySelected = _uiState.value.selectedDirectionTheme == theme
        _uiState.update {
            it.copy(
                selectedDirectionTheme = if (alreadySelected) null else theme,
                selectedArchetype = null,
                selectedTribeKey = null,
                selectedTribeLabel = null,
                colorComboSuggestions = emptyList(),
                suggestedSeedCards = emptyList(),
                // Same downstream-invalidation rule as onSelectTaxonomyArchetype -- see its comment.
                colorIdentity = emptySet(),
            )
        }
        if (!alreadySelected) recomputeColorComboSuggestions(archetype = null, theme = theme)
    }

    /** [ColorStrategyAffinity.combosFor] (curated ranking) blended with how strong the user's OWN
     * collection already is in each candidate combo ([CollectionProfile.colorShares]) -- a taxonomy
     * pick favors color combos the user can actually build today, not a purely abstract ranking. */
    private fun recomputeColorComboSuggestions(archetype: ArchetypeId?, theme: ThemeId?) {
        val shareByColor = _uiState.value.collectionProfile?.colorShares?.associate { it.color to it.share }.orEmpty()
        val combos = ColorStrategyAffinity.combosFor(archetype, theme)
            .map { (colors, weight) ->
                val avgShare = if (colors.isEmpty()) 0f else colors.map { shareByColor[it] ?: 0f }.average().toFloat()
                ColorComboSuggestion(colors, weight * (0.5f + avgShare))
            }
            .sortedByDescending { it.score }
        _uiState.update { it.copy(colorComboSuggestions = combos) }
    }

    /** Picks one [ColorComboSuggestion] (writes [DeckWizardUiState.colorIdentity]), then ranks
     * suggested seeds against the now-complete [StrategyProfile] (archetype/theme + these colors),
     * same as Flow B. Tapping the same combo again clears the color pick. */
    fun onSelectColorCombo(combo: ColorComboSuggestion) {
        val state = _uiState.value
        val alreadySelected = state.colorIdentity == combo.colors
        val colors = if (alreadySelected) emptySet() else combo.colors
        _uiState.update { it.copy(colorIdentity = colors) }
        if (alreadySelected) {
            _uiState.update { it.copy(suggestedSeedCards = emptyList()) }
            return
        }
        val profile = StrategyProfile(archetype = state.selectedArchetype, themes = listOfNotNull(state.selectedDirectionTheme), colors = colors)
        val suggested = rankOwnedCardsForProfileUseCase(profile, collectionSnapshot.map { it.card })
        _uiState.update { it.copy(suggestedSeedCards = suggested) }
    }

    /** QA fix (RUN 3b follow-up) -- see the call site in `init` for the race this closes. Re-derives
     * the SAME [StrategyProfile] [onSelectColorAffinityEntry] (Flow B)/[onSelectColorCombo] (Flow C)
     * would have ranked against, from whichever pick is CURRENTLY active, and re-ranks
     * [collectionSnapshot] now that it has actually loaded. A no-op when nothing is picked yet (the
     * common case -- this only ever does real work when the user picked during the loading window). */
    private fun recomputeActiveSuggestedSeeds() {
        val state = _uiState.value
        val affinityEntry = state.selectedColorAffinityEntry
        val profile = when {
            affinityEntry != null -> affinityEntry.toStrategyProfile(state.colorIdentity)
            state.entryFlow == WizardEntryFlow.STRATEGY &&
                state.colorIdentity.isNotEmpty() &&
                (state.selectedArchetype != null || state.selectedDirectionTheme != null) ->
                StrategyProfile(
                    archetype = state.selectedArchetype,
                    themes = listOfNotNull(state.selectedDirectionTheme),
                    colors = state.colorIdentity,
                )
            else -> null
        } ?: return
        val suggested = rankOwnedCardsForProfileUseCase(profile, collectionSnapshot.map { it.card })
        _uiState.update { it.copy(suggestedSeedCards = suggested) }
    }

    // ── Flow B/C shared — suggested-seed check/uncheck (plan §5 3.3/3.4) ─────────

    /** Toggles a [DeckWizardUiState.suggestedSeedCards] entry into/out of [DeckWizardUiState
     * .seedCards] via the SAME [onAddSeed]/[onRemoveSeed] handlers Flow A's seed picker uses (RC5 —
     * one seed-selection mechanism, three ways to arrive at it). */
    fun onToggleSuggestedSeed(card: Card) {
        val isSeeded = _uiState.value.seedCards.any { it.scryfallId == card.scryfallId }
        if (isSeeded) onRemoveSeed(card) else onAddSeed(card)
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

    /** Deck Engine Unification plan (§5 Phase 3.5) — no-op when [DeckWizardUiState
     * .communityEngineAvailable] is false (the UI never renders the toggle in that case either; this
     * guard is the actual source of truth, mirrors [onSelectFormat]'s "never trust the UI-only
     * disabled state" precedent). */
    fun onToggleUseCommunityData() {
        if (!_uiState.value.communityEngineAvailable) return
        _uiState.update { it.copy(useCommunityData = !it.useCommunityData) }
    }

    // ── Navigation between phases ────────────────────────────────────────────

    /**
     * Deck Engine Unification plan (§5 Phase 3.1) — Commander always forces [WizardEntryFlow.CARDS]
     * and skips [WizardPhase.ENTRY] entirely (the commander IS the mandatory first seed; there is
     * nothing for the chooser to offer), preserving today's Commander UX byte-for-byte. Casual routes
     * through the new entry chooser instead.
     */
    fun onNextFromFormat() {
        val format = _uiState.value.selectedFormat ?: return
        if (format == DeckFormat.COMMANDER) {
            logStep("direction")
            _uiState.update { it.copy(phase = WizardPhase.DIRECTION, entryFlow = WizardEntryFlow.CARDS) }
        } else {
            logStep("entry")
            _uiState.update { it.copy(phase = WizardPhase.ENTRY) }
        }
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
        // Deck Engine Unification plan (§5 Phase 3.3/3.4) -- Flow B/C's own sticky-button `enabled`
        // gate mirrors this (design review RUN 3b), but the VM guard is the actual source of truth
        // (same "never trust the UI-only disabled state" precedent as the Commander check above and
        // onToggleUseCommunityData's KDoc).
        if (state.entryFlow == WizardEntryFlow.COLORS && state.colorIdentity.isEmpty()) {
            crashReporter.log("deck_wizard_step_direction_blocked_no_colors")
            viewModelScope.launch {
                _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_colors_required)))
            }
            return
        }
        if (state.entryFlow == WizardEntryFlow.STRATEGY && state.selectedArchetype == null && state.selectedDirectionTheme == null) {
            crashReporter.log("deck_wizard_step_direction_blocked_no_strategy")
            viewModelScope.launch {
                _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_strategy_required)))
            }
            return
        }
        // Edge-case audit follow-up (android-edge-case-tester, RUN 3b QA fix): Flow C's own design
        // is "pick strategy -> pick matching colors" -- BOTH steps expected, not just the first. An
        // archetype/theme pick with zero colors chosen (e.g. tapping "Next" right after
        // onSelectTaxonomyArchetype resets colorIdentity to emptySet(), without ever tapping a combo
        // chip) would otherwise sail through to a fully colorless build under a color-hungry
        // archetype (RAMP, etc) -- spec.colorIdentity = emptySet() is the LEGITIMATE "Colorless"
        // filter state per analyzeCollection's D9 comment in BuildDeckFromTemplateUseCase, so this
        // would silently construct a deck that's structurally incoherent with the picked archetype
        // instead of erroring or nudging the user. Reuses the SAME colors-required toast the
        // COLORS-flow guard above uses -- the underlying problem (no colors picked) is identical.
        if (state.entryFlow == WizardEntryFlow.STRATEGY && state.colorIdentity.isEmpty()) {
            crashReporter.log("deck_wizard_step_direction_blocked_no_colors_for_strategy")
            viewModelScope.launch {
                _events.send(DeckWizardEvent.ShowToast(appContext.getString(R.string.deck_wizard_colors_required)))
            }
            return
        }
        // Casual: pre-fill colorIdentity from the picked seeds' union, same as Commander already
        // does from the commander (DeckWizardSpec KDoc contract) -- only when the user hasn't made
        // an explicit color choice yet, so this never clobbers a Discoveries v2 hand-off or a
        // manual pick on a later back-navigation. Flows B/C already resolved colorIdentity
        // explicitly in the Direction step itself, so this only ever fires for Flow A.
        val prefilled = if (state.selectedFormat != DeckFormat.COMMANDER && state.colorIdentity.isEmpty()) {
            state.seedCards.flatMap { it.colorIdentity }.toManaColorSet()
        } else {
            state.colorIdentity
        }
        // Deck Engine Unification plan (§5 Phase 3.5): Flow B/C already resolved BOTH colors and
        // strategy in their own Direction sub-step -- the IDENTITY step (color confirmation + EDHREC
        // theme picker, Commander-only) has nothing left to add for them, so they skip straight to
        // Review. Flow A (and Commander, always CARDS) keeps the existing IDENTITY step.
        if (state.entryFlow != WizardEntryFlow.CARDS) {
            logStep("review")
            _uiState.update { it.copy(phase = WizardPhase.REVIEW, colorIdentity = prefilled) }
        } else {
            logStep("identity")
            _uiState.update { it.copy(phase = WizardPhase.IDENTITY, colorIdentity = prefilled) }
        }
    }

    fun onNextFromIdentity() {
        logStep("review")
        _uiState.update { it.copy(phase = WizardPhase.REVIEW) }
    }

    /** Back navigation between steps 1-4 (+ the new [WizardPhase.ENTRY], plan §5 Phase 3.1). Returns
     * `true` when the caller should pop the whole wizard screen instead (already at step 1, or
     * Result — nothing left for the VM to unwind). */
    fun onBackPressed(): Boolean {
        val state = _uiState.value
        return when (state.phase) {
            WizardPhase.FORMAT -> true
            WizardPhase.ENTRY -> { _uiState.update { it.copy(phase = WizardPhase.FORMAT) }; false }
            // Commander skipped ENTRY (always CARDS, see WizardEntryFlow's KDoc) -- back goes
            // straight to FORMAT for it, and to ENTRY for every Casual flow.
            WizardPhase.DIRECTION -> {
                val target = if (state.selectedFormat == DeckFormat.COMMANDER) WizardPhase.FORMAT else WizardPhase.ENTRY
                _uiState.update { it.copy(phase = target) }
                false
            }
            WizardPhase.IDENTITY -> { _uiState.update { it.copy(phase = WizardPhase.DIRECTION) }; false }
            // Flow B/C skip IDENTITY entirely (onNextFromDirection) -- back must mirror that, or the
            // user would land on a step they never visited on the way in.
            WizardPhase.REVIEW -> {
                val target = if (state.entryFlow == WizardEntryFlow.CARDS) WizardPhase.IDENTITY else WizardPhase.DIRECTION
                _uiState.update { it.copy(phase = target) }
                false
            }
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
            // Wizard Quality Campaign telemetry: tracks which Direction-step chip (archetype /
            // theme / tribe / none) actually drove this build, to confirm the taxonomy chip slots
            // land real hint data (see the "dead chip" fix in this campaign).
            val directionType = when {
                state.selectedArchetype != null -> "archetype"
                state.selectedDirectionTheme != null -> "theme"
                state.selectedTribeKey != null -> "tribe"
                else -> "none"
            }
            crashlytics.setCustomKey("deck_wizard_direction_type", directionType)
            // Deck Engine Unification plan (§5 Phase 3) telemetry: which entry flow actually built
            // this deck, and whether the community-source toggle was on (Motor B live-wired).
            crashlytics.setCustomKey("deck_wizard_entry_flow", state.entryFlow.name)
            crashlytics.setCustomKey("deck_wizard_use_community_data", state.useCommunityData)

            val colorIdentity = if (format == DeckFormat.COMMANDER) {
                state.selectedCommander?.colorIdentity?.toManaColorSet() ?: state.colorIdentity
            } else {
                state.colorIdentity
            }
            // Deck Engine Unification (D2): the Direction step's own theme pick and the Identity
            // step's SEPARATE EDHREC theme picker are two independent slots (mirrors the
            // pre-unification strategyHint/tagHint + themeHint split) -- both fold into the same
            // StrategyProfile.themes list, capped at 2 (ArchetypeSkeletonResolver's own cap).
            val identityTheme = ThemeId.fromDisplayName(state.selectedThemeHint)
            val spec = DeckWizardSpec(
                format = format,
                commander = state.selectedCommander,
                strategyProfile = StrategyProfile(
                    archetype = state.selectedArchetype,
                    themes = listOfNotNull(state.selectedDirectionTheme, identityTheme).distinct().take(2),
                    tribe = state.selectedTribeKey,
                    colors = colorIdentity,
                ),
                colorIdentity = colorIdentity,
                seeds = state.seedCards,
                fillLands = state.fillLands,
                useCommunityData = state.useCommunityData,
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

    /** Creates the fresh deck, writes commander/cover (Commander only) AND the commander's
     * qty-1 mainboard entry (BUG-1 fix, Deck Engine Unification plan §0.1 — see below), every
     * [TemplateBuildResult.deckCards] entry, and the archetype/theme/tribe override + strategy
     * lock — mirrors [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel
     * .generateFromSeeds]'s write-through convention (one repo call per card copy; a mid-write
     * cancellation leaves a partial but valid deck).
     *
     * BUG-1: the wizard previously wrote ONLY [com.mmg.manahub.core.model.Deck.commanderCardId] --
     * [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel.rebuildUiState] resolves the
     * commander from the deck's ENTRIES (`allEntries.find { it.scryfallId == commanderId }`), and
     * [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel.setCommander] (the convention
     * owner) ALSO inserts a qty-1 mainboard row when one is absent. Without that row the commander
     * was invisible in the built deck. [BuildDeckFromTemplateUseCase.mainboardTargetSize] already
     * reserves `targetDeckSize - 1` for the commander, so this row brings the total to exactly 100.
     *
     * D4 (hard no-cut guarantee): every placed card — commander included — writes
     * `source = DeckCardSource.WIZARD`, and the deck is marked `strategyLocked = true`. Both gates
     * are CONSUMED starting Phase 2 (RUN 2); this write only persists the provenance/lock.
     */
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
            // BUG-1 fix: insert the commander as a qty-1 mainboard entry — see this method's KDoc.
            deckRepository.addCardToDeck(deckId, commander.scryfallId, 1, false, DeckCardSource.WIZARD)
        }

        result.deckCards.forEach { entry ->
            deckRepository.addCardToDeck(deckId, entry.card.scryfallId, entry.quantity, entry.isSideboard, DeckCardSource.WIZARD)
        }
        deckRepository.updateArchetypeOverride(deckId, result.archetypeOverride, result.themesOverride)
        deckRepository.updateTribeOverride(deckId, spec.strategyProfile.tribe)
        deckRepository.updateStrategyLocked(deckId, true)
        return deckId
    }

    private fun wizardDeckName(spec: DeckWizardSpec): String {
        // Defense in depth (RUN 3b): gate on format even though onSelectFormat's reset (above)
        // already makes a non-null commander on a non-Commander spec unreachable via normal UI
        // flow -- this function shouldn't silently trust an out-of-band-constructed spec.
        val commander = spec.commander.takeIf { spec.format == DeckFormat.COMMANDER }
        val profile = spec.strategyProfile
        val archetypeName = profile.archetype?.takeIf { it != ArchetypeId.GENERIC }?.displayName
        val themeName = profile.themes.firstOrNull()?.displayName
        return when {
            commander != null -> commander.name
            archetypeName != null ->
                String.format(appContext.getString(R.string.deck_wizard_name_suffix_deck), archetypeName)
            themeName != null ->
                String.format(appContext.getString(R.string.deck_wizard_name_suffix_deck), themeName)
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
                // D4: still part of the guided wizard build (Result screen, pre-handoff to Studio)
                // -- WIZARD provenance, same as every other card this flow placed.
                deckRepository.addCardToDeck(deckId, cardId, suggestion.suggestedCopies, false, DeckCardSource.WIZARD)
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
