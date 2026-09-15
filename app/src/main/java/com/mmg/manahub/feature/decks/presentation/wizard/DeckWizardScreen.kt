package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-08

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.CardRow
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.SmallCardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategyCatalog
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import org.jetbrains.compose.resources.painterResource
import org.koin.androidx.compose.koinViewModel

/**
 * Steps shown by [WizardStepIndicator]; GENERATING/RESULT replace the whole body instead.
 * Deck Wizard v4 (R13): the format is chosen at deck creation and arrives via a REQUIRED nav arg
 * (`Screen.DeckWizard.createRoute`) -- there is no format step for ANY flow any more. Commander
 * runs `COMMANDER_PICK → STRATEGY → MANUAL_ADDS → REVIEW`; every Casual flow (A/B/C) runs
 * `ENTRY → DIRECTION → MANUAL_ADDS → REVIEW` ([WizardPhase.IDENTITY] stays unreachable dead code
 * for every flow, see [DeckWizardViewModel.onNextFromDirection]'s KDoc).
 */
private fun stepPhasesFor(uiState: DeckWizardUiState): List<WizardPhase> {
    if (uiState.selectedFormat?.isCommanderFormat == true) {
        return listOf(WizardPhase.COMMANDER_PICK, WizardPhase.STRATEGY, WizardPhase.MANUAL_ADDS, WizardPhase.REVIEW)
    }
    return listOf(WizardPhase.ENTRY, WizardPhase.DIRECTION, WizardPhase.MANUAL_ADDS, WizardPhase.REVIEW)
}

