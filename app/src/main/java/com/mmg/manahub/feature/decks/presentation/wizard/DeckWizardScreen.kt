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
import com.mmg.manahub.core.ui.components.MagicCtaButton
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
 * Steps shown by [WizardStepIndicator]; GENERATING/CHOICE replace the whole body instead.
 * Deck Wizard 60-card wave (v6), plan §5 Phase 5.1, S20: ONE build engine, ONE step language for
 * every format now. Commander: `COMMANDER_PICK → STRATEGY → PLAN_SECTIONS → REVIEW` (4). Cards:
 * `ENTRY → SEED_PICK → STRATEGY → PLAN_SECTIONS → REVIEW` (5). Colors:
 * `ENTRY → COLOR_PICK → PLAN_SECTIONS → REVIEW` (4). Strategy:
 * `ENTRY → STRATEGY_PICK → PLAN_SECTIONS → REVIEW` (4).
 */
private fun stepPhasesFor(uiState: DeckWizardUiState): List<WizardPhase> {
    if (uiState.selectedFormat?.isCommanderFormat == true) {
        return listOf(WizardPhase.COMMANDER_PICK, WizardPhase.STRATEGY, WizardPhase.PLAN_SECTIONS, WizardPhase.REVIEW)
    }
    return when (uiState.entryFlow) {
        WizardEntryFlow.CARDS -> listOf(WizardPhase.ENTRY, WizardPhase.SEED_PICK, WizardPhase.STRATEGY, WizardPhase.PLAN_SECTIONS, WizardPhase.REVIEW)
        WizardEntryFlow.COLORS -> listOf(WizardPhase.ENTRY, WizardPhase.COLOR_PICK, WizardPhase.PLAN_SECTIONS, WizardPhase.REVIEW)
        WizardEntryFlow.STRATEGY -> listOf(WizardPhase.ENTRY, WizardPhase.STRATEGY_PICK, WizardPhase.PLAN_SECTIONS, WizardPhase.REVIEW)
    }
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
                // Deck Wizard 60-card wave (v6), plan §5 Phase 5.1: an unsupported/missing format
                // nav arg -- the toast already fired from the VM, this just pops the screen.
                is DeckWizardEvent.Exit -> onBack()
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
                        WizardPhase.SEED_PICK -> SeedPickStepContent(
                            uiState = uiState,
                            onQueryChange = viewModel::onSeedPickQueryChange,
                            onApplyStructuredSearch = viewModel::applySeedPickStructuredSearch,
                            onClearFilters = viewModel::onClearSeedPickFilters,
                            onClearSearchAndFilters = viewModel::onClearSeedPickSearchAndFilters,
                            onAddSeed = viewModel::onAddSeed,
                            onRemoveSeedCopy = viewModel::onRemoveSeedCopy,
                            onRemoveSeed = viewModel::onRemoveSeed,
                            onToggleSeedQueue = viewModel::onToggleSeedQueue,
                            onShowSeedDetail = viewModel::onShowSeedDetail,
                            onNext = viewModel::onNextFromSeedPick,
                        )
                        // Deck Wizard 60-card wave (v6), plan §5 Phase 5.1: minimal placeholders --
                        // run B (plan §5 Phase 5.3) wires ColorPickStepContent's/StrategyPickStepContent's
                        // real onNext handlers; Next stays disabled on both until then (see those
                        // composables' own KDoc).
                        WizardPhase.COLOR_PICK -> ColorPickStepContent(uiState = uiState, onNext = {})
                        WizardPhase.STRATEGY_PICK -> StrategyPickStepContent(uiState = uiState, onNext = {})
                        WizardPhase.STRATEGY -> StrategyStepContent(
                            uiState = uiState,
                            onSelectStrategy = viewModel::onSelectCommanderStrategy,
                            onSelectCustom = viewModel::onSelectCustomStrategy,
                            onRequestTribe = viewModel::onRequestTribeForStrategy,
                            onPickTribe = viewModel::onPickTribeForStrategy,
                            onCancelTribePick = viewModel::onCancelTribePickForStrategy,
                            onNext = viewModel::onNextFromStrategy,
                        )
                        // Deck Wizard 60-card wave (v6), plan §5 Phase 5.1 (S1): ONE PLAN_SECTIONS
                        // screen for every anchor now -- the pre-v6 Casual-only ManualAddsStepContent
                        // branch was deleted along with the DIRECTION/IDENTITY step machinery it
                        // depended on.
                        WizardPhase.PLAN_SECTIONS -> PlanSectionsStepContent(
                            uiState = uiState,
                            onQueryChange = viewModel::onPlanSectionsQueryChange,
                            onApplyStructuredSearch = viewModel::applyPlanSectionsStructuredSearch,
                            onFilterByTags = viewModel::searchPlanSectionsCollectionByTags,
                            onScryfallSearch = viewModel::searchPlanSectionsScryfall,
                            onAddCard = viewModel::onAddSeed,
                            onRemoveCard = viewModel::onRemoveSeed,
                            onClearSearchState = viewModel::clearPlanSectionsSearchState,
                            onCardClick = onCardClick,
                            onNext = viewModel::onNextFromPlanSections,
                        )
                        WizardPhase.REVIEW -> ReviewStepContent(
                            uiState = uiState,
                            onToggleIncludeNonBasicLands = viewModel::onToggleIncludeNonBasicLands,
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
    val spacing = MaterialTheme.spacing
    // W7 fix 5.6 (design review): swapped the raw M3 Button for MagicCtaButton -- it supports the
    // same full-width sticky layout (fillMaxWidth + fixed height, Box-centered content) plus the
    // shared gradient/glow/disabled-state language every other CTA in the app already uses.
    Surface(color = mc.background, modifier = modifier.fillMaxWidth().navigationBarsPadding()) {
        MagicCtaButton(
            text = label,
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.md).height(52.dp),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 4 — Review & Generate
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ReviewStepContent(
    uiState: DeckWizardUiState,
    onToggleIncludeNonBasicLands: () -> Unit,
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

            if (uiState.seeds.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    ReviewSectionLabel(
                        if (isCommander) {
                            stringResource(R.string.deck_wizard_review_manual_adds_count, uiState.seeds.size)
                        } else {
                            stringResource(R.string.deck_wizard_review_seeds)
                        }
                    )
                    // A nested-scroll horizontal LazyRow inside the outer verticalScroll Column is
                    // safe here (different axis) -- same reasoning as the task's guidance for this
                    // screen; each tile keyed by scryfallId (unique, no duplicate-copy collision
                    // since seeds are deduped by id upstream in the ViewModel). Deck Wizard 60-card
                    // wave (v6): a per-seed quantity badge (S6) is run C's own Review rewrite (plan
                    // §5 Phase 5.4) -- this run only had to keep the strip compiling.
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        items(uiState.seeds, key = { it.card.scryfallId }) { seed -> ReviewSeedCardTile(seed.card) }
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
                        // Deck Wizard 60-card wave (v6): the pre-v6 Casual-only Direction-theme pick
                        // is gone -- every non-Commander anchor now picks its theme via the SAME
                        // shared STRATEGY step Commander uses, so this reads selectedStrategyThemes
                        // (S1/S7) instead.
                        ReviewChipRow(
                            label = stringResource(R.string.deck_wizard_review_direction),
                            chipText = uiState.selectedArchetype?.displayName
                                ?: uiState.selectedStrategyThemes.firstOrNull()?.displayName
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

            // Deck Wizard 60-card wave (v6): the Casual-only all-or-nothing land-fill toggle and the
            // community-data toggle were DELETED along with their backing state
            // (fillLands/useCommunityData/communityEngineAvailable, S1's UI unification) --
            // generateCasualDeck now always fills lands and never blends community data (see that
            // function's own class-level KDoc); run C's S12 Review rewrite is the real replacement.
            // Commander keeps its own "Include non-basic lands" switch (basics are unconditional for
            // Commander per R12).
            if (isCommander) {
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