/**
 * Deck Builder v2 (`docs/plans/deck-builder-v2-plan.md` §3.4) — the 4-step wizard + generation +
 * result flow. A single full-screen composable with INTERNAL phase state driven by
 * [DeckWizardViewModel] (mirrors the Playtest mulligan/battle-phase-in-one-screen precedent — see
 * CLAUDE.md's Deck Playtest section for why this shape was chosen over per-step nav destinations).
 *
 * @param onBack pops the wizard off the back stack (invoked once [DeckWizardViewModel.onBackPressed]
 *   says there is nothing left to unwind internally).
 * @param onOpenDeckStudio navigates into [com.mmg.manahub.app.navigation.Screen.DeckStudio] for the
 *   freshly built deck (Result screen's primary CTA) — the caller pops the wizard off the stack too
 *   so returning from Deck Studio lands on whatever screen opened the wizard, not back on Result.
 * @param onCardClick opens the full Card Detail screen (Direction commander search / seed search /
 *   Result suggestion taps).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckWizardScreen(
    onBack: () -> Unit,
    onOpenDeckStudio: (deckId: String) -> Unit,
    onCardClick: (String) -> Unit,
    viewModel: DeckWizardViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val toastState = rememberMagicToastState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        FirebaseCrashlytics.getInstance().log("screen_viewed: deck_wizard")
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DeckWizardEvent.ShowToast -> toastState.show(event.message, event.type)
                is DeckWizardEvent.OpenDeckStudio -> onOpenDeckStudio(event.deckId)
            }
        }
    }

    val handleBack: () -> Unit = { if (viewModel.onBackPressed()) onBack() }
    BackHandler(onBack = handleBack)

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = mc.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                DeckWizardTopBar(
                    uiState = uiState,
                    onBack = handleBack,
                )
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                AnimatedContent(
                    targetState = uiState.phase,
                    transitionSpec = {
                        val stepPhases = stepPhasesFor(uiState)
                        val forward = stepPhases.indexOf(targetState) >= stepPhases.indexOf(initialState)
                        if (forward) {
                            (slideInHorizontally(tween(250)) { it / 4 } + fadeIn(tween(250))) togetherWith
                                (slideOutHorizontally(tween(250)) { -it / 4 } + fadeOut(tween(150)))
                        } else {
                            (slideInHorizontally(tween(250)) { -it / 4 } + fadeIn(tween(250))) togetherWith
                                (slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(150)))
                        }
                    },
                    label = "DeckWizardPhase",
                ) { phase ->
                    when (phase) {
                        WizardPhase.ENTRY -> EntryStepContent(
                            onSelect = viewModel::onSelectEntryFlow,
                        )
                        WizardPhase.COMMANDER_PICK -> CommanderPickStepContent(
                            uiState = uiState,
                            onQueryChange = viewModel::onCommanderNameFilterChange,
                            onSelectCommander = viewModel::onSelectCommander,
                            onClearCommander = viewModel::onClearCommander,
                            onApplyStructuredSearch = viewModel::applyCommanderStructuredSearch,
                            onClearFilters = viewModel::onClearCommanderFilters,
                            onClearSearchAndFilters = viewModel::onClearCommanderSearchAndFilters,
                            onNext = viewModel::onNextFromCommanderPick,
                        )
                        WizardPhase.STRATEGY -> StrategyStepContent(
                            uiState = uiState,
                            onSelectStrategy = viewModel::onSelectCommanderStrategy,
                            onSelectCustom = viewModel::onSelectCustomStrategy,
                            onRequestTribe = viewModel::onRequestTribeForStrategy,
                            onPickTribe = viewModel::onPickTribeForStrategy,
                            onCancelTribePick = viewModel::onCancelTribePickForStrategy,
                            onNext = viewModel::onNextFromStrategy,
                        )
                        WizardPhase.MANUAL_ADDS -> if (uiState.selectedFormat?.isCommanderFormat == true) {
                            // Deck Wizard Commander v3 plan (Phase 5, R4): Commander mounts the
                            // engine-attributed Plan Sections step on this SAME phase instead of the
                            // old skeleton-guided free search -- Casual keeps ManualAddsStepContent
                            // below, byte-identical.
                            PlanSectionsStepContent(
                                uiState = uiState,
                                onQueryChange = viewModel::onPlanSectionsQueryChange,
                                onApplyStructuredSearch = viewModel::applyPlanSectionsStructuredSearch,
                                onFilterByTags = viewModel::searchPlanSectionsCollectionByTags,
                                onScryfallSearch = viewModel::searchPlanSectionsScryfall,
                                onAddCard = viewModel::onAddSeed,
                                onRemoveCard = viewModel::onRemoveSeed,
                                onClearSearchState = viewModel::clearPlanSectionsSearchState,
                                onCardClick = onCardClick,
                                onNext = viewModel::onNextFromManualAdds,
                            )
                        } else {
                            ManualAddsStepContent(
                                uiState = uiState,
                                onQueryChange = viewModel::onManualAddsQueryChange,
                                onToggleIncludeOutsideCollection = viewModel::onToggleIncludeOutsideCollection,
                                onSelectRoleFilter = viewModel::onSelectManualAddsRoleFilter,
                                onAddSeed = viewModel::onAddSeed,
                                onRemoveSeed = viewModel::onRemoveSeed,
                                onNext = viewModel::onNextFromManualAdds,
                            )
                        }
                        WizardPhase.DIRECTION -> DirectionStepContent(
                            uiState = uiState,
                            onSelectDirectionTag = viewModel::onSelectDirectionTag,
                            onSelectTribe = viewModel::onSelectTribeDirection,
                            onCommanderQueryChange = viewModel::onCommanderQueryChange,
                            onSelectCommander = viewModel::onSelectCommander,
                            onClearCommander = viewModel::onClearCommander,
                            onToggleSeedPicker = viewModel::onToggleSeedPicker,
                            onToggleIncludeOutsideCollection = viewModel::onToggleIncludeOutsideCollection,
                            onSeedQueryChange = viewModel::onSeedQueryChange,
                            onAddSeed = viewModel::onAddSeed,
                            onRemoveSeed = viewModel::onRemoveSeed,
                            onSelectSeedStrategyCandidate = viewModel::onSelectSeedStrategyCandidate,
                            onToggleCardsFlowColor = viewModel::onToggleCardsFlowColor,
                            onToggleColorFlowColor = viewModel::onToggleColorFlowColor,
                            onSelectColorAffinityEntry = viewModel::onSelectColorAffinityEntry,
                            onTaxonomyQueryChange = viewModel::onTaxonomyQueryChange,
                            onSelectTaxonomyArchetype = viewModel::onSelectTaxonomyArchetype,
                            onSelectTaxonomyTheme = viewModel::onSelectTaxonomyTheme,
                            onSelectColorCombo = viewModel::onSelectColorCombo,
                            onToggleSuggestedSeed = viewModel::onToggleSuggestedSeed,
                            onNext = viewModel::onNextFromDirection,
                        )
                        WizardPhase.IDENTITY -> IdentityStepContent(
                            uiState = uiState,
                            onToggleColor = viewModel::onToggleColor,
                            onSelectTheme = viewModel::onSelectThemeHint,
                            onNext = viewModel::onNextFromIdentity,
                        )
                        WizardPhase.REVIEW -> ReviewStepContent(
                            uiState = uiState,
                            onToggleFillLands = viewModel::onToggleFillLands,
                            onToggleIncludeNonBasicLands = viewModel::onToggleIncludeNonBasicLands,
                            onToggleUseCommunityData = viewModel::onToggleUseCommunityData,
                            onGenerate = viewModel::onGenerate,
                        )
                        WizardPhase.GENERATING -> GeneratingContent(
                            uiState = uiState,
                            onCancel = viewModel::onCancelGeneration,
                            onRetry = viewModel::onRetryGeneration,
                        )
                        // Deck Wizard v4, W7 Task B (R10) -- Commander-only, inserted whenever the
                        // build surfaced at least one ambiguity group; a zero-group build skips this
                        // phase entirely (see WizardPhase.CHOICE's own KDoc).
                        WizardPhase.CHOICE -> ChoiceStepContent(
                            uiState = uiState,
                            onToggleCard = viewModel::onToggleChoiceCard,
                            onAutoFillSection = viewModel::onAutoFillChoiceSection,
                            onFinish = viewModel::onFinishChoices,
                            onCardClick = onCardClick,
                        )
                        // W7 Task D (plan 7.5): Commander never reaches RESULT any more --
                        // finalizeCommanderDraft fires DeckWizardEvent.OpenDeckStudio directly once
                        // the write succeeds (see that function's own KDoc). RESULT is Casual-only now.
                        WizardPhase.RESULT -> ResultContent(
                            uiState = uiState,
                            onAddSuggestion = viewModel::onAddCommunitySuggestion,
                            onCardClick = onCardClick,
                            onOpenDeckStudio = viewModel::onOpenDeckStudio,
                            onBack = handleBack,
                        )
                    }
                }
            }
        }

        MagicToastHost(state = toastState, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun DeckWizardTopBar(uiState: DeckWizardUiState, onBack: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(color = mc.backgroundSecondary) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xs, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = mc.textSecondary)
                }
                Text(
                    text = stringResource(R.string.deck_wizard_title),
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                    modifier = Modifier.padding(start = spacing.sm),
                )
            }
            val stepPhases = remember(uiState.selectedFormat, uiState.entryFlow) { stepPhasesFor(uiState) }
            val stepIndex = stepPhases.indexOf(uiState.phase)
            if (stepIndex >= 0) {
                WizardStepIndicator(
                    stepIndex = stepIndex,
                    stepCount = stepPhases.size,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.sm),
                )
            }
        }
    }
}

/** A lightweight "Step N of M" segmented progress bar (design spec: 4-segment `WizardStepIndicator`,
 * shown only for steps 1-4 — Generating/Result replace the whole body). */
@Composable
private fun WizardStepIndicator(stepIndex: Int, stepCount: Int, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.deck_wizard_step_of, stepIndex + 1, stepCount),
            style = ty.labelSmall,
            color = mc.textSecondary,
        )
        Spacer(Modifier.height(MaterialTheme.spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs), modifier = Modifier.fillMaxWidth()) {
            repeat(stepCount) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(ChipShape)
                        // C2 (design review): mc.surfaceVariant is ~1.1:1 contrast against
                        // surface/background on HallowedPrint (the app's one light theme) --
                        // a theme-agnostic alpha-tinted foreground reads on every palette
                        // (feedback_light_theme_contrast_tokens precedent).
                        .background(if (index <= stepIndex) mc.primaryAccent else mc.textDisabled.copy(alpha = 0.25f)),
                )
            }
        }
    }
}

/** Sticky bottom CTA reused by every step-1-4 content composable (mirrors the retired legacy
 * `SeedsContent`'s own sticky-button pattern). */
@Composable
internal fun WizardStickyButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(color = mc.background, modifier = modifier.fillMaxWidth().navigationBarsPadding()) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = ChipShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = mc.primaryAccent,
                contentColor = mc.background,
                // C2 (design review): surfaceVariant is near-invisible on HallowedPrint; this is
                // the PRIMARY CTA before a selection is made, so it must stay visibly a button.
                disabledContainerColor = mc.textDisabled.copy(alpha = 0.25f),
                disabledContentColor = mc.textDisabled,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.md).height(52.dp),
        ) {
            Text(text = label, style = ty.titleMedium)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 4 — Review & Generate
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ReviewStepContent(
    uiState: DeckWizardUiState,
    onToggleFillLands: () -> Unit,
    onToggleIncludeNonBasicLands: () -> Unit,
    onToggleUseCommunityData: () -> Unit,
    onGenerate: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val isCommander = uiState.selectedFormat?.isCommanderFormat == true

    // C1 (design review): the sticky CTA is a real Column sibling, weight(1f) + verticalScroll on
    // the content above it -- its real measured height is what the list actually loses, never a
    // guessed bottom-padding reservation.
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Spacer(Modifier.height(spacing.md))
            Text(stringResource(R.string.deck_wizard_review_title), style = ty.titleLarge, color = mc.textPrimary)

            uiState.selectedCommander?.let { commander ->
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    ReviewSectionLabel(stringResource(R.string.deck_wizard_review_commander_section))
                    CardRow(
                        card = commander,
                        isInCollection = true,
                        onClick = { /* Detail already visible if needed */ },
                        onRemove = null,
                        isCommander = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Deck Wizard Commander v3 plan (Phase 6, 6.2): the STRATEGY step's own pick, resolved
            // to its display name + description (Custom = the same fallback copy the Strategy step
            // itself shows) -- byte-identical to the pin generateCommanderDeck re-resolves for the
            // real build (D4), never a re-derivation.
            if (isCommander) {
                val strategy = uiState.selectedCuratedStrategyId?.let { CuratedStrategyCatalog.byId(it) }
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    ReviewSectionLabel(stringResource(R.string.deck_wizard_review_strategy_section))
                    Surface(shape = SmallCardShape, color = mc.backgroundSecondary, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(spacing.md)) {
                            Text(
                                text = strategy?.displayName ?: stringResource(R.string.deck_wizard_strategy_custom_title),
                                style = ty.titleMedium,
                                color = mc.textPrimary,
                            )
                            Text(
                                text = strategy?.description ?: stringResource(R.string.deck_wizard_strategy_custom_description),
                                style = ty.bodySmall,
                                color = mc.textSecondary,
                                modifier = Modifier.padding(top = spacing.xxs),
                            )
                        }
                    }
                }
            }

            if (uiState.seedCards.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    ReviewSectionLabel(
                        if (isCommander) {
                            stringResource(R.string.deck_wizard_review_manual_adds_count, uiState.seedCards.size)
                        } else {
                            stringResource(R.string.deck_wizard_review_seeds)
                        }
                    )
                    // A nested-scroll horizontal LazyRow inside the outer verticalScroll Column is
                    // safe here (different axis) -- same reasoning as the task's guidance for this
                    // screen; each tile keyed by scryfallId (unique, no duplicate-copy collision
                    // since seed cards are deduped by id upstream in the ViewModel).
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        items(uiState.seedCards, key = { it.scryfallId }) { card -> ReviewSeedCardTile(card) }
                    }
                }
            }

            Surface(shape = SmallCardShape, color = mc.backgroundSecondary, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    ReviewRow(stringResource(R.string.deck_wizard_review_format), uiState.selectedFormat?.displayName ?: "—")
                    // Commander already has its own dedicated Commander/Strategy sections above --
                    // this generic Direction chip is Casual-only from here on (it used to redundantly
                    // repeat the commander's name for Commander builds).
                    if (!isCommander) {
                        ReviewChipRow(
                            label = stringResource(R.string.deck_wizard_review_direction),
                            chipText = uiState.selectedArchetype?.displayName
                                ?: uiState.selectedDirectionTheme?.displayName
                                ?: uiState.selectedTribeLabel
                                ?: stringResource(R.string.deck_wizard_review_direction_none),
                        )
                    }
                    ReviewColorsRow(
                        label = stringResource(R.string.deck_wizard_review_colors),
                        colors = uiState.colorIdentity,
                    )
                }
            }

            // Casual-only (W5.2): the legacy all-or-nothing land-fill toggle for Motor A/
            // BuildDeckFromTemplateUseCase. Commander gets its own "Include non-basic lands" switch
            // below (basics are unconditional for Commander per R12 -- there is nothing left for a
            // Commander-facing all-or-nothing toggle to gate).
            if (!isCommander) {
                Surface(
                    onClick = onToggleFillLands,
                    shape = SmallCardShape,
                    color = mc.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(spacing.md).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.deck_wizard_fill_lands_title), style = ty.bodyMedium, color = mc.textPrimary)
                            Text(stringResource(R.string.deck_wizard_fill_lands_subtitle), style = ty.labelSmall, color = mc.textSecondary)
                        }
                        Switch(
                            checked = uiState.fillLands,
                            onCheckedChange = { onToggleFillLands() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = mc.onAccent,
                                checkedTrackColor = mc.primaryAccent,
                                uncheckedThumbColor = mc.textDisabled,
                                uncheckedTrackColor = mc.surfaceVariant,
                            ),
                        )
                    }
                }
            } else {
                Surface(
                    onClick = onToggleIncludeNonBasicLands,
                    shape = SmallCardShape,
                    color = mc.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(spacing.md).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.deck_wizard_include_nonbasic_lands_title), style = ty.bodyMedium, color = mc.textPrimary)
                            Text(stringResource(R.string.deck_wizard_include_nonbasic_lands_subtitle), style = ty.labelSmall, color = mc.textSecondary)
                        }
                        Switch(
                            checked = uiState.includeNonBasicLands,
                            onCheckedChange = { onToggleIncludeNonBasicLands() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = mc.onAccent,
                                checkedTrackColor = mc.primaryAccent,
                                uncheckedThumbColor = mc.textDisabled,
                                uncheckedTrackColor = mc.surfaceVariant,
                            ),
                        )
                    }
                }
            }

            // Deck Engine Unification plan (§5 Phase 3.5) — the shared source step, Casual-only
            // since W5.1 (G9): community decklists are unvalidated, so Commander's review step never
            // shows this toggle (its whole placement-prior plumbing was deleted). Hidden entirely
            // when the global community-engine flag is off (never a disabled-but-visible toggle for
            // a capability the user can't actually use — mirrors the Coming Soon format cards'
            // "never advertise something inert" convention).
            if (!isCommander && uiState.communityEngineAvailable) {
                Surface(
                    onClick = onToggleUseCommunityData,
                    shape = SmallCardShape,
                    color = mc.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(spacing.md).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.deck_wizard_use_community_data_title), style = ty.bodyMedium, color = mc.textPrimary)
                            Text(stringResource(R.string.deck_wizard_use_community_data_subtitle), style = ty.labelSmall, color = mc.textSecondary)
                        }
                        Switch(
                            checked = uiState.useCommunityData,
                            onCheckedChange = { onToggleUseCommunityData() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = mc.onAccent,
                                checkedTrackColor = mc.primaryAccent,
                                uncheckedThumbColor = mc.textDisabled,
                                uncheckedTrackColor = mc.surfaceVariant,
                            ),
                        )
                    }
                }
            }

            Surface(shape = CardShape, color = mc.goldMtg.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(spacing.md), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = mc.goldMtg, modifier = Modifier.size(20.dp))
                    Text(
                        // Deck Wizard Commander v3 plan (Phase 6, 6.2): Commander gets its own
                        // honest expectation copy (D7/D8 -- collection-only, gaps reported not
                        // filled with weak cards); Casual keeps the original "deeper build" copy.
                        text = if (isCommander) {
                            stringResource(R.string.deck_wizard_commander_expectation_copy)
                        } else {
                            stringResource(R.string.deck_wizard_expectation_copy)
                        },
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                        modifier = Modifier.padding(start = spacing.sm),
                    )
                }
            }
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_generate_cta),
            enabled = true,
            onClick = onGenerate,
        )
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = ty.bodyMedium, color = mc.textSecondary)
        Text(value, style = ty.bodyMedium, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Small uppercase-weight section header above a Review-step block (Commander / Seed cards). */
@Composable
private fun ReviewSectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.magicTypography.labelLarge, color = MaterialTheme.magicColors.textSecondary)
}

/** One seed card as a compact image tile for the Review step's horizontal seed-cards strip. */
@Composable
private fun ReviewSeedCardTile(card: Card) {
    val mc = MaterialTheme.magicColors
    AsyncImage(
        model = card.imageNormal,
        contentDescription = card.name,
        placeholder = painterResource(Res.drawable.mtg_card_back),
        error = painterResource(Res.drawable.mtg_card_back),
        fallback = painterResource(Res.drawable.mtg_card_back),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(width = 72.dp, height = 100.dp)
            .clip(SmallCardShape)
            .background(mc.surfaceVariant),
    )
}

/** A [ReviewRow] variant that renders its value as a tonal chip -- used for the Direction/
 * archetype pick, which is a single discrete choice rather than free-form text. */
@Composable
private fun ReviewChipRow(label: String, chipText: String) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = ty.bodyMedium, color = mc.textSecondary)
        Surface(shape = ChipShape, color = mc.primaryAccent.copy(alpha = 0.14f)) {
            Text(
                text = chipText,
                style = ty.labelMedium,
                color = mc.primaryAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
            )
        }
    }
}

/** Renders the deck's color identity as real WUBRG mana-symbol icons ([ManaCostImages]) instead of
 * the plain `.symbol` letters -- falls back to the existing "Colorless" copy when empty. */
@Composable
private fun ReviewColorsRow(label: String, colors: Set<ManaColor>) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = ty.bodyMedium, color = mc.textSecondary)
        if (colors.isEmpty()) {
            Text(stringResource(R.string.deck_seeds_identity_colorless), style = ty.bodyMedium, color = mc.textPrimary)
        } else {
            // ManaColor.symbol is already a bare WUBRG letter ("W"/"U"/...), the exact token shape
            // ManaSymbolImage/ManaCostImages expects (it maps token -> scryfalls's card-symbols
            // SVG) -- wrapped in "{}" only to satisfy ManaCostImages' cost-string parser.
            ManaCostImages(
                manaCost = colors.sortedBy { it.ordinal }.joinToString(separator = "") { "{${it.symbol}}" },
                symbolSize = 20.dp,
                spacing = spacing.xxs,
            )
        }
    }
}
